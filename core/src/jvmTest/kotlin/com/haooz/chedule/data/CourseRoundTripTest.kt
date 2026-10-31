package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.storage.CourseRowAnchor
import com.haooz.chedule.data.storage.SqliteCourseRowStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 全字段往返：造一门「每个字段都不是默认值」的课，写进去再读出来，整体相等。
 *
 * 为什么用 `assertEquals(full, loaded)` 而不是逐字段断言：`Course` 是 data class，
 * `equals` 比的就是全部字段 —— 少映射一个字段，读回的那一项就是默认值，断言立刻红。
 * 逐字段写反而容易在加字段时忘了补一条，那就和被测的 bug 同型了。
 *
 * 覆盖两条写入路径：
 * - [SqliteCourseRowStore.importAll]（迁移 / 恢复备份，自带自校验）
 * - [SqliteCourseRowStore.writeDiff]（日常编辑，**没有**自校验，是最容易漏的地方）
 */
class CourseRoundTripTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqliteCourseRowStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NexioDatabase.Schema.create(driver)
        driver.execute(identifier = null, sql = "PRAGMA foreign_keys = ON", parameters = 0)
        store = SqliteCourseRowStore(NexioDatabase(driver))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `importAll 之后每个字段都能原样读回`() {
        val anchor = anchor()
        val full = fullCourse()

        store.importAll(anchor, listOf(full))

        assertEquals(full, store.load(anchor.scheduleId).single(), "导入路径漏映射了某个字段")
    }

    @Test
    fun `writeDiff 之后每个字段都能原样读回`() {
        val anchor = anchor()
        // 先落一门全是默认值的课，再用 writeDiff 改成全字段非默认 ——
        // 这条路径没有自校验，漏映射不会被 importAll 的 sameCourseContent 挡住
        val bare = Course(
            id = "c-full",
            name = "空",
            classroom = "",
            teacher = "",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 1,
            startWeek = 1,
            endWeek = 1,
            weekType = Course.WEEK_TYPE_ALL,
            colorRes = 0L,
            // 必须显式给：写入时归属只认 anchor 的 scheduleId，读回的就是它；
            // 留空会让 importAll 的自校验判成「写入后读不回来」
            scheduleId = SCHEDULE,
        )
        store.importAll(anchor, listOf(bare))

        val full = fullCourse()
        store.writeDiff(anchor, listOf(bare), listOf(full))

        assertEquals(full, store.load(anchor.scheduleId).single(), "编辑路径漏映射了某个字段")
    }

    @Test
    fun `清空可选字段后也能正确读回`() {
        val anchor = anchor()
        val full = fullCourse()
        store.importAll(anchor, listOf(full))

        // 反向：把所有可选字段清空，确认旧值不会残留在行里（custom_start_time 等是 NULL）
        val cleared = full.copy(
            isCustomTime = false,
            customStartTime = null,
            customEndTime = null,
            note = "",
            weekNotes = emptyMap(),
            selectedWeeks = emptyList(),
        )
        store.writeDiff(anchor, listOf(full), listOf(cleared))

        assertEquals(cleared, store.load(anchor.scheduleId).single(), "清空后旧值仍在，说明写入没覆盖全列")
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun anchor() = CourseRowAnchor(
        scheduleId = SCHEDULE,
        timeConfigId = 1L,
        timeConfigName = "默认",
        timeConfigPayload = """{"name":"默认"}""",
        sortOrder = 0,
        currentWeek = 3,
        totalWeeks = 18,
        classStart = "2026/09/01",
    )

    /** 每个字段都取一个「不会是默认值」的值：漏映射时读回的是默认值，整体比较立刻不等。 */
    private fun fullCourse() = Course(
        id = "c-full",
        name = "全字段课程",
        classroom = "B203",
        teacher = "李老师",
        dayOfWeek = 5,
        startSection = 3,
        endSection = 4,
        startWeek = 2,
        endWeek = 15,
        weekType = Course.WEEK_TYPE_ODD,
        colorRes = 0xFF123456L,
        selectedWeeks = listOf(3, 5, 7),
        scheduleId = SCHEDULE,
        lastModified = 1_700_000_000_123L,
        isCustomTime = true,
        customStartTime = "08:15",
        customEndTime = "09:40",
        note = "主备注",
        weekNotes = mapOf(5 to "第五周小测"),
    )

    private companion object {
        const val SCHEDULE = "全字段课表"
    }
}
