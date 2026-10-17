package com.haooz.chedule.ui.activities.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.AppearanceConfig
import com.haooz.chedule.data.AppearanceStore
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.data.TeachingWeekReorganizationRule
import com.haooz.chedule.data.ThemeMode
import com.haooz.chedule.data.UpdateController
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.WallpaperTransform
import com.haooz.chedule.data.todayLocalDate
import com.haooz.chedule.ui.activities.today.TodayAssistantCardData
import com.haooz.chedule.ui.components.SharedFileIntent
import com.haooz.chedule.ui.effects.motion.CUSTOMIZE_COVER_FADE_MILLIS
import com.haooz.chedule.ui.effects.motion.CUSTOMIZE_COVER_HOLD_MILLIS
import com.haooz.chedule.ui.effects.motion.CUSTOMIZE_COVER_SCALE_MILLIS
import com.haooz.chedule.ui.effects.motion.CustomizePageEasing
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.ui.activities.classschedule.ScheduleGridGeometry
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.HostNavigationState
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.captureScreenWithNativeChrome
import com.haooz.chedule.ui.utils.clipDuringTransition
import com.haooz.chedule.ui.utils.clipScreenCorners
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

/**
 * 课表主界面的全部可变状态与交互逻辑。
 *
 * 从 `CourseScheduleApp.kt` 拆出：主函数只负责「注入上下文 + 跑组合副作用 + 画 UI」，
 * 具体状态都挂在这里。实例由 `remember { }` 持有，生命周期与原局部 `remember` 一致。
 *
 * ⚠ 这里的字段分三类，别混：
 * 1. `by mutableStateOf(...)` —— 真正的界面状态（原来是 `remember { mutableStateOf }`）。
 * 2. `var xxx: T = 默认` —— 每次重组由 Composable 侧同步的「当前值快照」
 *    （原来是 Composable 里的 `val xxx = ...` 或 `collectAsState()`）；不要在这边改。
 * 3. `lateinit var xxx` —— 组合期创建的实例（PagerState / GraphicsLayer / Density…），
 *    注入一次后不变。
 */

@Stable
class CourseScheduleAppState {

    // ── 1. 组合期注入的实例（注入一次，之后不变）────────────────────────────
    lateinit var viewModel: CourseViewModel
    lateinit var scheduleViewModel: ScheduleViewModel
    lateinit var settingsViewModel: SettingsViewModel
    lateinit var shiftViewModel: ShiftViewModel
    lateinit var router: AppRouter
    lateinit var liquidGlassBackdrop: LayerBackdrop
    lateinit var sharedWallpaperBackdrop: LayerBackdrop
    lateinit var windowInfo: WindowInfo
    lateinit var densityProvider: Density
    lateinit var detailLayerLayoutDirection: LayoutDirection
    lateinit var switchLayerLayoutDirection: LayoutDirection
    lateinit var hapticFeedback: HapticFeedback
    lateinit var coroutineScope: CoroutineScope
    lateinit var screenGraphicsLayer: GraphicsLayer
    lateinit var retainedDetailLayer: GraphicsLayer
    lateinit var retainedSwitchCardScratchLayer: GraphicsLayer
    lateinit var scheduleScrollState: ScrollState
    lateinit var todayListState: LazyListState
    lateinit var screenCornerRadius: ScreenCornerRadiiPx
    lateinit var pagerState: PagerState
    lateinit var todayPagerState: PagerState
    lateinit var mainPagerState: PagerState
    var hostNavigation: HostNavigationState? = null
    var settingsScrollBehavior: SharedScrollBehavior? = null
    var todayScrollBehavior: SharedScrollBehavior? = null
    var scheduleScrollBehavior: SharedScrollBehavior? = null
    lateinit var mainContentNestedScroll: NestedScrollConnection

