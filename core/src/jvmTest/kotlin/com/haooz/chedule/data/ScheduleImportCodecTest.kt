package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 「导入课表」JSON 解析（[parseFullScheduleJson]）的**真实备份**回归测试。
 *
 * [parseFullScheduleJson] 原用 `Gson().fromJson(text, TypeToken<Map<String,Any>>)`；
 * Gson 走运行时反射，Kotlin/Native（iOS）上不存在 —— 2026-10-11 换成
 * `kotlinx-serialization`（`JsonSupport.parseJsonObject` + `jsonToPlainValue`）。
 * **落盘/输入格式一字节未改**，只换了库；本测试用两份**用户真实备份文件**固化
 * 「换库后行为不变」。
 *
 * 夹具（2026-10-11 从本机导出，冻结快照）：
 * - `/backup_single_schedule.json`（12,285 字节）：单课表备份「数字经济3」，
 *   含 `semester_settings` + `teaching_week_reorganizations` + `courses[]`（23 门课）。
 * - `/backup_full_export.json`（61,949 字节）：全量导出，扁平 `schedule_*` 键值。
 *
 * 放 `jvmTest`：需读文件资源；解析器本身在 commonMain。
 */
class ScheduleImportCodecTest {

    private fun loadFixture(name: String): String {
        val stream = javaClass.getResourceAsStream(name)
            ?: error("找不到测试夹具 $name")
        return stream.use { it.readBytes() }.toString(Charsets.UTF_8)
    }

    @Test
    fun `单课表备份被正确识别为本地备份并拒绝`() {
        val text = loadFixture("/backup_single_schedule.json")
        val (ok, msg, data) = parseFullScheduleJson(text)
        assertEquals(false, ok)
        assertEquals("这是本地单课表备份，请使用本地备份恢复入口", msg)
        assertNull(data)
    }

    @Test
    fun `kotlinx 完整解析单课表备份的真实结构`() {
        // 直接走 JsonSupport 解析，证明真实嵌套结构（semester_settings / courses 数组）
        // 被 kotlinx 完整读出（这正是换库最容易静默出错的地方）
        val text = loadFixture("/backup_single_schedule.json")
        val plain = jsonToPlainValue(parseJsonObject(text)) as Map<*, *>

        @Suppress("UNCHECKED_CAST")
        val courses = plain["courses"] as? List<Map<String, Any?>>
        assertNotNull(courses)
        assertEquals(23, courses.size, "课程数量应与真实备份一致（23 门）")
        assertEquals("统计学", courses[0]["name"], "首门课名应与真实备份一致")

        @Suppress("UNCHECKED_CAST")
        val settings = plain["semester_settings"] as? Map<String, Any?>
        assertNotNull(settings)
        assertEquals(20, (settings["total_weeks"] as Number).toInt(), "总周数应为 20")
        assertEquals("2026/09/02", settings["class_start_time"], "开学日期字符串应逐字保留")

        // 数字字段经 as Number 可用（jsonToPlainValue 把整数转 Double，与 Gson 一致）
        val firstDay = courses[0]["dayOfWeek"] as? Number
        assertNotNull(firstDay)
        assertEquals(1, firstDay.toInt(), "统计学在周一")
    }

    @Test
    fun `全量导出（扁平 schedule_* 键值）被识别为无效格式`() {
        val text = loadFixture("/backup_full_export.json")
        val (ok, msg, data) = parseFullScheduleJson(text)
        assertEquals(false, ok)
        assertEquals("无效的课表数据格式", msg)
        assertNull(data)
    }

    @Test
    fun `分享格式被接受且课程字段正确`() {
        // 分享码格式：有 settings + courses（无 semester_settings / config）
        val share = """
            {"settings":{"total_weeks":18,"class_start_time":"2026/03/02"},
             "courses":[{"name":"高等数学","teacher":"张三","classroom":"A101",
                         "dayOfWeek":2,"startSection":1,"endSection":2,
                         "startWeek":1,"endWeek":16,"weekType":0,
                         "selectedWeeks":[1,2,3]}]}
        """.trimIndent()
        val (ok, msg, data) = parseFullScheduleJson(share)
        assertEquals(true, ok, "分享格式应解析成功：$msg")
        assertEquals("解析成功", msg)
        assertNotNull(data)
        @Suppress("UNCHECKED_CAST")
        val courses = data["courses"] as? List<Map<String, Any?>>
        assertNotNull(courses)
        assertEquals(1, courses.size)
        assertEquals("高等数学", courses[0]["name"])
        assertEquals(2, (courses[0]["dayOfWeek"] as Number).toInt())
    }

