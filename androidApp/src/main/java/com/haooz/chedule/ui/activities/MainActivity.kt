package com.haooz.chedule.ui.activities

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.haooz.chedule.ui.utils.AndroidUpdateController
import com.haooz.chedule.ui.navigation.AppNavHost
import com.haooz.chedule.ui.navigation.AppRoute
import com.haooz.chedule.ui.navigation.AppRouteContent
import com.haooz.chedule.ui.navigation.LocalAppRouter
import com.haooz.chedule.ui.navigation.MainLayerTransition
import com.haooz.chedule.ui.navigation.rememberAppRouter
import com.haooz.chedule.ui.navigation.rememberNavTransitionState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.PrivacyConsent
import com.haooz.chedule.data.ScheduleAppearance
import com.haooz.chedule.data.StatsReporter
import com.haooz.chedule.data.ThemeMode
import com.haooz.chedule.data.fromCombination
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.reminder.IslandNotificationHelper
import top.yukonga.miuix.kmp.layout.CollapsibleTopAppBar
import com.haooz.chedule.ui.basic.LiquidGlassDropdownMenu
import com.haooz.chedule.ui.basic.LiquidGlassDropdownMenuItem
import com.haooz.chedule.ui.basic.LiquidTopBarButton
import top.yukonga.miuix.kmp.layout.ProgressiveBlurTopBar
import top.yukonga.miuix.kmp.layout.SharedScrollBehavior
import com.haooz.chedule.ui.basic.ShortcutMenu
import com.haooz.chedule.ui.basic.ShortcutMenuItem
import top.yukonga.miuix.kmp.layout.rememberSharedScrollBehavior
import com.haooz.chedule.ui.components.BackToNowFloatingButton
import com.haooz.chedule.ui.components.CourseCard
import com.haooz.chedule.ui.components.LandRippleSpec
import com.haooz.chedule.ui.components.LocalLandRipple
import com.haooz.chedule.ui.components.ScheduleBottomBar
import com.haooz.chedule.ui.components.ScheduleTopBar
import com.haooz.chedule.ui.components.ShareImportDialog
import com.haooz.chedule.ui.components.SharedFileIntent
import com.haooz.chedule.data.showToast
import com.haooz.chedule.ui.components.UpdateDialog
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.effects.motion.OobeCubicOutEasing
import com.haooz.chedule.ui.effects.motion.OobeQuartOutEasing
import com.haooz.chedule.ui.screens.AddCourseDialog
import com.haooz.chedule.ui.screens.CourseDetailScreen
import com.haooz.chedule.ui.screens.CombinationThumbnail
import com.haooz.chedule.ui.screens.CustomizeScheduleScreen
import com.haooz.chedule.ui.screens.MainScheduleScreen
import com.haooz.chedule.ui.screens.PrivacyConsentScreen
import com.haooz.chedule.ui.screens.ScheduleGridGeometry
import com.haooz.chedule.ui.screens.AndroidSettingsScreen
import com.haooz.chedule.ui.screens.ShiftScheduleScreen
import com.haooz.chedule.ui.screens.TabletCourseManagePane
import com.haooz.chedule.ui.screens.TabletSwitchSchedulePane
import com.haooz.chedule.ui.screens.TodayAssistantCard
import com.haooz.chedule.ui.screens.TodayScreen
import com.haooz.chedule.ui.theme.CourseScheduleTheme
import com.haooz.chedule.ui.utils.ApiCompat
import com.haooz.chedule.ui.utils.LocalForcedDarkTheme
import com.haooz.chedule.ui.utils.applyNavigationBarIsDark
import com.haooz.chedule.ui.utils.applyThemeAwareSystemBars
import com.haooz.chedule.ui.utils.buildShareScheduleMap
import com.haooz.chedule.ui.utils.consumeAllTouches
import com.haooz.chedule.ui.utils.courseCardSolidBacking
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.PageBackdropSnapshot
import com.haooz.chedule.ui.utils.performScheduleShare
import com.haooz.chedule.ui.utils.rememberAppSettingDark
import com.haooz.chedule.ui.utils.rememberScheduleThemeMode
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import com.haooz.chedule.viewmodel.ShiftViewModel
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.FastForward
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import kotlinx.datetime.LocalDate
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.Color as ComposeColor
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import com.haooz.chedule.data.showToast
import top.yukonga.miuix.kmp.layout.isTabletWidth
import com.haooz.chedule.ui.utils.screenWidthDp
import com.haooz.chedule.ui.utils.screenHeightDp

