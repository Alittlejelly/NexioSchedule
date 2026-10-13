package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable

/**
 * 当前窗口宽度（dp）—— `LocalConfiguration.current.screenWidthDp` 的跨平台替身。
 * ⚠ Android actual 必须继续读 `LocalConfiguration`（与迁移前逐字一致，勿改）；
 * 只判「是不是平板」优先用 [top.yukonga.miuix.kmp.layout.isTabletWidth]，本函数供需要具体宽度值处。
 */
@Composable
expect fun screenWidthDp(): Int

/**
 * 当前窗口高度（dp）—— `LocalConfiguration.current.screenHeightDp` 的跨平台替身。
 * 只判「竖屏还是横屏」的调用点用它（如平板侧栏的避让策略分流）。
 */
@Composable
expect fun screenHeightDp(): Int
