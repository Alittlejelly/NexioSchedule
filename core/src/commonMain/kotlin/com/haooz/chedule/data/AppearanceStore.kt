package com.haooz.chedule.data

import kotlin.concurrent.Volatile

/**
 * 外观存储 —— **跨平台**。
 *
 * ## 为什么要这一层
 *
 * 原实现 `ScheduleAppearance` 整个在 `:androidApp`：依赖 `Context`（拿
 * `SharedPreferences` / `filesDir`）+ Gson（运行时反射，Native 上不存在）。
 * `CourseScheduleApp` 直接调它 → 整个函数搬不进共享模块。
 *
 * ## 与 `ScheduleAppearance` 的分工
 *
 * - **本类**：跨平台的读写逻辑（键名、快照编解码、缓存口径）
 * - **`ScheduleAppearance`**：降级成 Android 侧的**实现体 + 兼容迁移**
 *   （`migrateLegacyAppearanceIfNeeded` 读旧版主 prefs、壁纸文件改名迁移），
 *   外加 `init(context)` 这个平台入口
 *
 * 两边的 getter/setter 名字**逐字相同**（这是有意的：调用点可以零改动切换）。
 *
 * ## ⚠ 键名与文件位置是数据兼容契约
 *
 * `FILE` / `FILE_STYLE_KEY` 已下沉 `:core` 的 [AppearancePrefs]，两边同源不会抄错。
 * 改了等于老用户的外观设置读不出来。
 */
object AppearanceStore {

    /** 独立 prefs 文件名（与 [AppearancePrefs.FILE] 同值） */
    private const val FILE = AppearancePrefs.FILE

    /** 唯一的数据键：参数 JSON 快照 */
    private const val FILE_STYLE_KEY = AppearancePrefs.FILE_STYLE_KEY

    /** 快照缓存，读多写少（口径与原 ScheduleAppearance.cached 一致） */
    private var cached: AppearanceStyle? = null

    /**
     * 冷启动预加载的外观参数兜底缓存（承接原 `MainActivity.cachedAppearance`）。
     *
     * 宿主在启动时把盘上的外观参数读成 [AppearanceConfig] 回填到这里；
     * `CourseScheduleApp` 首帧若搭配尚未加载完成，用它兜底 —— 否则壁纸
     * 测光/卡片参数会先闪一帧默认值再跳到真实值。
     * 仅内存，不落盘；键名/落盘格式不受影响。
     */
    @Volatile
    var cachedConfig: AppearanceConfig = AppearanceConfig()

    /** 是否处于 [batchSave] 块内 */
    private var inBatch = false

    /** 批量期间是否有过写入（决定块末要不要落盘） */
    private var batchDirty = false

    private fun store(): KeyValueStore = AppStorage.store(FILE)

    /** 当前生效的外观快照。盘上没有 / 解析失败时回落默认值。 */
    fun currentStyle(): AppearanceStyle {
        cached?.let { return it }
        val raw = runCatching { store().getStringOrNull(FILE_STYLE_KEY) }.getOrNull()
        val style = raw?.let { AppearanceStyleCodec.decodeOrNull(it) } ?: AppearanceStyle.defaults()
        cached = style
        return style
    }

    /** 覆盖写入快照。批量期间只更新内存，等块末一次性落盘。 */
    fun saveStyle(style: AppearanceStyle) {
        cached = style
        if (inBatch) {
            batchDirty = true
            return
        }
        flush()
    }

    /** 在现有快照基础上做一次变更并落盘 */
    fun updateStyle(transform: (AppearanceStyle) -> AppearanceStyle) {
        saveStyle(transform(currentStyle()))
    }

    /**
     * 批量保存：块内所有写入合并为**一次**磁盘提交。可嵌套（内层直接执行，
     * 由最外层统一落盘）。
     *
     * 语义与原 `ScheduleAppearance.batchSave` 一致 —— 外观自定义页一次拖动会触发
     * 十几次 saveXxx，不合并就是十几次磁盘 IO。
     */
    fun batchSave(block: () -> Unit) {
        if (inBatch) {
            block()
            return
        }
        inBatch = true
        batchDirty = false
        try {
            block()
        } finally {
            inBatch = false
            val dirty = batchDirty
            batchDirty = false
            if (dirty) flush()
        }
    }

