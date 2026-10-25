/** WebView 兼容性委托 - 处理不同 Android 版本的 WebView 兼容问题 */
package com.haooz.chedule.ui.web

import android.graphics.Bitmap
import android.os.Build
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.haooz.chedule.data.NexioLog
import com.haooz.chedule.i18n.AndroidMiscStr
import com.haooz.chedule.ui.utils.screenWidthDp

private const val TAG = "WebCompatDelegate"

/** 桌面模式布局宽度（CSS px），见 desktopViewportContent() */
private const val DESKTOP_LAYOUT_WIDTH = 1280

/**
 * 桌面模式 UA：Chrome 大版本取系统 WebView 实际 UA —— 写死会和 Sec-Ch-Ua（真实版本）
 * 对不上，部分 WAF 判 bot 返回 HTML 挑战页，表现为「渲染了但 CSS 没生效」。
 */
internal fun buildDesktopUserAgent(context: android.content.Context): String {
    val realUA = WebSettings.getDefaultUserAgent(context)
    val chromeVer = Regex("Chrome/(\\d+)").find(realUA)?.groupValues?.getOrNull(1) ?: "120"
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/$chromeVer.0.0.0 Safari/537.36"
}

/**
 * 桌面模式视口覆盖脚本（document-start 注入，先于页面自身脚本执行）。
 *
 * 为什么非得这么早：本校门户在 `$(document).ready` 里就把 body 宽度量下来、
 * 把像素尺寸写进登录弹窗的行内样式（qsflat 全站没有 resize 监听），
 * 等 onPageFinished 再改 viewport 已无从纠正。
 */
private fun buildDesktopViewportScript(content: String): String = """
    (function () {
        var CONTENT = '$content';
        var FLAG = 'data-nexio-desktop-viewport';
        function apply() {
            if (!document.head) return false;
            // 页面自带的 viewport meta 一律清掉，只留我们这一条
            var metas = document.head.querySelectorAll('meta[name="viewport"]');
            for (var i = metas.length - 1; i >= 0; i--) {
                if (!metas[i].hasAttribute(FLAG)) metas[i].parentNode.removeChild(metas[i]);
            }
            var mine = document.head.querySelector('meta[' + FLAG + ']');
            if (!mine) {
                mine = document.createElement('meta');
                mine.setAttribute('name', 'viewport');
                mine.setAttribute(FLAG, '1');
                document.head.appendChild(mine);
            }
            if (mine.getAttribute('content') !== CONTENT) mine.setAttribute('content', CONTENT);
            return true;
        }
        function boot() {
            // 文档开始时 head 还不存在，等它出现
            if (!document.head) { setTimeout(boot, 0); return; }
            apply();
            document.addEventListener('DOMContentLoaded', apply);
            document.addEventListener('load', apply);
            // 站点自己的 viewport meta 可能比 boot 更晚才被解析到，出现即清除
            new MutationObserver(function () {
                if (document.head.querySelector('meta[name="viewport"]:not([' + FLAG + '])')) apply();
            }).observe(document.head, { childList: true });
        }
        boot();
    })();
""".trimIndent()

// JS_PROMISE_BRIDGE 2026-10-13 已上提到 :ui-shared（同包名 `com.haooz.chedule.ui.web`
// 的 JsPromiseBridge.kt，无需 import）—— iOS 的无头 WakeUp 导入要用同一份。

/**
 * WebView 兼容性配置
 * 管理 WebView 设置、Cookie、桌面模式等
 */
class WebCompatDelegate(private val webView: WebView) {

    private var viewportScriptHandler: ScriptHandler? = null

    /** document-start 视口覆盖是否已生效；为 false 时由 onPageFinished 兜底注入 */
    var desktopViewportOverrideActive = false
        private set

    /**
     * 是否已执行过桌面版视口自愈（[applyDesktopZoomFix]）。
     *
     * 首次加载 Chrome 常只落实布局宽度、不重算缩放（vvScale 仍 1.0）—— 这也是
     * 「切一次手机再切回电脑就好了」的真实原因。每页只修一次，避免和页面跳转打架。
     */
    private var viewportRetried = false

