package com.haooz.chedule.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * iOS 实现：取系统首选语言的 language 子标签。
 * `preferredLanguages` 形如 ["zh-Hans-CN", "en-CN"]，取首项 "zh-Hans-CN" → "zh"。
 */
actual fun systemLanguageTag(): String {
    val first = NSLocale.preferredLanguages.firstOrNull() as? String ?: return ""
    // 先截掉地区（-CN），再截掉文字系统（Hans），只留 language
    return first.split("-", "_").firstOrNull()?.lowercase() ?: ""
}
