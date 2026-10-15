package com.haooz.chedule.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [HolidayEntries.forFlatPush] 与**手环端真实算法**的对拍。
 *
 * 与 [HolidayEntriesForFlatPushTest] 的区别：那份按「数组首条命中」建模，而手环
 * `src/common/schedule.js` 实际是两条独立扫描：
 *
 * - `findHolidayEntry(date, 0)`：只扫 type=0，第一条命中即采用
 * - `findWorkSwap(date)`：只扫 type=1，**优先返回配了 follow 映射的那条**，都没有才用第一条
 * - `resolveDisplayDayKey`：`if (findHolidayEntry(date, 0)) return -1` —— **假期无条件压倒调休**
 *
 * 手机端同款口径（`HolidayCourseExclusion.resolveDayCourses`）：
 * `isHolidayDate = entriesForDate(date).any { type == TYPE_HOLIDAY }` → 有假期就空课表；
 * 调休取 `entriesForDate(date).firstOrNull { type == TYPE_WORKSWAP }`。
 *
 * 所以真正的对齐条件是两条：
 * 1. **假期判定**：手机端「有没有 type=0 命中」== 手环端「有没有 type=0 命中」（与顺序无关）
 * 2. **调休选取**：手机端首条 type=1 == 手环端 `findWorkSwap` 的结果
 */
class HolidayEntriesForWatchBandTest {

    private val today = LocalDate(2026, 10, 1)
    private val windowFirst = LocalDate(2026, 1, 1)
    private val windowLast = LocalDate(2027, 12, 31)

    private fun holiday(
        date: String,
        endDate: String = "",
        name: String = "假期",
        custom: Boolean = false,
    ) = HolidayEntry(
        date = date,
        endDate = endDate,
        name = name,
        type = HolidayEntry.TYPE_HOLIDAY,
        custom = custom,
    )

    private fun workSwap(
        date: String,
        followDate: String = "",
        endDate: String = "",
        custom: Boolean = false,
    ) = HolidayEntry(
        date = date,
        endDate = endDate,
        name = "补班",
        type = HolidayEntry.TYPE_WORKSWAP,
        followDate = followDate,
        custom = custom,
    )

    /** 手机端口径：当天是否算假期（有任意 type=0 命中即算）。 */
    private fun phoneIsHoliday(entries: Map<Int, List<HolidayEntry>>, date: LocalDate): Boolean =
        HolidayEntries.entriesForDate(entries, date).any { it.type == HolidayEntry.TYPE_HOLIDAY }

    /** 手环端口径：下发数组里是否还有 type=0 命中（`findHolidayEntry(date, 0) != null`）。 */
    private fun bandIsHoliday(pushed: List<HolidayEntry>, date: LocalDate): Boolean =
        pushed.any { it.type == HolidayEntry.TYPE_HOLIDAY && it.matches(date.toString()) }

    /** 手机端口径：调休取首条 type=1。 */
    private fun phoneWorkSwap(
        entries: Map<Int, List<HolidayEntry>>,
        date: LocalDate,
    ): HolidayEntry? =
        HolidayEntries.entriesForDate(entries, date).firstOrNull { it.type == HolidayEntry.TYPE_WORKSWAP }

    /** 手环端 findWorkSwap：首条 type=1 命中中，优先配了映射的；都没有才用第一条。 */
    private fun bandWorkSwap(pushed: List<HolidayEntry>, date: LocalDate): HolidayEntry? {
        val key = date.toString()
        var first: HolidayEntry? = null
        for (h in pushed) {
            if (h.type != HolidayEntry.TYPE_WORKSWAP) continue
            if (!h.matches(key)) continue
            if (first == null) first = h
            if (h.hasFollowMapping()) return h
        }
        return first
    }

    private fun assertHolidayAgrees(entries: Map<Int, List<HolidayEntry>>, date: LocalDate) {
        val pushed = HolidayEntries.forFlatPush(entries, today, windowFirst, windowLast)
        assertEquals(
            phoneIsHoliday(entries, date),
            bandIsHoliday(pushed, date),
            "两端在 $date 的「是否假期」判定不一致",
        )
    }

    private fun assertWorkSwapAgrees(entries: Map<Int, List<HolidayEntry>>, date: LocalDate) {
        val pushed = HolidayEntries.forFlatPush(entries, today, windowFirst, windowLast)
        assertEquals(
            phoneWorkSwap(entries, date)?.date,
            bandWorkSwap(pushed, date)?.date,
            "两端在 $date 选中的调休条目不一致",
        )
    }

    @Test
    fun 自定义调休整段覆盖内置假期时不能把假期吃掉() {
        // 用户在国庆整段上自建了一条 custom 补班（区间完全相同）
        val entries = mapOf(
            2026 to listOf(
                holiday("2026-10-01", "2026-10-07", name = "国庆节"),
                workSwap("2026-10-01", "2026-10-12", endDate = "2026-10-07", custom = true),
            )
        )
        // custom 降序 → 补班排在前面 → 假期被判定为「被完全遮住」而丢弃
        assertHolidayAgrees(entries, LocalDate(2026, 10, 3))
    }

