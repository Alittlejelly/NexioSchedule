package com.haooz.chedule.data

/**
 * 跨平台键值存储。Android 侧由 `SqliteStorage` 提供（`pref` 表），桌面侧是 JSON 文件，
 * iOS 侧是 UserDefaults。
 *
 * 语义约定：
 * 1. 写入走 [edit]，回调里是 [KeyValueEditor]，一次 edit 是一个事务。
 * 2. 类型不匹配时**返回默认值而非抛异常**。
 * 3. [all] 返回拷贝，改动它不影响存储。
 */

/**
 * 「不确定是哪个键」的哨兵常量。
 *
 * `registerListener` 的回调参数在 Android 上是准确的 key，在 iOS 上不是 ——
 * `NSUserDefaultsDidChangeNotification` 只说「有东西变了」，拿不到具体键，iOS 统一传本哨兵。
 * 所以凡是按 key 过滤的监听都必须同时接受它，否则 iOS 上会「收到通知但被过滤掉」，
 * 表现为设置项改了 UI 不刷新。
 */
const val KEY_CHANGED_ANYWHERE: String = "__key_changed_anywhere__"

interface KeyValueStore {
    fun getString(key: String, defaultValue: String): String
    fun getInt(key: String, defaultValue: Int): Int
    fun getLong(key: String, defaultValue: Long): Long
    fun getFloat(key: String, defaultValue: Float): Float
    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    /** Android 的 `getStringSet` 返回的集合是**内部实例的引用**，就地修改会抛异常。
     *  本接口约定返回**副本**，可就地修改。 */
    fun getStringSet(key: String, defaultValue: Set<String>): Set<String>

    fun contains(key: String): Boolean

    /** 全量快照（拷贝）。备份 / 迁移功能依赖它。 */
    fun all(): Map<String, Any?>

    /** 开启一次事务写入。回调内可混合调用任意 put / remove / clear。 */
    fun edit(block: KeyValueEditor.() -> Unit)

    fun remove(key: String)

    fun clear()

    /**
     * 变更监听。iOS 侧只有「不带 key」的通知（传 [KEY_CHANGED_ANYWHERE]），
     * 见 [UserDefaultsStore.registerListener]；确实没有等价能力的平台才返回 null。
     */
    fun registerListener(listener: (String) -> Unit): Any?

    fun unregisterListener(token: Any?)
}

/** 对应 `SharedPreferences.getString(key, null)`：键不存在返回 null；值不是字符串时返回空串。 */
fun KeyValueStore.getStringOrNull(key: String): String? =
    if (contains(key)) getString(key, "") else null

/** 事务内可用的写入面。用完 [KeyValueStore.edit] 的回调即提交。 */
interface KeyValueEditor {
    fun putString(key: String, value: String)
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun putFloat(key: String, value: Float)
    fun putBoolean(key: String, value: Boolean)
    fun putStringSet(key: String, value: Set<String>)
    fun remove(key: String)
    fun clear()
}

/** 内存实现，供 commonTest 做 round-trip 回归。不做线程安全优化：调用点普遍自带 `@Synchronized`。 */
class InMemoryKeyValueStore(
    initial: Map<String, Any?> = emptyMap(),
) : KeyValueStore {
    private val data = LinkedHashMap<String, Any?>(initial)

    override fun getString(key: String, defaultValue: String): String =
        data[key] as? String ?: defaultValue

    override fun getInt(key: String, defaultValue: Int): Int = when (val v = data[key]) {
        is Int -> v
        is Long -> v.toInt()
        else -> defaultValue
    }

    override fun getLong(key: String, defaultValue: Long): Long = when (val v = data[key]) {
        is Long -> v
        is Int -> v.toLong()
        else -> defaultValue
    }

    override fun getFloat(key: String, defaultValue: Float): Float =
        (data[key] as? Number)?.toFloat() ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        data[key] as? Boolean ?: defaultValue

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> {
        val stored = data[key] as? Set<String> ?: return defaultValue
        // 必须用 LinkedHashSet：toSet() 对 size==1 的集合返回共享不可变单例，
        // 调用方就地 add 会抛 UnsupportedOperationException。
        return LinkedHashSet(stored)
    }

    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun all(): Map<String, Any?> = LinkedHashMap(data)

    override fun edit(block: KeyValueEditor.() -> Unit) {
        InMemoryEditor().block()
    }

    override fun remove(key: String) {
        data.remove(key)
    }

    override fun clear() {
        data.clear()
    }

    /** 内存实现不支持跨实例监听。 */
    override fun registerListener(listener: (String) -> Unit): Any? = null

    override fun unregisterListener(token: Any?) = Unit

    private inner class InMemoryEditor : KeyValueEditor {
        override fun putString(key: String, value: String) { data[key] = value }
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun putLong(key: String, value: Long) { data[key] = value }
        override fun putFloat(key: String, value: Float) { data[key] = value }
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun putStringSet(key: String, value: Set<String>) { data[key] = LinkedHashSet(value) }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }
}
