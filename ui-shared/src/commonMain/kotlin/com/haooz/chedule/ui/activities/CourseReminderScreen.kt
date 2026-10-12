/** 课程提醒设置页面 - Screen */
package com.haooz.chedule.ui.activities

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.NoopReminderController
import com.haooz.chedule.data.ReminderController
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import com.haooz.chedule.ui.basic.OverlayDropdownMenu
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.settingsViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.Color as ComposeColor
import com.haooz.chedule.data.showToast
import top.yukonga.miuix.kmp.layout.isTabletWidth

/** 课程提醒页详细流程日志：仅「开始录制」后写入 */
private fun rlog(event: String, detail: String = "") {
    com.haooz.chedule.ui.utils.FeatureLog.reminderFlow(event, detail)
}

/**
 * lambda 版：未录制时 detail 不求值，用于带补零格式化参数的埋点。
 *
 * ⚠ 不能是 `inline`：FeatureLog.reminderFlow 已改为非 inline（本模块 android 变体 target 与
 * :androidApp 不一致，跨模块 inline 编不过），inline 参数无法继续往下传。
 */
private fun rlog(event: String, detail: () -> String) {
    com.haooz.chedule.ui.utils.FeatureLog.reminderFlow(event, detail)
}

/** 两位补零（原 `%02d`；commonMain 没有字符串格式化器，用 padStart 等价实现） */
private fun pad2(n: Int): String = n.toString().padStart(2, '0')

/** `HH:mm` 两位补零（原 `%02d:%02d`） */
private fun pad2(hour: Int, minute: Int): String = "${pad2(hour)}:${pad2(minute)}"

