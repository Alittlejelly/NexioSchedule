@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.haooz.chedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.NativeDocumentChrome
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.navigation.NavTransitionState
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.HostNavigationState
import com.haooz.chedule.ui.utils.HostOverlayContent
import com.haooz.chedule.ui.utils.HostOverlayState
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.LocalHostOverlays
import com.haooz.chedule.ui.utils.rememberScreenCornerRadiusPx
import androidx.compose.ui.platform.LocalDensity
import platform.UIKit.UIViewController
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixOverlayHostProvider
import top.yukonga.miuix.kmp.utils.MiuixOverlayHostState
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

/** One retained app session shared by native document and Compose controllers. */
class IosAppHost {
    internal val router = AppRouter()
    internal val mainTransition = NavTransitionState()
    internal var navigationOverlayActive by mutableStateOf(false)
    internal val navigation = HostNavigationState()
    private var systemDarkTheme by mutableStateOf<Boolean?>(null)
    private val overlays = HostOverlayState()
    private val scheduleLibraryOverlays = HostOverlayState()
    private val popupHost = MiuixOverlayHostState()
    private val nativePages = mutableStateListOf<NativeDocumentPage>()
    private var nextNativePageId = 0L
    // Mirror AppNavHost's route/occurrence saveable keys without retaining popped controllers.
    private val savedDocumentStates = mutableMapOf<String, Map<String, List<Any?>>>()
    private var requestedPopDepth by mutableStateOf<Int?>(null)
    private var nativeTransitionInFlight by mutableStateOf(false)
    private var deferredEditorPopRouteId: String? = null
    private var screenCornerRadiusPointsState by mutableStateOf(0.0)

    /**
     * 屏幕圆角（pt）。原生文档转场由 Swift 侧裁这个圆角 —— 与 Compose 侧 `AppNavHost` 用的是
     * 同一个 `rememberScreenCornerRadiusPx`，两条转场路径才能对得上。0 = 取不到 → 不裁。
     *
     * ⚠ Swift 读不到 Compose 的组合状态，只能从 host 取；写入见 [HostEnvironment]。
     */
    val screenCornerRadiusPoints: Double get() = screenCornerRadiusPointsState

    init {
        navigation.scheduleLibraryOverlays = scheduleLibraryOverlays
        router.nativeRouteHandler = { route ->
            if (route.isNativeDocument()) {
                navigateNativeDocument(route.id)
                true
            } else {
                false
            }
        }
        router.nativeBackHandler = ::requestNativePop
    }

    /** Instance IDs, not route kinds: pushing the same document twice keeps independent state. */
    val nativeRouteIds: List<String>
        get() = nativePages.take(requestedPopDepth ?: nativePages.size).map { it.id }
    val floatingOverlayActive: Boolean get() = overlays.isActive || popupHost.isActive
    val nativePageActive: Boolean get() = nativePages.isNotEmpty()
    val nativePopPending: Boolean get() = requestedPopDepth != null
    val nativeContentOverlayActive: Boolean
        get() = nativePages.lastOrNull()?.chrome?.let { it.contentOverlayActive || it.editor != null } == true

    fun nativeRouteTitle(routeId: String): String =
        nativePage(routeId).let {
            it.chrome.editor?.title ?: it.chrome.title.ifEmpty { it.route.nativeDocumentTitle().orEmpty() }
        }

    fun navigateNativeDocument(routeId: String) {
        val route = AppRoute.fromId(routeId) ?: return
        // ⚠ guard 只拦 UIKit 自己的转场状态（编辑器开着 / 转场中 / 有待处理的 pop）。
        // ⚠ 绝不拦 floatingOverlayActive（Compose 弹窗）：弹窗与 pushViewController 互不干扰，
        // 拦了会让「选适配器」弹窗里点学校被静默吞掉（Android 无此 guard）。
        if (!route.isNativeDocument() || nativeContentOverlayActive ||
            nativeTransitionInFlight || requestedPopDepth != null
        ) return
        val usedKeys = nativePages.map { it.stateKey }.toSet()
        var stateKey = route.id
        var occurrence = 2
        while (stateKey in usedKeys) stateKey = "${route.id}#${occurrence++}"
        nativePages.add(NativeDocumentPage(
            id = "native-document-${++nextNativePageId}",
            route = route,
            stateKey = stateKey,
            restoredValues = savedDocumentStates[stateKey],
        ))
    }

