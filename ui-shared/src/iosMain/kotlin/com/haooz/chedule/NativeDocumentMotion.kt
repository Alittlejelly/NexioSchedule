package com.haooz.chedule

import com.haooz.chedule.ui.navigation.NAV_BG_DIM
import com.haooz.chedule.ui.navigation.NAV_ENTER_DURATION
import com.haooz.chedule.ui.navigation.NAV_ENTER_EASING
import com.haooz.chedule.ui.navigation.NAV_EXIT_DURATION
import com.haooz.chedule.ui.navigation.NAV_EXIT_EASING
import com.haooz.chedule.ui.navigation.NAV_PARALLAX

/** UIKit samples the existing Compose tokens instead of maintaining a second curve library. */
object NativeDocumentMotion {
    val parallax: Double get() = NAV_PARALLAX.toDouble()
    val backgroundDim: Double get() = NAV_BG_DIM.toDouble()

    fun duration(push: Boolean): Double =
        (if (push) NAV_ENTER_DURATION else NAV_EXIT_DURATION) / 1000.0

    fun progress(fraction: Double, push: Boolean): Double {
        val easing = if (push) NAV_ENTER_EASING else NAV_EXIT_EASING
        return easing.transform(fraction.coerceIn(0.0, 1.0).toFloat()).toDouble()
    }
}
