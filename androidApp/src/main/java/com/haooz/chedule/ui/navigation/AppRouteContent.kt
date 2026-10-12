package com.haooz.chedule.ui.navigation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.TimeConfig
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import com.haooz.chedule.ui.activities.AboutScreen
import com.haooz.chedule.ui.activities.BackupAndMigrationScreen
import com.haooz.chedule.ui.activities.CourseManageScreen
import com.haooz.chedule.ui.activities.CourseTimeSettingsScreen
import com.haooz.chedule.ui.activities.MainActivity
import com.haooz.chedule.ui.activities.TimeConfigCardBounds
import com.haooz.chedule.ui.activities.ScheduleDataManageMode
import com.haooz.chedule.ui.basic.ShortcutMenu
import com.haooz.chedule.ui.basic.ShortcutMenuItem
import com.haooz.chedule.ui.components.DocumentPageScaffold
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.screens.CourseEditScreen
import com.haooz.chedule.ui.activities.EduImportUpdater
import com.haooz.chedule.ui.web.rememberAndroidWebHost
import com.haooz.chedule.ui.screens.TimeConfigEditScreen
import com.haooz.chedule.ui.screens.invalidateWeatherCache
import com.haooz.chedule.ui.utils.ApiCompat
import com.haooz.chedule.ui.utils.AndroidDiagnosticRecorder
import com.haooz.chedule.ui.utils.AndroidReminderController
import com.haooz.chedule.ui.utils.AndroidBackupFileController
import com.haooz.chedule.ui.utils.AndroidScheduleFileController
import com.haooz.chedule.ui.utils.AndroidUpdateController
import com.haooz.chedule.ui.utils.FeatureLog
import com.haooz.chedule.ui.utils.ShareCodeApi
import com.haooz.chedule.ui.utils.ShareCodeFetchResult
import com.haooz.chedule.ui.utils.ThirdPartyShareImporter
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.performScheduleShare
import com.haooz.chedule.wearable.WearableScheduleSync
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds
import com.haooz.chedule.data.showToast

/**
 * 子页路由 → 页面的映射表。
 *
 * 这是**唯一**需要知道「哪个路由对应哪个 Screen」的地方 —— 宿主只负责创建
 * [AppRouter]、渲染 [AppNavHost]，不关心具体有哪些页面。
 *
 * ## 迁移注意（阶段 5+，别照旧注释理解）
 *
 * 「路由 → composable」的映射形状是可搬的，但**这个文件当前不能整体搬进
 * `:ui-shared`**：里面还压着一批 Android 专有触点，整体搬会直接编不过。
 * 搬家前先把下列触点上收（注入或拆进 androidMain 宿主），让本文件只剩
 * 纯映射 + DocumentPageScaffold：
 *  - `isInFreeformWindow()` 的 `LocalActivity.current as? MainActivity` 强转；
 *  - CourseManageRoute / CourseTimeSettingsRoute 持有的 Bitmap 卡片快照 + ApiCompat 模糊；
 *  - CourseReminderHelper / IslandNotificationHelper 等平台副作用调用。
 * （~~HolidaySettings 的 HttpURLConnection~~ 已随第三十五批迁出 —— 换成 :core 的 HttpService。）
 *
 * 已迁走的页面本体在 `:ui-shared` 的 `com.haooz.chedule.ui.activities/screens`，
 * 未迁的在 androidApp 同包下 —— 两模块**共享包名**，靠「无同名顶层符号」维持，
 * 新增页面时先确认另一侧没有同名文件。以两侧源码树实际清单为准，本注释不做枚举。
 */
