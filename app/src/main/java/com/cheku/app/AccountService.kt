package com.cheku.app

import android.content.SharedPreferences
import com.cheku.app.config.ServerConfig
import com.cheku.app.net.HttpPinning
import com.cheku.app.net.MAX_API_BODY_BYTES
import com.cheku.app.net.readBodyLimited
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 账户 / 云备份 共用的底层 HTTP 与本地存储抽象 */

/** 一次 API 调用的描述 */
internal data class ApiRequest(
    val method: String,
    val url: String,
    val body: String? = null,
    val authToken: String? = null,
)

/** 一次 API 调用的原始结果 */
internal data class ApiResponse(
    val code: Int,
    val body: String,
)

/** 网络层抽象 */
internal fun interface ApiClient {
    suspend fun call(request: ApiRequest): ApiResponse
}

/** 默认网络实现 */
internal class HttpUrlConnectionClient : ApiClient {
    override suspend fun call(request: ApiRequest): ApiResponse = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            HttpPinning.configure(connection)
            connection.requestMethod = request.method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            request.authToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (request.body != null) {
                val bytes = request.body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                // 小体积 JSON 请求体：固定长度、不缓冲整包（与备份大文件同一原则）。
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            val raw = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = raw?.use { it.readBodyLimited(MAX_API_BODY_BYTES) }.orEmpty()
            ApiResponse(code, text)
        } finally {
            connection.disconnect()
        }
    }
}

/** 统一的错误模型 */
internal data class ApiError(
    val httpCode: Int,
    val rawBody: String,
    val userMessage: String,
    val retryable: Boolean,
)

internal class CloudApiException(val error: ApiError) : Exception(error.userMessage)

/** 从服务端错误响应里尽量抽出人话 */
internal fun extractServerMessage(body: String): String? {
    if (body.isBlank()) return null
    return runCatching {
        val j = JSONObject(body)
        j.optString("message").takeIf { it.isNotBlank() }
            ?: j.optString("error").takeIf { it.isNotBlank() }
    }.getOrNull()
}

/** android 无关的键值存储接口 */
internal interface KeyValueStore {
    fun getString(key: String, default: String? = null): String?
    fun putString(key: String, value: String?)
    fun getLong(key: String, default: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
    fun contains(key: String): Boolean
}

/** 唯一的 android 触点 */
internal class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }
    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
    override fun contains(key: String): Boolean = prefs.contains(key)
}

internal val DEFAULT_ACCOUNT_BASE_URL: String get() = ServerConfig.websiteUrl + "/api/account"
internal const val USER_AGENT = "CheKu (Android)"

private const val KEY_TOKEN = "account_token"
private const val KEY_UID = "account_uid"
private const val KEY_EMAIL = "account_email"
private const val KEY_EXPIRES_AT = "account_expires_at"

internal val ACCOUNT_SECRET_KEYS = setOf(KEY_TOKEN, KEY_UID)

/** 登录后的会话凭证 */
internal data class AccountSession(
    val token: String,
    val expiresAt: Long, // epoch millis；0 表示无过期
    val uid: String,
    val email: String,
)

/** /api/account/me 返回的资料 */
internal data class AccountProfile(
    val uid: String,
    val email: String,
    val emailVerified: Boolean,
    val createdAt: String?,
    val lastLoginAt: String?,
    val status: String?,
    val quotaBytes: Long,
    /** 注销宽限期到期时间 */
    val deletionScheduledAt: Long?,
)

/** 邮箱归一化 */
internal fun normalizeEmail(raw: String): String = raw.trim().lowercase()

/** 邮箱合法性 */
internal fun isValidEmail(email: String): Boolean =
    normalizeEmail(email).matches(Regex("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$"))

/** 密码强度 */
internal fun isValidPassword(password: String): Boolean = password.length in 8..128 && password.isNotBlank()

