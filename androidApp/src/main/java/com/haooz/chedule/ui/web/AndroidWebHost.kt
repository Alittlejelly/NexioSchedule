package com.haooz.chedule.ui.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.PixelCopy
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.haooz.chedule.data.NexioLog
import com.haooz.chedule.data.showToast
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/** 顶栏色条采样高度：只取页面最顶部几行，用来统计主导底色 */
private const val TOP_MIRROR_SAMPLE_HEIGHT_PX = 12

/** 主导色与上一帧接近时的通道阈值，低于此视为同一颜色 */
private const val TOP_MIRROR_COLOR_CLOSE_DELTA = 20

// ════════════════════════════════════════════════════════════════════════
//  网页内容宿主 —— Android 实现（android.webkit.WebView）
//
//  那一页的**外壳**已迁 `:ui-shared`，这里留下的是**网页本体**：
//  创建与配置 / UA 与视口策略 / JS 桥 / PixelCopy 逐帧采色 / 销毁。
//
//  契约见 `:ui-shared` 的 `ui/web/WebHost.kt`。iOS 侧要写一份 `IosWebHost`
//  （WKWebView），**页面与三个对话框不用动**。
//
//  ⚠ 搬动时**只改「写页面 state」为「发事件」**（`loadingProgress = x` →
//    `currentEvents.onProgress(x)` 之类），其余逻辑逐字保留 —— 教务导入这条链路
//    （UA 重取页、SSL 放行、自定义 scheme 外跳、Cookie 跨页保存）都是踩过坑的，
//    没有真机验证前不要顺手重构。
// ════════════════════════════════════════════════════════════════════════


