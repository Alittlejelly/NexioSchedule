package com.haooz.chedule.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.core.content.edit
import androidx.core.graphics.scale
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.haooz.chedule.data.ScheduleAppearance.FILE_STYLE_KEY
import java.io.File
import java.io.FileOutputStream

// ════════════════════════════════════════════════════════════════════════
//  课表外观（单文件全包）：壁纸 + 偏移缩放 + 卡片全部可调参数与三个枚举。
//  独立 `appearance_settings` 文件存储，不与课程数据混存，也不进全量备份。
// ════════════════════════════════════════════════════════════════════════

/** 卡片内容对齐方式 / 文字配色 / 透镜折射强度：已下沉 :core（同包名 CardTextColor.kt） */

// `ThemeMode` 已下沉 :core；本文件仅保留读写它的 getThemeMode/setThemeMode（需碰 SharedPreferences），页面经注入参数调用。

/**
 * 外观参数快照 —— 落盘唯一结构。
 *
 * ⚠ 这里的默认值即实际生效默认值；[AppearanceConfig]、`ScheduleCombination` 同名字段只在直接构造时有效，
 * 走 `fromCombination` 一律被覆盖 —— 三处默认值必须一致。
 *
 * 枚举字段声明为可空并配 safe getter：Gson UnsafeAllocator 使 Kotlin 默认值不生效。
 */
data class CombinationStyle(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    /** offset/scale 保存时的屏幕宽高（px）；0=未记录，加载时不重映射 */
    val offsetRefW: Float = 0f,
    val offsetRefH: Float = 0f,
    /** 课程卡片模糊半径（dp） */
    val cardBlur: Float = 4f,
    val cardAlpha: Float = 0.15f,
    /** 有壁纸时白/黑底不透明度；null=未设置，用默认 */
    val cardSurfaceAlpha: Float? = null,
    val cardHeight: Float = 54f,
    val cardCornerRadius: Float = 10f,
    val wallpaperBrightness: Float = 0f,
    /** null=无壁纸/未测光 */
    val wallpaperIsLight: Boolean? = null,
    val showBreakDividers: Boolean = true,
    val cardContentAlignment: CardContentAlignment? = null,
    val cardTextColor: CardTextColor? = null,
    val cardTextScale: Float = 1f,
    val showClassroom: Boolean = true,
    val showTeacher: Boolean = true,
    val cardRefraction: CardRefractionLevel? = null,
    val wallpaperBlur: Boolean = false
) {
    val safeAlignment: CardContentAlignment get() = cardContentAlignment ?: CardContentAlignment.CENTER_CENTER
    val safeTextColor: CardTextColor get() = cardTextColor ?: CardTextColor.COLORFUL
    val safeRefraction: CardRefractionLevel get() = cardRefraction ?: CardRefractionLevel.DEFAULT
    val safeCardTextScale: Float get() = if (cardTextScale > 0f) cardTextScale else 1f
    val safeCardSurfaceAlpha: Float get() = cardSurfaceAlpha?.takeIf { it in 0f..1f } ?: CARD_SURFACE_ALPHA_DEFAULT

    /** cardHeight 为 0 时网格整页静默空白（不崩无日志）；滑杆合法区间 34~92，0 视为损坏恢复默认。 */
    val safeCardHeight: Float get() = if (cardHeight > 0f) cardHeight else CARD_HEIGHT_DEFAULT

    companion object {
        /** 与自定义页滑杆默认值一致 */
        const val CARD_HEIGHT_DEFAULT = 54f
        const val CARD_SURFACE_ALPHA_DEFAULT = 0.15f

        /** 仅用于校验快照键名，不参与取值 */
        private val FIELD_NAMES = setOf(
            "offsetX", "offsetY", "scale", "offsetRefW", "offsetRefH",
            "cardBlur", "cardAlpha", "cardSurfaceAlpha",
            "cardHeight",
            "cardCornerRadius", "wallpaperBrightness", "wallpaperIsLight",
            "showBreakDividers", "cardContentAlignment", "cardTextColor",
            "cardTextScale", "showClassroom", "showTeacher", "cardRefraction",
            "wallpaperBlur"
        )

        /** 至少命中一个已知字段名才采信；键名对不上 Gson 会停在 Java 默认值（如 cardHeight=0 → 课表空白），判为不可用由调用方恢复默认。 */
        fun parseSnapshotOrNull(gson: Gson, json: String): CombinationStyle? {
            val obj = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull() ?: return null
            if (obj.keySet().none { it in FIELD_NAMES }) return null
            return runCatching { gson.fromJson(json, CombinationStyle::class.java) }.getOrNull()
        }
    }
}

