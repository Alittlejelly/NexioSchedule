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
        // 状态栏样式交给系统自适应。⚠ 自接管两条路都不通：`UIApplication.setStatusBarStyle` 自 iOS 27 起是 no-op；wrapper 覆写 `preferredStatusBarStyle` 会崩（UIKit 禁止把 root VC 加为 child，`UIHostingController` 正是 root）。`UIViewControllerBasedStatusBarAppearance=false` 已删，该键会让系统读废弃全局 API（刷屏警告来源）。

        // 原生 toast（液态玻璃）渲染放 Swift 侧（见 NativeToast），Kotlin 只留注入点。
        // ⚠ `longDuration` 是 `KotlinBoolean` 而非 `Bool`（Kotlin/Native 导出函数类型时会把基本类型 boxed），必须 `.boolValue` 拆箱。
        IosToast.shared.nativeShow = { message, longDuration in
            NativeToast.show(message, longDuration: longDuration.boolValue)
        }
        let host = IosAppHost()
        // Read the enclosing SwiftUI environment, not a child with an app/page style override; seed the host before any Compose controller creates its first composition.
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
    /// 全屏背景模糊层：详情页之下、其余一切之上。iOS 无模糊半径可设，只能让材质从 nil 渐变到满、强度逐帧写 `fractionComplete`。
    /// ⚠ 起始 effect 必须为 nil，animator 的动画才是「无 → 有」。
    private let detailBackdropBlur: UIVisualEffectView = {
        let view = UIVisualEffectView(effect: nil)
        view.isUserInteractionEnabled = false
        view.isHidden = true
        return view
    }()
    /// 详情页容器：唯一盖在 `detailBackdropBlur` 之上的 Compose 容器
    private let detailContainer = FloatingOverlayPassthroughView()
    /// Compose 推来的目标强度（0…1）
    private var detailBlurTarget: CGFloat = 0
    /// 已写入 animator 的强度（-1 = 还没写过）
    private var detailBlurApplied: CGFloat = -1
    /// 强度插值器；须在视图进 window 后创建
    private var detailBlurAnimator: UIViewPropertyAnimator?

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
        // 层级（自下而上）：原生导航栈 → 弹窗层 → 本层全屏背景模糊 → 详情页。
        // 底栏/顶栏是 UIKit 原生视图、弹窗层在另一 Compose 根里，都不在主内容模糊层内；与其按各自形状凑效果层，不如整块压住「除详情页外的一切」。
        pin(detailBackdropBlur, to: view)
        detailContainer.backgroundColor = .clear
        pin(detailContainer, to: view)
        embed(host.abovePopupsController, in: detailContainer)
        // Compose 绘制阶段直推模糊强度（0…1），与主内容同帧
        host.onDetailBackdropBlurProgress = { [weak self] progress in
            self?.applyDetailBlurProgress(progress)
        }
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
        // 详情页容器单独一层：只有显示时才接触摸，否则整层穿透
        let detailActive = host.detailOverlayActive
        detailContainer.overlayActive = detailActive
        detailContainer.accessibilityElementsHidden = !detailActive
        host.abovePopupsController.view.isUserInteractionEnabled = detailActive
        host.abovePopupsController.view.accessibilityElementsHidden = !detailActive
        host.abovePopupsController.view.accessibilityViewIsModal = detailActive
        refreshDetailBlur()
    }

    /// Compose 每帧推来的强度（0…1，dp→强度已在 Kotlin 侧归一化）；0 = 结束、隐藏本层。
    /// 只驱动页面级模糊，长按菜单在弹窗层内，不走这里。
    private func applyDetailBlurProgress(_ progress: KotlinDouble) {
        detailBlurTarget = CGFloat(progress.doubleValue)
        refreshDetailBlur()
    }

    /// 压着上层原生文档页时整块不能糊：那一页不属于背景
    private func refreshDetailBlur() {
        guard isViewLoaded else { return }
        let target = host.nativePageActive ? 0 : max(0, min(1, detailBlurTarget))
        guard let animator = detailBlurAnimatorIfReady() else { return }
        guard abs(target - detailBlurApplied) > 0.001 else { return }
        detailBlurApplied = target
        // 0/1 端点会让 animator 判为「已结束」，之后写进度失效 → 夹住，端点交给 isHidden
        animator.fractionComplete = min(max(target, 0.001), 0.999)
        detailBackdropBlur.isHidden = target <= 0.001
    }

    /// animator 闭包会改 `effect`，须等视图进 window 再建；新建时是 inactive，要先 `startAnimation()` 再 `pauseAnimation()` 把强度停在 0。
    /// `curve` 不参与输出（只赋 `fractionComplete`），别再套缓动以免双重缓动。
    private func detailBlurAnimatorIfReady() -> UIViewPropertyAnimator? {
        if let animator = detailBlurAnimator { return animator }
        guard view.window != nil else { return nil }
        let animator = UIViewPropertyAnimator(duration: 1, curve: .linear) { [weak self] in
            self?.detailBackdropBlur.effect = UIBlurEffect(style: .systemUltraThinMaterial)
        }
        animator.pausesOnCompletion = true
        animator.startAnimation()
        animator.pauseAnimation()
        detailBlurAnimator = animator
        return animator
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
        // The long-press back menu's last row is this retained root; same as NativeDocumentPageController: Compose draws the visible header, UIKit only needs a parent-facing label and must not render a bar one.
        title = "主界面"
        navigationItem.title = nil
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
        chromeContainer.chromeController = chromeController
        embed(host.overlayController, in: view)

        chromeController.onUserSelection = { [weak self] index in
            self?.host.selectTab(index: Int32(index))
        }
        chromeController.onLayout = { [weak self] in
            self?.reportBottomInset()
        }
        chromeController.onTransitionDirectionChange = { [weak self] vertical in
            self?.host.updateTabTransitionDirection(vertical: vertical)
        }
        // 折叠屏折叠态：tab bar 竖排、状态栏跑到右侧、顶部没有状态栏；顶栏糊带与星期行据此换几何
        chromeController.onVerticalBarEdgeChange = { [weak self] vertical in
            self?.host.updateVerticalBarEdge(active: vertical)
        }
        chromeController.updateTransitionDirection()
        // Compose 绘制阶段直推的位移：同步落到 UIKit，与内容同帧（详见 applyPushedChromeOffset）
        host.onCutoutChromeOffset = { [weak self] offsetY in
            self?.applyPushedChromeOffset(offsetY)
        }
        // 原生截屏桥：把含底栏与导航栏的全屏图交给 Compose 补快照
        IosNativeCapture.shared.captureScreen = { [weak self] in
            guard let cap = self?.captureScreenPixels() else {
                IosNativeCapture.shared.storePixels(data: nil, width: 0, height: 0)
                return
            }
            IosNativeCapture.shared.storePixels(
                data: cap.data,
                width: Int32(cap.width),
                height: Int32(cap.height)
            )
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
        // 开洞（外观页）与详情页期间都保留 items：外观页这一排跟着主内容缩放，详情页盖满全屏原生栏本就被挡；清空再设回会让 iOS 26 重建整块 platter。
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

    /// ⚠ `updateMainHeaderControls` 被 `applyState` 逐帧调用，每次新建 UIMenu 会让导航栏 platter 反复更新（观感即「顶栏重载」），按「排班模式 / 是否带课表外观项」缓存。
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

    /// 开洞（课表外观）时把原生 chrome（tab bar + 导航栏）跟着主内容一起移动缩小；两者都不在 Compose 缩放层里，得自己施加同一套变换（scale + 洞心原点）。
    /// ⚠ 不能直接缩 `chromeContainer`：带非单位 transform 后 UIKit 会重新解析 `verticalBarEdge`，折叠屏 iPhone 的侧边竖排 tab bar 会从 (397,0,69,678) 掉回底部 (0,595,466,83)；只能逐个缩真正的 chrome 视图。
    private func updateCutoutTransform() {
        let screenW = view.bounds.width
        let screenH = view.bounds.height
        guard screenW > 0, screenH > 0 else { return }
        let raw = CGFloat(min(1, max(0.65, host.cutoutScale)))
        let scale = raw < 0.999 ? raw : 1
        let pivot = CGPoint(
            x: screenW * CGFloat(host.cutoutCenterXRatio),
            y: screenH * CGFloat(host.cutoutCenterYRatio)
        )
        // 外观页里打开底部弹窗时，Compose 主内容整体上移让位；原生 chrome（底栏 + 导航栏）
        let sheetOffsetY = cachedChromeOffsetY
        // 竖排 tab bar 本体：子树里若有 SwiftUI 托管的 `FloatingBarHostingView`（其他 iOS 版本/布局下图标在那里）优先缩它，否则缩 `UITabBar`
        let barHost = findView(chromeController.view, className: "FloatingBarHostingView")
            ?? chromeController.tabBar
        applyCutoutScale(scale, extraTranslationY: sheetOffsetY, to: barHost, pivot: pivot)
        if let bar = navigationController?.navigationBar {
            applyCutoutScale(
                scale,
                extraTranslationY: sheetOffsetY,
                to: bar,
                pivot: pivot
            )
        }
        // 折叠屏竖排模式：顶栏的「更多 / 切换课表」不在 `UINavigationBar`（高度 0），而被 UIKit 挪进导航控制器的 `_UIFloatingBarContainerView` → `FloatingBarHostingView`（与 chromeController.view 是兄弟）。
        // ⚠ 这一层缩不动：祖先带缩放时它按 1/scale 反向撑大自身 bounds（实测 scale 0.75 → bounds 621×904），视觉尺寸恒不变、只会错位。各种 transform/anchorPoint 方案都试过，开洞期间直接淡出（此页头部由 Compose 的「取消/应用」承担）。
        if let navView = navigationController?.view,
           let floatingBar = findView(navView, className: "_UIFloatingBarContainerView"),
           let host = findView(floatingBar, className: "FloatingBarHostingView") {
            let fade = max(0, min(1, (scale - 0.72) / 0.28))
            if abs(host.alpha - fade) > 0.01 { host.alpha = fade }
        }
    }

    /// 外观页弹窗让位时主内容的纵向位移（pt，负值=上移；0 = 当前无位移）
    private var cachedChromeOffsetY: CGFloat = 0

    private func findView(_ root: UIView, className: String, _ depth: Int = 0) -> UIView? {
        if depth > 6 { return nil }
        if String(describing: type(of: root)).hasPrefix(className) { return root }
        for sub in root.subviews {
            if let hit = findView(sub, className: className, depth + 1) { return hit }
        }
        return nil
    }

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
        // Keep the retained root's geometry stable while a document covers it; hiding its controls must not clear the Compose header's reserved space.
        guard !host.nativePageActive else { return }
        // ⚠ 开洞期间也要算进来：inset 归零会让 Compose 顶栏换排布（多留 40dp、高度不同），主内容一缩放就跟原生栏错开约 40pt。
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
        let contentInsets = chromeController.selectedViewController?.view.safeAreaInsets ?? .zero
        host.updateHorizontalSafeArea(
            left: Double(max(view.safeAreaInsets.left, contentInsets.left)),
            right: Double(max(view.safeAreaInsets.right, contentInsets.right))
        )
        let inset: CGFloat
        if chromeContainer.isHidden || bar.isHidden || bar.bounds.isEmpty {
            inset = 0
        } else {
            // UIKit reserves space on the appropriate edge when tabs move to a sidebar.
            inset = contentInsets.bottom
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

    /// 全屏截图（含底栏与导航栏右上角菜单）的原始像素，供 Compose 补进快照。
    /// ⚠ 必须从 **window** 渲染：只画 `self.view` 会漏掉导航栏（兄弟节点）。像素 BGRA8888、rowBytes = width×4、alpha 预乘。
    /// 刻意不做 `pngData()`（同进程内存直传）：实测 215ms + Kotlin 解码 55ms vs 裸像素 ~30ms。字节序须与 `IosNativeCapture.storePixels` 严格对应，改此处必同步 Kotlin 侧。
    private func captureScreenPixels() -> (data: Data, width: Int, height: Int)? {
        guard let container = viewIfLoaded?.window ?? navigationController?.view ?? viewIfLoaded else { return nil }
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = container.window?.screen.scale ?? UIScreen.main.scale
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(size: container.bounds.size, format: format)
        let image = renderer.image { _ in
            container.drawHierarchy(in: container.bounds, afterScreenUpdates: true)
        }
        guard let cgImage = image.cgImage else { return nil }
        let width = cgImage.width
        let height = cgImage.height
        guard width > 0, height > 0 else { return nil }
        let bytesPerRow = width * 4
        guard let ctx = CGContext(
            data: nil,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: bytesPerRow,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue
                | CGBitmapInfo.byteOrder32Little.rawValue
        ) else { return nil }
        ctx.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        guard let base = ctx.data else { return nil }
        return (Data(bytes: base, count: bytesPerRow * height), width, height)
    }
}

/// Empty native tab content must not intercept the Compose content below it.
private final class TabBarPassthroughView: UIView {
    weak var chromeController: UITabBarController?

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard !isHidden, alpha > 0.01, isUserInteractionEnabled,
              let controller = chromeController,
              let hit = super.hitTest(point, with: event),
              hit !== self, hit !== controller.view else { return nil }
        // UIKit may host vertical tab controls outside the public UITabBar view; hit-test the real hierarchy but let empty tab content pass through.
        if let content = controller.selectedViewController?.view,
           hit === content || hit.isDescendant(of: content) {
            return nil
        }
        return hit
    }
}

/// UIKit owns the tab bar appearance, including automatic OS-version adaptation.
private final class NativeChromeController: UITabBarController, UITabBarControllerDelegate {
    var onUserSelection: ((Int) -> Void)?
    var onLayout: (() -> Void)?
    var onTransitionDirectionChange: ((Bool) -> Void)?
    /// tab bar 是否竖排到屏幕一侧 = 折叠屏 iPhone 折叠态（状态栏跑到右侧、顶部没有状态栏）
    var onVerticalBarEdgeChange: ((Bool) -> Void)?
    private var configuredShiftMode: Bool?
    private var configuredTabCount = 0

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        view.isOpaque = false
        delegate = self
        if #available(iOS 27.1, *) {
            registerForTraitChanges(UITraitCollection.systemTraitsAffectingVerticalBarEdge) {
                (controller: NativeChromeController, _: UITraitCollection) in
                controller.updateTransitionDirection()
            }
        }
    }

    /// `verticalBarEdge`（leading/trailing）= 折叠屏 iPhone 折叠态：tab bar 竖排到侧边、状态栏移到右侧、顶部无状态栏。形态只认这个 trait（高度算 0 分不出「顶部没有」与「被导航栏吃光」）。
    func updateTransitionDirection() {
        let vertical: Bool
        if #available(iOS 27.1, *) {
            switch traitCollection.verticalBarEdge {
            case .leading, .trailing: vertical = true
            case .unspecified: vertical = false
            @unknown default: vertical = false
            }
        } else {
            vertical = false
        }
        onTransitionDirectionChange?(vertical)
        // 排除 iPad：它把 tab bar 挪到侧边时同样报 verticalBarEdge，但顶部一直有状态栏
        onVerticalBarEdgeChange?(vertical && traitCollection.userInterfaceIdiom == .phone)
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
        updateTransitionDirection()
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
