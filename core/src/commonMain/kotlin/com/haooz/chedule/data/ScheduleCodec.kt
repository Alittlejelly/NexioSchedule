package com.haooz.chedule.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

// 课表数据模型 ↔ JSON 的显式字段清单编解码。
//
//  用手写清单而不是 @Serializable，因为 kotlinx-serialization 的编译器插件在本机不可用。
//
//  兼容性判据是「字段集合 + 字段名 + 值的语义」，不是逐字相等：JSON 对象按规范无序，
//  两端都按名字取值。真实备份里字段呈字母序，那是 release 构建被 R8 重排字段的产物，
//  随构建而变，不要拿它当契约去对齐。
//
//  与 Gson 的对齐点：null 字段省略、默认值照写、Long 原样输出、`<>&='` 不转义。
//  缺字段导致的 null 由调用方的 sanitize* 兜底。

/**
 * 落盘/备份用的 JSON 实例。
 *
 * ⚠ **不要**拿 `JsonSupport` 里那个内部实例来序列化模型 —— 那个是给
 * 「动态 JsonElement 解析」用的，语义取向不同（比如它对 null 会写 `JsonNull`）。
 */
internal val scheduleJson: Json = Json {
    // Gson 会写默认值（真实备份里 `isCustomTime:false` / `selectedWeeks:[]` 都在）
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * 把「值可能是 null」的 Map 转成 JSON，**递归地跳过对象里的 null 值**。
 *
 * 对应 Gson 的 `serializeNulls = false`：真实备份里 `Course.customStartTime` /
 * `customEndTime`（非自定义时间时为 null）根本没出现，`SpecialBlock.items` 同理。
 *
 * ⚠ **必须递归**：`specialBlocks` / `routines` 是嵌套对象，只跳顶层的话内层的
 * `"items":null` 照样会被写出去。
 *
 * ⚠ 数组元素里的 null **要保留**：`serializeNulls` 只影响对象字段，集合元素照写 null。
 */
private fun plainValueToJsonWithoutNulls(value: Any?): JsonElement? = when (value) {
    null -> null
    is Map<*, *> -> kotlinx.serialization.json.JsonObject(
        value.entries.mapNotNull { (key, item) ->
            plainValueToJsonWithoutNulls(item)?.let { key.toString() to it }
        }.toMap(LinkedHashMap())
    )
    is Iterable<*> -> kotlinx.serialization.json.JsonArray(
        value.map { plainValueToJsonWithoutNulls(it) ?: JsonNull }
    )
    else -> toJsonElement(value)
}

/** 见 [plainValueToJsonWithoutNulls]。顶层必须是对象（模型序列化都是对象）。 */
fun toJsonElementWithoutNulls(map: Map<String, Any?>): JsonElement =
    plainValueToJsonWithoutNulls(map) ?: JsonNull

// ── Map 读取小工具（输入形状 = Gson 的 Map<String,Any>：数字是 Double）──

private fun Map<*, *>.raw(key: String): Any? = this[key]

private fun Map<*, *>.str(key: String): String? = raw(key) as? String

private fun Map<*, *>.int(key: String, fallback: Int = 0): Int =
    (raw(key) as? Number)?.toInt() ?: fallback

private fun Map<*, *>.long(key: String, fallback: Long = 0L): Long =
    (raw(key) as? Number)?.toLong() ?: fallback

private fun Map<*, *>.bool(key: String, fallback: Boolean = false): Boolean =
    raw(key) as? Boolean ?: fallback

// ════════════════════════════════════════════════════════════════════════
//  Course
// ════════════════════════════════════════════════════════════════════════

/**
 * `Course` → JSON Map（显式字段清单，17 个字段）。
 *
 * 字段顺序 = 声明顺序（见文件头：顺序不是契约）。
 * `customStartTime` / `customEndTime` 为 null 时会被 [toJsonElementWithoutNulls] 跳过。
 */
fun Course.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "classroom" to classroom,
    "teacher" to teacher,
    "dayOfWeek" to dayOfWeek,
    "startSection" to startSection,
    "endSection" to endSection,
    "startWeek" to startWeek,
    "endWeek" to endWeek,
    "weekType" to weekType,
    "colorRes" to colorRes,
    "selectedWeeks" to selectedWeeks,
    "scheduleId" to scheduleId,
    "lastModified" to lastModified,
    "isCustomTime" to isCustomTime,
    "customStartTime" to customStartTime,
    "customEndTime" to customEndTime,
)

