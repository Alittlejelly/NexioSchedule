package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable

/** iOS 的输入框走 Compose 自己的 BasicTextField，[rememberImeDismiss] 里的 controller 已能收键盘。 */
@Composable
actual fun rememberNativeImeDismiss(): () -> Unit = {}
