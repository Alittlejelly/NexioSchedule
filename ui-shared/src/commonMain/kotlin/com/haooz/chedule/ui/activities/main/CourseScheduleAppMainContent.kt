package com.haooz.chedule.ui.activities.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.AppearanceConfig
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.NoopUpdateController
import com.haooz.chedule.data.Notice
import com.haooz.chedule.data.NoticeFetcher
import com.haooz.chedule.data.randomUuidV4
import com.haooz.chedule.data.showToast
import com.haooz.chedule.ui.activities.today.TodayScreen
import com.haooz.chedule.ui.components.BackToNowFloatingButton
import com.haooz.chedule.ui.activities.classschedule.LandRippleSpec
import com.haooz.chedule.ui.activities.classschedule.LocalLandRipple
import com.haooz.chedule.ui.components.ScheduleBottomBar
import com.haooz.chedule.ui.activities.classschedule.ScheduleTopBar
import com.haooz.chedule.ui.components.ShareImportDialog
import com.haooz.chedule.ui.components.TabletNavSideState
import com.haooz.chedule.ui.components.UpdateDialog
import com.haooz.chedule.ui.components.tabletNavExpandSampleTrack
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.screens.AddCourseDialog
import com.haooz.chedule.ui.activities.classschedule.MainScheduleScreen
import com.haooz.chedule.ui.components.LiquidNavigationRail
import com.haooz.chedule.ui.components.tabletNavRailStartPadding
import com.haooz.chedule.ui.screens.ShiftScheduleScreen
import com.haooz.chedule.ui.screens.TabletCourseManagePane
import com.haooz.chedule.ui.screens.TabletSettingsChromeOverlay
import com.haooz.chedule.ui.screens.TabletSwitchSchedulePane
import com.haooz.chedule.ui.theme.rememberAppThemeController
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.LocalForcedDarkTheme
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.addScreenCornerRect
import com.haooz.chedule.ui.utils.bottomFloatingPadding
import com.haooz.chedule.ui.utils.buildShareScheduleMap
import com.haooz.chedule.ui.utils.rememberAppSettingDark
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.isRenderEffectSupported
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

/**
 * 课表主界面「主内容层」：整屏 Box + graphicsLayer 模糊/裁切 + Scaffold 内容。
 *
 * 从 `CourseScheduleApp.kt` 拆出。参数只放外壳算好的局部值，
 * 其余状态一律走 [CourseScheduleAppState]（函数体内 `with(state)`）。
 */
