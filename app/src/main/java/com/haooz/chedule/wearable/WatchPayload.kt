package com.haooz.chedule.wearable

import android.content.Context
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.CourseTimeResolver
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.reminder.CourseReminderHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 手机端 → 手表端 课表 JSON 组装。
 *
 * 协议与手表 `src/common/sync.js` 对齐：
 * protocol=nexio.schedule, version=1, action=replace, week: {0..6 -> Course[]}
 * holidays: HolidayManager.Entry[]（type=0 假期隐藏 / type=1 调休跟 followWeekday）
 * 手表 weekday：0=周日 … 6=周六
 * 本应用 dayOfWeek：1=周一 … 7=周日
 *
 * version=3（[buildDaysJson]）：按日期直推，手表只做映射渲染。
 *
 * version=4（[buildFullJson]）：整表推送 —— 一次发「整学期课程（含周次规则）+
 * 学期设置 + 节次时间 + 假期/调休」，手表按周一起算公式自行推算任意一天，
 * 手环端无需再分次收数据。字段与手表 sync.js 的整表模式（mode=full）对齐。
 */
object WatchPayload {

    private const val PROTOCOL = "nexio.schedule"
    private const val VERSION = 1

    /** 按日期直推使用的协议版本 */
    private const val DATED_VERSION = 3

    /** 整表推送使用的协议版本 */
    private const val FULL_VERSION = 4

    /** 手机 dayOfWeek(1=周一..7=周日) → 手表 week key(0=周日..6=周六) */
    fun toWatchDay(dayOfWeek: Int): Int = when (dayOfWeek) {
        7 -> 0
        in 1..6 -> dayOfWeek
        else -> -1
    }

    /**
     * 按「当前教学周」过滤后的整周课表 JSON 字符串。
     * 只推本周会上的课，与手表「今日」逻辑一致，避免单双周/选周造成误显示。
     * 一并带上 HolidayManager 假期/调休，手表端据此隐藏假期课或映射调休日。
     * @param scheduleId 指定课表名；空则用当前课表
     */
    fun buildWeekJson(
        repository: CourseRepository,
        context: Context,
        scheduleId: String = ""
    ): String {
        val sid = scheduleId.ifEmpty { repository.getCurrentScheduleId() }
        val week = repository.getLiveTeachingWeek(scheduleId = sid)
        val all = if (sid == repository.getCurrentScheduleId()) {
            repository.getAllCourses()
        } else {
            repository.getCoursesForSchedule(sid)
        }
        // 本周过滤；若本周无课则退回全量，避免推送空表导致手表无显示
        val active = all.filter { it.isActiveInWeek(week) }
        val source = if (active.isEmpty()) all else active
        val weekMap = JSONObject()
        // 手表 week key 0-6
        val buckets = Array(7) { JSONArray() }

        for (course in source) {
            val day = toWatchDay(course.dayOfWeek)
            if (day < 0) continue
            buckets[day].put(toCourseJson(course, repository))
        }
        for (i in 0..6) {
            weekMap.put(i.toString(), buckets[i])
        }

        val holidaysArr = buildHolidaysJson(context)
        android.util.Log.i(
            "WatchPayload",
            "build sid=$sid teachWeek=$week total=${all.size} active=${active.size} packed=${source.size} holidays=${holidaysArr.length()}"
        )

        return JSONObject()
            .put("protocol", PROTOCOL)
            .put("version", VERSION)
            .put("action", "replace")
            .put("sentAt", System.currentTimeMillis())
            .put("scheduleName", sid)
            .put("holidays", holidaysArr)
            .put("week", weekMap)
            .toString()
    }

