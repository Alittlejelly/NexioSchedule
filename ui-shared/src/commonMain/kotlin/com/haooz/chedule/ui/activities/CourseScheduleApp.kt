package com.haooz.chedule.ui.activities

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.AppearanceConfig
import com.haooz.chedule.data.AppearanceStore
import com.haooz.chedule.data.CardContentAlignment
import com.haooz.chedule.data.CardRefractionLevel
import com.haooz.chedule.data.CardTextColor
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.SwitchCardSnapshotStore
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.data.Notice
import com.haooz.chedule.data.NoticeFetcher
import com.haooz.chedule.data.NoopUpdateController
import com.haooz.chedule.data.SyncManager
import com.haooz.chedule.data.ThemeMode
import com.haooz.chedule.data.UpdateController
import com.haooz.chedule.data.WebDavManager
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.WallpaperTransform
import com.haooz.chedule.data.ioDispatcher
import com.haooz.chedule.data.randomUuidV4
import com.haooz.chedule.data.showToast
import com.haooz.chedule.data.todayLocalDate
import com.haooz.chedule.ui.basic.LiquidGlassDropdownMenu
import com.haooz.chedule.ui.basic.LiquidGlassDropdownMenuItem
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.basic.ShortcutMenu
import com.haooz.chedule.ui.basic.ShortcutMenuItem
import com.haooz.chedule.ui.components.BackToNowFloatingButton
import com.haooz.chedule.ui.components.CourseCard
import com.haooz.chedule.ui.components.LandRippleSpec
import com.haooz.chedule.ui.components.LocalLandRipple
import com.haooz.chedule.ui.components.ScheduleBottomBar
import com.haooz.chedule.ui.theme.rememberAppThemeController
import com.haooz.chedule.ui.components.ScheduleTopBar
import com.haooz.chedule.ui.components.ShareImportDialog
import com.haooz.chedule.ui.components.SharedFileIntent
import com.haooz.chedule.ui.components.TabletNavSideState
import com.haooz.chedule.ui.components.UpdateDialog
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.effects.motion.CUSTOMIZE_COVER_FADE_MILLIS
import com.haooz.chedule.ui.effects.motion.CUSTOMIZE_COVER_HOLD_MILLIS
import com.haooz.chedule.ui.effects.motion.CUSTOMIZE_COVER_SCALE_MILLIS
import com.haooz.chedule.ui.effects.motion.CustomizePageEasing
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.screens.AddCourseDialog
import com.haooz.chedule.ui.screens.CombinationThumbnail
import com.haooz.chedule.ui.screens.CourseDetailScreen
import com.haooz.chedule.ui.screens.CustomizeScheduleScreen
import com.haooz.chedule.ui.screens.MainScheduleScreen
import com.haooz.chedule.ui.screens.ScheduleGridGeometry
import com.haooz.chedule.ui.screens.SettingsScreen
import com.haooz.chedule.ui.screens.ShiftScheduleScreen
import com.haooz.chedule.ui.screens.TabletCourseManagePane
import com.haooz.chedule.ui.screens.TabletSwitchSchedulePane
import com.haooz.chedule.ui.screens.TodayAssistantCardData
import com.haooz.chedule.ui.screens.TodayScreen
import com.haooz.chedule.ui.utils.TransitionLayerImage
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.LocalForcedDarkTheme
import com.haooz.chedule.ui.utils.bottomFloatingPadding
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.HostNavigationState
import com.haooz.chedule.ui.utils.HostedOverlay
import com.haooz.chedule.ui.utils.PageBackdropSnapshot
import com.haooz.chedule.ui.utils.buildShareScheduleMap
import com.haooz.chedule.ui.utils.clipDuringTransition
import com.haooz.chedule.ui.utils.clipScreenCorners
import com.haooz.chedule.ui.utils.consumeAllTouches
import com.haooz.chedule.ui.utils.courseCardSolidBacking
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.loadScheduleThemeMode
import com.haooz.chedule.ui.utils.rememberAppSettingDark
import com.haooz.chedule.ui.utils.rememberScheduleThemeMode
import com.haooz.chedule.ui.utils.rememberScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.toShape
import com.haooz.chedule.ui.utils.addScreenCornerRect
import com.haooz.chedule.ui.utils.saveScheduleThemeMode
import com.haooz.chedule.ui.utils.screenHeightDp
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import com.haooz.chedule.viewmodel.shiftViewModel
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.FastForward
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * 真课表主界面。平台能力全部走注入参数，缺能力的入口不渲染；
 * 位图统一 [ImageBitmap]，快照裁剪走 `cropImageBitmap`，壁纸存储走 `WallpaperStore`。
 */

/** 宿主注入的位图回收实现（Android = `asAndroidBitmap().recycle()`；iOS/桌面 = null，交给 GC）。 */
var bitmapRecycler: ((ImageBitmap) -> Unit)? = null

/** 释放独立快照位图；与进程级壁纸缓存或 keep 引用共用时不回收。 */
private fun recycleSnapshot(bitmap: ImageBitmap?, vararg keep: ImageBitmap?) {
    if (bitmap == null) return
    if (bitmap === WallpaperStore.currentImageBitmap()) return
    if (keep.any { it === bitmap }) return
    bitmapRecycler?.invoke(bitmap)
}

/** 将壁纸 offset/scale 从 fromW×fromH 重映射到 toW×toH；无有效 bitmap/参考尺寸时原样返回，避免横竖屏切换算坏数据。 */
private fun remapWallpaperForScreen(
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
fun AppearanceConfig.Companion.fromCombination(c: ScheduleCombination): AppearanceConfig = AppearanceConfig(
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
    val liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
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

private fun monotonicNow(): Long = MonotonicOrigin.elapsedNow().inWholeMilliseconds

/** 取消进行中的 pager 滚动/惯性 */
private suspend fun PagerState.cancelScroll() {
    scroll(MutatePriority.PreventUserInput) { }
}

/** 空白格快捷菜单锚点：高亮用列星期，添加课程用调课映射后的星期/周 */
private data class EmptyCellMenuTarget(
    val columnDay: Int,
    val section: Int,
    val addDay: Int,
    val addWeek: Int,
)

/**
 * 粘贴落点：动画/高亮走列星期（几何坐标），写库走调课映射后的数据坐标。
 * 调课日列显示的是 followWeekday/followWeek 的课，只传列星期会落到错误星期/周次。
 */
private data class PasteTarget(
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
private suspend fun PagerState.animateMainTabTo(target: Int) {
    val distance = abs(target - currentPage)
    val spec = if (distance > 1) MainTabPagerCrossPageAnimSpec else MainTabPagerAnimSpec
    try {
        animateScrollToPage(target, animationSpec = spec)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    }
    if (currentPage != target || abs(currentPageOffsetFraction) > 0.001f) {
        scrollToPage(target)
    }
}

/** 拖拽速度/采样时间；只在松手 spring 时读，避开 composition */
private class DragMotionHolder {
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
@Composable
private fun DeleteWeekCourseDialog(
    show: Boolean,
    course: Course?,
    week: Int,
    viewModel: CourseViewModel,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onDismiss: () -> Unit,
) {
    var deleteAllWeeks by remember(show) { mutableStateOf(false) }
    OverlayDialog(
        title = if (deleteAllWeeks) "删除课程" else "删除本周课程",
        summary = if (deleteAllWeeks) {
            "确定要删除「${course?.name}」的全部课程吗？\n此操作不可撤销。"
        } else {
            "确定要删除「${course?.name}」在第${week}周的课程吗？\n此操作不可撤销。"
        },
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    state = if (deleteAllWeeks) ToggleableState.On else ToggleableState.Off,
                    onClick = {
                        deleteAllWeeks = !deleteAllWeeks
                    }
                )
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "删除全部周",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (deleteAllWeeks) "删除该课程的所有周次" else "关闭则仅删除第${week}周",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "删除",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        course?.let {
                            if (deleteAllWeeks) viewModel.deleteCourse(it.id)
                            else viewModel.deleteCourseForWeek(it.id, week)
                        }
                        onDismiss()
                    },
                    textColor = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PasteRangeDialog(
    show: Boolean,
    courseName: String,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onPaste: (allWeeks: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        title = "粘贴课程",
        summary = "选择「${courseName}」的粘贴范围",
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                text = "全部周",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onPaste(true)
                },
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = "当前周",
                textColor = MiuixTheme.colorScheme.primary,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onPaste(false)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RescheduleConflictDialog(
    show: Boolean,
    source: Course?,
    target: Course?,
    /** 目标位数据坐标（星期, 起始节次）：调课日列要映射后的星期，几何/动画不走这里 */
    dropTarget: Pair<Int, Int>?,
    /** 目标位数据周次：调课日 = followWeek；交换/覆盖写库按它拆周 */
    dropTargetWeek: Int,
    draggedWeek: Int,
    viewModel: CourseViewModel,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onCancel: () -> Unit,
    onOverwriteResolved: () -> Unit,
    onSwapResolved: () -> Unit,
    onOverwrite: (Set<String>) -> Unit,
    onSwap: (Set<String>) -> Unit,
) {
    OverlayDialog(
        title = "该位置已有课程",
        summary = if (target != null && source != null && dropTarget != null) {
            "「${source.name}」与「${target.name}」的位置冲突\n" +
                    "覆盖：删除「${target.name}」本周的课程，并把「${source.name}」调到此位置\n" +
                    "交换：互换本周两节课的位置"
        } else {
            "该位置已有课程，要如何处理？"
        },
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onCancel
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                text = "取消",
                modifier = Modifier.weight(1f),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onCancel()
                },
            )
            TextButton(
                text = "覆盖",
                textColor = Color(0xFFFF9800),
                modifier = Modifier.weight(1f),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (source != null && dropTarget != null && target != null) {
                        val sectionSpan = source.endSection - source.startSection
                        val targetEnd = dropTarget.second + sectionSpan
                        // 源课在 draggedWeek，目标位在 dropTargetWeek（调课日两者可能不同 → 跨周移动）
                        // 注意 targetWeek 是最后一个参数，别插到中间（全是 Int，错位不报错）
                        viewModel.overwriteCourseForWeek(
                            source.id, draggedWeek,
                            dropTarget.first, dropTarget.second, targetEnd,
                            dropTargetWeek
                        )
                        val sourceCourses = viewModel.getCoursesAtSlot(
                            draggedWeek, source.dayOfWeek, source.startSection, source.endSection
                        ).filter { it.id != source.id && !it.isActiveInWeek(draggedWeek) }
                        if (sourceCourses.isNotEmpty()) {
                            onOverwrite(sourceCourses.map { it.id }.toSet())
                        }
                    }
                    onOverwriteResolved()
                },
            )
            TextButton(
                text = "交换",
                textColor = MiuixTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (source != null && target != null) {
                        // 源课在 draggedWeek、冲突课在 dropTargetWeek，两侧各拆一周互换
                        viewModel.swapCoursesForWeek(source.id, target.id, draggedWeek, dropTargetWeek)
                        val sourceCourses = viewModel.getCoursesAtSlot(
                            draggedWeek, source.dayOfWeek, source.startSection, source.endSection
                        ).filter {
                            it.id != source.id && it.id != target.id && !it.isActiveInWeek(
                                draggedWeek
                            )
                        }
                        val conflictCourses = viewModel.getCoursesAtSlot(
                            dropTargetWeek, target.dayOfWeek, target.startSection, target.endSection
                        ).filter {
                            it.id != target.id && it.id != source.id && !it.isActiveInWeek(
                                dropTargetWeek
                            )
                        }
                        val allAnimated = (sourceCourses + conflictCourses).map { it.id }.toSet()
                        if (allAnimated.isNotEmpty()) {
                            onSwap(allAnimated)
                        }
                    }
                    onSwapResolved()
                },
            )
        }
    }
}

@Composable
private fun ShiftLoadingOverlay(
    show: Boolean,
    onShiftReady: () -> Unit,
    onHide: () -> Unit,
    onActivityChanged: (Boolean) -> Unit = {},
) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(animationSpec = tween(100)),
        exit = fadeOut(animationSpec = tween(100))
    ) {
        DisposableEffect(Unit) {
            onActivityChanged(true)
            onDispose { onActivityChanged(false) }
        }
        LaunchedEffect(Unit) {
            delay(100.milliseconds)
            onShiftReady()
            delay(500.milliseconds)
            onHide()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(
                    size = 30.dp,
                    strokeWidth = 2.8.dp,
                    orbitingDotSize = 3.2.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "切换中",
                    style = MiuixTheme.textStyles.body1.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
    }
}

@Composable
private fun MorePopupMenus(
    showMorePopup: Boolean,
    onMorePopupDismiss: () -> Unit,
    onMorePopupExpand: () -> Unit = {},
    showTodayMorePopup: Boolean,
    onTodayMorePopupDismiss: () -> Unit,
    onTodayMorePopupExpand: () -> Unit = {},
    morePopupFraction: Animatable<Float, *>,
    todayMorePopupFraction: Animatable<Float, *>,
    scheduleMenuOffset: () -> Offset = { Offset.Zero },
    todayMenuOffset: () -> Offset = { Offset.Zero },
    /** 「更多」占位槽实测顶部（root 坐标 px）；传 state 而非 Float，定位须在绘制期读，首帧才不跳。 */
    moreSlotTopPx: MutableFloatState = mutableFloatStateOf(0f),
    scheduleMaterialAlpha: Float = 0f,
    todayMaterialAlpha: Float = 0f,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop,
    isShiftMode: Boolean = false,
    onJumpWeek: () -> Unit,
    onCourseManage: () -> Unit,
    onEnterCustomize: () -> Unit,
    onJumpToDate: () -> Unit,
    onMoreBackProgress: (Float) -> Unit = {},
    onMoreBackCancelled: () -> Unit = {},
    onTodayMoreBackProgress: (Float) -> Unit = {},
    onTodayMoreBackCancelled: () -> Unit = {},
    // 平板右上角不显示「更多」：跳转/管理/外观都已在侧栏里
    enabled: Boolean = true,
) {
    if (!enabled) {
        // 菜单开着时切进/切出禁用场景（平板 rail、课表外观、课表切换）要强制收起，
        // 否则展开态会盖在新页面上还能点。用 enabled 做 key，翻转即重跑。
        LaunchedEffect(enabled) {
            if (showMorePopup) onMorePopupDismiss()
            if (showTodayMorePopup) onTodayMorePopupDismiss()
        }
        return
    }
    if (showMorePopup) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onMorePopupDismiss() }
        )
    }
    if (showTodayMorePopup) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onTodayMorePopupDismiss() }
        )
    }
    // 「更多」锚点纵坐标：**绘制期**定位
    val fallbackAnchorTopPx = with(LocalDensity.current) {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        (if (statusBarTop > 0.dp) statusBarTop - 20.dp else 17.dp)
            .coerceAtLeast(0.dp)
            .toPx()
    }
    val shadowPaddingPx = with(LocalDensity.current) {
        com.haooz.chedule.ui.basic.LiquidGlassDropdownShadowPadding.toPx()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                clip = false
                // moreSlotTopPx 是占位槽顶边，减掉阴影外扩才是内容顶边
                val measured = moreSlotTopPx.floatValue - shadowPaddingPx
                translationY = if (measured > 0f) measured else fallbackAnchorTopPx
            }
            .offset(x = 9.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        // 课程表页「更多」
        LiquidGlassDropdownMenu(
            show = showMorePopup,
            backdrop = liquidGlassBackdrop,
            fraction = morePopupFraction,
            onDismiss = onMorePopupDismiss,
            onBackProgress = onMoreBackProgress,
            onBackCancelled = onMoreBackCancelled,
            triggerIcon = MiuixIcons.More,
            triggerContentDescription = "更多",
            onExpand = onMorePopupExpand,
            offsetPx = scheduleMenuOffset,
            materialAlpha = scheduleMaterialAlpha,
        ) {
            LiquidGlassDropdownMenuItem(
                text = "跳转周数",
                onClick = {
                    onMorePopupDismiss()
                    onJumpWeek()
                },
                icon = {
                    Icon(
                        imageVector = MiuixIcons.Basic.FastForward,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
            if (!isShiftMode) {
                LiquidGlassDropdownMenuItem(
                    text = "课程管理",
                    onClick = {
                        onMorePopupDismiss()
                        onCourseManage()
                    },
                    icon = {
                        Icon(
                            imageVector = MiuixIcons.Backup,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                )
            }
            LiquidGlassDropdownMenuItem(
                text = "课表外观",
                onClick = {
                    onMorePopupDismiss()
                    onEnterCustomize()
                },
                icon = {
                    Icon(
                        imageVector = MiuixIcons.Background,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp)
                    )
                }
            )
        }
        // 今日页「更多」
        LiquidGlassDropdownMenu(
            show = showTodayMorePopup,
            backdrop = liquidGlassBackdrop,
            fraction = todayMorePopupFraction,
            onDismiss = onTodayMorePopupDismiss,
            onBackProgress = onTodayMoreBackProgress,
            onBackCancelled = onTodayMoreBackCancelled,
            triggerIcon = MiuixIcons.More,
            triggerContentDescription = "更多",
            onExpand = onTodayMorePopupExpand,
            offsetPx = todayMenuOffset,
            materialAlpha = todayMaterialAlpha,
        ) {
            LiquidGlassDropdownMenuItem(
                text = "跳转日期",
                onClick = {
                    onTodayMorePopupDismiss()
                    onJumpToDate()
                },
                icon = {
                    Icon(
                        imageVector = MiuixIcons.Basic.FastForward,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp)
                    )
                }
            )
            LiquidGlassDropdownMenuItem(
                text = "课程管理",
                onClick = {
                    onTodayMorePopupDismiss()
                    onCourseManage()
                },
                icon = {
                    Icon(
                        imageVector = MiuixIcons.Backup,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp)
                    )
                }
            )
            LiquidGlassDropdownMenuItem(
                text = "课表外观",
                onClick = {
                    onTodayMorePopupDismiss()
                    onEnterCustomize()
                },
                icon = {
                    Icon(
                        imageVector = MiuixIcons.Background,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp)
                    )
                }
            )
        }
    }
}

/**
 * 点「课表外观」到开始截屏的等待，所有入口共用。
 */
private const val CUSTOMIZE_ENTRY_DELAY_MS = 320L

// 课表外观页「封面快照」的时序常量（CUSTOMIZE_COVER_*）在
// ui/effects/motion/OobeMotion.kt —— 外观页也要按同一个窗口对齐自己的入场时间轴。

