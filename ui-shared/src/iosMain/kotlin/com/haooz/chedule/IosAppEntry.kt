package com.haooz.chedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.capsule.continuities.G2Continuity
import com.haooz.chedule.data.AppFiles
import com.haooz.chedule.data.AppInfo
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.IosBackupFileController
import com.haooz.chedule.data.IosCourseLiveActivity
import com.haooz.chedule.data.IosCourseNotifier
import com.haooz.chedule.data.IosWidgetSnapshot
import com.haooz.chedule.data.IosScheduleFileController
import com.haooz.chedule.data.IosWallpaperBackend
import com.haooz.chedule.data.IosWallpaperPicker
import com.haooz.chedule.data.NexioLog
import com.haooz.chedule.data.NsFileAppFile
import com.haooz.chedule.data.PrivacyConsent
import com.haooz.chedule.data.ScheduleChangeSink
import com.haooz.chedule.data.ScheduleEffects
import com.haooz.chedule.data.StatsReporter
import com.haooz.chedule.data.UserDefaultsStore
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.defaultDocumentsDir
import com.haooz.chedule.data.showToast
import com.haooz.chedule.data.toByteArray
import com.haooz.chedule.ui.activities.main.CourseScheduleApp
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.screens.PrivacyConsentScreen
import com.haooz.chedule.ui.activities.today.TodayAssistantCard
import com.haooz.chedule.ui.screens.rememberWeatherIos
import com.haooz.chedule.ui.utils.IosSharedFileReceiver
import com.haooz.chedule.ui.utils.IosShareImport
import com.haooz.chedule.ui.utils.rememberIosShareAction
import com.haooz.chedule.ui.utils.IosSystemBars
import com.haooz.chedule.ui.utils.IosReminderController
import com.haooz.chedule.ui.utils.IosToast
import com.haooz.chedule.ui.web.rememberIosWebHost
import com.haooz.chedule.ui.navigation.AboutRoute
import com.haooz.chedule.ui.navigation.AiImportRoute
import com.haooz.chedule.ui.navigation.AppreciateAuthorRoute
import com.haooz.chedule.ui.navigation.AppNavHost
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.viewmodel.settingsViewModel
import com.haooz.chedule.ui.navigation.BackupAndMigrationRoute
import com.haooz.chedule.ui.navigation.ChangelogRoute
import com.haooz.chedule.ui.navigation.CommunicationRoute
import com.haooz.chedule.ui.navigation.CourseManageRoute
import com.haooz.chedule.ui.navigation.CourseReminderRoute
import com.haooz.chedule.ui.navigation.CourseTimeSettingsRoute
import com.haooz.chedule.ui.navigation.EducationalImportRoute
import com.haooz.chedule.ui.navigation.HolidaySettingsRoute
import com.haooz.chedule.ui.navigation.LicenseRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.navigation.LocalBackupRoute
import com.haooz.chedule.ui.navigation.PreferenceSettingsRoute
import com.haooz.chedule.ui.navigation.PrivacyPolicyRoute
import com.haooz.chedule.ui.navigation.UpdateSettingsRoute
import com.haooz.chedule.ui.navigation.WebImportRoute
import com.haooz.chedule.ui.navigation.WebDavSettingsRoute
import com.haooz.chedule.ui.navigation.WidgetIntroRoute
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.about_app_icon
import org.jetbrains.compose.resources.imageResource
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIPasteboard

/**
 * iOS 启动注入 —— 对应 Android 的 `NexioApplication.onCreate()` 开头。
 *
 * ⚠ **必须在 Swift 侧 `@main` 的 `init()` 里调用，且早于任何 Compose 界面出现**。
 *
 * ⚠ **函数名别以 `init` 开头**：Kotlin/Native 导出到 ObjC 时，`initXxx` 会被当成
 * 构造器，Swift 侧 `IosAppEntryKt.` 下**找不到**这个方法（实测踩到：别的顶层函数
 * 都在，只有它 `has no member`）。所以叫 `bootstrap…` 而不是 `init…`。
 * 缺任何一个都会在首次使用时抛异常（不是静默失败），具体是：
 * - [AppInfo]：`AppInfo.version` 变 "unknown"（埋点负载里的 app_version 丢失）
 * - [AppStorage]：第一次 `AppStorage.store(...)` 抛
 * - [AppFiles]：第一次 `AppFiles.root` 抛（表现为「学校索引读不出来」）
 *
 * Also injects the toast host, the wallpaper backend and the schedule-change sink.
 */
