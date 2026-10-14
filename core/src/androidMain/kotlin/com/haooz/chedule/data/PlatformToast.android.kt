package com.haooz.chedule.data

/**
 * Android 实现：转发到系统 Toast。
 *
 * Context 由宿主注入（见 [initToast]）—— `:core` 不能反向依赖 `android.app.Application`，
 * 与 `AppStorage.init` / `AppFiles.init` / `AppInfo.init` 同一套路。
 *
 * 用 **Application 级** Context（宿主传进来的那个），不用 Activity：
 * Toast 不需要窗口令牌，Application Context 反而不会挂住 Activity 实例。
 */
private var toastHost: ((message: String, longDuration: Boolean) -> Unit)? = null

/**
 * 注入 Toast 宿主。必须在 `Application.onCreate` 里最先调用 ——
 * 之后任何 [showToast] 才真的弹得出来（未注入时只落日志、不崩）。
 */
fun initToast(host: (message: String, longDuration: Boolean) -> Unit) {
    toastHost = host
}

actual fun showToast(message: String, longDuration: Boolean) {
    val host = toastHost
    if (host == null) {
        // 未注入就调用是**宿主接线漏了**，不是业务错误：记下来、静默丢弃，
        // 绝不能让「提示用户」这种事反过来把应用搞崩。
        NexioLog.w("PlatformToast", "showToast 未注入，丢弃提示：$message")
        return
    }
    host(message, longDuration)
}
