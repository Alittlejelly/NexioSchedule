package com.haooz.chedule.data

/**
 * 按中文（拼音）规则比较两个字符串；负数 / 0 / 正数语义同 `Comparator.compare`。
 *
 * ⚠ Native（iOS 等）退化为 `compareTo`（Unicode 码点），是有意降级而非等价实现；
 * 补真实现只需改这一个文件的 actual。
 */
expect fun compareByChineseCollator(a: String, b: String): Int
