@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.utils

import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTimer
import platform.QuartzCore.kCACornerCurveContinuous
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

/**
 * iOS 宿主 UI 工具：找 key window / 栈顶 VC / 弹原生提示条。
 * 文件选择器、选壁纸、分享面板都要从最顶层 VC 弹东西，统一放这里避免各写一份。
 */
internal object IosUiHost {

    /** 当前 key window；多场景取第一个，没有则退回第一个 window。 */
    fun keyWindow(): UIWindow? {
        val windows = UIApplication.sharedApplication.windows.mapNotNull { it as? UIWindow }
        return windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
    }

    /**
     * 栈顶视图控制器：从 root 一路跟到 `presentedViewController` 的末端。
     * 必须跟到底 —— 已有模态页时直接拿 root 去 present 会抛
     * `Attempt to present ... which is already presenting`。
     */
    fun topViewController(): UIViewController? {
        var vc = keyWindow()?.rootViewController
        while (true) {
            val presented = vc?.presentedViewController ?: return vc
            vc = presented
        }
    }
}

/**
 * iOS 提示条的**接线与兜底**（正式渲染在 Swift 侧 `NativeToast`，见 `iosApp/iosApp/ContentView.swift`）。
 *
 * 只做两件事：1. 启动时把 [com.haooz.chedule.data.showToast] 接到 [nativeShow]（Swift 启动注入）；
 * 2. Swift 未注入时的**极早期调用**，用半透明黑底提示兜底。
 *
 * ⚠ 液态玻璃刻意不在这里做：`UIGlassEffect` 在 Kotlin/Native 绑定里编译得过但运行期行为不可控，
 * 取 window 也得用 `connectedScenes`（`UIApplication.windows` iOS 13+ 已废弃、启动早期返回空），
 * 两边各写一份玻璃只会让效果漂移。
 *
 * 时长对齐 `LENGTH_SHORT`(2s) / `LENGTH_LONG`(3.5s)；**不合并**，连续调用弹两条、上下错开堆叠；
 * 不拦截触摸（`userInteractionEnabled = false`）。
 */
object IosToast {

    private const val SHORT_SECONDS = 2.0
    private const val LONG_SECONDS = 3.5
    private const val MAX_WIDTH = 280.0
    private const val H_PADDING = 14.0
    private const val V_PADDING = 10.0
    private const val BOTTOM_INSET = 140.0
    private const val STACK_GAP = 8.0
    private const val CORNER_RADIUS = 16.0

    /** 正在显示的提示条，用于上下错开堆叠（各自独立计时） */
    private val active = mutableListOf<UIView>()

    /**
     * Swift 侧注入的原生渲染实现（`NativeToast`，见 `iosApp/iosApp/ContentView.swift`）。
     *
     * ⚠ 渲染放 Swift 侧的原因见类注释：`UIGlassEffect` 运行期行为不可控，`UIApplication.windows`
     * 启动早期还会返回空（原写法踩过）。为 null 时退回下面的内置实现（宿主未注入的极早期调用）。
     */
    var nativeShow: ((message: String, longDuration: Boolean) -> Unit)? = null

    /** 启动注入：把 [com.haooz.chedule.data.showToast] 接到原生提示条上。 */
    fun install() {
        com.haooz.chedule.data.toastHandler = { message, longDuration ->
            val native = nativeShow
            if (native != null) native(message, longDuration) else show(message, longDuration)
        }
    }

    private fun show(message: String, longDuration: Boolean) {
        if (message.isBlank()) return
        // 可能从协程/回调线程调用，UI 操作统一回主队列
        NSOperationQueue.mainQueue.addOperationWithBlock {
            val window = IosUiHost.keyWindow() ?: return@addOperationWithBlock
            val windowW = window.bounds.useContents { size.width }
            val windowH = window.bounds.useContents { size.height }

            val toast = buildToast(message)
            val w = toast.frame.useContents { size.width }
            val h = toast.frame.useContents { size.height }

            // 已有提示条时往上错开，避免叠在一起
            var y = windowH - BOTTOM_INSET
            active.forEach { existing ->
                val existingTop = existing.frame.useContents { origin.y }
                y = minOf(y, existingTop - STACK_GAP - h)
            }
            toast.setFrame(CGRectMake((windowW - w) / 2.0, y, w, h))
            window.addSubview(toast)
            active.add(toast)

            NSTimer.scheduledTimerWithTimeInterval(
                if (longDuration) LONG_SECONDS else SHORT_SECONDS,
                repeats = false,
            ) {
                toast.removeFromSuperview()
                active.remove(toast)
            }
        }
    }

    /**
     * 组装**兜底**提示条（尺寸与子视图位置都已摆好，调用方直接 setFrame 定位即可）。
     *
     * ⚠ 刻意**不做液态玻璃**：那是 Swift 侧 `NativeToast` 的职责，兜底只要求「有个能看的提示」。
     */
    private fun buildToast(message: String): UIView {
        val label = UILabel().apply {
            text = message
            numberOfLines = 0
            textAlignment = NSTextAlignmentCenter
            textColor = UIColor.whiteColor
            font = UIFont.systemFontOfSize(14.0)
            userInteractionEnabled = false
        }
        // 内边距靠实测后手动加（attributed string 太重）
        val textSize = label.sizeThatFits(
            CGSizeMake(MAX_WIDTH - H_PADDING * 2, Double.MAX_VALUE)
        )
        val textW = textSize.useContents { width }
        val textH = textSize.useContents { height }
        val w = textW + H_PADDING * 2
        val h = textH + V_PADDING * 2

        val container = UIView().apply {
            userInteractionEnabled = false
            backgroundColor = UIColor.colorWithWhite(0.0, alpha = 0.82)
            clipsToBounds = true
            layer.cornerRadius = CORNER_RADIUS
            layer.cornerCurve = kCACornerCurveContinuous
        }
        container.addSubview(label)
        label.setFrame(CGRectMake(H_PADDING, V_PADDING, textW, textH))
        container.setFrame(CGRectMake(0.0, 0.0, w, h))
        return container
    }
}
