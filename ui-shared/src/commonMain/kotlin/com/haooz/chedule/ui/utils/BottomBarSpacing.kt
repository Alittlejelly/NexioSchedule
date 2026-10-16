package com.haooz.chedule.ui.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 底部悬浮元素距屏幕底部的统一间距。
 *
 * 有系统安全区（edge-to-edge 下的导航栏 / home indicator）= inset + 2dp：贴着导航栏
 * 并留一点呼吸；无安全区（桌面 / 平板全屏等）= 22dp。
 *
 * ⚠ 以下位置必须同源，各写各的常量必然互相错位：
 *   - 首页底部导航（[com.haooz.chedule.ui.components.ScheduleBottomBar]）
 *   - 切换课表底部编辑栏（SwitchScheduleScreen 的 bottomBar）
 *   - 教务导入页底部胶囊（WebViewScreen 的 web dock）
 *   - 首页「回到底」浮动按钮（CourseScheduleApp，在底栏胶囊上方 64dp）
 */
@Composable
internal fun bottomFloatingPadding(): Dp {
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return if (navBarBottomInset > 0.dp) {
        // 贴安全区再 +2dp：inset 本身就是留白，+2 只是给胶囊一点呼吸，不会悬空
        navBarBottomInset + 2.dp
    } else {
        22.dp
    }
}