class MainActivity : ComponentActivity() {

    companion object {
        /** 二级页侧栏点选主 tab */
        const val EXTRA_MAIN_TAB = "extra_main_tab"

        // 跨 Activity 重建复用，避免每次启动重新解码壁纸。
        // 壁纸与变换已下沉到 :ui-shared 的 WallpaperStore（桌面/iOS 的 CourseScheduleApp
        // 读同一份），这里只是**只读转发** —— 写一律走 WallpaperStore.setCached/saveTransform。
        // ⚠ 行为与迁移前一致：进程内跨 Activity 重建复用。
        val cachedWallpaperBitmap: android.graphics.Bitmap?
            get() = com.haooz.chedule.data.WallpaperStore.currentImageBitmap()?.asAndroidBitmap()

        var cachedWallpaperOffset: Offset
            get() = com.haooz.chedule.data.WallpaperStore.wallpaperOffset
            set(value) = com.haooz.chedule.data.WallpaperStore.saveTransform(
                value, com.haooz.chedule.data.WallpaperStore.transformScale
            )

        var cachedWallpaperScale: Float
            get() = com.haooz.chedule.data.WallpaperStore.transformScale
            set(value) = com.haooz.chedule.data.WallpaperStore.saveTransform(
                com.haooz.chedule.data.WallpaperStore.wallpaperOffset, value
            )

        @Volatile
        var cachedAppearance: com.haooz.chedule.data.AppearanceConfig =
            com.haooz.chedule.data.AppearanceConfig()

        fun setTaskExcludedFromRecents(context: Context, hidden: Boolean) {
            runCatching {
                val manager = context.getSystemService(ActivityManager::class.java)
                val appTask = manager.appTasks.firstOrNull { task ->
                    task.taskInfo?.baseIntent?.component?.packageName == context.packageName
                } ?: manager.appTasks.firstOrNull()
                appTask?.setExcludeFromRecents(hidden)
            }
        }
    }

    var shareIntentUri: android.net.Uri? = null
        private set
    var shareIntentAction: String? = null
        private set

    // Compose State 跟踪 intent，变化时触发重组
    var shareIntentVersion by mutableIntStateOf(0)
        private set

    var titleBarHeight by mutableStateOf(56.dp)

    var isInFreeformWindow by mutableStateOf(false)
        private set

    var resumeCount by mutableIntStateOf(0)
        private set

    /**
     * 由 Intent 触发、需要路由跳转的待处理路由（目前是「打开提醒设置」深链）。
     *
     * 这类 intent 在 `onCreate`/`onNewIntent` 里到达，那时要么 Compose 还没起来、
     * 要么已经起来 —— 统一记成状态，由 setContent 里的 LaunchedEffect 消费，
     * 避免在 Activity 层直接 startActivity（那会绕过单宿主）。
     */
    var pendingRoute by mutableStateOf<com.haooz.chedule.ui.navigation.AppRoute?>(null)
        private set

    fun clearShareIntent() {
        shareIntentUri = null
        shareIntentAction = null
    }

    private fun updateFreeformWindowState() {
        isInFreeformWindow = isInMultiWindowMode
    }

    fun applyHideFromRecents(hidden: Boolean) {
        setTaskExcludedFromRecents(this, hidden)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.getIntExtra(EXTRA_MAIN_TAB, -1)?.takeIf { it in 0..2 }?.let {
            com.haooz.chedule.ui.components.TabletNavSideState.pendingMainTab = it
        }

        // 默认不启用：只有"退出即隐藏后台"开启时才启用（见 syncBackCallback），
        // 其余情况交回系统默认返回，保证预测性返回动画可用
        backCallback = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        }.also { onBackPressedDispatcher.addCallback(this, it) }

        applyHideFromRecents(
            AppStorage.store("app_preferences")
                .getBoolean("hide_background", false)
        )

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyThemeAwareSystemBars()
        extractIntentData(intent)
        updateFreeformWindowState()
        handleReminderSettingsIntent(intent)

