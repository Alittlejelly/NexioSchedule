package com.haooz.chedule.i18n

import android.os.LocaleList

/** Android 实现：取系统首选 locale 的语言子标签。 */
actual fun systemLanguageTag(): String {
    val locale = LocaleList.getDefault().get(0) ?: java.util.Locale.getDefault()
    return locale?.language ?: ""
}
