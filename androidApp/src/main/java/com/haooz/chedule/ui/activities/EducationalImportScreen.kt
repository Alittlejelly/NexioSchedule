/**
 * 教务系统导入页面（Screen 主体）。
 *
 * 原先整个 UI 内联在 `EducationalImportActivity` 里（673 行）。
 * 2026-10-10 两步走：
 * 1. UI 拆出为 Screen（本文件），Activity 变薄壳；
 * 2. 按「彻底单 Activity」的决定，Activity 彻底删除 ——
 *    本页并入路由系统（`AppRoute.EducationalImport`），宿主为 MainActivity。
 *    返回 = `router.popBack()`，多窗状态 = MainActivity 的 `isInFreeformWindow`，
 *    系统栏 = 宿主全局处理。
 *
 * 仍在 Android 侧的部分
 * （WebView 分支的注入、Toast、Gson 保存）
 * 按用户决定**暂留本文件**，注入细节后置处理。
 */
package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.TimeRoutine
import com.haooz.chedule.data.NexioLog
import com.haooz.chedule.data.getStringOrNull
import com.haooz.chedule.data.school.AdapterData
import com.haooz.chedule.data.school.SchoolData
import com.haooz.chedule.data.school.ScriptRepository
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBarDefaults.CollapsedHeight
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.haooz.chedule.data.showToast
import top.yukonga.miuix.kmp.layout.isTabletWidth
import com.haooz.chedule.ui.utils.screenWidthDp

/**
 * 教务导入页主体：学校选择（tab/搜索/列表）与 WebView 导入两个分支。
 *
 * @param isInFreeformWindow 当前是否处于自由窗口/多窗模式（Activity 的多窗回调下发）
 * @param isUpdating 脚本索引是否正在后台更新（更新按钮转圈）
 * @param dataVersion 数据版本号（+1 触发列表刷新）
 * @param onForceUpdate 点击顶栏「更新」按钮（Activity 侧执行脚本更新并 Toast 结果）
 * @param onFinish 顶栏返回（= Activity.finish()）
 */
