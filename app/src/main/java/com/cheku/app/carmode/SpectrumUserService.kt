package com.cheku.app.carmode

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.util.Log
import java.lang.reflect.InvocationTargetException

class SpectrumUserService : ISpectrumService.Stub {
    /** Shizuku v13+ 会优先调这个带 Context 的构造函数 */
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context?) : super()

    constructor() : super()

    private companion object {
        const val TAG = "ChekuSpectrumSvc"

        const val SAMPLE_RATE = 44_100
        const val FFT_SIZE = 1_024
        const val BANDS = 96

        const val RULE_MATCH_ATTRIBUTE_USAGE = 0x1

        const val ROUTE_FLAG_LOOP_BACK = 1

        const val ROUTE_FLAG_RENDER = 2

        const val AUDIO_MANAGER_SUCCESS = 0

        /** 捕获侧平滑 */
        const val CAPTURE_SMOOTHING = 0.25f
    }

    @Volatile
    private var running = false

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var frames = 0L

    @Volatile
    private var lastPeak = 0f

    /** 最新一帧 */
    @Volatile
    private var latest: FloatArray? = null

    private var worker: Thread? = null
    private var record: AudioRecord? = null

    private var policy: Any? = null

    @Volatile
    private var registerStatus: Int? = null

    override fun isCapturing(): Boolean = running

    override fun frameCount(): Long = frames

    override fun latestBands(): FloatArray? = latest?.copyOf()

    override fun describe(): String {
        return buildString {
            append("uid=").append(android.os.Process.myUid())
            append(" pid=").append(android.os.Process.myPid())
            append(" capturing=").append(running)
            append(" register=").append(registerStatus ?: "n/a")
            append(" frames=").append(frames)
            append(" peak=").append(String.format("%.3f", lastPeak))
            lastError?.let { append(" error=").append(it) }
        }
    }

    /** 建立通路 */
    override fun start(): String {
        if (running) return ""
        lastError = null
        frames = 0
        lastPeak = 0f
        latest = null

        val rec = try {
            openCaptureRecord()
        } catch (t: Throwable) {
            val reason = "建立捕获通路失败：${t.javaClass.simpleName}: ${t.message}"
            Log.w(TAG, reason, t)
            lastError = reason
            releaseAll()
            return reason
        }
        record = rec

        val fft = SpectrumFft(FFT_SIZE)
        val out = FloatArray(BANDS)
        val smooth = FloatArray(BANDS)
        val buf = ShortArray(FFT_SIZE)
        val cosT = FloatArray(FFT_SIZE / 2)
        val sinT = FloatArray(FFT_SIZE / 2)
        for (i in 0 until FFT_SIZE / 2) {
            val ang = -2.0 * Math.PI * i / FFT_SIZE
            cosT[i] = kotlin.math.cos(ang).toFloat()
            sinT[i] = kotlin.math.sin(ang).toFloat()
        }

        running = true
        worker = Thread {
            var readErrors = 0
            while (running) {
                val read = try {
                    rec.read(buf, 0, buf.size)
                } catch (t: Throwable) {
                    -1
                }
                if (read < buf.size) {
                    if (read < 0 && ++readErrors > 30) {
                        fail("读取音频流连续失败（code=$read）")
                        break
                    }
                    continue
                }
                fft.magnitude(buf, out, cosT, sinT)
                for (i in 0 until BANDS) {
                    smooth[i] = smooth[i] * CAPTURE_SMOOTHING + out[i] * (1f - CAPTURE_SMOOTHING)
                }
                var mx = 0f
                for (v in smooth) if (v > mx) mx = v
                lastPeak = mx
                latest = smooth.copyOf()
                frames++
            }
            Log.i(TAG, "捕获线程退出：frames=$frames lastError=$lastError")
        }.also { it.isDaemon = true; it.start() }

        Log.i(TAG, "AudioMix(LOOP_BACK|RENDER) 通路已建立 sr=$SAMPLE_RATE bands=$BANDS")
        return ""
    }

    override fun stop() {
        running = false
        worker?.interrupt()
        worker = null
        runCatching { record?.stop() }
        releaseAll()
        latest = null
        Log.i(TAG, "捕获已停止")
    }

    /** Shizuku 的清理入口 */
    override fun destroy() {
        stop()
        System.exit(0)
    }

    /** 建 AudioRecord */
    private fun openCaptureRecord(): AudioRecord {
        val ruleCls = Class.forName("android.media.audiopolicy.AudioMixingRule")
        val ruleBuilderCls = Class.forName("android.media.audiopolicy.AudioMixingRule\$Builder")
        val ruleBuilder = ruleBuilderCls.getDeclaredConstructor().newInstance()
        val addRule = ruleBuilderCls.getMethod(
            "addRule", AudioAttributes::class.java, Int::class.javaPrimitiveType
        )
        for (usage in intArrayOf(
            AudioAttributes.USAGE_MEDIA,
            AudioAttributes.USAGE_GAME,
            AudioAttributes.USAGE_UNKNOWN
        )) {
            addRule.invoke(
                ruleBuilder,
                AudioAttributes.Builder().setUsage(usage).build(),
                RULE_MATCH_ATTRIBUTE_USAGE
            )
        }
        val mixingRule = ruleBuilderCls.getMethod("build").invoke(ruleBuilder)

        val mixCls = Class.forName("android.media.audiopolicy.AudioMix")
        val mixBuilderCls = Class.forName("android.media.audiopolicy.AudioMix\$Builder")
        val mixBuilder = mixBuilderCls.getConstructor(ruleCls).newInstance(mixingRule)
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        mixBuilderCls.getMethod("setFormat", AudioFormat::class.java).invoke(mixBuilder, format)
        mixBuilderCls.getMethod("setRouteFlags", Int::class.javaPrimitiveType)
            .invoke(mixBuilder, ROUTE_FLAG_LOOP_BACK or ROUTE_FLAG_RENDER)
        val mix = mixBuilderCls.getMethod("build").invoke(mixBuilder)

        val policyCls = Class.forName("android.media.audiopolicy.AudioPolicy")
        val policyBuilderCls = Class.forName("android.media.audiopolicy.AudioPolicy\$Builder")
        val policyBuilder = policyBuilderCls.getConstructor(Context::class.java)
            .newInstance(null as Context?)
        policyBuilderCls.getMethod("addMix", mixCls).invoke(policyBuilder, mix)
        val built = policyBuilderCls.getMethod("build").invoke(policyBuilder)

        val register = AudioManager::class.java
            .getDeclaredMethod("registerAudioPolicyStatic", policyCls)
            .apply { isAccessible = true }
        val status = try {
            register.invoke(null, built) as Int
        } catch (e: InvocationTargetException) {
            throw IllegalStateException("registerAudioPolicyStatic 抛异常：${e.cause}", e.cause)
        }
        registerStatus = status
        if (status != AUDIO_MANAGER_SUCCESS) {
            throw IllegalStateException(
                "registerAudioPolicy 返回 $status（0=SUCCESS，1=已存在，-1/-2=不支持/参数非法）"
            )
        }
        policy = built

        val sink = policyCls.getMethod("createAudioRecordSink", mixCls).invoke(built)
            ?: throw IllegalStateException("createAudioRecordSink 返回 null（policy 未就绪或权限不足）")
        val rec = sink as AudioRecord
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { rec.release() }
            throw IllegalStateException(
                "AudioRecord 未初始化（state=${rec.state}）—— 多半是归因不合法，看 logcat 的 EX_SECURITY"
            )
        }
        rec.startRecording()
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            runCatching { rec.stop() }
            runCatching { rec.release() }
            throw IllegalStateException("startRecording 后状态异常（${rec.recordingState}）")
        }
        return rec
    }

    /** 释放 AudioRecord 并注销 AudioPolicy */
    private fun releaseAll() {
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        val p = policy
        policy = null
        if (p != null) {
            runCatching {
                val policyCls = Class.forName("android.media.audiopolicy.AudioPolicy")
                val unregister = AudioManager::class.java
                    .getDeclaredMethod("unregisterAudioPolicyAsyncStatic", policyCls)
                    .apply { isAccessible = true }
                unregister.invoke(null, p)
            }.onFailure { Log.w(TAG, "注销 AudioPolicy 失败：${it.message}") }
        }
    }

    private fun fail(reason: String) {
        lastError = reason
        running = false
        Log.w(TAG, reason)
    }
}
