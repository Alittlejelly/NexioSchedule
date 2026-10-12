package com.haooz.chedule.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.haooz.chedule.data.AppFiles

/**
 * 壁纸的后端能力 —— 平台实现方需要提供的四件事。
 *
 * 走接口 + 启动注入而不是 expect/actual：Android 实现（`ScheduleAppearance`）在
 * `:androidApp`，`:ui-shared` 反向依赖不到它，写 actual 就得在共享层重写一遍解码。
 * Android 由 `NexioApplication.onCreate()` 注入 `AndroidWallpaperBackend`；
 * iOS 默认用 [IosWallpaperBackend]。
 */
interface WallpaperBackend {
    /** 解码壁纸字节为 [ImageBitmap]；失败返回 null（不抛）。 */
    suspend fun decode(bytes: ByteArray): ImageBitmap?

    /** 编码 [ImageBitmap] 为字节（quality 0~100）；失败返回 null。落盘由调用方负责。 */
    suspend fun encode(image: ImageBitmap, quality: Int): ByteArray?

    /** 缩放到指定像素尺寸（宽高比由调用方算好后传入）。 */
    suspend fun scale(image: ImageBitmap, width: Int, height: Int): ImageBitmap

    /** 壁纸的目标存储/解码分辨率，返回 `(长边, 短边)` 单位 px，与横竖屏无关。 */
    fun screenBoundsPx(): Pair<Int, Int>
}

/**
 * 壁纸存储。
 *
 * ⚠ 存储路径是数据兼容契约：文件名与落盘根目录（[AppFiles.root]）逐字不能改，
 * 改了等于存量用户的壁纸静默丢失。后缀 `.webp` 只是名字 —— 解码都按内容识别格式，
 * 不看扩展名。
 */
object WallpaperStore {

    /** 壁纸文件名，与存量用户的数据兼容绑定，别改。 */
    const val FILE_NAME = "appearance_wallpaper.webp"

    /** 测光网格边长（16×16）。 */
    private const val GRID = 16

    /** 平台后端。未注入时（桌面/测试）退化成「读不到盘」，表现为没有壁纸。 */
    var backend: WallpaperBackend? = null

    /**
     * 内存缓存。Android 侧真正的主缓存是 `ScheduleAppearance` 的 `LruCache`，
     * 这里只是「避免同一次会话里重复读盘」的兜底，单例语义不做 LRU ——
     * 所以改壁纸必须两边都清。
     */
    private var cached: ImageBitmap? = null

    // 变换存在这里而不是 Activity 的 companion：横竖屏切换、进程重建后
    // 都要靠它把壁纸摆回用户上次调的位置，而它是纯数据，没有平台成分。

    /** 重映射到当前屏幕后的壁纸 offset（px）。与 [transformScale] 成对。 */
    var wallpaperOffset: Offset = Offset.Zero
        private set

    /** 重映射到当前屏幕后的壁纸 scale（相对 ContentScale.Fit）。 */
    var transformScale: Float = 1f
        private set

    /**
     * 写入当前生效的壁纸变换。
     *
     * ⚠ 只在「已经 remap 到当前屏幕」之后调，否则旋转一圈后画面会漂。
     */
    fun saveTransform(offset: Offset, scale: Float) {
        wallpaperOffset = offset
        transformScale = scale
    }

    /**
     * 把壁纸变换从 `fromW×fromH` 屏幕重映射到 `toW×toH`，并**记住结果**。
     *
     * @return 重映射后的 (offset, scale)；没有壁纸或尺寸非法时返回当前值
     */
    fun remapTransform(
        fromW: Float,
        fromH: Float,
        toW: Float,
        toH: Float,
    ): Pair<Offset, Float> {
        val bitmap = cached ?: return wallpaperOffset to transformScale
        val (newOffset, newScale) = WallpaperTransform.remap(
            offset = wallpaperOffset,
            scale = transformScale,
            bitmapW = bitmap.width,
            bitmapH = bitmap.height,
            fromW = fromW,
            fromH = fromH,
            toW = toW,
            toH = toH,
        )
        wallpaperOffset = newOffset
        transformScale = newScale
        return newOffset to newScale
    }

    /** 取当前生效壁纸；没有返回 null。命中缓存则不解码、不读盘。 */
    suspend fun load(): ImageBitmap? {
        cached?.let { return it }
        val be = backend ?: return null
        val file = AppFiles.root.resolve(FILE_NAME)
        if (!file.exists()) return null
        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return null
        }
        val decoded = be.decode(bytes) ?: return null
        cached = decoded
        return decoded
    }

    /**
     * 保存壁纸（缩放到屏幕分辨率后编码落盘），成功返回 true。
     *
     * @param image 原图；超过屏幕长短边时先等比缩小再编码
     */
    suspend fun save(image: ImageBitmap): Boolean {
        val be = backend ?: return false
        return try {
            val (targetLong, targetShort) = be.screenBoundsPx()
            val longSide = maxOf(image.width, image.height)
            val shortSide = minOf(image.width, image.height)
            val scaled = if (longSide > targetLong || shortSide > targetShort) {
                val scale = minOf(
                    targetLong / longSide.toFloat(),
                    targetShort / shortSide.toFloat(),
                )
                be.scale(
                    image = image,
                    width = (image.width * scale).toInt().coerceAtLeast(1),
                    height = (image.height * scale).toInt().coerceAtLeast(1),
                )
            } else {
                image
            }
            val bytes = be.encode(scaled, quality = 80) ?: return false
            AppFiles.root.resolve(FILE_NAME).writeBytes(bytes)
            cached = scaled
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 删磁盘文件并清内存缓存（持久化清除）。 */
    fun clear() {
        cached = null
        try {
            AppFiles.root.resolve(FILE_NAME).delete()
        } catch (_: Exception) {
            // 删除失败不影响内存态已清；下次 load 会再试
        }
    }

    /**
     * 当前内存里的壁纸实例（不解码、不读盘）。
     *
     * 给宿主层做身份比较用 —— 靠 `===` 判断该不该回收，换实例会误回收正在显示的位图。
     */
    fun currentImageBitmap(): ImageBitmap? = cached

    /** 宿主层直接替换缓存实例（如 Android 选完新壁纸、或外部改过盘上文件后）。 */
    fun setCached(image: ImageBitmap?) {
        cached = image
    }

    /**
     * 16×16 网格**感知加权平均亮度**（0.299R + 0.587G + 0.114B），阈值 128。
     * 与 Android 侧一致 —— 阈值改了会让「跟随壁纸」的主题在临界图上翻转。
     *
     * @return null = 无壁纸或尺寸非法（调用方按「未测光」处理）
     */
    fun measureLightness(image: ImageBitmap?): Boolean? {
        if (image == null || image.width <= 0 || image.height <= 0) return null
        return try {
            val pixels = image.toPixelMap()
            var sum = 0.0
            for (x in 0 until GRID) {
                for (y in 0 until GRID) {
                    // 网格中心点采样，等比映射到原图坐标
                    val px = ((x + 0.5f) / GRID * image.width).toInt()
                        .coerceIn(0, image.width - 1)
                    val py = ((y + 0.5f) / GRID * image.height).toInt()
                        .coerceIn(0, image.height - 1)
                    // PixelMap 返回 Color（分量 0f~1f），不是打包的 Int
                    val c = pixels[px, py]
                    val r = c.red * 255f
                    val g = c.green * 255f
                    val b = c.blue * 255f
                    sum += 0.299 * r + 0.587 * g + 0.114 * b
                }
            }
            (sum / (GRID * GRID)) >= 128
        } catch (_: Exception) {
            null
        }
    }
}
