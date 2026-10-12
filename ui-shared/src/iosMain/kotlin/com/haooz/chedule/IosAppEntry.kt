package com.haooz.chedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.haooz.chedule.ui.navigation.AboutRoute
import com.haooz.chedule.ui.navigation.AppNavHost
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.MainLayerTransition
import com.haooz.chedule.ui.navigation.rememberAppRouter
import com.haooz.chedule.ui.navigation.rememberNavTransitionState
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
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
        // 主界面常驻底座（当前是注入自检页，见 NexioIosSmokeHome）
        MainLayerTransition(mainTransition) {
            NexioIosSmokeHome(onOpenAbout = { router.navigate(AppRoute.About) })
        }
        // 子页叠加层：整栈常驻 + 转场 —— 与 Android 同一个 AppNavHost
        AppNavHost(router, mainTransition = mainTransition) { route ->
            when (route) {
                AppRoute.About -> AboutRoute(
                    router = router,
                    appVersion = AppInfo.version,
                )
                // 其余路由等对应页面/能力迁齐后在此接线
                else -> {}
            }
        }
    }
}

/**
 * 注入自检：存储和文件各做一次 round-trip。
 *
 * 为什么首屏要显示这个 —— `AppStorage` / `AppFiles` 的 iOS 实现是**新写的**，
 * 编译通过不代表落盘位置与还原类型正确（`UserDefaultsStore` 的 Int/Long 不分
 * 是已知雷区）。与其等真机调试时才发现，不如让首屏自己报出来。
 */
@Composable
private fun NexioIosSmokeHome(onOpenAbout: () -> Unit) {
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

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nexio课程表 · iOS", style = MaterialTheme.typography.headlineMedium)
        Text("版本 ${AppInfo.version}", style = MaterialTheme.typography.bodyMedium)
        Text("存储(AppStorage/UserDefaults)：$storeProbe")
        Text("文件(AppFiles/NSFileManager)：$fileProbe")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenAbout) { Text("打开关于页") }
    }
}
