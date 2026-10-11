/** WebView 页面 - 用于加载教务系统网页 */
package com.haooz.chedule.ui.screens

// ⚠ 本页 2026-10-11 自 :androidApp 迁入 :ui-shared（第二十六批）。
//
// 切法（全仓唯一一处「网页本体」）：
//  · **外壳进 commonMain**：顶栏采样带 / 进度条 / 底部胶囊 / alert·prompt·单选三个对话框 /
//    目标课表选择 / 返回处理 —— 纯 Compose，约 600 行，iOS 直接复用。
//  · **网页本体留宿主**：WebView 创建与配置、UA/视口策略、JS 桥（AndroidBridge /
//    WebPostBridge / WebCompatDelegate）、PixelCopy 逐帧采色 —— 约 400 行，
//    iOS 要整体换成 WKWebView，一行都复用不了。见 androidApp/ui/web/AndroidWebHost.kt。
//  · 中间是 ui/web/WebHost.kt 的契约（7 个方法 + 8 个事件回调）。
//
// 因此本文件里**不应出现任何 android.* / androidx.activity / androidx.core**；
// 需要新能力就往 WebHost 上加方法，而不是把平台类型带进来。

import com.haooz.chedule.data.NexioLog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.ic_phone_pc
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.school.SchoolData
import com.haooz.chedule.ui.web.NoopWebHost
import com.haooz.chedule.ui.web.WebHost
import com.haooz.chedule.ui.web.WebHostConfig
import com.haooz.chedule.ui.web.WebHostEvents
import top.yukonga.miuix.kmp.utils.NavigationBackHandlerFor
import com.haooz.chedule.data.school.ScriptRepository
import com.kyant.backdrop.edgelight.edgeLight
import com.haooz.chedule.ui.utils.rememberDefaultEdgeLight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NativeMiuixTextField
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.haooz.chedule.data.showToast
import top.yukonga.miuix.kmp.layout.isTabletWidth
import com.haooz.chedule.ui.utils.screenWidthDp

// 三个「网页实现细节」常量（DESKTOP_USER_AGENT / TOP_MIRROR_*）随网页本体搬到宿主：
// androidApp/.../ui/web/AndroidWebHost.kt。页面不再采样像素，也就不需要它们。

/**
 * 在 [src] 全图里取出现次数最多的颜色。
 *
 * 返回 ARGB；[prevColor] 为上一帧色（-1 表示无）。
 *
 * 滞回：新色与上一帧接近则直接用；差得远时，只有当「接近上一帧的像素」不再占优
 * （新众数明显更多，或上一帧色已几乎消失）才切换，避免左右对半来回闪；
 * 同时也不会像「占比不足 55% 就锁死」那样，把加载时的白色一直粘住。
 */
private data class AlertData(
    val title: String,
    val content: String,
    val confirmText: String,
    val onResult: (Boolean) -> Unit
)

private data class PromptData(
    val title: String,
    val tip: String,
    val defaultText: String,
    val onResult: (String?) -> Unit
)

private data class SelectionData(
    val title: String,
    val items: List<String>,
    val defaultIndex: Int,
    val onResult: (Int?) -> Unit
)

