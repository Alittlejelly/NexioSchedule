/** 小组件使用引导页面 - Screen */
package com.haooz.chedule.ui.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import com.haooz.chedule.ui.components.text.material.Text as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toIntSize
import com.haooz.chedule.data.CourseRepository
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.isIosPlatform
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.Color as ComposeColor
import top.yukonga.miuix.kmp.layout.isTabletWidth
import org.jetbrains.compose.resources.painterResource
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.ic_widget_clock
import com.haooz.chedule.uishared.resources.ic_widget_location
import kotlin.math.abs
import kotlin.math.min

/** 4×2 组件预览统一宽高比，锁定后不随设备屏宽变化 */
private const val WidgetPreviewRatio4x2 = 2.15f

/** 预览卡上下的内边距（dp） */
private val WidgetPreviewVerticalPadding = 12.dp

/** 翻转卡最大模糊半径（dp），翻转进度越过起始阈值后 0 → 此值线性渐入 */
private val WidgetPreviewFlipBlurRadius = 8.dp

/** 模糊起始阈值：翻转进度（0–1）小于此值完全清晰，越过后才开始加模糊 */
private const val WidgetPreviewBlurStartProgress = 0.12f

/** 模糊出血（dp）：RenderEffect 会把图层强制裁到自身边界 */
private val WidgetPreviewBlurBleed = 8.dp

/**
 * @param onRequestWidgetRefresh 小组件边距改完后通知宿主刷新已添加的小组件。
 *   Android uses AppWidgetManager; iOS uses a shared WidgetKit snapshot.
 *   Platform-specific refresh behavior is supplied by the host.
 *   与 `SchoolSelectionScreen(onAdapterSelected)` / `CourseTimeSettingsScreen(onEditRoutine)`
 *   同一套路：**平台专有能力留在宿主，页面本体保持平台中立**。
 */
@Composable
fun WidgetIntroScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    onRequestWidgetRefresh: () -> Unit = {},
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .collapsibleTopInset(scrollBehavior)
                    .then(
                        scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                    )
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight + (if (isTablet) 12.dp else 0.dp)))

                // 今明课程是 Android 桌面专属组件，iOS 端不展示该预览页
                val pageCount = if (isIosPlatform) 2 else 3
                val pagerState = rememberPagerState(pageCount = { pageCount })

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    beyondViewportPageCount = 1
                ) { page ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = tabletHorizontalPadding - WidgetPreviewBlurBleed,
                                vertical = WidgetPreviewVerticalPadding - WidgetPreviewBlurBleed
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    val off = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                    val absOff = min(abs(off), 1f)
                                    cameraDistance = 48f
                                    rotationY = -off * 56f
                                    val scale = 1f - 0.2f * absOff
                                    scaleX = scale
                                    scaleY = scale
                                    val blurProgress = ((absOff - WidgetPreviewBlurStartProgress) /
                                        (1f - WidgetPreviewBlurStartProgress)).coerceIn(0f, 1f)
                                    renderEffect = if (blurProgress > 0.01f) {
                                        val blur = blurProgress * WidgetPreviewFlipBlurRadius.toPx()
                                        BlurEffect(blur, blur)
                                    } else null
                                }
                                // 出血必须在 graphicsLayer 内侧：扩大离屏缓冲的采样范围，
                                // 外层槽内边距已等量减回，静止布局不变
                                .padding(WidgetPreviewBlurBleed)
                        ) {
                            when (page) {
                                0 -> WidgetPreviewToday()
                                1 -> if (isIosPlatform) WidgetPreviewSmall2x2() else WidgetPreviewTodayTomorrow()
                                2 -> WidgetPreviewSmall2x2()
                            }
                        }
                    }
                }

                // 页面指示器
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pageCount) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected)
                                        MiuixTheme.colorScheme.primary
                                    else
                                        MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.12f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = tabletHorizontalPadding + 18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    FeatureItem("今日课程一目了然", "桌面显示当天课程、时间、地点和教师信息")
                    FeatureItem("明日课程提前预览", "开启次日课程提醒后，智能显示明日课程")
                    FeatureItem("进行中课程高亮", if (isIosPlatform) "突出显示正在上课的课程和结束时间" else "正在上课的课程高亮显示剩余时间")
                    FeatureItem("智能显示状态", "智能提醒今日课程当前状态")
                    if (isIosPlatform) {
                        FeatureItem("跟随桌面外观", "支持浅色、深色与系统着色外观")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!isIosPlatform) {
                    val repository = remember { CourseRepository() }
                    var paddingMode by remember { mutableIntStateOf(repository.getWidgetPaddingMode()) }
                    Box(modifier = Modifier.padding(horizontal = tabletHorizontalPadding)) {
                        WidgetPaddingSelector(
                            selectedMode = paddingMode,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            onSelect = { mode ->
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                paddingMode = mode
                                repository.setWidgetPaddingMode(mode)
                                onRequestWidgetRefresh()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(120.dp))
            }

            // 底部「添加到桌面」已上提到 WidgetIntroActivity
        }
    }
}

