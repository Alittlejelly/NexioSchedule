package com.haooz.chedule.ui.web

import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.InMemoryKeyValueStore
import com.haooz.chedule.data.getStringOrNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 教务导入桥内核的契约回归（官方 wiki「适配脚本开发指南 v2」）：锁**与云端脚本的契约**
 * —— 字段名、默认值、调用顺序。任何改动都会让存量脚本静默出错（导入"成功"但结果不对），
 * 宁可测碎一点。
 */
class EduImportBridgeCoreTest {

    private lateinit var store: InMemoryKeyValueStore
    private lateinit var core: EduImportBridgeCore
    private val imported = mutableListOf<List<Course>>()
    private var completed = 0

    @BeforeTest
    fun setUp() {
        store = InMemoryKeyValueStore()
        AppStorage.init { store }
        imported.clear()
        completed = 0
        core = EduImportBridgeCore(
            onCoursesImported = { imported += it },
            onTaskCompleted = { completed++ },
        )
    }

    private fun prefsString(key: String): String? =
        AppStorage.store("edu_import_prefs").getStringOrNull(key)

    private fun prefsInt(key: String): Int =
        AppStorage.store("edu_import_prefs").getInt(key, -1)

    // ── 1.1 课程 ────────────────────────────────────────────────────

    @Test
    fun `课程负载按契约映射`() {
        val json = """
            [{"name":"高等数学","teacher":"张教授","position":"教101","day":1,
              "startSection":1,"endSection":2,"weeks":[1,2,3,4],"credit":2.5}]
        """.trimIndent()

        assertEquals("true", core.handle("saveImportedCourses", listOf(json)))

        val course = imported.single().single()
        assertEquals("高等数学", course.name)
        assertEquals("张教授", course.teacher)
        assertEquals("教101", course.classroom)
        assertEquals(1, course.dayOfWeek)
        assertEquals(1, course.startSection)
        assertEquals(2, course.endSection)
        assertEquals(1, course.startWeek)
        assertEquals(4, course.endWeek)
        assertEquals(listOf(1, 2, 3, 4), course.selectedWeeks)
        assertEquals(Course.WEEK_TYPE_ALL, course.weekType)
        assertEquals("", course.scheduleId, "未选目标课表 = 当前课表（空串）")
    }

    @Test
    fun `自定义时间课程缺节次也能解析`() {
        val json = """
            [{"name":"晚自习","day":3,"isCustomTime":true,
              "customStartTime":"19:00","customEndTime":"20:30"}]
        """.trimIndent()

        core.handle("saveImportedCourses", listOf(json))

        val course = imported.single().single()
        assertEquals(3, course.dayOfWeek)
        assertEquals(1, course.startSection, "缺 startSection 时兜 1")
        assertEquals(1, course.endSection, "缺 endSection 时跟随 startSection")
        assertTrue(course.isCustomTime)
        assertEquals("19:00", course.customStartTime)
        assertEquals("20:30", course.customEndTime)
    }

    @Test
    fun `空课程数组直接拒绝`() {
        assertFailsWith<EduBridgeReject> { core.handle("saveImportedCourses", listOf("[]")) }
    }

    @Test
    fun `同名课程复用同色`() {
        val json = """
            [{"name":"大学英语","day":1,"startSection":1,"endSection":2,"weeks":[1]},
             {"name":"大学英语","day":3,"startSection":3,"endSection":4,"weeks":[1]}]
        """.trimIndent()

        core.handle("saveImportedCourses", listOf(json))

        val colors = imported.single().map { it.colorRes }
        assertEquals(colors[0], colors[1])
    }

    // ── 目标课表 ────────────────────────────────────────────────────

