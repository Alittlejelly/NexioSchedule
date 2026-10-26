package com.haooz.chedule.data

/**
 * 课表外观配置：全部可调参数收敛为不可变值对象
 */
data class AppearanceConfig(
    val cardBlurRadius: Float = 4f,
    /** 课程色着色程度（有无壁纸都可调） */
    val cardAlpha: Float = 0.15f,
    /** 有壁纸时卡片白/黑底不透明度 */
    val cardSurfaceAlpha: Float = 0.15f,
    val cardHeight: Float = 54f,
    val cardCornerRadius: Float = 10f,
    val wallpaperBrightness: Float = 0f,
    val showBreakDividers: Boolean = true,
    val cardContentAlignment: CardContentAlignment = CardContentAlignment.CENTER_CENTER,
    val cardTextColor: CardTextColor = CardTextColor.COLORFUL,
    /** 卡片文字缩放比例：作用于课程名称、教室、教师，0.5~2.0 */
    val cardTextScale: Float = 1f,
    val showClassroom: Boolean = true,
    val showTeacher: Boolean = true,
    val cardRefraction: CardRefractionLevel = CardRefractionLevel.DEFAULT,
    val wallpaperBlur: Boolean = false
) {
    companion object
}

/**
 * [AppearanceStyle]（落盘快照）→ 外观配置。
 *
 * 用于「搭配还没加载完」的首帧兜底：与 `AppearanceConfig.fromCombination` 字段一一对应，
 * 保证兜底值与加载完成后派生的值相同，不会闪一帧默认值。
 */
fun AppearanceConfig.Companion.fromStyle(style: AppearanceStyle): AppearanceConfig = AppearanceConfig(
    cardBlurRadius = style.cardBlur,
    cardAlpha = style.cardAlpha.coerceIn(0f, 1f),
    cardSurfaceAlpha = style.safeCardSurfaceAlpha,
    cardHeight = style.safeCardHeight,
    cardCornerRadius = style.cardCornerRadius,
    wallpaperBrightness = style.wallpaperBrightness,
    showBreakDividers = style.showBreakDividers,
    cardContentAlignment = style.safeAlignment,
    cardTextColor = style.safeTextColor,
    cardTextScale = style.safeCardTextScale,
    showClassroom = style.showClassroom,
    showTeacher = style.showTeacher,
    cardRefraction = style.safeRefraction,
    wallpaperBlur = style.wallpaperBlur,
)
