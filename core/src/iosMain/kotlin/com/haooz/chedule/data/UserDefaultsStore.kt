@file:OptIn(ExperimentalForeignApi::class)

package com.haooz.chedule.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDefaultsDidChangeNotification
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue

/**
 * [KeyValueStore] 的 iOS 实现：按名字开独立的 `NSUserDefaults(suiteName:)` 域
 * （不用 `standardUserDefaults`，否则 17 个 prefs 文件挤进一个域、同名键互相覆盖）。
 *
 * ⚠ 数值类型必须记到**独立域** `suiteName.__kvtypes`：plist 里数值都是 `NSNumber`，读出来
 * 分不清原本是 Int 还是 Long；统一按 Long 还原会让复制课表把 Int 键写成 Long → Android 侧
 * `getInt` 抛 `ClassCastException` → 被 `runCatching` 兜底成默认值 → **用户设置静默丢失**。
 * 标记键不能与业务键同域（`all()` 会遍历，会被当成业务数据复制/导出）。
 *
 * ⚠ **Windows 上无法编译本文件**（iOS target 需要 Xcode SDK），首次编译须在 macOS 上做。
 */
class UserDefaultsStore(
    private val suiteName: String,
) : KeyValueStore {

    private val defaults = NSUserDefaults(suiteName = suiteName)

    /** 类型标记域。见类注释：绝不和业务键同域。 */
    private val typeDomain = NSUserDefaults(suiteName = "$suiteName.__kvtypes")

    override fun getString(key: String, defaultValue: String): String =
        defaults.stringForKey(key) ?: defaultValue

    override fun getInt(key: String, defaultValue: Int): Int =
        number(key)?.intValue ?: defaultValue

    override fun getLong(key: String, defaultValue: Long): Long =
        number(key)?.longValue ?: defaultValue

    override fun getFloat(key: String, defaultValue: Float): Float =
        number(key)?.floatValue ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        number(key)?.boolValue ?: defaultValue

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> {
        val list = defaults.arrayForKey(key) ?: return defaultValue
        return LinkedHashSet(list.mapNotNull { it as? String })
    }

    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    override fun all(): Map<String, Any?> {
        val domain = defaults.persistentDomainForName(suiteName) ?: return emptyMap()
        val result = LinkedHashMap<String, Any?>(domain.size)
        for ((rawKey, rawValue) in domain) {
            result[rawKey.toString()] = restore(rawKey.toString(), rawValue)
        }
        return result
    }

    override fun edit(block: KeyValueEditor.() -> Unit) {
        UserDefaultsEditor().block()
        // 对应 Android 的 apply()：显式落盘让「提交」语义对齐；失败也不抛（调用方无能为力）
        defaults.synchronize()
        typeDomain.synchronize()
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
        typeDomain.removeObjectForKey(key)
    }

    override fun clear() {
        defaults.removePersistentDomainForName(suiteName)
        typeDomain.removePersistentDomainForName("$suiteName.__kvtypes")
    }

    /**
     * iOS 无「带 key 的变更回调」：`NSUserDefaultsDidChangeNotification` 只通知「变了」，
     * 不带是哪个键 → 回调时传 [KEY_CHANGED_ANYWHERE] 哨兵，由调用方自己复查关心的键。
     *
     * ⚠ 不能留空实现：`CourseScheduleTheme` 靠它监听 `theme_mode`，缺了会导致 iOS 上
     *   深浅色切换写进了磁盘但 UI 永不刷新。
     * ⚠ 必须用 block 版 `addObserverForName`：selector 版在 Kotlin/Native 里签名含 `CPointer`，塞不进 lambda。
     * ⚠ 返回值是 NotificationCenter 内部持有的 handle（不是 token），`removeObserver` 要传的就是它。
     */
    override fun registerListener(listener: (String) -> Unit): Any? =
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = NSUserDefaultsDidChangeNotification,
            // ⚠ 必须传本实例的 defaults 作为 object：系统只往 **defaultCenter** 派发这条通知
            //   （私有 `NSNotificationCenter()` 永远收不到，实测 iOS 27 模拟器确认）。
            //   带上 object 则只收本 suite 的变更，17 个 store 不会互相串台 ——
            //   早先担心的正是这个，但解法是过滤而不是换私有 center。
            `object` = defaults,
            // ⚠ NSUserDefaults 通知在**任意线程**投递，必须指定队列；listener 会写非线程安全的 Compose 状态
            queue = NSOperationQueue.mainQueue,
            usingBlock = { _ -> listener(KEY_CHANGED_ANYWHERE) },
        )

    override fun unregisterListener(token: Any?) {
        if (token != null) NSNotificationCenter.defaultCenter.removeObserver(token)
    }

    /** 按调用方期望的类型读 NSNumber —— 这是单键读取不需要类型标记的原因。 */
    private fun number(key: String): NSNumber? = defaults.objectForKey(key) as? NSNumber

    /** 把 plist 里的原始值还原成 Kotlin 类型，供 [all] 使用。 */
    private fun restore(key: String, raw: Any?): Any? = when {
        raw == null -> null
        // NSString 在 Kotlin/Native 里直接映射为 kotlin.String
        raw is String -> raw
        // 目前唯一的集合类型是 getStringSet 写的字符串数组
        raw is List<*> -> LinkedHashSet(raw.mapNotNull { it as? String })
        raw is NSNumber -> when (typeDomain.stringForKey(key)) {
            TYPE_INT -> raw.intValue
            TYPE_LONG -> raw.longValue
            TYPE_FLOAT -> raw.floatValue
            TYPE_BOOLEAN -> raw.boolValue
            // 无标记（例如从别的途径写进来的值）：按 Long 兜底，至少不丢量级
            else -> raw.longValue
        }
        else -> raw
    }

    private inner class UserDefaultsEditor : KeyValueEditor {
        override fun putString(key: String, value: String) {
            defaults.setObject(value, forKey = key)
            typeDomain.removeObjectForKey(key)
        }

        override fun putInt(key: String, value: Int) {
            defaults.setObject(value, forKey = key)
            typeDomain.setObject(TYPE_INT, forKey = key)
        }

        override fun putLong(key: String, value: Long) {
            defaults.setObject(value, forKey = key)
            typeDomain.setObject(TYPE_LONG, forKey = key)
        }

        override fun putFloat(key: String, value: Float) {
            defaults.setObject(value, forKey = key)
            typeDomain.setObject(TYPE_FLOAT, forKey = key)
        }

        override fun putBoolean(key: String, value: Boolean) {
            defaults.setObject(value, forKey = key)
            typeDomain.setObject(TYPE_BOOLEAN, forKey = key)
        }

        override fun putStringSet(key: String, value: Set<String>) {
            defaults.setObject(value.toList(), forKey = key)
            typeDomain.removeObjectForKey(key)
        }

        override fun remove(key: String) = this@UserDefaultsStore.remove(key)

        override fun clear() = this@UserDefaultsStore.clear()
    }

    private companion object {
        const val TYPE_INT = "i"
        const val TYPE_LONG = "l"
        const val TYPE_FLOAT = "f"
        const val TYPE_BOOLEAN = "b"
    }
}