    /** Request a UIKit pop; keep the page, its registry, and business state alive until didShow. */
    private fun requestNativePop(): Boolean {
        if (nativePages.isEmpty()) return false
        // Route-local morph editors must finish their own back/save path first.
        if (nativeContentOverlayActive) return true
        if (!nativeTransitionInFlight && requestedPopDepth == null) {
            requestedPopDepth = nativePages.lastIndex
        }
        return true
    }

    fun setNativeTransitionInFlight(inFlight: Boolean) {
        nativeTransitionInFlight = inFlight
    }

    /** UIKit calls this only after a successful pop; an interactive cancellation changes nothing. */
    fun completeNativePop(depth: Int) {
        if (depth !in 0..nativePages.size) return
        while (nativePages.size > depth) {
            val page = nativePages.removeAt(nativePages.lastIndex)
            savedDocumentStates[page.stateKey] = page.registry.performSave()
            // Do not cache live compositions: page disposal retains its original exit semantics.
            page.controller = null
        }
        requestedPopDepth = null
    }

    fun updateNativePageBarInset(routeId: String, points: Double) {
        val page = nativePages.firstOrNull { it.id == routeId } ?: return
        if (!points.isFinite()) return
        val inset = points.coerceAtLeast(0.0)
        if (kotlin.math.abs(page.chrome.barInset - inset) > 0.5) page.chrome.barInset = inset
    }

    fun nativePageEditorActive(routeId: String): Boolean = nativePage(routeId).chrome.editor != null
    fun nativePageActionSymbol(routeId: String): String = nativePage(routeId).chrome.effectiveAction?.symbol.orEmpty()
    fun nativePageActionLabel(routeId: String): String = nativePage(routeId).chrome.effectiveAction?.label.orEmpty()
    fun nativePageActionEnabled(routeId: String): Boolean =
        pageControlsAllowed(routeId) && nativePage(routeId).chrome.effectiveAction?.enabled == true
    fun nativePageActionBusy(routeId: String): Boolean = nativePage(routeId).chrome.effectiveAction?.busy == true
    fun nativePageActionSuccessful(routeId: String): Boolean = nativePage(routeId).chrome.effectiveAction?.successful == true
    fun nativePageSecondaryActionSymbol(routeId: String): String = nativePage(routeId).chrome.effectiveSecondaryAction?.symbol.orEmpty()
    fun nativePageSecondaryActionLabel(routeId: String): String = nativePage(routeId).chrome.effectiveSecondaryAction?.label.orEmpty()
    fun nativePageSecondaryActionEnabled(routeId: String): Boolean =
        pageControlsAllowed(routeId) && nativePage(routeId).chrome.effectiveSecondaryAction?.enabled == true
    fun nativePageSecondaryActionBusy(routeId: String): Boolean = nativePage(routeId).chrome.effectiveSecondaryAction?.busy == true
    fun nativePageSecondaryActionSuccessful(routeId: String): Boolean = nativePage(routeId).chrome.effectiveSecondaryAction?.successful == true
    fun nativePageTintArgb(routeId: String): Long = nativePage(routeId).chrome.tintArgb

    // Match the leading slot across list/editor configurations using UIKit item identity.
    fun nativePageLeadingSymbol(routeId: String): String = nativePage(routeId).let {
        it.chrome.effectiveLeading?.symbol ?: if (it.route == AppRoute.CourseTimeSettings || it.route == AppRoute.CourseManage) "chevron.backward" else ""
    }
    fun nativePageLeadingLabel(routeId: String): String =
        nativePage(routeId).chrome.effectiveLeading?.label ?: "返回"
    fun nativePageLeadingEnabled(routeId: String): Boolean =
        pageVisibleAndInteractive(routeId) &&
            nativePage(routeId).chrome.effectiveLeading?.let { it.enabled && !it.busy } != false

    private fun pageVisibleAndInteractive(routeId: String): Boolean =
        !nativeTransitionInFlight && requestedPopDepth == null && !floatingOverlayActive &&
            nativePages.lastOrNull()?.id == routeId

