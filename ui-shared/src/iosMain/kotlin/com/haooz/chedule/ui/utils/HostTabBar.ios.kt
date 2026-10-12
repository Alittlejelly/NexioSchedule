package com.haooz.chedule.ui.utils

/** iOS：底栏由宿主（`UITabBarController`）画，写回 [IosNativeTabBar] 供 Swift 同步。 */
actual fun notifyHostTabSelected(index: Int) {
    IosNativeTabBar.notifyFromCompose(index)
}

/** iOS：取走宿主要求的切页请求（消费即清，避免重复驱动 pager）。 */
actual fun consumeHostTabChangeRequest(): Int? = IosNativeTabBar.consumePendingHostRequest()
