// xcode: set sdk=iOS

import SwiftUI
import NexioShared

struct ContentView: View {
    var body: some View {
        ComposeRootRepresentable()
            .ignoresSafeArea()   // Compose 自己管状态栏与安全区，别让 SwiftUI 再叠一层
    }
}

/// 把 Kotlin 侧 `IosAppEntry.kt` 的 `MainViewController()` 接进 SwiftUI。
///
/// 目前那边渲染的是「注入自检」界面；等宿主层（`AppNavHost` / `AppRouteContent`）
/// 迁进 `:ui-shared` 之后，这里一行都不用改 —— 换的是 Kotlin 侧的 Compose 根。
private struct ComposeRootRepresentable: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosAppEntryKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // 状态全在 Compose 里，SwiftUI 侧没有需要同步的东西
    }
}
