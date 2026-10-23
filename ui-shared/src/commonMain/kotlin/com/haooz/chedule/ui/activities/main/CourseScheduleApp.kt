package com.haooz.chedule.ui.activities.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.AppearanceConfig
import com.haooz.chedule.data.AppearanceStore
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.data.SwitchCardSnapshotStore
import com.haooz.chedule.data.SyncManager
import com.haooz.chedule.data.TeachingWeekReorganization
import com.haooz.chedule.data.UpdateController
import com.haooz.chedule.data.WallpaperStore
import com.haooz.chedule.data.WebDavManager
import com.haooz.chedule.data.ioDispatcher
import com.haooz.chedule.ui.activities.today.TodayAssistantCardData
import com.haooz.chedule.ui.components.SharedFileIntent
import com.haooz.chedule.ui.components.TabletNavSideState
import com.haooz.chedule.ui.effects.motion.CUSTOMIZE_COVER_FADE_MILLIS
import com.haooz.chedule.ui.effects.motion.CUSTOMIZE_COVER_SCALE_MILLIS
import com.haooz.chedule.ui.effects.motion.CustomizePageEasing
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.PageBackdropSnapshot
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberAppSettingDark
import com.haooz.chedule.ui.utils.rememberScheduleThemeMode
import com.haooz.chedule.ui.utils.rememberScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.screenHeightDp
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.ui.utils.toShape
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import com.haooz.chedule.viewmodel.shiftViewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRenderEffectSupported
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import top.yukonga.miuix.kmp.layout.isTabletWidth
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * 真课表主界面。平台能力全部走注入参数，缺能力的入口不渲染；
 * 位图统一 [ImageBitmap]，快照裁剪走 [TransitionLayerSnapshot]（GraphicsLayer + clipRect，零位图分配），
 * 壁纸存储走 `WallpaperStore`。
 */

/** 宿主注入的位图回收实现（Android = `asAndroidBitmap().recycle()`；iOS/桌面 = null，交给 GC）。 */
var bitmapRecycler: ((ImageBitmap) -> Unit)? = null

/**
 * 点「课表外观」到开始截屏的等待，所有入口共用。
 */
internal const val CUSTOMIZE_ENTRY_DELAY_MS = 320L

// 课表外观页「封面快照」的时序常量（CUSTOMIZE_COVER_*）在
// ui/effects/motion/OobeMotion.kt —— 外观页也要按同一个窗口对齐自己的入场时间轴。

