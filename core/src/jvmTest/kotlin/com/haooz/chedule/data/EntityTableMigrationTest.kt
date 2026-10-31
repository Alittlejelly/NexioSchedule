package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 一次性迁移（标记 `entity_tables_version`，见 [SqliteCourseRowStore.ENTITY_MIGRATION_VERSION]）：
 * 三端启动各调一次，跑完写全局标记。
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
        // 规则是「顶层对象」格式，不能随手写 "[]"（decode 会拒）
        val reorgRaw = TeachingWeekReorganization.encode(emptyList(), Int.MAX_VALUE)
        repository.importAllPreferences(
            backupOf(name, listOf(course("c1", "高等数学", name))) + mapOf(
                "schedule_${name}_current_week" to 3,
                "schedule_${name}_total_weeks" to 18,
                "schedule_${name}_class_start_time" to "2026/09/01",
                "schedule_${name}_smart_weekend" to true,
                "schedule_${name}_show_non_current_week" to false,
                "schedule_${name}_teaching_week_reorganizations" to reorgRaw,
                "schedule_folders" to encodeScheduleFolders(
                    listOf(ScheduleFolder(id = "f1", name = "学期", schedules = listOf(name))),
                ),
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
        assertEquals(true, store.displaySettingsOf(name)?.smartWeekend)
        assertEquals(false, store.displaySettingsOf(name)?.showNonCurrentWeek)
        assertEquals(reorgRaw, store.teachingWeekReorganizationsOf(name))
        assertEquals(listOf("f1"), store.folders().map { it.id })

        // 盘上的副本全没了
        val remaining = AppStorage.store("course_schedule_prefs").all().keys
        val leftovers = remaining.filter { key ->
            key == "schedule_${name}_courses" ||
                key == "schedule_${name}_current_week" ||
                key == "schedule_${name}_total_weeks" ||
                key == "schedule_${name}_class_start_time" ||
                key == "schedule_${name}_smart_weekend" ||
                key == "schedule_${name}_show_non_current_week" ||
                key == "schedule_${name}_teaching_week_reorganizations" ||
                key == "schedule_folders" ||
                key == "schedule_time_config_$name" ||
                key == "time_config_1" ||
                key == "time_config_ids"
        }
        assertTrue(leftovers.isEmpty(), "迁移完成后这些键不该还在：$leftovers")
    }

    /**
     * 1.6.7 的「课表级作息」键（`morning_start` / `long_break_*` / `section_times` …）在作息搬进
     * `time_config` 表之后就没有读者了，迁移时顺手清掉 —— 不清就会跟着备份一起导出、恢复时再写回来。
     *
     * 但 `active_routine` 还活着（`syncRoutineAfterDateChange` 记上次生效的作息），不能误删。
     */
    @Test
    fun `迁移顺手清掉课表级作息的死键，活键保留`() {
        val name = schedule()
        val deadKeys = listOf(
            "morning_start",
            "long_break_morning_section",
            "section_times",
            "today_show_wallpaper",
        )
        repository.importAllPreferences(
            backupOf(name, listOf(course("c1", "高等数学", name))) + mapOf(
                "schedule_${name}_morning_start" to 8,
                "schedule_${name}_long_break_morning_section" to 2,
                "schedule_${name}_section_times" to "{}",
                "schedule_${name}_today_show_wallpaper" to true,
                "schedule_${name}_active_routine" to "1:1",
                "schedule_${name}__settings_last_modified" to 123L,
            ),
        )
        CourseStorage.install { store }

        repository.migrateLegacyEntitiesOnce()

        // 用导出代替直读 prefs：仓库单例的 prefs 在首次构造时钉死，@BeforeTest 里
        // AppStorage.init 之后 store(name) 拿到的是另一个空实例（见测试陷阱）。
        // 死键和 active_routine 都不在「从表物化」的清单里，导出看到的就是 prefs 原样。
        val keys = repository.exportAllPreferences().keys
        val leftovers = deadKeys.map { "schedule_${name}_$it" }.filter { it in keys }
        assertTrue(leftovers.isEmpty(), "死键没清干净：$leftovers（全部键=$keys）")
        assertTrue(
            "schedule_${name}_active_routine" in keys,
            "active_routine 是活键（跨日期切换要读它），不能当死键删",
        )
        assertTrue(
            "schedule_${name}__settings_last_modified" in keys,
            "设置时间戳是活键",
        )
    }

    /**
     * 迁移是**幂等**的：`ENTITY_MIGRATION_VERSION` +1 会让所有用户重跑一遍，
     * 已经绑好作息的课表不能因此多出一份作息、也不该被改绑。
     */
    @Test
    fun `重跑迁移不会给已绑定的课表再发一套作息`() {
        val name = schedule()
        repository.importAllPreferences(
            backupOf(name, listOf(course("c1", "高等数学", name))) + mapOf(
                "schedule_${name}_current_week" to 5,
            ),
        )
        CourseStorage.install { store }
        repository.migrateLegacyEntitiesOnce()
        val configsBefore = store.timeConfigs().map { it.id }.sorted()
        val bindingBefore = store.bindingOf(name)
        assertNotNull(bindingBefore, "先决条件：迁移后该课表已有绑定")

        // 模拟「版本 +1 之后老用户重跑」：只抹掉版本标记，其余数据不动
        NexioDatabase(driver).scheduleQueries.deleteMeta("entity_tables_version")
        val outcome = repository.migrateLegacyEntitiesOnce()

        assertTrue(outcome is MigrationOutcome.Done, "重跑必须成功：$outcome")
        assertEquals(configsBefore, store.timeConfigs().map { it.id }.sorted(), "重跑不该再复制一份作息")
        assertEquals(bindingBefore, store.bindingOf(name), "重跑不该把绑定改到别的配置上")
    }

    /**
     * 1.6.7 留下的标记是**布尔**语义（`entity_tables_v1 = "1"`）。1.6.8 新增了三块要搬的数据，
     * 如果继续认这个标记，老用户会直接被判成「已迁完」，新块永远不执行 ——
     * 而读路径已经表优先，表现就是「升级后智能周末被关、显示非本周被开、文件夹消失」。
     */
    @Test
    fun `1_6_7 的旧标记不算已迁完，新增的三块会重跑`() {
        val name = schedule()
        repository.importAllPreferences(
            backupOf(name, listOf(course("c1", "高等数学", name))) + mapOf(
                "schedule_${name}_smart_weekend" to true,
                "schedule_${name}_show_non_current_week" to false,
                "schedule_folders" to encodeScheduleFolders(
                    listOf(ScheduleFolder(id = "f1", name = "学期", schedules = listOf(name))),
                ),
            ),
        )
        // 模拟 1.6.7：只写了旧键，没有版本号
        NexioDatabase(driver).scheduleQueries.writeMeta("entity_tables_v1", "1")
        CourseStorage.install { store }

        val outcome = repository.migrateLegacyEntitiesOnce()

        assertTrue(outcome is MigrationOutcome.Done, "旧标记必须被当成「未迁完」：$outcome")
        assertEquals(true, store.displaySettingsOf(name)?.smartWeekend, "智能周末要被搬进行")
        assertEquals(false, store.displaySettingsOf(name)?.showNonCurrentWeek)
        assertEquals(listOf("f1"), store.folders().map { it.id }, "文件夹要被搬进表")
    }

    /**
     * 备份导出的往返：导出 → 恢复 → 再导出，两次必须一模一样。
     *
     * 这条守着「读路径表优先了，但导出还在自己拼 prefs 键」这类错位 ——
     * 迁移会把 prefs 副本删掉，导出再拼键就只能是「备份里永远缺这一块」。
     */
    @Test
    fun `备份导出带上显示开关与重组规则，恢复往返不丢`() {
        val name = schedule()
        val reorgRaw = TeachingWeekReorganization.encode(emptyList(), Int.MAX_VALUE)
        repository.importAllPreferences(
            backupOf(name, listOf(course("c1", "高等数学", name))) + mapOf(
                "schedule_${name}_current_week" to 5,
                // 有重组规则就必须有合法开课日，恢复校验会拒（见 validateFullScheduleBackupStructure）
                "schedule_${name}_class_start_time" to "2026/09/01",
                "schedule_${name}_smart_weekend" to true,
                "schedule_${name}_show_non_current_week" to false,
                "schedule_${name}_teaching_week_reorganizations" to reorgRaw,
            ),
        )
        CourseStorage.install { store }
        // 迁移会把 prefs 里的副本删干净，之后导出只能从表来
        repository.migrateLegacyEntitiesOnce()

        val exported = repository.exportAllPreferences()

        assertEquals(true, exported["schedule_${name}_smart_weekend"], "备份必须带上智能周末")
        assertEquals(false, exported["schedule_${name}_show_non_current_week"], "备份必须带上显示非本周")
        assertEquals(
            reorgRaw,
            exported["schedule_${name}_teaching_week_reorganizations"],
            "备份必须带上教学周重组规则",
        )

        // 走一遍恢复：恢复会作废行表，重读只能靠备份里的键
        repository.importAllPreferences(exported)
        assertEquals(true, repository.getSmartWeekend(name), "恢复后智能周末要跟备份一致")
        assertEquals(false, repository.getShowNonCurrentWeek(name))
        // 往返判据用「再导出一次」，不走 AppStorage 直读：仓库是单例，它的 prefs
        // 指向 @BeforeTest 里那一份，AppStorage.store 拿到的是另一个实例（见测试陷阱）。
        val reExported = repository.exportAllPreferences()
        assertEquals(
            exported["schedule_${name}_smart_weekend"],
            reExported["schedule_${name}_smart_weekend"],
            "恢复后智能周末要跟备份一致",
        )
        assertEquals(
            exported["schedule_${name}_show_non_current_week"],
            reExported["schedule_${name}_show_non_current_week"],
        )
        assertEquals(
            reorgRaw,
            reExported["schedule_${name}_teaching_week_reorganizations"],
            "恢复后重组规则要跟备份一致",
        )

        // 再模拟一次重启迁移：值必须还能进表，而不是又被默认值盖掉
        repository.invalidateAllCaches()
        repository.migrateLegacyEntitiesOnce()
        assertEquals(true, store.displaySettingsOf(name)?.smartWeekend)
        assertEquals(reorgRaw, store.teachingWeekReorganizationsOf(name))
    }

    /**
     * 恢复备份后**当场**重建成实体表，不留「等下次启动再迁」的窗口。
     *
     * 这是零兜底的前提：恢复完 prefs 副本已经被删干净，读路径只有表可用，
     * 停留一下都会读到被清空的行。
     */
    @Test
    fun `恢复备份后立刻重建成表并删掉 prefs 副本`() {
        val name = schedule()
        // 三端启动时就装好行表：恢复完当场迁，没有「等下次启动」的窗口
        CourseStorage.install { store }
        repository.importAllPreferences(
            backupOf(name, listOf(course("c1", "高等数学", name))) + mapOf(
                "schedule_${name}_smart_weekend" to true,
                "schedule_folders" to encodeScheduleFolders(
                    listOf(ScheduleFolder(id = "f1", name = "学期", schedules = listOf(name))),
                ),
            ),
        )
        val exported = repository.exportAllPreferences()
        // 往返一次：恢复只认备份，导出丢了什么这里就会现形
        repository.importAllPreferences(exported)

        assertTrue(store.isImported(name), "恢复完就该有行，不能等下次启动")
        assertEquals(listOf("高等数学"), store.load(name).map { it.name })
        assertEquals(true, store.displaySettingsOf(name)?.smartWeekend)
        assertEquals(listOf("f1"), repository.getScheduleFolders().map { it.id })
        assertEquals(true, repository.getSmartWeekend(name), "读取只走表也必须读得到")
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