    @Test
    fun `拾光格式被归一化为通用课表 map`() {
        // 拾光格式：有 courses + config（无 settings）
        val shiguang = """
            {"courses":[{"name":"线性代数","teacher":"李四","position":"B203",
                         "day":3,"startSection":3,"endSection":4,"weeks":[1,2,3,4],"color":16711680}],
             "timeSlots":[{"number":1,"startTime":"08:00","endTime":"08:45"}],
             "config":{"semesterStartDate":"2026/02/23","semesterTotalWeeks":16}}
        """.trimIndent()
        val (ok, msg, data) = parseFullScheduleJson(shiguang)
        assertEquals(true, ok, "拾光格式应解析成功：$msg")
        assertNotNull(data)
        assertEquals("拾光课程表导入", data["schedule_name"])
        @Suppress("UNCHECKED_CAST")
        val courses = data["courses"] as? List<Map<String, Any?>>
        assertNotNull(courses)
        assertEquals(1, courses.size)
        val c = courses[0]
        assertEquals("线性代数", c["name"])
        assertEquals("B203", c["classroom"], "position 应映射为 classroom")
        assertEquals(3, (c["dayOfWeek"] as Number).toInt(), "day 应映射为 dayOfWeek")
        assertEquals(listOf(1, 2, 3, 4), c["selectedWeeks"])
        assertEquals(16711680, (c["shiguangColor"] as Number).toInt())
        @Suppress("UNCHECKED_CAST")
        val settings = data["settings"] as? Map<String, Any?>
        assertNotNull(settings)
        assertEquals("2026/02/23", settings["class_start_time"], "semesterStartDate 应映射为 class_start_time")
        assertEquals(16, (settings["total_weeks"] as Number).toInt())
        @Suppress("UNCHECKED_CAST")
        val times = data["times"] as? Map<String, Any?>
        assertNotNull(times)
        @Suppress("UNCHECKED_CAST")
        val morning = times["morning"] as? Map<String, String>
        assertEquals("08:00-08:45", morning?.get("1"), "number 1 落到 morning_1")
    }

    @Test
    fun `分享 payload round-trip：parse → serialize → parse 课程数稳定`() {
        val share = """
            {"settings":{"total_weeks":18},
             "courses":[{"name":"大学英语","dayOfWeek":1,"startSection":1,"endSection":2,"selectedWeeks":[1,2]}]}
        """.trimIndent()
        val (ok1, _, data1) = parseFullScheduleJson(share)
        assertTrue(ok1)
        assertNotNull(data1)
        @Suppress("UNCHECKED_CAST")
        val n1 = (data1["courses"] as List<Map<String, Any?>>).size

        // 用 JsonSupport 把解析出的 plain Map 重新序列化，再解析一次 —— 课程数必须稳定
        val reserialized = toJsonElement(data1).toString()
        val (ok2, msg2, data2) = parseFullScheduleJson(reserialized)
        assertTrue(ok2, "round-trip 后应仍能解析：$msg2")
        assertNotNull(data2)
        @Suppress("UNCHECKED_CAST")
        val n2 = (data2["courses"] as List<Map<String, Any?>>).size
        assertEquals(n1, n2, "round-trip 后课程数应不变")

        // 非法输入：解析失败应返回失败而非抛异常
        val (okBad, msgBad, dataBad) = parseFullScheduleJson("{ not valid json")
        assertEquals(false, okBad)
        assertTrue(msgBad.startsWith("解析失败"), "非法 JSON 应走解析失败分支，实际：$msgBad")
        assertNull(dataBad)
    }
}
