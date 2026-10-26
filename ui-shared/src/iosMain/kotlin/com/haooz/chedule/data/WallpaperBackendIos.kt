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

    /**
     * 同步取壁纸，供首帧使用（注入到 `WallpaperStore.synchronousLoader`）。
     *
     * 读盘 + skia 解码本身就是同步的，[decode] 只是为了满足接口才标 suspend。
     * 首帧前必须拿到，否则先画一帧底色再跳成用户设定。
     *
     * 解码结果按原始像素缓存一份（[RAW_CACHE]）：壁纸只在换图时变，每次启动重解 JPEG 是纯浪费。
     */
    fun loadNow(): ImageBitmap? {
        val source = AppFiles.root.resolve(WallpaperStore.FILE_NAME)
        val cache = AppFiles.root.resolve(RAW_CACHE)
        // 壁纸已被清掉但像素缓存还在：作废，否则会读回一张已经删掉的壁纸
        if (cache.exists() && !source.exists()) runCatching { cache.delete() }
        decodeRaw(cache)?.let { return it }
        if (!source.exists()) return null
        return try {
            Image.makeFromEncoded(source.readBytes()).toComposeImageBitmap().also { saveRaw(it, cache) }
        } catch (_: Exception) {
            null
        }
    }

    /** 写原始像素缓存：`宽(4) + 高(4) + ARGB 平铺`；失败静默，只影响下次启动速度 */
    private fun saveRaw(image: ImageBitmap, file: AppFile) {
        runCatching {
            val w = image.width
            val h = image.height
            if (w <= 0 || h <= 0) return
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
            val bytes = ByteArray(8 + w * h * 4)
            bytes[0] = (w shr 24).toByte()
            bytes[1] = (w shr 16).toByte()
            bytes[2] = (w shr 8).toByte()
            bytes[3] = w.toByte()
            bytes[4] = (h shr 24).toByte()
            bytes[5] = (h shr 16).toByte()
            bytes[6] = (h shr 8).toByte()
            bytes[7] = h.toByte()
            for (i in pixels.indices) {
                val p = pixels[i]
                val o = 8 + i * 4
                bytes[o] = (p and 0xFF).toByte()
                bytes[o + 1] = ((p shr 8) and 0xFF).toByte()
                bytes[o + 2] = ((p shr 16) and 0xFF).toByte()
                bytes[o + 3] = ((p shr 24) and 0xFF).toByte()
            }
            file.writeBytes(bytes)
        }
    }

    /** 读原始像素缓存；不存在/损坏/尺寸对不上都返回 null，由调用方回落到解码 */
    private fun decodeRaw(file: AppFile): ImageBitmap? = runCatching {
        if (!file.exists()) return@runCatching null
        val bytes = file.readBytes()
        if (bytes.size < 8) return@runCatching null
        val w = ((bytes[0].toInt() and 0xFF) shl 24) or ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        val h = ((bytes[4].toInt() and 0xFF) shl 24) or ((bytes[5].toInt() and 0xFF) shl 16) or
            ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
        if (w <= 0 || h <= 0 || bytes.size != 8 + w * h * 4) return@runCatching null
        // toComposeImageBitmap 的接收者是 skia Image，不是 Bitmap（与上面 encode/scale 同一条转换）
        val bitmap = Bitmap().apply {
            allocN32Pixels(w, h)
            installPixels(bytes.copyOfRange(8, bytes.size))
        }
        Image.makeFromBitmap(bitmap).toComposeImageBitmap()
    }.getOrNull()

    companion object {
        /** 解码结果的原始像素缓存（纯派生数据，删了只是多解一次） */
        private const val RAW_CACHE = "appearance_wallpaper.pixels"
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
        // 方向感知：与 Android 同口径，横图 cover 竖屏时不再砍高度
        return w to h
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