        // 提醒调度与超级岛 Shizuku 初始化与首帧无关，放到 IO，不抢主线程
        val appContext = applicationContext
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            CourseReminderHelper.startReminderService(appContext)
            IslandNotificationHelper.init(appContext)
        }

        // 同步隐私同意状态到可观察状态：主界面已预加载，依赖同意的副作用据此判断是否可执行
        PrivacyConsent.refresh()

        // 合规：仅在用户同意隐私政策后，才进行设备信息上报
        if (PrivacyConsent.hasConsented()) {
            StatsReporter.reportActive()
            StatsReporter.reportInstallOnce()
        }

        // 异步预加载搭配壁纸，Compose 侧已处理「无壁纸」的情况
        if (cachedWallpaperBitmap == null) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 单搭配：无 id 维度，直接读外观文件。
                    //
                    // 壁纸与变换的缓存已下沉到共享层 WallpaperStore（桌面/iOS 的
                    // CourseScheduleApp 要读同一份）。这里负责「解码 → remap → 回填」。
                    val bitmap = ScheduleAppearance.loadWallpaper()
                    var offset = Offset(
                        ScheduleAppearance.getOffsetX(),
                        ScheduleAppearance.getOffsetY()
                    )
                    var scale = ScheduleAppearance.getScale()
                    val refW = ScheduleAppearance.getOffsetRefW()
                    val refH = ScheduleAppearance.getOffsetRefH()
                    val metrics = this@MainActivity.resources.displayMetrics
                    val curW = metrics.widthPixels.toFloat()
                    val curH = metrics.heightPixels.toFloat()
                    if (refW > 0f && refH > 0f && (refW != curW || refH != curH)) {
                        val mapped = remapWallpaperForScreen(
                            offset, scale, bitmap, refW, refH, curW, curH
                        )
                        offset = mapped.first
                        scale = mapped.second
                    }
                    // 回填共享层（ImageBitmap 视图，不复制像素）
                    bitmap?.asImageBitmap()?.let {
                        com.haooz.chedule.data.WallpaperStore.setCached(it)
                    }
                    com.haooz.chedule.data.WallpaperStore.saveTransform(offset, scale)
                    cachedAppearance = com.haooz.chedule.data.AppearanceConfig(
                        cardBlurRadius = ScheduleAppearance.getCardBlur(),
                        cardAlpha = ScheduleAppearance.getCardAlpha(),
                        cardSurfaceAlpha = ScheduleAppearance.getCardSurfaceAlpha(),
                        cardHeight = ScheduleAppearance.getCardHeight(),
                        cardCornerRadius = ScheduleAppearance.getCardCornerRadius(),
                        wallpaperBrightness = ScheduleAppearance.getWallpaperBrightness(),
                        showBreakDividers = ScheduleAppearance.getShowBreakDividers(),
                        cardContentAlignment = ScheduleAppearance.getCardContentAlignment(),
                        cardTextColor = ScheduleAppearance.getCardTextColor(),
                        cardTextScale = ScheduleAppearance.getCardTextScale(),
                        showClassroom = ScheduleAppearance.getShowClassroom(),
                        showTeacher = ScheduleAppearance.getShowTeacher(),
                        cardRefraction = ScheduleAppearance.getCardRefraction(),
                        wallpaperBlur = ScheduleAppearance.getWallpaperBlur()
                    )
                } catch (_: Exception) {
                }
            }
        }

        setContent {
            CourseScheduleTheme {
                // 合规：未同意隐私政策前不申请权限、不收集信息（由 privacyConsented 门禁网络副作用）；
                // 主界面仍照常组合（预加载），这样点「同意」后即时进入，不会卡顿
                var privacyAgreed by remember {
                    mutableStateOf(PrivacyConsent.hasConsented())
                }
                val router = rememberAppRouter()
                // 主界面（常驻底座，不在 AppNavHost 的 AnimatedContent 里）的首层转场进度。
                // 栈被 rememberSaveable 恢复成非空时（旋转 / 进程重建），主界面应已是「被盖住」的 1，
                // 不能从 0 起步白播一次进入动画。
                val mainTransition = rememberNavTransitionState(
                    initialValue = if (router.hasOverlay) 1f else 0f,
                )
                // 消费 Intent 带来的待跳转路由（提醒设置深链等）
                LaunchedEffect(pendingRoute) {
                    pendingRoute?.let {
                        router.navigate(it)
                        pendingRoute = null
                    }
                }
                CompositionLocalProvider(LocalAppRouter provides router) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // ── 主界面常驻底座 ──
                        MainLayerTransition(mainTransition) {
                            AndroidCourseScheduleApp(privacyConsented = privacyAgreed)
                        }

                        // ── 子页叠加层 ──
                        AppNavHost(router, mainTransition = mainTransition) { route ->
                            AppRouteContent(router, route)
                        }

                        if (!privacyAgreed) {
                            PrivacyConsentScreen(
                                onAgree = {
                                    PrivacyConsent.setConsented()
                                    // 同意后方可进行设备信息上报
                                    StatsReporter.reportActive()
                                    StatsReporter.reportInstallOnce()
                                    privacyAgreed = true
                                },
                                onDecline = { finish() },
                                onOpenPolicy = {
                                    router.navigate(com.haooz.chedule.ui.navigation.AppRoute.PrivacyPolicy)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        isInFreeformWindow = isInMultiWindowMode
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
        // 同步返回回调：仅"退出即隐藏后台"开启时需要自定义回调（moveTaskToBack），
        // 其余情况交回系统默认返回（finish），保留 Android 14+ 的预测性返回动画
        syncBackCallback()
    }

    // 仅"退出即隐藏后台"开启时才启用自定义返回回调，
    // 让系统把手势识别为默认返回并播放"返回桌面"动画
    private fun syncBackCallback() {
        val hideBackground = AppStorage.store("app_preferences")
            .getBoolean("hide_background", false)
        backCallback?.isEnabled = hideBackground
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentData(intent)
        handleReminderSettingsIntent(intent)
        intent.getIntExtra(EXTRA_MAIN_TAB, -1).takeIf { it in 0..2 }?.let {
            com.haooz.chedule.ui.components.TabletNavSideState.pendingMainTab = it
        }
        shareIntentVersion++
    }

    private fun handleBackNavigation() {
        // 仅在"退出即隐藏后台"开启时回调才被启用，这里直接走隐藏后台逻辑
        applyHideFromRecents(true)
        moveTaskToBack(true)
    }

    private var backCallback: androidx.activity.OnBackPressedCallback? = null

    private fun handleReminderSettingsIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(
                CourseReminderHelper.EXTRA_OPEN_REMINDER_SETTINGS,
                false
            ) == true
        ) {
            intent.removeExtra(CourseReminderHelper.EXTRA_OPEN_REMINDER_SETTINGS)
            // 课程提醒已改成路由，这里不能再 startActivity —— 记成「待跳转」，
            // 由 setContent 里的 LaunchedEffect 消费（onCreate 时 Compose 还没起来）。
            pendingRoute = com.haooz.chedule.ui.navigation.AppRoute.CourseReminder
        }
    }

    @SuppressLint("NewApi")
    private fun extractIntentData(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                shareIntentUri = intent.data
                shareIntentAction = Intent.ACTION_VIEW
            }

            Intent.ACTION_SEND -> {
                shareIntentUri = intent.getParcelableExtra(
                    Intent.EXTRA_STREAM,
                    android.net.Uri::class.java
                )
                shareIntentAction = Intent.ACTION_SEND
            }
        }
    }
}