    private fun pageControlsAllowed(routeId: String): Boolean =
        pageVisibleAndInteractive(routeId) &&
            (!nativeContentOverlayActive || nativePageEditorActive(routeId))

    fun performNativePageAction(routeId: String) {
        if (!pageControlsAllowed(routeId)) return
        val action = nativePage(routeId).chrome.effectiveAction ?: return
        if (action.enabled && !action.busy) action.onClick()
    }

    fun performNativePageSecondaryAction(routeId: String) {
        if (!pageControlsAllowed(routeId)) return
        val action = nativePage(routeId).chrome.effectiveSecondaryAction ?: return
        if (action.enabled && !action.busy) action.onClick()
    }

    /** 本页是否把顶栏整个交给系统（原生导航栏显示标题、页面不画 Compose 顶栏） */
    fun nativePageUsesSystemHeader(routeId: String): Boolean =
        nativePage(routeId).chrome.usesSystemHeader

    // ── 底部悬浮条（用系统控件渲染，见 Swift 侧 NativeDocumentNavigation）──

    // ⚠ 只判本页有没有底栏：底栏属于页面内容，转场时应跟页面一起移动，
    // 不能用 pageVisibleAndInteractive（会让它在转场中突然消失）。
    fun nativePageBottomBarVisible(routeId: String): Boolean =
        nativePage(routeId).chrome.bottomBar != null

    fun nativePageBottomBarTitle(routeId: String): String =
        nativePage(routeId).chrome.bottomBar?.title.orEmpty()

    fun nativePageBottomBarSubtitle(routeId: String): String =
        nativePage(routeId).chrome.bottomBar?.subtitle.orEmpty()

    fun nativePageBottomBarBadge(routeId: String): String =
        nativePage(routeId).chrome.bottomBar?.badge.orEmpty()

    fun nativePageBottomBarToggleSymbol(routeId: String): String =
        nativePage(routeId).chrome.bottomBar?.toggleSymbol.orEmpty()

    fun nativePageBottomBarToggleLabel(routeId: String): String =
        nativePage(routeId).chrome.bottomBar?.toggleLabel.orEmpty()

    fun nativePageBottomBarToggleEnabled(routeId: String): Boolean =
        pageControlsAllowed(routeId) && nativePage(routeId).chrome.bottomBar?.toggleEnabled == true

    fun nativePageBottomBarPrimarySymbol(routeId: String): String =
        nativePage(routeId).chrome.bottomBar?.primarySymbol.orEmpty()

    fun nativePageBottomBarPrimaryLabel(routeId: String): String =
        nativePage(routeId).chrome.bottomBar?.primaryLabel.orEmpty()

    fun nativePageBottomBarPrimaryEnabled(routeId: String): Boolean =
        pageControlsAllowed(routeId) && nativePage(routeId).chrome.bottomBar?.primaryEnabled == true

    fun performNativePageBottomToggle(routeId: String) {
        if (!pageControlsAllowed(routeId)) return
        val bar = nativePage(routeId).chrome.bottomBar ?: return
        if (bar.toggleEnabled) bar.onToggle()
    }

    fun performNativePageBottomPrimary(routeId: String) {
        if (!pageControlsAllowed(routeId)) return
        val bar = nativePage(routeId).chrome.bottomBar ?: return
        if (bar.primaryEnabled) bar.onPrimary()
    }

    fun performNativePageLeadingAction(routeId: String) {
        if (!nativePageLeadingEnabled(routeId)) return
        val leading = nativePage(routeId).chrome.effectiveLeading
        if (leading != null) leading.onClick()
        else if (nativeContentOverlayActive) {
            // The parent back button is already visible during the reverse morph.
            // Accept the tap now, but let save/disposal finish before popping its page.
            deferredEditorPopRouteId = routeId
        } else requestNativePop()
    }

    fun reconcileNativeEditorExit() {
        val id = deferredEditorPopRouteId ?: return
        if (nativePages.lastOrNull()?.id != id) {
            deferredEditorPopRouteId = null
        } else if (!nativeContentOverlayActive && pageVisibleAndInteractive(id)) {
            deferredEditorPopRouteId = null
            requestNativePop()
        }
    }

