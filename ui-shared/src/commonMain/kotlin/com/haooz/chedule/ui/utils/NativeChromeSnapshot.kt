package com.haooz.chedule.ui.utils

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 含**原生 chrome**（iOS 的 UITabBar）的全屏快照。
 *
 * 转场快照走 `GraphicsLayer.record()`，只录得到 Compose 绘制的内容；iOS 上原生
 * TabBar 是 UIKit 视图，不进 Compose 的绘制管线，故那一段永远缺。
 *
 * Android/桌面没有原生 chrome，返回 null —— 调用方沿用 Compose 录的快照即可。
 */
expect fun captureScreenWithNativeChrome(): ImageBitmap?
