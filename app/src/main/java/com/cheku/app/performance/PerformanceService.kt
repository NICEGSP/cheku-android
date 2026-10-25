package com.cheku.app.performance

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cheku.app.MainActivity
import com.cheku.app.data.AppDatabase
import com.cheku.app.data.PerformanceJerkEntity
import com.cheku.app.data.PerformanceQuality
import com.cheku.app.data.PerformanceSampleEntity
import com.cheku.app.data.PerformanceSessionEntity
import com.cheku.app.data.PerformanceStatus
import com.cheku.app.data.PerformanceTestType
import com.cheku.app.speed.SpeedEstimator
import java.util.UUID
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class PerformanceService : Service(), LocationListener, SensorEventListener {
    companion object {
        const val START = "com.cheku.performance.START"
        const val STOP = "com.cheku.performance.STOP"
        const val MARK = "com.cheku.performance.MARK"
        const val TYPE = "type"
        const val VEHICLE = "vehicle"
        const val NAME = "name"
        const val CHANNEL = "performance"
        const val NOTIFICATION = 7401

        /** 取定位点的速度精度 */
        fun speedAccuracyOrNull(location: Location): Double? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
            if (!location.hasSpeedAccuracy()) return null
            return location.speedAccuracyMetersPerSecond.toDouble()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private lateinit var repo: PerformanceRepository
    private lateinit var sensors: SensorManager
    private lateinit var locations: LocationManager
    private var sessionId: String? = null
    private var processor: PerformanceSessionProcessor? = null
    private var combinedProcessor: CombinedPerformanceProcessor? = null
    private var rotation = FloatArray(9)
    private var hasRotation = false
    private var bearingDegrees: Float? = null
    private var longitudinal = 0f
    private var lateral = 0f
    private var vertical = 0f
    private var gyro = 0f
    private var jerk = 0f
    private var lastAccel = 0f
    private var lastSensorNanos = 0L
    private var lastSavedNanos = 0L
    private var lastJerkAt = 0L
    private var fusedSpeed = 0.0
    private var distance = 0.0
    private var lastLocation: Location? = null
    private val qualityFrames = ArrayDeque<PerformanceFrame>()
    @Volatile private var relativeNoiseDb: Float? = null
    private var noiseSum = 0.0
    private var noiseCount = 0
    private var noiseMax: Double? = null
    private var accelerationAbsSum = 0.0
    private var accelerationSampleCount = 0L
    private var audio: AudioRecord? = null
    private var audioJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repo = PerformanceRepository(AppDatabase.get(this).performanceDao())
        sensors = getSystemService(SensorManager::class.java)
        locations = getSystemService(LocationManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> start(intent)
            STOP -> finish(interrupted = false)
            MARK -> manualMark()
            null -> finish(interrupted = true)
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        startForegroundCompat(notification("正在检查传感器与 GNSS"))
        scope.launch {
            repo.interruptActive()
            val type = runCatching {
                PerformanceTestType.valueOf(intent.getStringExtra(TYPE).orEmpty())
            }.getOrDefault(PerformanceTestType.Comfort)
            val session = repo.start(
                type,
                intent.getStringExtra(VEHICLE),
                intent.getStringExtra(NAME).orEmpty().ifBlank { "性能测试" }
            )
            sessionId = session.id
            processor = type.takeUnless { it == PerformanceTestType.Combined }?.let(::PerformanceSessionProcessor)
            combinedProcessor = if (type == PerformanceTestType.Combined) CombinedPerformanceProcessor() else null
            withContext(Dispatchers.Main) {
                registerSensors()
                requestLocations()
            }
            startAudio()
            updateNotification(session)
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (Build.VERSION.SDK_INT >= 30 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION, notification, types)
        } else {
            startForeground(NOTIFICATION, notification)
        }
    }

    private fun registerSensors() {
        listOf(Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_ROTATION_VECTOR).forEach { type ->
            sensors.getDefaultSensor(type)?.let {
                sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    private fun requestLocations() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            finish(interrupted = true, reason = "缺少精确位置权限")
            return
        }
        runCatching {
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100L, 0f, this, Looper.getMainLooper())
        }.onFailure {
            finish(interrupted = true, reason = "无法启动 GNSS：${it.message.orEmpty()}")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                hasRotation = true
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyro = sqrt(event.values.sumOf { (it * it).toDouble() }).toFloat()
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val projected = bearingDegrees?.takeIf { hasRotation }?.let {
                    VehicleAxisProjector.project(event.values, rotation, it)
                }
                if (projected != null) {
                    longitudinal = projected.longitudinalMps2
                    lateral = projected.lateralMps2
                    vertical = projected.verticalMps2
                } else {
                    // Before GNSS provides a reliable heading, keep using the fixed phone mount axes.
                    longitudinal = -event.values[1]
                    lateral = event.values[0]
                    vertical = event.values[2]
                }
                val dt = ((event.timestamp - lastSensorNanos) / 1e9).toFloat()
                if (dt in 0.005f..0.5f) jerk = (longitudinal - lastAccel) / dt
                lastAccel = longitudinal
                lastSensorNanos = event.timestamp
                if (abs(jerk) > 4f && abs(lateral) < 3f && abs(vertical) < 4f &&
                    event.timestamp - lastJerkAt > 700_000_000L
                ) {
                    lastJerkAt = event.timestamp
                    automaticJerk()
                }
                saveSensor(event.timestamp)
            }
        }
    }

    private fun saveSensor(nanos: Long) {
        val id = sessionId ?: return
        if (nanos - lastSavedNanos < 50_000_000L) return
        lastSavedNanos = nanos
        val speedSnapshot = fusedSpeed
        val distanceSnapshot = distance
        val longitudinalSnapshot = longitudinal
        val lateralSnapshot = lateral
        val verticalSnapshot = vertical
        val jerkSnapshot = jerk
        val noiseSnapshot = relativeNoiseDb
        scope.launch {
            repo.addSample(
                PerformanceSampleEntity(
                    sessionId = id,
                    timestampNanos = nanos,
                    wallTime = System.currentTimeMillis(),
                    gpsSpeedMps = null,
                    fusedSpeedMps = speedSnapshot,
                    distanceMeters = distanceSnapshot,
                    latitude = null,
                    longitude = null,
                    gpsAccuracyMeters = null,
                    altitudeMeters = null,
                    longitudinalAccelerationMps2 = longitudinalSnapshot,
                    lateralAccelerationMps2 = lateralSnapshot,
                    verticalAccelerationMps2 = verticalSnapshot,
                    jerkMps3 = jerkSnapshot,
                    noiseDbA = noiseSnapshot
                )
            )
        }
    }

    override fun onLocationChanged(location: Location) {
        if (location.provider != LocationManager.GPS_PROVIDER) return
        val id = sessionId ?: return
        val longitudinalSnapshot = longitudinal
        val lateralSnapshot = lateral
        val verticalSnapshot = vertical
        val jerkSnapshot = jerk
        val noiseSnapshot = relativeNoiseDb
        scope.launch {
            var session = repo.session(id) ?: return@launch
            val previousLocation = lastLocation
            if (previousLocation != null && location.elapsedRealtimeNanos <= previousLocation.elapsedRealtimeNanos) return@launch
            if (location.hasBearing() && location.speed >= 2f) bearingDegrees = location.bearing
            val dt = previousLocation?.let {
                (location.elapsedRealtimeNanos - it.elapsedRealtimeNanos) / 1e9
            }?.coerceIn(0.001, 2.0) ?: 0.0
            val gpsSpeed = location.speed.toDouble().coerceAtLeast(0.0)
            fusedSpeed = SpeedEstimator.update(
                rawSpeedMps = gpsSpeed,
                previousSpeedMps = previousLocation?.let { fusedSpeed },
                dtSeconds = dt,
                speedAccuracyMps = speedAccuracyOrNull(location),
                locationAccuracyM = location.accuracy
            ).speedMps
            if (previousLocation != null) {
                distance += (previousLocation.speed.toDouble().coerceAtLeast(0.0) + gpsSpeed) * 0.5 * dt
            }
            val frame = PerformanceFrame(
                timestampNanos = location.elapsedRealtimeNanos,
                wallTimeMillis = System.currentTimeMillis(),
                speedMps = fusedSpeed,
                distanceMeters = distance,
                accuracyMeters = location.accuracy,
                longitudinalAccelerationMps2 = longitudinalSnapshot
            )
            accelerationAbsSum += abs(longitudinalSnapshot.toDouble())
            accelerationSampleCount++
            qualityFrames.addLast(frame)
            while (qualityFrames.size > 5000) qualityFrames.removeFirst()
            if (session.testType == PerformanceTestType.Combined) {
                val combinedResults = combinedProcessor?.accept(frame).orEmpty()
                combinedResults.forEach { split ->
                    repo.split(
                        id,
                        split.label,
                        when {
                            split.label.contains("100") -> 100.0
                            split.label.contains("50") -> 50.0
                            else -> 0.0
                        },
                        split.result.elapsedSeconds,
                        split.result.distanceMeters
                    )
                }
                val completed = combinedProcessor?.isComplete() == true
                val combinedStatus = when (combinedProcessor?.stage()) {
                    CombinedStage.Preparing, null -> PerformanceStatus.Preparing
                    CombinedStage.Accelerating -> PerformanceStatus.Accelerating
                    CombinedStage.BrakingArmed -> PerformanceStatus.BrakingArmed
                    CombinedStage.Braking -> PerformanceStatus.Braking
                    CombinedStage.Complete -> PerformanceStatus.Completed
                }
                session = session.copy(
                    status = if (completed) PerformanceStatus.Completed else combinedStatus,
                    maxAccelerationMps2 = max(abs(longitudinalSnapshot.toDouble()), session.maxAccelerationMps2),
                    averageAccelerationMps2 = accelerationAbsSum / accelerationSampleCount.coerceAtLeast(1L),
                    updatedAt = System.currentTimeMillis()
                )
                if (completed) {
                    val report = PerformanceQualityEvaluator.evaluate(qualityFrames)
                    val summary = combinedProcessor?.summaryResult()
                    repo.update(
                        session.copy(
                            endedAt = System.currentTimeMillis(),
                            resultSeconds = summary?.elapsedSeconds,
                            resultDistanceMeters = summary?.distanceMeters,
                            quality = when {
                                report.valid -> PerformanceQuality.Valid
                                report.lowAccuracy -> PerformanceQuality.LowAccuracy
                                else -> PerformanceQuality.Invalid
                            },
                            invalidReason = report.reason,
                            averageNoiseDbA = noiseSum.takeIf { noiseCount > 0 }?.div(noiseCount),
                            maxNoiseDbA = noiseMax,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    stopEverything()
                    return@launch
                }
                repo.update(session)
                lastLocation = location
                updateNotification(session)
                return@launch
            }
            val step = processor?.accept(frame) ?: return@launch
            val nextStatus = when {
                step.result != null -> PerformanceStatus.Completed
                step.state.running -> PerformanceStatus.Running
                step.state.armed -> PerformanceStatus.Armed
                else -> PerformanceStatus.Preparing
            }
            session = session.copy(
                status = nextStatus,
                maxAccelerationMps2 = max(abs(longitudinalSnapshot.toDouble()), session.maxAccelerationMps2),
                updatedAt = System.currentTimeMillis()
            )
            val result = step.result
            if (result != null) {
                val criticalFrames = qualityFrames.filter {
                    it.timestampNanos in result.start.timestampNanos..result.end.timestampNanos
                }
                val report = PerformanceQualityEvaluator.evaluate(criticalFrames)
                val quality = when {
                    report.valid -> PerformanceQuality.Valid
                    report.lowAccuracy -> PerformanceQuality.LowAccuracy
                    else -> PerformanceQuality.Invalid
                }
                repo.split(
                    id,
                    splitLabel(session.testType),
                    PerformanceEngine.targetKmh(session.testType),
                    result.elapsedSeconds,
                    result.distanceMeters
                )
                repo.update(
                    session.copy(
                        endedAt = System.currentTimeMillis(),
                        resultSeconds = result.elapsedSeconds,
                        resultDistanceMeters = result.distanceMeters,
                        averageAccelerationMps2 = criticalFrames.map { abs(it.longitudinalAccelerationMps2.toDouble()) }
                            .average().takeIf { !it.isNaN() } ?: 0.0,
                        averageNoiseDbA = noiseSum.takeIf { noiseCount > 0 }?.div(noiseCount),
                        maxNoiseDbA = noiseMax,
                        quality = quality,
                        invalidReason = report.reason,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                stopEverything()
                return@launch
            }
            repo.update(session)
            repo.addSample(
                PerformanceSampleEntity(
                    sessionId = id,
                    timestampNanos = location.elapsedRealtimeNanos,
                    wallTime = frame.wallTimeMillis,
                    gpsSpeedMps = gpsSpeed,
                    fusedSpeedMps = fusedSpeed,
                    distanceMeters = distance,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    gpsAccuracyMeters = location.accuracy,
                    altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
                    longitudinalAccelerationMps2 = longitudinalSnapshot,
                    lateralAccelerationMps2 = lateralSnapshot,
                    verticalAccelerationMps2 = verticalSnapshot,
                    jerkMps3 = jerkSnapshot,
                    noiseDbA = noiseSnapshot
                )
            )
            lastLocation = location
            updateNotification(session)
        }
    }

    private fun splitLabel(type: PerformanceTestType): String =
        if (PerformanceEngine.isBraking(type)) "${PerformanceEngine.targetKmh(type).toInt()}-0"
        else "0-${PerformanceEngine.targetKmh(type).toInt()}"

    private fun automaticJerk() {
        val id = sessionId ?: return
        scope.launch {
            repo.addJerk(
                PerformanceJerkEntity(
                    UUID.randomUUID().toString(), id, System.currentTimeMillis(),
                    fusedSpeed * 3.6, longitudinal, jerk, 200, PerformanceEngine.jerkSeverity(jerk)
                )
            )
        }
    }

    private fun manualMark() {
        val id = sessionId ?: return
        scope.launch {
            repo.addJerk(
                PerformanceJerkEntity(
                    UUID.randomUUID().toString(), id, System.currentTimeMillis(),
                    fusedSpeed * 3.6, longitudinal, jerk, 0, PerformanceEngine.jerkSeverity(jerk), true
                )
            )
        }
    }

    @Suppress("MissingPermission")
    private fun startAudio() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val rate = 48_000
        val min = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        val source = MediaRecorder.AudioSource.UNPROCESSED
        audio = runCatching {
            AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2)
        }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        val recorder = audio ?: return
        recorder.startRecording()
        audioJob = scope.launch {
            val buffer = ShortArray(min)
            while (isActive) {
                val count = recorder.read(buffer, 0, buffer.size)
                if (count > 0) {
                    val rms = sqrt(buffer.take(count).sumOf { it.toDouble() * it } / count).coerceAtLeast(1.0)
                    val relative = (20 * log10(rms / 32768.0) + 100.0).toFloat().coerceIn(0f, 120f)
                    relativeNoiseDb = relative
                    noiseSum += relative
                    noiseCount++
                    noiseMax = max(noiseMax ?: relative.toDouble(), relative.toDouble())
                    sessionId?.let { repo.addNoise(it, System.currentTimeMillis(), relative) }
                }
            }
        }
    }

    private fun finish(interrupted: Boolean, reason: String = "") {
        val id = sessionId
        scope.launch {
            val session = id?.let { repo.session(it) } ?: repo.active()
            if (session != null) {
                val status = if (interrupted || session.testType != PerformanceTestType.Comfort) {
                    PerformanceStatus.Interrupted
                } else PerformanceStatus.Completed
                repo.update(
                    session.copy(
                        status = status,
                        endedAt = System.currentTimeMillis(),
                        averageNoiseDbA = noiseSum.takeIf { noiseCount > 0 }?.div(noiseCount),
                        maxNoiseDbA = noiseMax,
                        quality = if (interrupted) PerformanceQuality.Invalid else session.quality,
                        invalidReason = reason.ifBlank {
                            if (interrupted) "测试被中断，请重新校准后开始" else session.invalidReason
                        },
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            stopEverything()
        }
    }

    private fun stopEverything() {
        runCatching { locations.removeUpdates(this) }
        sensors.unregisterListener(this)
        audioJob?.cancel()
        runCatching { audio?.stop() }
        audio?.release()
        audio = null
        sessionId = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        // NotificationChannel 是 API 26 才有的；minSdk 23 下必须门控（同 FitRecordingService）。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "车辆性能测试", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, javaClass).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("车辆性能测试")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "结束", stop)
            .build()
    }

    private fun updateNotification(session: PerformanceSessionEntity) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION,
            notification("${session.status.label()} · ${"%.1f".format(fusedSpeed * 3.6)} km/h")
        )
    }

    private fun PerformanceStatus.label() = when (this) {
        PerformanceStatus.Preparing -> "正在校准"
        PerformanceStatus.Armed -> "已待发"
        PerformanceStatus.Running -> "测试中"
        PerformanceStatus.Accelerating -> "加速阶段"
        PerformanceStatus.BrakingArmed -> "等待制动"
        PerformanceStatus.Braking -> "制动阶段"
        PerformanceStatus.Completed -> "已完成"
        PerformanceStatus.Interrupted -> "已中断"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        runCatching { stopEverything() }
        scope.cancel()
        super.onDestroy()
    }
}
