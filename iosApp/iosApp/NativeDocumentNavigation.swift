import UIKit
import NexioShared

/// UIKit owns navigation chrome for document routes; complex Compose routes remain retained below.
final class NativeDocumentNavigationController: UINavigationController,
    UINavigationControllerDelegate, UIGestureRecognizerDelegate {
    private let host: IosAppHost
    private let retainedRoot: UIViewController
    private var edgeBack: UIScreenEdgePanGestureRecognizer!
    private var interactiveBack: UIPercentDrivenInteractiveTransition?
    private var transitionInFlight = false
    private var transitionOperation: UINavigationController.Operation = .none
    private var settledRouteIds: [String] = []
    private var transitionStartDepth = 0

    /// 栈底那一层在返回菜单里的名字（主界面本身没有原生标题，但菜单要有）。
    private static let rootMenuTitle = "我的"

    init(host: IosAppHost, root: UIViewController) {
        self.host = host
        retainedRoot = root
        super.init(rootViewController: root)
        delegate = self
        root.navigationItem.backButtonDisplayMode = .minimal
        root.navigationItem.hidesBackButton = true
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        navigationBar.prefersLargeTitles = false
        let appearance = UINavigationBarAppearance()
        appearance.configureWithTransparentBackground()
        appearance.shadowColor = .clear
        navigationBar.standardAppearance = appearance
        navigationBar.scrollEdgeAppearance = appearance
        navigationBar.compactAppearance = appearance
        navigationBar.compactScrollEdgeAppearance = appearance
        navigationBar.isTranslucent = true
        // Custom animators need their own interactive driver. Do not race UIKit's drivers.
        interactivePopGestureRecognizer?.isEnabled = false
        if #available(iOS 26.0, *) {
            interactiveContentPopGestureRecognizer?.isEnabled = false
        }
        edgeBack = UIScreenEdgePanGestureRecognizer(target: self, action: #selector(handleEdgeBack(_:)))
        edgeBack.edges = view.effectiveUserInterfaceLayoutDirection == .rightToLeft ? .right : .left
        edgeBack.delegate = self
        view.addGestureRecognizer(edgeBack)
    }

    func applyHostState() {
        guard isViewLoaded else { return }
        overrideUserInterfaceStyle = host.isDark ? .dark : .light
        if interactiveBack == nil {
            edgeBack.edges = view.effectiveUserInterfaceLayoutDirection == .rightToLeft ? .right : .left
        }
        host.reconcileNativeEditorExit()
        updateChromeState()
        // Reconcile programmatic requests only after the current transition settles.
        synchronizeStack()
    }

    private func updateChromeState() {
        let floatingActive = host.floatingOverlayActive
        view.isUserInteractionEnabled = !floatingActive
        view.accessibilityElementsHidden = floatingActive
        retainedRoot.viewIfLoaded?.accessibilityElementsHidden = topViewController !== retainedRoot
        // 平板（rail）布局下栈底这一层的顶栏全在 Compose 里画
        let needsNativeBar = host.nativePageActive || host.showsNativeHeader ||
            host.scheduleLibraryActive || host.keepsNativeHeaderDuringCutout
        let hideNativeBar = host.isTabletLayout && !needsNativeBar
        if navigationBar.isHidden != hideNativeBar {
            setNavigationBarHidden(hideNativeBar, animated: false)
        }
        // A route-local editor changes native items only, never the bar's geometry or alpha; its Compose card morph stays below this control layer.
        navigationBar.isUserInteractionEnabled = !floatingActive && !transitionInFlight
        for controller in viewControllers {
            (controller as? NativeDocumentPageController)?.applyChromeState(
                interactionAllowed: !floatingActive && !transitionInFlight
            )
        }
        if let page = topViewController as? NativeDocumentPageController {
            navigationBar.accessibilityLabel = page.pageTitle
            navigationBar.tintColor = page.chromeTint
        } else {
            navigationBar.accessibilityLabel = nil
            navigationBar.tintColor = nil
        }
        updateBackMenuTitles()
    }

    /// 长按返回菜单由 UIKit 按各控制器标题生成，但导航栏只渲染栈顶那一个，所以只有非栈顶条目才填标题，让菜单有文字、导航栏仍归 Compose。
    /// 两个属性都要写：`navigationItem.title` 为 nil 时导航栏回退到 `title`，而只写 `title` 拿不到菜单文字（UIKit 生成菜单读的是 navigationItem 那一份）。
    private func updateBackMenuTitles() {
        let top = topViewController
        for controller in viewControllers {
            let wanted: String?
            if controller === top {
                // 「顶栏交给系统」的页（如网页导入）标题归导航栏，否则导航栏保持空标题
                wanted = (controller as? NativeDocumentPageController)?.usesSystemHeader == true
                    ? (controller as? NativeDocumentPageController)?.pageTitle
                    : nil
            } else if let page = controller as? NativeDocumentPageController {
                wanted = page.pageTitle
            } else {
                wanted = Self.rootMenuTitle
            }
            if controller.title != wanted { controller.title = wanted }
            if controller.navigationItem.title != wanted { controller.navigationItem.title = wanted }
        }
    }

    private func beginTransition(operation: UINavigationController.Operation, depth: Int) {
        transitionOperation = operation
        transitionStartDepth = depth
        transitionInFlight = true
        host.setNativeTransitionInFlight(inFlight: true)
        updateChromeState()
    }

    private var displayedRouteIds: [String] {
        viewControllers.compactMap { ($0 as? NativeDocumentPageController)?.routeId }
    }

    private func synchronizeStack() {
        guard !transitionInFlight, interactiveBack == nil else { return }
        let wanted = host.nativeRouteIds
        let actual = displayedRouteIds
        guard wanted != actual else {
            // A push may be cancelled before UIKit displays it; with no transition there is no didShow callback, so acknowledge the pending pop here to release its state.
            if host.nativePopPending {
                host.completeNativePop(depth: Int32(actual.count))
            }
            return
        }
        let commonCount = zip(wanted, actual).prefix { $0 == $1 }.count
        let animated = view.window != nil
        if commonCount < actual.count {
            beginTransition(operation: .pop, depth: actual.count)
            let destination = commonCount == 0 ? retainedRoot : viewControllers[commonCount]
            popToViewController(destination, animated: animated)
            // 栈顶已经换了：菜单标题要跟着换，否则退出来的那一页会在导航栏上留一个标题。
            updateChromeState()
        } else if actual.count < wanted.count {
            let id = wanted[actual.count]
            let page = NativeDocumentPageController(routeId: id, host: host)
            beginTransition(operation: .push, depth: actual.count)
            pushViewController(page, animated: animated)
            updateChromeState()
        }
    }

    func navigationController(
        _ navigationController: UINavigationController,
        willShow viewController: UIViewController, animated: Bool
    ) {
        updateChromeState()
    }

    func navigationController(
        _ navigationController: UINavigationController,
        didShow viewController: UIViewController, animated: Bool
    ) {
        let actual = displayedRouteIds
        settledRouteIds = actual
        transitionOperation = .none
        transitionInFlight = false
        interactiveBack = nil
        // UIKit 是唯一权威：每次转场落定都按屏幕上真实存在的页对齐 Kotlin 侧的原生栈。
        // ⚠ 不能只在 pop 时对齐——漏对齐一次，旧 page 的 controller 会被复用而组合不重建，表现即「退出再进、状态还在」。
        host.completeNativePop(depth: Int32(actual.count))
        host.setNativeTransitionInFlight(inFlight: false)
        updateChromeState()
        synchronizeStack()
    }

    func navigationController(
        _ navigationController: UINavigationController,
        animationControllerFor operation: UINavigationController.Operation,
        from fromVC: UIViewController, to toVC: UIViewController
    ) -> UIViewControllerAnimatedTransitioning? {
        beginTransition(operation: operation, depth: settledRouteIds.count)
        return NativeDocumentAnimator(
            push: operation == .push,
            interactive: interactiveBack != nil,
            direction: view.effectiveUserInterfaceLayoutDirection == .rightToLeft ? -1 : 1,
            corners: NativeScreenCornerRadii(
                topLeft: CGFloat(host.screenTopLeftCornerRadiusPoints),
                topRight: CGFloat(host.screenTopRightCornerRadiusPoints),
                bottomRight: CGFloat(host.screenBottomRightCornerRadiusPoints),
                bottomLeft: CGFloat(host.screenBottomLeftCornerRadiusPoints)
            )
        )
    }

    func navigationController(
        _ navigationController: UINavigationController,
        interactionControllerFor animationController: UIViewControllerAnimatedTransitioning
    ) -> UIViewControllerInteractiveTransitioning? {
        interactiveBack
    }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard gestureRecognizer === edgeBack, viewControllers.count > 1,
              !transitionInFlight, !host.floatingOverlayActive, !host.nativeContentOverlayActive,
              presentedViewController == nil else { return false }
        let velocity = edgeBack.velocity(in: view)
        let direction: CGFloat = edgeBack.edges == .right ? -1 : 1
        return velocity.x * direction > 0 && abs(velocity.x) > abs(velocity.y)
    }

    @objc private func handleEdgeBack(_ gesture: UIScreenEdgePanGestureRecognizer) {
        let direction: CGFloat = gesture.edges == .right ? -1 : 1
        let width = max(1, view.bounds.width)
        let progress = min(1, max(0, gesture.translation(in: view).x * direction / width))
        switch gesture.state {
        case .began:
            guard !transitionInFlight, viewControllers.count > 1, !host.floatingOverlayActive,
                  !host.nativeContentOverlayActive else { return }
            let driver = UIPercentDrivenInteractiveTransition()
            driver.completionCurve = .easeOut
            interactiveBack = driver
            beginTransition(operation: .pop, depth: settledRouteIds.count)
            popViewController(animated: true)
        case .changed:
            interactiveBack?.update(progress)
        case .ended:
            let projected = progress + gesture.velocity(in: view).x * direction / width * 0.2
            if projected > 0.5 {
                interactiveBack?.finish()
            } else {
                interactiveBack?.cancel()
            }
        case .cancelled, .failed:
            interactiveBack?.cancel()
        default:
            break
        }
    }
}

