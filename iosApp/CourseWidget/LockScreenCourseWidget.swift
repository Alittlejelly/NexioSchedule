import SwiftUI
import WidgetKit

/// A separate gallery entry; desktop widget families remain unchanged.
struct NexioLockScreenCourseWidget: Widget {
    static let kind = "NexioLockScreenCourseWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: DesktopCourseWidgetProvider()) { entry in
            LockScreenCourseView(entry: entry)
        }
        .configurationDisplayName("下一节课")
        .description("在锁屏查看当前或下一节课、教室和时间。")
        .supportedFamilies([.accessoryRectangular])
        .containerBackgroundRemovable(true)
    }
}

struct LockScreenCourseView: View {
    let entry: DesktopWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            if let course = entry.presentation.courses.first {
                courseContent(course)
            } else {
                emptyContent
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        .containerBackground(for: .widget) { Color.clear }
        .widgetURL(URL(string: "nexioschedule://schedule"))
        .accessibilityElement(children: .combine)
    }

    private func courseContent(_ course: DesktopWidgetSnapshot.Course) -> some View {
        let active = !entry.presentation.isTomorrow && course.isActive(at: entry.date)
        let status = entry.presentation.isTomorrow ? "明日首课" : (active ? "上课中" : "下一节")
        return VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 4) {
                Image(systemName: active ? "book.fill" : "book.closed")
                    .accessibilityHidden(true)
                Text(status)
                    .lineLimit(1)
                Spacer(minLength: 2)
                timeRange(course)
            }
            .font(.caption2.weight(.medium))
            .foregroundStyle(.secondary)
            .widgetAccentable()

            Text(course.name)
                .font(.headline)
                .foregroundStyle(.primary)
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity, alignment: .leading)

            Text(course.classroom.isEmpty ? "未指定教室" : course.classroom)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .truncationMode(.middle)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func timeRange(_ course: DesktopWidgetSnapshot.Course) -> some View {
        Text("\(Self.time(course.start))–\(Self.time(course.end))")
            .monospacedDigit()
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
    }

    private var emptyContent: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label("下一节课", systemImage: "book.closed")
                .font(.caption2.weight(.medium))
                .foregroundStyle(.secondary)
                .widgetAccentable()
            Text(entry.presentation.emptyText)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
            if entry.presentation.cacheState != .ready {
                Text("轻点同步课表")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "HH:mm"
        return formatter
    }()

    private static func time(_ epoch: Double) -> String {
        timeFormatter.string(from: Date(timeIntervalSince1970: epoch))
    }
}

struct LockScreenCourseWidget_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            LockScreenCourseView(entry: DesktopWidgetPreview.entry)
                .previewContext(WidgetPreviewContext(family: .accessoryRectangular))
                .previewDisplayName("上课中")
            LockScreenCourseView(entry: .init(
                date: Date(timeIntervalSince1970: DesktopWidgetPreview.snapshot.days[0].courses[0].start - 1800),
                snapshot: DesktopWidgetPreview.snapshot
            ))
                .previewContext(WidgetPreviewContext(family: .accessoryRectangular))
                .previewDisplayName("下一节")
            LockScreenCourseView(entry: .init(date: Date(), snapshot: nil))
                .previewContext(WidgetPreviewContext(family: .accessoryRectangular))
                .previewDisplayName("首次同步")
        }
    }
}
