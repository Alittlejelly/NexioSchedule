package com.haooz.chedule.ui.navigation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.TimeConfig
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.BackupAndMigrationScreen
import com.haooz.chedule.ui.activities.CourseManageScreen
import com.haooz.chedule.ui.activities.CourseTimeSettingsScreen
import com.haooz.chedule.ui.activities.MainActivity
import com.haooz.chedule.ui.activities.TimeConfigCardBounds
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.basic.ShortcutMenu
import com.haooz.chedule.ui.basic.ShortcutMenuItem
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.screens.CourseEditScreen
import com.haooz.chedule.ui.activities.EduImportUpdater
import com.haooz.chedule.ui.web.rememberAndroidWebHost
import com.haooz.chedule.ui.screens.TimeConfigEditScreen
import com.haooz.chedule.ui.screens.invalidateWeatherCache
import com.haooz.chedule.ui.utils.ApiCompat
import com.haooz.chedule.ui.utils.AndroidDiagnosticRecorder
import com.haooz.chedule.ui.utils.AndroidReminderController
import com.haooz.chedule.ui.utils.AndroidBackupFileController
import com.haooz.chedule.ui.utils.AndroidScheduleFileController
import com.haooz.chedule.ui.utils.AndroidUpdateController
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.ShareCodeApi
import com.haooz.chedule.ui.utils.ShareCodeFetchResult
import com.haooz.chedule.ui.utils.ThirdPartyShareImporter
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.performScheduleShare
import com.haooz.chedule.wearable.WearableScheduleSync
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds
import com.haooz.chedule.data.showToast

/**
 * 子页路由 → 页面的映射表。
 *
 * 这是**唯一**需要知道「哪个路由对应哪个 Screen」的地方 —— 宿主只负责创建
 * [AppRouter]、渲染 [AppNavHost]，不关心具体有哪些页面。
 *
 * ## 迁移注意（阶段 5+，别照旧注释理解）
 *
 * 「路由 → composable」的映射形状是可搬的，但**这个文件当前不能整体搬进
 * `:ui-shared`**：里面还压着一批 Android 专有触点，整体搬会直接编不过。
 * 搬家前先把下列触点上收（注入或拆进 androidMain 宿主），让本文件只剩
 * 纯映射 + DocumentPageScaffold：
 *  - `isInFreeformWindow()` 的 `LocalActivity.current as? MainActivity` 强转；
 *  - CourseManageRoute / CourseTimeSettingsRoute 持有的 Bitmap 卡片快照 + ApiCompat 模糊；
 *  - CourseReminderHelper / IslandNotificationHelper 等平台副作用调用。
 * （~~HolidaySettings 的 HttpURLConnection~~ 已随第三十五批迁出 —— 换成 :core 的 HttpService。）
 *
 * 已迁走的页面本体在 `:ui-shared` 的 `com.haooz.chedule.ui.activities/screens`，
 * 未迁的在 androidApp 同包下 —— 两模块**共享包名**，靠「无同名顶层符号」维持，
 * 新增页面时先确认另一侧没有同名文件。以两侧源码树实际清单为准，本注释不做枚举。
 */
