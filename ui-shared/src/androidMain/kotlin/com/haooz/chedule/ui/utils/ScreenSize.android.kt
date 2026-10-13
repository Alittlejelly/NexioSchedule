package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Android：读 `LocalConfiguration.current.screenWidthDp`（自由窗口 / 分屏语义）。
 * ⚠ 与迁移前逐字一致，勿改。
 */
@Composable
actual fun screenWidthDp(): Int = LocalConfiguration.current.screenWidthDp

/** Android：同 [screenWidthDp]，读 LocalConfiguration。⚠ 与迁移前逐字一致，勿改。 */
@Composable
actual fun screenHeightDp(): Int = LocalConfiguration.current.screenHeightDp
