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
 *
 * 文件选择器、选壁纸、分享面板都要做同一件事 —— **从当前最顶层的视图控制器弹东西**，
 * 所以统一放这里，别在每个实现里各写一份（写错了就是「点了没反应」）。
 */
internal object IosUiHost {

    /** 当前 key window；多场景下取第一个 key window，都没有就退回第一个 window。 */
    fun keyWindow(): UIWindow? {
        val windows = UIApplication.sharedApplication.windows.mapNotNull { it as? UIWindow }
        return windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
    }

    /**
     * 栈顶视图控制器：从 root 一路跟到 `presentedViewController` 的末端。
     *
     * 必须跟到底 —— 已经有模态页（比如分享面板）在的时候，直接拿 root 去 present
     * 会抛 `Attempt to present ... which is already presenting`。
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
 * iOS 原生提示条（对应 Android 的 `Toast`）。
 *
 * ## 为什么要有
 *
 * `showToast` 的 native actual 原先只打日志（注释写着「等 :iosApp 真跑起来再决定是接
 * 原生提示还是维持日志」）—— 现在真跑起来了。导入导出、分享、选壁纸这些流程**全靠它给反馈**，
 * 只打日志的话用户看到的就是「点了没反应」，功能等于坏的。
 *
 * ## 行为对齐 Android
 *
 * - 时长同 `Toast.LENGTH_SHORT`(2s) / `LENGTH_LONG`(3.5s)
 * - **不合并**：连续调用弹两条（各自计时、各自消失），与 Android 语义一致；
 *   上下错开堆叠，避免两条文字叠在一起看不清
 * - 不拦截触摸（`userInteractionEnabled = false`），弹提示时底下的界面照常能点
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

            // 从下往上堆叠：已有提示条就往上让
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
        // 圆角
        layer.cornerRadius = 10.0
        layer.masksToBounds = true
        // 内边距：用 attributed string 太重，这里靠 sizeThatFits 后手动加宽
        // （见 show() 里对 fitting 的处理）
    }
}