@Composable
fun AppRouteContent(
    router: AppRouter,
    route: AppRoute,
) {
    when (route) {
        AppRoute.About -> AndroidAboutRoute(router)

        // 纯文档壳路由已迁 :ui-shared（第三十三批），直接调跨平台版
        AppRoute.Changelog -> ChangelogRoute(router)
        AppRoute.License -> LicenseRoute(router)
        AppRoute.PrivacyPolicy -> PrivacyPolicyRoute(router)
        AppRoute.Communication -> CommunicationRoute(router)
        AppRoute.AppreciateAuthor -> AppreciateAuthorRoute(router)

        // 带注入的文档壳路由已迁 :ui-shared（第三十四批），Android 注入平台能力。
        // ⚠ context/activity 必须在组合期取好再捕获 —— `LocalContext.current` 是
        // @Composable getter，写进非组合 lambda 会编不过。
        AppRoute.PreferenceSettings -> {
            val context = LocalContext.current
            val activity = LocalActivity.current
            PreferenceSettingsRoute(
                router = router,
                hasLocationPermission = {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                },
                onInvalidateWeatherCache = { invalidateWeatherCache() },
                onSetTaskExcludedFromRecents = { hidden ->
                    MainActivity.setTaskExcludedFromRecents(context, hidden)
                },
                // 偏好设置页改主题 → 系统栏跟着刷新（原来在 Activity 的 setContent 里）
                onThemeDarkChanged = { activity?.applyThemeAwareSystemBars() },
            )
        }

        AppRoute.UpdateSettings -> {
            val context = LocalContext.current
            UpdateSettingsRoute(
                router = router,
                updateController = remember { AndroidUpdateController(context) },
            )
        }

        AppRoute.LocalBackup -> {
            val context = LocalContext.current
            LocalBackupRoute(
                router = router,
                backupFileController = remember { AndroidBackupFileController(context) },
                onHolidayDataChanged = {
                    CourseReminderHelper.onHolidayDataChanged(context)
                },
            )
        }

        AppRoute.AiImport -> {
            val context = LocalContext.current
            AiImportRoute(
                router = router,
                onCopyToClipboard = { text ->
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("课表导入提示词", text)
                    clipboard.setPrimaryClip(clip)
                },
            )
        }

        // ── 数据管理（导入 / 导出 / 备份，共用 BackupAndMigrationScreen，按 mode 区分）──
        AppRoute.ScheduleImport -> AndroidBackupAndMigrationRoute(router, ScheduleDataManageMode.Import)
        AppRoute.ScheduleExport -> AndroidBackupAndMigrationRoute(router, ScheduleDataManageMode.Export)
        AppRoute.ScheduleBackup -> AndroidBackupAndMigrationRoute(router, ScheduleDataManageMode.Backup)

        // ── 带额外元素的页面（顶栏按钮 / 底部悬浮按钮 / 弹窗）──
        // 路由壳已迁 :ui-shared（第三十六批），Android 只注入平台能力。
        AppRoute.WidgetIntro -> {
            val context = LocalContext.current
            WidgetIntroRoute(
                router = router,
                onRequestWidgetRefresh = {
                    com.haooz.chedule.widget.WidgetUpdateCache.clear()
                    com.haooz.chedule.widget.WidgetUpdateCache.updateInstalledWidgets(context)
                },
            )
        }
        AppRoute.CourseReminder -> {
            val context = LocalContext.current
            // 开关值必须跟随 ViewModel，否则 remember 无 key 会把初次求值的结果钉死，
            // 切换开关后按钮文案不会更新；是否支持超级岛是设备静态能力，缓存一次即可
            val settingsViewModel: SettingsViewModel = viewModel()
            val islandNotification by settingsViewModel.islandNotification.collectAsState()
            val islandSupported = remember { IslandNotificationHelper.isIslandSupported(context) }
            val islandEnabled = islandNotification && islandSupported
            CourseReminderRoute(
                router = router,
                reminderController = remember { AndroidReminderController(context) },
                testActionLabel = if (islandEnabled) "测试小米超级岛" else "测试实时活动",
                onSendTestNotification = {
                    FeatureLog.reminderFlow(
                        "test_notification",
                        if (islandEnabled) "island" else "live"
                    )
                    if (islandEnabled) {
                        IslandNotificationHelper.sendTestIslandNotification(context)
                        FeatureLog.reminderFlow("test_island_sent")
                        showToast("已发送超级岛测试通知")
                    } else {
                        CourseReminderHelper.sendTestLiveNotification(context)
                        FeatureLog.reminderFlow("test_live_sent")
                        showToast("已发送实时活动测试通知")
                    }
                },
            )
        }
        AppRoute.WebDavSettings -> {
            val context = LocalContext.current
            WebDavSettingsRoute(
                router = router,
                onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
            )
        }
        AppRoute.HolidaySettings -> {
            val context = LocalContext.current
            // 节假日数据变更是 Android 专有的平台副作用（提醒 / 勿扰 / 小组件），由宿主注入
            HolidaySettingsRoute(
                router = router,
                onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
            )
        }
        AppRoute.CourseTimeSettings -> CourseTimeSettingsRoute(router)
        AppRoute.CourseManage -> CourseManageRoute(router)
        AppRoute.EducationalImport -> AndroidEducationalImportRoute(router)
    }
}

/**
 * 课表导入 / 导出 / 备份。
 *
 * ⚠ 这三个入口**必须**走路由，不能再是 Activity：原来 `BackupAndMigrationActivity`
 * 是独立 Activity，`BackupAndMigrationScreen` 里的「本地备份」入口读 [LocalAppRouter]，
 * 而那个 Activity 的 setContent 没有 provide，默认值是 `error(...)` —— 一点开就崩。
 */
