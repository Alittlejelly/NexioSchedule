@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.chedule.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import kotlin.concurrent.Volatile

/**
 * Course reminder scheduling for iOS: pre-class notices and tomorrow's summary.
 *
 * Split of duties — Swift (`NativeCourseNotifier`) owns anything touching
 * `UserNotifications` or `ActivityKit`, because Kotlin/Native's ObjC bindings do not
 * export the `UNCalendarNotificationTrigger` factory methods, and ActivityKit is a
 * pure-Swift framework cinterop cannot reach. This side only does platform-neutral
 * work: read settings, resolve target days, cap the pending list. Same injection
 * pattern as `IosToast` / `IosNativeCapture`.
 *
 * Gaps vs Android (see docs/KMP_IOS_MIGRATION_PLAN.md §7):
 *  - no `setAlarmClock` equivalent, so pre-class notices may fire late
 *  - the in-class island can only start on a notification tap or in the foreground
 *  - DND control is forbidden to third parties
 *  - 64 pending notices is a hard cap, hence the rolling window
 *
 * Schedule resolution is NOT here: ask [DayScheduleResolver], shared with Android's
 * reminder code, so both platforms cannot drift apart.
 */
object IosCourseNotifier {

    /** Rolling window: 14 days × at most one pre-class notice per day. */
    const val WINDOW_DAYS = 14

    /**
     * Pending ceiling. iOS allows 64; headroom is left for the summary and test
     * notices. Past the cap `add` fails and new notices are dropped silently, so the
     * trim has to happen here rather than in Swift.
     */
    const val MAX_PENDING = 56

    // Notice identifiers; Swift cancels by prefix, so do not change the format.
    const val ID_PREFIX_PRE_CLASS = "nexio.pre_class"
    const val ID_PREFIX_NEXT_DAY = "nexio.next_day"

    // userInfo keys, read back by the host on tap.
    const val KEY_ACTION = "nexio.action"
    const val KEY_COURSE_ID = "nexio.course_id"
    const val KEY_COURSE_NAME = "nexio.course_name"
    const val KEY_CLASSROOM = "nexio.classroom"
    const val KEY_SECTION = "nexio.section"
    const val KEY_START_TIME = "nexio.start_time"
    const val KEY_END_TIME = "nexio.end_time"
    const val KEY_START_EPOCH = "nexio.start_epoch"
    const val KEY_END_EPOCH = "nexio.end_epoch"
    const val KEY_FIRE_EPOCH = "nexio.fire_epoch"

    /** Tap a pre-class notice -> start the Live Activity, when the island switch is on. */
    const val ACTION_OPEN_COURSE_LIVE = "open_course_live"

    /** Tap tomorrow's summary -> open the app. */
    const val ACTION_OPEN_APP = "open_app"

    /** Shared with Android's `CourseReminderHelper`. */
    private const val PREF_IN_CLASS = "course_reminder_prefs"
    private const val DEFAULT_LEAD_MINUTES = 10

    @Volatile
    private var authorized = false

    /**
     * Injection points. Plain non-null fields plus [installed] rather than nullable
     * function types: Kotlin/Native mis-bridges `var f: ((Boolean) -> Unit)?`, parsing
     * `f.invoke(cb)` as passing a Boolean argument. Reproduced with nullable,
     * non-null and local-val variants alike.
     */
    @Volatile
    private var installed = false

    private var nativeWrite: (List<Notice>) -> Unit = {}
    private var nativeCancelAll: () -> Unit = {}
    private var nativeSendTestNotification: () -> Unit = {}

    /** Parameterless by design; the result comes back via [requestAuthorizationFromSwift]. */
    private var nativeRequestAuthorization: () -> Unit = {}

    /**
     * One pending notice. Mirrors the Swift side field for field.
     *
     * @param userInfo carried on the notification, handed back to the host on tap.
     */
    data class Notice(
        val id: String,
        val fireEpochMillis: Long,
        val title: String,
        val body: String,
        val userInfo: Map<String, String>,
    )