/** 验证码 */
internal fun isValidCode(code: String): Boolean = code.length in 4..8 && code.all { it.isDigit() }

internal fun buildSendCodeBody(email: String): String =
    JSONObject().put("email", normalizeEmail(email)).toString()

internal fun buildRegisterBody(email: String, code: String, password: String): String =
    JSONObject()
        .put("email", normalizeEmail(email))
        .put("code", code)
        .put("password", password)
        .toString()

internal fun buildLoginBody(email: String, password: String): String =
    JSONObject().put("email", normalizeEmail(email)).put("password", password).toString()

internal fun buildResetCodeBody(email: String): String =
    JSONObject().put("email", normalizeEmail(email)).toString()

internal fun buildResetBody(email: String, code: String, password: String): String =
    JSONObject()
        .put("email", normalizeEmail(email))
        .put("code", code)
        .put("password", password)
        .toString()

internal fun buildDeleteBody(password: String): String =
    JSONObject().put("password", password).toString()

internal fun parseLoginResponse(body: String): AccountSession {
    val json = JSONObject(body)
    val token = json.optString("token").trim()
    require(token.isNotBlank()) { "登录响应缺少 token" }
    return AccountSession(
        token = token,
        expiresAt = parseExpiresAt(json.opt("expiresAt")),
        uid = json.optString("uid").trim(),
        email = json.optString("email").trim(),
    )
}

internal fun parseMeResponse(body: String): AccountProfile {
    val json = JSONObject(body)
    return AccountProfile(
        uid = json.optString("uid").trim(),
        email = json.optString("email").trim(),
        emailVerified = json.optBoolean("emailVerified", false),
        createdAt = json.optString("createdAt").takeIf { it.isNotBlank() && it != "null" },
        lastLoginAt = json.optString("lastLoginAt").takeIf { it.isNotBlank() && it != "null" },
        status = json.optString("status").takeIf { it.isNotBlank() && it != "null" },
        quotaBytes = json.optLong("quotaBytes", 16L * 1024L * 1024L),
        // 服务端给的是数字（epoch millis）；0 / 缺失都归一成 null（= 未申请注销）
        deletionScheduledAt = json.optLong("deletionScheduledAt", 0L).takeIf { it > 0L },
    )
}

internal fun mapAccountError(code: Int, body: String): ApiError {
    val serverMsg = extractServerMessage(body)
    val retryable = code == 429 || code in 500..599
    val message = when (code) {
        400 -> accountClientMessage(serverMsg)
        401 -> "邮箱或密码错误，请重试"
        403 -> serverMsg?.let { "操作被拒绝：$it" } ?: "操作被拒绝"
        404 -> "服务暂不可用（404）"
        409 -> "该邮箱已被注册"
        429 -> {
            val secs = retryAfterSecondsOf(body)
            if (secs > 0) "操作过于频繁，请 ${secs} 秒后再试" else "操作过于频繁，请稍后再试"
        }
        in 500..599 -> "网络异常，请稍后重试"
        else -> serverMsg?.let { "请求失败（HTTP $code）：$it" } ?: "请求失败（HTTP $code）"
    }
    return ApiError(code, body, message, retryable)
}

internal fun isAccountErrorRetryable(code: Int): Boolean = code == 429 || code in 500..599

internal fun accountClientMessage(serverMsg: String?): String = when (serverMsg) {
    null, "" -> "请求参数有误"
    "invalid email" -> "邮箱格式不正确"
    "password too short (>=8)" -> "密码至少 8 位"
    "invalid code" -> "验证码不正确，请核对邮件中的 6 位数字"
    "code expired or not found", "code expired" -> "验证码已失效，请重新发送"
    "too many attempts" -> "验证码错误次数过多，请重新发送后再试"
    "email already registered" -> "该邮箱已注册，请直接登录"
    "account not found" -> "该邮箱尚未注册"
    else -> "请求参数有误：$serverMsg"
}

