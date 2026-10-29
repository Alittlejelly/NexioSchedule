package com.haooz.chedule.data

import android.content.Context

/**
 * SQLite 键值存储的入口：建库、**一次性从 SharedPreferences 导入并自校验**、按命名空间发 [KeyValueStore]。
 *
 * ## 迁移铁律
 *
 * 1. **不删、不改 SharedPreferences 原文件**。导入只是把数据抄一份进表，SP 文件留在原地当冷快照 ——
 *    万一要回滚版本，旧包还能读出用户出事前的数据。等新存储跑满两个版本再清理。
 * 2. **写完立刻回读比对，比对不过整体回滚**。校验和导入在**同一个事务**里：要么「数据 + 迁移标记」
 *    一起成立，要么什么都没发生。绝不能出现「导了一半、标记已写」—— 那等于永久丢了后半截数据。
 * 3. **失败就退回 SharedPreferences**，不是重试到成功。[NexioApplication] 在工厂里 `runCatching`
 *    兜住，任何异常都让这个命名空间继续走 SP，用户完全无感（只是没享受到新存储）。
 *
 * ## 首次启动的成本
 *
 * 一个命名空间第一次被访问时会同步做导入 + 自校验。`course_schedule_prefs`（数百 KB JSON）
 * 是最大的一笔，量级在几十毫秒；只在**升级后的第一次启动**发生，之后 `store()` 只读一行 meta。
 */
object SqliteStorage {

    private const val TAG = "SqliteStorage"

    /** 迁移标记的前缀。带命名空间，缺一个就少导一个，不会互相顶掉。 */
    private const val META_IMPORTED_PREFIX = "sp_imported:"

    private val lock = Any()

    @Volatile
    private var backend: AndroidSqlBackend? = null

    /** 课程行表门面：和键值表共用同一个 [AndroidSqlBackend]（同一个库、同一个连接）。 */
    @Volatile
    private var courseStore: CourseRowStore? = null

    /** 导入失败过的命名空间：不反复尝试（每次启动都重试等于每次都白花几十毫秒，且日志刷屏）。 */
    private val failedNamespaces = LinkedHashSet<String>()

    /**
     * 取某个命名空间的存储。异常一律外抛，由调用方决定回退策略 —— 这里不替调用方"决定"用哪套存储，
     * 否则回退路径会藏在内部，排查时看不见。
     *
     * @param mirror 过渡期影子写入目标，见 [SqliteKeyValueStore.mirror]。
     */
    fun store(context: Context, namespace: String, mirror: KeyValueStore? = null): KeyValueStore =
        synchronized(lock) {
            val b = backend ?: AndroidSqlBackend(context.applicationContext).also { backend = it }
            importFromSharedPreferencesIfNeeded(context, b, namespace)
            SqliteKeyValueStore(b, namespace, mirror)
        }

    /**
     * 课程行表门面。异常外抛，由 [CourseStorage] 兜住并退回 JSON —— 数据库打不开时
     * 应用仍然要能用（只是没享受到行级写入）。
     */
    fun courseRows(context: Context): CourseRowStore = synchronized(lock) {
        courseStore?.let { return it }
        val b = backend ?: AndroidSqlBackend(context.applicationContext).also { backend = it }
        SqliteCourseRowStore(b.database).also { courseStore = it }
    }

    /** 迁移是否已完成（诊断页 / 统计用）。 */
    fun isImported(namespace: String): Boolean =
        runCatching { backend?.readMeta(META_IMPORTED_PREFIX + namespace) != null }.getOrDefault(false)

    /**
     * 从同名 SharedPreferences 文件导入。
     *
     * 空文件也要写标记：否则每次启动都会去读一次 SP，白花 IO 且掩盖"这个命名空间确实没有数据"。
     */
    private fun importFromSharedPreferencesIfNeeded(
        context: Context,
        backend: SqlBackend,
        namespace: String,
    ) {
        if (namespace in failedNamespaces) error("$namespace 上次导入失败，继续使用 SharedPreferences")
        if (backend.readMeta(META_IMPORTED_PREFIX + namespace) != null) return

        val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
        val snapshot = try {
            prefs.all
        } catch (e: Exception) {
            // SP 文件本身坏了：不导入，也不标记，让它继续走 SP（行为与迁移前一致）
            failedNamespaces += namespace
            error("读取 $namespace 的 SharedPreferences 失败：$e")
        }

        val rows = snapshot.mapNotNull { (key, value) -> value?.let { toRow(key, it) } }
        val unconvertible = snapshot.size - rows.size
        if (unconvertible > 0) {
            // 部分值转不了不算失败：转了的照样导入。但必须留痕，否则就是"静默丢设置"
            NexioLog.w(TAG, "$namespace 有 $unconvertible 个键类型无法识别，已跳过（其余正常导入）")
        }
        if (snapshot.isNotEmpty() && rows.isEmpty()) {
            failedNamespaces += namespace
            error("$namespace 的 ${snapshot.size} 个键一个都没转成功，放弃导入")
        }

        try {
            backend.transaction {
                rows.forEach { upsert(namespace, it) }

                // ── 自校验：把刚写的读回来，逐键比对类型和值 ──
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

        NexioLog.w(TAG, "已从 SharedPreferences 导入 $namespace：${rows.size} 个键")
    }

    /** SharedPreferences 的运行时类型 → 带标签的行。认不出来返回 null（由调用方计数告警）。 */
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
