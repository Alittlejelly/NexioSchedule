package com.haooz.chedule.data.storage

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase

/**
 * [com.haooz.chedule.data.storage.SqlBackend] 的 Android 实现：整个应用一个库文件 `nexio.db`，`pref` 与实体表同库同连接。
 *
 * SQL 细节（类型映射、事务、set 编解码）全在 [com.haooz.chedule.data.storage.SqlDriverBackend]，这里只负责造 driver。
 * 三端共用同一份 [com.haooz.chedule.data.storage.SqlDriverBackend]，改一处三端同时生效。
 */
class AndroidSqlBackend(context: Context) : SqlBackend {

    private val impl = SqlDriverBackend(
        AndroidSqliteDriver(
            schema = NexioDatabase.Schema,
            context = context.applicationContext,
            name = SqlDriverBackend.DB_NAME,
        ),
    )

    /**
     * `internal` 给 [com.haooz.chedule.data.storage.SqliteCourseRowStore] 用 —— **必须是同一个实例**：键值表和课程表
     * 在同一份连接/事务里，两个实例会让「删课表 + 清课程」退回成两次提交。
     */
    internal val database: NexioDatabase get() = impl.database

    override fun <T> transaction(block: SqlBackend.SqlTx.() -> T): T = impl.transaction(block)

    override fun loadNamespace(namespace: String): List<KvRow> = impl.loadNamespace(namespace)

    override fun readMeta(key: String): String? = impl.readMeta(key)
}
