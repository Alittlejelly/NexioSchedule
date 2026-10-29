package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 课程行表回归 —— **跑真 SQLite**，不是假后端。
 *
 * 这里要验的四件事，任何一件错了都是静默丢数据，而且只有真库能验：
 * 1. 导入后能原样读回来（自校验那条判断本身是否成立）；
 * 2. 差量写只动该动的行（周次 / 备注不能被顺手抹掉）；
 * 3. `INSERT OR REPLACE` + `ON DELETE CASCADE` 的组合不会把子表清空；
 * 4. 改名 / 删课表时行数据跟着走，且不复活。
 */
class CourseRowStoreTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqliteCourseRowStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NexioDatabase.Schema.create(driver)
        // 外键是连接级开关，SQLite 默认关；不打开的话级联删除全是装饰
        driver.execute(identifier = null, sql = "PRAGMA foreign_keys = ON", parameters = 0)
        store = SqliteCourseRowStore(NexioDatabase(driver))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `导入后能逐字段读回来`() {
        val courses = listOf(
            course(
                id = "c1",
                name = "高等数学",
                // 故意乱序：行表按 week 升序返回，自校验必须按排序后比较
                selectedWeeks = listOf(9, 1, 5, 3),
                weekNotes = mapOf(3 to "带习题册", 9 to "小测"),
                note = "周三交作业",
            ),
            course(
                id = "c2",
                name = "英语",
                dayOfWeek = 3,
                weekType = Course.WEEK_TYPE_ODD,
                isCustomTime = true,
                customStartTime = "21:30",
                customEndTime = "23:00",
            ),
        )

        store.importAll(anchor(), courses)

        assertTrue(store.isImported(SCHEDULE), "导入后必须留下标记，否则每次读都会重导一次")
        val loaded = store.load(SCHEDULE)
        assertTrue(sameCourseContent(courses, loaded), "读回内容与写入不一致：$loaded")
        // 周次必须按升序落表
        assertEquals(listOf(1, 3, 5, 9), loaded.first { it.id == "c1" }.selectedWeeks)
    }

    @Test
    fun `重复导入是幂等的`() {
        val courses = listOf(course(id = "c1", name = "物理"))
        store.importAll(anchor(), courses)
        store.importAll(anchor(), courses)

        assertEquals(1, store.load(SCHEDULE).size, "整表重来不该留下重复行")
    }

    @Test
    fun `差量写不会碰没变的课程`() {
        val math = course(id = "c1", name = "数学", selectedWeeks = listOf(1, 2, 3), weekNotes = mapOf(2 to "复习"))
        val english = course(id = "c2", name = "英语", dayOfWeek = 4, selectedWeeks = listOf(5, 6))
        store.importAll(anchor(), listOf(math, english))

        // 只改备注
        val editedMath = math.copy(note = "改成明天交", lastModified = 2_000L)
        store.writeDiff(anchor(), listOf(math, english), listOf(editedMath, english))

        val loaded = store.load(SCHEDULE).associateBy { it.id }
        assertEquals("改成明天交", loaded.getValue("c1").note)
        // 关键：改一个字段不能把周次 / 周备注冲掉（REPLACE 的级联删除就藏在这里）
        assertEquals(listOf(1, 2, 3), loaded.getValue("c1").selectedWeeks)
        assertEquals(mapOf(2 to "复习"), loaded.getValue("c1").weekNotes)
        assertEquals(listOf(5, 6), loaded.getValue("c2").selectedWeeks, "没改的课不该被重写")
    }

    @Test
    fun `差量写支持新增与删除`() {
        val math = course(id = "c1", name = "数学")
        store.importAll(anchor(), listOf(math))

        val english = course(id = "c2", name = "英语", dayOfWeek = 2, selectedWeeks = listOf(1, 2))
        store.writeDiff(anchor(), listOf(math), listOf(english))

        val loaded = store.load(SCHEDULE)
        assertEquals(listOf("c2"), loaded.map { it.id }, "删掉的课必须真的从表里消失")
        assertEquals(listOf(1, 2), loaded.single().selectedWeeks)
    }

    @Test
    fun `周次减少时旧周次不会残留`() {
        val course = course(id = "c1", name = "数学", selectedWeeks = listOf(1, 2, 3, 4))
        store.importAll(anchor(), listOf(course))

        val shrunk = course.copy(selectedWeeks = listOf(2, 4), lastModified = 2L)
        store.writeDiff(anchor(), listOf(course), listOf(shrunk))

        assertEquals(listOf(2, 4), store.load(SCHEDULE).single().selectedWeeks)
    }

    @Test
    fun `删课表会连带删掉课程行`() {
        store.importAll(anchor(), listOf(course(id = "c1", name = "数学")))
        store.deleteSchedule(SCHEDULE)

        assertTrue(store.load(SCHEDULE).isEmpty(), "锚点删掉后课程必须跟着走")
        assertFalse(store.isImported(SCHEDULE), "导入标记必须一起清，否则同名课表会被判成空表")
    }

    @Test
    fun `改名把行数据搬过去而不是重建`() {
        val courses = listOf(course(id = "c1", name = "数学", selectedWeeks = listOf(1, 2)))
        store.importAll(anchor(), courses)

        store.renameSchedule(SCHEDULE, "新学期")

        assertTrue(store.load(SCHEDULE).isEmpty(), "旧名字下不该还有课")
        assertTrue(store.isImported("新学期"), "标记要跟着搬，否则新名字会从冻结的旧 JSON 重建")
        // 课程归属由所在课表决定：搬过去之后 scheduleId 跟着变成新名字（与 blob 时代
        // renameSchedule 里 `courses.map { it.copy(scheduleId = newName) }` 同一语义）
        assertTrue(
            sameCourseContent(courses.map { it.copy(scheduleId = "新学期") }, store.load("新学期")),
            "课程没搬过去：${store.load("新学期")}",
        )
    }

    @Test
    fun `没有锚点时写课程会被外键拒绝`() {
        // 直接写 course 行、不经过锚点，用来确认外键真的生效（不是装饰品）
        val db = NexioDatabase(driver)
        val failed = runCatching {
            db.scheduleQueries.upsertCourse(
                id = "x", schedule_id = "不存在的课表", name = "课", classroom = "", teacher = "",
                day_of_week = 1, start_section = 1, end_section = 2, start_week = 1, end_week = 16,
                week_type = 0, color_res = 0, note = "", last_modified = 0,
                is_custom_time = 0, custom_start_time = null, custom_end_time = null,
            )
        }.isFailure
        assertTrue(failed, "外键没生效：course.schedule_id 指向了不存在的课表却写成功了")
    }

    @Test
    fun `自校验比较忽略周次顺序`() {
        val a = course(id = "c1", name = "数学", selectedWeeks = listOf(3, 1, 2))
        val b = a.copy(selectedWeeks = listOf(1, 2, 3))
        assertTrue(sameCourseContent(listOf(a), listOf(b)))
        assertFalse(sameCourseContent(listOf(a), listOf(b.copy(note = "多了备注"))))
        assertFalse(sameCourseContent(listOf(a), emptyList()))
    }

    @Test
    fun `改名撞上已有课表时覆盖而不是抛异常`() {
        // 旧代码允许重名（renameSchedule 不查重），行表这边不能因此把整个改名操作搞崩
        store.importAll(anchor(), listOf(course(id = "c1", name = "数学")))
        store.importAll(
            anchor(scheduleId = "新学期"),
            listOf(course(id = "c2", name = "物理", scheduleId = "新学期")),
        )

        store.renameSchedule(SCHEDULE, "新学期")

        // 新名字原有的课被源课表覆盖
        assertEquals(listOf("c1"), store.load("新学期").map { it.id })
        assertTrue(store.load(SCHEDULE).isEmpty())
    }

    @Test
    fun `同名改名不动课表`() {
        // REPLACE 撞上自己的主键 = 先级联删光课程，再插空行、搬 0 门课、连锚点一起删，
        // 而导入标记被原样写回 —— 课表变空且再也不会从 JSON 重建。UI 挡了同名，存储层也要挡。
        store.importAll(anchor(), listOf(course(id = "c1", name = "数学")))

        store.renameSchedule(SCHEDULE, SCHEDULE)

        assertEquals(listOf("c1"), store.load(SCHEDULE).map { it.id }, "同名改名把课清空了")
        assertTrue(store.isImported(SCHEDULE), "同名改名不该动导入标记")
    }

    @Test
    fun `恢复备份后整库作废`() {
        store.importAll(anchor(), listOf(course(id = "c1", name = "数学")))
        store.importAll(anchor(scheduleId = "旧课表"), listOf(course(id = "c2", name = "英语", scheduleId = "旧课表")))

        store.invalidateAll()

        assertTrue(store.load(SCHEDULE).isEmpty())
        assertTrue(store.load("旧课表").isEmpty())
        assertFalse(store.isImported(SCHEDULE), "标记必须一起清，否则恢复后读到的还是恢复前的课表")
        assertFalse(store.isImported("旧课表"))
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun anchor(scheduleId: String = SCHEDULE) = CourseRowAnchor(
        scheduleId = scheduleId,
        timeConfigId = 1L,
        timeConfigName = scheduleId,
        timeConfigPayload = "{}",
        sortOrder = 0,
        currentWeek = 1,
        totalWeeks = 20,
        classStart = "2026/09/01",
    )

    private fun course(
        id: String,
        name: String,
        dayOfWeek: Int = 1,
        weekType: Int = Course.WEEK_TYPE_ALL,
        selectedWeeks: List<Int> = emptyList(),
        weekNotes: Map<Int, String> = emptyMap(),
        note: String = "",
        isCustomTime: Boolean = false,
        customStartTime: String? = null,
        customEndTime: String? = null,
        scheduleId: String = SCHEDULE,
    ) = Course(
        id = id,
        name = name,
        classroom = "A101",
        teacher = "张老师",
        dayOfWeek = dayOfWeek,
        startSection = 1,
        endSection = 2,
        startWeek = 1,
        endWeek = 16,
        weekType = weekType,
        colorRes = 0xFF4CAF50L,
        selectedWeeks = selectedWeeks,
        scheduleId = scheduleId,
        lastModified = 1_000L,
        isCustomTime = isCustomTime,
        customStartTime = customStartTime,
        customEndTime = customEndTime,
        note = note,
        weekNotes = weekNotes,
    )

    private companion object {
        const val SCHEDULE = "默认课表"
    }
}
