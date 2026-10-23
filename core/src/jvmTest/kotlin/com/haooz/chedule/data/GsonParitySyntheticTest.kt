package com.haooz.chedule.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 用**本机现场生成**的 Gson 产物对拍 [ScheduleCodec]，不依赖任何外部备份文件。
 *
 * [ScheduleCodecGsonParityTest] 里 6 个「真实 …」用例依赖本机 Downloads 下的备份文件，
 * 不在仓库、CI 上也没有 —— 那些用例实际上是**静默自跳过**，从未真正跑过。本文件用等价
 * 数据把同一批断言补上，保证「Gson 写的老数据能被新解码器读回」这条红线随时可验证。
 *
 * 判据同那份：语义相等（按名字取值、数字归一为 Double），不是逐字相等 ——
 * release 构建会被 R8 重排字段顺序，字段顺序不是兼容契约。
 */
class GsonParitySyntheticTest {

    private val gson = Gson()

    private fun semantic(json: String): Any? = jsonToPlainValue(parseJsonElement(json))

    private fun sampleCourses(): List<Course> = listOf(
        // 常规课：全部字段有值
        Course(
            id = "c1", name = "高等数学", classroom = "A101", teacher = "张老师",
            dayOfWeek = 3, startSection = 1, endSection = 2,
            startWeek = 1, endWeek = 16, weekType = 0,
            colorRes = 0xFF4CAF50L,
            selectedWeeks = listOf(1, 3, 5, 7, 9, 11, 13, 15),
            scheduleId = "数字经济3", lastModified = 1_760_000_000_000L,
            isCustomTime = false, customStartTime = null, customEndTime = null,
        ),
        // 自定义时间的课：两个 nullable 字段非 null
        Course(
            id = "c2", name = "体育", classroom = "操场", teacher = "",
            dayOfWeek = 5, startSection = 3, endSection = 4,
            startWeek = 1, endWeek = 16, weekType = 2,
            colorRes = 0xFF2196F3L,
            selectedWeeks = emptyList(),
            scheduleId = "数字经济3", lastModified = 1_760_000_000_001L,
            isCustomTime = true, customStartTime = "09:00", customEndTime = "10:30",
        ),
        // 最小课：空串 / 零值 / 空列表
        Course(
            id = "c3", name = "", classroom = "", teacher = "",
            dayOfWeek = 7, startSection = 12, endSection = 12,
            startWeek = 0, endWeek = 0, weekType = 1,
            colorRes = 0L,
            selectedWeeks = emptyList(),
            scheduleId = "", lastModified = 0L,
        ),
        // 中文 / 特殊字符：Gson 不转义 `<>&='`，转义必须一致
        Course(
            id = "c4", name = "C++ & <数据结构>", classroom = "B'2\"03", teacher = "李老师",
            dayOfWeek = 1, startSection = 5, endSection = 6,
            startWeek = 1, endWeek = 8, weekType = 0,
            colorRes = 0xFFFF9800L,
        ),
    )

    @Test
    fun `Gson 序列化的课程 JSON 能被新解码器原样读回`() {
        val courses = sampleCourses()
        val gsonJson = gson.toJson(courses)
        val decoded = decodeCourses(gsonJson)

        assertEquals(courses.size, decoded.size, "课程条数必须一致")
        courses.zip(decoded).forEachIndexed { i, (expect, actual) ->
            assertEquals(expect, actual, "第 $i 门课与 Gson 产物不一致")
        }
    }

    @Test
    fun `新编码器的课程 JSON 与 Gson 语义一致`() {
        val courses = sampleCourses()
        assertEquals(semantic(gson.toJson(courses)), semantic(encodeCourses(courses)))
    }

    @Test
    fun `课程编解码往返稳定`() {
        val courses = sampleCourses()
        val once = decodeCourses(encodeCourses(courses))
        val twice = decodeCourses(encodeCourses(once))
        assertEquals(once, twice)
        assertEquals(courses, once)
    }