    @Test
    fun `目标课表随课程登记 null 时清除`() {
        val json = """[{"name":"X","day":1,"startSection":1,"endSection":1,"weeks":[1]}]"""

        core.setImportTableId("大二上")
        core.handle("saveImportedCourses", listOf(json))
        assertEquals("大二上", prefsString("target_schedule_id"))
        assertEquals("大二上", imported.last().single().scheduleId)

        core.setImportTableId(null)
        core.handle("saveImportedCourses", listOf(json))
        assertNull(prefsString("target_schedule_id"), "null 必须显式移除该键")
    }

    // ── 1.3 课表配置 ────────────────────────────────────────────────

    @Test
    fun `配置只覆盖传来的字段 其余走默认值`() {
        val json = """{"semesterStartDate":"2024-09-01","semesterTotalWeeks":18,"defaultBreakDuration":5}"""

        core.handle("saveCourseConfig", listOf(json))

        // ⚠ 落盘走 App 内部的 yyyy/MM/dd 格式（normalizeClassStartDate 归一），不是脚本传的 ISO 串
        assertEquals("2024/09/01", prefsString("semester_start_date"))
        assertEquals(18, prefsInt("semester_total_weeks"))
        assertEquals(5, prefsInt("default_break_duration"))
        assertEquals(45, prefsInt("default_class_duration"), "未传则默认 45")
        assertEquals(1, prefsInt("first_day_of_week"), "未传则默认周一")
    }

    @Test
    fun `周起始日原样落盘`() {
        core.handle("saveCourseConfig", listOf("""{"firstDayOfWeek":7}"""))
        assertEquals(7, prefsInt("first_day_of_week"))
    }

    @Test
    fun `开学日无法解析时清掉旧值`() {
        core.handle("saveCourseConfig", listOf("""{"semesterStartDate":"2024-09-01"}"""))
        core.handle("saveCourseConfig", listOf("""{"semesterTotalWeeks":16}"""))
        assertNull(prefsString("semester_start_date"), "本次没传的字段不能残留上一次的值")
    }

    // ── 1.2 / 1.4 作息与组合作息（顺序约束）─────────────────────────

    @Test
    fun `组合作息必须先有基础时间段`() {
        val combo = """{"name":"作息方案","publicSchedules":[{"name":"夏季","startDate":"2025-05-01","timeSlots":[]}]}"""

        assertFailsWith<EduBridgeReject> { core.handle("saveComboSchedule", listOf(combo)) }
    }

    @Test
    fun `基础时间段之后提交组合作息 之后不再接受时间段`() {
        val slots = """[{"number":1,"startTime":"08:00","endTime":"08:45"}]"""
        val combo = """{"name":"作息方案","publicSchedules":[{"name":"夏季","startDate":"2025-05-01","timeSlots":[]}]}"""

        assertEquals("true", core.handle("savePresetTimeSlots", listOf(slots)))
        assertEquals("true", core.handle("saveComboSchedule", listOf(combo)))
        assertEquals(combo, prefsString("combo_schedule"), "原样存，合并交给消费端")

        assertFailsWith<EduBridgeReject> { core.handle("savePresetTimeSlots", listOf(slots)) }
    }

    @Test
    fun `重开一轮导入会重置顺序状态`() {
        val slots = """[{"number":1,"startTime":"08:00","endTime":"08:45"}]"""
        val combo = """{"name":"作息方案","publicSchedules":[{"name":"夏季","startDate":"2025-05-01","timeSlots":[]}]}"""

        core.handle("savePresetTimeSlots", listOf(slots))
        core.handle("saveComboSchedule", listOf(combo))

        core.setImportTableId("新课表")
        assertEquals("true", core.handle("savePresetTimeSlots", listOf(slots)))
    }

    // ── 收尾 ────────────────────────────────────────────────────────

    @Test
    fun `notifyTaskCompletion 触发收尾`() {
        core.setImportTableId("大二上")
        core.handle("notifyTaskCompletion", emptyList())
        assertEquals(1, completed)
    }

    @Test
    fun `未知方法交回平台处理`() {
        assertNull(core.handle("showToast", listOf("hi")), "对话框三件套由平台负责")
    }
}
