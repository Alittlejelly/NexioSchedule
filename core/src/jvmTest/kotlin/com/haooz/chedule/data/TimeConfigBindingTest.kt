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
        driver = openInMemoryDriver()
        NexioDatabase.Schema.create(driver)
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
    fun `改绑写进行表，并出现在备份里`() {
        val a = schedule("绑定-甲")
        freshState(listOf(a))
        CourseStorage.install { store }
        val configId = repository.addTimeConfig(TimeConfig(name = a))

        repository.setScheduleTimeConfigId(a, configId)

        assertEquals(configId, store.bindingOf(a), "权威绑定必须落在 schedule 行上")
        assertEquals(
            configId,
            mirrorOf(a),
            "备份必须带出这个绑定：导出是按表物化的，少了键恢复后所有课表会共用一套作息",
        )
        assertEquals(configId, repository.getScheduleTimeConfigId(a))
    }

    /**
     * 同名改名必须是空操作。
     *
     * 改名的 prefs 搬运会把每个键 put 到新键名再 remove 旧键：新旧同名时等于「写完自己删掉」，
     * 一次同名改名就能把这张课表在 prefs 里的残留键清空。UI 挡了，存储层也得挡。
     */
    @Test
    fun `同名改名是空操作`() {
        val a = schedule("同名")
        freshState(listOf(a))
        CourseStorage.install { store }
        val configId = repository.addTimeConfig(TimeConfig(name = a))
        repository.setScheduleTimeConfigId(a, configId)
        repository.addCourse(course(a, id = "same-name-1"))

        repository.renameSchedule(a, a)

        assertEquals(listOf(a), repository.getScheduleNames(), "名字不该变")
        assertEquals(configId, store.bindingOf(a), "同名改名不能把行搬丢")
        assertEquals(listOf("same-name-1"), store.load(a).map { it.id }, "课程也不该受影响")
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
     * 绑定只有行表一个来源。
     *
     * prefs 的 `schedule_time_config_{名}` 是迁移源，迁移完成即删；读路径不再看它，
     * 所以「行表与镜像不一致」这种状态本身就不存在了。
     */
    @Test
    fun `绑定只认行表`() {
        val a = schedule("优先-甲")
        freshState(listOf(a))
        repository.addTimeConfig(TimeConfig(name = a))
        val configB = repository.addTimeConfig(TimeConfig(name = a + "-乙"))

        CourseStorage.install { store }
        store.setBinding(anchorOf(a, configB))
        // 绕过仓库直接写库 = 别处写的数据，必须清缓存才能读到（缓存是仓库自己的一致性边界）
        repository.invalidateAllCaches()

        assertEquals(configB, repository.getScheduleTimeConfigId(a))
        assertEquals(configB, store.bindingOf(a))
        assertEquals(configB, mirrorOf(a), "备份里的绑定也必须来自表")
    }

    /**
     * 恢复备份：行表先整体作废，再**当场**按备份重建成表。
     *
     * 没有「等第一次读课程再重建」的窗口 —— 那段窗口里读表只能读到空的。
     */
    @Test
    fun `恢复备份后绑定当场落回行表`() {
        val a = schedule("恢复-甲")
        freshState(listOf(a))
        CourseStorage.install { store }
        val configA = repository.addTimeConfig(TimeConfig(name = a))
        repository.setScheduleTimeConfigId(a, configA)

        repository.importAllPreferences(backupWithOnly(listOf(a), mapOf(a to configA)))

        assertEquals(configA, store.bindingOf(a), "恢复完绑定就该在行上")
        assertEquals(configA, repository.getScheduleTimeConfigId(a), "不能回退到旧全局指针")

        // 继续写课程不能把绑定弄丢
        repository.addCourse(course(a, id = "binding-restore-1"))
        assertEquals(configA, store.bindingOf(a), "写课程不能覆盖掉绑定")
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