    // ── 2. 每次重组同步的当前值（别在这边写）────────────────────────────────
    var isDark: Boolean = false
    var appSettingDark: Boolean = false
    var isShiftMode: Boolean = false
    var totalWeeks: Int = 0
    var currentWeek: Int = 0
    var classStartTime: String = ""
    var morningSections: Int = 0
    var afternoonSections: Int = 0
    var eveningSections: Int = 0
    var configuredSectionTimes: Map<Int, String> = emptyMap()
    var holidayDataRevision: Long = 0L
    var smartWeekend: Boolean = false
    var dataVersion: Int = 0
    var teachingWeekRules: List<TeachingWeekReorganizationRule> = emptyList()
    var holidayVersion: Long = 0L
    var autoAdvancePage: Int = 0
    var courses: List<Course> = emptyList()
    var dayRange: List<Int> = emptyList()
    var weekDates: List<LocalDate> = emptyList()
    var settingsPageClipShape: Shape? = null
    var settingsTopBarClipShape: Shape? = null
    var settingsCardOnScreen: () -> Boolean = { false }
    var latestScreenWPx: Float = 0f
    var latestScreenHPx: Float = 0f
    var latestMainTabSelected: (Int) -> Unit = {}
    var cancelUnfinishedMainTabAnim: () -> Unit = {}
    var pushCutoutChromeOffset: (Double) -> Unit = {}
    var isTablet: Boolean = false
    var screenWPx: Float = 0f
    var screenHPx: Float = 0f
    var glowAlpha: Float = 0f
    var persistedScheduleThemeMode: ThemeMode = ThemeMode.FOLLOW_APP
    var initialCombWallpaperIsLight: Boolean? = null

    // ── 3. 宿主注入的平台能力 ─────────────────────────────────────────────
    var privacyConsented: Boolean = true
    var resumeCount: Int = 0
    var isInFreeformWindow: Boolean = false
    var onSystemBarsChanged: (statusDark: Boolean, navDark: Boolean) -> Unit = { _, _ -> }
    var onHolidayDataChanged: () -> Unit = {}
    var onPickWallpaperImage: (((ImageBitmap?) -> Unit) -> Unit)? = null
    var onShareSchedule: ((scheduleName: String, onSharingChanged: (Boolean) -> Unit) -> Unit)? =
        null
    var onReadSharedFileText: ((String) -> String?)? = null
    var sharedFileIntent: SharedFileIntent? = null
    var onShareIntentConsumed: () -> Unit = {}
    var settingsPane: (@Composable (SettingsPaneArgs) -> Unit)? = null
    var todayAssistantCard: (@Composable (TodayAssistantCardData) -> Unit)? = null
    var updateController: UpdateController? = null
    var showUpdateSettings: Boolean = true

    // ── 4. 界面状态与交互逻辑（原主函数体）──────────────────────────────────
    var selectedTab by mutableIntStateOf(1)
    var showShiftLoading by mutableStateOf(false)
    var shiftLoadingActive by mutableStateOf(false)
    var isExitingShift by mutableStateOf(false)
    var shiftModeInitialized by mutableStateOf(false)

    // 供今日/课程表卡片玻璃采样：录的是共享壁纸层，不是页内透明占位
    val totalSections get() = morningSections + afternoonSections + eveningSections

    // 只在「返回」时刷新；冷启动首次 onResume 时 ViewModel 刚加载完，再全量刷会拖慢首屏
    // 节假日/调休保存不走课程 reload；resume 时对比 HolidayManager 版本，变了才 bump dataVersion
    var seenHolidayVersion by mutableLongStateOf(HolidayManager.getVersion())
    val navBarStyle get() = if (isTablet) "rail" else "standard"
    val nativeBottomBar get() = hostNavigation != null && !isTablet
    val useVerticalMainPager
        get() = isTablet ||
                (nativeBottomBar && hostNavigation?.usesVerticalTabTransitions == true)

    // 首帧后预热 RenderEffect，避免首次开 BlurBottomSheet 掉帧
    var warmupBlurPx: Float = 0f
    var blurWarmupReady by mutableStateOf(false)

    // 侧栏展开状态用全局对象；内容让位只在 layout 读进度，避免整树每帧重组
    // 侧栏伸缩后强制续录 + 顶栏糊层重采样：否则采样停在旧帧，切页才会恢复
    val railBlurResampleEpoch = mutableIntStateOf(0)
    val railForceRecordFrames = intArrayOf(0)
    var showCourseDetailPopup by mutableStateOf(false)
    var detailCourses by mutableStateOf<List<Course>>(emptyList())
    var detailCardLeft by mutableFloatStateOf(0f)
    var detailCardTop by mutableFloatStateOf(0f)
    var detailCardWidth by mutableFloatStateOf(0f)
    var detailCardHeight by mutableFloatStateOf(0f)
    var detailSnapshot by mutableStateOf<TransitionLayerSnapshot?>(null)
    var showDetail by mutableStateOf(false)

