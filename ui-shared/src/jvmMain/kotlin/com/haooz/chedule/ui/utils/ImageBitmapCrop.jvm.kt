package com.haooz.chedule.ui.utils

import androidx.compose.ui.graphics.ImageBitmap

/** 桌面（skiko）没有硬件位图概念，重绘裁剪即可（见 [cropImageBitmap] 的 KDoc）。 */
internal actual fun cropImageBitmapImpl(
    source: ImageBitmap,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): ImageBitmap? = cropByRedraw(source, x, y, width, height)
