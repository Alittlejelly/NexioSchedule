package com.haooz.chedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
 * ⚠ 当前是**过渡根**：注入自检 + 一个「关于页」入口（第三十批迁过来的
 * `AboutRoute`，用来验证「`:ui-shared` 的 Route 在 iOS 上真能用」）。
 * 完整导航（`AppNavHost`）还在 `:androidApp`，迁过来之后这里换成真正的入口。
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NexioIosSmokeRoot()
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
private fun NexioIosSmokeRoot() {
    var storeProbe by remember { mutableStateOf("检查中…") }
    var fileProbe by remember { mutableStateOf("检查中…") }
    // 关于页是第三十批从 :androidApp 迁过来的跨平台 Route，这里直接拿来用
    // ⚠ 别在这里引用 AboutRoute 之类的真实页面：它们会牵出 miuix 官方子库
    //（`miuix-preference` 的 ArrowPreference），iOS 链接期报
    // `Undefined symbols … preference#ArrowPreference`（风险 R13，见迁移计划 §9）。
    // 那个坑解决之前，iOS 入口只跑注入自检。

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
    }
}
