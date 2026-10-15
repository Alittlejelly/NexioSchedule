package com.kyant.backdrop.backdrops

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.internal.InverseLayerScope
import com.kyant.backdrop.isRenderEffectSupported
import kotlin.math.roundToInt

/**
 * 烘焙层的分辨率比例。1f = 与源层同分辨率。
 *
 * 降采样是安全的，因为模糊**只烘焙一次**：[SharedBlurRecorderNode] 先把源录进
 * 带 RenderEffect 的中间层 blurLayer，再把结果烘进 `renderEffect = null` 的
 * bakedLayer。采样侧 `drawLayer(baked)` 是一次纯纹理 blit，不重跑高斯模糊。
 *
 * ⚠ 早期版本踩过的坑：那会儿 RenderEffect 直接挂在共享层上，而 RenderEffect 是
 * **每次 draw 都重算**的 —— 几十张课卡每帧各 drawLayer 同一张层，等于全屏模糊
 * 每帧跑几十次，降采样省下的采样成本全被吃光，看起来"降采样没用"。
 * 现架构已拆开两层，改这个常量前请先确认那条性质还成立。
 *
 * 0.5f 下 1080×2400 的纹理从 10.4MB 降到 2.6MB，能进 GPU 的 L2；源图本就是
 * 模糊过的壁纸，放大回来看不出差别。
 *
 * ⚠ **当前保持 1f，不要直接调小。** n≠1 的采样变换还没在真机上验证通过
 * （见 [SharedBlurBackdrop.drawBackdrop] 里那段关于 pivot 与拼接顺序的注释），
 * 两处默认值陷阱都是静默的：n=1 时整条分支是死代码，怎么改都看不出症状，
 * 一旦 n≠1 就同时引爆。降采样要重新上，必须先在真机量出对齐结果。
 */
const val BLUR_BAKE_SCALE = 1f

/**
 * 共享预模糊 Backdrop。
 *
 * 壁纸只模糊**一次**（预渲染时把模糊结果烘焙进 [blurredLayer]），
 * 之后所有课程卡片都从这张图上裁剪采样 —— 运行时高斯模糊开销为 0。
 * @param source 原始壁纸 LayerBackdrop（用于降级和坐标获取）
 */
