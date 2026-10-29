package com.haooz.chedule.data

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 键值表的一行。
 *
 * [type] 取 `s` / `i` / `l` / `f` / `b` / `ss`，与 [FileKeyValueStore] 的落盘标签**同源**。
 * 两个实现共用一套标签，跨端备份/迁移才不需要第二套映射表（多一套就多一处会写错的地方）。
 */
class KvRow(val key: String, val type: String, val value: Any)

/**
 * 键值表的存储后端。抽出来只为一件事：让 [SqliteKeyValueStore] 的语义能在宿主机上跑回归测试，
 * 不必起真机或 Robolectric（[SharedPreferencesStore] 的监听 bug 就是这么漏出去的，
 * 详见 `androidHostTest/SharedPreferencesStoreListenerTest`）。
 *
 * ⚠ [SqlTx] 的所有方法只允许在 [transaction] 的块内调用 —— 块外拿到的事务对象不代表任何一致性。
 */
interface SqlBackend {

    fun <T> transaction(block: SqlTx.() -> T): T

    /** 启动时一次性把整个命名空间读进内存。键值总量是几百行量级，不需要分页。 */
    fun loadNamespace(namespace: String): List<KvRow>

    /** 迁移标记等元数据。[SqlBackend] 之外的东西一律不放这张表。 */
    fun readMeta(key: String): String?

    interface SqlTx {
        fun upsert(namespace: String, row: KvRow)
        fun delete(namespace: String, key: String)
        fun clearNamespace(namespace: String)

        /** 事务内回读，迁移自校验用（写完立刻查一遍，比对不上就整体回滚）。 */
        fun load(namespace: String): List<KvRow>

        fun putMeta(key: String, value: String)
    }
}

/**
 * [KeyValueStore] 的 SQLite 实现：内存快照 + 行级写入 + 事务提交。
 *
 * ## 为什么是这套结构
 *
 * SharedPreferences 的三个硬伤，这里逐个消掉：
 * 1. **整文件重写**：`apply()` 每次都把整个 XML 落盘（课程 JSON 数百 KB 也一样）。这里只写
 *    受影响的行，`edit{}` 内的多次 put 合并成**一次事务**。
 * 2. **跨文件无原子性**：两个偏好文件之间做不到「要么都成功」。这里所有命名空间在同一张表，
 *    一个事务能同时改多个命名空间。
 * 3. **类型靠猜**：`getInt` 读到 `String` 抛 `ClassCastException`，被调用点的 `runCatching`
 *    吞成默认值 = 静默重置。这里类型写在 `type` 列里，读时按需窄化（Long→Int、Float→Int），
 *    真读不出来才返回默认值。
 *
 * ## 语义
 *
 * 与 [FileKeyValueStore] 严格对齐：`edit{}` 块内按调用序**直接生效**（`put(a); clear()` 后 a 不存在），
 * 提交失败时内存回滚到调用前（否则内存与磁盘会分叉，下一次写入就把错的东西固化下去）。
 *
 * 线程安全：`synchronized` 可用 —— `checkKmpPurity` 只扫 commonMain/skikoMain，扫不到 androidMain。
 */
