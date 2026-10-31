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
 * 一次性迁移（`entity_tables_v1`）：三端启动各调一次，跑完写全局标记。
 *
 * 标记写下的意义：懒迁移路径（blob 导入 / prefs→行 同步 / prefs→表 同步）从此只会空转，
 * 删掉它们的时机就是「这个标记已经在所有用户身上写下」。
 */
class EntityTableMigrationTest {

    private lateinit var repository: CourseRepository
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqliteCourseRowStore

    @BeforeTest
    fun setUp() {
        AppStorage.init { InMemoryKeyValueStore() }
        repository = CourseRepository.getInstance()
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NexioDatabase.Schema.create(driver)
        driver.execute(identifier = null, sql = "PRAGMA foreign_keys = ON", parameters = 0)
        store = SqliteCourseRowStore(NexioDatabase(driver))
    }

    @AfterTest
    fun tearDown() {
        CourseStorage.install { null }
        repository.invalidateAllCaches()
        driver.close()
    }

    @Test
    fun `迁移把 blob 课程搬进行表并写标记`() {
        val name = schedule()
        resetTo(name, listOf(course("c1", "高等数学", name)))
        CourseStorage.install { store }
        assertFalse(store.isImported(name), "前置条件：只有 blob，行表还是空的")
        assertFalse(store.isFullyMigrated(), "前置条件：还没迁过")

        val outcome = repository.migrateLegacyEntitiesOnce()

        assertTrue(outcome is MigrationOutcome.Done, "正常数据必须一次迁完：$outcome")
        assertTrue(store.isImported(name), "课程必须搬进实体表")
        assertEquals(listOf("高等数学"), store.load(name).map { it.name })
        assertTrue(store.isFullyMigrated(), "搬完要写全局标记，否则下次启动还要重跑")
    }

    @Test
    fun `再调一次是空操作`() {
        val name = schedule()
        resetTo(name, listOf(course("c1", "高等数学", name)))
        CourseStorage.install { store }
        repository.migrateLegacyEntitiesOnce()
        val first = store.load(name)

        repository.invalidateAllCaches()
        val again = repository.migrateLegacyEntitiesOnce()

        assertTrue(again is MigrationOutcome.AlreadyDone, "标记已写就短路：$again")
        assertEquals(first, store.load(name), "第二次不该改动任何行")
        assertTrue(store.isFullyMigrated())
    }

    /**
     * 搬不完时必须如实报 `Incomplete` 并**不写标记**。
     *
     * 这条比「迁移成功」更重要：`migrateLegacyEntitiesOnce` 的失败是静默的
     * （懒路径会兜住界面，用户看不出异常），如果它还把标记写了，下次启动就不再重试，
     * 升级就永久卡在半路。
     */
    @Test
    fun `搬不完时报 Incomplete 且不写标记`() {
        val name = schedule()
        resetTo(name, listOf(course("c1", "高等数学", name)))
        // 假 store：导入永远不成功，等价于库异常
        val broken = object : CourseRowStore by store {
            override fun isImported(scheduleId: String) = false
            override fun importAll(anchor: CourseRowAnchor, courses: List<Course>) = Unit
            override fun load(scheduleId: String) = emptyList<Course>()
        }
        CourseStorage.install { broken }
        repository.invalidateAllCaches()

        val outcome = repository.migrateLegacyEntitiesOnce()

        assertTrue(outcome is MigrationOutcome.Incomplete, "搬不完不能假装成功：$outcome")
        assertEquals(listOf(name), (outcome as MigrationOutcome.Incomplete).failed)
        assertFalse(store.isFullyMigrated(), "没搬完就不能写标记，否则下次启动不再重试")
    }

    @Test
    fun `恢复备份作废行表会清掉迁移标记`() {
        val name = schedule()
        resetTo(name, listOf(course("c1", "高等数学", name)))
        CourseStorage.install { store }
        repository.migrateLegacyEntitiesOnce()
        assertTrue(store.isFullyMigrated())

        store.invalidateAll()

        assertFalse(
            store.isFullyMigrated(),
            "恢复出的是另一套数据，标记必须一起清，否则新数据会被当成已经迁完",
        )
    }

