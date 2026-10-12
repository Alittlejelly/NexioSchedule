package com.haooz.chedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.haooz.chedule.data.AppFiles
import com.haooz.chedule.data.AppInfo
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.IosScheduleFileController
import com.haooz.chedule.data.IosWallpaperBackend
import com.haooz.chedule.data.IosWallpaperPicker
import com.haooz.chedule.data.NexioLog
import com.haooz.chedule.data.NsFileAppFile
import com.haooz.chedule.data.UserDefaultsStore
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.defaultDocumentsDir
import com.haooz.chedule.data.toByteArray
import com.haooz.chedule.ui.activities.CourseScheduleApp
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.screens.TodayAssistantCard
import com.haooz.chedule.ui.screens.rememberWeatherIos
import com.haooz.chedule.ui.utils.IosSharedFileReceiver
import com.haooz.chedule.ui.utils.rememberIosShareAction
import com.haooz.chedule.ui.utils.IosSystemBars
import com.haooz.chedule.ui.utils.IosToast
import com.haooz.chedule.ui.navigation.AboutRoute
import com.haooz.chedule.ui.navigation.AiImportRoute
import com.haooz.chedule.ui.navigation.AppreciateAuthorRoute
import com.haooz.chedule.ui.navigation.AppNavHost
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.BackupAndMigrationRoute
import com.haooz.chedule.ui.navigation.ChangelogRoute
import com.haooz.chedule.ui.navigation.CommunicationRoute
import com.haooz.chedule.ui.navigation.CourseManageRoute
import com.haooz.chedule.ui.navigation.CourseReminderRoute
import com.haooz.chedule.ui.navigation.CourseTimeSettingsRoute
import com.haooz.chedule.ui.navigation.HolidaySettingsRoute
import com.haooz.chedule.ui.navigation.LicenseRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.navigation.LocalBackupRoute
import com.haooz.chedule.ui.navigation.MainLayerTransition
import com.haooz.chedule.ui.navigation.PreferenceSettingsRoute
import com.haooz.chedule.ui.navigation.PrivacyPolicyRoute
import com.haooz.chedule.ui.navigation.UpdateSettingsRoute
import com.haooz.chedule.ui.navigation.WebDavSettingsRoute
import com.haooz.chedule.ui.navigation.WidgetIntroRoute
import com.haooz.chedule.ui.navigation.rememberAppRouter
import com.haooz.chedule.ui.navigation.rememberNavTransitionState
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController

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
 * 这里**只做三件套**。Toast 不用注入 —— `showToast` 在 `:core` 的 `nativeMain`
 * 已有 actual（落到日志）；`ScheduleEffects` 也不注入（未注入时静默丢弃，
 * iOS 还没有提醒 / 小组件实现）。
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

/**
 * iOS 的 Compose 根容器 —— Swift 侧用 `UIViewControllerRepresentable` 包它。
 *
 * 结构与 Android `MainActivity.setContent` 同形：主界面常驻底座（`MainLayerTransition`）
 * + 子页叠加层（`AppNavHost`）。主界面已是**真课表界面** `CourseScheduleApp`
 * （第四十三批迁入 `:ui-shared`，iOS 用默认注入即可用）；子页导航是真路由。
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    // ⚠ 主题根必须是 [CourseScheduleTheme]（= MiuixTheme + theme_mode 偏好 + 触感门控 +
    //   :miuix 能力注入），与 Android MainActivity / 桌面 Main **同一个**。
    //   原来这里包的是 MaterialTheme —— 于是：
    //   ① 应用内深浅色切换对 iOS 完全无效（theme_mode 没人读，也没人在 MiuixTheme 里应用）；
    //   ② 二级/三级页自己的 MiuixTheme(controller=...) 读不到上层的 colorSchemeMode，
    //      只能退回系统深浅色 → 「二级三级页面锁死跟随系统」。
    //   Android / 桌面的调用点见 CourseScheduleTheme 定义处。
    CourseScheduleTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NexioIosRoot()
        }
    }
}

/**
 * iOS 的导航根 —— 对应 MainActivity 的 `Box { MainLayerTransition + AppNavHost }`。
 *
 * iOS 没有系统返回键，`rememberNavigationBack` 的 skiko 侧 handler 是空实现；
 * 返回全靠页面自己的返回按钮 → `router.popBack()`（`AboutScreen` 自带）。
 *
 * ⚠ [LocalAppRouter] **必须在这里 provide**：页面里有若干入口（如
 * `BackupAndMigrationScreen` 的「本地备份」）不接收 router 参数，而是直接读
 * 这个 CompositionLocal —— 不 provide 就在点进去那一刻抛
 * `IllegalStateException: LocalAppRouter 未提供`。
 * Android 侧由 `MainActivity` 在最外层 provide，这里是 iOS 的对应位置。
 */
