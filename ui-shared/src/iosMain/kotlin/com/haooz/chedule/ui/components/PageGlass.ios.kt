package com.haooz.chedule.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.kyant.backdrop.backdrops.SharedBlurBackdrop

/**
 * 页级玻璃（iOS 实现）：不接管采样。
 *
 * iOS 侧走 Skiko 的 RuntimeEffect，这条合并采样管线只做了 Android 的 AGSL 版本；
 * 卡片保持原来的「每卡各自 drawBackdrop」路径，视觉与行为不变。
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
    // no-op：iOS 卡片继续各自采样
}
