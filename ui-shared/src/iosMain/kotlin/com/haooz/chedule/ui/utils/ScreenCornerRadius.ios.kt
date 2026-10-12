@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.cinterop.useContents
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIDevice
import platform.UIKit.UICornerConfiguration
import platform.UIKit.UICornerRadius
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.setCornerConfiguration
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIRectCornerAllCorners
import platform.UIKit.effectiveRadiusForCorner

/**
 * Returns the window's effective corner radius in pixels using public UIKit APIs.
 *
 * A UIWindow's default corner configuration can resolve to zero even on a rounded
 * display. On iOS 26+, a transparent child with a container-concentric configuration
 * resolves the window geometry after attachment and layout. The host is never clipped
 * or reconfigured. Older systems use a best-effort device-size estimate.
 */
@Composable
actual fun rememberScreenCornerRadiusPx(): Float {
    val container = LocalWindowInfo.current.containerSize
    val controller = LocalUIViewController.current
    var radiusPx by remember(controller, container) { mutableStateOf(0f) }

    LaunchedEffect(controller, container) {
        // Composition may run before UIKit attaches the Compose controller to a window.
        while (controller.view.window == null) withFrameNanos { }
        val window = controller.view.window ?: return@LaunchedEffect
        val selector = NSSelectorFromString("effectiveRadiusForCorner:")
        if (selector == null || !window.respondsToSelector(selector)) {
            radiusPx = (fallbackCornerRadiusPoints(window) * window.screen.scale).toFloat()
            return@LaunchedEffect
        }

        val probe = UIView(frame = window.bounds).apply {
            userInteractionEnabled = false
            setCornerConfiguration(
                UICornerConfiguration.configurationWithRadius(
                    UICornerRadius.containerConcentricRadius()
                )
            )
        }
        window.addSubview(probe)
        try {
            // Let UIKit commit the hierarchy and geometry before resolving concentricity.
            withFrameNanos { }
            probe.setFrame(window.bounds)
            window.layoutIfNeeded()
            val points = probe.effectiveRadiusForCorner(UIRectCornerAllCorners)
            radiusPx = (points * window.screen.scale).toFloat()
        } finally {
            // Also runs on cancellation when the host is removed or resized.
            probe.removeFromSuperview()
        }
    }
    return radiusPx
}

/**
 * Legacy device-size estimate; this is not a measured physical screen radius.
 */
private fun fallbackCornerRadiusPoints(window: UIWindow): Double {
    val topInset = window.safeAreaInsets.useContents { top }
    if (topInset <= 0.0) return 0.0
    val isPad = UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad
    if (isPad) return 30.0
    val shortSide = window.screen.bounds.useContents {
        minOf(size.width, size.height)
    }
    return when {
        shortSide >= 430.0 -> 62.0
        shortSide >= 390.0 -> 55.0
        else -> 44.0
    }
}
