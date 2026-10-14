package com.haooz.chedule.data

/**
 * 跨平台 HTTP 门面。
 *
 * ⚠ **别顺手换成 Ktor**：任何现代 Ktor 都会把 kotlinx-coroutines 从 1.9.0 顶到 1.10+
 *（3.6.0 → 1.11.0，3.1.3 → 1.10.2），而全 app 的协程 1.9.0 是从 Compose 传递来的 ——
 * 提醒、闹钟、同步、小组件全压在协程上。把「升级核心异步库」捆进「KMP 迁移」会让
 * 时序/取消类异常无法归因，也无法单独回退其中一个。
 *
 * 所以这里只定义接口，Android 实现继续用 OkHttp（引擎、超时、重试语义与迁移前逐字一致）。
 * 将来想换 Ktor，只需换一个实现 —— 这也是把接口单独抽出来的意义。
 *
 * 语义对应：网络异常**照常抛出**（不吞，迁移前的调用点普遍自带 try/catch）；
 * 响应体**一次性读入内存**（迁移前的调用点也都是一次性读取）。
 */
interface HttpService {

    /**
     * GET，返回状态码与响应体。
     *
     * @param maxBytes 响应体上限，`<= 0` 表示不限制。超出时**不抛异常**，而是返回
     *   [HttpResult.truncated] = true，由调用方决定怎么处理（迁移前 `ScriptRepository`
     *   就是「超限即视为下载失败」）。
     *   ⚠ 平台实现必须**边读边计数、超限即中止**，不能读完再检查大小（那样 OOM 已经发生）。
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Long = -1,
    ): HttpResult

    /** POST，body 为 UTF-8 文本。 */
    suspend fun post(
        url: String,
        body: String,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
    ): HttpResult

    /**
     * 任意方法。WebDAV 备份用到 `PROPFIND` / `MKCOL`。
     * @param body 为 null 表示无请求体
     */
    suspend fun request(
        method: String,
        url: String,
        body: String? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): HttpResult
}

/**
 * 一次性读空的响应。
 *
 * @param truncated 是否因超出 `maxBytes` 而被截断。为 true 时 [bytes] 是不完整内容，
 *   调用方应视为失败，不要拿去解析。
 */
class HttpResult(
    val code: Int,
    val bytes: ByteArray,
    val truncated: Boolean = false,
) {
    val isSuccessful: Boolean get() = code in 200..299

    /** 响应体按 UTF-8 解码。 */
    val text: String get() = bytes.decodeToString()

    override fun toString(): String =
        "HttpResult(code=$code, bytes=${bytes.size}${if (truncated) ", truncated" else ""})"
}

/**
 * 在读取过程中最多读 [maxBytes]：超限立即停手，返回（已读字节, 是否被截断）。
 *
 * 抽到 commonMain 是因为各平台实现需要同一套语义。用「按块收集再拼接」而不是
 * `ByteArrayOutputStream`（JVM 专有）。超限时保留到上限为止的字节并标记截断。
 *
 * @param read 每次调用读一段到给定缓冲区，返回读到的字节数，-1 表示流结束。
 */
internal fun readAtMost(maxBytes: Long, read: (ByteArray) -> Int): Pair<ByteArray, Boolean> {
    if (maxBytes <= 0) return ByteArray(0) to false
    val buffer = ByteArray(8192)
    val chunks = ArrayList<ByteArray>()
    var total = 0L
    var truncated = false
    while (true) {
        val n = read(buffer)
        if (n == -1) break
        total += n
        if (total > maxBytes) {
            // 只保留到上限为止，多出的部分丢弃
            val keep = (n - (total - maxBytes)).toInt()
            if (keep > 0) chunks.add(buffer.copyOf(keep))
            truncated = true
            break
        }
        chunks.add(buffer.copyOf(n))
    }
    val size = chunks.sumOf { it.size }
    val out = ByteArray(size)
    var pos = 0
    for (chunk in chunks) {
        chunk.copyInto(out, pos)
        pos += chunk.size
    }
    return out to truncated
}

/**
 * 超时配置（秒）。
 *
 * 迁移前每个调用点各自配超时，实测有 4 种组合：5/5、10/30、10/10+15、12/20+30。
 * 这里保留同样的粒度，不擅自统一。
 */
data class HttpTimeouts(
    val connectSeconds: Long = 10,
    val readSeconds: Long = 10,
    /** <=0 表示不设置（对应 OkHttp 不调 callTimeout）。 */
    val callSeconds: Long = 0,
)

/**
 * 平台侧创建默认实现：Android → OkHttp；JVM/桌面 → `HttpURLConnection`；
 * iOS → `NSURLSession`；linuxX64（仅编译门禁）→ 显式失败占位。
 */
expect fun createHttpService(timeouts: HttpTimeouts): HttpService