/**
 * 课表导入 / 导出 / 备份的 **Android 包装**（第三十七批）。
 *
 * 路由壳已迁 `:ui-shared` 的 [BackupAndMigrationRoute]，这里只剩四条 Android 专有能力
 * 的装配：文件选择器 / 提醒重排 / 口令取回与抓取 / 口令分享出图 / 手环推送。
 * 三者共用同一份装配，只是 `mode` 不同。
 */
@Composable
private fun AndroidBackupAndMigrationRoute(router: AppRouter, mode: ScheduleDataManageMode) {
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    BackupAndMigrationRoute(
        router = router,
        mode = mode,
        // 导入选文件 / 导出另存为（SAF）
        scheduleFileController = remember { AndroidScheduleFileController(context) },
        // WebDAV 恢复后的提醒重排
        onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
        // 分享口令取回（OkHttp + Gson）
        onFetchShareCode = { code ->
            ShareCodeApi.fetchShare(code).map {
                ShareCodeFetchResult(
                    code = it.code,
                    scheduleName = it.scheduleName,
                    scheduleData = it.scheduleData,
                )
            }
        },
        // 第三方口令抓取（无头 WebView + OkHttp + Gson）
        onImportThirdParty = { source, code ->
            ThirdPartyShareImporter.import(context, source, code)
        },
        // 口令分享（上传 + 出图 + 系统分享）
        onShareSchedule = { name, onSharingChanged ->
            performScheduleShare(
                context = context,
                scope = shareScope,
                scheduleName = name,
                onSharingChanged = onSharingChanged,
            )
        },
        // 手环推送
        onExportToWearable = { name -> WearableScheduleSync.exportToWearable(context, name) },
    )
}

/**
 * 是否处于自由小窗 / 分屏。
 *
 * 原来由每个 Activity 自己的 `onMultiWindowModeChanged` 维护（该回调 API 35 起废弃且无等价替代）。
 * 单宿主之后子页拿不到自己的 Activity 回调，统一读**宿主** [MainActivity] 的 Compose 状态 ——
 * 它由宿主的同一个回调驱动，语义不变。
 */
@Composable
private fun isInFreeformWindow(): Boolean {
    val activity = LocalActivity.current as? MainActivity ?: return false
    return activity.isInFreeformWindow
}

/**
 * 课表节数与时间（原 CourseTimeSettingsActivity，332 行）。
 *
 * 这一页比普通文档页多两层结构，迁移时**必须整体搬**：
 * - 外层 `Box` 做「背景缩放 + 模糊」动画，二级编辑页从列表卡片展开时主列表缩下去
 * - 二级编辑页 `TimeConfigEditScreen` 渲染在 `Scaffold` **外面**（与 CourseManageActivity 同构），
 *   否则会被顶栏裁掉
 */
