package top.yukonga.miuix.kmp.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 强制深色主题的覆盖开关。null = 不覆盖（跟随外层主题）。
 *
 * 原先定义在 :app 的 ThemeUtils.kt 里，是纯 CompositionLocal、无平台依赖，
 * 所以直接搬进本模块：BlurBottomSheet 会在内部提供 `null` 来阻止外层 App 的
 * 强制深色渗进弹窗（弹窗自己按 sheetAppDark 决定）。
 *
 * 放在 theme 包是因为 MiuixTheme 的 forceDark 也会读它 —— 属于主题语义而非 App 偏好。
 */
val LocalForcedDarkTheme = staticCompositionLocalOf<Boolean?> { null }

/**
 * App 偏好里的「是否深色」，由**宿主**在根部 provide。
 *
 * ## 为什么需要这个（2026-10-12 修 iOS 深浅色切换失效）
 *
 * `:miuix` 不依赖 `:core`，所以 iOS / 桌面的 `rememberAppSettingDark()`
 * **读不到 NSUserDefaults**，只能退回 `isSystemInDarkTheme()` —— 于是
 * 「应用内深浅色切换」在 iOS 上完全无效（Android 读 SharedPreferences 所以正常）。
 *
 * 按项目铁律用「null = 该平台/该宿主没有提供」表达：
 * - Android 的 actual 直接读 SharedPreferences，不依赖本 Local；
 * - Skia 侧 actual 读本 Local，宿主没 provide 时才退回系统深浅色。
 *
 * ⚠ 默认 `null` 而非 `false`：默认 false 会让「没 provide 的宿主」一律亮色，
 *   比跟随系统更糟。
 */
val LocalAppSettingDark = staticCompositionLocalOf<Boolean?> { null }

/**
 * App 偏好里记录的「是否深色」（跟随系统 / 强制浅 / 强制深）。
 *
 * 这里只声明契约：真实数据在 App 的 SharedPreferences 里，Android 侧的实现见
 * ThemeUtils.android.kt；Skia 侧退回系统深浅色（iOS 接入时换成 NSUserDefaults 即可）。
 * App 若要精确对齐自己的偏好，在 Android 实现里替换读取来源即可，调用方无感。
 */
@Composable
expect fun rememberAppSettingDark(): Boolean

/**
 * 宿主是否**真的**在用系统壁纸取色（Monet）—— 即「偏好已开启」且「当前平台支持」
 * （Android 要求 SDK >= 31，其它平台恒为 false）。
 *
 * 默认 `false` 而非 null：不取色 = 原有的内置亮暗主题，
 * 没接入本 Local 的宿主行为与从前逐字一致。
 */
val LocalMonetColorEnabled = staticCompositionLocalOf { false }