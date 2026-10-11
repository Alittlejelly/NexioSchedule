package com.haooz.chedule.ui.screens

import com.haooz.chedule.data.minusWeeks

import com.haooz.chedule.data.lengthOfMonth
import com.haooz.chedule.data.minusDays
import com.haooz.chedule.data.todayLocalDate
import com.haooz.chedule.data.weeksBetween
import kotlinx.datetime.isoDayNumber

import android.annotation.SuppressLint
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.haooz.chedule.data.HolidayManager
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.AiImportScreen
import com.haooz.chedule.ui.activities.BackupAndMigrationScreen
import com.haooz.chedule.ui.activities.CourseReminderScreen
import com.haooz.chedule.ui.activities.LocalBackupScreen
import com.haooz.chedule.ui.activities.MainActivity
import com.haooz.chedule.ui.activities.PreferenceSettingsScreen
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.activities.UpdateSettingsScreen
import com.haooz.chedule.ui.utils.AndroidDiagnosticRecorder
import com.haooz.chedule.ui.utils.AndroidReminderController
import com.haooz.chedule.ui.utils.AndroidUpdateController
import com.haooz.chedule.ui.utils.AndroidBackupFileController
import com.haooz.chedule.ui.utils.AndroidScheduleFileController
import com.haooz.chedule.ui.utils.ShareCodeApi
import com.haooz.chedule.ui.utils.ShareCodeFetchResult
import com.haooz.chedule.ui.utils.ThirdPartyShareImporter
import com.haooz.chedule.ui.utils.performScheduleShare
import com.haooz.chedule.wearable.WearableScheduleSync
import com.haooz.chedule.widget.WidgetUpdateCache
import java.net.HttpURLConnection
import java.net.URL
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

