// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.basic

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.effects.edgelight.edgeLight
import com.haooz.chedule.ui.effects.edgelight.rememberDefaultEdgeLight
import com.haooz.chedule.ui.utils.AppMaterialSettings
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.anim.SinOutEasing
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// =====================================================================
// 工具函数
// =====================================================================

/**
 * 将对齐方向根据布局方向（LTR/RTL）进行解析。
 * 在RTL布局下，Start和End会互换，TopStart和TopEnd会互换，依此类推。
 */
private fun PopupPositionProvider.Align.resolve(layoutDirection: LayoutDirection): PopupPositionProvider.Align {
    if (layoutDirection == LayoutDirection.Ltr) return this
    return when (this) {
        PopupPositionProvider.Align.Start -> PopupPositionProvider.Align.End
        PopupPositionProvider.Align.End -> PopupPositionProvider.Align.Start
        PopupPositionProvider.Align.TopStart -> PopupPositionProvider.Align.TopEnd
        PopupPositionProvider.Align.TopEnd -> PopupPositionProvider.Align.TopStart
        PopupPositionProvider.Align.BottomStart -> PopupPositionProvider.Align.BottomEnd
        PopupPositionProvider.Align.BottomEnd -> PopupPositionProvider.Align.BottomStart
    }
}

/**
 * 安全创建TransformOrigin，处理NaN和负值情况。
 * 如果值无效则返回0，否则返回原始值。
 */
internal fun safeTransformOrigin(x: Float, y: Float): TransformOrigin {
    val safeX = if (x.isNaN() || x < 0f) 0f else x
    val safeY = if (y.isNaN() || y < 0f) 0f else y
    return TransformOrigin(safeX, safeY)
}

/**
 * 从 [PopupPositionResult] 和实际偏移量推导 [PopupLayoutPosition] 和本地 [TransformOrigin]。
 * 在 composition 和 layout 阶段均可调用，保证方向和锚点始终一致。
 */
internal fun resolvePopupAnchors(
    positionResult: PopupPositionResult,
    calculatedOffset: IntOffset,
    popupContentSize: IntSize,
    parentBounds: IntRect,
    alignment: PopupPositionProvider.Align,
    layoutDirection: LayoutDirection,
): Pair<PopupLayoutPosition, TransformOrigin> {
    val isRightAligned = when (alignment.resolve(layoutDirection)) {
        PopupPositionProvider.Align.End,
        PopupPositionProvider.Align.TopEnd,
        PopupPositionProvider.Align.BottomEnd,
        -> true
        else -> false
    }
    val distLeft = abs(calculatedOffset.x - parentBounds.left)
    val distRight = abs((calculatedOffset.x + popupContentSize.width) - parentBounds.right)
    val rightAligned = if (popupContentSize.width > 0) distRight < distLeft else isRightAligned

    val layoutPos = PopupLayoutPosition(
        showBelow = positionResult.showBelow,
        showAbove = positionResult.showAbove,
        isRightAligned = rightAligned,
    )
    val origin = TransformOrigin(
        pivotFractionX = if (rightAligned) 1f else 0f,
        pivotFractionY = if (positionResult.showAbove) 1f else 0f,
    )
    return layoutPos to origin
}

// =====================================================================
// 常量 - 用于ListPopupColumn的测量策略
// =====================================================================

/** 计算宽度时考虑的最大子项数量 */
private const val MAX_ITEMS_FOR_WIDTH = 8
/** 计算高度时考虑的最大子项数量 */
private const val MAX_ITEMS_FOR_HEIGHT = 8

// =====================================================================
// ListPopupColumn - 弹窗内容列组件
// =====================================================================

/**
 * 弹窗内容列，自动将宽度对齐到最宽的子项。
 *
 * 功能说明：
 * - 自动计算宽度：取前8个子项的最大固有宽度，限制在200dp~288dp之间
 * - 支持垂直滚动
 * - 使用自定义MeasurePolicy进行精确的宽度控制
 *
 * @param content 弹窗内容子项
 */
@Composable
fun ListPopupColumn(
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()

    val measurePolicy = remember {
        object : MeasurePolicy {
            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints,
            ): MeasureResult {
                // 宽度范围：200dp ~ 288dp
                val minPx = 200.dp.roundToPx()
                val maxPx = 288.dp.roundToPx()
                val widthCount = min(MAX_ITEMS_FOR_WIDTH, measurables.size)
                var maxIntrinsic = 0
                for (i in 0 until widthCount) {
                    val w = measurables[i].maxIntrinsicWidth(constraints.maxHeight)
                    if (w > maxIntrinsic) maxIntrinsic = w
                }
                val parentMin = constraints.minWidth
                val parentMax = constraints.maxWidth
                val upper = maxOf(maxPx, parentMin).coerceAtMost(parentMax)
                val lower = maxOf(minPx, parentMin).coerceAtMost(upper)
                val listWidth = maxIntrinsic.coerceIn(lower, upper)

                // 使用计算出的宽度测量所有子项
                val childConstraints = constraints.copy(minWidth = listWidth, maxWidth = listWidth, minHeight = 0)

                val placeables = ArrayList<Placeable>(measurables.size)
                var listHeight = 0
                for (i in measurables.indices) {
                    val p = measurables[i].measure(childConstraints)
                    placeables.add(p)
                    listHeight += p.height
                }

                return layout(listWidth, listHeight) {
                    var currentY = 0
                    for (i in placeables.indices) {
                        val p = placeables[i]
                        p.placeRelative(0, currentY)
                        currentY += p.height
                    }
                }
            }

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int,
            ): Int {
                val minPx = 200.dp.roundToPx()
                val maxPx = 288.dp.roundToPx()
                val widthCount = min(MAX_ITEMS_FOR_WIDTH, measurables.size)
                var maxIntrinsic = 0
                for (i in 0 until widthCount) {
                    val w = measurables[i].maxIntrinsicWidth(Int.MAX_VALUE)
                    if (w > maxIntrinsic) maxIntrinsic = w
                }
                val listWidth = maxIntrinsic.coerceIn(minPx, maxPx)

                val heightCount = min(MAX_ITEMS_FOR_HEIGHT, measurables.size)
                var height = 0
                for (i in 0 until heightCount) {
                    height += measurables[i].minIntrinsicHeight(listWidth)
                }
                return height
            }
        }
    }

    Layout(
        content = content,
        modifier = Modifier
            .focusGroup()
            .height(IntrinsicSize.Min)
            .verticalScroll(state = scrollState),
        measurePolicy = measurePolicy,
    )
}

