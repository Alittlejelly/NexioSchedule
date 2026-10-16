@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * iOS：CMP 的 `ImageBitmap` 就是 skia 对象，但 skia 编码入口只认 [Bitmap]，
 * CMP 侧只有 `readPixels` 这一个取像素出口，所以要过一道 IntArray → ByteArray。
 *
 * ⚠ 字节序与 stride 的坑与 `WallpaperBackendIos.readToSkiaBitmap` 同源，见那边的注释。
 */
internal actual fun encodeImageBitmapPng(image: ImageBitmap): ByteArray? = runCatching {
    val w = image.width
    val h = image.height
    if (w <= 0 || h <= 0) return null
    val pixels = IntArray(w * h)
    image.readPixels(
        buffer = pixels,
        startX = 0,
        startY = 0,
        width = w,
        height = h,
        bufferOffset = 0,
        stride = w,
    )
    val bytes = ByteArray(w * h * 4)
    for (i in pixels.indices) {
        val p = pixels[i]
        bytes[i * 4] = (p and 0xFF).toByte()
        bytes[i * 4 + 1] = ((p shr 8) and 0xFF).toByte()
        bytes[i * 4 + 2] = ((p shr 16) and 0xFF).toByte()
        bytes[i * 4 + 3] = ((p shr 24) and 0xFF).toByte()
    }
    val skiaBitmap = Bitmap().apply {
        allocN32Pixels(w, h)
        installPixels(bytes)
    }
    Image.makeFromBitmap(skiaBitmap).encodeToData(EncodedImageFormat.PNG)?.bytes
}.getOrNull()

internal actual fun decodeImageBitmapPng(bytes: ByteArray): ImageBitmap? = runCatching {
    if (bytes.isEmpty()) return null
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()