/**
 * JSON Map → `Course`。
 *
 * ⚠ **缺字段时取零值**（0 / "" / emptyList / null），刻意**不用 Kotlin 的构造默认值** ——
 * 这才与 Gson 一致：Gson 走 `UnsafeAllocator` 绕开构造器，缺字段留下 JVM 零值
 * （非空 `String` 字段因此会变成 null）。调用方 `sanitizeCourses` 原本就是为兜这件事写的
 * （`course.name ?: ""` 等），所以**最终结果与 Gson 路径完全一致**。
 */
fun courseFromJsonMap(map: Map<*, *>): Course = Course(
    id = map.str("id") ?: "",
    name = map.str("name") ?: "",
    classroom = map.str("classroom") ?: "",
    teacher = map.str("teacher") ?: "",
    dayOfWeek = map.int("dayOfWeek"),
    startSection = map.int("startSection"),
    endSection = map.int("endSection"),
    startWeek = map.int("startWeek"),
    endWeek = map.int("endWeek"),
    weekType = map.int("weekType"),
    colorRes = map.long("colorRes"),
    selectedWeeks = (map.raw("selectedWeeks") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
        ?: emptyList(),
    scheduleId = map.str("scheduleId") ?: "",
    lastModified = map.long("lastModified"),
    isCustomTime = map.bool("isCustomTime"),
    customStartTime = map.str("customStartTime"),
    customEndTime = map.str("customEndTime"),
)

/** `List<Course>` → JSON 数组串。等价 `gson.toJson(courses)`。 */
fun encodeCourses(courses: List<Course>): String =
    scheduleJson.encodeToString(
        JsonElement.serializer(),
        kotlinx.serialization.json.JsonArray(courses.map { toJsonElementWithoutNulls(it.toJsonMap()) }),
    )

/**
 * JSON 数组串 → `List<Course>`。
 *
 * 语义对齐 `gson.fromJson<List<Course>>(json, …)`：
 * - 空数组 → 空列表
 * - 字面量 `null` → 空列表（Gson 返回 null，调用点写的是 `?: emptyList()`）
 * - **格式非法 → 抛异常**（Gson 抛 JsonSyntaxException），调用点的 `try/catch` 负责兜底
 * - 顶层不是数组 → 抛异常
 */
fun decodeCourses(json: String): List<Course> {
    val trimmed = json.trim()
    if (trimmed.isEmpty() || trimmed == "null") return emptyList()
    val element = parseJsonElement(trimmed)
    val array = element as? kotlinx.serialization.json.JsonArray
        ?: throw IllegalArgumentException("Not a course array")
    return array.mapNotNull { item ->
        (jsonToPlainValue(item) as? Map<*, *>)?.let(::courseFromJsonMap)
    }
}

// ════════════════════════════════════════════════════════════════════════
//  ScheduleFolder
// ════════════════════════════════════════════════════════════════════════

/** `ScheduleFolder` → JSON Map（3 个字段）。 */
fun ScheduleFolder.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "schedules" to schedules,
)

