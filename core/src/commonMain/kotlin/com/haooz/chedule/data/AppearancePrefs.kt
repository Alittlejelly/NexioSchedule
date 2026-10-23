package com.haooz.chedule.data

/**
 * 外观存储的偏好文件名与键名（数据兼容契约的一部分）。
 * `:core` 原本反向依赖 `:app` 的 `ScheduleAppearance` 类型来判断外观键，这里只把两个字符串常量下沉到 `:core`，由 `:app` 用 `const val` 转发，两边取值同源。
 * ⚠ 值**逐字未变**：改了等于老用户的外观设置读不出来。
 */
object AppearancePrefs {
    /** 外观独立 prefs 文件名（`ScheduleAppearance.FILE`） */
    const val FILE = "appearance_settings"

    /** 唯一键：`CombinationStyle` 的 JSON 快照（`ScheduleAppearance.FILE_STYLE_KEY`） */
    const val FILE_STYLE_KEY = "appearance_style"
}
