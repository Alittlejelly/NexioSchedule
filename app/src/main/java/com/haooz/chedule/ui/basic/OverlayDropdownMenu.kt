package com.haooz.chedule.ui.basic

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun resolveSelectedText(entries: List<DropdownEntry>): String? {
    for (entry in entries) {
        for (item in entry.items) {
            if (item.selected) return item.text
        }
    }
    return null
}

/**
 * 收起态胶囊的默认额外补偿。
 *
 * 收起态尺寸 = 「选中文字 + 箭头图标」的实测宽高 + 这个补偿。
 * 补偿只加在 lerp 的起点上，展开态终点仍是面板自然尺寸。
 * 单点下调即可全局生效；个别页面需要不同值时用 `OverlayDropdownMenu(collapseExtra = ...)` 覆盖。
 */
private val DefaultCollapseExtra = DpSize(14.dp, 10.dp)

/**
 * A [BasicComponent] wrapper that opens an [OverlayDropdownPopup] for a single [DropdownEntry].
 *
 * When [summary] is null, the text of the currently selected [top.yukonga.miuix.kmp.basic.DropdownItem]
 * is shown automatically.
 */
@Composable
fun OverlayDropdownMenu(
    entry: DropdownEntry,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    collapseExtra: DpSize = DefaultCollapseExtra,
) {
    val entries = remember(entry) { listOf(entry) }
    OverlayDropdownMenu(
        entries = entries,
        title = title,
        modifier = modifier,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        dropdownColors = dropdownColors,
        startAction = startAction,
        bottomAction = bottomAction,
        insideMargin = insideMargin,
        maxHeight = maxHeight,
        enabled = enabled,
        renderInRootScaffold = renderInRootScaffold,
        collapseOnSelection = collapseOnSelection,
        onExpandedChange = onExpandedChange,
        liquidGlassBackdrop = liquidGlassBackdrop,
        collapseExtra = collapseExtra,
    )
}

/**
 * A [BasicComponent] wrapper that opens an [OverlayDropdownPopup] for one or more [DropdownEntry] groups.
 */
@Composable
fun OverlayDropdownMenu(
    entries: List<DropdownEntry>,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = entries.size <= 1,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    collapseExtra: DpSize = DefaultCollapseExtra,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDropdownExpanded = remember { mutableStateOf(false) }
    val isHoldDown = remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val currentOnExpandedChange = rememberUpdatedState(onExpandedChange)
    val setExpanded: (Boolean) -> Unit = remember {
        { expanded ->
            if (isDropdownExpanded.value != expanded) {
                isDropdownExpanded.value = expanded
                currentOnExpandedChange.value?.invoke(expanded)
            }
        }
    }

    // 弹窗展开进度，用于驱动触发内容（选中文字与箭头）的淡入淡出
    val fractionState = remember { mutableStateOf(0f) }
    // 触发区（选中文字 + 箭头）实测尺寸，作为弹窗收起态的起点尺寸
    var triggerSize by remember { mutableStateOf(IntSize.Zero) }
    // 图标淡出：打开时系数 2.5（更快淡完），关闭时 1.7（更早出现）——
    // 与 LiquidGlassDropdownMenu 的触发图标完全一致
    val triggerAlpha: () -> Float = remember {
        {
            val iconK = if (isDropdownExpanded.value) 2.5f else 1.7f
            1f - (fractionState.value * iconK).coerceIn(0f, 1f)
        }
    }

    val nonEmptyEntries = entries.filter { it.items.isNotEmpty() }
    val hasEntries = nonEmptyEntries.isNotEmpty()
    val actualEnabled = enabled && hasEntries
    val selectedText = resolveSelectedText(nonEmptyEntries)
    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }

    val handleClick = remember(actualEnabled) {
        {
            if (actualEnabled) {
                setExpanded(!isDropdownExpanded.value)
                if (isDropdownExpanded.value) {
                    isHoldDown.value = true
                    currentHapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }
        }
    }

    BasicComponent(
        modifier = modifier,
        interactionSource = interactionSource,
        insideMargin = insideMargin,
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = {
            // 触发区（选中文字 + 箭头图标）实测尺寸 —— 弹窗收起态的起点尺寸。
            // 用 Row 包一层并测量，弹窗从这块内容原位长成面板。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.onSizeChanged { triggerSize = it },
            ) {
                if (selectedText != null) {
                    Text(
                        text = selectedText,
                        fontSize = 14.2.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .graphicsLayer { alpha = triggerAlpha() }
                    )
                }
                DropdownArrowEndAction(
                    actionColor = actionColor,
                    modifier = Modifier.graphicsLayer { alpha = triggerAlpha() }
                )
            }
            if (hasEntries) {
                OverlayDropdownPopup(
                    entries = nonEmptyEntries,
                    show = isDropdownExpanded.value,
                    onDismiss = { setExpanded(false) },
                    onDismissFinished = { isHoldDown.value = false },
                    maxHeight = maxHeight,
                    dropdownColors = dropdownColors,
                    renderInRootScaffold = renderInRootScaffold,
                    collapseOnSelection = collapseOnSelection,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    onFractionProgress = { fractionState.value = it },
                    collapseSize = triggerSize,
                    collapseExtra = collapseExtra,
                )
            }
        },
        bottomAction = bottomAction,
        onClick = handleClick,
        role = Role.DropdownList,
        holdDownState = isHoldDown.value,
        enabled = actualEnabled,
    )
}
