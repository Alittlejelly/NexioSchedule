package com.haooz.chedule.ui.activities.main

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import com.haooz.chedule.data.AppearanceConfig
import com.haooz.chedule.data.CardContentAlignment
import com.haooz.chedule.data.CardRefractionLevel
import com.haooz.chedule.data.CardTextColor
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.WallpaperTransform
import com.haooz.chedule.ui.screens.SettingsScreen
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.CancellationException
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.time.TimeSource

/**
 * 课表主界面用到的运行期数据模型、位图工具与几何小工具。
 *
 * 从 `CourseScheduleApp.kt` 拆出，避免主文件继续膨胀；
 * 这里只放与界面组合无关（或仅依赖少量状态）的声明。
 */

/** 释放独立快照位图；与进程级壁纸缓存或 keep 引用共用时不回收。 */
internal fun recycleSnapshot(bitmap: ImageBitmap?, vararg keep: ImageBitmap?) {
    if (bitmap == null) return
    if (bitmap === WallpaperStore.currentImageBitmap()) return
    if (keep.any { it === bitmap }) return
    bitmapRecycler?.invoke(bitmap)
}

/** 将壁纸 offset/scale 从 fromW×fromH 重映射到 toW×toH；无有效 bitmap/参考尺寸时原样返回，避免横竖屏切换算坏数据。 */
internal fun remapWallpaperForScreen(
    offset: Offset,
    scale: Float,
    bitmap: ImageBitmap?,
    fromW: Float,
    fromH: Float,
    toW: Float,
    toH: Float,
): Pair<Offset, Float> {
    if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return offset to scale
    return WallpaperTransform.remap(
        offset, scale, bitmap.width, bitmap.height, fromW, fromH, toW, toH
    )
}

/** 运行期的外观快照（壁纸位图 + 变换 + 参数）。 */
data class ScheduleCombination(
    val bitmap: ImageBitmap?,
    val offset: Offset,
    val scale: Float,
    var snapshot: ImageBitmap? = null,
    var cardBlurRadius: Float = 4f,
    var cardAlpha: Float = 0.15f,
    var cardSurfaceAlpha: Float? = null,
    var cardHeight: Float = 54f,
    var cardCornerRadius: Float = 10f,
    var wallpaperBrightness: Float = 0f,
    var showBreakDividers: Boolean = true,
    var cardContentAlignment: CardContentAlignment = CardContentAlignment.CENTER_CENTER,
    var cardTextColor: CardTextColor = CardTextColor.COLORFUL,
    var cardTextScale: Float = 1f,
    var showClassroom: Boolean = true,
    var showTeacher: Boolean = true,
    var cardRefraction: CardRefractionLevel = CardRefractionLevel.DEFAULT,
    var wallpaperIsLight: Boolean? = null,
    var wallpaperBlur: Boolean = false,
)

/** [ScheduleCombination] → 外观配置。 */
fun AppearanceConfig.Companion.fromCombination(c: ScheduleCombination): AppearanceConfig =
    AppearanceConfig(
        cardBlurRadius = c.cardBlurRadius,
        cardAlpha = c.cardAlpha.coerceIn(0f, 1f),
        cardSurfaceAlpha = (c.cardSurfaceAlpha ?: 0.15f).coerceIn(0f, 1f),
        cardHeight = c.cardHeight,
        cardCornerRadius = c.cardCornerRadius,
        wallpaperBrightness = c.wallpaperBrightness,
        showBreakDividers = c.showBreakDividers,
        cardContentAlignment = c.cardContentAlignment,
        cardTextColor = c.cardTextColor,
        cardTextScale = c.cardTextScale,
        showClassroom = c.showClassroom,
        showTeacher = c.showTeacher,
        cardRefraction = c.cardRefraction,
        wallpaperBlur = c.wallpaperBlur,
    )

