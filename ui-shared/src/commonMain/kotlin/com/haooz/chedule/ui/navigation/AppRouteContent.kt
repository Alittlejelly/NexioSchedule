package com.haooz.chedule.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import com.haooz.chedule.data.DiagnosticRecorder
import com.haooz.chedule.data.NoopDiagnosticRecorder
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.AppreciateAuthorScreen
import com.haooz.chedule.ui.activities.ChangelogScreen
import com.haooz.chedule.ui.activities.CommunicationScreen
import com.haooz.chedule.ui.activities.EduImportUpdater
import com.haooz.chedule.ui.activities.EducationalImportScreen
import com.haooz.chedule.ui.activities.LicenseScreen
import com.haooz.chedule.ui.activities.PrivacyPolicyScreen
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.web.WebHost
import com.haooz.chedule.ui.web.WebHostConfig
import com.haooz.chedule.ui.web.WebHostEvents
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 路由宿主的**跨平台**部分 —— 从 `:androidApp` 的 `AppRouteContent` 逐批搬过来
 * （第三十批起步：教务导入 + 关于页）。
 *
 * ## 为什么 Android 侧还留一份
 *
 * 宿主装配永远有两半：
 * - **跨平台的一半**（在这里）：路由跳转、状态接线、Updater 订阅 —— iOS / 桌面照抄即用
 * - **平台专有的一半**（`:androidApp` 的 `Android*Route` 包装）：`Context` / `Activity` /
 *   权限 / 应用图标 —— 通过**参数注入**进来，本文件一行平台代码都不碰
 *
 * ⚠ 命名纪律：Android 侧的同名包装一律加 `Android` 前缀（与既有 `AndroidSettingsScreen`
 * / `AndroidWebHost` 同款），否则跨模块同名同包，排查时会分不清调的是哪一份。
 */

/**
 * 教务导入路由。
 *
 * @param webHostFactory 网页宿主工厂：Android 传 `AndroidWebHost`，iOS 传将来的
 *   `IosWebHost`（WKWebView）。**这是本路由唯一必须注入的平台能力**。
 * @param isInFreeformWindow 自由窗口判定：Android 读 `Activity.isInMultiWindowMode`，
 *   桌面/iOS 传 false（那两边没有自由窗口概念）。
 */
@Composable
fun EducationalImportRoute(
    router: AppRouter,
    webHostFactory: @Composable (WebHostConfig, WebHostEvents) -> WebHost,
    isInFreeformWindow: Boolean = false,
) {
    // 进入页面即触发脚本索引更新（原 EducationalImportActivity.onCreate 时机，7 天节流）
    LaunchedEffect(Unit) { EduImportUpdater.startUpdateIfNeeded() }
    val isUpdating by EduImportUpdater.isUpdating.collectAsState()
    val dataVersion by EduImportUpdater.dataVersion.collectAsState()

    EducationalImportScreen(
        webHostFactory = webHostFactory,
        isInFreeformWindow = isInFreeformWindow,
        isUpdating = isUpdating,
        dataVersion = dataVersion,
        onForceUpdate = { EduImportUpdater.forceUpdate() },
        onFinish = { router.popBack() },
    )
}

/**
 * 关于页路由。
 *
 * 四个「只能由平台给」的值全部走参数：版本号、应用名、裁好边的应用图标、退出应用。
 * Android 从 `packageManager` / `Activity.finishAffinity()` 取，iOS 从 `NSBundle` 取，
 * 桌面给常量 —— 本路由不关心来源。
 */
@Composable
fun AboutRoute(
    router: AppRouter,
    appVersion: String = "未知版本",
    appName: String = "Nexio课程表",
    appIcon: ImageBitmap? = null,
    onExitApp: () -> Unit = {},
    diagnosticRecorder: DiagnosticRecorder = NoopDiagnosticRecorder,
) {
    AboutScreen(
        onBack = { router.popBack() },
        liquidGlassBackdrop = rememberLayerBackdrop(),
        onNavigate = { router.navigate(it) },
        appVersion = appVersion,
        appName = appName,
        appIcon = appIcon,
        onExitApp = onExitApp,
        diagnosticRecorder = diagnosticRecorder,
    )
}

// ── 纯文档壳路由（第三十三批）：DocumentPageScaffold + 单个已迁 Screen，零平台触点 ──
// 这五个的形状完全一样（标题 + 返回 + 正文），Android 侧同名 `Android*` 包装都不需要，
// `:androidApp` 的 AppRouteContent 直接调这里。

/** 更新日志路由。 */
@Composable
fun ChangelogRoute(router: AppRouter) {
    DocumentPageScaffold(title = "更新日志", onBack = { router.popBack() }) { scrollBehavior, _ ->
        ChangelogScreen(scrollBehavior = scrollBehavior)
    }
}

/** 开源协议路由。 */
@Composable
fun LicenseRoute(router: AppRouter) {
    DocumentPageScaffold(title = "开源协议", onBack = { router.popBack() }) { scrollBehavior, _ ->
        LicenseScreen(scrollBehavior = scrollBehavior)
    }
}

/** 隐私政策路由。 */
@Composable
fun PrivacyPolicyRoute(router: AppRouter) {
    DocumentPageScaffold(title = "隐私政策", onBack = { router.popBack() }) { scrollBehavior, _ ->
        PrivacyPolicyScreen(scrollBehavior = scrollBehavior)
    }
}

/** 交流与反馈路由。 */
@Composable
fun CommunicationRoute(router: AppRouter) {
    DocumentPageScaffold(title = "交流与反馈", onBack = { router.popBack() }) { scrollBehavior, _ ->
        CommunicationScreen(scrollBehavior = scrollBehavior)
    }
}

/** 捐赠支持路由。 */
@Composable
fun AppreciateAuthorRoute(router: AppRouter) {
    DocumentPageScaffold(title = "捐赠支持", onBack = { router.popBack() }) { scrollBehavior, _ ->
        AppreciateAuthorScreen(scrollBehavior = scrollBehavior)
    }
}
