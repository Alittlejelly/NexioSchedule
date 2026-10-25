package com.haooz.chedule.i18n

import java.util.Locale

/** 桌面（JVM）实现：取 JVM 默认 locale 的语言子标签。 */
actual fun systemLanguageTag(): String = Locale.getDefault().language
