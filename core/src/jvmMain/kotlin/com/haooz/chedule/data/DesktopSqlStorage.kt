package com.haooz.chedule.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import java.io.File
import java.util.Properties

/**
 * 桌面的存储装配：与 Android / iOS 同一个 `nexio.db` schema、同一份 [SqlDriverBackend]。
 *
 * 与 Android 的差别只有 driver（`JdbcSqliteDriver`）和「没有 SharedPreferences 要导入」。
 * 库文件放在调用方给的数据目录下，与旧版 `*.json` 偏好文件同目录、不同名。
 *
 * ⚠ `JdbcSqliteDriver` **不像** `AndroidSqliteDriver` / `NativeSqliteDriver` 那样接 schema
 * 自动建表/迁移，必须自己读 `PRAGMA user_version` 再决定 create 还是 migrate。
 * 漏了这一步，老库会停在旧版本、查询新列直接失败。
 */
object DesktopSqlStorage {

    private var impl: SqlNamespaceStorage? = null

    private fun requireStorage(): SqlNamespaceStorage =
        impl ?: error("DesktopSqlStorage 未初始化：请先调用 init(数据目录)")

    /**
     * @param directory 数据目录，库文件为其中的 `nexio.db`。
     * 目录不存在时创建；空库建表，旧库按 schema 版本迁移。
     */
    fun init(directory: File) {
        if (impl != null) return
        directory.mkdirs()
        val driver = openDriver(File(directory, SqlDriverBackend.DB_NAME))
        val version = driver.userVersion()
        when {
            version == 0L -> NexioDatabase.Schema.create(driver)
            version < NexioDatabase.Schema.version ->
                NexioDatabase.Schema.migrate(driver, version, NexioDatabase.Schema.version)
        }
        impl = SqlNamespaceStorage(SqlDriverBackend(driver))
    }

    /**
     * 建桌面的 driver。几条连接级 pragma 必须从**连接属性**进来，不能在
     * [SqlDriverBackend] 里跑：
     *
     * 实测（sqlite-jdbc）`driver.execute("PRAGMA foreign_keys = ON")` 是**静默无效**的 ——
     * 回读仍是 0，而它走 `executeQuery` 又直接抛「Query does not return results」。
     * 也就是说桌面端的外键其实一直是关着的（级联与 RESTRICT 全是装饰）。
     * 连接属性是 sqlite-jdbc 唯一可靠的注入点；journal / synchronous 同理。
     */
    internal fun openDriver(dbFile: File): JdbcSqliteDriver {
        val properties = Properties().apply {
            put("foreign_keys", "true")
            put("journal_mode", "WAL")
            put("synchronous", "NORMAL")
        }
        return JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", properties)
    }

    /** `PRAGMA user_version`：0 表示空库（从未 create 过）。 */
    private fun SqlDriver.userVersion(): Long =
        executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
            parameters = 0,
            binders = null,
        ).value ?: 0L

    /** 取某个命名空间的键值存储。未初始化时抛异常，没有第二套存储可退。 */
    fun store(namespace: String): KeyValueStore = requireStorage().store(namespace)

    /** 课程行表门面。 */
    fun courseRows(): CourseRowStore = requireStorage().courseRows()
}