/** JSON Map → `ScheduleFolder`（缺字段取零值，与 Gson 一致）。 */
fun scheduleFolderFromJsonMap(map: Map<*, *>): ScheduleFolder = ScheduleFolder(
    id = map.str("id") ?: "",
    name = map.str("name") ?: "",
    schedules = (map.raw("schedules") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
)

/** `List<ScheduleFolder>` → JSON 数组串。等价 `gson.toJson(folders)`。 */
fun encodeScheduleFolders(folders: List<ScheduleFolder>): String =
    scheduleJson.encodeToString(
        JsonElement.serializer(),
        kotlinx.serialization.json.JsonArray(folders.map { toJsonElementWithoutNulls(it.toJsonMap()) }),
    )

/** JSON 数组串 → `List<ScheduleFolder>`。语义同 [decodeCourses]。 */
fun decodeScheduleFolders(json: String): List<ScheduleFolder> {
    val trimmed = json.trim()
    if (trimmed.isEmpty() || trimmed == "null") return emptyList()
    val array = parseJsonElement(trimmed) as? kotlinx.serialization.json.JsonArray
        ?: throw IllegalArgumentException("Not a folder array")
    return array.mapNotNull { item ->
        (jsonToPlainValue(item) as? Map<*, *>)?.let(::scheduleFolderFromJsonMap)
    }
}

// TimeConfig（含 TimeRoutine / SpecialBlock / SpecialItem）
//
//  Gson 缺字段时给什么，判据是「有没有必填参数」：
//  - Course 有必填参数 → Gson 走 UnsafeAllocator 绕开构造器，缺字段得到零值/null，
//    由 CourseRepository 的 sanitize 兜底。
//  - TimeConfig 一族全部有默认值 → Gson 走合成无参构造器，缺字段得到 Kotlin 默认值。
//
//  所以 [timeConfigFromJsonMap] 用 `TimeConfig()` 作底 + `copy` 只覆盖出现过的字段，
//  嵌套的 TimeRoutine / SpecialBlock / SpecialItem 直接复用各自的 `fromRaw(Map)`
//  （那里的 fallback 字面量恰好等于各自的 Kotlin 默认值）。

private fun Map<*, *>.stringMap(key: String): Map<String, String> {
    val raw = raw(key) as? Map<*, *> ?: return emptyMap()
    val out = LinkedHashMap<String, String>()
    for ((k, v) in raw) {
        val name = k?.toString() ?: continue
        val value = v?.toString() ?: continue
        out[name] = value
    }
    return out
}

/** `SpecialItem` → JSON Map（4 个字段）。 */
fun SpecialItem.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "startDay" to startDay,
    "endDay" to endDay,
)

/**
 * `SpecialBlock` → JSON Map（5 个字段）。
 *
 * `items` 可空：**为 null 时会被 [toJsonElementWithoutNulls] 跳过**，
 * 与 Gson 的 `serializeNulls=false` 一致（旧数据里 `items` 缺失就是这个状态）。
 */
fun SpecialBlock.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "startTime" to startTime,
    "endTime" to endTime,
    "items" to items?.map { it.toJsonMap() },
)

/** `TimeRoutine` → JSON Map（24 个字段；`specialBlocks` 为 null 时跳过）。 */
fun TimeRoutine.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "effectiveMonth" to effectiveMonth,
    "effectiveDay" to effectiveDay,
    "quickTimeEnabled" to quickTimeEnabled,
    "classDuration" to classDuration,
    "shortBreak" to shortBreak,
    "longBreakEnabled" to longBreakEnabled,
    "longBreakMorning" to longBreakMorning,
    "longBreakAfternoon" to longBreakAfternoon,
    "longBreakEvening" to longBreakEvening,
    "longBreakMorningSection" to longBreakMorningSection,
    "longBreakAfternoonSection" to longBreakAfternoonSection,
    "longBreakEveningSection" to longBreakEveningSection,
    "morningStartHour" to morningStartHour,
    "morningStartMinute" to morningStartMinute,
    "afternoonStartHour" to afternoonStartHour,
    "afternoonStartMinute" to afternoonStartMinute,
    "eveningStartHour" to eveningStartHour,
    "eveningStartMinute" to eveningStartMinute,
    "sectionTimes" to sectionTimes,
    "sectionNames" to sectionNames,
    "specialBlocks" to specialBlocks?.map { it.toJsonMap() },
)