@Composable
private fun CourseTimeSettingsRoute(router: AppRouter) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val density = LocalDensity.current
    val repository = remember { CourseRepository() }
    val coroutineScope = rememberCoroutineScope()

    val isInFreeformWindow = isInFreeformWindow()

    // 页面状态管理（原来在 Activity 里）
    var currentPage by remember { mutableStateOf("select") }
    var editingConfig by remember { mutableStateOf<TimeConfig?>(null) }
    var editingRoutineId by remember { mutableStateOf<Long?>(null) }
    var editingCardBounds by remember { mutableStateOf<TimeConfigCardBounds?>(null) }
    var listRefreshTrigger by remember { mutableIntStateOf(0) }

    // 屏幕尺寸与圆角
    val windowSize = remember(isInFreeformWindow) {
        ApiCompat.currentWindowSize(context)
    }
    val screenWidth = windowSize.width.toFloat()
    val screenHeight = windowSize.height.toFloat()
    val screenCornerRadius = remember(isInFreeformWindow) {
        if (isInFreeformWindow) {
            20f * density.density
        } else {
            activity?.window?.let { ApiCompat.windowCornerRadius(it) } ?: 0f
        }
    }

    // 快照截取
    val screenGraphicsLayer = rememberGraphicsLayer()
    var cardSnapshot by remember { mutableStateOf<Bitmap?>(null) }

    // 背景缩放与模糊动画
    val backgroundScale = remember { Animatable(1f) }
    val managePageBlurRadius = remember { Animatable(0f) }
    // 二级编辑页的玻璃层是独立的（与一级页不共享采样源）
    val editLiquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 使用 renderEffect 模糊，避免动画时重组（API 31 以下不模糊）
                    val blurRadiusPx = managePageBlurRadius.value * this@graphicsLayer.density
                    renderEffect = ApiCompat.blurRenderEffect(blurRadiusPx)
                }
                .background(MiuixTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = backgroundScale.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .drawWithContent {
                        screenGraphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        val scale = backgroundScale.value
                        if (scale < 0.999f) {
                            val path = Path().apply {
                                addSquircleRect(
                                    width = size.width,
                                    height = size.height,
                                    cornerRadius = screenCornerRadius
                                )
                            }
                            clipPath(path) {
                                this@drawWithContent.drawContent()
                            }
                        } else {
                            drawContent()
                        }
                    }
            ) {
                DocumentPageScaffold(
                    title = "课表节数与时间",
                    onBack = { router.popBack() },
                ) { scrollBehavior, liquidGlassBackdrop ->
                    CourseTimeSettingsScreen(
                        onEditRoutine = { routine, baseConfig, bounds ->
                            // 二级页面只编辑这一个作息：把该作息的时间叠加上去
                            editingConfig = baseConfig.effectiveFor(routine.id)
                            editingRoutineId = routine.id
                            editingCardBounds = bounds
                            // 先捕获快照（原始状态）
                            coroutineScope.launch {
                                val lx = bounds.left.toInt().coerceIn(0, screenWidth.toInt() - 1)
                                val ly = bounds.top.toInt().coerceIn(0, screenHeight.toInt() - 1)
                                val lw = bounds.width.toInt().coerceIn(1, screenWidth.toInt() - lx)
                                val lh = bounds.height.toInt().coerceIn(1, screenHeight.toInt() - ly)
                                cardSnapshot = try {
                                    val fullBitmap = screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                    Bitmap.createBitmap(fullBitmap, lx, ly, lw, lh)
                                } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
                            }
                            // 等待一帧后启动背景动画
                            coroutineScope.launch {
                                delay(12.milliseconds)
                                launch {
                                    backgroundScale.animateTo(
                                        targetValue = 0.92f,
                                        animationSpec = tween(560, easing = OobeQuartOutEasing)
                                    )
                                }
                                launch {
                                    managePageBlurRadius.animateTo(
                                        targetValue = 5f,
                                        animationSpec = tween(560, easing = OobeQuartOutEasing)
                                    )
                                }
                            }
                            // 最后触发组合（背景已在动画中）
                            currentPage = "edit"
                        },
                        refreshTrigger = listRefreshTrigger,
                        scrollBehavior = scrollBehavior,
                        liquidGlassBackdrop = liquidGlassBackdrop,
                    )
                }
            }
        }

        // 编辑页面渲染在 Scaffold 外面（与 CourseManageActivity 结构一致）
        if (currentPage == "edit") {
            editingConfig?.let { config ->
                val bounds = editingCardBounds
                // ⚠ 必须 remember 缓存包装对象身份：asImageBitmap() 每次调用都 new 一个
                // AndroidImageBitmap，Image 的位图纹理缓存按对象身份命中，每帧新建 = 缓存全失效
                val timeConfigSnapshotImage = remember(cardSnapshot) { cardSnapshot?.asImageBitmap() }
                TimeConfigEditScreen(
                    timeConfig = config,
                    routineId = editingRoutineId ?: 0L,
                    onBackStart = {
                        coroutineScope.launch {
                            launch {
                                backgroundScale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(350, easing = OobeCubicOutEasing)
                                )
                            }
                            launch {
                                managePageBlurRadius.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(350, easing = OobeCubicOutEasing)
                                )
                            }
                        }
                    },
                    onBack = {
                        currentPage = "select"
                        editingConfig = null
                        editingRoutineId = null
                        editingCardBounds = null
                        cardSnapshot = null
                    },
                    onSave = { savedConfig ->
                        // 只替换这一个作息；节次骨架由一级页面管理，这里不碰。
                        // 顶层 name 是用户在这一页改的作息名，要带回去。
                        val saved = editingRoutineId?.let { routineId ->
                            repository.saveRoutine(routineId, savedConfig, savedConfig.name)
                        } ?: false
                        if (!saved) {
                            // 保存被丢弃必须让用户看见：以前静默丢弃，表现成「改完保存、重开还是原来的」
                            showToast("保存失败，请重试")
                        }
                        listRefreshTrigger++
                    },
                    cardLeft = bounds?.left ?: 0f,
                    cardTop = bounds?.top ?: 0f,
                    cardWidth = bounds?.width ?: screenWidth,
                    cardHeight = bounds?.height ?: (screenHeight * 0.2f),
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    screenCornerRadius = screenCornerRadius,
                    cardStartCornerRadius = 20f,
                    cardSnapshot = timeConfigSnapshotImage,
                    // 新建配置的 FAB 形态已随「快捷切换」一起移除，二级页永远从列表卡片展开
                    isFabCreation = false,
                    onDeleteRoutine = { routineId ->
                        // 删除会换掉生效作息，重排提醒等都在 repository 里一并做完
                        repository.deleteRoutine(routineId)
                        listRefreshTrigger++
                    },
                    liquidGlassBackdrop = editLiquidGlassBackdrop,
                )
            }
        }
    }
}