    fun makeNativePageController(routeId: String): UIViewController {
        val page = nativePage(routeId)
        return page.controller ?: ComposeUIViewController {
            HostEnvironment {
                CompositionLocalProvider(
                    LocalNativeDocumentChrome provides page.chrome,
                    LocalSaveableStateRegistry provides page.registry,
                    // ⚠ 必须给：页里 navigate 读 LocalAppRouter，而它默认值是 error(...)
                    LocalAppRouter provides router,
                ) {
                    Surface(Modifier.fillMaxSize(), color = MiuixTheme.colorScheme.surface) {
                        NexioIosRouteContent(router, page.route)
                    }
                }
            }
        }.also { page.controller = it }
    }

    internal fun nativePage(id: String): NativeDocumentPage =
        nativePages.firstOrNull { it.id == id } ?: error("Unknown native document instance: $id")

    var onStateChanged: (() -> Unit)? = null

    val scheduleLibraryActive: Boolean get() = navigation.scheduleLibraryActive
    val scheduleLibraryTransitionActive: Boolean get() = navigation.scheduleLibraryTransitionActive
    val scheduleLibraryTitle: String get() = navigation.scheduleLibraryChrome.title.ifEmpty { "全部课表" }
    val scheduleLibraryTintArgb: Long get() = navigation.scheduleLibraryChrome.tintArgb
    fun scheduleLibraryLeadingSymbol(): String = navigation.scheduleLibraryChrome.effectiveLeading?.symbol.orEmpty()
    fun scheduleLibraryLeadingLabel(): String = navigation.scheduleLibraryChrome.effectiveLeading?.label.orEmpty()
    fun scheduleLibraryLeadingEnabled(): Boolean = scheduleLibraryControlsAllowed() &&
        navigation.scheduleLibraryChrome.effectiveLeading?.let { it.enabled && !it.busy } == true
    fun scheduleLibraryActionSymbol(): String = navigation.scheduleLibraryChrome.effectiveAction?.symbol.orEmpty()
    fun scheduleLibraryActionLabel(): String = navigation.scheduleLibraryChrome.effectiveAction?.label.orEmpty()
    fun scheduleLibraryActionEnabled(): Boolean = scheduleLibraryTrailingAllowed() &&
        navigation.scheduleLibraryChrome.effectiveAction?.let { it.enabled && !it.busy } == true
    fun scheduleLibraryActionBusy(): Boolean = navigation.scheduleLibraryChrome.effectiveAction?.busy == true
    fun scheduleLibraryActionSuccessful(): Boolean = navigation.scheduleLibraryChrome.effectiveAction?.successful == true
    fun scheduleLibrarySecondaryActionSymbol(): String = navigation.scheduleLibraryChrome.effectiveSecondaryAction?.symbol.orEmpty()
    fun scheduleLibrarySecondaryActionLabel(): String = navigation.scheduleLibraryChrome.effectiveSecondaryAction?.label.orEmpty()
    fun scheduleLibrarySecondaryActionEnabled(): Boolean = scheduleLibraryTrailingAllowed() &&
        navigation.scheduleLibraryChrome.effectiveSecondaryAction?.let { it.enabled && !it.busy } == true
    fun scheduleLibrarySecondaryActionBusy(): Boolean = navigation.scheduleLibraryChrome.effectiveSecondaryAction?.busy == true
    fun scheduleLibrarySecondaryActionSuccessful(): Boolean = navigation.scheduleLibraryChrome.effectiveSecondaryAction?.successful == true

    private fun scheduleLibraryControlsAllowed(): Boolean = scheduleLibraryActive &&
        !floatingOverlayActive && !nativePageActive
    private fun scheduleLibraryTrailingAllowed(): Boolean = scheduleLibraryControlsAllowed() &&
        !navigation.scheduleLibraryChrome.contentOverlayActive

    fun performScheduleLibraryLeadingAction() {
        if (scheduleLibraryLeadingEnabled()) navigation.scheduleLibraryChrome.effectiveLeading?.onClick?.invoke()
    }

