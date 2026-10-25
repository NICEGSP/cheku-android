package com.cheku.app.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 典型线上配置的兼容性回归 */
class LiveConfigCompatibilityTest {
    private val liveFields = linkedMapOf(
        "configVersion" to 15,
        "ttlSeconds" to 86400,
        "endpoints" to JSONObject(
            """
            {
              "apiBase": "https://www.example.com",
              "latestPath": "/api/app/latest",
              "downloadPath": "/api/download",
              "accountBase": "https://www.example.com/api/account",
              "backupBase": "https://www.example.com/api/backup",
              "donationUrl": "",
              "oilPriceSources": ["https://www.autohome.com.cn/oil/", "https://www.icauto.com.cn/oil/"]
            }
            """.trimIndent()
        ),
        "features" to JSONObject("""{"accounts":true,"cloudBackup":true}"""),
        "minSupportedVersion" to "0.6.0",
        "noticeTitle" to "【维护通知】云备份服务今晚 23:00 起短暂暂停",
        "notice" to "预计持续约 1 小时，期间：\n1. 无法上传备份\n2. 恢复功能不受影响\n\n感谢理解。",
        "issuedAt" to "2026-09-22T03:40:00.000Z",
    )

    @Test
    fun `典型线上配置必须能通过校验`() {
        val body = JSONObject(liveFields as Map<*, *>).toString()
        val payload = ConfigSignatureTestSupport.signBody(body)
        val result = validateRuntimeConfig(
            body = payload,
            signature = JSONObject(payload).optString("sig"),
            publicKey = ConfigSignatureTestSupport.publicKeyBase64(),
            storedVersion = 0,
            siteDomain = "example.com",
        )
        assertTrue(
            "典型线上配置被拒绝：$result",
            result is RuntimeConfigValidation.Accepted,
        )
        val config = (result as RuntimeConfigValidation.Accepted).config
        assertEquals(86400, config.ttlSeconds)
        assertEquals(2, config.endpoints.oilPriceSources.size)
        assertEquals("【维护通知】云备份服务今晚 23:00 起短暂暂停", config.noticeTitle)
        assertTrue(config.notice.contains("\n\n"))
    }
}