    fun install(
        write: (List<Notice>) -> Unit,
        cancelAll: () -> Unit,
        requestAuthorization: () -> Unit,
        sendTestNotification: () -> Unit = {},
    ) {
        nativeWrite = write
        nativeCancelAll = cancelAll
        nativeRequestAuthorization = requestAuthorization
        nativeSendTestNotification = sendTestNotification
        installed = true
    }

    fun sendTestNotification() {
        if (installed) nativeSendTestNotification()
    }

    /** Called by Swift once the authorization dialog resolves. */
    fun requestAuthorizationFromSwift(granted: Boolean) {
        authorized = granted
    }

    // MARK: Rescheduling

    /**
     * Reconcile every notice against current settings; equivalent to Android's
     * `startReminderService`. Cancel-then-write, same order as Android's
     * cancelAllAlarms + scheduleAllAlarms, so deleted or renamed courses cannot leave
     * orphans behind.
     */
    fun reschedule(repository: CourseRepository? = null) {
        val repo = repository ?: CourseRepository()
        val preClass = repo.getPreClassReminder()
        val nextDay = repo.getNextDayReminder()
        if (!preClass && !nextDay) {
            cancelAll()
            return
        }
        ensureAuthorization { granted ->
            // Wait for the grant: `add` fails silently while unauthorized.
            if (granted) writeAll(repo, preClass, nextDay)
        }
    }

    fun stop() {
        cancelAll()
    }

    private fun writeAll(repo: CourseRepository, preClass: Boolean, nextDay: Boolean) {
        val pending = mutableListOf<Notice>()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val entriesByYear = HolidayManager.loadAllByYear()

        if (preClass) {
            for (offset in 0 until WINDOW_DAYS) {
                val date = today.plus(DatePeriod(days = offset))
                val yearEntries = entriesByYear[date.year] ?: continue
                val resolution = DayScheduleResolver.resolveDaySchedule(
                    date = date,
                    repository = repo,
                    holidayEntriesByYear = mapOf(date.year to yearEntries),
                )
                pending += preClassNotices(date, resolution, repo)
            }
        }

        if (nextDay) {
            nextDayNotice(today, repo)?.let { pending += it }
        }

        // Keep the nearest ones when over the cap: day 14 can wait for the next pass,
        // unlike the next bell.
        val limited = pending.sortedBy { it.fireEpochMillis }.take(MAX_PENDING)
        NexioLog.i(TAG, "reschedule: ${limited.size} notices (of ${pending.size})")
        if (installed) nativeWrite(limited)
    }

