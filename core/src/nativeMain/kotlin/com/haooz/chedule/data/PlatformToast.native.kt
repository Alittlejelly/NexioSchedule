package com.haooz.chedule.data

/**
 * Kotlin/Native 实现（iOS）。
 *
 * 有宿主注入 [toastHandler] 就用宿主的（iOS 启动时注入原生 HUD，见 `IosToast`）；
 * 没有就落到日志（如宿主还没起来的早期调用）。
 */
actual fun showToast(message: String, longDuration: Boolean) {
    val handler = toastHandler
    if (handler != null) {
        handler(message, longDuration)
    } else {
        NexioLog.i("Toast", message)
    }
}