    // 详情页退出中：主内容提前恢复绘制（快照垫底），画出来后再撤快照。见 onBackStart。
    var detailExiting by mutableStateOf(false)
    var detailFromToday by mutableStateOf(false)
    var detailTargetWeek by mutableIntStateOf(0)
    var hiddenCourseIds by mutableStateOf(setOf<String>())
    var isDraggingCard by mutableStateOf(false)
    var draggingCourseIds by mutableStateOf(setOf<String>())
    var draggedCardCourse by mutableStateOf<Course?>(null)
    var draggedCardPosition by mutableStateOf(Offset.Zero)
    var draggedCardSize by mutableStateOf(Offset.Zero)

    // 速度/采样时间只在 spring 起始读；offset 走 floatingOffset* 的 graphicsLayer，不进组合
    val dragMotion = DragMotionHolder()
    var draggedCardBackdrop by mutableStateOf<Backdrop?>(null)
    var draggedWeek by mutableIntStateOf(1)

    // 源卡片所在的**列**星期（几何坐标）。调课日列的数据星期 ≠ 列星期，
    // 落点原格高亮、几何吸附都要用列，写库才用数据星期
    var draggedColumnDay by mutableIntStateOf(1)

    // 拖拽落点检测用网格几何
    var gridGeometry by mutableStateOf<ScheduleGridGeometry?>(null)

    // (dayOfWeek, startSection)，null=无有效落点
    var pendingDropTarget by mutableStateOf<Pair<Int, Int>?>(null)
    var animateInCourseIds by mutableStateOf(setOf<String>())
    var showRescheduleConflictDialog by mutableStateOf(false)
    var pendingConflictCourse by mutableStateOf<Course?>(null)

    // 退出动画期间保持 true，结束后原卡片 alpha 恢复 1
    var floatingCardVisible by mutableStateOf(false)

    // 手指正拖着浮层：不能拿 floatingCardVisible 当高光开关——它从长按即为 true，
    // 且要等约 360ms 退场动画跑完才回 false，会出现「长按没拖也亮」「松手后不灭」。
    var isFloatingCardDragging by mutableStateOf(false)

    // 入场 0.94→1.04，退场 1.04→1.0
    val floatingScale = Animatable(0.94f)

    // 吸附期间用 floatingOffsetAnim 替代 draggedCardOffset
    var isSnapping by mutableStateOf(false)

    // 浮层平移：拖拽回调非挂起，用 mutableFloatStateOf；只在 graphicsLayer 读，不触发组合
    val floatingOffsetX = mutableFloatStateOf(0f)
    val floatingOffsetY = mutableFloatStateOf(0f)