fun bootstrapNexioIos() {
    val version = NSBundle.mainBundle
        .objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "unknown"
    AppInfo.init(version)

    // 按名字转发到各自的 UserDefaults suite —— 名字由各业务文件自带，这里不做汇总。
    // ⚠ 合并成一张表 = 篡改存量数据的落盘位置，见 UserDefaultsStore 的类注释。
    AppStorage.init { name -> UserDefaultsStore(name) }

    AppFiles.init(
        root = NsFileAppFile(defaultDocumentsDir()),
        readAsset = { path ->
            // 内置学校索引引导用。失败要**抛**：SchoolRepository 会捕获、打日志、跳过引导。
            val resolved = NSBundle.mainBundle.pathForResource(path, null)
                ?: error("内置资源缺失: $path")
            NSData.dataWithContentsOfFile(resolved)?.toByteArray()
                ?: error("内置资源读取失败: $path")
        },
    )

    // 壁纸后端：iOS 用 UIImage（JPEG 编码）+ 共享的 AppFiles 落盘路径。
    // 与 Android 同一条「接口 + 启动注入」链路，只是实现在 iosMain 里。
    WallpaperStore.backend = IosWallpaperBackend()

    // 原生提示条：把 `showToast` 接到 UIKit HUD。导入/导出/分享/选壁纸全靠它给反馈，
    // 不注入的话 native actual 只打日志 → 用户看到的是「点了没反应」。
    IosToast.install()

    // Course reminders. Without this sink the effects are dropped silently, which
    // shows up as "added a class but the reminder never updated".
    // Must precede anything that mutates the schedule: first-run import and
    // schedule switching both trigger a reschedule.
    ScheduleEffects.init(IosScheduleChangeSink)
}

/** iOS schedule effects: native reminders and shared WidgetKit snapshots. */
private object IosScheduleChangeSink : ScheduleChangeSink {
    override fun rescheduleReminders() = IosCourseNotifier.reschedule()

    override fun syncWearable(reason: String) {
        NexioLog.d("IosScheduleChangeSink", "syncWearable($reason) ignored: no wearable on iOS")
    }

    override fun refreshWidgets() = IosWidgetSnapshot.refresh()
}

/**
 * 文件选择/另存实现，全 app 共用一个。
 *
 * ⚠ 必须**全局单例**：它的 delegate 靠自身字段强引用（`UIDocumentPickerViewController.delegate`
 * 是弱引用），每次新建实例也能用，但共用一个更好排查；更要紧的是别让实例在 picker
 * 弹出后被回收 —— 所以挂在顶层 val 上，生命周期跟 app 一样长。
 */
private val fileController = IosScheduleFileController()

/** 相册选壁纸实现，全 app 共用一个（delegate 强引用同上） */
private val wallpaperPicker = IosWallpaperPicker()

/** 本地备份的文件读写（Documents/Neixo_Schedule 扫描 + UIDocumentPicker 选文件） */
private val backupFileController = IosBackupFileController()

/**
 * Course reminder controller: local notice scheduling plus the Live Activity island.
 *
 * Process-wide singleton, same lifetime as the other four controllers. It only holds
 * references to stateless objects, so nothing needs releasing when a page goes away.
 */
private val reminderController = IosReminderController()

/**
 * 节假日数据变更（拉取/备份恢复/云同步恢复）的平台副作用：重排提醒。
 * 不注入的话，待发通知会继续按旧节假日数据排。
 */
private val onHolidayDataChanged: () -> Unit = {
    IosCourseNotifier.reschedule()
    IosWidgetSnapshot.refresh()
}

