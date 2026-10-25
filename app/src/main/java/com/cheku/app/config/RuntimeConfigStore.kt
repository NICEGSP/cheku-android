package com.cheku.app.config

import com.cheku.app.KeyValueStore

/** 运行时配置的本地缓存 */
internal class RuntimeConfigStore(private val store: KeyValueStore) {
    fun rawBody(): String? = store.getString(KEY_BODY)?.takeIf { it.isNotBlank() }

    fun storedVersion(): Int = store.getLong(KEY_VERSION, 0L).toInt()

    fun fetchedAt(): Long = store.getLong(KEY_FETCHED_AT, 0L)

    /** 缓存是否仍在有效期内 */
    fun isFresh(now: Long, ttlSeconds: Int): Boolean {
        val at = fetchedAt()
        if (at <= 0L) return false
        val ttl = if (ttlSeconds > 0) ttlSeconds else DEFAULT_TTL_SECONDS
        return now - at < ttl * 1000L
    }

    fun save(body: String, version: Int, now: Long = System.currentTimeMillis()) {
        store.putString(KEY_BODY, body)
        store.putLong(KEY_VERSION, version.toLong())
        store.putLong(KEY_FETCHED_AT, now)
    }

    /** 上次已弹过的公告指纹 */
    fun lastSeenNoticeFingerprint(): String = store.getString(KEY_NOTICE_SEEN).orEmpty()

    fun markNoticeSeen(fingerprint: String) {
        store.putString(KEY_NOTICE_SEEN, fingerprint)
    }

    private companion object {
        const val KEY_BODY = "runtime_config_body"
        const val KEY_VERSION = "runtime_config_version"
        const val KEY_FETCHED_AT = "runtime_config_fetched_at"
        const val KEY_NOTICE_SEEN = "runtime_config_notice_seen"
        const val DEFAULT_TTL_SECONDS = 86_400
    }
}