/**
 * [AppearanceStyle]（共享层）⇄ [CombinationStyle]（本文件）互转。
 * 两份字段逐字同名，转换仅搬运；等 CourseScheduleApp 迁完后者可删。
 */
fun AppearanceStyle.toCombinationStyle(): CombinationStyle = CombinationStyle(
    offsetX = offsetX,
    offsetY = offsetY,
    scale = scale,
    offsetRefW = offsetRefW,
    offsetRefH = offsetRefH,
    cardBlur = cardBlur,
    cardAlpha = cardAlpha,
    cardSurfaceAlpha = cardSurfaceAlpha,
    cardHeight = cardHeight,
    cardCornerRadius = cardCornerRadius,
    wallpaperBrightness = wallpaperBrightness,
    wallpaperIsLight = wallpaperIsLight,
    showBreakDividers = showBreakDividers,
    cardContentAlignment = cardContentAlignment,
    cardTextColor = cardTextColor,
    cardTextScale = cardTextScale,
    showClassroom = showClassroom,
    showTeacher = showTeacher,
    cardRefraction = cardRefraction,
    wallpaperBlur = wallpaperBlur,
)

fun CombinationStyle.toAppearanceStyle(): AppearanceStyle = AppearanceStyle(
    offsetX = offsetX,
    offsetY = offsetY,
    scale = scale,
    offsetRefW = offsetRefW,
    offsetRefH = offsetRefH,
    cardBlur = cardBlur,
    cardAlpha = cardAlpha,
    cardSurfaceAlpha = cardSurfaceAlpha,
    cardHeight = cardHeight,
    cardCornerRadius = cardCornerRadius,
    wallpaperBrightness = wallpaperBrightness,
    wallpaperIsLight = wallpaperIsLight,
    showBreakDividers = showBreakDividers,
    cardContentAlignment = cardContentAlignment,
    cardTextColor = cardTextColor,
    cardTextScale = cardTextScale,
    showClassroom = showClassroom,
    showTeacher = showTeacher,
    cardRefraction = cardRefraction,
    wallpaperBlur = wallpaperBlur,
)

/**
 * 外观存储：单例，持有 prefs、壁纸位图缓存与全部读写方法。
 *
 * 只有一个键：[FILE_STYLE_KEY]（参数 JSON 快照）+ 一个壁纸文件。没有 id 维度。
 */
object ScheduleAppearance {

    // 这两个常量已下沉 :core（CourseRepository 判外观键要用，:core 不能反向依赖 :app）；此处 const 转发取值同源不抄错。
    /** 独立 prefs 文件名 */
    const val FILE = AppearancePrefs.FILE

    /** 唯一的数据键：参数JSON 快照 */
    const val FILE_STYLE_KEY = AppearancePrefs.FILE_STYLE_KEY

    /** 壁纸文件名（存 filesDir） */
    private const val WALLPAPER_FILE = "appearance_wallpaper.webp"

    /**
     * 供 [WallpaperStore] 复用同一落盘目录。
     * ⚠ 必须与此处 loadWallpaper/saveWallpaper 内部 `File(appContext.filesDir, …)` 逐字一致，否则存量壁纸静默丢失。
     */
    fun appFilesDir(): java.io.File = appContext.filesDir

    /** 屏幕显示指标 —— 供 [WallpaperStore] 计算壁纸目标分辨率（长边/短边）。 */
    fun displayMetrics(): android.util.DisplayMetrics = appContext.resources.displayMetrics

