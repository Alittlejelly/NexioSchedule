package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable

/**
 * 屏幕圆角半径（px），转场把上层页裁成圆角卡片时用；拿不到返回 0（合法值，表示不裁切）。
 * ⚠ Android actual 读系统真实圆角，与迁移前逐字一致，勿改；桌面恒返回 0（再裁会叠出双圆角）。
 */
@Composable
expect fun rememberScreenCornerRadiusPx(): Float
