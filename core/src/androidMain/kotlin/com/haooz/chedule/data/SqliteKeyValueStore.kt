package com.haooz.chedule.data

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 键值表的一行。[type] 取 `s`/`i`/`l`/`f`/`b`/`ss`，与 [FileKeyValueStore] 的落盘标签一致。
 */
class KvRow(val key: String, val type: String, val value: Any)

/**
 * 键值表的存储后端。接口只为宿主机单测能跑真事务，不需要真机或 Robolectric。
 *
 * ⚠ [SqlTx] 的方法只在 [transaction] 块内有效，块外拿到的事务不代表任何一致性。
 */
interface SqlBackend {

    fun <T> transaction(block: SqlTx.() -> T): T

    /** 启动时一次性把整个命名空间读进内存。键值总量是几百行量级，不需要分页。 */
    fun loadNamespace(namespace: String): List<KvRow>

    /** 导入标记等元数据。业务数据一律不进这张表。 */
    fun readMeta(key: String): String?

    interface SqlTx {
        fun upsert(namespace: String, row: KvRow)
        fun delete(namespace: String, key: String)
        fun clearNamespace(namespace: String)

        /** 事务内回读，供导入自校验：写完立刻查一遍，对不上就整体回滚。 */
        fun load(namespace: String): List<KvRow>

        fun putMeta(key: String, value: String)
    }
}

/**
 * [KeyValueStore] 的 SQLite 实现：内存快照 + 行级写入 + 单事务提交。
 *
 * 读是纯内存；写只碰受影响的行，`edit{}` 内的多次 put 合并成一次事务。
 * 每个命名空间只能有一个实例（[SqliteStorage.store] 缓存），两份快照会互相看不见。
 * `edit{}` 在副本上生效，提交失败时内存同步回滚。
 */
class SqliteKeyValueStore(
    private val backend: SqlBackend,
    private val namespace: String,
) : KeyValueStore {

    private val lock = Any()

    /** 全量内存快照。读写一律持 [lock]。 */
    private var data: LinkedHashMap<String, KvRow> = LinkedHashMap()

    /** 变更监听。强引用；由注册方持 token 注销。 */
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
            // 类型被写成字符串时按内容解析，解析不出用默认值
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

        // 先在副本上跑：块内抛异常时原状态不动
        val working = synchronized(lock) { LinkedHashMap(data) }
        EditorFacade(working, affected) { cleared = true }.block()

        synchronized(lock) {
            val previous = data
            // 只把「受影响的键」合进最新快照，不整张替换：块在锁外执行，这期间别的线程可能
            // 写入了别的键，整张替换会把它们从内存抹掉（磁盘上还在）—— 只有重启才恢复，
            // 表现为「设置改了但界面一直读旧值」。
            val merged = if (cleared) {
                LinkedHashMap(working)
            } else {
                LinkedHashMap(previous).apply {
                    for (key in affected) {
                        val row = working[key]
                        if (row != null) put(key, row) else remove(key)
                    }
                }
            }
            data = merged
            try {
                backend.transaction {
                    if (cleared) {
                        clearNamespace(namespace)
                        merged.values.forEach { upsert(namespace, it) }
                    } else {
                        for (key in affected) {
                            val r = merged[key]
                            if (r != null) upsert(namespace, r) else delete(namespace, key)
                        }
                    }
                }
            } catch (t: Throwable) {
                // 内存同步回滚，避免与磁盘分叉
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
        // 函数类型擦除，只能按引用移除
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

    /** 在副本 [working] 上按调用序生效，并记录受影响键。 */
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
            // 清掉的每个键都要通知
            affected += working.keys.toList()
            working.clear()
            onClear()
        }
    }

    companion object {
        const val TAG = "SqliteKeyValueStore"

        // 与 FileKeyValueStore 的标签一字不差；改动会废掉两端备份兼容
        const val TYPE_STRING = "s"
        const val TYPE_INT = "i"
        const val TYPE_LONG = "l"
        const val TYPE_FLOAT = "f"
        const val TYPE_BOOL = "b"
        const val TYPE_STRING_SET = "ss"
    }
}
