package com.cheku.app.net

import com.cheku.app.config.ServerConfig
import java.net.URLConnection
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * 自有站点的证书公钥锁定。
 *
 * 只对 [ServerConfig.siteDomain] 及其子域生效，第三方地址（如油价来源）不受影响。
 * 证书链上任意一级的 SPKI 指纹命中即通过，因此可以同时锁定叶子、中间证书和根证书，
 * 叶子证书续签后只要根不变就不会失效。未配置指纹时不启用锁定。
 */
internal object HttpPinning {
    /** 需要锁定的域名 */
    val pinnedDomains: Set<String>
        get() = setOfNotNull(ServerConfig.siteDomain.takeIf { it.isNotBlank() })

    /** 允许的公钥指纹 */
    val spkiSha256Pins: Set<String> get() = ServerConfig.certificatePins

    /** 该主机是否属于需要锁定的域名 */
    fun isPinnedHost(host: String?, domains: Set<String> = pinnedDomains): Boolean {
        val value = host?.trim()?.lowercase() ?: return false
        if (value.isEmpty()) return false
        return domains.any { value == it || value.endsWith(".$it") }
    }

    /** 给连接装上证书锁定，必须在 connect() 之前调用 */
    fun configure(connection: URLConnection) {
        val https = connection as? HttpsURLConnection ?: return
        if (spkiSha256Pins.isEmpty()) return
        if (!isPinnedHost(https.url?.host)) return
        https.sslSocketFactory = sslContext.socketFactory
    }

    /** 链上任一级的 SPKI 命中即视为通过 */
    fun matchesPins(spkiHashes: Collection<String>, pins: Set<String> = spkiSha256Pins): Boolean =
        spkiHashes.any { it in pins }

    /** sha256(SubjectPublicKeyInfo) 的 base64，与 openssl 的输出格式一致 */
    fun sha256Base64(subjectPublicKeyInfo: ByteArray): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(subjectPublicKeyInfo))

    private val sslContext: SSLContext by lazy {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        val delegate = factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            ?: error("系统未提供 X509TrustManager，无法启用证书锁定")
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(PinningTrustManager(delegate)), null) }
    }

    /** 先走系统校验，再要求链上任一级命中指纹集合 */
    private class PinningTrustManager(private val delegate: X509TrustManager) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
            delegate.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            delegate.checkServerTrusted(chain, authType)
            val hashes = chain.map { sha256Base64(it.publicKey.encoded) }
            if (!matchesPins(hashes)) {
                throw CertificateException(
                    "证书公钥指纹不在锁定集合内（可能是中间人，或站点换了签发 CA）。请从官网下载最新版 App。"
                )
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
    }
}
