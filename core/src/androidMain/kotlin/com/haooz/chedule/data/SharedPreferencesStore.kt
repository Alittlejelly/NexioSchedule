package com.haooz.chedule.data

import android.content.SharedPreferences

/**
 * Android 实现：**原样包装 SharedPreferences**，不换存储引擎。
 *
 * ⚠ **红线**：绝不能改成 DataStore / 自建文件 / Room —— 存量用户的课程表、时间配置、
 * 节假日设置、备份配置全部存在 SharedPreferences 里，换引擎等于全部读不出来。
 *
 * 读取类型不符时原样抛 `ClassCastException`、不吞异常（调用点普遍用 `runCatching` 兜底）；
 * [getStringSet] 返回**副本**（SharedPreferences 返回内部实例引用，就地修改会抛
 * UnsupportedOperationException）。
 */
class SharedPreferencesStore(
    private val prefs: SharedPreferences,
) : KeyValueStore {

    override fun getString(key: String, defaultValue: String): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    override fun getInt(key: String, defaultValue: Int): Int =
        prefs.getInt(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Long =
        prefs.getLong(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Float =
        prefs.getFloat(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
        prefs.getStringSet(key, defaultValue)?.let { LinkedHashSet(it) } ?: defaultValue

    override fun contains(key: String): Boolean = prefs.contains(key)

    override fun all(): Map<String, Any?> = LinkedHashMap(prefs.all)

    override fun edit(block: KeyValueEditor.() -> Unit) {
        val editor = prefs.edit()
        EditorAdapter(editor).block()
        editor.apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * ⚠ **必须自己留强引用**：`SharedPreferencesImpl` 内部是
     * `WeakHashMap<OnSharedPreferenceChangeListener, Object> mListeners`（AOSP
     * `android/app/SharedPreferencesImpl.java`），只持**弱引用**。这里若不留一份，
     * 下面那个 lambda 会被 GC 回收，回调从此**静默失效** —— 现象是「主题、开关怎么点都没反应，
     * 重启应用又好了」，且全程无异常可查。JVM([FileKeyValueStore]) / iOS([UserDefaultsStore])
     * 两个实现都由容器或系统强持有，只有走系统 SharedPreferences 的这条要自己兜住。
     *
     * token 也不能用平台返回值：`registerOnSharedPreferenceChangeListener` 返回 void，
     * 表达式会退化成 `Unit`，`unregisterListener` 永远匹配不上（既漏卸载、又等于没有强引用）。
     * 这里直接返回监听器自身，与 [FileKeyValueStore.registerListener] 语义一致。
     */
    private val strongListeners =
        mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun registerListener(listener: (String) -> Unit): Any? {
        val wrapped = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // 平台回调的 key 声明为 String?（契约是 String）；空值不会出现，跳过即可
            if (key != null) listener(key)
        }
        synchronized(strongListeners) { strongListeners.add(wrapped) }
        prefs.registerOnSharedPreferenceChangeListener(wrapped)
        return wrapped
    }

    override fun unregisterListener(token: Any?) {
        val wrapped = token as? SharedPreferences.OnSharedPreferenceChangeListener ?: return
        synchronized(strongListeners) { strongListeners.remove(wrapped) }
        prefs.unregisterOnSharedPreferenceChangeListener(wrapped)
    }

    private class EditorAdapter(
        private val editor: SharedPreferences.Editor,
    ) : KeyValueEditor {
        override fun putString(key: String, value: String) { editor.putString(key, value) }
        override fun putInt(key: String, value: Int) { editor.putInt(key, value) }
        override fun putLong(key: String, value: Long) { editor.putLong(key, value) }
        override fun putFloat(key: String, value: Float) { editor.putFloat(key, value) }
        override fun putBoolean(key: String, value: Boolean) { editor.putBoolean(key, value) }
        override fun putStringSet(key: String, value: Set<String>) {
            editor.putStringSet(key, LinkedHashSet(value))
        }
        override fun remove(key: String) { editor.remove(key) }
        override fun clear() { editor.clear() }
    }
}

/** 便捷构造：把既有的 `getSharedPreferences(name, MODE_PRIVATE)` 包一层。 */
fun SharedPreferences.asKeyValueStore(): KeyValueStore = SharedPreferencesStore(this)