    fun performScheduleLibraryAction() {
        if (scheduleLibraryActionEnabled()) navigation.scheduleLibraryChrome.effectiveAction?.onClick?.invoke()
    }

    fun performScheduleLibrarySecondaryAction() {
        if (scheduleLibrarySecondaryActionEnabled()) navigation.scheduleLibraryChrome.effectiveSecondaryAction?.onClick?.invoke()
    }

    /** System appearance comes from SwiftUI above all app and wallpaper style overrides. */
    fun updateSystemDarkTheme(isDark: Boolean) {
        systemDarkTheme = isDark
    }

    val selectedTab: Int get() = navigation.selectedTab
    val tabCount: Int get() = navigation.tabCount
    val shiftMode: Boolean get() = navigation.shiftMode
    val showsNativeTabBar: Boolean get() = navigation.showsNativeTabBar
    val showsNativeHeader: Boolean get() = navigation.showsNativeHeader
    val keepsNativeHeaderDuringCutout: Boolean get() = navigation.keepsNativeHeaderDuringCutout
    val isDark: Boolean get() = navigation.isDark
    val overlayActive: Boolean
        get() = router.hasOverlay || navigationOverlayActive || overlays.isActive || popupHost.isActive || scheduleLibraryOverlays.isActive
    val mainProgress: Double get() = mainTransition.value.toDouble()
    val cutoutScale: Float get() = navigation.cutoutScale
    val cutoutCenterYRatio: Float get() = navigation.cutoutCenterYRatio

    /**
     * Per-frame push of the main content's vertical offset while the customize page's sheet is
     * open (pt, negative = up, `-1` = none). Called from Compose's **draw phase** on the main
     * thread, so UIKit applies it in the same frame as the content it has to follow.
     * Assigned by Swift; must stay a plain callback — routing it through host state would put
     * it a frame or two behind.
     */
    var onCutoutChromeOffset: ((Double) -> Unit)? = null
    var onDetailChromeScale: ((Double) -> Unit)? = null

    init {
        navigation.onCutoutChromeOffsetY = { offsetY -> onCutoutChromeOffset?.invoke(offsetY) }
        navigation.onDetailChromeScale = { scale -> onDetailChromeScale?.invoke(scale) }
    }

    val mainController: UIViewController by lazy {
        ComposeUIViewController {
            HostEnvironment {
                Surface(Modifier.fillMaxSize()) { NexioIosMain(this@IosAppHost) }
            }
        }
    }

    val overlayController: UIViewController by lazy {
        ComposeUIViewController(configure = { opaque = false }) {
            HostEnvironment {
                Box(Modifier.fillMaxSize()) {
                    NexioIosNavigation(this@IosAppHost)
                    HostOverlayContent(scheduleLibraryOverlays)
                }
            }
        }
    }

