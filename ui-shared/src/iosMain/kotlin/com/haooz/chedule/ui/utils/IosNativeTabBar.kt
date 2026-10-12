@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * iOS 原生 tab bar（`UITabBarController`）与 Compose 主界面的桥。
 *
 * ## 为什么要这个
 *
 * Liquid Glass 的 tab bar 只有 UIKit 原生那条路能拿到（`UIGlassEffect`，iOS 26+；
 * Kotlin/Native 绑定里连 `UIView.effect` 都没有，详见 `ScreenCornerRadius.ios.kt` 的同类记录）。
 * 所以 Swift 侧起 `UITabBarController`，三个 tab 各自托管一个 Compose 根。
 *
 * ## 现状：视觉验证阶段
 *
 * 三个 tab 的 content **暂时都用同一个 Compose 根**（`IosAppEntry.MainViewController`），
 * 即点原生 tab bar 切的是 VC，Compose 那边暂时还是自己画底栏、自己切页。
 * 目的是**先确认 Liquid Glass tab bar 的视觉与交互手感**，再决定要不要为它付出
 * 「拆 Compose 横滑分页 + 状态跨 VC 共享」的架构代价。
 *
 * ## 选中态同步
 *
 * - [selectedTab]：Compose 侧自己切 tab（点底栏、横滑、侧栏跳转）时写回，
 *   Swift 侧监听它同步 `UITabBarController.selectedIndex`。
 * - [onNativeTabSelected]：Swift 侧点原生 tab bar 时调用，把 index 给 Compose。
 *
 * ⚠ **双向回灌会成环**：Compose 写 [selectedTab] → Swift 同步 selectedIndex →
 * `UITabBarControllerDelegate` 又回调 [onNativeTabSelected] → Compose 再写……
 * Swift 侧靠「当前 selectedIndex 与回调 index 相同则忽略」断开，见 `ContentView.swift`。
 */
object IosNativeTabBar {

    /** Compose 侧当前选中的主 tab（0 今日 / 1 课程表 / 2 我的；排班模式 0 排班 / 1 设置）。 */
    var selectedTab: Int by mutableIntStateOf(0)

    /** Swift 侧点原生 tab bar 时的回调（由 Swift 在 setup 时注入）。 */
    var onNativeTabSelected: ((Int) -> Unit)? = null

    /**
     * 宿主要求 Compose 切到第 N 页（点原生 tab bar 时由 Swift 写入）。
     *
     * ⚠ 为什么必须是 Compose [androidx.compose.runtime.mutableStateOf] 而不是普通
     * 字段：写普通字段不会触发重组，Compose 那边就停在旧页 —— 表现为
     * 「底栏高亮变了、内容还是上一页」。
     *
     * ⚠ 用 `null` 表示「没有待处理的请求」，不用 -1：tab 索引本就可能为 0。
     */
    var pendingHostTabRequest: Int? by androidx.compose.runtime.mutableStateOf(null)
        private set

    /** Compose 是否用系统原生 tab bar（false = Compose 自己画底栏，调试回落用）。 */
    var useNativeTabBar: Boolean = true

    fun select(index: Int) {
        selectedTab = index
        pendingHostTabRequest = index
    }

    fun consumePendingHostRequest(): Int? {
        val v = pendingHostTabRequest
        pendingHostTabRequest = null
        return v
    }

    /**
     * Compose 侧自己切 tab 时调用（由 commonMain 的 `notifyHostTabSelected` 转发）。
     *
     * 只改 [selectedTab] 不够 —— Swift 监听的是状态，但那边是**单向**的；
     * 要让系统 tab bar 高亮跟着动，得显式回调一次。
     */
    fun notifyFromCompose(index: Int) {
        if (selectedTab == index) return
        selectedTab = index
        onNativeTabSelected?.invoke(index)
    }
}
