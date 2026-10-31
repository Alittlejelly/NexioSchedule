package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.backup.encodeStringList
import com.haooz.chedule.data.db.NexioDatabase
import com.haooz.chedule.data.model.TimeConfig
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.CourseRowAnchor
import com.haooz.chedule.data.storage.CourseStorage
import com.haooz.chedule.data.storage.InMemoryKeyValueStore
import com.haooz.chedule.data.storage.SqliteCourseRowStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * current_week / total_weeks / class_start：行是真相，prefs 键是写穿镜像。
 * ⚠ 单例 prefs 在测试类之间共用，课表名一律带纳秒后缀。
 */
class ScheduleSettingsRowTest {

    private lateinit var repository: com.haooz.chedule.data.repo.CourseRepository
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqliteCourseRowStore

    @BeforeTest
    fun setUp() {
        AppStorage.init { InMemoryKeyValueStore() }
        repository = com.haooz.chedule.data.repo.CourseRepository.getInstance()
        driver = openInMemoryDriver()
        NexioDatabase.Schema.create(driver)
        store = SqliteCourseRowStore(NexioDatabase(driver))
    }

    @AfterTest
    fun tearDown() {
        CourseStorage.install { null }
        repository.invalidateAllCaches()
        driver.close()
    }

    @Test
    fun `写周次只落行，备份仍能导出`() {
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
        // 不再写 prefs 镜像：备份的值一律从表物化，所以这里必须仍然正确
        assertEquals(7, backupValueOf(name, "current_week"))
        assertEquals(16, backupValueOf(name, "total_weeks"))
        assertEquals("2026/09/08", backupValueOf(name, "class_start_time"))
    }

    @Test
    fun `行是真相，镜像被改也不影响读取`() {
        val name = schedule("真相")
        freshState(name)
        CourseStorage.install { store }
        seedRow(name, currentWeek = 5, totalWeeks = 20, classStart = "2026/09/01")
        // 只改行（镜像里仍是建行时的值）；读路径若回头信镜像就会拿到旧值
        store.writeSettings(name, 12, null, null)

        assertEquals(12, repository.getCurrentWeek(name), "读路径必须认行，不能回头信镜像")
    }

    @Test
    fun `行不存在时写设置会补建行`() {
        val name = schedule("补建")
        freshState(name)
        CourseStorage.install { store }
        // 恢复/迁移会建行，这里手动删掉，造出「新表还没有行」的形态
        store.deleteSchedule(name)
        repository.invalidateAllCaches()
        assertNull(store.settingsOf(name), "前置条件：还没有 schedule 行")

        repository.setCurrentWeek(4)

        val row = assertNotNull(store.settingsOf(name), "写设置应当补建 anchor 行")
        assertEquals(4, row.currentWeek)
        assertEquals(4, repository.getCurrentWeek(name))
    }

    @Test
    fun `改名把设置一起搬走`() {
        val old = schedule("改名前")
        CourseStorage.install { store }
        freshState(old, currentWeek = 6, totalWeeks = 20, classStart = "2026/09/01")
        assertEquals(6, store.settingsOf(old)?.currentWeek, "前置条件：迁移已经把设置落进行")

        val new = "$old-新"
        store.renameSchedule(old, new)

        assertNull(store.settingsOf(old), "旧名字下行应该没了")
        assertEquals(6, store.settingsOf(new)?.currentWeek, "设置随行搬走")
        // 搬完不应该被 prefs 覆盖回去
        repository.invalidateAllCaches()
        assertEquals(6, repository.getCurrentWeek(new))
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun schedule(prefix: String) = "$prefix-${System.nanoTime()}"

    /**
     * 清空课表并写入一条 `name` 的课表。带设置参数时同时把值写进 prefs ——
     * 模拟「升级用户 prefs 里有值」的形态（新代码不再写穿 prefs，只能从这里造）。
     */
    private fun freshState(
        name: String,
        currentWeek: Int? = null,
        totalWeeks: Int? = null,
        classStart: String? = null,
    ) {
        val cleaned = repository.exportAllPreferences()
            // 作息也从零开始：留着上一轮的 time_config_* 会被恢复进表
            .filterKeys { !it.startsWith("schedule_") && !it.startsWith("time_config_") }
            .toMutableMap()
        cleaned["schedule_names"] = encodeStringList(listOf(name))
        cleaned["schedule_folders"] = "[]"
        cleaned["current_schedule_id"] = name
        cleaned["schedule_${name}_courses"] = "[]"
        currentWeek?.let { cleaned["schedule_${name}_current_week"] = it }
        totalWeeks?.let { cleaned["schedule_${name}_total_weeks"] = it }
        classStart?.let { cleaned["schedule_${name}_class_start_time"] = it }
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

    /** 备份里这个键的值。镜像已不再写，所以这是「备份是否正确」而不是「prefs 里有什么」。 */
    private fun backupValueOf(scheduleId: String, key: String): Any? =
        repository.exportAllPreferences()["schedule_${scheduleId}_$key"]
}
