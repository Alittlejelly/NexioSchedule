package com.haooz.chedule.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import com.haooz.chedule.data.db.ScheduleQueries
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * [SqlBackend] 的 Android 实现。整个应用一个库文件 `nexio.db`，`pref` 与实体表同库同连接。
 *
 * - schema 归 SQLDelight，此文件不建表；
 * - 开库后必须 `PRAGMA foreign_keys = ON`（SQLite 默认关闭，漏了外键都不生效）；
 * - journal 用平台默认（WAL + NORMAL）；
 * - 写操作一律走 [transaction]。
 */
class AndroidSqlBackend(context: Context) : SqlBackend {

    private val driver: SqlDriver = AndroidSqliteDriver(
        schema = NexioDatabase.Schema,
        context = context.applicationContext,
        name = DB_NAME,
    )

    /**
     * 同一个 [SqlDriver] 上的数据库实例。`internal` 给 [SqliteCourseRowStore] 用 ——
     * **必须是同一个实例**：键值表和课程表在同一份连接/事务里，两个实例会让「删课表 + 清课程」
     * 退回成两次提交。
     */
    internal val database = NexioDatabase(driver)

    private val db: NexioDatabase get() = database

    /** 本文件（Schedule.sq）里声明的全部查询。 */
    private val q: ScheduleQueries get() = db.scheduleQueries

    init {
        // 必须在任何事务之前：这个开关是**连接级**的，漏了就是所有外键静默失效
        driver.execute(identifier = null, sql = "PRAGMA foreign_keys = ON", parameters = 0)
    }

    private val json = Json { ignoreUnknownKeys = true }

    override fun <T> transaction(block: SqlBackend.SqlTx.() -> T): T =
        db.transactionWithResult { TxImpl().block() }

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

    private companion object {
        const val TAG = "AndroidSqlBackend"
        const val DB_NAME = "nexio.db"
        val KNOWN_TYPES = setOf(
            SqliteKeyValueStore.TYPE_STRING,
            SqliteKeyValueStore.TYPE_INT,
            SqliteKeyValueStore.TYPE_LONG,
            SqliteKeyValueStore.TYPE_FLOAT,
            SqliteKeyValueStore.TYPE_BOOL,
            SqliteKeyValueStore.TYPE_STRING_SET,
        )
    }
}