    /**
     * 按日期直推课表 JSON（协议 version=3）。
     *
     * 与 [buildWeekJson] 的区别：不再只推「当前教学周」的分桶数据，而是把每一天由本应用
     * 解析好的课表直接发过去（含时段、节次文案、起止时间、假期/调休标记）。
     * 手表端只做映射渲染，不做周次/节次推算，因此：
     *   - 手环翻到任意日期都能显示正确课表（不会再固定显示同一周）
     *   - 不会因为手环自己算教学周而与手机差一周
     *
     * 包结构：
     * ```
     * { protocol, version:3, action:"replace", sentAt, week,
     *   days:[ { date:"2026-10-10", week:5, isHoliday:false, isWorkSwap:true,
     *            courses:[ { id, name, startTime, endTime, period, periods, location, teacher } ] } ] }
     * ```
     * period 取 morning / afternoon / evening（手环直接用作分组）。
     *
     * @param backDays 往前推的天数（默认 14）
     * @param forwardDays 往后推的天数（默认 14）
     */
    fun buildDaysJson(
        repository: CourseRepository,
        context: Context,
        backDays: Int = 14,
        forwardDays: Int = 14
    ): String {
        val today = LocalDate.now()
        val sectionTimes = repository.getCurrentSectionTimes()
        val morningSections = repository.getMorningSections()
        val afternoonSections = repository.getAfternoonSections()
        val days = JSONArray()
        var packed = 0

        for (offset in -backDays..forwardDays) {
            val date = today.plusDays(offset.toLong())
            val resolution = CourseReminderHelper.resolveDaySchedule(context, date, repository)
            val courses = JSONArray()
            for (course in resolution.courses) {
                val period = when (
                    course.periodIndex(sectionTimes, morningSections, afternoonSections)
                ) {
                    Course.PERIOD_MORNING -> "morning"
                    Course.PERIOD_AFTERNOON -> "afternoon"
                    else -> "evening"
                }
                courses.put(
                    JSONObject()
                        .put("id", course.id)
                        .put("name", course.name)
                        .put("startTime", CourseTimeResolver.getStartTime(course, repository) ?: "")
                        .put("endTime", CourseTimeResolver.getEndTime(course, repository) ?: "")
                        .put("period", period)
                        .put("periods", course.getSectionText())
                        .put("location", course.classroom ?: "")
                        .put("teacher", course.teacher ?: "")
                )
                packed++
            }
            days.put(
                JSONObject()
                    .put("date", date.toString())
                    .put("week", resolution.displayWeek)
                    .put("isHoliday", resolution.isHolidayDate)
                    .put("isWorkSwap", resolution.isWorkSwap)
                    .put("courses", courses)
            )
        }

        val week = repository.getLiveTeachingWeek()
        android.util.Log.i(
            "WatchPayload",
            "buildDays back=$backDays forward=$forwardDays packed=$packed week=$week"
        )

        return JSONObject()
            .put("protocol", PROTOCOL)
            .put("version", DATED_VERSION)
            .put("action", "replace")
            .put("sentAt", System.currentTimeMillis())
            .put("week", week)
            .put("days", days)
            .toString()
    }

    /** HolidayManager.Entry → 手表 holidays 数组（字段与 Entry.toJson 一致） */
    private fun buildHolidaysJson(context: Context): JSONArray {
        val arr = JSONArray()
        val entries = HolidayManager.loadAllByYear(context).values.flatten()
        for (entry in entries) {
            arr.put(entry.toJson())
        }
        return arr
    }

