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
/// Kotlin 那边渲染的是真课表主界面 + `AppNavHost` 子页导航；宿主能力（文件选择器 /
/// 分享 / 选壁纸 / 天气）都从 `:ui-shared` 注入。Kotlin 侧换 Compose 根时这里不用改。
private struct ComposeRootRepresentable: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // 状态栏样式：Kotlin 侧（`IosSystemBars`）只持状态，真正调用必须在 Swift 做 ——
        // `UIApplication.setStatusBarStyle` 是 iOS 9 起废弃的 API，Kotlin/Native 的
        // UIKit 绑定没有生成它（实测 Unresolved reference）。配合 Info.plist 的
        // `UIViewControllerBasedStatusBarAppearance=false` 才生效。
        IosSystemBars.shared.onApply = {
            let style: UIStatusBarStyle =
                IosSystemBars.shared.statusBarDark ? .lightContent : .darkContent
            UIApplication.shared.setStatusBarStyle(style, animated: true)
        }
        return IosAppEntryKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // 状态全在 Compose 里，SwiftUI 侧没有需要同步的东西
    }
}