@Composable
fun CourseReminderScreen(
    settingsViewModel: SettingsViewModel = settingsViewModel(),
    scrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    /** 提醒控制器（Android = AndroidReminderController 包装）；桌面/iOS 用 NoopReminderController */
    reminderController: ReminderController = NoopReminderController,
) {
    val preClassReminder by settingsViewModel.preClassReminder.collectAsState()
    val preClassReminderMinutes by settingsViewModel.preClassReminderMinutes.collectAsState()
    val nextDayReminder by settingsViewModel.nextDayReminder.collectAsState()
    val nextDayReminderHour by settingsViewModel.nextDayReminderHour.collectAsState()
    val nextDayReminderMinute by settingsViewModel.nextDayReminderMinute.collectAsState()
    val islandNotification by settingsViewModel.islandNotification.collectAsState()
    val classDndEnabled by settingsViewModel.classDndEnabled.collectAsState()
    val classDndMode by settingsViewModel.classDndMode.collectAsState()
    val reminderPrefs = remember { AppStorage.store("course_reminder_prefs") }
    var isIgnoringBattery by remember { mutableStateOf(true) }
    val hapticFeedback = LocalHapticFeedback.current

    var showMinutesDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var tempMinutes by remember { mutableIntStateOf(preClassReminderMinutes) }
    var tempHour by remember { mutableIntStateOf(nextDayReminderHour) }
    var tempMinute by remember { mutableIntStateOf(nextDayReminderMinute) }

    var shizukuRunning by remember { mutableStateOf(false) }
    var shizukuAuthorized by remember { mutableStateOf(false) }
    var isIslandSupported by remember { mutableStateOf(false) }

    // 实况通知右侧显示：0=课程名称，1=上课地点，2=倒计时
    var liveRightMode by remember { mutableIntStateOf(reminderPrefs.getInt("live_right_mode", 0)) }
    // 超级岛左侧显示：0=课程名称，1=上课地点，2=倒计时
    var islandLeftMode by remember { mutableIntStateOf(reminderPrefs.getInt("island_left_mode", 0)) }
    // 超级岛右侧显示：0=课程名称，1=上课地点，2=倒计时
    var islandRightMode by remember { mutableIntStateOf(reminderPrefs.getInt("island_right_mode", 1)) }
    // 超级岛息屏显示：0=课程名称，1=上课地点
    var islandAodMode by remember { mutableIntStateOf(reminderPrefs.getInt("island_aod_mode", 0)) }
    // 课中岛缩略态右侧：0=正在上课，1=距下课倒计时（仅超级岛生效）
    var islandInClassRightMode by remember {
        mutableIntStateOf(reminderPrefs.getInt("island_in_class_right_mode", 0))
    }
    // 课中提醒：超级岛 / 原生实况共用同一开关
    var inClassEnabled by remember {
        mutableStateOf(reminderController.isInClassEnabled())
    }
    // 提醒时机：0=全程，1=距下课
    var inClassTimingMode by remember {
        mutableIntStateOf(reminderController.getInClassTimingMode())
    }
    var inClassLeadMinutes by remember {
        mutableIntStateOf(reminderController.getInClassLeadMinutes())
    }
    var showInClassTimingDialog by remember { mutableStateOf(false) }
    var tempInClassTimingMode by remember { mutableIntStateOf(inClassTimingMode) }
    var tempInClassLeadMinutes by remember { mutableIntStateOf(inClassLeadMinutes) }

    val masterEnabled = preClassReminder || nextDayReminder
    // 总开关关闭时联动关掉子开关：勿扰 + 课中提醒（与下节课/次日提醒置 false 同一策略）
    val disableMasterDependentSwitches = {
        settingsViewModel.setClassDndEnabled(false)
        inClassEnabled = false
        reminderPrefs.edit { putBoolean(ReminderController.KEY_IN_CLASS, false) }
    }
    var permissionRefreshKey by remember { mutableIntStateOf(0) }
    // 电池优化 / 自启动设置页返回：重读「已忽略电池优化」并触发权限刷新
    // （原 batteryOptLauncher 与 autoStartLauncher 的回调逐字相同，合并为一个）
    val onBatterySettingsResult: () -> Unit = {
        isIgnoringBattery = reminderController.isIgnoringBatteryOptimizations()
        permissionRefreshKey++
    }
    var canPostPromoted by remember { mutableStateOf(false) }
    var canScheduleExactAlarms by remember { mutableStateOf(true) }
    var dndPermissionGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(false) }
    // 勿扰授权页返回：重读勿扰权限 → 触发权限刷新 → 已授权且总开关开着则重启提醒服务。
    // rememberUpdatedState：回调在从系统设置返回时才触发，保证拿到最新组合的 masterEnabled
    // （对齐原 rememberLauncherForActivityResult 的 rememberUpdatedState 回调语义；
    //  调用点必须传 { onDndPermissionResult() }，让读取发生在结果返回时）
    val onDndPermissionResult by rememberUpdatedState<() -> Unit> {
        dndPermissionGranted = reminderController.isDndPermissionGranted()
        rlog("permission_dnd_result", "granted=$dndPermissionGranted")
        permissionRefreshKey++
        if (dndPermissionGranted && masterEnabled) {
            reminderController.startReminderService()
            rlog("reminder_service", "start_after_dnd_grant")
        }
    }
    // 实况通知设置页返回：重读能否发布实况通知
    val onPromotedSettingsResult: () -> Unit = {
        canPostPromoted = reminderController.canPostPromotedNotifications()
    }
    // 精确闹钟授权页返回：重读精确闹钟权限 → 已授权且总开关开着则重启提醒服务（同上 rememberUpdatedState）
    val onExactAlarmResult by rememberUpdatedState<() -> Unit> {
        canScheduleExactAlarms = reminderController.canScheduleExactAlarms()
        rlog("permission_exact_alarm_result", "granted=$canScheduleExactAlarms")
        if (canScheduleExactAlarms && masterEnabled) {
            reminderController.startReminderService()
            rlog("reminder_service", "start_after_exact_alarm_grant")
        }
    }

    LaunchedEffect(masterEnabled) {
        rlog("page_state_snapshot") {
            "master=$masterEnabled pre=$preClassReminder next=$nextDayReminder " +
                "inClass=$inClassEnabled dnd=$classDndEnabled dndMode=$classDndMode " +
                "island=$islandNotification preMin=$preClassReminderMinutes " +
                "nextTime=${pad2(nextDayReminderHour, nextDayReminderMinute)}"
        }
        if (masterEnabled) {
            isIgnoringBattery = reminderController.isIgnoringBatteryOptimizations()
            canPostPromoted = reminderController.canPostPromotedNotifications()
            canScheduleExactAlarms = reminderController.canScheduleExactAlarms()
            shizukuRunning = reminderController.isShizukuRunning()
            shizukuAuthorized = reminderController.isShizukuAuthorized()
            isIslandSupported = reminderController.isIslandSupported()
            dndPermissionGranted = reminderController.isDndPermissionGranted()
            notificationGranted = reminderController.hasNotificationPermission()
            // 非 HyperOS 设备不支持超级岛，若存在残留开关则自动关闭，避免隐藏入口又残留脏数据
            if (!isIslandSupported && islandNotification) {
                settingsViewModel.setIslandNotification(false)
            }
        }
    }

    LaunchedEffect(permissionRefreshKey) {
        if (permissionRefreshKey > 0) {
            isIgnoringBattery = reminderController.isIgnoringBatteryOptimizations()
            canPostPromoted = reminderController.canPostPromotedNotifications()
            canScheduleExactAlarms = reminderController.canScheduleExactAlarms()
            dndPermissionGranted = reminderController.isDndPermissionGranted()
            notificationGranted = reminderController.hasNotificationPermission()
            // 授权状态变化后重新对账，保证开关与系统勿扰状态一致
            reminderController.startReminderService()
        }
    }

    var pendingPermissionAction by remember { mutableStateOf<Boolean?>(null) }
    // 通知权限弹窗返回（body 只读状态持有者，逐字保留原 launcher 回调逻辑）
    val onNotificationPermissionResult: (Boolean) -> Unit = { granted ->
        rlog("permission_notification_result", "granted=$granted pending=$pendingPermissionAction")
        notificationGranted = granted
        if (granted) {
            reminderController.startReminderService()
            pendingPermissionAction?.let { enable ->
                rlog("permission_notification_apply_pending", "enable=$enable")
                settingsViewModel.setPreClassReminder(enable)
                settingsViewModel.setNextDayReminder(enable)
                if (!enable) {
                    disableMasterDependentSwitches()
                }
                if (enable) {
                    reminderController.startReminderService()
                    rlog("reminder_service", "start_after_notif_grant")
                } else {
                    reminderController.stopReminderService()
                    rlog("reminder_service", "stop_after_notif_grant")
                }
            }
            pendingPermissionAction = null
        } else {
            pendingPermissionAction = null
            rlog("permission_notification_denied")
            showToast("需要通知权限才能使用课程提醒功能")
        }
    }

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val liquidGlassDropdownColors = DropdownDefaults.dropdownColors(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
    )
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {}
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                        .overScrollVertical()
                        .scrollEndHaptic(
                            hapticFeedbackType = HapticFeedbackType.TextHandleMove
                        )
                        .collapsibleTopInset(scrollBehavior)
                        .then(
                            scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
                        ),
                    contentPadding = PaddingValues(
                        start = tabletHorizontalPadding,
                        top = paddingValues.calculateTopPadding() + CollapsibleTopAppBarDefaults.CollapsedHeight +
                            (if (isTablet) 24.dp else 12.dp),
                        end = tabletHorizontalPadding,
                        bottom = 120.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 开启提醒
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            val masterEnabled = preClassReminder || nextDayReminder
                            SwitchPreference(
                                title = "开启提醒",
                                checked = masterEnabled,
                                onCheckedChange = {
                                    val hasPermission = reminderController.hasNotificationPermission()
                                    if (!hasPermission) {
                                        rlog("master_switch_permission_blocked", "want=$it")
                                        pendingPermissionAction = it
                                        reminderController.requestNotificationPermission(onNotificationPermissionResult)
                                        return@SwitchPreference
                                    }
                                    rlog("master_switch", "on=$it")
                                    settingsViewModel.setPreClassReminder(it)
                                    settingsViewModel.setNextDayReminder(it)
                                    if (!it) {
                                        disableMasterDependentSwitches()
                                    }
                                    if (it) {
                                        reminderController.startReminderService()
                                        rlog("reminder_service", "start")
                                    } else {
                                        reminderController.stopReminderService()
                                        rlog("reminder_service", "stop")
                                    }
                                }
                            )
                        }
                    }

                    // 提醒详情
                    item {
                        val masterEnabled = preClassReminder || nextDayReminder
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            SwitchPreference(
                                title = "下节课提醒",
                                summary = "提供下节上课时间、地点等信息，和上课勿扰等操作建议",
                                checked = preClassReminder,
                                enabled = masterEnabled,
                                onCheckedChange = {
                                    val hasPermission = reminderController.hasNotificationPermission()
                                    if (!hasPermission) {
                                        rlog("preclass_permission_blocked", "want=$it")
                                        pendingPermissionAction = it
                                        reminderController.requestNotificationPermission(onNotificationPermissionResult)
                                        return@SwitchPreference
                                    }
                                    rlog("preclass_switch", "on=$it")
                                    settingsViewModel.setPreClassReminder(it)
                                    reminderController.startReminderService()
                                    rlog("reminder_service", "start_after_preclass")
                                }
                            )
                            AnimatedVisibility(
                                visible = preClassReminder,
                                enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
                            ) {
                                ArrowPreference(
                                    title = "提前提醒时间",
                                    endActions = {
                                        Text(
                                            text = "${preClassReminderMinutes}分钟",
                                            fontSize = 14.5.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    holdDownState = showMinutesDialog,
                                    onClick = {
                                        rlog("preclass_minutes_dialog_open", "cur=${preClassReminderMinutes}min")
                                        tempMinutes = preClassReminderMinutes
                                        showMinutesDialog = true
                                    }
                                )
                            }
                            SwitchPreference(
                                title = "次日课程提醒",
                                summary = "提供明天课程、首节时间、地点等信息，和定闹钟等操作建议",
                                checked = nextDayReminder,
                                enabled = masterEnabled,
                                onCheckedChange = {
                                    val hasPermission = reminderController.hasNotificationPermission()
                                    if (!hasPermission) {
                                        rlog("nextday_permission_blocked", "want=$it")
                                        pendingPermissionAction = it
                                        reminderController.requestNotificationPermission(onNotificationPermissionResult)
                                        return@SwitchPreference
                                    }
                                    rlog("nextday_switch", "on=$it")
                                    settingsViewModel.setNextDayReminder(it)
                                    reminderController.startReminderService()
                                    rlog("reminder_service", "start_after_nextday")
                                }
                            )
                            AnimatedVisibility(
                                visible = nextDayReminder,
                                enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
                            ) {
                                ArrowPreference(
                                    title = "提醒时间",
                                    endActions = {
                                        Text(
                                            text = pad2(nextDayReminderHour, nextDayReminderMinute),
                                            fontSize = 14.5.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    holdDownState = showTimeDialog,
                                    onClick = {
                                        rlog("nextday_time_dialog_open") {
                                            "cur=${pad2(nextDayReminderHour, nextDayReminderMinute)}"
                                        }
                                        tempHour = nextDayReminderHour
                                        tempMinute = nextDayReminderMinute
                                        showTimeDialog = true
                                    }
                                )
                            }
                            SwitchPreference(
                                title = "课中提醒",
                                summary = "上课中展示距离下课时间与课程状态",
                                checked = inClassEnabled,
                                enabled = masterEnabled,
                                onCheckedChange = {
                                    rlog("inclass_switch", "on=$it")
                                    inClassEnabled = it
                                    reminderPrefs.edit { putBoolean(ReminderController.KEY_IN_CLASS, it) }
                                }
                            )
                            AnimatedVisibility(
                                visible = masterEnabled && inClassEnabled,
                                enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
                            ) {
                                ArrowPreference(
                                    title = "提醒时机",
                                    endActions = {
                                        Text(
                                            text = if (inClassTimingMode == ReminderController.IN_CLASS_TIMING_FULL) {
                                                "全程"
                                            } else {
                                                "距下课 ${inClassLeadMinutes} 分钟"
                                            },
                                            fontSize = 14.5.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    holdDownState = showInClassTimingDialog,
                                    onClick = {
                                        rlog(
                                            "inclass_timing_dialog_open",
                                            "mode=$inClassTimingMode lead=${inClassLeadMinutes}min"
                                        )
                                        tempInClassTimingMode = inClassTimingMode
                                        tempInClassLeadMinutes = inClassLeadMinutes
                                        showInClassTimingDialog = true
                                    }
                                )
                            }
                            SwitchPreference(
                                title = "自动开启勿扰",
                                summary = if (!dndPermissionGranted) {
                                    "需要先授予勿扰权限才能自动开启勿扰"
                                } else {
                                    "上课时自动开启勿扰，下课后自动恢复"
                                },
                                checked = classDndEnabled,
                                // 仅受总开关约束：无权限时仍可点开，会跳到授权卡片引导
                                enabled = masterEnabled,
                                onCheckedChange = { enable ->
                                    rlog("dnd_switch", "on=$enable perm=$dndPermissionGranted")
                                    settingsViewModel.setClassDndEnabled(enable)
                                    if (enable && !reminderController.isDndPermissionGranted()) {
                                        rlog("dnd_permission_launch")
                                        reminderController.openNotificationAccessSettings { onDndPermissionResult() }
                                    }
                                    reminderController.startReminderService()
                                    rlog("reminder_service", "start_after_dnd")
                                }
                            )
                            // 总开关开启时档位始终可见：通知/超级岛上的「上课勿扰」按钮也能切换勿扰开关
                            // 总开关关闭时整页子项折叠，档位一并隐藏
                            val selectMode: (Int) -> Unit = { mode ->
                                rlog("dnd_mode_select", "mode=$mode")
                                settingsViewModel.setClassDndMode(mode)
                                // 三个档位都要「免打扰访问权限」：静音档走 setRingerMode，
                                // Android N 起同样被判定为切换勿扰，未授权会直接抛异常
                                if (!reminderController.isDndPermissionGranted()) {
                                    rlog("dnd_mode_permission_needed", "mode=$mode")
                                    showToast("该档位需要勿扰权限，已为你打开授权页")
                                    reminderController.openNotificationAccessSettings { onDndPermissionResult() }
                                }
                                reminderController.startReminderService()
                                rlog("reminder_service", "start_after_dnd_mode")
                            }
                            val modeEntry = remember(classDndMode) {
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "完全勿扰 (DND)",
                                            summary = "屏蔽全部通知与铃声",
                                            selected = classDndMode == ReminderController.MODE_DND,
                                            onClick = { selectMode(ReminderController.MODE_DND) }
                                        ),
                                        DropdownItem(
                                            text = "静音模式 (SILENT)",
                                            summary = "关闭铃声与振动",
                                            selected = classDndMode == ReminderController.MODE_SILENT,
                                            onClick = { selectMode(ReminderController.MODE_SILENT) }
                                        ),
                                        DropdownItem(
                                            text = "勿扰模式 (PRIORITY)",
                                            summary = "只放行优先通知",
                                            selected = classDndMode == ReminderController.MODE_PRIORITY,
                                            onClick = { selectMode(ReminderController.MODE_PRIORITY) }
                                        )
                                    )
                                )
                            }
                            AnimatedVisibility(
                                visible = masterEnabled,
                                enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
                            ) {
                                OverlayDropdownMenu(
                                    title = "勿扰模式档位",
                                    entry = modeEntry,
                                    collapseOnSelection = true,
                                    enabled = masterEnabled,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                            }
                        }
                    }

                    // 超级岛设置（仅支持超级岛的设备且开启提醒才渲染，非 HyperOS 完全隐藏不占高度，避免空白区域）
                    if (isIslandSupported && masterEnabled) {
                        item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SwitchPreference(
                                    title = "小米超级岛",
                                    summary = if (islandNotification) "已开启，课程提醒将以超级岛样式显示" else "关闭后使用实时动态通知",
                                    checked = islandNotification,
                                    onCheckedChange = {
                                        rlog("island_switch", "on=$it supported=$isIslandSupported")
                                        settingsViewModel.setIslandNotification(it)
                                    }
                                )
                                AnimatedVisibility(
                                    visible = islandNotification,
                                    enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .padding(bottom = 16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Shizuku 状态",
                                                fontSize = 14.sp,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                text = when {
                                                    !shizukuRunning -> "未运行"
                                                    !shizukuAuthorized -> "未授权"
                                                    else -> "已就绪"
                                                },
                                                fontSize = 14.sp,
                                                color = when {
                                                    !shizukuRunning -> ComposeColor(0xFFFF6B6B)
                                                    !shizukuAuthorized -> ComposeColor(
                                                        0xFFFFB347
                                                    )

                                                    else -> ComposeColor(0xFF4CAF50)
                                                }
                                            )
                                        }
                                        if (!shizukuRunning) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "请安装并启动 Shizuku 应用",
                                                style = MiuixTheme.textStyles.body2,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            )
                                        } else if (!shizukuAuthorized) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            TextButton(
                                                text = "授权 Shizuku",
                                                onClick = {
                                                    rlog("shizuku_authorize_request")
                                                    reminderController.requestShizukuPermission { granted ->
                                                        rlog("shizuku_authorize_result", "granted=$granted")
                                                        shizukuAuthorized = granted
                                                        if (!granted) {
                                                            showToast("Shizuku 授权失败")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }


                    // 缩略态显示设置
                    if (masterEnabled) {
                        item {

                        val liveRightOptions = listOf(
                            DropdownItem(
                                text = "课程名称",
                                selected = liveRightMode == 0,
                                onClick = {
                                    rlog("live_right_mode", "mode=0 课程名称")
                                    liveRightMode = 0
                                    reminderPrefs.edit { putInt("live_right_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = "上课地点",
                                selected = liveRightMode == 1,
                                onClick = {
                                    rlog("live_right_mode", "mode=1 上课地点")
                                    liveRightMode = 1
                                    reminderPrefs.edit { putInt("live_right_mode", 1) }
                                }
                            ),
                            DropdownItem(
                                text = "倒计时",
                                selected = liveRightMode == 2,
                                onClick = {
                                    rlog("live_right_mode", "mode=2 倒计时")
                                    liveRightMode = 2
                                    reminderPrefs.edit { putInt("live_right_mode", 2) }
                                }
                            ),
                        )

                        val islandLeftOptions = listOf(
                            DropdownItem(
                                text = "课程名称",
                                selected = islandLeftMode == 0,
                                onClick = {
                                    rlog("island_left_mode", "mode=0 课程名称")
                                    islandLeftMode = 0
                                    reminderPrefs.edit { putInt("island_left_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = "上课地点",
                                selected = islandLeftMode == 1,
                                onClick = {
                                    rlog("island_left_mode", "mode=1 上课地点")
                                    islandLeftMode = 1
                                    reminderPrefs.edit { putInt("island_left_mode", 1) }
                                }
                            ),
                            DropdownItem(
                                text = "倒计时",
                                selected = islandLeftMode == 2,
                                onClick = {
                                    rlog("island_left_mode", "mode=2 倒计时")
                                    islandLeftMode = 2
                                    reminderPrefs.edit { putInt("island_left_mode", 2) }
                                }
                            ),
                        )

                        val islandRightOptions = listOf(
                            DropdownItem(
                                text = "课程名称",
                                selected = islandRightMode == 0,
                                onClick = {
                                    rlog("island_right_mode", "mode=0 课程名称")
                                    islandRightMode = 0
                                    reminderPrefs.edit { putInt("island_right_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = "上课地点",
                                selected = islandRightMode == 1,
                                onClick = {
                                    rlog("island_right_mode", "mode=1 上课地点")
                                    islandRightMode = 1
                                    reminderPrefs.edit { putInt("island_right_mode", 1) }
                                }
                            ),
                            DropdownItem(
                                text = "倒计时",
                                selected = islandRightMode == 2,
                                onClick = {
                                    rlog("island_right_mode", "mode=2 倒计时")
                                    islandRightMode = 2
                                    reminderPrefs.edit { putInt("island_right_mode", 2) }
                                }
                            ),
                        )

                        val islandAodOptions = listOf(
                            DropdownItem(
                                text = "课程名称",
                                selected = islandAodMode == 0,
                                onClick = {
                                    rlog("island_aod_mode", "mode=0 课程名称")
                                    islandAodMode = 0
                                    reminderPrefs.edit { putInt("island_aod_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = "上课地点",
                                selected = islandAodMode == 1,
                                onClick = {
                                    rlog("island_aod_mode", "mode=1 上课地点")
                                    islandAodMode = 1
                                    reminderPrefs.edit { putInt("island_aod_mode", 1) }
                                }
                            ),
                        )

                        // 课中岛缩略态右侧：与课前岛的左右两侧互不干扰
                        val islandInClassRightOptions = listOf(
                            DropdownItem(
                                text = "正在上课",
                                selected = islandInClassRightMode == 0,
                                onClick = {
                                    rlog("island_inclass_right_mode", "mode=0 正在上课")
                                    islandInClassRightMode = 0
                                    reminderPrefs.edit { putInt("island_in_class_right_mode", 0) }
                                }
                            ),
                            DropdownItem(
                                text = "倒计时",
                                selected = islandInClassRightMode == 1,
                                onClick = {
                                    rlog("island_inclass_right_mode", "mode=1 倒计时")
                                    islandInClassRightMode = 1
                                    reminderPrefs.edit { putInt("island_in_class_right_mode", 1) }
                                }
                            ),
                        )

                        if (!islandNotification || !isIslandSupported) {
                            // 原生实况：只保留右侧缩略内容；课中开关已在上方勿扰卡片
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                            ) {
                                OverlayDropdownMenu(
                                    title = "实时动态右侧",
                                    entry = DropdownEntry(items = liveRightOptions),
                                    collapseOnSelection = true,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                            }
                        } else {
                            // 开启超级岛：显示"超级岛左侧"、"超级岛右侧"和"息屏显示"
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                            ) {
                                OverlayDropdownMenu(
                                    title = "超级岛左侧",
                                    entry = DropdownEntry(items = islandLeftOptions),
                                    collapseOnSelection = true,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                                OverlayDropdownMenu(
                                    title = "超级岛右侧",
                                    entry = DropdownEntry(items = islandRightOptions),
                                    collapseOnSelection = true,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                                // 课中专用：未开课中提醒时整页不出现这一项
                                if (inClassEnabled) {
                                    OverlayDropdownMenu(
                                        title = "课中岛右侧",
                                        entry = DropdownEntry(items = islandInClassRightOptions),
                                        collapseOnSelection = true,
                                        liquidGlassBackdrop = liquidGlassBackdrop,
                                        dropdownColors = liquidGlassDropdownColors,
                                    )
                                }
                                OverlayDropdownMenu(
                                    title = "息屏显示",
                                    summary = "全天候显示时无效",
                                    entry = DropdownEntry(items = islandAodOptions),
                                    collapseOnSelection = true,
                                    liquidGlassBackdrop = liquidGlassBackdrop,
                                    dropdownColors = liquidGlassDropdownColors,
                                )
                            }
                        }
                    }
                    }

                    // 权限设置
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                    title = "通知权限",
                                    summary = "用于接收课程提醒通知",
                                    endActions = {
                                        Text(
                                            text = if (notificationGranted) "已授权" else "未授权",
                                            fontSize = 14.5.sp,
                                            color = if (notificationGranted) {
                                                ComposeColor(0xFF4CAF50)
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantActions
                                            }
                                        )
                                    },
                                    onClick = {
                                        rlog("permission_notification_click", "granted=$notificationGranted")
                                        if (!notificationGranted) {
                                            rlog("permission_notification_launch")
                                            reminderController.requestNotificationPermission(onNotificationPermissionResult)
                                        }
                                    }
                                )
                                if (reminderController.supportsPromotedNotifications) {
                                    ArrowPreference(
                                        title = "实况通知权限",
                                        summary = "在状态栏和锁屏实时显示课程倒计时",
                                        endActions = {
                                            Text(
                                                text = if (canPostPromoted) "已授权" else "未授权",
                                                fontSize = 14.5.sp,
                                                color = if (canPostPromoted) {
                                                    ComposeColor(0xFF4CAF50)
                                                } else {
                                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                                }
                                            )
                                        },
                                        onClick = {
                                            rlog("permission_promoted_click", "granted=$canPostPromoted")
                                            if (!canPostPromoted) {
                                                rlog("permission_promoted_launch")
                                                // 首选「实况通知管理页」、失败退回「应用通知设置」的兜底在 Android 包装层内逐字保留
                                                reminderController.openPromotedNotificationSettings(onPromotedSettingsResult)
                                            }
                                        }
                                    )
                                }
                                ArrowPreference(
                                    title = "精确闹钟权限",
                                    summary = "确保提醒准时触发，不受省电策略影响",
                                    endActions = {
                                        Text(
                                            text = if (canScheduleExactAlarms) "已授权" else "未授权",
                                            fontSize = 14.5.sp,
                                            color = if (canScheduleExactAlarms) {
                                                ComposeColor(0xFF4CAF50)
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantActions
                                            }
                                        )
                                    },
                                    onClick = {
                                        rlog("permission_exact_alarm_click", "granted=$canScheduleExactAlarms")
                                        if (!canScheduleExactAlarms) {
                                            rlog("permission_exact_alarm_launch")
                                            reminderController.openExactAlarmSettings { onExactAlarmResult() }
                                        }
                                    }
                                )
                                ArrowPreference(
                                    title = "电池优化",
                                    summary = "关闭电池优化以确保提醒准时送达",
                                    endActions = {
                                        Text(
                                            text = if (isIgnoringBattery) "已关闭" else "未关闭",
                                            fontSize = 14.5.sp,
                                            color = if (isIgnoringBattery) {
                                                ComposeColor(0xFF4CAF50)
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantActions
                                            }
                                        )
                                    },
                                    onClick = {
                                        rlog("permission_battery_click", "ignoring=$isIgnoringBattery")
                                        if (!isIgnoringBattery) {
                                            rlog("permission_battery_launch")
                                            // 首选「请求忽略电池优化」、失败退回「白名单列表」的兜底在 Android 包装层内逐字保留
                                            reminderController.openBatteryOptimizationSettings(onBatterySettingsResult)
                                        }
                                    }
                                )
                                ArrowPreference(
                                    title = "勿扰权限",
                                    summary = "用于上课时开启勿扰或静音",
                                    endActions = {
                                        Text(
                                            text = if (dndPermissionGranted) "已授权" else "未授权",
                                            fontSize = 14.5.sp,
                                            color = if (dndPermissionGranted) {
                                                ComposeColor(0xFF4CAF50)
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantActions
                                            }
                                        )
                                    },
                                    onClick = {
                                        rlog("permission_dnd_click", "granted=$dndPermissionGranted")
                                        if (!dndPermissionGranted) {
                                            rlog("permission_dnd_launch")
                                            try {
                                                reminderController.openNotificationAccessSettings { onDndPermissionResult() }
                                            } catch (_: Exception) {
                                                showToast("请手动在系统设置中授予勿扰权限")
                                            }
                                        }
                                    }
                                )
                                ArrowPreference(
                                    title = "自启动权限",
                                    summary = "在应用被系统清理后及时重启",
                                    endActions = {
                                        Text(
                                            text = "前往检查",
                                            fontSize = 14.5.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    onClick = {
                                        rlog("permission_autostart_click")
                                        reminderController.openAppDetailsSettings(onBatterySettingsResult)
                                    }
                                )
                            }
                        }
                    }

                }

                // 列表底部留白仍保留，给 Activity 层悬浮的测试按钮让位
            }

            // 课中提醒时机弹窗：左侧全程/距下课，右侧分钟数（全程时禁用）
            OverlayDialog(
                title = "提醒时机",
                show = showInClassTimingDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = { showInClassTimingDialog = false }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(bottom = 16.dp)
                    ) {
                        // 左侧滚轮：全程 / 距下课
                        NumberPicker(
                            value = tempInClassTimingMode,
                            onValueChange = {
                                tempInClassTimingMode = it
                            },
                            range = ReminderController.IN_CLASS_TIMING_FULL..ReminderController.IN_CLASS_TIMING_BEFORE_END,
                            visibleItemCount = 3,
                            itemHeight = 48.dp,
                            label = { mode ->
                                if (mode == ReminderController.IN_CLASS_TIMING_FULL) "全程" else "距下课"
                            },
                            textStyle = MiuixTheme.textStyles.title2,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // 右侧滚轮：距下课分钟数，全程时禁用
                        val minutesEnabled =
                            tempInClassTimingMode == ReminderController.IN_CLASS_TIMING_BEFORE_END
                        NumberPicker(
                            value = tempInClassLeadMinutes,
                            onValueChange = { if (minutesEnabled) tempInClassLeadMinutes = it },
                            range = 0..60,
                            visibleItemCount = 3,
                            itemHeight = 48.dp,
                            label = { "${it}分钟" },
                            textStyle = MiuixTheme.textStyles.title2,
                            wrapAround = true,
                            enabled = minutesEnabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = if (tempInClassTimingMode == ReminderController.IN_CLASS_TIMING_FULL) {
                            "全程显示课中进度"
                        } else {
                            "距下课 ${tempInClassLeadMinutes} 分钟开始显示"
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("inclass_timing_dialog_cancel")
                                showInClassTimingDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "确定",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog(
                                    "inclass_timing_dialog_ok",
                                    "mode=$tempInClassTimingMode lead=${tempInClassLeadMinutes}min"
                                )
                                inClassTimingMode = tempInClassTimingMode
                                inClassLeadMinutes = tempInClassLeadMinutes
                                reminderPrefs.edit {
                                    putInt(ReminderController.KEY_IN_CLASS_TIMING_MODE, tempInClassTimingMode)
                                    putInt(ReminderController.KEY_IN_CLASS_LEAD_MINUTES, tempInClassLeadMinutes)
                                }
                                showInClassTimingDialog = false
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 提前提醒分钟数弹窗
            OverlayDialog(
                title = "提前提醒时间",
                show = showMinutesDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = { showMinutesDialog = false }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NumberPicker(
                        value = tempMinutes,
                        onValueChange = { tempMinutes = it },
                        range = 1..60,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        label = { "${it}分钟" },
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("preclass_minutes_dialog_cancel")
                                showMinutesDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "确定",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("preclass_minutes_dialog_ok", "minutes=$tempMinutes")
                                settingsViewModel.setPreClassReminderMinutes(tempMinutes)
                                showMinutesDialog = false
                                reminderController.startReminderService()
                                rlog("reminder_service", "start_after_preclass_minutes")
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 次日提醒时间弹窗
            OverlayDialog(
                title = "提醒时间",
                show = showTimeDialog,
                liquidGlassBackdrop = liquidGlassBackdrop,
                onDismissRequest = { showTimeDialog = false }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberPicker(
                            value = tempHour,
                            onValueChange = { tempHour = it },
                            range = 0..23,
                            visibleItemCount = 3,
                            itemHeight = 60.dp,
                            label = { pad2(it) },
                            wrapAround = true,
                            modifier = Modifier.weight(1f)
                        )
                        Text(":",
                            style = MiuixTheme.textStyles.title2,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding()
                                .offset(y = (-2).dp))
                        NumberPicker(
                            value = tempMinute,
                            onValueChange = { tempMinute = it },
                            range = 0..59,
                            visibleItemCount = 3,
                            itemHeight = 60.dp,
                            label = { pad2(it) },
                            wrapAround = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("nextday_time_dialog_cancel")
                                showTimeDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "确定",
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                rlog("nextday_time_dialog_ok") {
                                    pad2(tempHour, tempMinute)
                                }
                                settingsViewModel.setNextDayReminderHour(tempHour)
                                settingsViewModel.setNextDayReminderMinute(tempMinute)
                                showTimeDialog = false
                                reminderController.startReminderService()
                                rlog("reminder_service", "start_after_nextday_time")
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
