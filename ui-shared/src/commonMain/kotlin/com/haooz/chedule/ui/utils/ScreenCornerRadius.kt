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
 * - **桌面 / iOS 返回 0（不裁切）**：桌面窗口圆角由系统窗口管理器画，Compose 再裁
 *   会叠出双圆角；iOS 的 CMP 容器本身就是圆角，同理。等真机上看到直角边缘
 *   再按平台补真实半径 —— 语义与 Android 拿不到 `RoundedCorner` 时相同。
 */
@Composable
expect fun rememberScreenCornerRadiusPx(): Float
