package com.haooz.chedule.ui.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android：**必须**走原生 `Bitmap.createBitmap` 的子集重载，不能用 [cropByRedraw]。
 *
 * 原因是 `GraphicsLayer.toImageBitmap()` 在 Android 上返回的是**硬件位图**
 * （`Bitmap.createBitmap(Picture)` 内部固定 `Config.HARDWARE`），画进软件 Canvas 会抛
 * `IllegalArgumentException: Software rendering doesn't support hardware bitmaps` ——
 * 重绘裁剪因此永远只能拿到 null
 */
internal actual fun cropImageBitmapImpl(
    source: ImageBitmap,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): ImageBitmap? = runCatching {
    Bitmap.createBitmap(source.asAndroidBitmap(), x, y, width, height).asImageBitmap()
}.getOrNull()
