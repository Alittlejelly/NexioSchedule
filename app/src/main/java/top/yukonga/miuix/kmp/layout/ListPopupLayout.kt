// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.basic.ListPopupContent
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PopupPositionResult
import top.yukonga.miuix.kmp.basic.rememberListPopupLayoutInfo
import top.yukonga.miuix.kmp.basic.resolvePopupAnchors
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.anim.SinOutEasing
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.haooz.chedule.ui.utils.PredictiveBackSettings

// 动画参数：与 LiquidGlassDropdownMenu 完全一致（1:1 复刻）
// 进场：fraction spring 0.78/240，锚点迁移 spring 0.78/500（比尺寸更快到 1）
private val FractionEnterAnimSpec = spring<Float>(dampingRatio = 0.78f, stiffness = 240f, visibilityThreshold = 0.0001f)
// 退场：fraction spring 0.85/650，锚点迁移 tween(340, CubicBezierEasing(0,0,0,1))
private val FractionExitAnimSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 650f, visibilityThreshold = 0.0001f)
private val OriginEnterAnimSpec = spring<Float>(dampingRatio = 0.78f, stiffness = 500f, visibilityThreshold = 0.0001f)
private val OriginExitAnimSpec = tween<Float>(340, easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f))
// 预测性返回取消后回到展开态
private val BackCancelAnimSpec = tween<Float>(150)
private val DimEnterAnimSpec = tween<Float>(durationMillis = 200, easing = SinOutEasing)
private val DimExitAnimSpec = tween<Float>(durationMillis = 300, easing = SinOutEasing)
private val LocalMinPopupHeight = 50.dp

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

