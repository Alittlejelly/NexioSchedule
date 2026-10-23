import Foundation
import SwiftUI
import WidgetKit

// The app writes this versioned JSON atomically into the App Group container; these models are self-contained so the widget never opens the app's database.
struct DesktopWidgetSnapshot: Codable {
    var schemaVersion: Int = 1
    var generatedAt: Double
    var validUntil: Double
    var tomorrowEnabled: Bool
    var tomorrowMinute: Int
    var days: [Day]

    struct Day: Codable {
        var date: String
        var start: Double
        var end: Double
        var week: Int
        var status: String
        var courses: [Course]
    }

    struct Course: Codable, Identifiable {
        var id: String
        var name: String
        var classroom: String
        var teacher: String
        var section: String
        var start: Double
        var end: Double
        var color: String

        func isActive(at date: Date) -> Bool {
            start <= date.timeIntervalSince1970 && date.timeIntervalSince1970 < end
        }

        var information: String {
            [section, classroom, teacher].filter { !$0.isEmpty }.joined(separator: "｜")
        }
    }
}

enum DesktopWidgetSnapshotStore {
    static let appGroupIdentifier = "group.com.haooz.iosschedule"
    static let fileName = "widget-snapshot.json"

    static var snapshotURL: URL? {
        FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: appGroupIdentifier
        )?.appendingPathComponent(fileName)
    }

    static func load() -> DesktopWidgetSnapshot? {
        guard let url = snapshotURL,
              let data = try? Data(contentsOf: url),
              let snapshot = try? JSONDecoder().decode(DesktopWidgetSnapshot.self, from: data),
              snapshot.schemaVersion == 1,
              snapshot.validUntil > snapshot.generatedAt,
              snapshot.days.allSatisfy({ day in
                  day.end > day.start && day.courses.allSatisfy { $0.end > $0.start }
              }) else { return nil }
        return snapshot
    }
}

struct DesktopWidgetPresentation {
    enum CacheState { case ready, unavailable, expired }

    var cacheState: CacheState
    var day: DesktopWidgetSnapshot.Day?
    var isTomorrow = false
    var courses: [DesktopWidgetSnapshot.Course] = []
    var emptyText: String

    init(snapshot: DesktopWidgetSnapshot?, at date: Date, calendar: Calendar = .current) {
        guard let snapshot else {
            cacheState = .unavailable
            emptyText = "打开应用同步课表"
            return
        }
        let now = date.timeIntervalSince1970
        guard snapshot.schemaVersion == 1, now < snapshot.validUntil,
              let today = snapshot.days.first(where: { $0.start <= now && now < $0.end }) else {
            cacheState = .expired
            emptyText = "打开应用更新课表"
            return
        }

        // Include yesterday's still-running class after midnight; its timestamp identifies this occurrence even when a recurring course reuses its ID.
        let carryover = snapshot.days.filter { $0.start < today.start }.flatMap(\.courses)
            .filter { $0.start < today.start && $0.end > now }
        let todayCourses = (carryover + today.courses).sorted {
            $0.start == $1.start ? $0.id < $1.id : $0.start < $1.start
        }
        let switchTime = DesktopWidgetTimeline.tomorrowSwitch(for: today, snapshot: snapshot, calendar: calendar)
        let finished = todayCourses.allSatisfy { $0.end <= now }
        isTomorrow = snapshot.tomorrowEnabled && now >= switchTime && finished
        let target: DesktopWidgetSnapshot.Day
        if isTomorrow {
            guard let tomorrow = snapshot.days.first(where: { abs($0.start - today.end) < 1 }) else {
                cacheState = .expired
                emptyText = "打开应用更新课表"
                return
            }
            target = tomorrow
            courses = tomorrow.courses.sorted { $0.start < $1.start }
        } else {
            target = today
            courses = todayCourses.filter { $0.end > now }
        }
        cacheState = .ready
        day = target
        if !courses.isEmpty {
            emptyText = ""
        } else if target.status == "notStarted" {
            emptyText = "学期暂未开始"
        } else if target.status == "holiday" {
            emptyText = "假期中，暂无课程"
        } else if isTomorrow {
            emptyText = "明日无课"
        } else if todayCourses.isEmpty {
            emptyText = "今日无课"
        } else {
            emptyText = "今日课程已上完"
        }
    }