@Stable
class SharedBlurBackdrop(
    internal val source: LayerBackdrop
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    /**
     * 烘焙好的模糊壁纸层。模糊已固化，drawLayer 时不再有模糊运算。
     * 由预渲染 Modifier 在壁纸/模糊半径变化时重录。
     */
    override var blurredLayer: GraphicsLayer? by mutableStateOf(null)
        internal set

    /**
     * 烘焙层相对源层的缩放比例（< 1 表示降采样烘焙）。
     *
     * 采样侧会按 1/n 放大回全屏坐标，所以它对按压缩放的反向补偿**没有影响**
     * （那条链路用的是节点尺寸，跟这张层的分辨率无关）。
     * 降采样的收益是实打实的：几十张卡随机采样同一张层，纹理面积小 → 采样带宽和
     * cache 局部性一起改善；而内容本来就是模糊的，放大回来看不出差别。
     */
    var blurredScale: Float = BLUR_BAKE_SCALE
        internal set

    // 原 fork 里的 `var sampledBitmap: android.graphics.Bitmap?` 已删除：
    // 它是平台专属类型，无法进 commonMain；而全项目（:app 与本模块）零引用，
    // 属于未接线的预留字段。将来若真要做「ImageShader 直采折射」，
    // 请改用 expect/actual 的平台位图类型再补回来。

    /** 共享层像素内容版本：每次重录源层时递增，供采样侧跳过无变化重录 */
    override var contentVersion: Int = 0
        internal set

    /**
     * 源 Backdrop 的坐标（壁纸层在视图树中的位置）。
     * 供 DrawBackdropNode 计算卡片在壁纸中的偏移量。
     */
    val sourceLayerCoordinates: LayoutCoordinates?
        get() = source.layerCoordinates

    private var inverseLayerScope: InverseLayerScope? = null

    fun release() {
        // GraphicsLayer cleanup is handled by SharedBlurRecorderNode.onDetach()
        blurredLayer = null
    }

    /**
     * 创建预渲染 Modifier。在指定 Composable 的 draw 阶段
     * 把壁纸源模糊一次并**烘焙**进 blurredLayer。
     *
     * 该 Modifier 应附加到壁纸 Box 之后的不可见 Box 上，
     * 确保 source.graphicsLayer 已被录制。
     *
     * @param blurRadiusPx 模糊半径（px）
     * @param downsampleScale 烘焙分辨率比例，默认 1f（全分辨率）
     */
    /**
     * @param sourceKey 源内容指纹（壁纸 bitmap / 缩放 / 偏移 / 亮度 / 主题色等）。
     *   它没变时说明源层内容和上一帧逐像素相同，可以整段跳过重新烘焙——
     *   滑动课表、开关弹窗这类场景下壁纸是静止的，重烘焙纯属白烧。
     *   注意：指纹必须覆盖所有会改变源层内容的因素，否则会用到过期采样。
     */
    fun preRenderModifier(
        blurRadiusPx: Float,
        downsampleScale: Float = BLUR_BAKE_SCALE,
        sourceKey: Any? = null
    ): Modifier {
        return SharedBlurRecorderElement(this, blurRadiusPx, downsampleScale, sourceKey)
    }

    /**
     * 采样入口：画**已烘焙的模糊层**（模糊开销为 0）；
     * 还没烘焙好时退回原始源层，保证不会出现空白。
     */
    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val coordinates = coordinates ?: return
        val layerCoordinates = source.layerCoordinates ?: return

        val baked = blurredLayer
        // 烘焙过了才按它的分辨率放大；降级到原始源层时源层是全分辨率，不用缩放
        val n = if (baked != null) blurredScale else 1f

        withTransform({
            if (layerBlock != null) {
                with(obtainInverseLayerScope()) { inverseTransform(density, layerBlock) }
            }
            if (n != 1f) {
                scale(1f / n, 1f / n, Offset.Zero)
            }
            val offset = try {
                layerCoordinates.localPositionOf(coordinates)
            } catch (_: Exception) {
                coordinates.positionInWindow() - layerCoordinates.positionInWindow()
            }
            translate(-offset.x, -offset.y)
        }) {
            drawLayer(baked ?: source.graphicsLayer)
        }
    }

    private fun obtainInverseLayerScope(): InverseLayerScope {
        return inverseLayerScope?.apply { reset() }
            ?: InverseLayerScope().also { inverseLayerScope = it }
    }
}

// --- 预渲染 Modifier 实现 ---

private class SharedBlurRecorderElement(
    private val sharedBackdrop: SharedBlurBackdrop,
    private val blurRadiusPx: Float,
    private val downsampleScale: Float = BLUR_BAKE_SCALE,
    private val sourceKey: Any? = null
) : ModifierNodeElement<SharedBlurRecorderNode>() {

    override fun create(): SharedBlurRecorderNode {
        return SharedBlurRecorderNode(sharedBackdrop, blurRadiusPx, downsampleScale, sourceKey)
    }

    override fun update(node: SharedBlurRecorderNode) {
        // 源内容相关参数变化才需要重录降采样层；
        // 仅 blur 半径变化时只刷新 renderEffect，避免壁纸层被无意义重录。
        val sourceChanged = node.sharedBackdrop !== sharedBackdrop ||
            node.downsampleScale != downsampleScale ||
            node.sourceKey != sourceKey
        node.sharedBackdrop = sharedBackdrop
        node.blurRadiusPx = blurRadiusPx
        node.downsampleScale = downsampleScale
        node.sourceKey = sourceKey
        if (sourceChanged) {
            node.markNeedsRecord()
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "SharedBlurRecorder"
        properties["sharedBackdrop"] = sharedBackdrop
        properties["blurRadiusPx"] = blurRadiusPx
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SharedBlurRecorderElement) return false
        return sharedBackdrop == other.sharedBackdrop &&
            blurRadiusPx == other.blurRadiusPx &&
            downsampleScale == other.downsampleScale &&
            sourceKey == other.sourceKey
    }

    override fun hashCode(): Int {
        var result = sharedBackdrop.hashCode()
        result = 31 * result + blurRadiusPx.hashCode()
        result = 31 * result + downsampleScale.hashCode()
        result = 31 * result + (sourceKey?.hashCode() ?: 0)
        return result
    }
}

