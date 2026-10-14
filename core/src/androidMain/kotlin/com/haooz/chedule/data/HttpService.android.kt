package com.haooz.chedule.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Android 实现：OkHttp。
 *
 * ⚠ 与迁移前逐字对齐，勿改：重试保持默认 true；不设 callTimeout（仅显式传
 * [HttpTimeouts.callSeconds] 时才设，否则会给原本无整体超时的请求加上超时，属行为变更）；
 * 响应体一次读空。
 *
 * 唯一差异：OkHttpClient 由本对象创建并缓存，不再每个调用点各建一个（原等于 10 套连接池）。
 *
 * 缓存键含超时值：超时不同的请求不能共用同一个 Client。
 */
private val clientCache = HashMap<HttpTimeouts, OkHttpClient>()

private fun clientFor(timeouts: HttpTimeouts): OkHttpClient =
    synchronized(clientCache) {
        clientCache.getOrPut(timeouts) {
            OkHttpClient.Builder()
                .connectTimeout(timeouts.connectSeconds, TimeUnit.SECONDS)
                .readTimeout(timeouts.readSeconds, TimeUnit.SECONDS)
                .also { builder ->
                    if (timeouts.callSeconds > 0) {
                        builder.callTimeout(timeouts.callSeconds, TimeUnit.SECONDS)
                    }
                }
                .build()
        }
    }

actual fun createHttpService(timeouts: HttpTimeouts): HttpService = OkHttpHttpService(timeouts)

private class OkHttpHttpService(private val timeouts: HttpTimeouts) : HttpService {

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        maxBytes: Long,
    ): HttpResult = execute(
        method = "GET", url = url, body = null, contentType = null, headers = headers, maxBytes = maxBytes,
    )

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
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }

        // OkHttp 要求 GET/HEAD 不能带 body；其余方法按需构造。
        val requestBody = when {
            body == null -> null
            else -> body.toRequestBody(contentType?.toMediaType())
        }
        builder.method(method, requestBody)

        val response = clientFor(timeouts).newCall(builder.build()).execute()
        try {
            val bodyOrNull = response.body
            val bytes: ByteArray
            var truncated = false
            if (maxBytes > 0 && bodyOrNull != null) {
                // 先看声明的长度：明知超限就不必去分配了
                val declared = bodyOrNull.contentLength()
                if (declared > maxBytes) {
                    bytes = ByteArray(0)
                    truncated = true
                } else {
                    // 流只能取一次，必须在 readAtMost 外面拿好
                    val stream = bodyOrNull.byteStream()
                    try {
                        val (b, t) = readAtMost(maxBytes) { buf -> stream.read(buf) }
                        bytes = b
                        truncated = t
                    } finally {
                        stream.close()
                    }
                }
            } else {
                bytes = bodyOrNull?.bytes() ?: ByteArray(0)
            }
            HttpResult(code = response.code, bytes = bytes, truncated = truncated)
        } finally {
            response.close()
        }
    }
}
