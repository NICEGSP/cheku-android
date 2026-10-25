package com.cheku.app

import android.content.SharedPreferences
import com.cheku.app.config.RuntimeConfigProvider
import com.cheku.app.net.HttpPinning
import com.cheku.app.net.MAX_API_BODY_BYTES
import com.cheku.app.net.readBodyLimited
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object FeedbackService {
    /** 正文上限 */
    const val MAX_TEXT_CHARS = 1000

    private val websiteBaseUrl get() = RuntimeConfigProvider.websiteBaseUrl()

    /** 提交一条反馈 */
    suspend fun submit(text: String): FeedbackResult = withContext(Dispatchers.IO) {
        val body = text.trim()
        if (body.isEmpty()) return@withContext FeedbackResult.Failed("请先写下你的意见")
        if (websiteBaseUrl.isBlank()) return@withContext FeedbackResult.Failed("官网地址未配置")
        val payload = JSONObject()
            .put("text", body)
            // 自报版本号：后台列表里能一眼看出"是不是某个特定版本在坏"。
            .put("version", BuildConfig.VERSION_NAME.removePrefix("v"))
            .toString()
        val connection = try {
            URL("$websiteBaseUrl/api/app/feedback").openConnection() as HttpURLConnection
        } catch (error: Throwable) {
            return@withContext FeedbackResult.Failed("网络不可用，请检查连接后重试")
        }
        try {
            HttpPinning.configure(connection)
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.useCaches = false
            connection.doOutput = true
            connection.setRequestProperty("User-Agent", "CheKu/${BuildConfig.VERSION_NAME} (Android)")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            val bytes = payload.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBodyLimited(MAX_API_BODY_BYTES) }
                .orEmpty()
            when {
                code in 200..299 -> FeedbackResult.Success
                code == 429 -> FeedbackResult.LimitReached(
                    errorMessage(raw) ?: "每个设备每天只能提交一条反馈，明天再来吧"
                )
                code == 400 -> FeedbackResult.Failed(errorMessage(raw) ?: "反馈内容不合法")
                else -> FeedbackResult.Failed("提交失败（HTTP $code），请稍后再试")
            }
        } catch (error: Throwable) {
            FeedbackResult.Failed("提交失败，请检查网络后重试")
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(raw: String): String? = try {
        JSONObject(raw).optString("error").takeIf { it.isNotBlank() }
    } catch (error: Throwable) {
        null
    }
}

/** 提交结果的三种终态，UI 直接照着渲染即可 */
sealed interface FeedbackResult {
    data object Success : FeedbackResult

    /** 今天已提交过 */
    data class LimitReached(val message: String) : FeedbackResult

    data class Failed(val message: String) : FeedbackResult
}

/** 本地时区的自然日键 */
internal fun feedbackDayKey(at: Long): String {
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = at
    val month = (calendar.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    return "${calendar.get(java.util.Calendar.YEAR)}-$month-$day"
}

internal fun isSubmittedOn(lastAt: Long, now: Long): Boolean =
    lastAt > 0L && feedbackDayKey(lastAt) == feedbackDayKey(now)

class FeedbackSubmitStore(private val preferences: SharedPreferences) {
    object Key {
        const val PREFERENCES_NAME = "feedback_submit"
        /** 最近一次成功提交的时刻 */
        const val LAST_AT = "last_at"
    }

    fun lastSubmittedAt(): Long = preferences.getLong(Key.LAST_AT, 0L)

    fun markSubmitted(at: Long = System.currentTimeMillis()) {
        preferences.edit().putLong(Key.LAST_AT, at).apply()
    }

    /** 今天是否已经提交过 */
    fun submittedToday(now: Long = System.currentTimeMillis()): Boolean =
        isSubmittedOn(lastSubmittedAt(), now)
}
