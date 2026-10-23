package com.haooz.chedule.data

/**
 * JVM / 桌面实现：按级别分流，`d` 丢弃以免 `schedule_*` 流水淹没控制台，`e` 走 stderr 便于与 stdout 分开重定向。
 */
actual fun platformLog(level: Int, tag: String, message: String, throwable: Throwable?) {
    if (level < LOG_LEVEL_INFO) return // v / d：项目当前 0 处使用 v，d 为调试流水
    val stream = if (level >= LOG_LEVEL_ERROR) System.err else System.out
    stream.println("[$tag] $message")
    throwable?.printStackTrace(stream)
}