    @Test
    fun 自定义调休部分覆盖假期时假期仍在() {
        val entries = mapOf(
            2026 to listOf(
                holiday("2026-10-01", "2026-10-07", name = "国庆节"),
                workSwap("2026-10-03", "2026-10-05", custom = true),
            )
        )
        assertHolidayAgrees(entries, LocalDate(2026, 10, 3))
        assertHolidayAgrees(entries, LocalDate(2026, 10, 5))
    }

    /**
     * 跨年窗口：一条包要服务所有日期，而手机端的年份优先级是**查询日所在年**优先，
     * 所以单一数组顺序无法对所有日期都复刻手机端首条 —— 这是协议层的固有限制。
     *
     * 已加固的部分：条目**不再被剔除**（两端「是否假期」的判定必须一致，见上一条用例）。
     * 残留差异只影响 `holidayNameFor` 的显示名（同为假期 → 都是空课表，课程结果一致）。
     * 要彻底修得让手环端按年份分桶取，或手机端按天下发，属协议改动，不在本次范围。
     */
    @Test
    fun 跨年窗口内条目不得被剔除() {
        val decToday = LocalDate(2026, 12, 1)
        val entries = mapOf(
            2026 to listOf(holiday("2026-12-20", "2027-02-10", name = "寒假")),
            2027 to listOf(holiday("2027-01-01", "2027-01-03", name = "元旦")),
        )
        val pushed = HolidayEntries.forFlatPush(
            entries, decToday, LocalDate(2026, 6, 1), LocalDate(2027, 6, 1),
        )
        // 两条都得在包里：删掉任何一条都会让某一年的日期在两端判定不一致
        assertEquals(2, pushed.size, "跨年条目不能被当作冗余剔除")
        // 两端对任意日期的「是否假期」必须一致
        for (date in listOf(LocalDate(2026, 12, 25), LocalDate(2027, 1, 1), LocalDate(2027, 1, 2))) {
            assertEquals(
                HolidayEntries.entriesForDate(entries, date)
                    .any { it.type == HolidayEntry.TYPE_HOLIDAY },
                pushed.any { it.type == HolidayEntry.TYPE_HOLIDAY && it.matches(date.toString()) },
                "两端在 $date 的「是否假期」判定不一致",
            )
        }
    }

    @Test
    fun 早年调休整段包住次年假期时不能把假期吃掉() {
        // 2026 的寒假调休跨到 2027，2027 当年还有一条元旦假期
        val decToday = LocalDate(2026, 12, 1)
        val entries = mapOf(
            2026 to listOf(workSwap("2026-12-20", "2027-01-05", endDate = "2027-02-10")),
            2027 to listOf(holiday("2027-01-01", "2027-01-03", name = "元旦")),
        )
        val pushed = HolidayEntries.forFlatPush(
            entries, decToday, LocalDate(2026, 6, 1), LocalDate(2027, 6, 1),
        )
        val date = LocalDate(2027, 1, 2)
        assertEquals(
            HolidayEntries.entriesForDate(entries, date).any { it.type == HolidayEntry.TYPE_HOLIDAY },
            pushed.any { it.type == HolidayEntry.TYPE_HOLIDAY && it.matches(date.toString()) },
            "两端在 $date 的「是否假期」判定不一致",
        )
    }

    @Test
    fun 未配映射的调休排在前面时两端仍一致() {
        // 手环 findWorkSwap 会跳过没配 follow 的条目找后面配了的；
        // 手机端只取首条 type=1，不要求它配了映射
        val entries = mapOf(
            2026 to listOf(
                workSwap("2026-10-10", followDate = "", custom = true),
                workSwap("2026-10-10", followDate = "2026-10-12", custom = false),
            )
        )
        assertWorkSwapAgrees(entries, LocalDate(2026, 10, 10))
    }

    @Test
    fun 常规场景保持原行为不回归() {
        val entries = mapOf(
            2025 to listOf(holiday("2025-10-01", "2025-10-07", name = "旧国庆")),
            2026 to listOf(
                holiday("2026-10-01", "2026-10-07", name = "国庆节"),
                workSwap("2026-10-10", "2026-10-12", custom = true),
            ),
        )
        val pushed = HolidayEntries.forFlatPush(entries, today, windowFirst, windowLast)
        // 2025 的存量不该进包
        assertEquals(2, pushed.size, "窗口外/冗余条目应被剔除")
        assertHolidayAgrees(entries, LocalDate(2026, 10, 1))
        assertWorkSwapAgrees(entries, LocalDate(2026, 10, 10))
    }
}
