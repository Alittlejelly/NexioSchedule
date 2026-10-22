package com.haooz.chedule.data.ai

/**
 * 混合加密信封：App 与服务端之间唯一的请求/响应形态。
 *
 * 服务端跑在明文 HTTP 上（HTTPS 会被拦），所以请求体和响应体都必须是密文：
 *
 * 1. App 每次随机生成 32 字节会话密钥；
 * 2. 会话密钥用服务端 RSA 公钥（OAEP + SHA-256）加密，得到 [ek]；
 * 3. 正文用会话密钥做 AES-256-GCM，得到 [iv] 与 [ct]；
 * 4. 服务端用私钥解出会话密钥，解正文，再用**同一把**会话密钥加密响应回传。
 *
 * 中间人既读不到明文，也因为没有私钥解不出 [ek]。会话密钥只在内存里，用完即弃。
 *
 * @property ek 被 RSA-OAEP 加密的会话密钥，base64
 * @property iv AES-256-GCM 的 12 字节 nonce，base64
 * @property ct 密文后附 16 字节 GCM tag，base64
 */
data class AiEnvelope(val ek: String, val iv: String, val ct: String)

/**
 * 服务端 RSA 公钥（PEM，SPKI/X.509 格式）。
 *
 * 公钥本来就是公开的，进仓库没问题；配套的私钥只留在服务端
 * （`server/ai-rsa-private.pem` 或环境变量 `AI_RSA_PRIVATE`）。
 *
 * ⚠ 换服务器或重新生成密钥对后必须同步改这里，否则信封解不开、AI 直接不可用。
 * 取服务端公钥：`curl -H "Authorization: Bearer $DASH_TOKEN" http://<host>/api/ai/public-key`
 */
object AiServerKey {

    private const val PLACEHOLDER = "REPLACE_WITH_SERVER_PUBLIC_KEY"

    /** 线上服务端公钥（`/api/ai/public-key`）；[isConfigured] 为 false 时 AI 调用一律跳过。 */
    val PUBLIC_KEY_PEM = """
        -----BEGIN PUBLIC KEY-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA49D6/61K00ruiFPLyjuZ
        FuBkZgf04YLyQoVac6JMyRTZGIyqxqiD6XgK+NNNasZFgkJZIZ2zCcopKPOy56sd
        yAK+NCDtOTs2uvFPutUR01/zFDrpMV4xYB+0zMRqfM4whovta4q6AihkLgyunhBE
        joHe7NOWDYznweHSCOWjGz+66u1jKVzX+TX7nkT34GVJIiEHtfhR9sB2MzPISK9X
        3w0PAdGRNCj/VhXgwkPsAMfqYw5XTgg0N01mB1OJUkcrNRtKCueVcteNEckU6hRm
        Ph+kmHLKhwaG0v7d3HuYCiQhdGCQ6prOQfmjjmyjL38xaCLV4VSts9lBNDoQWSSk
        lQIDAQAB
        -----END PUBLIC KEY-----
    """.trimIndent()

    fun isConfigured(): Boolean = PUBLIC_KEY_PEM != PLACEHOLDER
}

/**
 * 一次 AI 调用的加密会话。
 *
 * 构造时就地生成会话密钥并算出 [wrappedKey]（被公钥加密的形态）。
 * 各平台的加密实现都是平台原生的（Android/JVM 走 javax.crypto，iOS 走 Swift 桥）。
 *
 * @throws IllegalStateException 公钥不可用或这端没有加密能力
 */
expect class AiCryptoSession(publicKeyPem: String) {

    /** 被服务端公钥加密后的会话密钥（base64）。 */
    val wrappedKey: String

    /** 把请求正文封成信封。 */
    fun encrypt(plainText: String): AiEnvelope

    /** 解开服务端回的响应体。密文被改过或密钥不对时抛异常。 */
    fun decrypt(iv: String, ct: String): String
}
