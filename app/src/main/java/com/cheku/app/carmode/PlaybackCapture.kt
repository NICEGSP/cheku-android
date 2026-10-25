package com.cheku.app.carmode

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object PlaybackCapture {
    const val TAG = "PlaybackCapture"

    const val SAMPLE_RATE = 44_100
    const val FFT_SIZE = 1_024
    const val BANDS = 96

    /** 当前状态 */
    enum class State { Idle, Active, Failed }

    /** 当前状态 */
    var state by androidx.compose.runtime.mutableStateOf(State.Idle)
        private set

    val isReady: Boolean get() = state == State.Active

    /** 失败原因 */
    @Volatile
    var lastError: String? = null
        private set

    /** 最新一帧频段能量 */
    @Volatile
    private var latest: FloatArray? = null

    @Volatile
    var frameCount: Long = 0L
        private set

    fun latestFrame(): FloatArray? = latest

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(FloatArray) -> Unit>()

    fun addListener(l: (FloatArray) -> Unit) {
        listeners.addIfAbsent(l)
    }

    fun removeListener(l: (FloatArray) -> Unit) {
        listeners.remove(l)
    }

    fun publish(frame: FloatArray) {
        latest = frame
        frameCount++
        for (l in listeners) runCatching { l(frame) }
    }

    fun markActive() {
        state = State.Active
        lastError = null
        latest = null
        frameCount = 0
    }

    fun markFailed(reason: String) {
        Log.w(TAG, "捕获失败：$reason")
        state = State.Failed
        lastError = reason
        latest = null
    }

    fun reset() {
        state = State.Idle
        lastError = null
        latest = null
        frameCount = 0
    }

    fun start(context: Context, resultCode: Int, data: Intent) {
        val svc = Intent(context, AudioCaptureService::class.java).apply {
            putExtra(AudioCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(AudioCaptureService.EXTRA_RESULT_DATA, data)
        }
        runCatching { ContextCompat.startForegroundService(context, svc) }
            .onFailure { markFailed("前台服务启动失败：${it.message}") }
    }

    /** 停止捕获 */
    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, AudioCaptureService::class.java)) }
        reset()
    }
}

class AudioCaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "car_music_spectrum"
        private const val NOTIF_ID = 0x5EC7 // 随便一个不冲突的 id
    }

    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private val stopFlag = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (data == null) {
            PlaybackCapture.markFailed("授权数据缺失（data == null）")
            stopSelf()
            return START_NOT_STICKY
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        val token = runCatching { mpm?.getMediaProjection(resultCode, data) }.getOrNull()
        if (token == null) {
            PlaybackCapture.markFailed("getMediaProjection 返回 null（授权被拒或已过期）")
            stopSelf()
            return START_NOT_STICKY
        }
        projection = token
        token.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(PlaybackCapture.TAG, "MediaProjection 被系统/用户停止")
                    shutdown()
                }
            },
            null
        )

        val cfg = AudioPlaybackCaptureConfiguration.Builder(token)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(PlaybackCapture.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(
            PlaybackCapture.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = runCatching {
            AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(cfg)
                .setAudioFormat(format)
                .setBufferSizeInBytes(if (minBuf > 0) minBuf * 2 else PlaybackCapture.FFT_SIZE * 4)
                .build()
        }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            PlaybackCapture.markFailed("AudioRecord 初始化失败（state=${rec?.state}）")
            runCatching { rec?.release() }
            stopSelf()
            return START_NOT_STICKY
        }
        record = rec

        stopFlag.set(false)
        val fft = SpectrumFft(PlaybackCapture.FFT_SIZE)
        val bands = PlaybackCapture.BANDS
        val out = FloatArray(bands)
        val smooth = FloatArray(bands)
        val buf = ShortArray(PlaybackCapture.FFT_SIZE)
        val cosT = FloatArray(PlaybackCapture.FFT_SIZE / 2)
        val sinT = FloatArray(PlaybackCapture.FFT_SIZE / 2)
        for (i in 0 until PlaybackCapture.FFT_SIZE / 2) {
            val ang = -2.0 * Math.PI * i / PlaybackCapture.FFT_SIZE
            cosT[i] = cos(ang).toFloat()
            sinT[i] = sin(ang).toFloat()
        }

        runCatching { rec.startRecording() }
        worker = Thread {
            var logged = 0L
            while (!stopFlag.get()) {
                val read = rec.read(buf, 0, buf.size)
                if (read < buf.size) continue
                fft.magnitude(buf, out, cosT, sinT)
                for (i in 0 until bands) smooth[i] = smooth[i] * 0.25f + out[i] * 0.75f
                PlaybackCapture.publish(smooth.copyOf())
                if (++logged % 100 == 0L) {
                    var mx = 0f
                    for (v in smooth) if (v > mx) mx = v
                    Log.i(PlaybackCapture.TAG, "Capture#${PlaybackCapture.frameCount} peak=${"%.3f".format(mx)}")
                }
            }
            runCatching { rec.stop() }
        }.also { it.isDaemon = true; it.start() }

        PlaybackCapture.markActive()
        Log.i(PlaybackCapture.TAG, "AudioPlaybackCapture 通路已建立 sr=${PlaybackCapture.SAMPLE_RATE}")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        stopFlag.set(true)
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        runCatching { worker?.interrupt() }
        worker = null
        runCatching { projection?.stop() }
        projection = null
        PlaybackCapture.reset()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "音乐频谱", NotificationManager.IMPORTANCE_MIN
                ).apply { description = "读取正在播放的音乐以驱动频谱动画" }
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("音乐频谱运行中")
            .setContentText("正在读取正在播放的音乐（仅用于频谱动画）")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
