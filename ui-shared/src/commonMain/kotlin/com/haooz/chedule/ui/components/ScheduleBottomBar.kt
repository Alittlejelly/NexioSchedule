package com.haooz.chedule.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.haooz.chedule.ui.components.text.material.Text as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.utils.bottomFloatingPadding
import com.haooz.chedule.i18n.ChromeStr
import com.haooz.chedule.i18n.CommonStr
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 手机端底部导航。平板导航由 MainActivity 叠层 [LiquidNavigationRail] 绘制，此处直接 return，
 * 避免 Scaffold bottomBar 的 fillMaxSize 挡住内容滚动。
 *
 * 结构：左侧胶囊（今日 / 课表，排班模式只有排班课表）+ 右侧一颗**独立的圆形设置按钮**，
 * 两者按「胶囊 + 间距 + 圆钮」整组水平居中 —— 设置不再是 tab，是压栈的二级页入口。
 */
@Composable
fun ScheduleBottomBar(
    navBarStyle: String,
    isShiftMode: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onOpenSettings: () -> Unit = {},
    liquidGlassBackdrop: Backdrop? = null,
) {
    if (navBarStyle == "rail" || liquidGlassBackdrop == null) return

    val onSelect: (Int) -> Unit = { idx -> onTabSelected(idx) }
    val iconTint = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.8f)
    // 与胶囊同色：圆钮是同一排的另一块玻璃，色差一眼就看出来
    val surfaceColor = bottomBarSurfaceColor()
    // 底部间距统一走共享算法（见 bottomFloatingPadding）：切换课表底栏、教务导入底部胶囊、
    // 首页「回到底」浮动按钮都用它，四处互相有对位关系，不能各写各的常量
    val bottomPadding = bottomFloatingPadding()

    // 每格占屏比：沿用改造前「3 格占 0.63」的密度，一格 0.21
    val tabCount = if (isShiftMode) 1 else 2
    val tabsWidthFraction = 0.21f * tabCount

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        // 胶囊 + 圆钮整组居中（不是各自居中）
        contentAlignment = Alignment.Center
    ) {
        val tabsWidth = maxWidth * tabsWidthFraction
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidBottomTabs(
                selectedTabIndex = { selectedTab },
                onTabSelected = { onSelect(it) },
                backdrop = liquidGlassBackdrop,
                tabsCount = tabCount,
                modifier = Modifier
                    .width(tabsWidth)
                    .height(56.dp)
            ) {
                if (isShiftMode) {
                    LiquidBottomTab(index = 0, onClick = { onSelect(0) }) {
                        Image(
                            modifier = Modifier.size(24.dp),
                            imageVector = MiuixIcons.Months,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(iconTint)
                        )
                        Text(ChromeStr.shiftSchedule(), fontSize = 11.sp, color = iconTint)
                    }
                } else {
                    LiquidBottomTab(index = 0, onClick = { onSelect(0) }) {
                        Image(
                            modifier = Modifier.size(24.dp),
                            imageVector = MiuixIcons.Album,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(iconTint)
                        )
                        Text(CommonStr.today(), fontSize = 11.sp, color = iconTint)
                    }
                    LiquidBottomTab(index = 1, onClick = { onSelect(1) }) {
                        Image(
                            modifier = Modifier.size(24.dp),
                            imageVector = MiuixIcons.Months,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(iconTint)
                        )
                        Text(ChromeStr.schedule(), fontSize = 11.sp, color = iconTint)
                    }
                }
            }
            // 设置：独立圆形玻璃按钮。高度与胶囊一致（56dp），图标不跟顶栏那颗 42dp 混
            LiquidTopBarButton(
                onClick = onOpenSettings,
                backdrop = liquidGlassBackdrop,
                icon = MiuixIcons.Settings,
                contentDescription = CommonStr.settings(),
                iconSize = 28.dp,
                buttonHeight = 56.dp,
                containerColor = surfaceColor,
            )
        }
    }
}