    var weekday: String {
        guard let day else { return "课程表" }
        // Use the exported calendar date, not a swap day's teaching weekday or a timestamp interpreted in a timezone different from the writer's.
        let parts = day.date.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return "课程表" }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        guard let date = calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2])) else {
            return "课程表"
        }
        return ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][calendar.component(.weekday, from: date) - 1]
    }

    var weekText: String {
        guard let day else { return "" }
        if day.status == "notStarted" { return "未开始" }
        if day.status == "holiday" { return "放假中" }
        return "第\(day.week)周"
    }
}

struct DesktopWidgetEntry: TimelineEntry {
    let date: Date
    let presentation: DesktopWidgetPresentation

    init(date: Date, snapshot: DesktopWidgetSnapshot?) {
        self.date = date
        presentation = DesktopWidgetPresentation(snapshot: snapshot, at: date)
    }
}

enum DesktopWidgetTimeline {
    static let maximumEntries = 96

    static func tomorrowSwitch(
        for day: DesktopWidgetSnapshot.Day,
        snapshot: DesktopWidgetSnapshot,
        calendar: Calendar = .current
    ) -> Double {
        let minute = min(max(snapshot.tomorrowMinute, 0), 1439)
        let midnight = Date(timeIntervalSince1970: day.start)
        // Calendar arithmetic preserves the configured wall-clock time on DST days.
        let configured = calendar.date(
            bySettingHour: minute / 60, minute: minute % 60, second: 0, of: midnight
        )?.timeIntervalSince1970 ?? day.start + Double(minute * 60)
        return max(configured, day.courses.map(\.end).max() ?? day.start)
    }

    static func make(snapshot: DesktopWidgetSnapshot?, now: Date) -> Timeline<DesktopWidgetEntry> {
        let timestamp = now.timeIntervalSince1970
        guard let snapshot, snapshot.schemaVersion == 1, snapshot.validUntil > timestamp else {
            return Timeline(entries: [DesktopWidgetEntry(date: now, snapshot: snapshot)],
                            policy: .after(now.addingTimeInterval(3600)))
        }
        let threeDays = Calendar.current.date(byAdding: .day, value: 3, to: now) ?? now.addingTimeInterval(259200)
        let horizon = min(threeDays.timeIntervalSince1970, snapshot.validUntil)
        var transitions: Set<Double> = [timestamp, horizon]
        func add(_ value: Double) {
            if value > timestamp && value <= horizon { transitions.insert(value) }
        }
        for day in snapshot.days where day.end > timestamp && day.start <= horizon {
            add(day.start)
            add(day.end)
            if snapshot.tomorrowEnabled { add(tomorrowSwitch(for: day, snapshot: snapshot)) }
            for course in day.courses {
                add(course.start)
                add(course.end)
            }
        }
        // A course from the preceding day may still be active at the first entry.
        for course in snapshot.days.filter({ $0.end <= timestamp }).flatMap(\.courses) where course.end > timestamp {
            add(course.end)
        }
        let dates = transitions.sorted().prefix(maximumEntries).map { Date(timeIntervalSince1970: $0) }
        let entries = dates.map { DesktopWidgetEntry(date: $0, snapshot: snapshot) }
        // Re-read the latest shared snapshot at the end of this bounded timeline; if the 96-entry limit is hit, renewal starts at its last covered event.
        return Timeline(entries: entries, policy: .after(dates.last ?? now.addingTimeInterval(3600)))
    }
}

struct DesktopCourseWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> DesktopWidgetEntry { DesktopWidgetPreview.entry }

    func getSnapshot(in context: Context, completion: @escaping (DesktopWidgetEntry) -> Void) {
        if context.isPreview {
            completion(DesktopWidgetPreview.entry)
        } else {
            completion(DesktopWidgetEntry(date: Date(), snapshot: DesktopWidgetSnapshotStore.load()))
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<DesktopWidgetEntry>) -> Void) {
        completion(DesktopWidgetTimeline.make(snapshot: DesktopWidgetSnapshotStore.load(), now: Date()))
    }
}

struct NexioTodayCourseWidget: Widget {
    static let kind = "NexioTodayCourseWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: DesktopCourseWidgetProvider()) { entry in
            DesktopCourseListView(entry: entry)
        }
        .configurationDisplayName("课程表")
        .description("调整大小，查看当前课程或接下来的课程列表。")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .contentMarginsDisabled()
        .containerBackgroundRemovable(true)
    }
}

// MARK: - Android-aligned visual treatment

