package com.haooz.chedule.data

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
 * - 开库后必须 `PRAGMA foreign_keys = ON`（SQLite 默认关闭，漏了外键都不生效）；
 * - journal 用平台默认（WAL + `synchronous=NORMAL`，实测）；
 * - 写操作一律走 [transaction]。
 */
class SqlDriverBackend(private val driver: SqlDriver) : SqlBackend {

    /** 同一个 driver 上的数据库实例。**必须是同一个实例**：键值表和课程表在同一份连接/事务里。 */
    val database = NexioDatabase(driver)

    private val q: ScheduleQueries get() = database.scheduleQueries

    init {
        // 必须在任何事务之前：这个开关是**连接级**的，漏了就是所有外键静默失效
        driver.execute(identifier = null, sql = "PRAGMA foreign_keys = ON", parameters = 0)
        // journal 模式不在这里设：WAL + synchronous=NORMAL 是平台建库时的默认
        // （真机 `pragma journal_mode` 实测 wal）。`PRAGMA journal_mode=WAL` 会返回一行结果，
        // `driver.execute` 只认 executeUpdateDelete，会抛「Queries can be performed using query only」——
        // 每次启动刷一条带栈的警告，且什么也没改。要改 journal 得走 query API。
    }

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
