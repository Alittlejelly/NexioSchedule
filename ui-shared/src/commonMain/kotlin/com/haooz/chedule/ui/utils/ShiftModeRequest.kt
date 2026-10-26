package com.haooz.chedule.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 排班模式切换的**跨层请求**。
 *
 * 设置页已经从主 pager 搬到路由栈（二级页），它拿不到主界面组合里的
 * `showShiftLoading` / `isExitingShift`，也不该拿 —— 那套 loading 覆盖层和
 * pager 重排必须留在常驻底座里执行。
 *
 * 所以这里只做传递：设置页写入 [pending]，主界面 `LaunchedEffect` 消费后置回 null。
 * 与 [com.haooz.chedule.ui.components.TabletNavSideState] 同一模式（全局单点，不进组合参数）。
 */
object ShiftModeRequest {

    /** null = 无请求；true = 进入排班；false = 退出排班 */
    var pending: Boolean? by mutableStateOf(null)
        private set

    fun enter() {
        pending = true
    }

    fun exit() {
        pending = false
    }

    /** 主界面消费：取走并清空（避免 LaunchedEffect 重复触发同一请求）。 */
    fun consume(): Boolean? {
        val value = pending
        pending = null
        return value
    }
}
