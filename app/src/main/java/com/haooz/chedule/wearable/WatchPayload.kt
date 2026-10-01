package com.haooz.chedule.wearable

import android.content.Context
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.CourseTimeResolver
import com.haooz.chedule.data.HolidayManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * 手机端 → 手表端 课表 JSON 组装。
 *
 * 协议与手表 `src/common/sync.js` 对齐：
 * protocol=nexio.schedule, version=1, action=replace, week: {0..6 -> Course[]}
 * holidays: HolidayManager.Entry[]（type=0 假期隐藏 / type=1 调休跟 followWeekday）
 * 手表 weekday：0=周日 … 6=周六
 * 本应用 dayOfWeek：1=周一 … 7=周日
 */
object WatchPayload {

    private const val PROTOCOL = "nexio.schedule"
    private const val VERSION = 1

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

    /** HolidayManager.Entry → 手表 holidays 数组（字段与 Entry.toJson 一致） */
    private fun buildHolidaysJson(context: Context): JSONArray {
        val arr = JSONArray()
        val entries = HolidayManager.loadAllByYear(context).values.flatten()
        for (entry in entries) {
            arr.put(entry.toJson())
        }
        return arr
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