    /** Popups remain above both legacy Compose routes and native navigation chrome. */
    val floatingController: UIViewController by lazy {
        ComposeUIViewController(configure = { opaque = false }) {
            HostEnvironment {
                Box(Modifier.fillMaxSize()) {
                    HostOverlayContent(overlays) {
                        nativePages.isEmpty() && !router.hasOverlay && !navigationOverlayActive
                    }
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        val scale = navigation.detailBackgroundScale
                        scaleX = scale
                        scaleY = scale
                        val blur = ((1f - scale) / 0.08f).coerceIn(0f, 1f) * 5.dp.toPx()
                        renderEffect = if (blur > 0f) BlurEffect(null, blur, blur, TileMode.Clamp) else null
                    }) {
                        MiuixPopupHost()
                    }
                    // 顺序即层级：课程详情页（overlay）必须盖在弹窗层之上
                    HostOverlayContent(overlays, abovePopups = true)
                }
                val appDark = isAppDarkTheme()
                val hasNativePages = nativePages.isNotEmpty()
                SideEffect {
                    if (hasNativePages) {
                        navigation.isDark = appDark
                    }
                }
                LaunchedEffect(this@IosAppHost) {
                    snapshotFlow {
                        NativeHostSnapshot(
                            selectedTab, tabCount, shiftMode, showsNativeTabBar,
                            isDark, overlayActive, mainProgress, cutoutScale, cutoutCenterYRatio,
                            nativeRouteIds, floatingOverlayActive,
                            // 逐字段快照（含底栏与系统标题开关）—— 见 NativeDocumentChrome.snapshot
                            nativePages.map { it.chrome.snapshot() },
                            nativeTransitionInFlight,
                            showsNativeHeader,
                            keepsNativeHeaderDuringCutout,
                            scheduleLibraryActive,
                            scheduleLibraryTransitionActive,
                            navigation.scheduleLibraryChrome.snapshot(),
                        )
                    }.collect {
                        onStateChanged?.invoke()
                    }
                }
            }
        }
    }

    fun selectTab(index: Int) {
        if (!overlayActive && !nativePageActive && showsNativeTabBar && index in 0 until tabCount) {
            navigation.onTabSelected?.invoke(index)
        }
    }

    fun performMainAction(actionId: String) {
        when (actionId) {
            "switchSchedule" -> navigation.onOpenSwitchSchedule?.invoke()
            "jumpWeek" -> navigation.onJumpWeek?.invoke()
            "courseManage" -> navigation.onCourseManage?.invoke()
            "customize" -> navigation.onEnterCustomize?.invoke()
            "jumpToDate" -> navigation.onJumpToDate?.invoke()
        }
    }

    fun updateBottomBarInset(points: Double) {
        val inset = points.coerceAtLeast(0.0)
        if (kotlin.math.abs(navigation.bottomBarInset - inset) > 0.5) {
            navigation.bottomBarInset = inset
        }
    }

    fun updateTopBarInset(points: Double) {
        val inset = points.coerceAtLeast(0.0)
        // The retained content controller inherits the navigation bar's safe-area overlap.
        if (kotlin.math.abs(navigation.scheduleLibraryChrome.barInset - inset) > 0.5) {
            navigation.scheduleLibraryChrome.barInset = inset
        }
        if (kotlin.math.abs(navigation.topBarInset - inset) > 0.5) {
            navigation.topBarInset = inset
        }
    }

    @Composable
    private fun HostEnvironment(content: @Composable () -> Unit) {
        CourseScheduleTheme(systemDarkTheme = systemDarkTheme) {
            // 屏幕圆角递进 host，供 Swift 的原生转场裁切。三个 controller 都走这里，所以加条件：
            // 取到之前才探测 —— 探测要往 window 里临时插一个 probe view，跑三次纯属浪费。
            if (screenCornerRadiusPoints == 0.0) ScreenCornerRadiusBridge()
            MiuixOverlayHostProvider(popupHost) {
                CompositionLocalProvider(
                    LocalAppRouter provides router,
                    LocalHostNavigation provides navigation,
                    LocalHostOverlays provides overlays,
                    content = content,
                )
            }
        }
    }

    /** 把 [rememberScreenCornerRadiusPx] 的结果换算成 pt 写进 [screenCornerRadiusPoints]。 */
    @Composable
    private fun ScreenCornerRadiusBridge() {
        val density = LocalDensity.current
        val radiusPx = rememberScreenCornerRadiusPx()
        SideEffect {
            screenCornerRadiusPointsState = with(density) { radiusPx.toDp().value }.toDouble()
        }
    }
}

private data class NativeHostSnapshot(
    val selectedTab: Int,
    val tabCount: Int,
    val shiftMode: Boolean,
    val showsNativeTabBar: Boolean,
    val isDark: Boolean,
    val overlayActive: Boolean,
    val mainProgress: Double,
    val cutoutScale: Float,
    val cutoutCenterYRatio: Float,
    val nativeRouteIds: List<String>,
    val floatingOverlayActive: Boolean,
    val nativeChrome: List<NativeDocumentChromeSnapshot>,
    val nativeTransitionInFlight: Boolean,
    val showsNativeHeader: Boolean,
    val keepsNativeHeaderDuringCutout: Boolean,
    val scheduleLibraryActive: Boolean,
    val scheduleLibraryTransitionActive: Boolean,
    val scheduleLibraryChrome: NativeDocumentChromeSnapshot,
)

