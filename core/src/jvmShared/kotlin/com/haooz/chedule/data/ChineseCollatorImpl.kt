package com.haooz.chedule.data

import java.text.Collator
import java.util.Locale

/**
 * `java.text.Collator` 的中文比较实现，Android 与桌面（JVM）共用同一份。
 *
 * ⚠ 刻意不写 `actual`：同一份源码被两个源集编译，写 actual 会重复声明。
 * ThreadLocal 必须保留：Collator 非线程安全（compare 间会缓存上次结果），且只在真正排序时才创建。
 */
internal object ChineseCollatorImpl {

    private val collators = ThreadLocal.withInitial {
        Collator.getInstance(Locale.CHINA).apply { strength = Collator.SECONDARY }
    }

    fun compare(a: String, b: String): Int = collators.get().compare(a, b)
}
