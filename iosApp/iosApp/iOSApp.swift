import SwiftUI
import NexioShared

@main
struct iOSApp: App {

    init() {
        // 对应 Android 的 `NexioApplication.onCreate()`：注入 AppInfo / AppStorage /
        // AppFiles 三件套。**必须早于任何 Compose 界面出现** —— 缺任一个，首次
        // 使用时会抛异常（不是静默失败）。Kotlin 顶层函数导出为 `文件名Kt.函数名`。
        IosAppEntryKt.bootstrapNexioIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
