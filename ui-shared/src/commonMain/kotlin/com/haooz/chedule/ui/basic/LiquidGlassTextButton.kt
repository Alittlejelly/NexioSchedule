package com.haooz.chedule.ui.basic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.haooz.chedule.ui.components.text.material.Text as Text
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.edgelight.edgeLight
import com.haooz.chedule.ui.utils.rememberDefaultEdgeLight
import com.haooz.chedule.ui.effects.liquidglass.InteractiveHighlight
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.internal.drawBlurredPathShadow
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.interactive.computeDragTransform

/**
 * 底部悬浮液态玻璃按钮，效果对齐 [com.haooz.chedule.ui.components.BackToNowFloatingButton]：
 * graphicsLayer clip=false（形变不裁切），无 ShadowPadding 外溢；依赖 Activity 已有的 liquidGlassBackdrop 采样层。
 */
@Composable
fun LiquidGlassTextButton(
    text: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minHeight: Dp = ButtonDefaults.MinHeight,
    primary: Boolean = true,
) {
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val isLightTheme = !isAppDarkTheme()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            // 浅色提到 0.1（深色本就是 0.15）：浅色默认档 0.05 对这个材质太弱
            lightPeakAlpha = 0.1f,
        )
    }
    val interactionSource = remember { MutableInteractionSource() }

    val resolvedContainerColor = if (primary) {
        if (isLightTheme) {
            MiuixTheme.colorScheme.primary.copy(alpha = 0.72f)
        } else {
            MiuixTheme.colorScheme.primary.copy(alpha = 0.62f)
        }
    } else if (isLightTheme) {
        Color.White.copy(alpha = 0.76f)
    } else {
        if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.84f)
        else Color(0xFF242424).copy(alpha = 0.84f)
    }
    val textColor = if (primary) {
        Color.White
    } else if (isLightTheme) {
        Color.Black.copy(alpha = 0.85f)
    } else {
        Color.White.copy(alpha = 0.85f)
    }
    val shadowColor = if (primary) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
    val shadowAlpha = 0.4f

    val buttonShape: Shape = ContinuousRoundedRectangle(cornerRadius)
    val buttonShapeBlock: () -> Shape = remember(cornerRadius) { { buttonShape } }
    val chromeLens = com.haooz.chedule.ui.utils.AppMaterialSettings.chromeLensEnabled()
    // 玻璃模糊随材质档位：通透玻璃档更低（折射接管通透感），其余档 8
    val glassBlur = com.haooz.chedule.ui.utils.AppMaterialSettings.glassBlurDp()
    val buttonEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(chromeLens, glassBlur) {
        {
            vibrancy()
            blur(glassBlur.dp.toPx())
            // 均衡及以下关闭折射
            if (chromeLens) lens(6f.dp.toPx(), 36f.dp.toPx())
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
            .fillMaxWidth()
            // 位移放外层：阴影(drawBehind)与玻璃一起跟手，拖动时阴影不脱落。
            .graphicsLayer {
                clip = false

                // 与下拉菜单面板共用同一套形变算法（computeDragTransform），跟手手感一致。
                // shapeAspectRatio = 1f：本按钮是 fillMaxWidth 的长条（360x40），
                // 按实测长宽比算 penaltyY ≈ 0.11，纵向拉伸会被砍到九分之一。
                val drag = computeDragTransform(
                    width = size.width,
                    height = size.height,
                    fraction = 1f,
                    pressProgress = interactiveHighlight.pressProgress,
                    offset = interactiveHighlight.offset,
                    density = this,
                    shapeAspectRatio = 1f,
                )
                translationX = drag.translationX
                translationY = drag.translationY
            }
            .drawBehind {
                val offsetY = 4.dp.toPx()
                val blurRadius = 10f * density
                val radius = cornerRadius.toPx()
                // 跨平台阴影：compose Path + miuix 的 expect/actual（android=BlurMaskFilter / skiko=Skia）
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, offsetY, size.width, size.height + offsetY),
                            cornerRadius = CornerRadius(radius, radius),
                        ),
                    )
                }
                drawBlurredPathShadow(
                    path = path,
                    color = shadowColor.copy(alpha = shadowAlpha),
                    // 原实现对 BlurMaskFilter 半径有 coerceAtLeast(0.1f)，保留
                    blurRadius = blurRadius.coerceAtLeast(0.1f),
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    enabled = enabled,
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
                        val drag = computeDragTransform(
                            width = size.width,
                            height = size.height,
                            fraction = 1f,
                            pressProgress = interactiveHighlight.pressProgress,
                            offset = interactiveHighlight.offset,
                            density = this,
                            shapeAspectRatio = 1f,
                        )
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                    },
                    onDrawSurface = buttonOnDrawSurface
                )
                .edgeLight(shape = buttonShape, edgeLight = rememberDefaultEdgeLight(baseColor = resolvedContainerColor))
                .then(interactiveHighlight.modifier)
                .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
                .defaultMinSize(minHeight = minHeight)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MiuixTheme.textStyles.button,
                fontWeight = FontWeight.Medium,
                color = textColor.copy(alpha = if (enabled) 1f else 0.4f),
                // 文案本身居中（父 Box 的 contentAlignment），但须**显式**指定 textAlign：
                // 父级对齐在测量期不可见，不传会先按左对齐落位，文案变长时旧字先贴左缘再滑回。
                textAlign = TextAlign.Center,
            )
        }
    }
}