@Composable
fun CourseScheduleApp(
    privacyConsented: Boolean = true,
    // ── 平台注入（宿主提供；桌面/iOS 用默认空实现）─────────────────────────
    /** 宿主 resume 次数（Android = MainActivity.resumeCount）：>1 时刷新设置/课表 */
    resumeCount: Int = 0,
    /** 平台小窗模式（Android = MainActivity.isInFreeformWindow）；排班页/圆角用 */
    isInFreeformWindow: Boolean = false,
    /** 状态栏/导航栏深浅同步（Android = applyThemeAwareSystemBars/applyNavigationBarIsDark） */
    onSystemBarsChanged: (statusDark: Boolean, navDark: Boolean) -> Unit = { _, _ -> },
    /** 节假日数据变更副作用（Android = CourseReminderHelper.onHolidayDataChanged） */
    onHolidayDataChanged: () -> Unit = {},
    /** 选壁纸：宿主负责系统选图器+降采样解码，成功后回调 ImageBitmap（null=取消/失败） */
    onPickWallpaperImage: (((ImageBitmap?) -> Unit) -> Unit)? = null,
    /** 分享课表（Android = performScheduleShare）；null = 该端无此能力 */
    onShareSchedule: ((scheduleName: String, onSharingChanged: (Boolean) -> Unit) -> Unit)? = null,
    /** 读取外部分享进的文件全文（Android = contentResolver）；null = 不支持 */
    onReadSharedFileText: ((String) -> String?)? = null,
    /** 外部分享进的文件（Android = MainActivity.shareIntentUri/version 摊平） */
    sharedFileIntent: SharedFileIntent? = null,
    onShareIntentConsumed: () -> Unit = {},
    /** 设置页宿主装配（Android = AndroidSettingsScreen）；null = 共享 SettingsScreen 默认装配 */
    settingsPane: (@Composable (SettingsPaneArgs) -> Unit)? = null,
    /** 今日助手卡（天气/定位是 Android 能力）；null = 不渲染 */
    todayAssistantCard: (@Composable (TodayAssistantCardData) -> Unit)? = null,
    /** 更新能力（检查/下载/安装）；null = NoopUpdateController（不弹更新） */
    updateController: UpdateController? = null,
    /** 是否显示设置页的「更新设置」入口；iOS 走 App Store → 传 false，入口整个不渲染。 */
    showUpdateSettings: Boolean = true,
    /**
     * 是否显示「排班模式」入口（设置页进入开关 / 退出按钮 / 确认弹窗）；iOS 不支持 → 传 false，入口整个不渲染。
     */
) {


    val viewModel: CourseViewModel = courseViewModel()
    val scheduleViewModel: ScheduleViewModel = scheduleViewModel()
    val settingsViewModel: SettingsViewModel = settingsViewModel()
    val shiftViewModel: ShiftViewModel = shiftViewModel()
    val defaultHomepage by settingsViewModel.defaultHomepage.collectAsState()
    val router = LocalAppRouter.current
    var selectedTab by remember { mutableIntStateOf(if (defaultHomepage == "今日") 0 else 1) }
    val hostNavigation = LocalHostNavigation.current
    var showShiftLoading by remember { mutableStateOf(false) }
    var shiftLoadingActive by remember { mutableStateOf(false) }
    var isExitingShift by remember { mutableStateOf(false) }
    var shiftModeInitialized by remember { mutableStateOf(false) }
    val settingsScrollBehavior = rememberSharedScrollBehavior()
    val todayScrollBehavior = rememberSharedScrollBehavior()
    val scheduleScrollBehavior = rememberSharedScrollBehavior()

    LaunchedEffect(privacyConsented) {
        // 合规：未同意隐私政策前不进行云同步等网络行为
        if (!privacyConsented) return@LaunchedEffect
        val syncManager = SyncManager.getInstance()
        val repository = CourseRepository()
        val webDavManager = WebDavManager {
            onHolidayDataChanged()
        }
        syncManager.start(repository, webDavManager)
        // 备份/恢复后刷新 ViewModel 内存缓存
        syncManager.onSyncCompleted = {
            viewModel.refreshEssentialData()
            viewModel.reloadCourses()
            settingsViewModel.refreshSettings()
        }
    }

    val isDark = isAppDarkTheme()
    val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    // 供今日/课程表卡片玻璃采样：录的是共享壁纸层，不是页内透明占位
    val sharedWallpaperBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()

    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val morningSections by settingsViewModel.morningSections.collectAsState()
    val afternoonSections by settingsViewModel.afternoonSections.collectAsState()
    val eveningSections by settingsViewModel.eveningSections.collectAsState()
    val configuredSectionTimes by settingsViewModel.sectionTimes.collectAsState()
    val totalSections = morningSections + afternoonSections + eveningSections
    val holidayDataRevision by HolidayManager.dataRevision.collectAsState()
    // 只在「返回」时刷新；冷启动首次 onResume 时 ViewModel 刚加载完，再全量刷会拖慢首屏
    LaunchedEffect(resumeCount) {
        // 作息按日期自动切换：跨过生效日期后补一次提醒重排。
        // 只是比对 prefs 里记的 key，几乎零开销，所以冷启动这一次也跑
        withContext(ioDispatcher) {
            CourseRepository().syncRoutineAfterDateChange()
        }
        if (resumeCount > 1) {
            withContext(ioDispatcher) {
                settingsViewModel.refreshSettings()
                scheduleViewModel.refreshScheduleList()
            }
            viewModel.reloadCourses()
        }
    }
    // 节假日/调休保存不走课程 reload；resume 时对比 HolidayManager 版本，变了才 bump dataVersion
    var seenHolidayVersion by remember {
        mutableLongStateOf(
            HolidayManager.getVersion()
        )
    }
    LaunchedEffect(resumeCount, holidayDataRevision) {
        val holidayV = HolidayManager.getVersion()
        if (holidayV != seenHolidayVersion) {
            seenHolidayVersion = holidayV
            viewModel.bumpDataVersion()
        }
    }
    val isTablet = isTabletWidth()
    val navBarStyle = if (isTablet) "rail" else "standard"
    val nativeBottomBar = hostNavigation != null && !isTablet
    val useVerticalMainPager = isTablet ||
        (nativeBottomBar && hostNavigation?.usesVerticalTabTransitions == true)
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val density = LocalDensity.current
    val detailLayerLayoutDirection = LocalLayoutDirection.current
    val screenWPx = with(density) { screenWidthDp().dp.toPx() }
    val screenHPx = with(density) { screenHeightDp().dp.toPx() }
    val latestScreenWPx by rememberUpdatedState(screenWPx)
    val latestScreenHPx by rememberUpdatedState(screenHPx)

    // 首帧后预热 RenderEffect，避免首次开 BlurBottomSheet 掉帧
    val warmupBlurPx = with(density) { 24.dp.toPx() }
    var blurWarmupReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { blurWarmupReady = true }
    if (blurWarmupReady) {
        Box(
            modifier = Modifier
                .size(1.dp)
                .graphicsLayer { alpha = 0f }
                .then(
                    if (isRenderEffectSupported()) {
                        Modifier.drawBackdrop(
                            backdrop = liquidGlassBackdrop,
                            shape = { androidx.compose.foundation.shape.RoundedCornerShape(36.dp) },
                            effects = {
                                vibrancy()
                                blur(warmupBlurPx)
                            },
                            highlight = null
                        )
                    } else Modifier
                )
        )
    }
    // 侧栏展开状态用全局对象；内容让位只在 layout 读进度，避免整树每帧重组
    // 侧栏伸缩后强制续录 + 顶栏糊层重采样：否则采样停在旧帧，切页才会恢复
    val railBlurResampleEpoch = remember { mutableIntStateOf(0) }
    val railForceRecordFrames = remember { intArrayOf(0) }
    LaunchedEffect(TabletNavSideState.expanded, navBarStyle) {
        if (navBarStyle != "rail") return@LaunchedEffect
        railForceRecordFrames[0] = 12
        delay(360.milliseconds)
        railBlurResampleEpoch.intValue++
    }
    val isShiftMode by shiftViewModel.isShiftMode.collectAsState()

    var showCourseDetailPopup by remember { mutableStateOf(false) }

    var detailCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var detailCardLeft by remember { mutableFloatStateOf(0f) }
    var detailCardTop by remember { mutableFloatStateOf(0f) }
    var detailCardWidth by remember { mutableFloatStateOf(0f) }
    var detailCardHeight by remember { mutableFloatStateOf(0f) }
    var detailSnapshot by remember { mutableStateOf<TransitionLayerSnapshot?>(null) }
    val retainedDetailLayer = rememberGraphicsLayer()
    var showDetail by remember { mutableStateOf(false) }
    // 详情页退出中：主内容提前恢复绘制（快照垫底），画出来后再撤快照。见 onBackStart。
    var detailExiting by remember { mutableStateOf(false) }

    var detailFromToday by remember { mutableStateOf(false) }
    var detailTargetWeek by remember { mutableIntStateOf(0) }
    var hiddenCourseIds by remember { mutableStateOf(setOf<String>()) }

    var isDraggingCard by remember { mutableStateOf(false) }
    var draggingCourseIds by remember { mutableStateOf(setOf<String>()) }
    var draggedCardCourse by remember { mutableStateOf<Course?>(null) }
    var draggedCardPosition by remember { mutableStateOf(Offset.Zero) }
    var draggedCardSize by remember { mutableStateOf(Offset.Zero) }
    // 速度/采样时间只在 spring 起始读；offset 走 floatingOffset* 的 graphicsLayer，不进组合
    val dragMotion = remember { DragMotionHolder() }
    var draggedCardBackdrop by remember { mutableStateOf<com.kyant.backdrop.Backdrop?>(null) }
    var draggedWeek by remember { mutableIntStateOf(1) }
    // 源卡片所在的**列**星期（几何坐标）。调课日列的数据星期 ≠ 列星期，
    // 落点原格高亮、几何吸附都要用列，写库才用数据星期
    var draggedColumnDay by remember { mutableIntStateOf(1) }
    // 拖拽落点检测用网格几何
    var gridGeometry by remember {
        mutableStateOf<ScheduleGridGeometry?>(
            null
        )
    }
    // (dayOfWeek, startSection)，null=无有效落点
    var pendingDropTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var animateInCourseIds by remember { mutableStateOf(setOf<String>()) }
    var showRescheduleConflictDialog by remember { mutableStateOf(false) }
    var pendingConflictCourse by remember { mutableStateOf<Course?>(null) }
    // 退出动画期间保持 true，结束后原卡片 alpha 恢复 1
    var floatingCardVisible by remember { mutableStateOf(false) }
    // 手指正拖着浮层：不能拿 floatingCardVisible 当高光开关——它从长按即为 true，
    // 且要等约 360ms 退场动画跑完才回 false，会出现「长按没拖也亮」「松手后不灭」。
    var isFloatingCardDragging by remember { mutableStateOf(false) }
    // 入场 0.94→1.04，退场 1.04→1.0
    val floatingScale = remember { Animatable(0.94f) }
    // 吸附期间用 floatingOffsetAnim 替代 draggedCardOffset
    var isSnapping by remember { mutableStateOf(false) }
    // 浮层平移：拖拽回调非挂起，用 mutableFloatStateOf；只在 graphicsLayer 读，不触发组合
    val floatingOffsetX = remember { mutableFloatStateOf(0f) }
    val floatingOffsetY = remember { mutableFloatStateOf(0f) }

    // 高光开关 = 正在拖 && 浮层还活着：声明在 Scaffold 之前，Scaffold 内容里的提饱和节点要用它。
    // 一松手 onCourseDragEnd 立刻置 false 开始淡出，不等 360ms 退场动画。
    val glowShowing = isFloatingCardDragging && floatingCardVisible
    val glowAlpha by animateFloatAsState(
        targetValue = if (glowShowing) 1f else 0f,
        animationSpec = tween(150),
        label = "dragCardGlowAlpha",
    )

    suspend fun animateFloatState(
        state: MutableFloatState,
        target: Float,
        spec: androidx.compose.animation.core.AnimationSpec<Float>,
        initialVelocity: Float = 0f
    ) {
        animate(
            initialValue = state.floatValue,
            targetValue = target,
            animationSpec = spec,
            initialVelocity = initialVelocity
        ) { value, _ -> state.floatValue = value }
    }

    // 粘贴飞行：复用长按浮层，直线飞向目标格
    var isPasteFlight by remember { mutableStateOf(false) }
    // 落地冲击波：周围课程卡按距离延迟涟漪
    var landRippleCenter by remember { mutableStateOf(Offset.Zero) }
    var landRippleToken by remember { mutableIntStateOf(0) }
    // 冲突悬停：浮层停在目标卡上方等待用户选择
    var isConflictHover by remember { mutableStateOf(false) }
    var conflictHoverBobJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // 交换时目标课作为第二浮层交叉飞行
    var swapFlightVisible by remember { mutableStateOf(false) }
    var swapFlightCourse by remember { mutableStateOf<Course?>(null) }
    var swapFlightOriginCenter by remember { mutableStateOf(Offset.Zero) }
    var swapFlightWidth by remember { mutableFloatStateOf(0f) }
    val swapFlightOffsetX = remember { Animatable(0f) }
    val swapFlightOffsetY = remember { Animatable(0f) }
    val swapFlightScale = remember { Animatable(1f) }
    var shortcutMenuCourse by remember { mutableStateOf<Course?>(null) }
    var shortcutMenuVisible by remember { mutableStateOf(false) }
    var shortcutMenuPosition by remember { mutableStateOf(Offset.Zero) }
    var shortcutMenuSize by remember { mutableStateOf(IntSize.Zero) }
    var shortcutMenuBackdrop by remember { mutableStateOf<com.kyant.backdrop.Backdrop?>(null) }
    // 左移时菜单右边缘对齐卡片右边缘
    var shortcutMenuAnchorWidth by remember { mutableFloatStateOf(0f) }
    // (星期, 起始节次)；粘贴目标=长按格
    // columnDay 用于格子高亮；addDay/addWeek 供「添加」默认落到调课来源星期/周
    var emptyCellMenuTarget by remember { mutableStateOf<EmptyCellMenuTarget?>(null) }
    // 页面会话级剪贴板，再次复制覆盖，粘贴后保留
    var copiedCourseForPaste by remember { mutableStateOf<Course?>(null) }
    var showPasteRangeDialog by remember { mutableStateOf(false) }
    var pasteRangeTarget by remember { mutableStateOf<PasteTarget?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteConfirmCourse by remember { mutableStateOf<Course?>(null) }

    var showCustomizePage by remember { mutableStateOf(false) }
    var customizeSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var snapshotCoverBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isCustomizeExiting by remember { mutableStateOf(false) }
    var customizeExitTargetScale by remember { mutableFloatStateOf(1f) }
    val customizeExitScale = remember { Animatable(1f) }
    val customizeExitAlpha = remember { Animatable(1f) }
    var isWindowCutoutActive by remember { mutableStateOf(false) }
    // 动画只作用于快照覆盖层，避免双缩放叠加闪烁
    var customizeCoverActive by remember { mutableStateOf(false) }
    val customizeCoverScale = remember { Animatable(1f) }
    val customizeCoverAlpha = remember { Animatable(1f) }
    val wallpaperRepository = remember { CourseRepository() }
    var combinations by remember { mutableStateOf(listOf<ScheduleCombination>()) }
    var currentCombinationIndex by remember { mutableIntStateOf(0) }
    var wallpaperBitmap by remember { mutableStateOf(WallpaperStore.currentImageBitmap()) }
    var wallpaperOffset by remember { mutableStateOf(WallpaperStore.wallpaperOffset) }
    var wallpaperScale by remember { mutableFloatStateOf(WallpaperStore.transformScale) }
    // offset/scale 当前对应的屏幕尺寸；旋转后按此重映射，避免像素值跨方向复用
    var wallpaperScreenRef by remember { mutableStateOf(screenWPx to screenHPx) }
    // 截快照时临时按该搭配的壁纸亮暗覆盖主题
    var captureThemeActive by remember { mutableStateOf(false) }
    var captureThemeIsDark by remember { mutableStateOf<Boolean?>(null) }

    // 壁纸主题锁定：课程表页有壁纸时按测光强制浅/深色；今日页仅开启显示壁纸时锁定；设置页跟随系统
    val currentComb = combinations.getOrNull(currentCombinationIndex)
    val currentCombIsLight = currentComb?.wallpaperIsLight
    // 壁纸异步加载，首帧同步读持久化结果，避免主题跳变
    val initialCombWallpaperIsLight = remember {
        AppearanceStore.getWallpaperIsLight()
    }
    // 搭配未加载（首帧）才退回 initial，避免无壁纸搭配误用初始测光
    val combIsLight = if (currentComb == null) initialCombWallpaperIsLight else currentCombIsLight
    
    // 独立偏好 key，与全局主题开关隔离
    val persistedScheduleThemeMode = rememberScheduleThemeMode()
    // 编辑中的临时档位：「应用」才落盘
    var pendingScheduleThemeMode by remember { mutableStateOf<ThemeMode?>(null) }
    val scheduleThemeMode = pendingScheduleThemeMode ?: persistedScheduleThemeMode
    // 壁纸强制主题：不依赖 selectedTab，三页各自预先算好，切页不再改主题
    // 无壁纸时「默认主题」不生效
    val wallpaperForcedDark: Boolean? = if (isShiftMode || combIsLight == null) {
        null
    } else {
        when (scheduleThemeMode) {
            ThemeMode.FOLLOW_WALLPAPER -> !combIsLight
            ThemeMode.FOLLOW_APP -> null
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    }
    // 各页预先固定的主题（与 selectedTab 无关）
    // 今日页与课程表页壁纸恒同步，故两者共用同一壁纸锁定主题
    val todayPageForcedDark = wallpaperForcedDark
    val settingsPageForcedDark: Boolean? = null
    // 顶栏/底栏等 chrome 跟当前页有效主题
    val forcedDark = when {
        isShiftMode -> null
        showCustomizePage -> wallpaperForcedDark
        selectedTab == 2 -> null
        selectedTab == 1 -> wallpaperForcedDark
        selectedTab == 0 -> todayPageForcedDark
        else -> null
    }
    val effectiveIsDark = forcedDark ?: isDark
    val appSettingDark = rememberAppSettingDark()
    // 状态栏跟页面实际深浅；导航栏图标始终跟应用设置
    LaunchedEffect(effectiveIsDark, appSettingDark) {
        onSystemBarsChanged(effectiveIsDark, appSettingDark)
    }

    // 已应用快照，开洞编辑取消时回退
    var savedWallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var savedWallpaperOffset by remember { mutableStateOf(wallpaperOffset) }
    var savedWallpaperScale by remember { mutableFloatStateOf(wallpaperScale) }
    var savedAppearance by remember { mutableStateOf(AppearanceConfig()) }
    // 进入搭配页时的原始搭配；滑动切换不更新（退出非应用时还原）
    var originalCombinationIndex by remember { mutableIntStateOf(0) }
    var originalWallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var originalWallpaperOffset by remember { mutableStateOf(wallpaperOffset) }
    var originalWallpaperScale by remember { mutableFloatStateOf(wallpaperScale) }
    var originalSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    // 退出（非应用）时还原主题锁定
    var originalWallpaperIsLight by remember { mutableStateOf<Boolean?>(null) }
    // 整体还原，避免编辑回调字段残留
    var originalCombination by remember { mutableStateOf<ScheduleCombination?>(null) }
    var isApplyingCustomize by remember { mutableStateOf(false) }
    var pendingEnterCutout by remember { mutableStateOf(false) }

    // 外观从当前搭配派生，编辑只写 combinations；未加载时回退启动缓存防冷启动闪默认值
    fun currentAppearance(): AppearanceConfig =
        combinations.getOrNull(currentCombinationIndex)
            ?.let { AppearanceConfig.fromCombination(it) }
            ?: AppearanceStore.cachedConfig

    fun applyAppearance(value: AppearanceConfig) {
        val idx = currentCombinationIndex
        if (idx in combinations.indices) {
            combinations = combinations.toMutableList().also { list ->
                list[idx] = list[idx].copy(
                    cardBlurRadius = value.cardBlurRadius,
                    cardAlpha = value.cardAlpha.coerceIn(0f, 1f),
                    cardSurfaceAlpha = value.cardSurfaceAlpha.coerceIn(0f, 1f),
                    cardHeight = value.cardHeight,
                    cardCornerRadius = value.cardCornerRadius,
                    wallpaperBrightness = value.wallpaperBrightness,
                    showBreakDividers = value.showBreakDividers,
                    cardContentAlignment = value.cardContentAlignment,
                    cardTextColor = value.cardTextColor,
                    cardTextScale = value.cardTextScale,
                    showClassroom = value.showClassroom,
                    showTeacher = value.showTeacher,
                    cardRefraction = value.cardRefraction,
                    wallpaperBlur = value.wallpaperBlur
                )
            }
        }
    }

    /**
     * 搭配读取唯一入口：冷启动 IO 路径与伴生缓存路径都走这里。
     * 两份逐字段复制的构造曾是 bug 温床，新增外观字段只需改此处。
     */
    fun buildCombination(bitmap: ImageBitmap?) =
        ScheduleCombination(
            bitmap = bitmap,
            offset = Offset(
                AppearanceStore.getOffsetX(),
                AppearanceStore.getOffsetY()
            ),
            scale = AppearanceStore.getScale(),
            snapshot = null,
            cardBlurRadius = AppearanceStore.getCardBlur(),
            cardAlpha = AppearanceStore.getCardAlpha(),
            cardSurfaceAlpha = AppearanceStore.getCardSurfaceAlpha(),
            cardHeight = AppearanceStore.getCardHeight(),
            cardCornerRadius = AppearanceStore.getCardCornerRadius(),
            wallpaperBrightness = AppearanceStore.getWallpaperBrightness(),
            showBreakDividers = AppearanceStore.getShowBreakDividers(),
            cardContentAlignment = AppearanceStore.getCardContentAlignment(),
            cardTextColor = AppearanceStore.getCardTextColor(),
            cardTextScale = AppearanceStore.getCardTextScale(),
            showClassroom = AppearanceStore.getShowClassroom(),
            showTeacher = AppearanceStore.getShowTeacher(),
            cardRefraction = AppearanceStore.getCardRefraction(),
            wallpaperIsLight = AppearanceStore.getWallpaperIsLight(),
            wallpaperBlur = AppearanceStore.getWallpaperBlur()
        )

    // 加载单搭配；有伴生缓存则跳过 IO
    LaunchedEffect(Unit) {
        val cached = WallpaperStore.currentImageBitmap()

        val comb = if (cached != null) {
            buildCombination(cached)
        } else {
            withContext(ioDispatcher) { buildCombination(WallpaperStore.load()) }
        }
        // 单搭配恒为下标 0
        combinations = if (comb != null) listOf(comb) else emptyList()
        currentCombinationIndex = 0

        val curr = combinations.getOrNull(0)
        if (curr != null) {
            val curW = latestScreenWPx
            val curH = latestScreenHPx
            val refW = AppearanceStore.getOffsetRefW()
            val refH = AppearanceStore.getOffsetRefH()
            // 有参考尺寸且与当前屏不一致时重映射；旧数据 ref=0 时原样加载
            val (mappedOffset, mappedScale) = if (refW > 0f && refH > 0f && (refW != curW || refH != curH)) {
                remapWallpaperForScreen(curr.offset, curr.scale, curr.bitmap, refW, refH, curW, curH)
            } else {
                curr.offset to curr.scale
            }
            wallpaperBitmap = curr.bitmap
            wallpaperOffset = mappedOffset
            // 不把当前方向的 minScale 写回用户 scale：绘制层已 max(scale, minScale)，
            // 这里烘焙会导致旋转回原方向时缩放被抬高、画面错位
            wallpaperScale = mappedScale
            wallpaperScreenRef = curW to curH
            savedWallpaperBitmap = curr.bitmap
            savedWallpaperOffset = mappedOffset
            savedWallpaperScale = mappedScale
            savedAppearance = AppearanceConfig.fromCombination(curr)
            originalWallpaperBitmap = curr.bitmap
            originalWallpaperOffset = mappedOffset
            originalWallpaperScale = mappedScale
            if (mappedOffset != curr.offset || mappedScale != curr.scale) {
                combinations = combinations.toMutableList().also { list ->
                    list[0] = list[0].copy(offset = mappedOffset, scale = mappedScale)
                }
            }
            WallpaperStore.saveTransform(mappedOffset, mappedScale)
        }
    }

    // 横竖屏（含自由窗口）尺寸变化：把当前显示中的变换从旧屏重映射到新屏
    LaunchedEffect(screenWPx, screenHPx) {
        val (prevW, prevH) = wallpaperScreenRef
        if (prevW == screenWPx && prevH == screenHPx) return@LaunchedEffect
        val bmp = wallpaperBitmap
        if (bmp != null && prevW > 0f && prevH > 0f) {
            val (newOffset, newScale) = remapWallpaperForScreen(
                wallpaperOffset, wallpaperScale, bmp, prevW, prevH, screenWPx, screenHPx
            )
            wallpaperOffset = newOffset
            wallpaperScale = newScale
            val idx = currentCombinationIndex
            if (idx in combinations.indices) {
                combinations = combinations.toMutableList().also { list ->
                    list[idx] = list[idx].copy(offset = newOffset, scale = newScale)
                }
            }
            WallpaperStore.saveTransform(newOffset, newScale)
        }
        wallpaperScreenRef = screenWPx to screenHPx
    }
    val cutoutMainScale = remember { Animatable(1f) }
    var cutoutCenterYRatio by remember { mutableFloatStateOf(0.5f) }
    // 与 CustomizeScheduleScreen 共享实例，直接读 .value 同帧同步
    val sheetOffsetY = remember { Animatable(0f) }
    LaunchedEffect(isWindowCutoutActive) {
        if (isWindowCutoutActive) {
            val c = combinations.getOrNull(currentCombinationIndex)
            if (c != null) {
                // combinations 在加载/旋转时已同步为当前屏坐标系的 offset/scale
                wallpaperBitmap = c.bitmap
                wallpaperOffset = c.offset
                wallpaperScale = c.scale
                wallpaperScreenRef = latestScreenWPx to latestScreenHPx
            }
            cutoutMainScale.snapTo(0.65f)
            cutoutMainScale.animateTo(
                0.75f,
                tween(400, easing = CustomizePageEasing)
            )
        } else {
            cutoutMainScale.animateTo(
                1f,
                tween(400, easing = CustomizePageEasing)
            )
        }
    }
    // 用布尔量而不用整屏位图当哨兵，避免白养 ~10MB Bitmap
    var switchOverlayActive by remember { mutableStateOf(false) }
    var switchCardSnapshot by remember { mutableStateOf<TransitionLayerSnapshot?>(null) }
    // 形变插值起止依据（窗口坐标系）
    var switchCardBounds by remember { mutableStateOf<Rect?>(null) }
    // 页面坐标系 → 窗口坐标系的换算偏移
    var switchContentRootX by remember { mutableFloatStateOf(0f) }

    // 保留在 Activity 层不是为了防止销毁（详情页期间主内容已不再移出组合），
    // 而是液态玻璃重录要读它们的「滚动中 / 弹窗开合」（liquidGlassMustRecord、liquidGlassRecordKey）
    val scheduleScrollState = rememberScrollState()
    val scheduleSelectedCourse = remember { mutableStateOf<Course?>(null) }
    val scheduleSelectedCourses = remember { mutableStateOf<List<Course>>(emptyList()) }
    val scheduleShowCourseDetail = remember { mutableStateOf(false) }

    val todayListState = rememberLazyListState()
    // 今日页各日期页独立 LazyListState，这里只收「是否在滚」供液态玻璃重录
    val todayListScrollInProgress = remember { mutableStateOf(false) }
    var switchContentRootY by remember { mutableFloatStateOf(0f) }
    var switchAnimJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var switchAnimForward by remember { mutableStateOf(false) }
    var switchAnimRunning by remember { mutableStateOf(false) }
    // Await a schedule reload before recording the return layer.
    var switchReloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val switchAnimProgress = remember { Animatable(0f) }
    val managePageBlurRadius = remember { Animatable(0f) }
    // 仅课程长按菜单模糊；空白格菜单不模糊
    val shortcutMenuBlurRadius = remember { Animatable(0f) }
    LaunchedEffect(shortcutMenuVisible, shortcutMenuCourse) {
        if (shortcutMenuVisible && shortcutMenuCourse != null) {
            launch { shortcutMenuBlurRadius.animateTo(16f, tween(280)) }
        } else {
            launch { shortcutMenuBlurRadius.animateTo(0f, tween(250)) }
        }
    }
    val switchReturnBgScrim = remember { Animatable(0f) }
    val screenGraphicsLayer = rememberGraphicsLayer()
    // 切换课表：进入方向要复用「上次退出录到的卡片」，但**图层跨不过组合销毁** ——
    // `GraphicsLayer.record{}` 里 `drawLayer(x)` 存的是对 x 的引用，切换页一销毁那层就废。
    // 所以只能在退出那一帧把卡片区域**固化成位图**（位图不依赖任何组合），
    // 下次进入直接画它。进入方向因此零录制。
    var retainedSwitchCardBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    // 转位图用的临时小图层：只承载卡片那一块，避免整屏 toImageBitmap（12MB + 整屏回读）。
    val retainedSwitchCardScratchLayer = rememberGraphicsLayer()
    val switchLayerLayoutDirection = LocalLayoutDirection.current
    // 本次形变要画的卡片位图（只有进入方向用；退出方向仍用实时卡片图层）。
    var switchCardBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    // 读回上次会话退出时固化的卡片快照：位图活不过进程，只有文件能留到下次启动。
    // 桌面上 Store 未初始化/无实现，返回 null，退化成会话内复用。
    LaunchedEffect(Unit) {
        if (retainedSwitchCardBitmap == null) {
            retainedSwitchCardBitmap = SwitchCardSnapshotStore.load()
        }
    }
    // Retain drawing commands on demand for snapshots and schedule-library morphs.
    class MainSnapshotRequester {
        var lastRecordedToken: Int = 0
    }
    val mainSnapshotRequester = remember { MainSnapshotRequester() }
    var mainSnapshotToken by remember { mutableIntStateOf(0) }
    suspend fun prepareMainContentLayer() {
        val token = ++mainSnapshotToken
        // Recording retains drawing commands; no bitmap allocation or CPU rasterization.
        while (mainSnapshotRequester.lastRecordedToken < token) {
            withFrameNanos { }
        }
    }
    suspend fun captureMainContentBitmap(includeNativeChrome: Boolean = true): ImageBitmap? {
        mainSnapshotToken++
        // 等两帧：draw 录制完成 + 帧已提交
        withFrameNanos { }
        withFrameNanos { }
        // 必须单独 catch OutOfMemoryError：Bitmap 像素在 native 堆，全屏 toImageBitmap
        return try {
            // 有原生 chrome 时（iOS）优先用原生整屏截图：GraphicsLayer.record()
            // 只录 Compose 绘制的内容，底部原生 TabBar 会整条缺失。
            (if (includeNativeChrome) com.haooz.chedule.ui.utils.captureScreenWithNativeChrome() else null)
                ?: screenGraphicsLayer.toImageBitmap()
        } catch (_: OutOfMemoryError) {
            FeatureLog.t("主页", "main_snapshot_oom")
            null
        } catch (_: Exception) {
            null
        }
    }
    var blurSnapshotJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val hapticFeedback = LocalHapticFeedback.current

    val currentDayOfWeek = todayLocalDate().dayOfWeek.isoDayNumber
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    val dataVersion by viewModel.dataVersion.collectAsState()
    val teachingWeekRepository = remember { CourseRepository.getInstance() }
    val teachingWeekRules = remember(teachingWeekRepository, dataVersion, classStartTime) {
        teachingWeekRepository.getTeachingWeekReorganizations()
    }
    // 节假日/调休保存后需能重算跳周；resume 时刷新版本号
    val holidayVersion = remember(resumeCount, holidayDataRevision) {
        HolidayManager.getVersion()
    }

    val basePage = (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
    val autoAdvancePage = remember(currentWeek, totalWeeks, currentDayOfWeek, smartWeekend, holidayVersion, dataVersion, teachingWeekRules) {
        if (settingsViewModel.shouldAdvanceToNextWeek(currentDayOfWeek, currentWeek)) {
            (basePage + 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
        } else basePage
    }

    val pagerState = rememberPagerState(
        initialPage = autoAdvancePage,
        pageCount = { totalWeeks }
    )

    val todayMaxDateOffset = 1000
    val todayPagerState = rememberPagerState(
        initialPage = todayMaxDateOffset,
        pageCount = { todayMaxDateOffset * 2 }
    )

    // 主 tab 平移容器：仅点底栏/侧栏 tab 驱动。手机端只有 3 页，
    // 后两页（课程管理/切换课表）只有平板侧栏才到得了，按 5 页建会让它们常驻组合树白陪跑。
    val mainPagerPageCount = if (isShiftMode) 2 else if (navBarStyle == "rail") 5 else 3
    val mainPagerState = rememberPagerState(
        initialPage = selectedTab.coerceIn(0, mainPagerPageCount - 1),
        pageCount = { mainPagerPageCount }
    )
    // 程序化切 tab 期间为 true，避免 currentPage 在动画中途把 selectedTab 拉回去
    var mainTabProgrammatic by remember { mutableStateOf(false) }

    // 当前可见页背后是不是壁纸，决定高光亮度（见 InteractiveHighlight 的 peak）。
    // 必须在组合期算再交给 SideEffect：只在 SideEffect 里读 currentPage 不会订阅，切页不更新。
    val pageBehindHasWallpaper = wallpaperBitmap != null &&
        !isShiftMode &&
        mainPagerState.currentPage in 0..1
    SideEffect { PageBackdropSnapshot.hasWallpaper.value = pageBehindHasWallpaper }

    // 二级页侧栏点选主 tab（无转场回来后处理）
    LaunchedEffect(TabletNavSideState.pendingMainTab) {
        val pending = TabletNavSideState.pendingMainTab
        if (pending in 0..4 && shiftModeInitialized) {
            // 手机端只有 3 页，侧栏带过来的页号要按实际页数收口
            val target = pending.coerceAtMost(mainPagerPageCount - 1)
            selectedTab = target
            mainPagerState.scrollToPage(target)
            TabletNavSideState.pendingMainTab = -1
        }
    }

    LaunchedEffect(isShiftMode) {
        if (shiftModeInitialized) {
            selectedTab = if (isShiftMode) 0 else if (defaultHomepage == "今日") 0 else 1
            mainPagerState.scrollToPage(selectedTab)
        }
        shiftModeInitialized = true
    }

    var lastAutoPage by remember { mutableIntStateOf(autoAdvancePage) }
    var lastAutoWeek by remember { mutableIntStateOf(currentWeek) }
    LaunchedEffect(currentWeek, totalWeeks, autoAdvancePage) {
        // A rule edit must recalculate the default, but must not pull a manually browsed page back.
        if (currentWeek != lastAutoWeek || pagerState.currentPage == lastAutoPage) {
            if (pagerState.currentPage != autoAdvancePage) pagerState.scrollToPage(autoAdvancePage)
        }
        lastAutoWeek = currentWeek
        lastAutoPage = autoAdvancePage
    }

    // 切 tab 时强制放掉周 pager 上可能残留的 scroll 锁。
    LaunchedEffect(mainPagerState.currentPage) {
        runCatching { pagerState.scroll(MutatePriority.PreventUserInput) { } }
    }

    var todaySelectedDayOfWeek by remember { mutableIntStateOf(currentDayOfWeek) }
    var todayIsToday by remember { mutableStateOf(true) }
    var scrollToTodayTrigger by remember { mutableIntStateOf(0) }

    val currentViewingWeek = pagerState.currentPage + 1
    val courses by viewModel.courses.collectAsState()
    // dataVersion + courses 引用都进 key：调课 size 可能不变，只靠 size 会让智能周末星期行停在旧值
    val dayRange = remember(
        currentViewingWeek,
        smartWeekend,
        courses,
        dataVersion,
        holidayVersion,
        configuredSectionTimes,
        morningSections,
        afternoonSections,
        eveningSections,
    ) {
        (1..5).toList() + settingsViewModel.getWeekendDaysForWeek(currentViewingWeek)
            .filter { it in 6..7 }
    }
    val viewingIsHoliday = viewModel.isWeekHoliday(currentViewingWeek)
    val weekDates = remember(currentViewingWeek, classStartTime, teachingWeekRules) {
        try {
            val startDate = LocalDate.parse(classStartTime.replace("/", "-"))
            com.haooz.chedule.data.TeachingWeekReorganization.datesForTeachingWeek(
                startDate, currentViewingWeek, teachingWeekRules,
            )
        } catch (_: Exception) {
            emptyList()
        }
    }



    val screenCornerRadius = rememberScreenCornerRadiiPx()

    // 设置页的「卡片」圆角 = 屏幕圆角（与 [AppNavHost] 的转场裁切同一把尺）
    val settingsPageIndex = if (isShiftMode) 1 else 2
    val settingsPageClipShape = remember(screenCornerRadius, density) {
        if (screenCornerRadius.maxRadius > 0f) screenCornerRadius.toShape(density) else null
    }
    // 顶栏糊带：只圆**顶部两角**（四角全圆会把 120dp 高的糊带变成药丸）
    val settingsTopBarClipShape = remember(screenCornerRadius, density) {
        if (screenCornerRadius.maxRadius > 0f) {
            screenCornerRadius.copy(bottomRight = 0f, bottomLeft = 0f).toShape(density)
        } else {
            null
        }
    }
    val settingsCardOnScreen = remember(mainPagerState, isShiftMode) {
        {
            val scrollPos = mainPagerState.currentPage +
                mainPagerState.currentPageOffsetFraction
            scrollPos > settingsPageIndex - 1f
        }
    }
    // 平板（rail）主界面切页是瞬时的（scrollToPage，无动画），圆角卡片机制不参与。
    val mainTabTransitionAnimated = navBarStyle != "rail"
    val settingsPageClipModifier =
        if (mainTabTransitionAnimated) {
            Modifier
                .fillMaxSize()
                .clipScreenCorners(settingsPageClipShape)
        } else {
            Modifier
        }
    val settingsTopBarClipModifier =
        if (mainTabTransitionAnimated) {
            Modifier.clipDuringTransition(settingsTopBarClipShape, settingsCardOnScreen)
        } else {
            Modifier
        }

    val coroutineScope = rememberCoroutineScope()
    val mainTabAnimation = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val mainTabGeneration = remember { intArrayOf(0) }
    val onMainTabSelected: (Int) -> Unit = { index ->
        if (index in 0 until mainPagerPageCount &&
            (index != selectedTab || mainPagerState.currentPage != index)
        ) {
            if (index != selectedTab) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            val generation = ++mainTabGeneration[0]
            mainTabAnimation[0]?.cancel()
            mainTabProgrammatic = true
            selectedTab = index
            mainTabAnimation[0] = coroutineScope.launch {
                try {
                    if (todayPagerState.isScrollInProgress) todayPagerState.cancelScroll()
                    if (pagerState.isScrollInProgress) pagerState.cancelScroll()
                    if (mainPagerState.isScrollInProgress) mainPagerState.cancelScroll()
                    if (navBarStyle == "rail") mainPagerState.scrollToPage(index)
                    else mainPagerState.animateMainTabTo(index)
                    FeatureLog.t("主页", "tab", "idx=$index shift=$isShiftMode rail=${navBarStyle == "rail"}")
                } finally {
                    if (mainTabGeneration[0] == generation) mainTabProgrammatic = false
                }
            }
        }
    }
    val latestMainTabSelected by rememberUpdatedState(onMainTabSelected)
    DisposableEffect(hostNavigation) {
        val callback: (Int) -> Unit = { latestMainTabSelected(it) }
        hostNavigation?.onTabSelected = callback
        onDispose {
            hostNavigation?.let { host ->
                if (host.onTabSelected === callback) host.onTabSelected = null
            }
        }
    }
    SideEffect {
        hostNavigation?.let { host ->
            host.selectedTab = selectedTab
            host.tabCount = if (isShiftMode) 2 else 3
            host.shiftMode = isShiftMode
            host.showsNativeTabBar = nativeBottomBar
            host.isTabletLayout = isTablet
            host.isDark = effectiveIsDark
            // 需由Swift 读这个值自行施加同样变换（对齐 Android 上 Compose 底栏的行为）
            host.cutoutScale = cutoutMainScale.value
            host.cutoutCenterYRatio = cutoutCenterYRatio
        }
    }
    // 外观页弹窗让位时主内容的纵向位移，改由主内容自己的 graphicsLayer 块直推
    // 引用固定，避免每帧新建 lambda 让 iOS 侧 update() 比对失败。
    val pushCutoutChromeOffset = remember(hostNavigation) {
        { offsetY: Double -> hostNavigation?.onCutoutChromeOffsetY?.invoke(offsetY) }
    }
    // 内容区（切天/周）一有横向手势：立刻取消未完成的主 tab 平移，吸回当前页，避免露出隔壁页
    val cancelUnfinishedMainTabAnim by rememberUpdatedState {
        if (mainPagerState.isScrollInProgress ||
            abs(mainPagerState.currentPageOffsetFraction) > 0.001f
        ) {
            val target = mainPagerState.currentPage.coerceIn(
                0,
                (mainPagerState.pageCount - 1).coerceAtLeast(0)
            )
            coroutineScope.launch {
                mainPagerState.cancelScroll()
                mainPagerState.scrollToPage(target)
                // 底栏与当前页对齐，避免 tab 还停在未完成的目标页
                if (selectedTab != target) selectedTab = target
            }
        }
    }
    // 固定实例：lambda 每次重组都新建，不能拿它当 remember key
    val mainContentNestedScroll = remember(mainPagerState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 横向（手机主 tab）与纵向（平板主 tab）手势都取消未完成的切页动画
                if (source == NestedScrollSource.UserInput &&
                    (abs(available.x) > 1f || abs(available.y) > 1f)
                ) {
                    cancelUnfinishedMainTabAnim()
                }
                return Offset.Zero
            }
        }
    }
    val triggerLandRipple: (Offset) -> Unit = { center ->
        landRippleCenter = center
        landRippleToken++
    }
    val stopConflictHover: () -> Unit = {
        conflictHoverBobJob?.cancel()
        conflictHoverBobJob = null
        isConflictHover = false
    }
    val clearSwapFlight: () -> Unit = {
        swapFlightVisible = false
        swapFlightCourse = null
    }
    // 退场动画结束后再清状态，让原卡片 alpha 恢复 1
    val dismissFloatingCard: () -> Unit = {
        stopConflictHover()
        clearSwapFlight()
        coroutineScope.launch {
            floatingScale.animateTo(1f, tween(durationMillis = 180))
            isDraggingCard = false
            floatingCardVisible = false
            draggingCourseIds = emptySet()
            draggedCardCourse = null
            dragMotion.reset()
            pendingDropTarget = null
            isSnapping = false
            // 重置入场初值，避免下次首帧残留 1.0 造成抖动
            floatingScale.snapTo(0.94f)
            floatingOffsetX.floatValue = 0f
            floatingOffsetY.floatValue = 0f
        }
    }

    // 粘贴飞行：前半升到 peakScale，中段回 1.0，末段落地轻弹
    fun pasteFlightScaleAt(raw: Float, peakScale: Float): Float {
        val growSpan = 0.5f
        val landStart = 0.86f
        return when {
            raw <= growSpan -> 1f + (peakScale - 1f) * (raw / growSpan)
            raw <= landStart -> {
                val s = (raw - growSpan) / (landStart - growSpan)
                peakScale + (1f - peakScale) * s
            }
            else -> {
                val local = (raw - landStart) / (1f - landStart)
                1f + 0.05f * sin(PI * local).toFloat()
            }
        }
    }

    // 网格几何：分界缝只在开启时计入；节次 top / 课程视觉高度用于跨午休/晚修落点
    fun dividerPxFor(geom: ScheduleGridGeometry): Float =
        if (geom.showBreakDividers) with(density) { 24.dp.toPx() } else 0f

    fun sectionTopPx(geom: ScheduleGridGeometry, section: Int, dividerPx: Float): Float {
        // 优先用网格布局的实际节次顶（含特殊块挤占）；下面的现算公式漏掉特殊块高度，
        // 会让长按浮层与吸附落点整体偏上，偏移量等于该节次上方的特殊块总高。
        geom.sectionTopDp[section]?.let { dp -> return with(density) { dp.dp.toPx() } }
        val sectionH = geom.sectionHeightPx
        if (sectionH <= 0f) return 0f
        val morning = geom.morningSections
        val afternoon = geom.afternoonSections
        return when {
            section <= morning -> (section - 1) * sectionH
            section <= morning + afternoon ->
                morning * sectionH + dividerPx + (section - morning - 1) * sectionH
            else ->
                morning * sectionH + dividerPx + afternoon * sectionH + dividerPx +
                    (section - morning - afternoon - 1) * sectionH
        }
    }

    fun courseVisualHeightPx(
        geom: ScheduleGridGeometry,
        startSection: Int,
        endSection: Int,
        dividerPx: Float
    ): Float {
        val sectionH = geom.sectionHeightPx
        if (sectionH <= 0f) return 0f
        val top = sectionTopPx(geom, startSection, dividerPx)
        val bottom = sectionTopPx(geom, endSection.coerceAtLeast(startSection), dividerPx) + sectionH
        return (bottom - top).coerceAtLeast(sectionH)
    }

    // 目标落点卡片中心绝对坐标（root px），供吸附动画
    fun computeTargetCenter(dayOfWeek: Int, startSection: Int, sectionSpan: Int): Offset? {
        val geom = gridGeometry ?: return null
        val bounds = geom.dayBounds[dayOfWeek] ?: return null
        if (bounds.size < 3) return null
        val centerX = (bounds[0] + bounds[1]) / 2f
        val topY = bounds[2]
        val dividerPx = dividerPxFor(geom)
        val startTop = sectionTopPx(geom, startSection, dividerPx)
        val endSection = startSection + sectionSpan
        val visualH = courseVisualHeightPx(geom, startSection, endSection, dividerPx)
        val cardCenterY = topY + startTop + visualH / 2f
        return Offset(centerX, cardCenterY)
    }

    /**
     * 列星期 → 数据（星期, 周次）：调课日列渲染的是 followWeekday/followWeek 的课，
     * 落点写库、冲突检测都要走这个映射，几何仍用列星期；映射缺失时退回列星期+浏览周。
     */
    fun dataPositionForColumn(columnDay: Int): Pair<Int, Int> =
        gridGeometry?.columnDataPosition?.get(columnDay) ?: (columnDay to currentViewingWeek)

    /** 数据（星期, 周次）→ 列星期：几何反查（粘贴飞行起点、交换飞行原点），找不到退回数据星期 */
    fun columnForDataPosition(day: Int, week: Int): Int =
        gridGeometry?.columnDataPosition?.entries
            ?.firstOrNull { it.value.first == day && it.value.second == week }?.key ?: day

    // spring 带回弹吸进目标格，中段开涟漪
    val snapFloatingCardToTarget: (dayOfWeek: Int, startSection: Int, sectionSpan: Int) -> Unit =
        { day, section, span ->
            val targetCenter = computeTargetCenter(day, section, span)
            if (targetCenter != null) {
                val targetOffsetX = targetCenter.x - draggedCardPosition.x
                val targetOffsetY = targetCenter.y - draggedCardPosition.y
                coroutineScope.launch {
                    isSnapping = true
                    // 拖拽期已在 floatingOffset* 上 snapTo，无需再从 draggedCardOffset 拷贝
                    launch {
                        delay(120.milliseconds)
                        triggerLandRipple(targetCenter)
                    }
                    val snapSpec = spring<Float>(
                        dampingRatio = 0.58f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                    val scaleSpec = spring<Float>(
                        dampingRatio = 0.7f,
                        stiffness = Spring.StiffnessMedium
                    )
                    val jobX = launch {
                        animateFloatState(floatingOffsetX, targetOffsetX, snapSpec, dragMotion.velocityX)
                    }
                    val jobY = launch {
                        animateFloatState(floatingOffsetY, targetOffsetY, snapSpec, dragMotion.velocityY)
                    }
                    val jobScale = launch {
                        floatingScale.animateTo(1f, scaleSpec)
                    }
                    jobX.join(); jobY.join(); jobScale.join()
                    isDraggingCard = false
                    floatingCardVisible = false
                    draggingCourseIds = emptySet()
                    draggedCardCourse = null
                    dragMotion.reset()
                    pendingDropTarget = null
                    isSnapping = false
                    floatingScale.snapTo(0.94f)
                    floatingOffsetX.floatValue = 0f
                    floatingOffsetY.floatValue = 0f
                }
            } else {
                dismissFloatingCard()
            }
        }

    // 粘贴飞行：复用长按浮层直线飞向目标格；无法定位时直接回调
    val playPasteFlightAnimation: (
        source: Course,
        targetDay: Int,
        targetSection: Int,
        onFinished: () -> Unit
    ) -> Unit = { source, targetDay, targetSection, onFinished ->
        val span = (source.endSection - source.startSection).coerceAtLeast(0)
        // 起点按**列**取几何：源课若是从调课日列复制来的，数据星期对不上列
        val sourceColumn = columnForDataPosition(source.dayOfWeek, currentViewingWeek)
        val sourceCenter = computeTargetCenter(sourceColumn, source.startSection, span)
        val targetCenter = computeTargetCenter(targetDay, targetSection, span)
        val sourceBounds = gridGeometry?.dayBounds?.get(sourceColumn)
        val sectionH = gridGeometry?.sectionHeightPx
        if (sourceCenter != null && targetCenter != null && sourceBounds != null && sectionH != null && sectionH > 0f) {
            coroutineScope.launch {
                delay(120.milliseconds) // 等弹窗退场
                // 原卡片保持可见（复制语义）
                draggedCardCourse = source
                draggedWeek = currentViewingWeek
                draggedCardPosition = sourceCenter
                dragMotion.reset()
                // 与 CourseCard 默认 2dp padding 对齐
                val cardPadPx = with(density) { 2.dp.toPx() }
                draggedCardSize = Offset(
                    (sourceBounds[1] - sourceBounds[0] - cardPadPx * 2f).coerceAtLeast(1f),
                    (span + 1) * sectionH
                )
                isPasteFlight = true
                isSnapping = true
                floatingCardVisible = true
                floatingOffsetX.floatValue = 0f
                floatingOffsetY.floatValue = 0f
                floatingScale.snapTo(1f)

                val dx = targetCenter.x - sourceCenter.x
                val dy = targetCenter.y - sourceCenter.y
                val peakScale = 1.4f
                val durationNanos = 480_000_000L
                val moveEase = CubicBezierEasing(0.55f, 0f, 0.45f, 1f)
                var rippleFired = false
                val startNanos = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    val raw = ((now - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
                    val t = moveEase.transform(raw)
                    floatingOffsetX.floatValue = dx * t
                    floatingOffsetY.floatValue = dy * t
                    floatingScale.snapTo(pasteFlightScaleAt(raw, peakScale))
                    // 快落地时先开涟漪
                    if (!rippleFired && raw >= 0.8f) {
                        rippleFired = true
                        triggerLandRipple(targetCenter)
                    }
                    if (raw >= 1f) break
                }

                // 落地后立刻换成真实课程，避免双影
                floatingCardVisible = false
                isSnapping = false
                isPasteFlight = false
                draggedCardCourse = null
                floatingOffsetX.floatValue = 0f
                floatingOffsetY.floatValue = 0f
                floatingScale.snapTo(0.94f)
                onFinished()
            }
        } else {
            onFinished()
        }
    }

    // 原地长按松手，spring 吸回原位
    val snapFloatingCardToOrigin: () -> Unit = {
        // 吸回原位也算「落地」：锚点即长按瞬间的原格中心，落地节奏与吸到新格一致
        val originCenter = draggedCardPosition
        coroutineScope.launch {
            isSnapping = true
            launch {
                delay(120.milliseconds)
                triggerLandRipple(originCenter)
            }
            val snapSpec = spring<Float>(
                dampingRatio = 0.58f,
                stiffness = Spring.StiffnessMediumLow
            )
            val scaleSpec = spring<Float>(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessMedium
            )
            val jobX = launch {
                animateFloatState(floatingOffsetX, 0f, snapSpec, dragMotion.velocityX)
            }
            val jobY = launch {
                animateFloatState(floatingOffsetY, 0f, snapSpec, dragMotion.velocityY)
            }
            val jobScale = launch { floatingScale.animateTo(1f, scaleSpec) }
            jobX.join(); jobY.join(); jobScale.join()
            isDraggingCard = false
            floatingCardVisible = false
            draggingCourseIds = emptySet()
            draggedCardCourse = null
            dragMotion.reset()
            pendingDropTarget = null
            isSnapping = false
            floatingScale.snapTo(0.94f)
            floatingOffsetX.floatValue = 0f
            floatingOffsetY.floatValue = 0f
        }
    }

    // 粘贴节奏飞行：从悬停 offset 飞到 destOffset 后收起
    fun flyFloatingCardWithPasteMotion(destOffsetX: Float, destOffsetY: Float) {
        stopConflictHover()
        coroutineScope.launch {
            isSnapping = true
            isPasteFlight = true
            val startX = floatingOffsetX.floatValue
            val startY = floatingOffsetY.floatValue
            val peakScale = 1.4f
            val durationNanos = 480_000_000L
            val moveEase = CubicBezierEasing(0.55f, 0f, 0.45f, 1f)
            floatingScale.snapTo(1f)
            var rippleFired = false
            val startNanos = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val raw = ((now - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
                val t = moveEase.transform(raw)
                floatingOffsetX.floatValue = startX + (destOffsetX - startX) * t
                floatingOffsetY.floatValue = startY + (destOffsetY - startY) * t
                floatingScale.snapTo(pasteFlightScaleAt(raw, peakScale))
                if (!rippleFired && raw >= 0.8f) {
                    rippleFired = true
                    triggerLandRipple(
                        Offset(
                            draggedCardPosition.x + destOffsetX,
                            draggedCardPosition.y + destOffsetY
                        )
                    )
                }
                if (raw >= 1f) break
            }
            isDraggingCard = false
            floatingCardVisible = false
            draggingCourseIds = emptySet()
            draggedCardCourse = null
            pendingDropTarget = null
            isSnapping = false
            isPasteFlight = false
            floatingOffsetX.floatValue = 0f
            floatingOffsetY.floatValue = 0f
            floatingScale.snapTo(0.94f)
            clearSwapFlight()
        }
    }

    // 冲突取消：粘贴节奏飞回原位
    val flyFloatingCardHome: () -> Unit = {
        flyFloatingCardWithPasteMotion(0f, 0f)
    }

    // 覆盖确认：粘贴节奏飞到目标格
    val flyFloatingCardToDropTarget: () -> Unit = {
        val source = draggedCardCourse
        val target = pendingDropTarget
        if (source != null && target != null) {
            val span = source.endSection - source.startSection
            val targetCenter = computeTargetCenter(target.first, target.second, span)
            if (targetCenter != null) {
                flyFloatingCardWithPasteMotion(
                    targetCenter.x - draggedCardPosition.x,
                    targetCenter.y - draggedCardPosition.y
                )
            } else {
                dismissFloatingCard()
            }
        } else {
            dismissFloatingCard()
        }
    }

    // 交换双浮层交叉飞行（480ms / 1→1.4→1.0）
    val flySwapCards: (conflictCourse: Course) -> Unit = { conflictCourse ->
        stopConflictHover()
        val source = draggedCardCourse
        val dropTarget = pendingDropTarget
        if (source == null || dropTarget == null) {
            clearSwapFlight()
            dismissFloatingCard()
        } else {
            val sourceSpan = source.endSection - source.startSection
            val targetCenter = computeTargetCenter(dropTarget.first, dropTarget.second, sourceSpan)
            val occupiedSpan = conflictCourse.endSection - conflictCourse.startSection
            // 冲突课所在**列**：调课日列的数据星期 ≠ 列星期，几何必须按列取
            val conflictColumn = columnForDataPosition(
                conflictCourse.dayOfWeek,
                dataPositionForColumn(dropTarget.first).second
            )
            val occupiedCenter = computeTargetCenter(
                conflictColumn,
                conflictCourse.startSection,
                occupiedSpan
            )
            if (targetCenter == null || occupiedCenter == null) {
                clearSwapFlight()
                dismissFloatingCard()
            } else {
                coroutineScope.launch {
                    val bounds = gridGeometry?.dayBounds?.get(conflictColumn)
                    val cardPadPx = with(density) { 2.dp.toPx() }
                    swapFlightCourse = conflictCourse
                    swapFlightOriginCenter = occupiedCenter
                    swapFlightWidth = if (bounds != null && bounds.size >= 2) {
                        (bounds[1] - bounds[0] - cardPadPx * 2f).coerceAtLeast(1f)
                    } else {
                        draggedCardSize.x
                    }
                    swapFlightOffsetX.snapTo(0f)
                    swapFlightOffsetY.snapTo(0f)
                    swapFlightScale.snapTo(1f)
                    swapFlightVisible = true

                    isSnapping = true
                    isPasteFlight = true
                    floatingScale.snapTo(1f)
                    val srcStartX = floatingOffsetX.floatValue
                    val srcStartY = floatingOffsetY.floatValue
                    val srcEndX = targetCenter.x - draggedCardPosition.x
                    val srcEndY = targetCenter.y - draggedCardPosition.y
                    val tgtEndX = draggedCardPosition.x - occupiedCenter.x
                    val tgtEndY = draggedCardPosition.y - occupiedCenter.y

                    val peakScale = 1.4f
                    val durationNanos = 480_000_000L
                    val moveEase = CubicBezierEasing(0.55f, 0f, 0.45f, 1f)
                    var rippleFired = false
                    val startNanos = withFrameNanos { it }
                    while (true) {
                        val now = withFrameNanos { it }
                        val raw = ((now - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
                        val t = moveEase.transform(raw)
                        floatingOffsetX.floatValue = srcStartX + (srcEndX - srcStartX) * t
                        floatingOffsetY.floatValue = srcStartY + (srcEndY - srcStartY) * t
                        swapFlightOffsetX.snapTo(tgtEndX * t)
                        swapFlightOffsetY.snapTo(tgtEndY * t)
                        val scale = pasteFlightScaleAt(raw, peakScale)
                        floatingScale.snapTo(scale)
                        swapFlightScale.snapTo(scale)
                        if (!rippleFired && raw >= 0.8f) {
                            rippleFired = true
                            triggerLandRipple(targetCenter)
                        }
                        if (raw >= 1f) break
                    }

                    clearSwapFlight()
                    isDraggingCard = false
                    floatingCardVisible = false
                    draggingCourseIds = emptySet()
                    draggedCardCourse = null
                    pendingDropTarget = null
                    isSnapping = false
                    isPasteFlight = false
                    floatingOffsetX.floatValue = 0f
                    floatingOffsetY.floatValue = 0f
                    floatingScale.snapTo(0.94f)
                    swapFlightOffsetX.snapTo(0f)
                    swapFlightOffsetY.snapTo(0f)
                    swapFlightScale.snapTo(1f)
                }
            }
        }
    }

    // 由浮层中心点对齐网格，得出落点 (dayOfWeek, startSection)
    fun computeDropTarget(centerX: Float, firstSectionCenterY: Float): Pair<Int, Int>? {
        val geom = gridGeometry ?: return null
        if (geom.sectionHeightPx <= 0f) return null
        if (geom.dayBounds.isEmpty()) return null
        val day = geom.dayBounds.entries.firstOrNull { (columnDay, bounds) ->
            // 教学周重组的休课日列永远渲染为空，落进去的课在本页与今日页都不可见，直接拦掉
            columnDay !in geom.blockedColumns &&
                bounds.size >= 2 && centerX >= bounds[0] && centerX <= bounds[1]
        }?.key ?: return null
        val topY = geom.dayBounds[day]?.getOrNull(2) ?: return null
        val relY = firstSectionCenterY - topY
        if (relY < 0f) return null
        val sectionH = geom.sectionHeightPx

        // 优先用网格权威节次顶（sectionTopDp 含分界缝与特殊块挤占），与卡片排版、落点高亮保持一致；
        // 下面那套手写遍历漏掉特殊块，会让「实际落到的格子」与「高亮显示的格子」错开。
        val totalSections = geom.morningSections + geom.afternoonSections + geom.eveningSections
        if (geom.sectionTopDp.isNotEmpty() && totalSections > 0) {
            for (s in 1..totalSections) {
                val top = geom.sectionTopDp[s]?.let { with(density) { it.dp.toPx() } } ?: continue
                if (relY < top + sectionH) return day to s
            }
            // 落在最后一节之下：吸到最后一节
            return day to totalSections
        }

        val dividerH = with(density) { 24.dp.toPx() }
        var cursor = 0f
        for (s in 1..geom.morningSections) {
            if (relY < cursor + sectionH) return day to s
            cursor += sectionH
        }
        if (geom.showBreakDividers) cursor += dividerH
        val afternoonStart = geom.morningSections + 1
        for (i in 1..geom.afternoonSections) {
            val s = afternoonStart + i - 1
            if (relY < cursor + sectionH) return day to s
            cursor += sectionH
        }
        if (geom.showBreakDividers) cursor += dividerH
        val eveningStart = afternoonStart + geom.afternoonSections
        for (i in 1..geom.eveningSections) {
            val s = eveningStart + i - 1
            if (relY < cursor + sectionH) return day to s
            cursor += sectionH
        }
        return null
    }

    fun openCourseDetail(
        courses: List<Course>,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        cardLayer: TransitionLayerSnapshot?,
        fromToday: Boolean,
        courseIdToHide: String = courses.firstOrNull()?.id ?: "",
        targetWeek: Int = 0
    ) {
        detailCourses = courses
        detailCardLeft = cardLeft
        detailCardTop = cardTop
        detailCardWidth = cardWidth
        detailCardHeight = cardHeight
        detailFromToday = fromToday
        detailTargetWeek = targetWeek
        // 重新进入：上次退出若没来得及复位（异常路径），这里兜底
        detailExiting = false
        detailSnapshot = cardLayer?.takeIf {
            it.layer.size.width > 0 && it.layer.size.height > 0
        }?.let { source ->
            // Retain child layer dependencies above lazy and responsive source branches.
            retainedDetailLayer.record(density, detailLayerLayoutDirection, source.layer.size) {
                drawLayer(source.layer)
            }
            TransitionLayerSnapshot(retainedDetailLayer, source.bounds)
        }
        hiddenCourseIds = setOf(courseIdToHide)
        showDetail = true
        // 详情 morph 只做下层模糊，不做缩放（整屏离屏层是掉帧第一来源）
        coroutineScope.launch {
            managePageBlurRadius.animateTo(
                HostNavigationState.DETAIL_BACKDROP_BLUR_DP,
                animationSpec = tween(540, easing = OobeQuartOutEasing)
            )
        }
    }

    // 顶栏「课表外观」与长按按钮共用；所有入口都直接调这个，延迟统一在这里
    // （见 CUSTOMIZE_ENTRY_DELAY_MS），调用点不要再各自加 delay。
    val enterCustomizePage: () -> Unit = {
        FeatureLog.t("课表外观", "enter")
        coroutineScope.launch {
            delay(CUSTOMIZE_ENTRY_DELAY_MS.milliseconds)
            val screenW = windowInfo.containerSize.width.toFloat()
            customizeExitTargetScale = (screenW * 0.65f) / screenW
            val oldCombSnapshots = combinations.mapNotNull { it.snapshot }
            combinations = combinations.map { it.copy(snapshot = null) }
            delay(50.milliseconds)
            // 复制为独立 ARGB_8888 切断引用
            // 截不到（OOM）就不做开洞快照，直接照常进搭配页
            val captured = captureMainContentBitmap()
            val currentSnapshot = captured?.let { snap ->
                // ImageBitmap 本就是独立快照（toImageBitmap 读回），无需再 copy 一份
                snap
            }
            val oldCustomize = customizeSnapshot
            val oldCover = snapshotCoverBitmap
            customizeSnapshot = currentSnapshot
            // 先丢弃引用，下一帧再 recycle，避免仍在组合树中的 Image 读到已释放位图
            if (combinations.isNotEmpty()) {
                combinations = combinations.toMutableList().also {
                    it[0] = it[0].copy(snapshot = currentSnapshot)
                }
            }
            launch {
                delay(32.milliseconds)
                recycleSnapshot(oldCustomize, currentSnapshot, captured)
                recycleSnapshot(oldCover, currentSnapshot, captured)
                oldCombSnapshots.forEach { snap ->
                    recycleSnapshot(
                        snap,
                        currentSnapshot,
                        captured,
                        WallpaperStore.currentImageBitmap(),
                    )
                }
            }
            customizeExitScale.snapTo(1f)
            customizeExitAlpha.snapTo(1f)
            showCustomizePage = true
            isWindowCutoutActive = true
            pendingEnterCutout = true
            // 快照盖住开洞过渡，与主内容缩放同步后淡出
            customizeCoverActive = true
            customizeCoverScale.snapTo(1f)
            customizeCoverAlpha.snapTo(1f)
            // 丢弃未落盘的主题预览
            pendingScheduleThemeMode = null
            delay(CUSTOMIZE_COVER_HOLD_MILLIS.toLong())
            launch {
                customizeCoverScale.animateTo(
                    0.75f,
                    tween(CUSTOMIZE_COVER_SCALE_MILLIS, easing = CustomizePageEasing)
                )
            }
            launch {
                delay(CUSTOMIZE_COVER_SCALE_MILLIS.toLong())
                customizeCoverAlpha.animateTo(
                    0f,
                    tween(CUSTOMIZE_COVER_FADE_MILLIS, easing = FastOutSlowInEasing)
                )
                customizeCoverActive = false
            }
            originalCombinationIndex = 0
            originalWallpaperBitmap = wallpaperBitmap
            originalWallpaperOffset = wallpaperOffset
            originalWallpaperScale = wallpaperScale
            originalSnapshot = combinations.getOrNull(0)?.snapshot
            originalWallpaperIsLight = combinations.getOrNull(0)?.wallpaperIsLight
            originalCombination = combinations.getOrNull(0)
        }
    }

    // 选壁纸：系统选图器 + 降采样解码是 Android 能力（宿主注入），解出的 ImageBitmap 回填这里状态。
    val applyPickedWallpaper: (ImageBitmap?) -> Unit = { picked ->
        if (picked != null) {
            val oldWallpaper = wallpaperBitmap
            wallpaperBitmap = picked
            // 壁纸缓存与变换在共享层 WallpaperStore（桌面/iOS 读同一份）
            WallpaperStore.setCached(picked)
            wallpaperOffset = Offset.Zero
            val autoScale = WallpaperTransform.minScale(picked.width, picked.height, screenWPx, screenHPx)
            wallpaperScale = autoScale
            WallpaperStore.saveTransform(Offset.Zero, autoScale)
            val isLight = WallpaperStore.measureLightness(picked)
            val idx = currentCombinationIndex
            if (idx in combinations.indices) {
                combinations = combinations.toMutableList().also { list ->
                    list[idx] = list[idx].copy(
                        bitmap = picked,
                        // 清掉旧快照，搭配预览回退显示新壁纸，避免「选完没反应」
                        snapshot = null,
                        offset = Offset.Zero,
                        scale = autoScale,
                        wallpaperIsLight = isLight
                    )
                }
            }
            // 旧壁纸已离开当前 combination/显示引用后再回收（避开缓存与仍被展示的同一对象）
            coroutineScope.launch {
                delay(32.milliseconds)
                recycleSnapshot(
                    oldWallpaper,
                    picked,
                    WallpaperStore.currentImageBitmap(),
                    wallpaperBitmap,
                    savedWallpaperBitmap,
                    originalWallpaperBitmap,
                    *combinations.mapNotNull { c -> c.bitmap }.toTypedArray(),
                )
            }
        }
    }

    var showSwitchSchedule by remember { mutableStateOf(false) }
    var switchPendingReverse by remember { mutableStateOf(false) }
    var switchExiting by remember { mutableStateOf(false) }
    var switchMainLayerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Keep the destination hidden until its anchor and the source layer are ready.
    var switchPreparingLayer by remember { mutableStateOf(false) }
    // Re-record the source layer before returning to a different schedule.
    var scheduleChanged by remember { mutableStateOf(false) }
    var showMorePopup by remember { mutableStateOf(false) }
    var showTodayMorePopup by remember { mutableStateOf(false) }
    // 按钮原地随菜单展开淡出，形成"按钮连贯变为菜单"的单一动画
    val morePopupFraction = remember { Animatable(0f) }
    val todayMorePopupFraction = remember { Animatable(0f) }
    // 「更多」占位槽实测顶部位置（px，root 坐标）：布局期上报，菜单据此定位，避免首帧 inset 未到而偏位
    val moreSlotTopPx = remember { mutableFloatStateOf(0f) }
    // 占位槽在开洞/切页期间仍在顶层顶栏里，照常采样：关掉采样会让 moreSlotTopPx
    // 冻结在进页前旧高度，菜单错位且仍可点。
    val canSampleMoreSlot = !isWindowCutoutActive
    // 顶栏滚动材质透明度；两个顶栏各自一份，避免切页时互相覆盖。
    // 初值 0 而非 1f：真实值由 SideEffect 首帧写入，用 1f 会让「更多」先亮一帧。
    val scheduleMaterialAlpha = remember { mutableStateOf(0f) }
    val todayMaterialAlpha = remember { mutableFloatStateOf(0f) }
    var todayJumpToDateTrigger by remember { mutableIntStateOf(0) }

    val isViewingCurrentWeek = currentViewingWeek == currentWeek

    fun openScheduleLibrary() {
        if (isShiftMode || (showSwitchSchedule && !switchExiting)) return
        switchExiting = false
        switchAnimForward = true
        if (showSwitchSchedule) {
            // Interrupt the exit at its current progress; keep the live page and snapshots.
            switchAnimJob?.cancel()
            switchPendingReverse = false
            switchPreparingLayer = false
            switchAnimRunning = true
            // 这也是「进入」方向（p→0），卡片来源同样是上次退出固化的位图
            switchCardSnapshot = null
            switchCardBitmap = retainedSwitchCardBitmap
            switchAnimJob = coroutineScope.launch {
                switchAnimProgress.animateTo(0f, tween(320, easing = OobeCubicOutEasing))
                switchOverlayActive = false
                switchCardSnapshot = null
                switchCardBitmap = null
                switchCardBounds = null
                switchAnimRunning = false
            }
        } else {
            switchPendingReverse = true
            switchPreparingLayer = true
            showSwitchSchedule = true
            switchMainLayerJob = coroutineScope.launch {
                prepareMainContentLayer()
            }
        }
    }

    DisposableEffect(hostNavigation) {
        onDispose {
            hostNavigation?.let { host ->
                host.scheduleLibraryActive = false
                host.scheduleLibraryTransitionActive = false
                host.onOpenSwitchSchedule = null
                host.onJumpWeek = null
                host.onCourseManage = null
                host.onEnterCustomize = null
                host.onJumpToDate = null
                host.keepsNativeHeaderDuringCutout = false
                host.updateDetailBackgroundBlur(0f)
            }
        }
    }
    SideEffect {
        hostNavigation?.let { host ->
            host.scheduleLibraryActive = nativeBottomBar && !isTablet && showSwitchSchedule && !switchExiting
            host.scheduleLibraryTransitionActive = switchPreparingLayer || switchAnimRunning || switchPendingReverse ||
                (showSwitchSchedule && switchExiting)
            // ⚠ 不含 `!showDetail`：详情页盖满全屏，原生顶栏本就被挡住。
            host.showsNativeHeader = nativeBottomBar && !isTablet && !isWindowCutoutActive &&
                !showCustomizePage && (!showSwitchSchedule || switchExiting)
            // 课表外观页（含开洞进出场）：原生顶栏不清空，宿主拿 cutoutScale 自己施加与
            // 主内容/底栏同一套变换 —— 右上角菜单跟着一起移动缩小，而不是消失。
            host.keepsNativeHeaderDuringCutout = nativeBottomBar && !isTablet &&
                (showCustomizePage || isWindowCutoutActive)
            host.onOpenSwitchSchedule = { openScheduleLibrary() }
            host.onJumpWeek = { viewModel.showJumpWeekDialog() }
            host.onCourseManage = {
                router.navigate(AppRoute.CourseManage)
            }
            host.onEnterCustomize = { enterCustomizePage() }
            host.onJumpToDate = { todayJumpToDateTrigger++ }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (showCustomizePage) Color(0xFF1A1A1A) else MiuixTheme.colorScheme.surface)
            .drawWithContent {
                drawContent()

            }) {
        val displayAppearance =
            // 开洞前显示已应用外观，避免快照过渡期闪编辑值
            if (showCustomizePage && !isWindowCutoutActive) {
                originalCombination?.let { AppearanceConfig.fromCombination(it) }
                    ?: currentAppearance()
            } else {
                currentAppearance()
            }
        // 顶栏/底栏必须采主内容 liquidGlass，否则玻璃退化成纯色
        val chromeBackdrop: com.kyant.backdrop.Backdrop = liquidGlassBackdrop
        val isEntryAnimating = showSwitchSchedule && switchAnimForward && switchAnimRunning
        // The source remains rendered underneath the morph, including across controller frames.
        val mainContentAlpha = 1f

        // liquidGlass 录制跳帧指纹：结构量变了才 update→markNeedsRecord。
        // 必须 remember 出稳定 List，否则 equals 失败会强制录制。
        // 用 pager 当前页而非 selectedTab：点击后 selectedTab 先变，真正像素落定在 currentPage。
        // pagerState.currentPage：无壁纸周滑跳过 mustRecord 后，靠落页变 key 补录。
        val themeBackdropKey = listOf(
            MiuixTheme.isDynamicColor, MiuixTheme.colorScheme.background,
            MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.secondaryContainer,
            MiuixTheme.colorScheme.tertiaryContainer,
        )
        val liquidGlassRecordKey = remember(
            mainPagerState.currentPage, isShiftMode, showDetail, showCustomizePage, showSwitchSchedule,
            isWindowCutoutActive, shortcutMenuVisible, isDraggingCard, floatingCardVisible,
            dataVersion, currentWeek, totalWeeks, currentCombinationIndex, effectiveIsDark,
            wallpaperBitmap, TabletNavSideState.expanded, scheduleShowCourseDetail.value,
            pagerState.currentPage, themeBackdropKey
        ) {
            listOf(
                mainPagerState.currentPage, isShiftMode, showDetail, showCustomizePage, showSwitchSchedule,
                isWindowCutoutActive, shortcutMenuVisible, isDraggingCard, floatingCardVisible,
                dataVersion, currentWeek, totalWeeks, currentCombinationIndex, effectiveIsDark,
                wallpaperBitmap, TabletNavSideState.expanded, scheduleShowCourseDetail.value,
                pagerState.currentPage, themeBackdropKey
            )
        }
        // 局部 val 不能直接捕获进 remember lambda，一律经 rememberUpdatedState
        val latestCutoutScale by rememberUpdatedState(cutoutMainScale.value)
        val latestSheetOffset by rememberUpdatedState(sheetOffsetY.value)
        val latestShortcutBlur by rememberUpdatedState(shortcutMenuBlurRadius.value)
        val latestManageBlur by rememberUpdatedState(managePageBlurRadius.value)
        val latestCustomizeExitScale by rememberUpdatedState(customizeExitScale.value)
        val latestCustomizeCoverScale by rememberUpdatedState(customizeCoverScale.value)
        val latestCustomizeCoverAlpha by rememberUpdatedState(customizeCoverAlpha.value)
        val latestIsWindowCutout by rememberUpdatedState(isWindowCutoutActive)
        val latestShowCustomize by rememberUpdatedState(showCustomizePage)
        val latestIsCustomizeExiting by rememberUpdatedState(isCustomizeExiting)
        val latestDraggingCard by rememberUpdatedState(isDraggingCard)
        val latestRailBlurEpoch by rememberUpdatedState(railBlurResampleEpoch.intValue)
        // ⚠ 必须 remember 缓存：asImageBitmap() 每次调用都新建包装对象，每帧新建会让
        // Image 的位图纹理反复重建。Bitmap 生命周期管理里的 `===` 身份比较一律不动。
        val wallpaperImage = remember(wallpaperBitmap) { wallpaperBitmap }
        val originalWallpaperImage = originalWallpaperBitmap
        // 课表/今日/设置滚动时主内容像素在变，必须重录，否则顶栏/底栏玻璃冻结。
        // 周左右滑**不**重录，有无壁纸一视同仁：落页靠 recordKey 里的
        // pagerState.currentPage 补一帧，玻璃不会永久停在旧帧。
        // 「冻结采样、滚动时玻璃滞后」的既有取舍一致，不是新引入的行为。
        val liquidGlassMustRecord = remember(scheduleScrollState, todayListScrollInProgress, pagerState, todayPagerState, mainPagerState) {
            var lastRailExpand = Float.NaN
            var lastBlurEpoch = -1
            {
                // 侧栏进度只在 draw 阶段读，不进组合
                val expand = TabletNavSideState.expandProgress.floatValue
                val expandMoving = expand != lastRailExpand
                lastRailExpand = expand
                val epochBumped = latestRailBlurEpoch != lastBlurEpoch
                lastBlurEpoch = latestRailBlurEpoch
                val forceRail = railForceRecordFrames[0] > 0
                if (forceRail) railForceRecordFrames[0] = railForceRecordFrames[0] - 1
                scheduleScrollState.isScrollInProgress ||
                    todayListScrollInProgress.value ||
                    // ⚠ 周左右滑（pagerState）刻意不在此列 —— 与无壁纸时行为一致。
                    // 落页由 recordKey 里的 pagerState.currentPage 补一帧。
                    todayPagerState.isScrollInProgress ||
                    mainPagerState.isScrollInProgress ||
                    expandMoving ||
                    forceRail ||
                    epochBumped ||
                    // 开洞编辑时主内容持续缩放，绝不能停录
                    latestIsWindowCutout ||
                    (latestShowCustomize && latestIsCustomizeExiting) ||
                    // Morph progress does not change the source backdrop.
                    latestShortcutBlur > 0.01f ||
                    latestManageBlur > 0.01f ||
                    // 非静止端点视为动画进行中
                    (latestCutoutScale != 1f && latestCutoutScale != 0.75f) ||
                    latestSheetOffset != 0f ||
                    latestCustomizeExitScale != 1f ||
                    latestCustomizeCoverScale != 1f ||
                    latestCustomizeCoverAlpha != 1f ||
                    // 拖拽课程卡片时像素每帧变
                    latestDraggingCard
            }
        }
        // 连贯动画期间下层的实时模糊半径（dp）：长按菜单优先，其次详情 morph；与宿主侧口径一致
        fun currentDetailBlurDp(): Float =
            if (shortcutMenuBlurRadius.value > 0.01f) shortcutMenuBlurRadius.value
            else managePageBlurRadius.value

        // 主内容（带模糊和裁切）
        // 空闲态不挂 graphicsLayer：全屏离屏合成是周滑 P50 主头之一。
        // 仅模糊/透明度/位移/裁切真正非单位时才开层；derivedStateOf 避免 Animatable 每帧重组。
        // 模糊与缩放共用同一个 graphicsLayer：renderEffect 在绘制阶段读半径，组合期不依赖它
        //（`Modifier.blur` 会在组合期读 dp 值，半径每帧变 = 每帧失效整个主内容 scope）。
        val mainLayerNeeded by remember {
            derivedStateOf {
                isCustomizeExiting ||
                    isWindowCutoutActive ||
                    showSwitchSchedule ||
                    showCustomizePage ||
                    mainContentAlpha != 1f ||
                    shortcutMenuBlurRadius.value > 0.01f ||
                    managePageBlurRadius.value > 0.01f ||
                    cutoutMainScale.value != 1f ||
                    customizeExitScale.value != 1f ||
                    sheetOffsetY.value != 0f
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (mainLayerNeeded) {
                        Modifier.graphicsLayer {
                            val blurAnim = currentDetailBlurDp()
                            // renderEffect 需要 Android 12+，低版本退化为无模糊
                            renderEffect = if (blurAnim > 0.01f && isRenderEffectSupported()) {
                                // 不能用 `blurAnim * density`：外层同名 Density 局部量会遮蔽隐式 receiver
                                val blurPx = blurAnim.dp.toPx()
                                // 同 Modifier.blur 的 Clamp 边界处理
                                BlurEffect(null, blurPx, blurPx, TileMode.Clamp)
                            } else null
                            val exitScale = if (isCustomizeExiting) customizeExitScale.value else 1f
                            val cutoutScale = cutoutMainScale.value
                            // 开洞时由 cutoutScale 控制；进出场统一由快照覆盖层处理，避免叠加闪烁
                            val effectiveScale = if (isCustomizeExiting && isWindowCutoutActive) {
                                cutoutScale
                            } else {
                                exitScale * cutoutScale
                            }
                            scaleX = effectiveScale
                            scaleY = effectiveScale
                            alpha = mainContentAlpha
                            // 与 CustomizeScheduleScreen 共享同一 Animatable，像素级同帧
                            val sheetScale = cutoutMainScale.value
                            val sheetScaleProg = ((sheetScale - 0.65f) / (1f - 0.65f)).coerceIn(0f, 1f)
                            val contentOffsetY = sheetOffsetY.value * (1f - sheetScaleProg)
                            translationY = contentOffsetY
                            // 同一个 graphicsLayer 块在**绘制阶段**跑
                            pushCutoutChromeOffset((contentOffsetY / density.density).toDouble())
                            if (isCustomizeExiting) {
                                transformOrigin = TransformOrigin(0.5f, 0.58f)
                            }
                            if (isWindowCutoutActive) {
                                // 使用 CustomizeScheduleScreen 传回的裁剪中心比例，保证两者完全对齐
                                transformOrigin = TransformOrigin(0.5f, cutoutCenterYRatio)
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .then(
                    // 下层不再缩放，无需按缩放重裁圆角；仅搭配页退出时裁成屏幕圆角
                    Modifier.drawWithContent {
                        val animClipPx =
                            if (isCustomizeExiting) screenCornerRadius else ScreenCornerRadiiPx.Zero
                        if (animClipPx.maxRadius > 0f) {
                            val path = Path().apply {
                                addScreenCornerRect(
                                    width = size.width,
                                    height = size.height,
                                    corners = animClipPx
                                )
                            }
                            clipPath(path) {
                                this@drawWithContent.drawContent()
                            }
                        } else {
                            drawContent()
                        }
                    }
                )
                .then(
                    Modifier.drawWithContent {
                        // 逐帧把页面级模糊半径推给宿主（iOS 原生 chrome 与弹窗层不在主内容模糊层内）
                        hostNavigation?.updateDetailBackgroundBlur(managePageBlurRadius.value)
                        // 仅在被请求时录制一帧，避免每帧重绘整棵主内容树
                        if (mainSnapshotRequester.lastRecordedToken != mainSnapshotToken) {
                            screenGraphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            mainSnapshotRequester.lastRecordedToken = mainSnapshotToken
                            drawLayer(screenGraphicsLayer)
                        } else {
                            drawContent()
                        }
                    }
                )
        ) {
            val hostNav = LocalHostNavigation.current
            val nativeBarInset = (hostNav?.topBarInset ?: 0.0).dp
            val scaffoldContent = @Composable {
                Scaffold(
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).union(WindowInsets(
                                left = (hostNav?.leftSafeAreaInset ?: 0.0).toFloat().dp,
                                right = (hostNav?.rightSafeAreaInset ?: 0.0).toFloat().dp,
                            ))
                        )
                        .consumeWindowInsets(PaddingValues(top = nativeBarInset)),
                    bottomBar = {
                        // 平板导航在叠层；这里不占 Scaffold bottomBar，避免全屏挡住内容滚动
                        if (navBarStyle != "rail" && !nativeBottomBar) {
                            ScheduleBottomBar(
                                navBarStyle = navBarStyle,
                                isShiftMode = isShiftMode,
                                selectedTab = selectedTab,
                                onTabSelected = onMainTabSelected,
                                liquidGlassBackdrop = chromeBackdrop,
                            )
                        }
                    },
                    topBar = {
                        // 标题可见性只在页码/方向变化时重组；平移读 pager 写在 graphicsLayer，
                        // 避免动画每帧重组顶栏及 ScheduleTopBar 整棵树。
                        val showTodayTitle by remember(isShiftMode, mainPagerState) {
                            derivedStateOf {
                                if (isShiftMode) {
                                    false
                                } else {
                                    val page = mainPagerState.currentPage
                                    val off = mainPagerState.currentPageOffsetFraction
                                    page == 0 || (page == 1 && off < 0f)
                                }
                            }
                        }
                        val showScheduleTitle by remember(isShiftMode, mainPagerState) {
                            derivedStateOf {
                                val page = mainPagerState.currentPage
                                val off = mainPagerState.currentPageOffsetFraction
                                if (isShiftMode) {
                                    page == 0 || (page == 1 && off < 0f)
                                } else {
                                    page == 1 ||
                                        (page == 0 && off > 0f) ||
                                        (page == 2 && off < 0f)
                                }
                            }
                        }
                        val showSettingsTitle by remember(isShiftMode, mainPagerState) {
                            derivedStateOf {
                                val page = mainPagerState.currentPage
                                val off = mainPagerState.currentPageOffsetFraction
                                if (isShiftMode) {
                                    page == 1 || (page == 0 && off > 0f)
                                } else {
                                    page == 2 || (page == 1 && off > 0f)
                                }
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (showScheduleTitle) {
                                val scheduleTitleIndex = if (isShiftMode) 0 else 1
                                // 空闲停在本页时 delta=0：不挂 graphicsLayer，免掉顶栏整棵离屏合成。
                                // 切 tab 过程中才开层，用 size.width 跟 pager 对齐。
                                val scheduleTitleIdle by remember(scheduleTitleIndex, navBarStyle) {
                                    derivedStateOf {
                                        val page = mainPagerState.currentPage
                                        val off = mainPagerState.currentPageOffsetFraction
                                        (scheduleTitleIndex - page - off) == 0f
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (scheduleTitleIdle) {
                                                Modifier
                                            } else {
                                                Modifier.graphicsLayer {
                                                    val page = mainPagerState.currentPage
                                                    val off = mainPagerState.currentPageOffsetFraction
                                                    val delta = scheduleTitleIndex - page - off
                                                    if (useVerticalMainPager) {
                                                        translationX = 0f
                                                        translationY = delta * screenHPx
                                                    } else {
                                                        translationY = 0f
                                                        translationX = delta * size.width
                                                    }
                                                }
                                            }
                                        )
                                        // 滑出中的课表糊带同样裁顶部两角：否则它在卡片左缘留一条
                                        // 方切口，卡片上方的圆角被它顶掉。
                                        .then(settingsTopBarClipModifier)
                                ) {
                                    // 锁本页主题：共享顶栏槽外层主题跟 selectedTab 变，
                                    // 切到设置页（浅色）时课程表顶栏遮罩会跟着跳浅
                                    PageLockedTopBarTheme(
                                        if (captureThemeActive) captureThemeIsDark
                                        else wallpaperForcedDark
                                    ) {
                                        ScheduleTopBar(
                                            visible = true,
                                            pagerCurrentPage = pagerState.currentPage,
                                            currentWeek = currentWeek,
                                            isHoliday = viewingIsHoliday,
                                            dayRange = dayRange,
                                            currentDayOfWeek = currentDayOfWeek,
                                            isCurrentWeek = pagerState.currentPage + 1 == currentWeek && currentWeek in 1..totalWeeks,
                                            weekDates = weekDates,
                                            isReorganized = teachingWeekRules.isNotEmpty(),
                                            onOpenSwitchSchedule = { openScheduleLibrary() },
                                            onJumpWeek = { viewModel.showJumpWeekDialog() },
                                            onEnterCustomize = enterCustomizePage,
                                            isTablet = isTablet,
                                            isShiftMode = isShiftMode,
                                            liquidGlassBackdrop = chromeBackdrop,
                                            scrollBehavior = scheduleScrollBehavior,
                                            blurResampleKey = railBlurResampleEpoch.intValue,
                                            blurSampleTrack = com.haooz.chedule.ui.components.tabletNavExpandSampleTrack,
                                            onMoreSlotTop = { if (canSampleMoreSlot) moreSlotTopPx.value = it },
                                            onMoreMaterial = { scheduleMaterialAlpha.value = it },
                                            onMoreExpand = { showMorePopup = true },
                                        )
                                    }
                                }
                            }
                            if (showSettingsTitle) {
                                val settingsTitleIndex = if (isShiftMode) 1 else 2
                                val settingsTitleIdle by remember(settingsTitleIndex, navBarStyle) {
                                    derivedStateOf {
                                        val page = mainPagerState.currentPage
                                        val off = mainPagerState.currentPageOffsetFraction
                                        (settingsTitleIndex - page - off) == 0f
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (settingsTitleIdle) {
                                                Modifier
                                            } else {
                                                Modifier.graphicsLayer {
                                                    val page = mainPagerState.currentPage
                                                    val off = mainPagerState.currentPageOffsetFraction
                                                    val delta = settingsTitleIndex - page - off
                                                    if (useVerticalMainPager) {
                                                        translationX = 0f
                                                        translationY = delta * screenHPx
                                                    } else {
                                                        translationY = 0f
                                                        translationX = delta * size.width
                                                    }
                                                }
                                            }
                                        )
                                        // ⚠ 顶栏槽画在 pager **之上**：不跟着裁，
                                        // 这条全宽糊带会盖住卡片上方的两个圆角。
                                        .then(settingsTopBarClipModifier)
                                ) {
                                    SettingsTopBar(
                                        liquidGlassBackdrop = chromeBackdrop,
                                        navBarStyle = navBarStyle,
                                        scrollBehavior = settingsScrollBehavior,
                                        blurResampleKey = railBlurResampleEpoch.intValue,
                                        blurSampleTrack = com.haooz.chedule.ui.components.tabletNavExpandSampleTrack,
                                    )
                                }
                            }
                            // 始终渲染，保证 currentHeightPx 启动即就位，切页不慢一帧
                            // 空闲在本页时不挂层；离屏时用 graphicsLayer 位移+隐藏
                            val todayTitleIdle by remember(navBarStyle) {
                                derivedStateOf {
                                    val page = mainPagerState.currentPage
                                    val off = mainPagerState.currentPageOffsetFraction
                                    val delta = 0 - page - off
                                    delta == 0f && showTodayTitle
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (todayTitleIdle) {
                                            Modifier
                                        } else {
                                            Modifier.graphicsLayer {
                                                val page = mainPagerState.currentPage
                                                val off = mainPagerState.currentPageOffsetFraction
                                                val delta = 0 - page - off
                                                if (useVerticalMainPager) {
                                                    translationX = 0f
                                                    translationY = delta * screenHPx
                                                } else {
                                                    translationY = 0f
                                                    translationX = delta * size.width
                                                }
                                                alpha = if (showTodayTitle) 1f else 0f
                                            }
                                        }
                                    )
                            ) {
                                // 同 ScheduleTopBar：锁本页主题，避免切到设置页时今日页遮罩跳浅
                                PageLockedTopBarTheme(
                                    if (captureThemeActive) captureThemeIsDark
                                    else todayPageForcedDark
                                ) {
                                    TodayTopBar(
                                        liquidGlassBackdrop = chromeBackdrop,
                                        navBarStyle = navBarStyle,
                                        currentDayOfWeek = todaySelectedDayOfWeek,
                                        isToday = todayIsToday,
                                        onBackToToday = { scrollToTodayTrigger++ },
                                        onJumpToDate = { todayJumpToDateTrigger++ },
                                        onEnterCustomize = enterCustomizePage,
                                        scrollBehavior = todayScrollBehavior,
                                        visible = showTodayTitle,
                                        blurResampleKey = railBlurResampleEpoch.intValue,
                                        blurSampleTrack = com.haooz.chedule.ui.components.tabletNavExpandSampleTrack,
                                        onMoreSlotTop = { if (canSampleMoreSlot) moreSlotTopPx.value = it },
                                        onMoreMaterial = { todayMaterialAlpha.value = it },
                                        onMoreExpand = { showTodayMorePopup = true },
                                    )
                                }
                            }
                        }
                    },
                    // popup 槽按组合顺序绘制：「更多」排在 MiuixPopupHost 之前，
                    // 只高于顶栏按钮、低于弹窗，打开弹窗时会被遮罩一起压暗。
                    popupHost = {
                        // 单个「更多」控件随所在顶栏一起滑动：与顶栏同一套 delta 公式；
                        // 非当前页时整块被平移出屏幕，天然只留一份可见
                        val scheduleMoreMenuOffset: () -> Offset = {
                            val page = mainPagerState.currentPage
                            val off = mainPagerState.currentPageOffsetFraction
                            val delta = (if (isShiftMode) 0 else 1) - page - off
                            if (useVerticalMainPager) Offset(0f, delta * screenHPx)
                            else Offset(delta * screenWPx, 0f)
                        }
                        val todayMoreMenuOffset: () -> Offset = {
                            val page = mainPagerState.currentPage
                            val off = mainPagerState.currentPageOffsetFraction
                            // 排班模式下没有今日页，恒移出屏幕
                            val delta = if (isShiftMode) -1f else (-page - off)
                            if (useVerticalMainPager) Offset(0f, delta * screenHPx)
                            else Offset(delta * screenWPx, 0f)
                        }
                        // 不包 HostedOverlay：iOS 上浮层独立合成根拿不到主内容开洞缩放，
                        // 「更多」会不随顶栏缩小、盖在课表外观页上仍可点；留在本槽与顶栏按钮同层。
                        PageLockedTopBarTheme(wallpaperForcedDark) {
                        // LocalAppRouter.current 必须在组合层取（onClick lambda 里取不到）
                        val popupRouter = LocalAppRouter.current
                        MorePopupMenus(
                                    showMorePopup = showMorePopup,
                                    onMorePopupDismiss = { showMorePopup = false },
                                    onMorePopupExpand = { showMorePopup = true },
                                    showTodayMorePopup = showTodayMorePopup,
                                    onTodayMorePopupDismiss = { showTodayMorePopup = false },
                                    onTodayMorePopupExpand = { showTodayMorePopup = true },
                                    morePopupFraction = morePopupFraction,
                                    todayMorePopupFraction = todayMorePopupFraction,
                                    scheduleMenuOffset = scheduleMoreMenuOffset,
                                    todayMenuOffset = todayMoreMenuOffset,
                                    moreSlotTopPx = moreSlotTopPx,
                                    scheduleMaterialAlpha = scheduleMaterialAlpha.value,
                                    todayMaterialAlpha = todayMaterialAlpha.value,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    isShiftMode = isShiftMode,
                                    // 平板（rail）右上角不显示「更多」：跳转/管理/外观都在侧栏里。
                                    // iOS 主界面右上角那颗已由原生 UIMenu 接管，Compose 侧恒不渲染
                                    enabled = navBarStyle != "rail" && hostNavigation == null,
                                    // 预测性返回由 LiquidGlassDropdownMenu 内部驱动同一个 fraction Animatable，
                                    // 外面再 snapTo/animateTo 会和它互相 cancel，动画直接卡死。
                                    onJumpWeek = { viewModel.showJumpWeekDialog() },
                                    onCourseManage = {
                                        popupRouter.navigate(
                                            AppRoute.CourseManage
                                        )
                                    },
                                    onEnterCustomize = enterCustomizePage,
                                    onJumpToDate = { todayJumpToDateTrigger++ },
                                )
                        }
                        // 弹窗排在「更多」之后 → 始终盖在它之上，遮罩能压暗「更多」
                        MiuixPopupHost()
                    }
                ) { paddingValues ->
                    // Keep the real source rendered underneath the detail morph.
                    // 不门控 combinations.isEmpty()：网格只依赖 viewModel，与壁纸加载解耦
                    // backdrop 挂在未 padding 的全屏层：录制原点固定在屏幕
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 开洞时 mustRecord 恒 true：停录会让顶栏/底栏玻璃采样空内容
                            .liquidGlassLayerBackdrop(
                                backdrop = liquidGlassBackdrop,
                                recordKey = liquidGlassRecordKey,
                                mustRecord = liquidGlassMustRecord
                            )
                        ) {
                        // 共享壁纸层：铺满全屏（含侧栏下），与 rail 让位无关，侧栏伸缩时像素不动。
                            // 壁纸任何切页都钉死不动：课程表→设置靠「圆角设置卡片滑过来盖住壁纸」。
                            val sharedWallpaperBitmap =
                                if (showCustomizePage && !isWindowCutoutActive) originalWallpaperBitmap
                                else wallpaperBitmap
                            val sharedWallpaperOffset =
                                if (showCustomizePage && !isWindowCutoutActive) originalWallpaperOffset
                                else wallpaperOffset
                            val sharedWallpaperScale =
                                if (showCustomizePage && !isWindowCutoutActive) originalWallpaperScale
                                else wallpaperScale
                            // 可见性用 derivedStateOf：pager 每帧变 offset 时不重组父层/HorizontalPager
                            val showSharedWallpaperLayer by remember(
                                isShiftMode,
                                sharedWallpaperBitmap,
                                showCustomizePage,
                                mainPagerState,
                            ) {
                                derivedStateOf {
                                    // 搭配页/开洞编辑：必须始终显示壁纸层，否则选完壁纸「没反应」
                                    if (showCustomizePage && sharedWallpaperBitmap != null) {
                                        return@derivedStateOf true
                                    }
                                    val page = mainPagerState.currentPage
                                    val off = abs(mainPagerState.currentPageOffsetFraction)
                                    !isShiftMode && sharedWallpaperBitmap != null &&
                                        (page != 2 || off > 0.01f)
                                }
                            }
                            if (showSharedWallpaperLayer && sharedWallpaperBitmap != null) {
                                // 全屏垫背景色 + 裁切：页滑走露出的空隙由背景色/壁纸垫住（侧栏下始终有内容，不出漏）
                                val wallpaperPadColor =
                                    MiuixTheme.colorScheme.surface
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(wallpaperPadColor)
                                        .clipToBounds()
                                ) {
                                val sharedMinScale = remember(sharedWallpaperBitmap, screenWPx, screenHPx) {
                                    if (sharedWallpaperBitmap.width > 0 && sharedWallpaperBitmap.height > 0) {
                                        val fit = minOf(
                                            screenWPx / sharedWallpaperBitmap.width,
                                            screenHPx / sharedWallpaperBitmap.height
                                        )
                                        val cover = maxOf(
                                            screenWPx / sharedWallpaperBitmap.width,
                                            screenHPx / sharedWallpaperBitmap.height
                                        )
                                        if (fit > 0f) cover / fit else 1f
                                    } else 1f
                                }
                                val sharedBrightness = displayAppearance.wallpaperBrightness
                                val sharedBrightnessFilter = remember(sharedBrightness) {
                                    if (sharedBrightness != 0f) {
                                        val b = (1f + sharedBrightness / 50f).coerceIn(0f, 2f)
                                        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                            androidx.compose.ui.graphics.ColorMatrix(
                                                floatArrayOf(
                                                    b, 0f, 0f, 0f, 0f,
                                                    0f, b, 0f, 0f, 0f,
                                                    0f, 0f, b, 0f, 0f,
                                                    0f, 0f, 0f, 1f, 0f
                                                )
                                            )
                                        )
                                    } else null
                                }
                                val sharedBlurEffect = remember(displayAppearance.wallpaperBlur) {
                                    val blurRadiusPx = 24f * density.density
                                    if (displayAppearance.wallpaperBlur && blurRadiusPx > 0f && isRenderEffectSupported()) {
                                        BlurEffect(null, blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                                    } else null
                                }
                                val sharedWallpaperImage = remember(sharedWallpaperBitmap) {
                                    sharedWallpaperBitmap
                                }
                                Image(
                                    bitmap = sharedWallpaperImage,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .liquidGlassLayerBackdrop(
                                            backdrop = sharedWallpaperBackdrop,
                                            recordKey = listOf(
                                                sharedWallpaperBitmap,
                                                sharedWallpaperScale,
                                                sharedWallpaperOffset.x,
                                                sharedWallpaperOffset.y,
                                                sharedBrightness,
                                                displayAppearance.wallpaperBlur,
                                                sharedMinScale,
                                            ),
                                        )
                                        .graphicsLayer {
                                            val s = maxOf(sharedWallpaperScale, sharedMinScale)
                                            scaleX = s
                                            scaleY = s
                                            // 缩放/平移裁在自身 bounds 内，避免突出课程表页画进侧栏
                                            clip = true
                                            // 只用用户自己设的偏移：切页时壁纸是静止底图，
                                            // 圆角卡片从它上面盖过来（见 settingsPageClipModifier）
                                            translationX = sharedWallpaperOffset.x
                                            translationY = sharedWallpaperOffset.y
                                            renderEffect = sharedBlurEffect
                                        },
                                    contentScale = ContentScale.Fit,
                                    colorFilter = sharedBrightnessFilter
                                )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (navBarStyle == "rail") {
                                            com.haooz.chedule.ui.components.tabletNavRailStartPadding()
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                            // 主 tab 仅由底栏/侧栏点击切换；关闭用户手势翻页。
                            // Follow the navigation axis reported by the native host or tablet rail.
                            val mainPagerModifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(mainContentNestedScroll)
                            val mainPagerPageKey: (Int) -> String = { page ->
                                if (isShiftMode) "shift-$page" else "main-$page"
                            }
                            val mainPagerPageContent: @Composable (Int) -> Unit = { page ->
                                if (!isShiftMode) {
                                    when (page) {
                                        0 -> {
                                            // 今日页主题预先固定，切 tab 不跟着 chrome 闪一帧
                                            val todayForced = if (captureThemeActive) captureThemeIsDark else todayPageForcedDark
                                            val todayDark = todayForced ?: appSettingDark
                                            val todayThemeController = rememberAppThemeController(isDark = todayDark)
                                        MiuixTheme(controller = todayThemeController) {
                                            CompositionLocalProvider(LocalForcedDarkTheme provides todayForced) {
                                        TodayScreen(
                                            viewModel = viewModel,
                                            settingsViewModel = settingsViewModel,
                                            hiddenCourseIds = hiddenCourseIds,
                                            holidayDataTick = resumeCount,
                                            onCourseClick = { courses, left, top, width, height, cardLayer, courseIdToHide, targetWeek ->
                                                openCourseDetail(
                                                    courses,
                                                    left,
                                                    top,
                                                    width,
                                                    height,
                                                    cardLayer = cardLayer,
                                                    fromToday = true,
                                                    courseIdToHide = courseIdToHide,
                                                    targetWeek = targetWeek
                                                )
                                            },
                                            pagerState = todayPagerState,
                                            navBarStyle = navBarStyle,
                                            onScrollYChanged = { _ -> },
                                            settingsScrollBehavior = todayScrollBehavior,
                                            onSelectedDayChanged = { todaySelectedDayOfWeek = it },
                                            onSelectedDateChanged = { todayIsToday = it },
                                            scrollToTodayTrigger = scrollToTodayTrigger,
                                            jumpToDateTrigger = todayJumpToDateTrigger,
                                            onJumpToDateProcessed = { todayJumpToDateTrigger = 0 },
                                            wallpaperBitmap = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperImage else wallpaperImage,
                                            wallpaperOffset = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperOffset else wallpaperOffset,
                                            wallpaperScale = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperScale else wallpaperScale,
                                            useSharedWallpaper = !isShiftMode && wallpaperBitmap != null,
                                            sharedWallpaperBackdrop = sharedWallpaperBackdrop,
                                            wallpaperBrightness = displayAppearance.wallpaperBrightness,
                                            cardBlurRadius = displayAppearance.cardBlurRadius,
                                            cardRefraction = displayAppearance.cardRefraction,
                                            cardSurfaceAlpha = displayAppearance.cardSurfaceAlpha,
                                            wallpaperBlur = displayAppearance.wallpaperBlur,
                                            liquidGlassBackdrop = liquidGlassBackdrop,
                                            showClassroom = displayAppearance.showClassroom,
                                            showTeacher = displayAppearance.showTeacher,
                                            onListScrollInProgress = { todayListScrollInProgress.value = it },
                                            // 今日助手卡留在 :androidApp（天气/定位/Geocoder），通过槽位注入
                                            todayAssistantCard = todayAssistantCard,
                                        )
                                            }
                                        }
                                    }
                                    1 -> {
                                        val scheduleForced = if (captureThemeActive) captureThemeIsDark else wallpaperForcedDark
                                        val scheduleDark = scheduleForced ?: appSettingDark
                                        val scheduleThemeController = rememberAppThemeController(isDark = scheduleDark)
                                        MiuixTheme(controller = scheduleThemeController) {
                                            CompositionLocalProvider(LocalForcedDarkTheme provides scheduleForced) {
                                        CompositionLocalProvider(
                                            LocalLandRipple provides LandRippleSpec(
                                                center = landRippleCenter,
                                                token = landRippleToken
                                            )
                                        ) {
                                            MainScheduleScreen(
                                            viewModel = viewModel,
                                            settingsViewModel = settingsViewModel,
                                            pagerState = pagerState,
                                            hiddenCourseIds = hiddenCourseIds,
                                            draggingCourseIds = draggingCourseIds,
                                            onCourseClick = { courses, left, top, width, height, cardLayer, courseIdToHide, targetWeek ->
                                                openCourseDetail(
                                                    courses,
                                                    left,
                                                    top,
                                                    width,
                                                    height,
                                                    cardLayer = cardLayer,
                                                    fromToday = false,
                                                    courseIdToHide = courseIdToHide,
                                                    targetWeek = targetWeek
                                                )
                                            },
                                            onPopupStateChange = { showCourseDetailPopup = it },
                                            onEmptyLongPress = { day, section, centerX, cellTopY, width, _, addDay, addWeek ->
                                                shortcutMenuCourse = null
                                                emptyCellMenuTarget = EmptyCellMenuTarget(
                                                    columnDay = day,
                                                    section = section,
                                                    addDay = addDay,
                                                    addWeek = addWeek,
                                                )
                                                shortcutMenuVisible = true
                                                shortcutMenuPosition = Offset(centerX - width / 2f, cellTopY)
                                                shortcutMenuAnchorWidth = width
                                            },
                                            onCourseLongPress = { course, left, top, width, height, backdrop, currentWeek ->
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                                // 浮层照常起（原卡隐藏、浮层顶在原地）—— 自定义时间课也一样。
                                                // 「不能拖动」由卡片侧保证：DayColumn 给这类课传 dragEnabled=false，
                                                // 手指怎么动都不会 onDragStart/onDrag，浮层停在原位、也没有调课落点。
                                                // ⚠ draggedWeek 必须同步：删除确认弹窗按「本周」删时读的就是它。
                                                isDraggingCard = true
                                                floatingCardVisible = true
                                                // 新一轮长按：还没开始拖，高光先关
                                                isFloatingCardDragging = false
                                                draggingCourseIds = setOf(course.id)
                                                draggedCardCourse = course
                                                draggedWeek = currentWeek
                                                // 跨午休/晚修课以整课视觉几何为锚点，避免长按在分段卡片上时中心偏移
                                                val geom = gridGeometry
                                                val anchorCenter: Offset
                                                val anchorSize: Offset
                                                if (geom != null) {
                                                    val div = dividerPxFor(geom)
                                                    val fullH = courseVisualHeightPx(
                                                        geom,
                                                        course.startSection,
                                                        course.endSection,
                                                        div
                                                    )
                                                    val bounds = geom.dayBounds[course.dayOfWeek]
                                                        ?: geom.dayBounds.values.firstOrNull()
                                                    // 宽度沿用卡片回调（含 Day 列内 2dp padding 后的实际宽），不要用整列 dayBounds
                                                    val topRel = sectionTopPx(geom, course.startSection, div)
                                                    // X 必须用卡片实测中心 left：调课日列数据星期 ≠ 列星期，
                                                    // dayBounds[course.dayOfWeek] 会取到隔壁列；自定义时间课按时间轴定位。
                                                    anchorCenter = when {
                                                        course.hasValidCustomTime() ->
                                                            Offset(left, top)
                                                        bounds != null && bounds.size >= 3 ->
                                                            Offset(left, bounds[2] + topRel + fullH / 2f)
                                                        else ->
                                                            Offset(left, top)
                                                    }
                                                    anchorSize = if (course.hasValidCustomTime()) {
                                                        Offset(width, height)
                                                    } else {
                                                        Offset(width, fullH)
                                                    }
                                                } else {
                                                    anchorCenter = Offset(left, top)
                                                    anchorSize = Offset(width, height)
                                                }
                                                draggedCardPosition = anchorCenter
                                                // 由卡片实测 X 反推所在列：数据星期在调课日列对不上列
                                                draggedColumnDay = geom?.dayBounds?.entries
                                                    ?.firstOrNull { (_, b) ->
                                                        b.size >= 2 && left >= b[0] && left <= b[1]
                                                    }?.key ?: course.dayOfWeek
                                                dragMotion.reset()
                                                floatingOffsetX.floatValue = 0f
                                                floatingOffsetY.floatValue = 0f
                                                draggedCardSize = anchorSize
                                                draggedCardBackdrop = backdrop
                                                shortcutMenuCourse = course
                                                emptyCellMenuTarget = null
                                                shortcutMenuVisible = true
                                                shortcutMenuPosition =
                                                    Offset(
                                                        anchorCenter.x - anchorSize.x / 2f,
                                                        anchorCenter.y - anchorSize.y / 2f
                                                    )
                                                shortcutMenuAnchorWidth = anchorSize.x
                                                shortcutMenuBackdrop = backdrop
                                            },
                                            onCourseDragStart = { _ ->
                                                // 拖过位移阈值才到这里（卡片侧 moved > slop 才回调），
                                                // 跟手高光从这一刻才亮，紧接着 onCourseMenuDismiss 收菜单
                                                pendingDropTarget = null
                                                isFloatingCardDragging = true
                                            },
                                            onCourseMenuDismiss = {
                                                shortcutMenuVisible = false
                                                coroutineScope.launch {
                                                    delay(220.milliseconds)
                                                    shortcutMenuCourse = null
                                                }
                                            },
                                            onCourseDrag = { _, offsetX, offsetY ->
                                                // 跟手 1:1；offset 只进 graphicsLayer，速度进 holder
                                                val now = monotonicNow()
                                                if (dragMotion.lastTimeMs != 0L) {
                                                    val dt = (now - dragMotion.lastTimeMs).coerceAtLeast(1L)
                                                    dragMotion.velocityX =
                                                        (offsetX - dragMotion.lastOffsetX) / dt * 1000f
                                                    dragMotion.velocityY =
                                                        (offsetY - dragMotion.lastOffsetY) / dt * 1000f
                                                }
                                                dragMotion.lastTimeMs = now
                                                dragMotion.lastOffsetX = offsetX
                                                dragMotion.lastOffsetY = offsetY
                                                floatingOffsetX.floatValue = offsetX
                                                floatingOffsetY.floatValue = offsetY
                                                // 落点仅跨格时写 state，避免逐帧重组课表
                                                val course = draggedCardCourse
                                                if (course != null) {
                                                    val geomNow = gridGeometry
                                                    val sectionH = geomNow?.sectionHeightPx ?: 0f
                                                    val sectionCount =
                                                        course.endSection - course.startSection + 1
                                                    // 必须与浮层卡片用同一套高度（含分界缝与特殊块挤占）：写死
                                                    // sectionCount * sectionH 会让「起始节次中心」算偏，落点命中与高亮错开一节。
                                                    val cardHeightPx = when {
                                                        course.hasValidCustomTime() -> draggedCardSize.y
                                                        geomNow != null -> courseVisualHeightPx(
                                                            geomNow,
                                                            course.startSection,
                                                            course.endSection,
                                                            dividerPxFor(geomNow),
                                                        )
                                                        else -> sectionCount * sectionH
                                                    }
                                                    val centerX = draggedCardPosition.x + offsetX
                                                    val cardTopY =
                                                        draggedCardPosition.y + offsetY - cardHeightPx / 2f
                                                    val firstSectionCenterY =
                                                        cardTopY + sectionH / 2f
                                                    val rawTarget = computeDropTarget(
                                                        centerX,
                                                        firstSectionCenterY
                                                    )
                                                    // 放不下的格子不给落点（与粘贴路径同款校验）：起始节次+跨度
                                                    // 超过总节次时松手会写出 endSection > totalSections 的课，渲染溢出/错位。
                                                    val newTarget = rawTarget?.takeIf {
                                                        it.second + sectionCount - 1 <= totalSections
                                                    }
                                                    if (newTarget != pendingDropTarget) {
                                                        pendingDropTarget = newTarget
                                                    }
                                                }
                                            },
                                            onCourseDragEnd = { _ ->
                                                // 手指已离开：高光立刻开始淡出，不等下面的退场动画
                                                isFloatingCardDragging = false
                                                // 只结束拖拽浮层；菜单关闭交给 onCourseMenuDismiss
                                                val source = draggedCardCourse
                                                val target = pendingDropTarget
                                                val week = draggedWeek
                                                pendingDropTarget = null
                                                if (source != null && target != null) {
                                                    // 列 → 数据坐标：调课日列显示的是 followWeekday/followWeek 的课，
                                                    // 冲突检测与写库都必须落在数据坐标上，几何/动画仍用列
                                                    val (targetDay, targetWeek) =
                                                        dataPositionForColumn(target.first)
                                                    val sectionSpan =
                                                        source.endSection - source.startSection
                                                    val targetStart = target.second
                                                    val targetEnd = targetStart + sectionSpan
                                                    // 同格按数据坐标判：调课日列里原地拖动不再被误判成跨天
                                                    val sameSlot =
                                                        source.dayOfWeek == targetDay &&
                                                                week == targetWeek &&
                                                                source.startSection == targetStart &&
                                                                source.endSection == targetEnd
                                                    if (!sameSlot) {
                                                        val conflicts = viewModel.getCoursesAtSlot(
                                                            targetWeek,
                                                            targetDay,
                                                            targetStart,
                                                            targetEnd
                                                        ).filter {
                                                            it.id != source.id && it.isActiveInWeek(
                                                                targetWeek
                                                            )
                                                        }
                                                        if (conflicts.isEmpty()) {
                                                            viewModel.moveCourseForWeek(
                                                                source.id,
                                                                week,
                                                                targetDay,
                                                                targetStart,
                                                                targetEnd,
                                                                targetWeek
                                                            )
                                                            // 单周调课可能拆分/合并出新 id，按目标位重收并隐藏防叠影
                                                            draggingCourseIds =
                                                                viewModel.getCoursesAtSlot(
                                                                    targetWeek,
                                                                    targetDay,
                                                                    targetStart,
                                                                    targetEnd
                                                                )
                                                                    .filter { it.isActiveInWeek(targetWeek) }
                                                                    .map { it.id }
                                                                    .toSet()
                                                            snapFloatingCardToTarget(
                                                                target.first,
                                                                targetStart,
                                                                sectionSpan
                                                            )
                                                            // 原位置露出的非本周课程淡入放大
                                                            val sourceCourses =
                                                                viewModel.getCoursesAtSlot(
                                                                    week,
                                                                    source.dayOfWeek,
                                                                    source.startSection,
                                                                    source.endSection
                                                                ).filter {
                                                                    it.id != source.id && !it.isActiveInWeek(
                                                                        week
                                                                    )
                                                                }
                                                            if (sourceCourses.isNotEmpty()) {
                                                                animateInCourseIds =
                                                                    sourceCourses.map { it.id }
                                                                        .toSet()
                                                                coroutineScope.launch {
                                                                    delay(350.milliseconds)
                                                                    animateInCourseIds = emptySet()
                                                                }
                                                            }
                                                        } else {
                                                            // 浮层连贯移到目标卡上方悬停等用户选择
                                                            pendingConflictCourse =
                                                                conflicts.first()
                                                            pendingDropTarget =
                                                                target.first to targetStart
                                                            showRescheduleConflictDialog = true
                                                            val hoverTargetCenter =
                                                                computeTargetCenter(
                                                                    target.first,
                                                                    targetStart,
                                                                    sectionSpan
                                                                )
                                                            if (hoverTargetCenter != null) {
                                                                coroutineScope.launch {
                                                                    stopConflictHover()
                                                                    isConflictHover = true
                                                                    isSnapping = true
                                                                    // 拖拽位移已在 floatingOffset*
                                                                    val sectionHPx =
                                                                        gridGeometry?.sectionHeightPx
                                                                            ?: 0f
                                                                    val floatH =
                                                                        (sectionSpan + 1) * sectionHPx
                                                                    val gap = with(density) {
                                                                        10.dp.toPx()
                                                                    }
                                                                    val hoverOffsetX =
                                                                        hoverTargetCenter.x -
                                                                                draggedCardPosition.x
                                                                    val hoverOffsetY =
                                                                        hoverTargetCenter.y -
                                                                                draggedCardPosition.y -
                                                                                floatH - gap
                                                                    val settleEase =
                                                                        CubicBezierEasing(
                                                                            0.32f, 0.72f, 0.28f, 1f
                                                                        )
                                                                    val jobX = launch {
                                                                        animateFloatState(
                                                                            floatingOffsetX,
                                                                            hoverOffsetX,
                                                                            tween(
                                                                                durationMillis = 420,
                                                                                easing = settleEase
                                                                            )
                                                                        )
                                                                    }
                                                                    val jobY = launch {
                                                                        animateFloatState(
                                                                            floatingOffsetY,
                                                                            hoverOffsetY,
                                                                            tween(
                                                                                durationMillis = 420,
                                                                                easing = settleEase
                                                                            )
                                                                        )
                                                                    }
                                                                    val jobScale = launch {
                                                                        floatingScale.animateTo(
                                                                            1.08f,
                                                                            tween(durationMillis = 420)
                                                                        )
                                                                    }
                                                                    jobX.join(); jobY.join(); jobScale.join()
                                                                    val bobAmp = with(density) {
                                                                        4.dp.toPx()
                                                                    }
                                                                    conflictHoverBobJob = launch {
                                                                        while (true) {
                                                                            animateFloatState(
                                                                                floatingOffsetY,
                                                                                hoverOffsetY + bobAmp,
                                                                                tween(
                                                                                    durationMillis = 1400,
                                                                                    easing = FastOutSlowInEasing
                                                                                )
                                                                            )
                                                                            animateFloatState(
                                                                                floatingOffsetY,
                                                                                hoverOffsetY - bobAmp,
                                                                                tween(
                                                                                    durationMillis = 1400,
                                                                                    easing = FastOutSlowInEasing
                                                                                )
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            } else {
                                                                coroutineScope.launch {
                                                                    floatingScale.animateTo(
                                                                        1f,
                                                                        tween(durationMillis = 180)
                                                                    )
                                                                    delay(180.milliseconds)
                                                                    isDraggingCard = false
                                                                    floatingCardVisible = false
                                                                    draggingCourseIds = emptySet()
                                                                    floatingOffsetX.floatValue = 0f
                                                                    floatingOffsetY.floatValue = 0f
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        snapFloatingCardToOrigin()
                                                    }
                                                } else if (source != null) {
                                                    // 无落点：松手在网格外 / 超出总节次 / 落到重组休课日
                                                    // → 与「同格」一样吸回原位，不凭空消失
                                                    stopConflictHover()
                                                    snapFloatingCardToOrigin()
                                                } else {
                                                    dismissFloatingCard()
                                                }
                                            },
                                            wallpaperBitmap = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperImage else wallpaperImage,
                                            wallpaperOffset = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperOffset else wallpaperOffset,
                                            wallpaperScale = if (showCustomizePage && !isWindowCutoutActive) originalWallpaperScale else wallpaperScale,
                                            useSharedWallpaper = !isShiftMode && wallpaperBitmap != null,
                                            sharedWallpaperBackdrop = sharedWallpaperBackdrop,
                                            isWallpaperEditing = isWindowCutoutActive,
                                            onWallpaperOffsetChange = { wallpaperOffset = it },
                                            onWallpaperScaleChange = { wallpaperScale = it },
                                            appearance = displayAppearance,
                                            liquidGlassBackdrop = liquidGlassBackdrop,
                                            onGridGeometryChange = { geom -> gridGeometry = geom },
                                            dropHighlight = run {
                                                val emptyTarget = emptyCellMenuTarget
                                                if (emptyTarget != null && shortcutMenuVisible) {
                                                    emptyTarget.columnDay to (emptyTarget.section..emptyTarget.section)
                                                } else {
                                                    val target = pendingDropTarget
                                                    val source = draggedCardCourse
                                                    if (floatingCardVisible && target != null && source != null) {
                                                        val sectionSpan =
                                                            source.endSection - source.startSection
                                                        target.first to (target.second..(target.second + sectionSpan))
                                                    } else null
                                                }
                                            },
                                            dropHighlightOrigin = run {
                                                val source = draggedCardCourse
                                                if (floatingCardVisible && source != null && !isPasteFlight) {
                                                    // 高亮画在网格列上：调课日列要用列星期，不能用数据星期
                                                    draggedColumnDay to (source.startSection..source.endSection)
                                                } else {
                                                    val emptyTarget = emptyCellMenuTarget
                                                    if (emptyTarget != null && shortcutMenuVisible) {
                                                        emptyTarget.columnDay to (emptyTarget.section..emptyTarget.section)
                                                    } else null
                                                }
                                            },
                                            scheduleScrollBehavior = scheduleScrollBehavior,
                                                externalScrollState = scheduleScrollState,
                                            externalShowCourseDetail = scheduleShowCourseDetail,
                                            externalSelectedCourse = scheduleSelectedCourse,
                                            externalSelectedCourses = scheduleSelectedCourses
                                        )
                                        }
                                            }
                                        }
                                    }
                                    2 -> {
                                        // 设置页始终跟应用主题，不被壁纸锁深色
                                        val settingsDark = appSettingDark
                                        val settingsThemeController = rememberAppThemeController(isDark = settingsDark)
                                        Box(modifier = settingsPageClipModifier) {
                                        MiuixTheme(controller = settingsThemeController) {
                                            CompositionLocalProvider(LocalForcedDarkTheme provides settingsPageForcedDark) {
                                        RenderSettingsPane(
                                            settingsPane,
                                            SettingsPaneArgs(
                                                viewModel = viewModel,
                                                scheduleViewModel = scheduleViewModel,
                                                settingsViewModel = settingsViewModel,
                                                shiftViewModel = shiftViewModel,
                                                isShiftMode = false,
                                                onExitShiftMode = {},
                                                onEnterShiftMode = {
                                                    FeatureLog.shift("enter_loading")
                                                    showShiftLoading = true
                                                    isExitingShift = false
                                                },
                                                navBarStyle = navBarStyle,
                                                onScrollYChanged = { _ -> },
                                                settingsScrollBehavior = settingsScrollBehavior,
                                                liquidGlassBackdrop = liquidGlassBackdrop,
                                                showUpdateSettings = showUpdateSettings,
                                            )
                                        )
                                            }
                                        }
                                        }
                                    }
                                    3 -> {
                                        // 课程管理不预加载：beyond 撑满整窗后这一格任何时候都在窗口里，
                                        // 不加 if 就等于常驻；只有它是当前页才真正组合。
                                        if (mainPagerState.currentPage == 3) {
                                            TabletCourseManagePane(
                                                viewModel = viewModel,
                                                settingsViewModel = settingsViewModel,
                                                liquidGlassBackdrop = liquidGlassBackdrop,
                                            )
                                        }
                                    }
                                    4 -> {
                                        // 切换课表同上，不预加载
                                        if (mainPagerState.currentPage == 4) {
                                            val shareRepository = remember { CourseRepository() }
                                            TabletSwitchSchedulePane(
                                                viewModel = viewModel,
                                                scheduleViewModel = scheduleViewModel,
                                                settingsViewModel = settingsViewModel,
                                                liquidGlassBackdrop = liquidGlassBackdrop,
                                                onShareSchedule = { name, changed ->
                                                    onShareSchedule?.invoke(name, changed)
                                                },
                                                isScheduleShareable = { name ->
                                                    buildShareScheduleMap(shareRepository, name) != null
                                                },
                                            )
                                        }
                                    }
                                }
                            } else {
                                when (page) {
                                    0 -> ShiftScheduleScreen(
                                        shiftViewModel = shiftViewModel,
                                        settingsViewModel = settingsViewModel,
                                        pagerState = pagerState,
                                        scheduleDataVersion = dataVersion,
                                        cardHeightPerSection = currentAppearance().cardHeight,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                        scheduleScrollBehavior = scheduleScrollBehavior,
                                        isInFreeformWindow = isInFreeformWindow,
                                        titleBarHeight = 56.dp,
                                    )

                                    1 -> Box(modifier = settingsPageClipModifier) {
                                        RenderSettingsPane(
                                            settingsPane,
                                            SettingsPaneArgs(
                                                viewModel = viewModel,
                                                scheduleViewModel = scheduleViewModel,
                                                settingsViewModel = settingsViewModel,
                                                shiftViewModel = shiftViewModel,
                                                isShiftMode = true,
                                                onExitShiftMode = {
                                                    FeatureLog.shift("exit_loading")
                                                    showShiftLoading = true
                                                    isExitingShift = true
                                                },
                                                onEnterShiftMode = {
                                                    showShiftLoading = true
                                                    isExitingShift = false
                                                },
                                                navBarStyle = navBarStyle,
                                                onScrollYChanged = { _ -> },
                                                settingsScrollBehavior = settingsScrollBehavior,
                                                liquidGlassBackdrop = liquidGlassBackdrop,
                                                showUpdateSettings = showUpdateSettings,
                                            )
                                        )
                                    }
                                }
                            }
                            }
                            if (useVerticalMainPager) {
                                // beyond 撑到覆盖全部页，让 0~2（今日/课程表/我的）常驻组合树、切页零成本。
                                // 「3/4 仅当前页才组合」由 mainPagerPageContent 内容侧兜底。
                                VerticalPager(
                                    state = mainPagerState,
                                    modifier = mainPagerModifier,
                                    key = mainPagerPageKey,
                                    userScrollEnabled = false,
                                    beyondViewportPageCount = mainPagerPageCount,
                                ) { page -> mainPagerPageContent(page) }
                            } else {
                                // 手机切 tab 是 spring 平移动画：beyond=1 时目标页隔壁那页会在动画途中
                                // 才首次组合、砸在首帧上；beyond=2 让三页常驻，切页变纯位移动画。
                                HorizontalPager(
                                    state = mainPagerState,
                                    modifier = mainPagerModifier,
                                    key = mainPagerPageKey,
                                    userScrollEnabled = false,
                                    beyondViewportPageCount = 2,
                                ) { page -> mainPagerPageContent(page) }
                            }
                        }
                    }

                    // 手机端：只要不在当前周/天就常驻显示
                    // Pad（rail）顶栏仍保留原返回按钮，此处不叠加
                    val isPhoneChrome = navBarStyle != "rail"
                    val shouldShowBackToNowFab = isPhoneChrome &&
                        !isShiftMode &&
                        !showDetail &&
                        !showCustomizePage &&
                        !showSwitchSchedule &&
                        !isWindowCutoutActive &&
                        !shortcutMenuVisible &&
                        !floatingCardVisible &&
                        when (selectedTab) {
                            0 -> !todayIsToday
                            1 -> !isViewingCurrentWeek
                            else -> false
                        }
                    val backToNowLabel = "今"
                    // ⚠ 必须与底栏同一套间距算法（bottomFloatingPadding）：按钮悬在底栏胶囊
                    // 上方 64dp，底栏间距变了这里必须跟着变，否则按钮会陷进底栏里
                    val backToNowBottomPadding = if (nativeBottomBar) {
                        hostNavigation.bottomBarInset.toFloat().dp + 8.dp
                    } else bottomFloatingPadding() + 64.dp
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 对齐切换课表底栏：None 转场 + 手动 appear + graphicsLayer clip=false，
                        // 避免 AnimatedVisibility 收拢尺寸时把阴影裁掉
                        AnimatedVisibility(
                            visible = shouldShowBackToNowFab,
                            enter = EnterTransition.None,
                            exit = ExitTransition.None,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                // Native chrome reports its full occupied region, including safe area.
                                .padding(bottom = backToNowBottomPadding)
                                .zIndex(20f)
                        ) {
                            val appear by transition.animateFloat(
                                transitionSpec = {
                                    if (targetState == EnterExitState.Visible) {
                                        tween(durationMillis = 320, easing = FastOutSlowInEasing)
                                    } else {
                                        tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                    }
                                },
                                label = "BackToNowAppear"
                            ) { if (it == EnterExitState.Visible) 1f else 0f }

                            BackToNowFloatingButton(
                                backdrop = chromeBackdrop,
                                label = backToNowLabel,
                                modifier = Modifier.graphicsLayer {
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                    scaleX = 0.6f + 0.4f * appear
                                    scaleY = 0.6f + 0.4f * appear
                                    alpha = appear
                                    clip = false
                                },
                                onClick = {
                                    if (selectedTab == 0) {
                                        scrollToTodayTrigger++
                                    } else {
                                        coroutineScope.launch {
                                            val targetPage =
                                                (currentWeek - 1).coerceIn(
                                                    0,
                                                    (totalWeeks - 1).coerceAtLeast(0)
                                                )
                                            pagerState.animateScrollToPage(targetPage)
                                        }
                                    }
                                },
                            )
                        }
                    }

                    // 宿主把分享 Intent 摊平成 SharedFileIntent，并提供读该 uri 全文的回调。
                    ShareImportDialog(
                        shareIntent = sharedFileIntent,
                        onShareIntentConsumed = onShareIntentConsumed,
                        readSharedFileText = { uriString -> onReadSharedFileText?.invoke(uriString) },
                        courseViewModel = viewModel,
                        scheduleViewModel = scheduleViewModel,
                        settingsViewModel = settingsViewModel,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                    )

                    // 更新能力（检查/下载/校验/安装）由宿主注入
                    UpdateDialog(
                        updateController = updateController ?: NoopUpdateController,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                    )

                    val showJumpWeekDialog by viewModel.showJumpWeekDialog.collectAsState()
                    var jumpWeekTemp by remember { mutableIntStateOf(1) }
                    val hapticFeedback = LocalHapticFeedback.current
                    LaunchedEffect(showJumpWeekDialog) {
                        if (showJumpWeekDialog) {
                            jumpWeekTemp = pagerState.currentPage + 1
                        }
                    }
                    OverlayDialog(
                        title = "跳转周数",
                        show = showJumpWeekDialog,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        onDismissRequest = { viewModel.hideJumpWeekDialog() }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            NumberPicker(
                                value = jumpWeekTemp,
                                onValueChange = { jumpWeekTemp = it },
                                range = 1..totalWeeks,
                                visibleItemCount = 3,
                                itemHeight = 60.dp,
                                textStyle = MiuixTheme.textStyles.title2,
                                label = { "第${it}周" },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TextButton(
                                    text = "取消",
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        viewModel.hideJumpWeekDialog()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    text = "确定",
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        viewModel.hideJumpWeekDialog()
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(jumpWeekTemp - 1)
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    val showAddDialog by viewModel.showAddDialog.collectAsState()
                    val editingCourse by viewModel.editingCourse.collectAsState()
                    val selectedStartSection by viewModel.selectedStartSection.collectAsState()
                    val selectedEndSection by viewModel.selectedEndSection.collectAsState()
                    val editingStartSection =
                        editingCourse?.startSection ?: selectedStartSection
                    val editingEndSection = editingCourse?.endSection ?: selectedEndSection
                    val addDialogSectionTimes by settingsViewModel.sectionTimes.collectAsState()

                    // 始终跟随应用主题，不受壁纸强制主题影响
                    val appDialogDark = rememberAppSettingDark()
                    val appDialogController = rememberAppThemeController(isDark = appDialogDark)
                    MiuixTheme(controller = appDialogController) {
                        CompositionLocalProvider(LocalForcedDarkTheme provides null) {
                            val addDialogDefaultWeeks by viewModel.addDialogDefaultWeeks.collectAsState()
                            AddCourseDialog(
                                show = showAddDialog,
                                course = editingCourse,
                                selectedDay = viewModel.selectedDay.collectAsState().value,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                totalWeeks = totalWeeks,
                                totalSections = totalSections,
                                defaultStartSection = editingStartSection,
                                defaultEndSection = editingEndSection,
                                defaultWeeks = addDialogDefaultWeeks,
                                getOccupiedWeeks = { dayOfWeek, startSection, endSection, excludeIds, startTime, endTime ->
                                    viewModel.getOccupiedWeeks(
                                        dayOfWeek = dayOfWeek,
                                        startSection = startSection,
                                        endSection = endSection,
                                        excludeIds = excludeIds.toSet(),
                                        startTime = startTime,
                                        endTime = endTime
                                    )
                                },
                                onDismiss = { viewModel.hideDialog() },
                                onConfirm = { course ->
                                    if (editingCourse != null) {
                                        viewModel.updateCourse(course)
                                    } else {
                                        viewModel.addCourse(course)
                                    }
                                },
                                onDelete = { courseId ->
                                    viewModel.deleteCourse(courseId)
                                },
                                sectionTimes = addDialogSectionTimes
                            )
                        }
                    }
                    DeleteWeekCourseDialog(
                        show = showDeleteConfirmDialog,
                        course = deleteConfirmCourse,
                        week = draggedWeek,
                        viewModel = viewModel,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        hapticFeedback = hapticFeedback,
                        onDismiss = { showDeleteConfirmDialog = false },
                    )
                    PasteRangeDialog(
                        show = showPasteRangeDialog,
                        courseName = copiedCourseForPaste?.name ?: "",
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        hapticFeedback = hapticFeedback,
                        onPaste = { allWeeks ->
                            val meta = copiedCourseForPaste
                            val target = pasteRangeTarget
                            if (meta != null && target != null) {
                                // 写库用调课映射后的数据坐标；飞行/几何用列星期
                                val day = target.dataDay
                                val section = target.section
                                val span = (meta.endSection - meta.startSection).coerceAtLeast(0)
                                val endSection = section + span
                                // 「当前周」=该格所属教学周；调课日为 followWeek
                                val pasteWeek = target.dataWeek
                                if (endSection > totalSections) {
                                    showToast("空间不足，无法粘贴")
                                } else {
                                    val conflicts = viewModel.getCoursesAtSlot(pasteWeek, day, section, endSection)
                                        .filter { it.isActiveInWeek(pasteWeek) }
                                    if (conflicts.isNotEmpty()) {
                                        showToast("目标位置有课，无法粘贴")
                                    } else {
                                        val pasted = if (allWeeks) {
                                            meta.copy(
                                                id = randomUuidV4(),
                                                dayOfWeek = day,
                                                startSection = section,
                                                endSection = endSection,
                                                scheduleId = "",
                                                isCustomTime = false,
                                                customStartTime = null,
                                                customEndTime = null,
                                                lastModified = Clock.System.now().toEpochMilliseconds()
                                            )
                                        } else {
                                            meta.copy(
                                                id = randomUuidV4(),
                                                dayOfWeek = day,
                                                startSection = section,
                                                endSection = endSection,
                                                startWeek = pasteWeek,
                                                endWeek = pasteWeek,
                                                weekType = Course.WEEK_TYPE_ALL,
                                                selectedWeeks = emptyList(),
                                                scheduleId = "",
                                                isCustomTime = false,
                                                customStartTime = null,
                                                customEndTime = null,
                                                lastModified = Clock.System.now().toEpochMilliseconds()
                                            )
                                        }
                                        // 落地后再写入课程
                                        playPasteFlightAnimation(
                                            meta,
                                            target.columnDay,
                                            section
                                        ) {
                                            viewModel.addCourse(pasted)
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        }
                                    }
                                }
                            }
                            showPasteRangeDialog = false
                            pasteRangeTarget = null
                        },
                        onDismiss = {
                            showPasteRangeDialog = false
                            pasteRangeTarget = null
                        },
                    )
                    RescheduleConflictDialog(
                        show = showRescheduleConflictDialog,
                        source = draggedCardCourse,
                        target = pendingConflictCourse,
                        // 写库走数据坐标；flyFloatingCardToDropTarget 仍读列坐标算几何
                        dropTarget = pendingDropTarget?.let {
                            dataPositionForColumn(it.first).first to it.second
                        },
                        dropTargetWeek = pendingDropTarget?.let {
                            dataPositionForColumn(it.first).second
                        } ?: draggedWeek,
                        draggedWeek = draggedWeek,
                        viewModel = viewModel,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        hapticFeedback = hapticFeedback,
                        onCancel = {
                            showRescheduleConflictDialog = false
                            pendingConflictCourse = null
                            flyFloatingCardHome()
                        },
                        onOverwriteResolved = {
                            showRescheduleConflictDialog = false
                            pendingConflictCourse = null
                            val source = draggedCardCourse
                            val target = pendingDropTarget
                            if (source != null && target != null) {
                                val (targetDay, targetWeek) = dataPositionForColumn(target.first)
                                val span = source.endSection - source.startSection
                                val atTarget = viewModel.getCoursesAtSlot(
                                    targetWeek, targetDay, target.second, target.second + span
                                )
                                draggingCourseIds = atTarget
                                    .filter { it.isActiveInWeek(targetWeek) }
                                    .map { it.id }
                                    .toSet()
                            }
                            flyFloatingCardToDropTarget()
                        },
                        onSwapResolved = {
                            showRescheduleConflictDialog = false
                            val conflict = pendingConflictCourse
                            pendingConflictCourse = null
                            val source = draggedCardCourse
                            val target = pendingDropTarget
                            val week = draggedWeek
                            if (source != null && target != null) {
                                val (targetDay, targetWeek) = dataPositionForColumn(target.first)
                                val span = source.endSection - source.startSection
                                // 目标位按目标周次收、源位按 draggedWeek 收（调课日两者可能不同）
                                val atTarget = viewModel.getCoursesAtSlot(
                                    targetWeek, targetDay, target.second, target.second + span
                                ).filter { it.isActiveInWeek(targetWeek) }
                                val atSource = viewModel.getCoursesAtSlot(
                                    week, source.dayOfWeek, source.startSection, source.endSection
                                ).filter { it.isActiveInWeek(week) }
                                draggingCourseIds = (atTarget + atSource)
                                    .map { it.id }
                                    .toSet()
                            }
                            if (conflict != null) {
                                flySwapCards(conflict)
                            } else {
                                dismissFloatingCard()
                            }
                        },
                        onOverwrite = { ids ->
                            animateInCourseIds = ids
                            coroutineScope.launch {
                                delay(350.milliseconds)
                                animateInCourseIds = emptySet()
                            }
                        },
                        onSwap = { ids ->
                            animateInCourseIds = ids
                            coroutineScope.launch {
                                delay(350.milliseconds)
                                animateInCourseIds = emptySet()
                            }
                        },
                    )
                    // 节点常驻组合树、仅 show 控显隐，退出动画才能播完
                    var notice by remember { mutableStateOf<Notice?>(null) }
                    LaunchedEffect(privacyConsented) {
                        // 合规：未同意隐私政策前不拉取公告（不触网）
                        if (!privacyConsented) return@LaunchedEffect
                        val n = NoticeFetcher.fetch()
                        if (n != null && NoticeFetcher.shouldShow(n)) {
                            notice = n
                        }
                    }
                    OverlayDialog(
                        title = notice?.title,
                        summary = notice?.content?.ifBlank { null },
                        show = notice != null,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        onDismissRequest = { notice = null },
                        onDismissFinished = { notice = null }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            notice?.let { n ->
                                TextButton(
                                    text = "完成",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                        NoticeFetcher.markSeen(n)
                                        notice = null
                                    }
                                )
                            }
                        }
                    }
                    // 提饱和：与玻璃面板同源，采样主内容 export。
                    // 光晕半径取 min(短边, 250dp) × 0.5。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                if (glowAlpha > 0f) {
                                    drawContext.canvas.saveLayer(
                                        Rect(0f, 0f, size.width, size.height),
                                        Paint()
                                    )
                                    drawContent()
                                    // 半径与 InteractiveHighlight 同式：min(短边, 250dp) × 0.5
                                    val radius = minOf(size.minDimension, 250.dp.toPx()) * 0.5f
                                    drawRect(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color.White,
                                                Color.White,
                                                Color.Transparent,
                                            ),
                                            center = Offset(
                                                draggedCardPosition.x + floatingOffsetX.floatValue,
                                                draggedCardPosition.y + floatingOffsetY.floatValue,
                                            ),
                                            radius = radius,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                    drawContext.canvas.restore()
                                }
                            }
                            .drawBackdrop(
                                backdrop = liquidGlassBackdrop,
                                shape = { RectangleShape },
                                effects = { colorControls(saturation = 1.5f) },
                                highlight = null,
                                shadow = null,
                            )
                    )
                }
            }
            // 始终用 MiuixTheme 包裹保持结构恒定；ThemeController 原地切 mode，重建实例会卡一帧
            val effectiveForcedDark = if (captureThemeActive) captureThemeIsDark else forcedDark
            val effectiveDark = effectiveForcedDark ?: appSettingDark
            val pageController = rememberAppThemeController(isDark = effectiveDark)
            MiuixTheme(controller = pageController) {
                CompositionLocalProvider(LocalForcedDarkTheme provides effectiveForcedDark) {
                    scaffoldContent()
                    // 平板：胶囊/侧栏叠层（不占 Scaffold bottomBar，内容可正常滚动）
                    if (navBarStyle == "rail") {
                        val onTabletTabSelected: (Int) -> Unit = { idx ->
                            if (idx != selectedTab) {
                                mainTabProgrammatic = true
                                selectedTab = idx
                                coroutineScope.launch {
                                    try {
                                        if (todayPagerState.isScrollInProgress) todayPagerState.cancelScroll()
                                        if (pagerState.isScrollInProgress) pagerState.cancelScroll()
                                        if (mainPagerState.isScrollInProgress) mainPagerState.cancelScroll()
                                        mainPagerState.scrollToPage(idx)
                                    } finally {
                                        mainTabProgrammatic = false
                                    }
                                }
                            }
                        }
                        com.haooz.chedule.ui.components.LiquidNavigationRail(
                            selectedTab = selectedTab,
                            onTabSelected = onTabletTabSelected,
                            backdrop = chromeBackdrop,
                            isShiftMode = isShiftMode,
                            // 侧栏遮罩圆角由宿主注入（同一份 px）
                            screenCornerRadiusPx = screenCornerRadius.maxRadius,
                            onSidebarExpandedChange = {
                                TabletNavSideState.expanded = it
                            },
                            showBackToNow = !isShiftMode &&
                                !showDetail &&
                                !showCustomizePage &&
                                !showSwitchSchedule &&
                                !isWindowCutoutActive &&
                                !shortcutMenuVisible &&
                                !floatingCardVisible &&
                                when (selectedTab) {
                                    0 -> !todayIsToday
                                    1 -> !isViewingCurrentWeek
                                    else -> false
                                },
                            onBackToNow = {
                                if (selectedTab == 0) {
                                    scrollToTodayTrigger++
                                } else {
                                    coroutineScope.launch {
                                        val targetPage =
                                            (currentWeek - 1).coerceIn(
                                                0,
                                                (totalWeeks - 1).coerceAtLeast(0)
                                            )
                                        pagerState.animateScrollToPage(targetPage)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize().zIndex(22f),
                        )
                    }
                    // 平板设置：标题与分界线画在顶栏模糊之上。
                    // 主题锁定 appSettingDark：不随课程表/壁纸锁色瞬间切换。
                    // ⚠ 平板主界面切页是瞬时的（scrollToPage，无动画），这里不挂跟位位移与圆角裁切
                    //   —— 那套只服务手机的「圆角卡片盖住壁纸」转场。
                    // ⚠ 叠层必须只在设置页（rail 下第 3 页）当前时渲染：它画的是左栏「我的」+
                    //   右栏 dest 标题，画在 pager 外面、不带位移，留在今日/课程表页就会串页
                    //   （「今天是周六 我的 学期与周次」）。
                    if (navBarStyle == "rail" && !isShiftMode && mainPagerState.currentPage == 2) {
                        val settingsChromeController = rememberAppThemeController(isDark = appSettingDark)
                        MiuixTheme(controller = settingsChromeController) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                com.haooz.chedule.ui.screens.TabletSettingsChromeOverlay()
                            }
                        }
                    }
                }
            }

        }
        HostedOverlay(
            active = floatingCardVisible || swapFlightVisible ||
                shortcutMenuVisible || shortcutMenuCourse != null || emptyCellMenuTarget != null ||
                showCustomizePage || isCustomizeExiting || isWindowCutoutActive || customizeCoverActive ||
                snapshotCoverBitmap != null || showDetail || detailSnapshot != null ||
                showShiftLoading || shiftLoadingActive,
        ) {
        // 浮层主题跟壁纸/应用设置
        val overlayEffectiveForcedDark = if (captureThemeActive) captureThemeIsDark else forcedDark
        val overlayEffectiveDark = overlayEffectiveForcedDark ?: appSettingDark
        val overlayPageController = rememberAppThemeController(isDark = overlayEffectiveDark)
        // 无壁纸时浮层垫课程表页底色（与网格课程卡同一份，取值见 courseCardSolidBacking）
        val floatSolidBacking = courseCardSolidBacking(
            isDark = overlayEffectiveDark,
            hasWallpaper = wallpaperBitmap != null
        )
        // 拖浮层跟手高光的中心（px，非 state：只在 draw 里写，不触发额外失效）
        val glowCenterPx = remember { FloatArray(2) }
        val dragCardHighlight = remember {
            InteractiveHighlight(
                animationScope = coroutineScope,
                position = { _, _ ->
                    if (isFloatingCardDragging && floatingCardVisible) {
                        glowCenterPx[0] = draggedCardPosition.x + floatingOffsetX.floatValue
                        glowCenterPx[1] = draggedCardPosition.y + floatingOffsetY.floatValue
                    }
                    Offset(glowCenterPx[0], glowCenterPx[1])
                },
                radiusScale = { 1f / 3f },
                radiusBaseDp = 250.dp,
                // 按压进度由 glowAlpha 驱动（150ms 淡变），不走组件自带手势
                pressProgressOverride = { glowAlpha },
                // 只要圆形光晕，不要整幅提亮
                drawFlatOverlay = false,
            )
        }
        MiuixTheme(controller = overlayPageController) {
            CompositionLocalProvider(LocalForcedDarkTheme provides overlayEffectiveForcedDark) {
                // 跟手高光：画在浮层之前（同一父级里先声明先画），压在整页之上、浮层之下
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(dragCardHighlight.modifier)
                )
                if (floatingCardVisible) {
                    val course = draggedCardCourse
                    if (course != null) {
                        // 锚点只用长按瞬间的中心；拖拽/吸附位移走 graphicsLayer，不进组合
                        val widthPx = draggedCardSize.x
                        val geom = gridGeometry
                        val sectionH = geom?.sectionHeightPx
                            ?: with(density) { displayAppearance.cardHeight.dp.toPx() }
                        // 高度含午休/晚修分界缝，与网格上分段卡片的视觉外接框一致
                        //
                        // ⚠ 两种高度的**语义不一样**，别混用：
                        // - 普通课 = 外接框设定高度（不含卡片 2dp 外缝），浮层要再留一次缝才与网格对齐；
                        // - 自定义时间课 = 长按实测高，而网格卡片本来就带 2dp 外缝，实测到的已经是
                        //   **扣完缝的可见高度**，再留一次缝就上下各短 2dp。
                        val heightPx = when {
                            // 自定义时间课按时间轴定位/定高，节次几何算出来的高度对不上，用长按实测高
                            course.hasValidCustomTime() && !isPasteFlight -> draggedCardSize.y
                            geom != null -> courseVisualHeightPx(
                                geom,
                                course.startSection,
                                course.endSection,
                                dividerPxFor(geom)
                            )
                            else -> (course.endSection - course.startSection + 1) * sectionH
                        }
                        val baseOffsetX = with(density) {
                            (draggedCardPosition.x - widthPx / 2f).toDp()
                        }
                        val baseOffsetY = with(density) {
                            (draggedCardPosition.y - heightPx / 2f).toDp()
                        }
                        val width = with(density) { widthPx.toDp() }
                        val height = with(density) { heightPx.toDp() }
                        LaunchedEffect(floatingCardVisible, isPasteFlight) {
                            // 粘贴飞行自行控制缩放，跳过长按入场
                            if (floatingCardVisible && !isPasteFlight) {
                                floatingScale.snapTo(0.94f)
                                floatingScale.animateTo(1.08f, tween(durationMillis = 120))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .offset(x = baseOffsetX, y = baseOffsetY)
                                .size(width = width, height = height)
                                // 自定义时间课的高度已是扣完外缝的可见高，这里不能再缝一次
                                .then(
                                    if (course.hasValidCustomTime()) Modifier
                                    else Modifier.padding(vertical = 2.dp)
                                )
                                .graphicsLayer {
                                    translationX = floatingOffsetX.floatValue
                                    translationY = floatingOffsetY.floatValue
                                    scaleX = floatingScale.value
                                    scaleY = floatingScale.value
                                }
                        ) {
                            CourseCard(
                                course = course,
                                // 粘贴飞行强制本周样式，避免源课不在当前周飞出灰卡
                                isCurrentWeek = if (isPasteFlight) true else course.isActiveInWeek(draggedWeek),
                                wallpaperBackdrop = if (wallpaperBitmap != null) liquidGlassBackdrop else null,
                                cardBlurRadius = displayAppearance.cardBlurRadius,
                                cardRefraction = displayAppearance.cardRefraction,
                                cardAlpha = displayAppearance.cardAlpha,
                                cardSurfaceAlpha = displayAppearance.cardSurfaceAlpha,
                                // 无壁纸时垫页底色，取值见 floatSolidBacking
                                solidBackingColor = floatSolidBacking,
                                cardHeightPerSection = displayAppearance.cardHeight,
                                // ⚠ 必须把浮层 Box 的高度原样传给卡片，否则长按浮层会偏上：
                                // 卡片高（customCardHeightDp ?: 节次数×每节高）与 Box 高度 heightPx
                                // 不一致时按顶部对齐，视觉中心偏上 (heightPx − 卡片高)/2。
                                customCardHeightDp = with(density) { heightPx.toDp().value },
                                cardCornerRadius = displayAppearance.cardCornerRadius,
                                isTablet = isTablet,
                                cardContentAlignment = displayAppearance.cardContentAlignment,
                                cardTextColor = displayAppearance.cardTextColor,
                                cardTextScale = displayAppearance.cardTextScale,
                                showClassroom = displayAppearance.showClassroom,
                                showTeacher = displayAppearance.showTeacher,
                                disablePadding = true,
                                onClick = {}
                            )
                        }
                    }
                }
                if (swapFlightVisible) {
                    val swapCourse = swapFlightCourse
                    if (swapCourse != null) {
                        val centerX = swapFlightOriginCenter.x + swapFlightOffsetX.value
                        val centerY = swapFlightOriginCenter.y + swapFlightOffsetY.value
                        val widthPx = swapFlightWidth
                        val sectionCount = swapCourse.endSection - swapCourse.startSection + 1
                        val sectionH = gridGeometry?.sectionHeightPx
                            ?: with(density) { displayAppearance.cardHeight.dp.toPx() }
                        val heightPx = sectionCount * sectionH
                        val offsetX = with(density) { (centerX - widthPx / 2f).toDp() }
                        val offsetY = with(density) { (centerY - heightPx / 2f).toDp() }
                        val width = with(density) { widthPx.toDp() }
                        val height = with(density) { heightPx.toDp() }
                        Box(
                            modifier = Modifier
                                .offset(x = offsetX, y = offsetY)
                                .size(width = width, height = height)
                                .padding(vertical = 2.dp)
                                .graphicsLayer {
                                    scaleX = swapFlightScale.value
                                    scaleY = swapFlightScale.value
                                }
                        ) {
                            CourseCard(
                                course = swapCourse,
                                isCurrentWeek = true,
                                wallpaperBackdrop = if (wallpaperBitmap != null) liquidGlassBackdrop else null,
                                cardBlurRadius = displayAppearance.cardBlurRadius,
                                cardRefraction = displayAppearance.cardRefraction,
                                cardAlpha = displayAppearance.cardAlpha,
                                cardSurfaceAlpha = displayAppearance.cardSurfaceAlpha,
                                // 无壁纸时垫页底色，取值见 floatSolidBacking
                                solidBackingColor = floatSolidBacking,
                                cardHeightPerSection = displayAppearance.cardHeight,
                                cardCornerRadius = displayAppearance.cardCornerRadius,
                                isTablet = isTablet,
                                cardContentAlignment = displayAppearance.cardContentAlignment,
                                cardTextColor = displayAppearance.cardTextColor,
                                cardTextScale = displayAppearance.cardTextScale,
                                showClassroom = displayAppearance.showClassroom,
                                showTeacher = displayAppearance.showTeacher,
                                disablePadding = true,
                                onClick = {}
                            )
                        }
                    }
                }
            }
        }
        if (shortcutMenuCourse != null || emptyCellMenuTarget != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // 退场动画并行，结束后再清空
                        shortcutMenuVisible = false
                        dismissFloatingCard()
                        coroutineScope.launch {
                            delay(220.milliseconds)
                            shortcutMenuCourse = null
                            emptyCellMenuTarget = null
                        }
                    }
            )
        }
        val activeShortcutCourse = shortcutMenuCourse
        val activeEmptyTarget = emptyCellMenuTarget
        if (activeShortcutCourse != null || activeEmptyTarget != null) {
            val menuItems = if (activeShortcutCourse != null) {
                buildList {
                    add(
                        ShortcutMenuItem(
                            icon = MiuixIcons.Edit,
                            label = "编辑",
                            onClick = {
                                FeatureLog.course("edit_shortcut")
                                shortcutMenuVisible = false
                                dismissFloatingCard()
                                coroutineScope.launch {
                                    delay(240.milliseconds)
                                    shortcutMenuCourse = null
                                }
                                viewModel.showEditDialog(activeShortcutCourse)
                            }
                        )
                    )
                    // ⚠ 自定义时间课不给「复制」：粘贴是按节次跨度落位的，复制它既粘不出
                    // 正确的时间段、又会让用户以为能复制出一份自定义时间课。
                    if (!activeShortcutCourse.hasValidCustomTime()) {
                        add(
                            ShortcutMenuItem(
                                icon = MiuixIcons.Copy,
                                label = "复制",
                                onClick = {
                                    FeatureLog.course("copy_shortcut")
                                    copiedCourseForPaste = activeShortcutCourse
                                    shortcutMenuVisible = false
                                    dismissFloatingCard()
                                    coroutineScope.launch {
                                        delay(240.milliseconds)
                                        shortcutMenuCourse = null
                                    }
                                    showToast("已复制")
                                }
                            )
                        )
                    }
                    add(
                        ShortcutMenuItem(
                            icon = MiuixIcons.Delete,
                            label = "删除",
                            onClick = {
                                FeatureLog.course("delete_shortcut")
                                deleteConfirmCourse = activeShortcutCourse
                                shortcutMenuVisible = false
                                dismissFloatingCard()
                                coroutineScope.launch {
                                    delay(240.milliseconds)
                                    shortcutMenuCourse = null
                                }
                                showDeleteConfirmDialog = true
                            }
                        )
                    )
                }
            } else {
                val emptyDay = activeEmptyTarget?.columnDay ?: -1
                val emptySection = activeEmptyTarget?.section ?: -1
                val emptyAddDay = activeEmptyTarget?.addDay ?: emptyDay
                val emptyAddWeek = activeEmptyTarget?.addWeek ?: -1
                buildList {
                    if (copiedCourseForPaste != null && activeEmptyTarget != null) {
                        add(
                            ShortcutMenuItem(
                                icon = MiuixIcons.Paste,
                                label = "粘贴",
                                onClick = {
                                    pasteRangeTarget = PasteTarget(
                                        columnDay = emptyDay,
                                        dataDay = emptyAddDay,
                                        section = emptySection,
                                        dataWeek = if (emptyAddWeek > 0) emptyAddWeek else currentViewingWeek,
                                    )
                                    showPasteRangeDialog = true
                                    shortcutMenuVisible = false
                                    coroutineScope.launch {
                                        delay(240.milliseconds)
                                        emptyCellMenuTarget = null
                                    }
                                }
                            )
                        )
                    }
                    if (activeEmptyTarget != null) {
                        add(
                            ShortcutMenuItem(
                                icon = MiuixIcons.Add,
                                label = "添加",
                                iconSize = 24.dp,
                                onClick = {
                                    val defaultWeeks =
                                        if (emptyAddWeek > 0) setOf(emptyAddWeek) else emptySet()
                                    viewModel.showAddDialog(emptyAddDay, emptySection, null, defaultWeeks)
                                    shortcutMenuVisible = false
                                    coroutineScope.launch {
                                        delay(240.milliseconds)
                                        emptyCellMenuTarget = null
                                    }
                                }
                            )
                        )
                    }
                }
            }
            ShortcutMenu(
                show = shortcutMenuVisible,
                items = menuItems,
                modifier = Modifier.offset(
                    // ShadowPadding 12dp，左移使可见左缘与卡片对齐
                    x = with(density) { shortcutMenuPosition.x.toDp() - 12.dp },
                    y = with(density) { (shortcutMenuPosition.y - shortcutMenuSize.height).toDp() + 4.dp }
                ),
                backdrop = liquidGlassBackdrop,
                anchorRightPx = shortcutMenuPosition.x + shortcutMenuAnchorWidth,
                onMeasuredSize = { width, height ->
                    shortcutMenuSize = IntSize(width, height)
                },
                onDismiss = {
                    shortcutMenuVisible = false
                    dismissFloatingCard()
                    coroutineScope.launch {
                        delay(220.milliseconds)
                        shortcutMenuCourse = null
                        emptyCellMenuTarget = null
                    }
                }
            )
        }
        if (showCustomizePage && customizeSnapshot != null) {
            LaunchedEffect(true) {
                if (showCustomizePage) {
                    onSystemBarsChanged(true, true)
                } else {
                    onSystemBarsChanged(false, false)
                }
            }
            val dismissCustomize: () -> Unit = {
                isApplyingCustomize = false
                // 取消时丢弃未落盘主题预览
                pendingScheduleThemeMode = null
                coroutineScope.launch {
                    blurSnapshotJob?.cancel()
                    // 动画期间保持编辑后实时状态；结束时统一恢复。仅锁定原搭配主题防跟随编辑壁纸
                    captureThemeActive = true
                    captureThemeIsDark = originalWallpaperIsLight?.let { !it }
                    // 复用进入时快照，从开洞放大回全屏盖住回退
                    customizeCoverActive = true
                    customizeCoverScale.stop()
                    customizeCoverScale.snapTo(cutoutMainScale.value)
                    customizeCoverAlpha.stop()
                    customizeCoverAlpha.snapTo(0f)
                    customizeExitScale.stop()
                    customizeExitScale.snapTo(cutoutMainScale.value)
                    customizeExitAlpha.stop()
                    customizeExitAlpha.snapTo(1f)
                    isCustomizeExiting = true
                    onSystemBarsChanged(false, false)
                }
            }
            val applyCustomize: () -> Unit = {
                coroutineScope.launch {
                    val bitmap = wallpaperBitmap
                    val isLight = combinations.getOrNull(currentCombinationIndex)?.wallpaperIsLight
                    // 仅内存预览用
                    val capturedSnapshot = captureMainContentBitmap()
                    val saveJob = launch(ioDispatcher) {
                        // 壁纸文件独立落盘（WallpaperStore.save 是 suspend，进不了 batchSave 块）；
                        // 参数快照仍合并为一次磁盘提交
                        if (bitmap != null) {
                            WallpaperStore.save(bitmap)
                        } else {
                            // 删文件，否则旧壁纸会在重启后恢复
                            WallpaperStore.clear()
                        }
                        AppearanceStore.batchSave {
                            AppearanceStore.saveState(
                                wallpaperOffset.x,
                                wallpaperOffset.y,
                                wallpaperScale,
                                // 与当前显示尺寸绑定，旋转后可按参考屏重映射
                                latestScreenWPx,
                                latestScreenHPx
                            )
                            val appearanceToSave = currentAppearance()
                            AppearanceStore.saveCardBlur(
                                appearanceToSave.cardBlurRadius
                            )
                            AppearanceStore.saveCardAlpha(
                                appearanceToSave.cardAlpha
                            )
                            AppearanceStore.saveCardSurfaceAlpha(
                                appearanceToSave.cardSurfaceAlpha
                            )
                            AppearanceStore.saveCardHeight(
                                appearanceToSave.cardHeight
                            )
                            AppearanceStore.saveCardCornerRadius(
                                appearanceToSave.cardCornerRadius
                            )
                            AppearanceStore.saveWallpaperBrightness(
                                appearanceToSave.wallpaperBrightness
                            )
                            AppearanceStore.saveWallpaperIsLight( isLight)
                            AppearanceStore.saveShowBreakDividers(
                                appearanceToSave.showBreakDividers
                            )
                            AppearanceStore.saveCardContentAlignment(
                                appearanceToSave.cardContentAlignment
                            )
                            AppearanceStore.saveCardTextColor(
                                appearanceToSave.cardTextColor
                            )
                            AppearanceStore.saveCardTextScale(
                                appearanceToSave.cardTextScale
                            )
                            AppearanceStore.saveShowClassroom(
                                appearanceToSave.showClassroom
                            )
                            AppearanceStore.saveShowTeacher(
                                appearanceToSave.showTeacher
                            )
                            AppearanceStore.saveCardRefraction(
                                appearanceToSave.cardRefraction
                            )
                            AppearanceStore.saveWallpaperBlur(
                                appearanceToSave.wallpaperBlur
                            )
                        }
                        // 「应用」才写入偏好
                        pendingScheduleThemeMode?.let { mode ->
                            saveScheduleThemeMode(mode)
                            pendingScheduleThemeMode = null
                        }
                    }
                    // 快照仅存内存；替换前记下旧 snapshot，延迟回收
                    val idx = currentCombinationIndex
                    val replacedSnapshot = combinations.getOrNull(idx)?.snapshot
                    if (idx in combinations.indices) {
                        combinations = combinations.toMutableList().also {
                            it[idx] = it[idx].copy(
                                bitmap = bitmap,
                                offset = wallpaperOffset,
                                scale = wallpaperScale,
                                snapshot = capturedSnapshot,
                                wallpaperIsLight = isLight
                            )
                        }
                    }
                    if (replacedSnapshot !== capturedSnapshot) {
                        launch {
                            delay(32.milliseconds)
                            recycleSnapshot(
                                replacedSnapshot,
                                capturedSnapshot,
                                customizeSnapshot,
                                WallpaperStore.currentImageBitmap(),
                                wallpaperBitmap,
                            )
                        }
                    }
                    savedWallpaperBitmap = bitmap
                    savedWallpaperOffset = wallpaperOffset
                    savedWallpaperScale = wallpaperScale
                    savedAppearance = currentAppearance()
                    saveJob.join()
                    isApplyingCustomize = true
                    // 从开洞大小（0.75）放大回全屏
                    customizeExitScale.snapTo(cutoutMainScale.value)
                    customizeExitAlpha.snapTo(1f)
                    isCustomizeExiting = true
                }
            }
                // ⚠ 必须 remember：`asImageBitmap()` 每次调用都新建包装对象，不 remember 会让
                // 页面里 `remember(wallpaperBitmap, …)` 之类的 key 每帧都变（白跑一遍计算）。
                val customizeSnapshotImage = customizeSnapshot
                val customizeWallpaperImage = wallpaperBitmap
                // 搭配缩略图：页面只认「两张图」，宿主的 Combination（含 Bitmap，要做
                // remap/recycle/测光）留在这一侧，按需映射。
                val customizeThumbnails = remember(combinations) {
                    combinations.map {
                        CombinationThumbnail(
                            snapshot = it.snapshot,
                            wallpaper = it.bitmap,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = customizeExitAlpha.value
                        }
                ) {
                CustomizeScheduleScreen(
                    snapshot = customizeSnapshotImage,
                    screenCornerRadius = screenCornerRadius,
                    onDismiss = dismissCustomize,
                    onApply = applyCustomize,
                    onThemeModePreview = { mode -> pendingScheduleThemeMode = mode },
                    onPickWallpaper = { onPickWallpaperImage?.invoke(applyPickedWallpaper) },
                    onClearWallpaper = {
                        wallpaperBitmap = null
                        wallpaperOffset = Offset.Zero
                        wallpaperScale = 1f
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also { list ->
                                list[idx] = list[idx].copy(
                                    bitmap = null,
                                    offset = Offset.Zero,
                                    scale = 1f,
                                    wallpaperIsLight = null
                                )
                            }
                        }
                    },
                    pendingEnterCutout = pendingEnterCutout,
                    onCutoutEntered = { pendingEnterCutout = false },
                    combinations = customizeThumbnails,
                    currentCombinationIndex = currentCombinationIndex,
                    isExiting = isCustomizeExiting,
                    isApplying = isApplyingCustomize,
                    isApplyingCustomize = isApplyingCustomize,
                    wallpaperBitmap = customizeWallpaperImage,
                    // 「默认主题」档位读写（Android = ScheduleAppearance）
                    themeMode = { loadScheduleThemeMode() },
                    onThemeModePersist = { saveScheduleThemeMode(it) },
                    wallpaperOffset = wallpaperOffset,
                    wallpaperScale = wallpaperScale,
                    onWallpaperOffsetChange = {
                        wallpaperOffset = it
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also { list ->
                                list[idx] = list[idx].copy(offset = it)
                            }
                        }
                    },
                    onWallpaperScaleChange = {
                        wallpaperScale = it
                        val idx = currentCombinationIndex
                        if (idx in combinations.indices) {
                            combinations = combinations.toMutableList().also { list ->
                                list[idx] = list[idx].copy(scale = it)
                            }
                        }
                    },
                    onCutoutCenterChange = { cutoutCenterYRatio = it },
                    sheetOffsetShared = sheetOffsetY,
                    appearance = currentAppearance(),
                    onAppearanceChange = { newAppearance ->
                        applyAppearance(newAppearance)
                    },
                    hasWallpaper = wallpaperBitmap != null,
                    previewPage = if (selectedTab == 1) 1 else 0,
                    onPreviewPageChange = { page ->
                        val target = if (page == 1) 1 else 0
                        if (selectedTab != target) {
                            mainTabProgrammatic = true
                            selectedTab = target
                            coroutineScope.launch {
                                try {
                                    if (navBarStyle == "rail") {
                                        mainPagerState.scrollToPage(target)
                                    } else {
                                        mainPagerState.animateMainTabTo(target)
                                    }
                                } finally {
                                    mainTabProgrammatic = false
                                }
                            }
                        }
                    },
                )
            }
        }

        if (snapshotCoverBitmap != null) {
            val coverImage = remember(snapshotCoverBitmap) { snapshotCoverBitmap!! }
            Image(
                bitmap = coverImage,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // 只作用于快照这一层，避免叠加闪烁
        if (customizeCoverActive && customizeSnapshot != null) {
            val customizeImage = remember(customizeSnapshot) { customizeSnapshot!! }
            Image(
                bitmap = customizeImage,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = customizeCoverAlpha.value
                        scaleX = customizeCoverScale.value
                        scaleY = customizeCoverScale.value
                        transformOrigin = TransformOrigin(0.5f, cutoutCenterYRatio)
                    }
                    .drawWithContent {
                        val path = Path().apply {
                            addScreenCornerRect(
                                width = size.width,
                                height = size.height,
                                corners = screenCornerRadius
                            )
                        }
                        clipPath(path) {
                            this@drawWithContent.drawContent()
                        }
                    },
                contentScale = ContentScale.Crop
            )
        }

        LaunchedEffect(isCustomizeExiting) {
            if (isCustomizeExiting && customizeSnapshot != null) {
                kotlinx.coroutines.coroutineScope {
                    launch {
                        customizeExitScale.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                500,
                                easing = CustomizePageEasing
                            )
                        )
                    }
                    if (!isApplyingCustomize) {
                        launch {
                            customizeCoverScale.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    500,
                                    easing = CustomizePageEasing
                                )
                            )
                        }
                        launch {
                            customizeCoverAlpha.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    400,
                                    easing = CustomizePageEasing
                                )
                            )
                        }
                    }
                    if (!isApplyingCustomize) {
                        launch {
                            customizeExitAlpha.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    500,
                                    easing = CustomizePageEasing
                                )
                            )
                        }
                    }
                    launch {
                        cutoutMainScale.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                500,
                                easing = CustomizePageEasing
                            )
                        )
                    }
                }
                isCustomizeExiting = false
                showCustomizePage = false
                val exitingSnapshot = customizeSnapshot
                val exitingCover = snapshotCoverBitmap
                customizeSnapshot = null
                snapshotCoverBitmap = null
                // combinations 可能仍持有同一 snapshot 引用，共用时不 recycle
                val keptCombSnapshots = combinations.mapNotNull { it.snapshot }.toTypedArray()
                val keptCombBitmaps = combinations.mapNotNull { it.bitmap }.toTypedArray()
                recycleSnapshot(
                    exitingSnapshot,
                    exitingCover,
                    WallpaperStore.currentImageBitmap(),
                    wallpaperBitmap,
                    originalWallpaperBitmap,
                    savedWallpaperBitmap,
                    *keptCombSnapshots,
                    *keptCombBitmaps,
                )
                recycleSnapshot(
                    exitingCover,
                    exitingSnapshot,
                    WallpaperStore.currentImageBitmap(),
                    wallpaperBitmap,
                    originalWallpaperBitmap,
                    savedWallpaperBitmap,
                    *keptCombSnapshots,
                    *keptCombBitmaps,
                )
                customizeCoverActive = false
                isWindowCutoutActive = false
                // 原搭配已恢复，forcedDark 自然接管
                captureThemeActive = false
                captureThemeIsDark = null
                if (!isApplyingCustomize) {
                    // 非应用：整体还原，覆盖编辑回调改过的字段
                    wallpaperBitmap = originalWallpaperBitmap
                    wallpaperOffset = originalWallpaperOffset
                    wallpaperScale = originalWallpaperScale
                    currentCombinationIndex = originalCombinationIndex
                    val restoreIdx = originalCombinationIndex
                    val restored = originalCombination
                    if (restoreIdx in combinations.indices && restored != null) {
                        combinations = combinations.toMutableList().also {
                            it[restoreIdx] = restored
                        }
                    }
                }
                isApplyingCustomize = false
                onSystemBarsChanged(false, false)
            }
        }
        }
        // 课程详情页必须盖在弹窗层之上（见 HostOverlayEntry.abovePopups）：它是在课程表弹出的
        // 「课程详情」底部弹窗里点卡片长出来的，弹窗得留在它下面（对齐 Android 的层级）。
        HostedOverlay(
            active = showDetail || detailSnapshot != null,
            abovePopups = true,
        ) {
            if (showDetail) {
                val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
                val sectionTimes by settingsViewModel.sectionTimes.collectAsState()
                val classStartTime by viewModel.classStartTime.collectAsState()
                val detailSnapshotImage = detailSnapshot
                CourseDetailScreen(
                    courses = detailCourses,
                    cardLeft = detailCardLeft,
                    cardTop = detailCardTop,
                    cardWidth = detailCardWidth,
                    cardHeight = detailCardHeight,
                    screenWidth = windowInfo.containerSize.width.toFloat(),
                    screenHeight = windowInfo.containerSize.height.toFloat(),
                    screenCornerRadius = screenCornerRadius,
                    cardSnapshot = detailSnapshotImage,
                    sectionTimes = sectionTimes,
                    classStartTime = classStartTime,
                    teachingWeekReorganizations = teachingWeekRules,
                    targetWeek = detailTargetWeek,
                    onBackStart = {
                        detailExiting = true
                        coroutineScope.launch {
                            managePageBlurRadius.animateTo(
                                0f,
                                animationSpec = tween(320, easing = OobeCubicOutEasing)
                            )
                        }
                    },
                    onBack = {
                        hiddenCourseIds = emptySet()
                        // ⚠ 详情页延后一帧再消失：morph 结束时卡片已缩回原位，此刻 sheet 里
                        // 的原卡片还没画出来，同帧摘掉详情页就闪一帧空白。
                        coroutineScope.launch {
                            withFrameNanos { }
                            showDetail = false
                            detailExiting = false
                            detailSnapshot = null
                        }
                    }
                )
            }
        }
        // Retain the card morph below native navigation chrome, not in the modal layer.
        androidx.compose.runtime.CompositionLocalProvider(
            com.haooz.chedule.ui.utils.LocalHostOverlays provides
                (hostNavigation?.scheduleLibraryOverlays ?: com.haooz.chedule.ui.utils.LocalHostOverlays.current),
        ) {
        HostedOverlay(active = showSwitchSchedule || switchAnimRunning || switchOverlayActive) {
        // Entry shading belongs below native controls, never in the modal layer.
        if (isEntryAnimating) {
            Box(Modifier.fillMaxSize().background(Color.Black).graphicsLayer {
                alpha = switchReturnBgScrim.value * if (isDark) 1f else 0.6f
            })
        }
        // 切换课表：p 在卡片↔全屏之间形变；进入 p:1→0，退出 p:0→1
        if (showSwitchSchedule) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // A zero-alpha layer may skip recording its target snapshot on iOS.
                        alpha = if (switchPreparingLayer) 0.001f else 1f
                    }
            ) {
                val switchShareRepository = remember { CourseRepository() }
                androidx.compose.runtime.CompositionLocalProvider(
                    com.haooz.chedule.ui.utils.LocalNativeDocumentChrome provides
                        if (nativeBottomBar && !isTablet) hostNavigation?.scheduleLibraryChrome else null,
                ) {
                SwitchScheduleScreen(
                    onBackStart = {
                        switchExiting = true
                        switchPendingReverse = false
                        switchPreparingLayer = false
                    },
                    onBack = { cardLayer, bounds ->
                        if (!switchExiting || (switchAnimRunning && !switchAnimForward)) {
                            return@SwitchScheduleScreen
                        }
                        val wasForward = switchAnimForward
                        switchAnimForward = false
                        switchAnimRunning = true
                        switchAnimJob?.cancel()
                        switchAnimJob = coroutineScope.launch {
                            switchMainLayerJob?.join()
                            if (scheduleChanged) {
                                scheduleChanged = false
                                switchReloadJob?.join()
                                switchReloadJob = null
                                withFrameNanos { }
                                prepareMainContentLayer()
                            }
                            switchOverlayActive = true
                            switchCardBounds = bounds?.takeIf {
                                cardLayer != null && it.width > 0f && it.height > 0f
                            }
                            switchCardSnapshot = cardLayer
                            switchCardBitmap = null
                            // 固化给**下次进入**复用（进入方向不录制任何东西）
                            if (cardLayer != null) {
                                launch {
                                    val layerSize = cardLayer.layer.size
                                    val src = cardLayer.bounds ?: Rect(
                                        0f,
                                        0f,
                                        layerSize.width.toFloat(),
                                        layerSize.height.toFloat(),
                                    )
                                    val cardW = src.width.toInt()
                                    val cardH = src.height.toInt()
                                    val bmp = if (cardW <= 0 || cardH <= 0) {
                                        null
                                    } else {
                                        runCatching {
                                            // 只把**卡片那一块**录进一张卡片大小的小图层，再转位图
                                            retainedSwitchCardScratchLayer.record(
                                                density,
                                                switchLayerLayoutDirection,
                                                IntSize(cardW, cardH),
                                            ) {
                                                translate(-src.left, -src.top) {
                                                    drawLayer(cardLayer.layer)
                                                }
                                            }
                                            retainedSwitchCardScratchLayer.toImageBitmap()
                                        }.getOrNull()
                                    }
                                    if (bmp != null) {
                                        retainedSwitchCardBitmap = bmp
                                        // 落盘：保持到下次切换课表退出为止
                                        SwitchCardSnapshotStore.save(bmp)
                                    }
                                }
                            }
                            val remainingDuration =
                                ((1f - switchAnimProgress.value) * 540).toInt().coerceAtLeast(1)
                            switchAnimProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(remainingDuration, easing = OobeQuartOutEasing)
                            )
                            showSwitchSchedule = false
                            FeatureLog.t("切换课表", "close", "forward=$wasForward")
                            switchOverlayActive = false
                            switchCardSnapshot = null
                            switchCardBitmap = null
                            switchCardBounds = null
                            switchAnimRunning = false
                        }
                    },
                    onScheduleChanged = {
                        FeatureLog.t("切换课表", "schedule_changed")
                        switchReloadJob = viewModel.reloadCourses()
                        settingsViewModel.refreshSettings()
                        scheduleChanged = true
                        // 切换页直接改 repository，须把 ScheduleViewModel 拉回一致；摘要放 IO
                        coroutineScope.launch(ioDispatcher) {
                            scheduleViewModel.refreshScheduleList()
                        }
                    },
                    onScreenReady = { cardBounds ->
                        if (!switchPendingReverse || switchExiting) {
                            FeatureLog.t("切换课表", "screen_ready_stale", "pending=false")
                        } else {
                            switchPendingReverse = false
                            switchAnimForward = true
                            switchAnimRunning = true
                            FeatureLog.t("切换课表", "anim_enter_start", "bounds=$cardBounds")
                            switchAnimJob?.cancel()
                            val cardBoundsInScreen = Rect(
                                left = switchContentRootX + cardBounds.left,
                                top = switchContentRootY + cardBounds.top,
                                right = switchContentRootX + cardBounds.right,
                                bottom = switchContentRootY + cardBounds.bottom
                            )
                            switchAnimJob = coroutineScope.launch {
                                switchMainLayerJob?.join()
                                switchAnimProgress.snapTo(1f)
                                switchReturnBgScrim.snapTo(0.4f)
                                switchPreparingLayer = false
                                switchOverlayActive = true
                                switchCardBounds = cardBoundsInScreen.takeIf {
                                    it.width > 0f && it.height > 0f
                                }
                                switchCardSnapshot = null
                                // 复用上次退出固化的卡片位图 —— 进入零录制
                                switchCardBitmap = retainedSwitchCardBitmap
                                val remainingDuration = 320
                                val morphExitEase = CubicBezierEasing(0.3f, 0.65f, 0.35f, 1.0f)
                                launch {
                                    switchReturnBgScrim.animateTo(
                                        0f,
                                        animationSpec = tween(remainingDuration, easing = morphExitEase)
                                    )
                                }
                                switchAnimProgress.animateTo(
                                    0f,
                                    animationSpec = tween(remainingDuration, easing = OobeCubicOutEasing)
                                )
                                FeatureLog.t("切换课表", "anim_enter_end", "p=${switchAnimProgress.value}")
                                switchOverlayActive = false
                                switchCardSnapshot = null
                                switchCardBitmap = null
                                switchCardBounds = null
                                switchAnimRunning = false
                            }
                        }
                    },
                    onContentOffset = { x, y ->
                        switchContentRootX = x
                        switchContentRootY = y
                    },
                    pageScale = 1f,
                    onShareSchedule = { name, changed ->
                        onShareSchedule?.invoke(name, changed)
                    },
                    isScheduleShareable = { name ->
                        buildShareScheduleMap(switchShareRepository, name) != null
                    },
                    initialScheduleNames = scheduleViewModel.scheduleNames.collectAsState().value,
                    initialCurrentScheduleId = scheduleViewModel.currentScheduleName.collectAsState().value,
                    initialScheduleSummaries = scheduleViewModel.scheduleSummaries.collectAsState().value
                )
                }
            }
            // Morph between the retained main layer and the destination card.
            SwitchMorphOverlay(
                active = switchOverlayActive,
                progress = switchAnimProgress,
                entering = switchAnimForward,
                cardBounds = switchCardBounds,
                cardSnapshot = switchCardSnapshot,
                cardBitmap = switchCardBitmap,
                mainLayer = screenGraphicsLayer,
                screenCornerRadius = screenCornerRadius,
                isDark = isDark,
            )
        }

        }
        }
        HostedOverlay(active = showShiftLoading || shiftLoadingActive) {
        ShiftLoadingOverlay(
            show = showShiftLoading,
            onShiftReady = {
                if (isExitingShift) {
                    FeatureLog.shift("exit_confirmed")
                    shiftViewModel.exitShiftMode()
                    selectedTab = 0
                    selectedTab = 0
                    coroutineScope.launch { mainPagerState.scrollToPage(0) }
                } else {
                    FeatureLog.shift("enter_confirmed")
                    shiftViewModel.enterShiftMode()
                }
            },
            onHide = { showShiftLoading = false },
            onActivityChanged = { shiftLoadingActive = it },
        )
        }
    }
}

