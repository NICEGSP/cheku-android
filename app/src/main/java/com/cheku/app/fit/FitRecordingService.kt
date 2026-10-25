package com.cheku.app.fit

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.*
import android.location.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cheku.app.MainActivity
import com.cheku.app.data.*
import kotlinx.coroutines.*

class FitRecordingService : Service(), LocationListener, SensorEventListener {
    companion object {
        const val ACTION_START = "com.cheku.app.fit.START"
        const val ACTION_PAUSE = "com.cheku.app.fit.PAUSE"
        const val ACTION_RESUME = "com.cheku.app.fit.RESUME"
        const val ACTION_STOP = "com.cheku.app.fit.STOP"
        const val EXTRA_NAME = "name"
        const val EXTRA_AUTO_PAUSE = "autoPause"
        const val CHANNEL = "fit_recording"
        const val NOTIFICATION_ID = 7301
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    /** 最近一个 GPS 点的单调时钟时刻，决定网络点用不用 */
    private var lastGpsElapsedMs: Long? = null
    private var lastStoredElapsedNanos: Long? = null
    @Volatile private var segmentBreakPending = false
    private lateinit var repository: FitRepository
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private lateinit var ble: BleSensorManager
    private var activityId: String? = null
    private var lowSpeedSince: Long? = null
    private var pausedSpeed = 0.0
    private var lastUsableGpsElapsed = 0L
    private var lastSensorMovementElapsed = 0L
    private var gyroMagnitude = 0f
    @Volatile private var motionMagnitude: Float? = null
    private var sensorPublisherJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = FitRepository(AppDatabase.get(this).ledgerDao())
        locationManager = getSystemService(LocationManager::class.java)
        sensorManager = getSystemService(SensorManager::class.java)
        ble = BleSensorManager(this)
        createChannel()
    }

    private var sensorsRegistered = false

