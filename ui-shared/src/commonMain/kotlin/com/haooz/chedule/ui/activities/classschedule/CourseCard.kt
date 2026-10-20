package com.haooz.chedule.ui.activities.classschedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.CardContentAlignment
import com.haooz.chedule.data.CardRefractionLevel
import com.haooz.chedule.data.CardTextColor
import com.haooz.chedule.data.Course
import com.haooz.chedule.ui.theme.rememberCourseColors
import com.haooz.chedule.ui.theme.readableCourseTextColor
import com.haooz.chedule.data.hsvToRgb
import com.haooz.chedule.data.rgbToHsv
import com.haooz.chedule.ui.utils.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.LocalBackdropViewport
import com.kyant.backdrop.backdrops.SharedBlurBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.capsule.ContinuousRoundedRectangle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds

/** 落地冲击波：中心坐标 + token，触发周围课程卡涟漪 */
data class LandRippleSpec(
    val center: Offset = Offset.Zero,
    val token: Int = 0,
)

val LocalLandRipple = compositionLocalOf { LandRippleSpec() }

@Composable
fun CourseCard(
    course: Course,
    isCurrentWeek: Boolean = true,
    isHoliday: Boolean = false,
    isCourseCancelled: Boolean = false,
    isWorkSwap: Boolean = false,
    hasMultipleCourses: Boolean = false,
    wallpaperBackdrop: Backdrop? = null,
    cardBlurRadius: Float = 4f,
    cardAlpha: Float = 0.15f,
    /** 有壁纸时白/黑底不透明度（卡片不透明度） */
    cardSurfaceAlpha: Float = 0.15f,
    /**
     * 垫在卡片**最底部**的纯色，通常由调用方传页面底色 @0.8f。
     *
     * 无壁纸时课程色自身只有 0.1~0.4 透明度，浮层悬在网格上会透出底下的格线，
     * 垫一层不透明度更高的底色才立得住。有壁纸时玻璃层采样的是不透明壁纸层，
     * 垫色会被整块盖住，所以一般只在无壁纸路径传。
     * null = 不垫（网格里的卡片走默认）。
     */
    solidBackingColor: Color? = null,
    cardHeightPerSection: Float = 54f,
    // 自定义时间课显式指定高度；null 时按节次数算
    customCardHeightDp: Float? = null,
    cardCornerRadius: Float = 10f,
    isTablet: Boolean = false,
    cardContentAlignment: CardContentAlignment = CardContentAlignment.CENTER_CENTER,
    cardTextColor: CardTextColor = CardTextColor.COLORFUL,
    cardTextScale: Float = 1f,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    cardRefraction: CardRefractionLevel = CardRefractionLevel.DEFAULT,
    isDragging: Boolean = false,
    /**
     * 长按后是否允许接着拖动（调课）。
     *
     * ⚠ 自定义时间课传 false：它按时间轴定位、不存在「挪到第几节」的目标，
     * 拖出去也无处可放（还会顺带把「复制」带出来，而复制同样依赖节次跨度）。
     * 这类课长按只做一件事：弹「编辑 / 删除」菜单。
     */
    dragEnabled: Boolean = true,
    disablePadding: Boolean = false,
    isDark: Boolean = isAppDarkTheme(),
    // 非 state：滑动中坐标每帧变，跳过 localToRoot；读取不触发重组
    touchState: ScheduleTouchState? = null,
    onClick: () -> Unit,
    onLongPressStart: (cardLeft: Float, cardTop: Float, width: Float, height: Float) -> Unit = { _, _, _, _ -> },
    onDragStart: () -> Unit = {},
    onDrag: (offsetX: Float, offsetY: Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    onMenuDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sectionCount = course.endSection - course.startSection + 1
    val cardHeight = (customCardHeightDp ?: (sectionCount * cardHeightPerSection)).dp
    val hasBlur = wallpaperBackdrop != null
    val effectiveCornerRadius = if (isTablet) (cardCornerRadius * 1.3f) else cardCornerRadius
    val scope = rememberCoroutineScope()
    val localDensity = LocalDensity.current

    // 近处几乎立刻、远处按距离铺开；token=0（常态）不挂协程，降低新周页首帧组合成本
    val landRipple = LocalLandRipple.current
    val rippleScale = remember { Animatable(1f) }
    // 涟漪动画期间为 true；token 不会归零，不能直接拿 token 判层
    var rippleAnimating by remember { mutableStateOf(false) }
    val cardBoundsPx = remember { FloatArray(4) }
    // 最近一次布局坐标：滑动中 onGloballyPositioned 跳过计算会留下过期/零点，
    // 长按瞬间按需重算一次，保证浮层/菜单锚点准确（无需每帧算，开销只在长按时）
    val cardCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val refreshCardBounds = {
        val c = cardCoords[0]
        if (c != null && c.isAttached) {
            val center = c.localToRoot(Offset(c.size.width / 2f, c.size.height / 2f))
            cardBoundsPx[0] = center.x
            cardBoundsPx[1] = center.y
            cardBoundsPx[2] = c.size.width.toFloat()
            cardBoundsPx[3] = c.size.height.toFloat()
        }
    }
    // Sink：仅缩放反馈
    var sinkPressed by remember { mutableStateOf(false) }
    var sinkActive by remember { mutableStateOf(false) }
    val sinkScale = remember { Animatable(1f) }
    LaunchedEffect(sinkPressed) {
        sinkActive = true
        sinkScale.animateTo(
            targetValue = if (sinkPressed) 0.94f else 1f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f)
        )
        if (!sinkPressed) sinkActive = false
    }

    val lastRippleToken = remember { mutableIntStateOf(landRipple.token) }
    if (landRipple.token != 0) {
        LaunchedEffect(landRipple.token) {
            if (landRipple.token == lastRippleToken.intValue) return@LaunchedEffect
            lastRippleToken.intValue = landRipple.token
            val cx = cardBoundsPx[0]
            val cy = cardBoundsPx[1]
            if (cx == 0f && cy == 0f) return@LaunchedEffect
            val dist = hypot(cx - landRipple.center.x, cy - landRipple.center.y)
            val delayMs = if (dist < 90f) {
                0L
            } else {
                ((dist - 90f) * 0.3f).toLong().coerceAtMost(400L)
            }
            if (delayMs > 0) delay(delayMs.milliseconds)
            rippleAnimating = true
            try {
                rippleScale.animateTo(1.08f, tween(80, easing = FastOutSlowInEasing))
                rippleScale.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            } finally {
                rippleAnimating = false
            }
        }
    }

    val effectiveAlpha = (if (hasBlur) cardAlpha * 1.6f else cardAlpha).coerceIn(0f, 1f)
    val courseColors = rememberCourseColors(course, isDark)
    val useMonet = MiuixTheme.isDynamicColor
    val inactiveColor =
        if (useMonet) MiuixTheme.colorScheme.onSurfaceVariantActions else Color(0xFF9E9E9E)
    val cardColor = remember(
        courseColors.tint,
        inactiveColor,
        isCurrentWeek,
        isHoliday,
        isCourseCancelled,
        effectiveAlpha
    ) {
        if (isCurrentWeek && !isHoliday && !isCourseCancelled) {
            courseColors.tint.copy(alpha = effectiveAlpha)
        } else {
            inactiveColor.copy(alpha = effectiveAlpha * 0.7f)
        }
    }
    val themeBackground = MiuixTheme.colorScheme.background
    val monetCardBackground =
        remember(cardColor, solidBackingColor, themeBackground, hasBlur, cardSurfaceAlpha, isDark) {
            val backing = solidBackingColor?.compositeOver(themeBackground) ?: themeBackground
            val surface = cardColor.compositeOver(backing)
            if (hasBlur) {
                (if (isDark) Color.Black else Color.White)
                    .copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f)).compositeOver(surface)
            } else surface
        }
    val textColor = remember(
        courseColors,
        useMonet,
        inactiveColor,
        monetCardBackground,
        isCurrentWeek,
        isHoliday,
        isCourseCancelled,
        hasBlur,
        isDark,
        cardTextColor,
    ) {
        // 纯色模式仅本周课：黑白 0.74f；非本周/假期沿用灰色
        if (isCurrentWeek && !isHoliday && !isCourseCancelled &&
            cardTextColor == CardTextColor.SOLID
        ) {
            if (isDark) Color.White.copy(alpha = 0.74f) else Color.Black.copy(alpha = 0.74f)
        } else if (isCurrentWeek && !isHoliday && !isCourseCancelled) {
            if (useMonet) readableCourseTextColor(courseColors.onTint, monetCardBackground)
            else if (hasBlur) courseColors.tint.let { c ->
                val hsv = FloatArray(3)
                // AOSP Color.RGBToHSV/HSVToColor 的跨平台移植（:core 的纯数学版，golden vectors 锁定）
                rgbToHsv(
                    (c.red * 255).toInt(),
                    (c.green * 255).toInt(),
                    (c.blue * 255).toInt(),
                    hsv
                )
                if (isDark) {
                    hsv[1] = (hsv[1] * 0.5f).coerceIn(0f, 1f)
                    hsv[2] = (hsv[2] + 0.4f).coerceIn(0f, 1f)
                } else {
                    hsv[1] = (hsv[1] * 0.84f).coerceIn(0f, 1f)
                    hsv[2] = (hsv[2] + 0.5f).coerceIn(0f, 1f)
                }
                // hsvToRgb 返回打包 ARGB（alpha 满格）；Color(Int) 按 ARGB 解析 ——
                // 与原「HSVToColor → 取三通道 → Color(r,g,b)」逐位等价
                Color(hsvToRgb(hsv))
            }
            else courseColors.tint
        } else {
            if (hasBlur) {
                if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.3f)
            } else {
                inactiveColor.copy(alpha = if (isDark) 0.28f else 0.45f)
            }
        }
    }

    if (hasBlur) {
        key(effectiveCornerRadius) {
            val backdropShape =
                remember(effectiveCornerRadius) { ContinuousRoundedRectangle(effectiveCornerRadius.dp) }
            val blurPx =
                with(localDensity) { remember(cardBlurRadius) { cardBlurRadius.dp.toPx() } }
            val lensRadiusPx =
                with(localDensity) { remember(cardRefraction) { cardRefraction.lensRadiusDp.dp.toPx() } }
            val lensStrengthPx =
                with(localDensity) { remember(cardRefraction) { cardRefraction.lensStrengthDp.dp.toPx() } }
            // 白/黑底由「卡片不透明度」控制；课程色由「卡片着色程度」控制
            val overlayColor = remember(isDark, cardSurfaceAlpha) {
                if (isDark) Color.Black.copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
                else Color.White.copy(alpha = cardSurfaceAlpha.coerceIn(0f, 1f))
            }
            // backdrop 是 SharedBlurBackdrop 时，它的 drawBackdrop 画的是**已烘焙好的
            // 模糊壁纸层**，卡片不用再 blur —— 那正是「模糊 0 开销」的那一半。
            val isSharedBlur = wallpaperBackdrop is SharedBlurBackdrop
            val backdropEffects: BackdropEffectScope.() -> Unit =
                remember(isSharedBlur, blurPx, lensRadiusPx, lensStrengthPx, cardRefraction) {
                    {
                        if (!isSharedBlur) {
                            blur(blurPx)
                        }
                        if (cardRefraction != CardRefractionLevel.OFF) {
                            lens(lensRadiusPx, lensStrengthPx)
                        }
                    }
                }
            // 白/黑底 + 课程色压成一次 drawRect（两次全卡 fill 是每卡每帧的固定成本）
            val surfaceColor = remember(cardColor, overlayColor) {
                flattenStackedFills(overlayColor, cardColor)
            }
            // onDrawSurface 每次重组新建会令 drawBackdrop 判不等，每次重录壁纸层并重跑 GPU 模糊
            val onCardSurface: DrawScope.() -> Unit =
                remember(surfaceColor) {
                    { drawRect(surfaceColor) }
                }
            val outlineColor = remember(cardColor, isDark) { scheduleOutlineColor(cardColor, isDark) }
            val outlineWidthPx = remember(localDensity) {
                with(localDensity) { SCHEDULE_OUTLINE_WIDTH.toPx() }
            }
            val outlineCache = remember { OutlineCache() }
            // 静止卡不挂 graphicsLayer：几十张×两页的合成层是周滑 draw 热点
            val needTransformLayer = sinkPressed || sinkActive || isDragging || rippleAnimating

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .then(
                        if (disablePadding) Modifier else Modifier.padding(
                            horizontal = 2.dp,
                            vertical = 2.dp
                        )
                    )
                    .then(
                        if (needTransformLayer) {
                            Modifier.graphicsLayer {
                                val s = sinkScale.value * rippleScale.value
                                scaleX = s
                                scaleY = s
                                alpha = if (isDragging) 0f else 1f
                            }
                        } else {
                            Modifier
                        }
                    )
                    .onGloballyPositioned { coordinates ->
                        cardCoords[0] = coordinates
                        if (touchState?.scrolling == true) return@onGloballyPositioned
                        val center = coordinates.localToRoot(
                            Offset(
                                coordinates.size.width / 2f,
                                coordinates.size.height / 2f
                            )
                        )
                        cardBoundsPx[0] = center.x
                        cardBoundsPx[1] = center.y
                        cardBoundsPx[2] = coordinates.size.width.toFloat()
                        cardBoundsPx[3] = coordinates.size.height.toFloat()
                    }
                    // 垫底纯色画在采样层之前（更靠外=更早画），被玻璃采样盖住属预期
                    .then(
                        if (solidBackingColor != null) {
                            Modifier.background(solidBackingColor, backdropShape)
                        } else {
                            Modifier
                        }
                    )
                    .drawBackdrop(
                        backdrop = wallpaperBackdrop,
                        shape = { backdropShape },
                        effects = backdropEffects,
                        highlight = null,
                        shadow = null,
                        viewport = LocalBackdropViewport.current,
                        onDrawSurface = onCardSurface
                    )
                    .drawWithContent {
                        drawContent()
                        drawScheduleOutline(
                            cache = outlineCache,
                            cornerRadius = effectiveCornerRadius.dp,
                            color = outlineColor,
                            strokeWidthPx = outlineWidthPx,
                            layoutDirection = layoutDirection,
                        )
                    }
                    .courseCardGesture(
                        course = course,
                        touchState = touchState,
                        bounds = cardBoundsPx,
                        refreshBounds = refreshCardBounds,
                        scope = scope,
                        dragEnabled = dragEnabled,
                        onSinkChange = { sinkPressed = it },
                        onLongPressStart = onLongPressStart,
                        onClick = onClick,
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onMenuDismiss = onMenuDismiss,
                    ),
                contentAlignment = Alignment.Center
            ) {
                CardContent(
                    course,
                    sectionCount,
                    textColor,
                    hasMultipleCourses,
                    isTablet,
                    cardContentAlignment,
                    cardHeight.value,
                    cardHeightPerSection,
                    isHoliday,
                    isWorkSwap,
                    isCurrentWeek,
                    showClassroom,
                    showTeacher,
                    cardTextScale,
                    isDark,
                    isCourseCancelled,
                    courseTint = courseColors.tint
                )
            }
        }
    } else {
        val cardShape = remember(effectiveCornerRadius) {
            ContinuousRoundedRectangle(effectiveCornerRadius.dp)
        }
        // 静止卡不挂 graphicsLayer：几十张×两页的合成层是左右滑 draw 热点。
        // 仅按压/拖拽/落地涟漪动画时挂；松手即撤，缩放回弹让位给无层路径。
        val needTransformLayer = sinkPressed || sinkActive || isDragging || rippleAnimating
        // 纯色模式（无壁纸）不画描边：底色本身就和页面背景分得开，那圈线只会显脏
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(cardHeight)
                .then(
                    if (disablePadding) Modifier else Modifier.padding(
                        horizontal = 2.dp,
                        vertical = 2.dp
                    )
                )
                .then(
                    if (needTransformLayer) {
                        Modifier.graphicsLayer {
                            val s = sinkScale.value * rippleScale.value
                            scaleX = s
                            scaleY = s
                            alpha = if (isDragging) 0f else 1f
                            shape = cardShape
                            clip = true
                        }
                    } else {
                        Modifier
                    }
                )
                // 垫底纯色在下、课程色在上（链条越靠前越先画）
                .then(
                    if (solidBackingColor != null) Modifier.background(solidBackingColor, cardShape)
                    else Modifier
                )
                .background(cardColor, cardShape)
                .onGloballyPositioned { coordinates ->
                    // 与 hasBlur 分支一致：滑动中跳过 localToRoot，避免几十张卡每帧算坐标
                    cardCoords[0] = coordinates
                    if (touchState?.scrolling == true) return@onGloballyPositioned
                    val center = coordinates.localToRoot(
                        Offset(
                            coordinates.size.width / 2f,
                            coordinates.size.height / 2f
                        )
                    )
                    cardBoundsPx[0] = center.x
                    cardBoundsPx[1] = center.y
                    cardBoundsPx[2] = coordinates.size.width.toFloat()
                    cardBoundsPx[3] = coordinates.size.height.toFloat()
                }
                .courseCardGesture(
                    course = course,
                    touchState = touchState,
                    bounds = cardBoundsPx,
                    refreshBounds = refreshCardBounds,
                    scope = scope,
                    dragEnabled = dragEnabled,
                    onSinkChange = { sinkPressed = it },
                    onLongPressStart = onLongPressStart,
                    onClick = onClick,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onMenuDismiss = onMenuDismiss,
                )
        ) {
            CardContent(
                course,
                sectionCount,
                textColor,
                hasMultipleCourses,
                isTablet,
                cardContentAlignment,
                cardHeight.value,
                cardHeightPerSection,
                isHoliday,
                isWorkSwap,
                isCurrentWeek,
                showClassroom,
                showTeacher,
                cardTextScale,
                isDark,
                isCourseCancelled,
                courseTint = courseColors.tint
            )
        }
    }
}

