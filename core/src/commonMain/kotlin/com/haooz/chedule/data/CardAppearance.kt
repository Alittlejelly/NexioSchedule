package com.haooz.chedule.data

/**
 * 卡片外观的三个纯枚举：CourseCard（`:ui-shared`）用它们做布局/配色分流，零依赖。
 *
 * ⚠ 落盘格式 = ordinal（fromOrdinal 是旧数据的恢复入口），**枚举顺序不能改**。
 */

/** 卡片内容对齐方式 */
enum class CardContentAlignment(val label: String) {
    TOP_START("顶部居左"),
    TOP_CENTER("顶部居中"),
    CENTER_START("中间居左"),
    CENTER_CENTER("中间居中");

    companion object {
        fun fromOrdinal(ordinal: Int): CardContentAlignment =
            entries.getOrElse(ordinal) { CENTER_CENTER }
    }
}

/** 卡片文字配色 */
enum class CardTextColor(val label: String) {
    COLORFUL("彩色"),
    SOLID("纯色");

    companion object {
        fun fromOrdinal(ordinal: Int): CardTextColor =
            entries.getOrElse(ordinal) { COLORFUL }
    }
}

/** 卡片玻璃对壁纸的透镜折射强度（需壁纸才生效） */
enum class CardRefractionLevel(val label: String, val lensRadiusDp: Float, val lensStrengthDp: Float) {
    OFF("关闭", 0f, 0f),
    WEAK("较弱", 4f, 16f),
    DEFAULT("默认", 5f, 25f),
    STRONG("较强", 6f, 36f);

    companion object {
        fun fromOrdinal(ordinal: Int): CardRefractionLevel =
            entries.getOrElse(ordinal) { DEFAULT }
    }
}

