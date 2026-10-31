package com.haooz.chedule.data

import com.haooz.chedule.data.storage.deletePrefFiles
import com.haooz.chedule.data.storage.legacyPrefNames
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 旧存储收尾迁移的**文件层**口径：迁哪些、删哪些。
 * 删错一个文件就是用户数据没了，所以这里盯的是「范围」，不是流程。
 */
class LegacyStorageMigrationFilesTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "legacy-migration-test").apply {
        deleteRecursively()
        mkdirs()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun touch(name: String) = File(dir, name).writeText("<map/>")

    @Test
    fun `命名空间取全部 xml 并排除回退账本`() {
        touch("course_schedule_prefs.xml")
        touch("app_preferences.xml")
        // 回退账本不是业务数据：不导入（库里不留一行），文件单独删
        touch("storage_fallback_state.xml")
        // 只剩 Android 写偏好时留的副本 / 半成品，也必须算作待清理的命名空间
        touch("reminder_sent_history.xml.bak")
        // 实测有设备上只剩 `<名>.bak`（老版本自己做的副本），同样要认
        touch("edu_import_prefs.bak")
        // 非偏好文件不该被当成命名空间
        touch("notes.txt")

        assertEquals(
            listOf("app_preferences", "course_schedule_prefs", "edu_import_prefs", "reminder_sent_history"),
            legacyPrefNames(dir),
        )
    }

    @Test
    fun `删除只覆盖该命名空间自己的文件`() {
        touch("a.xml")
        touch("a.xml.bak")
        touch("a.bak")
        touch("ab.xml")
        touch("b.xml")

        assertTrue(deletePrefFiles(dir, "a"))

        assertFalse(File(dir, "a.xml").exists())
        assertFalse(File(dir, "a.xml.bak").exists())
        assertFalse(File(dir, "a.bak").exists())
        // 前缀匹配不能把 ab 一起吃掉了
        assertTrue(File(dir, "ab.xml").exists())
        assertTrue(File(dir, "b.xml").exists())
    }

    @Test
    fun `空目录与不存在的目录都不炸`() {
        assertEquals(emptyList(), legacyPrefNames(dir))
        assertFalse(deletePrefFiles(dir, "missing"))
        assertFalse(deletePrefFiles(File(dir, "nope"), "a"))
    }
}
