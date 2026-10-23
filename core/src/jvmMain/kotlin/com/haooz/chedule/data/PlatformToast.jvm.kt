package com.haooz.chedule.data

/**
 * 桌面（JVM）实现：无 Toast，落到日志。桌面定位为迁移验证壳，将来需要再换 AWT/Swing 通知组件（仅改此文件，调用方不动）。
 */
actual fun showToast(message: String, longDuration: Boolean) {
    NexioLog.i("Toast", message)
}
