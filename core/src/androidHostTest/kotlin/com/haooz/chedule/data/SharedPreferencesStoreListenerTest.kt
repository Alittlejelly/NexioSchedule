package com.haooz.chedule.data

import android.content.SharedPreferences
import java.util.WeakHashMap
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [SharedPreferencesStore] 变更监听的**回归测试**。
 *
 * 背景：AOSP 的 `SharedPreferencesImpl` 用
 * `WeakHashMap<OnSharedPreferenceChangeListener, Object> mListeners` 存放监听器
 * （`android/app/SharedPreferencesImpl.java`），**只持弱引用**。所以包装层必须自己留一份
 * 强引用，并且返回一个能用于卸载的 token —— 二者缺一，回调都会**静默失效**，
 * 线上表现为「切主题、开关怎么点都没反应，重启应用又好了」，且全程无异常。
 *
 * 这个 bug 修过一次（`registerListener` 原先返回平台 void 调用退化成 `Unit`），
 * 但 `commonTest` 跑不到 androidMain，所以这里用一个**逐字复刻 AOSP 弱引用语义**的
 * fake 把它钉死。
 */
class SharedPreferencesStoreListenerTest {

    @Test
    fun `unregisterListener 必须真的摘掉底层监听器（token 不能是 Unit）`() {
        val prefs = FakeSharedPreferences()
        val store = SharedPreferencesStore(prefs)

        var calls = 0
        val token = store.registerListener { calls++ }
        assertEquals(1, prefs.liveListenerCount(), "注册后底层应恰好有 1 个监听器")

        // 未触发 GC，弱引用条目此时必然还在 —— 摘不掉就只能是 token 不对
        store.unregisterListener(token)
        assertEquals(
            0,
            prefs.liveListenerCount(),
            "unregisterListener 没摘掉监听器：token 与注册时返回的对象对不上",
        )

        prefs.simulateChange("theme_mode", true)
        assertEquals(0, calls, "已卸载的监听器不应再收到回调")
    }

    @Test
    fun `注册后必须自带强引用，GC 不能回收掉回调`() {
        val prefs = FakeSharedPreferences()
        val store = SharedPreferencesStore(prefs)

        var calls = 0
        // 在**独立栈帧**里注册：若直接在测试方法内调用，JIT 内联后寄存器/栈槽可能残留
        // 指向监听器的引用，GC 就永远收不掉它 —— 那样这个测试会假通过。
        registerInIsolatedFrame(store) { calls++ }

        // 模拟线上场景：分配压力 + 多次 GC。若实现只依赖 SharedPreferences 的弱引用，
        // 这里监听器就会被回收，下面的回调也就收不到。
        forceGcUntilCollected()

        prefs.simulateChange("monet_color_enabled", true)
        assertEquals(
            1,
            calls,
            "监听器被 GC 回收了 —— 这正是「切主题/开关失灵、重启才好」的成因",
        )
    }

    @Test
    fun `变更派发到所有存活的监听器`() {
        val prefs = FakeSharedPreferences()
        val store = SharedPreferencesStore(prefs)

        var a = 0
        var b = 0
        store.registerListener { a++ }
        store.registerListener { b++ }

        prefs.simulateChange("theme_mode", true)
        assertEquals(1, a)
        assertEquals(1, b)
    }

    /** 隔一层调用，保证监听器只在被调方法的栈帧里出现过，不受测试方法栈槽影响。 */
    private fun registerInIsolatedFrame(store: SharedPreferencesStore, onCall: () -> Unit) {
        store.registerListener { onCall() }
    }

    /**
     * 反复制造分配压力并触发 GC，**直到确认不可达对象真的被回收**。
     *
     * 不能只盲调 `System.gc()`：它是否真的回收取决于 JVM 与堆状态，盲调会让这个测试
     * 在没修复时也偶然通过。这里用一个探针（WeakReference 监视的局部对象）确认
     * 「GC 确实生效了」再返回 —— 探针都被回收了，同样只被弱引用的监听器就不可能幸免。
     */
    private fun forceGcUntilCollected() {
        val ballast = ArrayList<ByteArray>()
        try {
            repeat(4) {
                // 留一点垃圾促成长寿代增长，逼出 full GC
                repeat(16) { ballast.add(ByteArray(64 * 1024)) }
                System.gc()
                if (probeCollected()) return
            }
        } finally {
            ballast.clear()
        }
    }

    /** 探针：新造一个对象、只留弱引用，返回它是否已被回收。 */
    private fun probeCollected(): Boolean {
        val probe = Any()
        val ref = java.lang.ref.WeakReference(probe)
        System.gc()
        return ref.get() == null
    }

    /**
     * 逐字复刻 AOSP `SharedPreferencesImpl` 的监听器语义：`WeakHashMap` 键，只持弱引用。
     * 用普通 Map 会让这个测试失去意义 —— 那样即使实现不留强引用也永远测不出来。
     */
    private class FakeSharedPreferences : SharedPreferences {
        private val listeners =
            WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Any>()
        private val values = LinkedHashMap<String, Any?>()

        private companion object {
            /** 对应 AOSP `SharedPreferencesImpl.CONTENT`：弱引用表里那个不引用键的哨兵值。 */
            val CONTENT = Any()
        }

        /** 底层**弱引用**表里当前还活着几条 —— 模拟 GC 后的真实可见性。 */
        fun liveListenerCount(): Int = listeners.size

        /** 模拟 `apply()` 提交后的变更派发。 */
        fun simulateChange(key: String, value: Boolean) {
            values[key] = value
            listeners.keys.toList().forEach { it.onSharedPreferenceChanged(this, key) }
        }

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) {
            // ⚠ 值必须是**不引用键**的常量标记。写成 `listeners[listener] = listener`
            //   会让值强引用键，WeakHashMap 就永远回收不掉这条 —— 测试会假通过。
            //   AOSP 存的正是 `CONTENT` 这个静态哨兵对象。
            listeners[listener] = CONTENT
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) {
            listeners.remove(listener)
        }

        override fun getAll(): MutableMap<String, *> = LinkedHashMap(values)
        override fun getString(key: String, defValue: String?): String? =
            values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(
            key: String,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = (values[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float =
            values[key] as? Float ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            values[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor()

        private inner class FakeEditor : SharedPreferences.Editor {
            private val pending = LinkedHashMap<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var cleared = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun putStringSet(
                key: String,
                values: MutableSet<String>?,
            ): SharedPreferences.Editor {
                pending[key] = values
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                removed += key
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                cleared = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (cleared) values.clear()
                removed.forEach { values.remove(it) }
                values.putAll(pending)
            }
        }
    }
}
