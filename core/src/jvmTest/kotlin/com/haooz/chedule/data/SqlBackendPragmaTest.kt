package com.haooz.chedule.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.haooz.chedule.data.db.NexioDatabase
import com.haooz.chedule.data.storage.DesktopSqlStorage
import com.haooz.chedule.data.storage.SqlDriverBackend
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [com.haooz.chedule.data.storage.SqlDriverBackend] 开库时钉住的三条 pragma。
 *
 * 这三条都是**连接级**的、且 schema 迁移带不上，只能靠「建完 driver 立刻设」：
 * - `foreign_keys` 漏了 → 所有外键约束静默失效（级联删不发生，孤儿行开始堆积）；
 * - `journal_mode` 漏了 → 桌面 / iOS 落在 SQLite 默认的 DELETE 模式，每次 commit 都 fsync；
 * - `synchronous` 与 journal 配套：WAL 下留在 FULL 就是白等磁盘。
 *
 * 用真文件库（`:memory:` 不支持 WAL），并且读的是驱动自己回出来的值 ——
 * 这条链上最容易犯的错就是「以为设上了」（sqlite-jdbc 对 `PRAGMA synchronous` 的
 * `execute` 就是静默无效的，所以桌面端必须靠连接属性，见 [com.haooz.chedule.data.storage.DesktopSqlStorage.openDriver]）。
 */
class SqlBackendPragmaTest {

    private var dbFile: File? = null
    private var driver: SqlDriver? = null

    @AfterTest
    fun tearDown() {
        driver?.close()
        dbFile?.let { db ->
            File(db.absolutePath + "-wal").delete()
            File(db.absolutePath + "-shm").delete()
            db.delete()
        }
    }

    @Test
    fun `开库即钉住 WAL 与 NORMAL，外键同时打开`() {
        val db = File.createTempFile("nexio-pragma", ".db").also { it.delete() }
        dbFile = db
        // 与桌面端同一条建库路径：三条连接属性 + backend 里的 pragma
        val d = DesktopSqlStorage.openDriver(db)
        driver = d
        NexioDatabase.Schema.create(d)

        SqlDriverBackend(d)

        assertEquals("wal", d.pragma("PRAGMA journal_mode"), "journal 必须是 WAL，否则每笔提交都 fsync")
        assertEquals("1", d.pragma("PRAGMA synchronous"), "WAL 下 synchronous 应为 NORMAL(1)")
        assertEquals("1", d.pragma("PRAGMA foreign_keys"), "外键必须打开，否则级联与 RESTRICT 全是装饰")
    }

    /**
     * 测试用的内存库也必须真开外键。
     *
     * 这不是形式主义：`driver.execute("PRAGMA foreign_keys = ON")` 在 sqlite-jdbc 上是静默
     * 无效的，整个 JVM 测试套件曾经因此在「外键关闭」的库上跑 —— 级联删、RESTRICT 这些断言
     * 全都空跑。所以外键改由连接属性注入（[openInMemoryDriver]），这里守着它没被改回去。
     */
    @Test
    fun `测试用内存库真的开了外键`() {
        val d = openInMemoryDriver()
        try {
            assertEquals("1", d.pragma("PRAGMA foreign_keys"))
        } finally {
            d.close()
        }
    }

    /** 只有查询形态（返回一行）能走 query API。 */
    private fun SqlDriver.pragma(sql: String): String? =
        executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
            parameters = 0,
            binders = null,
        ).value
}
