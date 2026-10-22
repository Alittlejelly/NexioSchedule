package com.haooz.chedule.data.ai

import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Android 与桌面共用一份实现（`src/jvmShared` 同时挂在 androidMain / jvmMain 上）：
 * 两端的 `javax.crypto` 是同一套 JCA，没有分叉的必要。
 *
 * 参数与服务端 `server/index.js` 必须逐项对齐，改任何一项都要两边一起改：
 * - RSA：`RSA/ECB/OAEPPadding`，OAEP SHA-256，MGF1 用 SHA-256，label 为空
 * - AES：`AES/GCM/NoPadding`，256 位密钥，12 字节 IV，128 位 tag
 */
actual class AiCryptoSession actual constructor(publicKeyPem: String) {

    private val sessionKey: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    private val rsaCipher: Cipher = Cipher.getInstance(RSA_TRANSFORMATION).apply {
        init(
            Cipher.ENCRYPT_MODE,
            KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(decodePem(publicKeyPem))),
            OAEP_PARAMETER_SPEC,
        )
    }

    actual val wrappedKey: String = Base64.getEncoder().encodeToString(rsaCipher.doFinal(sessionKey))

    actual fun encrypt(plainText: String): AiEnvelope {
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        }
        // JCA 的 GCM 把 tag 附在密文尾部，与服务端的 `ct = ciphertext || tag` 天然一致
        val body = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return AiEnvelope(
            ek = wrappedKey,
            iv = Base64.getEncoder().encodeToString(iv),
            ct = Base64.getEncoder().encodeToString(body),
        )
    }

    actual fun decrypt(iv: String, ct: String): String {
        val ivBytes = Base64.getDecoder().decode(iv)
        require(ivBytes.size == IV_LENGTH) { "IV 长度不对：${ivBytes.size}" }
        val cipher = Cipher.getInstance(AES_TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, ivBytes))
        }
        return String(cipher.doFinal(Base64.getDecoder().decode(ct)), Charsets.UTF_8)
    }

    private fun secretKey() = SecretKeySpec(sessionKey, "AES")

    private companion object {
        const val RSA_TRANSFORMATION = "RSA/ECB/OAEPPadding"
        const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128

        val OAEP_PARAMETER_SPEC = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT,
        )

        /** 只取 PEM 正文再 base64 解码；`\r`、空行与首尾空白都剔掉。 */
        fun decodePem(pem: String): ByteArray {
            val body = pem
                .replace("\r", "")
                .lineSequence()
                .filterNot { it.startsWith("-----") }
                .joinToString("")
                .trim()
            require(body.isNotEmpty()) { "公钥 PEM 为空" }
            return Base64.getDecoder().decode(body)
        }
    }
}
