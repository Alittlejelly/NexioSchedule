package com.haooz.chedule.ui.utils

/**
 * 当前运行平台。
 *
 * 项目里「判断是不是 iOS」过去有两种混淆写法：宿主穿参 `isIos = true`（iOS 侧传，其它端默认 false），或读 `hostNavigation != null`。二者语义不同：前者是平台本身，后者是「有没有原生宿主接管 chrome」。混用会误判（如将来 macOS 也挂宿主，`hostNavigation != null` 就不等于 iOS）。
 *
 * 规则：按平台分支一律用 [appPlatform] / [isIosPlatform]；判「原生宿主是否接管」继续看 `hostNavigation`，别换成平台判断。
 */
enum class AppPlatform { Android, Ios, Desktop }

expect fun currentAppPlatform(): AppPlatform

val isIosPlatform: Boolean get() = currentAppPlatform() == AppPlatform.Ios

val isAndroidPlatform: Boolean get() = currentAppPlatform() == AppPlatform.Android

val isDesktopPlatform: Boolean get() = currentAppPlatform() == AppPlatform.Desktop
