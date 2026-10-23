import Foundation
import WidgetKit

@main
// Standalone simulator regression checks (run-desktop-widget-checks.sh); compiles the production widget source without an XCTest/project target.
struct DesktopWidgetTimelineChecks {
    static func main() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        let midnight = calendar.date(from: DateComponents(year: 2026, month: 10, day: 15))!.timeIntervalSince1970
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: Date(timeIntervalSince1970: midnight))!.timeIntervalSince1970
        let dayAfter = calendar.date(byAdding: .day, value: 2, to: Date(timeIntervalSince1970: midnight))!.timeIntervalSince1970
        func time(_ hour: Int, _ minute: Int = 0) -> Double {
            calendar.date(bySettingHour: hour, minute: minute, second: 0, of: Date(timeIntervalSince1970: midnight))!.timeIntervalSince1970
        }
        func course(_ id: String, _ start: Double, _ end: Double) -> DesktopWidgetSnapshot.Course {
            .init(id: id, name: "课程\(id)", classroom: "A302", teacher: "张老师", section: "1–2节", start: start, end: end, color: "EF9A54")
        }
        var snapshot = DesktopWidgetSnapshot(
            generatedAt: midnight, validUntil: dayAfter, tomorrowEnabled: true, tomorrowMinute: 20 * 60,
            days: [
                .init(date: "2026-10-15", start: midnight, end: tomorrow, week: 8, status: "normal",
                      courses: [course("a", time(8), time(9, 40)), course("b", time(19,30), time(21))]),
                .init(date: "2026-10-16", start: tomorrow, end: dayAfter, week: 9, status: "normal",
                      courses: [course("c", tomorrow + 8*3600, tomorrow + 10*3600)])
            ])
        func state(_ value: Double) -> DesktopWidgetPresentation {
            DesktopWidgetPresentation(snapshot: snapshot, at: Date(timeIntervalSince1970: value))
        }
        func check(_ condition: @autoclosure () -> Bool, _ message: String) {
            precondition(condition(), message)
            print("PASS: \(message)")
        }
        let contractJSON = """
        {"schemaVersion":1,"generatedAt":100,"validUntil":200,"tomorrowEnabled":true,"tomorrowMinute":1200,"days":[{"date":"2026-10-15","start":100,"end":200,"week":8,"status":"normal","courses":[{"id":"raw","name":"高等数学","classroom":"A302","teacher":"张老师","section":"1–2节","start":110,"end":120,"color":"EF9A54"}]}]}
        """
        let contract = try JSONDecoder().decode(DesktopWidgetSnapshot.self, from: Data(contractJSON.utf8))
        check(contract.schemaVersion == 1 && contract.days[0].courses[0].id == "raw", "independently authored JSON follows writer contract")
        let decoded = try JSONDecoder().decode(DesktopWidgetSnapshot.self, from: JSONEncoder().encode(snapshot))
        check(decoded.days[0].courses[0].section == "1–2节", "JSON schema round-trip preserves course data")
        check(state(time(7)).courses.first?.id == "a", "before class displays next course")
        check(state(time(7)).weekday == "周四", "header uses calendar weekday")
        check(state(time(8)).courses.first?.isActive(at: Date(timeIntervalSince1970: time(8))) == true, "class starts inclusively")
        check(state(time(9, 40)).courses.first?.id == "b", "class ending is removed at exact boundary")
        check(!state(time(20, 30)).isTomorrow, "tomorrow cannot replace unfinished evening class")
        check(state(time(21)).isTomorrow && state(time(21)).courses.first?.id == "c", "tomorrow begins only after last class ends")
        check(state(time(21)).weekText == "第9周", "tomorrow uses next day's teaching week")
        let timeline = DesktopWidgetTimeline.make(snapshot: snapshot, now: Date(timeIntervalSince1970: time(7)))
        let timestamps = timeline.entries.map { $0.date.timeIntervalSince1970 }
        check(timestamps.contains(time(8)) && timestamps.contains(time(9, 40)) && timestamps.contains(time(21)) && timestamps.contains(tomorrow), "timeline contains class, tomorrow-switch and midnight transitions")
        check(!timestamps.contains(time(20)), "no unnecessary tomorrow entry while a class remains")
        check(timestamps == timestamps.sorted() && Set(timestamps).count == timestamps.count, "timeline events are sorted and deduplicated")
        check(timeline.entries.count == 10, "two-day fixture uses events rather than minute polling")
        check(timeline.entries.last?.presentation.cacheState == .expired, "timeline contains explicit cache-expiration entry")
        check(state(dayAfter).cacheState == .expired, "validUntil is an exclusive boundary")
        check(DesktopWidgetPresentation(snapshot: nil, at: Date()).cacheState == .unavailable, "missing cache requests initial app sync")
        check(state(tomorrow).courses.first?.id == "c" && !state(tomorrow).isTomorrow, "midnight resets tomorrow state")
        let originalDays = snapshot.days
        snapshot.days.removeLast()
        check(state(time(21)).cacheState == .expired, "missing tomorrow coverage requests an update")
        snapshot.days = originalDays
        snapshot.schemaVersion = 2
        check(state(time(8)).cacheState == .expired, "unsupported schema cannot display courses")
        snapshot.schemaVersion = 1
        snapshot.tomorrowEnabled = false
        check(!state(time(22)).isTomorrow && state(time(22)).emptyText == "今日课程已上完", "disabled tomorrow reminder preserves end-of-day state")
        snapshot.days[0].courses = []
        snapshot.tomorrowEnabled = true
        check(!state(time(19, 59)).isTomorrow && state(time(20)).isTomorrow, "empty day switches exactly at configured time")
        snapshot.tomorrowEnabled = false
        check(state(time(8)).emptyText == "今日无课", "normal empty day")
        snapshot.days[0].status = "holiday"
        check(state(time(8)).emptyText == "假期中，暂无课程", "holiday empty state")
        snapshot.days[0].status = "notStarted"
        check(state(time(8)).emptyText == "学期暂未开始", "semester not-started state")
        snapshot.days[0].status = "normal"
        snapshot.days.insert(.init(date: "2026-10-14", start: midnight - 86400, end: midnight, week: 8, status: "normal", courses: [course("night", midnight - 1800, midnight + 1800)]), at: 0)
        check(state(midnight + 600).courses.first?.id == "night", "cross-midnight class carries into next day")
        let nightTimeline = DesktopWidgetTimeline.make(snapshot: snapshot, now: Date(timeIntervalSince1970: midnight + 600))
        check(nightTimeline.entries.contains { $0.date.timeIntervalSince1970 == midnight + 1800 }, "carryover class ending is a timeline event")
        snapshot.days.removeFirst()
        snapshot.days[0].courses = (0..<200).map { course("dense-\($0)", midnight + Double($0*30), midnight + Double($0*30+15)) }
        let dense = DesktopWidgetTimeline.make(snapshot: snapshot, now: Date(timeIntervalSince1970: midnight))
        check(dense.entries.count == DesktopWidgetTimeline.maximumEntries, "timeline respects 96-entry cap")
        check(dense.policy == .after(dense.entries.last!.date), "capped timeline requests renewal at last covered event")
        snapshot.days = []
        check(state(time(8)).cacheState == .expired, "missing day coverage never displays stale courses")
        var losAngeles = Calendar(identifier: .gregorian)
        losAngeles.timeZone = TimeZone(identifier: "America/Los_Angeles")!
        let dstStart = losAngeles.date(from: DateComponents(year: 2026, month: 3, day: 8))!
        let dstEnd = losAngeles.date(byAdding: .day, value: 1, to: dstStart)!
        let dstDay = DesktopWidgetSnapshot.Day(date: "2026-03-08", start: dstStart.timeIntervalSince1970, end: dstEnd.timeIntervalSince1970, week: 1, status: "normal", courses: [])
        snapshot.tomorrowMinute = 20*60
        let switchDate = Date(timeIntervalSince1970: DesktopWidgetTimeline.tomorrowSwitch(for: dstDay, snapshot: snapshot, calendar: losAngeles))
        check(losAngeles.component(.hour, from: switchDate) == 20, "DST switch uses configured local wall-clock hour")
        print("All 30 desktop widget contract checks passed")
    }
}
