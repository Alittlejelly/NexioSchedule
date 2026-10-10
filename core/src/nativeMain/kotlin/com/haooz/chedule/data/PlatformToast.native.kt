package com.haooz.chedule.data

/**
 * Kotlin/Native 实现（linuxX64 编译门禁 + 将来的 iOS）：落到日志。
 *
 * iOS 上没有与 Toast 对等的东西，最接近的是短暂 HUD。等 `:iosApp` 真跑起来
 * 再决定是接原生提示还是维持日志 —— 调用方不用关心。
 */
actual fun showToast(message: String, longDuration: Boolean) {
    NexioLog.i("Toast", message)
}
