@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import platform.Foundation.NSThread
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync

class IosCourseNotifierTest {
    @Test
    fun reconcilesRollingNoticesAndUsesCurrentSettingsAfterAuthorization() {
        val check = {
            AppStorage.init { InMemoryKeyValueStore() }
            val repo = CourseRepository()
            val zone = TimeZone.currentSystemDefault()
            val today = Clock.System.todayIn(zone)
            repo.setClassStartTime(CourseRepository.formatClassStartDate(today))
            repo.setTotalWeeks(20)
            repo.saveCourses((1..7).map { day ->
                Course(
                    id = "reminder-test-$day", name = "Course $day", classroom = "Room",
                    teacher = "", dayOfWeek = day, startSection = 1, endSection = 1,
                    startWeek = 1, endWeek = 20, weekType = 0, colorRes = 0,
                    isCustomTime = true, customStartTime = "12:00", customEndTime = "12:45",
                )
            }, notify = false)
            repo.setPreClassReminder(false)
            repo.setNextDayReminder(true)
            repo.setNextDayReminderHour(21)
            repo.setNextDayReminderMinute(0)
            var writes = emptyList<IosCourseNotifier.Notice>()
            var requests = 0
            var cancels = 0
            IosCourseNotifier.install(
                write = { writes = it }, cancelAll = { cancels++ },
                requestAuthorization = { requests++ },
            )
            IosCourseNotifier.requestAuthorizationFromSwift(false)

            // Launch/resume must consult the system even after a previous grant.
            IosCourseNotifier.requestAuthorizationFromSwift(true)
            IosCourseNotifier.reconcile()
            assertEquals(1, requests)
            IosCourseNotifier.requestAuthorizationFromSwift(true)
            assertTrue(writes.size >= IosCourseNotifier.WINDOW_DAYS - 1)
            writes.forEach { notice ->
                val fire = kotlinx.datetime.Instant.fromEpochMilliseconds(notice.fireEpochMillis)
                    .toLocalDateTime(zone)
                assertEquals(21, fire.hour)
                assertEquals(0, fire.minute)
                val courseDate = fire.date.plus(DatePeriod(days = 1))
                assertEquals("${IosCourseNotifier.ID_PREFIX_NEXT_DAY}.$courseDate", notice.id)
            }
            // Tomorrow evening's summary must already be queued without reopening.
            val dayAfterTomorrow = today.plus(DatePeriod(days = 2))
            assertTrue(writes.any { it.id == "${IosCourseNotifier.ID_PREFIX_NEXT_DAY}.$dayAfterTomorrow" })

            // A pending authorization must not resurrect switches turned off meanwhile.
            IosCourseNotifier.requestAuthorizationFromSwift(false)
            IosCourseNotifier.reschedule(repo)
            repo.setNextDayReminder(false)
            IosCourseNotifier.reschedule(repo)
            assertTrue(cancels > 0)
            IosCourseNotifier.requestAuthorizationFromSwift(true)
            assertTrue(writes.isEmpty())

            // Returning from System Settings must replace a stale positive cache.
            repo.setPreClassReminder(true)
            IosCourseNotifier.reconcile()
            assertEquals(3, requests)
            IosCourseNotifier.requestAuthorizationFromSwift(false)
            assertFalse(IosCourseNotifier.isAuthorized())
            assertTrue(writes.isEmpty())

            // Restore injectable callbacks so this test cannot leak native work.
            repo.setPreClassReminder(false)
            IosCourseNotifier.install(write = {}, cancelAll = {}, requestAuthorization = {})
        }
        if (NSThread.currentThread.isMainThread) check()
        else dispatch_sync(dispatch_get_main_queue(), check)
    }
}
