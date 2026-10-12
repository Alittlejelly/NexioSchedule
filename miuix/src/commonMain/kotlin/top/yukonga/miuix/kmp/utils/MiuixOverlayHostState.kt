// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.utils

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.material.LocalChromeLensEnabled
import top.yukonga.miuix.kmp.material.LocalUseFakeProgressiveBlur
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.LocalAppSettingDark
import top.yukonga.miuix.kmp.theme.LocalColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalColors
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.LocalForcedDarkTheme
import top.yukonga.miuix.kmp.theme.LocalTextStyles
import top.yukonga.miuix.kmp.theme.TextStyles

/**
 * A root-scoped registry shared by registration and rendering compositions.
 *
 * Install the same instance in both roots with [MiuixOverlayHostProvider]. Render
 * [MiuixPopupUtils.Companion.MiuixPopupHost] only in the upper root, directly inside
 * the provider rather than a nested Scaffold. Scaffold-local overlays remain local.
 */
@Stable
class MiuixOverlayHostState {
    internal val dialogs = mutableStateListOf<MiuixPopupUtils.DialogState>()
    internal val popups = mutableStateListOf<MiuixPopupUtils.PopupState>()

    /** Idle registrations do not block input; entering and exiting entries do. */
    val isActive: Boolean
        get() = dialogs.any { it.showState.value || it.isAnimating } ||
            popups.any { it.showState.value || it.isAnimating }
}

/** Provides a shared registry without replacing platform owners or window locals. */
@Composable
fun MiuixOverlayHostProvider(
    state: MiuixOverlayHostState,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSharedOverlayHost provides state,
        LocalDialogStates provides state.dialogs,
        LocalPopupStates provides state.popups,
        LocalRootDialogStates provides state.dialogs,
        LocalRootPopupStates provides state.popups,
        content = content,
    )
}

internal val LocalSharedOverlayHost = staticCompositionLocalOf<MiuixOverlayHostState?> { null }

internal fun overlayEntryIsActive(
    show: Boolean,
    pendingOpen: Boolean,
    contentCurrent: Boolean,
    contentTarget: Boolean,
    contentIdle: Boolean,
    dimCurrent: Boolean,
    dimTarget: Boolean,
    dimIdle: Boolean,
): Boolean = show || pendingOpen || contentCurrent || contentTarget || !contentIdle ||
    dimCurrent || dimTarget || !dimIdle

/**
 * Only portable theme and component settings travel with an entry. Density,
 * keyboard, focus, accessibility and window owners come from the rendering root.
 */
internal data class MiuixOverlayTheme(
    val colors: Colors,
    val textStyles: TextStyles,
    val colorSchemeMode: ColorSchemeMode?,
    val contentColor: Color,
    val indication: Indication,
    val overscrollFactory: OverscrollFactory?,
    val appDark: Boolean?,
    val forcedDark: Boolean?,
    val dismiss: (() -> Unit)?,
    val chromeLensEnabled: Boolean,
    val useFakeProgressiveBlur: Boolean,
    val predictiveBackEnabled: Boolean,
) {
    @Composable
    fun Provide(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalTextStyles provides textStyles,
            LocalColorSchemeMode provides colorSchemeMode,
            LocalContentColor provides contentColor,
            LocalIndication provides indication,
            LocalOverscrollFactory provides overscrollFactory,
            LocalAppSettingDark provides appDark,
            LocalForcedDarkTheme provides forcedDark,
            LocalDismissState provides dismiss,
            LocalChromeLensEnabled provides chromeLensEnabled,
            LocalUseFakeProgressiveBlur provides useFakeProgressiveBlur,
            LocalPredictiveBackEnabled provides predictiveBackEnabled,
            content = content,
        )
    }
}

@Composable
internal fun currentMiuixOverlayTheme(): MiuixOverlayTheme = MiuixOverlayTheme(
    colors = LocalColors.current,
    textStyles = LocalTextStyles.current,
    colorSchemeMode = LocalColorSchemeMode.current,
    contentColor = LocalContentColor.current,
    indication = LocalIndication.current,
    overscrollFactory = LocalOverscrollFactory.current,
    appDark = LocalAppSettingDark.current,
    forcedDark = LocalForcedDarkTheme.current,
    dismiss = LocalDismissState.current,
    chromeLensEnabled = LocalChromeLensEnabled.current,
    useFakeProgressiveBlur = LocalUseFakeProgressiveBlur.current,
    predictiveBackEnabled = LocalPredictiveBackEnabled.current,
)
