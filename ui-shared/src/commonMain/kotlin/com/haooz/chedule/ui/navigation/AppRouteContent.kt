package com.haooz.chedule.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.isRenderEffectSupported
import com.haooz.chedule.data.BackupFileController
import com.haooz.chedule.data.DiagnosticRecorder
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.data.HttpTimeouts
import com.haooz.chedule.data.NoopBackupFileController
import com.haooz.chedule.data.NoopDiagnosticRecorder
import com.haooz.chedule.data.NoopReminderController
import com.haooz.chedule.data.NoopScheduleFileController
import com.haooz.chedule.data.NoopUpdateController
import com.haooz.chedule.data.ReminderController
import com.haooz.chedule.data.ScheduleFileController
import com.haooz.chedule.data.UpdateController
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.TimeConfig
import com.haooz.chedule.data.createHttpService
import com.haooz.chedule.data.ioDispatcher
import com.haooz.chedule.data.showToast
import com.haooz.chedule.data.todayLocalDate
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.AiImportScreen
import com.haooz.chedule.ui.activities.AppreciateAuthorScreen
import com.haooz.chedule.ui.activities.BackupAndMigrationScreen
import com.haooz.chedule.ui.activities.ChangelogScreen
import com.haooz.chedule.ui.activities.CommunicationScreen
import com.haooz.chedule.ui.activities.CourseManageScreen
import com.haooz.chedule.ui.activities.CourseReminderScreen
import com.haooz.chedule.ui.activities.CourseTimeSettingsScreen
import com.haooz.chedule.ui.activities.EduImportUpdater
import com.haooz.chedule.ui.activities.EducationalImportScreen
import com.haooz.chedule.ui.activities.HolidaySettingsScreen
import com.haooz.chedule.ui.activities.LicenseScreen
import com.haooz.chedule.ui.activities.LocalBackupScreen
import com.haooz.chedule.ui.activities.PreferenceSettingsScreen
import com.haooz.chedule.ui.activities.PrivacyPolicyScreen
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.activities.TimeConfigCardBounds
import com.haooz.chedule.ui.activities.UpdateSettingsScreen
import com.haooz.chedule.ui.activities.WebDavSettingsScreen
import com.haooz.chedule.ui.activities.WidgetIntroScreen
import com.haooz.chedule.ui.basic.LiquidGlassTextButton
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ShortcutMenu
import com.haooz.chedule.ui.basic.ShortcutMenuItem
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.screens.CourseEditScreen
import com.haooz.chedule.ui.screens.TimeConfigEditScreen
import com.haooz.chedule.ui.utils.ShareCodeFetchResult
import com.haooz.chedule.ui.utils.ThirdPartySharePayload
import com.haooz.chedule.ui.utils.ThirdPartyShareSource
import com.haooz.chedule.ui.utils.cropImageBitmapClamped
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberScreenCornerRadiusPx
import com.haooz.chedule.ui.web.WebHost
import com.haooz.chedule.ui.web.WebHostConfig
import com.haooz.chedule.ui.web.WebHostEvents
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.NavigationBackHandlerFor
import kotlin.time.Duration.Companion.milliseconds

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

/**
 * 节假日与调休路由（第三十五批）。
 *
 * 迁移前网络拉取走 `HttpURLConnection` + `Dispatchers.IO/Main`（Android 专有）；
 * 现在统一走 `:core` 的 [createHttpService]（iOS 是 NSURLSession，桌面是 OkHttp），
 * 协程调度用 [ioDispatcher] —— 路由本体已完全跨平台。
 *
 * @param onHolidayDataChanged 节假日数据变更后的平台副作用（重排提醒/刷小部件），
 *   Android 宿主注入；桌面/iOS 默认空实现。
 */
