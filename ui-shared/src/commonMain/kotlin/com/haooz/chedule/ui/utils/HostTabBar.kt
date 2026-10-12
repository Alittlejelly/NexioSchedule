package com.haooz.chedule.ui.utils

/**
 * 把主界面的选中 tab 写回宿主（iOS 的 `UITabBarController`）。
 *
 * ## 为什么需要
 *
 * iOS 的底栏是**宿主画的**（`UITabBarController` + Liquid Glass，见
 * `iosApp/iosApp/ContentView.swift`），Compose 侧不渲染底栏。但 Compose 内部
 * 仍持有 `selectedTab`（横滑切页、侧栏跳转、排班模式切换都会改它），
 * 必须让宿主知道当前选中哪个，才能把系统 tab bar 的高亮对齐。
 *
 * ## 平台实现
 *
 * - Android / 桌面：**空实现** —— 底栏是 Compose 自己画的（`ScheduleBottomBar`），
 *   没有宿主需要知道。
 * - iOS：写进 [IosNativeTabBar]，Swift 侧监听后同步 `selectedIndex`。
 *
 * ⚠ 双向回灌要断开：Compose 写回 → Swift 同步 selectedIndex →
 * `didSelect` 又回调 Kotlin → 成环。断点见 `ContentView.swift` 的 `lastSyncedIndex`。
 */
expect fun notifyHostTabSelected(index: Int)

/**
 * 读取并消费「宿主要求切到第 N 页」的请求。
 *
 * 宿主（iOS 的 `UITabBarController`）点原生 tab bar 时会写入请求；Compose 侧读到后
 * 驱动内部 pager，再消费掉（返回 null）。
 *
 * 返回 `null` = 当前没有待处理请求（Android / 桌面恒为 null，宿主 tab bar 不存在）。
 */
expect fun consumeHostTabChangeRequest(): Int?