@Composable
fun AppRouteContent(
    router: AppRouter,
    route: AppRoute,
) {
    when (route) {
        AppRoute.About -> AndroidAboutRoute(router)

        // 纯文档壳路由已迁 :ui-shared（第三十三批），直接调跨平台版
        AppRoute.Changelog -> ChangelogRoute(router)
        AppRoute.License -> LicenseRoute(router)
        AppRoute.PrivacyPolicy -> PrivacyPolicyRoute(router)
        AppRoute.Communication -> CommunicationRoute(router)
        AppRoute.AppreciateAuthor -> AppreciateAuthorRoute(router)

        // 带注入的文档壳路由已迁 :ui-shared（第三十四批），Android 注入平台能力。
        // ⚠ context/activity 必须在组合期取好再捕获 —— `LocalContext.current` 是
        // @Composable getter，写进非组合 lambda 会编不过。
        AppRoute.PreferenceSettings -> {
            val context = LocalContext.current
            val activity = LocalActivity.current
            PreferenceSettingsRoute(
                router = router,
                hasLocationPermission = {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                },
                onInvalidateWeatherCache = { invalidateWeatherCache() },
                onSetTaskExcludedFromRecents = { hidden ->
                    MainActivity.setTaskExcludedFromRecents(context, hidden)
                },
                // 偏好设置页改主题 → 系统栏跟着刷新（原来在 Activity 的 setContent 里）
                onThemeDarkChanged = { activity?.applyThemeAwareSystemBars() },
            )
        }

        AppRoute.UpdateSettings -> {
            val context = LocalContext.current
            UpdateSettingsRoute(
                router = router,
                updateController = remember { AndroidUpdateController(context) },
            )
        }

        AppRoute.LocalBackup -> {
            val context = LocalContext.current
            LocalBackupRoute(
                router = router,
                backupFileController = remember { AndroidBackupFileController(context) },
                onHolidayDataChanged = {
                    CourseReminderHelper.onHolidayDataChanged(context)
                },
            )
        }

        AppRoute.AiImport -> {
            val context = LocalContext.current
            AiImportRoute(
                router = router,
                onCopyToClipboard = { text ->
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("课表导入提示词", text)
                    clipboard.setPrimaryClip(clip)
                },
            )
        }

        // ── 数据管理（导入 / 导出 / 备份，共用 BackupAndMigrationScreen，按 mode 区分）──
        AppRoute.ScheduleImport -> AndroidBackupAndMigrationRoute(router, ScheduleDataManageMode.Import)
        AppRoute.ScheduleExport -> AndroidBackupAndMigrationRoute(router, ScheduleDataManageMode.Export)
        AppRoute.ScheduleBackup -> AndroidBackupAndMigrationRoute(router, ScheduleDataManageMode.Backup)

        // ── 带额外元素的页面（顶栏按钮 / 底部悬浮按钮 / 弹窗）──
        // 路由壳已迁 :ui-shared（第三十六批），Android 只注入平台能力。
        AppRoute.WidgetIntro -> {
            val context = LocalContext.current
            WidgetIntroRoute(
                router = router,
                onRequestWidgetRefresh = {
                    com.haooz.chedule.widget.WidgetUpdateCache.clear()
                    com.haooz.chedule.widget.WidgetUpdateCache.updateInstalledWidgets(context)
                },
            )
        }
        AppRoute.CourseReminder -> {
            val context = LocalContext.current
            // 开关值必须跟随 ViewModel，否则 remember 无 key 会把初次求值的结果钉死，
            // 切换开关后按钮文案不会更新；是否支持超级岛是设备静态能力，缓存一次即可
            val settingsViewModel: SettingsViewModel = viewModel()
            val islandNotification by settingsViewModel.islandNotification.collectAsState()
            val islandSupported = remember { IslandNotificationHelper.isIslandSupported(context) }
            val islandEnabled = islandNotification && islandSupported
            CourseReminderRoute(
                router = router,
                reminderController = remember { AndroidReminderController(context) },
                testActionLabel = if (islandEnabled) "测试小米超级岛" else "测试实时活动",
                onSendTestNotification = {
                    FeatureLog.reminderFlow(
                        "test_notification",
                        if (islandEnabled) "island" else "live"
                    )
                    if (islandEnabled) {
                        IslandNotificationHelper.sendTestIslandNotification(context)
                        FeatureLog.reminderFlow("test_island_sent")
                        showToast("已发送超级岛测试通知")
                    } else {
                        CourseReminderHelper.sendTestLiveNotification(context)
                        FeatureLog.reminderFlow("test_live_sent")
                        showToast("已发送实时活动测试通知")
                    }
                },
            )
        }
        AppRoute.WebDavSettings -> {
            val context = LocalContext.current
            WebDavSettingsRoute(
                router = router,
                onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
            )
        }
        AppRoute.HolidaySettings -> {
            val context = LocalContext.current
            // 节假日数据变更是 Android 专有的平台副作用（提醒 / 勿扰 / 小组件），由宿主注入
            HolidaySettingsRoute(
                router = router,
                onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
            )
        }
        // 路由壳已迁 :ui-shared（第三十八批），Android 只注入自由小窗判定
        AppRoute.CourseTimeSettings -> CourseTimeSettingsRoute(
            router = router,
            isInFreeformWindow = isInFreeformWindow(),
        )
        AppRoute.CourseManage -> CourseManageRoute(
            router = router,
            isInFreeformWindow = isInFreeformWindow(),
        )
        AppRoute.EducationalImport -> AndroidEducationalImportRoute(router)
    }
}

/**
 * 课表导入 / 导出 / 备份。
 *
 * ⚠ 这三个入口**必须**走路由，不能再是 Activity：原来 `BackupAndMigrationActivity`
 * 是独立 Activity，`BackupAndMigrationScreen` 里的「本地备份」入口读 [LocalAppRouter]，
 * 而那个 Activity 的 setContent 没有 provide，默认值是 `error(...)` —— 一点开就崩。
 */
/**
 * 课表导入 / 导出 / 备份的 **Android 包装**（第三十七批）。
 *
 * 路由壳已迁 `:ui-shared` 的 [BackupAndMigrationRoute]，这里只剩四条 Android 专有能力
 * 的装配：文件选择器 / 提醒重排 / 口令取回与抓取 / 口令分享出图 / 手环推送。
 * 三者共用同一份装配，只是 `mode` 不同。
 */