@Composable
fun WebViewScreen(
    school: SchoolData,
    adapterId: String,
    importUrl: String?,
    assetJsPath: String?,
    webContentBackdrop: LayerBackdrop,
    contentTopPadding: Dp = 0.dp,
    scheduleNames: List<String> = emptyList(),
    currentScheduleName: String = "",
    onBack: () -> Unit,
    onImportComplete: (List<Course>) -> Unit,
    onTaskCompleted: () -> Unit = {},
    onPageTitleChanged: (String) -> Unit = {},
    onDesktopModeChanged: (Boolean) -> Unit = {},
    onAssetJsPathChanged: (String?) -> Unit = {},
    /** 顶栏空白区当前上屏色（已含插值），供顶栏标题/图标按明暗切黑白 */
    onTopColorChanged: (Color) -> Unit = {},
    onExecuteImportRef: ((() -> Unit) -> Unit)? = null,
    onToggleDesktopModeRef: ((() -> Unit) -> Unit)? = null,
    onReloadRef: ((() -> Unit) -> Unit)? = null,
    /**
     * 网页宿主工厂：Android 注入 `AndroidWebHost`，桌面/iOS 尚未提供时用 [NoopWebHost]
     * （内容区留白、控制方法空转）。
     */
    webHostFactory: @Composable (WebHostConfig, WebHostEvents) -> WebHost = { _, _ -> NoopWebHost },
) {
    val isTablet = isTabletWidth()
    val tabletHorizontalPadding = if (isTablet) {
        val widthDp = screenWidthDp()
        ((widthDp - 600).coerceIn(0, 600) / 600f * 112 + 16).dp
    } else 0.dp
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    // 顶栏采样色：宿主逐帧上报（Android = PixelCopy）；null = 该平台没有这个能力，不画色带
    var topColor by remember { mutableStateOf<Color?>(null) }
var pageTitle by remember { mutableStateOf("加载中...") }
    // 平板默认桌面版，手机默认手机版（UA 与视口策略都跟着走，见 WebCompatDelegate）
    var isDesktopMode by remember { mutableStateOf(isTablet) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(isDesktopMode) { onDesktopModeChanged(isDesktopMode) }
    LaunchedEffect(assetJsPath) { onAssetJsPathChanged(assetJsPath) }
    LaunchedEffect(pageTitle) { onPageTitleChanged(pageTitle) }
    val currentOnTopColorChanged by rememberUpdatedState(onTopColorChanged)

    // 进入页面时按需预下载适配脚本，失败不阻塞页面
    LaunchedEffect(school.resourceFolder, assetJsPath) {
        val path = assetJsPath ?: return@LaunchedEffect
        ScriptRepository(ScriptRepository.getRepoUrl())
            .ensureScript(school.resourceFolder, path)
    }

    var alertData by remember { mutableStateOf<AlertData?>(null) }
    var promptData by remember { mutableStateOf<PromptData?>(null) }
    var selectionData by remember { mutableStateOf<SelectionData?>(null) }
    var showCourseTablePicker by remember { mutableStateOf(false) }
    val webDockShapeBlock: () -> Shape = remember { { ContinuousCapsule() } }
    // 与 LiquidBottomTabs 可见层同一套玻璃参数
    val webDockEffects: BackdropEffectScope.() -> Unit = remember {
        {
            vibrancy()
            blur(8f.dp.toPx())
            lens(24f.dp.toPx(), 24f.dp.toPx())
        }
    }
    val isLightTheme = !isAppDarkTheme()
    val webDockSurfaceColor = if (isLightTheme) {
        Color(0xFFFFFFFF).copy(alpha = 0.6f)
    } else {
        Color(0xFF121212).copy(alpha = 0.54f)
    }
    val webDockOnDrawSurface: DrawScope.() -> Unit = remember(webDockSurfaceColor) {
        {
            drawRect(webDockSurfaceColor)
        }
    }
    val webDockEdgeLight = rememberDefaultEdgeLight(baseColor = webDockSurfaceColor)
    var importCompleted by remember { mutableStateOf(false) }

    val currentOnImportComplete by rememberUpdatedState(onImportComplete)
    val currentOnTaskCompleted by rememberUpdatedState(onTaskCompleted)
    val currentOnBack by rememberUpdatedState(onBack)

    val showAlertCallback by rememberUpdatedState { title: String, content: String, confirmText: String, onResult: (Boolean) -> Unit ->
        alertData = AlertData(title, content, confirmText, onResult)
    }
    val showPromptCallback by rememberUpdatedState { title: String, tip: String, defaultText: String, validatorJs: String, onResult: (String?) -> Unit ->
        promptData = PromptData(title, tip, defaultText, onResult)
    }
    val showSelectionCallback by rememberUpdatedState { title: String, items: List<String>, defaultIndex: Int, onResult: (Int?) -> Unit ->
        selectionData = SelectionData(title, items, defaultIndex, onResult)
    }


    // ── 网页宿主 ────────────────────────────────────────────────────────────
    // 网页本体（WebView / JS 桥 / PixelCopy 采色）在平台侧，页面只通过这个契约驱动它。
    // 见 ui/web/WebHost.kt 的注释：iOS 侧换一份 IosWebHost 即可，本页不用动。
    //
    // events 用 remember 建一次：宿主内部用 rememberUpdatedState 持有它（WebView 的回调是
    // 长生命周期对象），而这里的闭包读的都是上面那些 rememberUpdatedState / MutableState，
    // 天然取到最新值。
    val webHostEvents = remember {
        WebHostEvents(
            onTitle = { pageTitle = it },
            onProgress = { loadingProgress = it },
            onTopColor = { color ->
                topColor = color
                currentOnTopColorChanged(color)
            },
            onAlert = { title, content, confirmText, onResult ->
                alertData = AlertData(title, content, confirmText, onResult)
            },
            onPrompt = { title, tip, defaultText, validatorJs, onResult ->
                promptData = PromptData(title, tip, defaultText, onResult)
            },
            onSingleSelection = { title, items, defaultIndex, onResult ->
                selectionData = SelectionData(title, items, defaultIndex, onResult)
            },
            onCoursesImported = { courses ->
                importCompleted = true
                currentOnImportComplete(courses)
            },
            onTaskCompleted = {
                currentOnTaskCompleted()
                if (!importCompleted) {
                    showToast("导入完成", longDuration = true)
                }
                currentOnBack()
            },
        )
    }
    val webHost = webHostFactory(
        WebHostConfig(
            importUrl = importUrl,
            assetJsPath = assetJsPath,
            resourceFolder = school.resourceFolder,
            isDesktopMode = isDesktopMode,
            contentTopPadding = contentTopPadding,
        ),
        webHostEvents,
    )
    // Alert 对话框
    alertData?.let { data ->
        WebAlertDialog(
            title = data.title,
            content = data.content,
            confirmText = data.confirmText,
            tabletHorizontalPadding = tabletHorizontalPadding,
            onConfirm = {
                data.onResult(true)
                alertData = null
            },
            onDismiss = {
                data.onResult(false)
                alertData = null
            }
        )
    }

    // Prompt 对话框
    promptData?.let { data ->
        WebPromptDialog(
            title = data.title,
            tip = data.tip,
            defaultText = data.defaultText,
            tabletHorizontalPadding = tabletHorizontalPadding,
            onConfirm = { input ->
                data.onResult(input)
                promptData = null
            },
            onDismiss = {
                data.onResult(null)
                promptData = null
            }
        )
    }

    val onExecuteImport: () -> Unit = {
        if (assetJsPath != null) {
            showCourseTablePicker = true
        } else {
            showToast("无导入脚本", longDuration = true)
        }
    }

    val scope = rememberCoroutineScope()

    val executeImportWithTable: (String) -> Unit = { tableId ->
        assetJsPath?.let { path ->
            scope.launch {
                val scriptFile = ScriptRepository(ScriptRepository.getRepoUrl())
                    .ensureScript(school.resourceFolder, path)
                if (scriptFile != null) {
                    val jsCode = scriptFile.readText()
                    webHost.setImportTableId(tableId)
                    val fullJsCode = "window.currentTableId = '$tableId';\n$jsCode"
                    webHost.evaluateJavascript(fullJsCode)
                    showToast("正在执行导入脚本...")
                } else {
                    showToast("脚本下载失败: $path", longDuration = true)
                }
            }
        } ?: showToast("无导入脚本", longDuration = true)
    }

    // 单选列表对话框
    selectionData?.let { data ->
        WebSelectionDialog(
            title = data.title,
            items = data.items,
            defaultIndex = data.defaultIndex,
            tabletHorizontalPadding = tabletHorizontalPadding,
            onSelect = { index ->
                data.onResult(index)
                selectionData = null
            },
            onDismiss = {
                data.onResult(null)
                selectionData = null
            }
        )
    }

    // 执行导入前的目标课表选择对话框
    if (showCourseTablePicker) {
        CourseTablePickerDialog(
            scheduleNames = scheduleNames,
            currentScheduleName = currentScheduleName,
            tabletHorizontalPadding = tabletHorizontalPadding,
            onDismissRequest = { showCourseTablePicker = false },
            onTableSelected = { tableId ->
                showCourseTablePicker = false
                executeImportWithTable(tableId)
            }
        )
    }


    NavigationBackHandlerFor(
        enabled = true,
        onBackCompleted = {
            if (webHost.canGoBack()) webHost.goBack() else onBack()
        },
        onBackCancelled = {},
    )

    LaunchedEffect(onExecuteImportRef) { onExecuteImportRef?.invoke(onExecuteImport) }
    LaunchedEffect(onToggleDesktopModeRef) { onToggleDesktopModeRef?.invoke { isDesktopMode = !isDesktopMode } }
    LaunchedEffect(onReloadRef) { onReloadRef?.invoke { webHost.reload() } }

    Box(modifier = Modifier.fillMaxSize()) {
        // 生产者包住 WebView：底部玻璃从这里实时采样页面像素
        // 顶栏在 Activity 层，这里只负责页面 + 底部胶囊
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface)
                .layerBackdrop(webContentBackdrop)
        ) {
            // 顶栏区域：宿主采到的上屏色铺满 contentTopPadding，供 ProgressiveBlurTopBar /
            // 顶栏空白区采样。原实现是把采到的颜色做成 1×1 Bitmap 再 FillBounds 铺开 ——
            // 等价于直接填一块纯色，只是不再需要 Bitmap（跨平台侧也没有 Bitmap）。
            topColor?.let { color ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(contentTopPadding)
                        .background(color)
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = contentTopPadding)
                    .fillMaxSize()
            ) {
                webHost.Content(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                )

                AnimatedVisibility(
                    visible = loadingProgress in 0.01f..0.99f,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(MiuixTheme.colorScheme.primary)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp + tabletHorizontalPadding)
                .padding(bottom = 24.dp)
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = webContentBackdrop,
                    shape = webDockShapeBlock,
                    effects = webDockEffects,
                    highlight = null,
                    onDrawSurface = webDockOnDrawSurface
                )
                .edgeLight(shape = ContinuousCapsule(), edgeLight = webDockEdgeLight)
        ) {
            WebImportDockContent(
                schoolName = school.name,
                isDesktopMode = isDesktopMode,
                onToggleDesktopMode = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    isDesktopMode = !isDesktopMode
                },
                onExecuteImport = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onExecuteImport()
                },
                importEnabled = assetJsPath != null
            )
        }
    }
}

