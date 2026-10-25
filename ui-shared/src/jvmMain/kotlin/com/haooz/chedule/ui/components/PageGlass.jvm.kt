package com.haooz.chedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.kyant.backdrop.backdrops.SharedBlurBackdrop

/**
 * 页级玻璃（桌面实现）：不接管采样。
 *
 * 桌面端没有 AGSL 折射管线，卡片保持原来的「每卡各自 drawBackdrop」路径 —— 视觉与行为
 * 与本次改动前完全一致，只是拿不到合并采样的性能收益。
 */
@Composable
actual fun PageGlass(
    registry: GlassCardRegistry,
    backdrop: SharedBlurBackdrop,
    bakedLayer: GraphicsLayer?,
    bakeScale: Float,
    refractionHeight: Float,
    refractionAmount: Float,
    modifier: Modifier
) {
    // no-op：桌面卡片继续各自采样
}
