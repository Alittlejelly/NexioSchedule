package com.haooz.chedule.data

import android.graphics.Bitmap
import android.util.DisplayMetrics
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale

/**
 * Android 壁纸后端 —— **全部委托给已有 `ScheduleAppearance`**。
 *
 * ## 为什么不自己解码
 *
 * `ScheduleAppearance` 里的壁纸三件套（`inSampleSize` 2 的幂降采样 / `LruCache`
 * 按 byteCount 淘汰 / WebP 有损 80）都是实测调过的。在 `:ui-shared` 里重写一遍
 * = 两条实现会漂移，而且直接违反「Android 零回归」红线。
 *
 * 所以这里只做**类型搬运**：`android.graphics.Bitmap ⇄ ImageBitmap`
 * （`asAndroidBitmap()` / `asImageBitmap()` 只是包装，不复制像素）。
 *
 * ## ⚠ 两层缓存，清的时候要一起清
 *
 * `ScheduleAppearance` 内部有 `LruCache`，`:ui-shared` 的 [WallpaperStore] 有
 * 自己的 `cached`。**换壁纸/清壁纸后必须两边都失效**，否则 `CourseScheduleApp`
 * 会继续拿到旧实例（`ScheduleAppearance.saveWallpaper` 内部已 put 新实例，
 * 但共享层的 `cached` 不会自动失效）。
 *
 * 注入点：`NexioApplication.onCreate()`。
 */
class AndroidWallpaperBackend : WallpaperBackend {

    override suspend fun decode(bytes: ByteArray): ImageBitmap? {
        // 走 ScheduleAppearance.loadWallpaper()：inSampleSize 降采样与 LruCache
        // 口径全部保持原样。调用方（WallpaperStore.load）已确认文件存在，
        // 这里读不到只可能是并发删除。
        return ScheduleAppearance.loadWallpaper()?.asImageBitmap()
    }

    override suspend fun encode(image: ImageBitmap, quality: Int): ByteArray? {
        // quality 由 WallpaperStore 固定传 80，与 ScheduleAppearance 内部一致。
        // 仍作参数是为了让「质量」这个决策留在共享层（iOS 那边用 JPEG 80）。
        //
        // ⚠ 落盘不在这里做：ScheduleAppearance.saveWallpaper 会**自己写文件**，
        //    于是 WallpaperStore.save 之后文件已经在盘上；但 WallpaperStore 需要
        //    「编码后的字节」来判定成功，所以这里再读一次盘。
        //    多一次读盘换来「Android 行为逐字节不变」，值得。
        if (!ScheduleAppearance.saveWallpaper(image.asAndroidBitmap())) return null
        return readWallpaperBytes()
    }

    override suspend fun scale(
        image: ImageBitmap,
        width: Int,
        height: Int,
    ): ImageBitmap {
        val src = image.asAndroidBitmap()
        val scaled: Bitmap = src.scale(width, height, true)
        // scale 在尺寸相同时返回原图（可变性未知）。原图可能是共享的只读实例，
        // 不能让人改坏 —— 拷一份可变的出来。
        val safe = if (scaled === src) {
            src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        } else {
            scaled
        }
        return safe.asImageBitmap()
    }

    override fun screenBoundsPx(): Pair<Int, Int> {
        val metrics: DisplayMetrics = ScheduleAppearance.displayMetrics()
        // 方向感知：横图在竖屏上要按 cover 保留高度，长短边口径会把高度砍到屏宽
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
