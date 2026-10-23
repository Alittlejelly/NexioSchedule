import Foundation
import NexioShared
import OSLog
import WidgetKit

/// Persists resolved display data; the extension never opens the app's database.
enum NativeWidgetSnapshot {
    private static let group = "group.com.haooz.iosschedule"
    private static let log = Logger(subsystem: "com.haooz.iosschedule", category: "widgets")
    private static let queue = DispatchQueue(label: "com.haooz.iosschedule.widget-snapshot")

    static func install() {
        IosWidgetSnapshot.shared.install { json in
            queue.async { write(json) }
        }
    }

    static func refresh() {
        IosWidgetSnapshot.shared.refresh()
    }

    private static func write(_ json: String) {
        guard let directory = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: group
        ) else {
            log.error("App Group container unavailable; check app and extension provisioning")
            return
        }
        do {
            let data = Data(json.utf8)
            let url = directory.appendingPathComponent("widget-snapshot.json")
            // Ignore the generation timestamp; otherwise foreground reconciliation would repeatedly trigger widget refreshes.
            var incoming = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            incoming?.removeValue(forKey: "generatedAt")
            if let previous = try? Data(contentsOf: url),
               var existing = try JSONSerialization.jsonObject(with: previous) as? [String: Any] {
                existing.removeValue(forKey: "generatedAt")
                if NSDictionary(dictionary: existing).isEqual(to: incoming ?? [:]) { return }
            }
            try data.write(to: url, options: .atomic)
            WidgetCenter.shared.reloadTimelines(ofKind: "NexioTodayCourseWidget")
            WidgetCenter.shared.reloadTimelines(ofKind: "NexioLockScreenCourseWidget")
        } catch {
            log.error("Widget snapshot write failed: \(error, privacy: .public)")
        }
    }
}
