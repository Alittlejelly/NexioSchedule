package com.haooz.chedule.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

/**
 * JVM / 桌面实现：基于 `java.net.HttpURLConnection`（纯 JDK，零依赖）。
 * jvm target 仅用于验证 commonMain 可编译，不承载线上流量，故不引入 OkHttp。
 * ⚠ 与 Android 差异（仅影响桌面调试）：`HttpURLConnection` 对 `PROPFIND`/`MKCOL` 等方法可能抛 `ProtocolException`；4xx/5xx 须改用 `errorStream` 读取，已对齐 Android 语义。
 */
actual fun createHttpService(timeouts: HttpTimeouts): HttpService = UrlConnectionHttpService(timeouts)

private class UrlConnectionHttpService(private val timeouts: HttpTimeouts) : HttpService {

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBytes: Long,
    ): HttpResult = execute("GET", url, null, null, headers, maxBytes)

    override suspend fun post(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String>,
    ): HttpResult = execute("POST", url, body, contentType, headers)

    override suspend fun request(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
    ): HttpResult = execute(method, url, body, contentType, headers)

    private suspend fun execute(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        headers: Map<String, String>,
        maxBytes: Long = -1,
    ): HttpResult = withContext(Dispatchers.IO) {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = (timeouts.connectSeconds * 1000).toInt()
            readTimeout = (timeouts.readSeconds * 1000).toInt()
            // ⚠ [HttpTimeouts.callSeconds] 在 HttpURLConnection 上无法表达，故此处忽略。早先版本误把它当 readTimeout 覆盖 readSeconds，导致与 Android 行为不一致。
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            // 与 OkHttp 默认一致
            instanceFollowRedirects = true
        }

        try {
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            var truncated = false
            val bytes: ByteArray
            // 与 Android 对齐：长度超限即返回空字节并标记 truncated，避免两平台在 truncated 分支结果不一致。
            if (maxBytes > 0 && conn.contentLengthLong > maxBytes) {
                truncated = true
                bytes = ByteArray(0)
            } else {
                // 4xx/5xx 走 errorStream，否则 HttpURLConnection 会抛 IOException
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                bytes = if (stream == null) {
                    ByteArray(0)
                } else {
                    stream.use { input ->
                        if (maxBytes > 0) {
                            val (b, t) = readAtMost(maxBytes) { buf -> input.read(buf) }
                            truncated = t
                            b
                        } else {
                            input.readBytes()
                        }
                    }
                }
            }
            HttpResult(code, bytes, truncated)
        } finally {
            conn.disconnect()
        }
    }
}