private final class NativeDocumentPageController: UIViewController {
    let routeId: String
    private let host: IosAppHost
    private let contentController: UIViewController
    private var appliedActionState: NativeActionState?
    private var appliedBottomBarState: BottomBarState?
    /// 底栏最大宽度：宽屏（iPad / 横向大屏）下居中并限制到此宽度，避免横跨整屏。
    private static let bottomBarMaxWidth: CGFloat = 600
    private lazy var backItem = makeLeadingItem(symbol: "chevron.backward")
    private lazy var cancelItem = makeLeadingItem(symbol: "xmark")

    /// 底部悬浮条的状态快照：只用来判重，避免每次布局都重建系统控件。
    private struct BottomBarState: Equatable {
        let visible: Bool
        let title: String
        let subtitle: String
        let badge: String
        let toggleSymbol: String
        let toggleLabel: String
        let toggleEnabled: Bool
        let primarySymbol: String
        let primaryLabel: String
        let primaryEnabled: Bool
    }

    // 底部悬浮条：页面只发布语义，这里用 UIVisualEffectView(UIGlassEffect()) + 系统按钮渲染，挂在 safeAreaLayoutGuide 上，home indicator 让位由系统安全区处理。
    private lazy var bottomGlass: UIVisualEffectView = {
        let view = UIVisualEffectView(effect: UIGlassEffect())
        // 真胶囊：半径在 viewDidLayoutSubviews 中按实际高度取一半；cornerCurve 用 circular（continuous 在半圆处会显扁）。
        view.layer.cornerCurve = .circular
        view.clipsToBounds = true
        view.translatesAutoresizingMaskIntoConstraints = false
        view.isHidden = true
        return view
    }()

