package com.haooz.chedule.ui.activities.classschedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.chedule.data.formatMonthDay
import com.haooz.chedule.data.todayLocalDate
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.components.tabletNavRailStartPadding
import com.haooz.chedule.ui.utils.hostStatusBarHeight
import com.haooz.chedule.ui.utils.LocalHostNavigation
import kotlinx.datetime.LocalDate
import com.haooz.chedule.ui.theme.scheduleAccentColor
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.FastForward
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults.CollapsedHeight
import top.yukonga.miuix.kmp.layout.NoStatusBarBlurReduction
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

// 不用 Scaffold paddingValues：随当前 tab 顶栏高度变化，切页时内容位移，
// 叠加 SharedBlur 层 draw 阶段写入滞后一帧，表现为顶部慢一帧就位
// （原为 internal；MainScheduleScreen 在 :androidApp 引用，跨模块必须 public）
fun scheduleContentTopPadding(statusBarHeight: Dp, rawStatusBarTop: Dp): Dp {
    val appBar = CollapsedHeight
    // 与 ScheduleTopBar 的 topBarHeight 同一套判定：见那里关于
    // 「statusBarHeight == 0 有两种含义」的说明 —— 折叠屏iPhone顶部没有状态栏
    val noTopStatusBar = rawStatusBarTop <= 0.dp
    val blurHeight = when {
        noTopStatusBar -> 120.dp - NoStatusBarBlurReduction
        statusBarHeight > 0.dp -> 120.dp + statusBarHeight
        else -> 160.dp
    }
    val weekRowBottom = statusBarHeight + appBar +
            (if (noTopStatusBar || statusBarHeight > 0.dp) 0.dp else 40.dp) + 40.dp
    val topBar = maxOf(blurHeight, appBar + statusBarHeight, weekRowBottom)
    // 内容顶恒为「星期行底 + 2dp」：糊带只是装饰，折叠屏上收窄 8dp 不该把内容一起拽上去
    return maxOf(weekRowBottom + 2.dp, appBar + topBar - 78.dp).coerceAtLeast(0.dp)
}

