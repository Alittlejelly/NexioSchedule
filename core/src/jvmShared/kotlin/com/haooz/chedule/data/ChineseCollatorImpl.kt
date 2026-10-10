package com.haooz.chedule.data

import java.text.Collator
import java.util.Locale

/**
 * `java.text.Collator` 的中文比较实现 —— Android 与桌面（JVM）**共用同一份**。
 *
 * 放在 `src/jvmShared/` 由 androidMain 与 jvmMain 各自 srcDir 接入（同 `FileAppFile` 的做法）。
 * ⚠ 这里刻意**不写 `actual`** —— 同一份源码被两个源集编译，写 actual 会重复声明。
 * actual 由各自的 `ChineseCollator.android.kt` / `ChineseCollator.jvm.kt` 转发到本对象。
 *
 * **ThreadLocal 必须保留**：Collator 在 compare 间会缓存上次结果，**非线程安全**；
 * 且只在真正排序时才创建，不拖慢首屏（与迁移前的注释同义）。
 */
internal object ChineseCollatorImpl {

    private val collators = ThreadLocal.withInitial {
        Collator.getInstance(Locale.CHINA).apply { strength = Collator.SECONDARY }
    }

    fun compare(a: String, b: String): Int = collators.get().compare(a, b)
}