    private lateinit var appContext: Context
    private val gson = Gson()
    private lateinit var prefs: KeyValueStore

    /** 参数快照缓存，读多写少 */
    private var cached: CombinationStyle? = null

    /** 壁纸解码是主要瓶颈，按可用内存做 Lru 缓存。上限须装得下一张 cover 壁纸（3680×2760≈40.5MB，长边封顶 5520×2760≈61MB）；
     * 旧上限 32MB 装不下会被逐出，每次冷启动全量重解（~100ms+40MB 分配）。48MB 下限不可省：小内存机型 maxMemory/4 可能仅几 MB。 */
    private val wallpaperCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        minOf(Runtime.getRuntime().maxMemory() / 4, 96L * 1024 * 1024)
            .coerceAtLeast(48L * 1024 * 1024)
            .toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }


    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        // 走 AppStorage 按名取（Android 侧仍是同一 appearance_settings 文件）
        prefs = AppStorage.store(FILE)
        migrateLegacyAppearanceIfNeeded()
    }

    /**
     * 从旧版主 prefs（course_schedule_prefs）迁移外观数据。
     * 外观曾寄存在主 prefs，键为 combination_style_{id}（自 1.5.2 起此格式），壁纸为 combination_wallpaper_{id}.webp。
     * 不迁移老用户升级会静默丢失全部外观且不报错。兼容基线 1.5.2；更早 comb_xxx 格式不再处理。
     * 幂等：仅在新文件完全为空时执行一次，已有值一律跳过绝不覆盖。
     */
    private fun migrateLegacyAppearanceIfNeeded() {
        if (prefs.all().isNotEmpty()) return
        val legacy = runCatching {
            AppStorage.store(LEGACY_PREFS)
        }.getOrNull() ?: return

        // 迁「当前正在用的那套」：1.5.6 及更早有 current_combination_id 指针，只取首个会在多搭配数据上迁错。
        val legacyIds = legacy.getStringOrNull(LEGACY_KEY_COMBINATION_IDS)
            ?.split(",")?.mapNotNull { it.trim().toLongOrNull() }
            ?.takeIf { it.isNotEmpty() }
        val currentId = runCatching {
            legacy.getLong(LEGACY_KEY_CURRENT_COMBINATION_ID, Long.MIN_VALUE)
        }.getOrDefault(Long.MIN_VALUE)
        val legacyId = when {
            legacyIds == null -> 0L
            currentId in legacyIds -> currentId
            else -> legacyIds.first()
        }

        // 快照坏掉（键名对不上/JSON 损坏）时不迁，交给 getStyle() 按壁纸重测光，避免把默认值当用户设置落盘。
        val style = legacy.getStringOrNull("$LEGACY_STYLE_PREFIX$legacyId")
            ?.let { CombinationStyle.parseSnapshotOrNull(gson, it) }
        if (style != null) {
            prefs.edit { putString(FILE_STYLE_KEY, gson.toJson(style)) }
            cached = style
        }
        migrateLegacyWallpaperFile(legacyId)
    }

    /** 壁纸文件改名迁移：webp 为主，旧 png 兜底（1.5.2 前后都写过 png） */
    private fun migrateLegacyWallpaperFile(legacyId: Long) {
        val target = File(appContext.filesDir, WALLPAPER_FILE)
        if (target.exists()) return
        val candidates = listOf(
            File(appContext.filesDir, WALLPAPER_FILE),
            File(appContext.filesDir, "$LEGACY_WALLPAPER_PREFIX$legacyId.webp"),
            File(appContext.filesDir, "$LEGACY_WALLPAPER_PREFIX$legacyId.png")
        )
        candidates.firstOrNull { it.exists() }?.let { src ->
            runCatching { src.copyTo(target, overwrite = true) }
        }
    }

    // ── 旧版存储位置（仅迁移期使用）──────────────────────────
    private const val LEGACY_PREFS = "course_schedule_prefs"
    private const val LEGACY_STYLE_PREFIX = "combination_style_"
    private const val LEGACY_WALLPAPER_PREFIX = "combination_wallpaper_"
    private const val LEGACY_KEY_COMBINATION_IDS = "combination_ids"
    private const val LEGACY_KEY_CURRENT_COMBINATION_ID = "current_combination_id"

    /** 批量保存外观参数，块内写入合并为一次提交（可嵌套）。委托 [AppearanceStore.batchSave]，合并逻辑在共享层。 */
    fun batchSave(block: () -> Unit) = AppearanceStore.batchSave(block)

    // ── 参数快照 ────────────────────────────────────────────

    /** 坏快照重置后用现有壁纸重测光，否则主题开关因 isLight=null 整条失效 */
    private fun restoreAfterBadSnapshot(): CombinationStyle {
        val isLight = computeWallpaperIsLight(loadWallpaper())
        val style = CombinationStyle(wallpaperIsLight = isLight)
        saveStyle(style)
        return style
    }

    /** 当前外观快照（委托 [AppearanceStore]）。返回既有 [CombinationStyle]，内部由共享层 [AppearanceStyle] 转来，字段逐字同名仅搬运。 */
    fun getStyle(): CombinationStyle {
        cached?.let { return it }
        val style = AppearanceStore.currentStyle()
            .let { s -> restoreAfterBadSnapshotIfNeeded(s) }
            .toCombinationStyle()
        cached = style
        return style
    }

    /** 快照坏掉（键名对不上）时用现有壁纸重测光后重存，避免主题开关整条失效 */
    private fun restoreAfterBadSnapshotIfNeeded(style: AppearanceStyle): AppearanceStyle {
        // AppearanceStore 解析失败回落默认值；用「盘上是否写过」区分真默认与坏快照被兜住，仅后者需重测光重存。
        val raw = runCatching { prefs.getStringOrNull(FILE_STYLE_KEY) }.getOrNull()
        if (raw != null && style == AppearanceStyle.defaults() &&
            AppearanceStyleCodec.decodeOrNull(raw) == null
        ) {
            val isLight = computeWallpaperIsLight(loadWallpaper())
            val fixed = style.copy(wallpaperIsLight = isLight)
            AppearanceStore.saveStyle(fixed)
            return fixed
        }
        return style
    }

    /** 覆盖写入快照（委托 [AppearanceStore]）。改为转 AppearanceStyle 后由 AppearanceStyleCodec 编码；字段逐字同名、JSON 键名不变，保证存量设置可读。 */
    fun saveStyle(style: CombinationStyle) {
        AppearanceStore.saveStyle(style.toAppearanceStyle())
        cached = style
    }

    /** 在现有快照基础上做一次变更并落盘 */
    fun updateStyle(transform: (CombinationStyle) -> CombinationStyle) {
        saveStyle(transform(getStyle()))
    }

    // ── 壁纸变换 ────────────────────────────────────────────

    fun getOffsetX(): Float = getStyle().offsetX
    fun getOffsetY(): Float = getStyle().offsetY
    fun getScale(): Float = getStyle().scale
    fun getOffsetRefW(): Float = getStyle().offsetRefW
    fun getOffsetRefH(): Float = getStyle().offsetRefH

    fun saveState(offsetX: Float, offsetY: Float, scale: Float, refW: Float = 0f, refH: Float = 0f) =
        updateStyle { it.copy(offsetX = offsetX, offsetY = offsetY, scale = scale, offsetRefW = refW, offsetRefH = refH) }

    // ── 卡片参数 ────────────────────────────────────────────

    fun saveCardBlur(radius: Float) = updateStyle { it.copy(cardBlur = radius) }
    fun getCardBlur(): Float = getStyle().cardBlur

    fun saveCardAlpha(alpha: Float) = updateStyle { it.copy(cardAlpha = alpha) }
    fun getCardAlpha(): Float = getStyle().cardAlpha

    fun saveCardSurfaceAlpha(alpha: Float) = updateStyle { it.copy(cardSurfaceAlpha = alpha) }
    fun getCardSurfaceAlpha(): Float = getStyle().safeCardSurfaceAlpha

    fun saveCardHeight(height: Float) = updateStyle { it.copy(cardHeight = height) }
    fun getCardHeight(): Float = getStyle().safeCardHeight

    fun saveCardCornerRadius(radius: Float) = updateStyle { it.copy(cardCornerRadius = radius) }
    fun getCardCornerRadius(): Float = getStyle().cardCornerRadius

    fun saveWallpaperBrightness(brightness: Float) = updateStyle { it.copy(wallpaperBrightness = brightness) }
    fun getWallpaperBrightness(): Float = getStyle().wallpaperBrightness

    fun saveWallpaperIsLight(isLight: Boolean?) = updateStyle { it.copy(wallpaperIsLight = isLight) }

    fun getWallpaperIsLight(): Boolean? {
        getStyle().wallpaperIsLight?.let { return it }
        // 有壁纸但测光结果丢失时兜底重算
        val isLight = computeWallpaperIsLight(loadWallpaper()) ?: return null
        updateStyle { it.copy(wallpaperIsLight = isLight) }
        return isLight
    }

    fun saveShowBreakDividers(show: Boolean) = updateStyle { it.copy(showBreakDividers = show) }
    fun getShowBreakDividers(): Boolean = getStyle().showBreakDividers

    fun saveCardContentAlignment(alignment: CardContentAlignment) =
        updateStyle { it.copy(cardContentAlignment = alignment) }
    fun getCardContentAlignment(): CardContentAlignment = getStyle().safeAlignment

    fun saveCardTextColor(color: CardTextColor) = updateStyle { it.copy(cardTextColor = color) }
    fun getCardTextColor(): CardTextColor = getStyle().safeTextColor

    fun saveCardTextScale(scale: Float) = updateStyle { it.copy(cardTextScale = scale) }
    fun getCardTextScale(): Float = getStyle().safeCardTextScale

    fun saveShowClassroom(show: Boolean) = updateStyle { it.copy(showClassroom = show) }
    fun getShowClassroom(): Boolean = getStyle().showClassroom

    fun saveShowTeacher(show: Boolean) = updateStyle { it.copy(showTeacher = show) }
    fun getShowTeacher(): Boolean = getStyle().showTeacher

    fun saveCardRefraction(level: CardRefractionLevel) = updateStyle { it.copy(cardRefraction = level) }
    fun getCardRefraction(): CardRefractionLevel = getStyle().safeRefraction

    fun saveWallpaperBlur(blur: Boolean) = updateStyle { it.copy(wallpaperBlur = blur) }
    fun getWallpaperBlur(): Boolean = getStyle().wallpaperBlur

    // ⚠ 下课烟花开关不在此文件，真实读写方是 `ClassEndEffectSettings`（`app_preferences` 的 `class_end_fireworks`）。
    // 曾在此重复定义同键属性却零调用点（读到默认值），已删，勿再加回。

    // ── 默认主题（课表外观自定义页的下拉）────────────────────

    /**
     * 课表页/今日页跟随主题。
     * ⚠ 存 `app_theme_prefs` 而非本文件：与全局主题共用文件但不同键，且 `Theme.kt`/`WidgetTextSizes`
     * 需在首帧读取；放进外观文件会让这些组件多依赖一文件，而首帧主题计算不能碰 IO 大的文件。
     */
    private val themePrefs: KeyValueStore
        get() = AppStorage.store(FILE_THEME)

    fun getThemeMode(): ThemeMode =
        ThemeMode.fromPrefsValue(themePrefs.getStringOrNull(ThemeMode.SCHEDULE_THEME_MODE_KEY))

    fun setThemeMode(mode: ThemeMode) {
        themePrefs.edit { putString(ThemeMode.SCHEDULE_THEME_MODE_KEY, mode.prefsValue) }
    }

    /** 与全局应用主题共用的 prefs 文件名 */
    const val FILE_THEME = "app_theme_prefs"

    // ── 壁纸位图 ────────────────────────────────────────────

    /** 壁纸目标存储/解码分辨率：当前屏幕 (宽, 高)，方向感知。
     * ⚠ 曾用长短边规避横竖屏漂移，代价是横图在竖屏被按短边砍高（4000×3000→1674×1256）cover 放大 2.2 倍 = 「选完壁纸很糊」根因。
     * 方向感知后 inSampleSize 对两方向只按 cover 计算（只缩不扩），不反向降质。 */
    private fun wallpaperTargetBounds(): Pair<Int, Int> {
        val metrics = appContext.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    /**
     * 保存前缩放策略：cover 到屏幕，只缩不扩，长边封顶 2×屏幕长边。
     * ⚠ 必须与 [WallpaperBackend.saveTargetSize] 逐条一致：共享层 save 先缩一次、encode 再判一次，
     * 策略须幂等（已处理图返回原尺寸）才不二次丢像素。
     */
    private fun scaleWallpaperForSave(bitmap: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (bitmap.width <= 0 || bitmap.height <= 0) return bitmap
        val cover = maxOf(
            targetW / bitmap.width.toFloat(),
            targetH / bitmap.height.toFloat()
        )
        // cover > 1：源图比屏幕小（短边方向覆盖不足），原样保存，显示侧放大是唯一选择
        if (cover > 1f) return bitmap
        val maxLong = 2 * maxOf(targetW, targetH)
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= maxLong) return bitmap
        val s = maxLong / longSide.toFloat()
        return bitmap.scale(
            (bitmap.width * s).toInt().coerceAtLeast(1),
            (bitmap.height * s).toInt().coerceAtLeast(1),
            true
        )
    }

    /** 计算满足目标尺寸的 2 的幂次降采样倍数（inSampleSize） */
    private fun calculateInSampleSize(bounds: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (bounds.outHeight > reqHeight || bounds.outWidth > reqWidth) {
            val halfHeight = bounds.outHeight / 2
            val halfWidth = bounds.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /** 16×16 网格感知加权平均亮度；无壁纸返回 null */
    private fun computeWallpaperIsLight(bitmap: Bitmap?): Boolean? {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
        val gridW = 16
        val gridH = 16
        val small = bitmap.scale(gridW, gridH)
        var sum = 0.0
        for (x in 0 until gridW) {
            for (y in 0 until gridH) {
                val c = small.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                sum += 0.299 * r + 0.587 * g + 0.114 * b
            }
        }
        val avg = sum / (gridW * gridH)
        return avg >= 128
    }

    /** 缩放到屏幕分辨率后以 WebP 有损 80 存储 */
    fun saveWallpaper(bitmap: Bitmap): Boolean {
        return try {
            val (targetW, targetH) = wallpaperTargetBounds()
            val scaled = scaleWallpaperForSave(bitmap, targetW, targetH)
            val file = File(appContext.filesDir, WALLPAPER_FILE)
            // API 30 起 WEBP 被 WEBP_LOSSY 取代，语义等价（均有损）
            val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                CompressFormat.WEBP
            }
            FileOutputStream(file).use { out -> scaled.compress(webpFormat, 80, out) }
            wallpaperCache.put(WALLPAPER_FILE, scaled)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 删磁盘文件并清缓存，实现持久化清除 */
    fun clearWallpaper() {
        wallpaperCache.remove(WALLPAPER_FILE)
        File(appContext.filesDir, WALLPAPER_FILE).delete()
    }

    /** 带 Lru 缓存；解码按屏幕分辨率降采样 */
    fun loadWallpaper(): Bitmap? {
        wallpaperCache.get(WALLPAPER_FILE)?.let { return it }
        val file = File(appContext.filesDir, WALLPAPER_FILE)
        if (!file.exists()) return null
        return try {
            val (targetW, targetH) = wallpaperTargetBounds()
            // 先读尺寸再降采样，避免超大图一次性解码耗尽内存
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            opts.inSampleSize = calculateInSampleSize(opts, targetW, targetH)
            opts.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.absolutePath, opts)?.also {
                wallpaperCache.put(WALLPAPER_FILE, it)
            }
        } catch (_: Exception) {
            null
        }
    }
}