    /** 把内存里的快照写到盘上。 */
    private fun flush() {
        val style = cached ?: return
        store().edit { putString(FILE_STYLE_KEY, AppearanceStyleCodec.encode(style)) }
    }

    /** 让缓存失效（盘上数据被别处改过时用）。 */
    fun invalidate() {
        cached = null
    }

    // ── 便捷读写（与 ScheduleAppearance 同名，便于调用点零改动切换） ──

    fun getOffsetX(): Float = currentStyle().offsetX
    fun getOffsetY(): Float = currentStyle().offsetY
    fun getScale(): Float = currentStyle().scale
    fun getOffsetRefW(): Float = currentStyle().offsetRefW
    fun getOffsetRefH(): Float = currentStyle().offsetRefH

    fun saveState(offsetX: Float, offsetY: Float, scale: Float, refW: Float = 0f, refH: Float = 0f) =
        updateStyle { it.copy(offsetX = offsetX, offsetY = offsetY, scale = scale, offsetRefW = refW, offsetRefH = refH) }

    fun getCardBlur(): Float = currentStyle().cardBlur
    fun saveCardBlur(radius: Float) = updateStyle { it.copy(cardBlur = radius) }

    fun getCardAlpha(): Float = currentStyle().cardAlpha
    fun saveCardAlpha(alpha: Float) = updateStyle { it.copy(cardAlpha = alpha) }

    fun getCardSurfaceAlpha(): Float = currentStyle().safeCardSurfaceAlpha
    fun saveCardSurfaceAlpha(alpha: Float) = updateStyle { it.copy(cardSurfaceAlpha = alpha) }

    fun getCardHeight(): Float = currentStyle().safeCardHeight
    fun saveCardHeight(height: Float) = updateStyle { it.copy(cardHeight = height) }

    fun getCardCornerRadius(): Float = currentStyle().cardCornerRadius
    fun saveCardCornerRadius(radius: Float) = updateStyle { it.copy(cardCornerRadius = radius) }

    fun getWallpaperBrightness(): Float = currentStyle().wallpaperBrightness
    fun saveWallpaperBrightness(brightness: Float) = updateStyle { it.copy(wallpaperBrightness = brightness) }

    fun getWallpaperIsLight(): Boolean? = currentStyle().wallpaperIsLight
    fun saveWallpaperIsLight(isLight: Boolean?) = updateStyle { it.copy(wallpaperIsLight = isLight) }

    fun getShowBreakDividers(): Boolean = currentStyle().showBreakDividers
    fun saveShowBreakDividers(show: Boolean) = updateStyle { it.copy(showBreakDividers = show) }

    fun getCardContentAlignment(): CardContentAlignment = currentStyle().safeAlignment
    fun saveCardContentAlignment(alignment: CardContentAlignment) =
        updateStyle { it.copy(cardContentAlignment = alignment) }

    fun getCardTextColor(): CardTextColor = currentStyle().safeTextColor
    fun saveCardTextColor(color: CardTextColor) = updateStyle { it.copy(cardTextColor = color) }

    fun getCardTextScale(): Float = currentStyle().safeCardTextScale
    fun saveCardTextScale(scale: Float) = updateStyle { it.copy(cardTextScale = scale) }

    fun getShowClassroom(): Boolean = currentStyle().showClassroom
    fun saveShowClassroom(show: Boolean) = updateStyle { it.copy(showClassroom = show) }

    fun getShowTeacher(): Boolean = currentStyle().showTeacher
    fun saveShowTeacher(show: Boolean) = updateStyle { it.copy(showTeacher = show) }

    fun getCardRefraction(): CardRefractionLevel = currentStyle().safeRefraction
    fun saveCardRefraction(level: CardRefractionLevel) = updateStyle { it.copy(cardRefraction = level) }

    fun getWallpaperBlur(): Boolean = currentStyle().wallpaperBlur
    fun saveWallpaperBlur(blur: Boolean) = updateStyle { it.copy(wallpaperBlur = blur) }
}
