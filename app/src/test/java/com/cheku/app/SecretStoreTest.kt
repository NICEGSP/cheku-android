package com.cheku.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 敏感值加密存储的 JVM 单测 */
class SecretStoreTest {
    /** 可逆的假 cipher */
    private class FakeCipher(private val broken: Boolean = false) : SecretCipher {
        override fun encrypt(plaintext: String): String = "fake:" + plaintext.reversed()
        override fun decrypt(stored: String): String? = when {
            broken -> null
            !stored.startsWith("fake:") -> stored          // 明文（未迁移）
            else -> stored.removePrefix("fake:").reversed()
        }
    }

    private class MapStore : KeyValueStore {
        val raw = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?): String? = raw[key] as? String ?: default
        override fun putString(key: String, value: String?) {
            if (value == null) raw.remove(key) else raw[key] = value
        }
        override fun getLong(key: String, default: Long): Long = (raw[key] as? Long) ?: default
        override fun putLong(key: String, value: Long) { raw[key] = value }
        override fun getBoolean(key: String, default: Boolean): Boolean = (raw[key] as? Boolean) ?: default
        override fun putBoolean(key: String, value: Boolean) { raw[key] = value }
        override fun remove(key: String) { raw.remove(key) }
        override fun contains(key: String): Boolean = raw.containsKey(key)
    }

    private val store = MapStore()
    private val store2 = EncryptedKeyValueStore(store, FakeCipher(), AccountServiceSecretKeys)

    private companion object {
        val AccountServiceSecretKeys = setOf("account_token")
        const val TOKEN = "eyJhbGciOiJIUzI1NiJ9.payload.signature"
    }

    @Test
    fun secretValueIsNotWrittenInPlaintext() {
        store2.putString("account_token", TOKEN)

        assertFalse("token 绝不能以明文形态落盘", store.raw["account_token"] == TOKEN)
        assertTrue("应经过 cipher", store.raw["account_token"].toString().startsWith("fake:"))
        assertEquals("读回来必须是原文", TOKEN, store2.getString("account_token"))
    }

    /** 升级路径 */
    @Test
    fun legacyPlaintextIsReadableAndMigratedOnFirstRead() {
        store.putString("account_token", TOKEN)          // 模拟加固前留下的明文

        assertEquals("明文必须继续可读，否则升级即全员掉线", TOKEN, store2.getString("account_token"))
        assertTrue("读到明文后应就地迁移为密文", store.raw["account_token"].toString().startsWith("fake:"))
        assertEquals(TOKEN, store2.getString("account_token"))
    }

    @Test
    fun nonSecretKeysAreLeftAlone() {
        store2.putString("account_email", "a@b.com")
        store2.putLong("account_expires_at", 1234L)

        assertEquals("非凭据键不该被加密（排障要用）", "a@b.com", store.raw["account_email"])
        assertEquals(1234L, store.raw["account_expires_at"])
        assertEquals("a@b.com", store2.getString("account_email"))
    }

    @Test
    fun undecryptableValueIsTreatedAsAbsent() {
        store.raw["account_token"] = "fake:坏掉的密文"
        val broken = EncryptedKeyValueStore(store, FakeCipher(broken = true), AccountServiceSecretKeys)

        assertNull("解不开应返回 default（表现为未登录），而不是原样密文", broken.getString("account_token"))
        assertNull(broken.getString("account_token", null))
    }

    @Test
    fun writingNullRemovesTheKey() {
        store2.putString("account_token", TOKEN)
        store2.putString("account_token", null)

        assertFalse(store.raw.containsKey("account_token"))
        assertNull(store2.getString("account_token"))
    }

    @Test
    fun accountSecretKeysStillCoversTheTokenKey() {
        assertTrue(
            "account_token 必须在加密集合里：ACCOUNT_SECRET_KEYS=$ACCOUNT_SECRET_KEYS",
            ACCOUNT_SECRET_KEYS.contains("account_token")
        )
        // 集合语义必须与 `AccountService` 写入的键一致（键名在 AccountService.kt 里是 private const）
        assertEquals(2, ACCOUNT_SECRET_KEYS.size)
    }

    @Test
    fun accountSecretKeysCoversUidAfterHardening() {
        assertTrue(
            "account_uid 必须在加密集合里：ACCOUNT_SECRET_KEYS=$ACCOUNT_SECRET_KEYS",
            ACCOUNT_SECRET_KEYS.contains("account_uid")
        )
    }
}
