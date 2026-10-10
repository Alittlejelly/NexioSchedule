package com.haooz.chedule.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * 某日课表解析（节假日 / 调休映射 / 末日或假期前节次排除）—— **平台中立的纯计算**。
 *
 * ## 从哪来
 *
 * 从 `:androidApp` 的 `reminder/CourseReminderHelper` 抠出来的「提醒时刻算法」核心。
 * 那边的 `resolveDaySchedule(context, …)` 重载**函数体根本没用 context**
 * （它只读 `HolidayManager` 与 `CourseRepository`），`Context` 是历史残留参数。
 *
 * ## 为什么抠出来
 *
 * 1. 计划里早就标记「`CourseReminderHelper` 的提醒时刻算法值得抠进 `:core`
 *    （iOS 本地通知要用）」—— iOS 没有 `AlarmManager`，但**算某天有什么课**两边都要。
 * 2. 它是 `SettingsViewModel` / `ScheduleViewModel` **唯一的 Android 依赖来源**：
 *    两个 VM 为了拿 `Context` 去调它，才被迫继承 `AndroidViewModel(application)`，
 *    于是引用这两个 VM 的 19 个页面全部卡在 `:androidApp`。抠出来即可解开。
 *
 * ## ⚠ 搬家原则
 *
 * **逐字搬，行为一位都不能改。** `CourseReminderHelper` 改成委托到本对象，
 * 20 个既有调用点一行不用动。任何「顺手优化」都会让提醒排课静默算错 ——
 * 提醒算错的代价是用户上课没收到通知，且极难复现。
 */
object DayScheduleResolver {

    /**
     * 某日课表解析结果。
     *
     * 小组件 / ContentProvider / 今日页 / 次日提醒共用。
     * 节假日默认空课，末日例外只返回命中课程；调休按 `followWeekday` / `followWeek` 映射。
     */
    data class DayScheduleResolution(
        val courses: List<Course>,
        /** 实际用于查课的星期（调休映射后） */
        val displayDayOfWeek: Int,
        /** 实际用于查课的周次 */
        val displayWeek: Int,
        /** 日历上的星期（未映射） */
        val calendarDayOfWeek: Int,
        /** 目标日是否为节假日 */
        val isHolidayDate: Boolean,
        /** 目标日是否配置了调休映射 */
        val isWorkSwap: Boolean,
        /** 目标日是节假日末日，且配置启用了节次课程排除 */
        val isHolidayEndCourseExclusionActive: Boolean = false,
        /** 目标日是假期前一天，且配置启用了节次课程排除 */
        val isHolidayBeforeCourseExclusionActive: Boolean = false,
    )

    /**
     * 与主课表「当前周」对齐的目标日周次。
     *
     * The semester start encodes a manual week adjustment, while a saved current_week
     * does not advance when the app remains closed across a week boundary.
     * 调休 `followWeek` 仍由调用方优先覆盖。
     */
    fun alignedStoredWeekForDate(repository: CourseRepository, date: LocalDate): Int =
        repository.getLiveTeachingWeek(date)

    internal fun liveTeachingWeek(
        semesterStartDate: LocalDate,
        date: LocalDate,
        rules: List<TeachingWeekReorganizationRule>,
    ): Int = TeachingWeekReorganization.mapDate(semesterStartDate, date, rules)
        .week.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
        .toInt()

    /** 智能周末：某周里真正有课的周六 / 周日（6=周六，7=周日）。 */
    fun effectiveWeekendDays(
        semesterStartDate: LocalDate,
        week: Int,
        rules: List<TeachingWeekReorganizationRule>,
        hasCoursesOnDate: (LocalDate) -> Boolean,
    ): Set<Int> = (6..7).filterTo(mutableSetOf()) { weekday ->
        TeachingWeekReorganization
            .dateForPosition(semesterStartDate, week, weekday, rules)
            ?.let(hasCoursesOnDate) == true
    }

    fun canResolveCourseCandidates(
        datePosition: TeachingWeekPosition,
        workSwapEntry: HolidayEntry?,
        displayWeek: Int,
        totalWeeks: Int,
        lastWeekWithCourses: Int,
    ): Boolean {
        val hasTeachingPosition = !datePosition.isReorganizationPause && datePosition.weekday != null
        val explicitlyMappedPause = workSwapEntry?.let {
            it.type == HolidayManager.TYPE_WORKSWAP && it.hasFollowMapping()
        } == true
        return (hasTeachingPosition || explicitlyMappedPause) &&
            displayWeek in 1..totalWeeks && displayWeek <= lastWeekWithCourses
    }

