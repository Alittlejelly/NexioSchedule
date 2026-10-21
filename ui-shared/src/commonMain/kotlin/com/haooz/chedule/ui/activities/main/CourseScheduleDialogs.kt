package com.haooz.chedule.ui.activities.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.Course
import com.haooz.chedule.viewmodel.CourseViewModel
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 课表页的确认/选择类弹窗：删除课程、粘贴范围、调课冲突。
 * 从 `CourseScheduleApp.kt` 拆出。
 */

@Composable
internal fun DeleteWeekCourseDialog(
    show: Boolean,
    course: Course?,
    week: Int,
    viewModel: CourseViewModel,
    liquidGlassBackdrop: Backdrop?,
    hapticFeedback: HapticFeedback,
    onDismiss: () -> Unit,
) {
    var deleteAllWeeks by remember(show) { mutableStateOf(false) }
    OverlayDialog(
        title = if (deleteAllWeeks) "删除课程" else "删除本周课程",
        summary = if (deleteAllWeeks) {
            "确定要删除「${course?.name}」的全部课程吗？\n此操作不可撤销。"
        } else {
            "确定要删除「${course?.name}」在第${week}周的课程吗？\n此操作不可撤销。"
        },
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    state = if (deleteAllWeeks) ToggleableState.On else ToggleableState.Off,
                    onClick = {
                        deleteAllWeeks = !deleteAllWeeks
                    }
                )
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "删除全部周",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (deleteAllWeeks) "删除该课程的所有周次" else "关闭则仅删除第${week}周",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "删除",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        course?.let {
                            if (deleteAllWeeks) viewModel.deleteCourse(it.id)
                            else viewModel.deleteCourseForWeek(it.id, week)
                        }
                        onDismiss()
                    },
                    textColor = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun PasteRangeDialog(
    show: Boolean,
    courseName: String,
    liquidGlassBackdrop: Backdrop?,
    hapticFeedback: HapticFeedback,
    onPaste: (allWeeks: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        title = "粘贴课程",
        summary = "选择「${courseName}」的粘贴范围",
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                text = "全部周",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onPaste(true)
                },
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = "当前周",
                textColor = MiuixTheme.colorScheme.primary,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onPaste(false)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 冲突处理方式：覆盖 / 交换 */
enum class ConflictResolution { OVERWRITE, SWAP }

/**
 * 调课冲突第一步：只让用户选处理方式。
 *
 * 覆盖/交换**不在这里写库** —— 先关掉本弹窗，等退出动画结束，再由
 * [RescheduleConflictConfirmDialog] 展示具体操作并等用户确认。
 */
@Composable
internal fun RescheduleConflictDialog(
    show: Boolean,
    liquidGlassBackdrop: Backdrop?,
    hapticFeedback: HapticFeedback,
    onCancel: () -> Unit,
    onChooseOverwrite: () -> Unit,
    onChooseSwap: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    OverlayDialog(
        title = "该位置已有课程",
        summary = "请选择课程处理方式",
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onCancel,
        onDismissFinished = onDismissFinished,
    ) {
        // 竖排，一行一个按钮；取消放最后
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = "覆盖",
                textColor = Color(0xFFFF9800),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onChooseOverwrite()
                },
            )
            TextButton(
                text = "交换",
                textColor = MiuixTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onChooseSwap()
                },
            )
            TextButton(
                text = "取消",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onCancel()
                },
            )
        }
    }
}

/**
 * 调课冲突第二步：写明覆盖/交换的具体结果，取消或确定。
 * 确定才真正写库；取消只把浮动卡片送回源周（相邻页锚点），不做任何修改。
 */
