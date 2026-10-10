package com.haooz.chedule.ui.utils

import android.app.Activity
import android.content.res.Configuration
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.ScheduleAppearance
import com.haooz.chedule.data.ThemeMode

// ⚠ 本文件只剩 **Android 专有**部分（2026-10-10 拆分）。
// 跨平台的核心（isAppDarkTheme / rememberAppSettingDark / schedulePageBackgroundColor /
// courseCardSolidBacking / LocalForcedDarkTheme / AppThemeSnapshot / PageBackdropSnapshot）
// 已迁入 :ui-shared 的同包名 ThemeUtils.kt —— 调用方 import 一行不用改。
//
// ⚠⚠ 文件名不要再改回 ThemeUtils.kt：:ui-shared 已有同包名的 ThemeUtils.kt，
// 同包同文件名在 Kotlin 里会生成同一个类 FQN（com.haooz.chedule.ui.utils.ThemeUtilsKt），
// 编译期各自能解析通过，但打 dex 时同名类互相覆盖，运行期表现为
// NoSuchMethodError: applyThemeAwareSystemBars（真机崩溃，编译看不出来）。

/** 仅影响今日页/课程表页，与全局 theme_mode 隔离 */
@Composable
fun rememberScheduleThemeMode(): ThemeMode {
    val prefs = remember { AppStorage.store(ScheduleAppearance.FILE_THEME) }
    val themeMode = remember { mutableStateOf(ScheduleAppearance.getThemeMode()) }

    DisposableEffect(prefs) {
        // 走 KeyValueStore.registerListener。⚠ iOS 侧该实现返回 null
        //（NSUserDefaultsDidChangeNotification 不带具体 key），主题切换后要等下次组合才刷新 ——
        // 是平台能力差异，不是崩溃：token 为 null 时 unregisterListener 是空操作。
        val token = prefs.registerListener { key ->
            if (key == ThemeMode.SCHEDULE_THEME_MODE_KEY) {
                themeMode.value = ScheduleAppearance.getThemeMode()
            }
        }
        onDispose {
            prefs.unregisterListener(token)
        }
    }

    return themeMode.value
}

fun Activity.applyThemeAwareSystemBars() {
    val themeMode = AppStorage.store("app_theme_prefs").getString("theme_mode", "system")

    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> {
            val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightMode == Configuration.UI_MODE_NIGHT_YES
        }
    }
    // 状态栏与导航栏均跟随应用设置，不受壁纸强制主题影响
    applyThemeAwareSystemBars(isDark)
    applyNavigationBarIsDark(isDark)
}

// 按显式深色值刷新状态栏；导航栏仍跟随应用设置
fun Activity.applyThemeAwareSystemBars(isDark: Boolean) {
    window.decorView.post {
        ApiCompat.setSystemBarsAppearance(
            window,
            if (isDark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )
    }
}

// 导航栏图标始终跟随 theme_mode，不随壁纸强制主题变化
fun Activity.applyNavigationBarIsDark(isDark: Boolean) {
    window.decorView.post {
        ApiCompat.setSystemBarsAppearance(
            window,
            if (isDark) 0 else WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )
    }
}
