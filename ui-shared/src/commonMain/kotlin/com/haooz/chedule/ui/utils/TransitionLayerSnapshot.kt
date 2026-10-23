package com.haooz.chedule.ui.utils

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale

/**
 * A retained drawing layer, optionally restricted to a region in its local pixel coordinates.
 *
 * 这里用 `GraphicsLayer` + `clipRect` 做裁剪，**不分配位图**。早期实现走的是
 * `ImageBitmap` 裁剪（`Bitmap.createBitmap(src,x,y,w,h)` 的跨平台替身），已删除 ——
 * 它在 Android 上有个绕不开的坑，值得记下来：
 *
 * - `GraphicsLayer.toImageBitmap()` 在 Android 走 `Bitmap.createBitmap(Picture)`，
 *   内部固定 `Config.HARDWARE` → 读回的是**硬件位图**；
 * - 目标却是 `ImageBitmap(w,h)` 这种**软件**位图，`Canvas.throwIfCannotDraw` 遇
 *   「HARDWARE 源 + 软件canvas」直接抛
 *   `IllegalArgumentException: Software rendering doesn't support hardware bitmaps`；
 * - 异常被 `runCatching { }.getOrNull()` 兜住 → 静默返回 null → 卡片底图凭空消失。
 *   整屏不裁剪的那条路（快捷菜单）走**硬件** canvas 一切正常，所以现象只在
 *   **需要裁剪**的页面出现，且只有 Android 端有。
 * - 顺带：Skiko 上 `drawLayer` 是重放录制命令而非光栅化，零分配 —— 这正是本实现
 *   在 iOS/桌面也不掉帧的原因（对比：承载静止快照必须落成 ImageBitmap）。
 */
data class TransitionLayerSnapshot(
    val layer: GraphicsLayer,
    val bounds: Rect? = null,
    /**
     * 源图层的光栅化副本（**整层尺寸，不预裁剪**；裁剪仍按 [bounds] 在绘制期做）。
     *
     * `drawLayer` 是**活引用**而不是画面：源图层（以及它内部再引用的图层）被隐藏、
     * 回收或重录，重放出来就跟着变。今日页进详情时源行被 `hiddenCourseIds` 藏起来，
     * 于是「进入时有内容、退出时只剩玻璃背景」—— 退出那次重放读的是被藏之后的源。
     * 有这份副本就与源图层彻底脱钩，进出看到同一张图。
     *
     * ⚠ Android 上 `toImageBitmap()` 给的是**硬件位图**，只用于上屏绘制，
     *   不要再喂软件 canvas（原因见上面那段注释）。
     */
    val frozen: ImageBitmap? = null,
)

/** Image layout and scaling semantics without allocating or rasterizing a bitmap. */
@Composable
fun TransitionLayerImage(
    snapshot: TransitionLayerSnapshot,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.TopCenter,
) {
    val painter = remember(snapshot) {
        object : Painter() {
            private val sourceBounds: Rect
                get() = snapshot.bounds ?: Rect(
                    0f,
                    0f,
                    snapshot.layer.size.width.toFloat(),
                    snapshot.layer.size.height.toFloat(),
                )

            override val intrinsicSize: Size
                get() = sourceBounds.size.takeIf { it.width > 0f && it.height > 0f }
                    ?: Size.Unspecified

            override fun DrawScope.onDraw() {
                val source = sourceBounds
                if (source.width <= 0f || source.height <= 0f) return
                clipRect {
                    scale(size.width / source.width, size.height / source.height, pivot = Offset.Zero) {
                        translate(-source.left, -source.top) {
                            // 有光栅化副本就画它：不依赖源图层的存活状态
                            val frozen = snapshot.frozen
                            if (frozen != null) drawImage(frozen) else drawLayer(snapshot.layer)
                        }
                    }
                }
            }
        }
    }
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier.clipToBounds(),
        contentScale = contentScale,
        alignment = alignment,
    )
}
