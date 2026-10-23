import SwiftUI
import NexioShared
import UserNotifications

@main
struct iOSApp: App {

    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Mirrors Android's `NexioApplication.onCreate()`: injects AppInfo/AppStorage/AppFiles. Must run before any Compose UI appears, or the first use throws.
        IosAppEntryKt.bootstrapNexioIos()

        // Course reminders; must follow bootstrap, which sets up AppStorage (pre-class switch and lead minutes are unreadable before it).
        NativeCourseNotifier.install()
        NativeCourseLiveActivity.install()
        NativeWidgetSnapshot.install()

        // Delegate is app-level because routing calls into `IosCourseLiveActivity`, a singleton in the main process.
        UNUserNotificationCenter.current().delegate = NotificationDelegate.shared
        IosCourseNotifier.shared.reconcile()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Files shared in from outside are held in a `mutableStateOf` by `IosSharedFileReceiver`, so the main UI recomposes and raises import confirmation automatically.
                .onOpenURL { url in
                    if url.scheme == "nexioschedule", url.host == "schedule" {
                        NativeWidgetSnapshot.refresh()
                    } else {
                        IosSharedFileReceiver.shared.onOpenURL(url: url.absoluteString)
                    }
                }
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active {
                        IosCourseNotifier.shared.reconcile()
                    }
                }
        }
    }
}

final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {

    static let shared = NotificationDelegate()

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        NativeCourseNotifier.handleNotificationResponse(response)
    }

    /// Foreground delivery still shows a banner; a silent reminder while the app is open reads as "it never fired".
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        NativeCourseNotifier.handlePresented(
            userInfo: notification.request.content.userInfo
        )
        return [.banner, .list, .sound]
    }
}
