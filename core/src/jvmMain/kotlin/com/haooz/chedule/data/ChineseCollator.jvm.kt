package com.haooz.chedule.data

/** 桌面（JVM）实现：转 `jvmShared` 的 Collator（与 Android 共用同一份）。 */
actual fun compareByChineseCollator(a: String, b: String): Int =
    ChineseCollatorImpl.compare(a, b)
