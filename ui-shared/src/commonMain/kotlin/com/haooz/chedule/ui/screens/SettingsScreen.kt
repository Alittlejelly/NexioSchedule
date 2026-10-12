package com.haooz.chedule.ui.screens

// ⚠ 本页 2026-10-11 自 :androidApp 迁入 :ui-shared（第二十四批）。
//
// 迁移动机与做法：设置页本体（手机端长列表 + 平板两栏外壳）没有任何 Android 依赖，
// 唯一的「平台味」集中在平板分支的 `androidPane` 槽位 —— 8 个 dest 的接线
// （SAF 文件选择 / 口令网络 / 无头 WebView / PackageManager 取图标 / 小部件刷新 /
// 超级岛测试通知 / HttpURLConnection 拉节假日）逐字搬到宿主
// `androidApp/.../ui/screens/AndroidSettingsScreen.kt`，本页只接收并转发这些槽位。
//
// 因此本文件里**不应该再出现任何 `android.*` / `androidx.activity` / `androidx.core`**；
// 加回来之前先想想是不是该做成注入参数。

import com.haooz.chedule.data.minusWeeks

import com.haooz.chedule.data.lengthOfMonth
import com.haooz.chedule.data.minusDays
import com.haooz.chedule.data.todayLocalDate
import com.haooz.chedule.data.weeksBetween
import kotlinx.datetime.isoDayNumber

import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.AiImportScreen
import com.haooz.chedule.ui.activities.BackupAndMigrationScreen
import com.haooz.chedule.ui.activities.CourseReminderScreen
import com.haooz.chedule.ui.activities.LocalBackupScreen
import com.haooz.chedule.ui.activities.PreferenceSettingsScreen
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.activities.UpdateSettingsScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.TeachingWeekReorganization
import com.haooz.chedule.data.TimeConfig
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.LocalAppRouter
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import top.yukonga.miuix.kmp.layout.collapsibleTopInset
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.isAppDarkTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.haooz.chedule.ui.utils.parseShareCourseWeekModel
import com.haooz.chedule.ui.utils.parseShareCourses
import com.haooz.chedule.ui.utils.parseShareSelectedWeeks
import com.haooz.chedule.ui.utils.parseShareSettings
import com.haooz.chedule.ui.utils.parseShareTotalWeeks
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlinx.datetime.LocalDate
import com.haooz.chedule.data.todayLocalDate
import com.haooz.chedule.data.randomUuidV4
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor
import com.haooz.chedule.data.showToast
import top.yukonga.miuix.kmp.layout.isTabletWidth

// 解析失败时回退到今天，避免弹窗初值出现非法日期
internal fun parseDate(dateStr: String): Triple<Int, Int, Int> {
    return try {
        val parts = dateStr.split("/")
        Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    } catch (_: Exception) {
        // kotlinx-datetime（R2 跨平台）：替代 JVM 的 java.util.Calendar
        val d = todayLocalDate()
        Triple(d.year, d.monthNumber, d.dayOfMonth)
    }
}

internal fun getDaysInMonth(year: Int, month: Int): Int {
    return try {
        LocalDate(year, month, 1).lengthOfMonth()
    } catch (_: Exception) {
        31
    }
}

// 顶栏折叠期间会逐帧重组，提到顶层避免组合期每帧新建 Set
// 说明：设置项的「二级页打开时压暗」只在平板布局需要，手机端不再追踪二级 Activity 栈。