/**
 * 课程卡手势：有壁纸 / 无壁纸两条渲染路径共用同一实现，避免各改一份产生漂移。
 *
 * - 轻点（位移 < 8dp）→ [onClick]
 * - 长按 320ms → [onLongPressStart] 弹菜单，随后移动即进拖拽态（浮层跟手）
 * - 拖拽态里位移超过容差（8dp）才算真的拖起来：此时才 [onDragStart]（点亮跟手高光）
 *   并 [onMenuDismiss] 收菜单（[dragEnabled] 为 false 时这两步都不发生）
 * - 长按前横滑主导 / 位移超容差 / 多指按下 → 放弃本次手势，把滚动让给 Pager
 *
 * ⚠ [dragEnabled] = false 时菜单**不随移动收起**：这类课唯一的操作入口就是菜单，
 * 手指抖一下就把菜单收掉等于没法用。松手后菜单继续留在屏幕上等用户点。
 */
private fun Modifier.courseCardGesture(
    course: Course,
    touchState: ScheduleTouchState?,
    bounds: FloatArray,
    refreshBounds: () -> Unit,
    scope: CoroutineScope,
    dragEnabled: Boolean,
    onSinkChange: (Boolean) -> Unit,
    onLongPressStart: (left: Float, top: Float, width: Float, height: Float) -> Unit,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (offsetX: Float, offsetY: Float) -> Unit,
    onDragEnd: () -> Unit,
    onMenuDismiss: () -> Unit,
): Modifier = pointerInput(course, dragEnabled) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        onSinkChange(true)
        val downPosition = down.position
        val slop = 8.dp.toPx()
        val horizontalSlop = 5.dp.toPx()
        var isLongPress = false
        var isDragging = false
        var menuShown = false
        val longPressJob = scope.launch {
            delay(320.milliseconds)
            // 多指（三指截图等）不当成长按
            if (touchState?.multiTouch == true) return@launch
            isLongPress = true
            onSinkChange(false)
            menuShown = true
            refreshBounds()
            onLongPressStart(bounds[0], bounds[1], bounds[2], bounds[3])
        }
        try {
            while (true) {
                // 菜单/拖拽需要拦穿透用 Main pass；否则 Final pass 让父级滚动先处理
                val pass =
                    if (menuShown || isDragging) PointerEventPass.Main else PointerEventPass.Final
                val event = awaitPointerEvent(pass)

                if (event.changes.none { it.pressed }) {
                    onSinkChange(false)
                    when {
                        isDragging -> if (menuShown) onDrag(0f, 0f) else onDragEnd()
                        menuShown -> Unit
                        else -> {
                            val up = event.changes.firstOrNull()
                            if (up != null) {
                                up.consume()
                                // 多指时不点击：系统手势（三指截图）拦截后 Compose 会补发一个
                                // 合成的「全部抬起」事件，看起来和松手一样，只能靠页面级多指标记区分
                                val multiTouch = touchState?.multiTouch == true
                                if (!isLongPress && !multiTouch &&
                                    (up.position - downPosition).getDistance() < slop
                                ) onClick()
                            }
                        }
                    }
                    break
                }

                val pos = event.changes.firstOrNull()?.position ?: continue
                val dx = pos.x - downPosition.x
                val dy = pos.y - downPosition.y
                val moved = pos.minus(downPosition).getDistance()

                // 长按前横滑主导 → 交给 Pager 翻页
                if (!isLongPress && !isDragging && !menuShown &&
                    abs(dx) > horizontalSlop && abs(dx) > abs(dy) * 1.2f
                ) {
                    onSinkChange(false)
                    break
                }
                // 长按前位移超容差 → 取消长按
                if (!isLongPress && moved > slop) {
                    onSinkChange(false)
                    event.changes.forEach { it.consume() }
                    break
                }
                // 菜单已弹出：手指一动就进「拖拽态」，浮层开始跟手。
                // ⚠ 这里**不**调 onDragStart —— 它点亮的是跟手高光，抖动几像素就亮
                //   会很廉价；高光留给下面真正「拖起来」的那一刻。
                // ⚠ dragEnabled = false（自定义时间课）时整段跳过：菜单留在原地等用户点
                if (dragEnabled && menuShown && !isDragging) {
                    isDragging = true
                    onSinkChange(false)
                }
                // 位移超容差 = 真的拖起来了：此刻收菜单，并点亮跟手高光
                if (dragEnabled && menuShown && moved > slop) {
                    menuShown = false
                    onDragStart()
                    onMenuDismiss()
                }
                if (dragEnabled && isDragging) onDrag(dx, dy)
                event.changes.forEach { it.consume() }
            }
        } finally {
            longPressJob.cancel()
        }
    }
}

