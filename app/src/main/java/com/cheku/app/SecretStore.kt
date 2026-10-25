package com.cheku.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 本地敏感值的加解密原语 */
internal interface SecretCipher {
    /** 密文与明文的区分靠前缀，不由调用方判断 */
    fun encrypt(plaintext: String): String

    /** 解不开返回 null */
    fun decrypt(stored: String): String?
}

internal class KeystoreSecretCipher(
    private val alias: String = DEFAULT_ALIAS,
    private val provider: String = "AndroidKeyStore"
) : SecretCipher {
    /** 缓存密钥对象 */
    private val secretKey: SecretKey by lazy { loadOrCreateKey() }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(provider).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, provider)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    override fun decrypt(stored: String): String? {
        if (!stored.startsWith(PREFIX)) return stored // 旧数据是明文：直接返回，下次写入时加密
        return try {
            val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            require(raw.size > IV_BYTES) { "密文过短" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(TAG_BITS, raw.copyOfRange(0, IV_BYTES))
            )
            String(cipher.doFinal(raw.copyOfRange(IV_BYTES, raw.size)), Charsets.UTF_8)
        } catch (error: Exception) {
            // 解不开就是解不开：返回 null 让上层当成"没登录"，绝不返回半截或原样密文。
            null
        }
    }

    private companion object {
        /** Keystore 里的密钥别名 */
        const val DEFAULT_ALIAS = "cheku_secret_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val PREFIX = "v1:"
    }
}

internal class EncryptedKeyValueStore(
    private val delegate: KeyValueStore,
    private val cipher: SecretCipher,
    private val secretKeys: Set<String>
) : KeyValueStore {
    override fun getString(key: String, default: String?): String? {
        val raw = delegate.getString(key) ?: return default
        if (key !in secretKeys) return raw
        val decrypted = cipher.decrypt(raw) ?: return default
        if (decrypted == raw) delegate.putString(key, cipher.encrypt(raw))
        return decrypted
    }

    override fun putString(key: String, value: String?) {
        if (value == null) {
            delegate.putString(key, null)
            return
        }
        delegate.putString(key, if (key in secretKeys) cipher.encrypt(value) else value)
    }

    override fun getLong(key: String, default: Long): Long = delegate.getLong(key, default)
    override fun putLong(key: String, value: Long) = delegate.putLong(key, value)
    override fun getBoolean(key: String, default: Boolean): Boolean = delegate.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = delegate.putBoolean(key, value)
    override fun remove(key: String) = delegate.remove(key)
    override fun contains(key: String): Boolean = delegate.contains(key)
}