    /**
     * 解析目标日的课表。
     *
     * @param holidayEntriesByYear 预先按年缓存的节假日条目，批量计算时传入避免重复读；
     *   为 null 时内部现读 `HolidayManager.loadAllByYear()`。
     */
    fun resolveDaySchedule(
        date: LocalDate,
        repository: CourseRepository,
        holidayEntriesByYear: Map<Int, List<HolidayEntry>>? = null,
    ): DayScheduleResolution {
        val calendarDay = date.dayOfWeek.isoDayNumber
        val entriesByYear = holidayEntriesByYear ?: HolidayManager.loadAllByYear()
        val holidayEntries = HolidayManager.entriesForDate(entriesByYear, date)
        val targetEntry = holidayEntries.firstOrNull { it.type == HolidayManager.TYPE_WORKSWAP }
        val datePosition = repository.teachingWeekPositionForDate(date)
        // 跟随绝对日期 → 按当前课表换算成 (周次, 星期)；换课表自动跟随那一周的课
        val follow = targetEntry?.let { repository.resolveWorkSwapFollow(it) }
        val isWorkSwap = follow != null
        val displayDay = follow?.weekday
            ?: datePosition.weekday ?: calendarDay
        // 未配置调休覆盖时，用教学周映射相对今天的偏移，并避免今日调休跟随周次污染。
        val displayWeek = follow?.week
            ?: alignedStoredWeekForDate(repository, date)
        val exclusion = HolidayManager.loadEndCourseExclusion()
        val beforeExclusion = HolidayManager.loadBeforeCourseExclusion()
        return resolveDaySchedule(
            date = date,
            entriesByYear = entriesByYear,
            exclusion = exclusion,
            beforeExclusion = beforeExclusion,
            displayDayOfWeek = displayDay,
            displayWeek = displayWeek,
            calendarDayOfWeek = calendarDay,
            isWorkSwap = isWorkSwap,
            candidates = {
                val totalWeeks = repository.getTotalWeeks()
                val lastWeekWithCourses = repository.getLastWeekWithCourses()
                if (!canResolveCourseCandidates(
                        datePosition = datePosition,
                        workSwapEntry = targetEntry,
                        displayWeek = displayWeek,
                        totalWeeks = totalWeeks,
                        lastWeekWithCourses = lastWeekWithCourses,
                    )
                ) {
                    emptyList()
                } else {
                    repository.getAllCourses()
                        .filter { it.dayOfWeek == displayDay && it.isActiveInWeek(displayWeek) }
                        .sortedBy { getCourseStartTime(it, repository).toMinutes() }
                }
            },
            sectionTimes = repository::getCurrentSectionTimes,
            sectionCount = {
                repository.getMorningSections() +
                    repository.getAfternoonSections() + repository.getEveningSections()
            },
        )
    }

    fun resolveDaySchedule(
        date: LocalDate,
        entriesByYear: Map<Int, List<HolidayEntry>>,
        exclusion: HolidayEndCourseExclusion,
        displayDayOfWeek: Int,
        displayWeek: Int,
        calendarDayOfWeek: Int,
        isWorkSwap: Boolean,
        candidates: () -> List<Course>,
        sectionTimes: () -> Map<Int, String>,
        sectionCount: () -> Int,
        beforeExclusion: HolidayBeforeCourseExclusion = HolidayBeforeCourseExclusion(),
    ): DayScheduleResolution {
        val dayResolution = HolidayCourseExclusion.resolveDayCourses(
            entriesByYear = entriesByYear,
            date = date,
            exclusion = exclusion,
            candidates = candidates,
            sectionTimes = sectionTimes,
            sectionCount = sectionCount,
            beforeExclusion = beforeExclusion,
        )
        return DayScheduleResolution(
            courses = dayResolution.courses,
            displayDayOfWeek = displayDayOfWeek,
            displayWeek = displayWeek,
            calendarDayOfWeek = calendarDayOfWeek,
            isHolidayDate = dayResolution.isHolidayDate,
            isWorkSwap = isWorkSwap,
            isHolidayEndCourseExclusionActive = dayResolution.isHolidayEndCourseExclusionActive,
            isHolidayBeforeCourseExclusionActive =
                dayResolution.isHolidayBeforeCourseExclusionActive,
        )
    }

    private fun getCourseStartTime(course: Course, repository: CourseRepository): String? =
        CourseTimeResolver.getStartTime(course, repository)

    /**
     * 逐字搬自 `CourseReminderHelper`（那边是 private，且同样的实现在 5 个文件里各抄了一份）。
     * 解析不出来时返回 `Int.MAX_VALUE` —— **不是 0**：那会把没时间的课排到最前面。
     */
    private fun String?.toMinutes(): Int {
        if (this.isNullOrBlank()) return Int.MAX_VALUE
        val parts = this.split(":")
        if (parts.size != 2) return Int.MAX_VALUE
        val h = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
        val m = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
        return h * 60 + m
    }
}
