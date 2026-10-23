package com.haooz.chedule.ui.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Android：自由窗口固定 20dp；Android 12+ 读系统四角真实圆角；更老系统返回 0（不裁切）。
 *
 * ⚠ remember key 必须含 containerSize：否则只挂 context/density 时全屏会缓存屏幕物理圆角，
 *   进出小窗不重算，导致「有的页裁、有的页不裁」。
 */
@Composable
actual fun rememberScreenCornerRadiiPx(): ScreenCornerRadiiPx {
    val context = LocalContext.current
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    return remember(context, density, containerSize) {
        try {
            if ((context as? Activity)?.isInMultiWindowMode == true) {
                ScreenCornerRadiiPx.uniform(20f * density.density)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                val bounds = wm?.currentWindowMetrics?.bounds
                val maxBounds = wm?.maximumWindowMetrics?.bounds
                // 小窗兜底：窗口任一维度明显小于屏幕（分屏 / ROM 自家小窗都算）→ 固定 20dp
                val isSmallWindow = bounds != null && maxBounds != null &&
                    (bounds.width() < maxBounds.width() * SMALL_WINDOW_RATIO ||
                        bounds.height() < maxBounds.height() * SMALL_WINDOW_RATIO)
                if (isSmallWindow) {
                    ScreenCornerRadiiPx.uniform(20f * density.density)
                } else {
                    val insets = wm?.currentWindowMetrics?.windowInsets
                    fun radius(position: Int): Float =
                        insets?.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
                    ScreenCornerRadiiPx(
                        topLeft = radius(RoundedCorner.POSITION_TOP_LEFT),
                        topRight = radius(RoundedCorner.POSITION_TOP_RIGHT),
                        bottomRight = radius(RoundedCorner.POSITION_BOTTOM_RIGHT),
                        bottomLeft = radius(RoundedCorner.POSITION_BOTTOM_LEFT),
                    )
                }
            } else {
                ScreenCornerRadiiPx.Zero
            }
        } catch (_: Exception) {
            ScreenCornerRadiiPx.Zero
        }
    }
}

/** 窗口尺寸 / 屏幕尺寸低于此比例视为小窗（留 5% 余量，防全屏下的取整误差误判） */
private const val SMALL_WINDOW_RATIO = 0.95f
