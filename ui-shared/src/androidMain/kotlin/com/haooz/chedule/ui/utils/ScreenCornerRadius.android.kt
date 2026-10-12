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

/**
 * Android：逐字保留迁移前 `AppNavHost.kt` 里的私有实现（零回归红线）——
 * 自由窗口固定 20dp；Android 12+ 读系统真实圆角；更老的系统返回 0（不裁切）。
 */
@Composable
actual fun rememberScreenCornerRadiusPx(): Float {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        try {
            if ((context as? Activity)?.isInMultiWindowMode == true) {
                20f * density.density
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.currentWindowMetrics?.windowInsets
                    ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()
                    ?: 0f
            } else {
                0f
            }
        } catch (_: Exception) {
            0f
        }
    }
}
