package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * 底部弹窗内部状态的 remember 键：**每次「打开」自增，关闭时不动**。
 *
 * 直接拿 `show` 当键会在 `show = false` 的那一刻把表单全部重置 —— 弹窗此时还在播退出动画，
 * 观众看到的是名称/地点/周次一瞬间被清空再淡出（跳闪）。换成这个键，只有下次打开才重建。
 */
@Composable
fun rememberOpenKey(show: Boolean): Int {
    val tracker = remember { OpenStateTracker() }
    if (show) {
        if (!tracker.opened) {
            tracker.opened = true
            tracker.key++
        }
    } else {
        tracker.opened = false
    }
    return tracker.key
}

/** 非快照状态：只在组合期按 show 的边沿推进，不需要触发重组。 */
private class OpenStateTracker {
    var opened = false
    var key = 0
}