// =====================================================================
// PopupPositionProvider - 弹窗位置提供者接口
// =====================================================================

/**
 * 弹窗位置提供者接口。
 * 负责计算弹窗相对于锚点（触发组件）的显示位置。
 *
 * 注意：位置是相对于窗口计算的，不是相对于锚点！
 */
/**
 * 弹窗位置计算结果，包含偏移量和展开方向。
 *
 * @param offset 弹窗左上角在窗口坐标系中的偏移量
 * @param showBelow 弹窗是否在锚点下方展开
 * @param showAbove 弹窗是否在锚点上方展开
 */
@Immutable
data class PopupPositionResult(
    val offset: IntOffset,
    val showBelow: Boolean,
    val showAbove: Boolean,
)

@Stable
interface PopupPositionProvider {
    /**
     * 计算弹窗的位置（偏移量）和展开方向。
     *
     * @param anchorBounds 锚点（父组件）的边界
     * @param windowBounds 窗口安全区域的边界（排除状态栏、导航栏、刘海等）
     * @param layoutDirection 布局方向（LTR/RTL）
     * @param popupContentSize 弹窗内容的实际大小
     * @param popupMargin 弹窗的额外边距
     * @param alignment 弹窗相对于窗口的对齐方式
     */
    fun calculatePosition(
        anchorBounds: IntRect,
        windowBounds: IntRect,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
        popupMargin: IntRect,
        alignment: Align,
    ): PopupPositionResult

    /**
     * 获取弹窗的额外边距
     */
    fun getMargins(): PaddingValues

    /**
     * 弹窗相对于窗口的对齐方式（不是相对于锚点！）
     */
    enum class Align {
        Start,      // 左对齐（RTL下为右对齐）
        End,        // 右对齐（RTL下为左对齐）
        TopStart,   // 左上角
        TopEnd,     // 右上角
        BottomStart,// 左下角
        BottomEnd,  // 右下角
    }
}

// =====================================================================
// ListPopupDefaults - 弹窗默认配置
// =====================================================================

/**
 * 弹窗的默认配置对象。
 * 包含动画参数、尺寸限制、位置提供者等。
 */
object ListPopupDefaults {
    // ---- 动画参数 ----

    /**
     * 进入时的缩放动画（较慢，stiffness较小）
     * - dampingRatio: 阻尼比，控制弹簧的弹性程度（0.82 = 适中的弹性）
     * - stiffness: 刚度，控制弹簧的硬度（200 = 较慢）
     */
    val FractionEnterAnimationSpec = spring(dampingRatio = 0.78f, stiffness = 232f, visibilityThreshold = 0.0001f)

    /**
     * 退出时的缩放动画（使用原始弹簧参数）
     * - dampingRatio: 阻尼比，控制弹簧的弹性程度（0.82 = 适中的弹性）
     * - stiffness: 刚度，控制弹簧的硬度（362.5 = 中等速度）
     */
    val FractionExitAnimationSpec = spring(dampingRatio = 0.78f, stiffness = 400f, visibilityThreshold = 0.0001f)

    /** 通用缩放动画（兼容库引用，等同于进入动画） */
    val FractionAnimationSpec = FractionEnterAnimationSpec

    /** 进入时的透明度动画（150ms渐入） */
    val AlphaEnterAnimationSpec = tween<Float>(durationMillis = 120)

    /** 退出时的透明度动画（300ms渐出） */
    val AlphaExitAnimationSpec = tween<Float>(durationMillis = 320)

    /** 背景变暗的进入动画（200ms，使用SinOut缓动） */
    val DimEnterAnimationSpec = tween<Float>(durationMillis = 200, easing = SinOutEasing)

    /** 背景变暗的退出动画（300ms，使用SinOut缓动） */
    val DimExitAnimationSpec = tween<Float>(durationMillis = 300, easing = SinOutEasing)

    /** 手势重置动画（弹簧效果，用于返回手势后恢复弹窗状态） */
    val ResetAnimationSpec = spring(dampingRatio = 0.82f, stiffness = 362.5f, visibilityThreshold = 0.0001f)

    // ---- 尺寸限制 ----

    /** 弹窗最小宽度（200dp） */
    val MinWidth = 200.dp

    /** 弹窗测量时的最小高度（50dp），用作maxHeight和minHeight约束的下限 */
    val MinPopupHeight = 50.dp

    // ---- 位置提供者 ----

