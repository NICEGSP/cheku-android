package com.cheku.app.config

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** 配置签名单测的公共工具 */
internal object ConfigSignatureTestSupport {
    /** 与生产同款曲线 */
    val keyPair: KeyPair by lazy {
        KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
    }

    /** 内置到 App 的那串 */
    fun publicKeyBase64(publicKey: PublicKey = keyPair.public): String =
        Base64.getEncoder().encodeToString(publicKey.encoded)

    /** 服务端那一步 */
    fun sign(privateKey: PrivateKey, canonical: String): String {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    fun signBody(bodyWithoutSig: String): String {
        val canonical = canonicalConfigString(org.json.JSONObject(bodyWithoutSig))
        return bodyWithoutSig.dropLast(1) + ""","sig":"${sign(keyPair.private, canonical)}"}"""
    }
}