    @Test
    fun `迁移同时把三个设置字段落进行`() {
        val name = schedule()
        resetTo(name, listOf(course("c1", "高等数学", name)))
        // 老用户形态：prefs 有值，行还没建
        repository.importAllPreferences(
            backupOf(name, listOf(course("c1", "高等数学", name))) + mapOf(
                "schedule_${name}_current_week" to 9,
                "schedule_${name}_total_weeks" to 17,
            ),
        )
        CourseStorage.install { store }

        repository.migrateLegacyEntitiesOnce()

        val row = store.settingsOf(name)
        assertEquals(9, row?.currentWeek, "当前周要跟着迁移进行")
        assertEquals(17, row?.totalWeeks, "总周数要跟着迁移进行")
    }

    /**
     * 迁移完成后，prefs 里那份「会过期的副本」必须被删掉。
     *
     * 这是「最干净」的判据：以前只是读路径表优先，盘上还留着课程 blob、三个设置镜像、
     * 作息 payload、绑定键 —— 看着像数据、其实是旧快照。
     */
    @Test
    fun `迁移完成后 prefs 里的实体残留键被清空`() {
        val name = schedule()
        repository.importAllPreferences(
            backupOf(name, listOf(course("c1", "高等数学", name))) + mapOf(
                "schedule_${name}_current_week" to 3,
                "schedule_${name}_total_weeks" to 18,
                "schedule_${name}_class_start_time" to "2026/09/01",
                "schedule_time_config_$name" to 1L,
                "time_config_1" to """{"name":"默认"}""",
                "time_config_ids" to "1",
            ),
        )
        CourseStorage.install { store }

        val outcome = repository.migrateLegacyEntitiesOnce()
        assertTrue(outcome is MigrationOutcome.Done, "先决条件：迁移必须成功：$outcome")

        // 表里该有的都在
        assertTrue(store.isImported(name))
        assertEquals(3, store.settingsOf(name)?.currentWeek)
        assertEquals(1L, store.bindingOf(name))
        assertEquals(listOf(1L), store.timeConfigs().map { it.id })

        // 盘上的副本全没了
        val remaining = AppStorage.store("course_schedule_prefs").all().keys
        val leftovers = remaining.filter { key ->
            key == "schedule_${name}_courses" ||
                key == "schedule_${name}_current_week" ||
                key == "schedule_${name}_total_weeks" ||
                key == "schedule_${name}_class_start_time" ||
                key == "schedule_time_config_$name" ||
                key == "time_config_1" ||
                key == "time_config_ids"
        }
        assertTrue(leftovers.isEmpty(), "迁移完成后这些键不该还在：$leftovers")
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun schedule() = "迁移-${System.nanoTime()}"

    /** 造一份「只有 blob、行表空」的备份并恢复进去 —— 正好是升级用户 / 恢复备份后的形态。 */
    private fun resetTo(name: String, courses: List<Course>) {
        repository.importAllPreferences(backupOf(name, courses))
    }

    private fun backupOf(name: String, courses: List<Course>): Map<String, Any> = mapOf(
        "schedule_names" to encodeStringList(listOf(name)),
        "schedule_folders" to "[]",
        "current_schedule_id" to name,
        "current_time_config_id" to 0L,
        "schedule_${name}_courses" to encodeCourses(courses),
    )

    private fun course(id: String, name: String, scheduleId: String) = Course(
        id = id,
        name = name,
        classroom = "A101",
        teacher = "张老师",
        dayOfWeek = 1,
        startSection = 1,
        endSection = 2,
        startWeek = 1,
        endWeek = 16,
        weekType = Course.WEEK_TYPE_ALL,
        colorRes = 0xFF4CAF50L,
        selectedWeeks = listOf(1, 2, 3),
        scheduleId = scheduleId,
        lastModified = 1_000L,
        note = "",
    )
}
