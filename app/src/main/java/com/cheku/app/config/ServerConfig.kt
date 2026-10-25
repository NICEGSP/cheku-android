package com.cheku.app.config

import com.cheku.app.BuildConfig
import java.net.URI

/**
 * 构建时注入的服务端配置。
 *
 * 取值来自 Gradle 属性或 local.properties（见 README 的「服务端配置」），仓库里不包含任何真实地址。
 * 未配置官网地址时，账户、云备份、在线更新等联网功能默认关闭，其余本地功能不受影响。
 */
internal object ServerConfig {
    /** 官网根地址，例如 `https://www.example.com`；未配置时为空串。 */
    val websiteUrl: String get() = BuildConfig.OFFICIAL_WEBSITE_URL.trim().trimEnd('/')

    val isConfigured: Boolean get() = websiteUrl.startsWith("https://", ignoreCase = true)

    /** 自有站点的域名，用于运行时配置的地址白名单和证书锁定。 */
    val siteDomain: String get() = siteDomainOf(websiteUrl)

    /** 证书公钥指纹（SPKI SHA-256 的 base64）；为空时不启用证书锁定。 */
    val certificatePins: Set<String> get() = parseCertificatePins(BuildConfig.CERT_PINS)
}

/** `https://www.example.com/x` → `example.com`；解析失败返回空串。 */
internal fun siteDomainOf(url: String): String {
    val host = runCatching { URI(url.trim()).host }.getOrNull()?.lowercase().orEmpty()
    return host.removePrefix("www.")
}

/** 逗号、分号或空白分隔的指纹列表。 */
internal fun parseCertificatePins(raw: String): Set<String> =
    raw.split(',', ';', ' ', '\n', '\t').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
