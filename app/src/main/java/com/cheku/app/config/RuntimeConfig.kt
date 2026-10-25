package com.cheku.app.config

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.json.JSONObject

data class RuntimeConfig(
    val configVersion: Int,
    val ttlSeconds: Int,
    val endpoints: Endpoints,
    val features: Features,
    val minSupportedVersion: String,
    /** 公告标题 */
    val noticeTitle: String,
    val notice: String,
) {
    data class Endpoints(
        val apiBase: String,
        val latestPath: String,
        val downloadPath: String,
        val accountBase: String,
        val backupBase: String,
        val donationUrl: String,
        val oilPriceSources: List<String>,
    )

    data class Features(val accounts: Boolean, val cloudBackup: Boolean)

    /** 版本检查地址 */
    val latestUrl: String get() = joinUrl(endpoints.apiBase, endpoints.latestPath)

    /** APK 下载地址 */
    val downloadUrl: String get() = joinUrl(endpoints.apiBase, endpoints.downloadPath)
}

internal fun joinUrl(base: String, path: String): String =
    base.trim().trimEnd('/') + "/" + path.trim().trimStart('/')

/** 解析下发 JSON */
internal fun parseRuntimeConfig(body: String): RuntimeConfig {
    val json = JSONObject(body)
    val endpoints = json.getJSONObject("endpoints")
    val features = json.getJSONObject("features")
    val sources = endpoints.optJSONArray("oilPriceSources") ?: org.json.JSONArray()
    return RuntimeConfig(
        configVersion = json.getInt("configVersion"),
        ttlSeconds = json.optInt("ttlSeconds", 86_400),
        endpoints = RuntimeConfig.Endpoints(
            apiBase = endpoints.getString("apiBase"),
            latestPath = endpoints.getString("latestPath"),
            downloadPath = endpoints.getString("downloadPath"),
            accountBase = endpoints.optString("accountBase", endpoints.getString("apiBase")),
            backupBase = endpoints.optString("backupBase", endpoints.getString("apiBase")),
            donationUrl = endpoints.optString("donationUrl", ""),
            oilPriceSources = buildList {
                for (i in 0 until sources.length()) sources.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            },
        ),
        features = RuntimeConfig.Features(
            accounts = features.optBoolean("accounts", false),
            cloudBackup = features.optBoolean("cloudBackup", false),
        ),
        minSupportedVersion = json.optString("minSupportedVersion", "0.0.0"),
        noticeTitle = json.optString("noticeTitle", ""),
        notice = json.optString("notice", ""),
    )
}

/** 重建服务端签名用的 canonical 串 */
internal fun canonicalConfigString(json: JSONObject): String {
    val endpoints = json.getJSONObject("endpoints")
    val features = json.getJSONObject("features")
    val sources = endpoints.optJSONArray("oilPriceSources") ?: org.json.JSONArray()
    return buildString {
        append('{')
        append("\"configVersion\":").append(json.getInt("configVersion")).append(',')
        append("\"ttlSeconds\":").append(json.optInt("ttlSeconds", 86_400)).append(',')
        append("\"endpoints\":{")
        append("\"apiBase\":").append(jsString(endpoints.getString("apiBase"))).append(',')
        append("\"latestPath\":").append(jsString(endpoints.getString("latestPath"))).append(',')
        append("\"downloadPath\":").append(jsString(endpoints.getString("downloadPath"))).append(',')
        append("\"accountBase\":").append(jsString(endpoints.getString("accountBase"))).append(',')
        append("\"backupBase\":").append(jsString(endpoints.getString("backupBase"))).append(',')
        append("\"donationUrl\":").append(jsString(endpoints.optString("donationUrl", ""))).append(',')
        append("\"oilPriceSources\":[")
        for (i in 0 until sources.length()) {
            if (i > 0) append(',')
            append(jsString(sources.getString(i)))
        }
        append("]},")
        append("\"features\":{")
        append("\"accounts\":").append(features.optBoolean("accounts", false)).append(',')
        append("\"cloudBackup\":").append(features.optBoolean("cloudBackup", false))
        append("},")
        append("\"minSupportedVersion\":").append(jsString(json.optString("minSupportedVersion", "0.0.0"))).append(',')
        append("\"noticeTitle\":").append(jsString(json.optString("noticeTitle", ""))).append(',')
        append("\"notice\":").append(jsString(json.optString("notice", ""))).append(',')
        append("\"issuedAt\":").append(jsString(json.optString("issuedAt", "")))
        append('}')
    }
}

