package com.haooz.chedule.data

/**
 * Kotlin/Native 实现（iOS）：**退化为 Unicode 码点比较**。
 *
 * ⚠ 这是有意的降级，不是等价实现 —— 中文会按码点排而不是拼音。
 * 需要真正的中文排序时改用 `NSString.compare(options = NSNumericSearch)` 或
 * `CFStringCompare` 的本地化选项，只改本文件即可。
 */
actual fun compareByChineseCollator(a: String, b: String): Int = a.compareTo(b)