    /** 进入记录态时才注册传感器 */
    private fun registerSensors() {
        if (sensorsRegistered) return
        sensorsRegistered = true
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /** 反注册 */
    private fun unregisterSensors() {
        if (!sensorsRegistered) return
        sensorsRegistered = false
        runCatching { sensorManager.unregisterListener(this) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_PAUSE -> changeStatus(FitActivityStatus.ManualPaused, FitTimerEventType.ManualPause)
            ACTION_RESUME -> {
                lowSpeedSince = null
                changeStatus(FitActivityStatus.Recording, FitTimerEventType.Resume)
            }
            ACTION_STOP -> finishRecording()
            null -> scope.launch {
                if (repository.activeActivity() != null) {
                    startRecording(Intent())
                } else {
                    withContext(Dispatchers.Main) {
                        ServiceCompat.stopForeground(this@FitRecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startRecording(intent: Intent) {
        startForeground(NOTIFICATION_ID, notification("正在准备定位…", false))
        registerSensors()
        scope.launch {
            val existing = repository.activeActivity()
            val activity = existing ?: repository.start(
                "",
                intent.getStringExtra(EXTRA_NAME).orEmpty(),
                intent.getBooleanExtra(EXTRA_AUTO_PAUSE, true)
            )
            if (activityId != activity.id) {
                // 换了一条记录：上一条的"上一个样本 / 暂停断段"不能带过来。
                lastStoredElapsedNanos = null
                segmentBreakPending = false
            }
            activityId = activity.id
            ble.connectConfigured()
            startSensorPublisher()
            withContext(Dispatchers.Main) { requestLocations() }
            updateNotification(activity)
        }
    }

    private fun changeStatus(status: FitActivityStatus, event: FitTimerEventType) {
        scope.launch {
            val id = activityId ?: repository.activeActivity()?.id
            if (id == null) {
                withContext(Dispatchers.Main) {
                    ServiceCompat.stopForeground(this@FitRecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return@launch
            }
            activityId = id
            if (status != FitActivityStatus.Recording) segmentBreakPending = true
            repository.setStatus(id, status, event)
            repository.activity(id)?.let(::updateNotification)
        }
    }

    private fun finishRecording() {
        scope.launch {
            val id = activityId ?: repository.activeActivity()?.id
            if (id != null) repository.finish(id)
            stopLocations()
            unregisterSensors()
            sensorPublisherJob?.cancel()
            ServiceCompat.stopForeground(this@FitRecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onLocationChanged(location: Location) {
        val id = activityId ?: return
        val provider = location.provider.orEmpty()
        val isGps = provider == LocationManager.GPS_PROVIDER
        // GPS can need several minutes to obtain a precise fix indoors or after a cold
        // start. A usable network fix is still better than reporting GPS as permanently
        // unavailable, and keeps route recording available while GPS converges.
        val isNetwork = provider == LocationManager.NETWORK_PROVIDER
        val atNanos = location.elapsedRealtimeNanos.takeIf { it > 0L } ?: SystemClock.elapsedRealtimeNanos()
        if (isGps) lastGpsElapsedMs = atNanos / 1_000_000L
        if (!FitLocationPolicy.shouldUseFix(isGps, atNanos / 1_000_000L, lastGpsElapsedMs)) return
        val isUsable = (isGps && location.accuracy <= 50f) || (isNetwork && location.accuracy <= 80f)
        if (!isUsable) {
            if (isGps || isNetwork) publishSignal(location.accuracy, provider)
            return
        }
        lastUsableGpsElapsed = SystemClock.elapsedRealtime()
        publishSignal(location.accuracy, provider)
        scope.launch {
            val activity = repository.activity(id) ?: return@launch
            val speedKmh = location.speed * 3.6
            val sensorMoving = SystemClock.elapsedRealtime() - lastSensorMovementElapsed <= 2_000L
            when (activity.status) {
                FitActivityStatus.Recording -> {
                    if (activity.autoPauseEnabled && speedKmh < 2.0 && location.accuracy <= 20f) {
                        val since = lowSpeedSince ?: location.time.also { lowSpeedSince = it }
                        if (location.time - since >= 5_000) {
                            pausedSpeed = speedKmh
                            segmentBreakPending = true
                            repository.setStatus(id, FitActivityStatus.AutoPaused, FitTimerEventType.AutoPause, location.time)
                            repository.activity(id)?.let(::updateNotification)
                            return@launch
                        }
                    } else lowSpeedSince = null
                    // Indoor multipath commonly reports a moving GPS point while the handset is physically still.
                    // Keep monitoring it, but do not persist that drift as a route point.
                    if (!(location.accuracy > 8f && !sensorMoving)) {
                        val sensors = ble.snapshot(location.time)
                        storeLocation(id, location, atNanos, sensors)
                    }
                }
                FitActivityStatus.AutoPaused -> {
                    if (location.accuracy <= 20f && sensorMoving && kotlin.math.abs(speedKmh - pausedSpeed) >= 0.5) {
                        lowSpeedSince = null
                        repository.setStatus(id, FitActivityStatus.Recording, FitTimerEventType.Resume, location.time)
                        val sensors = ble.snapshot(location.time)
                        storeLocation(id, location, atNanos, sensors)
                    }
                }
                else -> Unit
            }
            repository.activity(id)?.let(::updateNotification)
        }
    }

    private suspend fun storeLocation(id: String, location: Location, atNanos: Long, sensors: BleSnapshot) {
        val stored = repository.addLocation(
            id, location, sensors.heartRate, sensors.cadence, sensors.power, motionMagnitude,
            elapsedSinceLastSeconds = lastStoredElapsedNanos?.let { (atNanos - it) / 1e9 },
            startNewSegment = segmentBreakPending
        )
        if (stored) {
            lastStoredElapsedNanos = atNanos
            segmentBreakPending = false
        }
    }

    private fun updateNotification(activity: FitActivityEntity) {
        val state = when (activity.status) {
            FitActivityStatus.Recording -> "记录中"
            FitActivityStatus.ManualPaused -> "手动暂停"
            FitActivityStatus.AutoPaused -> "自动暂停"
            else -> activity.status.name
        }
        val text = "$state · ${"%.2f".format(activity.distanceMeters / 1000.0)} km"
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, activity.status != FitActivityStatus.Recording))
    }

    private fun notification(text: String, paused: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val toggleAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        val toggle = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(toggleAction), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, javaClass).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("车库 · FIT 轨迹记录")
            .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, if (paused) "继续" else "暂停", toggle).addAction(0, "结束", stop).build()
    }

    private fun createChannel() {
        // NotificationChannel 是 API 26 才有的；minSdk 23 下必须门控，
        // 否则 Android 6/7 上启动记录服务即 NoSuchMethodError。
        // 低版本没有渠道概念，用 build() 里不设 channelId 的降级路径即可。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "FIT 轨迹记录", NotificationManager.IMPORTANCE_LOW))
    }

    private fun requestLocations() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            publishRuntimeFailure("缺少精确定位权限")
            return
        }
        val gps = runCatching {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, this, Looper.getMainLooper())
        }
        val network = runCatching {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1_000L, 0f, this, Looper.getMainLooper())
        }
        if (gps.isFailure && network.isFailure) {
            publishRuntimeFailure("无法启动定位服务")
        }
    }
    private fun publishRuntimeFailure(message: String) {
        getSharedPreferences("fit_runtime", 0).edit()
            .putInt("gps_level", 0)
            .putString("mode", message)
            .putFloat("accuracy", -1f)
            .apply()
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(message, false))
    }
    private fun stopLocations() { runCatching { locationManager.removeUpdates(this) } }

    /** BLE data arrives independently from GPS */
    private fun startSensorPublisher() {
        if (sensorPublisherJob?.isActive == true) return
        sensorPublisherJob = scope.launch {
            while (isActive) {
                val sensors = ble.snapshot()
                getSharedPreferences("fit_runtime", 0).edit()
                    .putInt("heart_rate", sensors.heartRate ?: -1)
                    .putInt("cadence", sensors.cadence ?: -1)
                    .putInt("power", sensors.power ?: -1)
                    .putInt("connected_sensors", sensors.connected)
                    .apply()
                delay(500)
            }
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onDestroy() {
        getSharedPreferences("fit_runtime", 0).edit().putInt("gps_level", 0).putString("mode", "未记录").apply()
        sensorPublisherJob?.cancel()
        // 用 unregisterSensors()（幂等 + 维护标志位），别直接调 sensorManager.unregisterListener
        stopLocations(); unregisterSensors(); ble.close(); scope.cancel(); super.onDestroy()
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            motionMagnitude = kotlin.math.sqrt(event.values.sumOf { (it * it).toDouble() }).toFloat()
            if (kotlin.math.abs((motionMagnitude ?: 9.81f) - 9.81f) > 0.20f) lastSensorMovementElapsed = SystemClock.elapsedRealtime()
        }
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            gyroMagnitude = kotlin.math.sqrt(event.values.sumOf { (it * it).toDouble() }).toFloat()
            if (gyroMagnitude > 0.08f) lastSensorMovementElapsed = SystemClock.elapsedRealtime()
        }
        if (activityId != null && SystemClock.elapsedRealtime() - lastUsableGpsElapsed > 5_000L) {
            getSharedPreferences("fit_runtime", 0).edit().putInt("gps_level", 0).putString("mode", "手机传感器辅助").putFloat("accuracy", -1f).apply()
        }
    }
    private fun publishSignal(accuracy: Float, provider: String) {
        val rawLevel = when { accuracy <= 8f -> 3; accuracy <= 20f -> 2; accuracy <= 50f -> 1; else -> 0 }
        val level = if (provider == LocationManager.NETWORK_PROVIDER) rawLevel.coerceAtMost(2) else rawLevel
        val mode = when {
            level == 0 -> "定位精度不足"
            provider == LocationManager.NETWORK_PROVIDER -> "网络定位"
            else -> "GPS 定位"
        }
        getSharedPreferences("fit_runtime", 0).edit().putInt("gps_level", level).putString("mode", mode).putFloat("accuracy", accuracy).apply()
    }

    private fun publishSignalLegacy(accuracy: Float) {
        val level = when { accuracy <= 8f -> 3; accuracy <= 20f -> 2; accuracy <= 50f -> 1; else -> 0 }
        val mode = if (level == 0) "手机传感器辅助" else "GPS 定位"
        getSharedPreferences("fit_runtime", 0).edit().putInt("gps_level", level).putString("mode", mode).putFloat("accuracy", accuracy).apply()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