internal fun retryAfterSecondsOf(body: String): Long = runCatching {
    val j = JSONObject(body)
    j.optLong("retryAfterSeconds", j.optLong("retryAfter", 0L))
}.getOrDefault(0L)

internal sealed interface RegisterFlowState {
    data object Idle : RegisterFlowState
    data class CodeSent(val email: String) : RegisterFlowState
    data class Done(val session: AccountSession) : RegisterFlowState
    data class Error(val email: String?, val message: String) : RegisterFlowState
}

internal sealed interface RegisterFlowEvent {
    data class CodeSent(val email: String) : RegisterFlowEvent
    data class Registered(val session: AccountSession) : RegisterFlowEvent
    data class Failed(val email: String?, val message: String) : RegisterFlowEvent
    data object Reset : RegisterFlowEvent
}

/** 注册流程状态迁移 */
internal fun advanceRegisterFlow(state: RegisterFlowState, event: RegisterFlowEvent): RegisterFlowState =
    when (event) {
        is RegisterFlowEvent.CodeSent -> RegisterFlowState.CodeSent(event.email)
        is RegisterFlowEvent.Registered -> RegisterFlowState.Done(event.session)
        is RegisterFlowEvent.Failed -> RegisterFlowState.Error(event.email, event.message)
        is RegisterFlowEvent.Reset -> RegisterFlowState.Idle
    }

