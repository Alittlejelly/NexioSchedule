package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Android：继续读 `LocalConfiguration.current.screenWidthDp` —— 与迁移前逐字一致，
 * 自由窗口 / 分屏的既有语义不变（零回归红线）。
 */
@Composable
actual fun screenWidthDp(): Int = LocalConfiguration.current.screenWidthDp
