@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.utils

import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTimer
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

/**
 * iOS 宿主 UI 工具：找 key window / 栈顶 VC / 弹原生提示条。
 * 文件选择器、选壁纸、分享面板都要从最顶层 VC 弹东西，统一放这里避免各写一份。
 */
internal object IosUiHost {

    /** 当前 key window；多场景下取第一个 key window，都没有就退回第一个 window。 */
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
 * iOS 原生提示条（对应 Android 的 `Toast`）：导入导出、分享、选壁纸这些流程全靠它给反馈。
 * 时长对齐 `LENGTH_SHORT`(2s) / `LENGTH_LONG`(3.5s)；**不合并**，连续调用弹两条、上下错开堆叠；
 * 不拦截触摸（`userInteractionEnabled = false`）。
 */
object IosToast {

    private const val SHORT_SECONDS = 2.0
    private const val LONG_SECONDS = 3.5
    private const val MAX_WIDTH = 280.0
    private const val H_PADDING = 14.0
    private const val V_PADDING = 10.0
    private const val BOTTOM_INSET = 96.0
    private const val STACK_GAP = 8.0

    /** 正在显示的提示条，用于上下错开堆叠（各自独立计时） */
    private val active = mutableListOf<UILabel>()

    /** 启动注入：把 [com.haooz.chedule.data.showToast] 接到原生提示条上。 */
    fun install() {
        com.haooz.chedule.data.toastHandler = { message, longDuration ->
            show(message, longDuration)
        }
    }

    private fun show(message: String, longDuration: Boolean) {
        if (message.isBlank()) return
        // 可能从协程/回调线程调用，UI 操作统一回主队列
        NSOperationQueue.mainQueue.addOperationWithBlock {
            val window = IosUiHost.keyWindow() ?: return@addOperationWithBlock
            val label = buildLabel(message)
            val windowW = window.bounds.useContents { size.width }
            val windowH = window.bounds.useContents { size.height }

            // 自身尺寸：限宽后按文字自适应
            val fitting = label.sizeThatFits(CGSizeMake(MAX_WIDTH, Double.MAX_VALUE))
            val w = fitting.useContents { width }.coerceAtMost(MAX_WIDTH)
            val h = fitting.useContents { height }

            var y = windowH - BOTTOM_INSET
            active.forEach { existing ->
                val existingFrame = existing.frame
                val existingTop = existingFrame.useContents { origin.y }
                val existingH = existingFrame.useContents { size.height }
                y = minOf(y, existingTop - STACK_GAP - h)
            }
            label.setFrame(CGRectMake((windowW - w) / 2.0, y, w, h))
            window.addSubview(label)
            active.add(label)

            NSTimer.scheduledTimerWithTimeInterval(
                if (longDuration) LONG_SECONDS else SHORT_SECONDS,
                repeats = false,
            ) {
                label.removeFromSuperview()
                active.remove(label)
            }
        }
    }

    private fun buildLabel(message: String): UILabel = UILabel().apply {
        text = message
        numberOfLines = 0
        textAlignment = NSTextAlignmentCenter
        textColor = UIColor.whiteColor
        backgroundColor = UIColor.colorWithWhite(0.0, alpha = 0.82)
        font = UIFont.systemFontOfSize(14.0)
        userInteractionEnabled = false
        layer.cornerRadius = 10.0
        layer.masksToBounds = true
        // 内边距：attributed string 太重，靠 sizeThatFits 后手动加宽（见 show() 对 fitting 的处理）
    }
}
