package com.kyant.backdrop.effects

import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.isRenderEffectSupported

fun BackdropEffectScope.blur(
    @FloatRange(from = 0.0) radius: Float,
    edgeTreatment: TileMode = TileMode.Clamp
) {
    if (!isRenderEffectSupported()) return
    if (radius <= 0f) return

    if (edgeTreatment != TileMode.Clamp || renderEffect != null) {
        if (radius > padding) {
            padding = radius
        }
    }

    // 模糊作用在降采样缓冲上，半径需同步 ×该缓冲的实际比例，使视觉扩散范围保持一致。
    //
    // ⚠ 这里必须取 scope 上的**实际** downsampleScale，不能用编译期常量：
    // 降采样早已回退、缓冲是 1:1 全分辨率（downsampleScale 恒为 1f），常量残留会把
    // 所有「卡片自模糊」路径的半径缩到 42% —— 长按浮层因此明显比网格卡清。
    // 同一目录下 Lens.kt 用的就是这个 scope 字段，两边必须一致。
    val scaledRadius = radius * downsampleScale
    renderEffect =
        BlurEffect(
            renderEffect,
            scaledRadius,
            scaledRadius,
            edgeTreatment
        )
}
