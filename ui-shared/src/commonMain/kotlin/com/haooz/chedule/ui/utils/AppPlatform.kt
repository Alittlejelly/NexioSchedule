package com.haooz.chedule.ui.utils

/**
 * 当前运行平台。
 *
 * 项目里「判断是不是 iOS」过去有两种写法，容易混淆：
 * - 宿主穿参：`isIos = true`（iOS 侧传，其它端吃默认 false）；
 * - 读 `hostNavigation != null`。
 *
 * 二者语义不同：前者是**平台本身**，后者是「有没有原生宿主接管 chrome」。混用会误判
 * （比如将来 macOS 也挂宿主，`hostNavigation != null` 就不再等于 iOS）。
 *
 * 规则：
 * - 要按平台分支 → 一律用这里的 [appPlatform] / [isIosPlatform]；
 * - 要判「原生宿主是否接管」→ 继续看 `hostNavigation`，别换成平台判断。
 */
enum class AppPlatform { Android, Ios, Desktop }

expect fun currentAppPlatform(): AppPlatform

val isIosPlatform: Boolean get() = currentAppPlatform() == AppPlatform.Ios

val isAndroidPlatform: Boolean get() = currentAppPlatform() == AppPlatform.Android

val isDesktopPlatform: Boolean get() = currentAppPlatform() == AppPlatform.Desktop
