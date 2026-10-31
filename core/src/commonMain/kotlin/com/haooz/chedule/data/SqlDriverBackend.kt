package com.haooz.chedule.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.haooz.chedule.data.db.NexioDatabase
import com.haooz.chedule.data.db.ScheduleQueries
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * [SqlBackend] 的通用实现：只依赖 [SqlDriver]，三端共用一份。
 *
 * 平台侧只负责「把 driver 造出来」（Android 的 [AndroidSqliteDriver]、iOS 的
 * `NativeSqliteDriver`、桌面的 `JdbcSqliteDriver`），SQL 细节、类型映射、
 * 事务边界都在这里，改一处三端同时生效。
 *
 * - schema 归 SQLDelight，这里不建表；
 * - 连接级 pragma 见 [applyPragmas]（Android / iOS 由这里设，桌面走连接属性）；
 * - 写操作一律走 [transaction]。
 */
class SqlDriverBackend(private val driver: SqlDriver) : SqlBackend {

    /** 同一个 driver 上的数据库实例。**必须是同一个实例**：键值表和课程表在同一份连接/事务里。 */
    val database = NexioDatabase(driver)

    private val q: ScheduleQueries get() = database.scheduleQueries

    init {
        applyPragmas()
    }

    /**
     * 连接级 pragma，全部在三端的 driver 建好之后、任何事务之前跑。
     *
     * - `foreign_keys = ON`：SQLite 默认关，漏了所有外键都是装饰。
     * - `journal_mode = WAL`：**会返回一行结果，只能走 query API**。`driver.execute` 只认
     *   `executeUpdateDelete`，对它抛「Queries can be performed using query only」。
     *   不依赖平台默认：Android 的 framework 会开 WAL，但 `JdbcSqliteDriver` /
     *   `NativeSqliteDriver` 落在 SQLite 默认的 DELETE 模式，每次 commit 都 fsync。
     * - `synchronous = NORMAL`：WAL 下的推荐值（不 fsync 每笔提交，掉电最坏丢最后一批）。
     *   只在读回来不是 NORMAL 时才动，改完回读确认 —— 这条 pragma 的失败方式是**静默无效**。
     *
     * ⚠ **桌面端不靠这里**：实测 sqlite-jdbc 上 `driver.execute("PRAGMA foreign_keys = ON")`
     * 是静默无效的（回读仍是 0），因为 `foreign_keys` / `synchronous` 不能在事务里改，
     * 而 JDBC 的 `execute` 走事务路径（`user_version` 这种就不受影响，所以不容易发现）。
     * 桌面三条全部从连接属性注入，见 `DesktopSqlStorage.openDriver`。
     * Android / iOS 的 driver 是直连 SQLite，`execute` 不包事务，这里的 pragma 对它们有效。
     *
     * 失败只记日志不抛：库打不开会在别处以更明确的方式失败，
     * 这里抛反而把「性能参数没设上」升级成「启动崩溃」。
     */
    private fun applyPragmas() {
        driver.execute(identifier = null, sql = "PRAGMA foreign_keys = ON", parameters = 0)
        // 回读：这条 pragma 在 JDBC 上静默失效，不回读就等于「以为开了外键」。
        // 桌面端由连接属性补上，读到 0 只可能是平台 driver 出了问题。
        if (driver.pragmaText("PRAGMA foreign_keys") != "1") {
            NexioLog.e(TAG, "foreign_keys 没打开：级联删与 RESTRICT 都会失效（查 driver 的 pragma 是否真的执行）")
        }

        val journal = driver.pragmaText("PRAGMA journal_mode = WAL")
        if (journal?.lowercase() != "wal") {
            NexioLog.w(TAG, "journal_mode 没切成 WAL（实际 ${journal ?: "null"}），写入会按 DELETE 模式 fsync")
        }

        if (driver.pragmaText("PRAGMA synchronous") == SYNCHRONOUS_NORMAL) return
        driver.pragmaText("PRAGMA synchronous = NORMAL")
        val applied = driver.pragmaText("PRAGMA synchronous")
        if (applied != SYNCHRONOUS_NORMAL) {
            NexioLog.w(TAG, "synchronous 没切成 NORMAL（实际 ${applied ?: "null"}），提交会 fsync 到磁盘")
        }
    }