@Composable
fun EducationalImportScreen(
    isInFreeformWindow: Boolean,
    isUpdating: Boolean,
    dataVersion: Int,
    onForceUpdate: () -> Unit,
    onFinish: () -> Unit,
) {
    val courseViewModel: CourseViewModel = viewModel()
    val scheduleViewModel: ScheduleViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val scheduleNames by scheduleViewModel.scheduleNames.collectAsState()
    val currentScheduleName by scheduleViewModel.currentScheduleName.collectAsState()

    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val liquidGlassBackdrop: LayerBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val scrollBehavior = rememberSharedScrollBehavior()

    var currentScreen by remember { mutableStateOf("selection") }
    var selectedSchool by remember { mutableStateOf<SchoolData?>(null) }
    var selectedAdapter by remember { mutableStateOf<AdapterData?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // 点击搜索框时收起标题栏
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            scrollBehavior.collapse()
        }
    }

    var isDesktopMode by remember { mutableStateOf(false) }
    var currentAssetJsPath by remember { mutableStateOf<String?>(null) }
    var executeImportAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var toggleDesktopModeAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var webPageTitle by remember { mutableStateOf("加载中...") }
    // 页面顶部取色：按亮度定顶栏标题/图标的黑白，不跟应用深浅色主题
    var webTopColor by remember { mutableStateOf(Color.White) }
    val webTopIsLight = remember(webTopColor) {
        val lum = 0.299f * webTopColor.red + 0.587f * webTopColor.green + 0.114f * webTopColor.blue
        lum >= 0.55f
    }
    val webBarContentColor = if (webTopIsLight) Color.Black else Color.White
    var reloadWebViewAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val webViewScrollBehavior = rememberSharedScrollBehavior()

    when (currentScreen) {
        "selection" -> {
            val isTablet = isTabletWidth()
            val tabletHorizontalPadding = if (isTablet) {
                val widthDp = screenWidthDp()
                ((widthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
            } else 0.dp
            val hapticFeedback = LocalHapticFeedback.current
            val displayTabs = listOf("学校导入", "通用工具")

            Scaffold { paddingValues ->
                val topBarHeightDp = with(LocalDensity.current) {
                    (scrollBehavior.currentHeightPx).toDp()
                }
                val statusBarHeight = WindowInsets.statusBars
                    .asPaddingValues().calculateTopPadding()
                val blurHeight = 80.dp + statusBarHeight + 120.dp

                var searchBackdropAlpha by remember { mutableFloatStateOf(0f) }
                var searchShadowAlpha by remember { mutableFloatStateOf(0f) }

                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(backdrop)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().then(
                                Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                            )
                        ) {
                            com.haooz.chedule.ui.screens.SchoolSelectionScreen(
                                modifier = Modifier.fillMaxSize(),
                                isUpdating = isUpdating,
                                dataVersion = dataVersion,
                                isInFreeformWindow = isInFreeformWindow,
                                scrollBehavior = scrollBehavior,
                                searchQuery = searchQuery,
                                selectedTab = selectedTab,
                                topContentPadding = paddingValues.calculateTopPadding() + topBarHeightDp + 100.dp,
                                liquidGlassBackdrop = liquidGlassBackdrop,
                                onAdapterSelected = { school, adapter ->
                                    selectedSchool = school
                                    selectedAdapter = adapter
                                    currentScreen = "webview"
                                }
                            )
                        }
                    }

                    ProgressiveBlurTopBar(
                        backdrop = liquidGlassBackdrop,
                        height = blurHeight,
                    ) {
                        CollapsibleTopAppBar(
                            title = "选择学校",
                            largeTitle = "选择学校",
                            modifier = Modifier,
                            scrollBehavior = scrollBehavior,
                            contentPadding = {},
                            gradientMaskHeight = CollapsedHeight + 190.dp,
                            onAlphaChanged = { bd, sh ->
                                searchBackdropAlpha = bd
                                searchShadowAlpha = sh
                            },
                            startAction = { backdropAlpha, shadowAlpha ->
                                LiquidTopBarButton(
                                    onClick = onFinish,
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.ChevronBackward,
                                    contentDescription = "返回",
                                    performHapticFeedback = false,
                                    iconSize = 25.dp,
                                    iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                            },
                            endAction = { backdropAlpha, shadowAlpha ->
                                if (isUpdating) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = (-6).dp, y = (-4).dp)
                                            .size(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                } else {
                                    LiquidTopBarButton(
                                        onClick = onForceUpdate,
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Normal.Update,
                                        contentDescription = "更新",
                                        iconSize = 28.dp,
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                }
                            },
                        )
                    }

                    // 迁移前 InputField 内部直接读 isAppDarkTheme()；现在 :miuix 拿不到 App 偏好，
                    // 需显式传入，否则会退回「跟随系统」—— 强制深浅色时底色会与文字对不上。
                    // isAppDarkTheme 是 @Composable，必须在组合期先取好，不能塞进下面那个 lambda。
                    val searchIsLightTheme = !isAppDarkTheme()
                    Column(
                        modifier = Modifier
                            .padding(top = paddingValues.calculateTopPadding() + topBarHeightDp)
                            .fillMaxWidth()
                    ) {
                        SearchBar(
                            modifier = Modifier.padding(
                                top = 8.dp,
                                bottom = 6.dp,
                                start = 6.dp + tabletHorizontalPadding,
                                end = 6.dp + tabletHorizontalPadding
                            ),
                            inputField = {
                                InputField(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    onSearch = { searchExpanded = false },
                                    expanded = searchExpanded,
                                    onExpandedChange = { searchExpanded = it },
                                    label = "搜索学校",
                                    backdrop = liquidGlassBackdrop,
                                    backdropAlpha = searchBackdropAlpha,
                                    shadowAlpha = searchShadowAlpha,
                                    isLightTheme = { searchIsLightTheme },
                                )
                            },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            actionIcon = MiuixIcons.Normal.Close,
                            onActionClick = {
                                searchExpanded = false
                                searchQuery = ""
                            },
                            backdrop = liquidGlassBackdrop,
                            backdropAlpha = searchBackdropAlpha,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 20.dp + tabletHorizontalPadding, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            displayTabs.forEachIndexed { index, tabName ->
                                val isSelected = selectedTab == index
                                Surface(
                                    modifier = Modifier
                                        .clip(ContinuousRoundedRectangle(20.dp))
                                        .clickable {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            selectedTab = index
                                        },
                                    color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 16.dp).height(35.dp).clip(ContinuousCapsule()),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tabName,
                                            fontSize = 14.sp,
                                            color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        "webview" -> {
            val school = selectedSchool
            val adapter = selectedAdapter
            if (school != null && adapter != null) {
                val hapticFeedback = LocalHapticFeedback.current
                val toastContext = LocalContext.current

                Scaffold { paddingValues ->
                    val topBarHeightDp = with(LocalDensity.current) {
                        (webViewScrollBehavior.currentHeightPx).toDp()
                    }
                    val statusBarHeight = WindowInsets.statusBars
                        .asPaddingValues().calculateTopPadding()
                    val blurHeight = statusBarHeight + CollapsedHeight + 40.dp
                    // 顶栏与底部胶囊共用：录的是 WebView Offscreen 层
                    val webContentBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()

                    Box(modifier = Modifier.fillMaxSize()) {
                        com.haooz.chedule.ui.screens.WebViewScreen(
                            school = school,
                            adapterId = adapter.adapterId,
                            importUrl = adapter.importUrl,
                            assetJsPath = adapter.assetJsPath,
                            webContentBackdrop = webContentBackdrop,
                            // 与选择页同构：Scaffold padding + 顶栏高度，内容贴在顶栏下
                            contentTopPadding = (
                                    paddingValues.calculateTopPadding() + topBarHeightDp + 12.dp
                                    ).coerceAtLeast(0.dp),
                            scheduleNames = scheduleNames,
                            currentScheduleName = currentScheduleName,
                            onBack = {
                                selectedSchool = null
                                currentScreen = "selection"
                            },
                            onImportComplete = { courses ->
                                // AndroidBridge 已为导入课程设置 scheduleId（目标课表，空为当前课表）
                                val targetScheduleId =
                                    courses.firstOrNull()?.scheduleId?.takeIf { it.isNotEmpty() }
                                if (targetScheduleId != null && targetScheduleId != currentScheduleName) {
                                    scheduleViewModel.saveCoursesToSchedule(
                                        targetScheduleId,
                                        courses
                                    )
                                } else {
                                    courseViewModel.replaceCourses(courses)
                                }
                                scheduleViewModel.refreshScheduleList()
                                showToast("课程已保存，共 ${courses.size} 门课程")
                            },
                            onTaskCompleted = {
                                val prefs = AppStorage.store("edu_import_prefs")
                                val hasPresetTimeSlots =
                                    prefs.getStringOrNull("preset_time_slots") != null
                                val targetScheduleId = prefs.getStringOrNull("target_schedule_id")

                                if (hasPresetTimeSlots) {
                                    applyPresetTimeSlots(
                                        settingsViewModel,
                                        scheduleViewModel,
                                        targetScheduleId
                                    )
                                }
                                val hasStartDate =
                                    prefs.getStringOrNull("semester_start_date") != null
                                val hasTotalWeeks = prefs.getInt("semester_total_weeks", -1) > 0
                                val hasClassDuration =
                                    prefs.getInt("default_class_duration", -1) > 0
                                val hasBreakDuration =
                                    prefs.getInt("default_break_duration", -1) > 0
                                val hasFirstDayOfWeek = prefs.getInt("first_day_of_week", -1) > 0
                                if (hasStartDate || hasTotalWeeks || hasClassDuration || hasBreakDuration || hasFirstDayOfWeek) {
                                    applyImportedScheduleConfig(courseViewModel, scheduleViewModel)
                                }
                                val hasComboSchedule =
                                    prefs.getStringOrNull("combo_schedule") != null
                                if (hasComboSchedule) {
                                    applyComboSchedule(
                                        settingsViewModel,
                                        courseViewModel,
                                        scheduleViewModel,
                                        targetScheduleId
                                    )
                                }
                            },
                            onPageTitleChanged = { webPageTitle = it },
                            onDesktopModeChanged = { isDesktopMode = it },
                            onTopColorChanged = { webTopColor = it },
                            onAssetJsPathChanged = { currentAssetJsPath = it },
                            onExecuteImportRef = { action -> executeImportAction = action },
                            onToggleDesktopModeRef = { action -> toggleDesktopModeAction = action },
                            onReloadRef = { action -> reloadWebViewAction = action }
                        )

                        ProgressiveBlurTopBar(
                            backdrop = webContentBackdrop,
                            height = blurHeight,
                            // 教务页多为白底：默认 surface 暗色 tint 会在顶上压一层黑罩
                            tintIntensity = 0f,
                        ) {
                            CollapsibleTopAppBar(
                                title = webPageTitle,
                                showLargeTitle = false,
                                // 教务页自带顶栏空白区色条，不需要 AppBar 再叠黑白渐变遮罩
                                showShadow = false,
                                showGradientOverlay = false,
                                titleColor = webBarContentColor,
                                modifier = Modifier,
                                scrollBehavior = webViewScrollBehavior,
                                contentPadding = {},
                                startAction = { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            selectedSchool = null
                                            currentScreen = "selection"
                                        },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Close,
                                        iconTint = webBarContentColor.copy(alpha = 0.85f),
                                        contentDescription = "关闭",
                                        performHapticFeedback = false,
                                        iconSize = 22.dp,
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                },
                                endAction = { backdropAlpha, shadowAlpha ->
                                    LiquidTopBarButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            reloadWebViewAction?.invoke()
                                        },
                                        backdrop = liquidGlassBackdrop,
                                        icon = MiuixIcons.Refresh,
                                        iconTint = webBarContentColor.copy(alpha = 0.85f),
                                        contentDescription = "刷新",
                                        performHapticFeedback = false,
                                        iconSize = 24.dp,
                                        backdropAlpha = backdropAlpha,
                                        shadowAlpha = shadowAlpha,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 应用教务脚本下发的预设时间段（JSON 存在 `edu_import_prefs`，导入完成后消费一次）。
 * 目标课表可能是非当前课表 —— 那时直接走 CourseRepository 写入，不动当前 UI 状态。
 */
private fun applyPresetTimeSlots(
    settingsViewModel: SettingsViewModel,
    scheduleViewModel: ScheduleViewModel,
    targetScheduleId: String?
) {
    val prefs = AppStorage.store("edu_import_prefs")
    val timeSlotsJson = prefs.getStringOrNull("preset_time_slots") ?: return
    try {
        val timeSlots = Gson().fromJson<List<Map<String, Any>>>(
            timeSlotsJson,
            object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
        ) ?: return
        if (timeSlots.isEmpty()) return

        val isTargetCurrent = targetScheduleId.isNullOrEmpty() ||
            targetScheduleId == scheduleViewModel.currentScheduleName.value
        val repository = if (isTargetCurrent) null else CourseRepository()

        val morningSections: Int
        val afternoonSections: Int
        val eveningSections: Int
        if (isTargetCurrent) {
            morningSections = settingsViewModel.morningSections.value
            afternoonSections = settingsViewModel.afternoonSections.value
            eveningSections = settingsViewModel.eveningSections.value
        } else {
            val sid = targetScheduleId
            morningSections = repository!!.getMorningSections(sid)
            afternoonSections = repository.getAfternoonSections(sid)
            eveningSections = repository.getEveningSections(sid)
        }

        val morningTimes = mutableMapOf<Int, String>()
        val afternoonTimes = mutableMapOf<Int, String>()
        val eveningTimes = mutableMapOf<Int, String>()

        for (slot in timeSlots) {
            val number = (slot["number"] as? Number)?.toInt() ?: continue
            val startTime = slot["startTime"] as? String ?: ""
            val endTime = slot["endTime"] as? String ?: ""
            if (startTime.isEmpty() || endTime.isEmpty()) continue
            val timeStr = "$startTime-$endTime"

            when {
                number <= morningSections -> morningTimes[number] = timeStr
                number <= morningSections + afternoonSections -> afternoonTimes[number - morningSections] = timeStr
                else -> eveningTimes[number - morningSections - afternoonSections] = timeStr
            }
        }

        if (isTargetCurrent) {
            if (morningTimes.isNotEmpty()) settingsViewModel.saveMorningTimes(morningTimes)
            if (afternoonTimes.isNotEmpty()) settingsViewModel.saveAfternoonTimes(afternoonTimes)
            if (eveningTimes.isNotEmpty()) settingsViewModel.saveEveningTimes(eveningTimes)
            settingsViewModel.applyTimeImportToCurrentSchedule(
                morningSections, afternoonSections, eveningSections,
                morningTimes, afternoonTimes, eveningTimes
            )
        } else {
            val sid = targetScheduleId
            repository!!.applyTimeImportToSchedule(
                sid, morningSections, afternoonSections, eveningSections,
                morningTimes, afternoonTimes, eveningTimes
            )
        }

        prefs.edit { remove("preset_time_slots") }
    } catch (e: Exception) {
        NexioLog.e("EduImport", "应用预设时间段失败: ${e.message}")
    }
}

/**
 * 应用教务脚本通过 saveCourseConfig 传回的课表配置：
 * - 开学时间、总周数（semesterStartDate / semesterTotalWeeks）；
 * - 默认单节 / 课间时长（defaultClassDuration / defaultBreakDuration → 目标课表 TimeConfig）；
 * - 周起始日（firstDayOfWeek → 导入课程的星期归一化，保证落在 App 固定周一起始的表格上不错位）。
 * 与 applyPresetTimeSlots 一致，仅在导入完成时消费一次性标记，避免重复/旧数据覆盖；
 * 消费后整组清掉，防止残留旧值在下次导入时被误应用。
 * 开学日写入目标课表（可能非当前课表）；日期先规范化，防止被读路径重置成今天。
 */
private fun applyImportedScheduleConfig(
    courseViewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
) {
    val prefs = AppStorage.store("edu_import_prefs")
    val rawStart = prefs.getStringOrNull("semester_start_date")
    val startDate = CourseRepository.normalizeClassStartDate(rawStart)
    val totalWeeks = prefs.getInt("semester_total_weeks", -1)
    val targetScheduleId = prefs.getStringOrNull("target_schedule_id")
    val isTargetCurrent = targetScheduleId.isNullOrEmpty() ||
        targetScheduleId == scheduleViewModel.currentScheduleName.value
    val repository = CourseRepository.getInstance()

    var applied = false
    if (startDate != null) {
        if (isTargetCurrent) {
            courseViewModel.setClassStartTime(startDate)
        } else if (!targetScheduleId.isNullOrEmpty()) {
            // 只写目标课表存储，不 refreshEssentialData：当前 UI/提醒不应被非当前课表改动触发
            repository.setClassStartTime(targetScheduleId, startDate)
        }
        applied = true
        NexioLog.d("EduImport", "应用开学日: raw=$rawStart -> $startDate target=$targetScheduleId")
    } else if (!rawStart.isNullOrBlank()) {
        NexioLog.w("EduImport", "开学日无法解析，已忽略: $rawStart")
    }
    if (totalWeeks > 0) {
        if (isTargetCurrent) {
            courseViewModel.setTotalWeeks(totalWeeks)
        } else if (!targetScheduleId.isNullOrEmpty()) {
            repository.setTotalWeeks(targetScheduleId, totalWeeks)
        }
        applied = true
    }

    // 官方文档 config：defaultClassDuration（默认 45）/ defaultBreakDuration（默认 10）可选，
    // 未提供用默认值。桥接层已写入 prefs，这里落到目标课表 TimeConfig（节数、节次时间、作息方案
    // 原样保留；无绑定配置时新建并绑定）。
    val classDuration = prefs.getInt("default_class_duration", -1)
    val breakDuration = prefs.getInt("default_break_duration", -1)
    if (classDuration > 0 || breakDuration > 0) {
        val targetId = if (isTargetCurrent) repository.getCurrentScheduleId() else targetScheduleId
        if (!targetId.isNullOrEmpty()) {
            repository.applyImportedDefaultDurationsToSchedule(
                scheduleId = targetId,
                classDuration = classDuration.takeIf { it > 0 } ?: 45,
                shortBreak = breakDuration.takeIf { it > 0 } ?: 10,
            )
            applied = true
            NexioLog.d("EduImport", "应用默认节次时长: class=$classDuration break=$breakDuration target=$targetId")
        }
    }

    // 官方文档 config：firstDayOfWeek（1=周一…7=周日，默认 1）描述学校周起始日。
    // App 表格固定周一起始、不随导入改动；学校以其它日为周起始时，其课程 day 编号相对该周起始，
    // 落列前换算回绝对星期（1=周一…7=周日），否则整表错位一天。firstDayOfWeek=1 恒等，跳过。
    val firstDayOfWeek = prefs.getInt("first_day_of_week", 1)
    if (firstDayOfWeek in 2..7) {
        val targetId = if (isTargetCurrent) repository.getCurrentScheduleId() else targetScheduleId
        if (!targetId.isNullOrEmpty()) {
            val courses = repository.getCoursesForSchedule(targetId)
            if (courses.isNotEmpty()) {
                val remapped = courses.map { course ->
                    val day = course.dayOfWeek
                    if (day in 1..7) {
                        val absolute = (day - 1 + (firstDayOfWeek - 1)) % 7 + 1
                        if (absolute != day) course.copy(dayOfWeek = absolute) else course
                    } else {
                        course
                    }
                }
                if (remapped.indices.any { remapped[it].dayOfWeek != courses[it].dayOfWeek }) {
                    if (isTargetCurrent) {
                        courseViewModel.replaceCourses(remapped)
                    } else {
                        scheduleViewModel.saveCoursesToSchedule(targetId, remapped)
                    }
                    NexioLog.d("EduImport", "按周起始日归一化星期: firstDayOfWeek=$firstDayOfWeek target=$targetId")
                }
            }
            applied = true
        }
    }

    // config 是一次性消费标记：导入完成后整组清掉，避免残留旧值在下次导入时被误应用。
    prefs.edit {
        remove("semester_start_date")
        remove("semester_total_weeks")
        remove("default_class_duration")
        remove("default_break_duration")
        remove("first_day_of_week")
        remove("target_schedule_id")
    }
    if (applied) {
        NexioLog.d("EduImport", "应用课表配置成功: 开学时间=$startDate, 总周数=$totalWeeks, target=$targetScheduleId")
    }
}

/**
 * 应用教务脚本通过 saveComboSchedule 传回的组合作息方案（官方文档 4. comboSchedule）。
 * 同一课表在不同日期区间用不同作息（如夏季/冬季）；每个方案以 startDate 为生效起点，
 * 时间槽按官方「差量合并」语义：只覆盖脚本提交的节次，其余节次沿用基础时间段
 * （preset_time_slots / 课表现有节次时间），避免方案切换后未覆盖节次丢时间。
 * 与 applyPresetTimeSlots 一致，仅在导入完成时消费一次性标记。
 */
private fun applyComboSchedule(
    settingsViewModel: SettingsViewModel,
    courseViewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    targetScheduleId: String?,
) {
    val prefs = AppStorage.store("edu_import_prefs")
    val comboJson = prefs.getStringOrNull("combo_schedule") ?: return
    try {
        val combo = Gson().fromJson(comboJson, ComboScheduleImportModel::class.java)
        val comboName = combo?.name.orEmpty()
        val items = combo?.publicSchedules.orEmpty()
        if (items.isEmpty()) {
            prefs.edit { remove("combo_schedule") }
            return
        }

        val isTargetCurrent = targetScheduleId.isNullOrEmpty() ||
            targetScheduleId == scheduleViewModel.currentScheduleName.value
        val repository = CourseRepository.getInstance()
        val targetId = if (isTargetCurrent) repository.getCurrentScheduleId() else targetScheduleId
        if (targetId.isNullOrEmpty()) {
            prefs.edit { remove("combo_schedule") }
            return
        }

        val base = repository.getTimeConfig(repository.getScheduleTimeConfigId(targetId))
        val morning = base.morningSections
        val afternoon = base.afternoonSections
        val evening = base.eveningSections

        var nextId = base.nextRoutineId()
        val routines = items.mapNotNull { item ->
            val start = item.startDate ?: return@mapNotNull null
            val month = start.substringBefore("-").toIntOrNull() ?: return@mapNotNull null
            val day = start.substringAfter("-").substringBefore("-").toIntOrNull() ?: return@mapNotNull null
            if (month !in 1..12 || day !in 1..31) return@mapNotNull null

            // 差量合并：以基础时间段为底，仅覆盖脚本提交的节次
            val merged = base.sectionTimes.toMutableMap()
            for (slot in item.timeSlots) {
                val number = slot.number
                if (number <= 0) continue
                val startTime = slot.startTime
                val endTime = slot.endTime
                if (startTime.isNullOrEmpty() || endTime.isNullOrEmpty()) continue
                val key = when {
                    number <= morning -> "morning_$number"
                    number <= morning + afternoon -> "afternoon_${number - morning}"
                    number <= morning + afternoon + evening -> "evening_${number - morning - afternoon}"
                    else -> continue
                }
                merged[key] = "$startTime-$endTime"
            }

            TimeRoutine(
                id = nextId++,
                name = item.name.ifBlank { comboName.ifBlank { "作息方案" } },
                effectiveMonth = month,
                effectiveDay = TimeRoutine.clampDayOfMonth(month, day),
                // 时间字段以基础配置为准：方案只携带节次时间差量，其余沿用课表当前值
                quickTimeEnabled = base.quickTimeEnabled,
                classDuration = base.classDuration,
                shortBreak = base.shortBreak,
                longBreakEnabled = base.longBreakEnabled,
                longBreakMorning = base.longBreakMorning,
                longBreakAfternoon = base.longBreakAfternoon,
                longBreakEvening = base.longBreakEvening,
                longBreakMorningSection = base.longBreakMorningSection,
                longBreakAfternoonSection = base.longBreakAfternoonSection,
                longBreakEveningSection = base.longBreakEveningSection,
                morningStartHour = base.morningStartHour,
                morningStartMinute = base.morningStartMinute,
                afternoonStartHour = base.afternoonStartHour,
                afternoonStartMinute = base.afternoonStartMinute,
                eveningStartHour = base.eveningStartHour,
                eveningStartMinute = base.eveningStartMinute,
                sectionTimes = merged,
            )
        }
        if (routines.isEmpty()) {
            prefs.edit { remove("combo_schedule") }
            return
        }

        repository.saveTimeConfig(base.copy(routines = routines))
        if (isTargetCurrent) {
            settingsViewModel.refreshSettings()
            courseViewModel.refreshEssentialData()
        }
        prefs.edit { remove("combo_schedule") }
        NexioLog.d("EduImport", "应用组合作息成功: ${routines.size} 个方案 target=$targetId")
    } catch (e: Exception) {
        NexioLog.e("EduImport", "应用组合作息失败: ${e.message}")
    }
}

/** comboSchedule 消费端模型（与 AndroidBridge.ComboScheduleJsonModel 同构） */
private data class ComboScheduleImportModel(
    val name: String = "",
    val publicSchedules: List<ComboScheduleImportItem> = emptyList()
)

private data class ComboScheduleImportItem(
    val name: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val timeSlots: List<ComboTimeSlotImportModel> = emptyList()
)

private data class ComboTimeSlotImportModel(
    val number: Int,
    val startTime: String? = null,
    val endTime: String? = null
)

/**
 * 教务脚本索引的后台更新器（原 `EducationalImportActivity` 的 companion，
 * Activity 删除后随 Screen 迁到这里；逻辑逐字保留）。
 *
 * - [isUpdating] / [dataVersion]：页面组合期收成 State（更新中顶栏转圈；版本 +1 触发列表刷新）
 * - [startUpdateIfNeeded]：进入页面时调用（原 onCreate 时机），距上次成功更新 ≥7 天才动
 * - [forceUpdate]：顶栏「更新」按钮，结果 Toast 给用户
 */
internal object EduImportUpdater {

    private const val TAG = "EduImport"
    private const val PREFS_NAME = "edu_import_prefs"
    private const val KEY_LAST_UPDATE_TIME = "last_update_time"
    private const val AUTO_UPDATE_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L

    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val isUpdating = MutableStateFlow(false)
    internal val dataVersion = MutableStateFlow(0)

    @SuppressLint("UseKtx")
    fun startUpdateIfNeeded() {
        if (isUpdating.value) return

        val prefs = AppStorage.store(PREFS_NAME)
        val lastUpdateTime = prefs.getLong(KEY_LAST_UPDATE_TIME, 0)
        val now = System.currentTimeMillis()
        val needsUpdate = lastUpdateTime == 0L || (now - lastUpdateTime > AUTO_UPDATE_INTERVAL_MS)

        if (!needsUpdate) {
            return
        }

        isUpdating.value = true
        updateScope.launch {
            try {
                ScriptRepository(ScriptRepository.getRepoUrl()).updateAll(
                    onLog = { }
                )
                prefs.edit { putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis()) }
            } catch (e: Exception) {
                NexioLog.e(TAG, "更新失败: ${e.message}")
            } finally {
                isUpdating.value = false
                dataVersion.value++
            }
        }
    }

    fun forceUpdate(context: Context) {
        if (isUpdating.value) return
        isUpdating.value = true
        updateScope.launch {
            try {
                val result = ScriptRepository(ScriptRepository.getRepoUrl()).updateAll(
                    onLog = { }
                )
                val msg = when (result) {
                    0 -> "已是最新版本"
                    1 -> "更新完成"
                    -1 -> "更新失败，请检查网络连接"
                    else -> "更新失败"
                }
                withContext(Dispatchers.Main) {
                    showToast(msg)
                }
                if (result >= 0) {
                    val prefs = AppStorage.store(PREFS_NAME)
                    prefs.edit { putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis()) }
                }
            } catch (e: Exception) {
                NexioLog.e(TAG, "手动更新失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    showToast("更新失败")
                }
            } finally {
                isUpdating.value = false
                dataVersion.value++
            }
        }
    }
}