@Composable
private fun NexioIosRoot() {
    val router = rememberAppRouter()
    val mainTransition = rememberNavTransitionState(
        initialValue = if (router.hasOverlay) 1f else 0f,
    )
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
            // 主界面常驻底座：**真课表界面**（第四十三批）。尚未接的宿主能力
            // （分享/选壁纸/今日助手卡）走默认「无此能力」，入口不渲染而不是留个死按钮。
            MainLayerTransition(mainTransition) {
                CourseScheduleApp(
                    privacyConsented = true,
                    onSystemBarsChanged = { statusDark, _ ->
                        IosSystemBars.update(statusDark)
                    },
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
                )
            }
            // 子页叠加层：整栈常驻 + 转场 —— 与 Android 同一个 AppNavHost
            AppNavHost(router, mainTransition = mainTransition) { route ->
                when (route) {
                    AppRoute.About -> AboutRoute(
                        router = router,
                        appVersion = AppInfo.version,
                    )
                    // 纯文档壳路由（第三十三批迁入 commonMain），iOS 直接可用
                    AppRoute.Changelog -> ChangelogRoute(router)
                    AppRoute.License -> LicenseRoute(router)
                    AppRoute.PrivacyPolicy -> PrivacyPolicyRoute(router)
                    AppRoute.Communication -> CommunicationRoute(router)
                    AppRoute.AppreciateAuthor -> AppreciateAuthorRoute(router)
                    // 带注入的文档壳（第三十四批）：iOS 用默认 Noop 能力，剪贴板注入 UIPasteboard
                    AppRoute.PreferenceSettings -> PreferenceSettingsRoute(router)
                    AppRoute.UpdateSettings -> UpdateSettingsRoute(router)
                    AppRoute.LocalBackup -> LocalBackupRoute(router)
                    AppRoute.AiImport -> AiImportRoute(
                        router = router,
                        onCopyToClipboard = { text ->
                            UIPasteboard.generalPasteboard.string = text
                        },
                    )
                    // 节假日（第三十五批）：网络拉取已是跨平台 HttpService（iOS = NSURLSession）
                    AppRoute.HolidaySettings -> HolidaySettingsRoute(router)
                    // 小部件引导（第三十六批）：iOS 无 AppWidget，`onRequestWidgetRefresh` 不注入
                    AppRoute.WidgetIntro -> WidgetIntroRoute(router)
                    // 课程提醒（第三十六批）：iOS 没有闹钟/超级岛，提醒控制器用 Noop、
                    // 测试通知不注入 → 底部按钮不渲染（宁可不给，也不给点了没反应的）
                    AppRoute.CourseReminder -> CourseReminderRoute(router)
                    // WebDAV（第三十六批）：备份/恢复本体已在 :core，只差提醒重排副作用
                    AppRoute.WebDavSettings -> WebDavSettingsRoute(router)
                    // 课表导入/导出/备份：接 iOS 原生文件选择器（UIDocumentPicker）。
                    // 口令导入/第三方导入（要服务端接口 + 平台分享能力）仍走默认「不支持」。
                    AppRoute.ScheduleImport -> BackupAndMigrationRoute(
                        router = router,
                        mode = ScheduleDataManageMode.Import,
                        scheduleFileController = fileController,
                    )
                    AppRoute.ScheduleExport -> BackupAndMigrationRoute(
                        router = router,
                        mode = ScheduleDataManageMode.Export,
                        scheduleFileController = fileController,
                    )
                    AppRoute.ScheduleBackup -> BackupAndMigrationRoute(
                        router = router,
                        mode = ScheduleDataManageMode.Backup,
                        scheduleFileController = fileController,
                    )
                    // 课表节数与时间 / 课程管理（第三十八批）：iOS 没有自由小窗，传 false
                    AppRoute.CourseTimeSettings -> CourseTimeSettingsRoute(router)
                    AppRoute.CourseManage -> CourseManageRoute(router)
                    // 教务导入要 WKWebView 桥接（IosWebHost 未写），暂不开
                    AppRoute.EducationalImport -> {}
                    else -> {}
                }
            }
        }
    }
}
