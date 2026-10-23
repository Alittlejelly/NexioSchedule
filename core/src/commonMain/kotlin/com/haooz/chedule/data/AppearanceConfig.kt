package com.haooz.chedule.data

/**
 * 课表外观配置：全部可调参数收敛为不可变值对象，取代原先 N 字段 × 4 状态的写法以避免手工同步遗漏。
 * ⚠️ 从 `:androidApp` 拆到 `:core`（页面参数需跨平台可见），纯数据拆分零风险；
 * 原 android 版 `fromCombination` 已删除，现役在 `:ui-shared` 的 `CourseScheduleAppTypes.kt`。
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
