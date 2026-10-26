package com.haooz.chedule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.haooz.chedule.ui.effects.liquidglass.DampedDragAnimation
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberDefaultEdgeLight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.edgelight.edgeLight
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh

internal val LocalLiquidBottomTabScale =
    staticCompositionLocalOf { { 1f } }

/** 底栏胶囊底色。圆钮与胶囊共用同一块材质，颜色必须同源，所以只在这里定义一次。 */
@Composable
internal fun bottomBarSurfaceColor(): Color {
    return when {
        MiuixTheme.isDynamicColor ->
            MiuixTheme.colorScheme.surfaceContainer
                .copy(alpha = AppMaterialSettings.solidSurfaceAlpha(0.5f))
        !isAppDarkTheme() ->
            Color(0xFFFAFAFA).copy(alpha = AppMaterialSettings.solidSurfaceAlpha(0.5f))
        else ->
            Color(0xFF242424).copy(alpha = AppMaterialSettings.solidSurfaceAlpha(0.6f))
    }
}

/**
 * 底栏右端的圆形按钮。它是底栏形状的一部分（见 [BarShape]），不是贴在旁边的一颗按钮：
 * 胶囊、圆、中间的融球颈由同一个 Shape 描述，折射 / 高光 / 描边天然沿融合后的轮廓走。
 */
data class LiquidBottomTrailing(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    /** 与胶囊的静止间距 */
    val gap: Dp = 10.dp,
    /** 圆钮朝右拖时，形状还需要多留的宽度 */
    val dragRoom: Dp = 18.dp,
)