// cover-fill 最小缩放，保证壁纸填满屏幕
private fun computeWallpaperMinScale(
    bitmap: android.graphics.Bitmap?,
    screenWPx: Float,
    screenHPx: Float
): Float {
    if (bitmap == null) return 1f
    return com.haooz.chedule.data.WallpaperTransform.minScale(bitmap.width, bitmap.height, screenWPx, screenHPx)
}

/**
 * 将壁纸 offset/scale 从 fromW×fromH 重映射到 toW×toH。
 * 无有效 bitmap/参考尺寸时原样返回，避免横竖屏切换把数据算坏。
 */
private fun remapWallpaperForScreen(
    offset: Offset,
    scale: Float,
    bitmap: android.graphics.Bitmap?,
    fromW: Float,
    fromH: Float,
    toW: Float,
    toH: Float,
): Pair<Offset, Float> {
    if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return offset to scale
    return com.haooz.chedule.data.WallpaperTransform.remap(
        offset, scale, bitmap.width, bitmap.height, fromW, fromH, toW, toH
    )
}

// 16×16 网格感知加权测光，avg≥128 判亮
@SuppressLint("UseKtx")
private fun computeWallpaperIsLight(bitmap: android.graphics.Bitmap?): Boolean? {
    if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
    val gridW = 16
    val gridH = 16
    val small = bitmap.scale(gridW, gridH)
    var sum = 0L
    for (x in 0 until gridW) {
        for (y in 0 until gridH) {
            val c = small[x, y]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            sum += (299 * r + 587 * g + 114 * b) / 1000
        }
    }
    val avg = sum / (gridW * gridH)
    small.recycle()
    return avg >= 128
}

