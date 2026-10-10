package com.haooz.chedule.data

/**
 * 按中文（拼音）规则比较两个字符串。
 *
 * 迁移自 `CourseSorting` 里的 `java.text.Collator`：Collator 是 JVM 专有 API，
 * commonMain 编不过（iOS/桌面走各自的本地化实现）。
 *
 * ## 各平台口径
 *
 * - **Android / JVM**：`Collator.getInstance(Locale.CHINA)` + `strength = SECONDARY`，
 *   与迁移前**逐字一致** —— 中文按拼音、同音字按次级权重区分。
 * - **Native（iOS 等）**：退化为 `compareTo`（Unicode 码点）。
 *   ⚠ 这是**有意的降级**，不是等价实现：iOS 上要真按拼音排，得用 `NSString.compare(options = NSNumericSearch)`
 *   或 `CFStringCompare` 的本地化选项。等 iOS 侧真正接入时再补 actual，届时只改这一个文件。
 *
 * @return 负数 / 0 / 正数，语义同 `Comparator.compare`
 */
expect fun compareByChineseCollator(a: String, b: String): Int
