/** 应用主题 - 定义 Material3 主题配色方案 */
package com.haooz.chedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.KEY_CHANGED_ANYWHERE
import com.haooz.chedule.data.currentDeviceInfo
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
const val KEY_MONET_COLOR = "monet_color_enabled"

private val LocalMonetColorEnabled = staticCompositionLocalOf { false }

/** 系统壁纸取色目前仅由 Android 12 及以上提供，其他宿主保留原有主题。 */
internal fun isMonetColorAvailable(): Boolean = currentDeviceInfo().sdkLevel >= 31

@Composable
internal expect fun platformMonetMaterialColors(dark: Boolean): ColorScheme

/** 页面与弹窗的亮暗覆盖保留莫奈配色，并保持 controller 实例稳定。 */
@Composable
fun rememberAppThemeController(
    isDark: Boolean? = null,
    themeMode: String = "system",
    monetEnabled: Boolean = LocalMonetColorEnabled.current,
): ThemeController {
    val useMonet = monetEnabled && isMonetColorAvailable()
    val mode = when {
        isDark == true || (isDark == null && themeMode == "dark") ->
            if (useMonet) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
        isDark == false || (isDark == null && themeMode == "light") ->
            if (useMonet) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        else -> if (useMonet) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
    }
    val controller = remember { ThemeController(mode) }
    controller.colorSchemeMode = mode
    return controller
}

/** Unmodified system appearance; null lets hosts without a bridge use Compose's fallback. */
val LocalSystemDarkTheme = staticCompositionLocalOf<Boolean?> { null }

@Composable
fun CourseScheduleTheme(
    systemDarkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val prefs = remember { AppStorage.store("app_theme_prefs") }
    val themeMode = remember { mutableStateOf(prefs.getString("theme_mode", "system")) }
    var monetEnabled by remember { mutableStateOf(prefs.getBoolean(KEY_MONET_COLOR, false)) }

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
            if (key == KEY_CHANGED_ANYWHERE || key == KEY_MONET_COLOR) {
                monetEnabled = prefs.getBoolean(KEY_MONET_COLOR, false)
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

    // iOS supplies the system appearance from outside the overridden UIKit subtree.
    // Both Miuix colors and app-theme readers must use this same source to avoid feedback.
    val appDark = when (themeMode.value) {
        "light" -> false
        "dark" -> true
        else -> systemDarkTheme
    }
    val controller = rememberAppThemeController(isDark = appDark, monetEnabled = monetEnabled)
    val materialColors = if (monetEnabled && isMonetColorAvailable()) {
        platformMonetMaterialColors(appDark ?: isSystemInDarkTheme())
    } else MaterialTheme.colorScheme
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
                LocalMonetColorEnabled provides monetEnabled,
                LocalSystemDarkTheme provides systemDarkTheme,
                // A null system source retains the platform fallback on other hosts.
                // iOS resolves automatic mode using the externally supplied system appearance.
                LocalAppSettingDark provides appDark,
                // :miuix 需要的 App 能力注入点（避免 :miuix → :app 反向依赖）；
                // 效果与迁移前一致，调用点无需逐个改。
                LocalChromeLensEnabled provides AppMaterialSettings.chromeLensEnabled(),
                LocalUseFakeProgressiveBlur provides AppMaterialSettings.progressiveBlurUseFake(),
                LocalPredictiveBackEnabled provides PredictiveBackSettings.enabled,
            ) {
                MaterialTheme(colorScheme = materialColors) {
                    content()
                }
            }
        }
    )
}
