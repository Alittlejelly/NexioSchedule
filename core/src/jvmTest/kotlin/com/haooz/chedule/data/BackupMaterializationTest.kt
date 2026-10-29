package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 备份必须以表为准。
 *
 * 表成为真相之后，prefs 镜像只是「最近一次成功写入」的快照：建锚点、写镜像失败被吞掉，
 * 都会让镜像落后。而恢复只认备份，镜像一落后就是「备份 → 恢复」丢数据。
 */
class BackupMaterializationTest {

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
        CourseStorage.install { store }
        repository.invalidateAllCaches()
    }

    @AfterTest
    fun tearDown() {
        CourseStorage.install { null }
        repository.invalidateAllCaches()
        driver.close()
    }

    @Test
    fun `备份里的作息来自表，含只写进表的配置`() {
        val name = schedule("备份作息")
        freshState(name)
        val id = repository.addTimeConfig(config(name, morning = 4))
        // 只改表：镜像仍是 4
        store.upsertTimeConfig(StoredTimeConfig(id, name, encodeTimeConfig(config(name, morning = 6))))
        // 只存在于表、镜像里没有的配置
        store.upsertTimeConfig(StoredTimeConfig(SEED_ID, name, encodeTimeConfig(config(name, morning = 3))))

        val backup = repository.exportAllPreferences()

        assertEquals(6, configOf(backup["time_config_$id"]).morningSections, "备份必须用表里的值")
        assertEquals(
            listOf(id, SEED_ID).sorted(),
            (backup["time_config_ids"] as String).split(",").map { it.toLong() }.sorted(),
            "ids 也要来自表，否则恢复后只存在于表的配置会丢",
        )
        assertTrue((backup["time_config_$SEED_ID"] as? String).orEmpty().isNotEmpty())
    }

    @Test
    fun `备份里的周次与总周数来自表`() {
        val name = schedule("备份设置")
        freshState(name)
        seedRow(name)
        store.markSettingsSynced(name)
        repository.setCurrentWeek(3)
        repository.setTotalWeeks(name, 18)
        // 只改表：镜像仍是 3 / 18
        store.writeSettings(name, currentWeek = 5, totalWeeks = 16, classStart = null)

        val backup = repository.exportAllPreferences()

        assertEquals(5, backup["schedule_${name}_current_week"], "备份必须用表里的当前周")
        assertEquals(16, backup["schedule_${name}_total_weeks"], "备份必须用表里的总周数")
    }

    @Test
    fun `表不可用时备份退回镜像`() {
        val name = schedule("无表")
        freshState(name)
        val id = repository.addTimeConfig(config(name, morning = 4))
        repository.setCurrentWeek(3)

        CourseStorage.install { null }
        repository.invalidateAllCaches()
        val backup = repository.exportAllPreferences()

        assertEquals(4, configOf(backup["time_config_$id"]).morningSections)
        assertEquals(3, backup["schedule_${name}_current_week"])
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun schedule(prefix: String) = "$prefix-${System.nanoTime()}"

    private fun config(name: String, morning: Int) = TimeConfig(
        id = 0L,
        name = name,
        morningSections = morning,
        afternoonSections = 4,
        eveningSections = 4,
    )

    private fun configOf(raw: Any?): TimeConfig =
        TimeConfig.sanitize(0L, assertNotNull(parseTimeConfigSnapshotOrNull(assertNotNull(raw as? String))))

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

    private fun seedRow(name: String) {
        val configId = repository.addTimeConfig(TimeConfig(name = name))
        store.setBinding(
            CourseRowAnchor(
                scheduleId = name,
                timeConfigId = configId,
                timeConfigName = name,
                timeConfigPayload = """{"name":"$name"}""",
                sortOrder = 0,
                currentWeek = 1,
                totalWeeks = 20,
                classStart = null,
            )
        )
    }

    private companion object {
        const val SEED_ID = 90L
    }
}