/** `TimeConfig` → JSON Map（25 个字段）。 */
fun TimeConfig.toJsonMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "morningSections" to morningSections,
    "afternoonSections" to afternoonSections,
    "eveningSections" to eveningSections,
    "quickTimeEnabled" to quickTimeEnabled,
    "classDuration" to classDuration,
    "shortBreak" to shortBreak,
    "longBreakEnabled" to longBreakEnabled,
    "longBreakMorning" to longBreakMorning,
    "longBreakAfternoon" to longBreakAfternoon,
    "longBreakEvening" to longBreakEvening,
    "longBreakMorningSection" to longBreakMorningSection,
    "longBreakAfternoonSection" to longBreakAfternoonSection,
    "longBreakEveningSection" to longBreakEveningSection,
    "morningStartHour" to morningStartHour,
    "morningStartMinute" to morningStartMinute,
    "afternoonStartHour" to afternoonStartHour,
    "afternoonStartMinute" to afternoonStartMinute,
    "eveningStartHour" to eveningStartHour,
    "eveningStartMinute" to eveningStartMinute,
    "sectionTimes" to sectionTimes,
    "sectionNames" to sectionNames,
    "specialBlocks" to specialBlocks.map { it.toJsonMap() },
    "routines" to routines.map { it.toJsonMap() },
)

/**
 * JSON Map → `TimeConfig`。结果**必须再过一遍 [TimeConfig.sanitize]**，
 * 这里只负责「把 JSON 变成 Gson 会给的那个对象」，不做业务归一。
 */
fun timeConfigFromJsonMap(map: Map<*, *>): TimeConfig {
    // 以 TimeConfig() 作底，再只覆盖 JSON 里出现过的字段
    val base = TimeConfig()
    return base.copy(
        id = map.long("id", base.id),
        // 非空类型 + JSON 里显式 null → Gson 会塞 null；这里直接给底值，sanitize 后结果相同
        name = map.str("name") ?: base.name,
        morningSections = map.int("morningSections", base.morningSections),
        afternoonSections = map.int("afternoonSections", base.afternoonSections),
        eveningSections = map.int("eveningSections", base.eveningSections),
        quickTimeEnabled = map.bool("quickTimeEnabled", base.quickTimeEnabled),
        classDuration = map.int("classDuration", base.classDuration),
        shortBreak = map.int("shortBreak", base.shortBreak),
        longBreakEnabled = map.bool("longBreakEnabled", base.longBreakEnabled),
        longBreakMorning = map.int("longBreakMorning", base.longBreakMorning),
        longBreakAfternoon = map.int("longBreakAfternoon", base.longBreakAfternoon),
        longBreakEvening = map.int("longBreakEvening", base.longBreakEvening),
        longBreakMorningSection = map.int("longBreakMorningSection", base.longBreakMorningSection),
        longBreakAfternoonSection = map.int("longBreakAfternoonSection", base.longBreakAfternoonSection),
        longBreakEveningSection = map.int("longBreakEveningSection", base.longBreakEveningSection),
        morningStartHour = map.int("morningStartHour", base.morningStartHour),
        morningStartMinute = map.int("morningStartMinute", base.morningStartMinute),
        afternoonStartHour = map.int("afternoonStartHour", base.afternoonStartHour),
        afternoonStartMinute = map.int("afternoonStartMinute", base.afternoonStartMinute),
        eveningStartHour = map.int("eveningStartHour", base.eveningStartHour),
        eveningStartMinute = map.int("eveningStartMinute", base.eveningStartMinute),
        sectionTimes = map.stringMap("sectionTimes"),
        sectionNames = map.stringMap("sectionNames"),
        // 复用现有 fromRaw：与 safeSpecialBlocks / safeRoutines 同源，避免两套归一逻辑打架。
        // 它们的 fallback 字面量恰好等于各自的 Kotlin 默认值（已逐个核对）。
        specialBlocks = (map.raw("specialBlocks") as? List<*>)?.mapNotNull { SpecialBlock.fromRaw(it) }
            ?: emptyList(),
        routines = (map.raw("routines") as? List<*>)?.mapNotNull { TimeRoutine.fromRaw(it) }
            ?: emptyList(),
    )
}

