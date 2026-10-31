package com.haooz.chedule.data

import kotlinx.datetime.LocalDate
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 读取缓存的失效回归。
 *
 * [CourseRepository] 里新增了一批「热路径读取结果」缓存（生效作息、教学周规则、开学日、
 * 作息 JSON）。缓存没跟写入一起失效的表现是「设置改了但界面还是旧值，只有重启才恢复」——
 * 正是这轮优化最容易引入、也最难在真机上手测出来的问题。
 */
class RepositoryCacheInvalidationTest {

    private lateinit var repository: CourseRepository
    private lateinit var driver: JdbcSqliteDriver

    @BeforeTest
    fun setUp() {
        AppStorage.init { InMemoryKeyValueStore() }
        // 零兜底之后没有「无行表」这种形态：三端都装行表，测试也必须跑真库，
        // 否则写入全是空失败，测不出缓存有没有失效。
        driver = openInMemoryDriver()
        NexioDatabase.Schema.create(driver)
        CourseStorage.install { SqliteCourseRowStore(NexioDatabase(driver)) }
        repository = CourseRepository.getInstance()
        repository.invalidateAllCaches()
    }

    @AfterTest
    fun tearDown() {
        CourseStorage.install { null }
        repository.invalidateAllCaches()
        driver.close()
    }

    @Test
    fun `教学周重组规则写入后立刻读到新值`() {
        val name = schedule("重组规则")
        freshState(name)
        assertEquals(emptyList(), repository.getTeachingWeekReorganizations(name), "前置条件：还没有规则")

        val rules = listOf(
            TeachingWeekReorganizationRule(
                firstOriginalWeek = 2,
                firstStartWeekday = 1,
                firstEndWeekday = 3,
                secondOriginalWeek = 3,
                secondStartWeekday = 4,
                secondEndWeekday = 7,
            )
        )
        assertTrue(repository.setTeachingWeekReorganizations(rules, name))

        // 第一次读会把解析结果缓存起来，所以这一条同时验证「写入要让缓存失效」
        assertEquals(rules, repository.getTeachingWeekReorganizations(name))
        assertTrue(repository.removeTeachingWeekReorganization(rules.single(), name))
        assertEquals(emptyList(), repository.getTeachingWeekReorganizations(name))
    }

    @Test
    fun `开学日写入后立刻读到新值`() {
        val name = schedule("开学日")
        freshState(name)

        repository.setClassStartTime(name, "2025-09-01")
        assertEquals("2025/09/01", repository.getClassStartTime(name))

        repository.setClassStartTime(name, "2026-02-23")
        assertEquals("2026/02/23", repository.getClassStartTime(name), "缓存没有跟着写入失效")
    }

    @Test
    fun `改作息时间后 getPeriodTimes 与课程时间解析都拿到新值`() {
        val name = schedule("作息")
        freshState(name)
        val configId = repository.addTimeConfig(
            TimeConfig(
                name = name,
                morningSections = 4,
                afternoonSections = 4,
                eveningSections = 4,
            )
        )
        repository.setScheduleTimeConfigId(name, configId)

        // 先读一次把「当天生效作息」灌进缓存
        val before = repository.getPeriodTimes("morning", name)
        assertNotEquals("07:30-08:15", before[1], "前置条件：默认时间不该正好是这个")

        repository.savePeriodTimes("morning", mapOf(1 to "07:30-08:15"), name)

        assertEquals(
            "07:30-08:15",
            repository.getPeriodTimes("morning", name)[1],
            "改作息后缓存的生效作息必须失效",
        )
        val course = Course(
            id = "c1",
            name = "早课",
            classroom = "",
            teacher = "",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            startWeek = 1,
            endWeek = 16,
            weekType = Course.WEEK_TYPE_ALL,
            colorRes = 0L,
            scheduleId = name,
        )
        // CourseTimeResolver 现在一次取全局节次表，不能再走旧的三段拼接
        assertEquals("07:30", CourseTimeResolver.getStartTime(course, repository))
    }

    @Test
    fun `假期条目写入后 loadAllByYear 立刻反映新值`() {
        HolidayManager.save(2026, listOf(HolidayEntry("2026-10-01", "2026-10-08", "国庆节", 0)))
        assertEquals(1, HolidayManager.loadAllByYear()[2026]?.size)

        HolidayManager.save(2026, emptyList())
        assertEquals(
            emptyList(),
            HolidayManager.loadAllByYear()[2026],
            "版本号变了还命中旧快照就是丢数据",
        )
    }

