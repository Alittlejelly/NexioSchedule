package top.yukonga.miuix.kmp.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 强制深色主题的覆盖开关。null = 不覆盖（跟随外层主题）。
 * BlurBottomSheet 内部提供 null，阻止外层 App 的强制深色渗进弹窗；MiuixTheme 的 forceDark 也读它。
 */
val LocalForcedDarkTheme = staticCompositionLocalOf<Boolean?> { null }

/**
 * App 偏好里的「是否深色」，由**宿主**在根部 provide。
 *
 * :miuix 不依赖 :core，iOS/桌面的 `rememberAppSettingDark()` 读不到 NSUserDefaults，
 * 只能退回 `isSystemInDarkTheme()`——应用内深浅色切换在 iOS 因此失效（2026-10-12 修复引入）。
 * Android actual 直接读 SharedPreferences；Skia 侧读本 Local，宿主没 provide 时才退回系统深浅色。
 *
 * ⚠ 默认 `null` 而非 `false`：默认 false 会让没 provide 的宿主一律亮色，比跟随系统更糟。
 */
val LocalAppSettingDark = staticCompositionLocalOf<Boolean?> { null }

/**
 * App 偏好里记录的「是否深色」（跟随系统 / 强制浅 / 强制深）。
 *
 * 仅声明契约：真实数据在 App 的 SharedPreferences（Android 实现见 ThemeUtils.android.kt），
 * Skia 侧退回系统深浅色；宿主可替换读取来源，调用方无感。
 */
@Composable
expect fun rememberAppSettingDark(): Boolean

/**
 * 宿主是否**真的**在用系统壁纸取色（Monet）—— 偏好已开启且当前平台支持
 * （Android 要求 SDK >= 31，其它平台恒为 false）。
 *
 * 默认 `false` 而非 null：不取色即走内置亮暗主题，未接入本 Local 的宿主行为与从前一致。
 */
val LocalMonetColorEnabled = staticCompositionLocalOf { false }