package com.haooz.chedule.data.platform

/**
 * 跨平台日志门面：用法与 `android.util.Log` 完全同形（tag 在前、message 在后、Throwable 为可选末参），
 * 调用点只需改 import。级别映射：Android 保留原始级别，桌面/JVM 按级别分流到 stdout/stderr。
 *
 * ⚠ 不要在这里加 Android 专有重载（可变参数、Bundle、字节数组等），否则 commonMain 又出现平台 API。
 */

/**
 * 平台日志实现。Android → Logcat；JVM/桌面 → stdout/stderr；iOS 后续接 NSLog。
 *
 * 用顶层 `expect fun` 而非 `expect object`：后者在 Kotlin 仍是 Beta，会刷 KT-61573 警告。
 */
expect fun platformLog(level: Int, tag: String, message: String, throwable: Throwable?)

/** 级别常量，对外公开以便调用方设置门槛（如 debug 包放开到 DEBUG） */
const val LOG_LEVEL_VERBOSE = 0
const val LOG_LEVEL_DEBUG = 1
const val LOG_LEVEL_INFO = 2
const val LOG_LEVEL_WARN = 3
const val LOG_LEVEL_ERROR = 4

/** 全局日志开关。关掉后 [platformLog] 完全不被调用，调用点上的字符串拼接也一并省掉。 */
var logEnabled: Boolean = true

/** 最低输出级别，低于此级别直接丢弃（连字符串拼接都不会发生）。默认 [LOG_LEVEL_WARN]，滤掉 d/i 流水。 */
var logMinLevel: Int = LOG_LEVEL_WARN

internal fun emit(level: Int, tag: String, message: String, throwable: Throwable?) {
    if (logEnabled && level >= logMinLevel) platformLog(level, tag, message, throwable)
}

object NexioLog {
    fun v(tag: String, message: String) {
        emit(LOG_LEVEL_VERBOSE, tag, message, null)
    }

    fun v(tag: String, message: String, throwable: Throwable?) {
        emit(LOG_LEVEL_VERBOSE, tag, message, throwable)
    }

    fun d(tag: String, message: String) {
        emit(LOG_LEVEL_DEBUG, tag, message, null)
    }

    fun d(tag: String, message: String, throwable: Throwable?) {
        emit(LOG_LEVEL_DEBUG, tag, message, throwable)
    }

    fun i(tag: String, message: String) {
        emit(LOG_LEVEL_INFO, tag, message, null)
    }

    fun i(tag: String, message: String, throwable: Throwable?) {
        emit(LOG_LEVEL_INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String) {
        emit(LOG_LEVEL_WARN, tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable?) {
        emit(LOG_LEVEL_WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String) {
        emit(LOG_LEVEL_ERROR, tag, message, null)
    }

    fun e(tag: String, message: String, throwable: Throwable?) {
        emit(LOG_LEVEL_ERROR, tag, message, throwable)
    }
}