    /**
     * 启用/关闭桌面模式视口覆盖。返回 document-start 注入是否成功注册；
     * false（老 WebView 不支持）时 onPageFinished 兜底注入仍会开着。
     *
     * ⚠ 三条通道分工明确，别互相替代（每条都是被真机 bug 逼出来的）：
     *  - **document-start meta（width=1280 + initial-scale）**：保证首帧起布局宽度
     *    就是桌面宽 —— 站点脚本在 ready 里量到的 innerWidth 才是桌面值。1.6.4 曾
     *    整个砍掉它，结果首帧按窄视口渲染、onPageFinished 才注入 → 「先窄后宽」
     *    闪一下，资源只显示左半边。
     *  - **原生 setInitialScale**：load 前设置，部分 ROM 下唯一被理睬的初始缩放通道。
     *  - **zoomBy（onPageFinished 检测未落实时）**：前两条都靠不住 —— meta 的 width
     *    大于屏宽时 Chromium 的 mobile viewport 计算直接丢弃 initial-scale
     *    （inner=1283 但视觉 scale=1.0，只看得见左 28%，1.6.4 报的就是它），
     *    setInitialScale 也会被同一计算覆盖。zoomBy 走 page scale 通道、不经 meta
     *    解析，是唯一必然生效的手段，见 [applyDesktopZoomFix]。
     */
    fun applyDesktopViewportOverride(enabled: Boolean): Boolean {
        viewportScriptHandler?.remove()
        viewportScriptHandler = null
        desktopViewportOverrideActive = false
        // 模式切换 = 新一轮协商，允许再自愈一次
        viewportRetried = false
        if (!enabled) {
            // 切回手机版：恢复系统默认缩放（WebView.setInitialScale 0 = 默认；
            // 该 API 没有 getter，无需也无法保存旧值），否则窄视口页会被继续缩小
            webView.setInitialScale(0)
            return false
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            NexioLog.w(TAG, AndroidMiscStr.logNoDocStart())
            return false
        }
        // 初始缩放必须在 loadUrl 之前设置：renderer 每次加载读取一次。
        // API 在 WebView 上而非 WebSettings 上（API 37 的 WebSettings 已无此方法）。
        webView.setInitialScale(desktopInitialScalePercent())
        return try {
            viewportScriptHandler = WebViewCompat.addDocumentStartJavaScript(
                webView,
                buildDesktopViewportScript(desktopViewportContent()),
                setOf("*")
            )
            desktopViewportOverrideActive = true
            true
        } catch (e: Exception) {
            // 注入失败就退回 onPageFinished 兜底，缩放仍由 zoomBy 兜底修
            NexioLog.w(TAG, AndroidMiscStr.logViewportInjectFailed(), e)
            false
        }
    }

    /**
     * 原生初始缩放百分比（`WebView.setInitialScale`，API 37 起 WebSettings 上已无此方法）。
     *
     * 值 = 屏幕 CSS 宽 / 布局宽：布局固定 1280、屏幕 411dp → 32%，正好一屏。
     */
    private fun desktopInitialScalePercent(): Int {
        val cssWidth = viewportWidthInCssPx()
        val layoutWidth = maxOf(DESKTOP_LAYOUT_WIDTH, cssWidth)
        return (cssWidth * 100.0 / layoutWidth).toInt().coerceIn(10, 100)
    }

    /** 探当前视觉缩放；取不到回调 -1 */
    private fun probeVisualScale(view: WebView, onResult: (Double) -> Unit) {
        view.evaluateJavascript(
            "(function(){var v=window.visualViewport?window.visualViewport.scale:-1;return ''+v})()"
        ) { r ->
            val parsed = r?.let { raw ->
                Regex("-?\\d+(?:\\.\\d+)?").findAll(raw).firstOrNull()?.value?.toDoubleOrNull()
            }
            onResult(parsed ?: -1.0)
        }
    }

