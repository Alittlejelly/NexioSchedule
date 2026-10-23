package com.haooz.chedule.ui.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

internal actual fun encodeImageBitmapPng(image: ImageBitmap): ByteArray? = runCatching {
    val src = image.asAndroidBitmap()
    // Android 上 toImageBitmap()/原生裁剪产出 HARDWARE 位图，compress() 读不到像素，先 copy 成 ARGB_8888
    val software = if (src.config == Bitmap.Config.HARDWARE) {
        src.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        src
    }
    ByteArrayOutputStream().use { out ->
        if (software.compress(Bitmap.CompressFormat.PNG, 100, out)) out.toByteArray() else null
    }
}.getOrNull()

internal actual fun decodeImageBitmapPng(bytes: ByteArray): ImageBitmap? = runCatching {
    if (bytes.isEmpty()) return null
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()
