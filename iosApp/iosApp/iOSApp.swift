import SwiftUI
import NexioShared
import UserNotifications

@main
struct iOSApp: App {

    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Mirrors Android's `NexioApplication.onCreate()`: injects AppInfo /
        // AppStorage / AppFiles. Must run before any Compose UI appears, otherwise
        // first use throws instead of failing quietly.
        IosAppEntryKt.bootstrapNexioIos()

        // Course reminders. Must come after bootstrap, which is what sets up
        // AppStorage -- the pre-class switch and lead minutes are unreadable before it.
        NativeCourseNotifier.install()
        NativeCourseLiveActivity.install()
        NativeWidgetSnapshot.install()

        // Delegate lives at the app level because routing calls into
        // IosCourseLiveActivity, a singleton inside the main process.
        UNUserNotificationCenter.current().delegate = NotificationDelegate.shared
        IosCourseNotifier.shared.reconcile()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Files shared in from outside. Kotlin's `IosSharedFileReceiver`
                // keeps them in a mutableStateOf, so the main UI recomposes and
                // raises the import confirmation on its own.
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

    /// Foreground delivery still shows a banner: a reminder that stays silent while
    /// the app is open reads as "it never fired".
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
