package com.haooz.chedule.data

/**
 * 按命名空间发 [KeyValueStore]，与课程行表共用同一个 [SqlDriverBackend]。
 *
 * 用于「没有历史存储需要迁移」的平台（iOS / 桌面）。Android 的等价物是 [SqliteStorage]，
 * 它多一层 SharedPreferences 导入，这里不需要。
 *
 * 每个命名空间只能有一个实例：两个实例 = 两份互相看不见的内存快照。
 */
class SqlNamespaceStorage(private val backend: SqlDriverBackend) {

    private val stores = mutableMapOf<String, SqliteKeyValueStore>()

    private var rows: CourseRowStore? = null

    /** 取某个命名空间的存储。异常外抛，调用方没有别的存储可用。 */
    fun store(namespace: String): KeyValueStore = synchronizedOn(this) {
        stores.getOrPut(namespace) { SqliteKeyValueStore(backend, namespace) }
    }

    /** 课程行表门面，与键值表共用同一个库连接。 */
    fun courseRows(): CourseRowStore = synchronizedOn(this) {
        rows ?: SqliteCourseRowStore(backend.database).also { rows = it }
    }
}