@Composable
private fun SwitchMorphOverlay(
    active: Boolean,
    progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    /** true = 进入切换课表页（p: 1→0）；false = 退出回主界面（p: 0→1）。 */
    entering: Boolean,
    cardBounds: Rect?,
    cardSnapshot: TransitionLayerSnapshot?,
    /**
     * 进入方向复用的卡片位图（退出那一帧从卡片图层固化而来）。
     * 图层跨不过切换页的组合销毁，所以跨次复用的载体只能是位图。
     */
    cardBitmap: ImageBitmap?,
    mainLayer: GraphicsLayer,
    screenCornerRadius: ScreenCornerRadiiPx,
    isDark: Boolean,
) {
    // 门控必须留在本 composable 内：退出的兜底分支会出现
    // showSwitchSchedule && !switchOverlayActive 的窗口，无条件绘制会闪一帧全屏底色
    if (!active) return
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val screenWidth = windowInfo.containerSize.width.toFloat()
    val screenHeight = windowInfo.containerSize.height.toFloat()
    val density = LocalDensity.current
    val p = progress.value

    // The source layer stays owned by the main composition throughout the morph.
    val cardImage = remember(cardSnapshot) { cardSnapshot }
    // 本次形变有没有可用的卡片内容：进入看位图，退出看实时图层。
    val hasCard = if (entering) cardBitmap != null else cardImage != null

    val sBounds = cardBounds
    val cLeft: Float
    val cTop: Float
    val cWidth: Float
    val cHeight: Float
    if (sBounds != null) {
        cLeft = sBounds.left + (0f - sBounds.left) * p
        cTop = sBounds.top + (0f - sBounds.top) * p
        cWidth = sBounds.width + (screenWidth - sBounds.width) * p
        cHeight = sBounds.height + (screenHeight - sBounds.height) * p
    } else {
        cLeft = 0f; cTop = 0f; cWidth = screenWidth; cHeight = screenHeight
    }
    val startRadius = with(density) { 20.dp.toPx() }
    val cShape = screenCornerRadius.interpolateFrom(startRadius, p).toShape(density)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) Color(0xFF2C2C2C).copy(alpha = (p * 0.5f).coerceIn(0f, 0.5f))
                else Color.Black.copy(alpha = (p * 0.5f).coerceIn(0f, 0.5f))
            )
    )
    Box(
        modifier = Modifier
            .offset(
                x = with(density) { cLeft.toDp() },
                y = with(density) { cTop.toDp() }
            )
            .size(
                width = with(density) { cWidth.toDp() },
                height = with(density) { cHeight.toDp() }
            )
            .clip(cShape)
            .graphicsLayer {
                // 进入方向：有可交叉的卡片时容器整段全实
                alpha = if (entering) {
                    if (!hasCard) (p / 0.2f).coerceIn(0f, 1f) else 1f
                } else {
                    1f
                }
            }
            .background(MiuixTheme.colorScheme.background)
    ) {
        if (cardBitmap != null) {
            // 进入方向：画退出那一帧固化下来的卡片位图。
            // 它是容器的子节点（.align(TopStart)）→ 固定在快照顶部、跟着容器一起缩。
            Image(
                bitmap = cardBitmap,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(ContinuousRoundedRectangle(20.dp))
                    .graphicsLayer {
                        // 关键：必须在主界面快照开始化之前（p=0.8）就铺满
                        alpha = ((1f - p) / 0.2f).coerceIn(0f, 1f)
                    },
                contentScale = ContentScale.None,
            )
        }
        if (cardImage != null) {
            TransitionLayerImage(
                snapshot = cardImage,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(ContinuousRoundedRectangle(20.dp))
                    .graphicsLayer {
                        alpha = if (entering) {
                            ((1f - p) / 0.2f).coerceIn(0f, 1f)
                        } else {
                            (1f - p * 2f).coerceIn(0f, 1f)
                        }
                    },
                contentScale = ContentScale.None
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = when {
                        !entering -> ((p - 0.2f) / 0.7f).coerceIn(0f, 1f)
                        // 没有可交叉的卡片（本次会话第一次进入、还没退出过）：
                        // 保持不透明、由容器 alpha 收尾，否则中段只剩容器纯色底。
                        !hasCard -> 1f
                        // 淡出：起点沿用你改的 0.8，终点 0.2（跨 0.6 个进度）
                        else -> ((p - 0.2f) / 0.6f).coerceIn(0f, 1f)
                    }
                }
                .drawWithContent {
                    val layerSize = mainLayer.size
                    if (layerSize.width > 0 && layerSize.height > 0) {
                        // Match the previous snapshot's Crop / TopCenter alignment.
                        val scale = maxOf(
                            size.width / layerSize.width,
                            size.height / layerSize.height,
                        )
                        translate(left = (size.width - layerSize.width * scale) / 2f) {
                            scale(scale, scale, pivot = Offset.Zero) {
                                drawLayer(mainLayer)
                            }
                        }
                    }
                }
        )
    }
}

