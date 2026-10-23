package com.haooz.chedule.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * [KeyValueStore] 的 JVM / 桌面实现：每个偏好名一个独立 JSON 文件（一个名字一个文件，绝不合并）。
 *
 * ## 与 Android SharedPreferences 的对应关系
 *
 * | Android | 本实现 |
 * |---|---|
 * | `getSharedPreferences(name)` 的独立 XML 文件 | `<目录>/<name>.json` 的独立文件 |
 * | `edit { … }.apply()` | [edit] 回调结束后**同步写盘**（桌面文件小，无卡顿） |
 * | 类型存在 XML 标签里（`<int>` / `<long>`） | 类型存在每个值的**标签数组**里（`["i", 42]`） |
 * | `registerListener` 跨实例通知 | **同名实例共享**（[AppStorage] 按名缓存，天然满足） |
 *
 * ⚠ 每个值以 `["<类型标签>", <值>]` 落盘：`s`=String / `i`=Int / `l`=Long / `f`=Float / `b`=Boolean / `ss`=Set\<String\>。
 * JSON 的 `42` 无法区分 Int 与 Long——若还原猜错类型，`CourseRepository` 复制课表会把 Int 写成长整型，Android 侧 `getInt` 抛 `ClassCastException` 被兜底成默认值，导致用户设置静默丢失（与 iOS `UserDefaultsStore` 同一坑）。
 *
 * 解析失败不静默清空：坏文件改名 `<name>.json.corrupt-<时间戳>` 保留现场，再从空库启动并告警（静默丢数据是本项目的头号故障模式）。
 *
 * 线程安全：jvmMain 不受 `checkKmpPurity` 扫描，可用 `synchronized`；[edit] 块整体持锁，listener 在锁外通知（与 InMemoryKeyValueStore 顺序语义一致）。
 */
class FileKeyValueStore(private val file: File) : KeyValueStore {

    private val lock = Any()

    /** key → 带类型标签的值。读写均在 [lock] 内。 */
    private val data = LinkedHashMap<String, Any?>()

    /** 变更监听（回调在写锁外触发，避免 listener 里再读 store 造成死锁）。 */
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    init {
        loadFromDisk()
    }

    // ── 读 ────────────────────────────────────────────────────────────────────

    override fun getString(key: String, defaultValue: String): String =
        synchronized(lock) { data[key] as? String } ?: defaultValue

    override fun getInt(key: String, defaultValue: Int): Int {
        val v = synchronized(lock) { data[key] }
        // 与 InMemoryKeyValueStore 同款窄化语义：Long → Int 安全转换
        return when (v) {
            is Int -> v
            is Long -> v.toInt()
            else -> defaultValue
        }
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        val v = synchronized(lock) { data[key] }
        return when (v) {
            is Long -> v
            is Int -> v.toLong()
            else -> defaultValue
        }
    }

