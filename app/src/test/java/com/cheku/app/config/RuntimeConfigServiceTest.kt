package com.cheku.app.config

import com.cheku.app.ApiClient
import com.cheku.app.ApiResponse
import com.cheku.app.KeyValueStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 运行时配置服务的端到端单测 */
class RuntimeConfigServiceTest {
    /** 内置公钥 */
    private val publicKey = ConfigSignatureTestSupport.publicKeyBase64()

    private val payload =
        """{"configVersion":2,"ttlSeconds":86400,"endpoints":{"apiBase":"https://www.example.com","latestPath":"/api/app/latest","downloadPath":"/api/download","accountBase":"https://www.example.com/api/account","backupBase":"https://www.example.com/api/backup","donationUrl":"https://qr.alipay.com/demo","oilPriceSources":["https://www.autohome.com.cn/oil/"]},"features":{"accounts":false,"cloudBackup":true},"minSupportedVersion":"0.6.0","noticeTitle":"","notice":"","issuedAt":"2026-09-22T01:44:55.262Z","sig":"__SIG__"}"""

    /** 用与生产同一套算法签名，避免测试里出现手抄错 */
    private fun signed(version: Int): String {
        val bodyWithoutSig = payload
            .replace("\"configVersion\":2", "\"configVersion\":$version")
            .substringBefore(""","sig":""")
            .plus("}")
        return ConfigSignatureTestSupport.signBody(bodyWithoutSig)
    }

    @After
    fun reset() {
        RuntimeConfigProvider.update(null)
    }

    private class MemoryStore : KeyValueStore {
        private val map = HashMap<String, Any?>()
        override fun getString(key: String, default: String?) = map[key] as? String ?: default
        override fun putString(key: String, value: String?) { map[key] = value }
        override fun getLong(key: String, default: Long) = (map[key] as? Long) ?: default
        override fun putLong(key: String, value: Long) { map[key] = value }
        override fun getBoolean(key: String, default: Boolean) = (map[key] as? Boolean) ?: default
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun contains(key: String) = map.containsKey(key)
    }

    private class CountingClient(private val code: Int, private val body: String) : ApiClient {
        var calls = 0
            private set
        override suspend fun call(request: com.cheku.app.ApiRequest): ApiResponse {
            calls += 1
            return ApiResponse(code, body)
        }
    }

    @Test
    fun acceptedConfigIsAppliedAndPersisted() = runTest {
        val store = MemoryStore()
        val client = CountingClient(200, signed(2))
        val service = RuntimeConfigService(RuntimeConfigStore(store), publicKey, client, siteDomain = "example.com")

        val config = service.refresh()

        assertNotNull("应拿到配置", config)
        assertEquals(2, config!!.configVersion)
        assertEquals("https://qr.alipay.com/demo", RuntimeConfigProvider.donationUrl())
        assertTrue("云端备份应开启", RuntimeConfigProvider.cloudBackupEnabled())
        assertTrue("账号系统应关闭", !RuntimeConfigProvider.accountsEnabled())
        assertEquals(2, RuntimeConfigStore(store).storedVersion())
        assertEquals(1, client.calls)
    }

    @Test
    fun wrongSignatureIsRejectedAndFallsBack() = runTest {
        val store = MemoryStore()
        val client = CountingClient(200, signed(2).replace("\"accounts\":false", "\"accounts\":true"))
        val service = RuntimeConfigService(RuntimeConfigStore(store), publicKey, client, siteDomain = "example.com")

        val config = service.refresh()

        assertNull("校验不过应整份丢弃（保持 BuildConfig 兜底）", config)
        assertNull(RuntimeConfigProvider.current())
        assertTrue("应记录拒绝原因：" + service.lastRejection, service.lastRejection != null)
    }

    @Test
    fun freshCacheSkipsNetwork() = runTest {
        val store = MemoryStore()
        val client = CountingClient(200, signed(2))
        val service = RuntimeConfigService(RuntimeConfigStore(store), publicKey, client, siteDomain = "example.com")

        service.refresh()
        service.refresh() // 第二次：TTL 内应直接吃缓存，不再联网

        assertEquals(1, client.calls)
    }

    @Test
    fun storedConfigIsAppliedWithoutNetwork() = runTest {
        val store = MemoryStore()
        RuntimeConfigStore(store).save(signed(2), 2)
        // 网络不可用（非 2xx），仍应从本地缓存生效。
        val service = RuntimeConfigService(RuntimeConfigStore(store), publicKey, CountingClient(503, ""), siteDomain = "example.com")

        service.refresh(force = true)

        assertNotNull("离线也应应用本地缓存", RuntimeConfigProvider.current())
        assertEquals("https://www.example.com", RuntimeConfigProvider.websiteBaseUrl())
    }

    @Test
    fun versionRegressionIsRejected() = runTest {
        val store = MemoryStore()
        RuntimeConfigStore(store).save(signed(5), 5)
        val service = RuntimeConfigService(RuntimeConfigStore(store), publicKey, CountingClient(200, signed(2)), siteDomain = "example.com")

        service.refresh(force = true)

        assertTrue("版本回退必须被拒", service.lastRejection != null)
    }
}
