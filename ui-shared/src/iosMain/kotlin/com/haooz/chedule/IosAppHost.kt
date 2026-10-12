@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.haooz.chedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.haooz.chedule.ui.navigation.AppRouter
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.navigation.NavTransitionState
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.HostNavigationState
import com.haooz.chedule.ui.utils.HostOverlayContent
import com.haooz.chedule.ui.utils.HostOverlayState
import com.haooz.chedule.ui.utils.LocalHostNavigation
import com.haooz.chedule.ui.utils.LocalHostOverlays
import platform.UIKit.UIViewController
import top.yukonga.miuix.kmp.utils.MiuixOverlayHostProvider
import top.yukonga.miuix.kmp.utils.MiuixOverlayHostState
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

/** One retained app session shared by the fixed main and overlay controllers. */
class IosAppHost {
    internal val router = AppRouter()
    internal val mainTransition = NavTransitionState()
    internal var navigationOverlayActive by mutableStateOf(false)
    private val navigation = HostNavigationState()
    private val overlays = HostOverlayState()
    private val popupHost = MiuixOverlayHostState()

    var onStateChanged: (() -> Unit)? = null

    val selectedTab: Int get() = navigation.selectedTab
    val tabCount: Int get() = navigation.tabCount
    val shiftMode: Boolean get() = navigation.shiftMode
    val showsNativeTabBar: Boolean get() = navigation.showsNativeTabBar
    val isDark: Boolean get() = navigation.isDark
    val overlayActive: Boolean
        get() = router.hasOverlay || navigationOverlayActive || overlays.isActive || popupHost.isActive
    val mainProgress: Double get() = mainTransition.value.toDouble()
    val cutoutScale: Float get() = navigation.cutoutScale
    val cutoutCenterYRatio: Float get() = navigation.cutoutCenterYRatio

    val mainController: UIViewController by lazy {
        ComposeUIViewController {
            HostEnvironment {
                Surface(Modifier.fillMaxSize()) { NexioIosMain(this@IosAppHost) }
            }
        }
    }

    val overlayController: UIViewController by lazy {
        ComposeUIViewController(configure = { opaque = false }) {
            HostEnvironment {
                Box(Modifier.fillMaxSize()) {
                    HostOverlayContent(overlays)
                    NexioIosNavigation(this@IosAppHost)
                    MiuixPopupHost()
                }
                LaunchedEffect(this@IosAppHost) {
                    snapshotFlow {
                        NativeHostSnapshot(
                            selectedTab, tabCount, shiftMode, showsNativeTabBar,
                            isDark, overlayActive, mainProgress, cutoutScale, cutoutCenterYRatio,
                        )
                    }.collect { onStateChanged?.invoke() }
                }
            }
        }
    }

    fun selectTab(index: Int) {
        if (!overlayActive && showsNativeTabBar && index in 0 until tabCount) {
            navigation.onTabSelected?.invoke(index)
        }
    }

    fun updateBottomBarInset(points: Double) {
        val inset = points.coerceAtLeast(0.0)
        if (kotlin.math.abs(navigation.bottomBarInset - inset) > 0.5) {
            navigation.bottomBarInset = inset
        }
    }

    @Composable
    private fun HostEnvironment(content: @Composable () -> Unit) {
        CourseScheduleTheme {
            MiuixOverlayHostProvider(popupHost) {
                CompositionLocalProvider(
                    LocalAppRouter provides router,
                    LocalHostNavigation provides navigation,
                    LocalHostOverlays provides overlays,
                    content = content,
                )
            }
        }
    }
}

private data class NativeHostSnapshot(
    val selectedTab: Int,
    val tabCount: Int,
    val shiftMode: Boolean,
    val showsNativeTabBar: Boolean,
    val isDark: Boolean,
    val overlayActive: Boolean,
    val mainProgress: Double,
    val cutoutScale: Float,
    val cutoutCenterYRatio: Float,
)
