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
 * - **iOS 读真实屏幕圆角**：`UIScreen` 没有公开圆角 API，走 KVC 读私有
 *   `_displayCornerRadius`（读不到时按设备类型兜底）。**必须给真值** ——
 *   转场中间态的上层页是「浮在屏幕中间的卡片」，不是铺满的，用系统窗口裁切兜不住，
 *   给 0 会在圆屏上露出四个直角。理由与取舍详见 iOS actual 的 KDoc。
 */
@Composable
expect fun rememberScreenCornerRadiusPx(): Float
