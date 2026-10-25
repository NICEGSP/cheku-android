package com.cheku.app.config

import com.cheku.app.ApiClient
import com.cheku.app.ApiRequest
import com.cheku.app.BuildConfig
import com.cheku.app.HttpUrlConnectionClient

/** 运行时配置的拉取与生效 */
internal class RuntimeConfigService(
    private val store: RuntimeConfigStore,
    private val publicKey: String = BuildConfig.RUNTIME_CONFIG_PUBLIC_KEY,
    private val client: ApiClient = HttpUrlConnectionClient(),
    private val endpoint: String = defaultConfigEndpoint(),
    private val siteDomain: String = ServerConfig.siteDomain,
) {
    /** 最近一次被拒绝的原因 */
    @Volatile
    var lastRejection: String? = null
        private set

    /** 冷启动 / 手动刷新入口 */
    suspend fun refresh(force: Boolean = false): RuntimeConfig? {
        applyStored()

        val cached = RuntimeConfigProvider.current()
        val cachedVersion = store.storedVersion()
        if (!force && cached != null && store.isFresh(System.currentTimeMillis(), cached.ttlSeconds)) {
            return cached
        }

        val response = runCatching { client.call(ApiRequest("GET", endpoint)) }.getOrNull()
            ?: return cached
        if (response.code !in 200..299) return cached

        val signature = runCatching {
            org.json.JSONObject(response.body).optString("sig", "")
        }.getOrDefault("")

        return when (val result = validateRuntimeConfig(response.body, signature, publicKey, cachedVersion, siteDomain)) {
            is RuntimeConfigValidation.Accepted -> {
                lastRejection = null
                store.save(response.body, result.config.configVersion)
                RuntimeConfigProvider.update(result.config)
                result.config
            }
            is RuntimeConfigValidation.Rejected -> {
                lastRejection = result.reason
                cached
            }
        }
    }

    /** 仅应用本地缓存 */
    internal fun applyStored() {
        if (RuntimeConfigProvider.current() != null) return
        val body = store.rawBody() ?: return
        val signature = runCatching {
            org.json.JSONObject(body).optString("sig", "")
        }.getOrDefault("")
        when (val result = validateRuntimeConfig(body, signature, publicKey, 0, siteDomain)) {
            is RuntimeConfigValidation.Accepted -> RuntimeConfigProvider.update(result.config)
            is RuntimeConfigValidation.Rejected -> lastRejection = result.reason
        }
    }
}

/** 下发地址 = 官网根地址 + 固定路径 */
private fun defaultConfigEndpoint(): String =
    ServerConfig.websiteUrl + "/api/app/config"
