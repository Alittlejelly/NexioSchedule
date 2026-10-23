package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * 桌面（JVM）：窗口像素宽度 ÷ 密度换算 dp，与 `:miuix` 的 `TabletWidth.skiko.kt` 同口径。
 *
 * 读不到有效密度时（如组合首帧）按原值返回，不会除出负数；宽高为 0 时 padding 偏小，
 * 下一帧窗口尺寸就绪后自动重组修正。
 */
@Composable
actual fun screenWidthDp(): Int {
    val widthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val density = LocalDensity.current.density
    val widthDp = if (density > 0f) widthPx / density else widthPx
    return widthDp.toInt()
}

/** 桌面：同 [screenWidthDp] 的口径，取窗口高度。 */
@Composable
actual fun screenHeightDp(): Int {
    val heightPx = LocalWindowInfo.current.containerSize.height.toFloat()
    val density = LocalDensity.current.density
    val heightDp = if (density > 0f) heightPx / density else heightPx
    return heightDp.toInt()
}
