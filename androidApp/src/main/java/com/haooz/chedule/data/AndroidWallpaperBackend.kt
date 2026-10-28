package com.haooz.chedule.data

import android.graphics.Bitmap
import android.util.DisplayMetrics
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale

/**
 * Android 壁纸后端，全部委托 `ScheduleAppearance`（降采样/LruCache/WebP 80 已实测，
 * 重写会漂移并违反「Android 零回归」红线）。本类只做 Bitmap⇄ImageBitmap 类型搬运（不复制像素）。
 *
 * ⚠ 两层缓存必须一起清：`ScheduleAppearance` 内部 LruCache 与 [WallpaperStore] 自身缓存。
 * 换/清壁纸后不双失效，共享层会继续拿到旧实例。注入点：`NexioApplication.onCreate()`。
 */
class AndroidWallpaperBackend : WallpaperBackend {

    override suspend fun decode(bytes: ByteArray): ImageBitmap? {
        // 走 ScheduleAppearance.loadWallpaper()，降采样与 LruCache 口径保持原样。调用方已确认文件存在，读不到只可能是并发删除。
        return ScheduleAppearance.loadWallpaper()?.asImageBitmap()
    }

    /**
     * 同步取壁纸，供首帧使用（注入到 `WallpaperStore.synchronousLoader`）。
     *
     * 与 [decode] 同一条链路（降采样 + LruCache），只是不走协程：首帧前必须拿到，
     * 否则首帧只有底色，等异步加载完再跳成用户设定。
     */
    fun loadNow(): ImageBitmap? = ScheduleAppearance.loadWallpaper()?.asImageBitmap()

    override suspend fun encode(image: ImageBitmap, quality: Int): ByteArray? {
        // quality 由 WallpaperStore 固定传 80（与内部一致），保留参数让质量决策留在共享层（iOS 用 JPEG 80）。
        // ⚠ 落盘由 ScheduleAppearance.saveWallpaper 自己完成；这里再读一次盘取回字节供 WallpaperStore 判定成功，
        //    多一次读盘换取「Android 行为逐字节不变」。
        if (!ScheduleAppearance.saveWallpaper(image.asAndroidBitmap())) return null
        return readWallpaperBytes()
    }

    /**
     * 纯解码给定字节。
     * ⚠ 不能落到 [decode]：那是「读 App 壁纸文件」，会把小部件壁纸读成课表壁纸。
     */
    override suspend fun decodeBytes(bytes: ByteArray): ImageBitmap? = try {
        android.graphics.BitmapFactory
            .decodeByteArray(bytes, 0, bytes.size)
            ?.asImageBitmap()
    } catch (_: Exception) {
        null
    }

    /**
     * 纯编码，不落盘、不碰 LruCache。
     * ⚠ 不能落到 [encode]：那会调 `ScheduleAppearance.saveWallpaper`，把用户的课表壁纸
     * （以及外观页「跟随壁纸」的深浅色）覆盖成小部件壁纸。
     */
    override suspend fun encodeBytes(image: ImageBitmap, quality: Int): ByteArray? = try {
        val out = java.io.ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        val ok = image.asAndroidBitmap().compress(format, quality.coerceIn(1, 100), out)
        if (ok) out.toByteArray() else null
    } catch (_: Exception) {
        null
    }

    override suspend fun scale(
        image: ImageBitmap,
        width: Int,
        height: Int,
    ): ImageBitmap {
        val src = image.asAndroidBitmap()
        val scaled: Bitmap = src.scale(width, height, true)
        // scale 尺寸相同可能返回原图（共享只读实例，可变性未知），拷一份可变的避免被改坏。
        val safe = if (scaled === src) {
            src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        } else {
            scaled
        }
        return safe.asImageBitmap()
    }

    override fun screenBoundsPx(): Pair<Int, Int> {
        val metrics: DisplayMetrics = ScheduleAppearance.displayMetrics()
        // 方向感知：横图在竖屏需按 cover 保留高度，长短边口径会把高度砍到屏宽。
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun readWallpaperBytes(): ByteArray? = try {
        java.io.File(
            ScheduleAppearance.appFilesDir(),
            WallpaperStore.FILE_NAME,
        ).readBytes()
    } catch (_: Exception) {
        null
    }
}
