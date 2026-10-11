@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.data

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.posix.memcpy

/**
 * `NSData` ⇄ `ByteArray` 转换（iOS 侧共用）。
 *
 * 原本是 `HttpService.ios.kt` 里的两个 `private` 扩展，`NsFileAppFile` 也要用，
 * 抽到这里 —— 两处都是 cinterop 指针操作，重复实现容易在两件事上踩坑：
 * - **空数组不能取 `addressOf(0)`**（零长数组取指针会抛），必须走 `null + 0` 分支
 * - `length` 是 `ULong`，`memcpy` 的长度参数要显式 `convert()`
 *
 * ⚠ 是 **internal 顶层扩展**：只在 `:core` 的 `iosMain` 内可见，不会泄漏到
 * commonMain（那里编不了 `platform.Foundation`，会被 linuxX64 门禁拦下）。
 */
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, size.convert())
    }
    return out
}

internal fun ByteArray.toNSData(): NSData {
    // 空数组不能走 addressOf(0)（零长数组取指针会抛），显式走 null + 0 分支
    if (isEmpty()) return NSData.dataWithBytes(null, 0u)
    usePinned { pinned ->
        return NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
    }
}
