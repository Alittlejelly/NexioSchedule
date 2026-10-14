@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.utils

/**
 * iOS 状态栏样式桥（对应 Android 的 `ApiCompat.setSystemBarsAppearance`）。
 *
 * ⚠ 真正调 API 的是 Swift 侧：Kotlin/Native 没生成 `UIApplication.setStatusBarStyle`
 *（iOS 9 起废弃，cinterop 剔掉了）；VC 级覆写 `preferredStatusBarStyle` 也不通 ——
 * Compose 根塞在 SwiftUI 的 `UIViewControllerRepresentable` 里，状态栏裁决权在父容器手上。
 * 配套 Info.plist 必须设 `UIViewControllerBasedStatusBarAppearance = false`。
 *
 * `statusDark` = 「状态栏底色是暗的」（暗底配亮字），`true` → Swift 侧 `.lightContent`。
 */
object IosSystemBars {

    /** 当前状态栏底色是否为暗。Swift 侧读这个值算样式。 */
    var statusBarDark: Boolean = false
        private set

    /** Swift 侧启动时注入。为 null（未注入）时只更新状态，不报错。 */
    var onApply: (() -> Unit)? = null

    /** 应用状态栏样式。由 `CourseScheduleApp` 的 `onSystemBarsChanged` 调用。 */
    fun update(statusDark: Boolean) {
        if (statusBarDark == statusDark) return
        statusBarDark = statusDark
        onApply?.invoke()
    }
}
