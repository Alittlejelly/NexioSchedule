package com.haooz.chedule.data

// `ThemeMode` 独立成文件：只含纯数据，供 `:ui-shared` 页面引用
// （`ScheduleAppearance` 的读写函数要碰 SharedPreferences，仍留在宿主）。

/**
 * 课表外观自定义页「默认主题」下拉的取值。
 *
 * ⚠️ 与全局应用主题（`app_theme_prefs` 的 `theme_mode`，值域 system/light/dark）
 * **刻意隔离**：这里只决定今日页/课程表页跟随谁，全局主题开关不受影响。
 * 故 [FOLLOW_APP] 的 prefsValue 复用 "system" 字面量但落在不同的键上。
 *
 * ⚠ 落盘走 [prefsValue]（数据键，勿本地化）；[label] 是纯展示名，随应用语言（见 [DisplayLang]）。
 */
enum class ThemeMode(val prefsValue: String) {
    FOLLOW_WALLPAPER("follow_wallpaper"),
    FOLLOW_APP("system"),
    LIGHT("light"),
    DARK("dark");

    val label: String
        get() = when (this) {
            FOLLOW_WALLPAPER -> if (DisplayLang.isZh) "跟随壁纸" else "Follow Wallpaper"
            FOLLOW_APP -> if (DisplayLang.isZh) "跟随应用" else "Follow App"
            LIGHT -> if (DisplayLang.isZh) "浅色模式" else "Light Mode"
            DARK -> if (DisplayLang.isZh) "深色模式" else "Dark Mode"
        }

    companion object {
        /** 课表外观"默认主题"下拉专用的偏好 key，仅决定今日页/课程表页主题，与全局主题开关隔离 */
        const val SCHEDULE_THEME_MODE_KEY = "schedule_theme_mode"

        fun fromPrefsValue(value: String?): ThemeMode =
            entries.find { it.prefsValue == value } ?: FOLLOW_WALLPAPER
    }
}
