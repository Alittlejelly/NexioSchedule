package com.kyant.backdrop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

sealed interface BackdropEffectScope : Density, RuntimeShaderCache {

    val size: Size

    /**
     * 当前离屏模糊缓冲的降采样比例。
     *
     * 采样链路已回退到源库实现：缓冲是 1:1 全分辨率（只外扩 padding），
     * 因此固定为 1f —— effect（尤其 lens 的 runtimeShader）的像素 uniform
     * 不再需要缩放。保留这个字段只是为了让 lens()/blur() 不用改签名。
     *
     * ⚠ **effect 里凡是要按像素缩放 uniform/半径的，一律读这个字段，
     * 不要写死常量。** 这里曾经有个 `DOWNSAMPLE_SCALE = 0.42f` 的残留常量被
     * blur() 乘上去，降采样回退后没跟着删，所有「卡片自模糊」路径的半径都被
     * 缩到 42% —— 长按浮层因此明显比网格卡清，而 n=1 下看不出任何异常。
     * 常量已删；要恢复降采样时改这个字段即可，effect 会自动跟随。
     */
    val downsampleScale: Float

    val layoutDirection: LayoutDirection

    val shape: Shape

    var padding: Float

    var renderEffect: RenderEffect?

    /** lens() 产生的 RuntimeShader，供 ImageShader 直采折射路径复用 */
    var lensShader: RuntimeShader?

    /** 全分辨率直采用的折射参数（屏幕像素） */
    var lensRefractionHeight: Float
    var lensRefractionAmount: Float
    var lensCornerRadii: FloatArray?
}

internal abstract class BackdropEffectScopeImpl : BackdropEffectScope, RuntimeShaderCache {

    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var size: Size = Size.Unspecified
    override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override var padding: Float = 0f
    override var renderEffect: RenderEffect? = null
    override var lensShader: RuntimeShader? = null
    override var lensRefractionHeight: Float = 0f
    override var lensRefractionAmount: Float = 0f
    override var lensCornerRadii: FloatArray? = null
    override val downsampleScale: Float = 1f

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaderCache.obtainRuntimeShader(key, string)
    }

    fun update(scope: DrawScope): Boolean {
        val newDensity = scope.density
        val newFontScale = scope.fontScale
        val newSize = scope.size
        val newLayoutDirection = scope.layoutDirection

        val changed = newDensity != density ||
                newFontScale != fontScale ||
                newSize != size ||
                newLayoutDirection != layoutDirection

        if (changed) {
            density = newDensity
            fontScale = newFontScale
            size = newSize
            layoutDirection = newLayoutDirection
        }

        return changed
    }

    fun apply(effects: BackdropEffectScope.() -> Unit) {
        padding = 0f
        renderEffect = null
        lensShader = null
        lensRefractionHeight = 0f
        lensRefractionAmount = 0f
        lensCornerRadii = null
        effects()
    }

    fun reset() {
        density = 1f
        fontScale = 1f
        size = Size.Unspecified
        layoutDirection = LayoutDirection.Ltr
        padding = 0f
        renderEffect = null
        lensShader = null
        lensRefractionHeight = 0f
        lensRefractionAmount = 0f
        lensCornerRadii = null
        runtimeShaderCache.clear()
    }
}
