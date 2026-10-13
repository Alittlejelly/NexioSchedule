package com.haooz.chedule.ui.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Navigation chrome owned by a native host, scoped to one app instance. */
class HostNavigationState {
    var selectedTab by mutableIntStateOf(0)
    var tabCount by mutableIntStateOf(3)
    var shiftMode by mutableStateOf(false)
    var showsNativeTabBar by mutableStateOf(true)
    var isDark by mutableStateOf(false)
    var bottomBarInset by mutableStateOf(0.0)
    var topBarInset by mutableStateOf(0.0)
    var onTabSelected: ((Int) -> Unit)? = null
    /**
     * 开洞缩放进度，1f=正常。宿主（当前只有 iOS）据此对原生 chrome 施加同样缩放——
     * Android 上底栏是 Compose 的，在主内容缩放层内，本就跟随，无需上报。
     */
    var cutoutScale by mutableFloatStateOf(1f)
    /**
     * 开洞缩放的变换原点纵比，与主内容 graphicsLayer 同源。开洞中心由
     * CustomizeScheduleScreen 回传，不是常量，故一并上报供原生 chrome 对齐。
     */
    var cutoutCenterYRatio by mutableFloatStateOf(0.5f)
    /** Whether the main screen top bar header controls are owned by native host chrome. */
    var showsNativeHeader by mutableStateOf(false)
    var onOpenSwitchSchedule: (() -> Unit)? = null
    var onJumpWeek: (() -> Unit)? = null
    var onCourseManage: (() -> Unit)? = null
    var onEnterCustomize: (() -> Unit)? = null
    var onJumpToDate: (() -> Unit)? = null
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
fun HostOverlayContent(
    state: HostOverlayState,
    drawInactiveEntries: () -> Boolean = { true },
) {
    Box(Modifier.fillMaxSize()) {
        for (entry in state.entries) {
            androidx.compose.runtime.key(entry) {
                // Keep the composition alive without drawing or hit-testing hidden root chrome.
                Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        if (entry.active || drawInactiveEntries()) placeable.place(0, 0)
                    }
                }) {
                    entry.content()
                }
            }
        }
    }
}