    // 高光开关 = 正在拖 && 浮层还活着：声明在 Scaffold 之前，Scaffold 内容里的提饱和节点要用它。
    // 一松手 onCourseDragEnd 立刻置 false 开始淡出，不等 360ms 退场动画。
    val glowShowing get() = isFloatingCardDragging && floatingCardVisible
    suspend fun animateFloatState(
        state: MutableFloatState,
        target: Float,
        spec: AnimationSpec<Float>,
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
    var isPasteFlight by mutableStateOf(false)

    // 落地冲击波：周围课程卡按距离延迟涟漪
    var landRippleCenter by mutableStateOf(Offset.Zero)
    var landRippleToken by mutableIntStateOf(0)

    // 冲突悬停：浮层停在目标卡上方等待用户选择
    var isConflictHover by mutableStateOf(false)
    var conflictHoverBobJob by mutableStateOf<Job?>(null)

    // 交换时目标课作为第二浮层交叉飞行
    var swapFlightVisible by mutableStateOf(false)
    var swapFlightCourse by mutableStateOf<Course?>(null)
    var swapFlightOriginCenter by mutableStateOf(Offset.Zero)
    var swapFlightWidth by mutableFloatStateOf(0f)
    val swapFlightOffsetX = Animatable(0f)
    val swapFlightOffsetY = Animatable(0f)
    val swapFlightScale = Animatable(1f)
    var shortcutMenuCourse by mutableStateOf<Course?>(null)
    var shortcutMenuVisible by mutableStateOf(false)
    var shortcutMenuPosition by mutableStateOf(Offset.Zero)
    var shortcutMenuSize by mutableStateOf(IntSize.Zero)
    var shortcutMenuBackdrop by mutableStateOf<Backdrop?>(null)

    // 左移时菜单右边缘对齐卡片右边缘
    var shortcutMenuAnchorWidth by mutableFloatStateOf(0f)

    // (星期, 起始节次)；粘贴目标=长按格
    // columnDay 用于格子高亮；addDay/addWeek 供「添加」默认落到调课来源星期/周
    var emptyCellMenuTarget by mutableStateOf<EmptyCellMenuTarget?>(null)

    // 页面会话级剪贴板，再次复制覆盖，粘贴后保留
    var copiedCourseForPaste by mutableStateOf<Course?>(null)
    var showPasteRangeDialog by mutableStateOf(false)
    var pasteRangeTarget by mutableStateOf<PasteTarget?>(null)
    var showDeleteConfirmDialog by mutableStateOf(false)
    var deleteConfirmCourse by mutableStateOf<Course?>(null)
    var showCustomizePage by mutableStateOf(false)
    var customizeSnapshot by mutableStateOf<ImageBitmap?>(null)
    var snapshotCoverBitmap by mutableStateOf<ImageBitmap?>(null)
    var isCustomizeExiting by mutableStateOf(false)
    var customizeExitTargetScale by mutableFloatStateOf(1f)
    val customizeExitScale = Animatable(1f)
    val customizeExitAlpha = Animatable(1f)
    var isWindowCutoutActive by mutableStateOf(false)

    // 动画只作用于快照覆盖层，避免双缩放叠加闪烁
    var customizeCoverActive by mutableStateOf(false)
    val customizeCoverScale = Animatable(1f)
    val customizeCoverAlpha = Animatable(1f)
    val wallpaperRepository = CourseRepository()
    var combinations by mutableStateOf(listOf<ScheduleCombination>())
    var currentCombinationIndex by mutableIntStateOf(0)
    var wallpaperBitmap by mutableStateOf(WallpaperStore.currentImageBitmap())
    var wallpaperOffset by mutableStateOf(WallpaperStore.wallpaperOffset)
    var wallpaperScale by mutableFloatStateOf(WallpaperStore.transformScale)

    // offset/scale 当前对应的屏幕尺寸；旋转后按此重映射，避免像素值跨方向复用
    var wallpaperScreenRef by mutableStateOf(0f to 0f)

    // 截快照时临时按该搭配的壁纸亮暗覆盖主题
    var captureThemeActive by mutableStateOf(false)
    var captureThemeIsDark by mutableStateOf<Boolean?>(null)

    // 壁纸主题锁定：课程表页有壁纸时按测光强制浅/深色；今日页仅开启显示壁纸时锁定；设置页跟随系统
    val currentComb get() = combinations.getOrNull(currentCombinationIndex)
    val currentCombIsLight get() = currentComb?.wallpaperIsLight

    // 壁纸异步加载，首帧同步读持久化结果，避免主题跳变
    // 搭配未加载（首帧）才退回 initial，避免无壁纸搭配误用初始测光
    val combIsLight get() = if (currentComb == null) initialCombWallpaperIsLight else currentCombIsLight

    // 独立偏好 key，与全局主题开关隔离
    // 编辑中的临时档位：「应用」才落盘
    var pendingScheduleThemeMode by mutableStateOf<ThemeMode?>(null)
    val scheduleThemeMode get() = pendingScheduleThemeMode ?: persistedScheduleThemeMode

    // 壁纸强制主题：不依赖 selectedTab，三页各自预先算好，切页不再改主题
    // 无壁纸时「默认主题」不生效
    val wallpaperForcedDark: Boolean?
        get() {
            if (isShiftMode) return null
            val light = combIsLight ?: return null
            return when (scheduleThemeMode) {
                ThemeMode.FOLLOW_WALLPAPER -> !light
                ThemeMode.FOLLOW_APP -> null
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
        }

    // 各页预先固定的主题（与 selectedTab 无关）
    // 今日页与课程表页壁纸恒同步，故两者共用同一壁纸锁定主题
    val todayPageForcedDark get() = wallpaperForcedDark
    val settingsPageForcedDark: Boolean? get() = null

    // 顶栏/底栏等 chrome 跟当前页有效主题
    val forcedDark
        get() = when {
            isShiftMode -> null
            showCustomizePage -> wallpaperForcedDark
            selectedTab == 2 -> null
            selectedTab == 1 -> wallpaperForcedDark
            selectedTab == 0 -> todayPageForcedDark
            else -> null
        }
    val effectiveIsDark get() = forcedDark ?: isDark

    // 状态栏跟页面实际深浅；导航栏图标始终跟应用设置
    // 已应用快照，开洞编辑取消时回退
    var savedWallpaperBitmap by mutableStateOf<ImageBitmap?>(null)
    var savedWallpaperOffset by mutableStateOf(wallpaperOffset)
    var savedWallpaperScale by mutableFloatStateOf(wallpaperScale)
    var savedAppearance by mutableStateOf(AppearanceConfig())

    // 进入搭配页时的原始搭配；滑动切换不更新（退出非应用时还原）
    var originalCombinationIndex by mutableIntStateOf(0)
    var originalWallpaperBitmap by mutableStateOf<ImageBitmap?>(null)
    var originalWallpaperOffset by mutableStateOf(wallpaperOffset)
    var originalWallpaperScale by mutableFloatStateOf(wallpaperScale)
    var originalSnapshot by mutableStateOf<ImageBitmap?>(null)

    // 退出（非应用）时还原主题锁定
    var originalWallpaperIsLight by mutableStateOf<Boolean?>(null)

    // 整体还原，避免编辑回调字段残留
    var originalCombination by mutableStateOf<ScheduleCombination?>(null)
    var isApplyingCustomize by mutableStateOf(false)
    var pendingEnterCutout by mutableStateOf(false)

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
    // 横竖屏（含自由窗口）尺寸变化：把当前显示中的变换从旧屏重映射到新屏
    val cutoutMainScale = Animatable(1f)
    var cutoutCenterYRatio by mutableFloatStateOf(0.5f)

    // 与 CustomizeScheduleScreen 共享实例，直接读 .value 同帧同步
    val sheetOffsetY = Animatable(0f)

    // 用布尔量而不用整屏位图当哨兵，避免白养 ~10MB Bitmap
    var switchOverlayActive by mutableStateOf(false)
    var switchCardSnapshot by mutableStateOf<TransitionLayerSnapshot?>(null)

    // 形变插值起止依据（窗口坐标系）
    var switchCardBounds by mutableStateOf<Rect?>(null)

    // 页面坐标系 → 窗口坐标系的换算偏移
    var switchContentRootX by mutableFloatStateOf(0f)

    // 保留在 Activity 层不是为了防止销毁（详情页期间主内容已不再移出组合），
    // 而是液态玻璃重录要读它们的「滚动中 / 弹窗开合」（liquidGlassMustRecord、liquidGlassRecordKey）
    val scheduleSelectedCourse = mutableStateOf<Course?>(null)
    val scheduleSelectedCourses = mutableStateOf<List<Course>>(emptyList())
    val scheduleShowCourseDetail = mutableStateOf(false)

    // 今日页各日期页独立 LazyListState，这里只收「是否在滚」供液态玻璃重录
    val todayListScrollInProgress = mutableStateOf(false)
    var switchContentRootY by mutableFloatStateOf(0f)
    var switchAnimJob by mutableStateOf<Job?>(null)
    var switchAnimForward by mutableStateOf(false)
    var switchAnimRunning by mutableStateOf(false)

    // Await a schedule reload before recording the return layer.
    var switchReloadJob by mutableStateOf<Job?>(null)
    val switchAnimProgress = Animatable(0f)
    val managePageBlurRadius = Animatable(0f)

    // 仅课程长按菜单模糊；空白格菜单不模糊
    val shortcutMenuBlurRadius = Animatable(0f)
    val switchReturnBgScrim = Animatable(0f)

    // 切换课表：进入方向要复用「上次退出录到的卡片」，但**图层跨不过组合销毁** ——
    // `GraphicsLayer.record{}` 里 `drawLayer(x)` 存的是对 x 的引用，切换页一销毁那层就废。
    // 所以只能在退出那一帧把卡片区域**固化成位图**（位图不依赖任何组合），
    // 下次进入直接画它。进入方向因此零录制。
    var retainedSwitchCardBitmap by mutableStateOf<ImageBitmap?>(null)

    // 转位图用的临时小图层：只承载卡片那一块，避免整屏 toImageBitmap（12MB + 整屏回读）。
    // 本次形变要画的卡片位图（只有进入方向用；退出方向仍用实时卡片图层）。
    var switchCardBitmap by mutableStateOf<ImageBitmap?>(null)

    // 读回上次会话退出时固化的卡片快照：位图活不过进程，只有文件能留到下次启动。
    // 桌面上 Store 未初始化/无实现，返回 null，退化成会话内复用。
    // Retain drawing commands on demand for snapshots and schedule-library morphs.
    class MainSnapshotRequester {
        var lastRecordedToken: Int = 0
    }

    val mainSnapshotRequester = MainSnapshotRequester()
    var mainSnapshotToken by mutableIntStateOf(0)
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
            (if (includeNativeChrome) captureScreenWithNativeChrome() else null)
                ?: screenGraphicsLayer.toImageBitmap()
        } catch (_: OutOfMemoryError) {
            FeatureLog.t("主页", "main_snapshot_oom")
            null
        } catch (_: Exception) {
            null
        }
    }

