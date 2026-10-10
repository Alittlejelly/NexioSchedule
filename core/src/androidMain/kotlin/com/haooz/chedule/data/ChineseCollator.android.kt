package com.haooz.chedule.data

/** Android 实现：转 `jvmShared` 的 Collator（与桌面共用同一份，行为与迁移前逐字一致）。 */
actual fun compareByChineseCollator(a: String, b: String): Int =
    ChineseCollatorImpl.compare(a, b)
