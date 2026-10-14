@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.useContents
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import platform.UIKit.UIScreen

/**
 * iOS 壁纸后端。
 *
 * CMP 的 `ImageBitmap` 在 iOS 上就是 skia 对象，UIKit 侧没有公开的 `ImageBitmap ⇄ UIImage`
 * 双向转换，所以 encode 只能 `ImageBitmap → skia Bitmap → encodeToData`。
 *
 * ⚠ 文件名仍是 `appearance_wallpaper.webp`（数据兼容契约，Android 侧同一路径）但 iOS 存
 * **JPEG**：格式按内容识别不看扩展名，iOS 是新平台无存量 webp 可读，且 skia 的 WEBP 编码
 * 在 iOS 上不可用。
 */
internal class IosWallpaperBackend : WallpaperBackend {

    override suspend fun decode(bytes: ByteArray): ImageBitmap? {
        if (bytes.isEmpty()) return null
        return try {
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun encode(image: ImageBitmap, quality: Int): ByteArray? {
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return null
        return try {
            val skiaBitmap = readToSkiaBitmap(image, w, h) ?: return null
            Image.makeFromBitmap(skiaBitmap)
                .encodeToData(EncodedImageFormat.JPEG, quality.coerceIn(1, 100))
                ?.bytes
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun scale(
        image: ImageBitmap,
        width: Int,
        height: Int,
    ): ImageBitmap {
        if (width <= 0 || height <= 0) return image
        if (image.width == width && image.height == height) return image
        return try {
            val src = readToSkiaBitmap(image, image.width, image.height) ?: return image
            val dst = Bitmap().apply { allocN32Pixels(width, height) }
            Canvas(dst).drawImageRect(
                image = Image.makeFromBitmap(src),
                src = Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                dst = Rect.makeWH(width.toFloat(), height.toFloat()),
            )
            Image.makeFromBitmap(dst).toComposeImageBitmap()
        } catch (_: Exception) {
            image
        }
    }

    override fun screenBoundsPx(): Pair<Int, Int> {
        val bounds = UIScreen.mainScreen.bounds
        val scale = UIScreen.mainScreen.scale
        // CGRect 是 C 值类型，必须用 useContents 才能读字段
        var w = 0
        var h = 0
        bounds.useContents {
            w = (size.width * scale).toInt()
            h = (size.height * scale).toInt()
        }
        return maxOf(w, h) to minOf(w, h)
    }

    /**
     * 把 `ImageBitmap` 的像素读进一块新的 skia [Bitmap]（N32 / ARGB_8888）—— CMP 只有
     * `readPixels` 这一个取像素出口，而 skia 编码/缩放入口只认 `Bitmap`，中间没有直接桥。
     *
     * ⚠ 字节序：`readPixels` 给 IntArray、`installPixels` 吃 ByteArray（每像素 4 字节）；
     * 小端下 Int 低位在前 = BGRA，与 N32 内存布局一致，`int → 4 字节` 平铺即等价。
     * ⚠ `stride` 单位是**像素**（不是字节），必须等于 `width`。
     */
    private fun readToSkiaBitmap(image: ImageBitmap, w: Int, h: Int): Bitmap? {
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
        // Int 小端展开成 ByteArray：与 skia N32 的内存布局一致
        val bytes = ByteArray(w * h * 4)
        for (i in pixels.indices) {
            val p = pixels[i]
            bytes[i * 4] = (p and 0xFF).toByte()
            bytes[i * 4 + 1] = ((p shr 8) and 0xFF).toByte()
            bytes[i * 4 + 2] = ((p shr 16) and 0xFF).toByte()
            bytes[i * 4 + 3] = ((p shr 24) and 0xFF).toByte()
        }
        return Bitmap().apply {
            allocN32Pixels(w, h)
            installPixels(bytes)
        }
    }
}
