package com.haooz.chedule.data.ai

/**
 * Swift 侧注入的加解密实现。
 *
 * ## 为什么不在 Kotlin/Native 里做
 *
 * 这套信封要两样东西：RSA-OAEP(SHA-256) 和 AES-256-GCM。Kotlin/Native 自带的
 * Apple 平台库对这两样都不完整 —— `platform.Security` 只有 SecKey 那一套低层接口，
 * 而 CommonCrypto 的 `CCCryptorGCM` 根本没有绑定。在 Kotlin 里硬凑要么引入 cinterop
 * 定义文件，要么手写算法（绝不该手写密码学）。
 *
 * Swift 侧两样都是一行：`SecKeyCreateEncryptedData` + `CryptoKit` 的 `AES.GCM`。
 * 所以照仓库既有惯例（`IosNativeCapture` / `IosToast` 同一招）——
 * Kotlin 定接口，Swift 实现并在启动时把闭包塞进来。
 *
 * 四个闭包都是「失败返回 null」，不抛异常：跨语言的异常传递比返回值难查得多。
 * 会话密钥由 Swift 侧持有，Kotlin 只拿一个不透明的 handle。
 */
object IosAiCryptoBridge {

    /** Swift 注入：按公钥 PEM 建一个会话，返回不透明 handle；失败 null。 */
    var createSession: ((publicKeyPem: String) -> String?)? = null

    /** Swift 注入：取该会话被公钥加密后的会话密钥（base64）。 */
    var wrappedKey: ((handle: String) -> String?)? = null

    /** Swift 注入：AES-GCM 加密，返回 `iv\nct`（两段各 base64）；失败 null。 */
    var encrypt: ((handle: String, plainText: String) -> String?)? = null

    /** Swift 注入：AES-GCM 解密；失败 null。 */
    var decrypt: ((handle: String, iv: String, ct: String) -> String?)? = null

    /** 这端是否已经接好（Swift 启动时注入过）。 */
    fun isReady(): Boolean = createSession != null && wrappedKey != null &&
        encrypt != null && decrypt != null
}

/**
 * iOS 的加密会话，实际工作全在 Swift 侧。
 *
 * @throws IllegalStateException Swift 桥没注入或建会话失败
 */
actual class AiCryptoSession actual constructor(publicKeyPem: String) {

    private val handle: String = run {
        val create = IosAiCryptoBridge.createSession
            ?: throw IllegalStateException("iOS 端 AI 加密桥未注入")
        create(publicKeyPem) ?: throw IllegalStateException("会话密钥生成失败")
    }

    actual val wrappedKey: String = run {
        val get = IosAiCryptoBridge.wrappedKey ?: throw IllegalStateException("iOS 端 AI 加密桥未注入")
        get(handle) ?: throw IllegalStateException("会话密钥加密失败")
    }

    actual fun encrypt(plainText: String): AiEnvelope {
        val fn = IosAiCryptoBridge.encrypt ?: throw IllegalStateException("iOS 端 AI 加密桥未注入")
        val out = fn(handle, plainText) ?: throw IllegalStateException("请求加密失败")
        val sep = out.indexOf('\n')
        require(sep > 0) { "加密结果格式不对" }
        return AiEnvelope(ek = wrappedKey, iv = out.substring(0, sep), ct = out.substring(sep + 1))
    }

    actual fun decrypt(iv: String, ct: String): String {
        val fn = IosAiCryptoBridge.decrypt ?: throw IllegalStateException("iOS 端 AI 加密桥未注入")
        return fn(handle, iv, ct) ?: throw IllegalStateException("响应解密失败")
    }
}
