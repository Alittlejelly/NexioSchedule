@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.haooz.chedule.ui.utils.IosUiHost
import kotlinx.cinterop.useContents
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIScreen
import platform.darwin.NSObject

/**
 * iOS 相册选壁纸（对应 Android 的 `ActivityResultContracts.PickVisualMedia` + 降采样解码）。
 *
 * ## 流水线
 *
 * `PHPickerViewController` → `PHPickerResult.itemProvider`
 * → `loadItemForTypeIdentifier("public.image")` → `UIImage`（Photos 走这条）
 * → **`UIGraphics` 降到屏幕像素** → JPEG NSData → skia `Image.makeFromEncoded` → `ImageBitmap`
 *
 * ## 三个必须记住的点
 *
 * 1. **必须在 iOS 侧降采样**：共享层的 `applyPickedWallpaper` 拿到位图后**直接原样用**
 *    （只算 `minScale` 做显示缩放，不重采样），Android 是靠宿主在解码阶段三级降采样来压内存的。
 *    iOS 相册原图动辄 4000×3000，不降采样直接把内存顶爆。
 * 2. **回调必须回主线程**：`loadItem…` 的 completion 在后台队列，而共享层
 *    `applyPickedWallpaper` 会写 Compose 状态，后台写会崩。
 * 3. **不能用 `loadObjectOfClass(UIImage, …)`**：那个方法要的是 `Class<NSItemProviderReading>`
 *    协议对象，Kotlin/Native 侧只能拿 `UIImage.Companion` 硬转协议实例 ——
 *    编译期只报「This cast can never succeed」警告，**运行期直接抛**。
 *    改用 `loadItemForTypeIdentifier` + 类型分发（见 [PickerDelegate.picker]）。
 */
class IosWallpaperPicker {

    /** 强引用 delegate：`PHPickerViewController.delegate` 是弱引用 */
    private var delegate: PickerDelegate? = null

    /** 拉起相册；用户取消 / 解码失败都回调 `null`（共享层按「取消」处理） */
    fun pick(onResult: (ImageBitmap?) -> Unit) {
        val config = PHPickerConfiguration().apply {
            setFilter(PHPickerFilter.imagesFilter)
            setSelectionLimit(1)
        }
        val picker = PHPickerViewController(configuration = config)
        val d = PickerDelegate(onResult)
        picker.delegate = d
        delegate = d
        val host = IosUiHost.topViewController()
        if (host == null) {
            delegate = null
            onResult(null)
            return
        }
        host.presentViewController(picker, animated = true, completion = null)
    }

    private class PickerDelegate(
        private val onResult: (ImageBitmap?) -> Unit,
    ) : NSObject(), PHPickerViewControllerDelegateProtocol {

        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, completion = null)
            val provider = didFinishPicking
                .filterIsInstance<PHPickerResult>()
                .firstOrNull()
                ?.itemProvider
            if (provider == null) {
                onResult(null)
                return
            }
            // 不请求具体类型，拿到什么处理什么：Photos 一般给 UIImage（已按 EXIF 校正方向），
            // 也可能给 NSData / NSURL。三种都兜住，避免「某些相册源选不了」。
            provider.loadItemForTypeIdentifier("public.image", options = null) { item, _ ->
                val bitmap = when (item) {
                    is UIImage -> decodeToImageBitmap(item)
                    is NSData -> decodeBytes(item.toByteArray())
                    is NSURL -> item.path
                        ?.let { NSData.dataWithContentsOfFile(it) }
                        ?.let { decodeBytes(it.toByteArray()) }
                    else -> null
                }
                // 回主线程：共享层要写 Compose 状态
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    onResult(bitmap)
                }
            }
        }
    }

    companion object {

        /**
         * `UIImage` → 屏幕大小的 `ImageBitmap`。
         *
         * 失败返回 null（调用方按「取消」处理；提示文案在宿主装配层给，
         * 与 Android `decodeWallpaperSampled` 的分工一致）。
         */
        fun decodeToImageBitmap(image: UIImage): ImageBitmap? = try {
            val (targetW, targetH) = screenPixelSize()
            val scaled = downscale(image, targetW.toDouble(), targetH.toDouble())
            val data = UIImageJPEGRepresentation(scaled, 0.9)
            if (data == null) null else decodeBytes(data.toByteArray())
        } catch (_: Exception) {
            null
        }

        /** 直接给编码字节（JPEG/PNG/HEIC…）时：skia 解码 + 按屏幕降采样 */
        fun decodeBytes(bytes: ByteArray): ImageBitmap? = try {
            val src = Image.makeFromEncoded(bytes)
            val (targetW, targetH) = screenPixelSize()
            val scale = minOf(
                targetW.toFloat() / src.width.toFloat(),
                targetH.toFloat() / src.height.toFloat(),
                1f,
            )
            if (scale >= 1f) {
                src.toComposeImageBitmap()
            } else {
                val dstW = (src.width * scale).toInt().coerceAtLeast(1)
                val dstH = (src.height * scale).toInt().coerceAtLeast(1)
                val dst = Bitmap().apply { allocN32Pixels(dstW, dstH) }
                Canvas(dst).drawImageRect(
                    image = src,
                    src = Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
                    dst = Rect.makeWH(dstW.toFloat(), dstH.toFloat()),
                )
                Image.makeFromBitmap(dst).toComposeImageBitmap()
            }
        } catch (_: Exception) {
            null
        }

        /**
         * 按比例把原图缩到不超过屏幕像素（`UIGraphics` 经典路径）。
         *
         * ⚠ 不用 `preparingThumbnailOfSize`：那个 API（iOS 15+）在 Kotlin/Native 的
         * UIKit 绑定里没生成（实测 Unresolved reference）。`UIGraphics*` 这组是
         * iOS 2 起就有的老 API，绑定齐全且行为稳定。
         */
        private fun downscale(image: UIImage, maxW: Double, maxH: Double): UIImage {
            var w = 0.0
            var h = 0.0
            image.size.useContents {
                w = width
                h = height
            }
            if (w <= 0.0 || h <= 0.0) return image
            val ratio = minOf(maxW / w, maxH / h, 1.0)
            if (ratio >= 1.0) return image
            val newW = w * ratio
            val newH = h * ratio
            UIGraphicsBeginImageContextWithOptions(CGSizeMake(newW, newH), false, 1.0)
            image.drawInRect(CGRectMake(0.0, 0.0, newW, newH))
            val scaled = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()
            return scaled ?: image
        }

        /** 屏幕像素尺寸（宽, 高），与 `IosWallpaperBackend.screenBoundsPx` 同一口径 */
        private fun screenPixelSize(): Pair<Int, Int> {
            val bounds = UIScreen.mainScreen.bounds
            val scale = UIScreen.mainScreen.scale
            var w = 0
            var h = 0
            bounds.useContents {
                w = (size.width * scale).toInt()
                h = (size.height * scale).toInt()
            }
            return maxOf(w, h) to minOf(w, h)
        }
    }
}
