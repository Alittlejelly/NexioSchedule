package com.haooz.chedule.ui.utils

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 桌面：不落盘 —— 与壁纸后端在桌面未注入（[com.haooz.chedule.data.WallpaperStore.backend]
 * 为 null，「读不到盘」）同一降级口径：切换课表的卡片快照退化成**会话内**复用。
 */
internal actual fun encodeImageBitmapPng(image: ImageBitmap): ByteArray? = null

internal actual fun decodeImageBitmapPng(bytes: ByteArray): ImageBitmap? = null