    /** One notice per course, at start minus the configured lead. */
    private fun preClassNotices(
        date: LocalDate,
        resolution: DayScheduleResolver.DayScheduleResolution,
        repo: CourseRepository,
    ): List<Notice> {
        val leadMinutes = repo.getPreClassReminderMinutes()
        val now = Clock.System.now().toEpochMilliseconds()
        val out = mutableListOf<Notice>()
        resolution.courses.forEachIndexed { index, course ->
            val start = CourseTimeResolver.getStartTime(course, repo) ?: return@forEachIndexed
            val startTime = parseClock(start) ?: return@forEachIndexed
            val endTime = CourseTimeResolver.getEndTime(course, repo)?.let { parseClock(it) }
            val startEpoch = date.atTime(startTime).toInstant(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
            val fireAt = startEpoch - leadMinutes * 60_000L
            // A local notification cannot fire in the past; it would only be swept up
            // by the next reconciliation.
            if (fireAt <= now) return@forEachIndexed
            val endText = endTime?.let { formatClock(it) } ?: ""
            val endEpoch = if (endTime != null) {
                val startMin = startTime.hour * 60 + startTime.minute
                val endMin = endTime.hour * 60 + endTime.minute
                if (CourseTimeSpan.crossesMidnight(startMin, endMin)) {
                    date.plus(DatePeriod(days = 1)).atTime(endTime)
                        .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                } else {
                    date.atTime(endTime)
                        .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                }
            } else {
                startEpoch + 45 * 60_000L
            }
            out += Notice(
                id = "$ID_PREFIX_PRE_CLASS.$date.$index",
                fireEpochMillis = fireAt,
                title = if (leadMinutes > 0) {
                    "${course.name} · ${leadMinutes}分钟后上课"
                } else {
                    "${course.name} · 即将上课"
                },
                body = buildString {
                    val section = sectionText(course)
                    if (section.isNotEmpty()) append(section)
                    if (course.classroom.isNotEmpty()) {
                        if (isNotEmpty()) append("｜")
                        append(course.classroom)
                    }
                    if (course.teacher.isNotEmpty()) {
                        if (isNotEmpty()) append("｜")
                        append(course.teacher)
                    }
                    if (endText.isNotEmpty()) {
                        if (isNotEmpty()) append("｜")
                        append("$start-$endText")
                    }
                },
                userInfo = mapOf(
                    KEY_ACTION to ACTION_OPEN_COURSE_LIVE,
                    KEY_COURSE_ID to course.id,
                    KEY_COURSE_NAME to course.name,
                    KEY_CLASSROOM to course.classroom,
                    KEY_SECTION to sectionText(course),
                    KEY_START_TIME to start,
                    KEY_END_TIME to endText,
                    KEY_START_EPOCH to startEpoch.toString(),
                    KEY_END_EPOCH to endEpoch.toString(),
                    KEY_FIRE_EPOCH to fireAt.toString(),
                ),
            )
        }
        return out
    }

    /** Tomorrow's courses in one notice at the configured time. */
    private fun nextDayNotice(today: LocalDate, repo: CourseRepository): Notice? {
        val tomorrow = today.plus(DatePeriod(days = 1))
        val yearEntries = HolidayManager.loadAllByYear()[tomorrow.year] ?: return null
        val resolution = DayScheduleResolver.resolveDaySchedule(
            date = tomorrow,
            repository = repo,
            holidayEntriesByYear = mapOf(tomorrow.year to yearEntries),
        )
        if (resolution.courses.isEmpty()) return null

        val fireAt = tomorrow
            .atTime(LocalTime(repo.getNextDayReminderHour(), repo.getNextDayReminderMinute()))
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        if (fireAt <= Clock.System.now().toEpochMilliseconds()) return null

        val lines = resolution.courses.mapNotNull { course ->
            val start = CourseTimeResolver.getStartTime(course, repo) ?: return@mapNotNull null
            if (course.classroom.isBlank()) {
                "${course.name} $start"
            } else {
                "${course.name} $start · ${course.classroom}"
            }
        }
        return Notice(
            id = "$ID_PREFIX_NEXT_DAY.$tomorrow",
            fireEpochMillis = fireAt,
            title = "明日课程（${resolution.courses.size} 节）",
            body = lines.joinToString("\n"),
            userInfo = mapOf(KEY_ACTION to ACTION_OPEN_APP),
        )
    }

    // MARK: Authorization

    private fun cancelAll() {
        if (installed) nativeCancelAll()
    }

    /**
     * Requests notification permission. Calls back synchronously when already
     * granted; otherwise the system dialog appears. The callback may run on any
     * thread.
     */
    fun ensureAuthorization(onResult: (Boolean) -> Unit) {
        if (authorized) {
            onResult(true)
            return
        }
        if (!installed) {
            onResult(false)
            return
        }
        // The dialog is asynchronous: Swift reports the outcome through
        // [requestAuthorizationFromSwift], so callers must reschedule once it lands.
        onResult(authorized)
        val request = nativeRequestAuthorization
        request()
    }

    fun isAuthorized(): Boolean = authorized

    // MARK: In-class switch

    /**
     * Whether the Live Activity island is enabled, i.e. iOS's "in-class reminder".
     * Shares Android's `in_class_reminder_enabled` key so the switch means the same
     * thing on both platforms, even though the mechanism differs.
     */
    fun isLiveActivityEnabled(): Boolean =
        AppStorage.store(PREF_IN_CLASS).getBoolean(ReminderController.KEY_IN_CLASS, false)

    fun setLiveActivityEnabled(enabled: Boolean) {
        AppStorage.store(PREF_IN_CLASS).edit {
            putBoolean(ReminderController.KEY_IN_CLASS, enabled)
        }
        if (!enabled) IosCourseLiveActivity.endCurrent()
    }

    fun getInClassTimingMode(): Int = AppStorage.store(PREF_IN_CLASS)
        .getInt(ReminderController.KEY_IN_CLASS_TIMING_MODE, ReminderController.IN_CLASS_TIMING_FULL)

    fun getInClassLeadMinutes(): Int = AppStorage.store(PREF_IN_CLASS)
        .getInt(ReminderController.KEY_IN_CLASS_LEAD_MINUTES, DEFAULT_LEAD_MINUTES)

    // MARK: Helpers

    private fun parseClock(value: String): LocalTime? {
        val parts = value.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime(hour, minute)
    }

    private fun formatClock(time: LocalTime): String =
        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

    private fun sectionText(course: Course): String =
        if (course.startSection == course.endSection) {
            "第${course.startSection}节"
        } else {
            "第${course.startSection}~${course.endSection}节"
        }

    private const val TAG = "IosCourseNotifier"
}

/**
 * Injection point for driving the Live Activity.
 *
 * The implementation has to be Swift: ActivityKit is a pure-Swift framework, so
 * cinterop cannot reach it, same as the `UserNotifications` triggers. While null,
 * every call is dropped rather than throwing, meaning the host is not ready yet.
 */
object IosCourseLiveActivity {