@Composable
private fun WebImportDockContent(
    schoolName: String,
    isDesktopMode: Boolean,
    onToggleDesktopMode: () -> Unit,
    onExecuteImport: () -> Unit,
    importEnabled: Boolean
) {
    val isLightTheme = !isAppDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 11.dp, end = 12.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = schoolName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                // 仅展示当前模式，不可点击
                Surface(
                    shape = ContinuousRoundedRectangle(4.dp),
                    color = if (isDesktopMode)
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else
                        Color(0xFF66BB6A).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isDesktopMode) "桌面版" else "手机版",
                        fontSize = 12.sp,
                        color = if (isDesktopMode)
                            MiuixTheme.colorScheme.primary
                        else
                            Color(0xFF66BB6A),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                // 标签不要顶到右侧按钮
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "登录教务系统 → 进入课表 → 执行导入",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 手机/电脑切换：固定图标、不随状态变色
            Surface(
                modifier = Modifier.size(44.dp),
                shape = ContinuousRoundedRectangle(22.dp),
                color = if (isLightTheme) Color.Black.copy(alpha = 0.08f)
                else Color.White.copy(alpha = 0.1f)
            ) {
                IconButton(onClick = onToggleDesktopMode) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_phone_pc),
                        contentDescription = "切换手机版/桌面版",
                        modifier = Modifier.size(22.dp),
                        tint = if (isLightTheme) Color.Black else Color.White
                    )
                }
            }

            Surface(
                modifier = Modifier.size(44.dp),
                shape = ContinuousRoundedRectangle(22.dp),
                color = if (importEnabled)
                    MiuixTheme.colorScheme.primary
                else
                    MiuixTheme.colorScheme.surfaceVariant
            ) {
                IconButton(
                    onClick = onExecuteImport,
                    enabled = importEnabled
                ) {
                    Icon(
                        MiuixIcons.Normal.Download,
                        contentDescription = "执行导入",
                        modifier = Modifier.size(26.dp),
                        tint = if (importEnabled)
                            MiuixTheme.colorScheme.onPrimary
                        else
                            MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }
        }
    }
}

