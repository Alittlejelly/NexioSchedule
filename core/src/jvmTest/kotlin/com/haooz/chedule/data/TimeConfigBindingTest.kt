package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 作息绑定：真相在 `schedule.time_config_id`，prefs 键是镜像，两边都断言。
 * ⚠ `CourseRepository` 是单例，prefs 在首次构造时钉死，测试类之间共用。
 */
class TimeConfigBindingTest {

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
        // CourseStorage 是全局单例，留着指向已关闭的连接会毒到后面的测试
        CourseStorage.install { null }
        repository.invalidateAllCaches()
        driver.close()
    }

    @Test
    fun `改绑写进行表，并留下 prefs 镜像`() {
        val a = schedule("绑定-甲")
        freshState(listOf(a))
        CourseStorage.install { store }
        val configId = repository.addTimeConfig(TimeConfig(name = a))

        repository.setScheduleTimeConfigId(a, configId)

        assertEquals(configId, store.bindingOf(a), "权威绑定必须落在 schedule 行上")
        assertEquals(
            configId,
            mirrorOf(a),
            "prefs 镜像不能省：备份是全量遍历 prefs 的，少了这个键恢复后所有课表会共用一套作息",
        )
        assertEquals(configId, repository.getScheduleTimeConfigId(a))
    }

    @Test
    fun `改名后绑定跟着行走，镜像也搬过去`() {
        val a = schedule("改名-旧")
        val b = schedule("改名-旁")
        val c = schedule("改名-新")
        freshState(listOf(a, b))
        CourseStorage.install { store }
        val configA = repository.addTimeConfig(TimeConfig(name = a))
        val configB = repository.addTimeConfig(TimeConfig(name = b))
        repository.setScheduleTimeConfigId(a, configA)
        repository.setScheduleTimeConfigId(b, configB)

        repository.renameSchedule(a, c)

        // 行上的绑定由 copyScheduleRow 连列搬走，不需要任何「按键名搬绑定」的补偿逻辑
        assertEquals(configA, store.bindingOf(c), "改名后绑定没跟着走")
        assertNull(store.bindingOf(a), "旧名字下不该还留着绑定行")
        assertNull(mirrorOf(a), "旧名字的镜像键要清掉，否则同名课表重建会撞上残留")
        assertEquals(configA, mirrorOf(c))
        assertEquals(configB, store.bindingOf(b), "改名不该动到别的课表")
    }

    @Test
    fun `删课表时绑定随行消失`() {
        val a = schedule("删除-甲")
        freshState(listOf(a))
        CourseStorage.install { store }
        val configId = repository.addTimeConfig(TimeConfig(name = a))
        repository.setScheduleTimeConfigId(a, configId)

        repository.deleteSchedule(a)

        assertNull(store.bindingOf(a), "课表行没了，绑定必须跟着没（CASCADE），否则同名重建会继承旧作息")
        assertNull(mirrorOf(a))
    }

    /**
     * 行表与镜像不一致时以行表为准。
     *
     * 这不是假想场景：镜像只在改绑时同步写，而行表在恢复备份后会被整体作废、再由读取重建；
     * 两次重建之间如果镜像被旧值覆盖（旧版本恢复），读路径必须认行表，否则改绑看起来「没生效」。
     */
    @Test
    fun `行表绑定优先于 prefs 镜像`() {
        val a = schedule("优先-甲")
        freshState(listOf(a))
        val configA = repository.addTimeConfig(TimeConfig(name = a))
        val configB = repository.addTimeConfig(TimeConfig(name = a + "-乙"))

        // 只有镜像（此刻还没装行表）
        repository.setScheduleTimeConfigId(a, configA)
        assertEquals(configA, mirrorOf(a))

        // 行表说另一套 —— 读路径必须信它
        CourseStorage.install { store }
        store.setBinding(anchorOf(a, configB))
        // 绕过仓库直接写库 = 别处写的数据，必须清缓存才能读到（缓存是仓库自己的一致性边界）
        repository.invalidateAllCaches()

        assertEquals(configB, repository.getScheduleTimeConfigId(a), "行表是权威，读路径不能回头信镜像")
    }

    /**
     * 恢复备份：行表被整体作废，绑定这一刻只在刚写回的镜像里。
     * 之后第一次读取课程会重建锚点行，绑定必须**如实落到行上**，不能凭空回退到旧全局指针。
     */
    @Test
    fun `恢复备份后绑定从镜像读回，并随首次读取落回行表`() {
        val a = schedule("恢复-甲")
        freshState(listOf(a))
        CourseStorage.install { store }
        val configA = repository.addTimeConfig(TimeConfig(name = a))
        repository.setScheduleTimeConfigId(a, configA)

        // 恢复一份「绑定还是 configA」的备份：行表整体作废
        repository.importAllPreferences(backupWithOnly(listOf(a), mapOf(a to configA)))
        assertNull(store.bindingOf(a), "恢复备份必须作废行表，否则读到的还是恢复前的课表")
        assertEquals(configA, repository.getScheduleTimeConfigId(a), "行没了就该回退镜像，而不是旧全局指针")

        // 首次读取课程 → 重建锚点行 → 绑定落回行表
        repository.addCourse(course(a, id = "binding-restore-1"))

        assertEquals(configA, store.bindingOf(a), "锚点行重建时没把镜像里的绑定写回去")
    }

    /**
     * 孤儿清理的前置条件已经可以去掉：判据改成「行表绑定 ∪ 镜像绑定 ∪ 旧全局指针」，
     * 未绑定的课表会回退到旧指针，而指针本身就在判据里，不存在误删在用的配置。
     */
    @Test
    fun `有课表未绑定时依然能清掉真正的孤儿配置`() {
        val a = schedule("孤儿-已绑")
        val b = schedule("孤儿-未绑")
        freshState(listOf(a, b))
        CourseStorage.install { store }
        val configA = repository.addTimeConfig(TimeConfig(name = a))
        val orphan = repository.addTimeConfig(TimeConfig(name = "没人要的"))
        repository.setScheduleTimeConfigId(a, configA)

        // b 故意不绑：它回退到旧全局指针，指针值在判据里
        assertTrue(orphan in repository.getTimeConfigIds(), "前置条件：孤儿配置应当存在")

        repository.pruneOrphanTimeConfigsIfNeeded()

        assertTrue(configA in repository.getTimeConfigIds(), "在用的配置被删了：$configA")
        assertTrue(orphan !in repository.getTimeConfigIds(), "孤儿配置没被清掉：$orphan")
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    /** 测试名带纳秒后缀：单例 prefs 在测试类之间共用，固定名字会互相污染。 */
    private fun schedule(prefix: String) = "$prefix-${System.nanoTime()}"

    /**
     * 把 store 清到「只含这些课表、且没有任何绑定」的确定状态。
     *
     * 必须走 [CourseRepository.importAllPreferences]：它是唯一会**同时**清掉
     * `schedule_*` / `time_config_*` / 绑定键的公开入口，还会顺带作废行表。
     */
    private fun freshState(names: List<String>) {
        repository.importAllPreferences(backupWithOnly(names, emptyMap()))
    }

    /**
     * 造一份「只含这些课表」的全量备份：保留 `time_config_*` 本体与 ids，
     * 其余 `schedule_*`（含各表课程、绑定、文件夹、名字）全部丢掉重写。
     * 不这么做的话，store 里其它测试留下的课表会让结构校验报
     * 「course data belongs to an unknown schedule」。
     */
    private fun backupWithOnly(
        names: List<String>,
        bindings: Map<String, Long>,
    ): MutableMap<String, Any> {
        val cleaned = repository.exportAllPreferences()
            .filterKeys { !it.startsWith("schedule_") }
            .toMutableMap()
        cleaned["schedule_names"] = encodeStringList(names)
        cleaned["schedule_folders"] = "[]"
        cleaned["current_schedule_id"] = names.first()
        cleaned["current_time_config_id"] = bindings[names.first()] ?: 0L
        names.forEach { cleaned["schedule_${it}_courses"] = "[]" }
        bindings.forEach { (schedule, id) -> cleaned["schedule_time_config_$schedule"] = id }
        return cleaned
    }

    private fun mirrorOf(scheduleId: String): Long? =
        (repository.exportAllPreferences()["schedule_time_config_$scheduleId"] as? Number)?.toLong()

    private fun anchorOf(scheduleId: String, timeConfigId: Long) = CourseRowAnchor(
        scheduleId = scheduleId,
        timeConfigId = timeConfigId,
        timeConfigName = scheduleId,
        timeConfigPayload = """{"name":"$scheduleId"}""",
        sortOrder = 0,
        currentWeek = 1,
        totalWeeks = 20,
        classStart = null,
    )

    private fun course(scheduleId: String, id: String) = Course(
        id = id,
        name = "测试课",
        classroom = "A101",
        teacher = "张老师",
        dayOfWeek = 1,
        startSection = 1,
        endSection = 2,
        startWeek = 1,
        endWeek = 16,
        weekType = Course.WEEK_TYPE_ALL,
        colorRes = 0xFF4CAF50L,
        selectedWeeks = emptyList(),
        scheduleId = scheduleId,
        lastModified = 1_000L,
        note = "",
    )
}