@Composable
fun HolidaySettingsRoute(
    router: AppRouter,
    onHolidayDataChanged: () -> Unit = {},
) {
    val http = remember { createHttpService(HttpTimeouts(connectSeconds = 10, readSeconds = 10)) }
    val scope = rememberCoroutineScope()
    val currentDate = remember { todayLocalDate() }
    var year by remember { mutableIntStateOf(currentDate.year) }
    var entries by remember { mutableStateOf(HolidayManager.load(year)) }
    var loading by remember { mutableStateOf(false) }

    fun reload() {
        entries = HolidayManager.load(year)
    }

    fun requestYear(targetYear: Int) {
        if (loading) return
        loading = true
        scope.launch {
            val result = runCatching {
                val response = withContext(ioDispatcher) {
                    http.get(HolidayManager.sourceUrlFor(targetYear))
                }
                HolidayManager.parseSourceResponse(response.text)
            }.getOrDefault(emptyList())
            val merged = HolidayManager.mergeApiEntries(targetYear, result)
            if (targetYear == year) reload()
            loading = false
            if (merged && result.isNotEmpty()) {
                // API 合并同样要重排提醒并刷小部件，不能只改本地 SP
                onHolidayDataChanged()
            }
        }
    }

    DocumentPageScaffold(
        title = "节假日与调休",
        onBack = { router.popBack() },
        endAction = { backdropAlpha, shadowAlpha ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .offset(x = (-6).dp, y = (-4).dp)
                        .size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        progress = null,
                    )
                }
            } else {
                LiquidTopBarButton(
                    onClick = { requestYear(year) },
                    backdrop = rememberLayerBackdrop(),
                    icon = MiuixIcons.Normal.Update,
                    contentDescription = "更新",
                    iconSize = 28.dp,
                    backdropAlpha = backdropAlpha,
                    shadowAlpha = shadowAlpha,
                )
            }
        },
    ) { scrollBehavior, backdrop ->
        HolidaySettingsScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            year = year,
            entries = entries,
            onYearChange = { newYear ->
                year = newYear
                reload()
            },
            reload = { reload() },
            onHolidayDataChanged = onHolidayDataChanged,
        )
    }
}

// ── 带额外元素的页面路由（第三十六批）：底部悬浮按钮 / 引导弹窗 / 顶栏按钮 ──
// 共同形状：DocumentPageScaffold + 一个已迁 Screen，平台能力全部走参数。
// Android 注入真实能力；桌面/iOS 用默认值（按钮不显示 / 空实现）。

/**
 * 桌面小部件引导路由。
 *
 * @param onRequestWidgetRefresh 小组件刷新：Android 注入 `WidgetUpdateCache` 的清缓存 +
 *   更新已安装小部件；桌面/iOS 没有 AppWidget 概念，默认空实现。
 *
 * ⚠ 弹窗里的「添加到桌面」引导文案是 **Android 措辞**（长按桌面 → 安卓小部件）。
 * iOS 若做 WidgetKit，把这段文案提成参数再换 —— 现在 iOS 走不到这一页，先不预留。
 */
@Composable
fun WidgetIntroRoute(
    router: AppRouter,
    onRequestWidgetRefresh: () -> Unit = {},
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp
    var showGuideDialog by remember { mutableStateOf(false) }

    DocumentPageScaffold(
        title = "桌面小部件",
        onBack = { router.popBack() },
        overlay = { backdrop ->
            LiquidGlassTextButton(
                text = "添加到桌面",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    showGuideDialog = true
                },
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = tabletHorizontalPadding + 16.dp,
                        end = tabletHorizontalPadding + 16.dp
                    )
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
            )
            OverlayDialog(
                title = "添加桌面小部件",
                show = showGuideDialog,
                liquidGlassBackdrop = backdrop,
                onDismissRequest = { showGuideDialog = false }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "1. 长按桌面空白处\n2. 选择「全部应用」内的「安卓小部件」\n3. 找到「Nexio课程表」并添加",
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    TextButton(
                        text = "我知道了",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showGuideDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
    ) { scrollBehavior, backdrop ->
        WidgetIntroScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            onRequestWidgetRefresh = onRequestWidgetRefresh,
        )
    }
}

/**
 * 课程提醒路由。
 *
 * @param reminderController 提醒能力（闹钟 / 电池优化 / 权限 / 系统设置页跳转）：
 *   Android 注入 `AndroidReminderController(context)`；桌面/iOS 用 [NoopReminderController]。
 * @param testActionLabel 底部测试按钮文案 —— Android 按设备能力在「测试小米超级岛」/
 *   「测试实时活动」之间切换，由宿主导好再传进来。
 * @param onSendTestNotification 测试通知。**传 null 表示这一端没有测试能力 → 不渲染按钮**
 *   （iOS/桌面走不到，别给一个点了没反应的按钮）。
 */
@Composable
fun CourseReminderRoute(
    router: AppRouter,
    reminderController: ReminderController = NoopReminderController,
    testActionLabel: String = "测试通知",
    onSendTestNotification: (() -> Unit)? = null,
) {
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    DocumentPageScaffold(
        title = "课程提醒",
        onBack = { router.popBack() },
        overlay = { backdrop ->
            if (onSendTestNotification != null) {
                LiquidGlassTextButton(
                    text = testActionLabel,
                    onClick = onSendTestNotification,
                    backdrop = backdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = tabletHorizontalPadding + 16.dp,
                            end = tabletHorizontalPadding + 16.dp
                        )
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp),
                )
            }
        },
    ) { scrollBehavior, backdrop ->
        CourseReminderScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            reminderController = reminderController,
        )
    }
}

