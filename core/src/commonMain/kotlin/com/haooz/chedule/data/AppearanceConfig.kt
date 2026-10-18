package com.haooz.chedule.data

/**
 * 课表外观配置：全部可调参数收敛为不可变值对象。
 *
 * 取代原先分散的 N 个字段 × 4 份状态（cached/live/saved/original）的写法，
 * 避免手工同步遗漏导致的 bug。
 *
 * ⚠️ 2026-10-11 从 `:androidApp` 的 `data/ScheduleAppearance.kt` 拆到 `:core` ——
 * `MainScheduleScreen` 迁入 `:ui-shared` 后作为页面参数类型需要跨平台可见；
 * 本类是纯数据（Float/Boolean/三个 :core 枚举），拆分零风险。
 * `fromCombination` 曾以 **companion 扩展**留在 `ScheduleAppearance.kt`（依赖含 Bitmap 的
 * android 版 `Combination`），已随迁移完成删除 —— 现役实现在 `:ui-shared`
 * 的 `CourseScheduleAppTypes.kt`，参数是跨平台的 `ScheduleCombination`。
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
