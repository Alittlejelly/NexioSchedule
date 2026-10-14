// xcode: set sdk=iOS

import ActivityKit
import NexioShared
import OSLog
import UIKit
import UserNotifications

private let reminderLog = Logger(
    subsystem: "com.haooz.iosschedule",
    category: "course-reminder"
)

/// Native side of course reminders.
///
/// Kotlin (`IosCourseNotifier`) resolves the schedule and hands over ready-to-fire
/// `Notice`s; this enum registers them with `UNUserNotificationCenter` and owns the
/// Live Activity lifecycle.
///
/// Notification building must live in Swift: the Kotlin/Native ObjC bindings do not
/// export the `UNCalendarNotificationTrigger` factory methods, and `ActivityKit` is a
/// pure-Swift framework that cinterop cannot reach at all. Same injection pattern as
/// `NativeToast`.
enum NativeCourseNotifier {

    static func install() {
        IosCourseNotifier.shared.install(
            write: { write(notices: $0) },
            cancelAll: { cancelAll() },
            requestAuthorization: { requestAuthorization() },
            sendTestNotification: { sendTestNotification() }
        )
    }

    // MARK: - Registration

    private static func write(notices: [IosCourseNotifier.Notice]) {
        let center = UNUserNotificationCenter.current()
        let requests = notices.map { notice -> UNNotificationRequest in
            let content = UNMutableNotificationContent()
            content.title = notice.title
            content.body = notice.body
            // Carries the resolved course so a tap can start the Live Activity
            // without round-tripping back into Kotlin.
            content.userInfo = notice.userInfo
            content.sound = .default
            // repeats = false: each notice is a one-off, not a weekday rule. Holiday
            // and swap-day differences are already resolved on the Kotlin side.
            let trigger = UNCalendarNotificationTrigger(
                dateMatching: components(from: notice.fireEpochMillis),
                repeats: false
            )
            return UNNotificationRequest(
                identifier: notice.id,
                content: content,
                trigger: trigger
            )
        }

        // Cancel-then-write: pending requests read back asynchronously, so a diff is
        // not possible. Cancel is idempotent, mirroring Android's
        // cancelAllAlarms + scheduleAllAlarms ordering.
        cancelAll()
        for request in requests {
            center.add(request) { error in
                if let error {
                    reminderLog.error(
                        "add \(request.identifier) failed: \(error, privacy: .public)"
                    )
                }
            }
        }
        reminderLog.info("scheduled \(requests.count) notices")
    }

    private static func cancelAll() {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { requests in
            let ids = requests
                .map(\.identifier)
                .filter {
                    $0.hasPrefix(IosCourseNotifier.shared.ID_PREFIX_PRE_CLASS) ||
                        $0.hasPrefix(IosCourseNotifier.shared.ID_PREFIX_NEXT_DAY)
                }
            guard !ids.isEmpty else { return }
            center.removePendingNotificationRequests(withIdentifiers: ids)
        }
    }

    // MARK: - Authorization

    private static func requestAuthorization() {
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { settings in
            switch settings.authorizationStatus {
            case .authorized, .provisional, .ephemeral:
                IosCourseNotifier.shared.requestAuthorizationFromSwift(granted: true)

            case .notDetermined:
                center.requestAuthorization(options: [.alert, .sound, .badge]) {
                    granted, _ in
                    // Writes back into Kotlin's cache; reschedule() must run again
                    // for the notices to actually land.
                    IosCourseNotifier.shared.requestAuthorizationFromSwift(granted: granted)
                }

            case .denied:
                // Already denied: the system will not prompt again. Report false so
                // the settings page takes its "open System Settings" branch.
                IosCourseNotifier.shared.requestAuthorizationFromSwift(granted: false)

            @unknown default:
                IosCourseNotifier.shared.requestAuthorizationFromSwift(granted: false)
            }
        }
    }

    // MARK: - Tap routing

    static func handleNotificationResponse(_ response: UNNotificationResponse) {
        route(userInfo: response.notification.request.content.userInfo)
    }

    static func handlePresented(userInfo: [AnyHashable: Any]) {
        route(userInfo: userInfo)
    }