    @Test
    fun `null 字段被省略，与 Gson 的 serializeNulls=false 一致`() {
        val json = encodeCourses(sampleCourses().take(1))
        assertTrue(!json.contains("customStartTime"), "null 的 nullable 字段不能写出")
        assertTrue(!json.contains("null"), "不能出现字面量 null")
        // 有值的必须写出
        val customJson = encodeCourses(listOf(sampleCourses()[1]))
        assertTrue(customJson.contains("\"customStartTime\":\"09:00\""))
        assertTrue(customJson.contains("\"customEndTime\":\"10:30\""))
    }

    @Test
    fun `字段顺序被打乱（R8 重排）仍能正确解码`() {
        val courses = sampleCourses()
        // 模拟 release 构建的字母序 + 缺字段：JSON 对象无序，按名字取值必须成立
        val reordered = """
            [{"classroom":"A101","colorRes":4283215696,"customEndTime":null,
              "customStartTime":null,"dayOfWeek":3,"endSection":2,"endWeek":16,
              "id":"c1","isCustomTime":false,"lastModified":1760000000000,
              "name":"高等数学","scheduleId":"数字经济3","selectedWeeks":[1,3,5,7,9,11,13,15],
              "startSection":1,"startWeek":1,"teacher":"张老师","weekType":0}]
        """.trimIndent()
        val decoded = decodeCourses(reordered)
        assertEquals(1, decoded.size)
        assertEquals(courses[0], decoded[0], "乱序/全量字段的 JSON 必须解出同一门课")
    }

    @Test
    fun `缺字段的老 JSON 不会静默变空课表`() {
        // 老备份里可能只写了部分字段（早期版本模型更瘦）
        val lean = """[{"id":"old1","name":"英语","dayOfWeek":2,"startSection":1,"endSection":2}]"""
        val decoded = decodeCourses(lean)
        assertEquals(1, decoded.size)
        assertEquals("old1", decoded[0].id)
        assertEquals("英语", decoded[0].name)
        assertEquals(2, decoded[0].dayOfWeek)
        // 缺失字段回落零值而非丢失整条
        assertEquals("", decoded[0].classroom)
        assertEquals(emptyList(), decoded[0].selectedWeeks)
    }

    @Test
    fun `time_config 走 Gson 对拍`() {
        val config = TimeConfig(
            id = 7L,
            name = "默认作息",
            morningSections = 4,
            afternoonSections = 4,
            eveningSections = 3,
        ).let { TimeConfig.sanitize(7L, it) }

        val gsonJson = gson.toJson(config)
        val decoded = parseTimeConfigSnapshotOrNull(gsonJson)
        assertTrue(decoded != null, "Gson 写的 time_config 必须能解析")
        assertEquals(
            semantic(gsonJson),
            semantic(encodeTimeConfig(TimeConfig.sanitize(7L, decoded))),
            "新编码结果与 Gson 语义一致"
        )
    }

    @Test
    fun `schedule_names 与 schedule_folders 走 Gson 对拍`() {
        val names = listOf("数字经济3", "默认课表", "C++ & <数据结构>")
        assertEquals(semantic(gson.toJson(names)), semantic(encodeStringList(names)))
        assertEquals(names, decodeStringList(gson.toJson(names)))

        val folders = listOf(
            ScheduleFolder(id = "f1", name = "大一上", schedules = listOf("数字经济3")),
            ScheduleFolder(id = "f2", name = "大一下", schedules = emptyList()),
        )
        assertEquals(semantic(gson.toJson(folders)), semantic(encodeScheduleFolders(folders)))
        assertEquals(folders, decodeScheduleFolders(gson.toJson(folders)))
    }

    @Test
    fun `time_config_ids 两种存量格式都要能读`() {
        // saveTimeConfigIds 写的是逗号分隔 "0,1,2"；更老的备份里可能是 JSON 数组 "[0,1,2]"。
        // getTimeConfigIds 两条路都留了口，这里只锁住「JSON 数组」这条 codec 契约。
        assertEquals(listOf(0L, 1L, 2L), decodeLongList("[0,1,2]"))
        // 逗号分隔不走 codec，由 CourseRepository.getTimeConfigIds 自行 split —— 断言它不会
        // 被当成 JSON 抛异常而整体掉空（否则会误触发 rebuildTimeConfigIdsFromKeys）。
        assertTrue(
            runCatching { decodeLongList("0,1,2") }.isFailure,
            "非 JSON 必须由调用方分支处理，codec 侧保持抛异常"
        )
    }
}
