package com.cheku.app.carmode

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

private var rawFrame = 0

internal object SpectrumSourceFactory {
    const val TAG = "SpectrumSource"

    private val activeSourceState = androidx.compose.runtime.mutableStateOf<String?>(null)

    /** 当前真正在供数的通路名 */
    val activeSource: String? get() = activeSourceState.value

    private fun wrap(label: String, delegate: AutoCloseable): AutoCloseable {
        activeSourceState.value = label
        return object : AutoCloseable {
            override fun close() {
                runCatching { delegate.close() }
                if (activeSourceState.value == label) activeSourceState.value = null
            }
        }
    }

    /** 打开数据源 */
    fun open(
        samplingRate: Int = 44_100,
        frameSize: Int = 1_024,
        bands: Int = 96,
        smoothing: Float = 0.7f,
        onSpectrum: (FloatArray) -> Unit
    ): AutoCloseable? {
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            ShizukuCaptureBridge.open(onSpectrum)?.let {
                Log.i(TAG, "使用 Shizuku(AudioMix LOOP_BACK|RENDER) 通路")
                return wrap("Shizuku 免授权", it)
            }
        }

        PlaybackCaptureBridge.open(onSpectrum)?.let {
            Log.i(TAG, "使用 AudioPlaybackCapture 通路")
            return wrap("系统音频（已授权）", it)
        }

        MicrophoneSource.open(samplingRate, frameSize, bands, smoothing, onSpectrum)?.let {
            Log.i(TAG, "使用 Microphone 通路（${it.sourceName}）")
            return wrap("麦克风（免授权）", it)
        }

        if (!visualizerCooldownHit()) {
            VisualizerSource.open(frameSize, bands, smoothing, onSpectrum)?.let {
                clearCooldown(VISUALIZER)
                return wrap("输出混音", it)
            }
            markCooldown(VISUALIZER)
            Log.w(TAG, "Visualizer 通路不可用，降级到 REMOTE_SUBMIX")
        }
        SubmixSource.open(samplingRate, frameSize, bands, smoothing, onSpectrum)?.let {
            return wrap("输出混音", it)
        }
        activeSourceState.value = null
        return null
    }

    /** 记住某条通路刚刚失败过的时间戳 */
    private fun markCooldown(tag: String) {
        failedAt[tag] = System.currentTimeMillis()
    }

    private fun clearCooldown(tag: String) {
        failedAt.remove(tag)
    }

    private fun visualizerCooldownHit(): Boolean {
        val t = failedAt[VISUALIZER] ?: return false
        return System.currentTimeMillis() - t < COOLDOWN_MS
    }

    private const val VISUALIZER = "visualizer"
    private const val COOLDOWN_MS = 3_000L
    private val failedAt = HashMap<String, Long>()
}

internal class SpectrumAgc {
    private var ceilingDb = -20f

    fun apply(db: FloatArray) {
        if (db.isEmpty()) return

        var framePeak = -120f
        for (v in db) if (v > framePeak) framePeak = v

        ceilingDb = if (framePeak > ceilingDb) framePeak else ceilingDb - 0.25f
        ceilingDb = ceilingDb.coerceIn(-60f, 0f)

        val dbCeil = minOf(ceilingDb + 5f, 0f)
        val dbFloor = dbCeil - 40f
        val span = (dbCeil - dbFloor).coerceAtLeast(1f)

        for (i in db.indices) {
            db[i] = ((db[i] - dbFloor) / span).coerceIn(0f, 1f)
        }
    }
}

internal class ShizukuCaptureBridge private constructor(
    private val listener: (FloatArray) -> Unit
) : AutoCloseable {
    override fun close() {
        ShizukuCapture.removeListener(listener)
    }

    companion object {
        fun open(onSpectrum: (FloatArray) -> Unit): ShizukuCaptureBridge? {
            if (!ShizukuCapture.isReady) return null
            ShizukuCapture.addListener(onSpectrum)
            return ShizukuCaptureBridge(onSpectrum)
        }
    }
}

internal class PlaybackCaptureBridge private constructor(
    private val listener: (FloatArray) -> Unit
) : AutoCloseable {
    override fun close() {
        PlaybackCapture.removeListener(listener)
    }

    companion object {
        fun open(onSpectrum: (FloatArray) -> Unit): PlaybackCaptureBridge? {
            if (PlaybackCapture.state != PlaybackCapture.State.Active) return null
            PlaybackCapture.addListener(onSpectrum)
            return PlaybackCaptureBridge(onSpectrum)
        }
    }
}

internal class VisualizerSource : AutoCloseable {
    private val visualizer: android.media.audiofx.Visualizer

    private constructor(v: android.media.audiofx.Visualizer) {
        visualizer = v
    }

