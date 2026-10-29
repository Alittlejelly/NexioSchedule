package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * [AppStorage] 的实例唯一性契约。
 *
 * 这条契约在换掉 SharedPreferences 之后**从「无所谓」变成了硬要求**：
 * SharedPreferences 时代并发创建两个实例结果等价（都指向同一个文件、同一份内存缓存），
 * 现在的实现是「内存快照 + 行级写」，两个实例就是两份互相看不见的快照 ——
 * 一边写完另一边读旧值、后写者覆盖前者，表现为「设置改了偶发不生效」。
 *
 * 唯一性由平台工厂保证（`SqliteStorage.store` 按命名空间缓存），
 * 本文件守住 AppStorage 这一层不会自我破坏（比如有人把缓存改成不写回）。
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