private struct DesktopWidgetPalette {
    let colorScheme: ColorScheme
    let renderingMode: WidgetRenderingMode
    let showsBackground: Bool

    var isMonochrome: Bool { renderingMode != .fullColor || !showsBackground }
    var primary: Color { isMonochrome ? .primary : Color(hex: colorScheme == .dark ? "EEEEEE" : "1A1A1A") }
    var secondary: Color { isMonochrome ? Color.primary.opacity(0.75) : primary.opacity(0.55) }
    var header: Color { isMonochrome ? .primary : primary.opacity(0.60) }
    var active: Color { isMonochrome ? .primary : Color(hex: colorScheme == .dark ? "42A5F5" : "2196F3") }
    var listBackground: Color { Color(hex: colorScheme == .dark ? "1A1A1A" : "F7F7F7") }
    var todayBackground: Color { Color(hex: colorScheme == .dark ? "1A1A1A" : "FFFFFF") }

    func courseColor(_ hex: String) -> Color { isMonochrome ? .primary : Color(hex: hex) }

    func cardBackground(active: Bool) -> Color {
        if isMonochrome { return Color.primary.opacity(active ? 0.14 : 0.045) }
        return Color(hex: active
                     ? (colorScheme == .dark ? "25313B" : "E1EDF7")
                     : (colorScheme == .dark ? "262626" : "FFFFFF"))
    }
}

private extension Color {
    init(hex: String) {
        let value = UInt64(hex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0x2196F3
        self.init(.sRGB, red: Double((value >> 16) & 255) / 255,
                  green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255, opacity: 1)
    }
}

private struct DesktopScheduleMark: View {
    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            let height = geometry.size.height
            Path { path in
                path.addRoundedRect(in: CGRect(x: width * 0.08, y: height * 0.11, width: width * 0.84, height: height * 0.78),
                                    cornerSize: CGSize(width: width * 0.16, height: height * 0.16))
                path.addRoundedRect(in: CGRect(x: width * 0.18, y: height * 0.20, width: width * 0.64, height: height * 0.49),
                                    cornerSize: CGSize(width: width * 0.08, height: height * 0.08))
                path.addRoundedRect(in: CGRect(x: width * 0.28, y: height * 0.34, width: width * 0.27, height: height * 0.09),
                                    cornerSize: CGSize(width: width * 0.035, height: height * 0.035))
                path.addRoundedRect(in: CGRect(x: width * 0.28, y: height * 0.51, width: width * 0.44, height: height * 0.09),
                                    cornerSize: CGSize(width: width * 0.035, height: height * 0.035))
            }
            .fill(style: FillStyle(eoFill: true))
            .scaleEffect(x: 1, y: -1)
        }
        .frame(width: 16, height: 16)
        .accessibilityHidden(true)
    }
}

private struct DesktopWidgetHeader: View {
    let title: String
    let week: String
    let palette: DesktopWidgetPalette

    var body: some View {
        HStack(spacing: 5) {
            DesktopScheduleMark()
                .widgetAccentable()
            Text(title)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
            Spacer(minLength: 3)
            Text(week)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .layoutPriority(1)
        }
        .font(.system(size: 14, weight: .bold))
        .foregroundStyle(palette.header)
        .padding(.horizontal, 4)
    }
}

