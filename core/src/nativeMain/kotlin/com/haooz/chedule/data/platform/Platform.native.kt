package com.haooz.chedule.data.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSRecursiveLock

/**
 * Kotlin/Native 侧平台实现（iOS）。
 *
 * 设备信息在 `Platform.ios.kt`：它要调 `sysctl`，比这里其余几条重。
 */

/**
 * 给每个 lock 对象配一把 `NSRecursiveLock`，按对象身份分发。
 *
 * 用递归锁而不是 `NSLock`：`edit{}` 里存在「持 commitLock 再取 lock」的嵌套。
 * 锁表本身由 [mapLock] 保护，查完立刻放开 —— 不同命名空间的读写互不阻塞
 * （主线程读设置不会被后台写课表卡住）。
 *
 * 锁表强引用 lock 对象：调用点的 lock 都是长生命周期 `val`，不会无限增长。
 */
private val mapLock = NSRecursiveLock()
private val locks = HashMap<Any, NSRecursiveLock>()

actual fun <T> synchronizedOn(lock: Any, block: () -> T): T {
    val monitor = lockFor(lock)
    monitor.lock()
    try {
        return block()
    } finally {
        monitor.unlock()
    }
}

private fun lockFor(lock: Any): NSRecursiveLock {
    mapLock.lock()
    try {
        locks[lock]?.let { return it }
        return NSRecursiveLock().also { locks[lock] = it }
    } finally {
        mapLock.unlock()
    }
}

/** Native 上 `Dispatchers.IO` 是 internal，用 `Dispatchers.Default`（同样是多线程池）。 */
actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.Default

/** ⚠ 退化为 Unicode 码点比较：有意降级，中文不会按拼音排。 */
actual fun compareByChineseCollator(a: String, b: String): Int = a.compareTo(b)

/** 宿主注入过 [toastHandler] 就用它（iOS 注入原生 HUD），否则落日志。 */
actual fun showToast(message: String, longDuration: Boolean) {
    val handler = toastHandler
    if (handler != null) handler(message, longDuration) else NexioLog.i(TAG, message)
}

private const val TAG = "Toast"
