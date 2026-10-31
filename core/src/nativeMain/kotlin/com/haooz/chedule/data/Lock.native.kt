package com.haooz.chedule.data

import platform.Foundation.NSRecursiveLock

/**
 * Kotlin/Native 侧：给每个 lock 对象配一把 `NSRecursiveLock`，按对象身份分发。
 *
 * Native 没有 `synchronized`，stdlib 也没有等价原语（Kotlin 2.4 已移除
 * `kotlin.native.concurrent.synchronized`），所以这里用 Foundation 的锁。
 *
 * 用 `NSRecursiveLock` 而不是 `NSLock`：`edit{}` 里存在「持 commitLock 再取 lock」
 * 的嵌套，递归锁保证同一线程重复进入不会自锁死。
 *
 * 锁表用 [mapLock] 保护；查表后立刻释放，执行 [block] 时只持该对象那把锁，
 * 不同命名空间的读写互不阻塞（主线程读设置不会被后台写课表卡住）。
 *
 * 已知取舍：锁表强引用 lock 对象。调用点的 lock 都是长生命周期的 `val`
 * （每个 `SqliteKeyValueStore` 两个、`HolidayManager` 一个），不会无限增长。
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
