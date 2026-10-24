package com.haooz.chedule.ui.utils

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView

@Composable
actual fun rememberNativeImeDismiss(): () -> Unit {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    return remember(view, focusManager) {
        {
            focusManager.clearFocus(force = true)
            // AndroidView 里的 EditText 自己握着焦点：Compose 侧清了没用，
            // 得顺着窗口找到真正持有焦点的 View 清掉，再按它的 token 收键盘。
            val focused = view.rootView.findFocus()
            focused?.clearFocus()
            (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(focused?.windowToken ?: view.windowToken, 0)
        }
    }
}
