package com.haooz.chedule.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.haooz.chedule.data.parseFullScheduleJson
import com.haooz.chedule.data.parseIcsFile
import com.haooz.chedule.data.showToast
import com.haooz.chedule.ui.screens.applyScheduleData
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

// ⚠ 本文件 2026-10-11 自 :androidApp 迁入 :ui-shared（第二十七批）。
// 迁移动机：整页只碰 Android 三样 —— Intent.ACTION_VIEW/SEND 常量、contentResolver.openInputStream、
// MainActivity 三个分享 Intent 字段；抽成「宿主投递 [SharedFileIntent] + 提供读文本回调」后即纯 UI。
// ⚠ 本文件不应再出现任何 `android.*`；要读别的平台数据就再加注入回调。

/**
 * 宿主投递进来的「外部分享」请求。
 * 宿主负责在 `onNewIntent` / 冷启动里解析 Intent，**只把 ACTION_VIEW / ACTION_SEND 转成本类型**
 * （其它 action 直接丢，页面不再做 action 判断 —— 那需要 `android.content.Intent`）。
 * [version] 单调递增：同一 uri 再次分享时靠它触发重新处理（`LaunchedEffect` 的 key）。
 */
class SharedFileIntent(
    val uri: String,
    val version: Int,
)

/**
 * 分享导入课表的状态与弹窗。
 *
 * 监听宿主投递的 [shareIntent]，解析 JSON / ICS 文件，弹出确认对话框让用户编辑课表名后导入。
 *
 * @param shareIntent 待处理的分享请求；null = 无
 * @param onShareIntentConsumed 已取走该请求（宿主据此清掉自己的 pending 状态）
 * @param readSharedFileText 读该 uri 的全部文本；读失败返回 null（**宿主内部已提示**，
 *   页面不再重复报错）
 */
@Composable
fun ShareImportDialog(
    shareIntent: SharedFileIntent? = null,
    onShareIntentConsumed: () -> Unit = {},
    readSharedFileText: (String) -> String? = { null },
    courseViewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
) {
    var showShareImportDialog by remember { mutableStateOf(false) }
    var shareImportData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var shareImportScheduleName by remember { mutableStateOf("") }

    LaunchedEffect(shareIntent?.version) {
        val payload = shareIntent ?: return@LaunchedEffect
        // 先取走再读：与迁移前一致（原实现在读流之前就 clearShareIntent）
        onShareIntentConsumed()

        val text = readSharedFileText(payload.uri) ?: return@LaunchedEffect
        if (text.isBlank()) return@LaunchedEffect

        try {
            // 与原实现同口径：`shareUri.lastPathSegment` 就是最后一段路径
            val fileName = payload.uri.substringAfterLast('/').lowercase()
            val isIcs = fileName.endsWith(".ics")

            val (success, message, data) = if (isIcs) {
                parseIcsFile(text)
            } else {
                parseFullScheduleJson(text)
            }

            if (success && data != null) {
                val scheduleName =
                    if (isIcs) "ICS导入课表" else (data["schedule_name"] as? String) ?: "导入的课表"
                shareImportData = data
                shareImportScheduleName = scheduleName
                showShareImportDialog = true
            } else {
                showToast(message)
            }
        } catch (e: Exception) {
            showToast("导入失败: ${e.message}")
        }
    }

    OverlayDialog(
        title = "导入课表",
        summary = "是否导入课表「$shareImportScheduleName」？\n确定导入将创建一个新的课表",
        show = showShareImportDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = {
            showShareImportDialog = false
            shareImportData = null
            shareImportScheduleName = ""
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NativeMiuixTextField(
                value = shareImportScheduleName,
                onValueChange = { shareImportScheduleName = it },
                label = "课表名称",
                modifier = Modifier.fillMaxWidth(),
                requestFocus = showShareImportDialog
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        showShareImportDialog = false
                        shareImportData = null
                        shareImportScheduleName = ""
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "确定导入",
                    onClick = {
                        if (shareImportScheduleName.isNotBlank() && shareImportData != null) {
                            // applyScheduleData 已迁 :ui-shared；context 形参从未被函数体使用，去掉
                            val (_, importMessage) = applyScheduleData(
                                viewModel = courseViewModel,
                                scheduleViewModel = scheduleViewModel,
                                settingsViewModel = settingsViewModel,
                                scheduleName = shareImportScheduleName,
                                data = shareImportData!!,
                            )
                            showToast(importMessage, longDuration = true)
                        }
                        showShareImportDialog = false
                        shareImportData = null
                        shareImportScheduleName = ""
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
