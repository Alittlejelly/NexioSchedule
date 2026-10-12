package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable

/**
 * 屏幕圆角半径（px）—— 转场把上层页裁成圆角卡片时用（`AppNavHost`）。
 *
 * 拿不到时返回 0，调用方据此**不做裁切**（0 是合法值，不是失败标记）。
 *
 * ## 分平台实现的理由
 *
 * - **Android actual 读系统真实圆角**（`WindowManager` 的 `RoundedCorner`）：
 *   自由窗口固定 20dp、Android 12+ 取真实半径、更老的系统返回 0 —— 与迁移前
 *   `AppNavHost.kt` 里的私有实现逐字一致（零回归红线）。
 * - **桌面返回 0（不裁切）**：桌面窗口圆角由系统窗口管理器画，Compose 再裁会叠出双圆角。
 * - **iOS**: iOS 26+ uses the public UIKit `UIView.effectiveRadiusForCorner` API
 *   to resolve the window's effective corner radius. Older systems use a device-size
 *   estimate. Transition cards need their own clipping while moving inside the window;
 *   no private API is used.
 */
@Composable
expect fun rememberScreenCornerRadiusPx(): Float
