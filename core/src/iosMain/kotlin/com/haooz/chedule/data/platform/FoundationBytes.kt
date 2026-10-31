@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.data.platform
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.posix.memcpy

/**
 * `NSData` ⇄ `ByteArray` 转换（iOS 侧共用）。
 *
 * ⚠ `public` 是刻意的：internal 跨模块不可见，`:ui-shared` 的 `iosMain` 也要用，勿改回。
 * 坑：空数组不能取 `addressOf(0)`（零长数组取指针会抛），须走 `null + 0`；
 * `length` 是 `ULong`，`memcpy` 的长度参数要显式 `convert()`。
 */
public fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, size.convert())
    }
    return out
}

public fun ByteArray.toNSData(): NSData {
    // 零长数组取 addressOf 会抛，走 null + 0 分支
    if (isEmpty()) return NSData.dataWithBytes(null, 0u)
    usePinned { pinned ->
        return NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
    }
}
