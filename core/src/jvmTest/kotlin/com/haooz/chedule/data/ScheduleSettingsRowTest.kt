package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `schedule` 行上的 current_week / total_weeks / class_start：行是真相，prefs 键是写穿镜像。
 *
 * 覆盖三条容易退化的路径：老数据的一次性同步、写设置的双写、导入后标记失效带来的重同步。
 *
 * ⚠ 单例 prefs 在测试类之间共用，课表名一律带纳秒后缀。
 */
class ScheduleSettingsRowTest {

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
    fun `写周次同时落行与镜像`() {
        val name = schedule("周次")
        freshState(name)
        CourseStorage.install { store }
        seedRow(name, currentWeek = 3, totalWeeks = 18, classStart = "2026/09/01")

        repository.setCurrentWeek(7)
        repository.setTotalWeeks(name, 16)
        repository.setClassStartTime(name, "2026/09/08")

        val row = assertNotNull(store.settingsOf(name), "设置必须落在 schedule 行上")
        assertEquals(7, row.currentWeek)
        assertEquals(16, row.totalWeeks)
        assertEquals("2026/09/08", row.classStart)
        assertEquals(7, repository.getCurrentWeek(name))
        // 镜像不能省：备份是遍历 prefs 导出的
        assertEquals(7, mirrorOf(name, "current_week"))
        assertEquals(16, mirrorOf(name, "total_weeks"))
        assertEquals("2026/09/08", mirrorOf(name, "class_start_time"))
    }

    @Test
    fun `行是真相，镜像被改也不影响读取`() {
        val name = schedule("真相")
        freshState(name)
        CourseStorage.install { store }
        seedRow(name, currentWeek = 5, totalWeeks = 20, classStart = "2026/09/01")
        store.markSettingsSynced(name)
        // 只改行（镜像里仍是建行时的 5）；读路径若回头信镜像就会拿到 5
        store.writeSettings(name, 12, null, null)

        assertEquals(12, repository.getCurrentWeek(name), "读路径必须认行，不能回头信镜像")
    }

    @Test
    fun `老数据首次读取时把 prefs 同步进行`() {
        val name = schedule("老数据")
        freshState(name)
        CourseStorage.install { store }
        // 行已存在（seedRow 只建行、不写同步标记），字段是建行时的快照
        seedRow(name, currentWeek = 1, totalWeeks = 20, classStart = null)
        // 再让 repository 写出一套正确值（prefs + 行），最后把行改回旧快照：
        // 这就是升级用户的形态 —— prefs 是对的，行里是旧值，且没有同步标记。
        repository.setCurrentWeek(9)
        repository.setTotalWeeks(name, 17)
        repository.setClassStartTime(name, "2026/09/01")
        store.writeSettings(name, 1, 20, null)
        repository.invalidateAllCaches()

        assertEquals(
            9,
            repository.getCurrentWeek(name),
            "首次读取要用 prefs 的值覆盖行里的旧快照。" +
                " synced=${store.isSettingsSynced(name)} row=${store.settingsOf(name)}" +
                " prefs=${repository.exportAllPreferences()["schedule_${name}_current_week"]}",
        )
        assertEquals(17, repository.getTotalWeeks(name))
        assertEquals("2026/09/01", repository.getClassStartTime(name))
        assertTrue(store.isSettingsSynced(name), "同步后要留标记，之后不再覆盖")
    }

    @Test
    fun `清除导入标记后设置会重新从 prefs 同步`() {
        val name = schedule("重同步")
        freshState(name)
        CourseStorage.install { store }
        seedRow(name, currentWeek = 1, totalWeeks = 20, classStart = null)
        repository.setCurrentWeek(3)
        store.markSettingsSynced(name)
        assertEquals(3, repository.getCurrentWeek(name))

        // 单表导入的形态：prefs 被改成 11，行还是旧的 3，然后清标记等重建
        repository.setCurrentWeek(11)
        store.writeSettings(name, 3, null, null)
        repository.invalidateAllCaches()
        store.markStale(name)
        repository.invalidateAllCaches()

        assertEquals(11, repository.getCurrentWeek(name), "markStale 必须连设置同步标记一起清")
    }

    @Test
    fun `行不存在时写设置会补建行`() {
        val name = schedule("补建")
        freshState(name)
        CourseStorage.install { store }
        assertNull(store.settingsOf(name), "前置条件：还没有 schedule 行")

        repository.setCurrentWeek(4)

        val row = assertNotNull(store.settingsOf(name), "写设置应当补建 anchor 行")
        assertEquals(4, row.currentWeek)
        assertEquals(4, repository.getCurrentWeek(name))
    }

    @Test
    fun `改名把设置同步标记一起搬走`() {
        val old = schedule("改名前")
        freshState(old)
        CourseStorage.install { store }
        seedRow(old, currentWeek = 6, totalWeeks = 20, classStart = "2026/09/01")
        store.markSettingsSynced(old)

        val new = "$old-新"
        store.renameSchedule(old, new)

        assertNull(store.settingsOf(old), "旧名字下行应该没了")
        assertEquals(6, store.settingsOf(new)?.currentWeek, "设置随行搬走")
        assertTrue(store.isSettingsSynced(new), "标记也要搬，否则新名字会再同步一次旧值")
        // 搬完不应该被 prefs 覆盖回去
        assertEquals(6, repository.getCurrentWeek(new))
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun schedule(prefix: String) = "$prefix-${System.nanoTime()}"

    private fun freshState(name: String) {
        val cleaned = repository.exportAllPreferences()
            .filterKeys { !it.startsWith("schedule_") }
            .toMutableMap()
        cleaned["schedule_names"] = encodeStringList(listOf(name))
        cleaned["schedule_folders"] = "[]"
        cleaned["current_schedule_id"] = name
        cleaned["schedule_${name}_courses"] = "[]"
        repository.importAllPreferences(cleaned)
    }

    /** 直接建一行 schedule（不经过课程写入），模拟「行已存在但字段是建行时快照」。 */
    private fun seedRow(name: String, currentWeek: Int, totalWeeks: Int, classStart: String?) {
        val configId = repository.addTimeConfig(TimeConfig(name = name))
        store.setBinding(
            CourseRowAnchor(
                scheduleId = name,
                timeConfigId = configId,
                timeConfigName = name,
                timeConfigPayload = """{"name":"$name"}""",
                sortOrder = 0,
                currentWeek = currentWeek,
                totalWeeks = totalWeeks,
                classStart = classStart,
            )
        )
    }

    private fun mirrorOf(scheduleId: String, key: String): Any? =
        repository.exportAllPreferences()["schedule_${scheduleId}_$key"]
}
