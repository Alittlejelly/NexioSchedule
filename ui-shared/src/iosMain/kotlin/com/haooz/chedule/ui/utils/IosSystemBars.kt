@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.utils

/**
 * iOS 状态栏样式桥（对应 Android 的 `ApiCompat.setSystemBarsAppearance`）。
 *
 * ## 为什么真正干活的是 Swift 侧
 *
 * Kotlin/Native 的 UIKit 绑定**没有生成** `UIApplication.setStatusBarStyle`
 * （iOS 9 起废弃，cinterop 直接剔掉了）—— 实测 `Unresolved reference`。
 * 而 VC 级覆写 `preferredStatusBarStyle` 也不通：Compose 根塞在 SwiftUI 的
 * `UIViewControllerRepresentable` 里，状态栏裁决权在**父容器**（SwiftUI 的 hosting
 * controller）手上，子 VC 覆写根本不会被问到（`childForStatusBarStyle` 默认 nil）。
 *
 * 所以分工：**Kotlin 持状态 + 发通知，Swift 调那个废弃的全局 API**。
 * 配套 Info.plist 必须设 `UIViewControllerBasedStatusBarAppearance = false`，
 * 否则全局 API 不生效（该键为 false 时才尊重全局设置）。
 *
 * ## 语义对齐 Android
 *
 * Android 侧传进来的 `statusDark` 是「**状态栏底色是暗的**」，暗底要配**亮字**
 * （注意 `APPEARANCE_LIGHT_STATUS_BARS` 是反的：它表示「浅色栏 = 深色图标」）。
 * 故 `statusDark = true` → Swift 侧 `.lightContent`。
 */
object IosSystemBars {

    /** 当前状态栏底色是否为暗（决定图标用亮色还是暗色）。Swift 侧读这个值算样式。 */
    var statusBarDark: Boolean = false
        private set

    /**
     * Swift 侧在启动时注入：真正去调 `UIApplication.setStatusBarStyle`。
     * 为 null（还没注入）时只更新状态，不报错。
     */
    var onApply: (() -> Unit)? = null

    /**
     * 应用状态栏样式。由 `CourseScheduleApp` 的 `onSystemBarsChanged` 调用。
     *
     * @param statusDark 状态栏底色是否为暗
     */
    fun update(statusDark: Boolean) {
        if (statusBarDark == statusDark) return
        statusBarDark = statusDark
        onApply?.invoke()
    }
}