    private lazy var bottomTitleLabel: UILabel = {
        let label = UILabel()
        label.font = .preferredFont(forTextStyle: .headline)
        label.adjustsFontForContentSizeCategory = true
        label.numberOfLines = 1
        label.lineBreakMode = .byTruncatingTail
        // 「桌面版 / 手机版」胶囊要紧跟在名字右侧：名字只占自身宽度，过长时优先截断。
        label.setContentHuggingPriority(.defaultHigh, for: .horizontal)
        label.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return label
    }()

    /// 「桌面版 / 手机版」胶囊容器：圆角小底，颜色与 Compose 底栏一致。
    private lazy var bottomBadgeContainer: UIView = {
        let view = UIView()
        view.translatesAutoresizingMaskIntoConstraints = false
        view.layer.cornerRadius = 4
        view.layer.cornerCurve = .circular
        view.clipsToBounds = true
        return view
    }()

    private lazy var bottomBadgeLabel: UILabel = {
        let label = UILabel()
        label.font = .preferredFont(forTextStyle: .caption1)
        label.adjustsFontForContentSizeCategory = true
        label.numberOfLines = 1
        label.translatesAutoresizingMaskIntoConstraints = false
        label.setContentHuggingPriority(.required, for: .horizontal)
        label.setContentCompressionResistancePriority(.required, for: .horizontal)
        return label
    }()

    private lazy var bottomSubtitleLabel: UILabel = {
        let label = UILabel()
        label.font = .preferredFont(forTextStyle: .footnote)
        label.adjustsFontForContentSizeCategory = true
        label.textColor = .secondaryLabel
        label.numberOfLines = 1
        label.lineBreakMode = .byTruncatingTail
        return label
    }()

    private lazy var bottomToggleButton: UIButton = {
        let button = UIButton(configuration: .gray(), primaryAction: UIAction { [weak self] _ in
            guard let self else { return }
            self.host.performNativePageBottomToggle(routeId: self.routeId)
        })
        button.configuration?.cornerStyle = .capsule
        button.translatesAutoresizingMaskIntoConstraints = false
        button.widthAnchor.constraint(equalToConstant: 44).isActive = true
        button.heightAnchor.constraint(equalToConstant: 44).isActive = true
        return button
    }()

    private lazy var bottomPrimaryButton: UIButton = {
        let button = UIButton(configuration: .gray(), primaryAction: UIAction { [weak self] _ in
            guard let self else { return }
            self.host.performNativePageBottomPrimary(routeId: self.routeId)
        })
        button.configuration?.cornerStyle = .capsule
        button.translatesAutoresizingMaskIntoConstraints = false
        button.widthAnchor.constraint(equalToConstant: 44).isActive = true
        button.heightAnchor.constraint(equalToConstant: 44).isActive = true
        return button
    }()