    /**
     * 创建下拉式位置提供者。
     * 弹窗会覆盖锚点文字显示（弹窗上端或下端与锚点对齐）。
     *
     * @param verticalMargin 弹窗与锚点之间的垂直间距（默认0dp，覆盖模式）
     * @param horizontalMargin 弹窗的水平边距（默认0dp）
     */
    fun dropdownPositionProvider(
        verticalMargin: Dp = 0.dp,
        horizontalMargin: Dp = 0.dp,
    ): PopupPositionProvider = object : PopupPositionProvider {
        private val margins = PaddingValues(horizontal = horizontalMargin, vertical = verticalMargin)

        override fun calculatePosition(
            anchorBounds: IntRect,
            windowBounds: IntRect,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
            popupMargin: IntRect,
            alignment: PopupPositionProvider.Align,
        ): PopupPositionResult {
            val offsetXDelta = 82  //@ 3x density
            val offsetYDelta = 94  //@ 3x density

            // 计算X偏移（左对齐或右对齐，往右偏移）
            val offsetX = if (alignment.resolve(layoutDirection) == PopupPositionProvider.Align.End) {
                anchorBounds.right - popupContentSize.width - popupMargin.right + offsetXDelta
            } else {
                anchorBounds.left + popupMargin.left + offsetXDelta
            }

            // 计算Y偏移并记录展开方向
            val spaceBelow = windowBounds.bottom - anchorBounds.bottom
            val spaceAbove = anchorBounds.top - windowBounds.top
            val offsetY: Int
            val showBelow: Boolean
            val showAbove: Boolean
            if (spaceBelow > popupContentSize.height) {
                // 显示在下方：弹窗上端与锚点上端对齐，往上偏移
                offsetY = anchorBounds.top - offsetYDelta
                showBelow = true
                showAbove = false
            } else if (spaceAbove > popupContentSize.height) {
                // 显示在上方：弹窗下端与锚点下端对齐，往下偏移
                offsetY = anchorBounds.bottom - popupContentSize.height + offsetYDelta
                showBelow = false
                showAbove = true
            } else {
                // 居中显示
                offsetY = anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2
                showBelow = false
                showAbove = false
            }

            val clampedOffset = IntOffset(
                x = offsetX.coerceIn(
                    windowBounds.left,
                    (windowBounds.right - popupContentSize.width - popupMargin.right).coerceAtLeast(windowBounds.left),
                ),
                y = offsetY.coerceIn(
                    (windowBounds.top + popupMargin.top).coerceAtMost(windowBounds.bottom - popupContentSize.height - popupMargin.bottom),
                    windowBounds.bottom - popupContentSize.height - popupMargin.bottom,
                ),
            )
            return PopupPositionResult(clampedOffset, showBelow, showAbove)
        }

        override fun getMargins(): PaddingValues = margins
    }

    /** 默认的下拉位置提供者（verticalMargin=8dp, horizontalMargin=0dp） */
    val DropdownPositionProvider: PopupPositionProvider = dropdownPositionProvider()

    /**
     * 右键菜单/上下文菜单的位置提供者。
     * 弹窗会锚定到锚点的某个角上。
     *
     * 注意：目前此实现与dropdownPositionProvider逻辑相同，可能需要根据需求调整。
     */
    val ContextMenuPositionProvider = object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowBounds: IntRect,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
            popupMargin: IntRect,
            alignment: PopupPositionProvider.Align,
        ): PopupPositionResult {
            val offsetX: Int
            val offsetY: Int
            val showBelow: Boolean
            val showAbove: Boolean
            when (alignment.resolve(layoutDirection)) {
                PopupPositionProvider.Align.TopStart -> {
                    offsetX = anchorBounds.left + popupMargin.left
                    offsetY = anchorBounds.bottom + popupMargin.top
                    showBelow = true
                    showAbove = false
                }
                PopupPositionProvider.Align.TopEnd -> {
                    offsetX = anchorBounds.right - popupContentSize.width - popupMargin.right
                    offsetY = anchorBounds.bottom + popupMargin.top
                    showBelow = true
                    showAbove = false
                }
                PopupPositionProvider.Align.BottomStart -> {
                    offsetX = anchorBounds.left + popupMargin.left
                    offsetY = anchorBounds.top - popupContentSize.height - popupMargin.bottom
                    showBelow = false
                    showAbove = true
                }
                PopupPositionProvider.Align.BottomEnd -> {
                    offsetX = anchorBounds.right - popupContentSize.width - popupMargin.right
                    offsetY = anchorBounds.top - popupContentSize.height - popupMargin.bottom
                    showBelow = false
                    showAbove = true
                }
                else -> {
                    // 兜底逻辑：与dropdownPositionProvider相同
                    offsetX = if (alignment.resolve(layoutDirection) == PopupPositionProvider.Align.End) {
                        anchorBounds.right - popupContentSize.width - popupMargin.right
                    } else {
                        anchorBounds.left + popupMargin.left
                    }
                    val spaceBelow = windowBounds.bottom - anchorBounds.bottom
                    val spaceAbove = anchorBounds.top - windowBounds.top
                    if (spaceBelow > popupContentSize.height) {
                        offsetY = anchorBounds.bottom + popupMargin.bottom
                        showBelow = true
                        showAbove = false
                    } else if (spaceAbove > popupContentSize.height) {
                        offsetY = anchorBounds.top - popupContentSize.height - popupMargin.top
                        showBelow = false
                        showAbove = true
                    } else {
                        offsetY = anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2
                        showBelow = false
                        showAbove = false
                    }
                }
            }
            val clampedOffset = IntOffset(
                x = offsetX.coerceIn(
                    windowBounds.left,
                    (windowBounds.right - popupContentSize.width - popupMargin.right).coerceAtLeast(windowBounds.left),
                ),
                y = offsetY.coerceIn(
                    (windowBounds.top + popupMargin.top).coerceAtMost(windowBounds.bottom - popupContentSize.height - popupMargin.bottom),
                    windowBounds.bottom - popupContentSize.height - popupMargin.bottom,
                ),
            )
            return PopupPositionResult(clampedOffset, showBelow, showAbove)
        }

        override fun getMargins(): PaddingValues = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
    }
}

// =====================================================================
// 布局位置描述
// =====================================================================

/**
 * 描述弹窗相对于其锚点的放置方式。
 * 用于驱动方向性揭示动画和变换原点。
 */
