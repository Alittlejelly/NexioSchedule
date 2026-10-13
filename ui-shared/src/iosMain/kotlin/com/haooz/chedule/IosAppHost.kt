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
    private val overlays = HostOverlayState()
    private val popupHost = MiuixOverlayHostState()
    private val nativePages = mutableStateListOf<NativeDocumentPage>()
    private var nextNativePageId = 0L
    // Mirror AppNavHost's route/occurrence saveable keys without retaining popped controllers.
    private val savedDocumentStates = mutableMapOf<String, Map<String, List<Any?>>>()
    private var requestedPopDepth by mutableStateOf<Int?>(null)
    private var nativeTransitionInFlight by mutableStateOf(false)
    private var deferredEditorPopRouteId: String? = null

    init {
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
        if (!route.isNativeDocument() || floatingOverlayActive || nativeContentOverlayActive ||
            nativeTransitionInFlight || requestedPopDepth != null) return
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
    fun nativePageTintArgb(routeId: String): Long = nativePage(routeId).chrome.tintArgb

    // Match the leading slot across list/editor configurations using UIKit item identity.
    fun nativePageLeadingSymbol(routeId: String): String = nativePage(routeId).let {
        it.chrome.editor?.cancel?.symbol ?: if (it.route == AppRoute.CourseTimeSettings || it.route == AppRoute.CourseManage) "chevron.backward" else ""
    }
    fun nativePageLeadingLabel(routeId: String): String =
        nativePage(routeId).chrome.editor?.cancel?.label ?: "返回"
    fun nativePageLeadingEnabled(routeId: String): Boolean =
        pageVisibleAndInteractive(routeId) &&
            nativePage(routeId).chrome.editor?.cancel?.let { it.enabled && !it.busy } != false

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

    fun performNativePageLeadingAction(routeId: String) {
        if (!nativePageLeadingEnabled(routeId)) return
        val editor = nativePage(routeId).chrome.editor
        if (editor != null) editor.cancel.onClick()
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

    val selectedTab: Int get() = navigation.selectedTab
    val tabCount: Int get() = navigation.tabCount
    val shiftMode: Boolean get() = navigation.shiftMode
    val showsNativeTabBar: Boolean get() = navigation.showsNativeTabBar
    val showsNativeHeader: Boolean get() = navigation.showsNativeHeader
    val isDark: Boolean get() = navigation.isDark
    val overlayActive: Boolean
        get() = router.hasOverlay || navigationOverlayActive || overlays.isActive || popupHost.isActive
    val mainProgress: Double get() = mainTransition.value.toDouble()
    val cutoutScale: Float get() = navigation.cutoutScale
    val cutoutCenterYRatio: Float get() = navigation.cutoutCenterYRatio

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
                    MiuixPopupHost()
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
                            nativePages.map { page ->
                                val action = page.chrome.effectiveAction
                                val editor = page.chrome.editor
                                NativeDocumentChromeSnapshot(
                                    editor?.title ?: page.chrome.title, page.chrome.tintArgb,
                                    action?.symbol, action?.label, action?.enabled,
                                    action?.busy, action?.successful, page.chrome.contentOverlayActive,
                                    editor != null, editor?.cancel?.symbol, editor?.cancel?.label,
                                    editor?.cancel?.enabled,
                                )
                            },
                            nativeTransitionInFlight,
                            showsNativeHeader,
                        )
                    }.collect { onStateChanged?.invoke() }
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
        if (kotlin.math.abs(navigation.topBarInset - inset) > 0.5) {
            navigation.topBarInset = inset
        }
    }

    @Composable
    private fun HostEnvironment(content: @Composable () -> Unit) {
        CourseScheduleTheme {
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
)

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
    AppRoute.About -> "关于应用"
    AppRoute.CourseTimeSettings -> "课表节数与时间"
    // Import still reaches EducationalImport, whose iOS WebHost is not implemented.
    AppRoute.ScheduleImport, AppRoute.EducationalImport -> null
    AppRoute.CourseManage -> "课程管理"
}
