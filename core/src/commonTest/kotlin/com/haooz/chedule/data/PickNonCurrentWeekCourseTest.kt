package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 「同一节次、本周都没课」时该显示哪一条。
 *
 * 规则是「谁下一次更早会上」而不是「谁 startWeek 更大」—— 后者会把
 * 「1 周就开始、第 10 周还有一次」的课判出局，明明下周就上却显示成下个月才上的那门。
 */
class PickNonCurrentWeekCourseTest {

    @Test
    fun `第9周时优先选择下周就上的那门`() {
        // 1~8 周 + 第 10 周（不连续，靠 selectedWeeks 表达）
        val a = base(
            id = "A",
            startWeek = 1,
            endWeek = 10,
            weeks = (1..8).toList() + 10,
            startSection = 8,
            endSection = 9,
        )
        // 12~15 周
        val b = base(id = "B", startWeek = 12, endWeek = 15, startSection = 8, endSection = 8)

        assertEquals("A", pickNonCurrentWeekCourse(listOf(a, b), 9)?.id)
    }

    @Test
    fun `第11周时改选12周开始的那门`() {
        val a = base(
            id = "A",
            startWeek = 1,
            endWeek = 10,
            weeks = (1..8).toList() + 10,
            startSection = 8,
            endSection = 9,
        )
        val b = base(id = "B", startWeek = 12, endWeek = 15, startSection = 8, endSection = 8)

        assertEquals("B", pickNonCurrentWeekCourse(listOf(a, b), 11)?.id)
    }

    @Test
    fun `全都上完时显示最后结束的那门`() {
        val a = base(id = "A", startWeek = 1, endWeek = 10, startSection = 8, endSection = 9)
        val b = base(id = "B", startWeek = 12, endWeek = 15, startSection = 8, endSection = 8)

        assertEquals("B", pickNonCurrentWeekCourse(listOf(a, b), 16)?.id)
    }

    @Test
    fun `还没开始的课按开始周取最近`() {
        val a = base(id = "A", startWeek = 12, endWeek = 15, startSection = 8, endSection = 8)
        val b = base(id = "B", startWeek = 16, endWeek = 18, startSection = 8, endSection = 9)

        assertEquals("A", pickNonCurrentWeekCourse(listOf(a, b), 3)?.id)
    }

    @Test
    fun `下一周查询兼容单双周`() {
        val odd = base(id = "odd", startWeek = 1, endWeek = 15, weekType = Course.WEEK_TYPE_ODD)
        // 第 8 周（双周）之后，下一次单周是第 9 周
        assertEquals(9, odd.nextActiveWeekAfter(8))
        // 第 9 周之后就是第 11 周
        assertEquals(11, odd.nextActiveWeekAfter(9))
        assertNull(odd.nextActiveWeekAfter(15))
    }

    private fun base(
        id: String,
        startWeek: Int,
        endWeek: Int,
        weeks: List<Int> = emptyList(),
        startSection: Int = 8,
        endSection: Int = 9,
        weekType: Int = Course.WEEK_TYPE_ALL,
    ) = Course(
        id = id,
        name = id,
        classroom = "",
        teacher = "",
        dayOfWeek = 1,
        startSection = startSection,
        endSection = endSection,
        startWeek = startWeek,
        endWeek = endWeek,
        weekType = weekType,
        colorRes = 0L,
        selectedWeeks = weeks,
    )
}
