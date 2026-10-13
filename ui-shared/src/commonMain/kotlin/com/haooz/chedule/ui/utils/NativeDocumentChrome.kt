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
    /** Outer navigation waits for the editor's save and reverse morph to finish. */
    var contentOverlayActive by mutableStateOf(false)
    /** Editor controls override the retained parent's chrome without competing publishers. */
    var editor by mutableStateOf<NativeDocumentEditorChrome?>(null)
    val effectiveAction: NativeDocumentAction? get() = editor?.save ?: action
}

/** A route-local editor owns native controls, but keeps its original content transition. */
data class NativeDocumentEditorChrome(
    val title: String,
    val cancel: NativeDocumentAction,
    val save: NativeDocumentAction,
)

@Composable
fun NativeDocumentEditorEffect(editor: NativeDocumentEditorChrome?) {
    val chrome = LocalNativeDocumentChrome.current ?: return
    SideEffect { chrome.editor = editor }
    DisposableEffect(chrome) {
        onDispose { chrome.editor = null }
    }
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

/** Keep outer navigation blocked during a Compose morph without disposing its parent page. */
@Composable
fun NativeDocumentOverlayEffect(active: Boolean) {
    val chrome = LocalNativeDocumentChrome.current ?: return
    SideEffect { chrome.contentOverlayActive = active }
    DisposableEffect(chrome) {
        onDispose { chrome.contentOverlayActive = false }
    }
}

/** Publish the same operation that the Compose endAction invokes, scoped to this live page. */
@Composable
fun NativeDocumentActionEffect(action: NativeDocumentAction) {
    val chrome = LocalNativeDocumentChrome.current ?: return
    SideEffect { chrome.action = action }
    DisposableEffect(chrome) {
        onDispose { chrome.action = null }
    }
}