/**
 * 课程管理（原 CourseManageActivity，571 行，业务逻辑全内联在 Activity 里）。
 *
 * 与 `CourseTimeSettingsRoute` 同构，但比它多两块**必须在模糊层之外**的叠加内容：
 * - 快捷菜单（长按课程卡片）：遮罩 + 卡片快照 + `ShortcutMenu`
 * - `CourseEditScreen`
 * 所以它们不能走 `DocumentPageScaffold` 的 `overlay`（那是在采样层内部），
 * 必须留在根 `Box` 里当外层 `Box` 的兄弟 —— 否则会跟着一起被模糊/缩放。
 *
 * ⚠ `DisposableEffect` 的清理（退出时删掉「本次新建但没编辑过」的空课程）从
 * 「Activity finish」变成「路由弹出、组合退出」。语义一致：都是离开本页时清理。
 */
@Composable
private fun CourseManageRoute(router: AppRouter) {
    val activity = LocalActivity.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val coroutineScope = rememberCoroutineScope()
    val courseViewModel: CourseViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val sectionTimes by settingsViewModel.sectionTimes.collectAsState()

    val isInFreeformWindow = isInFreeformWindow()

    // Edit screen state
    var showEditScreen by remember { mutableStateOf(false) }
    var selectedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var cardLeft by remember { mutableFloatStateOf(0f) }
    var cardTop by remember { mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableFloatStateOf(0f) }
    var cardHeight by remember { mutableFloatStateOf(0f) }
    var cardSnapshot by remember { mutableStateOf<Bitmap?>(null) }
    var cardColor by remember { mutableStateOf(Color(0xFF4CAF50)) }
    var cardAlpha by remember { mutableFloatStateOf(0.15f) }
    var hiddenCourseIds by remember { mutableStateOf(setOf<String>()) }
    var shrinkingCourseIds by remember { mutableStateOf(setOf<String>()) }
    var pendingAutoExitDeleteIds by remember { mutableStateOf(setOf<String>()) }

    // Shortcut菜单状态
    var showShortcutMenu by remember { mutableStateOf(false) }
    var shortcutMenuPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var shortcutMenuCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var shortcutMenuHeight by remember { mutableFloatStateOf(0f) }
    var shortcutMenuCardWidth by remember { mutableFloatStateOf(0f) }
    var shortcutMenuCardHeight by remember { mutableFloatStateOf(0f) }
    val shortcutMenuBlurRadius = remember { Animatable(0f) }
    val shortcutMenuCardScale = remember { Animatable(1f) }
    val shortcutMenuPageScale = remember { Animatable(1f) }
    var shortcutMenuSnapshot by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }

    // 删除确认弹窗状态
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteCourses by remember { mutableStateOf<List<Course>>(emptyList()) }

    // Track course IDs created in this session for cleanup on exit
    var createdCourseIds by remember { mutableStateOf(setOf<String>()) }
    // Track course IDs that were opened for editing (to avoid deleting modified ones)
    var editedCourseIds by remember { mutableStateOf(setOf<String>()) }

    // 退出本页时清理本次新建但没编辑过的空课程（原来是 Activity finish 时做）
    DisposableEffect(Unit) {
        onDispose {
            val coursesToDelete = createdCourseIds - editedCourseIds
            coursesToDelete.forEach { courseId: String ->
                courseViewModel.deleteCourse(courseId)
            }
        }
    }

    // Graphics layer for capturing screen content
    val screenGraphicsLayer = rememberGraphicsLayer()

    LaunchedEffect(showShortcutMenu) {
        if (showShortcutMenu) {
            launch { shortcutMenuBlurRadius.animateTo(10f, tween(280)) }
            launch { shortcutMenuCardScale.animateTo(1.04f, tween(280)) }
            launch { shortcutMenuPageScale.animateTo(0.98f, tween(280)) }
        } else {
            launch { shortcutMenuBlurRadius.animateTo(0f, tween(250)) }
            launch { shortcutMenuCardScale.animateTo(1f, tween(250)) }
            launch { shortcutMenuPageScale.animateTo(1f, tween(250)) }
        }
    }

    // 返回键关闭菜单（嵌套 BackHandler：菜单开着时由它接管，否则交给 AppNavHost 的返回）
    BackHandler(enabled = showShortcutMenu) {
        showShortcutMenu = false
    }

    // Background scale animation
    val backgroundScale = remember { Animatable(1f) }
    val managePageBlurRadius = remember { Animatable(0f) }

    // Dynamically get screen corner radius from window insets
    val screenCornerRadius = remember(isInFreeformWindow) {
        if (isInFreeformWindow) {
            20f * density.density  // 小窗默认圆角 20dp
        } else {
            activity?.window?.let { ApiCompat.windowCornerRadius(it) } ?: 0f
        }
    }

    // 二级编辑页 / 快捷菜单各自独立的玻璃采样源
    val editLiquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val shortcutMenuBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content with blur and scale animation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(if (shortcutMenuBlurRadius.value > 0.01f) shortcutMenuBlurRadius.value.dp else managePageBlurRadius.value.dp)
                .graphicsLayer {
                    scaleX = shortcutMenuPageScale.value
                    scaleY = shortcutMenuPageScale.value
                }
                .background(MiuixTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = backgroundScale.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .drawWithContent {
                        screenGraphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        // 缩放时保持屏幕圆角不变
                        val scale = backgroundScale.value
                        if (scale < 0.999f) {
                            val path = Path().apply {
                                addSquircleRect(
                                    width = size.width,
                                    height = size.height,
                                    cornerRadius = screenCornerRadius
                                )
                            }
                            clipPath(path) {
                                this@drawWithContent.drawContent()
                            }
                        } else {
                            drawContent()
                        }
                    }
            ) {
                DocumentPageScaffold(
                    title = "课程管理",
                    onBack = { router.popBack() },
                ) { scrollBehavior, liquidGlassBackdrop ->
                    CourseManageScreen(
                        scrollBehavior = scrollBehavior,
                        hiddenCourseIds = hiddenCourseIds,
                        shrinkingCourseIds = shrinkingCourseIds,
                        sectionTimes = sectionTimes,
                        onNewCourseCreated = { course ->
                            courseViewModel.addCourse(course)
                            createdCourseIds = createdCourseIds + course.id
                        },
                        onCourseUpdated = { oldName, course ->
                            courseViewModel.updateCoursesByName(oldName, course)
                        },
                        onEditDismiss = {
                            editingCourse = null
                        },
                        pendingEditCourse = editingCourse,
                        onCourseLongPress = { courses, left, top, width, height ->
                            shortcutMenuCourses = courses
                            shortcutMenuPosition = androidx.compose.ui.geometry.Offset(left, top)
                            shortcutMenuCardWidth = width
                            shortcutMenuCardHeight = height
                            coroutineScope.launch {
                                shortcutMenuSnapshot = screenGraphicsLayer.toImageBitmap()
                                showShortcutMenu = true
                            }
                        },
                        onDeleteCourses = { courses ->
                            coroutineScope.launch {
                                courses.forEach { course ->
                                    courseViewModel.deleteCourse(course.id)
                                }
                            }
                        },
                        deleteConfirmShow = showDeleteConfirmDialog,
                        deleteConfirmCourses = pendingDeleteCourses,
                        onDeleteConfirmDismiss = { showDeleteConfirmDialog = false },
                        onCourseClick = { courses, left, top, width, height, _, color, alpha ->
                            coroutineScope.launch {
                                selectedCourses = courses
                                cardLeft = left
                                cardTop = top
                                cardWidth = width
                                cardHeight = height
                                cardColor = color
                                cardAlpha = alpha
                                hiddenCourseIds = courses.map { it.id }.toSet()
                                // Mark courses as edited when opened
                                editedCourseIds = editedCourseIds + courses.map { it.id }

                                // Capture full screen snapshot
                                val fullSnapshot = try {
                                    screenGraphicsLayer.toImageBitmap().asAndroidBitmap()
                                } catch (_: Exception) {
                                    null
                                } catch (_: OutOfMemoryError) {
                                    null
                                }

                                // Crop card area from full snapshot
                                cardSnapshot = fullSnapshot?.let { snap ->
                                    try {
                                        val x = left.toInt().coerceIn(0, snap.width - 1)
                                        val y = top.toInt().coerceIn(0, snap.height - 1)
                                        val w = width.toInt().coerceIn(1, snap.width - x)
                                        val h = height.toInt().coerceIn(1, snap.height - y)
                                        Bitmap.createBitmap(snap, x, y, w, h)
                                    } catch (_: Exception) {
                                        null
                                    } catch (_: OutOfMemoryError) {
                                        null
                                    }
                                }

                                showEditScreen = true
                                launch {
                                    delay(12.milliseconds)
                                    launch {
                                        backgroundScale.animateTo(
                                            targetValue = 0.92f,
                                            animationSpec = tween(560, easing = OobeQuartOutEasing)
                                        )
                                    }
                                    launch {
                                        managePageBlurRadius.animateTo(
                                            targetValue = 5f,
                                            animationSpec = tween(560, easing = OobeQuartOutEasing)
                                        )
                                    }
                                }
                            }
                        },
                        liquidGlassBackdrop = liquidGlassBackdrop,
                    )
                }
            }
        }

        // Edit screen (above blur layer)
        if (showEditScreen) {
            // ⚠ 必须 remember 缓存包装对象身份：asImageBitmap() 每次调用都 new 一个
            // AndroidImageBitmap，Image 的位图纹理缓存按对象身份命中，每帧新建 = 缓存全失效
            val cardSnapshotImage = remember(cardSnapshot) { cardSnapshot?.asImageBitmap() }
            CourseEditScreen(
                courses = selectedCourses,
                cardLeft = cardLeft,
                cardTop = cardTop,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                screenWidth = windowInfo.containerSize.width.toFloat(),
                screenHeight = windowInfo.containerSize.height.toFloat(),
                screenCornerRadius = screenCornerRadius,
                cardSnapshot = cardSnapshotImage,
                cardColor = cardColor,
                cardAlpha = cardAlpha,
                onBackStart = {
                    coroutineScope.launch {
                        launch {
                            backgroundScale.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(350, easing = OobeCubicOutEasing)
                            )
                        }
                        launch {
                            managePageBlurRadius.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(350, easing = OobeCubicOutEasing)
                            )
                        }
                    }
                },
                onBack = {
                    if (pendingAutoExitDeleteIds.isNotEmpty()) {
                        showEditScreen = false
                        cardSnapshot = null
                        hiddenCourseIds = emptySet()
                        shrinkingCourseIds = pendingAutoExitDeleteIds
                        coroutineScope.launch {
                            delay(300.milliseconds)
                            pendingAutoExitDeleteIds.forEach { id ->
                                courseViewModel.deleteCourse(id)
                            }
                            shrinkingCourseIds = emptySet()
                            pendingAutoExitDeleteIds = emptySet()
                        }
                    } else {
                        showEditScreen = false
                        cardSnapshot = null
                        hiddenCourseIds = emptySet()
                    }
                },
                onCourseUpdated = { course ->
                    courseViewModel.updateCourse(course)
                    selectedCourses = selectedCourses.map { if (it.id == course.id) course else it }
                },
                onCourseAdded = { course ->
                    courseViewModel.addCourse(course)
                    selectedCourses = selectedCourses + course
                },
                onDeleteCourse = { courseId ->
                    if (selectedCourses.size > 1) {
                        courseViewModel.deleteCourse(courseId)
                    } else {
                        pendingAutoExitDeleteIds = setOf(courseId)
                    }
                    selectedCourses = selectedCourses.filter { it.id != courseId }
                },
                onColorChanged = { colorRes ->
                    cardColor = Color(colorRes)
                },
                getOccupiedWeeks = { dayOfWeek, startSection, endSection, excludeIds, startTime, endTime ->
                    courseViewModel.getOccupiedWeeks(
                        dayOfWeek = dayOfWeek,
                        startSection = startSection,
                        endSection = endSection,
                        excludeIds = excludeIds.toSet(),
                        startTime = startTime,
                        endTime = endTime
                    )
                },
                liquidGlassBackdrop = editLiquidGlassBackdrop,
                sectionTimes = sectionTimes
            )
        }

        // Shortcut菜单遮罩层
        if (showShortcutMenu || shortcutMenuBlurRadius.value > 0.01f) {
            val snapshot = shortcutMenuSnapshot
            val cardLeftPx = shortcutMenuPosition.x
            val cardTopPx = shortcutMenuPosition.y
            val cardW = shortcutMenuCardWidth
            val cardH = shortcutMenuCardHeight

            // 点击空白区域关闭菜单
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showShortcutMenu = false }
            )

            // 卡片快照（清晰，在最顶层）
            if (snapshot != null && cardW > 0f && cardH > 0f) {
                Canvas(
                    modifier = Modifier
                        .offset(
                            x = with(density) { cardLeftPx.toDp() },
                            y = with(density) { cardTopPx.toDp() }
                        )
                        .size(
                            width = with(density) { cardW.toDp() },
                            height = with(density) { cardH.toDp() }
                        )
                        .graphicsLayer {
                            scaleX = shortcutMenuCardScale.value
                            scaleY = shortcutMenuCardScale.value
                        }
                ) {
                    val cornerRadius = 16.dp.toPx()
                    val squirclePath = Path().apply {
                        addSquircleRect(
                            width = size.width,
                            height = size.height,
                            cornerRadius = cornerRadius
                        )
                    }
                    clipPath(squirclePath) {
                        drawImage(
                            image = snapshot,
                            srcOffset = IntOffset(cardLeftPx.toInt(), cardTopPx.toInt()),
                            srcSize = IntSize(cardW.toInt(), cardH.toInt()),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt())
                        )
                    }
                }
            }
        }

        // Shortcut菜单
        ShortcutMenu(
            show = showShortcutMenu,
            items = listOf(
                ShortcutMenuItem(
                    icon = MiuixIcons.Edit,
                    label = "编辑",
                    onClick = {
                        if (shortcutMenuCourses.isNotEmpty()) {
                            val courseToEdit = shortcutMenuCourses.first()
                            showShortcutMenu = false
                            coroutineScope.launch {
                                delay(260.milliseconds)
                                editingCourse = courseToEdit
                            }
                        }
                    }
                ),
                ShortcutMenuItem(
                    icon = MiuixIcons.Delete,
                    label = "删除",
                    onClick = {
                        pendingDeleteCourses = shortcutMenuCourses.toList()
                        showShortcutMenu = false
                        coroutineScope.launch {
                            delay(260.milliseconds)
                            showDeleteConfirmDialog = true
                        }
                    }
                )
            ),
            modifier = Modifier.offset(
                x = with(density) { shortcutMenuPosition.x.toDp() - 14.dp },
                y = with(density) { shortcutMenuPosition.y.toDp() - shortcutMenuHeight.toDp() + 4.dp }
            ),
            backdrop = shortcutMenuBackdrop,
            onDismiss = { showShortcutMenu = false },
            onMeasuredSize = { _, height ->
                shortcutMenuHeight = height.toFloat()
            }
        )
    }
}

