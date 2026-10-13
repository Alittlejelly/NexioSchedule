// xcode: set sdk=iOS

import SwiftUI
import UIKit
import NexioShared

struct ContentView: View {
    var body: some View {
        ComposeRootRepresentable()
            .ignoresSafeArea()   // Compose 自己管状态栏与安全区
    }
}

/// Retains the original Compose app beneath native document navigation.
private struct ComposeRootRepresentable: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // 状态栏样式：**交给系统自适应**，App 侧不接管。
        //
        // ⚠ 两条自己接管的路都不通，别再试：
        //  1. `UIApplication.setStatusBarStyle` 自 iOS 27 起是 no-op；
        //  2. 把 SwiftUI 的 root VC 包一层、在 wrapper 上覆写 `preferredStatusBarStyle`
        //     → 直接崩：UIKit 禁止把 root VC 加为 child，而 `UIHostingController` 正是 root。
        //
        // Info.plist 的 `UIViewControllerBasedStatusBarAppearance=false` 也已删 ——
        // 那个键会让系统去读已废弃的全局 API（就是刷屏警告的来源）。

        // 原生 toast（液态玻璃）：渲染放 Swift 侧（见 NativeToast），Kotlin 只留注入点。
        // ⚠ `longDuration` 是 `KotlinBoolean` 而不是 `Bool` —— Kotlin/Native 导出**函数类型**时
        // 会把基本类型 boxed，必须 `.boolValue` 拆箱。
        IosToast.shared.nativeShow = { message, longDuration in
            NativeToast.show(message, longDuration: longDuration.boolValue)
        }
        return NativeSessionController(host: IosAppHost())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Kotlin publishes state changes through the host callback.
    }
}

/// The floating Compose surface sits above both native navigation and the retained root.
private final class NativeSessionController: UIViewController {
    private let host: IosAppHost
    private let rootController: AppHostController
    private let documentNavigation: NativeDocumentNavigationController
    private let floatingContainer = FloatingOverlayPassthroughView()

    init(host: IosAppHost) {
        self.host = host
        rootController = AppHostController(host: host)
        documentNavigation = NativeDocumentNavigationController(host: host, root: rootController)
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        embed(documentNavigation, in: view)
        floatingContainer.backgroundColor = .clear
        pin(floatingContainer, to: view)
        embed(host.floatingController, in: floatingContainer)
        host.onStateChanged = { [weak self] in
            if Thread.isMainThread {
                self?.applyState()
            } else {
                DispatchQueue.main.async { [weak self] in self?.applyState() }
            }
        }
        applyState()
    }

    private func applyState() {
        guard isViewLoaded else { return }
        rootController.applyState()
        documentNavigation.applyHostState()
        let active = host.floatingOverlayActive
        floatingContainer.overlayActive = active
        floatingContainer.accessibilityElementsHidden = !active
        host.floatingController.view.isUserInteractionEnabled = active
        host.floatingController.view.accessibilityViewIsModal = active
        host.floatingController.view.accessibilityElementsHidden = !active
    }

    private func embed(_ controller: UIViewController, in container: UIView) {
        addChild(controller)
        pin(controller.view, to: container)
        controller.didMove(toParent: self)
    }

    private func pin(_ child: UIView, to parent: UIView) {
        child.translatesAutoresizingMaskIntoConstraints = false
        parent.addSubview(child)
        NSLayoutConstraint.activate([
            child.leadingAnchor.constraint(equalTo: parent.leadingAnchor),
            child.trailingAnchor.constraint(equalTo: parent.trailingAnchor),
            child.topAnchor.constraint(equalTo: parent.topAnchor),
            child.bottomAnchor.constraint(equalTo: parent.bottomAnchor)
        ])
    }

    deinit {
        host.onStateChanged = nil
    }
}

private final class FloatingOverlayPassthroughView: UIView {
    var overlayActive = false

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        overlayActive ? super.hitTest(point, with: event) : nil
    }
}

/// Keeps the original main, tab bar and legacy navigation overlay together as one root page.
private final class AppHostController: UIViewController {
    private let host: IosAppHost
    private let mainContainer = UIView()
    private let chromeContainer = TabBarPassthroughView()
    private let chromeController = NativeChromeController()
    private var lastBottomInset: CGFloat = -1