@Composable
private fun AndroidBackupAndMigrationRoute(router: AppRouter, mode: ScheduleDataManageMode) {
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    BackupAndMigrationRoute(
        router = router,
        mode = mode,
        // 导入选文件 / 导出另存为（SAF）
        scheduleFileController = remember { AndroidScheduleFileController(context) },
        // WebDAV 恢复后的提醒重排
        onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
        // 分享口令取回（OkHttp + Gson）
        onFetchShareCode = { code ->
            ShareCodeApi.fetchShare(code).map {
                ShareCodeFetchResult(
                    code = it.code,
                    scheduleName = it.scheduleName,
                    scheduleData = it.scheduleData,
                )
            }
        },
        // 第三方口令抓取（无头 WebView + OkHttp + Gson）
        onImportThirdParty = { source, code ->
            ThirdPartyShareImporter.import(context, source, code)
        },
        // 口令分享（上传 + 出图 + 系统分享）
        onShareSchedule = { name, onSharingChanged ->
            performScheduleShare(
                context = context,
                scope = shareScope,
                scheduleName = name,
                onSharingChanged = onSharingChanged,
            )
        },
        // 手环推送
        onExportToWearable = { name -> WearableScheduleSync.exportToWearable(context, name) },
    )
}

/**
 * 是否处于自由小窗 / 分屏。
 *
 * 原来由每个 Activity 自己的 `onMultiWindowModeChanged` 维护（该回调 API 35 起废弃且无等价替代）。
 * 单宿主之后子页拿不到自己的 Activity 回调，统一读**宿主** [MainActivity] 的 Compose 状态 ——
 * 它由宿主的同一个回调驱动，语义不变。
 */
@Composable
private fun isInFreeformWindow(): Boolean {
    val activity = LocalActivity.current as? MainActivity ?: return false
    return activity.isInFreeformWindow
}

/**
 * 关于页（原来内联在 AboutActivity 的 1200 行，已抽到 `:ui-shared` commonMain 的 `AboutScreen.kt`）。
 *
 * 平台专有的一半留在宿主：版本号 / 应用名 / **裁边后的应用图标**（`packageManager` +
 * AdaptiveIcon 透明边裁剪，Compose 没有跨平台等价物）/ `finishAffinity` / 诊断录制器。
 * 其余（路由跳转、子页面导航）走 `:ui-shared` 的 [com.haooz.chedule.ui.navigation.AboutRoute]。
 */
@Composable
private fun AndroidAboutRoute(router: AppRouter) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    // 应用图标：drawable → Bitmap → 裁掉 AdaptiveIcon 四周透明边 → ImageBitmap
    // （裁边逻辑原在 AboutScreen.cropBitmapToOpaque，随迁移到 ui-shared 已删除，宿主在此复刻）
    val aboutIcon = remember {
        runCatching { context.packageManager.getApplicationIcon(context.packageName) }
            .getOrNull()
            ?.let { drawable ->
                val size = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight, 1)
                val bitmap = androidx.core.graphics.createBitmap(size, size)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(android.graphics.Canvas(bitmap))
                val pixels = IntArray(size * size)
                bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
                var minX = size
                var minY = size
                var maxX = -1
                var maxY = -1
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        if (pixels[y * size + x] ushr 24 != 0) {
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        }
                    }
                }
                val cropped = if (maxX >= minX && maxY >= minY &&
                    (maxX - minX + 1 != size || maxY - minY + 1 != size)
                ) {
                    android.graphics.Bitmap.createBitmap(bitmap, minX, minY, maxX - minX + 1, maxY - minY + 1)
                } else {
                    bitmap
                }
                cropped.asImageBitmap()
            }
    }
    com.haooz.chedule.ui.navigation.AboutRoute(
        router = router,
        appVersion = remember { packageInfo?.versionName ?: "未知版本" },
        appName = remember {
            packageInfo?.applicationInfo?.labelRes?.let { context.getString(it) }
                ?: context.applicationInfo?.nonLocalizedLabel?.toString()
                ?: "Nexio课程表"
        },
        appIcon = aboutIcon,
        onExitApp = { activity?.finishAffinity() },
        diagnosticRecorder = remember { AndroidDiagnosticRecorder(context) },
    )
}

/**
 * 教务系统导入（原 `EducationalImportActivity`，673 行，2026-10-10 并入单 Activity 路由）。
 *
 * 与原 Activity 的对应关系：
 * - `onCreate` 里的 `startUpdate(this)` → 进入路由即 `startUpdateIfNeeded()`（原时机）
 * - `finish()` → `router.popBack()`（原「退出页面」语义；WebView 分支的内部返回由 Screen 自管）
 * - `onMultiWindowModeChanged` → 宿主 MainActivity 的 `isInFreeformWindow`（经 [isInFreeformWindow] 读取）
 * - `forceUpdate(context)`（Toast 结果）→ [EduImportUpdater.forceUpdate]，Context 用组合期的 LocalContext
 * - 系统栏 / adjustResize / configChanges：MainActivity 的 Manifest 条目已覆盖（与原条目逐项同款）
 */
@Composable
private fun AndroidEducationalImportRoute(router: AppRouter) {
    // 只有网页宿主与自由窗口判定是 Android 专有，其余在 :ui-shared
    com.haooz.chedule.ui.navigation.EducationalImportRoute(
        router = router,
        webHostFactory = { config, events -> rememberAndroidWebHost(config, events) },
        isInFreeformWindow = isInFreeformWindow(),
    )
}