@Composable
private fun CardContent(
    course: Course,
    sectionCount: Int,
    textColor: Color,
    hasMultipleCourses: Boolean,
    isTablet: Boolean = false,
    cardContentAlignment: CardContentAlignment = CardContentAlignment.CENTER_CENTER,
    cardHeightDp: Float = 0f,
    cardHeightPerSection: Float = 54f,
    isHoliday: Boolean = false,
    isWorkSwap: Boolean = false,
    isCurrentWeek: Boolean = true,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    cardTextScale: Float = 1f,
    isDark: Boolean = isAppDarkTheme(),
    isCourseCancelled: Boolean = false,
    courseTint: Color = Color(course.colorRes)
) {
    val infoFontSize = 11.sp * cardTextScale.coerceIn(0.5f, 2.0f)
    val infoLineHeight = 12.sp * cardTextScale.coerceIn(0.5f, 2.0f)
    val courseNameFontSize = 12.7.sp * cardTextScale.coerceIn(0.5f, 2.0f)
    val courseNameLineHeight = 14.2.sp * cardTextScale.coerceIn(0.5f, 2.0f)

    val effectiveShowClassroom = showClassroom && course.classroom.isNotEmpty()
    val effectiveShowTeacher = showTeacher && course.teacher.isNotEmpty()

    // 卡片高度能放下几行就几行（单 Text 后按总行数限）
    val density = LocalDensity.current
    val availableHeightDp = cardHeightDp - 16f
    val courseNameLineH = with(density) { courseNameLineHeight.toDp().value }
    val infoLineH = with(density) { infoLineHeight.toDp().value }
    val reservedForInfo =
        ((if (effectiveShowClassroom) 1 else 0) + (if (effectiveShowTeacher) 1 else 0)) * infoLineH
    val nameMaxLines =
        ((availableHeightDp - reservedForInfo) / courseNameLineH).toInt().coerceAtLeast(1)
    val bodyMaxLines = nameMaxLines +
            (if (effectiveShowClassroom) 1 else 0) +
            (if (effectiveShowTeacher) 1 else 0)

    Box(modifier = Modifier.fillMaxSize()) {
        val verticalArrangement = when (cardContentAlignment) {
            CardContentAlignment.TOP_START,
            CardContentAlignment.TOP_CENTER -> Arrangement.Top

            CardContentAlignment.CENTER_START,
            CardContentAlignment.CENTER_CENTER -> Arrangement.Center
        }
        val horizontalAlignment = when (cardContentAlignment) {
            CardContentAlignment.TOP_START,
            CardContentAlignment.CENTER_START -> Alignment.Start

            CardContentAlignment.TOP_CENTER,
            CardContentAlignment.CENTER_CENTER -> Alignment.CenterHorizontally
        }
        val textAlign = when (cardContentAlignment) {
            CardContentAlignment.TOP_START,
            CardContentAlignment.CENTER_START -> TextAlign.Start

            CardContentAlignment.TOP_CENTER,
            CardContentAlignment.CENTER_CENTER -> TextAlign.Center
        }
        // 课名+地点+教师合成一段 Text：省掉每卡 2 个 Text + 2 个 Spacer 的测量/绘制节点
        val bodyText = buildAnnotatedString {
            pushStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = courseNameFontSize,
                )
            )
            append(course.name)
            pop()
            if (effectiveShowClassroom) {
                append('\n')
                pushStyle(SpanStyle(fontSize = infoFontSize))
                append("@${course.classroom}")
                pop()
            }
            if (effectiveShowTeacher) {
                append('\n')
                pushStyle(SpanStyle(fontSize = infoFontSize))
                append(course.teacher)
                pop()
            }
        }
        // 行高用课名（较大者），保证换行间距与原多 Text 布局接近
        val bodyLineHeight = courseNameLineHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement
        ) {
            Text(
                text = bodyText,
                lineHeight = bodyLineHeight,
                color = textColor,
                textAlign = textAlign,
                maxLines = bodyMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (hasMultipleCourses) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (isTablet) 6.dp else 5.dp)
                    .size(8.dp)
                    .background(color = textColor, shape = CircleShape)
            )
        }

        val badge = resolveCourseCardBadge(
            isHoliday = isHoliday,
            isCourseCancelled = isCourseCancelled,
            isWorkSwap = isWorkSwap,
            isCurrentWeek = isCurrentWeek,
        )
        if (badge != null) {
            val badgeBackground = if (badge.usesWorkSwapStyle) {
                courseTint.copy(alpha = 0.32f)
            } else {
                if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
            }
            Text(
                text = badge.text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = if (badge.usesWorkSwapStyle) {
                    if (MiuixTheme.isDynamicColor) textColor
                    else if (isDark) Color.White.copy(alpha = 0.8f) else Color.White
                } else {
                    if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f)
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (isTablet) 6.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeBackground)
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                maxLines = 1
            )
        }

        if (course.hasValidCustomTime() && cardHeightDp >= 2 * cardHeightPerSection) {
            Text(
                text = course.customStartTime ?: "",
                fontSize = 8.sp,
                color = textColor,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(if (isTablet) 6.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(textColor.copy(alpha = 0.1f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                maxLines = 1
            )
            Text(
                // 跨午夜标注「次日」，否则 23:00 / 00:30 两个角标看着像两节课
                text = if (course.crossesMidnightCustomTime()) {
                    "次日${course.customEndTime ?: ""}"
                } else {
                    course.customEndTime ?: ""
                },
                fontSize = 8.sp,
                color = textColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(if (isTablet) 6.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(textColor.copy(alpha = 0.1f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                maxLines = 1
            )
        }
    }
}

/**
 * 把「底下一层纯色 + 上面一层纯色」的两次 SrcOver 叠加压成**一次** drawRect
 */
internal fun flattenStackedFills(bottom: Color, top: Color): Color {
    val a1 = bottom.alpha
    val a2 = top.alpha
    val a = 1f - (1f - a1) * (1f - a2)
    if (a <= 0f) return Color.Transparent
    val w1 = a1 * (1f - a2) / a
    val w2 = a2 / a
    return Color(
        red = bottom.red * w1 + top.red * w2,
        green = bottom.green * w1 + top.green * w2,
        blue = bottom.blue * w1 + top.blue * w2,
        alpha = a
    )
}

// 超椭圆 outline 构建昂贵，滑动时约 40~80 张×2 页；仅尺寸/圆角/layoutDirection 变时重建
internal class OutlineCache {
    var width: Float = Float.NaN
    var height: Float = Float.NaN
    var radius: Float = Float.NaN
    var layoutDirection: LayoutDirection =
        LayoutDirection.Ltr
    var outline: Outline? = null
}

/** 课表内元素（课程卡 / 午休晚休分界线 / 空槽加号卡）的纯色描边宽度 */
internal val SCHEDULE_OUTLINE_WIDTH = 2.dp
/** 纯色描边 alpha */
private const val SCHEDULE_OUTLINE_ALPHA = 0.08f
/** 底色饱和度低于此值视为中性（白 / 浅灰 / 深灰），描边改用黑或白 */
private const val SCHEDULE_OUTLINE_NEUTRAL_S = 0.08f

/**
 * 课表内元素的纯色描边颜色。
 *
 * 有色底（课程色）→ 同色淡描边，保留课程色的体积感；中性底（分界线、加号卡、
 * 非动态取色）→ 浅色黑、深色白，否则同色描边在白/灰底上完全看不见。
 */
internal fun scheduleOutlineColor(baseColor: Color, isDark: Boolean): Color {
    val v = maxOf(baseColor.red, maxOf(baseColor.green, baseColor.blue))
    val minC = minOf(baseColor.red, minOf(baseColor.green, baseColor.blue))
    val saturation = if (v > 0f) (v - minC) / v else 0f
    return when {
        saturation > SCHEDULE_OUTLINE_NEUTRAL_S -> baseColor.copy(alpha = SCHEDULE_OUTLINE_ALPHA)
        isDark -> Color.White.copy(alpha = SCHEDULE_OUTLINE_ALPHA)
        else -> Color.Black.copy(alpha = SCHEDULE_OUTLINE_ALPHA)
    }
}

/**
 * 纯色描边（不发光、不采样，就是一次 [drawOutline]），复用 [OutlineCache] 避免每帧重建。
 *
 * 在 `drawWithContent { drawContent(); drawScheduleOutline(...) }` 里调用，描边压在内容之上。
 * 注意 `drawBackdrop` 之后调用时外半圈会被它的 shape clip 裁掉，实际呈现为内描边。
 */
internal fun DrawScope.drawScheduleOutline(
    cache: OutlineCache,
    cornerRadius: Dp,
    color: Color,
    strokeWidthPx: Float,
    layoutDirection: LayoutDirection,
) {
    if (cache.width != size.width ||
        cache.height != size.height ||
        cache.radius != cornerRadius.value ||
        cache.layoutDirection != layoutDirection
    ) {
        cache.outline = ContinuousRoundedRectangle(cornerRadius)
            .createOutline(size, layoutDirection, this)
        cache.width = size.width
        cache.height = size.height
        cache.radius = cornerRadius.value
        cache.layoutDirection = layoutDirection
    }
    drawOutline(outline = cache.outline!!, color = color, style = Stroke(strokeWidthPx))
}
