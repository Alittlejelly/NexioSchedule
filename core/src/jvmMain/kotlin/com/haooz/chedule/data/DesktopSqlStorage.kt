package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import java.io.File

/**
 * 桌面的存储装配：与 Android / iOS 同一个 `nexio.db` schema、同一份 [SqlDriverBackend]。
 *
 * 与 Android 的差别只有 driver（`JdbcSqliteDriver`）和「没有 SharedPreferences 要导入」。
 * 库文件放在调用方给的数据目录下，与旧版 `*.json` 偏好文件同目录、不同名。
 */
object DesktopSqlStorage {

    private var impl: SqlNamespaceStorage? = null

    private fun requireStorage(): SqlNamespaceStorage =
        impl ?: error("DesktopSqlStorage 未初始化：请先调用 init(数据目录)")

    /**
     * @param directory 数据目录，库文件为其中的 `nexio.db`。
     * 目录不存在时创建；库文件不存在或为空时建表。
     */
    fun init(directory: File) {
        if (impl != null) return
        directory.mkdirs()
        val dbFile = File(directory, SqlDriverBackend.DB_NAME)
        val fresh = !dbFile.exists() || dbFile.length() == 0L
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        // 空库必须建表；已有库交给 SQLDelight 的 schema 版本管理，不重复 create
        if (fresh) NexioDatabase.Schema.create(driver)
        impl = SqlNamespaceStorage(SqlDriverBackend(driver))
    }

    /** 取某个命名空间的键值存储。未初始化时抛异常，没有第二套存储可退。 */
    fun store(namespace: String): KeyValueStore = requireStorage().store(namespace)

    /** 课程行表门面。 */
    fun courseRows(): CourseRowStore = requireStorage().courseRows()
}
