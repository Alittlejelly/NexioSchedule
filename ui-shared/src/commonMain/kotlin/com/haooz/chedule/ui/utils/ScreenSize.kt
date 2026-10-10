package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable

/**
 * 当前窗口宽度（dp）—— 页面里 `LocalConfiguration.current.screenWidthDp` 的跨平台替身。
 *
 * ## 为什么需要它
 *
 * `:androidApp` 有 42 处 `LocalConfiguration`（判平板 / 算列表 padding），
 * 而 `LocalConfiguration` 是 Android 专有类型，commonMain 编不过。
 *
 * ## 分平台实现的理由
 *
 * - **Android actual 必须继续读 `LocalConfiguration`**：与迁移前逐字一致，
 *   自由窗口 / 分屏下 `Configuration.screenWidthDp` 的语义是实测过的，
 *   换成窗口尺寸 API 属于「改行为」，违反 Android 零回归红线。
 * - **桌面 actual 读窗口像素 + 密度换算**（与 `:miuix` 的 `TabletWidth.skiko.kt` 同口径）。
 *
 * 只判「是不是平板」的调用点优先用 `:miuix` 现成的 [top.yukonga.miuix.kmp.layout.isTabletWidth]；
 * 本函数给**需要具体宽度值**的场景（如 `((width - 600) …)` 的渐变 padding 公式）用。
 */
@Composable
expect fun screenWidthDp(): Int
