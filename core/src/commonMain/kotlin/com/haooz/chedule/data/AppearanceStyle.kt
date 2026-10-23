package com.haooz.chedule.data

/**
 * 外观参数快照（跨平台）
 * 存量用户 JSON 快照键名=字段名，一个字符都不能改（改名/删除=老用户外观全部读不出来）；
 * 可新增字段（解析器忽略未知键），落盘格式须保持原样。
 */
data class AppearanceStyle(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    /** offset/scale 保存时的屏幕宽高（px）；0=未记录，加载时不重映射 */
    val offsetRefW: Float = 0f,
    val offsetRefH: Float = 0f,
    /** 课程卡片模糊半径（dp） */
    val cardBlur: Float = 4f,
    val cardAlpha: Float = 0.15f,
    /** 有壁纸时白/黑底不透明度；null=未设置，用默认 */
    val cardSurfaceAlpha: Float? = null,
    val cardHeight: Float = 54f,
    val cardCornerRadius: Float = 10f,
    val wallpaperBrightness: Float = 0f,
    /** null=无壁纸/未测光 */
    val wallpaperIsLight: Boolean? = null,
    val showBreakDividers: Boolean = true,
    val cardContentAlignment: CardContentAlignment? = null,
    val cardTextColor: CardTextColor? = null,
    val cardTextScale: Float = 1f,
    val showClassroom: Boolean = true,
    val showTeacher: Boolean = true,
    val cardRefraction: CardRefractionLevel? = null,
    val wallpaperBlur: Boolean = false
) {
    val safeAlignment: CardContentAlignment get() = cardContentAlignment ?: CardContentAlignment.CENTER_CENTER
    val safeTextColor: CardTextColor get() = cardTextColor ?: CardTextColor.COLORFUL
    val safeRefraction: CardRefractionLevel get() = cardRefraction ?: CardRefractionLevel.DEFAULT

    /** 0 一律视为损坏恢复默认（滑杆合法区间 34~92，0 会让网格整页静默空白） */
    val safeCardTextScale: Float get() = if (cardTextScale > 0f) cardTextScale else 1f

    val safeCardSurfaceAlpha: Float
        get() = cardSurfaceAlpha?.takeIf { it in 0f..1f }
            ?: CombinationDefaults.CARD_SURFACE_ALPHA_DEFAULT

    val safeCardHeight: Float
        get() = if (cardHeight > 0f) cardHeight else CombinationDefaults.CARD_HEIGHT_DEFAULT

    companion object {
        /** 默认值集中一处，三处（本类 / 落地 JSON / UI 滑杆）必须一致 */
        fun defaults(): AppearanceStyle = AppearanceStyle()
    }
}

/** 外观默认值 —— 与滑杆默认值、JSON 缺省值保持一致。 */
object CombinationDefaults {
    const val CARD_HEIGHT_DEFAULT = 54f
    const val CARD_SURFACE_ALPHA_DEFAULT = 0.15f
}
