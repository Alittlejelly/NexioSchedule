package com.haooz.chedule.ui.utils

import com.haooz.chedule.data.Course
import com.haooz.chedule.data.truncateNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 课程备注在「分享包 / 单课表备份」这条链路上的往返。
 *
 * 两个坑值得钉住：
 * 1. 没有备注时**不能写字段** —— 旧版本导入方看到的载荷要逐字不变；
 * 2. 长度上限只有 [Course.NOTE_MAX_LENGTH] 一个来源，超长截断要发生在导入侧，
 *    而不是让整份导入失败；且不能把 emoji 的代理对切成半个。
 */
class ShareNoteCodecTest {

    private fun course(
        note: String = "",
        weekNotes: Map<Int, String> = emptyMap(),
    ) = Course(
        id = "c1",
        name = "高等数学",
        classroom = "A101",
        teacher = "张老师",
        dayOfWeek = 1,
        startSection = 1,
        endSection = 2,
        startWeek = 1,
        endWeek = 16,
        weekType = Course.WEEK_TYPE_ALL,
        colorRes = 0L,
        note = note,
        weekNotes = weekNotes,
    )

    @Test
    fun `备注与单周备注经过分享包往返后仍在`() {
        val source = course(note = "带计算器", weekNotes = mapOf(3 to "调课到 302"))
        val shared = courseToShareMap(source)

        assertEquals("带计算器", parseShareCourseNote(shared))
        assertEquals(mapOf(3 to "调课到 302"), parseShareCourseWeekNotes(shared))
    }

    @Test
    fun `没有备注时不写字段`() {
        val shared = courseToShareMap(course())

        assertFalse(shared.containsKey("note"), "空备注不该落字段：$shared")
        assertFalse(shared.containsKey("weekNotes"), "空 weekNotes 不该落字段：$shared")
    }

    @Test
    fun `空白单周备注被丢掉`() {
        val shared = courseToShareMap(course(weekNotes = mapOf(3 to "   ", 4 to "带 U 盘")))

        assertEquals(mapOf(4 to "带 U 盘"), parseShareCourseWeekNotes(shared))
    }

    @Test
    fun `超长备注按上限截断而不是让导入失败`() {
        val overlong = "备".repeat(Course.NOTE_MAX_LENGTH + 20)
        val shared = courseToShareMap(course(note = overlong, weekNotes = mapOf(2 to overlong)))

        val note = parseShareCourseNote(shared)
        assertEquals(Course.NOTE_MAX_LENGTH, note.length)
        assertEquals("备".repeat(Course.NOTE_MAX_LENGTH), note)
        assertEquals(
            setOf(Course.NOTE_MAX_LENGTH),
            parseShareCourseWeekNotes(shared).map { it.value.length }.toSet(),
        )
    }

    @Test
    fun `截断不会把 emoji 切成半个代理对`() {
        // 49 个汉字 + 1 个 emoji（占 2 个 UTF-16 单元）→ 上限落在 emoji 的前半个上
        val text = "备".repeat(Course.NOTE_MAX_LENGTH - 1) + "\uD83D\uDCDA"
        val cut = text.truncateNote()

        assertEquals(Course.NOTE_MAX_LENGTH - 1, cut.length)
        assertTrue(cut.none { it.isHighSurrogate() || it.isLowSurrogate() }, "不能留下半个代理对")
    }

    @Test
    fun `周次非正数或类型不对的条目被跳过`() {
        val shared = mapOf<String, Any?>(
            "weekNotes" to mapOf("0" to "无效周", "3" to "有效", "x" to "乱码", "5" to 42),
        )

        assertEquals(mapOf(3 to "有效"), parseShareCourseWeekNotes(shared))
    }
}
