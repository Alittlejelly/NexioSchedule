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

private const val TAG = "WallpaperPicker"

/**
 * iOS 相册选壁纸（对应 Android 的 `ActivityResultContracts.PickVisualMedia` + 降采样解码）。
 *
 * ## 流水线
 *
 * `PHPickerViewController` → `PHPickerResult.itemProvider`
 * → `loadDataRepresentationForTypeIdentifier("public.image")` → `NSData`
 * → `UIImage` 解码 + `UIGraphics` 降到屏幕像素 → JPEG `NSData`
 * → skia `Image.makeFromEncoded` → `ImageBitmap`
 *
 * ## 三个必须记住的点
 *
 * 1. **必须用 `loadDataRepresentation…`，不能用 `loadItemForTypeIdentifier`**：后者对相册图
 *    返回 `PLSandboxedURL`（Photos 沙盒 URL），拿 `path` 读出来是 **0 字节**（App 没有那个沙盒的
 *    读权限、也不是 security-scoped）—— 表现就是「选完图没反应」；前者由系统读好数据再交给我们。
 * 2. **必须在 iOS 侧降采样**：共享层的 `applyPickedWallpaper` 拿到位图后**直接原样用**
 *    （只算 `minScale` 做显示缩放，不重采样），Android 是靠宿主在解码阶段三级降采样压内存的。
 *    相册原图动辄 4000×3000，不降采样直接把内存顶爆。
 * 3. **解码必须回主线程**：`loadDataRepresentation…` 的 completion 在**后台队列**，
 *    而解码用的 `UIGraphics*` / `UIScreen` 是 UIKit 的图形 API（非线程安全），
 *    且共享层会写 Compose 状态 —— 后台跑会静默失败或崩。
 *
 * ⚠ 另：不能用 `loadObjectOfClass(UIImage, …)`：它要 `Class<NSItemProviderReading>` 协议对象，
 * Kotlin/Native 侧只能硬转 —— 编译期只报「This cast can never succeed」警告，**运行期直接抛**。
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
            NexioLog.e(TAG, "topViewController() 为 null，相册拉不起来")
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
                // 用户直接取消（没选任何图）
                onResult(null)
                return
            }
            provider.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
                if (data == null) {
                    NexioLog.e(
                        TAG,
                        "loadData 没拿到数据" + (error?.let { "：${it.localizedDescription}" } ?: ""),
                    )
                }
                // ⚠ 解码必须回主线程，见类 KDoc 第 3 点
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    onResult(data?.let { decodeViaUIImage(it) })
                }
            }
        }
    }

    companion object {

        /**
         * `NSData` → `ImageBitmap`：**必须先过 `UIImage`**。
         *
         * ⚠ 相册原图多是 **HEIC**（iOS 默认拍照格式），而 skia 的
         * `Image.makeFromEncoded` **不带 HEIC 解码器** —— 直接把字节喂给它会抛
         * `Failed to Image::makeFromEncoded`。`UIImage` 支持系统全部图片格式，
         * 让它解码 + 降采样 + 转 JPEG 后 skia 才认。
         *
         * 兜底保留 skia 直解：万一 `UIImage` 也解不开（如非图片字节）再试一次。
         */
        fun decodeViaUIImage(data: NSData): ImageBitmap? {
            val bytes = data.toByteArray()
            if (bytes.isEmpty()) {
                NexioLog.e(TAG, "相册返回的 NSData 长度为 0")
                return null
            }
            // 路 1：UIImage（系统全部图片格式）。类方法与构造器都试 ——
            // 不同 iOS / Kotlin 绑定版本暴露的形态不一样。
            val image = UIImage.imageWithData(data) ?: UIImage(data = data)
            if (image == null) {
                NexioLog.w(TAG, "UIImage 解不开这份数据，回退 skia 直解")
            } else {
                decodeToImageBitmap(image)?.let { return it }
                NexioLog.w(TAG, "UIImage 解出来了但转 ImageBitmap 失败，回退 skia 直解")
            }
            // 路 2：skia 直解（PNG/JPEG/WebP…，不含 HEIC）
            return decodeBytes(bytes)
        }

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
            if (data == null) {
                NexioLog.e(TAG, "UIImageJPEGRepresentation 返回 null（图可能不是 CGImage-backed）")
                null
            } else {
                decodeBytes(data.toByteArray())
            }
        } catch (e: Exception) {
            NexioLog.e(TAG, "decodeToImageBitmap 抛异常：${e.message}", e)
            null
        }

        /** 直接给编码字节（JPEG/PNG/WebP…）时：skia 解码 + 按屏幕降采样。⚠ skia 不认 HEIC。 */
        fun decodeBytes(bytes: ByteArray): ImageBitmap? = try {
            val src = Image.makeFromEncoded(bytes)
            val (targetW, targetH) = screenPixelSize()
            // cover：只缩到刚好覆盖屏幕（>1 的小图不放大），与保存侧策略同口径
            val scale = minOf(
                maxOf(
                    targetW.toFloat() / src.width.toFloat(),
                    targetH.toFloat() / src.height.toFloat()
                ),
                1f
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
        } catch (e: Exception) {
            NexioLog.e(TAG, "skia 解码失败：${e.message}", e)
            null
        }

        /**
         * 按比例把原图缩到不超过屏幕像素（`UIGraphics` 经典路径）。
         *
         * ⚠ 不用 `preparingThumbnailOfSize`：该 API（iOS 15+）在 Kotlin/Native 的 UIKit 绑定里
         * 没生成（实测 Unresolved reference）；`UIGraphics*` 是 iOS 2 起的老 API，绑定齐全且稳定。
         */
        private fun downscale(image: UIImage, maxW: Double, maxH: Double): UIImage {
            var w = 0.0
            var h = 0.0
            image.size.useContents {
                w = width
                h = height
            }
            if (w <= 0.0 || h <= 0.0) return image
            // cover：只缩到刚好覆盖 maxW×maxH（>1 的小图不放大）
            val ratio = minOf(maxOf(maxW / w, maxH / h), 1.0)
            if (ratio >= 1.0) return image
            val newW = w * ratio
            val newH = h * ratio
            UIGraphicsBeginImageContextWithOptions(CGSizeMake(newW, newH), false, 1.0)
            image.drawInRect(CGRectMake(0.0, 0.0, newW, newH))
            val scaled = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()
            return scaled ?: image
        }

        /** 屏幕像素尺寸（宽, 高），与 `IosWallpaperBackend.screenBoundsPx` 同一口径。
         *
         * 方向感知：长短边口径会把横图高度砍到屏宽，竖屏 cover 显示时放大发虚。 */
        private fun screenPixelSize(): Pair<Int, Int> {
            val bounds = UIScreen.mainScreen.bounds
            val scale = UIScreen.mainScreen.scale
            var w = 0
            var h = 0
            bounds.useContents {
                w = (size.width * scale).toInt()
                h = (size.height * scale).toInt()
            }
            return w to h
        }
    }
}
