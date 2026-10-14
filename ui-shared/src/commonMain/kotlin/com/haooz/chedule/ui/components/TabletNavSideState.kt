package com.haooz.chedule.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 平板左侧导航全局状态。
 * 只保留侧边态：true = 展开（图标+文字），false = 折叠（仅图标）。
 *
 * 2026-10-10 从 TabletNavSideBar.kt 抽出迁入 :ui-shared ——
 * BackToNowFloatingButton（已迁）要读 [expandProgress] 做自适应内边距，
 * 状态对象必须和它同模块。同包名，调用方 import 一行不用改。
 */
object TabletNavSideState {
    /** 展开=完整侧栏；折叠=仅图标轨 */
    var expanded by mutableStateOf(true)

    /**
     * 展开进度 0=折叠图标轨，1=完整侧栏。
     * 只在 layout/draw/graphicsLayer 读，禁止在组合期读 ——
     * 否则 CourseScheduleApp 整树会随动画每帧重组，平板展开/缩回直接掉帧。
     */
    val expandProgress = mutableFloatStateOf(1f)

    /** 二级页点选主 tab 后宿主待处理下标；-1 = 无 */
    var pendingMainTab by mutableStateOf(-1)
}
