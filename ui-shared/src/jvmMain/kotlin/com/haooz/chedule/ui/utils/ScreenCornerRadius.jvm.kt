package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable

/**
 * 桌面：返回 0（不裁切）—— 窗口圆角由系统窗口管理器画，Compose 再裁会叠出双圆角。
 * 理由详见 [rememberScreenCornerRadiiPx] 的 commonMain KDoc。
 */
@Composable
actual fun rememberScreenCornerRadiiPx(): ScreenCornerRadiiPx = ScreenCornerRadiiPx.Zero