// 自定义下拉定位提供者（带偏移量）
fun liquidDropdownPositionProvider(): PopupPositionProvider = object : PopupPositionProvider {
    private val margins = PaddingValues(horizontal = 0.dp, vertical = 0.dp)

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowBounds: IntRect,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
        popupMargin: IntRect,
        alignment: PopupPositionProvider.Align,
    ): PopupPositionResult {
        val offsetXDelta = 102  //@ 3x density
        val offsetYDelta = 102  //@ 3x density

        val offsetX = if (alignment.resolve(layoutDirection) == PopupPositionProvider.Align.End) {
            anchorBounds.right - popupContentSize.width - popupMargin.right + offsetXDelta
        } else {
            anchorBounds.left + popupMargin.left + offsetXDelta
        }

        val spaceBelow = windowBounds.bottom - anchorBounds.bottom
        val spaceAbove = anchorBounds.top - windowBounds.top
        val offsetY: Int
        val showBelow: Boolean
        val showAbove: Boolean
        if (spaceBelow > popupContentSize.height) {
            offsetY = anchorBounds.top - offsetYDelta
            showBelow = true
            showAbove = false
        } else if (spaceAbove > popupContentSize.height) {
            offsetY = anchorBounds.bottom - popupContentSize.height + offsetYDelta
            showBelow = false
            showAbove = true
        } else {
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

/**
 * 弹窗布局的核心逻辑。
 *
 * @param show 是否显示弹窗
 * @param popupHost 弹窗容器
 * @param popupModifier 弹窗修饰符
 * @param popupPositionProvider 弹窗位置提供者
 * @param alignment 弹窗对齐方式
 * @param enableWindowDim 是否启用背景变暗
 * @param onDismissRequest 关闭弹窗的回调
 * @param onDismissFinished 关闭动画完成后的回调
 * @param maxHeight 弹窗最大高度
 * @param minWidth 弹窗最小宽度
 * @param content 弹窗内容
 */
@Composable
fun ListPopupLayout(
    show: Boolean,
    popupHost: @Composable (visible: Boolean, content: @Composable () -> Unit) -> Unit,
    popupModifier: Modifier = Modifier,
    popupPositionProvider: PopupPositionProvider = liquidDropdownPositionProvider(),
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.Start,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    maxHeight: Dp? = null,
    minWidth: Dp = ListPopupDefaults.MinWidth,
    liquidGlassBackdrop: Backdrop? = null,
    onFractionProgress: ((Float) -> Unit)? = null,
    collapseSize: IntSize? = null,
    collapseExtra: DpSize = DpSize.Zero,
    content: @Composable () -> Unit,
) {
    val fractionProgress = remember { Animatable(0f) }
    // 锚点迁移进度：比尺寸更快到 1，先「移向面板中心」再放大
    val originProgress = remember { Animatable(0f) }
    val dimProgress = remember { Animatable(0f) }
    // 预测性返回手势进度（取消时据此决定是否要恢复）
    val backProgress = remember { Animatable(0f) }
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    val internalVisible = remember { mutableStateOf(false) }
    var popupContentSize by remember { mutableStateOf(IntSize.Zero) }
    var hostPositionInWindow by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(show) {
        if (show) {
            internalVisible.value = true
            launch { fractionProgress.animateTo(1f, FractionEnterAnimSpec) }
            launch { originProgress.animateTo(1f, OriginEnterAnimSpec) }
            if (enableWindowDim) {
                launch { dimProgress.animateTo(1f, DimEnterAnimSpec) }
            }
        } else {
            if (!internalVisible.value) return@LaunchedEffect
            launch { fractionProgress.animateTo(0f, FractionExitAnimSpec) }
            if (enableWindowDim) {
                launch { dimProgress.animateTo(0f, DimExitAnimSpec) }
            }
            // 这里必须「等」动画跑完（不能立刻 snapTo，否则会把上面的动画取消掉）；
            // 用锚点 tween 兜住，结束后再收尾
            originProgress.animateTo(0f, OriginExitAnimSpec)
            fractionProgress.snapTo(0f)
            originProgress.snapTo(0f)
            dimProgress.snapTo(0f)
            internalVisible.value = false
            currentOnDismissFinished?.invoke()
        }
    }

    val currentOnFractionProgress by rememberUpdatedState(onFractionProgress)
    LaunchedEffect(Unit) {
        currentOnFractionProgress?.let { callback ->
            snapshotFlow { fractionProgress.value }
                .collect { callback(it) }
        }
    }

    if (!show && !internalVisible.value) return

    var parentBounds by remember { mutableStateOf(IntRect.Zero) }

    Spacer(
        modifier = Modifier
            .onGloballyPositioned { childCoordinates ->
                childCoordinates.parentLayoutCoordinates?.let { parentLayoutCoordinates ->
                    val positionInWindow = parentLayoutCoordinates.positionInWindow()
                    parentBounds = IntRect(
                        left = positionInWindow.x.toInt(),
                        top = positionInWindow.y.toInt(),
                        right = positionInWindow.x.toInt() + parentLayoutCoordinates.size.width,
                        bottom = positionInWindow.y.toInt() + parentLayoutCoordinates.size.height,
                    )
                }
            },
    )

    if (parentBounds == IntRect.Zero) return

    val layoutInfo = rememberListPopupLayoutInfo(
        alignment = alignment,
        popupPositionProvider = popupPositionProvider,
        parentBounds = parentBounds,
        popupContentSize = popupContentSize,
    )

    // 由 layout 阶段计算的真实锚点，与 calculatedOffset 使用同一个 measuredSize，
    // 避免 composition 阶段的 popupContentSize 与 layout 阶段的 measuredSize 不一致。
    var realLocalTransformOrigin by remember { mutableStateOf(TransformOrigin(0f, 0f)) }

    val requestDismiss: () -> Unit = remember {
        { currentOnDismiss?.invoke() }
    }

    popupHost(internalVisible.value) {
        val coroutineScope = rememberCoroutineScope()
        val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)

        // 预测性返回：手势进度驱动 fraction/origin/dim 全部收敛到 0（弹窗缩回消失），
        // 取消恢复、完成关闭；低版本 NavigationBackHandler 自动退化为立即关闭
        NavigationBackHandler(
            state = backState,
            isBackEnabled = show,
            onBackCancelled = {
                coroutineScope.launch {
                    if (backProgress.value > 0f) {
                        fractionProgress.animateTo(1f, BackCancelAnimSpec)
                        originProgress.animateTo(1f, BackCancelAnimSpec)
                        if (enableWindowDim) {
                            dimProgress.animateTo(1f, DimEnterAnimSpec)
                        }
                        backProgress.snapTo(0f)
                    }
                }
            },
            onBackCompleted = {
                requestDismiss()
            },
        )

        // 逐帧收集返回手势进度（单独协程，避免手势期间每帧取消/重启 LaunchedEffect）
        LaunchedEffect(Unit) {
            snapshotFlow { backState.transitionState }
                .collect { transitionState ->
                    if (
                        transitionState is NavigationEventTransitionState.InProgress &&
                        transitionState.direction == NavigationEventTransitionState.TRANSITIONING_BACK
                    ) {
                    // 预测性返回动画开关：关闭时不驱动跟随动画（返回仍被拦截，直接关闭）
                    if (PredictiveBackSettings.enabled) {
                        val progress = transitionState.latestEvent.progress
                        backProgress.snapTo(progress)
                        fractionProgress.snapTo(1f - progress)
                        originProgress.snapTo(1f - progress)
                        if (enableWindowDim) {
                            dimProgress.snapTo(1f - progress)
                        }
                    }
                    }
                }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = popupModifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        hostPositionInWindow = coordinates.positionInWindow()
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { requestDismiss() },
                        )
                    }
                    .layout { measurable, constraints ->
                        val windowBounds = layoutInfo.windowBounds
                        val popupMargin = layoutInfo.popupMargin
                        val minHeightPx = LocalMinPopupHeight.roundToPx()
                        val placeable = measurable.measure(
                            constraints.copy(
                                maxHeight = maxHeight?.roundToPx()?.coerceAtLeast(minHeightPx)
                                    ?: (windowBounds.height - popupMargin.top - popupMargin.bottom)
                                        .coerceAtLeast(minHeightPx),
                                minHeight = if (minHeightPx <= constraints.maxHeight) minHeightPx else constraints.maxHeight,
                                maxWidth = constraints.maxWidth,
                                minWidth = minWidth.roundToPx().coerceAtMost(constraints.maxWidth),
                            ),
                        )
                        val measuredSize = IntSize(placeable.width, placeable.height)

                        val positionResult = popupPositionProvider.calculatePosition(
                            parentBounds,
                            windowBounds,
                            layoutDirection,
                            measuredSize,
                            popupMargin,
                            alignment,
                        )
                        val calculatedOffset = positionResult.offset

                        // 从同一个 positionResult 推导锚点，保证一致性
                        val (_, transformOrigin) = resolvePopupAnchors(
                            positionResult, calculatedOffset, measuredSize, parentBounds, alignment, layoutDirection,
                        )
                        realLocalTransformOrigin = transformOrigin

                        val adjustedOffset = IntOffset(
                            x = calculatedOffset.x - hostPositionInWindow.x.toInt(),
                            y = calculatedOffset.y - hostPositionInWindow.y.toInt(),
                        )

                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(adjustedOffset)
                        }
                    },
            ) {
                ListPopupContent(
                    popupContentSize = popupContentSize,
                    onPopupContentSizeChange = { popupContentSize = it },
                    fractionProgress = { fractionProgress.value },
                    originProgress = { originProgress.value },
                    localTransformOrigin = realLocalTransformOrigin,
                    liquidGlassBackdrop = liquidGlassBackdrop,
                    collapseSize = collapseSize,
                    collapseExtra = collapseExtra,
                    // show 为 true 即进场；退场动画期间 show 已是 false 但仍在渲染，
                    // 正好对应「淡出用晚一档」的时机。
                    isEntering = show,
                    content = {
                        CompositionLocalProvider(LocalDismissState provides requestDismiss) {
                            content()
                        }
                    },
                )
            }
        }
    }
}
