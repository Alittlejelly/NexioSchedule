package com.haooz.chedule.ui.utils

/** Android / 桌面：底栏由 Compose 自己画，宿主不需要知道选中态 → 空实现。 */
actual fun notifyHostTabSelected(index: Int) = Unit

/** Android：底栏是 Compose 自己画的，没有宿主请求 → 恒 null。 */
actual fun consumeHostTabChangeRequest(): Int? = null
