package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 跨午夜课程时间段（issue #76：`23:00 - 00:30`）的语义回归。
 *
 * 约定：结束不晚于开始即视为**次日**结束；起止相同按满 24 小时处理。
 * 存盘格式不变（仍是 `"HH:mm"` 两字段），所以老数据与导入导出完全不受影响。
 */
class CrossMidnightTimeTest {

    private fun hm(s: String): Int {
        val parts = s.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    @Test
    fun `23-00 30 视为跨午夜且时长 90 分钟`() {
        assertTrue(CourseTimeSpan.crossesMidnight(hm("23:00"), hm("00:30")))
        assertEquals(90, CourseTimeSpan.minutesUntilEnd(hm("23:00"), hm("00:30")))
        // 结束落在次日的分钟偏移 > 1440
        assertEquals(1470, CourseTimeSpan.endOffsetMinutes(hm("23:00"), hm("00:30")))
    }

    @Test
    fun `普通时段不跨午夜`() {
        assertFalse(CourseTimeSpan.crossesMidnight(hm("08:00"), hm("08:45")))
        assertEquals(45, CourseTimeSpan.minutesUntilEnd(hm("08:00"), hm("08:45")))
        assertEquals(525, CourseTimeSpan.endOffsetMinutes(hm("08:00"), hm("08:45")))
    }

    @Test
    fun `起止相同按满 24 小时处理`() {
        // 相等不是有效输入（UI 会拦），但真脏数据进来也不能算出 0 长的课
        assertTrue(CourseTimeSpan.crossesMidnight(hm("08:00"), hm("08:00")))
        assertEquals(1440, CourseTimeSpan.minutesUntilEnd(hm("08:00"), hm("08:00")))
    }

    @Test
    fun `正在上课判定只认跨午夜课的今晚这一段`() {
        val start = hm("23:00")
        val end = hm("00:30")
        assertTrue(CourseTimeSpan.isActiveAt(start, end, hm("23:30")))
        assertTrue(CourseTimeSpan.isActiveAt(start, end, hm("23:59")))
        // 0 点后的尾巴属于昨天那节课，今日课表里没有它
        assertFalse(CourseTimeSpan.isActiveAt(start, end, hm("00:10")))
        assertFalse(CourseTimeSpan.isActiveAt(start, end, hm("22:59")))

        // 普通时段保持半开区间语义
        assertTrue(CourseTimeSpan.isActiveAt(hm("08:00"), hm("09:40"), hm("08:30")))
        assertFalse(CourseTimeSpan.isActiveAt(hm("08:00"), hm("09:40"), hm("09:40")))

        // 起止相同（脏数据）按非进行中处理
        assertFalse(CourseTimeSpan.isActiveAt(hm("08:00"), hm("08:00"), hm("09:00")))
    }

    @Test
    fun `跨午夜课与次日零点的课判出冲突`() {
        // 这是本次修复的关键：旧逻辑 newStart < existingEnd 直接比较，
        // 23:00 与 00:30 永不相交 → 允许同时添加，实际会撞车
        assertTrue(
            CourseTimeSpan.overlaps(
                hm("23:00"), hm("00:30"),
                hm("00:00"), hm("01:00"),
            )
        )
        assertTrue(
            CourseTimeSpan.overlaps(
                hm("00:00"), hm("01:00"),
                hm("23:00"), hm("00:30"),
            )
        )
    }

    @Test
    fun `相邻不算冲突`() {
        assertFalse(CourseTimeSpan.overlaps(hm("08:00"), hm("08:45"), hm("08:45"), hm("09:30")))
        // 跨午夜的边界：23:00-00:30 与次日 00:30 开始的那节
        assertFalse(CourseTimeSpan.overlaps(hm("23:00"), hm("00:30"), hm("00:30"), hm("01:00")))
    }

    @Test
    fun `互不重叠的跨午夜课不误报`() {
        assertFalse(CourseTimeSpan.overlaps(hm("23:00"), hm("00:30"), hm("20:00"), hm("21:00")))
        assertFalse(CourseTimeSpan.overlaps(hm("23:00"), hm("00:30"), hm("02:00"), hm("03:00")))
        // 同为跨午夜但不重叠
        assertFalse(CourseTimeSpan.overlaps(hm("22:00"), hm("23:00"), hm("23:30"), hm("00:30")))
    }

    @Test
    fun `课程模型派生的跨午夜判断与时长`() {
        val course = Course(
            id = "1", name = "夜间实验", classroom = "A101", teacher = "",
            dayOfWeek = 1, startSection = 0, endSection = 0,
            startWeek = 1, endWeek = 1, weekType = 0, colorRes = 0,
            isCustomTime = true, customStartTime = "23:00", customEndTime = "00:30",
        )
        assertTrue(course.hasValidCustomTime())
        assertTrue(course.crossesMidnightCustomTime())
        assertEquals(90, course.customDurationMinutes())
        assertEquals("23:00 - 次日00:30", course.getTimeDisplayText())
    }

    @Test
    fun `非跨午夜课的显示与时长保持原样`() {
        val course = Course(
            id = "1", name = "高数", classroom = "A101", teacher = "",
            dayOfWeek = 1, startSection = 1, endSection = 2,
            startWeek = 1, endWeek = 1, weekType = 0, colorRes = 0,
            isCustomTime = true, customStartTime = "08:00", customEndTime = "08:45",
        )
        assertFalse(course.crossesMidnightCustomTime())
        assertEquals(45, course.customDurationMinutes())
        assertEquals("08:00 - 08:45", course.getTimeDisplayText())
    }

    @Test
    fun `非自定义时间课没有自定义时长`() {
        val course = Course(
            id = "1", name = "高数", classroom = "", teacher = "",
            dayOfWeek = 1, startSection = 1, endSection = 2,
            startWeek = 1, endWeek = 1, weekType = 0, colorRes = 0,
        )
        assertNull(course.customDurationMinutes())
        assertFalse(course.crossesMidnightCustomTime())
    }

    @Test
    fun `毫秒差按需处理跨午夜`() {
        val late = kotlinx.datetime.LocalTime(23, 0)
        val early = kotlinx.datetime.LocalTime(0, 30)
        // 默认不跨午夜：负数（下游按「已结束」处理）。23:00 → 00:30 是 -22.5 小时
        assertEquals(-(22L * 60L * 60L + 30L * 60L) * 1000L, millisBetween(late, early))
        // 显式开启跨午夜：-22.5h + 24h = 1.5 小时（跨午夜到次日 00:30）
        assertEquals(90L * 60L * 1000L, millisBetween(late, early, allowRollover = true))
    }

    @Test
    fun `跨午夜课程能通过编解码往返`() {
        val course = Course(
            id = "c1", name = "夜间实验", classroom = "A101", teacher = "张",
            dayOfWeek = 3, startSection = 0, endSection = 0,
            startWeek = 1, endWeek = 8, weekType = 0, colorRes = 0xFF4CAF50L,
            isCustomTime = true, customStartTime = "23:00", customEndTime = "00:30",
        )
        val json = encodeCourses(listOf(course))
        val restored = decodeCourses(json).single()
        assertEquals("23:00", restored.customStartTime)
        assertEquals("00:30", restored.customEndTime)
        assertTrue(restored.hasValidCustomTime())
        assertTrue(restored.crossesMidnightCustomTime())
        assertEquals(90, restored.customDurationMinutes())
    }

    @Test
    fun `进行中判定_跨午夜课只认当天晚上段`() {
        val start = kotlinx.datetime.LocalTime(23, 0)
        val end = kotlinx.datetime.LocalTime(0, 30)
        // 当天晚上的 [start, 午夜) 段：进行中
        assertTrue(coursePeriodOngoing(start, end, kotlinx.datetime.LocalTime(23, 0)))
        assertTrue(coursePeriodOngoing(start, end, kotlinx.datetime.LocalTime(23, 50)))
        // 凌晨 [0, end) 段属于「前一天」的那一节；当天列表在零点已换日，不能再认领
        assertFalse(coursePeriodOngoing(start, end, kotlinx.datetime.LocalTime(0, 10)))
        assertFalse(coursePeriodOngoing(start, end, kotlinx.datetime.LocalTime(22, 59)))
    }

    @Test
    fun `进行中判定_普通课与边界`() {
        val start = kotlinx.datetime.LocalTime(8, 0)
        val end = kotlinx.datetime.LocalTime(8, 45)
        assertTrue(coursePeriodOngoing(start, end, kotlinx.datetime.LocalTime(8, 0)))
        assertTrue(coursePeriodOngoing(start, end, kotlinx.datetime.LocalTime(8, 44)))
        assertFalse(coursePeriodOngoing(start, end, kotlinx.datetime.LocalTime(8, 45)))
        assertFalse(coursePeriodOngoing(start, end, kotlinx.datetime.LocalTime(7, 59)))
    }

    @Test
    fun `已结束判定_跨午夜课当天内永不结束`() {
        val start = kotlinx.datetime.LocalTime(23, 0)
        val end = kotlinx.datetime.LocalTime(0, 30)
        // 白天/晚上都不能因为 now > 00:30 而误判成「已上完」
        assertFalse(coursePeriodCompleted(start, end, kotlinx.datetime.LocalTime(10, 0)))
        assertFalse(coursePeriodCompleted(start, end, kotlinx.datetime.LocalTime(22, 0)))
        // 普通课正常判定
        assertTrue(
            coursePeriodCompleted(
                kotlinx.datetime.LocalTime(8, 0),
                kotlinx.datetime.LocalTime(8, 45),
                kotlinx.datetime.LocalTime(10, 0),
            )
        )
        assertFalse(
            coursePeriodCompleted(
                kotlinx.datetime.LocalTime(8, 0),
                kotlinx.datetime.LocalTime(8, 45),
                kotlinx.datetime.LocalTime(8, 45),
            )
        )
    }
}