/**
 * 关于页（原来内联在 AboutActivity 的 1200 行，已抽到 `:ui-shared` commonMain 的 `AboutScreen.kt`）。
 *
 * 平台专有的一半留在宿主：版本号 / 应用名 / **裁边后的应用图标**（`packageManager` +
 * AdaptiveIcon 透明边裁剪，Compose 没有跨平台等价物）/ `finishAffinity` / 诊断录制器。
 * 其余（路由跳转、子页面导航）走 `:ui-shared` 的 [com.haooz.chedule.ui.navigation.AboutRoute]。
 */
@Composable
private fun AndroidAboutRoute(router: AppRouter) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    // 应用图标：drawable → Bitmap → 裁掉 AdaptiveIcon 四周透明边 → ImageBitmap
    // （裁边逻辑原在 AboutScreen.cropBitmapToOpaque，随迁移到 ui-shared 已删除，宿主在此复刻）
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
                    android.graphics.Bitmap.createBitmap(bitmap, minX, minY, maxX - minX + 1, maxY - minY + 1)
                } else {
                    bitmap
                }
                cropped.asImageBitmap()
            }
    }
    com.haooz.chedule.ui.navigation.AboutRoute(
        router = router,
        appVersion = remember { packageInfo?.versionName ?: "未知版本" },
        appName = remember {
            packageInfo?.applicationInfo?.labelRes?.let { context.getString(it) }
                ?: context.applicationInfo?.nonLocalizedLabel?.toString()
                ?: "Nexio课程表"
        },
        appIcon = aboutIcon,
        onExitApp = { activity?.finishAffinity() },
        diagnosticRecorder = remember { AndroidDiagnosticRecorder(context) },
    )
}

/**
 * 教务系统导入（原 `EducationalImportActivity`，673 行，2026-10-10 并入单 Activity 路由）。
 *
 * 与原 Activity 的对应关系：
 * - `onCreate` 里的 `startUpdate(this)` → 进入路由即 `startUpdateIfNeeded()`（原时机）
 * - `finish()` → `router.popBack()`（原「退出页面」语义；WebView 分支的内部返回由 Screen 自管）
 * - `onMultiWindowModeChanged` → 宿主 MainActivity 的 `isInFreeformWindow`（经 [isInFreeformWindow] 读取）
 * - `forceUpdate(context)`（Toast 结果）→ [EduImportUpdater.forceUpdate]，Context 用组合期的 LocalContext
 * - 系统栏 / adjustResize / configChanges：MainActivity 的 Manifest 条目已覆盖（与原条目逐项同款）
 */
@Composable
private fun AndroidEducationalImportRoute(router: AppRouter) {
    // 只有网页宿主与自由窗口判定是 Android 专有，其余在 :ui-shared
    com.haooz.chedule.ui.navigation.EducationalImportRoute(
        router = router,
        webHostFactory = { config, events -> rememberAndroidWebHost(config, events) },
        isInFreeformWindow = isInFreeformWindow(),
    )
}
