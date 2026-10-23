package com.haooz.chedule.data

// `ThemeMode` 独立成文件：只含纯数据，供 `:ui-shared` 页面引用
// （`ScheduleAppearance` 的读写函数要碰 SharedPreferences，仍留在宿主）。

/**
 * 课表外观自定义页「默认主题」下拉的取值。
 *
 * ⚠️ 与全局应用主题（`app_theme_prefs` 的 `theme_mode`，值域 system/light/dark）
 * **刻意隔离**：这里只决定今日页/课程表页跟随谁，全局主题开关不受影响。
 * 故 [FOLLOW_APP] 的 prefsValue 复用 "system" 字面量但落在不同的键上。
 */
enum class ThemeMode(val label: String, val prefsValue: String) {
    FOLLOW_WALLPAPER("跟随壁纸", "follow_wallpaper"),
    FOLLOW_APP("跟随应用", "system"),
    LIGHT("浅色模式", "light"),
    DARK("深色模式", "dark");

    companion object {
        /** 课表外观"默认主题"下拉专用的偏好 key，仅决定今日页/课程表页主题，与全局主题开关隔离 */
        const val SCHEDULE_THEME_MODE_KEY = "schedule_theme_mode"

        fun fromPrefsValue(value: String?): ThemeMode =
            entries.find { it.prefsValue == value } ?: FOLLOW_WALLPAPER
    }
}
