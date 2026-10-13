package com.haooz.chedule.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull

/**
 * [AppearanceStyle] 的 JSON 编解码 —— 与 Gson 输出逐字段对齐。
 *
 * ⚠ 存量数据是 `Gson().toJson(CombinationStyle)` 写的，键名 = 字段名，逐字不能改；
 * 也不能换成 `@Serializable`：枚举字段的缺省行为不同（Gson 的 UnsafeAllocator 让 Kotlin 默认值失效），
 * 会让「老数据里没有的字段」在两端落到不同默认值。枚举按 ordinal 存取、缺键一律用 Kotlin 默认值、
 * 反序列化失败给 null（交给 safe getter 兜默认值）。
 */
object AppearanceStyleCodec {

    /** 全部合法字段名。用于「这份 JSON 是不是外观快照」的校验。 */
    val FIELD_NAMES: Set<String> = setOf(
        "offsetX", "offsetY", "scale", "offsetRefW", "offsetRefH",
        "cardBlur", "cardAlpha", "cardSurfaceAlpha",
        "cardHeight",
        "cardCornerRadius", "wallpaperBrightness", "wallpaperIsLight",
        "showBreakDividers", "cardContentAlignment", "cardTextColor",
        "cardTextScale", "showClassroom", "showTeacher", "cardRefraction",
        "wallpaperBlur"
    )

    fun encode(style: AppearanceStyle): String {
        val obj = JsonObject(
            buildMap {
                put("offsetX", JsonPrimitive(style.offsetX))
                put("offsetY", JsonPrimitive(style.offsetY))
                put("scale", JsonPrimitive(style.scale))
                put("offsetRefW", JsonPrimitive(style.offsetRefW))
                put("offsetRefH", JsonPrimitive(style.offsetRefH))
                put("cardBlur", JsonPrimitive(style.cardBlur))
                put("cardAlpha", JsonPrimitive(style.cardAlpha))
                style.cardSurfaceAlpha?.let { put("cardSurfaceAlpha", JsonPrimitive(it)) }
                put("cardHeight", JsonPrimitive(style.cardHeight))
                put("cardCornerRadius", JsonPrimitive(style.cardCornerRadius))
                put("wallpaperBrightness", JsonPrimitive(style.wallpaperBrightness))
                style.wallpaperIsLight?.let { put("wallpaperIsLight", JsonPrimitive(it)) }
                put("showBreakDividers", JsonPrimitive(style.showBreakDividers))
                style.cardContentAlignment?.let { put("cardContentAlignment", JsonPrimitive(it.ordinal)) }
                style.cardTextColor?.let { put("cardTextColor", JsonPrimitive(it.ordinal)) }
                put("cardTextScale", JsonPrimitive(style.cardTextScale))
                put("showClassroom", JsonPrimitive(style.showClassroom))
                put("showTeacher", JsonPrimitive(style.showTeacher))
                style.cardRefraction?.let { put("cardRefraction", JsonPrimitive(it.ordinal)) }
                put("wallpaperBlur", JsonPrimitive(style.wallpaperBlur))
            }
        )
        return obj.toString()
    }

    /**
     * 解析 JSON 快照；格式非法 / 键名一个都对不上时返回 null。
     *
     * ⚠ 「键名对不上就判不可用」是刻意的：字段名写错曾让 Gson 把值停在 Java 默认值 0，
     * 导致课表页网格整页静默空白（不崩、无日志）。宁可回落默认值也不要静默显示坏状态。
     */
    fun decodeOrNull(json: String): AppearanceStyle? {
        val obj = runCatching { parseJsonObject(json) }.getOrNull() ?: return null
        if (obj.keys.none { it in FIELD_NAMES }) return null
        return runCatching {
            AppearanceStyle(
                offsetX = obj.floatOr("offsetX", 0f),
                offsetY = obj.floatOr("offsetY", 0f),
                scale = obj.floatOr("scale", 1f),
                offsetRefW = obj.floatOr("offsetRefW", 0f),
                offsetRefH = obj.floatOr("offsetRefH", 0f),
                cardBlur = obj.floatOr("cardBlur", 4f),
                cardAlpha = obj.floatOr("cardAlpha", 0.15f),
                cardSurfaceAlpha = obj.floatOrNull("cardSurfaceAlpha"),
                cardHeight = obj.floatOr("cardHeight", 54f),
                cardCornerRadius = obj.floatOr("cardCornerRadius", 10f),
                wallpaperBrightness = obj.floatOr("wallpaperBrightness", 0f),
                wallpaperIsLight = obj["wallpaperIsLight"]?.let { it as? JsonPrimitive }?.booleanOrNull,
                showBreakDividers = obj.boolOr("showBreakDividers", true),
                cardContentAlignment = obj.enumOr("cardContentAlignment") { CardContentAlignment.fromOrdinal(it) },
                cardTextColor = obj.enumOr("cardTextColor") { CardTextColor.fromOrdinal(it) },
                cardTextScale = obj.floatOr("cardTextScale", 1f),
                showClassroom = obj.boolOr("showClassroom", true),
                showTeacher = obj.boolOr("showTeacher", true),
                cardRefraction = obj.enumOr("cardRefraction") { CardRefractionLevel.fromOrdinal(it) },
                wallpaperBlur = obj.boolOr("wallpaperBlur", false),
            )
        }.getOrNull()
    }

    private fun JsonObject.floatOr(key: String, default: Float): Float =
        (this[key] as? JsonPrimitive)?.floatOrNull ?: default

    private fun JsonObject.floatOrNull(key: String): Float? =
        (this[key] as? JsonPrimitive)?.floatOrNull

    private fun JsonObject.boolOr(key: String, default: Boolean): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

    /**
     * 枚举按 ordinal（数字）存取 —— 与 Gson 对本项目这三个枚举的实际行为一致。
     *
     * ⚠ 别改成按 name：落盘格式 = ordinal，枚举顺序不能改。Gson 默认按 name 序列化枚举，
     * 但旧数据的编写路径（手写 TypeAdapter / 手动 putInt）存的是 ordinal，以存量数据为准。
     */
    private fun <T> JsonObject.enumOr(key: String, fromOrdinal: (Int) -> T): T? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        // ordinal 存的是数字；JsonPrimitive.content 是文本，手动转 Int
        //（float 落盘可能带 ".0" 尾巴，先 toDouble 再截断，与旧行为一致）
        val v = primitive.content.toDoubleOrNull()?.toInt() ?: return null
        return runCatching { fromOrdinal(v) }.getOrNull()
    }
}
