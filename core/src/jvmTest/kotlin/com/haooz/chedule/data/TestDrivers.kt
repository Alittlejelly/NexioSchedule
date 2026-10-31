package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

/**
 * 测试用内存库。
 *
 * ⚠ **外键必须走连接属性**，不能用 `driver.execute("PRAGMA foreign_keys = ON")`：
 * 实测 sqlite-jdbc 上那条 pragma 是静默无效的（回读仍是 0），而 `foreign_keys` 又不能在
 * 事务里改 —— SQLDelight 的 `execute` 走的就是事务路径。靠 execute 开外键的测试等于全程
 * 没开外键，「级联删 / RESTRICT」这些断言都是空跑。
 *
 * 属性在连接建立时就生效，不受事务影响。`DesktopSqlStorage.openDriver` 用的是同一套做法。
 */
internal fun openInMemoryDriver(): JdbcSqliteDriver =
    JdbcSqliteDriver(
        JdbcSqliteDriver.IN_MEMORY,
        Properties().apply { put("foreign_keys", "true") },
    )
