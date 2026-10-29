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
 * 作息配置：`time_config` 表是真相，prefs 的 `time_config_{id}` 与 `time_config_ids` 是镜像。
 * 重点是老 payload 的一次性同步、删除时必须连表行一起删。
 */
class TimeConfigRowTest {

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
    fun `新增配置同时落表与镜像`() {
        val name = schedule("作息")
        val id = repository.addTimeConfig(config(name, morning = 6))

        val row = assertNotNull(
            store.timeConfigs().firstOrNull { it.id == id },
            "作息必须落进 time_config 表",
        )
        assertEquals(6, configOf(row.payload).morningSections)
        assertEquals(6, repository.getTimeConfig(id).morningSections)
        // 镜像不能省：备份是全量遍历 prefs 的
        assertEquals(6, mirrorOf(id).morningSections)
    }

    @Test
    fun `表是真相，镜像被改也不影响读取`() {
        val name = schedule("真相")
        val id = repository.addTimeConfig(config(name, morning = 6))

        // 直接改表，镜像不动；读路径若回头信镜像就会拿到 6
        store.upsertTimeConfig(StoredTimeConfig(id, name, encodeTimeConfig(config(name, morning = 5))))
        repository.invalidateAllCaches()

        assertEquals(5, repository.getTimeConfig(id).morningSections)
    }

    @Test
    fun `改作息会同时更新表与镜像`() {
        val name = schedule("改写")
        val id = repository.addTimeConfig(config(name, morning = 4))
        repository.saveTimeConfig(repository.getTimeConfig(id).copy(morningSections = 5))

        assertEquals(5, store.timeConfigs().first { it.id == id }.let { configOf(it.payload).morningSections })
        assertEquals(5, mirrorOf(id).morningSections)
        // 清缓存后从表重建，仍然一致
        repository.invalidateAllCaches()
        assertEquals(5, repository.getTimeConfig(id).morningSections)
    }

    @Test
    fun `老数据首次访问时把 prefs 同步进表`() {
        val name = schedule("老作息")
        val stale = config(name, morning = 1)
        // 表里先放一份过期快照（建锚点那一刻写进去的）
        store.upsertTimeConfig(StoredTimeConfig(SEED_ID, name, encodeTimeConfig(stale)))
        // prefs 里是用户后来改过的值
        writeOnlyPrefs(name, morning = 6)

        repository.invalidateAllCaches()
        assertEquals(6, repository.getTimeConfig(SEED_ID).morningSections, "首次访问要用 prefs 覆盖表的旧快照")
        assertEquals(6, store.timeConfigs().first { it.id == SEED_ID }.let { configOf(it.payload).morningSections })
        assertTrue(store.areTimeConfigsSynced(), "同步后要留标记，之后不再覆盖")
    }

    @Test
    fun `清理孤儿配置时连表行一起删`() {
        val name = schedule("孤儿")
        freshState(name)
        val bound = repository.addTimeConfig(config(name, morning = 4))
        repository.setScheduleTimeConfigId(name, bound)
        val orphan = repository.addTimeConfig(config("$name-未绑定", morning = 3))
        repository.setCurrentTimeConfigId(bound)

        repository.pruneOrphanTimeConfigsIfNeeded()

        assertNull(
            store.timeConfigs().firstOrNull { it.id == orphan },
            "只删 prefs 键不删表行，配置会在下次读取时复活",
        )
        assertNotNull(store.timeConfigs().firstOrNull { it.id == bound }, "在用配置不能删")
    }

    @Test
    fun `hydrate 直读表`() {
        store.upsertTimeConfig(StoredTimeConfig(SEED_ID, "x", encodeTimeConfig(config("x", morning = 5))))

        store.markTimeConfigsSynced()
        repository.invalidateAllCaches()

        assertEquals(5, repository.getTimeConfig(SEED_ID).morningSections)
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

    private fun config(name: String, morning: Int) = TimeConfig(
        id = 0L,
        name = name,
        morningSections = morning,
        afternoonSections = 4,
        eveningSections = 4,
    )

    private fun configOf(payload: String): TimeConfig =
        TimeConfig.sanitize(0L, assertNotNull(parseTimeConfigSnapshotOrNull(payload)))

    private fun mirrorOf(id: Long): TimeConfig =
        configOf(assertNotNull(repository.exportAllPreferences()["time_config_$id"] as? String))

    /** 只写 prefs 镜像（含 ids），不碰表。用来模拟旧版本留下的数据。 */
    private fun writeOnlyPrefs(name: String, morning: Int) {
        // 过滤掉所有旧课表的键，否则结构校验会报「course data belongs to an unknown schedule」
        val backup = repository.exportAllPreferences()
            .filterKeys { !it.startsWith("schedule_") }
            .toMutableMap()
        backup["schedule_names"] = encodeStringList(listOf(name))
        backup["schedule_folders"] = "[]"
        backup["current_schedule_id"] = name
        backup["schedule_${name}_courses"] = "[]"
        backup["current_time_config_id"] = SEED_ID
        backup["time_config_ids"] = SEED_ID.toString()
        backup["time_config_$SEED_ID"] = encodeTimeConfig(config(name, morning))
        repository.importAllPreferences(backup)
    }

    private companion object {
        const val SEED_ID = 7L
    }
}