/** The retained main content; UIKit applies the shared main-layer transition. */
@Composable
internal fun NexioIosMain(host: IosAppHost) {
    val router = host.router
    // 合规：对齐 Android MainActivity —— 启动时同步同意状态，未同意前不触网（云同步/公告/统计）
    PrivacyConsent.refresh()
    val consented by PrivacyConsent.consented.collectAsState()
    // 统计上报：已同意的回访用户，冷启动即上报（active + 一次性 install），对齐 Android onCreate
    LaunchedEffect(Unit) {
        if (PrivacyConsent.hasConsented()) {
            StatsReporter.reportActive()
            StatsReporter.reportInstallOnce()
        }
    }
    // 注入自检：AppStorage/UserDefaults 与 AppFiles/NSFileManager 的 iOS 实现是新写的，
    // 编译通过 ≠ 落盘位置与还原类型正确（UserDefaultsStore 的 Int/Long 不分是已知雷区）。
    // 原先是首页两行文字，真主界面就位后降级为**启动日志**——仍是唯一运行时证据，别删。
    LaunchedEffect(Unit) {
        runCatching {
            val store = AppStorage.store("ios_smoke_test")
            store.edit { putString("k", "v") }
            store.edit { putInt("i", 42) }
            val ok = store.getString("k", "") == "v" && store.getInt("i", 0) == 42
            NexioLog.i("IosBootstrap", "AppStorage/UserDefaults 自检：${if (ok) "OK" else "值不一致"}")
        }.onFailure { NexioLog.e("IosBootstrap", "AppStorage 自检失败：${it.message}", it) }

        runCatching {
            val file = AppFiles.root.resolve("ios_smoke_test").resolve("probe.txt")
            file.writeBytes("课程表".encodeToByteArray())
            val ok = file.readText() == "课程表"
            NexioLog.i("IosBootstrap", "AppFiles/NSFileManager 自检：${if (ok) "OK" else "内容不一致"}")
        }.onFailure { NexioLog.e("IosBootstrap", "AppFiles 自检失败：${it.message}", it) }
    }
    CompositionLocalProvider(LocalAppRouter provides router) {
        Box(Modifier.fillMaxSize()) {
            // 主界面常驻底座：**真课表界面**。尚未接的宿主能力
            // （分享/选壁纸/今日助手卡）走默认「无此能力」，入口不渲染而不是留个死按钮。
                CourseScheduleApp(
                    privacyConsented = consented,
                    onSystemBarsChanged = { statusDark, _ ->
                        IosSystemBars.update(statusDark)
                    },
                    // 节假日数据变更 → 重排提醒
                    onHolidayDataChanged = onHolidayDataChanged,
                    // 相册选壁纸：PHPicker → UIImage（系统已按 EXIF 校正方向）→ 降到屏幕像素
                    // → skia 解码成 ImageBitmap。必须在 iOS 侧降采样：共享层拿到位图直接原样用。
                    onPickWallpaperImage = { onPicked -> wallpaperPicker.pick(onPicked) },
                    // 分享课表：上传生成口令 → 复制剪贴板 → 模板图绘口令 → 系统分享面板
                    onShareSchedule = rememberIosShareAction(),
                    // 今日助手卡：卡片本体在共享层，iOS 只提供天气+定位（CoreLocation）
                    todayAssistantCard = { d ->
                        val (weather, hasLocationPermission, requestLocation) = rememberWeatherIos()
                        TodayAssistantCard(
                            weather = weather,
                            hasLocationPermission = hasLocationPermission,
                            onRequestLocation = requestLocation,
                            courses = d.courses,
                            tomorrowCourses = d.tomorrowCourses,
                            sectionTimes = d.sectionTimes,
                            morningSections = d.morningSections,
                            afternoonSections = d.afternoonSections,
                            showClassroom = d.showClassroom,
                            showTeacher = d.showTeacher,
                            wallpaperBackdrop = d.wallpaperBackdrop,
                            blurRadius = d.blurRadius,
                            surfaceOpacity = d.surfaceOpacity,
                            // 下课烟花是 Android 专有（LocalContext/nanoTime），iOS 不渲染
                            fireworksOverlay = null,
                        )
                    },
                    // 外部分享进来的文件（Swift 侧 .onOpenURL → IosSharedFileReceiver）
                    sharedFileIntent = IosSharedFileReceiver.pending,
                    onShareIntentConsumed = { IosSharedFileReceiver.clear() },
                    onReadSharedFileText = { uri -> IosSharedFileReceiver.readText(uri) },
                    // iOS 走 App Store 更新 → 设置页的「更新设置」入口整个不渲染
                    showUpdateSettings = false,
                    // 平板设置两栏的 8 个平台专有 dest 装配（对齐 Android 的 AndroidSettingsScreen）
                    settingsPane = { args ->
                        com.haooz.chedule.ui.screens.IosSettingsScreen(
                            args = args,
                            scheduleFileController = fileController,
                            backupFileController = backupFileController,
                            reminderController = reminderController,
                            onHolidayDataChanged = onHolidayDataChanged,
                        )
                    },
                )
        }
    }
}