private class SharedBlurRecorderNode(
    var sharedBackdrop: SharedBlurBackdrop,
    var blurRadiusPx: Float,
    var downsampleScale: Float = BLUR_BAKE_SCALE,
    var sourceKey: Any? = null
) : DrawModifierNode, Modifier.Node() {

    /**
     * 中间层：源内容 + BlurEffect。只用于烘焙，不直接给采样侧用
     * （RenderEffect 是 drawLayer 时才应用的，直接采样会每张卡重跑一次全屏模糊）。
     */
    private var blurLayer: GraphicsLayer? = null

    /** 持久层：烘焙结果，模糊已固化，无 RenderEffect。 */
    private var bakedLayer: GraphicsLayer? = null

    private var needsRecord = true
    private var recordedW = 0
    private var recordedH = 0
    private var recordedBlurRadius = Float.NaN

    /** 烘焙时**实际达成**的分辨率比例（可能与请求的 downsampleScale 有取整偏差） */
    private var recordedScale = Float.NaN

    fun markNeedsRecord() { needsRecord = true }

    override fun ContentDrawScope.draw() {
        drawContent()

        if (!isRenderEffectSupported()) return

        val blur = blurLayer ?: return
        val baked = bakedLayer ?: return

        val targetW = (size.width * downsampleScale).roundToInt().coerceAtLeast(1)
        val targetH = (size.height * downsampleScale).roundToInt().coerceAtLeast(1)

        val actualScale = if (size.width > 0f) targetW.toFloat() / size.width else 1f

        // 只在半径/比例真正变化时赋值，避免每帧 new BlurEffect 让图层反复失效
        if (recordedBlurRadius != blurRadiusPx || recordedScale != actualScale) {
            recordedBlurRadius = blurRadiusPx
            recordedScale = actualScale
            blur.renderEffect = if (blurRadiusPx > 0f) {
                // 模糊作用在降采样后的缓冲上，半径同步 ×比例，放大回原尺寸后扩散范围不变
                val scaledBlur = blurRadiusPx * actualScale
                BlurEffect(
                    null,
                    scaledBlur,
                    scaledBlur,
                    TileMode.Clamp
                )
            } else null
            needsRecord = true
        }

        // 源内容、模糊半径、目标尺寸都没变 → 复用上一帧的烘焙结果，跳过整段模糊
        if (needsRecord || recordedW != targetW || recordedH != targetH) {
            val sourceLayer = sharedBackdrop.source.graphicsLayer

            // 1) 源 -> 中间层
            blur.record(IntSize(targetW, targetH)) {
                drawContext.canvas.save()
                drawContext.canvas.scale(actualScale, actualScale)
                drawLayer(sourceLayer)
                drawContext.canvas.restore()
            }

            // 2) 烘焙：把带 BlurEffect 的中间层画进持久层，模糊就此固化
            baked.record(IntSize(targetW, targetH)) {
                drawLayer(blur)
            }
            baked.renderEffect = null

            needsRecord = false
            recordedW = targetW
            recordedH = targetH

            sharedBackdrop.blurredLayer = baked
            sharedBackdrop.blurredScale = actualScale
            sharedBackdrop.contentVersion++
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        blurLayer = graphicsContext.createGraphicsLayer()
        bakedLayer = graphicsContext.createGraphicsLayer()
        needsRecord = true
        recordedBlurRadius = Float.NaN
        recordedScale = Float.NaN
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        blurLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            blurLayer = null
        }
        bakedLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            bakedLayer = null
        }
        sharedBackdrop.blurredLayer = null
        needsRecord = true
        recordedBlurRadius = Float.NaN
        recordedScale = Float.NaN
    }
}
