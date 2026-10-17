package com.haooz.chedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.haooz.chedule.ui.utils.LocalNativeDocumentChrome
import com.haooz.chedule.ui.utils.hostStatusBarHeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.LocalOverScrollState
import top.yukonga.miuix.kmp.utils.OverScrollState
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop

/**
 * 「全屏文档页」的统一脚手架：折叠大标题顶栏 + 玻璃返回按钮 + 内容采样层
 */
@Composable
fun DocumentPageScaffold(
    title: String,
    onBack: () -> Unit,
    endAction: (@Composable (backdropAlpha: Float, shadowAlpha: Float) -> Unit)? = null,
    overlay: (@Composable BoxScope.(liquidGlassBackdrop: com.kyant.backdrop.Backdrop) -> Unit)? = null,
    content: @Composable (
        scrollBehavior: SharedScrollBehavior,
        liquidGlassBackdrop: com.kyant.backdrop.Backdrop,
    ) -> Unit,
) {
    val nativeChrome = LocalNativeDocumentChrome.current
    val nativeBarInset = (nativeChrome?.barInset ?: 0.0).dp
    val showNavigationActions = nativeChrome == null
    val tintArgb = MiuixTheme.colorScheme.primary.toArgb().toLong()
    SideEffect {
        nativeChrome?.title = title
        nativeChrome?.tintArgb = tintArgb
    }
    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val scrollBehavior = rememberSharedScrollBehavior()
    // 本页独立的 overscroll 作用域：内容的 overScrollVertical 节点把越界量写进这个实例，
    // 顶栏 CollapsibleTopAppBar 也从同一个 CompositionLocal 读它 —— 两边必须是同一套。
    // 搬迁到 :miuix 后曾出现两套 LocalOverScrollState（app 的 fork 与 miuix 自带的），
    // 顶栏读的是永远为 0 的那套，于是「回弹也显现材质」整条分支成了死代码。
    // 顺带隔离：每页一个实例，不会互相串到 compositionLocalOf 的全局默认实例上。
    val pageOverScrollState = remember { OverScrollState() }

    CompositionLocalProvider(LocalOverScrollState provides pageOverScrollState) {
        Scaffold(
            modifier = Modifier
                .background(backgroundColor)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                )
                .consumeWindowInsets(PaddingValues(top = nativeBarInset)),
            topBar = {
                ProgressiveBlurTopBar(
                    backdrop = liquidGlassBackdrop,
                    topInsetAdjustment = nativeBarInset,
                    statusBarHeightOverride = hostStatusBarHeight(),
                ) {
                    CollapsibleTopAppBar(
                        title = title,
                        largeTitle = title,
                        modifier = Modifier,
                        scrollBehavior = scrollBehavior,
                        contentPadding = {},
                        startAction = if (showNavigationActions) {
                            { backdropAlpha, shadowAlpha ->
                                LiquidTopBarButton(
                                    onClick = onBack,
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.ChevronBackward,
                                    contentDescription = "返回",
                                    performHapticFeedback = false,
                                    iconSize = 25.dp,
                                    iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                            }
                        } else null,
                        endAction = if (showNavigationActions) endAction else null,
                    )
                }
            }
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                // 采样层只包内容；顶栏玻璃按钮放在层外，避免循环采样
                Box(
                    modifier = Modifier.fillMaxSize().then(
                        Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                    )
                ) {
                    content(scrollBehavior, liquidGlassBackdrop)
                }
                overlay?.invoke(this, liquidGlassBackdrop)
            }
        }
    }
}