/** Draws routes above both the main content and the native tab bar. */
@Composable
internal fun NexioIosNavigation(host: IosAppHost) {
    val router = host.router
    val mainTransition = host.mainTransition
    val consented by PrivacyConsent.consented.collectAsState()
    // 与 Android MainActivity 对齐：隐私弹窗挂在 AppNavHost 之上，
    // 全文页在栈上时撤掉弹窗遮罩，否则全文页读不了也滑不动。
    // ⚠ iOS 的「隐私政策」是**原生文档页**（isNativeDocument），被 nativeRouteHandler 吃掉了、
    // 不进 router.routes —— 只能问 nativePageIds。
    val policyPageVisible = host.nativeRouteIds.any { it == AppRoute.PrivacyPolicy.id }
    CompositionLocalProvider(LocalAppRouter provides router) {
        Box(Modifier.fillMaxSize()) {
            AppNavHost(
                router,
                mainTransition = mainTransition,
                onOverlayActivityChanged = { host.navigationOverlayActive = it },
            ) { route ->
                NexioIosRouteContent(router, route)
            }
            // 隐私同意弹窗：与 Android MainActivity 相同的挂载方式（AppNavHost 之后、同层 Box），
            // 且必须在这一层 —— 它位于原生 TabBar 之上，遮罩才盖得住底栏（mainController 层盖不住）。
            // 同意后 PrivacyConsent.consented 流翻转 → CourseScheduleApp 内部的
            // LaunchedEffect(privacyConsented) 才会启动云同步/公告，统计上报也在此时触发。
            if (!consented) {
                PrivacyConsentScreen(
                    onAgree = {
                        PrivacyConsent.setConsented()
                        // 合规：同意后方可进行设备信息上报（对齐 Android 的同意回调）
                        StatsReporter.reportActive()
                        StatsReporter.reportInstallOnce()
                    },
                    onDecline = {
                        // 对齐 Android 的 finish()：拒绝即退出。iOS 没有公开的「退出应用」API，
                        // exit(0) 是仅有的等价手段（App Store 审核不喜，但拒绝者本就无法继续用）。
                        platform.posix.exit(0)
                    },
                    onOpenPolicy = { router.navigate(AppRoute.PrivacyPolicy) },
                    policyPageVisible = policyPageVisible,
                )
            }
        }
    }
}