    /**
     * 把视觉缩放掰到桌面期望值。理由见 [applyDesktopViewportOverride]：只有 page scale
     * 通道必然生效。zoomBy 有过渡动画，落定后复检一次，仍偏差就按新 scale 再掰一次。
     */
    private fun applyDesktopZoomFix(view: WebView, attempt: Int = 0) {
        val expect = desktopInitialScalePercent() / 100.0
        probeVisualScale(view) { current ->
            if (current <= 0) return@probeVisualScale
            if (kotlin.math.abs(current - expect) > 0.03) {
                NexioLog.d(TAG, "zoomFix: current=$current expect=$expect attempt=$attempt")
                view.post { view.zoomBy((expect / current).toFloat()) }
                if (attempt < 1) {
                    view.postDelayed({ applyDesktopZoomFix(view, attempt + 1) }, 400)
                }
            }
        }
    }

    /**
     * onPageFinished 兜底注入的视口 content（老 WebView / document-start 注册失败时用）。
     *
     * 布局宽度取 max(1280, 屏幕宽度)：门户 `.main-content` 上限 1600、登录页 `#login`
     * 固定 1000 宽，1280 下两者都完整可见。initial-scale 必须自洽地取
     * `屏幕宽 / 布局宽`：写死 0.28 时 Chrome 会把布局宽撑成 屏宽/0.28≈1403，多一截。
     */
    private fun desktopViewportContent(): String {
        val cssWidth = viewportWidthInCssPx()
        val layoutWidth = maxOf(DESKTOP_LAYOUT_WIDTH, cssWidth)
        val scale = cssWidth.toDouble() / layoutWidth
        // 必须锁 Locale：默认区域（如德语）会把小数点写成逗号，CSS 直接失效
        return "width=$layoutWidth, initial-scale=" + "%.4f".format(java.util.Locale.US, scale)
    }

    /**
     * WebView 的 CSS 像素宽度：优先实际布局宽度（分屏/自由窗口下才正确），未布局时退回屏幕宽度
     */
    private fun viewportWidthInCssPx(): Int {
        val density = webView.resources.displayMetrics.density
        if (density > 0f && webView.width > 0) {
            val cssWidth = (webView.width / density).toInt()
            if (cssWidth > 0) return cssWidth
        }
        return webView.context.resources.configuration.screenWidthDp
    }

    /**
     * 增强 WebView 基础配置（JS / 存储 / Cookie / 缩放 / 桌面视口两选项）
     */
    fun enhanceSettings(isDesktopMode: Boolean): WebCompatDelegate {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // 三项在 API 33 起废弃且不再生效（本地文件访问被安全策略收紧），
            // 仅旧版本还有意义，按版本设置而非无条件打开
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                databaseEnabled = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
            }

