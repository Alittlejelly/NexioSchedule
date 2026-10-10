package com.haooz.chedule.data

/**
 * 桌面（JVM）实现：**没有 Toast 这回事**，落到日志。
 *
 * 桌面端当前的定位是「迁移验证壳」，提示条不是它要验证的东西；
 * 将来要真提示再换成 AWT/Swing 的通知组件 —— 那时改这一个文件即可，
 * 调用方（已在 `:ui-shared`）一行都不用动。
 */
actual fun showToast(message: String, longDuration: Boolean) {
    NexioLog.i("Toast", message)
}