struct DesktopTodayCourseView: View {
    let entry: DesktopWidgetEntry
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.widgetRenderingMode) private var renderingMode
    @Environment(\.showsWidgetContainerBackground) private var showsBackground

    private var palette: DesktopWidgetPalette {
        DesktopWidgetPalette(colorScheme: colorScheme, renderingMode: renderingMode, showsBackground: showsBackground)
    }

    var body: some View {
        let state = entry.presentation
        VStack(alignment: .leading, spacing: 8) {
            DesktopWidgetHeader(title: state.isTomorrow ? "明天" : state.weekday,
                                week: state.weekText, palette: palette)
            if let course = state.courses.first {
                Spacer(minLength: 0)
                VStack(alignment: .leading, spacing: 5) {
                    Text(course.name)
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(palette.primary)
                        .lineLimit(1)
                        .padding(.bottom, 2)
                    Label {
                        Text("\(DesktopWidgetTime.string(course.start))–\(DesktopWidgetTime.string(course.end))")
                            .monospacedDigit()
                    } icon: {
                        Image(systemName: "clock").frame(width: 12)
                    }
                    Label {
                        Text(course.classroom.isEmpty ? "未指定教室" : course.classroom)
                            .lineLimit(1)
                    } icon: {
                        Image(systemName: "mappin.and.ellipse").frame(width: 12)
                    }
                    .padding(.bottom, 7)
                    remainingCourses(state)
                }
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(palette.secondary)
                .padding(.horizontal, 4)
            } else {
                DesktopWidgetEmptyView(state: state, palette: palette)
            }
        }
        .padding(.horizontal, 11)
        .padding(.vertical, 13)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .containerBackground(for: .widget) { palette.todayBackground }
        .widgetURL(URL(string: "nexioschedule://schedule"))
    }

    private func remainingCourses(_ state: DesktopWidgetPresentation) -> some View {
        let count = max(0, state.courses.count - 1)
        let active = !state.isTomorrow && state.courses.first?.isActive(at: entry.date) == true
        let dots = active ? Array(state.courses.dropFirst().prefix(8)) : Array(state.courses.prefix(8))
        return ViewThatFits(in: .horizontal) {
            HStack(spacing: 4) {
                remainingText(count)
                dotsView(dots)
            }
            VStack(alignment: .leading, spacing: 4) {
                remainingText(count)
                dotsView(dots)
            }
        }
    }

    private func remainingText(_ count: Int) -> some View {
        Text(count > 0 ? "还有\(count)节课" : "没有其他课程")
            .font(.system(size: 11))
            .lineLimit(1)
    }

    private func dotsView(_ courses: [DesktopWidgetSnapshot.Course]) -> some View {
        HStack(spacing: 4) {
            ForEach(Array(courses.enumerated()), id: \.offset) { _, course in
                Circle()
                    .fill(palette.courseColor(course.color))
                    .frame(width: 6, height: 6)
                    .widgetAccentable()
            }
        }
        .accessibilityHidden(true)
    }
}

struct DesktopCourseListView: View {
    let entry: DesktopWidgetEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        if family == .systemSmall {
            DesktopTodayCourseView(entry: entry)
        } else {
            DesktopCourseListContent(entry: entry, rowCount: family == .systemLarge ? 5 : 2)
        }
    }
}

struct DesktopCourseListContent: View {
    let entry: DesktopWidgetEntry
    let rowCount: Int
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.widgetRenderingMode) private var renderingMode
    @Environment(\.showsWidgetContainerBackground) private var showsBackground

    private var palette: DesktopWidgetPalette {
        DesktopWidgetPalette(colorScheme: colorScheme, renderingMode: renderingMode, showsBackground: showsBackground)
    }

    var body: some View {
        let state = entry.presentation
        VStack(spacing: 8) {
            DesktopWidgetHeader(title: state.day == nil ? "课程提醒" : (state.isTomorrow ? "明日课程 / \(state.weekday)" : "今天 / \(state.weekday)"),
                                week: state.weekText, palette: palette)
            if state.courses.isEmpty {
                DesktopWidgetEmptyView(state: state, palette: palette)
            } else {
                ForEach(0..<rowCount, id: \.self) { index in
                    if index < state.courses.count {
                        DesktopCourseRow(course: state.courses[index], date: entry.date,
                                         isTomorrow: state.isTomorrow, palette: palette)
                    } else {
                        Color.clear.frame(maxHeight: .infinity)
                            .accessibilityHidden(true)
                    }
                }
            }
        }
        .padding(.horizontal, 11)
        .padding(.top, 11)
        .padding(.bottom, 10)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .containerBackground(for: .widget) { palette.listBackground }
        .widgetURL(URL(string: "nexioschedule://schedule"))
    }
}

private struct DesktopCourseRow: View {
    let course: DesktopWidgetSnapshot.Course
    let date: Date
    let isTomorrow: Bool
    let palette: DesktopWidgetPalette

    private var isActive: Bool { !isTomorrow && course.isActive(at: date) }

    var body: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(DesktopWidgetTime.string(course.start))
                    .foregroundStyle(palette.header)
                Text(DesktopWidgetTime.string(course.end))
                    .foregroundStyle(palette.secondary)
            }
            .font(.system(size: 12, weight: .medium))
            .monospacedDigit()
            RoundedRectangle(cornerRadius: 2)
                .fill(palette.courseColor(course.color))
                .frame(width: 4, height: 30)
                .widgetAccentable()
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(course.name)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(palette.primary)
                if !course.information.isEmpty {
                    Text(course.information)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(palette.secondary)
                }
            }
            .lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .leading)
            if isActive {
                Text("上课中")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(palette.active)
                    .widgetAccentable()
                    .fixedSize()
            }
        }
        .padding(.horizontal, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.cardBackground(active: isActive), in: RoundedRectangle(cornerRadius: 12))
        .overlay {
            if isActive && palette.isMonochrome {
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(palette.primary.opacity(0.30), lineWidth: 1)
                    .widgetAccentable()
            }
        }
        .accessibilityElement(children: .combine)
    }
}

