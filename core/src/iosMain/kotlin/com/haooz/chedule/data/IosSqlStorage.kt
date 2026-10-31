package com.haooz.chedule.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase

/**
 * iOS 的存储装配：与 Android 同一个 `nexio.db` schema、同一份 [SqlDriverBackend]。
 *
 * 与 Android 的差别只有 driver（`NativeSqliteDriver` vs `AndroidSqliteDriver`）和
 * 「没有 SharedPreferences 要导入」—— iOS 从第一天起就用这个库，不存在历史迁移。
 *
 * ⚠ 与 Android 的 `SqliteStorage` 一样，进程内只许有一份 backend：
 * 两个 `NexioDatabase` 实例会让「删课表 + 清课程」退回成两次提交。
 */
object IosSqlStorage {

    private val storage by lazy {
        SqlNamespaceStorage(
            SqlDriverBackend(
                NativeSqliteDriver(
                    schema = NexioDatabase.Schema,
                    name = SqlDriverBackend.DB_NAME,
                ),
            ),
        )
    }

    /** 取某个命名空间的键值存储。异常外抛，没有第二套存储可退。 */
    fun store(namespace: String): KeyValueStore = storage.store(namespace)

    /** 课程行表门面。 */
    fun courseRows(): CourseRowStore = storage.courseRows()
}