            // 两项都保持 true（Chromium「like a browser」配置）：useWideViewPort 让 width=1280
            // 生效，loadWithOverviewMode 负责「内容宽于屏幕就缩到一屏」。关掉后页面按 100%
            // 渲染 1283 宽布局，手机屏只看得到 28%，别改。
            useWideViewPort = true
            loadWithOverviewMode = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        return this
    }

    /** 包装 WebViewClient：请求拦截 + 各回调转发给 original + 视口注入/自愈 */
    fun wrapWebViewClient(original: WebViewClient, isDesktopMode: Boolean): WebViewClient {
        // 转发通道的响应不进 WebView 缓存，靠 OkHttp 侧的 Cache 补（见 installCache）
        WebViewRequestInterceptor.installCache(webView.context.applicationContext)
        val interceptor = WebViewRequestInterceptor()
        // UA 在 UI 线程读取并缓存：shouldInterceptRequest 跑在后台线程，
        // 后台访问 WebView settings 会抛异常
        val currentUserAgent = webView.settings.userAgentString
        return object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request != null) {
                    val interceptedResponse = interceptor.intercept(request, currentUserAgent)
                    if (interceptedResponse != null) {
                        return interceptedResponse
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            // ⚠ 两个重载都必须转发给 original：自定义 scheme 外跳写在那里。漏转发 =
            // WebView 按默认行为当 URL 加载 → onReceivedError 误判"回退上一页"，
            // 表现就是点了支付/跳转没反应（外壳迁 :ui-shared 时真漏过一次）。
            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                original.shouldOverrideUrlLoading(view, url)

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
                original.shouldOverrideUrlLoading(view, request)

            // ⚠ 同样必须转发：4xx/5xx 只走这个回调（onReceivedError 只报网络层失败）。
            // 静态资源被 WAF 拦（403）、防盗链、路径变更 404，全靠它现形。
            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) = original.onReceivedHttpError(view, request, errorResponse)

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                original.onPageStarted(view, url, favicon)
                view?.evaluateJavascript(JS_INTERCEPT_POST, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                original.onPageFinished(view, url)
                view?.let { wv ->
                    wv.evaluateJavascript(JS_INTERCEPT_POST, null)
                    wv.evaluateJavascript(JS_PROMISE_BRIDGE, null)
                    // 视口已由 document-start 保证；只有老 WebView / 注册失败才兜底注入
                    if (isDesktopMode && !desktopViewportOverrideActive) {
                        injectDesktopViewport(wv)
                    }
                    // 缩放自愈：meta initial-scale 与 setInitialScale 都可能被
                    // Chromium 丢弃（见 applyDesktopViewportOverride 注释），
                    // 丢了就是 inner=1283 但 scale=1.0，只看得见左 28%。
                    if (isDesktopMode && !viewportRetried) {
                        probeVisualScale(wv) { vvScale ->
                            if (vvScale <= 0) return@probeVisualScale
                            val expect = desktopInitialScalePercent() / 100.0
                            NexioLog.d(TAG, "viewport: vvScale=$vvScale expect=$expect")
                            if (kotlin.math.abs(vvScale - expect) > 0.05) {
                                viewportRetried = true
                                applyDesktopZoomFix(wv)
                            }
                        }
                    }
                }
            }

            override fun onReceivedSslError(v: WebView, h: SslErrorHandler, e: android.net.http.SslError) =
                original.onReceivedSslError(v, h, e)

            override fun onReceivedError(v: WebView, q: WebResourceRequest, e: WebResourceError) =
                original.onReceivedError(v, q, e)
        }
    }

    /**
     * onPageFinished 兜底注入桌面 viewport（仅老 WebView / document-start 注册失败）。
     * 页面脚本已执行完，只能纠正页面自身布局，站点脚本写死的像素尺寸无力回天。
     */
    private fun injectDesktopViewport(view: WebView) {
        val content = desktopViewportContent()
        view.evaluateJavascript(
            """
            (function() {
                var metas = document.getElementsByTagName('meta');
                for (var i = metas.length - 1; i >= 0; i--) {
                    if (metas[i].getAttribute('name') === 'viewport') metas[i].parentNode.removeChild(metas[i]);
                }
                var meta = document.createElement('meta');
                meta.name = "viewport";
                meta.content = "$content";
                document.head.appendChild(meta);
            })();
            """.trimIndent(),
            null
        )
    }

    fun wrapWebChromeClient(original: WebChromeClient, onProgress: (Int) -> Unit): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgress(newProgress)
                original.onProgressChanged(view, newProgress)
            }

            override fun onReceivedTitle(v: WebView?, t: String?) = original.onReceivedTitle(v, t)

            // ⚠ 必须转发：Chromium 自己的诊断（"Refused to apply style ... MIME type"、
            // "Mixed Content ... was blocked"）只走 onConsoleMessage。漏转发 = 静态资源
            // 为什么没生效一行日志都没有，只能看到"页面没样式"。
            override fun onConsoleMessage(m: ConsoleMessage?): Boolean = original.onConsoleMessage(m)
        }
    }
}