@Immutable
data class PopupLayoutPosition(
    val showBelow: Boolean,     // 弹窗是否显示在锚点下方
    val showAbove: Boolean,     // 弹窗是否显示在锚点上方
    val isRightAligned: Boolean,// 弹窗是否右对齐（与锚点右侧对齐）
)

/**
 * 弹窗的解析布局信息。
 * 由 [rememberListPopupLayoutInfo] 计算并记忆。
 */
@Immutable
data class ListPopupLayoutInfo(
    val windowBounds: IntRect,              // 窗口安全区域边界
    val popupMargin: IntRect,               // 弹窗的额外边距（像素）
    val effectiveTransformOrigin: TransformOrigin, // 窗口坐标系下的变换原点（用于缩放动画）
    val localTransformOrigin: TransformOrigin,     // 本地坐标系下的变换原点（用于graphicsLayer）
    val popupLayoutPosition: PopupLayoutPosition,  // 弹窗的放置方向
)

// =====================================================================
// rememberListPopupLayoutInfo - 计算弹窗布局信息
// =====================================================================

/**
 * 计算并记忆弹窗的布局信息。
 * 根据锚点位置、内容大小、对齐方式等计算弹窗应该显示的位置。
 *
 * @param alignment 弹窗相对于窗口的对齐方式
 * @param popupPositionProvider 弹窗位置提供者
 * @param parentBounds 锚点（父组件）在窗口坐标系中的边界
 * @param popupContentSize 弹窗内容的测量大小
 */
@Composable
fun rememberListPopupLayoutInfo(
    alignment: PopupPositionProvider.Align,
    popupPositionProvider: PopupPositionProvider,
    parentBounds: IntRect,
    popupContentSize: IntSize,
): ListPopupLayoutInfo {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val layoutDirection = LocalLayoutDirection.current
    val displayCutout = WindowInsets.displayCutout
    val statusBars = WindowInsets.statusBars
    val navigationBars = WindowInsets.navigationBars
    val captionBar = WindowInsets.captionBar

    // 计算弹窗边距（像素）
    val margins = popupPositionProvider.getMargins()
    val popupMargin = remember(layoutDirection, density, margins) {
        with(density) {
            IntRect(
                left = margins.calculateLeftPadding(layoutDirection).roundToPx(),
                top = margins.calculateTopPadding().roundToPx(),
                right = margins.calculateRightPadding(layoutDirection).roundToPx(),
                bottom = margins.calculateBottomPadding().roundToPx(),
            )
        }
    }

    val containerSize = windowInfo.containerSize

    // 计算窗口安全区域边界（排除刘海、状态栏、导航栏等）
    val windowBounds = remember(
        layoutDirection,
        density,
        displayCutout,
        statusBars,
        navigationBars,
        captionBar,
        containerSize,
    ) {
        with(density) {
            IntRect(
                left = displayCutout.getLeft(this, layoutDirection),
                top = statusBars.getTop(this),
                right = containerSize.width - displayCutout.getRight(this, layoutDirection),
                bottom = containerSize.height - navigationBars.getBottom(this) - captionBar.getBottom(this),
            )
        }
    }

    // 预测变换原点（在弹窗未测量时使用）
    val predictedTransformOrigin = remember(alignment, popupMargin, parentBounds, layoutDirection, containerSize) {
        val xInWindow = when (alignment.resolve(layoutDirection)) {
            PopupPositionProvider.Align.End,
            PopupPositionProvider.Align.TopEnd,
            PopupPositionProvider.Align.BottomEnd,
            -> parentBounds.right - popupMargin.right
            else -> parentBounds.left + popupMargin.left
        }
        val yInWindow = when (alignment.resolve(layoutDirection)) {
            PopupPositionProvider.Align.BottomEnd, PopupPositionProvider.Align.BottomStart ->
                parentBounds.top - popupMargin.bottom
            else ->
                parentBounds.bottom + popupMargin.bottom
        }
        safeTransformOrigin(
            xInWindow / containerSize.width.toFloat(),
            yInWindow / containerSize.height.toFloat(),
        )
    }

    // 计算弹窗位置和展开方向（由 positionProvider 一次性返回）
    val positionResult = remember(
        popupContentSize,
        windowBounds,
        parentBounds,
        alignment,
        layoutDirection,
        popupMargin,
        popupPositionProvider,
    ) {
        if (popupContentSize == IntSize.Zero) {
            PopupPositionResult(IntOffset.Zero, showBelow = true, showAbove = false)
        } else {
            popupPositionProvider.calculatePosition(
                parentBounds,
                windowBounds,
                layoutDirection,
                popupContentSize,
                popupMargin,
                alignment,
            )
        }
    }
    val calculatedOffset = positionResult.offset

    // 解析弹窗的放置方向和本地变换原点：方向直接取自 positionProvider 的真实分支。
    val (popupLayoutPosition, localTransformOrigin) = remember(
        popupContentSize,
        calculatedOffset,
        positionResult,
        layoutDirection,
        alignment,
    ) {
        if (popupContentSize == IntSize.Zero) {
            val isRightAligned = when (alignment.resolve(layoutDirection)) {
                PopupPositionProvider.Align.End,
                PopupPositionProvider.Align.TopEnd,
                PopupPositionProvider.Align.BottomEnd,
                -> true
                else -> false
            }
            PopupLayoutPosition(showBelow = true, showAbove = false, isRightAligned = isRightAligned) to
                TransformOrigin(if (isRightAligned) 1f else 0f, 0f)
        } else {
            resolvePopupAnchors(positionResult, calculatedOffset, popupContentSize, parentBounds, alignment, layoutDirection)
        }
    }

    // 计算有效的变换原点（窗口坐标系，用于缩放动画的pivot）。
    // 方向直接取自 positionResult，与实际展开分支完全一致。
    val effectiveTransformOrigin = remember(
        popupContentSize,
        calculatedOffset,
        positionResult,
        containerSize,
        predictedTransformOrigin,
        layoutDirection,
        alignment,
    ) {
        if (popupContentSize == IntSize.Zero) {
            predictedTransformOrigin
        } else {
            val isRightAligned = when (alignment.resolve(layoutDirection)) {
                PopupPositionProvider.Align.End,
                PopupPositionProvider.Align.TopEnd,
                PopupPositionProvider.Align.BottomEnd,
                -> true
                else -> false
            }
            val cornerX = if (isRightAligned) {
                (calculatedOffset.x + popupContentSize.width).toFloat()
            } else {
                calculatedOffset.x.toFloat()
            }

            val cornerY = when {
                positionResult.showBelow -> calculatedOffset.y.toFloat()
                positionResult.showAbove -> (calculatedOffset.y + popupContentSize.height).toFloat()
                else -> (calculatedOffset.y + popupContentSize.height / 2f)
            }

            safeTransformOrigin(
                cornerX / containerSize.width.toFloat(),
                cornerY / containerSize.height.toFloat(),
            )
        }
    }

    return ListPopupLayoutInfo(
        windowBounds = windowBounds,
        popupMargin = popupMargin,
        effectiveTransformOrigin = effectiveTransformOrigin,
        localTransformOrigin = localTransformOrigin,
        popupLayoutPosition = popupLayoutPosition,
    )
}