private data class NativeDocumentChromeSnapshot(
    val title: String,
    val tintArgb: Long,
    val symbol: String?,
    val label: String?,
    val enabled: Boolean?,
    val busy: Boolean?,
    val successful: Boolean?,
    val contentOverlayActive: Boolean,
    val editorActive: Boolean,
    val leadingSymbol: String?,
    val leadingLabel: String?,
    val leadingEnabled: Boolean?,
    val leadingBusy: Boolean?,
    val secondarySymbol: String?,
    val secondaryLabel: String?,
    val secondaryEnabled: Boolean?,
    val secondaryBusy: Boolean?,
    val secondarySuccessful: Boolean?,
    // ⚠ 底栏与「顶栏交给系统」也要进快照，否则变化不触发 Swift 刷新（底栏不出现 / 标题停在旧值）
    val usesSystemHeader: Boolean,
    val bottomBarVisible: Boolean,
    val bottomTitle: String,
    val bottomSubtitle: String,
    val bottomBadge: String,
    val bottomToggleSymbol: String,
    val bottomPrimarySymbol: String,
    val bottomToggleEnabled: Boolean,
    val bottomPrimaryEnabled: Boolean,
)

private fun NativeDocumentChrome.snapshot(): NativeDocumentChromeSnapshot {
    val action = effectiveAction
    val leading = effectiveLeading
    val secondary = effectiveSecondaryAction
    val bar = bottomBar
    return NativeDocumentChromeSnapshot(
        editor?.title ?: title, tintArgb,
        action?.symbol, action?.label, action?.enabled, action?.busy, action?.successful,
        contentOverlayActive, editor != null,
        leading?.symbol, leading?.label, leading?.enabled, leading?.busy,
        secondary?.symbol, secondary?.label, secondary?.enabled, secondary?.busy, secondary?.successful,
        usesSystemHeader,
        bar != null, bar?.title.orEmpty(), bar?.subtitle.orEmpty(), bar?.badge.orEmpty(),
        bar?.toggleSymbol.orEmpty(), bar?.primarySymbol.orEmpty(),
        bar?.toggleEnabled == true, bar?.primaryEnabled == true,
    )
}

internal class NativeDocumentPage(
    val id: String,
    val route: AppRoute,
    val stateKey: String,
    restoredValues: Map<String, List<Any?>>?,
) {
    val chrome = NativeDocumentChrome()
    // Values remain in memory, just like AppNavHost's SaveableStateHolder on iOS.
    val registry = SaveableStateRegistry(restoredValues = restoredValues, canBeSaved = { true })
    var controller: UIViewController? = null
}

internal fun AppRoute.isNativeDocument(): Boolean = nativeDocumentTitle() != null

internal fun AppRoute.nativeDocumentTitle(): String? = when (this) {
    AppRoute.Changelog -> "更新日志"
    AppRoute.License -> "开源协议"
    AppRoute.PrivacyPolicy -> "隐私政策"
    AppRoute.Communication -> "交流与反馈"
    AppRoute.AppreciateAuthor -> "捐赠支持"
    AppRoute.PreferenceSettings -> "应用偏好设置"
    AppRoute.UpdateSettings -> "更新设置"
    AppRoute.LocalBackup -> "本地备份"
    AppRoute.AiImport -> "AI 文本导入"
    AppRoute.WidgetIntro -> "桌面小部件"
    AppRoute.CourseReminder -> "课程提醒"
    AppRoute.HolidaySettings -> "节假日与调休"
    AppRoute.WebDavSettings -> "WebDAV 云备份"
    AppRoute.ScheduleExport -> "课表导出"
    AppRoute.ScheduleBackup -> "课表备份"
    AppRoute.ScheduleImport -> "课表导入"
    AppRoute.About -> "关于应用"
    AppRoute.CourseTimeSettings -> "课表节数与时间"
    // 网页导入页与选择页同级：必须是原生文档（走 Compose 栈会被原生文档挡住）
    AppRoute.WebImport -> "网页导入"
    // ⚠ 必须是原生文档：从「课表导出/导入/备份」这些原生文档页跳进来，
    // 走 Compose 栈会被上面压着的原生文档挡住（表现为「点了没反应」）
    AppRoute.EducationalImport -> "教务导入"
    AppRoute.CourseManage -> "课程管理"
}