@Suppress("RememberReturnType")
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

    // 状态与交互逻辑全在 CourseScheduleAppState；这里只负责注入上下文、跑组合副作用、画 UI。
    // `with(state)` 让下面几千行 UI 代码的变量名一个都不用改（成员解析到 state 上）。
    val state = remember {
        CourseScheduleAppState().apply {
            this.viewModel = viewModel
            this.scheduleViewModel = scheduleViewModel
            this.settingsViewModel = settingsViewModel
            this.shiftViewModel = shiftViewModel
            // 首屏默认 tab：与原来的 remember { mutableIntStateOf(...) } 初值一致
            selectedTab = if (defaultHomepage == "今日") 0 else 1
        }
    }
    // 宿主参数每次重组同步（值变化极罕见，但保持与原实现一致）
    state.privacyConsented = privacyConsented
    state.resumeCount = resumeCount
    state.isInFreeformWindow = isInFreeformWindow
    state.onSystemBarsChanged = onSystemBarsChanged
    state.onHolidayDataChanged = onHolidayDataChanged
    state.onPickWallpaperImage = onPickWallpaperImage
    state.onShareSchedule = onShareSchedule
    state.onReadSharedFileText = onReadSharedFileText
    state.sharedFileIntent = sharedFileIntent
    state.onShareIntentConsumed = onShareIntentConsumed
    state.settingsPane = settingsPane
    state.todayAssistantCard = todayAssistantCard
    state.updateController = updateController
    state.showUpdateSettings = showUpdateSettings

    with(state) {
        // ── 组合期上下文：每次重组把「当前值」写进 state，等价于原来的局部 val ──
        router = LocalAppRouter.current
        hostNavigation = LocalHostNavigation.current
        settingsScrollBehavior = rememberSharedScrollBehavior()
        todayScrollBehavior = rememberSharedScrollBehavior()
        scheduleScrollBehavior = rememberSharedScrollBehavior()

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

        isDark = isAppDarkTheme()
        // ⚠ 主内容采样层**不能**降采样录制（recordScale）：它 contentOnlyCapture=true，
        // 录完 blit 上屏，用户直接看的就是这层；且改成双遍历（上屏走 drawContent）
        // 后实测切 tab 更差（20 帧 / 50% janky / 90th 97ms，基线 192 帧 9.38% 24ms）——
        // 每帧多遍历整棵三页树的代价超过采样 blit 面积降 4 倍的收益。
        // recordScale 基础设施（默认 1f）保留给**本来就是双遍历**的纯采样层
        // （contentOnlyCapture=false 的页级 backdrop，降采样不增加遍历成本）。
        liquidGlassBackdrop = rememberLayerBackdrop()
        // 顶栏/底栏各自再录一层：两者都在内容层之上，只有把它们也录进来，
        // 「比底栏更高一层」的组合采样层才成立
        topBarSampleBackdrop = rememberLayerBackdrop()
        bottomBarSampleBackdrop = rememberLayerBackdrop()
        overlayGlassBackdrop = rememberCombinedBackdrop(
            liquidGlassBackdrop, topBarSampleBackdrop, bottomBarSampleBackdrop
        )
        // 供今日/课程表卡片玻璃采样：录的是共享壁纸层，不是页内透明占位
        sharedWallpaperBackdrop = rememberLayerBackdrop()

        totalWeeks = viewModel.totalWeeks.collectAsState().value
        currentWeek = viewModel.currentWeek.collectAsState().value
        classStartTime = viewModel.classStartTime.collectAsState().value
        morningSections = settingsViewModel.morningSections.collectAsState().value
        afternoonSections = settingsViewModel.afternoonSections.collectAsState().value
        eveningSections = settingsViewModel.eveningSections.collectAsState().value
        configuredSectionTimes = settingsViewModel.sectionTimes.collectAsState().value
        holidayDataRevision = HolidayManager.dataRevision.collectAsState().value
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
        LaunchedEffect(resumeCount, holidayDataRevision) {
            val holidayV = HolidayManager.getVersion()
            if (holidayV != seenHolidayVersion) {
                seenHolidayVersion = holidayV
                viewModel.bumpDataVersion()
            }
        }
        windowInfo = LocalWindowInfo.current
        densityProvider = LocalDensity.current
        // 局部别名：UI 里大量 `density.xxx` 保持原写法（也避免被 graphicsLayer 的 density 遮蔽）
        val density = densityProvider
        detailLayerLayoutDirection = LocalLayoutDirection.current
        // 这三个原来是 Composable 局部 val（依赖 LocalConfiguration / density），每次重组重算
        isTablet = isTabletWidth()
        screenWPx = with(density) { screenWidthDp().dp.toPx() }
        screenHPx = with(density) { screenHeightDp().dp.toPx() }
        warmupBlurPx = with(density) { 24.dp.toPx() }
        latestScreenWPx = screenWPx
        latestScreenHPx = screenHPx

        // 首帧后预热 RenderEffect，避免首次开 BlurBottomSheet 掉帧
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
                                shape = { RoundedCornerShape(36.dp) },
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
        LaunchedEffect(TabletNavSideState.expanded, navBarStyle) {
            if (navBarStyle != "rail") return@LaunchedEffect
            railForceRecordFrames[0] = 12
            delay(360.milliseconds)
            railBlurResampleEpoch.intValue++
        }
        isShiftMode = shiftViewModel.isShiftMode.collectAsState().value

        retainedDetailLayer = rememberGraphicsLayer()

        // 高光开关 = 正在拖 && 浮层还活着：声明在 Scaffold 之前，Scaffold 内容里的提饱和节点要用它。
        // 一松手 onCourseDragEnd 立刻置 false 开始淡出，不等 360ms 退场动画。
        glowAlpha = animateFloatAsState(
            targetValue = if (glowShowing) 1f else 0f,
            animationSpec = tween(150),
            label = "dragCardGlowAlpha",
        ).value

        // 独立偏好 key，与全局主题开关隔离
        persistedScheduleThemeMode = rememberScheduleThemeMode()
        // 状态栏跟页面实际深浅；导航栏图标始终跟应用设置
        appSettingDark = rememberAppSettingDark()
        LaunchedEffect(effectiveIsDark, appSettingDark) {
            onSystemBarsChanged(effectiveIsDark, appSettingDark)
        }

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
                    remapWallpaperForScreen(
                        curr.offset,
                        curr.scale,
                        curr.bitmap,
                        refW,
                        refH,
                        curW,
                        curH
                    )
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
                // 开洞过渡：主内容（含顶/底栏按钮）与封面快照必须**同帧**启动
                launch {
                    cutoutMainScale.animateTo(
                        0.75f,
                        tween(400, easing = CustomizePageEasing)
                    )
                }
                if (customizeSnapshot != null) {
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
                }
            } else {
                cutoutMainScale.animateTo(
                    1f,
                    tween(400, easing = CustomizePageEasing)
                )
            }
        }

        // 保留在 Activity 层不是为了防止销毁（详情页期间主内容已不再移出组合），
        // 而是液态玻璃重录要读它们的「滚动中 / 弹窗开合」（liquidGlassMustRecord、liquidGlassRecordKey）
        scheduleScrollState = rememberScrollState()
        todayListState = rememberLazyListState()
        // 仅课程长按菜单模糊；空白格菜单不模糊
        LaunchedEffect(shortcutMenuVisible, shortcutMenuCourse) {
            if (shortcutMenuVisible && shortcutMenuCourse != null) {
                launch { shortcutMenuBlurRadius.animateTo(16f, tween(280)) }
            } else {
                launch { shortcutMenuBlurRadius.animateTo(0f, tween(250)) }
            }
        }
        screenGraphicsLayer = rememberGraphicsLayer()
        // 转位图用的临时小图层：只承载卡片那一块，避免整屏 toImageBitmap（12MB + 整屏回读）。
        retainedSwitchCardScratchLayer = rememberGraphicsLayer()
        switchLayerLayoutDirection = LocalLayoutDirection.current
        // 读回上次会话退出时固化的卡片快照：位图活不过进程，只有文件能留到下次启动。
        // 桌面上 Store 未初始化/无实现，返回 null，退化成会话内复用。
        LaunchedEffect(Unit) {
            if (retainedSwitchCardBitmap == null) {
                retainedSwitchCardBitmap = SwitchCardSnapshotStore.load()
            }
        }

        hapticFeedback = LocalHapticFeedback.current

        currentDayOfWeek.let { } // 保持与原声明顺序一致（getter，无副作用）
        smartWeekend = settingsViewModel.smartWeekend.collectAsState().value
        dataVersion = viewModel.dataVersion.collectAsState().value
        teachingWeekRules = remember(teachingWeekRepository, dataVersion, classStartTime) {
            teachingWeekRepository.getTeachingWeekReorganizations()
        }
        // 节假日/调休保存后需能重算跳周；resume 时刷新版本号
        holidayVersion = remember(resumeCount, holidayDataRevision) {
            HolidayManager.getVersion()
        }

        autoAdvancePage = remember(
            currentWeek,
            totalWeeks,
            currentDayOfWeek,
            smartWeekend,
            holidayVersion,
            dataVersion,
            teachingWeekRules
        ) {
            if (settingsViewModel.shouldAdvanceToNextWeek(currentDayOfWeek, currentWeek)) {
                (basePage + 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
            } else basePage
        }

        pagerState = rememberPagerState(
            initialPage = autoAdvancePage,
            pageCount = { totalWeeks }
        )

        todayPagerState = rememberPagerState(
            initialPage = todayMaxDateOffset,
            pageCount = { todayMaxDateOffset * 2 }
        )

        // 主 tab 平移容器：仅点底栏/侧栏 tab 驱动。手机端只有 3 页，
        // 后两页（课程管理/切换课表）只有平板侧栏才到得了，按 5 页建会让它们常驻组合树白陪跑。
        mainPagerState = rememberPagerState(
            initialPage = selectedTab.coerceIn(0, mainPagerPageCount - 1),
            pageCount = { mainPagerPageCount }
        )

        // 当前可见页背后是不是壁纸，决定高光亮度（见 InteractiveHighlight 的 peak）。
        // 必须在组合期算再交给 SideEffect：只在 SideEffect 里读 currentPage 不会订阅，切页不更新。
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

        // 基线初值：等价于原来的 remember { mutableIntStateOf(currentWeek / autoAdvancePage) }
        remember { lastAutoWeek = currentWeek; lastAutoPage = autoAdvancePage }
        LaunchedEffect(currentWeek, totalWeeks, autoAdvancePage) {
            // A rule edit must recalculate the default, but must not pull a manually browsed page back.
            if (currentWeek != lastAutoWeek || pagerState.currentPage == lastAutoPage) {
                if (pagerState.currentPage != autoAdvancePage) pagerState.scrollToPage(
                    autoAdvancePage
                )
            }
            lastAutoWeek = currentWeek
            lastAutoPage = autoAdvancePage
        }

        // 切 tab 时强制放掉 pager 上可能残留的 scroll 锁。
        // 周 pager 与今日页 pager 走的是同一套 pagerAxisTakeoverGesture，锁泄漏家族相同：
        // 今日页锁一旦泄漏，切回今日后左右滑就彻底失效（只能重进应用）。
        LaunchedEffect(mainPagerState.currentPage) {
            runCatching { pagerState.scroll(MutatePriority.PreventUserInput) { } }
            runCatching {
                // 抢占会把未完成的 settle 动画一起掐掉，落点可能停在两页之间，补一次吸附
                todayPagerState.scroll(MutatePriority.PreventUserInput) { }
                val visual =
                    todayPagerState.currentPage + todayPagerState.currentPageOffsetFraction
                val target = visual.roundToInt()
                if (abs(visual - target) > 0.001f) todayPagerState.scrollToPage(target)
            }
        }

        // 今日页默认选中今天：等价于原来的 remember { mutableIntStateOf(currentDayOfWeek) }
        remember { if (todaySelectedDayOfWeek == 0) todaySelectedDayOfWeek = currentDayOfWeek }

        courses = viewModel.courses.collectAsState().value
        // dataVersion + courses 引用都进 key：调课 size 可能不变，只靠 size 会让智能周末星期行停在旧值
        dayRange = remember(
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
        weekDates = remember(currentViewingWeek, classStartTime, teachingWeekRules) {
            try {
                val startDate = LocalDate.parse(classStartTime.replace("/", "-"))
                TeachingWeekReorganization.datesForTeachingWeek(
                    startDate, currentViewingWeek, teachingWeekRules,
                )
            } catch (_: Exception) {
                emptyList()
            }
        }

        screenCornerRadius = rememberScreenCornerRadiiPx()

        // 设置页的「卡片」圆角 = 屏幕圆角（与 [AppNavHost] 的转场裁切同一把尺）
        settingsPageClipShape = remember(screenCornerRadius, density) {
            if (screenCornerRadius.maxRadius > 0f) screenCornerRadius.toShape(density) else null
        }
        // 顶栏糊带：只圆**顶部两角**（四角全圆会把 120dp 高的糊带变成药丸）
        settingsTopBarClipShape = remember(screenCornerRadius, density) {
            if (screenCornerRadius.maxRadius > 0f) {
                screenCornerRadius.copy(bottomRight = 0f, bottomLeft = 0f).toShape(density)
            } else {
                null
            }
        }
        settingsCardOnScreen = remember(mainPagerState, isShiftMode) {
            {
                val scrollPos = mainPagerState.currentPage +
                        mainPagerState.currentPageOffsetFraction
                scrollPos > settingsPageIndex - 1f
            }
        }

        coroutineScope = rememberCoroutineScope()
        latestMainTabSelected = onMainTabSelected
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
                host.cutoutCenterXRatio = cutoutCenterXRatio
            }
        }
        // 外观页弹窗让位时主内容的纵向位移，改由主内容自己的 graphicsLayer 块直推
        // 引用固定，避免每帧新建 lambda 让 iOS 侧 update() 比对失败。
        pushCutoutChromeOffset = remember(hostNavigation) {
            { offsetY: Double -> hostNavigation?.onCutoutChromeOffsetY?.invoke(offsetY) }
        }
        // 内容区（切天/周）一有横向手势：立刻取消未完成的主 tab 平移，吸回当前页，避免露出隔壁页
        cancelUnfinishedMainTabAnim = {
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
        mainContentNestedScroll = remember(mainPagerState) {
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
                host.scheduleLibraryActive =
                    nativeBottomBar && !isTablet && showSwitchSchedule && !switchExiting
                host.scheduleLibraryTransitionActive =
                    switchPreparingLayer || switchAnimRunning || switchPendingReverse ||
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
    }

    // UI 仍在本文件，后续按需再拆；这里统一 `with(state)` 让旧代码零改动。
    with(state) {
        // 局部别名（同初始化区）：让 `density.xxx` 不被 graphicsLayer 作用域的 density 遮蔽
        val density = densityProvider

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
            val chromeBackdrop: Backdrop = liquidGlassBackdrop
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
                mainPagerState.currentPage,
                isShiftMode,
                showDetail,
                showCustomizePage,
                showSwitchSchedule,
                isWindowCutoutActive,
                shortcutMenuVisible,
                isDraggingCard,
                floatingCardVisible,
                dataVersion,
                currentWeek,
                totalWeeks,
                currentCombinationIndex,
                effectiveIsDark,
                wallpaperBitmap,
                TabletNavSideState.expanded,
                scheduleShowCourseDetail.value,
                pagerState.currentPage,
                themeBackdropKey
            ) {
                listOf(
                    mainPagerState.currentPage,
                    isShiftMode,
                    showDetail,
                    showCustomizePage,
                    showSwitchSchedule,
                    isWindowCutoutActive,
                    shortcutMenuVisible,
                    isDraggingCard,
                    floatingCardVisible,
                    dataVersion,
                    currentWeek,
                    totalWeeks,
                    currentCombinationIndex,
                    effectiveIsDark,
                    wallpaperBitmap,
                    TabletNavSideState.expanded,
                    scheduleShowCourseDetail.value,
                    pagerState.currentPage,
                    themeBackdropKey
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
            val liquidGlassMustRecord = remember(
                scheduleScrollState,
                todayListScrollInProgress,
                pagerState,
                todayPagerState,
                mainPagerState
            ) {
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
            // 主内容（带模糊/裁切）与浮层组各拆一个文件，外壳只做局部值派生
            CourseScheduleAppMainContent(
                state = state,
                displayAppearance = displayAppearance,
                chromeBackdrop = chromeBackdrop,
                liquidGlassRecordKey = liquidGlassRecordKey,
                liquidGlassMustRecord = liquidGlassMustRecord,
                mainContentAlpha = mainContentAlpha,
                wallpaperImage = wallpaperImage,
                originalWallpaperImage = originalWallpaperImage,
            )
            CourseScheduleAppOverlayLayers(
                state = state,
                displayAppearance = displayAppearance,
                isEntryAnimating = isEntryAnimating,
            )
        }
    }
}

