package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
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
    var leading by mutableStateOf<NativeDocumentAction?>(null)
    var secondaryAction by mutableStateOf<NativeDocumentAction?>(null)
    var bottomBar by mutableStateOf<NativeDocumentBottomBar?>(null)
    /** true = 顶栏整个交给系统（原生导航栏画标题），页面不画 Compose 渐变模糊顶栏 */
    var usesSystemHeader by mutableStateOf(false)
    /** Outer navigation waits for the editor's save and reverse morph to finish. */
    var contentOverlayActive by mutableStateOf(false)
    /** Editor controls override the retained parent's chrome without competing publishers. */
    var editor by mutableStateOf<NativeDocumentEditorChrome?>(null)
    val effectiveAction: NativeDocumentAction? get() = editor?.save ?: action
    val effectiveLeading: NativeDocumentAction? get() = editor?.cancel ?: leading
    val effectiveSecondaryAction: NativeDocumentAction? get() = if (editor == null) secondaryAction else null
}

/**
 * 原生底部悬浮条（iOS）：玻璃材质与布局由系统控件渲染，页面只发布语义。
 * Android / 桌面没有 chrome，这条恒为 null，页面仍用 Compose 画的底部胶囊。
 */
data class NativeDocumentBottomBar(
    val title: String,
    val subtitle: String,
    val badge: String,
    val toggleSymbol: String,
    val toggleLabel: String,
    val toggleEnabled: Boolean = true,
    val primarySymbol: String,
    val primaryLabel: String,
    val primaryEnabled: Boolean = true,
    val onToggle: () -> Unit,
    val onPrimary: () -> Unit,
)

/** 发布底部悬浮条；传 null 表示本页没有（原生侧会收起）。 */
@Composable
fun NativeDocumentBottomBarEffect(bar: NativeDocumentBottomBar?) {
    val chrome = LocalNativeDocumentChrome.current ?: return
    SideEffect { chrome.bottomBar = bar }
    DisposableEffect(chrome) {
        onDispose { chrome.bottomBar = null }
    }
}

/** 顶栏交给系统（原生导航栏标题 + 材质），页面不再画 Compose 渐变模糊顶栏。 */
@Composable
fun NativeDocumentSystemHeaderEffect(enabled: Boolean) {
    val chrome = LocalNativeDocumentChrome.current ?: return
    SideEffect { chrome.usesSystemHeader = enabled }
    DisposableEffect(chrome) {
        onDispose { chrome.usesSystemHeader = false }
    }
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

/** A page may handle leading taps locally while keeping outer swipe navigation blocked. */
@Composable
fun NativeDocumentLeadingActionEffect(action: NativeDocumentAction?) {
    val chrome = LocalNativeDocumentChrome.current ?: return
    SideEffect { chrome.leading = action }
    DisposableEffect(chrome) {
        onDispose { chrome.leading = null }
    }
}

/** Publish an optional second operation without replacing the primary trailing action. */
@Composable
fun NativeDocumentSecondaryActionEffect(action: NativeDocumentAction?) {
    val chrome = LocalNativeDocumentChrome.current ?: return
    SideEffect { chrome.secondaryAction = action }
    DisposableEffect(chrome) {
        onDispose { chrome.secondaryAction = null }
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