    /**
     * 整表推送 JSON（协议 version=4）：一次打包完整学期，手表端自行推算任意日期。
     *
     * 包结构（与手表 sync.js normalizePayload 的整表分支对齐）：
     * ```
     * { protocol, version:4, action:"replace", sentAt, schedule_name,
     *   settings: { class_start_time:"yyyy/MM/dd", current_week, total_weeks,
     *               morning_sections, afternoon_sections, evening_sections },
     *   times:    { morning:{'1':'08:00-08:40',...}, afternoon:{...}, evening:{...} },
     *   courses:  [ { id, name, dayOfWeek(1-7), startSection, endSection,
     *                 startWeek, endWeek, weekType(0=全周/1=单周/2=双周),
     *                 selectedWeeks:[...], isCustomTime, customStartTime, customEndTime,
     *                 location, teacher } ],
     *   holidays: [ HolidayManager.Entry.toJson() ] }
     * ```
     * 周次公式以 [com.haooz.chedule.data.CourseScheduleDateBounds.calendarWeekForDate]
     * 为准（周一起算，第 1 周 = 开学日所在周），手表端用 settings.class_start_time 复现。
     */
    fun buildFullJson(
        repository: CourseRepository,
        context: Context,
        scheduleId: String = ""
    ): String {
        val sid = scheduleId.ifEmpty { repository.getCurrentScheduleId() }
        val all = if (sid == repository.getCurrentScheduleId()) {
            repository.getAllCourses()
        } else {
            repository.getCoursesForSchedule(sid)
        }

        val courseArr = JSONArray()
        for (course in all) {
            if (toWatchDay(course.dayOfWeek) < 0) continue
            courseArr.put(
                JSONObject()
                    .put("id", course.id)
                    .put("name", course.name)
                    .put("dayOfWeek", course.dayOfWeek)
                    .put("startSection", course.startSection)
                    .put("endSection", course.endSection)
                    .put("startWeek", course.startWeek)
                    .put("endWeek", course.endWeek)
                    .put("weekType", course.weekType)
                    .put("selectedWeeks", JSONArray(course.selectedWeeks))
                    .put("isCustomTime", course.isCustomTime)
                    .put("customStartTime", course.customStartTime ?: "")
                    .put("customEndTime", course.customEndTime ?: "")
                    .put("location", course.classroom ?: "")
                    .put("teacher", course.teacher ?: "")
            )
        }

        val settings = JSONObject()
            .put("class_start_time", repository.getClassStartTime(sid))
            .put("current_week", repository.getLiveTeachingWeek(scheduleId = sid))
            .put("total_weeks", repository.getTotalWeeks(sid))
            .put("morning_sections", repository.getMorningSections(sid))
            .put("afternoon_sections", repository.getAfternoonSections(sid))
            .put("evening_sections", repository.getEveningSections(sid))

        val times = JSONObject()
            .put("morning", periodTimesJson(repository, "morning", sid))
            .put("afternoon", periodTimesJson(repository, "afternoon", sid))
            .put("evening", periodTimesJson(repository, "evening", sid))

        val holidaysArr = buildHolidaysJson(context)
        android.util.Log.i(
            "WatchPayload",
            "buildFull sid=$sid total=${all.size} packed=${courseArr.length()} " +
                "week=${settings.optInt("current_week")}/${settings.optInt("total_weeks")} " +
                "holidays=${holidaysArr.length()}"
        )

        return JSONObject()
            .put("protocol", PROTOCOL)
            .put("version", FULL_VERSION)
            .put("action", "replace")
            .put("sentAt", System.currentTimeMillis())
            .put("schedule_name", sid)
            .put("settings", settings)
            .put("times", times)
            .put("courses", courseArr)
            .put("holidays", holidaysArr)
            .toString()
    }

    /** 节次时间 → 手表格式 {相对节次号: 'HH:mm-HH:mm'}（getPeriodTimes 本身即此格式） */
    private fun periodTimesJson(
        repository: CourseRepository,
        period: String,
        scheduleId: String
    ): JSONObject {
        val obj = JSONObject()
        for ((index, time) in repository.getPeriodTimes(period, scheduleId)) {
            obj.put(index.toString(), time ?: "")
        }
        return obj
    }

    private fun toCourseJson(course: Course, repository: CourseRepository): JSONObject {
        val start = CourseTimeResolver.getStartTime(course, repository) ?: ""
        val end = CourseTimeResolver.getEndTime(course, repository) ?: ""
        val periods = course.getSectionText()
        return JSONObject()
            .put("id", course.id)
            .put("name", course.name)
            .put("startTime", start)
            .put("endTime", end)
            .put("periods", periods)
            .put("location", course.classroom ?: "")
            .put("teacher", course.teacher ?: "")
    }
}
