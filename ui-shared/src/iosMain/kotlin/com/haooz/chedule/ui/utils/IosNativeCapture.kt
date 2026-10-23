@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.haooz.chedule.data.toByteArray
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.Foundation.NSData

/**
 * 原生层截屏桥：把含原生 chrome（UITabBar + 导航栏右上角菜单）的全屏画面交给 Compose——
 * 转场快照走 `GraphicsLayer.record()` 只录 Compose 绘制，抓不到 UIKit 那两层。
 *
 * ⚠ 传递**未压缩 BGRA 原始像素**而非 PNG：实测（iPhone 18 Pro Max / iOS 27.0）PNG 编解码共 270ms，
 * 改裸像素后截图段 344ms → ~90ms（同进程内存直接递缓冲，无需可传输压缩格式）。
 */
object IosNativeCapture {

    private var pendingPixels: ByteArray? = null
    private var pendingWidth = 0
    private var pendingHeight = 0

    /** Swift 侧注入：产出全屏截图的原始像素。 */
    var captureScreen: (() -> Unit)? = null

    /**
     * Swift 侧调用：交回截屏结果。
     *
     * `data` 为 **BGRA8888 紧凑排布**（rowBytes = width × 4，alpha 预乘），
     * 与 CoreGraphics 的 `premultipliedFirst | byteOrder32Little` 逐字节对应；
     * null / 非正尺寸 表示截屏失败。
     */
    fun storePixels(data: NSData?, width: Int, height: Int) {
        if (data == null || width <= 0 || height <= 0) {
            pendingPixels = null
            pendingWidth = 0
            pendingHeight = 0
            return
        }
        pendingPixels = data.toByteArray()
        pendingWidth = width
        pendingHeight = height
    }

    /**
     * 截一张含原生 chrome（底栏 **+ 导航栏右上角菜单**）的全屏图，失败返回 null
     * （调用方都能处理「没有快照」）。
     * 全程兜住异常：全屏 drawHierarchy 在低端机上容易 OOM，而 OOM 是 Error 不是
     * Exception，只 catch 常规异常等于没防。
     */
    fun captureScreenWithChrome(): ImageBitmap? {
        // 先清空：Swift 若没回调（或回调失败），绝不能拿到上一次的残留像素
        pendingPixels = null
        pendingWidth = 0
        pendingHeight = 0
        try {
            captureScreen?.invoke()
        } catch (_: Exception) {
            return null
        } catch (_: Throwable) {
            return null
        }
        val pixels = pendingPixels ?: return null
        val width = pendingWidth
        val height = pendingHeight
        pendingPixels = null
        pendingWidth = 0
        pendingHeight = 0
        if (width <= 0 || height <= 0 || pixels.size < width * height * 4) return null

        return try {
            Image.makeRaster(
                ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL),
                pixels,
                width * 4,
            ).toComposeImageBitmap()
        } catch (_: Exception) {
            null
        } catch (_: Throwable) {
            null
        }
    }
}