@Composable
fun SettingsScreen(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    shiftViewModel: ShiftViewModel,
    isShiftMode: Boolean = false,
    onExitShiftMode: () -> Unit = {},
    onEnterShiftMode: () -> Unit = {},
    navBarStyle: String = "standard",
    onScrollYChanged: (Int) -> Unit = {},
    settingsScrollBehavior: SharedScrollBehavior? = null,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    // ── 平台注入（宿主提供；桌面/iOS 用默认空实现）────────────────────────────
    // 设置页的「平板两栏」外壳 TabletSettingsScreen 有 8 个平台专有 dest
    // （提醒/口令导入/AI 导入/导出/本地备份/偏好/更新/关于），它们的本体早已迁进
    // :ui-shared，但**接线全是 Android 代码**（SAF / OkHttp / WebView / PackageManager /
    // WidgetUpdateCache / 超级岛…）。这些接线留在宿主 —— 见 androidApp 的
    // `AndroidSettingsScreen.kt`，本页只负责把槽位转交下去。
    /** 平台专有设置面板（8 个 dest）由宿主渲染；null 显示占位 */
    androidPane: (@Composable (TabletSettingsDest) -> Unit)? = null,
    /** 提醒页「测试通知」动作（Android 发超级岛/实时活动） */
    onSendTestNotification: () -> Unit = {},
    /** 是否超级岛设备（决定「测试通知」按钮文案） */
    isIslandSupported: () -> Boolean = { false },
    /** 小部件页「刷新已装小部件」（Android = WidgetUpdateCache） */
    onRefreshWidgets: () -> Unit = {},
    /** 节假日数据变更副作用（重排提醒 + 刷小部件） */
    onHolidayDataChanged: () -> Unit = {},
    /** 节假日 API 拉取（Android 宿主实现）；null 时返回 emptyList */
    fetchHolidayEntries: (suspend (year: Int) -> List<com.haooz.chedule.data.HolidayEntry>)? = null,
    /**
     * 是否显示「更新设置」入口。
     */
    showUpdateSettings: Boolean = true,
    /**
     * 是否显示「排班模式」入口（进入开关 + 已进入时的「退出排班模式」按钮 + 确认弹窗）
     * ⚠ 传 false 时 `isShiftMode` 必为 false（iOS 侧永远进不去排班模式），
     *   所以只需挡「进入」那两处；「退出」那处一并挡掉以防状态被外部置起。
     */
) {
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val currentWeek by viewModel.currentWeek.collectAsState()
    val isSemesterStarted by viewModel.isSemesterStarted.collectAsState()
    val classStartTime by viewModel.classStartTime.collectAsState()
    val smartWeekend by settingsViewModel.smartWeekend.collectAsState()
    val showNonCurrentWeek by settingsViewModel.showNonCurrentWeek.collectAsState()
    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()
    // 不可在 forEach 内 collectAsState：remember 槽位会随课表增删错位
    val scheduleSummaries by scheduleViewModel.scheduleSummaries.collectAsState()
    val shiftSelectedSchedules by shiftViewModel.shiftSelectedSchedules.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    // LocalAppRouter.current 是 @Composable 调用，必须在组合层取（各入口共用）
    val settingsRouter = LocalAppRouter.current
    var showShiftModeConfirmDialog by remember { mutableStateOf(false) }
    var showNewSemesterDialog by remember { mutableStateOf(false) }
    var newSemesterName by remember { mutableStateOf("") }

    val (tempYearInit, tempMonthInit, tempDayInit) = remember(classStartTime) {
        parseDate(classStartTime)
    }

    var showCurrentWeekDialog by remember { mutableStateOf(false) }
    var showTotalWeeksDialog by remember { mutableStateOf(false) }
    var showStartDateDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    var tempCurrentWeek by remember { mutableIntStateOf(currentWeek) }
    var tempTotalWeeks by remember { mutableIntStateOf(totalWeeks) }
    var tempYear by remember { mutableIntStateOf(tempYearInit) }
    var tempMonth by remember { mutableIntStateOf(tempMonthInit) }
    var tempDay by remember { mutableIntStateOf(tempDayInit) }

    val backgroundColor = MiuixTheme.colorScheme.surface
    // 平板：设置页左右两栏，原地静态切换，不跳 Activity
    if (navBarStyle == "rail") {
        TabletSettingsScreen(
            viewModel = viewModel,
            scheduleViewModel = scheduleViewModel,
            settingsViewModel = settingsViewModel,
            shiftViewModel = shiftViewModel,
            isShiftMode = isShiftMode,
            onExitShiftMode = onExitShiftMode,
            onEnterShiftMode = onEnterShiftMode,
            liquidGlassBackdrop = liquidGlassBackdrop,
            // —— 8 个平台专有 dest（提醒/口令导入/AI 导入/导出/本地备份/偏好/更新/关于）——
            // 分支体自原平板页 when(selected) 逐字迁入，由宿主 androidPane 槽位渲染
            androidPane = androidPane,
            onSendTestNotification = onSendTestNotification,
            isIslandSupported = isIslandSupported,
            onRefreshWidgets = onRefreshWidgets,
            onHolidayDataChanged = onHolidayDataChanged,
            fetchHolidayEntries = fetchHolidayEntries,
        )
        return
    }
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isDark = isAppDarkTheme()
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) 20.dp else 16.dp

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        Scaffold(
        topBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            val listState = rememberLazyListState()
            // rememberUpdatedState：LaunchedEffect 只依赖 listState，避免持旧闭包
            val currentOnScrollYChanged by rememberUpdatedState(onScrollYChanged)
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemScrollOffset }
                    .collect { offset ->
                        currentOnScrollYChanged(offset)
                    }
            }
            // 折叠高度在布局阶段补齐，避免组合期读 currentHeightPx 导致动画每帧重组
            val scrollBehaviorModifier = remember(settingsScrollBehavior) {
                settingsScrollBehavior?.let {
                    Modifier
                        .collapsibleTopInset(it)
                        .nestedScroll(it.nestedScrollConnection)
                } ?: Modifier
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .then(scrollBehaviorModifier)
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(
                        hapticFeedbackType = HapticFeedbackType.TextHandleMove
                    ),
                contentPadding = PaddingValues(
                    start = tabletHorizontalPadding,
                    top = paddingValues.calculateTopPadding() +
                        CollapsibleTopAppBarDefaults.CollapsedHeight,
                    end = tabletHorizontalPadding,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "basic") {
                    SmallTitle(
                        text = "基本设置",
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp)
                    ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                title = "开始上课日期",
                                endActions = {
                                    Text(
                                        text = classStartTime,
                                        fontSize = 14.5.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = {
                                    FeatureLog.t("设置", "start_date_dialog")
                                    val (y, m, d) = parseDate(classStartTime)
                                    tempYear = y
                                    tempMonth = m
                                    tempDay = d
                                    showStartDateDialog = true
                                },
                                holdDownState = showStartDateDialog
                            )

                            ArrowPreference(
                                title = "当前周数",
                                endActions = {
                                    Text(
                                        text = when {
                                            !isSemesterStarted -> "未开始"
                                            currentWeek > totalWeeks -> "放假中"
                                            else -> "第${currentWeek}周"
                                        },
                                        fontSize = 14.5.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = {
                                    FeatureLog.t("设置", "current_week_dialog")
                                    tempCurrentWeek = currentWeek.coerceAtMost(totalWeeks)
                                    showCurrentWeekDialog = true
                                },
                                holdDownState = showCurrentWeekDialog
                            )

                            ArrowPreference(
                                title = "本学期总周数",
                                endActions = {
                                    Text(
                                        text = "共${totalWeeks}周",
                                        fontSize = 14.5.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = {
                                    FeatureLog.t("设置", "total_weeks_dialog")
                                    tempTotalWeeks = totalWeeks
                                    showTotalWeeksDialog = true
                                },
                                holdDownState = showTotalWeeksDialog
                            )

                            SwitchPreference(
                                title = "智能显示周末",
                                summary = "开启后隐藏无课的周六日",
                                checked = smartWeekend,
                                onCheckedChange = { settingsViewModel.setSmartWeekend(it) }
                            )

                            if (!isShiftMode) {
                                SwitchPreference(
                                    title = "显示非本周课程",
                                    checked = showNonCurrentWeek,
                                    onCheckedChange = { settingsViewModel.setShowNonCurrentWeek(it) }
                                )
                            }

                            ArrowPreference(
                                title = "课表节数与时间",
                                summary = "管理课表的节数与课程时间",
                                onClick = {
                                    FeatureLog.timeConfig("open")
                                    // 已路由化：不再走 Activity。保存/删除作息时 repository 会
                                    // notifyCourseChanged("settings")，CourseViewModel 与
                                    // SettingsViewModel 都会自行刷新，无需 Activity 结果回调
                                    settingsRouter.navigate(AppRoute.CourseTimeSettings)
                                }
                            )
                        }
                    }
                }

                if (!isShiftMode) {
                    item(key = "features") {
                        SmallTitle(
                            text = "特色功能",
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                    title = "课程提醒",
                                    summary = "课前提醒、次日课程提醒",
                                    onClick = {
                                        FeatureLog.reminder("open")
                                        settingsRouter.navigate(AppRoute.CourseReminder)
                                    }
                                )
                                ArrowPreference(
                                    title = "节假日与调休",
                                    onClick = {
                                        FeatureLog.holiday("open")
                                        settingsRouter.navigate(AppRoute.HolidaySettings)
                                    }
                                )
                                ArrowPreference(
                                    title = "桌面小部件",
                                    onClick = {
                                        FeatureLog.widget("open")
                                        settingsRouter.navigate(AppRoute.WidgetIntro)
                                    }
                                )
                                ArrowPreference(
                                    title = "排班模式",
                                    summary = "同时对比多个课表的排班情况",
                                    holdDownState = showShiftModeConfirmDialog,
                                    onClick = { showShiftModeConfirmDialog = true }
                                )
                            }
                        }
                    }
                }

                if (isShiftMode) {
                    item(key = "shift_schedules") {
                        SmallTitle(
                            text = "选择对比课表",
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                scheduleNames.forEach { name ->
                                    CheckboxPreference(
                                        title = name,
                                        summary = scheduleSummaries[name] ?: "",
                                        checked = name in shiftSelectedSchedules,
                                        onCheckedChange = { checked ->
                                            val newList = if (checked) {
                                                shiftSelectedSchedules + name
                                            } else {
                                                shiftSelectedSchedules - name
                                            }
                                            shiftViewModel.setShiftSelectedSchedules(newList)
                                        },
                                        checkboxLocation = CheckboxLocation.End
                                    )
                                }
                            }
                        }
                    }

                    item(key = "shift_exit") {
                        top.yukonga.miuix.kmp.basic.Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, end = tabletHorizontalPadding, start = tabletHorizontalPadding)
                                .height(50.dp),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                onExitShiftMode()
                            },
                            colors = if (isDark) ButtonDefaults.buttonColors(
                                color = Color(0xFF181818)
                            ) else ButtonDefaults.buttonColors(),
                        ) {
                            Text(
                                text = "退出排班模式",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = ComposeColor(0xFFF44336)
                            )
                        }
                    }
                }

                if (!isShiftMode) {
                    item(key = "data_manage") {
                        SmallTitle(
                            text = "数据管理",
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // LocalAppRouter.current 是 @Composable 调用，必须在组合层取
                                val dataManageRouter = LocalAppRouter.current
                                ArrowPreference(
                                    title = "课表导入",
                                    onClick = {
                                        FeatureLog.import("open")
                                        dataManageRouter.navigate(AppRoute.ScheduleImport)
                                    }
                                )
                                ArrowPreference(
                                    title = "课表导出",
                                    onClick = {
                                        FeatureLog.backup("open_export")
                                        dataManageRouter.navigate(AppRoute.ScheduleExport)
                                    }
                                )
                                ArrowPreference(
                                    title = "课表备份",
                                    onClick = {
                                        FeatureLog.backup("open_backup")
                                        dataManageRouter.navigate(AppRoute.ScheduleBackup)
                                    }
                                )
                            }
                        }
                    }
                }

                if (!isShiftMode) {
                    item(key = "others_title") {
                        SmallTitle(
                            text = "其他",
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArrowPreference(
                                    title = "开启新学期",
                                    summary = "复用当前课表设置，创建空课程的新课表",
                                    holdDownState = showNewSemesterDialog,
                                    onClick = {
                                        FeatureLog.t("设置", "new_semester_dialog")
                                        newSemesterName = ""
                                        showNewSemesterDialog = true
                                    }
                                )
                            }
                        }
                    }
                    item(key = "others_prefs") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // LocalAppRouter.current 是 @Composable 调用，必须在组合层取
                                val appRouter = LocalAppRouter.current
                                ArrowPreference(
                                    title = "应用偏好设置",
                                    // 只在偏好设置页仍在栈上时压暗；
                                    // 单独打开更新设置时不应连带压暗本项
                                    onClick = {
                                        FeatureLog.preference("open")
                                        appRouter.navigate(AppRoute.PreferenceSettings)
                                    }
                                )
                                // iOS 走 App Store 更新 → 宿主传 showUpdateSettings=false，入口整个不渲染
                                if (showUpdateSettings) {
                                    ArrowPreference(
                                        title = "更新设置",
                                        onClick = {
                                            FeatureLog.update("open")
                                            appRouter.navigate(AppRoute.UpdateSettings)
                                        }
                                    )
                                }
                                ArrowPreference(
                                    title = "关于应用",
                                    onClick = {
                                        FeatureLog.about("open")
                                        appRouter.navigate(AppRoute.About)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // show=false 时整个弹窗不渲染：iOS 上没有入口能把它打开，
        // 但仍要挡 —— 万一 showShiftModeConfirmDialog 被别的路径置 true，别漏出弹窗。
        OverlayDialog(
            title = "进入排班模式",
            summary = "将切换到排班课表模式，可同时对比多个课表的排班情况。确定进入？",
            show = showShiftModeConfirmDialog,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onDismissRequest = { showShiftModeConfirmDialog = false },

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
                            showShiftModeConfirmDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showShiftModeConfirmDialog = false
                            coroutineScope.launch {
                                delay(100.milliseconds)
                                onEnterShiftMode()
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        OverlayDialog(
            title = "开启新学期",
            summary = "将复用当前课表的所有设置数据，创建一个清空课程的新课表",
            show = showNewSemesterDialog,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onDismissRequest = {
                showNewSemesterDialog = false
                newSemesterName = ""
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NativeMiuixTextField(
                    value = newSemesterName,
                    onValueChange = { newSemesterName = it },
                    label = "新课表名称",
                    modifier = Modifier.fillMaxWidth(),
                    requestFocus = showNewSemesterDialog
                )
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
                            showNewSemesterDialog = false
                            newSemesterName = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "创建",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (newSemesterName.isNotBlank()) {
                                val name = newSemesterName
                                if (name in scheduleViewModel.scheduleNames.value) {
                                    showToast("该课表名称已存在")
                                } else {
                                    scheduleViewModel.createNewSemesterSchedule(name)
                                    showNewSemesterDialog = false
                                    newSemesterName = ""
                                    showToast("「${name}」创建成功")
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        OverlayDialog(
            title = "开始上课日期",
            show = showStartDateDialog,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onDismissRequest = { showStartDateDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val maxDaysInMonth = remember(tempYear, tempMonth) {
                    getDaysInMonth(tempYear, tempMonth)
                }
                LaunchedEffect(maxDaysInMonth) {
                    if (tempDay > maxDaysInMonth) {
                        tempDay = maxDaysInMonth
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberPicker(
                        value = tempYear,
                        onValueChange = { tempYear = it },
                        range = 2024..2030,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f)
                    )
                    NumberPicker(
                        value = tempMonth,
                        onValueChange = { tempMonth = it },
                        range = 1..12,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        label = { "${it}月" },
                        wrapAround = true,
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f)
                    )
                    NumberPicker(
                        value = tempDay,
                        onValueChange = { tempDay = it },
                        range = 1..maxDaysInMonth,
                        visibleItemCount = 3,
                        itemHeight = 60.dp,
                        label = { "${it}日" },
                        wrapAround = true,
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showStartDateDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            // ⚠ 原实现是 `String.format("%04d/%02d/%02d", …)` —— JVM 专有。
                            // 本文件迁进 commonMain 后才被 checkKmpPurity + iOS 编译同时抓到
                            // （在 :androidApp 里编了两年都没事，因为 Android 就是 JVM）。
                            // 手写 padStart，格式逐字一致，且**不会像 LocalDate(...) 那样
                            // 在月/日组合非法时抛异常**（tempDay 的 range 跟着月份变，
                            // 换月瞬间可能短暂越界）。
                            val date = "${tempYear.toString().padStart(4, '0')}/" +
                                "${tempMonth.toString().padStart(2, '0')}/" +
                                tempDay.toString().padStart(2, '0')
                            viewModel.setClassStartTime(date)
                            showStartDateDialog = false
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        OverlayDialog(
            title = "选择当前周次",
            show = showCurrentWeekDialog,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onDismissRequest = { showCurrentWeekDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NumberPicker(
                    value = tempCurrentWeek,
                    onValueChange = { tempCurrentWeek = it },
                    range = 1..totalWeeks,
                    visibleItemCount = 3,
                    itemHeight = 60.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp, bottom = 20.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showCurrentWeekDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.setCurrentWeek(tempCurrentWeek)
                            showCurrentWeekDialog = false
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        OverlayDialog(
            title = "选择学期总周数",
            show = showTotalWeeksDialog,
            liquidGlassBackdrop = liquidGlassBackdrop,
            onDismissRequest = { showTotalWeeksDialog = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NumberPicker(
                    value = tempTotalWeeks,
                    onValueChange = { tempTotalWeeks = it },
                    range = 1..30,
                    visibleItemCount = 3,
                    itemHeight = 60.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp, bottom = 20.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            showTotalWeeksDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.setTotalWeeks(tempTotalWeeks)
                            showTotalWeeksDialog = false
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


// 「导入课表」JSON 解析（parseFullScheduleJson / 拾光格式）已迁 :core（kotlinx-serialization，
// 原 Gson 走运行时反射、iOS 不存在）—— 见 com.haooz.chedule.data.ScheduleImportCodec.kt。
//
// ICS 解析（parseIcsFile）2026-10-11 也迁 :core —— 见 com.haooz.chedule.data.IcsCodec.kt。
// 落库逻辑（applyScheduleData）随「课表导入/导出」页迁 :ui-shared ——
// 见 com.haooz.chedule.ui.screens.ScheduleImportApply.kt。
