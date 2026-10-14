package top.yukonga.miuix.kmp.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/**
 * Skia（iOS / 桌面 / wasm）实现 —— **优先读宿主的 [LocalAppSettingDark]**，没有才跟随系统。
 *
 * ⚠ 2026-10-12 重写：原来无条件 `= isSystemInDarkTheme()`，上面 KDoc 里那句
 *   「iOS 接入时应改为读 NSUserDefaults」一直没有落实 —— 于是 iOS 上
 *   **应用内深浅色切换完全无效**（写进了磁盘但 UI 不变），页面也只能锁死跟随系统。
 *
 * 为什么不由本模块自己读：`:miuix` **不依赖 `:core`**，拿不到 NSUserDefaults，
 * 也不该为读一个偏好把数据层拖进来（反向依赖）。按项目铁律用
 * 「null = 该宿主没有提供」表达，由宿主在根部 provide。
 *
 * 未 provide 时退回系统深浅色 —— 与旧行为一致，不是退化。
 */
@Composable
actual fun rememberAppSettingDark(): Boolean =
    LocalAppSettingDark.current ?: isSystemInDarkTheme()