/** 底部导航 tab：纯视觉，手势由 [LiquidBottomTabs] 统一处理 */
@Composable
fun RowScope.LiquidBottomTab(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            .clip(ContinuousCapsule())
            .semantics { role = Role.Tab }
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/**
 * 液态玻璃底部导航。手势统一由内部层处理：按下 tab 胶囊飞过去并保持按压；
 * 按下胶囊 / 拖动 1:1 跟手；松手吸附最近 tab 并提交选中。
 *
 * 传 [trailing] 时右侧圆钮并入同一块材质：底栏总宽 = 胶囊 + 间距 + 圆钮 + 拖动余量，
 * 材质由一条 shape（胶囊 ∪ 圆 ∪ 颈）描述。
 */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    containerHeight: Dp = 56.dp,
    selectorHeight: Dp = 48.dp,
    trailing: LiquidBottomTrailing? = null,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isAppDarkTheme()
    val accentColor = if (isLightTheme) Color.Black else Color.White
    val containerColor = bottomBarSurfaceColor()
    val defaultEdgeLight = rememberDefaultEdgeLight(baseColor = containerColor)

    val tabsBackdrop = rememberLayerBackdrop()

    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    val heightPx = with(density) { containerHeight.toPx() }
    // 圆钮占掉的额外宽度（间距 + 直径 + 拖动余量）；没有圆钮时为 0
    val extraPx = trailing?.let {
        with(density) { (it.gap + containerHeight + it.dragRoom).toPx() }
    } ?: 0f
    val gapPx = trailing?.let { with(density) { it.gap.toPx() } } ?: 0f
    // 胶囊按压到底时额外增加的宽度（左右各 8dp）
    val pressExtraPx = with(density) { 16f.dp.toPx() }
    val maxIndex = (tabsCount - 1).toFloat()
    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedTabIndex().toFloat(),
            valueRange = 0f..maxIndex,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> }
        )
    }

    // 材质节点用 requiredSize 撑到可见底栏之外，靠 TopStart 保证它不被父层再居中一次
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopStart) {
        val viewConfiguration = LocalViewConfiguration.current
        val hapticFeedback = LocalHapticFeedback.current
        val padPx = with(density) { 4f.dp.toPx() }
        val pillPx = constraints.maxWidth.toFloat() - extraPx
        val pillWidthDp = with(density) { pillPx.toDp() }
        val tabWidth = (pillPx - padPx * 2f) / tabsCount
        // 滑块（可按压的那块玻璃）左右各收 1dp，宽度减 2dp，位置补回 1dp 保持居中
        val selectorInsetPx = with(density) { BottomBarSelectorInset.toPx() }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / pillPx).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val ltrSign = if (isLtr) 1f else -1f
        // 不能用 selectedTabIndex 做 remember key：每次重组都是新实例，currentIndex 会被冲掉
        var currentIndex by remember { mutableIntStateOf(selectedTabIndex()) }
        val latestSelectedTabIndex by rememberUpdatedState(selectedTabIndex)
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { latestSelectedTabIndex() }
                .collectLatest { index ->
                    if (index != currentIndex) {
                        currentIndex = index
                    }
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }

        // 材质画布四周的余量：胶囊按压放大、圆钮上下拖动都要画到栏外
        val bleedX = with(density) { MaterialBleedX.toPx() }
        val bleedY = with(density) { MaterialBleedY.toPx() }
        // 只缩胶囊，圆钮有自己的按压缩放
        val pressScaleOf: () -> Float = {
            lerp(1f, 1f + pressExtraPx / pillPx, dampedDragAnimation.pressProgress)
        }

        // 圆钮的按压高光 / 缩放 / 回弹与顶栏那颗 LiquidTopBarButton 同一套 InteractiveHighlight，
        // 但光晕画在整条底栏的材质节点上，坐标要换算过去。
        val trailingTopLeft = Offset(bleedX + pillPx + gapPx, bleedY)
        val trailingHighlight = if (trailing != null) {
            remember(animationScope, trailingTopLeft, containerHeight) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { _, finger -> finger + trailingTopLeft },
                    radiusBaseDp = containerHeight,
                    fixedRadius = true,
                    // 只要圆形光晕，不整条底栏平铺提亮
                    drawFlatOverlay = false,
                )
            }
        } else {
            null
        }

        // 圆钮位移，与顶栏按钮逐字同一条映射 `minDimension · tanh(k · 手指位移 / minDimension)`。
        // 不做 clamp，拖出画布的部分由 bleed 兜住。
        val trailingTranslationOf: () -> Offset = {
            val d = trailingHighlight?.offset ?: Offset.Zero
            Offset(
                heightPx * tanh(0.025f * d.x / heightPx),
                heightPx * tanh(0.025f * d.y / heightPx),
            )
        }
        // 圆钮按压缩放 + 沿拖动方向拉伸，与顶栏按钮的 graphicsLayer 同一条公式。
        // 材质是 Path，所以倍数直接喂给圆的 rx / ry；图标读同一个值才不会和材质脱开。
        val trailingScaleOf: () -> Offset = {
            val progress = (trailingHighlight?.pressProgress ?: 0f).coerceAtLeast(0f)
            val scale = lerp(1f, 1f + with(density) { 4.dp.toPx() } / heightPx, progress)
            val d = trailingHighlight?.offset ?: Offset.Zero
            val maxDragScale = with(density) { 1.dp.toPx() } / heightPx
            val angle = atan2(d.y, d.x)
            Offset(
                scale + maxDragScale * abs(cos(angle) * d.x / heightPx),
                scale + maxDragScale * abs(sin(angle) * d.y / heightPx),
            )
        }

        // 光晕画在材质坐标系：手势层坐标要补上 bleed、减掉 panelOffset，再反解按压缩放
        val pillCenterInMaterial = Offset(bleedX + pillPx / 2f, bleedY + heightPx / 2f)
        val interactiveHighlight = remember(animationScope, bleedX, bleedY, pillPx) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { _, finger ->
                    val s = pressScaleOf()
                    val local = finger + Offset(bleedX, bleedY) - Offset(panelOffset, 0f)
                    Offset(
                        (local.x - pillCenterInMaterial.x) / s + pillCenterInMaterial.x,
                        (local.y - pillCenterInMaterial.y) / s + pillCenterInMaterial.y
                    )
                },
                // 节点比可见底栏大一圈，光晕半径不能由 min(size) 决定
                radiusBaseDp = containerHeight,
                fixedRadius = true,
            )
        }

        val chromeLens = AppMaterialSettings.chromeLensEnabled()
        val glassBlur = AppMaterialSettings.glassBlurDp()
        val glassEffectsOn = AppMaterialSettings.glassEffectsEnabled()
        val refractionShaderRef = remember { arrayOfNulls<RuntimeShader>(1) }
        val refractionScaleRef = remember { floatArrayOf(1f) }
        val feedRefraction: () -> Unit = {
            val sh = refractionShaderRef[0]
            if (sh != null) {
                val scale = refractionScaleRef[0]
                val s = pressScaleOf()
                val ts = trailingScaleOf()
                val t = trailingTranslationOf()
                val pillHalfW = pillPx / 2f * s
                val pillHalfH = heightPx / 2f * s
                val pillCx = bleedX + pillPx / 2f + panelOffset
                val pillCy = bleedY + heightPx / 2f
                val circleCx = bleedX + pillPx + gapPx + heightPx / 2f + t.x
                val circleCy = pillCy + t.y
                val circleRx = heightPx / 2f * ts.x
                val circleRy = heightPx / 2f * ts.y
                val neck = barNeckGeometryPx(
                    pillCenterX = pillCx,
                    pillCenterY = pillCy,
                    pillHalfWidth = pillHalfW,
                    pillHalfHeight = pillHalfH,
                    circleCenterX = circleCx,
                    circleCenterY = circleCy,
                    circleRadiusX = circleRx,
                    circleRadiusY = circleRy,
                    maxNeckHalfPx = heightPx * NeckStrength,
                    density = density,
                )
                val pillRadiusPx = minOf(pillHalfW, pillHalfH)
                sh.setFloatUniform("offset", 0f, 0f)
                sh.setFloatUniform("pillCenter", pillCx * scale, pillCy * scale)
                sh.setFloatUniform("pillHalf", pillHalfW * scale, pillHalfH * scale)
                sh.setFloatUniform("pillRadius", pillRadiusPx * scale)
                sh.setFloatUniform("circleCenter", circleCx * scale, circleCy * scale)
                sh.setFloatUniform("circleRadii", circleRx * scale, circleRy * scale)
                // 山丘参数无条件传：它在颈出现之前就有了，折射必须跟着鼓起来
                sh.setFloatUniform("bulgeH", (neck?.bulgeH ?: 0f) * scale)
                sh.setFloatUniform("bulgeAngle", neck?.bulgeAngle ?: 0f)
                sh.setFloatUniform("bulgePhiPill", neck?.phiPill ?: 0f)
                sh.setFloatUniform("bulgePhiCircle", neck?.phiCircle ?: 0f)
                sh.setFloatUniform("bulgeGaussEdge", neck?.gaussEdge ?: 0f)
                if (neck != null && neck.hasNeck) {
                    sh.setFloatUniform("pu", neck.pu.x * scale, neck.pu.y * scale)
                    sh.setFloatUniform("c1u", neck.c1u.x * scale, neck.c1u.y * scale)
                    sh.setFloatUniform("c2u", neck.c2u.x * scale, neck.c2u.y * scale)
                    sh.setFloatUniform("cu", neck.cu.x * scale, neck.cu.y * scale)
                    sh.setFloatUniform("pl", neck.pl.x * scale, neck.pl.y * scale)
                    sh.setFloatUniform("c1l", neck.c1l.x * scale, neck.c1l.y * scale)
                    sh.setFloatUniform("c2l", neck.c2l.x * scale, neck.c2l.y * scale)
                    sh.setFloatUniform("cl", neck.cl.x * scale, neck.cl.y * scale)
                    sh.setFloatUniform("neckRadius", 1f)
                } else {
                    sh.setFloatUniform("neckRadius", 0f)
                }
                sh.setFloatUniform("refractionHeight", with(density) { 6f.dp.toPx() } * scale)
                sh.setFloatUniform("refractionAmount", with(density) { -36f.dp.toPx() } * scale)
            }
        }
        val panelEffects: BackdropEffectScope.() -> Unit =
            remember(
                chromeLens, glassBlur, glassEffectsOn,
                pillPx, gapPx, heightPx, bleedX, bleedY, density, feedRefraction,
            ) {
                {
                    if (glassEffectsOn) {
                        vibrancy()
                        // Mirror：默认的 Clamp 会把边缘采样钳到最外一行，边缘一圈模糊退化
                        blur(glassBlur.dp.toPx(), TileMode.Mirror)
                        padding = 0f
                        if (chromeLens) {
                            refractionScaleRef[0] = downsampleScale
                            refractionShaderRef[0] =
                                obtainRuntimeShader("BottomBarRefraction", BottomBarRefractionShader)
                            runtimeShaderEffect(
                                key = "BottomBarRefraction",
                                shaderString = BottomBarRefractionShader,
                                uniformShaderName = "content",
                            ) {
                                feedRefraction()
                            }
                        }
                    }
                }
            }
        val panelSurface: DrawScope.() -> Unit =
            remember(containerColor) { { drawRect(containerColor) } }

        val shapeParams = BarShapeParams(
            panelOffset = { panelOffset },
            pressScale = pressScaleOf,
            trailingTranslation = trailingTranslationOf,
            trailingScale = trailingScaleOf,
            extraPx = extraPx,
            gapPx = gapPx,
            maxNeckHalfPx = heightPx * NeckStrength,
            bleedX = bleedX,
            bleedY = bleedY,
        )
        // drawBackdrop 的 ShapeProvider 按引用比较，必须每次现造实例
        val barShapeFactory: () -> Shape = { BarShape(shapeParams) }
        // edgeLight 按引用缓存 outline，所以给它稳定实例 + 版本号。
        // key 只用不变的标量：shapeParams 每次重组都是新对象，拿它当 key 会让描边每帧重录。
        val barEdgeShape: Shape = remember(extraPx, gapPx, heightPx, bleedX, bleedY) {
            BarShape(shapeParams)
        }
        val barRevision: () -> Long = remember(trailingHighlight) {
            {
                val t = trailingTranslationOf()
                var r = (pressScaleOf() * 2000f).roundToInt().toLong()
                r = r * 31 + (panelOffset * 40f).roundToInt()
                r = r * 31 + (t.x * 40f).roundToInt()
                r = r * 31 + (t.y * 40f).roundToInt()
                val ts = trailingScaleOf()
                r = r * 31 + (ts.x * 500f).roundToInt()
                r = r * 31 + (ts.y * 500f).roundToInt()
                r
            }
        }

        // 材质：一块玻璃，覆盖「可见底栏 + 四周余量」。
        // 只用 offset + requiredSize 把画布顶到 (-bleed)，父层对齐必须是 TopStart；
        // 这两者都不参与父层对齐计算，比手写 layout placement 可靠。
        Box(
            Modifier
                .requiredSize(
                    width = with(density) { (constraints.maxWidth + bleedX * 2f).toDp() },
                    height = with(density) { (heightPx + bleedY * 2f).toDp() },
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = barShapeFactory,
                    effects = panelEffects,
                    highlight = null,
                    onDrawSurface = panelSurface,
                    // 每帧刷折射几何：拖动 / 按压 / 拉伸时形状在变，而 updateEffects 不会重跑
                    onDrawBackdrop = { drawBackdrop ->
                        feedRefraction()
                        drawBackdrop()
                    },
                )
                // 高光在 drawBackdrop 的 clipPath 之内，自然沿融合后的边缘裁切
                .then(interactiveHighlight.modifier)
                .then(trailingHighlight?.modifier ?: Modifier)
                .edgeLight(
                    shape = barEdgeShape,
                    edgeLight = defaultEdgeLight,
                    outlineRevision = barRevision,
                )
        )

        // tab 内容，只占胶囊宽度
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .width(pillWidthDp)
                .height(containerHeight)
                .graphicsLayer {
                    translationX = panelOffset
                    // 内容跟着胶囊一起放大，圆钮不跟
                    val s = pressScaleOf()
                    scaleX = s
                    scaleY = s
                }
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        // 捕获层（透明）：滑块的唯一采样源，画布必须等于底栏可见矩形。
        // 滑块的采样窗口是「48dp × tabWidth」按 window 坐标 1:1 直取，带上一圈 bleed
        // 就只会截到中间一条，玻璃看起来被压细。
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    // 与材质节点同尺寸、同原点，录制层与可见层的玻璃才落在同一个世界坐标上。
                    // 不能用普通 width/height：会被父层 56dp 的 maxHeight 夹回去。
                    .requiredSize(
                        width = with(density) { (constraints.maxWidth + bleedX * 2f).toDp() },
                        height = with(density) { (heightPx + bleedY * 2f).toDp() },
                    )
                    // 只录不上屏：录制走独立 GraphicsLayer，alpha 0 不影响录制内容
                    .alpha(0f)
                    .then(
                        if (glassEffectsOn) Modifier.layerBackdrop(tabsBackdrop) else Modifier
                    )
                    .drawBackdrop(
                        backdrop = backdrop,
                        // 与可见层同一条轮廓
                        shape = barShapeFactory,
                        effects = {
                            if (glassEffectsOn) {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                // 与可见层同一个模糊，滑块放大时玻璃底才一致
                                blur(glassBlur.dp.toPx(), TileMode.Mirror)
                                padding = 0f
                                lens(
                                    24f.dp.toPx() * progress,
                                    24f.dp.toPx() * progress
                                )
                            }
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    // 按「玻璃」而不是「画布」缩进：start 补回 bleedX，end 让出圆钮 + 右侧 bleed
                    .padding(
                        start = with(density) { (bleedX + 4.dp.toPx()).toDp() },
                        end = with(density) { (extraPx + bleedX + 4.dp.toPx()).toDp() },
                    )
                    .graphicsLayer {
                        translationX = panelOffset
                        colorFilter = ColorFilter.tint(accentColor)
                    },
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        // 滑块（纯视觉）。父容器是 TopStart，这里必须显式居中：
        // 它只有 48dp 高，不居中就贴顶，比胶囊偏上 4dp。
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset + selectorInsetPx
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset + selectorInsetPx
                }
                .drawBackdrop(
                    // 页面 + 捕获层（图标随 lens 一起折射，轮廓天然一致）
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { ContinuousCapsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.06f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .width(with(density) { (tabWidth - selectorInsetPx * 2f).toDp() })
                .height(selectorHeight)
        )

        // 圆钮：材质已并入底栏形状，这里只放内容和交互
        if (trailing != null && trailingHighlight != null) {
            val trailingLeftDp = with(density) { (pillPx + gapPx).toDp() }
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .offset {
                        val t = trailingTranslationOf()
                        IntOffset(
                            with(density) { trailingLeftDp.toPx() }.roundToInt() + t.x.roundToInt(),
                            t.y.roundToInt(),
                        )
                    }
                    .size(containerHeight)
                    // 与 LiquidTopBarButton 同一套手势：纯视觉跟踪、不 consume，
                    // 下面的 clickable 照常收到点击。
                    .then(trailingHighlight.gestureModifier)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            trailing.onClick()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = trailing.icon,
                    contentDescription = trailing.contentDescription,
                    tint = if (isLightTheme) Color.Black.copy(alpha = 0.85f)
                    else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(28.dp)
                        // 与材质读同一个倍率，否则图标会和圆脱开
                        .graphicsLayer {
                            val ts = trailingScaleOf()
                            scaleX = ts.x
                            scaleY = ts.y
                        },
                )
            }
        }

        // 统一手势层：按下 tab / 拖胶囊 / 松手选中。
        // 只覆盖胶囊宽度 —— 它是最上层的兄弟节点，盖到圆钮会抢走命中测试。
        Box(
            Modifier
                .width(pillWidthDp)
                .fillMaxHeight()
                .then(interactiveHighlight.gestureModifier)
                .pointerInput(tabsCount, tabWidth, isLtr, padPx, pillPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x
                        // 圆钮那一侧交给它自己的手势
                        if (downX > pillPx) return@awaitEachGesture

                        val capsuleCenterX = if (isLtr) {
                            padPx + (dampedDragAnimation.value + 0.5f) * tabWidth
                        } else {
                            size.width - padPx - (dampedDragAnimation.value + 0.5f) * tabWidth
                        }
                        val capsuleHalf = tabWidth * 0.55f
                        var dragging = abs(downX - capsuleCenterX) <= capsuleHalf
                        var pressedTab = -1

                        if (dragging) {
                            dampedDragAnimation.press()
                        } else {
                            pressedTab = if (isLtr) {
                                floor((downX - padPx) / tabWidth).toInt()
                            } else {
                                floor((size.width - padPx - downX) / tabWidth).toInt()
                            }.fastCoerceIn(0, tabsCount - 1)
                            // 按下 tab：胶囊飞过去，保持按压
                            dampedDragAnimation.animateToValueKeepingPress(pressedTab.toFloat())
                        }

                        var lastX = downX
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                val target = if (dragging) {
                                    dampedDragAnimation.targetValue
                                        .fastRoundToInt()
                                        .fastCoerceIn(0, tabsCount - 1)
                                } else {
                                    pressedTab.fastCoerceIn(0, tabsCount - 1)
                                }
                                currentIndex = target
                                dampedDragAnimation.animateToValue(target.toFloat())
                                dampedDragAnimation.release()
                                // 立即通知宿主，不等 snapshotFlow 下一拍，减少与 pager 错帧
                                onTabSelected(target)
                                animationScope.launch {
                                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                                }
                                break
                            }

                            val dx = change.position.x - lastX
                            val totalDx = change.position.x - downX

                            // 点 tab 后继续滑，接管为拖动
                            if (!dragging && abs(totalDx) > touchSlop) {
                                dragging = true
                                lastX = change.position.x
                            }

                            if (dragging && abs(dx) > 0.01f) {
                                lastX = change.position.x
                                dampedDragAnimation.updateValue(
                                    (dampedDragAnimation.targetValue + dx / tabWidth * ltrSign)
                                        .fastCoerceIn(0f, maxIndex)
                                )
                                animationScope.launch {
                                    offsetAnimation.snapTo(offsetAnimation.value + dx)
                                }
                            } else {
                                lastX = change.position.x
                            }

                            change.consume()
                        }
                    }
                }
        )
    }
}
