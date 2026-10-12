package com.haooz.chedule.ui.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Navigation chrome owned by a native host, scoped to one app instance. */
class HostNavigationState {
    var selectedTab by mutableIntStateOf(0)
    var tabCount by mutableIntStateOf(3)
    var shiftMode by mutableStateOf(false)
    var showsNativeTabBar by mutableStateOf(true)
    var isDark by mutableStateOf(false)
    var bottomBarInset by mutableStateOf(0.0)
    var onTabSelected: ((Int) -> Unit)? = null
}

val LocalHostNavigation = staticCompositionLocalOf<HostNavigationState?> { null }

/** A fixed render destination for full-screen overlays above native chrome. */
class HostOverlayState {
    internal val entries = mutableStateListOf<HostOverlayEntry>()
    val isActive: Boolean get() = entries.any { it.active }
}

internal class HostOverlayEntry {
    var active by mutableStateOf(false)
    var content by mutableStateOf<@Composable () -> Unit>({})
}

val LocalHostOverlays = staticCompositionLocalOf<HostOverlayState?> { null }

/** Registers once; content keeps its composition in the upper controller while idle. */
@Composable
fun BoxScope.HostedOverlay(
    active: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val host = LocalHostOverlays.current
    if (host == null) {
        content(this)
        return
    }
    val colors = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val forcedDark = LocalForcedDarkTheme.current
    val entry = remember(host) { HostOverlayEntry() }
    SideEffect {
        entry.active = active
        entry.content = {
            MiuixTheme(colors = colors, textStyles = textStyles) {
                CompositionLocalProvider(LocalForcedDarkTheme provides forcedDark) {
                    Box(Modifier.fillMaxSize(), content = content)
                }
            }
        }
    }
    DisposableEffect(host, entry) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

@Composable
fun HostOverlayContent(state: HostOverlayState) {
    Box(Modifier.fillMaxSize()) {
        for (entry in state.entries) {
            androidx.compose.runtime.key(entry) { entry.content() }
        }
    }
}
