package com.haooz.chedule.data

import com.haooz.chedule.data.db.NexioDatabase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [SqliteKeyValueStore] 的并发读：**读完全不进锁**，靠「不可变快照 + 整份换指针」。
 *
 * 三条不变量：
 * 1. 一次 `edit{}` 里的多个键**一起可见** —— 同一份快照里不可能出现「a 是新值、b 还是旧值」；
 * 2. 写完立刻读得到（同一线程），不会因为快照没换而读回旧值；
 * 3. 并发写不同键互不抹掉。
 *
 * ⚠ 注意「一起可见」的判据是**一次拿到快照**（`all()`），不是连着两次 `getInt`：
 * 两次读各自取快照，中途别的线程提交一次就会看到「不一致」—— 那是调用方的用法问题，
 * 不是存储的撕裂。多键一致性必须用 `all()`。
 *
 * 读锁删掉之前，draw 期每一次读设置都要抢一把正被写操作持着的 monitor；
 * 现在读只取一次字段引用，写只在 commitLock 里换指针。
 */
class SqliteKeyValueStoreConcurrencyTest {

    private val driver = openInMemoryDriver().also { NexioDatabase.Schema.create(it) }
    private val backend = SqlDriverBackend(driver)
    private val store = SqliteKeyValueStore(backend, "concurrency_test")

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `并发读不会读到半次提交`() {
        val rounds = 300
        var torn = 0
        var reads = 0
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)

        val reader = Thread {
            while (!stop.get()) {
                // 一次取快照：同一次提交里的两个键必须一起可见
                val snapshot = store.all()
                val a = snapshot[KEY_A]
                val b = snapshot[KEY_B]
                reads++
                if (a != null && b != null && a != b) torn++
            }
        }
        reader.start()
        try {
            repeat(rounds) { i ->
                store.edit {
                    putInt(KEY_A, i)
                    putInt(KEY_B, i)
                }
            }
        } finally {
            stop.set(true)
            reader.join(5_000)
        }

        assertEquals(0, torn, "读到过撕裂状态（a≠b）：快照不是整份替换，或读路径动过内部 map")
        assertTrue(reads > 0, "读线程一次都没跑起来，这条测试没意义")
        assertEquals(rounds - 1, store.getInt(KEY_A, -1), "最后一次写必须立刻可见")
        assertEquals(rounds - 1, store.getInt(KEY_B, -1))
    }

    @Test
    fun `并发写不同键不互相抹掉`() {
        // edit{} 的块在锁外跑，提交时只合并「受影响的键」；整张副本覆盖会把这期间别的线程
        // 写进去的键从内存抹掉（磁盘还在），表现为「设置改了但界面一直读旧值」。
        val threads = (0 until 4).map { t ->
            Thread {
                repeat(50) { i -> store.edit { putInt("k$t-$i", i) } }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(10_000) }

        val all = store.all()
        assertEquals(4 * 50, all.keys.count { it.startsWith("k") }, "有键被并发提交抹掉了：$all")
    }

    private companion object {
        const val KEY_A = "pair_a"
        const val KEY_B = "pair_b"
    }
}
