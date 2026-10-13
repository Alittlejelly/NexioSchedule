package com.haooz.chedule.ui.utils

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * `ImageBitmap` 的**跨平台裁剪** —— `Bitmap.createBitmap(src, x, y, w, h)` 的替身。
 * 二级编辑页拿的是裁好的卡片快照，用 `fillMaxWidth + ContentScale.FillWidth` 显示，
 * 传整屏快照会拉伸变形，必须裁。
 *
 * ⚠ **Android 必须走原生裁剪**，纯 Compose 的 `drawImageRect` 重绘在 Android 上必然失败，
 * 表现是「morph 展开动画里原卡片快照永远不出现」：
 * - `GraphicsLayer.toImageBitmap()` 在 Android 上走 `Bitmap.createBitmap(Picture)`，
 *   内部固定用 `Config.HARDWARE` → 读回的是**硬件位图**；
 * - 重绘裁剪的目标是 `ImageBitmap(width, height)`（**软件**位图），Android 的
 *   `Canvas.throwIfCannotDraw` 遇「HARDWARE 源 + 软件 canvas」直接抛
 *   `IllegalArgumentException: Software rendering doesn't support hardware bitmaps`；
 * - 异常被 `runCatching { }.getOrNull()` 兜住 → 静默返回 null → 卡片底图没了。
 *   整屏快照直接 `drawImage` 那条路（快捷菜单）走**硬件** canvas，一切正常 ——
 *   所以现象只在**需要裁剪**的页面上出现，也只有 Android 端有。
 * - iOS/桌面（skiko）没有硬件位图概念，重绘那条路本来就没问题。
 *
 * 原生 `Bitmap.createBitmap(src, x, y, w, h)` **原生支持 HARDWARE 源**（内部先落到软件位图
 * 再取子集），所以 Android 侧走原生裁剪，其余平台继续重绘。
 *
 * @return 裁剪后的新位图；区域非法或 OOM 时返回 null（调用方按「没快照」处理，不会崩）。
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
    return cropImageBitmapImpl(source, x, y, width, height)
}

/** 平台裁剪实现：Android 走原生 `Bitmap.createBitmap`，其余平台重绘（见 [cropImageBitmap]）。 */
internal expect fun cropImageBitmapImpl(
    source: ImageBitmap,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): ImageBitmap?

/**
 * 重绘裁剪：新建目标位图把源区域画进去（Android 以外的平台实现）。
 *
 * ⚠ 这里是 `androidx.compose.ui.graphics.Canvas` 的 `drawImageRect`（src + dst 各一组），
 *   不是 DrawScope 的 `drawImage`；混用会报 "No parameter with name 'srcOffset'"。
 */
internal fun cropByRedraw(
    source: ImageBitmap,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): ImageBitmap? = runCatching {
    val dst = ImageBitmap(width, height)
    val canvas = Canvas(dst)
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

/**
 * 按**浮点边界**（屏幕坐标 px）裁剪，越界自动夹到源图范围内。
 * 卡片边界来自 `onGloballyPositioned` 的原始坐标，可能超出一两个像素，集中夹一次。
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
