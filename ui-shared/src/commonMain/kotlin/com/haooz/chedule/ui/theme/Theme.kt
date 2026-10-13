/** 应用主题 - 定义 Material3 主题配色方案 */
package com.haooz.chedule.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.KEY_CHANGED_ANYWHERE
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import top.yukonga.miuix.kmp.material.LocalChromeLensEnabled
import top.yukonga.miuix.kmp.material.LocalUseFakeProgressiveBlur
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalAppSettingDark
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.LocalPredictiveBackEnabled

/** 应用触感反馈开关对应的 SharedPreferences 文件与键 */
const val APP_PREFS_NAME = "app_preferences"
const val KEY_HAPTIC_FEEDBACK = "haptic_feedback_enabled"

@Composable
fun CourseScheduleTheme(
    content: @Composable () -> Unit
) {
    val prefs = remember { AppStorage.store("app_theme_prefs") }
    val themeMode = remember { mutableStateOf(prefs.getString("theme_mode", "system")) }

    // 全局触感开关：关闭后屏蔽全 App 触感
    // （本项目与 Miuix 组件的触感都经 LocalHapticFeedback，统一拦截即可）
    val hapticPrefs = remember { AppStorage.store(APP_PREFS_NAME) }
    var hapticFeedbackEnabled by remember {
        mutableStateOf(hapticPrefs.getBoolean(KEY_HAPTIC_FEEDBACK, true))
    }

    DisposableEffect(prefs) {
        // ⚠ iOS 回调不带 key（传 `KEY_CHANGED_ANYWHERE` 哨兵），Android 才给准确 key。
        // 不能用 `key == "theme_mode"` 精确过滤：那样 iOS 收到通知也会被丢掉、主题不刷新。
        // key 未知时无条件复查，已知时才精确过滤。
        val token = prefs.registerListener { key ->
            if (key == KEY_CHANGED_ANYWHERE || key == "theme_mode") {
                themeMode.value = prefs.getString("theme_mode", "system")
            }
        }
        onDispose {
            prefs.unregisterListener(token)
        }
    }

    DisposableEffect(hapticPrefs) {
        // 同上：iOS 传哨兵 → 无条件复查，不能按 key 精确过滤。
        val token = hapticPrefs.registerListener { key ->
            if (key == KEY_CHANGED_ANYWHERE || key == KEY_HAPTIC_FEEDBACK) {
                hapticFeedbackEnabled = hapticPrefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
            }
        }
        onDispose {
            hapticPrefs.unregisterListener(token)
        }
    }

    // 稳定 ThemeController：只改 colorSchemeMode，避免 remember(themeMode) 换实例导致组合树重建
    val controller = remember {
        ThemeController(
            when (themeMode.value) {
                "light" -> ColorSchemeMode.Light
                "dark" -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            }
        )
    }
    controller.colorSchemeMode = when (themeMode.value) {
        "light" -> ColorSchemeMode.Light
        "dark" -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }
    // 捕获当前（系统默认）触感实现，封装为受开关控制的门控实现
    val defaultHaptic = LocalHapticFeedback.current
    val gatedHaptic = remember(defaultHaptic) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (hapticFeedbackEnabled) {
                    defaultHaptic.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }
    MiuixTheme(
        controller = controller,
        content = {
            CompositionLocalProvider(
                LocalHapticFeedback provides gatedHaptic,
                // :miuix 读不到 :core 偏好，Skia 侧靠这个 Local 拿「应用内深浅色」；
                // 不 provide 时 iOS/桌面会退回跟随系统。
                // ⚠ `system` 必须传 null（退回 isSystemInDarkTheme()），不能当成「非 dark 即 light」。
                LocalAppSettingDark provides when (themeMode.value) {
                    "dark" -> true
                    "light" -> false
                    else -> null
                },
                // :miuix 需要的 App 能力注入点（避免 :miuix → :app 反向依赖）；
                // 效果与迁移前一致，调用点无需逐个改。
                LocalChromeLensEnabled provides AppMaterialSettings.chromeLensEnabled(),
                LocalUseFakeProgressiveBlur provides AppMaterialSettings.progressiveBlurUseFake(),
                LocalPredictiveBackEnabled provides PredictiveBackSettings.enabled,
            ) {
                content()
            }
        }
    )
}