/** `TimeConfig` → JSON 串。等价 `gson.toJson(config)`。 */
fun encodeTimeConfig(config: TimeConfig): String =
    scheduleJson.encodeToString(JsonElement.serializer(), toJsonElementWithoutNulls(config.toJsonMap()))

/**
 * JSON 串 → `TimeConfig`。
 *
 * 语义对齐 `gson.fromJson<TimeConfig>(json)`：顶层不是对象或格式非法时**抛异常**
 * （Gson 抛 JsonSyntaxException），调用点用 `runCatching` 兜。
 * ⚠ 调用方拿到之后**必须**再过 [TimeConfig.sanitize]。
 */
fun decodeTimeConfig(json: String): TimeConfig {
    val element = parseJsonElement(json)
    val map = jsonToPlainValue(element) as? Map<*, *>
        ?: throw IllegalArgumentException("Not a time config object")
    return timeConfigFromJsonMap(map)
}

/**
 * `TimeConfig` 的已知字段名。JSON 里**一个都不像**时判为损坏。
 *
 * 原先是 `TimeConfigSnapshotParser.kt`（`:app`）里的私有常量 —— 那个文件因为依赖 Gson
 * 被留在 Android 侧，注释里写明「等阶段 2 换成 kotlinx.serialization 之后应当移回」。
 * 现在就是那个时候：逻辑逐字搬过来，原文件删除。
 */
private val TIME_CONFIG_FIELD_NAMES = setOf(
    "id", "name", "morningSections", "afternoonSections", "eveningSections",
    "quickTimeEnabled", "classDuration", "shortBreak",
    "sectionTimes", "sectionNames", "specialBlocks", "routines",
)

/**
 * `TimeConfig` 快照解析：JSON 键名一个已知字段都不像时判为损坏，返回 null。
 *
 * 与迁移前 `parseTimeConfigSnapshotOrNull(gson, json)` **逻辑完全一致**：
 * 先解析成对象做「像不像 TimeConfig」的判定，再整体解析。
 * 两步都吞异常返回 null（调用方按「损坏」处理）。
 */
fun parseTimeConfigSnapshotOrNull(json: String): TimeConfig? {
    val obj = runCatching { parseJsonObject(json) }.getOrNull() ?: return null
    if (obj.keys.none { it in TIME_CONFIG_FIELD_NAMES }) return null
    return runCatching { decodeTimeConfig(json) }.getOrNull()
}

// ════════════════════════════════════════════════════════════════════════
//  简单标量列表（课表名 / time_config id）
// ════════════════════════════════════════════════════════════════════════

/** `List<String>` → JSON 数组串。等价 `gson.toJson(names)`。 */
fun encodeStringList(values: List<String>): String =
    scheduleJson.encodeToString(JsonElement.serializer(), toJsonElement(values))

/** JSON 数组串 → `List<String>`；非数组或格式非法时抛异常（Gson 同样抛）。 */
fun decodeStringList(json: String): List<String> {
    val array = parseJsonElement(json) as? kotlinx.serialization.json.JsonArray
        ?: throw IllegalArgumentException("Not a string array")
    return array.mapNotNull { (jsonToPlainValue(it) as? String) }
}

/** `List<Long>` → JSON 数组串。等价 `gson.toJson(ids)`。 */
fun encodeLongList(values: List<Long>): String =
    scheduleJson.encodeToString(JsonElement.serializer(), toJsonElement(values))

/** JSON 数组串 → `List<Long>`。 */
fun decodeLongList(json: String): List<Long> {
    val array = parseJsonElement(json) as? kotlinx.serialization.json.JsonArray
        ?: throw IllegalArgumentException("Not a long array")
    return array.mapNotNull { (jsonToPlainValue(it) as? Number)?.toLong() }
}