class SqliteKeyValueStore(
    private val backend: SqlBackend,
    private val namespace: String,
) : KeyValueStore {

    private val lock = Any()

    /** 全量内存快照。读写一律持 [lock]。 */
    private var data: LinkedHashMap<String, KvRow> = LinkedHashMap()

    /** 变更监听。自己持强引用 —— 不需要 SharedPreferences 那套弱引用兜底（见 [SharedPreferencesStore] 的教训）。 */
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    init {
        backend.loadNamespace(namespace).forEach { data[it.key] = it }
    }

    // ── 读 ────────────────────────────────────────────────────────────────────

    private fun row(key: String): KvRow? = synchronized(lock) { data[key] }

    override fun getString(key: String, defaultValue: String): String =
        (row(key)?.takeIf { it.type == TYPE_STRING }?.value as? String) ?: defaultValue

    override fun getInt(key: String, defaultValue: Int): Int {
        val r = row(key) ?: return defaultValue
        return when (r.type) {
            TYPE_INT -> r.value as? Int
            TYPE_LONG -> (r.value as? Long)?.toInt()
            TYPE_FLOAT -> (r.value as? Float)?.toInt()
            // 历史脏数据：类型被写成了字符串，能解析就用，解析不出再退回默认值
            TYPE_STRING -> (r.value as? String)?.toIntOrNull()
            else -> null
        } ?: defaultValue
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        val r = row(key) ?: return defaultValue
        return when (r.type) {
            TYPE_LONG -> r.value as? Long
            TYPE_INT -> (r.value as? Int)?.toLong()
            TYPE_FLOAT -> (r.value as? Float)?.toLong()
            TYPE_STRING -> (r.value as? String)?.toLongOrNull()
            else -> null
        } ?: defaultValue
    }

    override fun getFloat(key: String, defaultValue: Float): Float {
        val r = row(key) ?: return defaultValue
        return when (r.type) {
            TYPE_FLOAT -> r.value as? Float
            TYPE_INT -> (r.value as? Int)?.toFloat()
            TYPE_LONG -> (r.value as? Long)?.toFloat()
            TYPE_STRING -> (r.value as? String)?.toFloatOrNull()
            else -> null
        } ?: defaultValue
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val r = row(key) ?: return defaultValue
        return when (r.type) {
            TYPE_BOOL -> r.value as? Boolean
            TYPE_STRING -> (r.value as? String)?.toBooleanStrictOrNull()
            else -> null
        } ?: defaultValue
    }

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> {
        val r = row(key) ?: return defaultValue
        @Suppress("UNCHECKED_CAST")
        val stored = r.value as? Set<String> ?: return defaultValue
        // 必须返回副本：调用方会就地 add，返回内部实例会抛 UnsupportedOperationException
        return LinkedHashSet(stored)
    }

    override fun contains(key: String): Boolean = row(key) != null

    override fun all(): Map<String, Any?> = synchronized(lock) {
        val out = LinkedHashMap<String, Any?>(data.size)
        data.forEach { (k, row) ->
            val v = row.value
            out[k] = if (v is Set<*>) LinkedHashSet(v) else v
        }
        out
    }

    // ── 写 ────────────────────────────────────────────────────────────────────

    override fun edit(block: KeyValueEditor.() -> Unit) {
        val affected = LinkedHashSet<String>()
        var cleared = false

        // 先在副本上跑：块内抛异常时原状态一点不动，不需要「回滚」这种容易写错的逻辑
        val working = synchronized(lock) { LinkedHashMap(data) }
        EditorFacade(working, affected) { cleared = true }.block()

        synchronized(lock) {
            val previous = data
            data = working
            try {
                backend.transaction {
                    if (cleared) {
                        clearNamespace(namespace)
                        working.values.forEach { upsert(namespace, it) }
                    } else {
                        for (key in affected) {
                            val r = working[key]
                            if (r != null) upsert(namespace, r) else delete(namespace, key)
                        }
                    }
                }
            } catch (t: Throwable) {
                // 内存必须跟着回滚：否则「磁盘没写成功、内存已经变了」，下一次写入把错的状态固化
                data = previous
                throw t
            }
        }

        notifyListeners(affected)
    }

    override fun remove(key: String) {
        val existed = synchronized(lock) { data.containsKey(key) }
        if (!existed) return

        synchronized(lock) {
            val previous = data
            val working = LinkedHashMap(data).apply { remove(key) }
            data = working
            try {
                backend.transaction { delete(namespace, key) }
            } catch (t: Throwable) {
                data = previous
                throw t
            }
        }

        notifyListeners(listOf(key))
    }

    override fun clear() {
        val removed: List<String>
        synchronized(lock) {
            if (data.isEmpty()) return
            removed = data.keys.toList()
            val previous = data
            data = LinkedHashMap()
            try {
                backend.transaction { clearNamespace(namespace) }
            } catch (t: Throwable) {
                data = previous
                throw t
            }
        }

        notifyListeners(removed)
    }

    override fun registerListener(listener: (String) -> Unit): Any? {
        listeners.add(listener)
        return listener
    }

    override fun unregisterListener(token: Any?) {
        // 函数类型有类型擦除，不能 `is (String) -> Unit` 判型 —— 按引用移除
        @Suppress("UNCHECKED_CAST")
        (token as? (String) -> Unit)?.let { listeners.remove(it) }
    }

    // ── 内部 ──────────────────────────────────────────────────────────────────

    private fun notifyListeners(keys: Collection<String>) {
        if (listeners.isEmpty() || keys.isEmpty()) return
        for (key in keys) {
            for (l in listeners) {
                runCatching { l(key) }.onFailure { e ->
                    NexioLog.w(TAG, "listener 异常（已跳过）: namespace=$namespace key=$key", e)
                }
            }
        }
    }

    /** 在副本 [working] 上按调用序直接生效，同时记录受影响键。 */
    private inner class EditorFacade(
        private val working: LinkedHashMap<String, KvRow>,
        private val affected: MutableSet<String>,
        private val onClear: () -> Unit,
    ) : KeyValueEditor {

        private fun put(key: String, type: String, value: Any) {
            working[key] = KvRow(key, type, value)
            affected += key
        }

        override fun putString(key: String, value: String) = put(key, TYPE_STRING, value)
        override fun putInt(key: String, value: Int) = put(key, TYPE_INT, value)
        override fun putLong(key: String, value: Long) = put(key, TYPE_LONG, value)
        override fun putFloat(key: String, value: Float) = put(key, TYPE_FLOAT, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, TYPE_BOOL, value)

        override fun putStringSet(key: String, value: Set<String>) =
            put(key, TYPE_STRING_SET, LinkedHashSet(value))

        override fun remove(key: String) {
            working.remove(key)
            affected += key
        }

        override fun clear() {
            // 通知要覆盖被清掉的每一个键，否则监听方不会刷新
            affected += working.keys.toList()
            working.clear()
            onClear()
        }
    }

    companion object {
        const val TAG = "SqliteKeyValueStore"

        // 与 FileKeyValueStore 的落盘标签一字不差，改动会同时废掉两端的备份兼容
        const val TYPE_STRING = "s"
        const val TYPE_INT = "i"
        const val TYPE_LONG = "l"
        const val TYPE_FLOAT = "f"
        const val TYPE_BOOL = "b"
        const val TYPE_STRING_SET = "ss"
    }
}