@Composable
internal fun RescheduleConflictConfirmDialog(
    show: Boolean,
    resolution: ConflictResolution,
    source: Course?,
    target: Course?,
    /** 目标位数据坐标（星期, 起始节次） */
    dropTarget: Pair<Int, Int>?,
    /** 目标位数据周次：调课日 = followWeek */
    dropTargetWeek: Int,
    draggedWeek: Int,
    viewModel: CourseViewModel,
    liquidGlassBackdrop: Backdrop?,
    hapticFeedback: HapticFeedback,
    onCancel: () -> Unit,
    onOverwriteResolved: () -> Unit,
    onSwapResolved: () -> Unit,
    onOverwrite: (Set<String>) -> Unit,
    onSwap: (Set<String>) -> Unit,
    onDismissFinished: () -> Unit,
) {
    val crossWeek = draggedWeek != dropTargetWeek
    val summary = when {
        source == null || target == null -> "确定要执行此操作吗？"
        resolution == ConflictResolution.OVERWRITE && crossWeek ->
            "将删除「${target.name}」第${dropTargetWeek}周的课程，\n" +
                    "并把「${source.name}」从第${draggedWeek}周调到第${dropTargetWeek}周的此位置。"
        resolution == ConflictResolution.OVERWRITE ->
            "将删除「${target.name}」本周的课程，\n并把「${source.name}」调到此位置。"
        crossWeek ->
            "将把「${source.name}」第${draggedWeek}周与「${target.name}」第${dropTargetWeek}周的两节课互换位置。"
        else ->
            "将把「${source.name}」与「${target.name}」本周的两节课互换位置。"
    }
    OverlayDialog(
        title = if (resolution == ConflictResolution.OVERWRITE) "确认覆盖" else "确认交换",
        summary = summary,
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onCancel,
        onDismissFinished = onDismissFinished,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = "取消",
                modifier = Modifier.weight(1f),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onCancel()
                },
            )
            TextButton(
                text = "确定",
                textColor = if (resolution == ConflictResolution.OVERWRITE) {
                    Color(0xFFFF9800)
                } else {
                    MiuixTheme.colorScheme.primary
                },
                modifier = Modifier.weight(1f),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (resolution == ConflictResolution.OVERWRITE) {
                        if (source != null && dropTarget != null && target != null) {
                            val sectionSpan = source.endSection - source.startSection
                            val targetEnd = dropTarget.second + sectionSpan
                            // 源课在 draggedWeek，目标位在 dropTargetWeek（调课日两者可能不同 → 跨周移动）
                            // 注意 targetWeek 是最后一个参数，别插到中间（全是 Int，错位不报错）
                            viewModel.overwriteCourseForWeek(
                                source.id, draggedWeek,
                                dropTarget.first, dropTarget.second, targetEnd,
                                dropTargetWeek
                            )
                            val sourceCourses = viewModel.getCoursesAtSlot(
                                draggedWeek, source.dayOfWeek, source.startSection, source.endSection
                            ).filter { it.id != source.id && !it.isActiveInWeek(draggedWeek) }
                            if (sourceCourses.isNotEmpty()) {
                                onOverwrite(sourceCourses.map { it.id }.toSet())
                            }
                        }
                        onOverwriteResolved()
                    } else {
                        if (source != null && target != null) {
                            // 源课在 draggedWeek、冲突课在 dropTargetWeek，两侧各拆一周互换
                            viewModel.swapCoursesForWeek(
                                source.id, target.id, draggedWeek, dropTargetWeek
                            )
                            val sourceCourses = viewModel.getCoursesAtSlot(
                                draggedWeek, source.dayOfWeek, source.startSection, source.endSection
                            ).filter {
                                it.id != source.id && it.id != target.id && !it.isActiveInWeek(
                                    draggedWeek
                                )
                            }
                            val conflictCourses = viewModel.getCoursesAtSlot(
                                dropTargetWeek, target.dayOfWeek, target.startSection, target.endSection
                            ).filter {
                                it.id != target.id && it.id != source.id && !it.isActiveInWeek(
                                    dropTargetWeek
                                )
                            }
                            val allAnimated =
                                (sourceCourses + conflictCourses).map { it.id }.toSet()
                            if (allAnimated.isNotEmpty()) {
                                onSwap(allAnimated)
                            }
                        }
                        onSwapResolved()
                    }
                },
            )
        }
    }
}
