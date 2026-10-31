package com.haooz.chedule.data.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 桌面（JVM）侧平台实现。桌面定位是「迁移验证壳」：能真跑库与网络，不追求平台观感。
 */

actual fun <T> synchronizedOn(lock: Any, block: () -> T): T = synchronized(lock) { block() }

actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

actual fun compareByChineseCollator(a: String, b: String): Int = ChineseCollatorImpl.compare(a, b)

/** `sdkLevel` 固定 0：这是 Android 专有字段，非 Android 平台无意义。 */
actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    model = System.getProperty("os.arch") ?: "unknown",
    brand = System.getProperty("os.name") ?: "unknown",
    manufacturer = System.getProperty("java.vendor") ?: "unknown",
    osVersion = System.getProperty("os.version") ?: "unknown",
    sdkLevel = 0,
)

/** 没有系统提示条，落到日志；将来要弹窗只改这一个函数，调用方不动。 */
actual fun showToast(message: String, longDuration: Boolean) {
    NexioLog.i(TAG, message)
}

private const val TAG = "Toast"
