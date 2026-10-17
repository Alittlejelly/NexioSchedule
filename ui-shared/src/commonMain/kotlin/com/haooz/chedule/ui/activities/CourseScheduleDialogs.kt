package com.haooz.chedule.ui.activities

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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.Course
import com.haooz.chedule.viewmodel.CourseViewModel
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
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
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
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
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

@Composable
internal fun RescheduleConflictDialog(
    show: Boolean,
    source: Course?,
    target: Course?,
    /** 目标位数据坐标（星期, 起始节次）：调课日列要映射后的星期，几何/动画不走这里 */
    dropTarget: Pair<Int, Int>?,
    /** 目标位数据周次：调课日 = followWeek；交换/覆盖写库按它拆周 */
    dropTargetWeek: Int,
    draggedWeek: Int,
    viewModel: CourseViewModel,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop?,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onCancel: () -> Unit,
    onOverwriteResolved: () -> Unit,
    onSwapResolved: () -> Unit,
    onOverwrite: (Set<String>) -> Unit,
    onSwap: (Set<String>) -> Unit,
) {
    OverlayDialog(
        title = "该位置已有课程",
        summary = if (target != null && source != null && dropTarget != null) {
            "「${source.name}」与「${target.name}」的位置冲突\n" +
                    "覆盖：删除「${target.name}」本周的课程，并把「${source.name}」调到此位置\n" +
                    "交换：互换本周两节课的位置"
        } else {
            "该位置已有课程，要如何处理？"
        },
        show = show,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onCancel
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                text = "覆盖",
                textColor = Color(0xFFFF9800),
                modifier = Modifier.weight(1f),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
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
                },
            )
            TextButton(
                text = "交换",
                textColor = MiuixTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (source != null && target != null) {
                        // 源课在 draggedWeek、冲突课在 dropTargetWeek，两侧各拆一周互换
                        viewModel.swapCoursesForWeek(source.id, target.id, draggedWeek, dropTargetWeek)
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
                        val allAnimated = (sourceCourses + conflictCourses).map { it.id }.toSet()
                        if (allAnimated.isNotEmpty()) {
                            onSwap(allAnimated)
                        }
                    }
                    onSwapResolved()
                },
            )
        }
    }
}
