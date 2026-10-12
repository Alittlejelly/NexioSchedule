package com.haooz.chedule.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import com.haooz.chedule.data.BackupFileController
import com.haooz.chedule.data.DiagnosticRecorder
import com.haooz.chedule.data.NoopBackupFileController
import com.haooz.chedule.data.NoopDiagnosticRecorder
import com.haooz.chedule.data.NoopUpdateController
import com.haooz.chedule.data.UpdateController
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.AiImportScreen
import com.haooz.chedule.ui.activities.AppreciateAuthorScreen
import com.haooz.chedule.ui.activities.ChangelogScreen
import com.haooz.chedule.ui.activities.CommunicationScreen
import com.haooz.chedule.ui.activities.EduImportUpdater
import com.haooz.chedule.ui.activities.EducationalImportScreen
import com.haooz.chedule.ui.activities.LicenseScreen
import com.haooz.chedule.ui.activities.LocalBackupScreen
import com.haooz.chedule.ui.activities.PreferenceSettingsScreen
import com.haooz.chedule.ui.activities.PrivacyPolicyScreen
import com.haooz.chedule.ui.activities.UpdateSettingsScreen
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.utils.isAppDarkTheme
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

// ── 带能力注入的文档壳路由（第三十四批）：壳在 commonMain，平台能力走参数 ──

/**
 * 应用偏好设置路由。
 *
 * 四个平台能力全部参数注入：定位权限检查 / 天气缓存清空 / 最近任务卡片隐藏 /
 * 主题切换后的系统栏刷新。Android 从 `ContextCompat` / `MainActivity` 取，
 * 桌面/iOS 用默认值（前三项空/false，后一项无系统栏概念）。
 */
@Composable
fun PreferenceSettingsRoute(
    router: AppRouter,
    hasLocationPermission: () -> Boolean = { false },
    onInvalidateWeatherCache: () -> Unit = {},
    onSetTaskExcludedFromRecents: (Boolean) -> Unit = {},
    onThemeDarkChanged: (Boolean) -> Unit = {},
) {
    DocumentPageScaffold(
        title = "应用偏好设置",
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        // 偏好设置页可能改主题 → 系统栏要跟着刷新（原来在 Activity 的 setContent 里）
        val isDark = isAppDarkTheme()
        LaunchedEffect(isDark) { onThemeDarkChanged(isDark) }
        PreferenceSettingsScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            hasLocationPermission = hasLocationPermission,
            onInvalidateWeatherCache = onInvalidateWeatherCache,
            onSetTaskExcludedFromRecents = onSetTaskExcludedFromRecents,
        )
    }
}

/**
 * 更新设置路由。
 *
 * @param updateController 更新能力（检查/下载/APK 安装）：Android 注入
 *   `AndroidUpdateController(context)`；桌面/iOS 用默认 [NoopUpdateController]。
 */
@Composable
fun UpdateSettingsRoute(
    router: AppRouter,
    updateController: UpdateController = NoopUpdateController,
) {
    DocumentPageScaffold(
        title = "更新设置",
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        UpdateSettingsScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            updateController = updateController,
        )
    }
}

/**
 * 本地备份路由。
 *
 * @param backupFileController 备份文件能力（SAF 选文件 + 目录扫描读写）：Android 注入
 *   `AndroidBackupFileController(context)`；桌面/iOS 用默认 [NoopBackupFileController]。
 * @param onHolidayDataChanged 全量恢复成功后的平台副作用（重排提醒等），Android 宿主注入。
 */
@Composable
fun LocalBackupRoute(
    router: AppRouter,
    backupFileController: BackupFileController = NoopBackupFileController,
    onHolidayDataChanged: () -> Unit = {},
) {
    DocumentPageScaffold(
        title = "本地备份",
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        LocalBackupScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            backupFileController = backupFileController,
            onHolidayDataChanged = onHolidayDataChanged,
        )
    }
}

/**
 * AI 文本导入路由。
 *
 * @param onCopyToClipboard 复制提示词到系统剪切板：Android 用 `ClipboardManager`，
 *   iOS 用 `UIPasteboard`，桌面用 `Toolkit`。默认空实现。
 */
@Composable
fun AiImportRoute(
    router: AppRouter,
    onCopyToClipboard: (String) -> Unit = {},
) {
    DocumentPageScaffold(
        title = "AI 文本导入",
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        AiImportScreen(
            onBack = { router.popBack() },
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            onCopyToClipboard = onCopyToClipboard,
        )
    }
}
