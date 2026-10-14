package com.haooz.chedule.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [HolidayEntries.forFlatPush] 的对齐测试。
 *
 * 背景：小米穿戴 rpk 只能线性扫数组取**第一条**命中，没有 `custom` / 年份优先级概念。
 * 所以手机端必须在下发前把优先级烘焙进数组顺序 —— 否则自定义调休 / 当年条目会被
 * 更早年份的同名条目盖掉，手表换课直接跟错。
 *
 * 本测试的核心断言不是「排序结果长什么样」，而是更强的性质：
 * **对窗口内任意一天，forFlatPush 线性扫到的第一条 == entriesForDate 的首条。**
 * 这一条成立，两端在任意日期上的选课结果就必然一致。
 */
class HolidayEntriesForFlatPushTest {

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

    private fun workSwap(date: String, followDate: String, custom: Boolean = false) = HolidayEntry(
        date = date,
        name = "补班",
        type = HolidayEntry.TYPE_WORKSWAP,
        followDate = followDate,
        custom = custom,
    )

    /** 模拟对端行为：数组顺序第一条命中即采用。 */
    private fun firstHit(
        pushed: List<HolidayEntry>,
        date: LocalDate,
    ): HolidayEntry? = pushed.firstOrNull { it.matches(date.toString()) }

    /** 本测试的主断言：对窗口内逐日核对两端选中的条目一致。 */
    private fun assertAgreesWithPhone(
        entriesByYear: Map<Int, List<HolidayEntry>>,
        from: LocalDate = LocalDate(2026, 9, 20),
        to: LocalDate = LocalDate(2027, 3, 20),
    ): List<HolidayEntry> {
        val pushed = HolidayEntries.forFlatPush(entriesByYear, today, windowFirst, windowLast)
        var cursor = from
        while (cursor <= to) {
            val phone = HolidayEntries.entriesForDate(entriesByYear, cursor).firstOrNull()
            val band = firstHit(pushed, cursor)
            assertEquals(
                phone?.let { it.date to it.name },
                band?.let { it.date to it.name },
                "两端在 $cursor 选到了不同条目",
            )
            cursor = cursor.plusDays(1)
        }
        return pushed
    }

    @Test
    fun 自定义条目盖过内置条目() {
        // 内置国庆 10/01-10/07，用户自己把 10/03-10/05 改成补班
        val entries = mapOf(
            2026 to listOf(
                holiday("2026-10-01", "2026-10-07", name = "国庆节"),
                workSwap("2026-10-03", "2026-09-27", custom = true),
            )
        )

        val pushed = assertAgreesWithPhone(entries)

        // 10/03 必须命中自定义补班，而不是被内置假期吃掉
        assertEquals("补班", firstHit(pushed, LocalDate(2026, 10, 3))?.name)
        // 10/01 仍走内置假期
        assertEquals("国庆节", firstHit(pushed, LocalDate(2026, 10, 1))?.name)
    }

    @Test
    fun 当年条目盖过年份更早的同名条目() {
        // 跨年学期：2025 与 2026 都有同名国庆，2026 才是本学期该用的
        val entries = mapOf(
            2025 to listOf(holiday("2025-10-01", "2025-10-07", name = "国庆节")),
            2026 to listOf(holiday("2026-10-01", "2026-10-07", name = "国庆节")),
        )

        val pushed = assertAgreesWithPhone(entries)

        assertEquals("2026-10-01", firstHit(pushed, LocalDate(2026, 10, 1))?.date)
        // 窗口外的 2025 条目压根不该下发
        assertTrue(pushed.none { it.date.startsWith("2025") })
    }

    @Test
    fun 完全重复的条目只保留优先级最高的那条() {
        val entries = mapOf(
            2026 to listOf(
                workSwap("2026-10-10", "2026-10-12", custom = false),
                workSwap("2026-10-10", "2026-09-28", custom = true),
            )
        )

        val pushed = assertAgreesWithPhone(entries)

        assertEquals(1, pushed.size)
        assertEquals("2026-09-28", pushed.single().followDate)
    }

    @Test
    fun 窗口外的条目被丢弃() {
        val entries = mapOf(
            2024 to listOf(holiday("2024-10-01", "2024-10-07", name = "国庆节")),
            2026 to listOf(holiday("2026-10-01", "2026-10-07", name = "国庆节")),
        )

        val pushed = HolidayEntries.forFlatPush(entries, today, windowFirst, windowLast)

        assertEquals(1, pushed.size)
        assertEquals("2026-10-01", pushed.single().date)
    }

    @Test
    fun endDate为空按单日处理() {
        val entries = mapOf(2026 to listOf(holiday("2026-10-01", name = "单日假期")))

        val pushed = assertAgreesWithPhone(entries)

        assertEquals("单日假期", firstHit(pushed, LocalDate(2026, 10, 1))?.name)
        // 没写 endDate 就不该溢出到第二天
        assertEquals(null, firstHit(pushed, LocalDate(2026, 10, 2)))
    }

    @Test
    fun 窗口首尾颠倒时返回空表() {
        val entries = mapOf(2026 to listOf(holiday("2026-10-01", name = "国庆节")))

        val pushed = HolidayEntries.forFlatPush(entries, today, windowLast, windowFirst)

        assertTrue(pushed.isEmpty())
    }

    @Test
    fun 同年同优先级且区间重叠时保持存储顺序() {
        // 中秋（10/05-10/06）落在国庆（10/01-10/07）区间里，两者同为内置（custom=false）。
        // 存储顺序是 [中秋, 国庆]，手机端 entriesForDate 靠稳定排序保留它 —— 10/06 应命中中秋。
        // 一旦按 start 排序，国庆(10/01) 会排到中秋(10/05) 前面，手表就换错课了。
        val entries = mapOf(
            2026 to listOf(
                holiday("2026-10-05", "2026-10-06", name = "中秋节"),
                holiday("2026-10-01", "2026-10-07", name = "国庆节"),
            )
        )

        val pushed = assertAgreesWithPhone(entries)

        assertEquals("中秋节", firstHit(pushed, LocalDate(2026, 10, 6))?.name)
    }

    @Test
    fun 同年同custom的完全重复条目按存储顺序取首条() {
        val entries = mapOf(
            2026 to listOf(
                holiday("2026-10-01", "2026-10-03", name = "先建的"),
                holiday("2026-10-01", "2026-10-03", name = "后建的"),
            )
        )

        val pushed = assertAgreesWithPhone(entries)

        // 存储顺序优先于区间大小；手机端取「先建的」
        assertEquals("先建的", firstHit(pushed, LocalDate(2026, 10, 2))?.name)
    }

    @Test
    fun 调休按followDate换课且两端一致() {
        val entries = mapOf(
            2026 to listOf(
                workSwap("2026-10-10", "2026-10-12"),
                holiday("2026-10-01", "2026-10-07", name = "国庆节"),
            )
        )

        val pushed = assertAgreesWithPhone(entries)

        val swap = firstHit(pushed, LocalDate(2026, 10, 10))
        assertEquals("2026-10-12", swap?.followDate)
    }
}