    companion object {
        fun open(
            frameSize: Int,
            bands: Int,
            smoothing: Float,
            onSpectrum: (FloatArray) -> Unit
        ): VisualizerSource? {
            return runCatching {
                val captureSize = runCatching {
                    android.media.audiofx.Visualizer.getCaptureSizeRange()[0]
                }.getOrDefault(256)
                val rate = (android.media.audiofx.Visualizer.getMaxCaptureRate() *
                    (0.25f + smoothing * 0.75f)).toInt().coerceAtLeast(16)

                val out = FloatArray(bands)
                // 自适应增益：与 SpectrumFft 同一套（切通路时观感不跳变）。
                val agc = SpectrumAgc()
                val v = android.media.audiofx.Visualizer(0).apply {
                    this.captureSize = captureSize
                    setDataCaptureListener(
                        object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                visualizer: android.media.audiofx.Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int
                            ) = Unit

                            override fun onFftDataCapture(
                                visualizer: android.media.audiofx.Visualizer?,
                                fftData: ByteArray?,
                                samplingRate: Int
                            ) {
                                val d = fftData ?: return
                                val half = d.size / 2
                                val mags = FloatArray(half + 1)
                                mags[0] = kotlin.math.abs(d[0].toFloat())     // DC
                                mags[half] = kotlin.math.abs(d[1].toFloat())  // Nyquist
                                for (k in 1 until half) {
                                    val i = k * 2
                                    mags[k] = kotlin.math.hypot(d[i].toFloat(), d[i + 1].toFloat())
                                }
                                rawFrame++
                                if (rawFrame % 20 == 0) {
                                    var mn = Float.MAX_VALUE; var mx = 0f; var sum = 0f
                                    for (m in mags) { if (m < mn) mn = m; if (m > mx) mx = m; sum += m }
                                    Log.i(
                                        SpectrumSourceFactory.TAG,
                                        "VisualizerRaw#f=$rawFrame min=${"%.1f".format(mn)} " +
                                            "max=${"%.1f".format(mx)} avg=${"%.1f".format(sum / mags.size)}"
                                    )
                                }
                                val binHz = samplingRate / d.size.toFloat()
                                val ratio = 12_000f / 20f
                                for (i in 0 until bands) {
                                    val lo = 20f * Math.pow(ratio.toDouble(), i / bands.toDouble()).toFloat()
                                    val hi = 20f * Math.pow(ratio.toDouble(), (i + 1) / bands.toDouble()).toFloat()
                                    val a = (lo / binHz).toInt().coerceIn(0, half)
                                    val b = (hi / binHz).toInt().coerceIn(a + 1, half + 1)
                                    var sum = 0f
                                    var cnt = 0
                                    for (k in a until b) { sum += mags[k]; cnt++ }
                                    val mean = if (cnt > 0) sum / cnt else 0f
                                    // Visualizer 的 byte 本身已是「8-bit 归一化幅度」，
                                    // 满量程 ≈ 127；折算成 dB 后再套同一套映射。
                                    val norm = (mean / 127f).coerceIn(1e-7f, 1f)
                                    // 写原始 dB，交给 [SpectrumAgc] 统一做 dB 域自动量程
                                    // （与 SpectrumFft 完全同一套处理链）。
                                    out[i] = 20f * kotlin.math.log10(norm)
                                }
                                agc.apply(out)
                                onSpectrum(out.copyOf())
                            }
                        },
                        rate, false, true
                    )
                    enabled = true
                }
                Log.i(SpectrumSourceFactory.TAG, "Visualizer 通路已建立")
                VisualizerSource(v)
            }.getOrNull()
        }
    }

    override fun close() {
        runCatching { visualizer.enabled = false }
        runCatching { visualizer.release() }
    }
}

