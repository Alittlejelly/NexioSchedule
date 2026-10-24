package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable

/** 桌面端没有软键盘，靠 [rememberImeDismiss] 里的 Compose controller 就够。 */
@Composable
actual fun rememberNativeImeDismiss(): () -> Unit = {}
