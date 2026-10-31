package com.haooz.chedule.data

import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Android 侧平台实现。五条 `actual` 全部与迁移前逐字等价 —— 这里只做接线，不改行为。
 */

actual fun <T> synchronizedOn(lock: Any, block: () -> T): T = synchronized(lock) { block() }

actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

actual fun compareByChineseCollator(a: String, b: String): Int = ChineseCollatorImpl.compare(a, b)

actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    model = Build.MODEL,
    brand = Build.BRAND,
    manufacturer = Build.MANUFACTURER,
    osVersion = Build.VERSION.RELEASE,
    sdkLevel = Build.VERSION.SDK_INT,
)

/**
 * Toast 宿主。`:core` 不能反向依赖 `android.app.Application`，Context 由宿主注入
 * （同 `AppStorage.init` / `AppFiles.init` / `AppInfo.init`）。
 */
private var toastHost: ((message: String, longDuration: Boolean) -> Unit)? = null

/** 必须在 `Application.onCreate` 里调用一次，之后 [showToast] 才弹得出来。 */
fun initToast(host: (message: String, longDuration: Boolean) -> Unit) {
    toastHost = host
}

actual fun showToast(message: String, longDuration: Boolean) {
    val host = toastHost
    if (host == null) {
        // 未注入是宿主接线漏了，不是业务错误：记一笔、丢弃，绝不能让「提示用户」把应用搞崩
        NexioLog.w(TAG, "showToast 未注入，丢弃提示：$message")
        return
    }
    host(message, longDuration)
}

private const val TAG = "Platform"
