package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * 取一个「立刻收起软键盘」的动作。
 *
 * 只靠 Compose 的 `LocalSoftwareKeyboardController` 不够：输入框是 `AndroidView` 里的原生
 * `EditText` 时，Compose 没有文本输入会话 —— controller 为 null、`FocusManager.clearFocus()`
 * 也清不掉原生焦点，于是「点保存」时键盘照旧挂着。所以再叠一层平台实现去清原生焦点。
 */
@Composable
fun rememberImeDismiss(): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val dismissNative = rememberNativeImeDismiss()
    return remember(keyboard, dismissNative) {
        {
            keyboard?.hide()
            dismissNative()
        }
    }
}

/** 平台侧：清掉原生输入框的焦点并收起键盘。 */
@Composable
expect fun rememberNativeImeDismiss(): () -> Unit