/** 设置页装配参数（宿主包装 AndroidSettingsScreen / iOS 自己的装配用）。 */
data class SettingsPaneArgs(
    val viewModel: CourseViewModel,
    val scheduleViewModel: ScheduleViewModel,
    val settingsViewModel: SettingsViewModel,
    val shiftViewModel: ShiftViewModel,
    val isShiftMode: Boolean,
    val onExitShiftMode: () -> Unit,
    val onEnterShiftMode: () -> Unit,
    val navBarStyle: String,
    val onScrollYChanged: (Int) -> Unit,
    val settingsScrollBehavior: SharedScrollBehavior?,
    val liquidGlassBackdrop: Backdrop?,
    /** 是否显示「更新设置」入口（iOS 走 App Store → false） */
    val showUpdateSettings: Boolean = true,
)

/**
 * 设置页渲染：宿主注入了 `settingsPane` 就用宿主的，
 * 否则回落共享 [SettingsScreen] 默认装配。
 */
@Composable
internal fun RenderSettingsPane(
    settingsPane: (@Composable (SettingsPaneArgs) -> Unit)?,
    args: SettingsPaneArgs,
) {
    val custom = settingsPane
    if (custom != null) {
        custom(args)
    } else {
        SettingsScreen(
            viewModel = args.viewModel,
            scheduleViewModel = args.scheduleViewModel,
            settingsViewModel = args.settingsViewModel,
            shiftViewModel = args.shiftViewModel,
            isShiftMode = args.isShiftMode,
            onExitShiftMode = args.onExitShiftMode,
            onEnterShiftMode = args.onEnterShiftMode,
            navBarStyle = args.navBarStyle,
            onScrollYChanged = args.onScrollYChanged,
            settingsScrollBehavior = args.settingsScrollBehavior,
            liquidGlassBackdrop = args.liquidGlassBackdrop,
            showUpdateSettings = args.showUpdateSettings,
        )
    }
}

/** 单调毫秒时钟（拖拽速度采样用；`SystemClock.uptimeMillis` 是 Android 专有）。 */
private val MonotonicOrigin = TimeSource.Monotonic.markNow()

internal fun monotonicNow(): Long = MonotonicOrigin.elapsedNow().inWholeMilliseconds

/** 取消进行中的 pager 滚动/惯性 */
internal suspend fun PagerState.cancelScroll() {
    scroll(MutatePriority.PreventUserInput) { }
}

/** 空白格快捷菜单锚点：高亮用列星期，添加课程用调课映射后的星期/周 */
data class EmptyCellMenuTarget(
    val columnDay: Int,
    val section: Int,
    val addDay: Int,
    val addWeek: Int,
)

/**
 * 粘贴落点：动画/高亮走列星期（几何坐标），写库走调课映射后的数据坐标。
 * 调课日列显示的是 followWeekday/followWeek 的课，只传列星期会落到错误星期/周次。
 */
data class PasteTarget(
    val columnDay: Int,
    val dataDay: Int,
    val section: Int,
    /** 数据周次；调课日 = followWeek，否则 = 正在浏览的周 */
    val dataWeek: Int,
)

/** 主 tab 翻页动画（点底栏 tab 时平移切换）；相邻页略偏软 */
private val MainTabPagerAnimSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 320f,
)

/** 跨页（今日↔设置，距离 2 页）更缓，避免长距离仍按同刚度显得急 */
private val MainTabPagerCrossPageAnimSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 240f,
)

/** 动画切到目标主 tab；正常结束后若未精确落页则强制吸附 */
internal suspend fun PagerState.animateMainTabTo(target: Int) {
    val distance = abs(target - currentPage)
    val spec = if (distance > 1) MainTabPagerCrossPageAnimSpec else MainTabPagerAnimSpec
    try {
        animateScrollToPage(target, animationSpec = spec)
    } catch (e: CancellationException) {
        throw e
    }
    if (currentPage != target || abs(currentPageOffsetFraction) > 0.001f) {
        scrollToPage(target)
    }
}

/** 拖拽速度/采样时间；只在松手 spring 时读，避开 composition */
class DragMotionHolder {
    @Volatile
    var velocityX = 0f

    @Volatile
    var velocityY = 0f

    @Volatile
    var lastTimeMs = 0L

    @Volatile
    var lastOffsetX = 0f

    @Volatile
    var lastOffsetY = 0f

    fun reset() {
        velocityX = 0f
        velocityY = 0f
        lastTimeMs = 0L
        lastOffsetX = 0f
        lastOffsetY = 0f
    }
}