@SuppressLint("MissingPermission")
internal class SubmixSource private constructor(
    private val record: AudioRecord,
    private val thread: Thread,
    private val stop: java.util.concurrent.atomic.AtomicBoolean
) : AutoCloseable {
    override fun close() {
        stop.set(true)
        runCatching { record.stop() }
        runCatching { record.release() }
        runCatching { thread.interrupt() }
    }

    companion object {
        @SuppressLint("MissingPermission")
        fun open(
            samplingRate: Int,
            frameSize: Int,
            bands: Int,
            smoothing: Float,
            onSpectrum: (FloatArray) -> Unit
        ): SubmixSource? {
            // FFT 长度必须是 2 的幂。
            if (frameSize <= 0 || (frameSize and (frameSize - 1)) != 0) return null
            return runCatching {
                val min = AudioRecord.getMinBufferSize(
                    samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                if (min <= 0) return null
                val rec = AudioRecord(
                    MediaRecorder.AudioSource.REMOTE_SUBMIX,
                    samplingRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, min * 4
                ).takeIf { it.state == AudioRecord.STATE_INITIALIZED } ?: return null

                val fft = SpectrumFft(frameSize)
                val smooth = FloatArray(bands)
                val shortBuf = ShortArray(frameSize)
                val cosT = FloatArray(frameSize / 2)
                val sinT = FloatArray(frameSize / 2)
                for (i in 0 until frameSize / 2) {
                    val ang = -2.0 * Math.PI * i / frameSize
                    cosT[i] = cos(ang).toFloat()
                    sinT[i] = kotlin.math.sin(ang).toFloat()
                }
                val s = smoothing.coerceIn(0f, 0.95f)
                val alpha = 1f - s
                val stop = java.util.concurrent.atomic.AtomicBoolean(false)

                val t = Thread {
                    runCatching { rec.startRecording() }
                    while (!stop.get()) {
                        val read = rec.read(shortBuf, 0, frameSize)
                        if (read < frameSize) continue
                        val frame = FloatArray(bands)
                        fft.magnitude(shortBuf, frame, cosT, sinT)
                        // 指数平滑：s 越接近 1，结果越接近上一帧（越平滑/越滞后）。
                        for (i in 0 until bands) smooth[i] = smooth[i] * s + frame[i] * alpha
                        onSpectrum(smooth.copyOf())
                    }
                    runCatching { rec.stop() }
                }
                t.isDaemon = true
                t.start()
                Log.i(
                    SpectrumSourceFactory.TAG,
                    "REMOTE_SUBMIX 通路已建立 sr=$samplingRate frame=$frameSize"
                )
                SubmixSource(rec, t, stop)
            }.getOrNull()
        }
    }
}

internal class MicrophoneSource private constructor(
    private val record: AudioRecord,
    private val thread: Thread,
    private val stop: java.util.concurrent.atomic.AtomicBoolean,
    val sourceUsed: Int,
    val sourceName: String
) : AutoCloseable {
    override fun close() {
        stop.set(true)
        runCatching { record.stop() }
        runCatching { record.release() }
        runCatching { thread.interrupt() }
    }

    companion object {
        private fun nameOf(source: Int): String = when (source) {
            MediaRecorder.AudioSource.VOICE_PERFORMANCE -> "VOICE_PERFORMANCE(10)"
            MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED(9)"
            MediaRecorder.AudioSource.MIC -> "MIC(1)"
            else -> "source($source)"
        }

        /** 依次尝试的音源 */
        private val CANDIDATES = intArrayOf(
            MediaRecorder.AudioSource.VOICE_PERFORMANCE,
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.MIC,
        )

        private const val PROBE_FRAMES = 20

        @SuppressLint("MissingPermission")
        fun open(
            samplingRate: Int,
            frameSize: Int,
            bands: Int,
            smoothing: Float,
            onSpectrum: (FloatArray) -> Unit
        ): MicrophoneSource? {
            // FFT 长度必须是 2 的幂。
            if (frameSize <= 0 || (frameSize and (frameSize - 1)) != 0) return null
            for (source in CANDIDATES) {
                val built = tryOpenSource(source, samplingRate, frameSize, bands, smoothing, onSpectrum)
                if (built != null) return built
            }
            Log.w(SpectrumSourceFactory.TAG, "麦克风通路：所有音源都不可用")
            return null
        }

        @SuppressLint("MissingPermission")
        private fun tryOpenSource(
            source: Int,
            samplingRate: Int,
            frameSize: Int,
            bands: Int,
            smoothing: Float,
            onSpectrum: (FloatArray) -> Unit
        ): MicrophoneSource? {
            val name = nameOf(source)
            val min = runCatching {
                AudioRecord.getMinBufferSize(
                    samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
            }.getOrDefault(-1)
            if (min <= 0) {
                Log.w(SpectrumSourceFactory.TAG, "麦克风通路 $name：minBufferSize=$min")
                return null
            }
            val rec = runCatching {
                AudioRecord(
                    source, samplingRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, min * 4
                )
            }.getOrNull() ?: run {
                Log.w(SpectrumSourceFactory.TAG, "麦克风通路 $name：构造失败")
                return null
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(SpectrumSourceFactory.TAG, "麦克风通路 $name：state=${rec.state} 未初始化")
                runCatching { rec.release() }
                return null
            }
            if (!runCatching { rec.startRecording() }.isSuccess ||
                rec.recordingState != AudioRecord.RECORDSTATE_RECORDING
            ) {
                Log.w(SpectrumSourceFactory.TAG, "麦克风通路 $name：startRecording 失败")
                runCatching { rec.release() }
                return null
            }

            val probeBuf = ShortArray(frameSize)
            var probePeak = 0
            var probed = 0
            while (probed < PROBE_FRAMES && probePeak == 0) {
                val n = runCatching { rec.read(probeBuf, 0, frameSize) }.getOrDefault(-1)
                if (n <= 0) break
                for (i in 0 until n) {
                    val v = kotlin.math.abs(probeBuf[i].toInt())
                    if (v > probePeak) probePeak = v
                }
                probed++
            }
            if (probePeak == 0) {
                Log.i(
                    SpectrumSourceFactory.TAG,
                    "麦克风通路 $name：已建立，但试读 $probed 帧全为 0（此刻无声音，属正常）"
                )
            } else {
                Log.i(
                    SpectrumSourceFactory.TAG,
                    "麦克风通路 $name：已建立，试读 $probed 帧 peak=$probePeak"
                )
            }

            val fft = SpectrumFft(frameSize)
            val out = FloatArray(bands)
            val smooth = FloatArray(bands)
            val shortBuf = ShortArray(frameSize)
            val cosT = FloatArray(frameSize / 2)
            val sinT = FloatArray(frameSize / 2)
            for (i in 0 until frameSize / 2) {
                val ang = -2.0 * Math.PI * i / frameSize
                cosT[i] = cos(ang).toFloat()
                sinT[i] = kotlin.math.sin(ang).toFloat()
            }
            val s = smoothing.coerceIn(0f, 0.95f)
            val alpha = 1f - s
            val stop = java.util.concurrent.atomic.AtomicBoolean(false)

            val t = Thread {
                while (!stop.get()) {
                    val read = runCatching { rec.read(shortBuf, 0, frameSize) }.getOrDefault(-1)
                    if (read < frameSize) continue
                    fft.magnitude(shortBuf, out, cosT, sinT)
                    for (i in 0 until bands) smooth[i] = smooth[i] * s + out[i] * alpha
                    onSpectrum(smooth.copyOf())
                }
                runCatching { rec.stop() }
            }
            t.isDaemon = true
            t.start()
            Log.i(SpectrumSourceFactory.TAG, "麦克风通路已启用：$name sr=$samplingRate frame=$frameSize")
            return MicrophoneSource(rec, t, stop, source, name)
        }
    }
}

/** 迭代式 radix-2 Cooley–Tukey FFT */
internal class SpectrumFft(private val n: Int) {
    private val agc = SpectrumAgc()

    init { require(n > 0 && (n and (n - 1)) == 0) { "FFT 长度必须是 2 的幂" } }
    private val re = FloatArray(n)
    private val im = FloatArray(n)

    /** Hann 窗系数 */
    private val win = FloatArray(n) {
        (0.5 - 0.5 * cos(2.0 * Math.PI * it / n)).toFloat()
    }

    private val rev = IntArray(n).also { table ->
        var j = 0
        for (i in 0 until n) {
            table[i] = j
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
        }
    }

    fun magnitude(input: ShortArray, out: FloatArray, cosT: FloatArray, sinT: FloatArray) {
        var mean = 0f
        for (s in input) mean += s
        mean /= input.size
        for (i in 0 until n) {
            val v = (input[rev[i]] - mean) / 32768f * win[i]
            re[i] = v
            im[i] = 0f
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var k = 0
            var i = 0
            while (i < n) {
                var j = i
                var tIdx = 0
                repeat(half) {
                    val wr = cosT[tIdx]
                    val wi = sinT[tIdx]
                    val a = i + it
                    val b = a + half
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                    j++
                    tIdx += step
                }
                i += len
                k++
            }
            len = len shl 1
        }
        val half = n / 2
        val binHz = 22_050f / half
        val ratio = 12_000f / 20f
        for (b in out.indices) {
            val lo = 20f * Math.pow(ratio.toDouble(), b / out.size.toDouble()).toFloat()
            val hi = 20f * Math.pow(ratio.toDouble(), (b + 1) / out.size.toDouble()).toFloat()
            val a = (lo / binHz).toInt().coerceIn(0, half - 1)
            val e = (hi / binHz).toInt().coerceIn(a + 1, half)
            var sum = 0f
            var cnt = 0
            for (i2 in a until e) {
                sum += sqrt(re[i2] * re[i2] + im[i2] * im[i2])
                cnt++
            }
            val peak = if (cnt > 0) sum / cnt else 0f
            val fullScale = (n / 4f).coerceAtLeast(1f)
            val norm = (peak / fullScale).coerceIn(1e-7f, 1f)
            val db = 20f * kotlin.math.log10(norm)
            out[b] = db
        }
        agc.apply(out)
    }
}
