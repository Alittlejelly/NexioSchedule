package com.haooz.chedule.data

/**
 * Kotlin/Native 实现（iOS：iosArm64 / iosSimulatorArm64）。
 *
 * 与 JVM 实现一致：`i`/`w` 走 stdout、`e` 走 stderr、`d` 丢弃。
 * 需要时可在本文件内换成 `NSLog`（`platform.Foundation.NSLog`），结构不用变。
 */
actual fun platformLog(level: Int, tag: String, message: String, throwable: Throwable?) {
    if (level < LOG_LEVEL_INFO) return // v / d：调试流水
    val prefix = if (level >= LOG_LEVEL_ERROR) "[E]" else "[I]"
    println("$prefix[$tag] $message")
    throwable?.printStackTrace()
}