/**
 * 面板矩形 Shape：每次重组都新建实例，配合 [equals] 让相等判定仍能命中。
 *
 * 存在的理由是 `EdgeLightNode` 的 outline 缓存用**引用比较**（`cachedOutlineShape === shape`）
 * 判断能否复用，而弹窗节点尺寸在动画中恒定不变 —— 若 shape 实例被 remember 住，
 * outline 会被冻结在第一帧的 42dp 小圆上（表现为描边消失、背景框像被钉死）。
 * 每帧换引用可强制它重算。
 *
 * @param rectKey 量化后的矩形标识（整数三元组：动画帧号 + 面板宽 + 面板高），用于 [equals]/[hashCode]
 * @param cornerRadius 圆角半径
 * @param rectProvider 返回 (left, top, width, height) 的 px 计算函数
 */
private class AnimatedPanelRectShape(
    private val rectKey: List<Int>,
    private val cornerRadius: Dp,
    private val rectProvider: (Float, Float) -> FloatArray,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val rect = rectProvider(size.width, size.height)
        val base = ContinuousRoundedRectangle(cornerRadius)
            .createOutline(Size(rect[2], rect[3]), layoutDirection, density)
        val offset = Offset(rect[0], rect[1])
        return when (base) {
            // Outline.Rounded 不是 data class（无 copy），只能重新构造
            is Outline.Rounded -> Outline.Rounded(base.roundRect.translate(offset))
            // Path.translate 是原地修改的成员函数，必须新建 Path 承接，否则会污染 base
            is Outline.Generic -> Outline.Generic(Path().apply {
                addPath(base.path)
                translate(offset)
            })
            else -> base
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnimatedPanelRectShape) return false
        return rectKey == other.rectKey
    }

    override fun hashCode(): Int = rectKey.hashCode()
}

// =====================================================================
// ListPopupContent - 弹窗内容容器
// =====================================================================

/**
 * 弹窗内容容器，动画与 [com.haooz.chedule.ui.basic.LiquidGlassDropdownMenu] 完全一致（1:1 复刻）：
 *
 * - 面板矩形从「收起态尺寸」起按每轴 `lerp(收起态, 面板尺寸, f)` **真实插值**（不是 scale，容器永不变形），
 *   矩形锚在锚点角（[localTransformOrigin]，朝上/朝下/朝左/朝右四种 —— 唯一区别于标准实现的地方）。
 *   收起态尺寸由 [collapseSize] 给出（触发区「选中文字 + 箭头」的实测宽高），未传入时退回 42dp。
 * - 锚点迁移位移（`k = 0.5p(1-s)`，`s = 当前宽度比`）与退出回弹位移叠加在矩形左上角。
 * - 内容按 `w / 面板宽` **等比**缩放并移到矩形中心（对应标准的 contentScale + align(Center)）。
 * - 裁剪 / 玻璃 / 边缘光共用同一个矩形，圆角恒为 25dp（收起态被胶囊化成正圆），不做反向补偿。
 * - 内容按 `((f-0.3)/0.4)` 淡入、按 `6dp*(1-|2f-1|)` 起雾。
 *
 * 弹窗节点自身尺寸始终是自然尺寸（不随动画变化），因此弹窗定位不会抖动。
 *
 * @param popupContentSize 弹窗内容的当前大小
 * @param onPopupContentSizeChange 内容大小变化时的回调
 * @param fractionProgress 提供当前展开进度（0→1，spring 可能过冲）
 * @param originProgress 提供锚点迁移进度（比 fraction 更快到 1）
 * @param localTransformOrigin 本地坐标系下的变换原点（= 锚点角）
 * @param modifier 修饰符
 * @param collapseSize 收起态尺寸（px）。通常是触发区「选中文字 + 箭头图标」的实测尺寸，
 *   弹窗从这块内容原位长成面板。为 null 或 0 时退回 42dp。
 * @param collapseExtra 收起态尺寸的额外补偿（加在 lerp 的起点上）
 * @param isEntering 是否正在进场。进场/退场用不同的淡入淡出时机档位。
 * @param content 弹窗内容
 */
