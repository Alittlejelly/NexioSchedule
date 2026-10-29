package com.haooz.chedule.data

/**
 * 课程节次→时间字符串的唯一解析器。
 *
 * 之前这段逻辑（自定义时间优先 / 上午下午晚间归属 / 时段映射）在 `CourseReminderHelper`
 * 与 4 个 widget provider 里各有一份，改一处极易漏改，正是"查课时间不对"类 bug 的温床。
 * 现在统一调用这里。
 *
 * 返回值：节次对应的 "HH:mm"，非法节次返回 null。
 */
object CourseTimeResolver {

    /** 课程开始时间（如 "08:00"）；自定义时间优先 */
    fun getStartTime(course: Course, repository: PeriodTimeSource): String? {
        if (course.hasValidCustomTime()) return course.customStartTime
        return resolveSectionTime(course.startSection, repository)
    }

    /** 课程结束时间（如 "08:45"）；自定义时间优先 */
    fun getEndTime(course: Course, repository: PeriodTimeSource): String? {
        if (course.hasValidCustomTime()) return course.customEndTime
        return resolveSectionTime(course.endSection, repository, isEnd = true)
    }

    private fun resolveSectionTime(
        section: Int,
        repository: PeriodTimeSource,
        isEnd: Boolean = false
    ): String? {
        if (section <= 0) return null
        // 一次取到全局节次表（实现方有缓存），不要再按三个时段各取一遍——
        // 解一门课的起止时间原本要走 6 次 getPeriodTimes，每一步都要重算生效作息
        val range = repository.getSectionTimes()[section] ?: return null
        // 同一节次对应 "HH:mm-HH:mm"，起始/结束按 isEnd 取左/右
        return if (isEnd) {
            range.substringAfter("-", range).trim().ifEmpty { null }
        } else {
            range.substringBefore("-").trim().ifEmpty { null }
        }
    }
}
