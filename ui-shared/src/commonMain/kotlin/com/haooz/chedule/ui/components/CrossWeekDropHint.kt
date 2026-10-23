package com.haooz.chedule.ui.components

import com.haooz.chedule.ui.components.cardContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import com.haooz.chedule.ui.components.text.material.Text as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.haooz.chedule.ui.utils.rememberDefaultEdgeLight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.edgelight.edgeLight
import com.kyant.capsule.ContinuousCapsule
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 跨周调课提示胶囊：拖着课翻周时告诉用户松手会落到第几周。
 * [fromWeek] 为原周次（拖拽起点），[toWeek] 为当前浏览周（落点周）；两者不同即跨周调课，胶囊用主题色强调。
 * 玻璃效果与「回到今天/本周」悬浮按钮同一套：vibrancy + blur + 材质档位折射，底色始终主题色玻璃。
 */
@Composable
fun CrossWeekDropHint(
    fromWeek: Int,
    toWeek: Int,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val crossWeek = fromWeek > 0 && toWeek > 0 && fromWeek != toWeek
    val isLightTheme = !isAppDarkTheme()
    val baseAlpha = if (isLightTheme) 0.72f else 0.62f
    val containerColor = MiuixTheme.colorScheme.primary.copy(alpha = baseAlpha)
    val textColor = Color.White
    val hintShape: Shape = ContinuousCapsule()
    val chromeLens = AppMaterialSettings.chromeLensEnabled()
    val glassBlur = AppMaterialSettings.glassBlurDp()
    val edgeLight = rememberDefaultEdgeLight(baseColor = MiuixTheme.colorScheme.primary)
    val onDrawSurface: DrawScope.() -> Unit = remember {
        { drawRect(containerColor) }
    }

    Box(modifier = modifier.wrapContentSize(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { hintShape },
                    effects = {
                        vibrancy()
                        blur(glassBlur.dp.toPx())
                        if (chromeLens) lens(6f.dp.toPx(), 36f.dp.toPx())
                    },
                    highlight = null,
                    shadow = null,
                    onDrawSurface = onDrawSurface,
                )
                .edgeLight(shape = hintShape, edgeLight = edgeLight)
                // 尺寸补间与 CardExpand 同一条 spring
                .cardContentSize()
                .padding(horizontal = 18.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 跨周才出现的「第N周 →」淡入淡出，宽度由外层 animateContentSize 平滑展开
            AnimatedVisibility(
                visible = crossWeek,
                enter = fadeIn(tween(durationMillis = 220)),
                exit = fadeOut(tween(durationMillis = 160)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "第${fromWeek}周",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.72f),
                    )
                    Text(
                        text = "→",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.72f),
                    )
                }
            }
            Text(
                text = "第${toWeek}周",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
        }
    }
}

/**
 * 边缘自动翻周指示：拖着课停在屏幕左右边缘热区时，在那一侧铺一条渐隐色带 + 方向箭头，
 * 告诉用户停住就会继续翻周（见 [EDGE_HINT_WIDTH]）。
 *
 * 箭头用设置项同款 `MiuixIcons.Basic.ArrowRight`，左侧镜像。
 *
 * @param direction -1 上一周（左）/ 1 下一周（右）；0 时不画。
 */
@Composable
fun EdgeAutoPageIndicator(
    direction: Int,
    modifier: Modifier = Modifier,
) {
    if (direction == 0) return
    val accent = MiuixTheme.colorScheme.primary.copy(alpha = 0.34f)
    val brush = if (direction < 0) {
        Brush.horizontalGradient(listOf(accent, Color.Transparent))
    } else {
        Brush.horizontalGradient(listOf(Color.Transparent, accent))
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(EDGE_HINT_WIDTH)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    // 左侧用同一枚箭头镜像，保证两侧视觉重量一致
                    if (direction < 0) scaleX = -1f
                },
        )
    }
}

/** 边缘提示带宽度：略窄于 `CourseScheduleAppState.updateEdgeAutoPage` 的 32dp 触发热区，
 *  仅作视觉暗示；真正判定看卡片中心是否进热区。 */
val EDGE_HINT_WIDTH = 24.dp
