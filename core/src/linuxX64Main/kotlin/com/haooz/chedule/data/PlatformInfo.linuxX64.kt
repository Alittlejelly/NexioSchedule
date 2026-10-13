package com.haooz.chedule.data

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * linuxX64 实现（仅作编译门禁目标，不出发布物）。
 *
 * 用 `kotlin.native.Platform` 的 osFamily / cpuArchitecture 填充 —— 无需平台框架。
 * iOS 的真实实现在 `iosMain`（PlatformInfo.ios.kt，sysctl 读真机数据），
 * 不再与本文件共用：同一 expect 不允许在 nativeMain 与 iosMain 各挂一份 actual。
 */
@OptIn(ExperimentalNativeApi::class)
actual fun currentDeviceInfo(): DeviceInfo {
    val os = Platform.osFamily.name
    return DeviceInfo(
        model = Platform.cpuArchitecture.name,
        brand = os,
        manufacturer = os,
        osVersion = os,
        sdkLevel = 0,
    )
}
