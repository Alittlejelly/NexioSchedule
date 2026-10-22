/**
 * 全局「单卡片高度变化」动效。
 *
 * ## 为什么高度动画放在这里，而不是 `Card` 的 `animateContentSize`
 *
 * `animateContentSize` 补间的是**占位尺寸**，子节点始终按最终尺寸绘制：
 * - 长高时靠裁切露出来，看着还行；
 * - 收起时内容已经按小尺寸摆好，剩下的是空白在缩 —— 观感是「内容瞬间消失、下面留白慢慢收」，
 *   也就是「关闭没有动画」。
 * - 再叠上 `Card` 的裁切，嵌套卡片之间还会因为两层尺寸不同步而错位重叠。
 *
 * 所以高度变化必须由 `AnimatedVisibility` 自己算：它退出期间**仍然保留内容**，
 * 逐帧把内容压到 0 高度，这才是「收得回去」。
 *
 * ## 用法
 *
 * - [CardExpand]：卡片里某段内容条件出现 / 消失。
 * - [Modifier.cardContentSize]：自绘容器（不是 miuix 的 `Card`）自己的测量尺寸变了时使用。
 *
 * ⚠ 同一段内容只用一种：`CardExpand` 里再套 `cardContentSize` 就是「动画的动画」，发肉。
 */
package com.haooz.chedule.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.ui.unit.dp
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import com.kyant.backdrop.isRenderEffectSupported
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import top.yukonga.miuix.kmp.basic.CardDefaults

/** 卡内高度变化的统一参数，改这里即全站生效。 */
object CardExpandSpec {

    /**
     * 高度补间曲线：**直接复用 `CardDefaults.SizeAnimation`**，与其它地方用的同一条 spring，
     * 避免两处参数漂移。
     *
     * `dampingRatio = 0.9` 只有极轻微回弹；`StiffnessMediumLow` 对 40~120dp 的行高变化
     * 大致落在 260~320ms，不必再写固定时长。
     */
    val HeightSpring: FiniteAnimationSpec<IntSize> = CardDefaults.SizeAnimation

    /** 高度变化时内容淡入淡出的时长（ms）：比 spring 短，免得「先亮后长」。 */
    const val FADE_MILLIS = 180

    /** 模糊半径（dp） */
    const val BLUR_DP = 12f

    /**
     * 展开时模糊散尽的时长（ms）。**比高度长**：高度先长到位，模糊再收尾变清晰
     * （高度 spring 大致 260~320ms，这里留 420ms 做拖尾）。
     */
    const val BLUR_IN_MILLIS = 420

    /**
     * 收起时模糊糊满的时长（ms）。**比高度短**：模糊先到位，之后高度继续缩，
     * 观感是「先糊住、再收起来」。
     */
    const val BLUR_OUT_MILLIS = 160
}

/**
 * 卡片里某段内容条件出现 / 消失。
 *
 * 展开：高度从 0 长到实际高度 + 淡入 + **模糊 12dp → 0**（从糊到清，且比高度晚散尽）；
 * 收回：内容一起压到 0 高度 + 淡出 + **模糊 0 → 12**（比高度更早糊满）。
 * 高度走 spring（[CardExpandSpec.HeightSpring]），模糊走两条固定时长
 * （[CardExpandSpec.BLUR_IN_MILLIS] / [CardExpandSpec.BLUR_OUT_MILLIS]），
 * 两者故意错开：进比高度慢、出比高度快。
 *
 * @param visible 该段内容是否呈现。
 * @param expandFrom 展开 / 收缩的锚点。默认从顶部开始长；文件夹展开这类需要内容跟着
 *   底边下滑的场景传 `Alignment.Bottom`。
 * @param fade 是否淡入淡出。个别地方想更干净可以关掉。
 */
@Composable
fun CardExpand(
    visible: Boolean,
    modifier: Modifier = Modifier,
    expandFrom: Alignment.Vertical = Alignment.Top,
    fade: Boolean = true,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(
            animationSpec = CardExpandSpec.HeightSpring,
            expandFrom = expandFrom,
        ) + if (fade) fadeIn(tween(CardExpandSpec.FADE_MILLIS)) else EnterTransition.None,
        exit = shrinkVertically(
            animationSpec = CardExpandSpec.HeightSpring,
            shrinkTowards = expandFrom,
        ) + if (fade) fadeOut(tween(CardExpandSpec.FADE_MILLIS)) else ExitTransition.None,
    ) {
        // 模糊随展开进度走：展开 12dp → 0（从糊到清），收起 0 → 12。
        // ⚠ 只在 `graphicsLayer` 绘制期读 `blurProgress.value`，组合期不读，整段动画零重组。
        val blurProgress = transition.animateFloat(
            transitionSpec = {
                // 展开（→ Visible）：模糊比高度散得慢，高度长完后再收尾变清晰；
                // 收起（→ PreExit）：模糊比高度先糊满，之后高度继续缩。
                if (targetState == EnterExitState.Visible) {
                    tween(CardExpandSpec.BLUR_IN_MILLIS, easing = FastOutSlowInEasing)
                } else {
                    tween(CardExpandSpec.BLUR_OUT_MILLIS, easing = FastOutSlowInEasing)
                }
            },
            label = "CardExpandBlur",
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
        // ⚠ 必须包一层 Column：`AnimatedVisibility` 内部的 Layout 会把**每个子项都
        // place 在 (0,0)**（它按「只有一个子内容」设计）。直接放多行内容会全部叠在
        // 同一个位置上，看起来像「好几个选项挤成一行」。
        Column(
            modifier = Modifier.graphicsLayer {
                val px = CardExpandSpec.BLUR_DP.dp.toPx() * (1f - blurProgress.value)
                renderEffect = if (px > 0.01f && isRenderEffectSupported()) {
                    BlurEffect(px, px, TileMode.Decal)
                } else null
            }
        ) {
            content()
        }
    }
}

/**
 * 容器自身高度变化（没有条件子节点，纯测量结果变了）时按同一条 spring 补间。
 *
 * 只给**自绘容器**用；miuix 的 `Card` 请改用 [CardExpand] 包住会变的那一段。
 *
 * @param alignment 尺寸变化时的对齐方向，默认从左上开始长。
 */
fun Modifier.cardContentSize(
    alignment: Alignment = Alignment.TopStart,
): Modifier = animateContentSize(
    animationSpec = CardExpandSpec.HeightSpring,
    alignment = alignment,
)
