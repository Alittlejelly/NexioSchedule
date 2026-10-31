package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.backup.encodeStringList
import com.haooz.chedule.data.db.NexioDatabase
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.CourseStorage
import com.haooz.chedule.data.storage.InMemoryKeyValueStore
import com.haooz.chedule.data.storage.SqliteCourseRowStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 多课表作息隔离回归 —— 「只同步了作息」「改一张表的作息另一张跟着变」的根因。
 *
 * ## 背景
 *
 * 课表与作息是「一个课表绑一个 TimeConfig」，绑定键为 `schedule_time_config_{课表名}`。
 * 问题在于 [com.haooz.chedule.data.repo.CourseRepository.getScheduleTimeConfigId] 对**未绑定**的课表会回退到
 * 旧全局指针 / 第一个配置 —— 这是给升级老数据兜底的读取语义，但写入路径当年也复用了它：
 * 于是任何未绑定的课表改节次、改时间、删作息时，改的都是它回退到的那套配置，
 * 也就是**另一张课表的作息**（2026-10-28/29 用户反馈的「传到平板后只有作息变了」）。
 *
 * 修复：写路径统一走 `ensureScheduleTimeConfigId` —— 未绑定就先绑上（回退到的那套若没人用
 * 就过户，已被别人绑着就复制独立副本），读取路径保持回退以便老数据不炸。
 *
 * ⚠ `CourseRepository()` 实为单例，prefs 在 JVM 内首次构造时钉死当时的 store，且测试类之间
 * 共用同一份 store；所以这里所有数据都自己造，不假设 store 是空的。
 */
class TimeConfigBindingIsolationTest {

    private lateinit var repository: com.haooz.chedule.data.repo.CourseRepository
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqliteCourseRowStore

    @BeforeTest
    fun setUp() {
        AppStorage.init { InMemoryKeyValueStore() }
        // 零兜底之后没有「无行表」的平台：作息与绑定都只认表
        driver = openInMemoryDriver()
        NexioDatabase.Schema.create(driver)
        store = SqliteCourseRowStore(NexioDatabase(driver))
        CourseStorage.install { store }
        repository = com.haooz.chedule.data.repo.CourseRepository.getInstance()
        repository.invalidateAllCaches()
    }

    @AfterTest
    fun tearDown() {
        CourseStorage.install { null }
        repository.invalidateAllCaches()
        driver.close()
    }

    /**
     * 造一份「只含这些课表」的全量备份：保留 `time_config_*` 本体与 ids，
     * 其余 `schedule_*`（含各表课程、绑定、文件夹、名字）全部丢掉重写。
     * 不这么做的话，store 里其它测试留下的课表会让结构校验报「course data belongs to an unknown schedule」。
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
        // 旧全局指针指向第一张表的配置 —— 未绑定的课表正是回退到它，
        // 不这么设就复现不出「写到别人配置上」的真实形态
        cleaned["current_time_config_id"] = bindings[names.first()] ?: 0L
        names.forEach { cleaned["schedule_${it}_courses"] = "[]" }
        bindings.forEach { (schedule, id) -> cleaned["schedule_time_config_$schedule"] = id }
        return cleaned
    }

    /** 新建的课表必须拿到自己的配置，改一套不影响另一套 */
    @Test
    fun `新建课表的作息互相独立`() {
        val a = "隔离-A-${System.nanoTime()}"
        val b = "隔离-B-${System.nanoTime()}"
        repository.addSchedule(a)
        repository.addSchedule(b)

        repository.savePeriodTimes("morning", mapOf(1 to "07:10-07:55"), a)

        assertEquals("07:10-07:55", repository.getPeriodTimes("morning", a)[1])
        assertNotEquals(
            "07:10-07:55",
            repository.getPeriodTimes("morning", b)[1],
            "B 没改过作息，不能跟着 A 变",
        )
        assertNotEquals(
            repository.getScheduleTimeConfigId(a),
            repository.getScheduleTimeConfigId(b),
            "两张课表不能绑同一个 TimeConfig",
        )
    }

    /**
     * 关键回归：绑定键丢失 / 指向已删配置（历史脏数据）时写作息，
     * 必须落在「这张课表自己的」配置上，绝不能改到它回退到的那套。
     *
     * 修复前这里会红：A 的 07:10 被改成 06:00。
     */
    @Test
    fun `未绑定课表写作息不会污染其他课表`() {
        val a = "隔离-源-${System.nanoTime()}"
        val b = "隔离-未绑-${System.nanoTime()}"
        repository.addSchedule(a)
        repository.savePeriodTimes("morning", mapOf(1 to "07:10-07:55"), a)
        val aBinding = repository.getScheduleTimeConfigId(a)

        // B 的绑定指向不存在的配置 → 等价未绑定
        repository.importAllPreferences(
            backupWithOnly(listOf(a, b), mapOf(a to aBinding, b to 999L))
        )

        repository.savePeriodTimes("morning", mapOf(1 to "06:00-06:45"), b)

        assertEquals(
            "07:10-07:55",
            repository.getPeriodTimes("morning", a)[1],
            "A 的作息被 B 的写入覆盖了",
        )
        assertEquals("06:00-06:45", repository.getPeriodTimes("morning", b)[1])
        assertNotEquals(
            repository.getScheduleTimeConfigId(a),
            repository.getScheduleTimeConfigId(b),
            "补绑后仍与 A 共用配置",
        )
    }

    /** 复制另一张课表的作息：目标未绑定时不能把「它回退到的那套」原地改掉 */
    @Test
    fun `从未绑定课表复制作息不会覆盖其他课表`() {
        val source = "隔离-来源-${System.nanoTime()}"
        val target = "隔离-目标-${System.nanoTime()}"
        val other = "隔离-他人-${System.nanoTime()}"
        repository.addSchedule(source)
        repository.addSchedule(other)
        repository.savePeriodTimes("morning", mapOf(1 to "08:30-09:15"), source)
        val sourceBinding = repository.getScheduleTimeConfigId(source)
        val otherBinding = repository.getScheduleTimeConfigId(other)
        val otherMorningBefore = repository.getPeriodTimes("morning", other)[1]

        // target 未绑定，且旧全局指针指向 other 的配置 —— 正是它会被回退命中
        val backup = backupWithOnly(
            names = listOf(source, target, other),
            bindings = mapOf(source to sourceBinding, target to 999L, other to otherBinding),
        )
        backup["current_time_config_id"] = otherBinding
        repository.importAllPreferences(backup)

        repository.copyTimeConfigFromSchedule(sourceScheduleId = source, targetScheduleId = target)

        assertEquals(
            otherMorningBefore,
            repository.getPeriodTimes("morning", other)[1],
            "第三张课表的作息被「复制到未绑定目标」这个动作覆盖了",
        )
        assertEquals("08:30-09:15", repository.getPeriodTimes("morning", source)[1])
        assertEquals("08:30-09:15", repository.getPeriodTimes("morning", target)[1])
    }
}