    init(host: IosAppHost) {
        self.host = host
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        view.clipsToBounds = true
        mainContainer.backgroundColor = .clear
        chromeContainer.backgroundColor = .clear

        pin(mainContainer, to: view)
        embed(host.mainController, in: mainContainer)
        pin(chromeContainer, to: mainContainer)
        embed(chromeController, in: chromeContainer)
        chromeContainer.tabBar = chromeController.tabBar
        embed(host.overlayController, in: view)

        chromeController.onUserSelection = { [weak self] index in
            self?.host.selectTab(index: Int32(index))
        }
        chromeController.onLayout = { [weak self] in
            self?.reportBottomInset()
        }
        // 原生截屏桥：把含 TabBar 的全屏图交给 Compose 补快照
        IosNativeCapture.shared.captureScreen = { [weak self] in
            IosNativeCapture.shared.store(data: self?.captureScreenPNG())
        }
        applyState()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateMainTransform()
        updateChromeTransform()
        reportBottomInset()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        reportBottomInset()
    }

    func applyState() {
        guard isViewLoaded else { return }
        chromeController.configure(
            tabCount: Int(host.tabCount),
            shiftMode: host.shiftMode,
            selectedIndex: Int(host.selectedTab)
        )
        let hideChrome = !host.showsNativeTabBar
        if chromeContainer.isHidden != hideChrome {
            chromeContainer.isHidden = hideChrome
            view.setNeedsLayout()
        }
        let chromeStyle: UIUserInterfaceStyle = host.isDark ? .dark : .light
        if chromeController.overrideUserInterfaceStyle != chromeStyle {
            chromeController.overrideUserInterfaceStyle = chromeStyle
        }
        let overlayActive = host.overlayActive
        host.overlayController.view.isUserInteractionEnabled = overlayActive
        host.overlayController.view.accessibilityElementsHidden = !overlayActive
        host.overlayController.view.accessibilityViewIsModal = overlayActive
        mainContainer.isUserInteractionEnabled = !overlayActive
        mainContainer.accessibilityElementsHidden = overlayActive
        updateMainTransform()
        updateChromeTransform()
        reportBottomInset()
    }

    /// 开洞（课表外观）时把原生 TabBar 跟着主内容一起上移缩小。
    private func updateChromeTransform() {
        let scale = CGFloat(min(1, max(0.65, host.cutoutScale)))
        let screenH = view.bounds.height
        let screenW = view.bounds.width
        guard scale < 0.999, screenH > 0, screenW > 0 else {
            if chromeContainer.transform != .identity {
                chromeContainer.transform = .identity
            }
            return
        }
        let layer = chromeContainer.layer
        let centerX = screenW * 0.5
        let centerY = screenH * CGFloat(host.cutoutCenterYRatio)
        let newAnchor = CGPoint(x: 0.5, y: CGFloat(host.cutoutCenterYRatio))
        let oldAnchor = CGPoint(x: 0.5, y: 0.5)
        layer.position = CGPoint(
            x: centerX + (layer.position.x - centerX) * (oldAnchor.x - newAnchor.x),
            y: centerY + (layer.position.y - centerY) * (oldAnchor.y - newAnchor.y)
        )
        layer.anchorPoint = newAnchor
        chromeContainer.transform = CGAffineTransform(scaleX: scale, y: scale)
    }

    private func updateMainTransform() {
        let progress = min(1, max(0, host.mainProgress))
        mainContainer.transform = CGAffineTransform(
            translationX: -0.24 * CGFloat(progress) * view.bounds.width,
            y: 0
        )
    }

    private func reportBottomInset() {
        guard isViewLoaded, view.window != nil else { return }
        let bar = chromeController.tabBar
        let inset: CGFloat
        if chromeContainer.isHidden || bar.isHidden || bar.bounds.isEmpty {
            inset = 0
        } else {
            let barBounds = bar.convert(bar.bounds, to: view)
            inset = min(view.bounds.height, max(0, view.bounds.maxY - barBounds.minY))
        }
        guard abs(lastBottomInset - inset) > 0.5 else { return }
        lastBottomInset = inset
        host.updateBottomBarInset(points: Double(inset))
    }

    private func embed(_ controller: UIViewController, in container: UIView) {
        addChild(controller)
        pin(controller.view, to: container)
        controller.didMove(toParent: self)
    }

