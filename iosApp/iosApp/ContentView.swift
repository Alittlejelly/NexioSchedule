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
    @Environment(\.colorScheme) private var systemColorScheme

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
        let host = IosAppHost()
        // Read the enclosing SwiftUI environment, not a child with an app/page style override.
        // Seed the host before any Compose controller creates its first composition.
        host.updateSystemDarkTheme(isDark: systemColorScheme == .dark)
        return NativeSessionController(host: host)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        (uiViewController as? NativeSessionController)?
            .updateSystemDarkTheme(isDark: systemColorScheme == .dark)
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

    func updateSystemDarkTheme(isDark: Bool) {
        host.updateSystemDarkTheme(isDark: isDark)
    }

    private func applyState() {
        guard isViewLoaded else { return }
        let style: UIUserInterfaceStyle = host.isDark ? .dark : .light
        if overrideUserInterfaceStyle != style {
            overrideUserInterfaceStyle = style
        }
        documentNavigation.applyHostState()
        // Apply the retained root's library label after native navigation updates its chrome.
        rootController.applyState()
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
    private var lastTopInset: CGFloat = -1

    private lazy var mainMoreItem: UIBarButtonItem = {
        let icon = MiuixTabIcons.more.withRenderingMode(.alwaysTemplate)
        let item = UIBarButtonItem(image: icon, style: .plain,
                                   target: nil, action: nil)
        if #available(iOS 26.0, *) {
            item.identifier = "main-header-more"
        }
        item.accessibilityLabel = "更多"
        item.tintColor = .label
        return item
    }()

    private lazy var mainSwitchItem: UIBarButtonItem = {
        let icon = MiuixTabIcons.convertFile.withRenderingMode(.alwaysTemplate)
        let item = UIBarButtonItem(image: icon,
                                   style: .plain,
                                   target: self,
                                   action: #selector(handleSwitchScheduleTap))
        if #available(iOS 26.0, *) {
            item.identifier = "main-header-switch"
        }
        item.accessibilityLabel = "课表切换"
        item.tintColor = .label
        return item
    }()

    private lazy var libraryBackItem = makeLibraryLeadingItem(symbol: "chevron.backward")
    private lazy var libraryCloseItem = makeLibraryLeadingItem(symbol: "xmark")
    private lazy var libraryAddItem = UIBarButtonItem(
        image: nil, style: .plain, target: self, action: #selector(handleLibraryAddTap)
    )
    private lazy var libraryFolderItem = UIBarButtonItem(
        image: nil, style: .plain, target: self, action: #selector(handleLibraryFolderTap)
    )

    private func makeLibraryLeadingItem(symbol: String) -> UIBarButtonItem {
        let item = UIBarButtonItem(image: UIImage(systemName: symbol), style: .plain,
                                  target: self, action: #selector(handleLibraryLeadingTap))
        if #available(iOS 26.0, *) { item.identifier = "schedule-library-leading" }
        return item
    }

    private var libraryTint: UIColor { .label }

    init(host: IosAppHost) {
        self.host = host
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        edgesForExtendedLayout = [.top, .bottom]
        extendedLayoutIncludesOpaqueBars = true
        view.backgroundColor = .clear
        view.clipsToBounds = true
        navigationItem.hidesBackButton = true
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
        // Compose 绘制阶段直推的位移：同步落到 UIKit，与内容同帧（详见 applyPushedChromeOffset）
        host.onCutoutChromeOffset = { [weak self] offsetY in
            self?.applyPushedChromeOffset(offsetY)
        }
        // 原生截屏桥：把含底栏与导航栏的全屏图交给 Compose 补快照
        IosNativeCapture.shared.captureScreen = { [weak self] in
            IosNativeCapture.shared.store(data: self?.captureScreenPNG())
        }
        applyState()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateMainTransform()
        updateCutoutTransform()
        reportTopInset()
        reportBottomInset()
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        reportTopInset()
        reportBottomInset()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        reportTopInset()
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
        if overrideUserInterfaceStyle != chromeStyle {
            overrideUserInterfaceStyle = chromeStyle
        }
        if chromeController.overrideUserInterfaceStyle != chromeStyle {
            chromeController.overrideUserInterfaceStyle = chromeStyle
        }
        let overlayActive = host.overlayActive
        host.overlayController.view.isUserInteractionEnabled = overlayActive
        host.overlayController.view.accessibilityElementsHidden = !overlayActive
        host.overlayController.view.accessibilityViewIsModal = overlayActive &&
            !host.scheduleLibraryActive && !host.scheduleLibraryTransitionActive
        mainContainer.isUserInteractionEnabled = !overlayActive
        mainContainer.accessibilityElementsHidden = overlayActive
        updateMainHeaderControls()
        updateMainTransform()
        updateCutoutTransform()
        reportTopInset()
        reportBottomInset()
    }

    private func updateMainHeaderControls() {
        let animated = viewIfLoaded?.window != nil && !UIAccessibility.isReduceMotionEnabled
        if host.scheduleLibraryActive && !host.nativePageActive {
            updateLibraryHeaderControls(animated: animated)
            return
        }
        if !host.nativePageActive {
            navigationController?.navigationBar.accessibilityLabel = nil
        }
        if navigationItem.leftBarButtonItem != nil {
            navigationItem.setLeftBarButtonItems(nil, animated: animated)
        }
        // 开洞（外观页）与详情页期间都保留 items：外观页这一排跟着主内容一起缩放，
        // 详情页盖满全屏、原生栏本来就被挡住。清空再设回会让 iOS 26 重建整块 platter
        let showHeader = (host.showsNativeHeader || host.keepsNativeHeaderDuringCutout) &&
            !host.nativePageActive
        // 已经在外观页里时「课表外观」项无意义（再点会重跑一次入场）
        let includeCustomize = !host.keepsNativeHeaderDuringCutout
        let targetItems: [UIBarButtonItem]
        if showHeader {
            if host.shiftMode {
                // ⚠ 排班模式只有两页，页序与非排班模式不同：0 = 排班课表，1 = 设置
                if host.selectedTab == 0 {
                    mainMoreItem.menu = scheduleMenu(includeCustomize: includeCustomize)
                    targetItems = [mainMoreItem]
                } else {
                    targetItems = []
                }
            } else {
                switch host.selectedTab {
                case 0:
                    mainMoreItem.menu = todayMenu(includeCustomize: includeCustomize)
                    targetItems = [mainMoreItem]
                case 1:
                    mainMoreItem.menu = scheduleMenu(includeCustomize: includeCustomize)
                    targetItems = [mainMoreItem, mainSwitchItem]
                default:
                    targetItems = []
                }
            }
        } else {
            targetItems = []
        }
        let returningToMain = host.scheduleLibraryTransitionActive && !host.scheduleLibraryActive
        let interactionAllowed = !host.floatingOverlayActive && !host.nativePageActive &&
            (!host.overlayActive || returningToMain)
        mainMoreItem.isEnabled = interactionAllowed
        mainSwitchItem.isEnabled = interactionAllowed
        if (navigationItem.rightBarButtonItems ?? []) != targetItems {
            navigationItem.setRightBarButtonItems(targetItems, animated: animated)
        }
    }

    private func updateLibraryHeaderControls(animated: Bool) {
        navigationController?.navigationBar.accessibilityLabel = host.scheduleLibraryTitle
        let leadingSymbol = host.scheduleLibraryLeadingSymbol()
        if leadingSymbol.isEmpty {
            if navigationItem.leftBarButtonItem != nil {
                navigationItem.setLeftBarButtonItems(nil, animated: animated)
            }
        } else {
            let item = leadingSymbol == "xmark" ? libraryCloseItem : libraryBackItem
            item.image = UIImage(systemName: leadingSymbol)
            item.accessibilityLabel = host.scheduleLibraryLeadingLabel()
            item.isEnabled = host.scheduleLibraryLeadingEnabled()
            item.tintColor = libraryTint
            if navigationItem.leftBarButtonItem !== item {
                navigationItem.setLeftBarButtonItems([item], animated: animated)
            }
        }
        if #available(iOS 26.0, *) {
            libraryAddItem.identifier = "schedule-library-add"
            libraryFolderItem.identifier = "schedule-library-folder"
        }
        var items: [UIBarButtonItem] = []
        let symbol = host.scheduleLibraryActionSymbol()
        if !symbol.isEmpty {
            libraryAddItem.image = UIImage(systemName: host.scheduleLibraryActionBusy() ? "hourglass" : symbol)
            libraryAddItem.accessibilityLabel = host.scheduleLibraryActionLabel()
            libraryAddItem.isEnabled = host.scheduleLibraryActionEnabled()
            libraryAddItem.tintColor = host.scheduleLibraryActionSuccessful() ? .systemGreen : libraryTint
            items.append(libraryAddItem)
        }
        let secondarySymbol = host.scheduleLibrarySecondaryActionSymbol()
        if !secondarySymbol.isEmpty {
            libraryFolderItem.image = UIImage(systemName: host.scheduleLibrarySecondaryActionBusy() ? "hourglass" : secondarySymbol)
            libraryFolderItem.accessibilityLabel = host.scheduleLibrarySecondaryActionLabel()
            libraryFolderItem.isEnabled = host.scheduleLibrarySecondaryActionEnabled()
            libraryFolderItem.tintColor = host.scheduleLibrarySecondaryActionSuccessful() ? .systemGreen : libraryTint
            items.append(libraryFolderItem)
        }
        if (navigationItem.rightBarButtonItems ?? []) != items {
            navigationItem.setRightBarButtonItems(items.isEmpty ? nil : items, animated: animated)
        }
    }

    @objc private func handleLibraryLeadingTap() {
        host.performScheduleLibraryLeadingAction()
    }

    @objc private func handleLibraryAddTap() {
        host.performScheduleLibraryAction()
    }

    @objc private func handleLibraryFolderTap() {
        host.performScheduleLibrarySecondaryAction()
    }

    @objc private func handleSwitchScheduleTap() {
        host.performMainAction(actionId: "switchSchedule")
    }

    /// ⚠ `updateMainHeaderControls` 被 `applyState` 逐帧调用，每次新建 UIMenu 会让
    /// 导航栏 platter 反复更新（观感即「顶栏重载」）。按「排班模式 / 是否带课表外观项」缓存。
    private var scheduleMenuCache: (shift: Bool, customize: Bool, menu: UIMenu)?
    private var todayMenuCache: (shift: Bool, customize: Bool, menu: UIMenu)?

    private func scheduleMenu(includeCustomize: Bool) -> UIMenu {
        if let cached = scheduleMenuCache, cached.shift == host.shiftMode,
           cached.customize == includeCustomize {
            return cached.menu
        }
        let menu = makeScheduleMenu(includeCustomize: includeCustomize)
        scheduleMenuCache = (host.shiftMode, includeCustomize, menu)
        return menu
    }

    private func todayMenu(includeCustomize: Bool) -> UIMenu {
        if let cached = todayMenuCache, cached.shift == host.shiftMode,
           cached.customize == includeCustomize {
            return cached.menu
        }
        let menu = makeTodayMenu(includeCustomize: includeCustomize)
        todayMenuCache = (host.shiftMode, includeCustomize, menu)
        return menu
    }

    private func makeScheduleMenu(includeCustomize: Bool = true) -> UIMenu {
        var actions: [UIAction] = [
            UIAction(title: "跳转周数", image: UIImage(systemName: "calendar.badge.clock")) { [weak self] _ in
                self?.host.performMainAction(actionId: "jumpWeek")
            }
        ]
        if !host.shiftMode {
            actions.append(
                UIAction(title: "课程管理", image: UIImage(systemName: "books.vertical")) { [weak self] _ in
                    self?.host.performMainAction(actionId: "courseManage")
                }
            )
        }
        if includeCustomize {
            actions.append(
                UIAction(title: "课表外观", image: UIImage(systemName: "paintbrush")) { [weak self] _ in
                    self?.host.performMainAction(actionId: "customize")
                }
            )
        }
        return UIMenu(children: actions)
    }

    private func makeTodayMenu(includeCustomize: Bool = true) -> UIMenu {
        var actions: [UIAction] = [
            UIAction(title: "跳转日期", image: UIImage(systemName: "calendar")) { [weak self] _ in
                self?.host.performMainAction(actionId: "jumpToDate")
            },
            UIAction(title: "课程管理", image: UIImage(systemName: "books.vertical")) { [weak self] _ in
                self?.host.performMainAction(actionId: "courseManage")
            }
        ]
        if includeCustomize {
            actions.append(
                UIAction(title: "课表外观", image: UIImage(systemName: "paintbrush")) { [weak self] _ in
                    self?.host.performMainAction(actionId: "customize")
                }
            )
        }
        return UIMenu(children: actions)
    }

    /// 以 `pivot`（整屏坐标）为原点把 `target` 缩放 `scale`，再叠加 `extraTranslationY`
    private func applyCutoutScale(
        _ scale: CGFloat,
        extraTranslationY: CGFloat = 0,
        to target: UIView,
        pivot: CGPoint
    ) {
        let center = target.center
        var transform = CGAffineTransform(scaleX: scale, y: scale)
        transform.tx = (1 - scale) * (pivot.x - center.x)
        transform.ty = (1 - scale) * (pivot.y - center.y) + extraTranslationY
        if target.transform != transform { target.transform = transform }
    }

    /// 开洞（课表外观）时把原生 chrome（底栏容器 + 导航栏）跟着主内容一起移动缩小。
    /// 两者都不在 Compose 的缩放层里，得自己施加同一套变换（scale + 洞心原点）。
    private func updateCutoutTransform() {
        let screenW = view.bounds.width
        let screenH = view.bounds.height
        guard screenW > 0, screenH > 0 else { return }
        let raw = CGFloat(min(1, max(0.65, host.cutoutScale)))
        let scale = raw < 0.999 ? raw : 1
        let pivot = CGPoint(x: screenW * 0.5, y: screenH * CGFloat(host.cutoutCenterYRatio))
        // 外观页里打开底部弹窗时，Compose 主内容整体上移让位；原生 chrome（底栏 + 导航栏）
        let sheetOffsetY = cachedChromeOffsetY
        applyCutoutScale(scale, extraTranslationY: sheetOffsetY, to: chromeContainer, pivot: pivot)
        if let bar = navigationController?.navigationBar {
            applyCutoutScale(
                scale,
                extraTranslationY: sheetOffsetY,
                to: bar,
                pivot: pivot
            )
        }
    }

    /// 外观页弹窗让位时主内容的纵向位移（pt，负值=上移；0 = 当前无位移）
    private var cachedChromeOffsetY: CGFloat = 0
    private func applyPushedChromeOffset(_ offsetY: KotlinDouble) {
        cachedChromeOffsetY = CGFloat(offsetY.doubleValue)
        updateCutoutTransform()
    }

    private func updateMainTransform() {
        let progress = min(1, max(0, host.mainProgress))
        mainContainer.transform = CGAffineTransform(
            translationX: -0.24 * CGFloat(progress) * view.bounds.width,
            y: 0
        )
    }

    private func reportTopInset() {
        guard isViewLoaded, let window = view.window else { return }
        // Keep the retained root's geometry stable while a document covers it.
        // Hiding its controls must not clear the Compose header's reserved space.
        guard !host.nativePageActive else { return }
        // ⚠ 开洞期间也要算进来：inset 归零会让 Compose 顶栏换一套排布（多留 40dp、
        // 顶栏高度不同），主内容一缩放就跟原生栏错开约 40pt
        let showHeader = host.showsNativeHeader || host.scheduleLibraryActive ||
            host.keepsNativeHeaderDuringCutout
        let inset = showHeader ? max(0, view.safeAreaInsets.top - window.safeAreaInsets.top) : 0
        guard abs(lastTopInset - inset) > 0.5 else { return }
        lastTopInset = inset
        host.updateTopBarInset(points: Double(inset))
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

    /// 全屏截图（含底栏与导航栏右上角菜单），PNG 字节。供 Compose 侧补进快照。
    ///
    /// ⚠ 必须从 **window** 渲染：只画 `self.view` 漏导航栏（它是 self.view 的兄弟）
    private func captureScreenPNG() -> Data? {
        guard let container = viewIfLoaded?.window ?? navigationController?.view ?? viewIfLoaded else { return nil }
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = container.window?.screen.scale ?? UIScreen.main.scale
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(size: container.bounds.size, format: format)
        let image = renderer.image { _ in
            container.drawHierarchy(in: container.bounds, afterScreenUpdates: true)
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

/// 原生提示条（对应 Android 的 `Toast`）—— **液态玻璃**渲染
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

    /// ⚠ 必须用 `connectedScenes`
    private static func keyWindow() -> UIWindow? {
        let windows = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
        return windows.first { $0.isKeyWindow } ?? windows.first
    }
}
