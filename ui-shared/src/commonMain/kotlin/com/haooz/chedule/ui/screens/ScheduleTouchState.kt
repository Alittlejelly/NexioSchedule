package com.haooz.chedule.ui.screens

/**
 * 课程表页面级触控状态。刻意用普通 holder 而非 snapshot state：
 * 里面的值每帧都可能变（滑动坐标、手指数量），用 state 会带着几十张卡一起重组。
 *
 * 2026-10-10 从 MainScheduleScreen.kt 抽出迁入 :ui-shared ——
 * DayColumn / CourseCard（已迁）要把本类型放进公开签名。同包名，调用方 import 零改动。
 */
class ScheduleTouchState {
    /** 页面是否正在滑动（横滑 pager 或页内纵滑） */
    var scrolling: Boolean = false
    /** 本次手势是否出现过「多指」。必须放在页面层统计：Compose 只把「命中该节点的指针」
     *  派发给节点，卡片自己看不到落在别的卡片上的手指。用于拦掉多指误触发的点击/长按。 */
    var multiTouch: Boolean = false
}
