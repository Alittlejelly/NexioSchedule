package com.haooz.chedule.data

/**
 * 卡片外观的三个纯枚举：CourseCard（`:ui-shared`）用它们做布局/配色分流，零依赖。
 *
 * ⚠ 落盘格式 = ordinal（fromOrdinal 是旧数据的恢复入口），**枚举顺序不能改**。
 * [label] 是纯展示名（随应用语言，见 [DisplayLang]），不参与落盘与比较。
 */

/** 卡片内容对齐方式 */
enum class CardContentAlignment {
    TOP_START,
    TOP_CENTER,
    CENTER_START,
    CENTER_CENTER;

    val label: String
        get() = when (this) {
            TOP_START -> if (DisplayLang.isZh) "顶部居左" else "Top left"
            TOP_CENTER -> if (DisplayLang.isZh) "顶部居中" else "Top center"
            CENTER_START -> if (DisplayLang.isZh) "中间居左" else "Middle left"
            CENTER_CENTER -> if (DisplayLang.isZh) "中间居中" else "Middle center"
        }

    companion object {
        fun fromOrdinal(ordinal: Int): CardContentAlignment =
            entries.getOrElse(ordinal) { CENTER_CENTER }
    }
}

/** 卡片文字配色 */
enum class CardTextColor {
    COLORFUL,
    SOLID;

    val label: String
        get() = when (this) {
            COLORFUL -> if (DisplayLang.isZh) "彩色" else "Colorful"
            SOLID -> if (DisplayLang.isZh) "纯色" else "Solid"
        }

    companion object {
        fun fromOrdinal(ordinal: Int): CardTextColor =
            entries.getOrElse(ordinal) { COLORFUL }
    }
}

/** 卡片玻璃对壁纸的透镜折射强度（需壁纸才生效） */
enum class CardRefractionLevel(val lensRadiusDp: Float, val lensStrengthDp: Float) {
    OFF(0f, 0f),
    WEAK(4f, 16f),
    DEFAULT(5f, 25f),
    STRONG(6f, 36f);

    val label: String
        get() = when (this) {
            OFF -> if (DisplayLang.isZh) "关闭" else "Off"
            WEAK -> if (DisplayLang.isZh) "较弱" else "Weak"
            DEFAULT -> if (DisplayLang.isZh) "默认" else "Default"
            STRONG -> if (DisplayLang.isZh) "较强" else "Strong"
        }

    companion object {
        fun fromOrdinal(ordinal: Int): CardRefractionLevel =
            entries.getOrElse(ordinal) { DEFAULT }
    }
}