/** Shared by native documents and the retained Compose stack, including platform injections. */
@Composable
internal fun NexioIosRouteContent(router: AppRouter, route: AppRoute) {
    when (route) {
        AppRoute.About -> AboutRoute(
            router = router,
            appVersion = AppInfo.version,
            appIcon = imageResource(Res.drawable.about_app_icon),
            appIconShape = ContinuousRoundedRectangle(20.dp, continuity = G2Continuity()),
            // 「撤回同意」确认后退出应用：iOS 无 finishAffinity 等价物，
            // 与隐私同意弹窗的拒绝路径一致用 exit(0)。不传的话默认空实现，
            // 撤回只清状态不退出，本该退出的语义就丢了。
            onExitApp = { platform.posix.exit(0) },
        )
        // 纯文档壳路由，iOS 直接可用
        AppRoute.Changelog -> ChangelogRoute(router)
        AppRoute.License -> LicenseRoute(router)
        AppRoute.PrivacyPolicy -> PrivacyPolicyRoute(router)
        AppRoute.Communication -> CommunicationRoute(router)
        AppRoute.AppreciateAuthor -> AppreciateAuthorRoute(router)
        // 带注入的文档壳：iOS 用默认 Noop 能力，剪贴板注入 UIPasteboard
        AppRoute.PreferenceSettings -> PreferenceSettingsRoute(router)
        AppRoute.UpdateSettings -> UpdateSettingsRoute(router)
        // 本地备份：目录扫描 + 「手动选择文件」都要原生文件能力。
        // ⚠ 不传的话会落到默认的 NoopBackupFileController ——
        // 点「手动选择文件」直接静默回调 null，什么都不发生。
        AppRoute.LocalBackup -> LocalBackupRoute(
            router = router,
            backupFileController = backupFileController,
            // 全量恢复会写节假日数据 → 重排提醒
            onHolidayDataChanged = onHolidayDataChanged,
        )
        AppRoute.AiImport -> AiImportRoute(
            router = router,
            onCopyToClipboard = { text ->
                UIPasteboard.generalPasteboard.string = text
            },
        )
        // 节假日：网络拉取走跨平台 HttpService（iOS = NSURLSession）
        AppRoute.HolidaySettings -> HolidaySettingsRoute(
            router = router,
            onHolidayDataChanged = onHolidayDataChanged,
        )
        AppRoute.WidgetIntro -> WidgetIntroRoute(
            router,
            onRequestWidgetRefresh = { IosWidgetSnapshot.refresh() },
        )
        // Course reminders: local notifications for pre-class and tomorrow's summary.
        // Gaps vs Android (docs/KMP_IOS_MIGRATION_PLAN.md §7):
        //  - no setAlarmClock counterpart, so pre-class notices may fire late;
        //  - the in-class island can only start from a tap or while foregrounded,
        //    so the island switch here means "start the activity when I tap a notice";
        //  - DND / exact alarm / battery / Shizuku have no system API and their
        //    entries stay hidden.
        AppRoute.CourseReminder -> {
            val islandNotification by settingsViewModel().islandNotification.collectAsState()
            CourseReminderRoute(
                router = router,
                reminderController = reminderController,
                testActionLabel = if (islandNotification) "测试灵动岛" else "测试通知",
                onSendTestNotification = {
                    if (islandNotification) {
                        IosCourseLiveActivity.startTest()
                        showToast("已启动灵动岛测试")
                    } else {
                        IosCourseNotifier.sendTestNotification()
                        showToast("已发送测试通知")
                    }
                },
            )
        }
        // WebDAV：备份/恢复本体已在 :core；恢复成功后要重排提醒
        AppRoute.WebDavSettings -> WebDavSettingsRoute(
            router = router,
            onHolidayDataChanged = onHolidayDataChanged,
        )
        // 课表导入/导出/备份：接 iOS 原生文件选择器（UIDocumentPicker）+ 三条口令导入。
        // 口令取回与星链是纯 HTTP（共享 HttpService = NSURLSession），WakeUp 走无头
        // WKWebView（IosWakeUpImport）。口令**导出**与手环推送仍是 Android 专有。
        AppRoute.ScheduleImport -> BackupAndMigrationRoute(
            router = router,
            mode = ScheduleDataManageMode.Import,
            scheduleFileController = fileController,
            onHolidayDataChanged = onHolidayDataChanged,
            onFetchShareCode = { code -> IosShareImport.fetchShareCode(code) },
            onImportThirdParty = { source, code -> IosShareImport.importThirdParty(source, code) },
        )
        AppRoute.ScheduleExport -> BackupAndMigrationRoute(
            router = router,
            mode = ScheduleDataManageMode.Export,
            scheduleFileController = fileController,
            onHolidayDataChanged = onHolidayDataChanged,
            onFetchShareCode = { code -> IosShareImport.fetchShareCode(code) },
            onImportThirdParty = { source, code -> IosShareImport.importThirdParty(source, code) },
        )
        AppRoute.ScheduleBackup -> BackupAndMigrationRoute(
            router = router,
            mode = ScheduleDataManageMode.Backup,
            scheduleFileController = fileController,
            onHolidayDataChanged = onHolidayDataChanged,
            onFetchShareCode = { code -> IosShareImport.fetchShareCode(code) },
            onImportThirdParty = { source, code -> IosShareImport.importThirdParty(source, code) },
        )
        // 课程时间管理 / 课程管理：iOS 没有自由小窗，传 false
        AppRoute.CourseTimeSettings -> CourseTimeSettingsRoute(router)
        AppRoute.CourseManage -> CourseManageRoute(router)
        // 教务导入（学校选择）：外壳全在 :ui-shared，无需平台能力（索引更新走共享 HttpService）。
        AppRoute.EducationalImport -> EducationalImportRoute(router = router)
        // 网页导入：独立一层（右侧推入），网页宿主用 WKWebView + JS 桥 shim（见 IosWebHost）。
        AppRoute.WebImport -> WebImportRoute(
            router = router,
            webHostFactory = { config, events -> rememberIosWebHost(config, events) },
        )
    }
}