/**
 * 按屏幕长短边降采样解码相册选图。
 * 相册原图可达 50MP+，全尺寸 ARGB_8888 解码是选壁纸路径 OOM 的主因。
 * 部分机型/云相册 URI 一次 open 不稳，先落到缓存文件再解码。
 */
private fun decodeWallpaperSampled(
    context: Context,
    uri: android.net.Uri,
    screenWPx: Float,
    screenHPx: Float,
): android.graphics.Bitmap? {
    val targetW = maxOf(screenWPx, screenHPx).toInt().coerceAtLeast(1)
    val targetH = minOf(screenWPx, screenHPx).toInt().coerceAtLeast(1)

    fun sampleSizeFromBounds(outW: Int, outH: Int): Int {
        var sample = 1
        while ((outW / 2 / sample) >= targetW && (outH / 2 / sample) >= targetH) {
            sample *= 2
        }
        return sample
    }

    fun downscaleIfNeeded(decoded: android.graphics.Bitmap?): android.graphics.Bitmap? {
        decoded ?: return null
        if (decoded.width <= 0 || decoded.height <= 0) return null
        if (decoded.width > targetW * 2 || decoded.height > targetH * 2) {
            val scale = minOf(
                targetW.toFloat() / decoded.width,
                targetH.toFloat() / decoded.height,
            )
            val scaled = decoded.scale(
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== decoded) decoded.recycle()
            return scaled
        }
        return decoded
    }

    // 1) 直接 URI 解码
    try {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            val sampleOpts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFromBounds(bounds.outWidth, bounds.outHeight)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, sampleOpts)
            }
            downscaleIfNeeded(decoded)?.let { return it }
        }
    } catch (_: Exception) { }

    // 2) ImageDecoder（HEIC/部分云图更稳，API 28+）
    if (android.os.Build.VERSION.SDK_INT >= 28) {
        try {
            val decoded = android.graphics.ImageDecoder.decodeBitmap(
                android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            ) { decoder, _, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
            downscaleIfNeeded(decoded)?.let { return it }
        } catch (_: Exception) { }
    }

    // 3) 复制到缓存再解码：规避 content URI 只能读一次 / 权限瞬时失效
    val cacheFile = java.io.File(context.cacheDir, "wallpaper_pick_${System.currentTimeMillis()}.img")
    try {
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
            true
        } == true
        if (copied && cacheFile.exists() && cacheFile.length() > 0) {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                val sampleOpts = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFromBounds(bounds.outWidth, bounds.outHeight)
                }
                val decoded = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath, sampleOpts)
                downscaleIfNeeded(decoded)?.let { return it }
            }
        }
    } catch (_: Exception) { } finally {
        // 成功/失败都删，避免 cacheDir 残留 wallpaper_pick_*.img
        runCatching { cacheFile.delete() }
    }
    return null
}

/** 释放独立快照位图；与进程级壁纸缓存或 keep 引用共用时不 recycle */
private fun recycleIndependentBitmap(
    bitmap: android.graphics.Bitmap?,
    vararg keep: android.graphics.Bitmap?,
) {
    if (bitmap == null || bitmap.isRecycled) return
    if (bitmap === MainActivity.cachedWallpaperBitmap) return
    if (keep.any { it === bitmap }) return
    try {
        if (!bitmap.isRecycled) bitmap.recycle()
    } catch (_: Exception) {
    }
}


// ── Android 装配层（第四十三批）──────────────────────────────────────────────
//
// `CourseScheduleApp` 已整体迁 `:ui-shared`（2026-10-12）。Android 专有能力在这里
// 逐项注入：系统栏 / 相册选壁纸（launcher + 降采样解码）/ 分享 / 外部分享文件读取 /
// 节假日提醒重排 / 设置页 8 个平台 dest / 今日助手卡 / 更新 / 位图回收。
// 模式同 `AndroidSettingsScreen` —— 共享页只认回调，不认平台类型。

/**
 * 位图回收注入：ImageBitmap 无 recycle()，共享层的快照回收点（原
 * recycleIndependentBitmap 的 22 处调用）经此回调落到 Android Bitmap。
 * isRecycled 检查在这里做，共享层只管「该不该回收」（身份比对）。
 */
