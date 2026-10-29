package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JSON blob → 课程行表 的端到端回归。测 [CourseRepository] 的切换逻辑（何时认行表、
 * 写完要不要写 blob、读失败退回哪里），而不是表本身。
 */
class CourseRowMigrationTest {

    private lateinit var driver: JdbcSqliteDriver

    @BeforeTest
    fun setUp() {
        AppStorage.init { InMemoryKeyValueStore() }
        // 无行表状态（只有 JSON）
        CourseStorage.install { null }
        val repository = CourseRepository.getInstance()
        repository.saveScheduleNames(listOf(SCHEDULE))
        repository.setCurrentScheduleId(SCHEDULE)
        repository.saveCourses(emptyList(), notify = false)
        repository.invalidateAllCaches()

        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NexioDatabase.Schema.create(driver)
        driver.execute(identifier = null, sql = "PRAGMA foreign_keys = ON", parameters = 0)
    }

    @AfterTest
    fun tearDown() {
        // 必须拆掉：CourseStorage 是全局单例，留着指向已关闭的连接会毒到后面的测试
        CourseStorage.install { null }
        CourseRepository.getInstance().invalidateAllCaches()
        driver.close()
    }

    @Test
    fun `首次读取把 JSON 迁入行表，之后写只碰行表`() {
        val repository = CourseRepository.getInstance()
        val math = course(id = "c1", name = "高等数学", selectedWeeks = listOf(1, 2, 3))
        val english = course(id = "c2", name = "英语", dayOfWeek = 3)

        // 1) 老状态：只有 JSON
        repository.saveCourses(listOf(math, english), notify = false)
        val blobBefore = blobOf()
        assertTrue(blobBefore.isNullOrBlank().not(), "前置条件：老状态应当有 JSON blob")

        // 2) 装上行表，第一次读取触发迁移
        val store = SqliteCourseRowStore(NexioDatabase(driver))
        CourseStorage.install { store }
        repository.invalidateAllCaches()

        val loaded = repository.getAllCourses()
        assertEquals(2, loaded.size)
        assertTrue(store.isImported(SCHEDULE), "首次读取必须完成导入并留下标记")
        assertTrue(sameCourseContent(listOf(math, english), loaded), "迁移后内容必须逐字段一致：$loaded")

        // 3) 改一门课：行表更新
        repository.updateCourse(math.copy(note = "改成周三交"))
        assertEquals("改成周三交", store.load(SCHEDULE).first { it.id == "c1" }.note)
        // 没改的那门课，周次不能被顺手抹掉
        assertEquals(
            listOf(1, 2, 3),
            store.load(SCHEDULE).first { it.id == "c1" }.selectedWeeks,
        )

        // 4) 删一门、加一门
        repository.deleteCourse("c2")
        assertEquals(listOf("c1"), store.load(SCHEDULE).map { it.id })

        repository.addCourse(course(id = "c3", name = "物理"))
        assertEquals(setOf("c1", "c3"), store.load(SCHEDULE).map { it.id }.toSet())

        // 5) 导入成功后源 JSON 必须被清掉：行表是唯一真相，留着 blob 就等于留一份会过期的第二真相。
        //    作废行表时没有 blob 可读 ⇒ 读回空表（生产路径都是「先写新 blob 再作废」）。
        store.markStale(SCHEDULE)
        repository.invalidateAllCaches()
        assertTrue(
            repository.getAllCourses().isEmpty(),
            "导入完成后 schedule_X_courses 应当已被删除",
        )

        // 6) 「先写 blob 再作废」的路径（单表导入 / 恢复备份）：重建出来的就是刚写进去的那版
        repository.saveCourses(listOf(math.copy(note = "改过的")), notify = false)
        repository.invalidateAllCaches()
        assertEquals("改过的", repository.getAllCourses().single().note)
    }

    @Test
    fun `没有行表时读写的仍是 JSON`() {
        val repository = CourseRepository.getInstance()
        val math = course(id = "c1", name = "高等数学", note = "带计算器")
        repository.saveCourses(listOf(math), notify = false)
        repository.invalidateAllCaches()

        // CourseStorage 装的是 null（setUp 里装的），等同桌面端 / 测试环境
        val loaded = repository.getAllCourses().single()
        assertEquals("带计算器", loaded.note)
    }

    @Test
    fun `整库恢复作废行表后从恢复出的 JSON 重建，课程不丢`() {
        val repository = CourseRepository.getInstance()
        val store = SqliteCourseRowStore(NexioDatabase(driver))
        CourseStorage.install { store }
        repository.invalidateAllCaches()
        assertTrue(repository.getAllCourses().isEmpty())

        // 一份最小可用的全量备份：恢复只认 `schedule_X_courses` 里的 JSON
        val backup = mapOf(
            "schedule_names" to """["$SCHEDULE"]""",
            "current_schedule_id" to SCHEDULE,
            "schedule_${SCHEDULE}_courses" to
                """[{"name":"高等数学","dayOfWeek":1,"startSection":1,"endSection":2,""" +
                """"selectedWeeks":[1,2,3],"classroom":"A101","teacher":"张三"}]""",
        )
        repository.importAllPreferences(backup)

        val reloaded = repository.getAllCourses()
        assertEquals(1, reloaded.size, "作废后应当从恢复出的 JSON 重建，而不是返回空课表")
        assertEquals("高等数学", reloaded.single().name)
        assertEquals(listOf(1, 2, 3), reloaded.single().selectedWeeks)
        assertTrue(store.isImported(SCHEDULE))
    }

    /**
     * 行表生效后 `schedule_X_courses` 停在上次导入那一刻，备份若照抄这份 JSON，
     * 「备份 → 恢复」就会把之后的所有增删改抹掉。这条把「备份导出的是行表当前内容」钉住。
     */
    @Test
    fun `备份导出的是行表当前内容，恢复后课程还在`() {
        val repository = CourseRepository.getInstance()
        val store = SqliteCourseRowStore(NexioDatabase(driver))
        CourseStorage.install { store }
        repository.invalidateAllCaches()
        // 触发导入：此时 blob 是 "[]"，之后新增的课只写行表
        repository.getAllCourses()

        repository.addCourse(course(id = "c1", name = "高等数学"))
        repository.invalidateAllCaches()
        assertEquals(1, repository.getAllCourses().size)

        val backup = repository.exportAllPreferences()
        val exported = backup["schedule_${SCHEDULE}_courses"] as? String
        assertTrue(
            exported.orEmpty().contains("高等数学"),
            "备份里的课程必须来自行表，而不是冻结的旧 JSON：$exported",
        )

        // 恢复会作废行表、只认备份里的 JSON
        repository.importAllPreferences(backup)
        val restored = repository.getAllCourses()
        assertEquals(1, restored.size, "恢复后课程不该丢：$restored")
        assertEquals("高等数学", restored.single().name)
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    /** 备份里该课表那一段。行表没装时它就是 prefs 里那份原始 JSON。 */
    private fun blobOf(): String? =
        CourseRepository.getInstance().exportAllPreferences()["schedule_${SCHEDULE}_courses"] as? String

    private fun course(
        id: String,
        name: String,
        dayOfWeek: Int = 1,
        selectedWeeks: List<Int> = emptyList(),
        note: String = "",
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
        weekType = Course.WEEK_TYPE_ALL,
        colorRes = 0xFF4CAF50L,
        selectedWeeks = selectedWeeks,
        scheduleId = SCHEDULE,
        lastModified = 1_000L,
        note = note,
    )

    private companion object {
        const val SCHEDULE = "默认课表"
    }
}
