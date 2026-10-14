package com.haooz.chedule.data

import android.util.Log

/**
 * Android 实现：原样转发到 Logcat。
 *
 * ⚠ 刻意不加过滤或级别映射：加映射会让同一行日志在两个平台长得不一样。
 */
actual fun platformLog(level: Int, tag: String, message: String, throwable: Throwable?) {
    when (level) {
        LOG_LEVEL_VERBOSE -> if (throwable != null) Log.v(tag, message, throwable) else Log.v(tag, message)
        LOG_LEVEL_DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
        LOG_LEVEL_INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
        LOG_LEVEL_WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        else -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