    private static func route(userInfo: [AnyHashable: Any]) {
        var strings: [String: String] = [:]
        for pair in userInfo {
            if let key = pair.key as? String, let value = pair.value as? String {
                strings[key] = value
            }
        }
        guard let action = strings[IosCourseNotifier.shared.KEY_ACTION] else { return }

        switch action {
        case IosCourseNotifier.shared.ACTION_OPEN_COURSE_LIVE:
            // Island switch off -> the host opens the app as usual, no activity.
            guard IosCourseNotifier.shared.isLiveActivityEnabled(),
                let courseId = strings[IosCourseNotifier.shared.KEY_COURSE_ID],
                let courseName = strings[IosCourseNotifier.shared.KEY_COURSE_NAME]
            else { return }

            let startMillis: Int64
            if let epoch = strings[IosCourseNotifier.shared.KEY_START_EPOCH].flatMap(Int64.init) {
                startMillis = epoch
            } else if let epoch = strings[IosCourseNotifier.shared.KEY_START_TIME].flatMap(Int64.init) {
                startMillis = epoch
            } else {
                return
            }

            let endMillis: Int64
            if let epoch = strings[IosCourseNotifier.shared.KEY_END_EPOCH].flatMap(Int64.init) {
                endMillis = epoch
            } else if let epoch = strings[IosCourseNotifier.shared.KEY_END_TIME].flatMap(Int64.init) {
                endMillis = epoch
            } else {
                endMillis = startMillis + 45 * 60 * 1000
            }

            IosCourseLiveActivity.shared.startFromCourse(
                courseId: courseId,
                courseName: courseName,
                classroom: strings[IosCourseNotifier.shared.KEY_CLASSROOM] ?? "",
                section: strings[IosCourseNotifier.shared.KEY_SECTION] ?? "",
                startEpochMillis: startMillis,
                endEpochMillis: endMillis
            )

        default:
            // ACTION_OPEN_APP: the host opens the app on its own.
            break
        }
    }

    // MARK: - Test Notification

    private static func sendTestNotification() {
        let center = UNUserNotificationCenter.current()
        let content = UNMutableNotificationContent()
        content.title = "大学英语Ⅱ · 10分钟后上课"
        content.body = "第3~4节｜博A201"
        content.sound = .default
        let now = Date().timeIntervalSince1970
        let startMillis = Int64((now + 70) * 1000)
        let endMillis = Int64((now + 190) * 1000)
        content.userInfo = [
            IosCourseNotifier.shared.KEY_ACTION: IosCourseNotifier.shared.ACTION_OPEN_COURSE_LIVE,
            IosCourseNotifier.shared.KEY_COURSE_ID: "test_live_course",
            IosCourseNotifier.shared.KEY_COURSE_NAME: "大学英语Ⅱ",
            IosCourseNotifier.shared.KEY_CLASSROOM: "博A201",
            IosCourseNotifier.shared.KEY_SECTION: "第3~4节",
            IosCourseNotifier.shared.KEY_START_TIME: "08:00",
            IosCourseNotifier.shared.KEY_END_TIME: "08:45",
            IosCourseNotifier.shared.KEY_START_EPOCH: "\(startMillis)",
            IosCourseNotifier.shared.KEY_END_EPOCH: "\(endMillis)",
            IosCourseNotifier.shared.KEY_FIRE_EPOCH: "\(Int64(now * 1000))"
        ]
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
        let request = UNNotificationRequest(
            identifier: "nexio.test_notification",
            content: content,
            trigger: trigger
        )
        center.add(request) { error in
            if let error {
                reminderLog.error("test notification failed: \(error, privacy: .public)")
            }
        }
    }

    // MARK: - Helpers

    /// Split via the current calendar rather than handing the raw timestamp to the
    /// trigger, which drifts by an hour across DST and timezone changes.
    private static func components(from epochMillis: Int64) -> DateComponents {
        let date = Date(timeIntervalSince1970: Double(epochMillis) / 1000.0)
        return Calendar.current.dateComponents(
            [.year, .month, .day, .hour, .minute],
            from: date
        )
    }
}

/// Course Live Activity, the iOS counterpart of Android's super island.
///
/// Platform limit: `Activity.request()` requires the app to be visible, so the
/// pre-class phase cannot start an island on its own. It starts either from a
/// notification tap or while the app is in the foreground; the system ends it at
/// `staleDate`.
enum NativeCourseLiveActivity {

    /// Must be retained: a released `Activity` can no longer be updated or ended,
    /// which shows up as "island never dismisses".
    private static var current: Activity<NexioCourseActivityAttributes>?

    /// Timer task to automatically end the activity once countdown hits zero.
    private static var autoDismissWorkItem: DispatchWorkItem?

    /// Background task assertion to allow timer execution even when backgrounded.
    private static var bgTaskIdentifier: UIBackgroundTaskIdentifier = .invalid

