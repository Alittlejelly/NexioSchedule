package com.haooz.chedule.ui.basic

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.interactive.dropdownPanelDragSelect as miuixDropdownPanelDragSelect
import top.yukonga.miuix.kmp.interactive.dropdownPanelDragTransform as miuixDropdownPanelDragTransform
import top.yukonga.miuix.kmp.interactive.dropdownPanelEntry as miuixDropdownPanelEntry

// 跟手滑选实现已搬进 :miuix（miuix/.../interactive/DropdownPanelDragSelect.kt）：
// :miuix 的 ListPopup/Dropdown/OverlayListPopup/LiquidOverlayDropdownPopup 都用它，
// 放 :app 会让 :miuix 反向依赖业务层、无法进 commonMain。本文件改为重导出 ——
// :app 内按 com.haooz.chedule.ui.basic.* 引用的地方一行不改，且两边操作同一批类型（非副本，状态互通）。

typealias DropdownPanelEntry = top.yukonga.miuix.kmp.interactive.DropdownPanelEntry

typealias DropdownPanelDragSelectState =
    top.yukonga.miuix.kmp.interactive.DropdownPanelDragSelectState

typealias DragSelectAxis = top.yukonga.miuix.kmp.interactive.DragSelectAxis

/** 原为 internal；:app 侧需要读取，搬进模块后必须放开。 */
val LocalDropdownPanelDragSelect =
    top.yukonga.miuix.kmp.interactive.LocalDropdownPanelDragSelect

fun Modifier.dropdownPanelDragSelect(
    state: DropdownPanelDragSelectState,
    fraction: () -> Float,
    hapticFeedback: HapticFeedback,
): Modifier = miuixDropdownPanelDragSelect(
    state = state,
    fraction = fraction,
    hapticFeedback = hapticFeedback,
)

fun Modifier.dropdownPanelDragTransform(
    fraction: () -> Float,
    pressProgress: () -> Float,
    dragOffset: () -> Offset,
): Modifier = miuixDropdownPanelDragTransform(
    fraction = fraction,
    pressProgress = pressProgress,
    dragOffset = dragOffset,
)

@androidx.compose.runtime.Composable
fun Modifier.dropdownPanelEntry(
    enabled: Boolean,
    state: DropdownPanelDragSelectState? = null,
    highlightPadding: PaddingValues = PaddingValues(0.dp),
    highlightShape: Shape = RectangleShape,
    selected: Boolean = false,
    action: () -> Unit,
): Modifier = miuixDropdownPanelEntry(
    enabled = enabled,
    state = state,
    highlightPadding = highlightPadding,
    highlightShape = highlightShape,
    selected = selected,
    action = action,
)