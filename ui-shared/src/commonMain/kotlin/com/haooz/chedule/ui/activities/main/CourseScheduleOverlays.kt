package com.haooz.chedule.ui.activities.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.utils.ScreenCornerRadiiPx
import com.haooz.chedule.ui.utils.TransitionLayerImage
import com.haooz.chedule.ui.utils.TransitionLayerSnapshot
import com.haooz.chedule.ui.utils.toShape
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds

/**
 * 课表主界面上的整屏过渡浮层：排班切换 loading、切换课表的卡片形变。
 * 从 `CourseScheduleApp.kt` 拆出。
 */

@Composable
internal fun ShiftLoadingOverlay(
    show: Boolean,
    onShiftReady: () -> Unit,
    onHide: () -> Unit,
    onActivityChanged: (Boolean) -> Unit = {},
) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(animationSpec = tween(100)),
        exit = fadeOut(animationSpec = tween(100))
    ) {
        DisposableEffect(Unit) {
            onActivityChanged(true)
            onDispose { onActivityChanged(false) }
        }
        LaunchedEffect(Unit) {
            delay(100.milliseconds)
            onShiftReady()
            delay(500.milliseconds)
            onHide()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                InfiniteProgressIndicator(
                    size = 30.dp,
                    strokeWidth = 2.8.dp,
                    orbitingDotSize = 3.2.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "切换中",
                    style = MiuixTheme.textStyles.body1.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
    }
}

@Composable
internal fun SwitchMorphOverlay(
    active: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    /** true = 进入切换课表页（p: 1→0）；false = 退出回主界面（p: 0→1）。 */
    entering: Boolean,
    cardBounds: Rect?,
    cardSnapshot: TransitionLayerSnapshot?,
    /**
     * 进入方向复用的卡片位图（退出那一帧从卡片图层固化而来）。
     * 图层跨不过切换页的组合销毁，所以跨次复用的载体只能是位图。
     */
    cardBitmap: ImageBitmap?,
    /**
     * **课程表页面（主界面）那一屏的整屏原生截图**（含 UITabBar / 导航栏，iOS 专有）。
     *
     * 只用它底部的「原生底栏」那一条：顶栏继续用 Compose 图层画的（原生导航栏样式和
     * 切换页对不上，截进来反而多一条）。见 [bottomBarInsetPx]。
     * Android/桌面恒为 null，整段走原来的 `drawLayer(mainLayer)`。
     */
    nativeScreenShot: ImageBitmap? = null,
    /** 原生底栏高度（Compose px）。0 = 该端没有原生底栏，不贴条。 */
    bottomBarInsetPx: Float = 0f,
    mainLayer: GraphicsLayer,
    screenCornerRadius: ScreenCornerRadiiPx,
    isDark: Boolean,
) {
    // 门控必须留在本 composable 内：退出的兜底分支会出现
    // showSwitchSchedule && !switchOverlayActive 的窗口，无条件绘制会闪一帧全屏底色
    if (!active) return
    val windowInfo = LocalWindowInfo.current
    val screenWidth = windowInfo.containerSize.width.toFloat()
    val screenHeight = windowInfo.containerSize.height.toFloat()
    val density = LocalDensity.current
    val p = progress.value

    // The source layer stays owned by the main composition throughout the morph.
    val cardImage = remember(cardSnapshot) { cardSnapshot }
    // 本次形变有没有可用的卡片内容：进入看位图，退出看实时图层。
    val hasCard = if (entering) cardBitmap != null else cardImage != null

    val sBounds = cardBounds
    val cLeft: Float
    val cTop: Float
    val cWidth: Float
    val cHeight: Float
    if (sBounds != null) {
        cLeft = sBounds.left + (0f - sBounds.left) * p
        cTop = sBounds.top + (0f - sBounds.top) * p
        cWidth = sBounds.width + (screenWidth - sBounds.width) * p
        cHeight = sBounds.height + (screenHeight - sBounds.height) * p
    } else {
        cLeft = 0f; cTop = 0f; cWidth = screenWidth; cHeight = screenHeight
    }
    val startRadius = with(density) { 20.dp.toPx() }
    val cShape = screenCornerRadius.interpolateFrom(startRadius, p).toShape(density)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) Color(0xFF2C2C2C).copy(alpha = (p * 0.5f).coerceIn(0f, 0.5f))
                else Color.Black.copy(alpha = (p * 0.5f).coerceIn(0f, 0.5f))
            )
    )
    Box(
        modifier = Modifier
            .offset(
                x = with(density) { cLeft.toDp() },
                y = with(density) { cTop.toDp() }
            )
            .size(
                width = with(density) { cWidth.toDp() },
                height = with(density) { cHeight.toDp() }
            )
            .clip(cShape)
            .graphicsLayer {
                // 进入方向：有可交叉的卡片时容器整段全实
                alpha = if (entering) {
                    if (!hasCard) (p / 0.2f).coerceIn(0f, 1f) else 1f
                } else {
                    1f
                }
            }
            .background(MiuixTheme.colorScheme.background)
    ) {
        if (cardBitmap != null) {
            // 进入方向：画退出那一帧固化下来的卡片位图。
            // 它是容器的子节点（.align(TopStart)）→ 固定在快照顶部、跟着容器一起缩。
            Image(
                bitmap = cardBitmap,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(ContinuousRoundedRectangle(20.dp))
                    .graphicsLayer {
                        // 关键：必须在主界面快照开始化之前（p=0.8）就铺满
                        alpha = ((1f - p) / 0.2f).coerceIn(0f, 1f)
                    },
                contentScale = ContentScale.None,
            )
        }
        if (cardImage != null) {
            TransitionLayerImage(
                snapshot = cardImage,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(ContinuousRoundedRectangle(20.dp))
                    .graphicsLayer {
                        alpha = if (entering) {
                            ((1f - p) / 0.2f).coerceIn(0f, 1f)
                        } else {
                            (1f - p * 2f).coerceIn(0f, 1f)
                        }
                    },
                contentScale = ContentScale.None
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = when {
                        !entering -> ((p - 0.2f) / 0.7f).coerceIn(0f, 1f)
                        // 没有可交叉的卡片（本次会话第一次进入、还没退出过）：
                        // 保持不透明、由容器 alpha 收尾，否则中段只剩容器纯色底。
                        !hasCard -> 1f
                        // 淡出：起点沿用你改的 0.8，终点 0.2（跨 0.6 个进度）
                        else -> ((p - 0.2f) / 0.6f).coerceIn(0f, 1f)
                    }
                }
                .drawWithContent {
                    val layerSize = mainLayer.size
                    if (layerSize.width > 0 && layerSize.height > 0) {
                        // Match the previous snapshot's Crop / TopCenter alignment.
                        val scale = maxOf(
                            size.width / layerSize.width,
                            size.height / layerSize.height,
                        )
                        translate(left = (size.width - layerSize.width * scale) / 2f) {
                            scale(scale, scale, pivot = Offset.Zero) {
                                // Compose 主内容（顶栏是 Compose 画的，正是要的那一版）
                                drawLayer(mainLayer)
                                // 原生底栏不在 Compose 图层里：从整屏截图底部裁一条贴上去。
                                // 只贴底栏、不换整屏 —— 原生导航栏样式和切换页对不上。
                                val shot = nativeScreenShot
                                if (shot != null && shot.width > 0 && shot.height > 0 &&
                                    bottomBarInsetPx > 0.5f
                                ) {
                                    val shotPxPerComposePx = shot.height.toFloat() / screenHeight
                                    val srcH = (bottomBarInsetPx * shotPxPerComposePx)
                                        .toInt().coerceIn(1, shot.height)
                                    val dstH = bottomBarInsetPx.toInt()
                                        .coerceIn(1, layerSize.height)
                                    drawImage(
                                        image = shot,
                                        srcOffset = IntOffset(0, shot.height - srcH),
                                        srcSize = IntSize(shot.width, srcH),
                                        dstOffset = IntOffset(0, layerSize.height - dstH),
                                        dstSize = IntSize(layerSize.width, dstH),
                                        filterQuality = FilterQuality.None,
                                    )
                                }
                            }
                        }
                    }
                }
        )
    }
}
