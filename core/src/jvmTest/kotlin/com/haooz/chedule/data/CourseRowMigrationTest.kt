package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JSON blob → 课程行表 导入链路的端到端回归。
 *
 * 测 [CourseRepository] 而不是 [SqliteCourseRowStore]：会丢数据的是切换逻辑 ——
 * 何时判定行表为真相、写行表后还要不要写 blob、读失败退回哪里。
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
        val blobBefore = blobOf(repository)
        assertTrue(blobBefore.isNullOrBlank().not(), "前置条件：老状态应当有 JSON blob")

        // 2) 装上行表，第一次读取触发迁移
        val store = SqliteCourseRowStore(NexioDatabase(driver))
        CourseStorage.install { store }
        repository.invalidateAllCaches()

        val loaded = repository.getAllCourses()
        assertEquals(2, loaded.size)
        assertTrue(store.isImported(SCHEDULE), "首次读取必须完成导入并留下标记")
        assertTrue(sameCourseContent(listOf(math, english), loaded), "迁移后内容必须逐字段一致：$loaded")

        // 3) 改一门课：行表更新，JSON 一个字节都不该动 —— 这就是「不再整份重写」的证据
        repository.updateCourse(math.copy(note = "改成周三交"))
        assertEquals("改成周三交", store.load(SCHEDULE).first { it.id == "c1" }.note)
        assertEquals(blobBefore, blobOf(repository), "行表生效后不该再整份重写 JSON blob")
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
        assertEquals(blobBefore, blobOf(repository), "增删课同样只该写行表")
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
    fun `行表被作废后会重新从 JSON 导入`() {
        val repository = CourseRepository.getInstance()
        val math = course(id = "c1", name = "高等数学")
        repository.saveCourses(listOf(math), notify = false)

        val store = SqliteCourseRowStore(NexioDatabase(driver))
        CourseStorage.install { store }
        repository.invalidateAllCaches()
        assertEquals(1, repository.getAllCourses().size)

        // 模拟恢复备份：行表整体作废
        store.invalidateAll()
        repository.invalidateAllCaches()

        val reloaded = repository.getAllCourses()
        assertEquals(1, reloaded.size, "作废后应当从 JSON 重建，而不是返回空课表")
        assertTrue(store.isImported(SCHEDULE))
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun blobOf(repository: CourseRepository): String? =
        repository.exportAllPreferences()["schedule_${SCHEDULE}_courses"] as? String

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
