package com.haooz.chedule.data

/**
 * 外观参数快照 —— **跨平台**。
 *
 * ## ⚠ 字段名是数据兼容契约
 *
 * 这是 `:androidApp` 的 `ScheduleAppearance.CombinationStyle` 的跨平台副本。
 * 存量用户在 `appearance_settings` 里存的是这个结构的 JSON 快照（Gson 序列化，
 * 键名 = 字段名），**一个字符都不能改** —— 改了等于老用户的外观设置全部读不出来。
 *
 * 新增字段可以加（Gson/kotlinx 都会忽略未知键），但**改名/删除 = 数据丢失**。
 *
 * ## 与 `AppearanceConfig` 的关系
 *
 * `AppearanceConfig`（在 `:core`）是 `CombinationStyle` 去掉壁纸变换后的**业务视图**，
 * 页面（`MainScheduleScreen` 等）消费它。这里存的是完整快照（含 offset/scale/ref），
 * 落盘格式必须保持原样。
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
