package com.haooz.chedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.haooz.chedule.data.AppFiles
import com.haooz.chedule.data.AppInfo
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.NsFileAppFile
import com.haooz.chedule.data.UserDefaultsStore
import com.haooz.chedule.data.defaultDocumentsDir
import com.haooz.chedule.data.toByteArray
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
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
}

/**
 * iOS 的 Compose 根容器 —— Swift 侧用 `UIViewControllerRepresentable` 包它。
 *
 * 结构与 Android `MainActivity.setContent` 同形：主界面常驻底座（`MainLayerTransition`）
 * + 子页叠加层（`AppNavHost`）。当前主界面还是注入自检页（真课表界面要等
 * `CourseScheduleApp` 依赖的宿主能力在 iOS 侧齐备）；子页导航已经是**真路由**。
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NexioIosRoot()
        }
    }
}

/**
 * iOS 宿主首页当前能进的路由清单。
 *
 * 真课表主界面（`CourseScheduleApp`）还在 `:androidApp` 的 `MainActivity` 里，
 * 迁过来之前，首页就是这个**路由导航台** —— 每个已迁路由都能从这儿点进去，
 * 便于在模拟器里逐页人工验证（尤其是 `@Composable` 里那些编译期查不出的东西：
 * 玻璃采样、折叠顶栏、对话框、文件选择器降级提示）。
 *
 * ⚠ 这是**临时页**，等 `CourseScheduleApp` 迁进 `:ui-shared` 就整块删掉
 * （连同 [NexioIosSmokeHome]），换成真主界面。
 */
private val IOS_HOME_ROUTES: List<Pair<String, AppRoute>> = listOf(
    "关于" to AppRoute.About,
    "应用偏好设置" to AppRoute.PreferenceSettings,
    "更新设置" to AppRoute.UpdateSettings,
    "课程提醒" to AppRoute.CourseReminder,
    "课表节数与时间" to AppRoute.CourseTimeSettings,
    "课程管理" to AppRoute.CourseManage,
    "节假日与调休" to AppRoute.HolidaySettings,
    "WebDAV 云备份" to AppRoute.WebDavSettings,
    "课表导入" to AppRoute.ScheduleImport,
    "课表导出" to AppRoute.ScheduleExport,
    "课表备份" to AppRoute.ScheduleBackup,
    "本地备份" to AppRoute.LocalBackup,
    "AI 文本导入" to AppRoute.AiImport,
    "桌面小部件" to AppRoute.WidgetIntro,
    "更新日志" to AppRoute.Changelog,
    "开源协议" to AppRoute.License,
    "隐私政策" to AppRoute.PrivacyPolicy,
    "交流与反馈" to AppRoute.Communication,
    "捐赠支持" to AppRoute.AppreciateAuthor,
)

/**
 * iOS 的导航根 —— 对应 MainActivity 的 `Box { MainLayerTransition + AppNavHost }`。
 *
 * iOS 没有系统返回键，`rememberNavigationBack` 的 skiko 侧 handler 是空实现；
 * 返回全靠页面自己的返回按钮 → `router.popBack()`（`AboutScreen` 自带）。
 */
@Composable
private fun NexioIosRoot() {
    val router = rememberAppRouter()
    val mainTransition = rememberNavTransitionState(
        initialValue = if (router.hasOverlay) 1f else 0f,
    )
    Box(Modifier.fillMaxSize()) {
        // 主界面常驻底座（当前是路由导航台，见 NexioIosSmokeHome）
        MainLayerTransition(mainTransition) {
            NexioIosSmokeHome(onNavigate = { router.navigate(it) })
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
                // 课表导入/导出/备份（第三十七批）：iOS 无系统文件选择器，
                // 走默认 NoopScheduleFileController；口令通道四条都用默认「不支持」
                AppRoute.ScheduleImport -> BackupAndMigrationRoute(
                    router = router,
                    mode = ScheduleDataManageMode.Import,
                )
                AppRoute.ScheduleExport -> BackupAndMigrationRoute(
                    router = router,
                    mode = ScheduleDataManageMode.Export,
                )
                AppRoute.ScheduleBackup -> BackupAndMigrationRoute(
                    router = router,
                    mode = ScheduleDataManageMode.Backup,
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

/**
 * iOS 临时首页：**注入自检** + **路由导航台**。
 *
 * ## 上半部分为什么还在
 *
 * `AppStorage` / `AppFiles` 的 iOS 实现是**新写的**，编译通过不代表落盘位置与还原
 * 类型正确（`UserDefaultsStore` 的 Int/Long 不分是已知雷区）。与其等真机调试时才发现，
 * 不如让首屏自己报出来 —— 这两行是**唯一的运行时证据**，别删。
 *
 * ## 下半部分
 *
 * 每个已迁路由一个按钮，模拟器里能逐页点进去人工验证。
 */
@Composable
private fun NexioIosSmokeHome(onNavigate: (AppRoute) -> Unit) {
    var storeProbe by remember { mutableStateOf("检查中…") }
    var fileProbe by remember { mutableStateOf("检查中…") }

    LaunchedEffect(Unit) {
        storeProbe = runCatching {
            val store = AppStorage.store("ios_smoke_test")
            store.edit { putString("k", "v") }
            store.edit { putInt("i", 42) }
            if (store.getString("k", "") == "v" && store.getInt("i", 0) == 42) "OK" else "值不一致"
        }.getOrElse { "失败：${it.message}" }

        fileProbe = runCatching {
            val file = AppFiles.root.resolve("ios_smoke_test").resolve("probe.txt")
            file.writeBytes("课程表".encodeToByteArray())
            if (file.readText() == "课程表") "OK" else "内容不一致"
        }.getOrElse { "失败：${it.message}" }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(48.dp))
        Text("Nexio课程表 · iOS", style = MaterialTheme.typography.headlineMedium)
        Text("版本 ${AppInfo.version}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text("存储(AppStorage/UserDefaults)：$storeProbe", style = MaterialTheme.typography.bodySmall)
        Text("文件(AppFiles/NSFileManager)：$fileProbe", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(IOS_HOME_ROUTES) { (label, route) ->
                Button(
                    onClick = { onNavigate(route) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(label) }
            }
        }
    }
}
