package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 跨周调课（拖着课翻周后松手）的拆分/合并语义回归。
 *
 * 这条路径以前只有假期调休列能触发，UI 放开「任意周 → 任意周」后成为常规入口：
 * `moveWeekInPlace` 的四个分支（同位置跨周只挪周次 / 合并同源 / 单周直接改 / 多周拆分）
 * 里跨周用到的三条都在这里过一遍。
 *
 * 语义约定：**移动** —— 源周少一节，目标周多一节。
 */
class CourseRepositoryCrossWeekMoveTest {

    private lateinit var repository: CourseRepository
    private lateinit var driver: JdbcSqliteDriver

    @BeforeTest
    fun setUp() {
        AppStorage.init { InMemoryKeyValueStore() }
        // 课程只写行表（写失败就抛，没有 JSON 副本可退），所以必须有真库
        driver = openInMemoryDriver()
        NexioDatabase.Schema.create(driver)
        CourseStorage.install { SqliteCourseRowStore(NexioDatabase(driver)) }
        repository = CourseRepository.getInstance()
        repository.invalidateAllCaches()
        // 每个用例都从空课表起步：覆盖掉行表里上一轮留下的课
        repository.saveCourses(emptyList(), notify = false)
    }

    @AfterTest
    fun tearDown() {
        CourseStorage.install { null }
        repository.invalidateAllCaches()
        driver.close()
    }

    private fun course(
        id: String,
        name: String,
        day: Int,
        start: Int,
        end: Int,
        weeks: List<Int> = (1..16).toList(),
    ): Course = Course(
        id = id,
        name = name,
        classroom = "A101",
        teacher = "张老师",
        dayOfWeek = day,
        startSection = start,
        endSection = end,
        startWeek = weeks.min(),
        endWeek = weeks.max(),
        weekType = Course.WEEK_TYPE_ALL,
        colorRes = 0xFF3355FF,
        selectedWeeks = weeks,
    )

    /** 第 [week] 周 [day] 星期 [start]-[end] 节上的课 */
    private fun List<Course>.at(
        week: Int,
        day: Int,
        start: Int,
        end: Int,
    ): List<Course> = filter {
        it.isActiveInWeek(week) &&
            it.dayOfWeek == day &&
            it.startSection == start &&
            it.endSection == end
    }

    @Test
    fun moveAcrossWeeks_sourceWeekLosesOne_targetWeekGainsOne() {
        val math = course("math", "高数", day = 1, start = 1, end = 2)
        repository.saveCourses(listOf(math), notify = false)

        // 第 3 周周一 1-2 节 → 第 8 周周三 5-6 节
        val result = repository.moveCourseForWeek(
            sourceCourseId = "math",
            week = 3,
            targetDayOfWeek = 3,
            targetStartSection = 5,
            targetEndSection = 6,
            targetWeek = 8,
        )

        // 源周这一格空出来
        assertTrue(
            result.at(week = 3, day = 1, start = 1, end = 2).isEmpty(),
            "第 3 周原位置应为空",
        )
        // 目标周多出一节
        val moved = result.at(week = 8, day = 3, start = 5, end = 6)
        assertEquals(1, moved.size, "第 8 周目标格应恰有一节课")
        assertEquals("高数", moved.first().name)
        // 其余周次（含第 8 周的原格）不受影响
        assertEquals(1, result.at(week = 8, day = 1, start = 1, end = 2).size)
        assertEquals(1, result.at(week = 4, day = 1, start = 1, end = 2).size)
        // 拆出来的那条只覆盖第 8 周
        assertEquals(listOf(8), moved.first().selectedWeeks)
        // 原课把第 3 周摘掉后仍覆盖其余 15 周
        val source = result.first { it.id == "math" }
        assertEquals(15, source.selectedWeeks.size)
        assertTrue(3 !in source.selectedWeeks)
    }

    @Test
    fun swapAcrossWeeks_eachSideTakesTheOtherSlotInItsOwnWeek() {
        val math = course("math", "高数", day = 1, start = 1, end = 2)
        val english = course("english", "英语", day = 3, start = 5, end = 6)
        repository.saveCourses(listOf(math, english), notify = false)

        // 高数第 3 周 ↔ 英语第 8 周
        val result = repository.swapCoursesForWeek(
            sourceCourseId = "math",
            targetCourseId = "english",
            week = 3,
            targetWeek = 8,
        )

        val mathNames = result.at(week = 8, day = 3, start = 5, end = 6).map { it.name }
        assertEquals(listOf("高数"), mathNames, "高数第 3 周应落到第 8 周的英语格")

        val englishNames = result.at(week = 3, day = 1, start = 1, end = 2).map { it.name }
        assertEquals(listOf("英语"), englishNames, "英语第 8 周应落到第 3 周的高数格")
    }

    @Test
    fun overwriteAcrossWeeks_conflictLosesOnlyTargetWeek() {
        val math = course("math", "高数", day = 1, start = 1, end = 2)
        val english = course("english", "英语", day = 3, start = 5, end = 6)
        repository.saveCourses(listOf(math, english), notify = false)

        val result = repository.overwriteCourseForWeek(
            sourceCourseId = "math",
            week = 3,
            targetDayOfWeek = 3,
            targetStartSection = 5,
            targetEndSection = 6,
            targetWeek = 8,
        )

        val atTarget = result.at(week = 8, day = 3, start = 5, end = 6)
        assertEquals(1, atTarget.size)
        assertEquals("高数", atTarget.first().name, "目标格应只剩调过来的课")

        val englishAfter = result.first { it.id == "english" }
        assertTrue(
            8 !in englishAfter.selectedWeeks,
            "冲突课只该丢第 8 周，其余周次保留（实际剩 ${englishAfter.selectedWeeks.size} 周）",
        )
        assertEquals(15, englishAfter.selectedWeeks.size)
    }

    @Test
    fun moveToSameSlotInAnotherWeek_onlyShiftsThatWeek() {
        // 同星期同节次、只换周次（1..5 周 → 第 3 周挪到第 8 周）：
        // 不该拆成两条同槽记录，而是原地改周次集合
        val math = course("math", "高数", day = 1, start = 1, end = 2, weeks = (1..5).toList())
        repository.saveCourses(listOf(math), notify = false)

        val result = repository.moveCourseForWeek(
            sourceCourseId = "math",
            week = 3,
            targetDayOfWeek = 1,
            targetStartSection = 1,
            targetEndSection = 2,
            targetWeek = 8,
        )

        assertEquals(1, result.size, "同位置跨周是挪周次，不该拆出新记录")
        assertEquals(
            listOf(1, 2, 4, 5, 8),
            result.first().selectedWeeks,
            "第 3 周换成第 8 周，其余周次不动",
        )
    }

    @Test
    fun moveToSameSlotOfWeekThatAlreadyHasIt_isNoOp() {
        // 目标周本来就有这节课：挪过去是同一格同一内容，净效果只是源周少一节
        // （用户视角 = 拖了一下课没了）→ 判为无事可做
        val math = course("math", "高数", day = 1, start = 1, end = 2)
        repository.saveCourses(listOf(math), notify = false)

        val result = repository.moveCourseForWeek(
            sourceCourseId = "math",
            week = 3,
            targetDayOfWeek = 1,
            targetStartSection = 1,
            targetEndSection = 2,
            targetWeek = 8,
        )

        assertEquals(1, result.size)
        assertEquals(
            (1..16).toList(),
            result.first().selectedWeeks,
            "第 8 周已有这节课，整次拖拽应无效果",
        )
    }
}