    private static func beginBackgroundTask() {
        endBackgroundTask()
        bgTaskIdentifier = UIApplication.shared.beginBackgroundTask(withName: "NexioLiveActivityAutoDismiss") {
            endBackgroundTask()
        }
    }

    private static func endBackgroundTask() {
        if bgTaskIdentifier != .invalid {
            UIApplication.shared.endBackgroundTask(bgTaskIdentifier)
            bgTaskIdentifier = .invalid
        }
    }

    private static func scheduleAutoDismiss(at targetSeconds: Double) {
        autoDismissWorkItem?.cancel()
        beginBackgroundTask()
        let delay = max(0, targetSeconds - Date().timeIntervalSince1970)
        let item = DispatchWorkItem {
            endCurrent()
        }
        autoDismissWorkItem = item
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: item)
    }

    static func install() {
        IosCourseLiveActivity.shared.install(
            startOrUpdate: { courseId, courseName, classroom, section, start, end, leftMode, rightMode, inClassRightMode in
                startOrUpdate(
                    courseId: courseId as String,
                    courseName: courseName as String,
                    classroom: classroom as String,
                    section: section as String,
                    startEpochMillis: start.int64Value,
                    endEpochMillis: end.int64Value,
                    leftMode: Int(leftMode.int32Value),
                    rightMode: Int(rightMode.int32Value),
                    inClassRightMode: Int(inClassRightMode.int32Value)
                )
            },
            end: { endCurrent() }
        )
    }

    private static func startOrUpdate(
        courseId: String,
        courseName: String,
        classroom: String,
        section: String,
        startEpochMillis: Int64,
        endEpochMillis: Int64,
        leftMode: Int = 0,
        rightMode: Int = 2,
        inClassRightMode: Int = 1
    ) {
        // ActivityKit calls must be on the main thread.
        DispatchQueue.main.async {
            let startSeconds = Double(startEpochMillis) / 1000.0
            let endSeconds = Double(endEpochMillis) / 1000.0
            let now = Date().timeIntervalSince1970

            let targetSeconds = (now < startSeconds) ? startSeconds : endSeconds

            // Past the target time: do not start an island that can never finish.
            guard targetSeconds > now else {
                endCurrent()
                return
            }

            let attributes = NexioCourseActivityAttributes(
                courseId: courseId,
                courseName: courseName,
                startEpochSeconds: startSeconds
            )
            let state = NexioCourseActivityAttributes.ContentState(
                endEpochSeconds: endSeconds,
                classroom: classroom,
                section: section,
                isInClass: now >= startSeconds,
                leftMode: leftMode,
                rightMode: rightMode,
                inClassRightMode: inClassRightMode
            )

            scheduleAutoDismiss(at: targetSeconds)

            if let current, current.attributes.courseId == courseId {
                // Same course: update in place. Restarting replays the entry animation.
                Task {
                    await current.update(
                        ActivityContent(
                            state: state,
                            staleDate: Date(timeIntervalSince1970: targetSeconds)
                        )
                    )
                }
                return
            }

            if current != nil {
                Task {
                    await endCurrentAndClear()
                    await startActivity(
                        attributes: attributes,
                        state: state,
                        targetSeconds: targetSeconds
                    )
                }
            } else {
                Task {
                    await startActivity(
                        attributes: attributes,
                        state: state,
                        targetSeconds: targetSeconds
                    )
                }
            }
        }
    }

    private static func startActivity(
        attributes: NexioCourseActivityAttributes,
        state: NexioCourseActivityAttributes.ContentState,
        targetSeconds: Double
    ) async {
        do {
            current = try Activity.request(
                attributes: attributes,
                content: ActivityContent(
                    state: state,
                    staleDate: Date(timeIntervalSince1970: targetSeconds)
                ),
                pushType: nil
            )
        } catch {
            reminderLog.warning("request failed: \(error, privacy: .public)")
        }
    }

    private static func endCurrent() {
        DispatchQueue.main.async { endCurrentAndClearSync() }
    }

    @discardableResult
    private static func endCurrentAndClearSync() -> Activity<NexioCourseActivityAttributes>? {
        autoDismissWorkItem?.cancel()
        autoDismissWorkItem = nil
        endBackgroundTask()
        let activity = current
        current = nil
        Task {
            if let activity {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            for act in Activity<NexioCourseActivityAttributes>.activities {
                await act.end(nil, dismissalPolicy: .immediate)
            }
        }
        return activity
    }

    private static func endCurrentAndClear() async {
        await MainActor.run { endCurrentAndClearSync() }
    }
}
