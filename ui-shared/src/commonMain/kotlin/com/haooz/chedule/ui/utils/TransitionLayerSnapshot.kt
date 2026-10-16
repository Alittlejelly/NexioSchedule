package com.haooz.chedule.ui.utils

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale

/** A retained drawing layer, optionally restricted to a region in its local pixel coordinates. */
data class TransitionLayerSnapshot(
    val layer: GraphicsLayer,
    val bounds: Rect? = null,
)

/** Image layout and scaling semantics without allocating or rasterizing a bitmap. */
@Composable
fun TransitionLayerImage(
    snapshot: TransitionLayerSnapshot,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.TopCenter,
) {
    val painter = remember(snapshot) {
        object : Painter() {
            private val sourceBounds: Rect
                get() = snapshot.bounds ?: Rect(
                    0f,
                    0f,
                    snapshot.layer.size.width.toFloat(),
                    snapshot.layer.size.height.toFloat(),
                )

            override val intrinsicSize: Size
                get() = sourceBounds.size.takeIf { it.width > 0f && it.height > 0f }
                    ?: Size.Unspecified

            override fun DrawScope.onDraw() {
                val source = sourceBounds
                if (source.width <= 0f || source.height <= 0f) return
                clipRect {
                    scale(size.width / source.width, size.height / source.height, pivot = Offset.Zero) {
                        translate(-source.left, -source.top) {
                            drawLayer(snapshot.layer)
                        }
                    }
                }
            }
        }
    }
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier.clipToBounds(),
        contentScale = contentScale,
        alignment = alignment,
    )
}
