package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Page-scoped native chrome. Absent on Android, desktop, and retained Compose routes. */
@Stable
class NativeDocumentChrome {
    var barInset by mutableStateOf(0.0)
    var title by mutableStateOf("")
    var tintArgb by mutableStateOf(0L)
    var action by mutableStateOf<NativeDocumentAction?>(null)
}

/** The operation stays in the existing route; UIKit only renders its presentation. */
data class NativeDocumentAction(
    val symbol: String,
    val label: String,
    val enabled: Boolean = true,
    val busy: Boolean = false,
    val successful: Boolean = false,
    val onClick: () -> Unit,
)

val LocalNativeDocumentChrome = staticCompositionLocalOf<NativeDocumentChrome?> { null }

/** Publish the same operation that the Compose endAction invokes, scoped to this live page. */
@Composable
fun NativeDocumentActionEffect(action: NativeDocumentAction) {
    val chrome = LocalNativeDocumentChrome.current ?: return
    SideEffect { chrome.action = action }
    DisposableEffect(chrome) {
        onDispose { chrome.action = null }
    }
}