    override fun getFloat(key: String, defaultValue: Float): Float {
        val v = synchronized(lock) { data[key] }
        return (v as? Number)?.toFloat() ?: defaultValue
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        synchronized(lock) { data[key] as? Boolean } ?: defaultValue

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> {
        val stored = synchronized(lock) { data[key] as? Set<String> } ?: return defaultValue
        // 必须返回副本（契约；Android 上就地改会抛 UnsupportedOperationException）
        return LinkedHashSet(stored)
    }

    override fun contains(key: String): Boolean = synchronized(lock) { data.containsKey(key) }

    override fun all(): Map<String, Any?> = synchronized(lock) {
        // 双层拷贝：外层 Map 与内层 Set 都是新对象，改快照不影响存储
        LinkedHashMap(data).mapValues { (_, v) -> if (v is Set<*>) LinkedHashSet(v) else v }
    }

    // ── 写 ────────────────────────────────────────────────────────────────────

    /** 与 InMemoryKeyValueStore 相同：块内操作**按调用序直接生效**（clear 管它之后的清空）。 */
    override fun edit(block: KeyValueEditor.() -> Unit) {
        val affected = LinkedHashSet<String>()
        synchronized(lock) {
            PendingEditor(block, affected).apply()
        }
        persist()
        notifyListeners(affected)
    }

    override fun remove(key: String) {
        val existed = synchronized(lock) {
            // 必须先记 containsKey 再 remove —— remove 之后再查恒为 false
            val had = data.containsKey(key)
            data.remove(key)
            had
        }
        if (!existed) return
        persist()
        notifyListeners(setOf(key))
    }

    override fun clear() {
        val removed = synchronized(lock) {
            if (data.isEmpty()) return
            val keys = data.keys.toSet()
            data.clear()
            keys
        }
        persist()
        notifyListeners(removed)
    }

    override fun registerListener(listener: (String) -> Unit): Any? {
        listeners.add(listener)
        return listener
    }

    override fun unregisterListener(token: Any?) {
        // 函数类型有类型擦除，不能 `is (String) -> Unit` 判型 —— 直接按引用移除即可
        @Suppress("UNCHECKED_CAST")
        (token as? (String) -> Unit)?.let { listeners.remove(it) }
    }

    // ── 磁盘 ──────────────────────────────────────────────────────────────────

    private fun loadFromDisk() {
        if (!file.exists()) return
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrElse { e ->
            System.err.println("[FileKeyValueStore] 读取失败，按空库启动: ${file.name}: $e")
            return
        }
        val parsed = runCatching { parse(text) }.getOrElse { e ->
            // 不静默清空：保留现场文件供排查
            val quarantine = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
            runCatching {
                Files.move(file.toPath(), quarantine.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            System.err.println("[FileKeyValueStore] 解析失败，已隔离为 ${quarantine.name} 并按空库启动: $e")
            return
        }
        synchronized(lock) {
            data.clear()
            data.putAll(parsed)
        }
    }

    private fun persist() {
        val snapshot: Map<String, Any?>
        synchronized(lock) {
            snapshot = LinkedHashMap(data).mapValues { (_, v) -> if (v is Set<*>) LinkedHashSet(v) else v }
        }
        runCatching {
            // 原子替换：先写 .tmp 再 move，进程中途被杀不会留下半截文件
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(serialize(snapshot), Charsets.UTF_8)
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.onFailure { e ->
            // 内存中的值仍然有效，只是本轮没落盘 —— 明确喊出来，不静默
            System.err.println("[FileKeyValueStore] 写盘失败（内存中的值仍然有效）: ${file.name}: $e")
        }
    }

    private fun notifyListeners(keys: Collection<String>) {
        if (listeners.isEmpty() || keys.isEmpty()) return
        for (key in keys) {
            for (l in listeners) {
                runCatching { l(key) }.onFailure { e ->
                    System.err.println("[FileKeyValueStore] listener 异常（已跳过）: key=$key: $e")
                }
            }
        }
    }

    // ── 序列化 ────────────────────────────────────────────────────────────────

    /** 值 → `["<标签>", <JSON 值>]`。 */
    private fun encodeValue(value: Any?): JsonElement = when (value) {
        is String -> tagged("s", JsonPrimitive(value))
        is Int -> tagged("i", JsonPrimitive(value))
        is Long -> tagged("l", JsonPrimitive(value))
        is Float -> tagged("f", JsonPrimitive(value))
        is Boolean -> tagged("b", JsonPrimitive(value))
        is Set<*> -> tagged("ss", JsonArray(value.map { JsonPrimitive(it.toString()) }))
        // 契约里没有可空 put 重载；走到这里只可能是历史脏数据 —— 按字符串抢救，不丢
        null -> tagged("s", JsonPrimitive(""))
        else -> tagged("s", JsonPrimitive(value.toString()))
    }

    private fun tagged(tag: String, element: JsonElement) =
        JsonArray(listOf(JsonPrimitive(tag), element))

    private fun serialize(map: Map<String, Any?>): String {
        val sb = StringBuilder(map.size * 48 + 2)
        sb.append('{')
        var first = true
        for ((k, v) in map) {
            if (!first) sb.append(',')
            first = false
            appendEscaped(sb, k)
            sb.append(':')
            sb.append(encodeValue(v))
        }
        sb.append('}')
        return sb.toString()
    }

    private fun parse(text: String): Map<String, Any?> {
        val root = Json.parseToJsonElement(text)
        val obj = root as? JsonObject ?: error("根元素不是 JSON 对象")
        val out = LinkedHashMap<String, Any?>(obj.size)
        for ((key, element) in obj) {
            val arr = element as? JsonArray ?: continue
            if (arr.size < 2) continue
            val tag = arr[0].jsonPrimitive.contentOrNull ?: continue
            if (!arr[0].jsonPrimitive.isString) continue
            // 按标签还原；null 表示该条目类型损坏 —— 跳过该键而不是整库失败
            decodeValue(tag, arr[1])?.let { out[key] = it }
        }
        return out
    }

    private fun decodeValue(tag: String, element: JsonElement): Any? = when (tag) {
        "s" -> element.jsonPrimitive.takeIf { it.isString }?.contentOrNull
        "i" -> element.jsonPrimitive.intOrNull
        "l" -> element.jsonPrimitive.longOrNull
        "f" -> element.jsonPrimitive.floatOrNull
        "b" -> element.jsonPrimitive.booleanOrNull
        "ss" -> element.jsonArray
            .mapNotNull { it.jsonPrimitive.contentOrNull }
            .toCollection(LinkedHashSet())
        // 未知标签：按字符串抢救，宁可类型宽松也不要丢数据
        else -> element.jsonPrimitive.contentOrNull
    }

    private fun appendEscaped(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else ->
                    if (c < ' ') sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    else sb.append(c)
            }
        }
        sb.append('"')
    }

    /**
     * [edit] 块的执行器：**按调用序直接改 [data]**（与 [InMemoryKeyValueStore] 一致 ——
     * `putString(x); clear()` 后 x 必须不存在，顺序不能重排）。
     */
    private inner class PendingEditor(
        private val block: KeyValueEditor.() -> Unit,
        private val affected: MutableSet<String>,
    ) {
        fun apply() {
            EditorFacade().block()
        }

        private inner class EditorFacade : KeyValueEditor {
            private fun touch(key: String) { affected.add(key) }

            override fun putString(key: String, value: String) { data[key] = value; touch(key) }
            override fun putInt(key: String, value: Int) { data[key] = value; touch(key) }
            override fun putLong(key: String, value: Long) { data[key] = value; touch(key) }
            override fun putFloat(key: String, value: Float) { data[key] = value; touch(key) }
            override fun putBoolean(key: String, value: Boolean) { data[key] = value; touch(key) }
            override fun putStringSet(key: String, value: Set<String>) {
                data[key] = LinkedHashSet(value)
                touch(key)
            }
            override fun remove(key: String) { data.remove(key); touch(key) }
            override fun clear() {
                affected.addAll(data.keys)
                data.clear()
            }
        }
    }
}
