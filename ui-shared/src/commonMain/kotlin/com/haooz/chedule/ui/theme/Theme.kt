/** 应用主题 - 定义 Material3 主题配色方案 */
package com.haooz.chedule.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.platform.LocalDensity
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.KEY_CHANGED_ANYWHERE
import com.haooz.chedule.data.currentDeviceInfo
import com.haooz.chedule.ui.components.text.TextMotionBox
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.haooz.chedule.ui.utils.rememberScreenCornerRadiiPx
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.material.LocalMiuixTextMotion
import top.yukonga.miuix.kmp.material.MiuixTextMotion
import top.yukonga.miuix.kmp.theme.LocalScreenCornerRadius
import top.yukonga.miuix.kmp.theme.LocalScreenTopCornerRadii
import top.yukonga.miuix.kmp.theme.ScreenTopCornerRadii
import top.yukonga.miuix.kmp.material.LocalChromeLensEnabled
import top.yukonga.miuix.kmp.material.LocalGlassBlurDp
import top.yukonga.miuix.kmp.material.GlassSurfaceAlphaDrop
import top.yukonga.miuix.kmp.material.LocalGlassDialogBlurDp
import top.yukonga.miuix.kmp.material.LocalGlassPanelBlurDp
import top.yukonga.miuix.kmp.material.LocalGlassSurfaceAlphaDrop
import top.yukonga.miuix.kmp.material.LocalUseFakeProgressiveBlur
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalAppSettingDark
import top.yukonga.miuix.kmp.theme.LocalMonetColorEnabled
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.LocalPredictiveBackEnabled

/** 应用触感反馈开关对应的 SharedPreferences 文件与键 */
const val APP_PREFS_NAME = "app_preferences"
const val KEY_HAPTIC_FEEDBACK = "haptic_feedback_enabled"
const val KEY_MONET_COLOR = "monet_color_enabled"

// 系统壁纸取色（Monet）的「是否生效」标志定义在 `:miuix`（`LocalMonetColorEnabled`）：
// 弹窗组件要在 `:miuix` 内部读它来决定自建 controller 用不用 Monet 模式，
// 而 `:miuix` 不能反向依赖本模块。本模块 provide 的是**有效值**（偏好 && 平台支持）。

/** 系统壁纸取色目前仅由 Android 12 及以上提供，其他宿主保留原有主题。 */
internal fun isMonetColorAvailable(): Boolean = currentDeviceInfo().sdkLevel >= 31

@Composable
internal expect fun platformMonetMaterialColors(dark: Boolean): ColorScheme

/** 页面与弹窗的亮暗覆盖保留莫奈配色，并保持 controller 实例稳定。 */
@Composable
fun rememberAppThemeController(
    isDark: Boolean? = null,
    themeMode: String = "system",
    // 传原始偏好值时本函数自行叠加平台判断；不传则吃到当前作用域已生效的取值。
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
    val density = LocalDensity.current
    val screenCorners = rememberScreenCornerRadiiPx()
    val screenCornerRadius = with(density) { screenCorners.maxRadius.toDp() }
    val screenTopCorners = with(density) {
        if (screenCorners.maxRadius > 0f) {
            ScreenTopCornerRadii(screenCorners.topLeft.toDp(), screenCorners.topRight.toDp())
        } else null
    }
    MiuixTheme(
        controller = controller,
        content = {
            // MaterialTheme 会自己提供两样东西并顶掉 miuix 默认值，故必须在进入 MaterialTheme 前捕获、再在里层写回：
            // 1. `LocalIndication = ripple()`（顶掉 miuix 的 MiuixIndication）；2. `ProvideTextStyle(bodyLarge)`（只写 fontSize 不写 lineHeight 的 Text）
            val miuixIndication: Indication = LocalIndication.current
            val outerTextStyle: TextStyle = LocalTextStyle.current
            CompositionLocalProvider(
                LocalScreenCornerRadius provides screenCornerRadius,
                LocalScreenTopCornerRadii provides screenTopCorners,
                LocalHapticFeedback provides gatedHaptic,
                // 弹窗（BlurBottomSheet）会读它来决定自建 controller 用不用 Monet 模式，
                // 所以这里下发的是**有效值**：偏好开启 && 平台支持。
                LocalMonetColorEnabled provides (monetEnabled && isMonetColorAvailable()),
                LocalSystemDarkTheme provides systemDarkTheme,
                // A null system source retains the platform fallback on other hosts.
                // iOS resolves automatic mode using the externally supplied system appearance.
                LocalAppSettingDark provides appDark,
                // :miuix 需要的 App 能力注入点（避免 :miuix → :app 反向依赖）；
                // 效果与迁移前一致，调用点无需逐个改。
                LocalChromeLensEnabled provides AppMaterialSettings.chromeLensEnabled(),
                LocalUseFakeProgressiveBlur provides AppMaterialSettings.progressiveBlurUseFake(),
                LocalGlassBlurDp provides AppMaterialSettings.glassBlurDp(),
                LocalGlassPanelBlurDp provides AppMaterialSettings.glassPanelBlurDp(),
                LocalGlassDialogBlurDp provides AppMaterialSettings.glassDialogBlurDp(),
                LocalGlassSurfaceAlphaDrop provides GlassSurfaceAlphaDrop(
                    light = AppMaterialSettings.glassSurfaceAlphaDrop(isLight = true),
                    dark = AppMaterialSettings.glassSurfaceAlphaDrop(isLight = false),
                ),
                LocalPredictiveBackEnabled provides PredictiveBackSettings.enabled,
                // :miuix 内部绘制的展示文字（偏好项标题/副标题、下拉菜单当前值、
                // 弹窗标题/摘要）交给我们的换字动效：模块不能反向依赖 :ui-shared，
                // 只能在这里注册实现。没注册时组件退回普通 Text。
                LocalMiuixTextMotion provides AppTextMotion,
            ) {
                MaterialTheme(colorScheme = materialColors) {
                    CompositionLocalProvider(
                        LocalIndication provides miuixIndication,
                        LocalTextStyle provides outerTextStyle,
                    ) {
                        content()
                    }
                }
            }
        }
    )
}

/**
 * :miuix 内部展示文字的换字动效实现。
 *
 * 只给「整段换掉」的展示文案用（偏好项标题/副标题、下拉菜单当前值、弹窗标题/摘要），
 * 不接输入框 —— 那里的文字每敲一个字都会变，带动效会一直糊着。
 *
 * 顶层 val：身份稳定，`LocalMiuixTextMotion` 不会因为重组换实例而让整棵子树失效。
 */
private val AppTextMotion: MiuixTextMotion = { text, modifier, color, fontSize, fontWeight, textAlign ->
    TextMotionBox(key = text, modifier = modifier, textAlign = textAlign) { shown, layerModifier ->
        MiuixText(
            text = shown,
            modifier = layerModifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
        )
    }
}