    private func pin(_ child: UIView, to parent: UIView) {
        child.translatesAutoresizingMaskIntoConstraints = false
        parent.addSubview(child)
        NSLayoutConstraint.activate([
            child.leadingAnchor.constraint(equalTo: parent.leadingAnchor),
            child.trailingAnchor.constraint(equalTo: parent.trailingAnchor),
            child.topAnchor.constraint(equalTo: parent.topAnchor),
            child.bottomAnchor.constraint(equalTo: parent.bottomAnchor),
        ])
    }

    /// 全屏截图（含原生 TabBar），PNG 字节。供 Compose 侧补进切换课表页快照 ——
    private func captureScreenPNG() -> Data? {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = view.window?.screen.scale ?? UIScreen.main.scale
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(size: view.bounds.size, format: format)
        let image = renderer.image { _ in
            view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
        }
        return image.pngData()
    }
}

/// Empty native tab content must not intercept the Compose content below it.
private final class TabBarPassthroughView: UIView {
    weak var tabBar: UITabBar?

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard !isHidden, alpha > 0.01, isUserInteractionEnabled,
              let bar = tabBar, !bar.isHidden, bar.alpha > 0.01,
              bar.isUserInteractionEnabled else { return nil }
        let barPoint = convert(point, to: bar)
        guard bar.point(inside: barPoint, with: event) else { return nil }
        return bar.hitTest(barPoint, with: event)
    }
}

/// UIKit owns the tab bar appearance, including automatic OS-version adaptation.
private final class NativeChromeController: UITabBarController, UITabBarControllerDelegate {
    var onUserSelection: ((Int) -> Void)?
    var onLayout: (() -> Void)?
    private var configuredShiftMode: Bool?
    private var configuredTabCount = 0

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        view.isOpaque = false
        delegate = self
    }

    func configure(tabCount: Int, shiftMode: Bool, selectedIndex: Int) {
        let titles = shiftMode ? ["排班课表", "设置"] : ["今日", "课程表", "我的"]
        let count = min(titles.count, max(1, tabCount))
        if configuredShiftMode != shiftMode || configuredTabCount != count {
            configuredShiftMode = shiftMode
            configuredTabCount = count
            let shells = (0..<count).map { index -> UIViewController in
                let controller = UIViewController()
                controller.view.backgroundColor = .clear
                controller.view.isOpaque = false
                let icon: UIImage?
                if shiftMode {
                    icon = index == 0 ? MiuixTabIcons.imageMonths : MiuixTabIcons.imageSettings
                } else {
                    icon = MiuixTabIcons.tabIcon(index)
                }
                let template = icon?.withRenderingMode(.alwaysTemplate)
                controller.tabBarItem = UITabBarItem(
                    title: titles[index], image: template, selectedImage: template
                )
                return controller
            }
            setViewControllers(shells, animated: false)
        }
        let target = min(count - 1, max(0, selectedIndex))
        if self.selectedIndex != target {
            self.selectedIndex = target
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        onLayout?()
    }

    func tabBarController(
        _ tabBarController: UITabBarController,
        didSelect viewController: UIViewController
    ) {
        guard let index = viewControllers?.firstIndex(of: viewController) else { return }
        onUserSelection?(index)
    }
}

/// 原生提示条（对应 Android 的 `Toast`）—— **液态玻璃**渲染。
///
/// ⚠ 渲染刻意放 Swift 侧（Kotlin 侧只留一个注入点 `IosToast.nativeShow`）：
///  1. iOS 26 的 `UIGlassEffect` 在 Kotlin/Native 的 UIKit 绑定里虽然编译得过，
///     但运行期行为不可控；
///  2. 取 window 必须走 `connectedScenes` —— `UIApplication.windows` 在 iOS 13+ 已废弃，
///     **启动早期会返回空**（Kotlin 侧原来的写法就踩了这个，表现为「启动时的提示条不见」）。
///
/// 出现/消失带缩放动画：0.85 → 1 弹簧弹入（轻微过冲）配淡入，1 → 0.9 缩回配淡出。
/// 时长对齐 `LENGTH_SHORT`(2s) / `LENGTH_LONG`(3.5s)；**不合并**，连续调用弹两条、上下错开堆叠；
/// 不拦截触摸（`isUserInteractionEnabled = false`）。
enum NativeToast {

    private static let shortSeconds: TimeInterval = 2.0
    private static let longSeconds: TimeInterval = 3.5
    private static let maxWidth: CGFloat = 280
    private static let hPadding: CGFloat = 14
    private static let vPadding: CGFloat = 10
    private static let bottomInset: CGFloat = 140
    private static let stackGap: CGFloat = 8
    private static let cornerRadius: CGFloat = 16

