/** 本地备份页面 - Screen（2026-10-11 自 :androidApp 迁入 :ui-shared；文件 I/O 走 BackupFileController） */
package com.haooz.chedule.ui.activities

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.data.toJsonMap
import com.haooz.chedule.data.encodeBackupJson
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.TeachingWeekReorganization
import com.haooz.chedule.data.parseBackupPayload
import com.haooz.chedule.data.BackupPayload
import com.haooz.chedule.data.BackupFileInfo
import com.haooz.chedule.data.BackupFileController
import com.haooz.chedule.data.NoopBackupFileController
import com.haooz.chedule.data.formatCompactStamp
import com.haooz.chedule.data.ioDispatcher
import com.haooz.chedule.data.nowLocalDateTime
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import com.haooz.chedule.ui.utils.isAppDarkTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.courseViewModel
import com.haooz.chedule.viewmodel.scheduleViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.time.Duration.Companion.milliseconds
import com.haooz.chedule.data.showToast
import top.yukonga.miuix.kmp.layout.isTabletWidth

// 备份体 / 学期设置 / 恢复门禁 parseBackupPayload、写侧 encodeBackupJson 已迁 :core
// （kotlinx-serialization，原 Gson 走运行时反射、iOS 不存在）—— 见 com.haooz.chedule.data.BackupCodec.kt。
// 备份目录扫描 / SAF 选文件 / 文件读写删已抽成 :core 的 BackupFileController 接口
// （见 data/BackupFileController.kt），Android 实现是 :androidApp 的 AndroidBackupFileController。

/** 课表名可能含路径分隔符/非法字符，直接拼进文件名会写失败或写到意外位置 */
private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "课表" }

/**
 * 备份文件名。原实现 `SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())` ——
 * 该 pattern 只含数字与固定分隔符、不受 Locale 影响，commonMain 用 :core 的
 * [formatCompactStamp] 手写零填充，行为逐字一致。
 */
private fun generateBackupFileName(mode: String, scheduleName: String?): String {
    val timestamp = nowLocalDateTime().formatCompactStamp()
    return if (mode == "all") {
        "全部备份_$timestamp.json"
    } else {
        "${sanitizeFileName(scheduleName ?: "课表")}_$timestamp.json"
    }
}

/**
 * 单课表备份的课程字段序列化 —— 与 `:androidApp` 的 `ScheduleExport.courseToShareMap`
 * **同一份字段清单**（颜色、周次模型保持一致；那份是分享/导出链路的 internal 函数，
 * 仍在 Android 侧，commonMain 不可见，故这里保留一份同构副本；**两边改字段必须同步**）。
 */
private fun courseToShareMap(course: Course): Map<String, Any?> = mapOf(
    "name" to course.name,
    "classroom" to course.classroom,
    "teacher" to course.teacher,
    "dayOfWeek" to course.dayOfWeek,
    "startSection" to course.startSection,
    "endSection" to course.endSection,
    "isCustomTime" to course.isCustomTime,
    "customStartTime" to course.customStartTime,
    "customEndTime" to course.customEndTime,
    "colorRes" to course.colorRes,
) + mapOf(
    "startWeek" to course.startWeek,
    "endWeek" to course.endWeek,
    "weekType" to course.weekType,
    "selectedWeeks" to course.selectedWeeks.sorted(),
)

private fun countFullBackupSchedules(data: Map<String, Any>): Int =
    data.keys.count { it.startsWith("schedule_") && it.endsWith("_courses") }