@Composable
internal fun CourseScheduleAppMainContent(
    state: CourseScheduleAppState,
    displayAppearance: AppearanceConfig,
    chromeBackdrop: Backdrop,
    liquidGlassRecordKey: List<Any?>,
    liquidGlassMustRecord: () -> Boolean,
    mainContentAlpha: Float,
    wallpaperImage: ImageBitmap?,
    originalWallpaperImage: ImageBitmap?,
) {
    with(state) {
        val density = densityProvider

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
                            val sheetScaleProg =
                                ((sheetScale - 0.65f) / (1f - 0.65f)).coerceIn(0f, 1f)
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
            // 右侧避让量：右下角竖排的原生 tab bar 占的空间（折叠屏 iPhone 折叠态）。
            val titleEndPadding = (hostNav?.rightSafeAreaInset ?: 0.0).toFloat().dp
            val scaffoldContent = @Composable {
                Scaffold(
                    modifier = Modifier
                        // ⚠ 只取 Left、不取 Horizontal：`safeDrawing` 的 right 
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Left).union(
                                WindowInsets(
                                    left = (hostNav?.leftSafeAreaInset ?: 0.0).toFloat().dp,
                                    right = 0.dp,
                                )
                            )
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
                                                    val off =
                                                        mainPagerState.currentPageOffsetFraction
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
                                            blurSampleTrack = tabletNavExpandSampleTrack,
                                            titleEndPadding = titleEndPadding,
                                            onMoreSlotTop = {
                                                if (canSampleMoreSlot) moreSlotTopPx.value = it
                                            },
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
                                                    val off =
                                                        mainPagerState.currentPageOffsetFraction
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
                                        blurSampleTrack = tabletNavExpandSampleTrack,
                                        titleEndPadding = titleEndPadding,
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
                                        blurSampleTrack = tabletNavExpandSampleTrack,
                                        titleEndPadding = titleEndPadding,
                                        onMoreSlotTop = {
                                            if (canSampleMoreSlot) moreSlotTopPx.value = it
                                        },
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
                                val sharedMinScale =
                                    remember(sharedWallpaperBitmap, screenWPx, screenHPx) {
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
                                        ColorFilter.colorMatrix(
                                            ColorMatrix(
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
                                        tabletNavRailStartPadding()
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            // 主 tab 仅由底栏/侧栏点击切换；关闭用户手势翻页。
                            // Follow the navigation axis reported by the native host or tablet rail.
                            val mainPagerModifier = Modifier
                                .fillMaxSize()
                                .padding(end = (hostNav?.rightSafeAreaInset ?: 0.0).toFloat().dp)
                                .nestedScroll(mainContentNestedScroll)
                            val mainPagerPageKey: (Int) -> String = { page ->
                                if (isShiftMode) "shift-$page" else "main-$page"
                            }
                            val mainPagerPageContent: @Composable (Int) -> Unit = { page ->
                                if (!isShiftMode) {
                                    when (page) {
                                        0 -> {
                                            // 今日页主题预先固定，切 tab 不跟着 chrome 闪一帧
                                            val todayForced =
                                                if (captureThemeActive) captureThemeIsDark else todayPageForcedDark
                                            val todayDark = todayForced ?: appSettingDark
                                            val todayThemeController =
                                                rememberAppThemeController(isDark = todayDark)
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
                                                        onSelectedDayChanged = {
                                                            todaySelectedDayOfWeek = it
                                                        },
                                                        onSelectedDateChanged = {
                                                            todayIsToday = it
                                                        },
                                                        scrollToTodayTrigger = scrollToTodayTrigger,
                                                        jumpToDateTrigger = todayJumpToDateTrigger,
                                                        onJumpToDateProcessed = {
                                                            todayJumpToDateTrigger = 0
                                                        },
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
                                                        onListScrollInProgress = {
                                                            todayListScrollInProgress.value = it
                                                        },
                                                        // 今日助手卡留在 :androidApp（天气/定位/Geocoder），通过槽位注入
                                                        todayAssistantCard = todayAssistantCard,
                                                    )
                                                }
                                            }
                                        }

                                        1 -> {
                                            val scheduleForced =
                                                if (captureThemeActive) captureThemeIsDark else wallpaperForcedDark
                                            val scheduleDark = scheduleForced ?: appSettingDark
                                            val scheduleThemeController =
                                                rememberAppThemeController(isDark = scheduleDark)
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
                                                            onPopupStateChange = {
                                                                showCourseDetailPopup = it
                                                            },
                                                            onEmptyLongPress = { day, section, centerX, cellTopY, width, _, addDay, addWeek ->
                                                                shortcutMenuCourse = null
                                                                emptyCellMenuTarget =
                                                                    EmptyCellMenuTarget(
                                                                        columnDay = day,
                                                                        section = section,
                                                                        addDay = addDay,
                                                                        addWeek = addWeek,
                                                                    )
                                                                shortcutMenuVisible = true
                                                                shortcutMenuPosition = Offset(
                                                                    centerX - width / 2f,
                                                                    cellTopY
                                                                )
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
                                                                    val fullH =
                                                                        courseVisualHeightPx(
                                                                            geom,
                                                                            course.startSection,
                                                                            course.endSection,
                                                                            div
                                                                        )
                                                                    val bounds =
                                                                        geom.dayBounds[course.dayOfWeek]
                                                                            ?: geom.dayBounds.values.firstOrNull()
                                                                    // 宽度沿用卡片回调（含 Day 列内 2dp padding 后的实际宽），不要用整列 dayBounds
                                                                    val topRel = sectionTopPx(
                                                                        geom,
                                                                        course.startSection,
                                                                        div
                                                                    )
                                                                    // X 必须用卡片实测中心 left：调课日列数据星期 ≠ 列星期，
                                                                    // dayBounds[course.dayOfWeek] 会取到隔壁列；自定义时间课按时间轴定位。
                                                                    anchorCenter = when {
                                                                        course.hasValidCustomTime() ->
                                                                            Offset(left, top)

                                                                        bounds != null && bounds.size >= 3 ->
                                                                            Offset(
                                                                                left,
                                                                                bounds[2] + topRel + fullH / 2f
                                                                            )

                                                                        else ->
                                                                            Offset(left, top)
                                                                    }
                                                                    anchorSize =
                                                                        if (course.hasValidCustomTime()) {
                                                                            Offset(width, height)
                                                                        } else {
                                                                            Offset(width, fullH)
                                                                        }
                                                                } else {
                                                                    anchorCenter = Offset(left, top)
                                                                    anchorSize =
                                                                        Offset(width, height)
                                                                }
                                                                draggedCardPosition = anchorCenter
                                                                // 由卡片实测 X 反推所在列：数据星期在调课日列对不上列
                                                                draggedColumnDay =
                                                                    geom?.dayBounds?.entries
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
                                                                shortcutMenuAnchorWidth =
                                                                    anchorSize.x
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
                                                                    val dt =
                                                                        (now - dragMotion.lastTimeMs).coerceAtLeast(
                                                                            1L
                                                                        )
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
                                                                    val sectionH =
                                                                        geomNow?.sectionHeightPx
                                                                            ?: 0f
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
                                                                    val centerX =
                                                                        draggedCardPosition.x + offsetX
                                                                    val cardTopY =
                                                                        draggedCardPosition.y + offsetY - cardHeightPx / 2f
                                                                    val firstSectionCenterY =
                                                                        cardTopY + sectionH / 2f
                                                                    val rawTarget =
                                                                        computeDropTarget(
                                                                            centerX,
                                                                            firstSectionCenterY
                                                                        )
                                                                    // 放不下的格子不给落点（与粘贴路径同款校验）：起始节次+跨度
                                                                    // 超过总节次时松手会写出 endSection > totalSections 的课，渲染溢出/错位。
                                                                    val newTarget =
                                                                        rawTarget?.takeIf {
                                                                            it.second + sectionCount - 1 <= totalSections
                                                                        }
                                                                    if (newTarget != pendingDropTarget) {
                                                                        pendingDropTarget =
                                                                            newTarget
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
                                                                    val targetEnd =
                                                                        targetStart + sectionSpan
                                                                    // 同格按数据坐标判：调课日列里原地拖动不再被误判成跨天
                                                                    val sameSlot =
                                                                        source.dayOfWeek == targetDay &&
                                                                                week == targetWeek &&
                                                                                source.startSection == targetStart &&
                                                                                source.endSection == targetEnd
                                                                    if (!sameSlot) {
                                                                        val conflicts =
                                                                            viewModel.getCoursesAtSlot(
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
                                                                                    .filter {
                                                                                        it.isActiveInWeek(
                                                                                            targetWeek
                                                                                        )
                                                                                    }
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
                                                                                    animateInCourseIds =
                                                                                        emptySet()
                                                                                }
                                                                            }
                                                                        } else {
                                                                            // 浮层连贯移到目标卡上方悬停等用户选择
                                                                            pendingConflictCourse =
                                                                                conflicts.first()
                                                                            pendingDropTarget =
                                                                                target.first to targetStart
                                                                            showRescheduleConflictDialog =
                                                                                true
                                                                            val hoverTargetCenter =
                                                                                computeTargetCenter(
                                                                                    target.first,
                                                                                    targetStart,
                                                                                    sectionSpan
                                                                                )
                                                                            if (hoverTargetCenter != null) {
                                                                                coroutineScope.launch {
                                                                                    stopConflictHover()
                                                                                    isConflictHover =
                                                                                        true
                                                                                    isSnapping =
                                                                                        true
                                                                                    // 拖拽位移已在 floatingOffset*
                                                                                    val sectionHPx =
                                                                                        gridGeometry?.sectionHeightPx
                                                                                            ?: 0f
                                                                                    val floatH =
                                                                                        (sectionSpan + 1) * sectionHPx
                                                                                    val gap =
                                                                                        with(density) {
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
                                                                                            0.32f,
                                                                                            0.72f,
                                                                                            0.28f,
                                                                                            1f
                                                                                        )
                                                                                    val jobX =
                                                                                        launch {
                                                                                            animateFloatState(
                                                                                                floatingOffsetX,
                                                                                                hoverOffsetX,
                                                                                                tween(
                                                                                                    durationMillis = 420,
                                                                                                    easing = settleEase
                                                                                                )
                                                                                            )
                                                                                        }
                                                                                    val jobY =
                                                                                        launch {
                                                                                            animateFloatState(
                                                                                                floatingOffsetY,
                                                                                                hoverOffsetY,
                                                                                                tween(
                                                                                                    durationMillis = 420,
                                                                                                    easing = settleEase
                                                                                                )
                                                                                            )
                                                                                        }
                                                                                    val jobScale =
                                                                                        launch {
                                                                                            floatingScale.animateTo(
                                                                                                1.08f,
                                                                                                tween(
                                                                                                    durationMillis = 420
                                                                                                )
                                                                                            )
                                                                                        }
                                                                                    jobX.join(); jobY.join(); jobScale.join()
                                                                                    val bobAmp =
                                                                                        with(density) {
                                                                                            4.dp.toPx()
                                                                                        }
                                                                                    conflictHoverBobJob =
                                                                                        launch {
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
                                                                                        tween(
                                                                                            durationMillis = 180
                                                                                        )
                                                                                    )
                                                                                    delay(180.milliseconds)
                                                                                    isDraggingCard =
                                                                                        false
                                                                                    floatingCardVisible =
                                                                                        false
                                                                                    draggingCourseIds =
                                                                                        emptySet()
                                                                                    floatingOffsetX.floatValue =
                                                                                        0f
                                                                                    floatingOffsetY.floatValue =
                                                                                        0f
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
                                                            onWallpaperOffsetChange = {
                                                                wallpaperOffset = it
                                                            },
                                                            onWallpaperScaleChange = {
                                                                wallpaperScale = it
                                                            },
                                                            appearance = displayAppearance,
                                                            liquidGlassBackdrop = liquidGlassBackdrop,
                                                            onGridGeometryChange = { geom ->
                                                                gridGeometry = geom
                                                            },
                                                            dropHighlight = run {
                                                                val emptyTarget =
                                                                    emptyCellMenuTarget
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
                                                                    val emptyTarget =
                                                                        emptyCellMenuTarget
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
                                            val settingsThemeController =
                                                rememberAppThemeController(isDark = settingsDark)
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
                                                val shareRepository =
                                                    remember { CourseRepository() }
                                                TabletSwitchSchedulePane(
                                                    viewModel = viewModel,
                                                    scheduleViewModel = scheduleViewModel,
                                                    settingsViewModel = settingsViewModel,
                                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                                    onShareSchedule = { name, changed ->
                                                        onShareSchedule?.invoke(name, changed)
                                                    },
                                                    isScheduleShareable = { name ->
                                                        buildShareScheduleMap(
                                                            shareRepository,
                                                            name
                                                        ) != null
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
                        hostNavigation!!.bottomBarInset.toFloat().dp + 8.dp
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
                                    val conflicts = viewModel.getCoursesAtSlot(
                                        pasteWeek,
                                        day,
                                        section,
                                        endSection
                                    )
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
                                                lastModified = Clock.System.now()
                                                    .toEpochMilliseconds()
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
                                                lastModified = Clock.System.now()
                                                    .toEpochMilliseconds()
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
                        LiquidNavigationRail(
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
                        val settingsChromeController =
                            rememberAppThemeController(isDark = appSettingDark)
                        MiuixTheme(controller = settingsChromeController) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                TabletSettingsChromeOverlay()
                            }
                        }
                    }
                }
            }

        }
    }
}
