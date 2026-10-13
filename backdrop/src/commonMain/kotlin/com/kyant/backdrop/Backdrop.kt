package com.kyant.backdrop

import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density

interface Backdrop {

    val isCoordinatesDependent: Boolean

    /**
     * 共享降采样+预模糊层。
     * 保留仅为兼容：采样链路已回退到源库实现（每帧独立录制），
     * DrawBackdropNode 不再读取它。
     */
    val sharedSampledLayer: GraphicsLayer? get() = null

    /**
     * 源内容版本号。
     * 保留仅为兼容：采样链路已回退到源库实现（每帧独立录制），不再用它做跳过判断。
     */
    val contentVersion: Int get() = 0

    fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)? = null
    )
}
