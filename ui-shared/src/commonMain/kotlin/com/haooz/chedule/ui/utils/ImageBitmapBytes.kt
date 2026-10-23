package com.haooz.chedule.ui.utils

import androidx.compose.ui.graphics.ImageBitmap

/**
 * `ImageBitmap ⇄ PNG 字节`。
 *
 * 只给「切换课表卡片快照落盘」用 —— 那是全仓唯一需要跨进程保留位图的地方：`GraphicsLayer` 的录制内容活不过组合销毁（`record{}` 里 `drawLayer(x)` 只存引用），`ImageBitmap` 活不过进程，只有文件能留到下次启动。
 *
 * ⚠ 不复用壁纸那套 `WallpaperBackend.encode`：Android 实现内部直接调 `ScheduleAppearance.saveWallpaper()`，会覆盖用户的壁纸文件，语义完全不同。
 *
 * 失败一律返回 null（调用方按「没有快照」降级），不抛。
 */
internal expect fun encodeImageBitmapPng(image: ImageBitmap): ByteArray?

internal expect fun decodeImageBitmapPng(bytes: ByteArray): ImageBitmap?
