package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable

/**
 * iOS：返回 0（不裁切）—— CMP 容器本身圆角，同桌面口径。
 * 理由详见 [rememberScreenCornerRadiusPx] 的 commonMain KDoc。
 */
@Composable
actual fun rememberScreenCornerRadiusPx(): Float = 0f