@SuppressLint("ConfigurationScreenWidthHeight")
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
    val context = androidx.compose.ui.platform.LocalContext.current
    // 提醒页「测试通知」按钮文案需要超级岛开关（原平板页内联逻辑随迁到宿主注入）
    val islandNotification by settingsViewModel.islandNotification.collectAsState()
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
            androidPane = { dest ->
                when (dest) {
                    TabletSettingsDest.Reminder -> CourseReminderScreen(
                        settingsViewModel = settingsViewModel,
                        scrollBehavior = null,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        // Android 提醒能力（闹钟/电池/权限/设置页跳转）注入
                        reminderController = remember { AndroidReminderController(context) },
                    )

                    TabletSettingsDest.ScheduleImport -> BackupAndMigrationScreen(
                        courseViewModel = viewModel,
                        scheduleViewModel = scheduleViewModel,
                        settingsViewModel = settingsViewModel,
                        scrollBehavior = null,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        mode = ScheduleDataManageMode.Import,
                        compactImport = true,
                        // Android 专有能力注入（同 AppRouteContent 的 BackupAndMigrationRoute）
                        scheduleFileController = remember { AndroidScheduleFileController(context) },
                        onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
                        onFetchShareCode = { code ->
                            ShareCodeApi.fetchShare(code).map {
                                ShareCodeFetchResult(
                                    code = it.code,
                                    scheduleName = it.scheduleName,
                                    scheduleData = it.scheduleData,
                                )
                            }
                        },
                        onImportThirdParty = { source, code ->
                            ThirdPartyShareImporter.import(context, source, code)
                        },
                        onShareSchedule = { name, onSharingChanged ->
                            performScheduleShare(
                                context = context,
                                scope = coroutineScope,
                                scheduleName = name,
                                onSharingChanged = onSharingChanged,
                            )
                        },
                        onExportToWearable = { name ->
                            WearableScheduleSync.exportToWearable(context, name)
                        },
                    )

                    TabletSettingsDest.AiImport -> AiImportScreen(
                        onBack = {},
                        scrollBehavior = null,
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        // 剪贴板是 Android 专有能力，宿主注入给已迁 :ui-shared 的页面
                        onCopyToClipboard = { text ->
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("课表导入提示词", text)
                            clipboard.setPrimaryClip(clip)
                        },
                    )

                    TabletSettingsDest.ScheduleExport -> BackupAndMigrationScreen(
                        courseViewModel = viewModel,
                        scheduleViewModel = scheduleViewModel,
                        settingsViewModel = settingsViewModel,
                        scrollBehavior = null,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        mode = ScheduleDataManageMode.Export,
                        scheduleFileController = remember { AndroidScheduleFileController(context) },
                        onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
                        onFetchShareCode = { code ->
                            ShareCodeApi.fetchShare(code).map {
                                ShareCodeFetchResult(
                                    code = it.code,
                                    scheduleName = it.scheduleName,
                                    scheduleData = it.scheduleData,
                                )
                            }
                        },
                        onImportThirdParty = { source, code ->
                            ThirdPartyShareImporter.import(context, source, code)
                        },
                        onShareSchedule = { name, onSharingChanged ->
                            performScheduleShare(
                                context = context,
                                scope = coroutineScope,
                                scheduleName = name,
                                onSharingChanged = onSharingChanged,
                            )
                        },
                        onExportToWearable = { name ->
                            WearableScheduleSync.exportToWearable(context, name)
                        },
                    )

                    TabletSettingsDest.LocalBackup -> LocalBackupScreen(
                        scrollBehavior = null,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        // Android 文件 I/O（SAF + 备份目录）注入
                        backupFileController = remember { AndroidBackupFileController(context) },
                        onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
                    )

                    TabletSettingsDest.Preference -> PreferenceSettingsScreen(
                        scrollBehavior = null,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        // Android 宿主专有副作用：定位权限 / 天气缓存 / 最近任务卡片
                        hasLocationPermission = {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                        },
                        onInvalidateWeatherCache = { invalidateWeatherCache() },
                        onSetTaskExcludedFromRecents = { hidden ->
                            MainActivity.setTaskExcludedFromRecents(context, hidden)
                        },
                    )

                    TabletSettingsDest.Update -> UpdateSettingsScreen(
                        scrollBehavior = null,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                        // Android 更新能力（检查/下载/APK 安装）注入
                        updateController = remember { AndroidUpdateController(context) },
                    )

                    TabletSettingsDest.About -> {
                        val aboutBackdrop =
                            com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                        // LocalAppRouter.current 是 @Composable 调用，必须在组合层取
                        val appRouter = LocalAppRouter.current
                        // 宿主能力注入：版本号 / 应用名 / 图标（drawable→Bitmap→裁透明边→ImageBitmap）/ 退出 / 诊断录制
                        // （裁边逻辑原在 AboutScreen.cropBitmapToOpaque，随迁移到 ui-shared 已删除，宿主在此复刻）
                        val aboutActivity = LocalActivity.current
                        val aboutPackageInfo = remember {
                            runCatching {
                                context.packageManager.getPackageInfo(context.packageName, 0)
                            }.getOrNull()
                        }
                        val aboutIcon = remember {
                            runCatching { context.packageManager.getApplicationIcon(context.packageName) }
                                .getOrNull()
                                ?.let { drawable ->
                                    val size = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight, 1)
                                    val bitmap = androidx.core.graphics.createBitmap(size, size)
                                    drawable.setBounds(0, 0, size, size)
                                    drawable.draw(android.graphics.Canvas(bitmap))
                                    val pixels = IntArray(size * size)
                                    bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
                                    var minX = size
                                    var minY = size
                                    var maxX = -1
                                    var maxY = -1
                                    for (y in 0 until size) {
                                        for (x in 0 until size) {
                                            if (pixels[y * size + x] ushr 24 != 0) {
                                                if (x < minX) minX = x
                                                if (x > maxX) maxX = x
                                                if (y < minY) minY = y
                                                if (y > maxY) maxY = y
                                            }
                                        }
                                    }
                                    val cropped = if (maxX >= minX && maxY >= minY &&
                                        (maxX - minX + 1 != size || maxY - minY + 1 != size)
                                    ) {
                                        android.graphics.Bitmap.createBitmap(
                                            bitmap, minX, minY, maxX - minX + 1, maxY - minY + 1
                                        )
                                    } else {
                                        bitmap
                                    }
                                    cropped.asImageBitmap()
                                }
                        }
                        AboutScreen(
                            onBack = {},
                            liquidGlassBackdrop = aboutBackdrop,
                            embedded = true,
                            // 弹窗采样全屏层，才能把左栏内容一起虚化
                            dialogBackdrop = liquidGlassBackdrop,
                            // 平板设置页还没路由化（它在 MainActivity 里），
                            // 所以这三个子页仍以「开新宿主 + 指定初始路由」的方式打开 ——
                            // 行为与改造前一致：全屏打开，返回回到这里。
                            onNavigate = { route ->
                                appRouter.navigate(route)
                            },
                            appVersion = remember { aboutPackageInfo?.versionName ?: "未知版本" },
                            appName = remember {
                                aboutPackageInfo?.applicationInfo?.labelRes?.let { context.getString(it) }
                                    ?: context.applicationInfo?.nonLocalizedLabel?.toString()
                                    ?: "Nexio课程表"
                            },
                            appIcon = aboutIcon,
                            onExitApp = { aboutActivity?.finishAffinity() },
                            diagnosticRecorder = remember { AndroidDiagnosticRecorder(context) },
                        )
                    }

                    else -> {}
                }
            },
            // 提醒页底部「测试通知」：原平板页覆盖层 onClick（FeatureLog + 超级岛/实时活动 + toast）逐字迁入
            onSendTestNotification = {
                val islandEnabled = islandNotification &&
                    IslandNotificationHelper.isIslandSupported(context)
                com.haooz.chedule.ui.utils.FeatureLog.reminderFlow(
                    "test_notification",
                    if (islandEnabled) "island" else "live"
                )
                if (islandEnabled) {
                    IslandNotificationHelper.sendTestIslandNotification(
                        context
                    )
                    com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("test_island_sent")
                    showToast("已发送超级岛测试通知")
                } else {
                    CourseReminderHelper.sendTestLiveNotification(
                        context
                    )
                    com.haooz.chedule.ui.utils.FeatureLog.reminderFlow("test_live_sent")
                    showToast("已发送实时活动测试通知")
                }
            },
            isIslandSupported = { IslandNotificationHelper.isIslandSupported(context) },
            onRefreshWidgets = {
                WidgetUpdateCache.clear()
                WidgetUpdateCache.updateInstalledWidgets(context)
            },
            onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
            // 节假日 API 拉取：原平板页内联 HttpURLConnection（10s 超时）+ parseSourceResponse 逐字迁入
            fetchHolidayEntries = { year ->
                val conn = URL(
                    HolidayManager.sourceUrlFor(year)
                ).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                HolidayManager.parseSourceResponse(text)
            },
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
                                ArrowPreference(
                                    title = "更新设置",
                                    onClick = {
                                        FeatureLog.update("open")
                                        appRouter.navigate(AppRoute.UpdateSettings)
                                    }
                                )
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
                            val date = String.format("%04d/%02d/%02d", tempYear, tempMonth, tempDay)
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
