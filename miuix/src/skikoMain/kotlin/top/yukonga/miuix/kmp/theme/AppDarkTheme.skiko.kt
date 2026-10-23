package top.yukonga.miuix.kmp.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/**
 * Skia（iOS / 桌面 / wasm）实现：优先读宿主的 [LocalAppSettingDark]，没有才跟随系统。
 *
 * ⚠ 2026-10-12 重写：原无条件 `= isSystemInDarkTheme()`，iOS 上应用内深浅色切换写盘后 UI 不变、页面锁死跟随系统。
 * 本模块不依赖 `:core`（拿不到 NSUserDefaults），故由宿主在根部 provide「null = 未提供」，
 * 未 provide 时退回系统深浅色（与旧行为一致，非退化）。
 */
@Composable
actual fun rememberAppSettingDark(): Boolean =
    LocalAppSettingDark.current ?: isSystemInDarkTheme()