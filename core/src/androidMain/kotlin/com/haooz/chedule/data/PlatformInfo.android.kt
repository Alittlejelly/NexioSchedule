package com.haooz.chedule.data

import android.os.Build

/** Android 实现：逐字读取 `Build.*`，字段含义与上报口径不变。 */
actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    model = Build.MODEL,
    brand = Build.BRAND,
    manufacturer = Build.MANUFACTURER,
    osVersion = Build.VERSION.RELEASE,
    // sdkLevel 是数字 SDK_INT，不是版本字符串 RELEASE
    sdkLevel = Build.VERSION.SDK_INT,
)