/**
 * WebDAV 云备份设置路由。
 *
 * 顶栏「测试连接」（[DocumentPageScaffold] 的 endAction）+ 底部「备份到云端 / 从云端恢复」。
 * 两个按钮的实际实现**由 Screen 反抛给宿主**（`onTestConnectionReady` /
 * `onBackupRestoreReady`），本路由只持有并渲染它们 —— 这样才能跨端复用。
 *
 * @param onHolidayDataChanged 云端恢复后的平台副作用（重排提醒 / 勿扰 / 小组件），
 *   Android 宿主注入；桌面/iOS 默认空实现。
 */
@Composable
fun WebDavSettingsRoute(
    router: AppRouter,
    onHolidayDataChanged: () -> Unit = {},
) {
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp
    var connected by remember { mutableStateOf(false) }
    var onTestConnection by remember { mutableStateOf({}) }
    var backingUp by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var onBackup by remember { mutableStateOf({}) }
    var onRestore by remember { mutableStateOf({}) }

    DocumentPageScaffold(
        title = "WebDAV 云备份",
        onBack = { router.popBack() },
        overlay = { backdrop ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = tabletHorizontalPadding + 8.dp,
                        end = tabletHorizontalPadding + 8.dp
                    )
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LiquidGlassTextButton(
                    text = if (backingUp) "备份中..." else "备份到云端",
                    onClick = { onBackup() },
                    backdrop = backdrop,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassTextButton(
                    text = if (restoring) "恢复中..." else "从云端恢复",
                    onClick = { onRestore() },
                    backdrop = backdrop,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        endAction = { backdropAlpha, shadowAlpha ->
            LiquidTopBarButton(
                onClick = { onTestConnection() },
                backdrop = rememberLayerBackdrop(),
                icon = if (connected) MiuixIcons.Ok else MiuixIcons.Play,
                contentDescription = if (connected) "已连接" else "测试连接",
                backdropAlpha = backdropAlpha,
                shadowAlpha = shadowAlpha,
                iconOffset = if (!connected) DpOffset(x = 2.dp, y = 0.dp) else DpOffset.Zero,
                iconTint = if (connected) Color(0xFF4CAF50) else Color.Unspecified,
            )
        },
    ) { scrollBehavior, backdrop ->
        WebDavSettingsScreen(
            scrollBehavior = scrollBehavior,
            onConnectedChange = { connected = it },
            onTestConnectionReady = { onTestConnection = it },
            onBackupRestoreReady = { backup, restore ->
                onBackup = backup
                onRestore = restore
            },
            onBusyStateChange = { b, r ->
                backingUp = b
                restoring = r
            },
            onHolidayDataChanged = onHolidayDataChanged,
        )
    }
}

/**
 * 课表导入 / 导出 / 备份（三者共用 [BackupAndMigrationScreen]，只差 [mode]）。
 *
 * ⚠ 这三个入口**必须**走路由，不能再是独立 Activity：原来 `BackupAndMigrationActivity`
 * 里 `BackupAndMigrationScreen` 的「本地备份」入口读 [LocalAppRouter]，而独立 Activity 的
 * setContent 没有 provide，默认值是 `error(...)` —— 一点开就崩。
 *
 * ## 四条 Android 专有能力全部走注入，且**都有跨平台默认值**
 *
 * 口令取回 / 第三方口令抓取 / 口令分享出图 / 手环推送 —— Android 注入真实实现，
 * iOS 与桌面用默认值（"当前平台不支持"）。**默认值不是偷懒**：这一页在 iOS 上
 * 仍应能打开（WebDAV 备份、本地备份、文件导入导出都能用），只是口令通道不可用。
 *
 * @param scheduleFileController 文件选择器（导入选文件 / 导出另存为）：
 *   Android 注入 `AndroidScheduleFileController(context)`；iOS/桌面用
 *   [NoopScheduleFileController]（没有系统文件选择器，点导入会提示不可用）。
 */
@Composable
fun BackupAndMigrationRoute(
    router: AppRouter,
    mode: ScheduleDataManageMode,
    scheduleFileController: ScheduleFileController = NoopScheduleFileController,
    onHolidayDataChanged: () -> Unit = {},
    onFetchShareCode: suspend (code: String) -> Result<ShareCodeFetchResult> =
        { Result.failure(IllegalStateException("当前平台不支持口令导入")) },
    onImportThirdParty: suspend (source: ThirdPartyShareSource, code: String)
    -> Result<ThirdPartySharePayload> =
        { source, _ -> Result.failure(IllegalStateException("当前平台不支持${source.displayName}")) },
    onShareSchedule: (scheduleName: String, onSharingChanged: (Boolean) -> Unit) -> Unit = { _, _ -> },
    onExportToWearable: suspend (scheduleName: String) -> String? = { null },
) {
    val title = when (mode) {
        ScheduleDataManageMode.Import -> "课表导入"
        ScheduleDataManageMode.Export -> "课表导出"
        ScheduleDataManageMode.Backup -> "课表备份"
    }
    DocumentPageScaffold(
        title = title,
        onBack = { router.popBack() },
    ) { scrollBehavior, backdrop ->
        BackupAndMigrationScreen(
            scrollBehavior = scrollBehavior,
            liquidGlassBackdrop = backdrop,
            mode = mode,
            scheduleFileController = scheduleFileController,
            onHolidayDataChanged = onHolidayDataChanged,
            onFetchShareCode = onFetchShareCode,
            onImportThirdParty = onImportThirdParty,
            onShareSchedule = onShareSchedule,
            onExportToWearable = onExportToWearable,
        )
    }
}

// ── 最后两个路由壳（第三十八批）：二级页转场 + 卡片快照 ──
// 这两个是全部路由里最复杂的：一级列表之上叠一层「背景缩放 + 模糊」，
// 二级编辑页渲染在 Scaffold **外面**（否则会被顶栏裁掉），
// 快捷菜单还要在模糊层之外再叠一层卡片快照。结构必须整体搬，不能只搬 Screen。

/**
 * 课表节数与时间。
 *
 * 比普通文档页多两层结构：
 * - 外层 `Box` 做「背景缩放 + 模糊」动画，二级编辑页从列表卡片展开时主列表缩下去
 * - 二级编辑页 `TimeConfigEditScreen` 渲染在 `Scaffold` **外面**，否则会被顶栏裁掉
 *
 * @param isInFreeformWindow 自由小窗 / 分屏判定：Android 读宿主 `MainActivity` 的
 *   Compose 状态（子页拿不到自己的 Activity 回调）；桌面/iOS 没有这个概念，传 false。
 */
@Composable
fun CourseTimeSettingsRoute(
    router: AppRouter,
    isInFreeformWindow: Boolean = false,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val repository = remember { CourseRepository() }
    val coroutineScope = rememberCoroutineScope()

    // 屏幕尺寸（px）：与 CourseManageRoute 同口径读 Compose 容器尺寸，跨平台一致
    val screenWidth = windowInfo.containerSize.width.toFloat()
    val screenHeight = windowInfo.containerSize.height.toFloat()
    // 圆角：Android 读系统真实圆角（自由小窗固定 20dp）；iOS/桌面返回 0 = 不裁切
    val screenCornerRadius = rememberScreenCornerRadiusPx()

    // 页面状态管理（原来在 Activity 里）
    var currentPage by remember { mutableStateOf("select") }
    var editingConfig by remember { mutableStateOf<TimeConfig?>(null) }
    var editingRoutineId by remember { mutableStateOf<Long?>(null) }
    var editingCardBounds by remember { mutableStateOf<TimeConfigCardBounds?>(null) }
    var listRefreshTrigger by remember { mutableIntStateOf(0) }

    // 快照截取
    val screenGraphicsLayer = rememberGraphicsLayer()
    var cardSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }

    // 背景缩放与模糊动画
    val backgroundScale = remember { Animatable(1f) }
    val managePageBlurRadius = remember { Animatable(0f) }
    // 二级编辑页的玻璃层是独立的（与一级页不共享采样源）
    val editLiquidGlassBackdrop = rememberLayerBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 用 renderEffect 模糊，避免动画时重组
                    //（:backdrop 的 isRenderEffectSupported 跨平台；不支持时返回 null = 不模糊）
                    val blurRadiusPx = managePageBlurRadius.value * density.density
                    renderEffect = if (blurRadiusPx > 0f && isRenderEffectSupported()) {
                        BlurEffect(null, blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                    } else null
                }
                .background(MiuixTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = backgroundScale.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .drawWithContent {
                        screenGraphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        val scale = backgroundScale.value
                        if (scale < 0.999f) {
                            val path = Path().apply {
                                addSquircleRect(
                                    width = size.width,
                                    height = size.height,
                                    cornerRadius = screenCornerRadius
                                )
                            }
                            clipPath(path) {
                                this@drawWithContent.drawContent()
                            }
                        } else {
                            drawContent()
                        }
                    }
            ) {
                DocumentPageScaffold(
                    title = "课表节数与时间",
                    onBack = { router.popBack() },
                ) { scrollBehavior, liquidGlassBackdrop ->
                    CourseTimeSettingsScreen(
                        onEditRoutine = { routine, baseConfig, bounds ->
                            // 二级页面只编辑这一个作息：把该作息的时间叠加上去
                            editingConfig = baseConfig.effectiveFor(routine.id)
                            editingRoutineId = routine.id
                            editingCardBounds = bounds
                            // 先捕获快照（原始状态）
                            coroutineScope.launch {
                                val full = screenGraphicsLayer.toImageBitmap()
                                cardSnapshot = cropImageBitmapClamped(
                                    source = full,
                                    left = bounds.left,
                                    top = bounds.top,
                                    width = bounds.width,
                                    height = bounds.height,
                                )
                            }
                            // 等待一帧后启动背景动画
                            coroutineScope.launch {
                                delay(12.milliseconds)
                                launch {
                                    backgroundScale.animateTo(
                                        targetValue = 0.92f,
                                        animationSpec = tween(560, easing = OobeQuartOutEasing)
                                    )
                                }
                                launch {
                                    managePageBlurRadius.animateTo(
                                        targetValue = 5f,
                                        animationSpec = tween(560, easing = OobeQuartOutEasing)
                                    )
                                }
                            }
                            // 最后触发组合（背景已在动画中）
                            currentPage = "edit"
                        },
                        refreshTrigger = listRefreshTrigger,
                        scrollBehavior = scrollBehavior,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                    )
                }
            }
        }

        // 编辑页面渲染在 Scaffold 外面（与 CourseManageRoute 结构一致）
        if (currentPage == "edit") {
            editingConfig?.let { config ->
                val bounds = editingCardBounds
                TimeConfigEditScreen(
                    timeConfig = config,
                    routineId = editingRoutineId ?: 0L,
                    onBackStart = {
                        coroutineScope.launch {
                            launch {
                                backgroundScale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(350, easing = OobeCubicOutEasing)
                                )
                            }
                            launch {
                                managePageBlurRadius.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(350, easing = OobeCubicOutEasing)
                                )
                            }
                        }
                    },
                    onBack = {
                        currentPage = "select"
                        editingConfig = null
                        editingRoutineId = null
                        editingCardBounds = null
                        cardSnapshot = null
                    },
                    onSave = { savedConfig ->
                        // 只替换这一个作息；节次骨架由一级页面管理，这里不碰。
                        // 顶层 name 是用户在这一页改的作息名，要带回去。
                        val saved = editingRoutineId?.let { routineId ->
                            repository.saveRoutine(routineId, savedConfig, savedConfig.name)
                        } ?: false
                        if (!saved) {
                            // 保存被丢弃必须让用户看见：以前静默丢弃，表现成「改完保存、重开还是原来的」
                            showToast("保存失败，请重试")
                        }
                        listRefreshTrigger++
                    },
                    cardLeft = bounds?.left ?: 0f,
                    cardTop = bounds?.top ?: 0f,
                    cardWidth = bounds?.width ?: screenWidth,
                    cardHeight = bounds?.height ?: (screenHeight * 0.2f),
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    screenCornerRadius = screenCornerRadius,
                    cardStartCornerRadius = 20f,
                    cardSnapshot = cardSnapshot,
                    // 新建配置的 FAB 形态已随「快捷切换」一起移除，二级页永远从列表卡片展开
                    isFabCreation = false,
                    onDeleteRoutine = { routineId ->
                        // 删除会换掉生效作息，重排提醒等都在 repository 里一并做完
                        repository.deleteRoutine(routineId)
                        listRefreshTrigger++
                    },
                    liquidGlassBackdrop = editLiquidGlassBackdrop,
                )
            }
        }
    }
}