    var blurSnapshotJob by mutableStateOf<Job?>(null)
    val currentDayOfWeek get() = todayLocalDate().dayOfWeek.isoDayNumber
    val teachingWeekRepository = CourseRepository.getInstance()

    // 节假日/调休保存后需能重算跳周；resume 时刷新版本号
    val basePage get() = (currentWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
    val todayMaxDateOffset get() = 1000

    // 主 tab 平移容器：仅点底栏/侧栏 tab 驱动。手机端只有 3 页，
    // 后两页（课程管理/切换课表）只有平板侧栏才到得了，按 5 页建会让它们常驻组合树白陪跑。
    val mainPagerPageCount get() = if (isShiftMode) 2 else if (navBarStyle == "rail") 5 else 3

    // 程序化切 tab 期间为 true，避免 currentPage 在动画中途把 selectedTab 拉回去
    var mainTabProgrammatic by mutableStateOf(false)

    // 当前可见页背后是不是壁纸，决定高光亮度（见 InteractiveHighlight 的 peak）。
    val pageBehindHasWallpaper
        get() = wallpaperBitmap != null &&
                !isShiftMode &&
                mainPagerState.currentPage in 0..1

    // 二级页侧栏点选主 tab（无转场回来后处理）
    var lastAutoPage by mutableIntStateOf(0)
    var lastAutoWeek by mutableIntStateOf(0)

    // 切 tab 时强制放掉周 pager 上可能残留的 scroll 锁。
    var todaySelectedDayOfWeek by mutableIntStateOf(0)
    var todayIsToday by mutableStateOf(true)
    var scrollToTodayTrigger by mutableIntStateOf(0)
    val currentViewingWeek get() = pagerState.currentPage + 1

    // dataVersion + courses 引用都进 key：调课 size 可能不变，只靠 size 会让智能周末星期行停在旧值
    val viewingIsHoliday get() = viewModel.isWeekHoliday(currentViewingWeek)

    // 设置页的「卡片」圆角 = 屏幕圆角（与 [AppNavHost] 的转场裁切同一把尺）
    val settingsPageIndex get() = if (isShiftMode) 1 else 2

    // 顶栏糊带：只圆**顶部两角**（四角全圆会把 120dp 高的糊带变成药丸）
    // 平板（rail）主界面切页是瞬时的（scrollToPage，无动画），圆角卡片机制不参与。
    val mainTabTransitionAnimated get() = navBarStyle != "rail"
    val settingsPageClipModifier
        get() =
            if (mainTabTransitionAnimated) {
                Modifier
                    .fillMaxSize()
                    .clipScreenCorners(settingsPageClipShape)
            } else {
                Modifier
            }
    val settingsTopBarClipModifier
        get() =
            if (mainTabTransitionAnimated) {
                Modifier.clipDuringTransition(settingsTopBarClipShape, settingsCardOnScreen)
            } else {
                Modifier
            }
    val mainTabAnimation = arrayOfNulls<Job>(1)
    val mainTabGeneration = intArrayOf(0)
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
                    FeatureLog.t(
                        "主页",
                        "tab",
                        "idx=$index shift=$isShiftMode rail=${navBarStyle == "rail"}"
                    )
                } finally {
                    if (mainTabGeneration[0] == generation) mainTabProgrammatic = false
                }
            }
        }
    }

    // 外观页弹窗让位时主内容的纵向位移，改由主内容自己的 graphicsLayer 块直推
    // 引用固定，避免每帧新建 lambda 让 iOS 侧 update() 比对失败。
    // 内容区（切天/周）一有横向手势：立刻取消未完成的主 tab 平移，吸回当前页，避免露出隔壁页
    // 固定实例：lambda 每次重组都新建，不能拿它当 remember key
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
        if (geom.showBreakDividers) with(densityProvider) { 24.dp.toPx() } else 0f

    fun sectionTopPx(geom: ScheduleGridGeometry, section: Int, dividerPx: Float): Float {
        // 优先用网格布局的实际节次顶（含特殊块挤占）；下面的现算公式漏掉特殊块高度，
        // 会让长按浮层与吸附落点整体偏上，偏移量等于该节次上方的特殊块总高。
        geom.sectionTopDp[section]?.let { dp -> return with(densityProvider) { dp.dp.toPx() } }
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
        val bottom =
            sectionTopPx(geom, endSection.coerceAtLeast(startSection), dividerPx) + sectionH
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
                        animateFloatState(
                            floatingOffsetX,
                            targetOffsetX,
                            snapSpec,
                            dragMotion.velocityX
                        )
                    }
                    val jobY = launch {
                        animateFloatState(
                            floatingOffsetY,
                            targetOffsetY,
                            snapSpec,
                            dragMotion.velocityY
                        )
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
                val cardPadPx = with(densityProvider) { 2.dp.toPx() }
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
                    val cardPadPx = with(densityProvider) { 2.dp.toPx() }
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
                val top =
                    geom.sectionTopDp[s]?.let { with(densityProvider) { it.dp.toPx() } } ?: continue
                if (relY < top + sectionH) return day to s
            }
            // 落在最后一节之下：吸到最后一节
            return day to totalSections
        }

        val dividerH = with(densityProvider) { 24.dp.toPx() }
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
            retainedDetailLayer.record(
                densityProvider,
                detailLayerLayoutDirection,
                source.layer.size
            ) {
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
            delay(CUSTOMIZE_COVER_HOLD_MILLIS.toLong().milliseconds)
            launch {
                customizeCoverScale.animateTo(
                    0.75f,
                    tween(CUSTOMIZE_COVER_SCALE_MILLIS, easing = CustomizePageEasing)
                )
            }
            launch {
                delay(CUSTOMIZE_COVER_SCALE_MILLIS.toLong().milliseconds)
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
            val autoScale =
                WallpaperTransform.minScale(picked.width, picked.height, screenWPx, screenHPx)
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
    var showSwitchSchedule by mutableStateOf(false)
    var switchPendingReverse by mutableStateOf(false)
    var switchExiting by mutableStateOf(false)
    var switchMainLayerJob by mutableStateOf<Job?>(null)

    // Keep the destination hidden until its anchor and the source layer are ready.
    var switchPreparingLayer by mutableStateOf(false)

    // Re-record the source layer before returning to a different schedule.
    var scheduleChanged by mutableStateOf(false)
    var showMorePopup by mutableStateOf(false)
    var showTodayMorePopup by mutableStateOf(false)

    // 按钮原地随菜单展开淡出，形成"按钮连贯变为菜单"的单一动画
    val morePopupFraction = Animatable(0f)
    val todayMorePopupFraction = Animatable(0f)

    // 「更多」占位槽实测顶部位置（px，root 坐标）：布局期上报，菜单据此定位，避免首帧 inset 未到而偏位
    val moreSlotTopPx = mutableFloatStateOf(0f)

    // 占位槽在开洞/切页期间仍在顶层顶栏里，照常采样：关掉采样会让 moreSlotTopPx
    // 冻结在进页前旧高度，菜单错位且仍可点。
    val canSampleMoreSlot get() = !isWindowCutoutActive

    // 顶栏滚动材质透明度；两个顶栏各自一份，避免切页时互相覆盖。
    val scheduleMaterialAlpha = mutableStateOf(0f)
    val todayMaterialAlpha = mutableFloatStateOf(0f)
    var todayJumpToDateTrigger by mutableIntStateOf(0)
    val isViewingCurrentWeek get() = currentViewingWeek == currentWeek
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
}
