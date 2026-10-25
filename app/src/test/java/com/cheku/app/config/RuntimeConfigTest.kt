package com.cheku.app.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeConfigTest {
    /** 服务端返回的完整报文，字段顺序与真实下发一致 */
    private val serverBodyWithoutSig =
        """{"configVersion":1,"ttlSeconds":86400,"endpoints":{"apiBase":"https://www.example.com","latestPath":"/api/app/latest","downloadPath":"/api/download","accountBase":"https://www.example.com/api/account","backupBase":"https://www.example.com/api/backup","donationUrl":"","oilPriceSources":[]},"features":{"accounts":false,"cloudBackup":false},"minSupportedVersion":"0.6.0","noticeTitle":"","notice":"","issuedAt":"2026-09-22T01:27:01.967Z"}"""

    /** 服务端签名时使用的 canonical 串 */
    private val expectedCanonical =
        """{"configVersion":1,"ttlSeconds":86400,"endpoints":{"apiBase":"https://www.example.com","latestPath":"/api/app/latest","downloadPath":"/api/download","accountBase":"https://www.example.com/api/account","backupBase":"https://www.example.com/api/backup","donationUrl":"","oilPriceSources":[]},"features":{"accounts":false,"cloudBackup":false},"minSupportedVersion":"0.6.0","noticeTitle":"","notice":"","issuedAt":"2026-09-22T01:27:01.967Z"}"""

    private val publicKey = ConfigSignatureTestSupport.publicKeyBase64()

    private val serverPayload = ConfigSignatureTestSupport.signBody(serverBodyWithoutSig)

    private fun jsonSig(payload: String = serverPayload): String = JSONObject(payload).optString("sig")

    @Test
    fun canonicalStringMatchesServerFieldOrderByteForByte() {
        assertEquals(expectedCanonical, canonicalConfigString(JSONObject(serverPayload)))
    }

    @Test
    fun noticeTitleSitsBetweenMinVersionAndNotice() {
        val body = serverBodyWithoutSig
            .replace("\"noticeTitle\":\"\"", "\"noticeTitle\":\"云备份维护\"")
        val canonical = canonicalConfigString(JSONObject(ConfigSignatureTestSupport.signBody(body)))
        assertTrue(
            "标题必须排在 minSupportedVersion 与 notice 之间：$canonical",
            canonical.contains("\"minSupportedVersion\":\"0.6.0\",\"noticeTitle\":\"云备份维护\",\"notice\":\""),
        )
    }

    @Test
    fun jsStringEscapesLikeJsonStringifyButKeepsChinese() {
        assertEquals("\"a\\\"b\"", jsString("a\"b"))
        assertEquals("\"a\\\\b\"", jsString("a\\b"))
        assertEquals("\"换行\\n结束\"", jsString("换行\n结束"))
        // 非 ASCII 原样保留（Node 的 JSON.stringify 也不会转义中文）。
        assertEquals("\"车库\"", jsString("车库"))
    }

    @Test
    fun validateAcceptsProperlySignedPayload() {
        val result = validateRuntimeConfig(serverPayload, jsonSig(), publicKey, storedVersion = 0, siteDomain = "example.com")
        assertTrue("应接受签名正确的报文：$result", result is RuntimeConfigValidation.Accepted)
        val config = (result as RuntimeConfigValidation.Accepted).config
        assertEquals(1, config.configVersion)
        assertEquals("https://www.example.com", config.endpoints.apiBase)
        assertFalse(config.features.cloudBackup)
        assertEquals("", config.noticeTitle)
    }

    @Test
    fun signatureIsDerBase64NotHex() {
        // 旧实现是 64 位 hex（HMAC-SHA256）。这里顺手断言"已经不是它"，
        // 防止哪天有人把算法换回对称实现而测试仍是绿的。
        val sig = jsonSig()
        assertFalse("签名不应再是 hex：$sig", Regex("^[0-9a-f]{64}$").matches(sig))
        assertTrue("签名应是 base64：" + sig, runCatching { java.util.Base64.getDecoder().decode(sig) }.isSuccess)
    }

    @Test
    fun validateRejectsTamperedBody() {
        val tampered = serverPayload.replace("\"accounts\":false", "\"accounts\":true")
        val result = validateRuntimeConfig(tampered, jsonSig(), publicKey, storedVersion = 0, siteDomain = "example.com")
        assertTrue("改过内容必须验签失败", result is RuntimeConfigValidation.Rejected)
    }

    @Test
    fun validateRejectsOtherKeyPair() {
        val otherPair = java.security.KeyPairGenerator.getInstance("EC")
            .apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
        val canonical = canonicalConfigString(JSONObject(serverPayload))
        val other = serverBodyWithoutSig.dropLast(1) +
            ""","sig":"${ConfigSignatureTestSupport.sign(otherPair.private, canonical)}"}"""
        val result = validateRuntimeConfig(other, jsonSig(other), publicKey, storedVersion = 0, siteDomain = "example.com")
        assertTrue("别人密钥签的必须拒", result is RuntimeConfigValidation.Rejected)
    }

    @Test
    fun validateRejectsMissingSignature() {
        val result = validateRuntimeConfig(serverPayload, "", publicKey, storedVersion = 0, siteDomain = "example.com")
        assertTrue(result is RuntimeConfigValidation.Rejected)
    }

    @Test
    fun validateRejectsMissingPublicKey() {
        val result = validateRuntimeConfig(serverPayload, jsonSig(), "", storedVersion = 0, siteDomain = "example.com")
        assertTrue(result is RuntimeConfigValidation.Rejected)
    }

    @Test
    fun validateRejectsNonWhitelistedHost() {
        val evil = serverPayload.replace("https://www.example.com", "https://evil.example.org")
        val result = validateRuntimeConfig(evil, jsonSig(), publicKey, storedVersion = 0, siteDomain = "example.com")
        assertTrue("非白名单域名必须拒绝", result is RuntimeConfigValidation.Rejected)
    }

    @Test
    fun validateRejectsVersionRegression() {
        val result = validateRuntimeConfig(serverPayload, jsonSig(), publicKey, storedVersion = 5, siteDomain = "example.com")
        assertTrue("版本回退必须拒绝", result is RuntimeConfigValidation.Rejected)
    }

    @Test
    fun whitelistAcceptsOnlyWangqiyanHttps() {
        assertTrue(isWhitelistedSiteUrl("https://www.example.com", "example.com"))
        assertTrue(isWhitelistedSiteUrl("https://admin.example.com", "example.com"))
        assertTrue(isWhitelistedSiteUrl("https://example.com", "example.com"))
        assertFalse(isWhitelistedSiteUrl("http://www.example.com", "example.com"))
        assertFalse(isWhitelistedSiteUrl("https://example.com.evil.com", "example.com"))
        assertFalse(isWhitelistedSiteUrl("https://www.autohome.com.cn/oil/", "example.com"))
    }

    /** 含标题与回车换行的公告 */
    @Test
    fun noticeKeepsTitleAndNewlinesThroughParseAndSignatureCheck() {
        val title = "【维护通知】云备份暂停服务"
        val notice = "预计 1 小时，期间：\n1. 无法上传备份\n2. 恢复不受影响"
        val body =
            """{"configVersion":9,"ttlSeconds":86400,"endpoints":{"apiBase":"https://www.example.com","latestPath":"/api/app/latest","downloadPath":"/api/download","accountBase":"https://www.example.com/api/account","backupBase":"https://www.example.com/api/backup","donationUrl":"","oilPriceSources":[]},"features":{"accounts":true,"cloudBackup":true},"minSupportedVersion":"0.6.0","noticeTitle":"【维护通知】云备份暂停服务","notice":"预计 1 小时，期间：\n1. 无法上传备份\n2. 恢复不受影响","issuedAt":"2026-09-22T03:40:00.000Z"}"""
        val payload = ConfigSignatureTestSupport.signBody(body)
        val sig = jsonSig(payload)

        assertEquals(body, canonicalConfigString(JSONObject(payload)))

        val result = validateRuntimeConfig(payload, sig, publicKey, storedVersion = 0, siteDomain = "example.com")
        assertTrue("应接受含标题与换行的公告：$result", result is RuntimeConfigValidation.Accepted)
        val config = (result as RuntimeConfigValidation.Accepted).config
        assertEquals(title, config.noticeTitle)
        assertEquals(notice, config.notice)
        assertEquals(3, config.notice.split("\n").size)
        assertTrue("段落换行必须保留", config.notice.contains("\n2. 恢复不受影响"))
    }

    /** 指纹 */
    @Test
    fun noticeFingerprintTracksTitleAndContent() {
        val a = noticeFingerprint("维护", "今晚维护\n请提前备份")
        assertEquals("首尾空白不影响判定", a, noticeFingerprint("  维护 ", "  今晚维护\n请提前备份\n"))
        assertTrue("内容变了必须换指纹", a != noticeFingerprint("维护", "今晚维护\n请提前备份！"))
        assertNotEquals(
            "只改标题也必须换指纹（否则改标题后公告不会重弹）",
            a,
            noticeFingerprint("维护（更新）", "今晚维护\n请提前备份"),
        )
        assertEquals("同一内容稳定可复现", a, noticeFingerprint("维护", "今晚维护\n请提前备份"))
    }
}