/** 账户系统 App 端客户端 */
internal class AccountService(
    private val store: KeyValueStore,
    private val client: ApiClient = HttpUrlConnectionClient(),
    private val accountBaseUrl: String = DEFAULT_ACCOUNT_BASE_URL,
) {
    /** 读取本地会话 */
    fun getSession(): AccountSession? {
        val token = store.getString(KEY_TOKEN) ?: return null
        val expiresAt = store.getLong(KEY_EXPIRES_AT)
        if (expiresAt != 0L && System.currentTimeMillis() > expiresAt) {
            clearSession()
            return null
        }
        return AccountSession(
            token = token,
            expiresAt = expiresAt,
            uid = store.getString(KEY_UID).orEmpty(),
            email = store.getString(KEY_EMAIL).orEmpty(),
        )
    }

    val isLoggedIn: Boolean get() = getSession() != null
    fun getEmail(): String? = store.getString(KEY_EMAIL)

    fun clearSession() {
        store.remove(KEY_TOKEN)
        store.remove(KEY_UID)
        store.remove(KEY_EMAIL)
        store.remove(KEY_EXPIRES_AT)
    }

    /** 发送注册验证码 */
    suspend fun sendCode(email: String) {
        require(isValidEmail(email)) { "邮箱格式不正确" }
        val resp = client.call(
            ApiRequest("POST", "$accountBaseUrl/register/send-code", buildSendCodeBody(email)),
        )
        if (resp.code !in 200..299) throw CloudApiException(mapAccountError(resp.code, resp.body))
    }

    /** 两步式注册第二步 */
    suspend fun register(email: String, code: String, password: String): AccountSession {
        require(isValidEmail(email)) { "邮箱格式不正确" }
        require(isValidCode(code)) { "验证码格式不正确" }
        require(isValidPassword(password)) { "密码长度需为 8–128 位" }
        val resp = client.call(
            ApiRequest("POST", "$accountBaseUrl/register", buildRegisterBody(email, code, password)),
        )
        if (resp.code !in 200..299) throw CloudApiException(mapAccountError(resp.code, resp.body))
        val session = runCatching { parseLoginResponse(resp.body) }
            .getOrElse { login(normalizeEmail(email), password) }
        persist(session)
        return session
    }

    /** 登录 */
    suspend fun login(email: String, password: String): AccountSession {
        require(isValidEmail(email)) { "邮箱格式不正确" }
        require(password.isNotBlank()) { "请输入密码" }
        val resp = client.call(
            ApiRequest("POST", "$accountBaseUrl/login", buildLoginBody(email, password)),
        )
        if (resp.code !in 200..299) throw CloudApiException(mapAccountError(resp.code, resp.body))
        val session = parseLoginResponse(resp.body)
        persist(session)
        return session
    }

    /** 注销 */
    suspend fun logout() {
        runCatching {
            val token = getSession()?.token ?: return@runCatching
            client.call(ApiRequest("POST", "$accountBaseUrl/logout", authToken = token))
        }
        clearSession()
    }

    /** 拉取账户资料 */
    suspend fun loadMe(): AccountProfile {
        val token = getSession()?.token ?: throw CloudApiException(notLoggedInError())
        val resp = client.call(ApiRequest("GET", "$accountBaseUrl/me", authToken = token))
        if (resp.code !in 200..299) throw CloudApiException(mapAccountError(resp.code, resp.body))
        return parseMeResponse(resp.body)
    }

    /** 申请注销账号 */
    suspend fun deleteAccount(password: String): Long {
        val token = getSession()?.token ?: throw CloudApiException(notLoggedInError())
        require(password.isNotBlank()) { "请输入密码以确认注销" }
        val resp = client.call(
            ApiRequest("POST", "$accountBaseUrl/delete", buildDeleteBody(password), token),
        )
        if (resp.code !in 200..299) throw CloudApiException(mapAccountError(resp.code, resp.body))
        runCatching { persist(parseLoginResponse(resp.body)) }
        return runCatching { JSONObject(resp.body).optLong("deletionScheduledAt", 0L) }.getOrDefault(0L)
    }

    /** 撤销注销 */
    suspend fun cancelAccountDeletion() {
        val token = getSession()?.token ?: throw CloudApiException(notLoggedInError())
        val resp = client.call(ApiRequest("POST", "$accountBaseUrl/delete/cancel", "{}", token))
        if (resp.code !in 200..299) {
            throw CloudApiException(
                if (resp.code == 401) ApiError(401, resp.body, "登录状态已失效，请重新登录后再试", false)
                else mapAccountError(resp.code, resp.body),
            )
        }
    }

    /** 忘记密码 */
    suspend fun sendResetCode(email: String) {
        require(isValidEmail(email)) { "邮箱格式不正确" }
        val resp = client.call(
            ApiRequest("POST", "$accountBaseUrl/password/reset-code", buildResetCodeBody(email)),
        )
        if (resp.code !in 200..299) throw CloudApiException(mapAccountError(resp.code, resp.body))
    }

    /** 忘记密码 */
    suspend fun resetPassword(email: String, code: String, password: String) {
        require(isValidEmail(email)) { "邮箱格式不正确" }
        require(isValidCode(code)) { "验证码格式不正确" }
        require(isValidPassword(password)) { "密码长度需为 8–128 位" }
        val resp = client.call(
            ApiRequest("POST", "$accountBaseUrl/password/reset", buildResetBody(email, code, password)),
        )
        if (resp.code !in 200..299) throw CloudApiException(mapAccountError(resp.code, resp.body))
    }

    private fun persist(session: AccountSession) {
        store.putString(KEY_TOKEN, session.token)
        store.putString(KEY_UID, session.uid)
        store.putString(KEY_EMAIL, session.email)
        store.putLong(KEY_EXPIRES_AT, session.expiresAt)
    }
}

private fun parseExpiresAt(value: Any?): Long {
    if (value == null || value == JSONObject.NULL) return 0L
    if (value is Number) {
        val v = value.toLong()
        return if (v > 1_000_000_000_000L) v else v * 1000 // 秒级时间戳补成毫秒
    }
    if (value is String) {
        value.toLongOrNull()?.let { v -> return if (v > 1_000_000_000_000L) v else v * 1000 }
        return runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
    }
    return 0L
}

private fun notLoggedInError(): ApiError =
    ApiError(401, "", "请先登录账户", false)
