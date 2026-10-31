package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.backup.encodeStringList
import com.haooz.chedule.data.backup.encodeTimeConfig
import com.haooz.chedule.data.backup.parseTimeConfigSnapshotOrNull
import com.haooz.chedule.data.db.NexioDatabase
import com.haooz.chedule.data.model.TimeConfig
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.CourseRowAnchor
import com.haooz.chedule.data.storage.CourseStorage
import com.haooz.chedule.data.storage.InMemoryKeyValueStore
import com.haooz.chedule.data.storage.SqliteCourseRowStore
import com.haooz.chedule.data.storage.StoredTimeConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 备份必须以表为准：镜像只是「最近一次成功写入」的快照，会落后，而恢复只认备份。
 */
class BackupMaterializationTest {

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
        store.upsertTimeConfig(
            StoredTimeConfig(
                id,
                name,
                encodeTimeConfig(config(name, morning = 6))
            )
        )
        // 只存在于表、镜像里没有的配置
        store.upsertTimeConfig(
            StoredTimeConfig(
                SEED_ID,
                name,
                encodeTimeConfig(config(name, morning = 3))
            )
        )

        val backup = repository.exportAllPreferences()

        assertEquals(6, configOf(backup["time_config_$id"]).morningSections, "备份必须用表里的值")
        val backupIds = (backup["time_config_ids"] as String).split(",").map { it.toLong() }
        assertTrue(
            backupIds.containsAll(listOf(id, SEED_ID)),
            "ids 也要来自表，否则恢复后只存在于表的配置会丢：$backupIds",
        )
        assertTrue((backup["time_config_$SEED_ID"] as? String).orEmpty().isNotEmpty())
    }

    @Test
    fun `备份里的周次与总周数来自表`() {
        val name = schedule("备份设置")
        freshState(name)
        seedRow(name)
        repository.setCurrentWeek(3)
        repository.setTotalWeeks(name, 18)
        // 只改表：镜像仍是 3 / 18
        store.writeSettings(name, currentWeek = 5, totalWeeks = 16, classStart = null)

        val backup = repository.exportAllPreferences()

        assertEquals(5, backup["schedule_${name}_current_week"], "备份必须用表里的当前周")
        assertEquals(16, backup["schedule_${name}_total_weeks"], "备份必须用表里的总周数")
    }

    @Test
    fun `没有行表时写入不落盘，读取取默认值`() {
        val name = schedule("无表")
        freshState(name)
        // 三端都装了行表；拿不到行表只可能是接线漏了。这时不做任何降级，写失败、读默认。
        CourseStorage.install { null }
        repository.invalidateAllCaches()
        repository.setCurrentWeek(3)

        assertEquals(1, repository.getCurrentWeek(name), "没有行表就读默认值，不回看 prefs")
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
            // 作息也从零开始：留着上一轮的 time_config_* 会被恢复进表，id 断言全乱
            .filterKeys { !it.startsWith("schedule_") && !it.startsWith("time_config_") }
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
