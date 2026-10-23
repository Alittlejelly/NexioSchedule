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
 * 有系统安全区（edge-to-edge 下的导航栏 / home indicator）= inset + 2dp，贴着导航栏留一点呼吸；无安全区（桌面 / 平板全屏等）= 22dp。
 *
 * ⚠ 以下位置必须同源，各写各的常量必然互相错位：首页底部导航、切换课表底部编辑栏、教务导入页底部胶囊、首页「回到底」浮动按钮。
 */
@Composable
internal fun bottomFloatingPadding(): Dp {
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 硬下限 22dp：无论有无系统导航条都不得低于此值 —— 部分 ROM 隐藏手势条后 inset
    // 只剩几 dp，inset + 2 会几乎贴底
    return maxOf(navBarBottomInset + 2.dp, 22.dp)
}
