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
        // A route-local editor changes native items, never the bar's geometry or alpha.
        // Its Compose card morph runs entirely below this persistent control layer.
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
            // A push can be cancelled before UIKit has displayed it. No transition means
            // no didShow callback; acknowledge that pending pop here to release its state.
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
        } else if actual.count < wanted.count {
            let id = wanted[actual.count]
            let page = NativeDocumentPageController(routeId: id, host: host)
            beginTransition(operation: .push, depth: actual.count)
            pushViewController(page, animated: animated)
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
        let completedPop = transitionOperation == .pop && actual.count < transitionStartDepth
        settledRouteIds = actual
        transitionOperation = .none
        transitionInFlight = false
        interactiveBack = nil
        if completedPop {
            // UIKit is authoritative only after successful completion, never at gesture begin.
            host.completeNativePop(depth: Int32(actual.count))
        }
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
            direction: view.effectiveUserInterfaceLayoutDirection == .rightToLeft ? -1 : 1
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
    private lazy var backItem = makeLeadingItem(symbol: "chevron.backward")
    private lazy var cancelItem = makeLeadingItem(symbol: "xmark")

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
    private lazy var activityIndicator = UIActivityIndicatorView(style: .medium)
    private lazy var activityItem: UIBarButtonItem = {
        let container = UIView(frame: CGRect(x: 0, y: 0, width: 44, height: 44))
        activityIndicator.center = CGPoint(x: 22, y: 22)
        container.addSubview(activityIndicator)
        container.isAccessibilityElement = true
        container.accessibilityTraits = [.staticText]
        return UIBarButtonItem(customView: container)
    }()

    var pageTitle: String { host.nativeRouteTitle(routeId: routeId) }
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
        // Compose owns both expanded and collapsed titles. Native chrome owns actions only.
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
            tintArgb: host.nativePageTintArgb(routeId: routeId)
        )
        guard appliedActionState != state else { return }
        appliedActionState = state
        let animated = viewIfLoaded?.window != nil && !UIAccessibility.isReduceMotionEnabled
        if !state.leadingSymbol.isEmpty {
            navigationItem.hidesBackButton = true
            let leadingItem = state.editorActive ? cancelItem : backItem
            leadingItem.accessibilityLabel = state.leadingLabel
            leadingItem.isEnabled = state.leadingEnabled
            if navigationItem.leftBarButtonItem !== leadingItem {
                navigationItem.setLeftBarButtonItems([leadingItem], animated: animated)
            }
        }
        guard !state.symbol.isEmpty else {
            activityIndicator.stopAnimating()
            if navigationItem.rightBarButtonItem != nil {
                navigationItem.setRightBarButtonItems(nil, animated: animated)
            }
            return
        }
        if #available(iOS 26.0, *) {
            actionItem.identifier = "document-trailing"
            actionItem.style = state.editorActive ? .prominent : .plain
        }
        if state.busy {
            activityIndicator.color = chromeTint
            activityIndicator.startAnimating()
            activityItem.customView?.accessibilityLabel = state.label
            activityItem.isEnabled = false
            if navigationItem.rightBarButtonItem !== activityItem {
                navigationItem.setRightBarButtonItems([activityItem], animated: animated)
            }
        } else {
            activityIndicator.stopAnimating()
            actionItem.image = UIImage(systemName: state.symbol)
            actionItem.accessibilityLabel = state.label
            actionItem.isEnabled = state.enabled
            actionItem.tintColor = state.successful ? .systemGreen : chromeTint
            if navigationItem.rightBarButtonItem !== actionItem {
                navigationItem.setRightBarButtonItems([actionItem], animated: animated)
            }
        }
    }

    @objc private func performLeadingAction() {
        guard appliedActionState?.leadingEnabled == true else { return }
        host.performNativePageLeadingAction(routeId: routeId)
    }

    @objc private func performPageAction() {
        guard appliedActionState?.enabled == true, appliedActionState?.busy == false else { return }
        host.performNativePageAction(routeId: routeId)
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
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateComposeSafeArea()
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
        // UIKit includes this bar in the child's safe area. Compose explicitly consumes
        // that extra inset; negative additionalSafeAreaInsets do not reliably remove it.
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

/// Only content views enter this container; UINavigationBar remains owned by UIKit above it.
/// New navigation is gated while a transition is in flight. Interactive pop is linear while
/// dragging; normal push/pop sample the existing Kotlin curves rather than copying them.
private final class NativeDocumentAnimator: NSObject, UIViewControllerAnimatedTransitioning {
    private let push: Bool
    private let interactive: Bool
    private let direction: CGFloat
    private let reduceMotion: Bool

    init(push: Bool, interactive: Bool, direction: CGFloat) {
        self.push = push
        self.interactive = interactive
        self.direction = direction
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
        // Keyframe calculationModeLinear alone still inherits an ease-in-out outer timeline.
        // Explicit linear timing keeps the sampled curve intact and drag progress exactly 1:1.
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
                scrim.removeFromSuperview()
                if !completed && !toWasInContainer { to.removeFromSuperview() }
                context.completeTransition(completed)
            }
        )
    }
}
