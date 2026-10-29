package com.haooz.chedule.data

/**
 * 节次→时间解析所需的最小能力集。
 *
 * `CourseTimeResolver` 原本直接把 `CourseRepository` 当参数类型，而它是强 Android 依赖
 * （构造要 `Context`、内部全是 SharedPreferences），跟着进 commonMain 会直接阻断 iOS 目标。
 * 这里只把它真正用到的能力抽成接口：Android 侧由 `CourseRepository` 实现（签名本来就
 * 完全一致，不需要改实现），iOS / 桌面侧将来各自提供一个 actual 实现即可。
 *
 * 调用方（CourseReminderHelper 与 4 个 widget）传的还是 CourseRepository 实例，
 * 向上转型自动完成，调用点无需改动。
 */
interface PeriodTimeSource {
    /** period 取 "morning" / "afternoon" / "evening"；返回 节次 → "HH:mm-HH:mm" */
    fun getPeriodTimes(period: String): Map<Int, String>

    fun getMorningSections(): Int

    fun getAfternoonSections(): Int

    /**
     * 全局节次（1..总节数）→ "HH:mm-HH:mm"。
     *
     * 默认实现把三个时段拼起来；[CourseTimeResolver] 解一门课的时间只查一次这张表，
     * 有现成缓存的实现（[CourseRepository]）可以覆盖掉这三次读取。
     */
    fun getSectionTimes(): Map<Int, String> {
        val morning = getPeriodTimes("morning")
        val afternoon = getPeriodTimes("afternoon")
        val evening = getPeriodTimes("evening")
        val morningSections = getMorningSections()
        val afternoonSections = getAfternoonSections()
        return buildMap {
            morning.forEach { (index, time) -> put(index, time) }
            afternoon.forEach { (index, time) -> put(morningSections + index, time) }
            evening.forEach { (index, time) -> put(morningSections + afternoonSections + index, time) }
        }
    }
}
