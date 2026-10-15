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
    // 返回 MaterialTheme.colorScheme 的分支走不到（调用点已被 isMonetColorAvailable()
    // 挡在 Android 12 以下），但保留兜底；它读 staticCompositionLocal，必须留在 remember 外。
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return MaterialTheme.colorScheme
    // 壁纸配色变化会更新系统 Configuration；即使 Activity 不重建也要换掉缓存。
    val configuration = LocalConfiguration.current
    // ⚠ 必须缓存：dynamicXxxColorScheme 每次调用都新建一整套 ColorScheme（40+ 个 Color，
    //   每个都要跑一遍 HCT 换算）。而它被 CourseScheduleTheme 直接调用于**每次重组**，
    //   且 MaterialTheme 的 LocalColorScheme 是 staticCompositionLocalOf(neverEqualPolicy)——
    //   新实例一律判为「变了」，会把整棵子树强制重组并制造大量垃圾（垃圾一多就加速 GC，
    //   正好触发上面那条弱引用监听器被回收）。修法与 :miuix platformDynamicColors 一致。
    return remember(context, configuration, dark) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
}