@Composable
private fun WidgetPreviewToday() {
    val isDark = isAppDarkTheme()
    val bgColor = if (isDark) ComposeColor(0xFF1A1A1A) else ComposeColor(0xFFF7F7F7)
    val cardBg = if (isDark) ComposeColor(0xFF262626) else ComposeColor.White
    val borderColor = if (isDark) ComposeColor(0xFF2A2A2A) else ComposeColor(0x0D000000)
    val titleColor = if (isDark) ComposeColor(0x77FFFFFF) else ComposeColor(0x77000000)
    val nameColor = if (isDark) ComposeColor(0xFFEEEEEE) else ComposeColor(0xFF1A1A1A)
    val infoColor = if (isDark) ComposeColor(0x55FFFFFF) else ComposeColor(0x55000000)
    val timeStartColor = if (isDark) ComposeColor(0x88FFFFFF) else ComposeColor(0x88000000)
    val timeEndColor = if (isDark) ComposeColor(0x55FFFFFF) else ComposeColor(0x55000000)
    val nowColor = if (isDark) ComposeColor(0xFF42A5F5) else ComposeColor(0xFF2196F3)

    Card(
        cornerRadius = 22.dp,
        modifier = Modifier.fillMaxWidth().aspectRatio(WidgetPreviewRatio4x2).border(1.dp, borderColor, com.kyant.capsule.ContinuousRoundedRectangle(22.dp)),
        insideMargin = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("今天 / 周五", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, fontFamily = FontFamily.SansSerif, modifier = Modifier.weight(1f))
                Text("第10周", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, fontFamily = FontFamily.SansSerif)
            }

            CourseCardPreview("08:00", "09:35", "高等数学", "第1-2节｜A301｜张老师", ComposeColor(0xFF4CAF50), null, cardBg, nameColor, infoColor, timeStartColor, timeEndColor, nowColor, Modifier.fillMaxWidth().weight(1f))
            Spacer(modifier = Modifier.height(8.dp))
            CourseCardPreview("14:00", "15:35", "大学英语", "第5-6节｜B205｜李老师", ComposeColor(0xFF2196F3), "25分钟结束", ComposeColor(0x1A2196F3), nameColor, infoColor, timeStartColor, timeEndColor, nowColor, Modifier.fillMaxWidth().weight(1f))
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun WidgetPreviewSmall2x2() {
    val isDark = isAppDarkTheme()
    val bgColor = if (isDark) ComposeColor(0xFF1A1A1A) else ComposeColor.White
    val borderColor = if (isDark) ComposeColor(0xFF2A2A2A) else ComposeColor(0x0D000000)
    val titleColor = if (isDark) ComposeColor(0x99FFFFFF) else ComposeColor(0x99000000)
    val nameColor = if (isDark) ComposeColor(0xFFEEEEEE) else ComposeColor(0xFF1A1A1A)
    val infoColor = if (isDark) ComposeColor(0x88FFFFFF) else ComposeColor(0x88000000)
    val remainingColor = if (isDark) ComposeColor(0x55FFFFFF) else ComposeColor(0x55000000)

    // 外层与 4×2 预览同高，2×2 正方形 fillMaxHeight 居中，保证三页 pager 高度一致
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(WidgetPreviewRatio4x2),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .border(1.dp, borderColor, com.kyant.capsule.ContinuousRoundedRectangle(22.dp))
                .background(bgColor, com.kyant.capsule.ContinuousRoundedRectangle(22.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, bottom = 4.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title: 14sp bold
                    Text("周五", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, fontFamily = FontFamily.SansSerif)
                    Spacer(modifier = Modifier.weight(1f))
                    // Week: 14sp bold
                    Text("第10周", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, fontFamily = FontFamily.SansSerif)
                }

                // Course content: gravity=bottom, weight=1
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Course name: 17sp, paddingStart=4dp, paddingTop=8dp, paddingBottom=4dp, marginBottom=4dp
                    Text(
                        "高等数学",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = nameColor,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                    )

                    // Time row: marginStart=4dp, marginBottom=2dp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon: 12dp, marginEnd=6dp
                        androidx.compose.foundation.Image(
                            painter = painterResource(Res.drawable.ic_widget_clock),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            alpha = 0.5f
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Text: 14sp
                        Text("08:00 - 09:35", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = infoColor, fontFamily = FontFamily.SansSerif)
                    }

                    // Location row: marginStart=4dp, marginBottom=16dp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon: 12dp, marginEnd=6dp
                        androidx.compose.foundation.Image(
                            painter = painterResource(Res.drawable.ic_widget_location),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            alpha = 0.5f
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Text: 14sp
                        Text("A301", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = infoColor, fontFamily = FontFamily.SansSerif, maxLines = 1)
                    }
                }

                // Remaining info: marginStart=4dp, text 12sp
                Row(
                    modifier = Modifier.padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "还剩 2 节课",
                        fontSize = 12.sp,
                        color = remainingColor,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(ComposeColor(0xFF4CAF50), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(ComposeColor(0xFF2196F3), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewTodayTomorrow() {
    val isDark = isAppDarkTheme()
    val bgColor = if (isDark) ComposeColor(0xFF1A1A1A) else ComposeColor(0xFFF7F7F7)
    val cardBg = if (isDark) ComposeColor(0xFF262626) else ComposeColor.White
    val borderColor = if (isDark) ComposeColor(0xFF2A2A2A) else ComposeColor(0x0D000000)
    val titleColor = if (isDark) ComposeColor(0x77FFFFFF) else ComposeColor(0x77000000)
    val nameColor = if (isDark) ComposeColor(0xFFEEEEEE) else ComposeColor(0xFF1A1A1A)
    val timeColor = if (isDark) ComposeColor(0x88FFFFFF) else ComposeColor(0x88000000)
    val infoColor = if (isDark) ComposeColor(0x55FFFFFF) else ComposeColor(0x55000000)

    Card(
        cornerRadius = 22.dp,
        modifier = Modifier.fillMaxWidth().aspectRatio(WidgetPreviewRatio4x2).border(1.dp, borderColor, com.kyant.capsule.ContinuousRoundedRectangle(22.dp)),
        insideMargin = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(start = 9.dp, end = 9.dp, top = 9.dp, bottom = 9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 与真实组件一致：两个各占一半的表头槽位，明天槽位左移 4dp 起点
                Text("今天 / 周五", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, fontFamily = FontFamily.SansSerif, maxLines = 1, modifier = Modifier.weight(1f))
                Text("明天 / 周六", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = titleColor, fontFamily = FontFamily.SansSerif, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 4.dp))
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    TodayTomorrowCardPreview("高等数学", "08:00-09:35", "A301｜张老师", ComposeColor(0xFF4CAF50), cardBg, nameColor, timeColor, infoColor, Modifier.fillMaxWidth().weight(1f))
                    TodayTomorrowCardPreview("大学英语", "14:00-15:35", "B205｜李老师", ComposeColor(0xFF2196F3), ComposeColor(0x1A2196F3), nameColor, timeColor, infoColor, Modifier.fillMaxWidth().weight(1f))
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    TodayTomorrowCardPreview("线性代数", "08:00-09:35", "C102｜王老师", ComposeColor(0xFFFF9800), cardBg, nameColor, timeColor, infoColor, Modifier.fillMaxWidth().weight(1f))
                    TodayTomorrowCardPreview("大学物理", "10:00-11:35", "D203｜赵老师", ComposeColor(0xFF9C27B0), cardBg, nameColor, timeColor, infoColor, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TodayTomorrowCardPreview(
    name: String, time: String, info: String,
    barColor: ComposeColor, cardBg: ComposeColor,
    nameColor: ComposeColor, timeColor: ComposeColor, infoColor: ComposeColor,
    modifier: Modifier = Modifier
) {
    Card(
        cornerRadius = 14.dp,
        modifier = modifier,
        insideMargin = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBg)
                .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 6.dp)
                    .width(4.dp)
                    .background(barColor, com.kyant.capsule.ContinuousRoundedRectangle(2.dp))
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = nameColor, maxLines = 1)
                Text(time, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = timeColor, fontFamily = FontFamily.SansSerif, maxLines = 1)
                Text(info, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = infoColor, fontFamily = FontFamily.SansSerif, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CourseCardPreview(
    startTime: String, endTime: String, name: String, info: String,
    barColor: ComposeColor, remaining: String?,
    cardBg: ComposeColor, nameColor: ComposeColor, infoColor: ComposeColor,
    timeStartColor: ComposeColor, timeEndColor: ComposeColor, nowColor: ComposeColor,
    modifier: Modifier = Modifier
) {
    Card(
        cornerRadius = 12.dp,
        modifier = modifier,
        insideMargin = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBg)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(35.dp), horizontalAlignment = Alignment.Start) {
                Text(startTime, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = timeStartColor, fontFamily = FontFamily.SansSerif)
                Text(endTime, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = timeEndColor, fontFamily = FontFamily.SansSerif)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.width(4.dp).height(32.dp).background(barColor, com.kyant.capsule.ContinuousRoundedRectangle(2.dp)))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = nameColor, maxLines = 1)
                Text(info, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = infoColor, fontFamily = FontFamily.SansSerif)
            }
            if (remaining != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(remaining, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = nowColor, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun FeatureItem(title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(8.dp).padding(top = 7.dp).background(MiuixTheme.colorScheme.primary, com.kyant.capsule.ContinuousRoundedRectangle(4.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions)
        }
    }
}

@Composable
private fun WidgetPaddingSelector(
    selectedMode: Int,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    onSelect: (Int) -> Unit
) {
    val entry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = "标准",
                selected = selectedMode == 0,
                onClick = { onSelect(0) }
            ),
            DropdownItem(
                text = "4×6",
                selected = selectedMode == 1,
                onClick = { onSelect(1) }
            ),
            DropdownItem(
                text = "4×7",
                selected = selectedMode == 2,
                onClick = { onSelect(2) }
            ),
            DropdownItem(
                text = "4×6 无字",
                selected = selectedMode == 3,
                onClick = { onSelect(3) }
            ),
            DropdownItem(
                text = "4×7 无字",
                selected = selectedMode == 4,
                onClick = { onSelect(4) }
            ),
        )
    )
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = ComposeColor.Transparent,
        selectedContainerColor = ComposeColor.Transparent,
    )
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        insideMargin = PaddingValues(0.dp)
    ) {
        OverlayDropdownMenu(
            title = "小组件适应布局",
            entry = entry,
            collapseOnSelection = true,
            liquidGlassBackdrop = liquidGlassBackdrop,
            dropdownColors = liquidGlassDropdownColors,
        )
    }
}
