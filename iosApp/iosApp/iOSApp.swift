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
                // 外部分享进来的文件：在「文件」App 或其它 app 里选「用 Nexio课程表 打开」
                // 会走这里。Kotlin 侧 `IosSharedFileReceiver` 用 Compose 的 mutableStateOf
                // 存，所以这里更新后主界面会自动重组并弹出导入确认框。
                .onOpenURL { url in
                    IosSharedFileReceiver.shared.onOpenURL(url: url.absoluteString)
                }
        }
    }
}
