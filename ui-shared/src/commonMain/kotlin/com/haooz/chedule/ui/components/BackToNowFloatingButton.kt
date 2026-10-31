package com.haooz.chedule.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.haooz.chedule.ui.components.text.material.Text as Text
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberDefaultEdgeLight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.edgelight.edgeLight
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import top.yukonga.miuix.kmp.internal.drawBlurredPathShadow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 底部导航栏上方的液态玻璃悬浮「返回今天/本周」按钮。
 * 结构对齐 ShortcutMenu：外层留 ShadowPadding 供阴影外溢，graphicsLayer clip=false，
 * 避免进/退动画时阴影被布局边界裁掉。
 */
@Composable
fun BackToNowFloatingButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    label: String,
    modifier: Modifier = Modifier,
    /** 玻璃内文字左右内边距；侧栏折叠轨用紧凑值，避免被面板裁切 */
    horizontalTextPadding: Dp = 30.dp,
    /** 阴影外溢预留 */
    shadowPadding: Dp = 12.dp,
    /** 侧栏：内边距随折叠↔展开插值（折叠紧凑、展开饱满） */
    adaptiveToSidebarExpand: Boolean = false,
) {
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val isLightTheme = !isAppDarkTheme()
    // 主题色半透明叠在玻璃上；无材质档改实色
    val resolvedContainerColor = if (isLightTheme) {
        MiuixTheme.colorScheme.primary.copy(alpha = com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(0.72f))
    } else {
        MiuixTheme.colorScheme.primary.copy(alpha = com.haooz.chedule.ui.utils.AppMaterialSettings.solidSurfaceAlpha(0.62f))
    }

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    val shadowColor = MiuixTheme.colorScheme.primary
    val shadowAlpha = 0.4f
    val interactionSource = remember { MutableInteractionSource() }
    val buttonShape: Shape = ContinuousCapsule()

    val buttonShapeBlock: () -> Shape = remember { { buttonShape } }
    val chromeLens = com.haooz.chedule.ui.utils.AppMaterialSettings.chromeLensEnabled()
    // 玻璃模糊随材质档位：通透玻璃档更低（折射接管通透感），其余档 8；无材质档整体关
    val glassBlur = com.haooz.chedule.ui.utils.AppMaterialSettings.glassBlurDp()
    val glassEffectsOn = com.haooz.chedule.ui.utils.AppMaterialSettings.glassEffectsEnabled()
    val buttonEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(chromeLens, glassBlur, glassEffectsOn) {
        {
            if (glassEffectsOn) {
                vibrancy()
                blur(glassBlur.dp.toPx())
                // 均衡及以下关闭折射
                if (chromeLens) lens(6f.dp.toPx(), 36f.dp.toPx())
            }
        }
    }
    val buttonOnDrawSurface: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit =
        remember(resolvedContainerColor, interactiveHighlight) {
            {
                drawRect(resolvedContainerColor)
                drawRect(Color.Black.copy(alpha = 0.03f * interactiveHighlight.pressProgress))
            }
        }

    Box(
        modifier = modifier
            .wrapContentSize()
            // 位移放外层：阴影(drawBehind)与玻璃一起跟手，拖动时阴影不脱落
            .graphicsLayer {
                clip = false
                val offset = interactiveHighlight.offset
                val contentMin = (size.minDimension - shadowPadding.toPx() * 2).coerceAtLeast(1f)
                // 起始跟手斜率：0.08 时位移约为手指的 8%，越拖阻尼越强（与 LiquidTopBarButton 一致）。
                val initialDerivative = 0.08f
                translationX = contentMin * tanh(initialDerivative * offset.x / contentMin)
                translationY = contentMin * tanh(initialDerivative * offset.y / contentMin)
            }
            .drawBehind {
                // 阴影画在 shadowPadding 内缩矩形上，模糊半径落在预留边距里，不会被父级裁切
                val inset = shadowPadding.toPx()
                // 略偏下，更像自然光投影
                val offsetY = 4.dp.toPx()
                if (size.width <= inset * 2 || size.height <= inset * 2) return@drawBehind
                val blurRadius = 10f * density
                val radius = (size.height - inset * 2) / 2f
                // 跨平台阴影：compose Path + miuix expect/actual（原为 Paint+BlurMaskFilter）
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(
                                inset,
                                inset + offsetY,
                                size.width - inset,
                                size.height - inset + offsetY,
                            ),
                            cornerRadius = CornerRadius(radius, radius),
                        ),
                    )
                }
                drawBlurredPathShadow(
                    path = path,
                    color = shadowColor.copy(alpha = shadowAlpha),
                    blurRadius = blurRadius.coerceAtLeast(0.1f),
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (adaptiveToSidebarExpand) {
                        Modifier.adaptiveHorizontalPadding(collapsed = 6.dp, expanded = ShadowPadding)
                    } else {
                        Modifier.padding(shadowPadding)
                    }
                )
                // 不用 clip：layerBlock 的拖动/拉伸会超出原 layout bounds
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onClick()
                    }
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = buttonShapeBlock,
                    effects = buttonEffects,
                    highlight = null,
                    shadow = null,
                    layerBlock = {
                        val width = size.width
                        val height = size.height

                        val progress = interactiveHighlight.pressProgress.coerceAtLeast(0f)
                        val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)
                        scaleX = scale
                        scaleY = scale

                        val offset = interactiveHighlight.offset
                        val maxDragScale = 1f.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX =
                            scale +
                                maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                (width / height).fastCoerceAtMost(1f)
                        scaleY =
                            scale +
                                maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                (height / width).fastCoerceAtMost(1f)
                    },
                    onDrawSurface = buttonOnDrawSurface
                )
                .edgeLight(shape = buttonShape, edgeLight = rememberDefaultEdgeLight(baseColor = resolvedContainerColor))
                .then(interactiveHighlight.modifier)
                .then(interactiveHighlight.gestureModifier)
                .then(
                    if (adaptiveToSidebarExpand) {
                        Modifier.adaptiveHorizontalPadding(collapsed = 12.dp, expanded = 30.dp)
                    } else {
                        Modifier.padding(horizontal = horizontalTextPadding)
                    }
                )
                .padding(vertical = 8.dp)
                .zIndex(0f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }
    }
}

/** 默认阴影外溢预留，见 ShortcutMenu */
private val ShadowPadding = 12.dp

/**
 * 左右对称水平 padding，宽度随侧栏展开进度在 [collapsed]↔[expanded] 间插值。
 * 只在 layout 读进度，不进组合。
 */
private fun Modifier.adaptiveHorizontalPadding(
    collapsed: Dp,
    expanded: Dp,
): Modifier = this then AdaptiveHorizontalPaddingElement(collapsed, expanded)

private class AdaptiveHorizontalPaddingElement(
    private val collapsed: Dp,
    private val expanded: Dp,
) : androidx.compose.ui.layout.LayoutModifier {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val padPx = lerp(
            collapsed.toPx(),
            expanded.toPx(),
            TabletNavSideState.expandProgress.floatValue,
        )
        val padInt = padPx.roundToInt().coerceAtLeast(0)
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = (constraints.maxWidth - padInt * 2).coerceAtLeast(0),
            )
        )
        val width = (placeable.width + padInt * 2)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeable.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) {
            placeable.place(padInt, 0)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AdaptiveHorizontalPaddingElement) return false
        return collapsed == other.collapsed && expanded == other.expanded
    }

    override fun hashCode(): Int {
        var result = collapsed.hashCode()
        result = 31 * result + expanded.hashCode()
        return result
    }
}