/**
 * 共享顶栏槽里单页顶栏的主题锁：外层 chrome 主题跟随当前 tab（[forcedDark]），
 * 切页时会先变，尚未移出屏幕的顶栏遮罩瞬间变色；这里按本页自己的主题再锁一层。
 */
@Composable
private fun PageLockedTopBarTheme(
    pageForcedDark: Boolean?,
    content: @Composable () -> Unit,
) {
    val appDark = rememberAppSettingDark()
    val pageDark = pageForcedDark ?: appDark
    val controller = rememberAppThemeController(isDark = pageDark)
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(LocalForcedDarkTheme provides pageForcedDark) {
            content()
        }
    }
}

@Composable
private fun SettingsTopBar(
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    navBarStyle: String,
    scrollBehavior: SharedScrollBehavior? = null,
    blurResampleKey: Int = 0,
    blurSampleTrack: () -> Float = { 0f },
) {
    if (liquidGlassBackdrop == null) return
    val isTablet = navBarStyle == "rail"
    // 仅设置页顶栏锁应用主题，避免切页时渐变/糊层随课程表壁纸锁色跳变
    val settingsBarDark = rememberAppSettingDark()
    val settingsBarController = rememberAppThemeController(isDark = settingsBarDark)

    // 平板设置：渐变画在 TabletSettingsScreen 内容层，这里不再叠全宽遮罩
    val settingsNativeBarInset = (LocalHostNavigation.current?.topBarInset ?: 0.0).dp
    MiuixTheme(controller = settingsBarController) {
        ProgressiveBlurTopBar(
            backdrop = liquidGlassBackdrop,
            blurAlpha = if (isTablet) 0f else 1f,
            topInsetAdjustment = settingsNativeBarInset,
            resampleKey = blurResampleKey,
            sampleTrack = blurSampleTrack,
        ) {
            CollapsibleTopAppBar(
                // 平板设置：顶栏不再显示「我的」，标题由 MainActivity 叠层绘制
                title = if (isTablet) "" else "我的",
                largeTitle = if (isTablet) "" else "我的",
                showLargeTitle = if (isTablet) false else null,
                showSmallTitle = if (isTablet) true else null,
                showGradientOverlay = !isTablet,
                gradientColorOverride = MiuixTheme.colorScheme.surface,
                modifier = Modifier.zIndex(1f),
                scrollBehavior = if (isTablet) null else scrollBehavior,
                startAction = null,
            )
        }
    }
}