/**
 * 课程管理。
 *
 * 与 [CourseTimeSettingsRoute] 同构，但比它多两块**必须在模糊层之外**的叠加内容：
 * - 快捷菜单（长按课程卡片）：遮罩 + 卡片快照 + [ShortcutMenu]
 * - [CourseEditScreen]
 * 所以它们不能走 `DocumentPageScaffold` 的 `overlay`（那是在采样层内部），
 * 必须留在根 `Box` 里当外层 `Box` 的兄弟 —— 否则会跟着一起被模糊/缩放。
 *
 * ⚠ `DisposableEffect` 的清理（退出时删掉「本次新建但没编辑过」的空课程）从
 * 「Activity finish」变成「路由弹出、组合退出」。语义一致：都是离开本页时清理。
 *
 * @param isInFreeformWindow 自由小窗 / 分屏判定，Android 宿主注入。
 */
@Composable
fun CourseManageRoute(
    router: AppRouter,
    isInFreeformWindow: Boolean = false,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val coroutineScope = rememberCoroutineScope()
    val courseViewModel: CourseViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val sectionTimes by settingsViewModel.sectionTimes.collectAsState()

    // Edit screen state
    var showEditScreen by remember { mutableStateOf(false) }
    var selectedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var cardLeft by remember { mutableFloatStateOf(0f) }
    var cardTop by remember { mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableFloatStateOf(0f) }
    var cardHeight by remember { mutableFloatStateOf(0f) }
    var cardSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var cardColor by remember { mutableStateOf(Color(0xFF4CAF50)) }
    var cardAlpha by remember { mutableFloatStateOf(0.15f) }
    var hiddenCourseIds by remember { mutableStateOf(setOf<String>()) }
    var shrinkingCourseIds by remember { mutableStateOf(setOf<String>()) }
    var pendingAutoExitDeleteIds by remember { mutableStateOf(setOf<String>()) }

    // Shortcut菜单状态
    var showShortcutMenu by remember { mutableStateOf(false) }
    var shortcutMenuPosition by remember { mutableStateOf(Offset.Zero) }
    var shortcutMenuCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var shortcutMenuHeight by remember { mutableFloatStateOf(0f) }
    var shortcutMenuCardWidth by remember { mutableFloatStateOf(0f) }
    var shortcutMenuCardHeight by remember { mutableFloatStateOf(0f) }
    val shortcutMenuBlurRadius = remember { Animatable(0f) }
    val shortcutMenuCardScale = remember { Animatable(1f) }
    val shortcutMenuPageScale = remember { Animatable(1f) }
    var shortcutMenuSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }

    // 删除确认弹窗状态
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteCourses by remember { mutableStateOf<List<Course>>(emptyList()) }

    // Track course IDs created in this session for cleanup on exit
    var createdCourseIds by remember { mutableStateOf(setOf<String>()) }
    // Track course IDs that were opened for editing (to avoid deleting modified ones)
    var editedCourseIds by remember { mutableStateOf(setOf<String>()) }

    // 退出本页时清理本次新建但没编辑过的空课程（原来是 Activity finish 时做）
    DisposableEffect(Unit) {
        onDispose {
            val coursesToDelete = createdCourseIds - editedCourseIds
            coursesToDelete.forEach { courseId: String ->
                courseViewModel.deleteCourse(courseId)
            }
        }
    }

    // Graphics layer for capturing screen content
    val screenGraphicsLayer = rememberGraphicsLayer()

    LaunchedEffect(showShortcutMenu) {
        if (showShortcutMenu) {
            launch { shortcutMenuBlurRadius.animateTo(10f, tween(280)) }
            launch { shortcutMenuCardScale.animateTo(1.04f, tween(280)) }
            launch { shortcutMenuPageScale.animateTo(0.98f, tween(280)) }
        } else {
            launch { shortcutMenuBlurRadius.animateTo(0f, tween(250)) }
            launch { shortcutMenuCardScale.animateTo(1f, tween(250)) }
            launch { shortcutMenuPageScale.animateTo(1f, tween(250)) }
        }
    }

    // 返回键关闭菜单（嵌套返回拦截：菜单开着时由它接管，否则交给 AppNavHost 的返回）
    // ⚠ `androidx.activity.compose.BackHandler` 是 Android 专有 —— 这里用 :miuix 的
    // `NavigationBackHandlerFor`（skiko 侧有 actual，iOS/桌面是空实现）
    NavigationBackHandlerFor(
        enabled = showShortcutMenu,
        onBackCompleted = { showShortcutMenu = false },
        onBackCancelled = {},
    )

    // Background scale animation
    val backgroundScale = remember { Animatable(1f) }
    val managePageBlurRadius = remember { Animatable(0f) }

    // 圆角：Android 读系统真实圆角（自由小窗固定 20dp）；iOS/桌面返回 0 = 不裁切
    val screenCornerRadius = rememberScreenCornerRadiusPx()

    // 二级编辑页 / 快捷菜单各自独立的玻璃采样源
    val editLiquidGlassBackdrop = rememberLayerBackdrop()
    val shortcutMenuBackdrop = rememberLayerBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content with blur and scale animation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(
                    if (shortcutMenuBlurRadius.value > 0.01f) shortcutMenuBlurRadius.value.dp
                    else managePageBlurRadius.value.dp
                )
                .graphicsLayer {
                    scaleX = shortcutMenuPageScale.value
                    scaleY = shortcutMenuPageScale.value
                }
                .background(MiuixTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = backgroundScale.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .drawWithContent {
                        screenGraphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        // 缩放时保持屏幕圆角不变
                        val scale = backgroundScale.value
                        if (scale < 0.999f) {
                            val path = Path().apply {
                                addSquircleRect(
                                    width = size.width,
                                    height = size.height,
                                    cornerRadius = screenCornerRadius
                                )
                            }
                            clipPath(path) {
                                this@drawWithContent.drawContent()
                            }
                        } else {
                            drawContent()
                        }
                    }
            ) {
                DocumentPageScaffold(
                    title = "课程管理",
                    onBack = { router.popBack() },
                ) { scrollBehavior, liquidGlassBackdrop ->
                    CourseManageScreen(
                        scrollBehavior = scrollBehavior,
                        hiddenCourseIds = hiddenCourseIds,
                        shrinkingCourseIds = shrinkingCourseIds,
                        sectionTimes = sectionTimes,
                        onNewCourseCreated = { course ->
                            courseViewModel.addCourse(course)
                            createdCourseIds = createdCourseIds + course.id
                        },
                        onCourseUpdated = { oldName, course ->
                            courseViewModel.updateCoursesByName(oldName, course)
                        },
                        onEditDismiss = {
                            editingCourse = null
                        },
                        pendingEditCourse = editingCourse,
                        onCourseLongPress = { courses, left, top, width, height ->
                            shortcutMenuCourses = courses
                            shortcutMenuPosition = Offset(left, top)
                            shortcutMenuCardWidth = width
                            shortcutMenuCardHeight = height
                            coroutineScope.launch {
                                shortcutMenuSnapshot = screenGraphicsLayer.toImageBitmap()
                                showShortcutMenu = true
                            }
                        },
                        onDeleteCourses = { courses ->
                            coroutineScope.launch {
                                courses.forEach { course ->
                                    courseViewModel.deleteCourse(course.id)
                                }
                            }
                        },
                        deleteConfirmShow = showDeleteConfirmDialog,
                        deleteConfirmCourses = pendingDeleteCourses,
                        onDeleteConfirmDismiss = { showDeleteConfirmDialog = false },
                        onCourseClick = { courses, left, top, width, height, _, color, alpha ->
                            coroutineScope.launch {
                                selectedCourses = courses
                                cardLeft = left
                                cardTop = top
                                cardWidth = width
                                cardHeight = height
                                cardColor = color
                                cardAlpha = alpha
                                hiddenCourseIds = courses.map { it.id }.toSet()
                                // Mark courses as edited when opened
                                editedCourseIds = editedCourseIds + courses.map { it.id }

                                // 裁出卡片区域的快照（跨平台重绘，见 cropImageBitmapClamped）
                                cardSnapshot = runCatching {
                                    screenGraphicsLayer.toImageBitmap()
                                }.getOrNull()?.let { full ->
                                    cropImageBitmapClamped(full, left, top, width, height)
                                }

                                showEditScreen = true
                                launch {
                                    delay(12.milliseconds)
                                    launch {
                                        backgroundScale.animateTo(
                                            targetValue = 0.92f,
                                            animationSpec = tween(560, easing = OobeQuartOutEasing)
                                        )
                                    }
                                    launch {
                                        managePageBlurRadius.animateTo(
                                            targetValue = 5f,
                                            animationSpec = tween(560, easing = OobeQuartOutEasing)
                                        )
                                    }
                                }
                            }
                        },
                        liquidGlassBackdrop = liquidGlassBackdrop,
                    )
                }
            }
        }

        // Edit screen (above blur layer)
        if (showEditScreen) {
            CourseEditScreen(
                courses = selectedCourses,
                cardLeft = cardLeft,
                cardTop = cardTop,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                screenWidth = windowInfo.containerSize.width.toFloat(),
                screenHeight = windowInfo.containerSize.height.toFloat(),
                screenCornerRadius = screenCornerRadius,
                cardSnapshot = cardSnapshot,
                cardColor = cardColor,
                cardAlpha = cardAlpha,
                onBackStart = {
                    coroutineScope.launch {
                        launch {
                            backgroundScale.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(350, easing = OobeCubicOutEasing)
                            )
                        }
                        launch {
                            managePageBlurRadius.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(350, easing = OobeCubicOutEasing)
                            )
                        }
                    }
                },
                onBack = {
                    if (pendingAutoExitDeleteIds.isNotEmpty()) {
                        showEditScreen = false
                        cardSnapshot = null
                        hiddenCourseIds = emptySet()
                        shrinkingCourseIds = pendingAutoExitDeleteIds
                        coroutineScope.launch {
                            delay(300.milliseconds)
                            pendingAutoExitDeleteIds.forEach { id ->
                                courseViewModel.deleteCourse(id)
                            }
                            shrinkingCourseIds = emptySet()
                            pendingAutoExitDeleteIds = emptySet()
                        }
                    } else {
                        showEditScreen = false
                        cardSnapshot = null
                        hiddenCourseIds = emptySet()
                    }
                },
                onCourseUpdated = { course ->
                    courseViewModel.updateCourse(course)
                    selectedCourses = selectedCourses.map { if (it.id == course.id) course else it }
                },
                onCourseAdded = { course ->
                    courseViewModel.addCourse(course)
                    selectedCourses = selectedCourses + course
                },
                onDeleteCourse = { courseId ->
                    if (selectedCourses.size > 1) {
                        courseViewModel.deleteCourse(courseId)
                    } else {
                        pendingAutoExitDeleteIds = setOf(courseId)
                    }
                    selectedCourses = selectedCourses.filter { it.id != courseId }
                },
                onColorChanged = { colorRes ->
                    cardColor = Color(colorRes)
                },
                getOccupiedWeeks = { dayOfWeek, startSection, endSection, excludeIds, startTime, endTime ->
                    courseViewModel.getOccupiedWeeks(
                        dayOfWeek = dayOfWeek,
                        startSection = startSection,
                        endSection = endSection,
                        excludeIds = excludeIds.toSet(),
                        startTime = startTime,
                        endTime = endTime
                    )
                },
                liquidGlassBackdrop = editLiquidGlassBackdrop,
                sectionTimes = sectionTimes
            )
        }

        // Shortcut菜单遮罩层
        if (showShortcutMenu || shortcutMenuBlurRadius.value > 0.01f) {
            val snapshot = shortcutMenuSnapshot
            val cardLeftPx = shortcutMenuPosition.x
            val cardTopPx = shortcutMenuPosition.y
            val cardW = shortcutMenuCardWidth
            val cardH = shortcutMenuCardHeight

            // 点击空白区域关闭菜单
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showShortcutMenu = false }
            )

            // 卡片快照（清晰，在最顶层）
            if (snapshot != null && cardW > 0f && cardH > 0f) {
                Canvas(
                    modifier = Modifier
                        .offset(
                            x = with(density) { cardLeftPx.toDp() },
                            y = with(density) { cardTopPx.toDp() }
                        )
                        .size(
                            width = with(density) { cardW.toDp() },
                            height = with(density) { cardH.toDp() }
                        )
                        .graphicsLayer {
                            scaleX = shortcutMenuCardScale.value
                            scaleY = shortcutMenuCardScale.value
                        }
                ) {
                    val cornerRadius = 16.dp.toPx()
                    val squirclePath = Path().apply {
                        addSquircleRect(
                            width = size.width,
                            height = size.height,
                            cornerRadius = cornerRadius
                        )
                    }
                    clipPath(squirclePath) {
                        drawImage(
                            image = snapshot,
                            srcOffset = IntOffset(cardLeftPx.toInt(), cardTopPx.toInt()),
                            srcSize = IntSize(cardW.toInt(), cardH.toInt()),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt())
                        )
                    }
                }
            }
        }

        // Shortcut菜单
        ShortcutMenu(
            show = showShortcutMenu,
            items = listOf(
                ShortcutMenuItem(
                    icon = MiuixIcons.Edit,
                    label = "编辑",
                    onClick = {
                        if (shortcutMenuCourses.isNotEmpty()) {
                            val courseToEdit = shortcutMenuCourses.first()
                            showShortcutMenu = false
                            coroutineScope.launch {
                                delay(260.milliseconds)
                                editingCourse = courseToEdit
                            }
                        }
                    }
                ),
                ShortcutMenuItem(
                    icon = MiuixIcons.Delete,
                    label = "删除",
                    onClick = {
                        pendingDeleteCourses = shortcutMenuCourses.toList()
                        showShortcutMenu = false
                        coroutineScope.launch {
                            delay(260.milliseconds)
                            showDeleteConfirmDialog = true
                        }
                    }
                )
            ),
            modifier = Modifier.offset(
                x = with(density) { shortcutMenuPosition.x.toDp() - 14.dp },
                y = with(density) { shortcutMenuPosition.y.toDp() - shortcutMenuHeight.toDp() + 4.dp }
            ),
            backdrop = shortcutMenuBackdrop,
            onDismiss = { showShortcutMenu = false },
            onMeasuredSize = { _, height ->
                shortcutMenuHeight = height.toFloat()
            }
        )
    }
}
