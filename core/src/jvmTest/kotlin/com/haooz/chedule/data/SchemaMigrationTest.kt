package com.haooz.chedule.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.haooz.chedule.data.db.NexioDatabase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 真实升级路径：拿 v1 基线库插上数据，跑 `Schema.migrate(1, 2)`，验证
 * 「旧数据还在 + 新列拿到正确默认值」。
 *
 * `verifyMigrations` 只保证「迁移脚本执行完的 schema 与 .sq 声明一致」，
 * 它不证明旧行能读出来 —— 而 Android 1.6.7 用户的库就是 v1。
 *
 * 基线库由 `:core:generateCommonMainNexioDatabaseSchema` 生成，改 schema 前要先落一份。
 */
class SchemaMigrationTest {

    @Test
    fun `v1 库升到 v2 后旧数据还在且新列取默认值`() {
        val baseline = File("src/commonMain/sqldelight/databases/1.db")
        assertTrue(baseline.exists(), "缺少 v1 基线库：${baseline.absolutePath}")

        val temp = File.createTempFile("nexio-v1-", ".db").apply { deleteOnExit() }
        baseline.copyTo(temp, overwrite = true)

        val driver = JdbcSqliteDriver("jdbc:sqlite:${temp.absolutePath}")
        try {
            // v1 的列：schedule 还没有 smart_weekend / show_non_current_week
            driver.execute(
                identifier = null,
                sql = "INSERT INTO time_config(id, name, payload) VALUES (1, '默认', '{}')",
                parameters = 0,
            )
            driver.execute(
                identifier = null,
                sql = """
                    INSERT INTO schedule(id, name, folder_id, time_config_id, sort_order,
                                         current_week, total_weeks, class_start)
                    VALUES ('s1', 's1', NULL, 1, 0, 3, 18, '2026/09/01')
                """.trimIndent(),
                parameters = 0,
            )
            driver.execute(
                identifier = null,
                sql = "INSERT INTO course(id, schedule_id, name, day_of_week, start_section, end_section, " +
                    "start_week, end_week) VALUES ('c1', 's1', '高等数学', 1, 1, 2, 1, 16)",
                parameters = 0,
            )

            NexioDatabase.Schema.migrate(driver, 1L, NexioDatabase.Schema.version)

            val q = NexioDatabase(driver).scheduleQueries
            // 旧数据原样还在
            val settings = q.selectScheduleSettings("s1").executeAsOne()
            assertEquals(3L, settings.current_week)
            assertEquals(18L, settings.total_weeks)
            assertEquals("高等数学", q.selectCoursesOf("s1").executeAsOne().name)
            // 新列取到与读取口一致的默认值（smart=false / showNonCurrentWeek=true）
            val display = q.selectScheduleDisplaySettings("s1").executeAsOne()
            assertEquals(0L, display.smart_weekend)
            assertEquals(1L, display.show_non_current_week)
        } finally {
            driver.close()
        }
    }
}