@Composable
private fun TodayTopBar(
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    navBarStyle: String,
    currentDayOfWeek: Int,
    isToday: Boolean = true,
    onBackToToday: () -> Unit = {},
    onJumpToDate: () -> Unit = {},
    onEnterCustomize: () -> Unit = {},
    scrollBehavior: SharedScrollBehavior? = null,
    visible: Boolean = true,
    blurResampleKey: Int = 0,
    blurSampleTrack: () -> Float = { 0f },
    onMoreSlotTop: (Float) -> Unit = {},
    onMoreMaterial: (Float) -> Unit = {},
    onMoreExpand: () -> Unit = {},
) {
    if (liquidGlassBackdrop == null) return
    val isTabletLiquidGlass = navBarStyle == "rail"
    // 同 ScheduleTopBar：iOS 今日页右上角由原生 UIMenu 接管，Compose 侧不渲染
    val hostOwnsTopRight = LocalHostNavigation.current != null
    // 平板：标题避让侧栏后左对齐用的附加修饰符
    val titleRailPadding = if (isTabletLiquidGlass) {
        com.haooz.chedule.ui.components.tabletNavRailStartPadding().padding(start = 12.dp)
    } else {
        Modifier
    }
    val dayOfWeekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val dayOfWeekName = if (currentDayOfWeek in 1..7) dayOfWeekNames[currentDayOfWeek - 1] else ""
    val titleText = if (isToday) "今天是$dayOfWeekName" else dayOfWeekName
    val todayNativeBarInset = (LocalHostNavigation.current?.topBarInset ?: 0.0).dp

    // 隐藏时 alpha=0 但仍测量，保证 currentHeightPx 启动即就位
    ProgressiveBlurTopBar(
        backdrop = liquidGlassBackdrop,
        topInsetAdjustment = todayNativeBarInset,
        modifier = Modifier.graphicsLayer { alpha = if (visible) 1f else 0f },
        resampleKey = blurResampleKey,
        sampleTrack = blurSampleTrack,
    ) {
        CollapsibleTopAppBar(
            title = titleText,
            largeTitle = titleText,
            modifier = Modifier.zIndex(1f),
            scrollBehavior = scrollBehavior,
            showGradientOverlay = false,
            // 平板：标题避让左侧侧栏后左对齐（手机仍居中）
            titleStartAligned = isTabletLiquidGlass,
            titleModifier = titleRailPadding,
            // 平板左上角不放返回按钮
            startAction = null,
            onAlphaChanged = { backdrop, _ -> onMoreMaterial(backdrop) },
            endAction = { backdropAlpha, shadowAlpha ->
                if (visible) {
                    if (isTabletLiquidGlass) {
                        // pad：课程管理已在侧栏，右上角直接放跳转日期 + 课表外观
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LiquidTopBarButton(
                                onClick = onJumpToDate,
                                backdrop = liquidGlassBackdrop,
                                icon = MiuixIcons.Basic.FastForward,
                                contentDescription = "跳转日期",
                                iconSize = 23.dp,
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                            LiquidTopBarButton(
                                onClick = onEnterCustomize,
                                backdrop = liquidGlassBackdrop,
                                icon = MiuixIcons.Background,
                                contentDescription = "课表外观",
                                iconSize = 23.dp,
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                        }
                    } else {
                        // 「更多」按钮由下拉菜单组件自带（收起态即那颗按钮，唯一一份），这里只占位对齐
                        Spacer(
                            modifier = Modifier
                                .size(42.dp)
                                .then(
                                    if (!hostOwnsTopRight) Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onMoreExpand,
                                        )
                                        .semantics { contentDescription = "更多" }
                                    else Modifier
                                )
                                .onGloballyPositioned { onMoreSlotTop(it.positionInRoot().y) }
                        )
                    }
                }
            },
        )
    }
}
