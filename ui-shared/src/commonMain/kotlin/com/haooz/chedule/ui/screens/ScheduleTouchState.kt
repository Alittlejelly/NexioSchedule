package com.haooz.chedule.ui.screens

/**
 * 课程表页面级触控状态。刻意用普通 holder 而非 snapshot state：
 * 值每帧都可能变，用 state 会带着几十张卡一起重组。
 */
class ScheduleTouchState {
    /** 页面是否正在滑动（横滑 pager 或页内纵滑） */
    var scrolling: Boolean = false
    /** 本次手势是否出现过「多指」。必须放页面层统计：Compose 只把命中该节点的指针派发给节点，
     *  卡片看不到落在别处的手指。用于拦掉多指误触发的点击/长按。 */
    var multiTouch: Boolean = false
}
