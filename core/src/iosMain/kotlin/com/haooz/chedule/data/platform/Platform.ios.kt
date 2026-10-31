package com.haooz.chedule.data.platform
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.size_tVar

// sysctl 系列已从 Apple 平台库移除且直接绑头文件会被弃用标注滤掉，
// 由自定义 cinterop 的 static inline 包装（apple-sysctl.def）暴露
import applesysctl.nexio_sysctlbyname

/**
 * iOS 实现：用 `sysctl` 读取真实机型与系统版本。
 *
 * 刻意不走 `UIDevice`——统计上报跑在 IO 线程，UIKit 的 currentDevice 属主线程 API，
 * 而且被裁剪过的 native target 也要能编。纯 posix 读取，线程安全：
 * - `hw.machine` → 机型标识（如 "iPhone16,2"，与 Android 侧 `Build.MODEL` 同为标识符口径）
 * - `kern.osproductversion` → 系统版本（如 "26.0"，对应 `Build.VERSION.RELEASE`）
 *
 * `brand` / `manufacturer` 恒为 "Apple"：iOS 生态没有厂商之分，写死即可。
 * `sdkLevel` 恒为 0 —— 它是 Android `Build.VERSION.SDK_INT` 的语义。
 */
@OptIn(ExperimentalForeignApi::class)
private fun sysctlString(name: String): String? = memScoped {
    val size = alloc<size_tVar>()
    if (nexio_sysctlbyname(name, null, size.ptr) != 0) return null
    if (size.value == 0uL) return null
    val buffer = allocArray<ByteVar>(size.value.toInt())
    if (nexio_sysctlbyname(name, buffer, size.ptr) != 0) return null
    buffer.toKString().takeIf { it.isNotBlank() }
}

actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    model = sysctlString("hw.machine") ?: "unknown",
    brand = "Apple",
    manufacturer = "Apple",
    osVersion = sysctlString("kern.osproductversion") ?: "unknown",
    sdkLevel = 0,
)
