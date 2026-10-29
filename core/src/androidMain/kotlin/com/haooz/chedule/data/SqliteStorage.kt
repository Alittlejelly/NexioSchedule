package com.haooz.chedule.data

import android.content.Context

/**
 * 键值存储入口：建库、按命名空间发 [KeyValueStore]、首次访问时从同名 SharedPreferences 导入一次。
 *
 * 导入规则：
 * - 不删不改 SharedPreferences 原文件，它留作回滚版本的冷快照；
 * - 导入、回读比对、写导入标记在同一个事务里，任一步失败整体回滚且不写标记；
 * - 失败即退回 SharedPreferences（由 [NexioApplication] 的工厂兜住），不重试。
 *
 * 导入只在命名空间第一次 `store()` 时发生，之后只读一行 meta。
 * 冷启动路径上的命名空间由 [NexioApplication] 在 IO 线程预热。
 */
object SqliteStorage {

    private const val TAG = "SqliteStorage"

    /** 导入标记的前缀。带命名空间，缺一个就少导一个，互不影响。 */
    private const val META_IMPORTED_PREFIX = "sp_imported:"

    /** 回退期间的写入标记。单独一个 SP 文件：库打不开时只有 SP 可用，不能存进库。 */
    private const val FALLBACK_PREFS = "storage_fallback_state"
    private const val FALLBACK_KEY = "namespaces"

    private val lock = Any()

    @Volatile
    private var backend: AndroidSqlBackend? = null

    /** 课程行表门面，与键值表共用同一个 [AndroidSqlBackend]。 */
    @Volatile
    private var courseStore: CourseRowStore? = null

    /** 导入失败过的命名空间：不再重试。 */
    private val failedNamespaces = LinkedHashSet<String>()

    /** 每个命名空间唯一的实例。两个实例 = 两份互相看不见的快照。 */
    private val stores = HashMap<String, SqliteKeyValueStore>()

    /** 取某个命名空间的存储。异常外抛，由调用方决定回退。 */
    fun store(context: Context, namespace: String): KeyValueStore =
        synchronized(lock) {
            val b = backend ?: AndroidSqlBackend(context.applicationContext).also { backend = it }
            importFromSharedPreferencesIfNeeded(context, b, namespace)
            stores.getOrPut(namespace) { SqliteKeyValueStore(b, namespace) }
        }

    /** 课程行表门面。异常外抛，由 [CourseStorage] 兜住并退回 JSON。 */
    fun courseRows(context: Context): CourseRowStore = synchronized(lock) {
        courseStore?.let { return it }
        val b = backend ?: AndroidSqlBackend(context.applicationContext).also { backend = it }
        SqliteCourseRowStore(b.database).also { courseStore = it }
    }

    /** 该命名空间是否已导入（诊断页 / 统计用）。 */
    fun isImported(namespace: String): Boolean =
        runCatching { backend?.readMeta(META_IMPORTED_PREFIX + namespace) != null }.getOrDefault(false)

    /**
     * 记下「这个命名空间曾在 SQLite 不可用时退回 SharedPreferences 写过」。
     *
     * 没有这个标记，回退期间写的数据会永久消失：导入标记还在库里，下次启动直接跳过导入，
     * 那份新写进 SP 的数据既不会被读也不会被清。有了标记，下次开库会覆盖式重导一次。
     */
    fun recordFallback(context: Context, namespace: String) {
        runCatching {
            val prefs = context.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
            val marked = prefs.getStringSet(FALLBACK_KEY, emptySet()).orEmpty()
            prefs.edit().putStringSet(FALLBACK_KEY, marked + namespace).commit()
        }.onFailure { NexioLog.w(TAG, "记录回退命名空间失败：$namespace", it) }
    }

    /** 该命名空间是否有「回退期间写入」的待重导标记。 */
    private fun isFallbackMarked(context: Context, namespace: String): Boolean = runCatching {
        context.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
            .getStringSet(FALLBACK_KEY, emptySet()).orEmpty().contains(namespace)
    }.getOrDefault(false)