@Composable
private fun WebAlertDialog(
    title: String,
    content: String,
    confirmText: String,
    tabletHorizontalPadding: Dp = 0.dp,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = ContinuousRoundedRectangle(28.dp),
            color = MiuixTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp + tabletHorizontalPadding)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = content,
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        text = confirmText,
                        onClick = onConfirm,
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}

@Composable
private fun WebPromptDialog(
    title: String,
    tip: String,
    defaultText: String,
    tabletHorizontalPadding: Dp = 0.dp,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(defaultText)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = ContinuousRoundedRectangle(28.dp),
            color = MiuixTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp + tabletHorizontalPadding)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = tip,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = ContinuousRoundedRectangle(12.dp),
                    color = MiuixTheme.colorScheme.surfaceVariant
                ) {
                    NativeMiuixTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        requestFocus = true
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = { onConfirm(textFieldValue.text) },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WebSelectionDialog(
    title: String,
    items: List<String>,
    defaultIndex: Int,
    tabletHorizontalPadding: Dp = 0.dp,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(defaultIndex) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = ContinuousRoundedRectangle(28.dp),
            color = MiuixTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp + tabletHorizontalPadding)
                .heightIn(max = 400.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(items) { index, item ->
                        val isSelected = index == selectedIndex
                        Surface(
                            shape = ContinuousRoundedRectangle(12.dp),
                            color = if (isSelected)
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else
                                Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ContinuousRoundedRectangle(12.dp))
                                .clickable {
                                    selectedIndex = index
                                }
                        ) {
                            Text(
                                text = item,
                                fontSize = 16.sp,
                                color = if (isSelected)
                                    MiuixTheme.colorScheme.primary
                                else
                                    MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = { onSelect(selectedIndex) },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseTablePickerDialog(
    scheduleNames: List<String>,
    currentScheduleName: String,
    tabletHorizontalPadding: Dp = 0.dp,
    onDismissRequest: () -> Unit,
    onTableSelected: (String) -> Unit
) {
    var selectedSchedule by remember { mutableStateOf(currentScheduleName.ifBlank { scheduleNames.firstOrNull() ?: "" }) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = ContinuousRoundedRectangle(28.dp),
            color = MiuixTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp + tabletHorizontalPadding)
                .heightIn(max = 400.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "选择要导入的课表",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "导入的课程将写入所选课表，当前课表不会受影响",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(scheduleNames) { index, name ->
                        val isCurrent = name == currentScheduleName
                        val isSelected = name == selectedSchedule
                        Surface(
                            shape = ContinuousRoundedRectangle(12.dp),
                            color = if (isSelected)
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else
                                Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ContinuousRoundedRectangle(12.dp))
                                .clickable {
                                    selectedSchedule = name
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 16.sp,
                                    color = if (isSelected)
                                        MiuixTheme.colorScheme.primary
                                    else
                                        MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Text(
                                        text = "当前",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        onClick = { onTableSelected(selectedSchedule) },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