@Composable
fun ListPopupContent(
    popupContentSize: IntSize,
    onPopupContentSizeChange: (IntSize) -> Unit,
    fractionProgress: () -> Float,
    originProgress: () -> Float,
    localTransformOrigin: TransformOrigin,
    modifier: Modifier = Modifier,
    liquidGlassBackdrop: com.kyant.backdrop.Backdrop? = null,
    collapseSize: IntSize? = null,
    collapseExtra: DpSize = DpSize.Zero,
    isEntering: Boolean = true,
    collapseContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // ============================================
    // 圆角值 - 修改这里可以改变弹窗的圆角
    // ============================================
    val cornerRadius = 25.dp
    val backgroundColor = MiuixTheme.colorScheme.surfaceContainer
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f

    val localDensity = LocalDensity.current
    val fallbackCollapsePx = with(localDensity) { 42.dp.toPx() }
    val extraW = with(localDensity) { collapseExtra.width.toPx() }
    val extraH = with(localDensity) { collapseExtra.height.toPx() }
    val collapseW = (collapseSize?.width?.takeIf { it > 0 }?.toFloat() ?: fallbackCollapsePx) + extraW
    val collapseH = (collapseSize?.height?.takeIf { it > 0 }?.toFloat() ?: fallbackCollapsePx) + extraH
    val shadowPadding = 24.dp
    val overshootRoom = 14.dp
    val overshootRoomPx = with(localDensity) { overshootRoom.toPx() }
    val chromeLens = AppMaterialSettings.chromeLensEnabled()


    val containerAlphaEnter = 7f    // 标准 5f：alpha 在 f=0.20 就到 1
    val containerAlphaExit = 3.6f   // 标准 5f：alpha 到 f=0.28 才开始降（淡出更晚）
    // 内容：alpha 窗口 [起点, 终点]，f 升到终点才全显；退场时终点抬高 → 淡出更晚
    val contentEnterFrom = 0.22f    // 标准 0.30
    val contentEnterTo = 0.62f      // 标准 0.70
    val contentExitFrom = 0.30f
    val contentExitTo = 0.78f       // 标准 0.70 → 退场时 f 降到 0.78 才开始淡出

    fun containerAlphaOf(f: Float): Float {
        val k = if (isEntering) containerAlphaEnter else containerAlphaExit
        return (f * k).coerceIn(0f, 1f)
    }

    fun contentAlphaOf(f: Float): Float {
        val from = if (isEntering) contentEnterFrom else contentExitFrom
        val to = if (isEntering) contentEnterTo else contentExitTo
        return ((f - from) / (to - from)).coerceIn(0f, 1f)
    }

    fun contentBlurOf(f: Float): androidx.compose.ui.unit.Dp =
        6.dp * (1f - abs(2f * f - 1f)).coerceIn(0f, 1f)

    val blurDp = 24f

    // 锚点角：右对齐 → 面板从右侧长出；上对齐（showAbove）→ 从下侧长出
    val anchorRight = localTransformOrigin.pivotFractionX >= 0.5f
    val anchorBottom = localTransformOrigin.pivotFractionY >= 0.5f


    // 面板矩形（left, top, width, height，px）。
    //
    // ⚠️ 坐标系统一为**玻璃画布坐标系**：原点在玻璃画布左上角（比内容自然区域外扩
    // overshootRoomPx）。菜单内容层自行 offset 同等的量与之对齐，因此这里必须带上
    // 这个偏移，调用方不能重复补。
    //
    // 为什么必须严丝合缝：DrawBackdropNode 的 draw() 按**自身节点尺寸 this.size**
    // 创建 outline 并 clipPath，离屏缓冲也按同一 size 申请。材质层只要比画布小一圈，
    // 矩形超出它的部分就被直接削掉 —— 这就是「面板边缘/末尾回弹被切平」的来源。
    val panelRect: (Float, Float) -> FloatArray = { canvasW, canvasH ->
        val fr = fractionProgress()
        val naturalW = popupContentSize.width.toFloat().takeIf { it > 0f } ?: collapseW
        val naturalH = popupContentSize.height.toFloat().takeIf { it > 0f } ?: collapseH
        // 每轴独立 lerp：容器是真插值而非 scale，因此不会变形
        val wRaw = collapseW + (naturalW - collapseW) * fr
        val hRaw = collapseH + (naturalH - collapseH) * fr
        // 锚点迁移：比尺寸更快到 1，先「移向面板中心」再放大
        val p = originProgress()
        val s = if (naturalW > 0f) wRaw / naturalW else 1f
        val k = 0.5f * p * (1f - s)
        val migrationX = (if (anchorRight) -1f else 1f) * naturalW * k
        val migrationY = (if (anchorBottom) -1f else 1f) * naturalH * k
        val leftRaw = (if (anchorRight) naturalW - wRaw else 0f) + migrationX
        val topRaw = (if (anchorBottom) naturalH - hRaw else 0f) + migrationY

        // 保险丝：矩形必须完整落在画布内。spring 过冲只有约 2%，远小于每边 14dp 的余量，
        // 正常动画碰不到这个上限；它兜的是 migration 项把矩形推出画布的情况。
        val maxW = maxOf(canvasW, naturalW + 2 * overshootRoomPx)
        val maxH = maxOf(canvasH, naturalH + 2 * overshootRoomPx)
        val w = wRaw.coerceIn(0f, maxW)
        val h = hRaw.coerceIn(0f, maxH)
        val left = (leftRaw + overshootRoomPx).coerceIn(0f, (maxW - w).coerceAtLeast(0f))
        val top = (topRaw + overshootRoomPx).coerceIn(0f, (maxH - h).coerceAtLeast(0f))
        floatArrayOf(left, top, w, h)
    }


    val animFrame = remember(popupContentSize.width, popupContentSize.height) {
        derivedStateOf { (fractionProgress() * 400f).roundToInt() }
    }
    // 读一次触发订阅
    val frame = animFrame.value

    // 统一的 shape：给材质 drawBackdrop / edgeLight 用（三者共用同一坐标系）
    val panelShape: Shape = AnimatedPanelRectShape(
        rectKey = listOf(frame, popupContentSize.width, popupContentSize.height),
        cornerRadius = cornerRadius,
        rectProvider = panelRect,
    )


    // 玻璃画布的**显式**尺寸 = 内容自然尺寸 + 两侧余量。
    //
    // 这里必须是明确的 requiredSize，不能靠 wrap content + padding：
    // padding 是包在 Box 外面的 LayoutModifier，内层 Box 仍按「自然尺寸」测量，
    // 材质层无论用 matchParentSize（=自然，无余量）还是 fillMaxSize（顶到 max 约束、
    // 反过来把盒子撑大）都对不上。显式定尺寸后，材质层 matchParentSize 才刚好等于画布。
    val canvasWidthPx = popupContentSize.width.toFloat().takeIf { it > 0f }
        ?.let { it + 2 * overshootRoomPx } ?: (collapseW + 2 * overshootRoomPx)
    val canvasHeightPx = popupContentSize.height.toFloat().takeIf { it > 0f }
        ?.let { it + 2 * overshootRoomPx } ?: (collapseH + 2 * overshootRoomPx)
    val canvasWidth = with(localDensity) { canvasWidthPx.toDp() }
    val canvasHeight = with(localDensity) { canvasHeightPx.toDp() }

    val glassEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
        remember(chromeLens) {
            {
                vibrancy()
                blur(blurDp.dp.toPx())
            }
        }

    Box(
        modifier = modifier
            // 外层阴影留白。弹窗总测量尺寸 = 内容 + 2×(shadowPadding + overshootRoom)，
            // ListPopupLayout 里按同样的量扣回来（chromePaddingPx），保证定位不偏。
            .padding(shadowPadding)
    ) {
        // ── 玻璃画布 ──
        // 坐标系原点在此（比内容自然区域外扩 overshootRoomPx）。
        // 尺寸显式定死，材质层 matchParentSize 才能刚好等于它。
        Box(
            modifier = Modifier
                .requiredSize(canvasWidth, canvasHeight)
                .drawBehind {
                    val spread = containerAlphaOf(fractionProgress())
                    if (spread > 0.01f) {
                        // 阴影画在玻璃画布上 → 直接用画布坐标系
                        val rect = panelRect(size.width, size.height)
                        val left = rect[0]
                        val top = rect[1]
                        val boxW = rect[2]
                        val boxH = rect[3]
                        val shadowArgb = if (isDark) 0x20000000 else 0x12000000
                        val blurRadius = 10f * density * spread
                        val shadowSpread = 2f * density * spread
                        val r = cornerRadius.toPx()
                        val nativePath = android.graphics.Path().apply {
                            addRoundRect(
                                left - shadowSpread, top - shadowSpread,
                                left + boxW + shadowSpread, top + boxH + shadowSpread,
                                r + shadowSpread, r + shadowSpread,
                                android.graphics.Path.Direction.CW
                            )
                            addRoundRect(
                                left, top, left + boxW, top + boxH, r, r,
                                android.graphics.Path.Direction.CCW
                            )
                        }
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(
                                (android.graphics.Color.alpha(shadowArgb) * 3.2f).coerceAtMost(255f).toInt(),
                                android.graphics.Color.red(shadowArgb),
                                android.graphics.Color.green(shadowArgb),
                                android.graphics.Color.blue(shadowArgb)
                            )
                            maskFilter = BlurMaskFilter(
                                blurRadius.coerceAtLeast(0.1f),
                                BlurMaskFilter.Blur.NORMAL
                            )
                        }
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawPath(nativePath, paint)
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    // 撑满玻璃画布（含两侧余量）。此处必须是 matchParentSize 且父级尺寸
                    // 是显式 requiredSize —— 见 canvasWidth/canvasHeight 的注释。
                    .matchParentSize()
                    .graphicsLayer { alpha = containerAlphaOf(fractionProgress()) }
                    .then(
                        if (liquidGlassBackdrop != null && android.os.Build.VERSION.SDK_INT >= 33) {
                            Modifier.drawBackdrop(
                                backdrop = liquidGlassBackdrop,
                                shape = { panelShape },
                                effects = glassEffects,
                                highlight = null,
                                shadow = null,
                                onDrawSurface = {
                                    drawRect(
                                        color = backgroundColor.copy(
                                            alpha = if (isDark) 0.8f else 0.72f,
                                        )
                                    )
                                }
                            )
                        } else Modifier
                    )
                    .edgeLight(
                        shape = panelShape,
                        edgeLight = rememberDefaultEdgeLight(baseColor = backgroundColor)
                    ),
            )

            // 收起态内容（原选项文本 + 箭头）：锚在面板矩形角上，随容器长大淡出。
            // 不加 alpha —— 材质的 alpha 在材质子层上，这里不受影响。
            if (collapseContent != null) {
                Box(
                    modifier = Modifier
                        // 基准统一用左上角：下面用 translation 把内容中心直接算到矩形中心。
                        // 之前按锚点角 align + 线性迁到中心，收起态（fr≈0，translation 还没起来）
                        // 会停在锚点角上 —— 就是那个「偏右上角」。
                        .align(Alignment.TopStart)
                        .graphicsLayer {
                            val fr = fractionProgress()
                            // 画布坐标系：矩形已含余量偏移
                            val rect = panelRect(canvasWidthPx, canvasHeightPx)
                            val iconK = if (isEntering) 2.5f else 1.7f
                            val p = (fr * iconK).coerceIn(0f, 1f)
                            alpha = 1f - p
                            // 始终锁在面板矩形正中心（矩形长多大就跟着移到哪）
                            translationX = rect[0] + rect[2] / 2f - size.width / 2f
                            translationY = rect[1] + rect[3] / 2f - size.height / 2f
                        },
                ) {
                    // DropdownTriggerContent 是 RowScope 扩展（DropdownArrowEndAction 是），
                    // 这里套一层 Row 提供作用域。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        collapseContent()
                    }
                }
            }
        }

        // 菜单内容：居中在画布里，按面板矩形等比缩放。
        //
        // ⚠️ 不能放进玻璃画布 Box 里 —— 那会让画布退化成 wrap content，
        // 余量就又没了（drawBackdrop 按自身节点尺寸裁，见 canvasWidth 注释）。
        // 所以用 offset 挪到画布坐标系的 (余量, 余量)，作为画布 Box 的兄弟节点。
        Box(
            modifier = Modifier
                .offset(overshootRoom, overshootRoom)
                // 尺寸上报挂在内容层：它是真实自然尺寸，画布层多了两个余量会污染。
                .onGloballyPositioned { coordinates ->
                    val size = coordinates.size
                    if (popupContentSize != size) onPopupContentSizeChange(size)
                }
                .graphicsLayer {
                    val fr = fractionProgress()
                    // 入参 = 玻璃画布尺寸（内容 + 两侧余量），矩形按它钳制
                    val rect = panelRect(
                        size.width + 2 * overshootRoomPx,
                        size.height + 2 * overshootRoomPx,
                    )
                    val contentScale = if (size.width > 0f) rect[2] / size.width else 1f
                    scaleX = contentScale
                    scaleY = contentScale
                    alpha = contentAlphaOf(fr)
                    // 内容中心移到面板矩形中心：μ 矩形中心 -= 内容中心（都已在同一画布坐标系）
                    translationX = rect[0] + rect[2] / 2f - (overshootRoomPx + size.width / 2f)
                    translationY = rect[1] + rect[3] / 2f - (overshootRoomPx + size.height / 2f)
                }
                .blur(contentBlurOf(fractionProgress())),
        ) {
            content()
        }
    }
}