private val SharedBitmapRecycler: (ImageBitmap) -> Unit = { image ->
    runCatching {
        val bmp = image.asAndroidBitmap()
        if (!bmp.isRecycled) bmp.recycle()
    }
}

@Composable
fun AndroidCourseScheduleApp(privacyConsented: Boolean) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? MainActivity
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenW = with(density) { screenWidthDp().dp.toPx() }
    val screenH = with(density) { screenHeightDp().dp.toPx() }

    // 共享层快照回收 → Android Bitmap.recycle（iOS/桌面不注入，交给 GC）
    SideEffect { com.haooz.chedule.ui.activities.bitmapRecycler = SharedBitmapRecycler }

    // ── 相册选壁纸：系统选图器 + 三级降采样解码，结果回填共享层状态 ──
    var wallpaperDecodeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var pendingWallpaperPicked by remember { mutableStateOf<((ImageBitmap?) -> Unit)?>(null) }
    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            showToast("未选择图片")
            return@rememberLauncherForActivityResult
        }
        if (wallpaperDecodeJob?.isActive == true) {
            showToast("正在处理壁纸，请稍候")
            return@rememberLauncherForActivityResult
        }
        val decodeW = screenW
        val decodeH = screenH
        showToast("正在处理壁纸…")
        wallpaperDecodeJob = coroutineScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeWallpaperSampled(context, uri, decodeW, decodeH)
            }
            if (bitmap == null) {
                showToast("壁纸解码失败，请换一张图片", longDuration = true)
                return@launch
            }
            pendingWallpaperPicked?.invoke(bitmap.asImageBitmap())
        }
    }

    com.haooz.chedule.ui.activities.CourseScheduleApp(
        privacyConsented = privacyConsented,
        resumeCount = activity?.resumeCount ?: 0,
        isInFreeformWindow = activity?.isInFreeformWindow ?: false,
        onSystemBarsChanged = { statusDark, navDark ->
            val window = activity?.window
            if (window != null) {
                val statusFlag = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                val navFlag = android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                var appearance = 0
                if (!statusDark) appearance = appearance or statusFlag
                if (!navDark) appearance = appearance or navFlag
                window.decorView.post {
                    ApiCompat.setSystemBarsAppearance(window, appearance, statusFlag or navFlag)
                }
            }
        },
        onHolidayDataChanged = { CourseReminderHelper.onHolidayDataChanged(context) },
        onPickWallpaperImage = { onPicked ->
            pendingWallpaperPicked = onPicked
            wallpaperPickerLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        },
        onShareSchedule = { name, changed ->
            performScheduleShare(
                context = context,
                scope = coroutineScope,
                scheduleName = name,
                onSharingChanged = changed,
            )
        },
        onReadSharedFileText = { uriString ->
            try {
                activity?.contentResolver
                    ?.openInputStream(android.net.Uri.parse(uriString))
                    ?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) {
                showToast("导入失败: ${e.message}")
                null
            }
        },
        sharedFileIntent = activity?.let { act ->
            act.shareIntentUri?.let { uri ->
                SharedFileIntent(uri.toString(), act.shareIntentVersion)
            }
        },
        onShareIntentConsumed = { activity?.clearShareIntent() },
        settingsPane = { args ->
            AndroidSettingsScreen(
                viewModel = args.viewModel,
                scheduleViewModel = args.scheduleViewModel,
                settingsViewModel = args.settingsViewModel,
                shiftViewModel = args.shiftViewModel,
                isShiftMode = args.isShiftMode,
                onExitShiftMode = args.onExitShiftMode,
                onEnterShiftMode = args.onEnterShiftMode,
                navBarStyle = args.navBarStyle,
                onScrollYChanged = args.onScrollYChanged,
                settingsScrollBehavior = args.settingsScrollBehavior,
                liquidGlassBackdrop = args.liquidGlassBackdrop,
                showUpdateSettings = args.showUpdateSettings,
            )
        },
        todayAssistantCard = { d ->
            TodayAssistantCard(
                courses = d.courses,
                tomorrowCourses = d.tomorrowCourses,
                sectionTimes = d.sectionTimes,
                morningSections = d.morningSections,
                afternoonSections = d.afternoonSections,
                showClassroom = d.showClassroom,
                showTeacher = d.showTeacher,
                wallpaperBackdrop = d.wallpaperBackdrop,
                blurRadius = d.blurRadius,
                surfaceOpacity = d.surfaceOpacity,
            )
        },
        updateController = remember { AndroidUpdateController(context) },
    )
}
