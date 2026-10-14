package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * iOS：与桌面（[screenWidthDp] 的 jvm actual）同口径 —— 窗口像素 ÷ 密度换算 dp。
 *
 * CMP 在 iOS 上同样提供 `LocalWindowInfo` / `LocalDensity`，所以这里不是「重写一份
 * 平台专有实现」，而是**跨平台 Compose API 的直接使用**；与 jvm actual 逐字相同
 * （不共用一份源码只是因为源集不同，逻辑要保持一致 —— 改一处要改两处）。
 *
 * 与 Android actual 的差异是有意为之：Android 必须继续读 `LocalConfiguration`
 * （自由窗口 / 分屏语义是实测过的，换成窗口尺寸 API 属于改行为，违反零回归红线）。
 *
 * 读不到有效密度时（组合首帧等）按原值返回，不会除出负数；宽高为 0 时调用方算出的
 * padding 偏小，下一帧窗口尺寸就绪后自动重组修正。
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