private struct DesktopWidgetEmptyView: View {
    let state: DesktopWidgetPresentation
    let palette: DesktopWidgetPalette

    var body: some View {
        VStack(spacing: 7) {
            if state.cacheState != .ready {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.system(size: 19, weight: .medium))
                    .widgetAccentable()
                    .accessibilityHidden(true)
            }
            Text(state.emptyText)
                .font(.system(size: 14, weight: .medium))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            if state.cacheState != .ready {
                Text("轻点小组件打开应用")
                    .font(.system(size: 11))
            }
        }
        .foregroundStyle(palette.secondary)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.bottom, 8)
    }
}

private enum DesktopWidgetTime {
    static let formatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "HH:mm"
        return formatter
    }()

    static func string(_ epoch: Double) -> String {
        formatter.string(from: Date(timeIntervalSince1970: epoch))
    }
}

// MARK: - Previews

enum DesktopWidgetPreview {
    static var snapshot: DesktopWidgetSnapshot {
        let calendar = Calendar.current
        let start = calendar.startOfDay(for: Date())
        let next = calendar.date(byAdding: .day, value: 1, to: start)!
        let courses = [
            ("高等数学", "教学楼 A302", "张老师", "1–2节", 8, "EF9A54"),
            ("大学英语", "教学楼 B201", "李老师", "3–4节", 10, "6588D8"),
            ("大学物理", "实验楼 C102", "王老师", "5–6节", 14, "77B799"),
            ("程序设计基础", "计算机楼 402", "陈老师", "7–8节", 16, "B58ACA"),
            ("体育", "体育馆", "刘老师", "9–10节", 19, "DA889A")
        ].enumerated().map { index, value in
            DesktopWidgetSnapshot.Course(
                id: "preview-\(index)", name: value.0, classroom: value.1, teacher: value.2, section: value.3,
                start: calendar.date(bySettingHour: value.4, minute: 0, second: 0, of: start)!.timeIntervalSince1970,
                end: calendar.date(bySettingHour: value.4 + 1, minute: 40, second: 0, of: start)!.timeIntervalSince1970,
                color: value.5
            )
        }
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return DesktopWidgetSnapshot(generatedAt: start.timeIntervalSince1970, validUntil: next.timeIntervalSince1970,
                                     tomorrowEnabled: false, tomorrowMinute: 1200,
                                     days: [.init(date: formatter.string(from: start), start: start.timeIntervalSince1970,
                                                  end: next.timeIntervalSince1970, week: 8, status: "normal", courses: courses)])
    }

    static var entry: DesktopWidgetEntry {
        DesktopWidgetEntry(date: Date(timeIntervalSince1970: snapshot.days[0].courses[0].start + 1800), snapshot: snapshot)
    }
}

struct DesktopCourseWidgets_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            DesktopTodayCourseView(entry: DesktopWidgetPreview.entry)
                .previewContext(WidgetPreviewContext(family: .systemSmall))
                .previewDisplayName("今日课程 · 浅色")
            DesktopCourseListView(entry: DesktopWidgetPreview.entry)
                .previewContext(WidgetPreviewContext(family: .systemMedium))
                .previewDisplayName("课程提醒 · 中号")
            DesktopCourseListView(entry: DesktopWidgetPreview.entry)
                .environment(\.colorScheme, .dark)
                .previewContext(WidgetPreviewContext(family: .systemLarge))
                .previewDisplayName("课程提醒 · 深色大号")
            DesktopCourseListView(entry: DesktopWidgetPreview.entry)
                .environment(\.widgetRenderingMode, .accented)
                .previewContext(WidgetPreviewContext(family: .systemMedium))
                .previewDisplayName("课程提醒 · 系统着色布局")
            DesktopTodayCourseView(entry: .init(date: Date(), snapshot: nil))
                .previewContext(WidgetPreviewContext(family: .systemSmall))
                .previewDisplayName("首次同步")
        }
    }
}