    private func makeLeadingItem(symbol: String) -> UIBarButtonItem {
        let item = UIBarButtonItem(image: UIImage(systemName: symbol), style: .plain,
                                 target: self, action: #selector(performLeadingAction))
        // UIKit matches these two native configurations and owns their entire transition.
        if #available(iOS 26.0, *) { item.identifier = "time-settings-leading" }
        item.tintColor = .label
        return item
    }
    private lazy var actionItem = UIBarButtonItem(
        image: nil, style: .plain, target: self, action: #selector(performPageAction)
    )
    private lazy var secondaryActionItem = UIBarButtonItem(
        image: nil, style: .plain, target: self, action: #selector(performSecondaryPageAction)
    )
    private lazy var secondaryActivityIndicator = UIActivityIndicatorView(style: .medium)
    private lazy var secondaryActivityItem = makeActivityItem(indicator: secondaryActivityIndicator)
    private lazy var activityIndicator = UIActivityIndicatorView(style: .medium)
    private lazy var activityItem = makeActivityItem(indicator: activityIndicator)

    private func makeActivityItem(indicator: UIActivityIndicatorView) -> UIBarButtonItem {
        let container = UIView()
        container.translatesAutoresizingMaskIntoConstraints = false
        indicator.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(indicator)
        // Let the navigation bar override the preferred height for its current metrics.
        let preferredHeight = container.heightAnchor.constraint(equalToConstant: 44)
        preferredHeight.priority = .defaultLow
        NSLayoutConstraint.activate([
            container.widthAnchor.constraint(equalTo: indicator.widthAnchor),
            preferredHeight,
            indicator.centerXAnchor.constraint(equalTo: container.centerXAnchor),
            indicator.centerYAnchor.constraint(equalTo: container.centerYAnchor),
        ])
        container.isAccessibilityElement = true
        container.accessibilityTraits = [.staticText]
        return UIBarButtonItem(customView: container)
    }

    var pageTitle: String { host.nativeRouteTitle(routeId: routeId) }

    /// 本页是否把顶栏整个交给系统（原生导航栏标题 + 材质），Compose 不再画渐变模糊顶栏。
    var usesSystemHeader: Bool { host.nativePageUsesSystemHeader(routeId: routeId) }
    var chromeTint: UIColor {
        let argb = host.nativePageTintArgb(routeId: routeId)
        // The first composition publishes its tint after the controller is created.
        return ((argb >> 24) & 0xff) == 0 ? .label : UIColor(argb: argb)
    }

    init(routeId: String, host: IosAppHost) {
        self.routeId = routeId
        self.host = host
        contentController = host.makeNativePageController(routeId: routeId)
        super.init(nibName: nil, bundle: nil)
        // Compose owns titles (expanded/collapsed); native chrome owns actions only. Back-menu titles are filled by the navigation controller (see updateBackMenuTitles) since the route title isn't published by Compose yet.
        navigationItem.title = nil
        edgesForExtendedLayout = [.top, .bottom]
        extendedLayoutIncludesOpaqueBars = true
        navigationItem.largeTitleDisplayMode = .never
        navigationItem.backButtonDisplayMode = .minimal
        applyChromeState(interactionAllowed: false)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func applyChromeState(interactionAllowed: Bool) {
        // 底栏要独立于导航栏按钮的判重：导航栏状态没变时早退会让底栏刷不出来
        updateBottomBar()
        let state = NativeActionState(
            leadingSymbol: host.nativePageLeadingSymbol(routeId: routeId),
            leadingLabel: host.nativePageLeadingLabel(routeId: routeId),
            leadingEnabled: interactionAllowed && host.nativePageLeadingEnabled(routeId: routeId),
            editorActive: host.nativePageEditorActive(routeId: routeId),
            symbol: host.nativePageActionSymbol(routeId: routeId),
            label: host.nativePageActionLabel(routeId: routeId),
            enabled: interactionAllowed && host.nativePageActionEnabled(routeId: routeId),
            busy: host.nativePageActionBusy(routeId: routeId),
            successful: host.nativePageActionSuccessful(routeId: routeId),
            secondarySymbol: host.nativePageSecondaryActionSymbol(routeId: routeId),
            secondaryLabel: host.nativePageSecondaryActionLabel(routeId: routeId),
            secondaryEnabled: interactionAllowed && host.nativePageSecondaryActionEnabled(routeId: routeId),
            secondaryBusy: host.nativePageSecondaryActionBusy(routeId: routeId),
            secondarySuccessful: host.nativePageSecondaryActionSuccessful(routeId: routeId),
            tintArgb: host.nativePageTintArgb(routeId: routeId)
        )
        guard appliedActionState != state else { return }
        appliedActionState = state
        let animated = viewIfLoaded?.window != nil && !UIAccessibility.isReduceMotionEnabled
        if !state.leadingSymbol.isEmpty {
            navigationItem.hidesBackButton = true
            let leadingItem = state.leadingSymbol == "xmark" ? cancelItem : backItem
            leadingItem.image = UIImage(systemName: state.leadingSymbol)
            leadingItem.accessibilityLabel = state.leadingLabel
            leadingItem.isEnabled = state.leadingEnabled
            if navigationItem.leftBarButtonItem !== leadingItem {
                navigationItem.setLeftBarButtonItems([leadingItem], animated: animated)
            }
        }
        if #available(iOS 26.0, *) {
            actionItem.identifier = "document-trailing"
            actionItem.style = state.editorActive ? .prominent : .plain
            secondaryActionItem.identifier = "document-secondary-trailing"
        }
        var trailingItems: [UIBarButtonItem] = []
        if state.symbol.isEmpty {
            activityIndicator.stopAnimating()
        } else if state.busy {
            activityIndicator.color = nil
            activityIndicator.startAnimating()
            activityItem.customView?.accessibilityLabel = state.label
            activityItem.isEnabled = false
            trailingItems.append(activityItem)
        } else {
            activityIndicator.stopAnimating()
            actionItem.image = UIImage(systemName: state.symbol)
            actionItem.accessibilityLabel = state.label
            actionItem.isEnabled = state.enabled
            actionItem.tintColor = state.successful ? .systemGreen : .label
            trailingItems.append(actionItem)
        }
        if state.secondarySymbol.isEmpty {
            secondaryActivityIndicator.stopAnimating()
        } else if state.secondaryBusy {
            secondaryActivityIndicator.color = nil
            secondaryActivityIndicator.startAnimating()
            secondaryActivityItem.customView?.accessibilityLabel = state.secondaryLabel
            secondaryActivityItem.isEnabled = false
            trailingItems.append(secondaryActivityItem)
        } else {
            secondaryActivityIndicator.stopAnimating()
            secondaryActionItem.image = UIImage(systemName: state.secondarySymbol)
            secondaryActionItem.accessibilityLabel = state.secondaryLabel
            secondaryActionItem.isEnabled = state.secondaryEnabled
            secondaryActionItem.tintColor = state.secondarySuccessful ? .systemGreen : .label
            trailingItems.append(secondaryActionItem)
        }
        if (navigationItem.rightBarButtonItems ?? []) != trailingItems {
            navigationItem.setRightBarButtonItems(trailingItems.isEmpty ? nil : trailingItems, animated: animated)
        }
    }

    /// 页面发布的底栏语义 → 系统控件状态。位置/材质/安全区由 UIKit 负责。
    private func updateBottomBar() {
        let state = BottomBarState(
            visible: host.nativePageBottomBarVisible(routeId: routeId),
            title: host.nativePageBottomBarTitle(routeId: routeId),
            subtitle: host.nativePageBottomBarSubtitle(routeId: routeId),
            badge: host.nativePageBottomBarBadge(routeId: routeId),
            toggleSymbol: host.nativePageBottomBarToggleSymbol(routeId: routeId),
            toggleLabel: host.nativePageBottomBarToggleLabel(routeId: routeId),
            toggleEnabled: host.nativePageBottomBarToggleEnabled(routeId: routeId),
            primarySymbol: host.nativePageBottomBarPrimarySymbol(routeId: routeId),
            primaryLabel: host.nativePageBottomBarPrimaryLabel(routeId: routeId),
            primaryEnabled: host.nativePageBottomBarPrimaryEnabled(routeId: routeId)
        )
        guard appliedBottomBarState != state else { return }
        appliedBottomBarState = state
        bottomGlass.isHidden = !state.visible
        bottomTitleLabel.text = state.title
        bottomBadgeLabel.text = state.badge
        // 颜色与 Compose 底栏一致：桌面版=主色，手机版=绿色；底色取同色低透明度。
        let isDesktopBadge = state.badge == "桌面版"
        let badgeColor: UIColor = isDesktopBadge
            ? chromeTint
            : UIColor(red: 0x66 / 255.0, green: 0xBB / 255.0, blue: 0x6A / 255.0, alpha: 1)
        bottomBadgeLabel.textColor = badgeColor
        bottomBadgeContainer.backgroundColor = badgeColor.withAlphaComponent(isDesktopBadge ? 0.12 : 0.15)
        bottomBadgeContainer.isHidden = state.badge.isEmpty
        bottomSubtitleLabel.text = state.subtitle
        bottomToggleButton.configuration?.image = UIImage(systemName: state.toggleSymbol)
        bottomToggleButton.accessibilityLabel = state.toggleLabel
        bottomToggleButton.isEnabled = state.toggleEnabled
        bottomToggleButton.tintColor = .label
        bottomPrimaryButton.configuration?.image = UIImage(systemName: state.primarySymbol)
        bottomPrimaryButton.accessibilityLabel = state.primaryLabel
        bottomPrimaryButton.isEnabled = state.primaryEnabled
        bottomPrimaryButton.tintColor = state.primaryEnabled ? chromeTint : .tertiaryLabel
    }

    @objc private func performLeadingAction() {
        guard appliedActionState?.leadingEnabled == true else { return }
        host.performNativePageLeadingAction(routeId: routeId)
    }

    @objc private func performPageAction() {
        guard appliedActionState?.enabled == true, appliedActionState?.busy == false else { return }
        host.performNativePageAction(routeId: routeId)
    }

    @objc private func performSecondaryPageAction() {
        guard appliedActionState?.secondaryEnabled == true, appliedActionState?.secondaryBusy == false else { return }
        host.performNativePageSecondaryAction(routeId: routeId)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        addChild(contentController)
        let content = contentController.view!
        content.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(content)
        NSLayoutConstraint.activate([
            content.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            content.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            content.topAnchor.constraint(equalTo: view.topAnchor),
            content.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        contentController.didMove(toParent: self)
        installBottomBar()
    }

    /// 底部悬浮条：挂在 safeArea 底部之上，内容用系统 Stack / Button 排。
    private func installBottomBar() {
        bottomBadgeContainer.addSubview(bottomBadgeLabel)
        // 末尾加可伸缩占位：让胶囊紧跟名字右侧、多余宽度塞给占位，而不是把名字拉长、把胶囊顶到最右
        let titleSpacer = UIView()
        titleSpacer.setContentHuggingPriority(.defaultLow, for: .horizontal)
        titleSpacer.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        let titleRow = UIStackView(arrangedSubviews: [bottomTitleLabel, bottomBadgeContainer, titleSpacer])
        titleRow.axis = .horizontal
        titleRow.alignment = .center
        titleRow.spacing = 6

        let infoStack = UIStackView(arrangedSubviews: [titleRow, bottomSubtitleLabel])
        infoStack.axis = .vertical
        infoStack.spacing = 3

        let buttonStack = UIStackView(arrangedSubviews: [bottomToggleButton, bottomPrimaryButton])
        buttonStack.axis = .horizontal
        buttonStack.spacing = 8

        let rootStack = UIStackView(arrangedSubviews: [infoStack, buttonStack])
        rootStack.axis = .horizontal
        rootStack.alignment = .center
        rootStack.spacing = 12
        rootStack.translatesAutoresizingMaskIntoConstraints = false

        bottomGlass.contentView.addSubview(rootStack)
        view.addSubview(bottomGlass)
        // 宽度：手机铺满（左右各 16），宽屏（iPad / 横向大屏）限最大宽度并居中，否则横跨整屏；fullWidth 是「铺满」偏好，被 maxWidth 压到上限
        let fullWidth = bottomGlass.widthAnchor.constraint(
            equalTo: view.widthAnchor, constant: -16 * 2
        )
        fullWidth.priority = .defaultHigh
        NSLayoutConstraint.activate([
            bottomGlass.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            bottomGlass.leadingAnchor.constraint(
                greaterThanOrEqualTo: view.leadingAnchor, constant: 16
            ),
            bottomGlass.trailingAnchor.constraint(
                lessThanOrEqualTo: view.trailingAnchor, constant: -16
            ),
            bottomGlass.widthAnchor.constraint(
                lessThanOrEqualToConstant: Self.bottomBarMaxWidth
            ),
            fullWidth,
            // home indicator 的让位由系统安全区给，不写死常数
            bottomGlass.bottomAnchor.constraint(
                equalTo: view.safeAreaLayoutGuide.bottomAnchor
            ),
            rootStack.leadingAnchor.constraint(
                equalTo: bottomGlass.contentView.leadingAnchor, constant: 16
            ),
            rootStack.trailingAnchor.constraint(
                equalTo: bottomGlass.contentView.trailingAnchor, constant: -12
            ),
            rootStack.topAnchor.constraint(
                equalTo: bottomGlass.contentView.topAnchor, constant: 10
            ),
            rootStack.bottomAnchor.constraint(
                equalTo: bottomGlass.contentView.bottomAnchor, constant: -10
            ),
            // 胶囊内边距：水平 4、垂直 2（与 Compose 底栏一致）
            bottomBadgeLabel.leadingAnchor.constraint(
                equalTo: bottomBadgeContainer.leadingAnchor, constant: 4
            ),
            bottomBadgeLabel.trailingAnchor.constraint(
                equalTo: bottomBadgeContainer.trailingAnchor, constant: -4
            ),
            bottomBadgeLabel.topAnchor.constraint(
                equalTo: bottomBadgeContainer.topAnchor, constant: 2
            ),
            bottomBadgeLabel.bottomAnchor.constraint(
                equalTo: bottomBadgeContainer.bottomAnchor, constant: -2
            ),
        ])
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateComposeSafeArea()
        // 胶囊：半径 = 实际高度的一半（内容字号/动态字体变了也跟着变）
        let radius = bottomGlass.bounds.height / 2
        if radius > 0, abs(bottomGlass.layer.cornerRadius - radius) > 0.5 {
            bottomGlass.layer.cornerRadius = radius
        }
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        updateComposeSafeArea()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        updateComposeSafeArea()
    }

    private func updateComposeSafeArea() {
        guard isViewLoaded, let window = view.window else { return }
        // UIKit includes this bar in the child's safe area, but Compose consumes that inset; negative additionalSafeAreaInsets don't reliably remove it.
        let overlap = max(0, view.safeAreaInsets.top - window.safeAreaInsets.top)
        host.updateNativePageBarInset(routeId: routeId, points: Double(overlap))
    }
}

private struct NativeActionState: Equatable {
    let leadingSymbol: String
    let leadingLabel: String
    let leadingEnabled: Bool
    let editorActive: Bool
    let symbol: String
    let label: String
    let enabled: Bool
    let busy: Bool
    let successful: Bool
    let secondarySymbol: String
    let secondaryLabel: String
    let secondaryEnabled: Bool
    let secondaryBusy: Bool
    let secondarySuccessful: Bool
    let tintArgb: Int64
}

private extension UIColor {
    convenience init(argb: Int64) {
        let value = UInt64(bitPattern: argb)
        self.init(
            red: CGFloat((value >> 16) & 0xff) / 255,
            green: CGFloat((value >> 8) & 0xff) / 255,
            blue: CGFloat(value & 0xff) / 255,
            alpha: CGFloat((value >> 24) & 0xff) / 255
        )
    }
}

/// Physical corners stay in screen coordinates, including when navigation runs right to left.
private struct NativeScreenCornerRadii {
    let topLeft: CGFloat
    let topRight: CGFloat
    let bottomRight: CGFloat
    let bottomLeft: CGFloat

    var maxRadius: CGFloat { max(topLeft, topRight, bottomRight, bottomLeft) }
    var isUniform: Bool {
        topLeft == topRight && topLeft == bottomRight && topLeft == bottomLeft
    }

    func path(in bounds: CGRect) -> CGPath {
        // Match ContinuousRoundedRectangle: clamp each radius independently to half
        // the shortest edge. One oversized corner must not shrink the other corners.
        let limit = max(0, min(bounds.width, bounds.height) / 2)
        let tl = min(max(0, topLeft), limit), tr = min(max(0, topRight), limit)
        let br = min(max(0, bottomRight), limit), bl = min(max(0, bottomLeft), limit)
        let left = bounds.minX, right = bounds.maxX, top = bounds.minY, bottom = bounds.maxY
        let path = UIBezierPath()

        // The same rounded-rectangle/capsule profiles as capsule's G2ContinuityProfile.
        // Each corner has a zero-curvature cubic entry, a central arc, and a cubic exit.
        func appendCorner(_ radius: CGFloat, incomingHalfEdge: CGFloat, outgoingHalfEdge: CGFloat,
                          transform: CGAffineTransform, rotation: CGFloat) {
            func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
                CGPoint(x: x, y: y).applying(transform)
            }
            guard radius > 0 else {
                let corner = point(0, 0)
                if path.isEmpty { path.move(to: corner) } else { path.addLine(to: corner) }
                return
            }
            let extensionFraction: CGFloat = 0.5286651
            let curvatureIncrement: CGFloat = 0.0732051
            let incomingRatio = min(1, max(0, (incomingHalfEdge / radius - 1) / extensionFraction))
            let outgoingRatio = min(1, max(0, (outgoingHalfEdge / radius - 1) / extensionFraction))
            let ratio = min(incomingRatio, outgoingRatio)
            let extended = extensionFraction * (0.75 + 0.25 * ratio)
            let arcFraction = (5.0 / 9.0) * ratio
            let arcScale = 1 / (1 + curvatureIncrement * ratio)
            let theta = (.pi / 2 - .pi / 2 * arcFraction) / 2
            let sine = sin(theta), cosine = cos(theta)
            let centerOffset = (1 - arcScale) / sqrt(2)
            let arcStart = CGPoint(x: centerOffset + sine * arcScale,
                                   y: 1 - centerOffset - cosine * arcScale)

            // G2ContinuityProfile.createBaseBezier: controls join the straight edge with
            // zero curvature and meet the central arc with the profile's curvature.
            func bezier(_ edgeRatio: CGFloat) -> [CGPoint] {
                let ext = extended * edgeRatio
                let curvature = 1 + curvatureIncrement * edgeRatio
                let p0 = CGPoint(x: -ext, y: 0)
                if curvature == 1 && arcScale == 1 {
                    let halfTan = sine / (1 + cosine)
                    return [p0, CGPoint(x: (1 - 1.5 / (1 + cosine)) * halfTan, y: 0),
                            CGPoint(x: halfTan, y: 0), CGPoint(x: sine, y: 1 - cosine)]
                }
                let dx = arcStart.x + ext, dy = arcStart.y
                let lambda0 = -(dy * cosine - dx * sine) / sine
                    - 1.5 * curvature * dy * dy / (sine * sine * sine)
                let lambda3 = dy / sine
                return [p0, CGPoint(x: -ext + max(0, lambda0), y: 0),
                        CGPoint(x: arcStart.x - max(0, lambda3 * cosine),
                                y: arcStart.y - max(0, lambda3 * sine)), arcStart]
            }
            let incoming = bezier(incomingRatio), outgoing = bezier(outgoingRatio)
            let start = point(incoming[0].x, incoming[0].y)
            if path.isEmpty { path.move(to: start) } else { path.addLine(to: start) }
            path.addCurve(to: point(incoming[3].x, incoming[3].y),
                          controlPoint1: point(incoming[1].x, incoming[1].y),
                          controlPoint2: point(incoming[2].x, incoming[2].y))
            if arcFraction > 0 {
                path.addArc(withCenter: point(centerOffset, 1 - centerOffset), radius: radius * arcScale,
                            startAngle: -.pi / 2 + theta + rotation,
                            endAngle: -theta + rotation, clockwise: true)
            }
            // Reflect and reverse the exit half of the canonical top-right corner.
            path.addCurve(to: point(1 - outgoing[0].y, 1 - outgoing[0].x),
                          controlPoint1: point(1 - outgoing[2].y, 1 - outgoing[2].x),
                          controlPoint2: point(1 - outgoing[1].y, 1 - outgoing[1].x))
        }
        let halfWidth = bounds.width / 2, halfHeight = bounds.height / 2
        appendCorner(tr, incomingHalfEdge: halfWidth, outgoingHalfEdge: halfHeight,
                     transform: CGAffineTransform(a: tr, b: 0, c: 0, d: tr, tx: right - tr, ty: top), rotation: 0)
        appendCorner(br, incomingHalfEdge: halfHeight, outgoingHalfEdge: halfWidth,
                     transform: CGAffineTransform(a: 0, b: br, c: -br, d: 0, tx: right, ty: bottom - br), rotation: .pi / 2)
        appendCorner(bl, incomingHalfEdge: halfWidth, outgoingHalfEdge: halfHeight,
                     transform: CGAffineTransform(a: -bl, b: 0, c: 0, d: -bl, tx: left + bl, ty: bottom), rotation: .pi)
        appendCorner(tl, incomingHalfEdge: halfHeight, outgoingHalfEdge: halfWidth,
                     transform: CGAffineTransform(a: 0, b: -tl, c: tl, d: 0, tx: left, ty: top + tl), rotation: .pi * 1.5)
        path.close()
        return path.cgPath
    }
}

/// Only content views enter this container; UINavigationBar stays owned by UIKit. New navigation is gated while a transition is in flight, and interactive pop is linear while dragging (normal push/pop sample the existing Kotlin curves).
/// The incoming/outgoing page is clipped to the screen corner radius only while moving, mirroring `PageLayer.clipDuringTransition` on the Compose side.
private final class NativeDocumentAnimator: NSObject, UIViewControllerAnimatedTransitioning {
    private let push: Bool
    private let interactive: Bool
    private let direction: CGFloat
    private let corners: NativeScreenCornerRadii
    private let reduceMotion: Bool

    init(push: Bool, interactive: Bool, direction: CGFloat, corners: NativeScreenCornerRadii) {
        self.push = push
        self.interactive = interactive
        self.direction = direction
        self.corners = corners
        reduceMotion = UIAccessibility.isReduceMotionEnabled
    }

    func transitionDuration(using transitionContext: UIViewControllerContextTransitioning?) -> TimeInterval {
        let duration = NativeDocumentMotion.shared.duration(push: push)
        // Reduced Motion keeps state feedback, but replaces translation with a short crossfade.
        return reduceMotion ? min(duration, 0.2) : duration
    }

    func animateTransition(using context: UIViewControllerContextTransitioning) {
        guard let from = context.view(forKey: .from), let to = context.view(forKey: .to),
              let toController = context.viewController(forKey: .to) else {
            context.completeTransition(false)
            return
        }
        let container = context.containerView
        to.frame = context.finalFrame(for: toController)
        to.layoutIfNeeded()
        let toWasInContainer = to.superview === container
        if push { container.addSubview(to) }
        else { container.insertSubview(to, belowSubview: from) }

        let scrim = UIView(frame: container.bounds)
        scrim.backgroundColor = .black
        scrim.isUserInteractionEnabled = false
        scrim.accessibilityElementsHidden = true
        container.insertSubview(scrim, belowSubview: push ? to : from)
        let width = container.bounds.width * direction
        let originalFromTransform = from.transform
        let originalToTransform = to.transform
        let originalFromAlpha = from.alpha
        let originalToAlpha = to.alpha
        let upper = push ? to : from
        let lower = push ? from : to
        let upperAlpha = push ? originalToAlpha : originalFromAlpha
        let lowerAlpha = push ? originalFromAlpha : originalToAlpha

        // 圆角只在转场期间裁上层（推入/退出中的页），到位后交还系统按屏幕圆角裁，常裁会叠出双圆角。
        let upperLayer = upper.layer
        let originalCornerRadius = upperLayer.cornerRadius
        let originalCornerCurve = upperLayer.cornerCurve
        let originalMasksToBounds = upperLayer.masksToBounds
        let originalMask = upperLayer.mask
        if corners.maxRadius > 0 {
            upperLayer.masksToBounds = true
            if corners.isUniform {
                upperLayer.cornerRadius = corners.topLeft
                upperLayer.cornerCurve = .continuous
            } else {
                let mask = CAShapeLayer()
                mask.frame = upper.bounds
                mask.path = corners.path(in: CGRect(origin: .zero, size: upper.bounds.size))
                upperLayer.cornerRadius = 0
                upperLayer.mask = mask
            }
        }

        func apply(_ timeline: CGFloat) {
            let p = push ? timeline : 1 - timeline
            if reduceMotion {
                upper.alpha = upperAlpha * p
                lower.alpha = lowerAlpha
                scrim.alpha = 0
            } else {
                upper.transform = CGAffineTransform(translationX: (1 - p) * width, y: 0)
                lower.transform = CGAffineTransform(
                    translationX: -CGFloat(NativeDocumentMotion.shared.parallax) * p * width, y: 0
                )
                scrim.alpha = CGFloat(NativeDocumentMotion.shared.backgroundDim) * p
            }
        }
        apply(0)
        let duration = transitionDuration(using: context)
        let samples = interactive || reduceMotion ? 1 : max(1, Int(ceil(duration * 120)))
        // calculationModeLinear alone still inherits an ease-in-out outer timeline; explicit linear timing keeps the sampled curve intact and drag progress exactly 1:1.
        let linearTiming = UIView.KeyframeAnimationOptions(rawValue: UIView.AnimationOptions.curveLinear.rawValue)
        UIView.animateKeyframes(
            withDuration: duration, delay: 0,
            options: [.calculationModeLinear, .overrideInheritedOptions, linearTiming],
            animations: {
                for step in 1...samples {
                    let fraction = Double(step) / Double(samples)
                    let eased = self.interactive || self.reduceMotion ? fraction :
                        NativeDocumentMotion.shared.progress(fraction: fraction, push: self.push)
                    UIView.addKeyframe(
                        withRelativeStartTime: Double(step - 1) / Double(samples),
                        relativeDuration: 1 / Double(samples)
                    ) { apply(CGFloat(eased)) }
                }
            },
            completion: { _ in
                let completed = !context.transitionWasCancelled
                from.transform = originalFromTransform
                to.transform = originalToTransform
                from.alpha = originalFromAlpha
                to.alpha = originalToAlpha
                upperLayer.masksToBounds = originalMasksToBounds
                upperLayer.cornerRadius = originalCornerRadius
                upperLayer.cornerCurve = originalCornerCurve
                upperLayer.mask = originalMask
                scrim.removeFromSuperview()
                if !completed && !toWasInContainer { to.removeFromSuperview() }
                context.completeTransition(completed)
            }
        )
    }
}