@Composable
fun ScheduleTopBar(
    visible: Boolean,
    pagerCurrentPage: Int,
    currentWeek: Int,
    isHoliday: Boolean,
    dayRange: List<Int>,
    currentDayOfWeek: Int,
    isCurrentWeek: Boolean,
    weekDates: List<LocalDate>,
    isReorganized: Boolean,
    onOpenSwitchSchedule: () -> Unit,
    onJumpWeek: () -> Unit = {},
    onEnterCustomize: () -> Unit = {},
    isTablet: Boolean = false,
    isShiftMode: Boolean = false,
    liquidGlassBackdrop: Backdrop?,
    scrollBehavior: SharedScrollBehavior? = null,
    blurResampleKey: Int = 0,
    blurSampleTrack: () -> Float = { 0f },
    // 标题/星期行的右侧额外内边距：避开右下角竖排的原生 tab bar（折叠屏 iPhone 折叠态）
    titleEndPadding: Dp = 0.dp,
    // 布局期上报「更多」占位槽的真实位置，供下拉菜单按实际位置定位（避免首帧 inset 未到导致偏位）
    onMoreSlotTop: (Float) -> Unit = {},
    // 上报顶栏滚动材质透明度，供「更多」控件收起态同步渐显渐隐
    onMoreMaterial: (Float) -> Unit = {},
    onMoreExpand: () -> Unit = {},
) {
    if (!visible || liquidGlassBackdrop == null) return
    // iOS 主界面右上角那一排（「更多」UIMenu + 课表切换）已由原生 chrome 接管
    // （见 iosApp/ContentView 的 mainMoreItem / mainSwitchItem）：有宿主导航即 iOS，
    // Compose 这一侧整排都不渲染。Android / desktop 没有宿主导航，照常显示。
    val hostOwnsTopRight = LocalHostNavigation.current != null
    val nativeBarInset = (LocalHostNavigation.current?.topBarInset ?: 0.0).dp

    val titleText = when {
        isHoliday -> "放假中"
        currentWeek < 1 -> "学期未开始"
        else -> "第${pagerCurrentPage + 1}周"
    }

    // 宿主上报的真实状态栏高度优先：iOS 折叠屏折叠态状态栏在右侧、顶部为 0，
    // 而 WindowInsets.statusBars 在 iOS 读到的是 view.safeAreaInsets.top（含导航栏），判不出来
    val rawStatusBarTop = hostStatusBarHeight().let { override ->
        if (override != Dp.Unspecified) override
        else WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    }
    val statusBarHeight = (rawStatusBarTop - nativeBarInset).coerceAtLeast(0.dp)
    // statusBarHeight == 0 有两种完全不同的含义，必须靠 rawStatusBarTop 区分：
    val topBarHeight = when {
        rawStatusBarTop <= 0.dp -> 120.dp - NoStatusBarBlurReduction
        statusBarHeight > 0.dp -> 120.dp + statusBarHeight
        else -> 160.dp
    }
    // 组合期读 currentHeightPx 会拿到未测量的值，星期行会慢一帧就位；改布局期读
    ProgressiveBlurTopBar(
        backdrop = liquidGlassBackdrop,
        height = topBarHeight,
        topInsetAdjustment = nativeBarInset,
        resampleKey = blurResampleKey,
        sampleTrack = blurSampleTrack,
    ) {
        Box {
            // 平板：标题避让左侧侧栏后左对齐（手机仍居中）
            val titleRailPadding =
                if (isTablet) tabletNavRailStartPadding().padding(start = 12.dp) else Modifier
            val titleEndModifier =
                if (titleEndPadding > 0.dp) Modifier.padding(end = titleEndPadding) else Modifier
            // zIndex 必须挂在这层 Box 上，不能直接传给 CollapsibleTopAppBar：
            // 它的 modifier 参数只应用在内部那层 Layout 上（见 CollapsibleTopAppBar 末尾），
            // 挂到孙节点对它与 DayOfWeekRow 的排序无效 —— zIndex 只在同一父节点的兄弟间比较。
            Box(modifier = Modifier.zIndex(1f)) {
                CollapsibleTopAppBar(
                    title = titleText,
                    showLargeTitle = false,
                    showGradientOverlay = false,
                    titleStartAligned = isTablet,
                    titleModifier = titleRailPadding.then(titleEndModifier),
                    scrollBehavior = scrollBehavior,
                    // 平板左上角不放返回按钮
                    startAction = null,
                    onAlphaChanged = { backdrop, _ -> onMoreMaterial(backdrop) },
                    endAction = { backdropAlpha, shadowAlpha ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isTablet) {
                                // pad：切换课表/课程管理已在侧栏，右上角直接放跳转周数 + 课表外观
                                LiquidTopBarButton(
                                    onClick = onJumpWeek,
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.Basic.FastForward,
                                    contentDescription = "跳转周数",
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
                            } else {
                                if (!isShiftMode && !hostOwnsTopRight) {
                                    LiquidTopBarButton(
                                        onClick = {
                                            onOpenSwitchSchedule()
                                        },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Normal.ConvertFile,
                                        contentDescription = "课表切换",
                                        iconSize = 27.dp,
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha
                                    )
                                }
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
                    }
                )
            }
            DayOfWeekRow(
                dayRange = dayRange,
                currentDayOfWeek = currentDayOfWeek,
                isCurrentWeek = isCurrentWeek,
                weekDates = weekDates,
                isReorganized = isReorganized,
                isTablet = isTablet,
                contentEndPadding = titleEndPadding,
                modifier = Modifier.dayOfWeekTopPadding(statusBarHeight, rawStatusBarTop, scrollBehavior)
            )
        }
    }
}

@Composable
private fun DayOfWeekRow(
    dayRange: List<Int>,
    currentDayOfWeek: Int,
    isCurrentWeek: Boolean,
    weekDates: List<LocalDate>,
    isReorganized: Boolean,
    isTablet: Boolean,
    /** 星期行右侧额外内边距：避开右下角竖排的原生 tab bar（折叠屏 iPhone 折叠态）。 */
    contentEndPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(
                if (isTablet) {
                    // 侧栏避让放在原 padding(start=) 位置，避免撑高 dayOfWeekTopPadding
                    Modifier
                        .then(tabletNavRailStartPadding())
                        .padding(horizontal = 24.dp)
                } else {
                    Modifier.padding(end = 2.dp + contentEndPadding)
                }
            )
    ) {
        Spacer(modifier = Modifier.width(if (isTablet) 56.dp else 36.dp))
        dayRange.forEach { dayOfWeek ->
            val index = dayOfWeek - 1
            val name = DAY_NAMES[index]
            val isToday = dayOfWeek == currentDayOfWeek && isCurrentWeek &&
                (!isReorganized || weekDates.getOrNull(index) == todayLocalDate())

            val todayHighlightColor = scheduleAccentColor()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = name,
                        style = MiuixTheme.textStyles.footnote1.copy(
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isToday) todayHighlightColor
                        else MiuixTheme.colorScheme.onSurface
                    )
                    if (weekDates.isNotEmpty() && index < weekDates.size) {
                        val dateText = remember(weekDates[index]) {
                            weekDates[index].formatMonthDay()
                        }
                        Text(
                            text = dateText,
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (isToday) todayHighlightColor
                            else MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }
        }
    }
}

// 顶栏高度是测量后才写入的状态，组合期读会得 0；布局期读与测量同帧对齐。
// 未测量时用 CollapsedHeight+状态栏兜底（本顶栏 showLargeTitle=false，实测恒为该值）
private fun Modifier.dayOfWeekTopPadding(
    statusBarHeight: Dp,
    rawStatusBarTop: Dp,
    scrollBehavior: SharedScrollBehavior?,
): Modifier = layout { measurable, constraints ->
    val statusBarPx = statusBarHeight.roundToPx()
    val barHeightPx = scrollBehavior?.currentHeightPx ?: 0f
    val measuredBarPx = if (barHeightPx > 0f) {
        barHeightPx.roundToInt()
    } else {
        CollapsedHeight.roundToPx() + statusBarPx
    }
    // 与 scheduleContentTopPadding 同一套判定（内容顶 = 星期行底 + 2dp，两边必须同源）：
    // statusBarHeight == 0 时只有「状态栏被原生导航栏吃完」才补那 40dp；
    // 折叠屏 iPhone 折叠态顶部压根没有状态栏，再补就把星期行整体压低 40dp。
    val noTopStatusBar = rawStatusBarTop <= 0.dp
    val gapPx = if (noTopStatusBar || statusBarPx > 0) 0 else 40.dp.roundToPx()
    val top = statusBarPx + measuredBarPx + gapPx
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height + top) {
        placeable.place(0, top)
    }
}