internal fun jsString(value: String): String = buildString {
    append('"')
    for (ch in value) {
        when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (ch < ' ') append("\\u").append(ch.code.toString(16).padStart(4, '0')) else append(ch)
        }
    }
    append('"')
}

/** 公告指纹 */
internal fun noticeFingerprint(title: String, notice: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest((title.trim() + "\n" + notice.trim()).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(16)

/** 校验服务端签名 */
internal fun verifyConfigSignature(
    publicKeyBase64: String,
    canonical: String,
    signatureBase64: String,
): Boolean {
    if (publicKeyBase64.isBlank() || signatureBase64.isBlank()) return false
    return runCatching {
        val keyBytes = Base64.getDecoder().decode(publicKeyBase64)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(canonical.toByteArray(Charsets.UTF_8))
        verifier.verify(Base64.getDecoder().decode(signatureBase64))
    }.getOrDefault(false)
}


/** 油价来源主机白名单 */
private val OIL_SOURCE_HOSTS = setOf("www.autohome.com.cn", "www.icauto.com.cn")

/** 提取 https URL 的主机名 */
private fun hostOf(url: String): String? = runCatching {
    java.net.URI(url.trim()).host?.lowercase()
}.getOrNull()

/** 公告正文字数上限 */
private const val MAX_NOTICE_CHARS = 4000

/** 公告标题字数上限 */
private const val MAX_NOTICE_TITLE_CHARS = 200

/** 配置缓存时间的合理区间 */
private const val MIN_TTL_SECONDS = 60
private const val MAX_TTL_SECONDS = 7 * 24 * 3600

/** 站点类地址白名单：必须是 HTTPS，且主机是 [siteDomain] 本身或其子域 */
internal fun isWhitelistedSiteUrl(url: String, siteDomain: String = ServerConfig.siteDomain): Boolean {
    if (siteDomain.isBlank() || !isHttpsUrl(url)) return false
    val host = hostOf(url) ?: return false
    return host == siteDomain || host.endsWith(".$siteDomain")
}

internal fun isHttpsUrl(url: String): Boolean = url.trim().startsWith("https://", ignoreCase = true)

/** 校验结果 */
internal sealed interface RuntimeConfigValidation {
    data class Accepted(val config: RuntimeConfig) : RuntimeConfigValidation
    data class Rejected(val reason: String) : RuntimeConfigValidation
}

internal fun validateRuntimeConfig(
    body: String,
    signature: String,
    publicKey: String,
    storedVersion: Int,
    siteDomain: String = ServerConfig.siteDomain,
): RuntimeConfigValidation {
    val config = runCatching { parseRuntimeConfig(body) }.getOrElse {
        return RuntimeConfigValidation.Rejected("配置解析失败：${it.message ?: "格式错误"}")
    }
    val endpoints = config.endpoints

    for ((label, url) in listOf("主域名" to endpoints.apiBase, "账号接口" to endpoints.accountBase, "云备份接口" to endpoints.backupBase)) {
        if (!isHttpsUrl(url)) return RuntimeConfigValidation.Rejected("$label 不是 HTTPS")
    }
    for (url in endpoints.oilPriceSources) {
        if (!isHttpsUrl(url)) return RuntimeConfigValidation.Rejected("油价来源不是 HTTPS")
    }
    if (endpoints.donationUrl.isNotBlank() && !isHttpsUrl(endpoints.donationUrl)) {
        return RuntimeConfigValidation.Rejected("赞助地址不是 HTTPS")
    }

    for ((label, url) in listOf("主域名" to endpoints.apiBase, "账号接口" to endpoints.accountBase, "云备份接口" to endpoints.backupBase)) {
        if (!isWhitelistedSiteUrl(url, siteDomain)) return RuntimeConfigValidation.Rejected("$label 不在白名单内")
    }
    for (url in endpoints.oilPriceSources) {
        val host = hostOf(url) ?: return RuntimeConfigValidation.Rejected("油价来源地址无法解析")
        if (host !in OIL_SOURCE_HOSTS) return RuntimeConfigValidation.Rejected("油价来源主机不在白名单内：$host")
    }
    if (config.notice.length > MAX_NOTICE_CHARS) {
        return RuntimeConfigValidation.Rejected("公告正文超长（> $MAX_NOTICE_CHARS 字）")
    }
    if (config.noticeTitle.length > MAX_NOTICE_TITLE_CHARS) {
        return RuntimeConfigValidation.Rejected("公告标题超长（> $MAX_NOTICE_TITLE_CHARS 字）")
    }
    if (config.ttlSeconds !in MIN_TTL_SECONDS..MAX_TTL_SECONDS) {
        return RuntimeConfigValidation.Rejected("配置缓存时间超出合理区间")
    }
    if (!endpoints.latestPath.trim().startsWith("/") || !endpoints.downloadPath.trim().startsWith("/")) {
        return RuntimeConfigValidation.Rejected("版本检查/下载路径必须以 / 开头")
    }

    if (publicKey.isBlank()) return RuntimeConfigValidation.Rejected("未配置签名校验公钥")
    val canonical = runCatching {
        canonicalConfigString(org.json.JSONObject(body))
    }.getOrElse { return RuntimeConfigValidation.Rejected("canonical 串重建失败") }
    if (!verifyConfigSignature(publicKey, canonical, signature)) {
        return RuntimeConfigValidation.Rejected("签名不匹配")
    }

    if (config.configVersion < storedVersion) {
        return RuntimeConfigValidation.Rejected("配置版本回退（本地 $storedVersion，下发 ${config.configVersion}）")
    }
    return RuntimeConfigValidation.Accepted(config)
}

/** 当前生效配置的全局只读入口 */
internal object RuntimeConfigProvider {
    @Volatile
    private var value: RuntimeConfig? = null

    /** 默认油价来源，拉不到配置时使用 */
    private val DEFAULT_OIL_SOURCES = listOf(
        "https://www.autohome.com.cn/oil/",
        "https://www.icauto.com.cn/oil/",
    )

    fun current(): RuntimeConfig? = value

    internal fun update(config: RuntimeConfig?) {
        value = config
    }

    /** 官网根地址 */
    fun websiteBaseUrl(): String =
        value?.endpoints?.apiBase?.takeIf { it.isNotBlank() } ?: ServerConfig.websiteUrl

    fun latestUrl(): String = value?.latestUrl ?: joinUrl(websiteBaseUrl(), "/api/app/latest")

    fun downloadUrl(): String = value?.downloadUrl ?: joinUrl(websiteBaseUrl(), "/api/download")

    fun donationUrl(): String = value?.endpoints?.donationUrl.orEmpty()

    /** 油价来源 */
    fun oilPriceSources(): List<String> =
        value?.endpoints?.oilPriceSources?.takeIf { it.isNotEmpty() } ?: DEFAULT_OIL_SOURCES

    /** 未配置服务端时，账户与云备份默认关闭 */
    fun accountsEnabled(): Boolean = value?.features?.accounts ?: ServerConfig.isConfigured
    fun cloudBackupEnabled(): Boolean = value?.features?.cloudBackup ?: ServerConfig.isConfigured
}
