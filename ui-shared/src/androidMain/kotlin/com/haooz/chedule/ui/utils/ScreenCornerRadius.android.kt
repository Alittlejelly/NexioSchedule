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
 * Android：自由窗口固定 20dp；Android 12+ 读系统真实圆角；更老的系统返回 0（不裁切）。
 *
 * ⚠ remember key 必须含窗口尺寸（containerSize）：只挂 context/density 的话，全屏时缓存了
 *   屏幕物理圆角，进出小窗不会重算 —— 表现为页面间转场不裁 20dp，而「进小窗之后才 push」
 *   的二级页首次组合恰好缓存到 20dp，出现「有的页有、有的页没有」。
 */
@Composable
actual fun rememberScreenCornerRadiusPx(): Float {
    val context = LocalContext.current
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    return remember(context, density, containerSize) {
        try {
            if ((context as? Activity)?.isInMultiWindowMode == true) {
                20f * density.density
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                val bounds = wm?.currentWindowMetrics?.bounds
                val maxBounds = wm?.maximumWindowMetrics?.bounds
                // 小窗兜底：窗口任一维度明显小于屏幕（分屏 / ROM 自家小窗都算）→ 固定 20dp
                val isSmallWindow = bounds != null && maxBounds != null &&
                    (bounds.width() < maxBounds.width() * SMALL_WINDOW_RATIO ||
                        bounds.height() < maxBounds.height() * SMALL_WINDOW_RATIO)
                if (isSmallWindow) {
                    20f * density.density
                } else {
                    wm?.currentWindowMetrics?.windowInsets
                        ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()
                        ?: 0f
                }
            } else {
                0f
            }
        } catch (_: Exception) {
            0f
        }
    }
}

/** 窗口尺寸 / 屏幕尺寸低于此比例视为小窗（留 5% 余量，防全屏下的取整误差误判） */
private const val SMALL_WINDOW_RATIO = 0.95f
