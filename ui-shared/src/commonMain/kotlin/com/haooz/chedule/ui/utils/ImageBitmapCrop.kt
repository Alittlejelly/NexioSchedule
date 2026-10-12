package com.haooz.chedule.ui.utils

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * `ImageBitmap` 的**跨平台裁剪** —— `Bitmap.createBitmap(src, x, y, w, h)` 的替身。
 *
 * ## 为什么要有这个
 *
 * `android.graphics.Bitmap.createBitmap(...)` 的**四参裁剪重载**在 Compose 里没有跨平台
 * 等价物（`ImageBitmap` 只有 `toImageBitmap()` / `asAndroidBitmap()`，没有 crop）。
 * 二级编辑页（课程卡片展开、`TimeConfigEditScreen`）拿的都是**裁好的卡片快照**，
 * 它们用 `fillMaxWidth + ContentScale.FillWidth` 显示，传整屏快照会拉伸变形 —— 必须裁。
 *
 * ## 为什么不再走 expect/actual
 *
 * 2026-10-11 曾误判这个需求，在 `:core` 建了个 `expect class SnapshotBitmap`
 * （`820bd00`，已撤销）—— 双重错误：方向错（砍掉桌面端功能），位置也错（`:core`
 * 不依赖 Compose）。**裁剪是纯 Compose 层的绘制操作，用 `Canvas.drawImage` 重绘即可**，
 * 快捷菜单那段（`ShortcutMenu` 的卡片快照）早就在这么做了。
 *
 * @return 裁剪后的新位图；区域非法或 OOM 时返回 null（调用方按「没快照」处理，
 *   表现为展开动画少了卡片底图，不会崩）
 */
fun cropImageBitmap(
    source: ImageBitmap,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): ImageBitmap? {
    if (width <= 0 || height <= 0) return null
    if (x < 0 || y < 0) return null
    if (x + width > source.width || y + height > source.height) return null
    return runCatching {
        val dst = ImageBitmap(width, height)
        val canvas = Canvas(dst)
        // ⚠ 这是 `androidx.compose.ui.graphics.Canvas` 的 `drawImageRect`，
        //   不是 DrawScope 的 `drawImage` —— 前者是 5 参数版（src + dst 各一组），
        //   后者才有 srcOffset/srcSize/dstSize 这组命名参数。混用会报
        //   "No parameter with name 'srcOffset'"。
        canvas.drawImageRect(
            image = source,
            srcOffset = IntOffset(x, y),
            srcSize = IntSize(width, height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(width, height),
            paint = Paint(),
        )
        dst
    }.getOrNull()
}

/**
 * 按**浮点边界**（屏幕坐标 px）裁剪，越界自动夹到源图范围内。
 *
 * 卡片边界来自 `onGloballyPositioned` 的原始坐标，理论上可能超出一两个像素 ——
 * 逐处 `coerceIn` 太啰嗦，集中在这里夹一次。
 */
fun cropImageBitmapClamped(
    source: ImageBitmap,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
): ImageBitmap? {
    val x = left.toInt().coerceIn(0, (source.width - 1).coerceAtLeast(0))
    val y = top.toInt().coerceIn(0, (source.height - 1).coerceAtLeast(0))
    val w = width.toInt().coerceIn(1, source.width - x)
    val h = height.toInt().coerceIn(1, source.height - y)
    return cropImageBitmap(source, x, y, w, h)
}