@Composable
fun LocalBackupScreen(
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    /**
     * 备份文件能力控制器（Android = `AndroidBackupFileController`，构造时捕获 context、
     * 包装目录扫描 / SAF 选文件 / 文件读写删；宿主用
     * `remember { AndroidBackupFileController(context) }` 注入）。
     * 桌面/iOS 没有对应能力，用默认的 [NoopBackupFileController]。
     */
    backupFileController: BackupFileController = NoopBackupFileController,
    // 全量恢复成功后的平台副作用（重排提醒等）。
    // 由 Android 宿主注入：CourseReminderHelper.onHolidayDataChanged(context)。
    // 不直接调 reminder 模块 —— 那是 Android 专有的（内部要 Context，桌面/iOS 无此概念）。
    onHolidayDataChanged: () -> Unit = {},
) {
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var listScrollY by remember { mutableIntStateOf(0) }
    // 液态玻璃效果的透明下拉颜色
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )

    var backupMode by remember { mutableStateOf("all") }
    var selectedSchedule by remember { mutableStateOf("") }

    // 三个 VM 都已去掉 Application（平台副作用走 :core 的 ScheduleEffects 出口），统一走 viewModel() 进 ViewModelStore
    val courseViewModel = courseViewModel()
    val scheduleViewModel = scheduleViewModel()
    val settingsViewModel = settingsViewModel()
    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()

    // 扫描要逐个读 json 解析统计课表数，必须在 IO 线程做；初值留空，由 LaunchedEffect 填充
    var backupHistory by remember { mutableStateOf<List<BackupFileInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restorePayload by remember { mutableStateOf<BackupPayload?>(null) }
    var restoreSourceLabel by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<BackupFileInfo?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var deletingFileName by remember { mutableStateOf<String?>(null) }

    fun refreshHistory() {
        coroutineScope.launch {
            isScanning = true
            val scanned = withContext(ioDispatcher) { backupFileController.listBackups() }
            backupHistory = scanned
            isScanning = false
        }
    }

    /**
     * 先解析校验，再弹确认框。
     * 这样非法文件在弹框前就被拦下，且弹框文案能按备份类型给出准确说明。
     *
     * @param backupId 列表项的备份文件句柄（[BackupFileInfo.id]）
     * @param backupFileName 列表项的文件名（确认框来源文案）
     * @param externalJson SAF 选中的外部文件内容（已由控制器读出）
     */
    fun beginRestore(backupId: String? = null, backupFileName: String? = null, externalJson: String? = null) {
        if (isRestoring || showRestoreDialog) return
        coroutineScope.launch {
            isRestoring = true
            val result = withContext(ioDispatcher) {
                runCatching {
                    val json = when {
                        backupId != null -> backupFileController.readBackupFile(backupId)
                            ?: throw IllegalArgumentException("没有可恢复的文件")
                        externalJson != null -> externalJson
                        else -> throw IllegalArgumentException("没有可恢复的文件")
                    }
                    parseBackupPayload(json)
                }
            }
            isRestoring = false
            result.fold(
                onSuccess = { payload ->
                    restorePayload = payload
                    restoreSourceLabel = backupFileName ?: "外部文件"
                    showRestoreDialog = true
                },
                onFailure = { e ->
                    showToast("无法识别备份文件：${e.message ?: "格式错误"}")
                }
            )
        }
    }

    fun applyRestore() {
        val payload = restorePayload ?: return
        showRestoreDialog = false
        restorePayload = null
        isRestoring = true
        coroutineScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val repository = CourseRepository()
                    when (payload) {
                        is BackupPayload.Single -> {
                            // 重名时自动编号，避免覆盖已有课表
                            val existing = repository.getScheduleNames()
                            var name = payload.scheduleName
                            if (name in existing) {
                                var index = 1
                                while ("$name($index)" in existing) index++
                                name = "$name($index)"
                            }
                            repository.importSingleSchedule(
                                scheduleName = name,
                                coursesData = payload.courses,
                                timeConfigData = payload.timeConfig,
                                classStartTime = payload.semesterSettings?.classStartTime,
                                currentWeek = payload.semesterSettings?.currentWeek,
                                totalWeeks = payload.semesterSettings?.totalWeeks,
                                teachingWeekReorganizations = payload.teachingWeekReorganizations,
                                smartWeekend = payload.semesterSettings?.smartWeekend,
                                showNonCurrentWeek = payload.semesterSettings?.showNonCurrentWeek,
                            )
                        }

                        is BackupPayload.Full -> {
                            repository.importAllPreferences(payload.data)
                            onHolidayDataChanged()
                        }
                    }
                    // 等加载完成再提示成功，否则会先弹 Toast 再刷出数据
                    courseViewModel.reloadCourses().join()
                    scheduleViewModel.refreshScheduleList()
                    settingsViewModel.refreshSettings()
                }
            }
            isRestoring = false
            result.fold(
                onSuccess = {
                    showToast("恢复成功")
                },
                onFailure = { e ->
                    showToast("恢复失败: ${e.message}")
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        refreshHistory()
    }

    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    Scaffold(
        topBar = {}
    ) { paddingValues ->
        val listState = rememberLazyListState()
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemScrollOffset }
                .collect { offset -> listScrollY = offset }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(hapticFeedbackType = HapticFeedbackType.TextHandleMove)
                .collapsibleTopInset(scrollBehavior)
                .then(
                    scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                ),
            contentPadding = PaddingValues(
                start = tabletHorizontalPadding,
                end = tabletHorizontalPadding,
                top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight +
                    (if (isTablet) 24.dp else 12.dp),
                bottom = 120.dp
            ),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val modeEntry = DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = "全部备份",
                                    selected = backupMode == "all",
                                    onClick = { backupMode = "all" }
                                ),
                                DropdownItem(
                                    text = "单独课表",
                                    selected = backupMode == "single",
                                    onClick = { backupMode = "single" }
                                ),
                            )
                        )
                        OverlayDropdownMenu(
                            title = "备份模式",
                            entry = modeEntry,
                            collapseOnSelection = true,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                            dropdownColors = liquidGlassDropdownColors,
                        )

                        if (scheduleNames.isNotEmpty()) {
                            val scheduleEntry = DropdownEntry(
                                items = scheduleNames.map { name ->
                                    DropdownItem(
                                        text = name,
                                        selected = selectedSchedule == name,
                                        onClick = { selectedSchedule = name }
                                    )
                                }
                            )
                            OverlayDropdownMenu(
                                title = "选择课表",
                                summary = if (backupMode == "single") {
                                    if (selectedSchedule.isNotEmpty()) null else "请选择要备份的课表"
                                } else "全部备份模式下无需选择",
                                entry = scheduleEntry,
                                collapseOnSelection = true,
                                enabled = backupMode == "single",
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                dropdownColors = liquidGlassDropdownColors,
                            )
                        } else {
                            Text(
                                text = "暂无课表数据",
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = if (isBackingUp) "备份中..." else "立即备份",
                            summary = if (backupMode == "all") "备份全部课表数据至存储" else "备份「${selectedSchedule.ifEmpty { "未选择" }}」至存储",
                            onClick = {
                                if (isBackingUp) return@ArrowPreference
                                if (backupMode == "single" && selectedSchedule.isEmpty()) {
                                    showToast("请先选择要备份的课表")
                                    return@ArrowPreference
                                }
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                isBackingUp = true
                                coroutineScope.launch {
                                    val result = withContext(ioDispatcher) {
                                        try {
                                            val repository = CourseRepository()

                                            val fileName = generateBackupFileName(backupMode, selectedSchedule)

                                            val data = if (backupMode == "all") {
                                                repository.exportAllPreferences()
                                            } else {
                                                val courses = repository.getCoursesForSchedule(selectedSchedule)
                                                // 获取该课表绑定的时间配置
                                                val configId = repository.getScheduleTimeConfigId(selectedSchedule)
                                                // effective()：顶层记「当天生效那套」的时间，routines 里仍带全部作息，
                                                // 旧版本 App 只读顶层也能拿到正确时间
                                                val timeConfig = repository.getTimeConfig(configId).effective()
                                                // 课程字段与导出/分享共用 courseToShareMap：颜色、周次模型保持一致
                                                mapOf(
                                                    "schedule_name" to selectedSchedule,
                                                    "semester_settings" to mapOf(
                                                        "class_start_time" to repository.getClassStartTime(selectedSchedule),
                                                        "current_week" to repository.getCurrentWeek(selectedSchedule),
                                                        "total_weeks" to repository.getTotalWeeks(selectedSchedule),
                                                        // 与导出 settings 对齐，避免单课表恢复后显示设置丢失
                                                        "smart_weekend" to repository.getSmartWeekend(selectedSchedule),
                                                        "show_non_current_week" to repository.getShowNonCurrentWeek(selectedSchedule),
                                                    ),
                                                    "teaching_week_reorganizations" to TeachingWeekReorganization.toBackupValue(
                                                        repository.getTeachingWeekReorganizations(selectedSchedule),
                                                    ),
                                                    "courses" to courses.map(::courseToShareMap),
                                                    // kotlinx（R2）：TimeConfig → 扁平 Map（25 字段，
                                                    // :core 的 toJsonMap，等价原 Gson 反射展开；写侧再剔 null）
                                                    "time_config" to timeConfig.toJsonMap()
                                                )
                                            }

                                            val json = encodeBackupJson(data)
                                            // 目录选择/可写校验/写盘全在控制器里（Android 包装保留原异常文案）
                                            if (!backupFileController.saveBackupFile(fileName, json)) {
                                                throw IllegalStateException("备份文件写入失败")
                                            }
                                            Result.success(fileName)
                                        } catch (e: Exception) {
                                            Result.failure(e)
                                        }
                                    }
                                    isBackingUp = false
                                    result.fold(
                                        onSuccess = { name ->
                                            showToast("备份成功: $name")
                                            refreshHistory()
                                        },
                                        onFailure = { e ->
                                            showToast("备份失败: ${e.message}")
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // 备份历史
            item(key = "history_title") {
                SmallTitle(
                    text = "备份历史",
                    modifier = Modifier.animateItem()
                )
            }
            if (backupHistory.isEmpty()) {
                item(key = "history_empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = if (isScanning) "正在扫描备份文件..." else "暂无备份记录",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }
            itemsIndexed(backupHistory, key = { _, item -> item.fileName }) { _, info ->
                val isDeleting = deletingFileName == info.fileName
                val cardScale = remember { Animatable(0.8f) }
                val cardAlpha = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    launch { cardScale.animateTo(1f, animationSpec = tween(400)) }
                    launch { cardAlpha.animateTo(1f, animationSpec = tween(400)) }
                }
                LaunchedEffect(isDeleting) {
                    if (isDeleting) {
                        launch { cardScale.animateTo(0.8f, animationSpec = tween(300)) }
                        launch { cardAlpha.animateTo(0f, animationSpec = tween(300)) }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .animateItem()
                        .graphicsLayer {
                            scaleX = cardScale.value
                            scaleY = cardScale.value
                            alpha = cardAlpha.value
                        },
                    insideMargin = PaddingValues(0.dp)
                ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (info.scheduleName != null) "·${info.scheduleName}·" else "共${info.scheduleCount}个课表",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = info.timestamp,
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                }
                                Row {
                                    Box(
                                        modifier = Modifier
                                            .clip(com.kyant.capsule.ContinuousRoundedRectangle(20.dp))
                                            .background(MiuixTheme.colorScheme.primary)
                                            .clickable {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.VirtualKey
                                                )
                                                beginRestore(backupId = info.id, backupFileName = info.fileName)
                                            }
                                            .padding(horizontal = 20.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "恢复",
                                            style = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp),
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(com.kyant.capsule.ContinuousRoundedRectangle(20.dp))
                                            .background(
                                                if (isAppDarkTheme()) Color(0xFF363636)
                                                else Color(0xFFF0F0F0)
                                            )
                                            .clickable {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.VirtualKey
                                                )
                                                pendingDelete = info
                                                showDeleteDialog = true
                                            }
                                            .padding(horizontal = 20.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "删除",
                                            style = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp),
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFF44336)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            item(key = "other_operations") {
                SmallTitle(
                    text = "其他操作",
                    modifier = Modifier.animateItem()
                )
                Card(
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = "手动选择文件",
                            summary = "从其他位置选择备份文件进行恢复",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                // mime 放开（*/*）与合法性把关（parseBackupPayload）的原因见 Android 包装的注释：
                                // 各厂商文件管理器对 .json 上报的 mime 不一致，限定 application/json 会选不到文件。
                                // 取消 / 读取失败由控制器回调 (null, null)（失败已由控制器按原文案 toast）。
                                backupFileController.pickAndReadBackup { json, _ ->
                                    if (json != null) beginRestore(externalJson = json)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    val restorePayloadValue = restorePayload
    val restoreIsFull = restorePayloadValue is BackupPayload.Full
    val restoreSummary = when (restorePayloadValue) {
        is BackupPayload.Full -> {
            val count = countFullBackupSchedules(restorePayloadValue.data)
            "「$restoreSourceLabel」\n" +
                "这是全量备份，包含 $count 个课表。\n" +
                "恢复会先清除现有全部课表与时间配置，再整体覆盖为备份内容，此操作不可撤销。"
        }

        is BackupPayload.Single -> {
            "「$restoreSourceLabel」\n" +
                "这是单课表备份，包含 ${restorePayloadValue.courses.size} 门课程。\n" +
                "将新建课表「${restorePayloadValue.scheduleName}」（重名时自动编号），不影响现有课表。"
        }

        null -> ""
    }
    OverlayDialog(
        title = if (restoreIsFull) "恢复全部备份" else "恢复备份",
        summary = restoreSummary,
        show = showRestoreDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = {
            showRestoreDialog = false
            restorePayload = null
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                        showRestoreDialog = false
                        restorePayload = null
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = if (restoreIsFull) "覆盖恢复" else "确定恢复",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        applyRestore()
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    val deleteDialogInfo = pendingDelete
    OverlayDialog(
        title = "删除备份",
        summary = if (deleteDialogInfo != null) "确定要删除备份「${deleteDialogInfo.fileName}」吗？\n此操作不可恢复" else "",
        show = showDeleteDialog,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = {
            showDeleteDialog = false
            pendingDelete = null
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                        showDeleteDialog = false
                        pendingDelete = null
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "删除",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        // 必须先把目标文件捕获成局部变量：删除动画有 300ms 延迟，
                        // 期间若用户点了另一个文件的删除按钮，pendingDelete 会被覆盖，
                        // 原来的协程就会去删新选中的文件（旧版 bug：删错且原文件没删掉）
                        val target = pendingDelete ?: return@TextButton
                        showDeleteDialog = false
                        pendingDelete = null
                        deletingFileName = target.fileName
                        coroutineScope.launch {
                            try {
                                delay(300.milliseconds)
                                if (backupFileController.deleteBackupFile(target.id)) {
                                    showToast("删除成功")
                                } else {
                                    showToast("删除失败：文件不存在或不可写")
                                }
                                refreshHistory()
                            } catch (e: Exception) {
                                showToast("删除失败: ${e.message}")
                            } finally {
                                // 放在 finally：否则扫描抛异常时该卡片会永久停在 alpha=0
                                deletingFileName = null
                            }
                        }
                    },
                    textColor = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

}
