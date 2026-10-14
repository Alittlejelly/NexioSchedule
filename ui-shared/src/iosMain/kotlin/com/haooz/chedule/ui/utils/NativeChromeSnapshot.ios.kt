package com.haooz.chedule.ui.utils

import androidx.compose.ui.graphics.ImageBitmap

/** 走原生视图树截屏，Swift 侧用 drawHierarchy 渲染（含 UITabBar）。 */
actual fun captureScreenWithNativeChrome(): ImageBitmap? = IosNativeCapture.captureScreenWithChrome()
