@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.haooz.chedule.data.toByteArray
import org.jetbrains.skia.Image
import platform.Foundation.NSData

/**
 * 原生层截屏桥：把**含原生 TabBar**的全屏画面交给 Compose。
 *
 * ## 为什么 Compose 自己截不到
 *
 * 转场快照走 `GraphicsLayer.record()`，它只录制 Compose 的绘制内容。iOS 上原生
 * UITabBar 是 UIKit 视图，不进 Compose 的绘制管线，`record()` 天然抓不到它——
 * 快照里底部永远缺一条。
 *
 * ## 桥的形状
 *
 * Swift 侧只负责**把结果存进来**（`store`），不直接返回 Kotlin 类型：
 * Swift 的 `Data` 与 `KotlinByteArray` 之间没有生成隐式转换，由 Kotlin 主动
 * `take()` 拉取，桥接面最小、方向单一。
 */
object IosNativeCapture {

    private var pending: NSData? = null

    /** Swift 侧注入：产出全屏截图的字节数据。 */
    var captureScreen: (() -> Unit)? = null

    /** Swift 侧调用：交回截屏结果，null 表示失败。 */
    fun store(data: NSData?) {
        pending = data
    }

    /** 取出并清空最近一次结果。 */
    fun take(): NSData? = pending.also { pending = null }

    /**
     * 截一张含原生底栏的全屏图。失败返回 null —— 调用方都能处理「没有快照」。
     *
     * 全程兜住异常：全屏 drawHierarchy 在低端机上容易 OOM，而 OOM 是 Error 不是
     * Exception，只 catch 常规异常等于没防。
     */
    fun captureScreenWithChrome(): ImageBitmap? {
        // 全程兜住异常：全屏 drawHierarchy 在低端机上容易 OOM，而 OOM 是 Error 不是
        // Exception，只 catch 常规异常等于没防。
        val bytes = try {
            captureScreen?.invoke()
            val data = take() ?: return null
            data.toByteArray()
        } catch (_: Exception) {
            return null
        } catch (_: Throwable) {
            return null
        }
        if (bytes.isEmpty()) return null

        return try {
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (_: Exception) {
            null
        } catch (_: Throwable) {
            null
        }
    }
}