    @Test
    fun `换掉存储实例后不再命中旧快照`() {
        HolidayManager.save(2025, listOf(HolidayEntry("2025-01-01", "", "元旦", 0)))
        assertEquals(setOf(2025), HolidayManager.loadAllByYear().keys)

        // AppStorage.init 换实例（测试 / 数据重置）：版本号可能相同，必须按实例判等
        AppStorage.init {
            InMemoryKeyValueStore(
                mapOf(
                    "entries_2026" to
                        """[{"date":"2026-01-01","endDate":"","name":"元旦","type":0}]""",
                )
            )
        }
        assertEquals(
            setOf(2026),
            HolidayManager.loadAllByYear().keys,
            "换了存储实例还返回上一次的快照 = 读到别人的数据",
        )
    }

    /**
     * 生效作息按日期选方案（夏令时 / 冬令时）。缓存条目只比对日期：日期一变必须重算，
     * 否则跨天之后课程时间还按昨天那套方案显示。
     */
    @Test
    fun `生效作息跨天必须重算`() {
        val name = schedule("跨天")
        freshState(name)
        // quickTimeEnabled：时间由「上课时间 + 时长」算出来，否则 getPeriodTimes 走 sectionTimes
        // 覆盖表（空表回落到内置默认时间），看不出两套方案的差别
        val summer = TimeRoutine(
            id = 1,
            name = "夏令",
            effectiveMonth = 3,
            effectiveDay = 1,
            quickTimeEnabled = true,
            morningStartHour = 8,
            morningStartMinute = 0,
        )
        val winter = TimeRoutine(
            id = 2,
            name = "冬令",
            effectiveMonth = 10,
            effectiveDay = 1,
            quickTimeEnabled = true,
            morningStartHour = 8,
            morningStartMinute = 30,
        )
        val configId = repository.addTimeConfig(
            TimeConfig(
                name = name,
                morningSections = 4,
                afternoonSections = 4,
                eveningSections = 4,
                routines = listOf(summer, winter),
            )
        )
        repository.setScheduleTimeConfigId(name, configId)

        val march = repository.effectiveConfigOn(configId, LocalDate(2026, 3, 15))
        val october = repository.effectiveConfigOn(configId, LocalDate(2026, 10, 15))
        assertEquals("08:00", march.getPeriodTimes("morning")[1]?.substringBefore("-"))
        assertEquals(
            "08:30",
            october.getPeriodTimes("morning")[1]?.substringBefore("-"),
            "日期变了还在用缓存里的旧方案",
        )
    }

    /**
     * 孤儿配置被 prune 掉之后，`addTimeConfig` 会复用那个 id。生效作息缓存是按 id 存的，
     * 复用必须重算，否则新配置的课程时间还按被删的那套算。
     */
    @Test
    fun `孤儿配置被清后 id 复用，生效作息不能拿旧的`() {
        val name = schedule("id复用")
        freshState(name)
        val bound = repository.addTimeConfig(
            TimeConfig(name = name, morningSections = 4, afternoonSections = 4, eveningSections = 4)
        )
        repository.setScheduleTimeConfigId(name, bound)
        // 孤儿放在最大 id 上：prune 之后 addTimeConfig 正好把它复用
        val orphan = repository.addTimeConfig(
            TimeConfig(name = "没人要的", morningSections = 5, afternoonSections = 5, eveningSections = 5)
        )
        // 先把孤儿的生效作息灌进缓存
        repository.effectiveConfigOn(orphan, todayLocalDate())
        assertEquals(5, repository.effectiveConfigOn(orphan, todayLocalDate()).morningSections)

        repository.pruneOrphanTimeConfigsIfNeeded()
        val reused = repository.addTimeConfig(
            TimeConfig(name = "新的", morningSections = 3, afternoonSections = 3, eveningSections = 3)
        )
        assertEquals(orphan, reused, "前置条件：prune 掉的是最大 id，新配置应当复用它")
        assertEquals(
            3,
            repository.effectiveConfigOn(reused, todayLocalDate()).morningSections,
            "id 复用后生效作息还是被删那套 = 课程时间全错",
        )
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun schedule(prefix: String) = "$prefix-${System.nanoTime()}"

    /** 单例 prefs 在测试类之间共用：只留这一张课表，其余键清掉。 */
    private fun freshState(name: String) {
        repository.importAllPreferences(
            mapOf(
                "schedule_names" to encodeStringList(listOf(name)),
                "schedule_folders" to "[]",
                "current_schedule_id" to name,
                "schedule_${name}_courses" to "[]",
            )
        )
    }
}
