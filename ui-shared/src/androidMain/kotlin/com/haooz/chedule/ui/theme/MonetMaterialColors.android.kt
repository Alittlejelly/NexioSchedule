package com.haooz.chedule.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun platformMonetMaterialColors(dark: Boolean): ColorScheme {
    val context = LocalContext.current
    // Android 12 以下走不到此分支（调用点已用 isMonetColorAvailable() 挡住），仅作兜底；
    // 它读取 staticCompositionLocal，必须留在 remember 外。
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return MaterialTheme.colorScheme
    // 壁纸配色变化会更新系统 Configuration，因此需重新取色而非复用旧缓存。
    val configuration = LocalConfiguration.current
    // ⚠ 必须缓存：dynamicXxxColorScheme 每次调用都新建 40+ 个 Color（逐个 HCT 换算），且每次
    //   重组都被 CourseScheduleTheme 调用；而 LocalColorScheme 用 neverEqualPolicy，新实例一律
    //   触发整树重组并产生大量垃圾，加速 GC 进而回收上方弱引用监听器。修法与 :miuix platformDynamicColors 一致。
    return remember(context, configuration, dark) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
}