private fun pickDominantColor(src: Bitmap, prevColor: Int): Int {
    val width = src.width
    val height = src.height
    if (width <= 0 || height <= 0) {
        return if (prevColor != -1) prevColor else 0xFF808080.toInt()
    }

    val pixels = IntArray(width * height)
    src.getPixels(pixels, 0, width, 0, 0, width, height)
    val total = pixels.size

    // 4bit/通道量化：key = 0..4095
    val counts = IntArray(4096)
    val sumR = IntArray(4096)
    val sumG = IntArray(4096)
    val sumB = IntArray(4096)
    val touched = IntArray(total.coerceAtMost(4096))
    var touchedN = 0

    val pr = if (prevColor != -1) (prevColor ushr 16) and 0xFF else -1
    val pg = if (prevColor != -1) (prevColor ushr 8) and 0xFF else -1
    val pb = if (prevColor != -1) prevColor and 0xFF else -1
    var prevNearCount = 0

    for (i in pixels.indices) {
        val c = pixels[i]
        val r = (c ushr 16) and 0xFF
        val g = (c ushr 8) and 0xFF
        val b = c and 0xFF
        if (pr >= 0 &&
            abs(r - pr) <= TOP_MIRROR_COLOR_CLOSE_DELTA &&
            abs(g - pg) <= TOP_MIRROR_COLOR_CLOSE_DELTA &&
            abs(b - pb) <= TOP_MIRROR_COLOR_CLOSE_DELTA
        ) {
            prevNearCount++
        }
        val key = ((r ushr 4) shl 8) or ((g ushr 4) shl 4) or (b ushr 4)
        if (counts[key] == 0) {
            if (touchedN < touched.size) {
                touched[touchedN] = key
                touchedN++
            }
        }
        counts[key]++
        sumR[key] += r
        sumG[key] += g
        sumB[key] += b
    }

    if (touchedN == 0) {
        return if (prevColor != -1) prevColor else 0xFF808080.toInt()
    }

    var bestKey = touched[0]
    var bestCount = counts[bestKey]
    var t = 1
    while (t < touchedN) {
        val k = touched[t]
        val cnt = counts[k]
        if (cnt > bestCount) {
            bestCount = cnt
            bestKey = k
        }
        t++
    }

    val n = bestCount.coerceAtLeast(1)
    val avgR = (sumR[bestKey] / n).coerceIn(0, 255)
    val avgG = (sumG[bestKey] / n).coerceIn(0, 255)
    val avgB = (sumB[bestKey] / n).coerceIn(0, 255)
    val color = (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB

    if (prevColor == -1) return color

    val close = abs(pr - avgR) <= TOP_MIRROR_COLOR_CLOSE_DELTA &&
        abs(pg - avgG) <= TOP_MIRROR_COLOR_CLOSE_DELTA &&
        abs(pb - avgB) <= TOP_MIRROR_COLOR_CLOSE_DELTA
    if (close) return color

    // 上一帧色已几乎不在采样里，或新众数比「接近上一帧的像素」多 5% 以上 → 切换
    // 对半开时两边接近，维持上一帧；页面真变色时 prevNearCount 会掉下去，能切走
    if (prevNearCount * 20 < total || bestCount * 100 > prevNearCount * 105) {
        return color
    }
    return prevColor
}

private fun dominantColorBitmap(color: Int): Bitmap {
    val out = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    out.setPixel(0, 0, color)
    return out
}

/** 顶栏色切换时的每帧插值系数：越小越慢、越绵 */
private const val TOP_MIRROR_COLOR_LERP = 0.16f

private fun lerpArgb(from: Int, to: Int, t: Float): Int {
    if (from == to) return to
    val fr = (from ushr 16) and 0xFF
    val fg = (from ushr 8) and 0xFF
    val fb = from and 0xFF
    val tr = (to ushr 16) and 0xFF
    val tg = (to ushr 8) and 0xFF
    val tb = to and 0xFF
    val r = (fr + (tr - fr) * t).toInt().coerceIn(0, 255)
    val g = (fg + (tg - fg) * t).toInt().coerceIn(0, 255)
    val b = (fb + (tb - fb) * t).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * 造一个 Android 网页宿主。
 *
 * @param config 会随页面状态变（桌面模式 / 顶栏让位高度），内部用 [rememberUpdatedState] 跟；
 *   `config.isDesktopMode` 与 `config.importUrl` 分别驱动两个 `LaunchedEffect`。
 * @param events 页面的事件回调，同样用 [rememberUpdatedState] 持有 —— WebView 的
 *   client 是长生命周期对象，持旧闭包会写到过期的 state 上。
 */
@Composable
fun rememberAndroidWebHost(
    config: WebHostConfig,
    events: WebHostEvents,
): WebHost {
    val context = LocalContext.current
    val currentConfig by rememberUpdatedState(config)
    val currentEvents by rememberUpdatedState(events)
    val density = LocalDensity.current


    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.Transparent.toArgb())
            // 与 SleepDown 一致：硬件层 + 后面 AndroidView 的 Offscreen，页面像素才能被 layerBackdrop 录进去
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        }
    }
    var importCompleted by remember { mutableStateOf(false) }
    val androidBridge = remember {
        AndroidBridge(
            context = context,
            webView = webView,
            onCourseImported = { courses ->
                importCompleted = true
                currentEvents.onCoursesImported(courses)
            },
            onTaskCompleted = {
                currentEvents.onTaskCompleted()
                if (!importCompleted) {
                    showToast("导入完成", longDuration = true)
                }
            },
            onShowAlert = { title, content, confirmText, onResult ->
                currentEvents.onAlert(title, content, confirmText, onResult)
            },
            onShowPrompt = { title, tip, defaultText, validatorJs, onResult ->
                currentEvents.onPrompt(title, tip, defaultText, validatorJs, onResult)
            },
            onShowSingleSelection = { title, items, defaultIndex, onResult ->
                currentEvents.onSingleSelection(title, items, defaultIndex, onResult)
            }
        )
    }
    // 顶栏空白区：采样页面顶部一小段，取全图众数色做成 1×1，FillBounds 铺满 contentTopPadding。
    // 大面积底色压倒文字笔画；整条纯色填充，没有拉伸字形问题。
    var webTopMirrorBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // 上一帧主导色（滞回用）与当前已上屏颜色（插值用）
    val lastTopMirrorColor = remember { intArrayOf(-1) }
    val lastAppliedMirrorColor = remember { intArrayOf(-1) }
    val edgeSampleHandler = remember { Handler(Looper.getMainLooper()) }
    val topMirrorVisiblePx = with(density) { currentConfig.contentTopPadding.roundToPx() }
    val pixelCopyInFlight = remember { AtomicBoolean(false) }
    val mirrorFrameRunning = remember { AtomicBoolean(false) }

    val captureWebTopMirror: (WebView) -> Unit = remember(topMirrorVisiblePx) {
        { target ->
            if (topMirrorVisiblePx <= 0) return@remember
            // 上一帧还没回来就跳过，避免 PixelCopy 排队打满
            if (!pixelCopyInFlight.compareAndSet(false, true)) return@remember
            try {
                val activity = target.context.findActivity()
                val window = activity?.window
                val width = target.width
                val height = target.height
                if (window != null && width > 0 && height > 0) {
                    // 只采样固定高度的一小段：足够判断色块/文字，又比整条 top padding 便宜
                    val strip = TOP_MIRROR_SAMPLE_HEIGHT_PX.coerceAtMost(height)
                    val location = IntArray(2)
                    target.getLocationInWindow(location)
                    val src = Rect(
                        location[0],
                        location[1],
                        location[0] + width,
                        location[1] + strip
                    )
                    val raw = Bitmap.createBitmap(width, strip, Bitmap.Config.ARGB_8888)
                    PixelCopy.request(
                        window,
                        src,
                        raw,
                        { result ->
                            if (result == PixelCopy.SUCCESS) {
                                val target = pickDominantColor(raw, lastTopMirrorColor[0])
                                raw.recycle()
                                lastTopMirrorColor[0] = target

                                val prevApplied = lastAppliedMirrorColor[0]
                                val color = if (prevApplied == -1) {
                                    target
                                } else {
                                    val lerped = lerpArgb(prevApplied, target, TOP_MIRROR_COLOR_LERP)
                                    // 已非常接近目标时贴齐，避免长期停在中间色
                                    val dr = abs(((lerped ushr 16) and 0xFF) - ((target ushr 16) and 0xFF))
                                    val dg = abs(((lerped ushr 8) and 0xFF) - ((target ushr 8) and 0xFF))
                                    val db = abs((lerped and 0xFF) - (target and 0xFF))
                                    if (dr <= 1 && dg <= 1 && db <= 1) target else lerped
                                }
                                lastAppliedMirrorColor[0] = color

                                val prevBmp = webTopMirrorBitmap
                                if (prevBmp == null || prevBmp.isRecycled || prevBmp.getPixel(0, 0) != color) {
                                    prevBmp?.takeIf { !it.isRecycled }?.recycle()
                                    webTopMirrorBitmap = dominantColorBitmap(color)
                                    currentEvents.onTopColor(
                                        Color(
                                            red = ((color ushr 16) and 0xFF) / 255f,
                                            green = ((color ushr 8) and 0xFF) / 255f,
                                            blue = (color and 0xFF) / 255f,
                                            alpha = 1f
                                        )
                                    )
                                }
                            } else {
                                raw.recycle()
                            }
                            pixelCopyInFlight.set(false)
                        },
                        edgeSampleHandler
                    )
                } else {
                    pixelCopyInFlight.set(false)
                }
            } catch (_: Throwable) {
                pixelCopyInFlight.set(false)
            }
        }
    }

    DisposableEffect(webView, captureWebTopMirror) {
        val choreographer = Choreographer.getInstance()
        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!mirrorFrameRunning.get()) return
                captureWebTopMirror(webView)
                choreographer.postFrameCallback(this)
            }
        }
        mirrorFrameRunning.set(true)
        choreographer.postFrameCallback(frameCallback)
        onDispose {
            mirrorFrameRunning.set(false)
            choreographer.removeFrameCallback(frameCallback)
            webTopMirrorBitmap?.takeIf { !it.isRecycled }?.recycle()
            webTopMirrorBitmap = null
        }
    }
    val compatDelegate = remember(webView) { WebCompatDelegate(webView) }
    LaunchedEffect(Unit) {
        webView.addJavascriptInterface(androidBridge, "AndroidBridge")
        webView.addJavascriptInterface(WebPostBridge(), "WebPostService")
    }
    LaunchedEffect(config.isDesktopMode) {
        compatDelegate.enhanceSettings(config.isDesktopMode)
        // 视口覆盖必须赶在页面自身脚本之前注册，晚了站点就把像素尺寸写死了（登录弹窗会留在屏外）
        compatDelegate.applyDesktopViewportOverride(config.isDesktopMode)
        webView.settings.userAgentString = if (config.isDesktopMode) DESKTOP_USER_AGENT
        else WebSettings.getDefaultUserAgent(context)

        webView.webViewClient = compatDelegate.wrapWebViewClient(
            object : WebViewClient() {
                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url.isNullOrBlank()) return false
                    val scheme = url.toUri().scheme?.lowercase() ?: return false
                    if (scheme == "http" || scheme == "https") return false
                    // 自定义 scheme（intent://, market://, tel:// 等）交给外部处理
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                            url.toUri())
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        view?.context?.startActivity(intent)
                    } catch (_: Exception) { }
                    return true
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    val scheme = request.url.scheme?.lowercase() ?: return false
                    if (scheme == "http" || scheme == "https") return false
                    // 自定义 scheme（intent://, market://, tel:// 等）交给外部处理
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        view?.context?.startActivity(intent)
                    } catch (_: Exception) { }
                    return true
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.proceed()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    val urlScheme = request.url.scheme?.lowercase() ?: ""
                    val isCustomScheme = urlScheme.isNotEmpty() && urlScheme != "http" && urlScheme != "https"
                    if (request.isForMainFrame) {
                        if (isCustomScheme) {
                            // 自定义 scheme 加载失败，回到之前的页面
                            view.post {
                                if (view.canGoBack()) view.goBack() else view.loadUrl("about:blank")
                            }
                        } else {
                            view.post {
                                showToast("加载失败: ${error.description}", longDuration = true)
                            }
                        }
                    }
                }
            },
            config.isDesktopMode
        )

        webView.webChromeClient = compatDelegate.wrapWebChromeClient(
            object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    currentEvents.onProgress(newProgress / 100f)
                }
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (title != null) currentEvents.onTitle(title)
                }
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        NexioLog.d("EduImport", "JS [${it.messageLevel()}]: ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                    }
                    return true
                }
            }
        ) { }

        // 切换 UA 后必须带新 UA 重新取页才生效（document-start 脚本只对新的文档起作用）。
        // 且不能只 reload 当前地址：部分站点识别出手机端后会把页面重定向到手机专属地址
        //（如 m.xxx.com），此时 reload 仍是手机版页面；需带新 UA 重新访问导入入口，
        // 让站点按新 UA 重新解析。同域（未被重定向到其它主机）则 reload 保持当前流程位置。
        if (webView.url != null) {
            val currentHost = webView.url?.toUri()?.host?.lowercase()?.removePrefix("www.")
            val importHost = currentConfig.importUrl?.toUri()?.host?.lowercase()?.removePrefix("www.")
            if (!currentHost.isNullOrBlank() && !importHost.isNullOrBlank() && currentHost != importHost) {
                currentConfig.importUrl?.let { webView.loadUrl(it) }
            } else {
                webView.reload()
            }
        }
    }
    LaunchedEffect(currentConfig.importUrl) {
        val target = currentConfig.importUrl
        if (!target.isNullOrBlank() && target != "about:blank") {
            webView.loadUrl(if (target.startsWith("http")) target else "https://$target")
        }
    }
    DisposableEffect(webView) {
        onDispose {
            com.haooz.chedule.ui.web.WebViewRequestInterceptor.clearPostData()
            webView.stopLoading()
            webView.clearCache(true)
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
            // 不清除 Cookie 与 localStorage：教务系统登录态跨页面保存，下次进入免登录。
            // 两者都按域名隔离，不同学校互不影响；如需换账号，请在教务页面内退出登录。
        }
    }

    return remember(webView) {
        object : WebHost {
            @Composable
            override fun Content(modifier: Modifier) {
                AndroidView(
                    modifier = modifier,
                    factory = { webView },
                    update = {},
                )
            }

            override fun reload() {
                webView.reload()
            }

            override fun canGoBack(): Boolean = webView.canGoBack()

            override fun goBack() {
                webView.goBack()
            }

            override fun currentHostOrNull(): String? =
                webView.url?.toUri()?.host?.lowercase()?.removePrefix("www.")

            override fun evaluateJavascript(js: String) {
                webView.evaluateJavascript(js, null)
            }

            override fun setImportTableId(tableId: String) {
                androidBridge.setImportTableId(tableId)
            }
        }
    }
}

/**
 * 从任意 Context 向外层找到宿主 Activity（`PixelCopy.request(window, …)` 需要 window）。
 *
 * ⚠ `ContextWrapper` 那一支**不能省**：`LocalContext.current` 在 Dialog / ComposeView
 * 之类的宿主里拿到的是包装过的 Context，少了这一支会一路返回 null → 顶栏采样静默失效
 * （不崩、无日志，只是色带永远不出现）。搬动时我一度写丢，代码复核时发现。
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
