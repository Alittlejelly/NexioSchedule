/**
 * 全局文字变化动效：文字被改写时，旧字模糊淡出、新字从模糊中聚焦，两者交叉重叠。
 *
 * ## 三条硬约束
 *
 * 1. **稳态零代价**：不动画时 Box 与 graphicsLayer **整块不挂载**，调用方的 modifier
 *    原样交给唯一的那个 Text，节点数与改造前完全相同。动画进度只在**绘制期**读取，
 *    过渡期间也不触发重组。
 * 2. **首帧不播**：首次组合只是「出现」，不是「变化」。不记住初值的话，列表滚动 / 页面
 *    进入时每个字都会糊一下，看起来像渲染故障。
 * 3. **批量降级**：整屏同帧全变时由 [TextMotionBudget] 卡住并发数，超出的直接换字。
 *
 * ⚠ 状态一律在协程里写，**绝不在组合期写**。这个包装器要盖住全部 376 处 Text，
 * 组合期写状态一旦哪里不收敛就是全局性的重组风暴，排查成本远超收益。
 */
package com.haooz.chedule.ui.components.text

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import kotlin.math.abs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.isRenderEffectSupported

/** 交叉淡入总时长（ms）。 */
private const val TEXT_MOTION_MILLIS = 460

/**
 * 模糊半径上限（dp）。
 *
 * 只糊到这个量级有原因：`renderEffect` 每处都要建一份离屏缓冲，半径越大离屏面积越大。
 */
private val TEXT_MOTION_BLUR = 8.dp

/**
 * 把文字包成「变化时有交叉模糊过渡」的容器，[key] 是判断「文字是否变了」的依据。
 *
 * **布局契约**：调用方**不要**再把自己的 modifier 直接贴到 Text 上，交给本函数的 [modifier]。
 * 稳态时它被原样转交给唯一那个 Text（节点数与改造前一致）；过渡期间它挂在叠放容器上，
 * 保证 `weight` / `fillMaxWidth` 这类父级布局语义不因多了一层而错位。
 *
 * @param key 用于比较的文字内容，相等即视为「没变」。
 * @param content 绘制文字：参数是要画的文字，以及应施加于该 Text 的修饰。
 */
@Composable
internal fun <T : Any> TextMotionBox(
    key: T,
    modifier: Modifier = Modifier,
    content: @Composable (text: T, modifier: Modifier) -> Unit,
) {
    // 当前该画的新文字。
    var displayed by remember { mutableStateOf(key) }
    // 正在淡出的旧文字；非 null 即处于过渡态。
    var outgoing by remember { mutableStateOf<T?>(null) }
    val progress = remember { Animatable(1f) }
    // 平台不支持 RenderEffect（Android 12 以下）时全程退化为直接换字。
    val supported = remember { isRenderEffectSupported() }
    // 取消时要把字落回**最新**内容，而不是本协程启动时捕获的那个 key
    val latestKey by rememberUpdatedState(key)

    LaunchedEffect(key) {
        if (key == displayed) return@LaunchedEffect
        // 不支持模糊、或并发配额已满：直接换字，不播动效（批量降级）。
        if (!supported || !TextMotionBudget.tryAcquire()) {
            displayed = key
            outgoing = null
            return@LaunchedEffect
        }
        try {
            progress.snapTo(0f)
            outgoing = displayed
            displayed = key
            progress.animateTo(1f, tween(TEXT_MOTION_MILLIS, easing = FastOutSlowInEasing))
        } finally {
            // 正常结束与被取消（文字又变了 / 组件被移除）都要回到稳态并归还配额。
            if (outgoing != null) {
                // 被取消时把字直接落到最新值，避免停在某个半透明中间态。
                displayed = latestKey
                outgoing = null
            }
            TextMotionBudget.release()
        }
    }

    if (outgoing == null) {
        // 稳态：不套额外节点，调用方的 modifier 原样落到唯一的 Text 上。
        content(displayed, modifier)
        return
    }

    // 过渡态：旧字与新字各占一个独立 Box，二者叠在同一原点；模糊只在**最外层**套一次
    Layout(
        modifier = modifier.then(
            Modifier.graphicsLayer {
                // 绘制期读动画值：只失效本图层，不连累上层重组。
                val p = progress.value
                // 钟形：0 → 1 → 0。两层共用这一个半径。
                val bell = 1f - abs(2f * p - 1f)
                val blurPx = TEXT_MOTION_BLUR.toPx() * bell
                renderEffect = if (blurPx > 0.01f && supported) {
                    BlurEffect(blurPx, blurPx, TileMode.Decal)
                } else null
            }
        ),
        content = {
            Box(
                Modifier.graphicsLayer {
                    alpha = 1f - progress.value
                }
            ) {
                content(outgoing!!, Modifier)
            }
            Box(
                Modifier.graphicsLayer {
                    alpha = progress.value
                }
            ) {
                content(displayed, Modifier)
            }
        },
    ) { measurables, constraints ->
        val outPlaceable = measurables[0].measure(constraints)
        val inPlaceable = measurables[1].measure(constraints)

        layout(
            maxOf(outPlaceable.width, inPlaceable.width),
            maxOf(outPlaceable.height, inPlaceable.height),
        ) {
            outPlaceable.place(0, 0)
            inPlaceable.place(0, 0)
        }
    }
}
