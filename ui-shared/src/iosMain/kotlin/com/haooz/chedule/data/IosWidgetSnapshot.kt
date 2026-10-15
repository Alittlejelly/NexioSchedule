@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.chedule.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Exports resolved course data to the Swift App Group writer, independently of
 * notification authorization. WidgetKit only reads the resulting JSON snapshot;
 * all teaching-week, holiday and swap-day rules remain in [DayScheduleResolver].
 *
 * Call [refresh] on foreground entry and after holiday/reminder settings change:
 * those settings do not all emit CourseRepository's course-change event.
 */
object IosWidgetSnapshot {
    private const val TAG = "IosWidgetSnapshot"
    private const val FUTURE_DAYS = 30

    // These fields and the native callback are confined to the main queue.
    private var nativeWrite: (String) -> Unit = {}
    private var installed = false
    private var refreshQueued = false
    private val courseChanged: (String, String) -> Unit = { _, _ -> refresh() }

    /** Re-installing replaces the writer without registering a second listener. */
    fun install(write: (String) -> Unit) {
        onMain {
            nativeWrite = write
            if (!installed) {
                CourseRepository.getInstance().addCourseChangedListener(courseChanged)
                installed = true
            }
            queueRefresh()
        }
    }

    /** Safe from IO callbacks; repeated changes in one main-queue turn coalesce. */
    fun refresh() {
        onMain { queueRefresh() }
    }

    private fun queueRefresh() {
        if (!installed || refreshQueued) return
        refreshQueued = true
        // Always enqueue the export, including main-thread callers. Imports can
        // commit several settings together; exporting inline sees partial state
        // and may recursively re-enter through the Swift callback.
        dispatch_async(dispatch_get_main_queue()) {
            refreshQueued = false
            try {
                nativeWrite(buildSnapshot(CourseRepository.getInstance()))
            } catch (error: Exception) {
                // Preserve the last valid on-disk snapshot when resolution fails.
                NexioLog.e(TAG, "Failed to export widget schedule snapshot", error)
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (NSThread.currentThread.isMainThread) block()
        else dispatch_async(dispatch_get_main_queue(), block)
    }

    private fun buildSnapshot(repository: CourseRepository): String {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(zone).date
        val holidays = HolidayManager.loadAllByYear()
        val totalWeeks = repository.getTotalWeeks()
        val lastCourseWeek = repository.getLastWeekWithCourses()

        return buildJsonObject {
            put("schemaVersion", 1)
            put("generatedAt", now.toEpochMilliseconds() / 1000.0)
            // Day +30 is included for tomorrow preview. Expire at the start of
            // that day so the last valid evening (+29) always has tomorrow data.
            put("validUntil", today.plus(DatePeriod(days = FUTURE_DAYS))
                .atStartOfDayIn(zone).toEpochMilliseconds() / 1000.0)
            put("tomorrowEnabled", repository.getNextDayReminder())
            put("tomorrowMinute", repository.getNextDayReminderHour().coerceIn(0, 23) * 60 +
                repository.getNextDayReminderMinute().coerceIn(0, 59))
            putJsonArray("days") {
                for (offset in -1..FUTURE_DAYS) {
                    val date = today.plus(DatePeriod(days = offset))
                    val nextDate = date.plus(DatePeriod(days = 1))
                    val resolution = DayScheduleResolver.resolveDaySchedule(date, repository, holidays)
                    val courses = resolution.courses.mapNotNull { course ->
                        courseSnapshot(course, date, repository, zone)
                    }.sortedBy { it.first }
                    add(buildJsonObject {
                        put("date", date.toString())
                        put("start", date.atStartOfDayIn(zone).toEpochMilliseconds() / 1000.0)
                        put("end", nextDate.atStartOfDayIn(zone).toEpochMilliseconds() / 1000.0)
                        put("week", resolution.displayWeek)
                        put("status", when {
                            resolution.displayWeek < 1 -> "notStarted"
                            resolution.isHolidayDate || resolution.displayWeek > totalWeeks ||
                                resolution.displayWeek > lastCourseWeek -> "holiday"
                            else -> "normal"
                        })
                        putJsonArray("courses") { courses.forEach { add(it.second) } }
                    })
                }
            }
        }.toString()
    }

    /** Invalid clock values are omitted instead of inventing a course time. */
    private fun courseSnapshot(
        course: Course,
        date: LocalDate,
        repository: CourseRepository,
        zone: TimeZone,
    ): Pair<Long, JsonObject>? {
        val startTime = parseClock(CourseTimeResolver.getStartTime(course, repository)) ?: return null
        val endTime = parseClock(CourseTimeResolver.getEndTime(course, repository)) ?: return null
        val endDate = if (CourseTimeSpan.crossesMidnight(
                startTime.hour * 60 + startTime.minute,
                endTime.hour * 60 + endTime.minute,
            )) date.plus(DatePeriod(days = 1)) else date
        val start = date.atTime(startTime).toInstant(zone).toEpochMilliseconds()
        val end = endDate.atTime(endTime).toInstant(zone).toEpochMilliseconds()
        return start to buildJsonObject {
            put("id", course.id)
            put("name", course.name)
            put("classroom", course.classroom)
            put("teacher", course.teacher)
            put("section", if (course.hasValidCustomTime()) "" else course.getSectionText())
            put("start", start / 1000.0)
            put("end", end / 1000.0)
            put("color", (course.colorRes and 0xFFFFFFL).toString(16).padStart(6, '0').uppercase())
        }
    }

    private fun parseClock(value: String?): LocalTime? {
        val parts = value?.split(":") ?: return null
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime(hour, minute)
    }
}