// =====================================================================
// rememberDynamicCornerRadiusShape 已移至 DynamicCornerRadiusShape.kt，
// 避免与 libs.miuix.ui 库中的 ListPopupKt 类冲突导致 NoSuchMethodError。

// =====================================================================
// popupClipReveal - 方向性裁剪揭示修饰符
// =====================================================================

/**
 * 方向性裁剪揭示修饰符。
 *
 * 在弹窗进入/退出时，可见区域会沿着弹窗的生成方向逐渐展开：
 * - 显示在锚点下方：从顶部向下展开
 * - 显示在锚点上方：从底部向上展开
 * - 居中显示：从中心向两侧展开
 *
 * 使用squircle（超椭圆）形状来保持四角与周围的squircle修饰符对齐。
 * 当squircleEnabled为false时，会退化为普通的圆角矩形。
 */
fun Modifier.popupClipReveal(
    fractionProgress: () -> Float,
    popupLayoutPosition: PopupLayoutPosition,
    cornerRadius: Dp,
    squircleEnabled: Boolean,
    revealLimitHeightPx: Float = 0f,
): Modifier = drawWithCache {
    val path = Path()
    val showBelow = popupLayoutPosition.showBelow
    val showAbove = popupLayoutPosition.showAbove
    onDrawWithContent {
        // 限制进度值在0~1之间（弹簧动画可能会超出）
        val progress = fractionProgress().coerceIn(0f, 1f)
        if (progress <= 0f) return@onDrawWithContent

        val height = size.height
        // 当设置了 revealLimitHeightPx 且朝上/朝下时，从限制高度展开到完整高度
        val visibleHeight = if (revealLimitHeightPx > 0f && (showBelow || showAbove)) {
            (revealLimitHeightPx + (height - revealLimitHeightPx) * progress).coerceIn(0f, height)
        } else {
            height
        }
        if (visibleHeight <= 0f) return@onDrawWithContent

        // 计算裁剪起始位置
        // 朝上/朝下：从锚点一侧向另一侧展开（配合 visibleHeight 限制）
        // 居中：从中心向两侧展开
        val clipStart = when {
            showBelow -> 0f                        // 朝下：从顶部向下展开
            showAbove -> height - visibleHeight   // 朝上：从底部向上展开
            else -> height * (0.5f - 0.5f * progress) // 居中：从中心向两侧展开
        }

        path.rewind()
        // 使用kyant库的RoundedRectangle创建圆角矩形路径
        // 圆角在动画过程中保持不变：当弹窗缩小时，圆角需要放大以抵消缩放
        val fraction = fractionProgress().coerceIn(0f, 1f)
        val scaleXL = 0.24f + 0.76f * fraction
        val scaleXY = 0.24f + 0.76f * fraction
        // 使用两个轴缩放的平均值来计算圆角，保持圆角不变
        val avgScale = (scaleXL + scaleXY) / 2f
        val scaledCornerRadius = cornerRadius / avgScale
        val roundedRectShape = ContinuousRoundedRectangle(scaledCornerRadius)
        val outline = roundedRectShape.createOutline(
            size = Size(size.width, visibleHeight),
            layoutDirection = layoutDirection,
            density = this@drawWithCache
        )
        when (outline) {
            is Outline.Rounded -> path.addRoundRect(outline.roundRect)
            is Outline.Generic -> path.addPath(outline.path)
            is Outline.Rectangle -> path.addRect(outline.rect)
        }
        if (clipStart == 0f) {
            clipPath(path) {
                this@onDrawWithContent.drawContent()
            }
        } else {
            translate(top = clipStart) {
                clipPath(path) {
                    translate(top = -clipStart) {
                        this@onDrawWithContent.drawContent()
                    }
                }
            }
        }
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
