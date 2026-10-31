package com.haooz.chedule.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.storage.CourseRowAnchor
import com.haooz.chedule.data.storage.SqlDriverBackend
import com.haooz.chedule.data.storage.SqliteCourseRowStore
import com.haooz.chedule.data.storage.SqliteKeyValueStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * iOS 真 SQL 往返：验证 `NativeSqliteDriver` + [SqlDriverBackend] 在 Kotlin/Native 上
 * 与 Android / 桌面是同一套语义（类型标签、事务、外键级联、行表读写、并发）。
 *
 * 这个类只跑在 iOS 上（`iosTest` 源集）。JVM 侧由 `SqliteKeyValueStoreTest` /
 * `CourseRowStoreTest` 覆盖同一份 commonMain 实现 —— 但那些跑的是 JdbcSqliteDriver，
 * 换 driver 会不会变味（尤其 Native 的内存模型和锁）只能在这里验。
 */
class IosSqlStorageTest {

    /** 递增 + 随机后缀：内存库也要唯一名字，否则并行用例会撞到同一个库。 */
    private var seq = 0

    /** 每次开一个独立的内存库：测试之间不共享表，也不会在模拟器沙箱里留文件。 */
    private fun openBackend(): SqlDriverBackend = SqlDriverBackend(
        NativeSqliteDriver(
            schema = NexioDatabase.Schema,
            name = "ios-test-${seq++}-${Random.nextInt(1_000_000)}",
            onConfiguration = { it.copy(inMemory = true) },
        ),
    )

    @Test
    fun `键值各类型往返`() {
        val backend = openBackend()
        val store = SqliteKeyValueStore(backend, "ns")
        store.edit {
            putString("s", "值")
            putInt("i", 42)
            putLong("l", 9_000_000_000L)
            putFloat("f", 1.5f)
            putBoolean("b", true)
            putStringSet("ss", setOf("a", "b"))
        }

        // 重新开一个实例 = 模拟「重启后从表读回」，而不是读同一个内存快照
        val reopened = SqliteKeyValueStore(backend, "ns")
        assertEquals("值", reopened.getString("s", ""))
        assertEquals(42, reopened.getInt("i", 0))
        assertEquals(9_000_000_000L, reopened.getLong("l", 0L))
        assertEquals(1.5f, reopened.getFloat("f", 0f))
        assertTrue(reopened.getBoolean("b", false))
        assertEquals(setOf("a", "b"), reopened.getStringSet("ss", emptySet()))
    }

    @Test
    fun `事务回滚后内存与磁盘都不留痕`() {
        val backend = openBackend()
        val store = SqliteKeyValueStore(backend, "ns")
        store.edit { putInt("k", 1) }

        // 块内抛异常：原状态不动（内存副本丢弃）
        runCatching { store.edit { putInt("k", 2); error("boom") } }

        assertEquals(1, store.getInt("k", 0), "内存里不能留下半截写入")
        assertEquals(1, SqliteKeyValueStore(backend, "ns").getInt("k", 0), "磁盘上也不能")
    }

    @Test
    fun `课程行表往返与外键级联`() {
        val backend = openBackend()
        val rows = SqliteCourseRowStore(backend.database)
        val anchor = CourseRowAnchor(
            scheduleId = "s1",
            timeConfigId = 1L,
            timeConfigName = "默认",
            timeConfigPayload = """{"name":"默认"}""",
            sortOrder = 0,
            currentWeek = 3,
            totalWeeks = 18,
            classStart = "2026/09/01",
        )
        val course = Course(
            id = "c1",
            name = "高等数学",
            classroom = "A101",
            teacher = "张老师",
            dayOfWeek = 2,
            startSection = 1,
            endSection = 2,
            startWeek = 1,
            endWeek = 16,
            weekType = Course.WEEK_TYPE_ALL,
            colorRes = 0xFF4CAF50L,
            selectedWeeks = listOf(1, 2, 3),
            scheduleId = "s1",
            lastModified = 1_000L,
            note = "带计算器",
            weekNotes = mapOf(2 to "小测"),
        )

        rows.importAll(anchor, listOf(course))

        assertTrue(rows.isImported("s1"), "导入必须留下标记")
        val loaded = rows.load("s1").single()
        assertEquals("高等数学", loaded.name)
        assertEquals(listOf(1, 2, 3), loaded.selectedWeeks)
        assertEquals(mapOf(2 to "小测"), loaded.weekNotes)
        assertEquals(3, rows.settingsOf("s1")?.currentWeek)

        // 外键级联：删课表必须把课程一起带走（PRAGMA foreign_keys 生效的证据）
        rows.deleteSchedule("s1")
        assertTrue(rows.load("s1").isEmpty(), "删 schedule 应级联删掉 course")
    }

    @Test
    fun `并发写不丢键`() = runBlocking {
        val backend = openBackend()
        val store = SqliteKeyValueStore(backend, "ns")
        val workers = 8
        val perWorker = 20

        withContext(Dispatchers.Default) {
            (0 until workers).map { i ->
                async {
                    repeat(perWorker) { j -> store.edit { putInt("k$i-$j", j) } }
                }
            }.awaitAll()
        }

        assertEquals(workers * perWorker, store.all().size, "并发写必须全部落地")
        // 重新打开再数一遍：内存快照对了不代表磁盘对了
        assertEquals(
            workers * perWorker,
            SqliteKeyValueStore(backend, "ns").all().size,
            "磁盘上也要是完整的",
        )
    }
}