    /**
     * 读一行文本的 pragma。赋值型 pragma 返回结果集，`execute` 走不通。
     *
     * 执行失败只返回 null（有的驱动对「不带结果集的 PRAGMA」直接抛）。
     */
    private fun SqlDriver.pragmaText(sql: String): String? = runCatching {
        executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
            },
            parameters = 0,
            binders = null,
        ).value
    }.getOrNull()

    private val json = Json { ignoreUnknownKeys = true }

    override fun <T> transaction(block: SqlBackend.SqlTx.() -> T): T =
        database.transactionWithResult { TxImpl().block() }

    override fun loadNamespace(namespace: String): List<KvRow> =
        q.selectPrefNamespace(namespace).executeAsList().map { it.toKvRow() }

    override fun readMeta(key: String): String? =
        q.readMeta(key).executeAsOneOrNull()

    private inner class TxImpl : SqlBackend.SqlTx {

        override fun upsert(namespace: String, row: KvRow) {
            var s: String? = null
            var i: Long? = null
            var f: Double? = null
            var b: Long? = null
            when (row.type) {
                SqliteKeyValueStore.TYPE_STRING -> s = row.value as String
                // Int 也走 i 列（SQLite 只有 INTEGER），读回时按 type 标签还原成 Int
                SqliteKeyValueStore.TYPE_INT -> i = (row.value as Int).toLong()
                SqliteKeyValueStore.TYPE_LONG -> i = row.value as Long
                // REAL 只有 Double：Float→Double→Float 是精确的，不会丢精度
                SqliteKeyValueStore.TYPE_FLOAT -> f = (row.value as Float).toDouble()
                SqliteKeyValueStore.TYPE_BOOL -> b = if (row.value as Boolean) 1L else 0L
                SqliteKeyValueStore.TYPE_STRING_SET -> s = encodeSet(row.value)
                else -> error("未知的键值类型标签：${row.type}")
            }
            q.upsertPref(
                ns = namespace,
                k = row.key,
                type = row.type,
                s = s,
                i = i,
                f = f,
                b = b,
            )
        }

        override fun delete(namespace: String, key: String) {
            q.deletePref(namespace, key)
        }

        override fun clearNamespace(namespace: String) {
            q.clearPrefNamespace(namespace)
        }

        override fun load(namespace: String): List<KvRow> =
            q.selectPrefNamespace(namespace).executeAsList().map { it.toKvRow() }

        override fun putMeta(key: String, value: String) {
            q.writeMeta(key, value)
        }
    }

    /** 行 → [KvRow]。类型以 `type` 列为准，不靠「哪一列非空」猜。 */
    private fun com.haooz.chedule.data.db.SelectPrefNamespace.toKvRow(): KvRow {
        val value: Any = when (type) {
            SqliteKeyValueStore.TYPE_STRING -> s ?: ""
            SqliteKeyValueStore.TYPE_INT -> (i ?: 0L).toInt()
            SqliteKeyValueStore.TYPE_LONG -> i ?: 0L
            SqliteKeyValueStore.TYPE_FLOAT -> (f ?: 0.0).toFloat()
            SqliteKeyValueStore.TYPE_BOOL -> (b ?: 0L) != 0L
            SqliteKeyValueStore.TYPE_STRING_SET -> decodeSet(s)
            else -> {
                NexioLog.w(TAG, "未知的类型标签 '$type'（key=$k），按字符串抢救")
                s ?: ""
            }
        }
        val safeType = if (type in KNOWN_TYPES) type else SqliteKeyValueStore.TYPE_STRING
        return KvRow(k, safeType, value)
    }

    private fun encodeSet(value: Any): String {
        @Suppress("UNCHECKED_CAST")
        val set = value as? Set<String> ?: emptySet()
        return buildJsonArray { set.forEach { add(JsonPrimitive(it)) } }.toString()
    }

    private fun decodeSet(text: String?): Set<String> {
        if (text.isNullOrEmpty()) return emptySet()
        val parsed = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonArray
            ?: return emptySet()
        return parsed.mapNotNull { it.jsonPrimitive.contentOrNull }.toCollection(LinkedHashSet())
    }

    companion object {
        const val TAG = "SqlDriverBackend"

        /** 三端统一的库文件名。改名等于把用户数据留在旧文件里读不到。 */
        const val DB_NAME = "nexio.db"

        /** `PRAGMA synchronous` 的 NORMAL 值（0=OFF, 1=NORMAL, 2=FULL）。 */
        const val SYNCHRONOUS_NORMAL = "1"

        private val KNOWN_TYPES = setOf(
            SqliteKeyValueStore.TYPE_STRING,
            SqliteKeyValueStore.TYPE_INT,
            SqliteKeyValueStore.TYPE_LONG,
            SqliteKeyValueStore.TYPE_FLOAT,
            SqliteKeyValueStore.TYPE_BOOL,
            SqliteKeyValueStore.TYPE_STRING_SET,
        )
    }
}

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
