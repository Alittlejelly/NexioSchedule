package com.haooz.chedule.data

import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * [com.haooz.chedule.data.storage.AppStorage] 实例唯一性：同名只创建一个实例（两份内存快照会互相看不见）。
 */
class AppStorageInstanceTest {

    @Test
    fun `同一个名字只调用一次工厂`() {
        var created = 0
        AppStorage.init {
            created++
            InMemoryKeyValueStore()
        }
        val a = AppStorage.store("app_preferences")
        val b = AppStorage.store("app_preferences")

        assertSame(a, b, "同名必须拿到同一个实例，否则两份快照会互相看不见")
        assertEquals(1, created, "缓存没生效：工厂被调用多次")
    }

    @Test
    fun `写入在另一个调用点立即可见`() {
        AppStorage.init { InMemoryKeyValueStore() }
        AppStorage.store("app_preferences").edit { putBoolean("island_expand_glow_enabled", true) }

        assertEquals(
            true,
            AppStorage.store("app_preferences").getBoolean("island_expand_glow_enabled", false),
            "写完再取必须是同一个实例，否则这里会读到默认值",
        )
    }

    @Test
    fun `不同名字互相隔离`() {
        AppStorage.init { InMemoryKeyValueStore() }
        AppStorage.store("app_preferences").edit { putInt("k", 1) }
        AppStorage.store("course_reminder_prefs").edit { putInt("k", 2) }

        assertEquals(1, AppStorage.store("app_preferences").getInt("k", 0))
        assertEquals(2, AppStorage.store("course_reminder_prefs").getInt("k", 0))
    }
}