    /**
     * Starts or updates the island.
     *
     * @param endEpochMillis the bell time; a day later than [startEpochMillis] when
     *   the course crosses midnight.
     */
    var startOrUpdate: ((
        courseId: String,
        courseName: String,
        classroom: String,
        section: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        leftMode: Int,
        rightMode: Int,
        inClassRightMode: Int,
    ) -> Unit)? = null

    var end: (() -> Unit)? = null

    fun install(
        startOrUpdate: (
            String, String, String, String, Long, Long, Int, Int, Int,
        ) -> Unit,
        end: () -> Unit,
    ) {
        this.startOrUpdate = startOrUpdate
        this.end = end
    }

    fun getLeftMode(): Int =
        AppStorage.store("course_reminder_prefs").getInt("island_left_mode", 0)

    fun getRightMode(): Int =
        AppStorage.store("course_reminder_prefs").getInt("island_right_mode", 2)

    fun getInClassRightMode(): Int =
        AppStorage.store("course_reminder_prefs").getInt("island_in_class_right_mode", 1)

    /** Entry point used on a notification tap or when the app notices class started. */
    fun startFromCourse(
        courseId: String,
        courseName: String,
        classroom: String,
        section: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
    ) {
        startFromCourse(
            courseId,
            courseName,
            classroom,
            section,
            startEpochMillis,
            endEpochMillis,
            getLeftMode(),
            getRightMode(),
            getInClassRightMode(),
        )
    }

    fun startFromCourse(
        courseId: String,
        courseName: String,
        classroom: String,
        section: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        leftMode: Int,
        rightMode: Int,
        inClassRightMode: Int,
    ) {
        startOrUpdate?.invoke(
            courseId,
            courseName,
            classroom,
            section,
            startEpochMillis,
            endEpochMillis,
            leftMode,
            rightMode,
            inClassRightMode,
        )
    }

    fun endCurrent() {
        end?.invoke()
    }

    /** Triggers a 30-second test Live Activity to verify countdown and auto-dismissal. */
    fun startTest() {
        val now = Clock.System.now().toEpochMilliseconds()
        startFromCourse(
            courseId = "test_live_course",
            courseName = "大学英语Ⅱ",
            classroom = "博A201",
            section = "第3~4节",
            startEpochMillis = now + 30_000L,
            endEpochMillis = now + 30_000L,
        )
    }
}
