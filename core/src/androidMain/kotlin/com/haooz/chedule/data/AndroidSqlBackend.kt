package com.haooz.chedule.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * [SqlBackend] 的 Android 实现：一个 `nexio_kv.db`，两张表。
 *
 * ```
 * kv(ns, k, type, s, i, f, b)   -- 主键 (ns, k)，ns 就是原来的「偏好文件名」
 * meta(k, v)                    -- 迁移标记，不放业务数据
 * ```
 *
 * ## 落盘格式
 *
 * 每个值只占一列：字符串/字符串集合进 `s`（集合存 JSON 数组），整型/长整型进 `i`，
 * 浮点进 `f`，布尔进 `b`。`type` 列是唯一真相 —— 不靠「这一列非空」去猜类型，
 * 因为 SQLite 的 INTEGER 读出来永远是 Long，靠猜就会重演 FileKeyValueStore 注释里
 * 记的那个坑（Int 被写成 Long → Android `getInt` 抛 ClassCastException → 设置静默丢失）。
 *
 * ## 一致性
 *
 * - WAL 模式（`setWriteAheadLoggingEnabled`）：写入是顺序追加，进程被杀不会留下半截事务；
 *   WAL 下 SQLite 默认 `synchronous=NORMAL`，小事务不必真 fsync。
 * - 所有写操作都走 [transaction]，`beginTransaction` / `setTransactionSuccessful` /
 *   `endTransaction` 三步齐全 —— 漏掉 `setTransactionSuccessful` 会静默回滚，是最常见的写法错误。
 */
class AndroidSqlBackend(context: Context) : SqlBackend {

    private val helper = object : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS kv (
                    ns   TEXT NOT NULL,
                    k    TEXT NOT NULL,
                    type TEXT NOT NULL,
                    s    TEXT,
                    i    INTEGER,
                    f    REAL,
                    b    INTEGER,
                    PRIMARY KEY (ns, k)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS meta (k TEXT NOT NULL PRIMARY KEY, v TEXT NOT NULL)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    init {
        // 必须在第一次 getWritableDatabase 之前设置，之后再调用只影响新开连接
        helper.setWriteAheadLoggingEnabled(true)
    }

    private val json = Json { ignoreUnknownKeys = true }

    override fun <T> transaction(block: SqlBackend.SqlTx.() -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val result = TxImpl(db).block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    override fun loadNamespace(namespace: String): List<KvRow> {
        val db = helper.writableDatabase
        return db.rawQuery(SELECT_NS, arrayOf(namespace)).use { c -> c.readRows() }
    }

    override fun readMeta(key: String): String? {
        val db = helper.writableDatabase
        return db.rawQuery("SELECT v FROM meta WHERE k = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }

    private inner class TxImpl(private val db: SQLiteDatabase) : SqlBackend.SqlTx {

        override fun upsert(namespace: String, row: KvRow) {
            val values = ContentValues(7).apply {
                put("ns", namespace)
                put("k", row.key)
                put("type", row.type)
                when (row.type) {
                    SqliteKeyValueStore.TYPE_STRING -> put("s", row.value as String)
                    SqliteKeyValueStore.TYPE_INT -> put("i", row.value as Int)
                    SqliteKeyValueStore.TYPE_LONG -> put("i", row.value as Long)
                    SqliteKeyValueStore.TYPE_FLOAT -> put("f", row.value as Float)
                    SqliteKeyValueStore.TYPE_BOOL -> put("b", if (row.value as Boolean) 1 else 0)
                    SqliteKeyValueStore.TYPE_STRING_SET -> put("s", encodeSet(row.value))
                    else -> error("未知的键值类型标签：${row.type}")
                }
            }
            db.insertWithOnConflict("kv", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }

        override fun delete(namespace: String, key: String) {
            db.delete("kv", "ns = ? AND k = ?", arrayOf(namespace, key))
        }

        override fun clearNamespace(namespace: String) {
            db.delete("kv", "ns = ?", arrayOf(namespace))
        }

        override fun load(namespace: String): List<KvRow> =
            db.rawQuery(SELECT_NS, arrayOf(namespace)).use { c -> c.readRows() }

        override fun putMeta(key: String, value: String) {
            val values = ContentValues(2).apply {
                put("k", key)
                put("v", value)
            }
            db.insertWithOnConflict("meta", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun Cursor.readRows(): List<KvRow> {
        val out = ArrayList<KvRow>(count)
        while (moveToNext()) {
            val key = getString(0)
            val type = getString(1)
            val value: Any = when (type) {
                SqliteKeyValueStore.TYPE_STRING -> getString(2) ?: ""
                // 按 type 列还原：i 列读出来是 Long，但标签说它是 Int，就必须还成 Int
                SqliteKeyValueStore.TYPE_INT -> getInt(3)
                SqliteKeyValueStore.TYPE_LONG -> getLong(3)
                SqliteKeyValueStore.TYPE_FLOAT -> getFloat(4)
                SqliteKeyValueStore.TYPE_BOOL -> getInt(5) != 0
                SqliteKeyValueStore.TYPE_STRING_SET -> decodeSet(getString(2))
                else -> {
                    NexioLog.w(TAG, "未知的类型标签 '$type'（key=$key），按字符串抢救")
                    getString(2) ?: ""
                }
            }
            out += KvRow(key, if (type in KNOWN_TYPES) type else SqliteKeyValueStore.TYPE_STRING, value)
        }
        return out
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
        const val DB_NAME = "nexio_kv.db"
        const val DB_VERSION = 1
        const val SELECT_NS = "SELECT k, type, s, i, f, b FROM kv WHERE ns = ?"
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
