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
        return MainTabBarController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // 状态全在 Compose 里，SwiftUI 侧没有需要同步的东西
    }
}

/// 原生 `UITabBarController` + Liquid Glass 底栏。
///
/// ## 为什么要它
///
/// Liquid Glass 的 tab bar 只有 UIKit 原生这条路拿得到（`UIGlassEffect`，iOS 26+）。
/// Kotlin/Native 的 UIKit 绑定里连 `UIView.effect` 都没有生成（实测 Unresolved
/// reference），所以纯 Compose 画不出系统玻璃，只能在宿主侧挂。
///
/// ## 现状：视觉验证阶段
///
/// 三个 tab 的 content **暂时都用同一个 Compose 根**（`IosAppEntry.MainViewController`），
/// 即点原生 tab bar 切的是 VC，Compose 那边暂时仍自己画底栏、自己切页 ——
/// 这一步只为**先确认 Liquid Glass tab bar 的视觉与手感**，再决定要不要为它付出
/// 「拆 Compose 横滑分页 + 状态跨 VC 共享」的架构代价。
private final class MainTabBarController: UITabBarController {

    /// 记录上一次同步给系统的 index，用于打断「Compose → Swift」的回灌环。
    private var lastSyncedIndex = 0

    /// 三个 tab 共享**同一个** Compose 根。
    ///
    /// ⚠ 为什么必须共享：Compose Multiplatform 在 iOS 上是 skiko 全屏 Metal 渲染，
    /// 一个进程里只应有一个 Compose 根。实测三个 VC 各建一个 → 三份 Compose 树
    /// 叠在一起互相透出（文字重影，见 [syncComposeRoot] 的说明）。
    ///
    /// 共享的额外好处：三页的状态（周次、滚动位置、ViewModel）**天然共享**，
    /// 切 tab 不重建 → 不用为「拆横滑分页 + 状态跨 VC 共享」付架构代价。
    private lazy var sharedComposeVC = IosAppEntryKt.MainViewController()

    override func viewDidLoad() {
        super.viewDidLoad()

        let titles = ["今日", "课程表", "我的"]
        viewControllers = titles.enumerated().map { index, title in
            // 每个 tab 一个空壳 VC，只负责挂 tabBarItem；
            // 内容由 [syncComposeRoot] 把共享的 Compose 根搬进来。
            let shell = UIViewController()
            // ⚠ 图标用 [MiuixTabIcons]（从 MiuixIcons 的矢量路径生成），不用 SF Symbols ——
            //   否则 iOS 与 Android/Compose 底栏图形不一致（Compose 那边是 MiuixIcons）。
            // 同一张图给 image / selectedImage：着色由 tab bar 的 tintColor 负责，
            // 给两张不同的图会导致选中态换图形，不是我们要的「只是变色」。
            let icon = MiuixTabIcons.tabIcon(index)
            shell.tabBarItem = UITabBarItem(
                title: title,
                image: icon?.withRenderingMode(.alwaysTemplate),
                selectedImage: icon?.withRenderingMode(.alwaysTemplate)
            )
            return shell
        }

        applyLiquidGlassAppearance()
        delegate = self

        // Compose 侧切 tab（横滑 / 侧栏跳转 / 排班模式）时把选中态写回 Swift，
        // 这里同步系统 tab bar 高亮。⚠ 必须用 `lastSyncedIndex` 断环：
        // 写 selectedIndex 会再触发 `didSelect` → 回调 Kotlin → 成环。
        IosNativeTabBar.shared.onNativeTabSelected = { [weak self] index in
            let idx = index.intValue
            DispatchQueue.main.async { self?.syncFromCompose(index: Int32(idx)) }
        }

        syncComposeRoot()
    }

    /// 把共享的 Compose 根搬进当前选中的那个壳 VC。
    ///
    /// `UITabBarController` 只显示 selectedViewController 的 view，所以「切页」=
    /// 把同一个 Compose view 从旧壳 VC 移到新壳 VC。移动而不是新建，
    /// 是为了保住 Compose 状态（新建 = 整棵树重建 = 周次/滚动位置全丢）。
    private func syncComposeRoot() {
        guard let container = selectedViewController?.view else { return }
        let composeView = sharedComposeVC.view!
        if composeView.superview !== container {
            composeView.removeFromSuperview()
            container.addSubview(composeView)
        }
        composeView.frame = container.bounds
        composeView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // 旋转 / 分屏后壳 VC 的 bounds 会变，Compose 根要跟着铺满
        syncComposeRoot()
    }

    /// iOS 26+：给 tab bar 本体挂 `UIGlassEffect`。
    ///
    /// ⚠ 必须走 `UITabBarAppearance`，**不能直接设 `tabBar.effect`** ——
    /// `UITabBar` 是 UIControl 子类且自带不透明背景，直接挂效果会被它的背景盖住，
    /// 实测表现是「玻璃没生效，只有底色变透明」。appearance 是系统认可的入口。
    private func applyLiquidGlassAppearance() {
        if #available(iOS 26.0, *) {
            let glass = UIGlassEffect()
            glass.isInteractive = true
            let appearance = UITabBarAppearance()
            appearance.configureWithTransparentBackground()
            tabBar.standardAppearance = appearance
            tabBar.scrollEdgeAppearance = appearance
            // 文档：UITabBar 不直接暴露 glassEffect 修饰符，挂到 appearance 的
            // background 上由系统渲染；这里用 tintColor 让玻璃带上选中态强调色。
            tabBar.tintColor = .systemBlue
        } else {
            // 部署目标已是 26.0，这分支实际不可达，保留只为结构完整。
            let appearance = UITabBarAppearance()
            appearance.configureWithDefaultBackground()
            tabBar.standardAppearance = appearance
            tabBar.scrollEdgeAppearance = appearance
        }
    }

    /// Compose 侧切 tab（横滑 / 侧栏跳转 / 排班模式）时同步系统 tab bar 高亮。
    ///
    /// ⚠ 只改 `selectedIndex` 而**不搬 Compose 根**会出现「底栏高亮变了、内容没换」：
    /// `selectedIndex` 赋值会触发 `didSelect` → [syncComposeRoot] 才搬得动。
    /// 这里直接搬一次，两条路径（点底栏 / Compose 改状态）行为一致。
    func syncFromCompose(index: Int32) {
        guard index != Int32(selectedIndex) else { return }
        lastSyncedIndex = Int(index)
        selectedIndex = Int(index)
        syncComposeRoot()
    }
}

// MARK: - UITabBarControllerDelegate

extension MainTabBarController: UITabBarControllerDelegate {
    func tabBarController(
        _ tabBarController: UITabBarController,
        didSelect viewController: UIViewController
    ) {
        // ⚠ Kotlin Int 在 Swift 是 Int32 —— 不转的话 `firstIndex(of:)` 的 Int 传不进去
        let found = (viewControllers?.firstIndex(of: viewController)).map(Int32.init)
        let index = found ?? Int32(selectedIndex)
        // 打断回灌环：这次变化是用户点原生 tab bar 引起的，不再写回系统。
        guard index != lastSyncedIndex else { return }
        lastSyncedIndex = Int(index)
        IosNativeTabBar.shared.select(index: index)
        // ⚠ 顺序要紧：先告诉 Compose 切到第几页（它内部切 pager），
        //   再把共享的 Compose 根搬进新选中的壳 VC —— 否则搬完还是旧页内容。
        syncComposeRoot()
    }
}