    /// 正在显示的提示条，用于上下错开堆叠（各自独立计时）
    private static var active: [UIView] = []

    static func show(_ message: String, longDuration: Bool) {
        guard !message.isEmpty else { return }
        // 可能从协程/回调线程调用，UI 操作统一回主队列
        DispatchQueue.main.async {
            guard let window = keyWindow() else { return }
            let toast = makeToast(message)
            let w = toast.frame.width
            let h = toast.frame.height

            // 已有提示条时往上错开，避免叠在一起
            var y = window.bounds.height - bottomInset
            for existing in active {
                y = min(y, existing.frame.minY - stackGap - h)
            }
            toast.frame = CGRect(x: (window.bounds.width - w) / 2, y: y, width: w, height: h)
            window.addSubview(toast)
            active.append(toast)

            // 出现：从 0.85 弹回 1（弹簧带轻微过冲）+ 淡入。
            // ⚠ transform 要在设完 frame 之后再改 —— 反过来 frame 会被缩放影响。
            toast.transform = CGAffineTransform(scaleX: 0.85, y: 0.85)
            toast.alpha = 0
            UIView.animate(
                withDuration: 0.34,
                delay: 0,
                usingSpringWithDamping: 0.78,
                initialSpringVelocity: 0,
                options: [.allowUserInteraction, .beginFromCurrentState]
            ) {
                toast.transform = .identity
                toast.alpha = 1
            }

            DispatchQueue.main.asyncAfter(
                deadline: .now() + (longDuration ? longSeconds : shortSeconds)
            ) {
                // 消失：缩回 0.9 并淡出，动画结束再摘视图
                UIView.animate(
                    withDuration: 0.22,
                    delay: 0,
                    options: [.allowUserInteraction, .beginFromCurrentState]
                ) {
                    toast.transform = CGAffineTransform(scaleX: 0.9, y: 0.9)
                    toast.alpha = 0
                } completion: { _ in
                    toast.removeFromSuperview()
                    active.removeAll { $0 === toast }
                }
            }
        }
    }

    /// 组装提示条（尺寸与子视图位置都已摆好，调用方直接设 frame 定位即可）。
    private static func makeToast(_ message: String) -> UIView {
        let label = UILabel()
        label.text = message
        label.numberOfLines = 0
        label.textAlignment = .center
        label.font = .systemFont(ofSize: 14)
        label.isUserInteractionEnabled = false

        // 内边距靠实测后手动加（attributed string 太重）
        let textSize = label.sizeThatFits(
            CGSize(width: maxWidth - hPadding * 2, height: .greatestFiniteMagnitude)
        )
        let w = textSize.width + hPadding * 2
        let h = textSize.height + vPadding * 2

        let container: UIView
        if #available(iOS 26.0, *) {
            // Liquid Glass 自带边缘光与背景折射，不用再自己描边
            label.textColor = .label
            let glass = UIVisualEffectView(effect: UIGlassEffect())
            glass.isUserInteractionEnabled = false
            glass.clipsToBounds = true
            glass.layer.cornerRadius = cornerRadius
            // 连续曲率，对齐 Compose 侧的 ContinuousRoundedRectangle
            glass.layer.cornerCurve = .continuous
            // 直接加到 effectView 上会被效果层盖住，必须走 contentView
            glass.contentView.addSubview(label)
            container = glass
        } else {
            label.textColor = .white
            let plain = UIView()
            plain.isUserInteractionEnabled = false
            plain.backgroundColor = UIColor(white: 0, alpha: 0.82)
            plain.clipsToBounds = true
            plain.layer.cornerRadius = cornerRadius
            plain.layer.cornerCurve = .continuous
            plain.addSubview(label)
            container = plain
        }
        label.frame = CGRect(x: hPadding, y: vPadding, width: textSize.width, height: textSize.height)
        container.frame = CGRect(x: 0, y: 0, width: w, height: h)
        return container
    }

    /// ⚠ 必须用 `connectedScenes`：`UIApplication.shared.windows` 在 iOS 13+ 已废弃，
    /// 多场景下**启动早期可能为空**（那时弹的提示条会直接不见）。
    private static func keyWindow() -> UIWindow? {
        let windows = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
        return windows.first { $0.isKeyWindow } ?? windows.first
    }
}