    /** 重导完成后清标记。失败不清：宁可下次多导一次，也不要把回退期间的数据漏掉。 */
    private fun clearFallbackMark(context: Context, namespace: String) {
        runCatching {
            val prefs = context.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
            val remaining = prefs.getStringSet(FALLBACK_KEY, emptySet()).orEmpty() - namespace
            prefs.edit().putStringSet(FALLBACK_KEY, remaining).commit()
        }
    }

    /** 从同名 SharedPreferences 文件导入。空文件也写标记，表示「这个命名空间确实没有数据」。 */
    private fun importFromSharedPreferencesIfNeeded(
        context: Context,
        backend: SqlBackend,
        namespace: String,
    ) {
        if (namespace in failedNamespaces) error("$namespace 上次导入失败，继续使用 SharedPreferences")
        // 回退期间写过：以 SP 为准覆盖式重导，否则那批改动永远读不到
        val reimport = isFallbackMarked(context, namespace)
        if (!reimport && backend.readMeta(META_IMPORTED_PREFIX + namespace) != null) return

        val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
        val snapshot = try {
            prefs.all
        } catch (e: Exception) {
            // SP 文件损坏：不导入也不标记，继续走 SP
            failedNamespaces += namespace
            error("读取 $namespace 的 SharedPreferences 失败：$e")
        }

        val rows = snapshot.mapNotNull { (key, value) -> value?.let { toRow(key, it) } }
        val unconvertible = snapshot.size - rows.size
        if (unconvertible > 0) {
            // 部分值转不了不算失败，但要留痕
            NexioLog.w(TAG, "$namespace 有 $unconvertible 个键类型无法识别，已跳过（其余正常导入）")
        }
        if (snapshot.isNotEmpty() && rows.isEmpty()) {
            failedNamespaces += namespace
            error("$namespace 的 ${snapshot.size} 个键一个都没转成功，放弃导入")
        }

        try {
            backend.transaction {
                // 覆盖式重导：回退期间被删掉的键不能留在库里。SP 为空时不覆盖 ——
                // 「回退期间没写过东西」与「回退期间清空了一切」分不出来，删数据比留设置贵。
                if (reimport && rows.isNotEmpty()) clearNamespace(namespace)
                rows.forEach { upsert(namespace, it) }

                // 自校验：读回刚写的，逐键比对类型和值
                val written = load(namespace).associateBy { it.key }
                for (row in rows) {
                    val back = written[row.key]
                        ?: error("$namespace/${row.key} 写入后读不回来")
                    if (back.type != row.type) {
                        error("$namespace/${row.key} 类型漂移：写入 ${row.type}，读回 ${back.type}")
                    }
                    if (back.value != row.value) {
                        error("$namespace/${row.key} 值不一致：写入 ${row.value}，读回 ${back.value}")
                    }
                }

                putMeta(META_IMPORTED_PREFIX + namespace, rows.size.toString())
            }
        } catch (t: Throwable) {
            failedNamespaces += namespace
            error("导入 $namespace 失败，已整体回滚：${t.message}")
        }

        if (reimport) clearFallbackMark(context, namespace)
        NexioLog.w(TAG, "已从 SharedPreferences 导入 $namespace：${rows.size} 个键（回退重导=$reimport）")
    }

    /** SharedPreferences 运行时类型 → 带标签的行。认不出返回 null。 */
    private fun toRow(key: String, value: Any): KvRow? = when (value) {
        is String -> KvRow(key, SqliteKeyValueStore.TYPE_STRING, value)
        is Int -> KvRow(key, SqliteKeyValueStore.TYPE_INT, value)
        is Long -> KvRow(key, SqliteKeyValueStore.TYPE_LONG, value)
        is Float -> KvRow(key, SqliteKeyValueStore.TYPE_FLOAT, value)
        is Boolean -> KvRow(key, SqliteKeyValueStore.TYPE_BOOL, value)
        is Set<*> -> KvRow(
            key,
            SqliteKeyValueStore.TYPE_STRING_SET,
            value.mapNotNull { it as? String }.toCollection(LinkedHashSet()),
        )
        else -> null
    }
}
