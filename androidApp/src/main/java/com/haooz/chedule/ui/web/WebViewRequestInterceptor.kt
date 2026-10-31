/** WebView 请求拦截器 - 桌面模式下用 OkHttp 转发指定请求（剥离 X-Requested-With） */
package com.haooz.chedule.ui.web

import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.haooz.chedule.data.platform.NexioLog
import com.haooz.chedule.i18n.AndroidMiscStr
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * WebView 请求拦截器。
 *
 * 两类转发：① JS 补丁注册过 body 的 POST（强制）；② WebForwardHosts 按站点开过
 * 开关的普通请求（默认关，见那里的说明）。其余全部放行回 WebView 原生处理。
 */
class WebViewRequestInterceptor {

    companion object {
        /**
         * ⚠ 转发响应不进 WebView 的 HTTP 缓存，必须靠这层补，否则二次打开全量重下。
         * 响应头原样复制（含 Cache-Control/ETag），缓存语义与浏览器一致。
         */
        private var httpCache: Cache? = null

        /** 在首次 `intercept()` 之前调用一次即可（client 是 lazy 的）。 */
        fun installCache(context: android.content.Context) {
            if (httpCache != null) return
            httpCache = runCatching {
                Cache(java.io.File(context.cacheDir, "webview-okhttp"), 32L * 1024 * 1024)
            }.getOrNull()
        }

        private val clientWithRedirects by lazy {
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .apply { httpCache?.let { cache(it) } }
                .build()
        }

        /** 主框架专用：不跟随重定向，交给 WebView 自己跳（保留 navigator 语义） */
        private val clientNoRedirects by lazy {
            clientWithRedirects.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }

        // JS 注册了但请求未发出时 body 会残留；封顶并允许页面销毁时清空
        private const val MAX_POST_REGISTRY = 32

        private val postBodyRegistry = java.util.Collections.synchronizedMap(
            mutableMapOf<String, RegisteredPostData>()
        )

        fun registerPostData(id: String, body: String, contentType: String) {
            synchronized(postBodyRegistry) {
                if (postBodyRegistry.size >= MAX_POST_REGISTRY) {
                    val iterator = postBodyRegistry.entries.iterator()
                    while (postBodyRegistry.size >= MAX_POST_REGISTRY && iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
                postBodyRegistry[id] = RegisteredPostData(body, contentType)
            }
        }

        fun clearPostData() {
            postBodyRegistry.clear()
        }

        private data class RegisteredPostData(val body: String, val contentType: String)
    }

    private val cookieManager = CookieManager.getInstance()

    /**
     * 拦截 WebView 请求。
     *
     * 无条件消费 JS 补丁注册的 POST Body：补丁对「非 GET + 字符串 body」的请求一律加
     * X-WebView-Post-Id 头并注册请求体，该头只有这里能消费并剔除 —— 不转发的话
     * WebView 会把它原样发往服务器，被目标 WAF 拒掉（如福信教务登录 POST）。
     * 普通 GET / 未注册 POST 一律放行，交回 WebView 原生处理，保住 Referer/Origin 等属性。
     *
     * @param userAgent WebView 当前 UA，转发时原样带上，避免 okhttp 默认 UA 被判自动化拦截
     */
    fun intercept(request: WebResourceRequest, userAgent: String? = null): WebResourceResponse? {
        val rawUrl = request.url.toString()

        if (!rawUrl.startsWith("http")) return null

        // JS 补丁注册的 POST Body ID（优先头，URL 参数兜底）
        val requestIdHeader = request.requestHeaders["X-WebView-Post-Id"]
        val requestIdParam = request.url.getQueryParameter("_webview_post_id")
        val requestId = requestIdHeader ?: requestIdParam

        // 走 URL 参数注册的，把污染参数从待转发 URL 里剔掉
        val url = if (requestIdParam != null) {
            val uriBuilder = request.url.buildUpon().clearQuery()
            request.url.queryParameterNames.forEach { name ->
                if (name != "_webview_post_id") {
                    request.url.getQueryParameters(name).forEach { value ->
                        uriBuilder.appendQueryParameter(name, value)
                    }
                }
            }
            uriBuilder.build().toString()
        } else {
            rawUrl
        }

        val registeredData = requestId?.let { postBodyRegistry.remove(it) }

        // 转发条件：捕获到 POST body 必须转发（X-WebView-Post-Id 只有这里能剔除）；
        // 其余只对 WebForwardHosts 开过的 host 转发 —— ⚠ 不能无条件转发，
        // 另一类站点的 WAF 会拦（见 WebForwardHosts）。
        // 覆盖口径用黑名单：只排掉 XHR/fetch（教务接口原生就是 200，动它徒增 CORS 变数）
        // 与音视频（流式 + Range 续传）。⚠ 写白名单会漏掉字体和图片。
        val fetchDest = request.requestHeaders["Sec-Fetch-Dest"]?.lowercase()
        val isXhr = fetchDest == "empty"
        val isMedia = fetchDest == "video" || fetchDest == "audio"
        val viaProxy = registeredData != null ||
            (WebForwardHosts.isEnabled(request.url.host) &&
                (request.isForMainFrame || (!isXhr && !isMedia)))
        if (!viaProxy) return null

        NexioLog.d(
            "WebViewInterceptor",
            AndroidMiscStr.logForwardN(request.method, fetchDest ?: "main", url)
        )

        val client = if (request.isForMainFrame) clientNoRedirects else clientWithRedirects

        try {
            val builder = Request.Builder().url(url)

            if (registeredData != null) {
                val mediaType = registeredData.contentType
                    .ifBlank { "application/x-www-form-urlencoded" }
                    .toMediaTypeOrNull()
                val requestBody = registeredData.body.toRequestBody(mediaType)
                builder.method(request.method, requestBody)
            } else {
                // GET / HEAD 无请求体。OkHttp 的 method(...) 对不允许带体的方法会校验，
                // 所以这里必须显式传 null 而不是复用上面的分支。
                builder.method(request.method, null)
            }

            // 复制头部，剔除四项：
            // ⚠ Accept-Encoding —— 原样带时 OkHttp 不再透明解压，而下面又摘掉
            //   Content-Encoding，交给 WebView 的是 gzip 原文（页面全乱码）。
            // ⚠ Content-Length —— 请求体在这里重建，旧长度不符会让服务器挂起或截断。
            // X-Requested-With / X-WebView-Post-Id —— 前者是 WebView 指纹（本模块的存在意义），
            //   后者是内部协议头，不能出网。
            request.requestHeaders.forEach { (key, value) ->
                if (!key.equals("X-Requested-With", ignoreCase = true) &&
                    !key.equals("X-WebView-Post-Id", ignoreCase = true) &&
                    !key.equals("Accept-Encoding", ignoreCase = true) &&
                    !key.equals("Content-Length", ignoreCase = true)
                ) {
                    builder.addHeader(key, value)
                }
            }

            // 带上浏览器用户代理，避免 OkHttp 默认的 okhttp/<版本> 被目标站点 WAF 判定为自动化请求而拦截
            if (!userAgent.isNullOrBlank()) {
                builder.header("User-Agent", userAgent)
            }

            // 同步 Cookie（WebView 容器 ↔ OkHttp 请求双向同步，登录态不丢）
            val cookies = cookieManager.getCookie(url)
            if (!cookies.isNullOrEmpty()) {
                builder.addHeader("Cookie", cookies)
            }

            val response = client.newCall(builder.build()).execute()
            NexioLog.d(
                "WebViewInterceptor",
                AndroidMiscStr.logForwardResultN(
                    response.code,
                    response.header("Content-Type"),
                    response.body?.contentLength(),
                    url
                )
            )

            // 同步 Set-Cookie 回 WebView
            response.headers.values("Set-Cookie").forEach {
                cookieManager.setCookie(url, it)
            }
            cookieManager.flush()

            // 主框架重定向：构造一个替身 HTML 让 WebView 自己跳，保留历史记录与
            // navigator 语义；子资源重定向直接放行放弃（交回 WebView 默认处理）
            if (response.code in 300..399) {
                if (request.isForMainFrame) {
                    val location = response.header("Location")
                    if (location != null) {
                        val absoluteLocation = response.request.url.resolve(location)?.toString() ?: location
                        val html = "<html><script>window.location.replace('$absoluteLocation');</script></html>"
                        response.close()
                        return WebResourceResponse(
                            "text/html",
                            "UTF-8",
                            200,
                            "OK",
                            mapOf("Cache-Control" to "no-cache"),
                            html.byteInputStream()
                        )
                    }
                }
                response.body?.close()
                response.close()
                return null
            }

            // 构造 WebResourceResponse：Content-Encoding 必须从响应头剔除
            // （body.byteStream() 已被 OkHttp 解压，再带这个头 WebView 会二次解压 → 乱码）
            val contentType = response.header("Content-Type")
            val mimeType = contentType?.substringBefore(";") ?: "text/html"
            val encoding = contentType?.substringAfter("charset=", "UTF-8") ?: "UTF-8"

            val responseHeadersMap = mutableMapOf<String, String>()
            for (i in 0 until response.headers.size) {
                val name = response.headers.name(i)
                if (!name.equals("Content-Encoding", ignoreCase = true)) {
                    responseHeadersMap[name] = response.headers.value(i)
                }
            }

            val body = response.body ?: run {
                response.close()
                return null
            }

            return WebResourceResponse(
                mimeType,
                encoding,
                response.code,
                response.message.ifBlank { "OK" },
                responseHeadersMap,
                body.byteStream()
            )
        } catch (e: Exception) {
            NexioLog.e("WebViewInterceptor", "Error intercepting request: $url", e)
            return null
        }
    }
}

/**
 * WebPostBridge —— 供注入 JS 注册 POST 请求体（内部桥接，不暴露业务）。
 * 对应 ui-shared 的 WebPostInterceptJS，两端协议头 X-WebView-Post-Id 对齐。
 */
class WebPostBridge {
    @JavascriptInterface
    fun register(id: String, body: String, type: String) {
        WebViewRequestInterceptor.registerPostData(id, body, type)
    }
}
