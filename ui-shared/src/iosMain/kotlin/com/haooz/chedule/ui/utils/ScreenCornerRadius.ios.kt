@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.cinterop.useContents
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIDevice
import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIRectCornerAllCorners
import platform.UIKit.effectiveRadiusForCorner

/**
 * Returns the window's effective corner radius in pixels using public UIKit APIs.
 *
 * iOS 26+ resolves the window's corner configuration, including windowed layouts.
 * Older systems use a best-effort device-size estimate for transition cards.
 */
@Composable
actual fun rememberScreenCornerRadiusPx(): Float {
    val container = LocalWindowInfo.current.containerSize
    val window = IosUiHost.keyWindow()
    return remember(container, window) {
        if (window == null) return@remember 0f
        val selector = NSSelectorFromString("effectiveRadiusForCorner:")
        // Capability checking keeps the iOS 26 API safe on older systems.
        val points = if (selector != null && window.respondsToSelector(selector)) {
            window.effectiveRadiusForCorner(UIRectCornerAllCorners)
        } else {
            fallbackCornerRadiusPoints()
        }
        (points * window.screen.scale).toFloat()
    }
}

/**
 * Legacy device-size estimate; this is not a measured physical screen radius.
 */
private fun fallbackCornerRadiusPoints(): Double {
    val window = IosUiHost.keyWindow() ?: return 0.0
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
