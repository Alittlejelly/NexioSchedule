package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * iOS：与桌面（[screenWidthDp] 的 jvm actual）同口径 —— 窗口像素 ÷ 密度换算 dp。
 * 用的是跨平台 Compose API（LocalWindowInfo / LocalDensity），与 jvm actual 逻辑逐字相同（源集不同才没共用）。
 * 与 Android actual 的差异是有意为之：Android 必须继续读 LocalConfiguration（自由窗口/分屏语义已实测，换窗口尺寸 API 属改行为，违反零回归红线）。
 * 读不到有效密度时按原值返回（不会除出负数）；宽高 0 时 padding 偏小，下一帧窗口就绪后自动重组修正。
 */
@Composable
actual fun screenWidthDp(): Int {
    val widthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val density = LocalDensity.current.density
    val widthDp = if (density > 0f) widthPx / density else widthPx
    return widthDp.toInt()
}

/** iOS：同 [screenWidthDp] 的口径，取窗口高度。 */
@Composable
actual fun screenHeightDp(): Int {
    val heightPx = LocalWindowInfo.current.containerSize.height.toFloat()
    val density = LocalDensity.current.density
    val heightDp = if (density > 0f) heightPx / density else heightPx
    return heightDp.toInt()
}
