package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SqliteKeyValueStore] 的语义回归：原子性、回滚、按类型窄化。
 * 这些破掉都是静默丢数据，真机手测测不出来。
 */
class SqliteKeyValueStoreTest {

    // ── 基本读写 ──────────────────────────────────────────────────────────────

    @Test
    fun `写入后内存与后端都应有值`() {
        val backend = FakeBackend()
        val store = SqliteKeyValueStore(backend, "app_preferences")

        store.edit {
            putBoolean("predictive_back", true)
            putInt("week", 3)
            putString("lang", "zh")
        }

        assertTrue(store.getBoolean("predictive_back", false))
        assertEquals(3, store.getInt("week", -1))
        assertEquals("zh", store.getString("lang", ""))
        assertEquals(3, backend.rowsOf("app_preferences").size)
    }

    @Test
    fun `edit 块内按调用序生效 clear 之后 put 的值必须还在`() {
        val store = SqliteKeyValueStore(FakeBackend(), "ns")

        store.edit {
            putString("a", "1")
            clear()
        }
        assertFalse(store.contains("a"), "clear() 之后 a 不应存在")

        store.edit {
            putString("b", "2")
        }
        assertTrue(store.contains("b"))
    }

    // ── 原子性 / 回滚 ─────────────────────────────────────────────────────────

    @Test
    fun `块内抛异常时内存与后端都不变`() {
        val backend = FakeBackend()
        val store = SqliteKeyValueStore(backend, "ns")
        store.edit { putString("keep", "v1") }

        assertFailsWith<IllegalStateException> {
            store.edit {
                putString("keep", "v2")
                putString("other", "x")
                error("用户在块里又抛了")
            }
        }

        assertEquals("v1", store.getString("keep", ""), "块抛异常后内存不应被改")
        assertFalse(store.contains("other"))
        assertEquals("v1", backend.rowsOf("ns")["keep"]?.value)
        assertFalse(backend.rowsOf("ns").containsKey("other"))
    }

    @Test
    fun `提交失败时内存必须回滚 否则下一次写入会固化错误状态`() {
        val backend = FakeBackend()
        val store = SqliteKeyValueStore(backend, "ns")
        store.edit { putString("k", "old") }

        backend.failOnCommit = true
        assertFailsWith<IllegalStateException> { store.edit { putString("k", "new") } }

        assertEquals("old", store.getString("k", ""), "磁盘没写成功，内存就不能停在 new")
        assertEquals("old", backend.rowsOf("ns")["k"]?.value)
    }

    // ── 类型 ─────────────────────────────────────────────────────────────────

    @Test
    fun `类型不匹配返回默认值 不抛 ClassCastException`() {
        val store = SqliteKeyValueStore(FakeBackend(), "ns")
        store.edit { putString("week", "3") }

        // SharedPreferences 在这里会抛 ClassCastException，被调用点 runCatching 吞成默认值 = 静默重置
        assertEquals(3, store.getInt("week", -1), "字符串里的数字应当能读出来")
        assertEquals(3f, store.getFloat("week", 0f), 0.001f)
        assertEquals(42, store.getInt("missing", 42))
    }

    @Test
    fun `Int 与 Long 不会互相污染`() {
        val store = SqliteKeyValueStore(FakeBackend(), "ns")
        store.edit {
            putInt("i", 7)
            putLong("l", 7L)
        }
        assertEquals(7, store.getInt("i", -1))
        assertEquals(7L, store.getLong("l", -1L))
        // 类型标签各自独立，读长整型不该被当成 Int 截断
        assertEquals(7L, store.getLong("i", -1L), "Int 读成 Long 应当安全窄化")
    }

    @Test
    fun `字符串集合返回可变副本`() {
        val store = SqliteKeyValueStore(FakeBackend(), "ns")
        store.edit { putStringSet("selected", setOf("a", "b")) }

        val got = store.getStringSet("selected", emptySet())
        // 接口返回类型是只读 Set，但契约要求实例本身可变（对应 SharedPreferences 返回内部引用那个坑）
        @Suppress("UNCHECKED_CAST")
        (got as MutableSet<String>).add("c")
        assertEquals(setOf("a", "b"), store.getStringSet("selected", emptySet()))
    }

    @Test
    fun `all 返回拷贝`() {
        val store = SqliteKeyValueStore(FakeBackend(), "ns")
        store.edit { putString("a", "1") }

        @Suppress("UNCHECKED_CAST")
        val snapshot = store.all() as MutableMap<String, Any?>
        snapshot["b"] = "2"
        assertFalse(store.contains("b"))
    }

    // ── 删除 ─────────────────────────────────────────────────────────────────

    @Test
    fun `remove 与 clear 都要落到后端`() {
        val backend = FakeBackend()
        val store = SqliteKeyValueStore(backend, "ns")
        store.edit {
            putString("a", "1")
            putString("b", "2")
        }

        store.remove("a")
        assertEquals(setOf("b"), backend.rowsOf("ns").keys)

        store.clear()
        assertTrue(backend.rowsOf("ns").isEmpty())
    }

    // ── 监听 ─────────────────────────────────────────────────────────────────

    @Test
    fun `变更按 key 通知 且注销后不再回调`() {
        val store = SqliteKeyValueStore(FakeBackend(), "ns")
        val seen = mutableListOf<String>()
        val token = store.registerListener { seen += it }

        store.edit {
            putString("theme_mode", "dark")
            putInt("week", 2)
        }
        assertEquals(listOf("theme_mode", "week"), seen)

        store.unregisterListener(token)
        store.edit { putString("theme_mode", "light") }
        assertEquals(listOf("theme_mode", "week"), seen, "注销后不应再收到回调")
    }

    /** 后端的内存假实现。事务用整体快照还原，用来钉住「提交失败必须回滚」这条语义。 */
    private class FakeBackend : SqlBackend {

        private val store = LinkedHashMap<String, LinkedHashMap<String, KvRow>>()
        private val meta = LinkedHashMap<String, String>()

        /** 模拟磁盘满 / 库损坏：块执行完、提交前炸。 */
        var failOnCommit = false

        fun rowsOf(namespace: String): Map<String, KvRow> = store[namespace].orEmpty()

        override fun <T> transaction(block: SqlBackend.SqlTx.() -> T): T {
            val rowsBackup = store.mapValues { LinkedHashMap(it.value) }
            val metaBackup = LinkedHashMap(meta)
            try {
                val result = Tx().block()
                if (failOnCommit) error("模拟提交失败")
                return result
            } catch (t: Throwable) {
                store.clear()
                store.putAll(rowsBackup.mapValues { LinkedHashMap(it.value) })
                meta.clear()
                meta.putAll(metaBackup)
                throw t
            }
        }

        override fun loadNamespace(namespace: String): List<KvRow> =
            store[namespace].orEmpty().values.toList()

        override fun readMeta(key: String): String? = meta[key]

        private inner class Tx : SqlBackend.SqlTx {
            override fun upsert(namespace: String, row: KvRow) {
                store.getOrPut(namespace) { LinkedHashMap() }[row.key] = row
            }

            override fun delete(namespace: String, key: String) {
                store[namespace]?.remove(key)
            }

            override fun clearNamespace(namespace: String) {
                store.remove(namespace)
            }

            override fun load(namespace: String): List<KvRow> =
                store[namespace].orEmpty().values.toList()

            override fun putMeta(key: String, value: String) {
                meta[key] = value
            }
        }
    }
}
