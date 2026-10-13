package com.haooz.chedule.ui.navigation

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.haooz.chedule.ui.effects.motion.OobeQuartOutSoftStartEasing
import com.haooz.chedule.ui.effects.motion.SecondaryPageExitEasing
import com.haooz.chedule.ui.utils.PredictiveBackSettings
import com.haooz.chedule.ui.utils.rememberScreenCornerRadiusPx
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.rememberNavigationBack
import kotlin.math.roundToInt

/**
 * 子页叠加层：**整栈常驻渲染** + 在有子页时接管系统返回。
 *
 * 栈内每一层都保持组合（任意深度 pop 回去都是活页而非重建），静止时按三档暂停绘制
 *（栈顶常画 / 直接下层只在转场·手势时画 / 更深层永不画）—— 等价 stop 的 Activity。
 * 栈空时返回键交还宿主（Android 的预测性返回 / iOS 靠页面自己的返回按钮）。
 *
 * **转场只有一个进度 `p`**：栈顶页 `(1-p)×W`、下一层 `-0.24×p×W`、黑幕 `alpha = 0.42×p`。
 * push = 0→1、pop = 1→0，公式同一套只方向相反 —— 这是打断续播与手势跟手的前提。
 *
 * ⚠ 别改回 `AnimatedContent` 的 `EnterExitTransition`：那是两条独立动画，拿不到中间进度，
 * 既不能半路续播也不能跟手（它 `sizeTransform` 默认非空，还会让退出「缩小并弹出」）。
 *
 * ⚠ 两处必须**同帧**做完，否则会闪：push 起始帧要在组合期把 p 归零（`Animatable.snapTo`
 * 是 suspend，赶不上当帧绘制）；pop 结束时清 `outgoing` 与 p 拉回 1 也要同帧，
 * 否则新栈顶会按 `(1-0)×W` 被摆到屏外。
 *
 * ⚠ 仍必须用 `SaveableStateHolder`：整栈常驻只覆盖**在栈期间**，出栈的页面组合销毁，
 * 滚动位置由 holder 按 route.id 托管。已知取舍：出栈不清状态，重开同一页会回到上次位置。
 *
 * 返回手势走 `:miuix` 的 `rememberNavigationBack()` 跨平台封装（Android 包
 * navigationevent，skiko 侧空实现 —— 系统手势由宿主接管，Kotlin 侧拿不到）。
 */
@Composable
fun AppNavHost(
    router: AppRouter,
    /** 主界面（常驻底座）的首层转场进度：它不在这个转场容器里，视差只能由宿主施加。 */
    mainTransition: NavTransitionState,
    modifier: Modifier = Modifier,
    onOverlayActivityChanged: ((Boolean) -> Unit)? = null,
    content: @Composable (AppRoute) -> Unit,
) {
    // key 由 layerKeys() 统一算（裸 route.id + 同帧去重后缀）：
    // 出栈动画里的那一层与栈内层不能撞 key，同一路由重复入栈也不行 —— 见 layerKeys 的 KDoc
    val stateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()

    val progress = rememberNavTransitionState(initialValue = 1f)
    // pop 期间仍在屏幕上的那一层：已出栈，但要跟着把退出动画播完
    var outgoing by remember { mutableStateOf<AppRoute?>(null) }

    /**
     * 出场收尾：从当前进度续播到 0，**完整播完**才同帧清 [outgoing] 并把 p 拉回 1
     *（只清 `outgoing` 而 p 停在 0 的话，新栈顶会被摆到屏外）。
     *
     * ⚠ 半路被打断时（预测性返回手势接管）什么都不做 —— 那时 p 已归手势驱动，
     * 再拉回 1 会把正在跟手的那一层瞬间甩到屏外。靠 [NavTransitionState.animateTo]
     * 的返回值判断是否被打断。
     *
     * ⚠ 还要比对「收的是不是还是同一层」：这次收尾挂在 `rememberCoroutineScope` 上、
     * **不随导航自动取消** —— 播到一半用户又 push 了新页时，不比对会把新页的入场动画掐掉。
     */
    val settleExit: suspend () -> Unit = {
        val exiting = outgoing
        if (exiting != null) {
            val from = progress.value.coerceIn(0f, 1f)
            // 从当前值续播到 0：可能是手势松手的剩余距离，也可能是被打断的位置
            val (dur, easing) = settleOnGlobalCurve(
                fromProgress = from,
                toProgress = 0f,
                fullEasing = NAV_EXIT_EASING,
                totalDuration = NAV_EXIT_DURATION,
                invertGlobal = true,
                minDuration = MIN_GESTURE_SETTLE_MS,
            )
            val finished = coroutineScope {
                val exit = async { progress.animateTo(0f, tween(dur, easing = easing)) }
                if (router.depth == 0) {
                    launch { mainTransition.animateTo(0f, tween(dur, easing = easing)) }
                }
                exit.await()
            }
            if (finished && outgoing === exiting) {
                // 同帧收尾：只清 outgoing 而 p 停在 0 的话，新栈顶会被摆到屏外
                outgoing = null
                progress.snapTo(1f)
            }
        }
    }

    // 转场起点必须在组合期对齐：LaunchedEffect 晚一帧，那一帧会闪
    var lastRoute by remember { mutableStateOf(router.current) }
    if (lastRoute !== router.current) {
        lastRoute = router.current
        when (router.lastDirection) {
            NavDirection.Push -> {
                val interrupted = outgoing
                // ⚠ 必须**同帧**摘掉 outgoing：出场动画没播完又点进同一页时，
                // outgoing 与新栈顶是同一个 route.id，同一帧里两次 SaveableStateProvider(同 key)
                // → SaveableStateHolderImpl 直接抛 IllegalArgumentException 崩掉（实测）。
                outgoing = null
                // 同一个 route 被打断（出场动画里又点进来）：这一层从没销毁过，从当前进度续播；
                // 换了 route 才是新页，才归零从屏外右侧推入。
                if (interrupted?.id != router.current?.id) progress.snapTo(0f)
            }
            // pop 起点就是 1，不用动 p，只要留住被弹出的那层
            NavDirection.Pop -> outgoing = router.lastPopped
        }
    }

    // 预测性返回：跟手逐帧落值；松手完成 → 出栈（剩余距离交给下面的动画续播）；取消 → 弹回。
    // ⚠ handler 与 backProgress 必须来自同一个 rememberNavigationBack（同一份底层状态），
    // 拆成两处各自 remember = 编译全绿但跟手动画永远不动（miuix fork 踩过，见其 KDoc）。
    val back = rememberNavigationBack()
    back.handler(
        enabled = router.hasOverlay,
        onBackCompleted = { router.popBack() },
        onBackCancelled = {
            if (outgoing != null) {
                // 手势打断的是一次还没播完的出场：「回弹」= 把那次出场播完 ——
                // 用户已经决定离开这一页，取消手势只表示「别把下面那层也弹掉」。
                //（不这么走的话会把已出栈的那层又拉回全屏盖住新栈顶。）
                scope.launch { settleExit() }
            } else {
                val from = progress.value.coerceIn(0f, 1f)
                val (dur, easing) = settleOnGlobalCurve(
                    fromProgress = from,
                    toProgress = 1f,
                    fullEasing = NAV_ENTER_EASING,
                    totalDuration = NAV_ENTER_DURATION,
                    invertGlobal = false,
                    minDuration = MIN_GESTURE_RESTORE_MS,
                )
                scope.launch {
                    launch { progress.animateTo(1f, tween(dur, easing = easing)) }
                    // 首层返回时主界面也在跟手，一起弹回
                    if (router.depth == 1) {
                        launch { mainTransition.animateTo(1f, tween(dur, easing = easing)) }
                    }
                }
            }
        },
    )
    LaunchedEffect(Unit) {
        // backProgress：手势期间为 0..1 的连续进度，非手势期间为 null（skiko 恒为 null）。
        // Android 上它读 navigationevent 的 snapshot state，snapshotFlow 能跟上逐帧变化。
        //
        // ⚠ 手势必须**按手势起点成比例**往回落，不能写成 `p = 1 - 手势进度`：
        // 入场动画没播完就滑返回时（p 还在 0.4），第一帧手势进度≈0 会把 p 直接拉到 1 ——
        // 页面先「跳」到完全覆盖再跟手。所以先冻结 `gestureBase`，再 `p = gestureBase × (1 - 进度)`。
        var gestureBase = 1f
        var mainGestureBase = 1f
        var sawGesture = false
        snapshotFlow { back.backProgress() }.collect { gestureProgress ->
            if (gestureProgress == null) {
                sawGesture = false
                return@collect
            }
            if (!PredictiveBackSettings.enabled) return@collect
            if (!sawGesture) {
                sawGesture = true
                gestureBase = progress.value.coerceIn(0f, 1f)
                mainGestureBase = mainTransition.value.coerceIn(0f, 1f)
            }
            val remain = (1f - gestureProgress).coerceIn(0f, 1f)
            // snapTo 会把还在跑的转场动画挤掉（NavTransitionState 的 mutation）；
            // 否则被打断的入场动画下一帧就把手势落的值盖回去，跟手不动。
            progress.snapTo(gestureBase * remain)
            // 栈里只剩一层时，被推的是主界面
            if (router.depth == 1) mainTransition.snapTo(mainGestureBase * remain)
        }
    }

    LaunchedEffect(router.current) {
        if (router.lastDirection == NavDirection.Pop) {
            settleExit()
        } else {
            val from = progress.value.coerceIn(0f, 1f)
            // minDuration = 0：纯续播
            val (dur, easing) = settleOnGlobalCurve(
                fromProgress = from,
                toProgress = 1f,
                fullEasing = NAV_ENTER_EASING,
                totalDuration = NAV_ENTER_DURATION,
                invertGlobal = false,
                minDuration = 0,
            )
            coroutineScope {
                launch { progress.animateTo(1f, tween(dur, easing = easing)) }
                // 首层 push：被推的是主界面（常驻底座），视差由宿主施加
                if (router.depth == 1) {
                    launch { mainTransition.animateTo(1f, tween(dur, easing = easing)) }
                }
            }
        }
    }

    val routes = router.routes

    // 屏幕圆角：转场中间态把上层裁成圆角，让它像一张卡片从右边推进来。
    // 只对上层做 —— 下层是被揭开的那一层，不该有轮廓。
    val cornerRadiusPx = rememberScreenCornerRadiusPx()
    val density = LocalDensity.current
    val screenClipShape = remember(cornerRadiusPx, density) {
        if (cornerRadiusPx > 0f) {
            with(density) { ContinuousRoundedRectangle(cornerRadiusPx.toDp()) }
        } else {
            null
        }
    }
    val inFlight: () -> Boolean = {
        val p = progress.value
        p > 0f && p < 1f
    }

    // ── SaveableStateHolder 的 key ─────────────────────────────────────────
    // ⚠ 同一帧里同一个 key 只能挂一份，否则 SaveableStateHolderImpl 直接抛
    // IllegalArgumentException("Key xxx was used multiple times")，整屏崩。两条路径：
    //   ① 出场动画没播完又点进同一页（outgoing 与新栈顶同 route.id）
    //   ② 连点两次同一入口（`AppRouter.navigate` 允许重复入栈）
    //
    // ⚠ **别包 `remember(routes, outgoing)`**：`routes` 是 `SnapshotStateList`，没有重写
    // equals/hashCode（走引用相等）→ `Composer.changed` 判「没变」→ remember 永不重算 →
    // `topKey == null` → **二级页一个都不组合**。直接算即可，开销是几个字符串。
    val (stackKeys, exitKey) = layerKeys(routes, outgoing)

    // ── 这一帧要渲染的层 ───────────────────────────────────────────────────
    // 栈内各层 +（pop 期间）已出栈、正在播退场的那层（排在最后 = 画在最上面）。
    //
    // ⚠ **所有层都走同一个循环**：每层的组合槽位只由「它在列表里的下标」决定。
    // 把栈顶单独放在循环**外**的话，push 时那层要换槽位 = 新挂一份，
    // 新槽位的 `SaveableStateRegistry` 读到的还是空的 → `rememberSaveable` 全部重建。
    // 栈只在尾部 append / 移除，下标天然稳定（二级 A → 三级 B 时 A 仍在 `[0]`、B 在 `[1]`）。
    val exitingLayer = outgoing
    val layers: List<AppRoute> = if (exitingLayer != null) routes + exitingLayer else routes
    val renderKeys: List<String> = if (exitKey != null) stackKeys + exitKey else stackKeys
    // Keep native input blocked until the outgoing page has finished drawing.
    SideEffect { onOverlayActivityChanged?.invoke(layers.isNotEmpty()) }

    // 黑幕：只压暗被盖住的内容，推入/退出的顶层不被压暗。挂在最上层自己的 `drawBehind`
    //（画在「它自己的内容之下、之前所有兄弟之上」，且在全屏未平移的坐标系里）。
    val scrimAlpha: () -> Float = {
        val p = progress.value
        // p 到位（顶层完全盖住）就不画：省一次全屏 alpha 混合
        if (p < 0.999f) NAV_BG_DIM * p else 0f
    }

    Box(modifier.fillMaxSize()) {
        // ── 整栈常驻：栈内每层都保持组合，任意深度 pop 回去都是活页而非重建。
        // 绘制三档：
        //   最上层   → 常画（全屏圆角裁切 + 黑幕，都在 PageLayer 里）
        //   直接下层 → p<1 就画，p==1（被完全盖住）才暂停。
        //              ⚠ 不能用「0<p<1」判：push 起始帧 p 恰好=0、顶层还在屏外，
        //              那一帧下层必须画，否则会闪出主界面（实测踩过）
        //   更深层   → 永远不画，只保留组合与状态
        val topIndex = layers.lastIndex
        for (index in layers.indices) {
            val page = layers[index]
            val isTop = index == topIndex
            val isDirectUnder = index == topIndex - 1
            val fraction: () -> Float = when {
                isTop -> ({ 1f - progress.value })
                isDirectUnder -> ({ -NAV_PARALLAX * progress.value })
                // 更深层固定停靠位：p 永远不影响它们
                else -> ({ -NAV_PARALLAX })
            }
            val paused: () -> Boolean = when {
                isTop -> ({ false })
                isDirectUnder -> ({ progress.value >= 1f })
                else -> ({ true })
            }
            PageLayer(
                fraction = fraction,
                paused = paused,
                clipShape = if (isTop) screenClipShape else null,
                scrimAlpha = if (isTop) scrimAlpha else null,
                inFlight = inFlight,
            ) {
                stateHolder.SaveableStateProvider(renderKeys[index]) { content(page) }
            }
        }
    }
}

/**
 * 给「这一帧要渲染的每一层」算 [androidx.compose.runtime.saveable.SaveableStateHolder] 的 key，
 * 返回 `(栈内各层的 key, 出栈层的 key)`。
 *
 * ⚠ 同一个 key 在同一帧出现两次会抛 `IllegalArgumentException`，所以保证整体唯一：
 * - 栈内重复入栈：首次保持**裸 id**，其后 `#2` / `#3` —— 裸 id 才能让重开同一页恢复滚动位置。
 * - 出栈层**后算**：先算会把它挤到 `#2`，出场动画里那层的 saveable 状态会当场重置。
 */
private fun layerKeys(routes: List<AppRoute>, outgoing: AppRoute?): Pair<List<String>, String?> {
    val used = HashSet<String>(routes.size + 1)
    val stackKeys = ArrayList<String>(routes.size)
    for (route in routes) stackKeys += uniqueKey(route.id, used)
    return stackKeys to outgoing?.let { uniqueKey(it.id, used) }
}

/** 取 [id]，已被占用就依次试 `#2` / `#3`… 直到拿到一个没被占的。 */
private fun uniqueKey(id: String, used: MutableSet<String>): String {
    if (used.add(id)) return id
    var n = 2
    while (!used.add("$id#$n")) n++
    return "$id#$n"
}

/**
 * 按「占自身宽度的比例」横向摆位。
 *
 * ⚠ 用 `Modifier.layout` 自己 place：`offset` 会把位移写进 constraints，子节点每帧真正
 * re-measure（整页列表/pager/玻璃采样跟着重测，必掉帧）；`graphicsLayer { translationX }`
 * 不重测但会给整页套离屏 layer，可能干扰页内 `layerBackdrop` 采样。
 */
@Composable
private fun PageLayer(
    fraction: () -> Float,
    paused: () -> Boolean = { false },
    clipShape: Shape? = null,
    /**
     * 非 null 时在本层内容**之下**画一层全屏黑幕（只给最上面那层传，见 [AppNavHost] 的
     * `scrimAlpha`）：夹在「下层各页」与「本层」之间，只压暗被盖住的内容。
     */
    scrimAlpha: (() -> Float)? = null,
    inFlight: () -> Boolean = { false },
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            // 暂停绘制：层还在组合里（状态、协程全活着），只是不进渲染管线 ——
            // 等价 stop 的 Activity 不画窗口。放最外层：暂停时连内层 clip 都跳过。
            .skipDrawWhen(paused)
            // ⚠ 黑幕必须放在 layout **外层**（和下面 clip 相反）：它要全屏不动 ——
            // layout 更内层的坐标系是跟着页面平移过的，放里面黑幕会跟着页面一起滑。
            .then(
                if (scrimAlpha == null) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        val a = scrimAlpha()
                        if (a > 0.001f) drawRect(Color.Black.copy(alpha = a))
                    }
                },
            )
            // ⚠ 顺序关键：clip 必须在 layout **之后**（更内层）—— 位移是 layout 在摆放阶段
            // 做的，clip 在内层时坐标系跟着页面平移，圆角才长在页面自己的角上。
            // 放到 layout 外层的话裁切锚在屏幕四角，页面的角只有滑到贴边时才与屏幕角重合，
            // 看起来等于没生效。
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place((fraction() * placeable.width).roundToInt(), 0)
                }
            }
            .clipDuringTransition(clipShape, inFlight),
    ) { content() }
}

/**
 * 转场中间态才把页面裁成屏幕圆角，到位后恢复直角 —— 系统窗口本身已有圆角，
 * Compose 再裁一次会在角上叠出双圆角。
 *
 * ⚠ 在 draw 阶段自己 `clipPath`，**不用** `graphicsLayer { clip = true }`：
 * 后者会给整页套一层离屏 layer，可能干扰页内的 `layerBackdrop` 采样。
 */
private fun Modifier.clipDuringTransition(shape: Shape?, inFlight: () -> Boolean): Modifier =
    if (shape == null) this else this then TransitionClipElement(shape, inFlight)

/**
 * 暂停绘制（「整栈常驻」策略的一半，另一半是栈内每层都保持组合）。
 *
 * [paused] 在 draw 阶段读 —— 恢复绘制时由快照观察自动失效重画；
 * [SkipDrawElement.update] 里还要显式 `invalidateDraw()`：pop 刚结束时某层从
 *「更深层」升为「栈顶」，paused lambda 换了引用但没有任何进度帧在跑，不显式失效就没人重画。
 */
private fun Modifier.skipDrawWhen(paused: () -> Boolean): Modifier =
    this then SkipDrawElement(paused)

private data class SkipDrawElement(
    private val paused: () -> Boolean,
) : ModifierNodeElement<SkipDrawNode>() {
    override fun create() = SkipDrawNode(paused)
    override fun update(node: SkipDrawNode) {
        node.paused = paused
        node.invalidateDraw()
    }
}

private class SkipDrawNode(
    var paused: () -> Boolean,
) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        if (!paused()) drawContent()
        // paused 时什么都不画：组合、状态、协程全部保留，只是不进渲染
    }
}

private data class TransitionClipElement(
    private val shape: Shape,
    private val inFlight: () -> Boolean,
) : ModifierNodeElement<TransitionClipNode>() {
    override fun create() = TransitionClipNode(shape, inFlight)
    override fun update(node: TransitionClipNode) {
        node.shape = shape
        node.inFlight = inFlight
    }
}

private class TransitionClipNode(
    var shape: Shape,
    var inFlight: () -> Boolean,
) : Modifier.Node(), DrawModifierNode {
    private val path = Path()

    // inFlight() 在这里读进度 → 只 invalidate draw，不会触发重组
    override fun ContentDrawScope.draw() {
        if (!inFlight() || size.isEmpty()) {
            drawContent()
            return
        }
        path.rewind()
        path.addOutline(shape.createOutline(size, layoutDirection, this))
        // clipPath 的 block receiver 是 DrawScope，drawContent() 得指名外层
        clipPath(path) { this@draw.drawContent() }
    }
}

/**
 * 在开/关曲线上取从 [fromProgress] 到 [toProgress] 的**剩余段**。
 * 用于打断续播与手势吸附：半路接手时速度连续，而不是从头按一条新曲线重播。
 *
 * - [invertGlobal] = false：全局进度 = `fullEasing(u)`（入场 0→1）
 * - [invertGlobal] = true：全局进度 = `1 - fullEasing(u)`（出场 1→0）
 *
 * @return (剩余时长, 把该尾段映射到 0..1 的局部 easing)
 */
private fun settleOnGlobalCurve(
    fromProgress: Float,
    toProgress: Float,
    fullEasing: Easing,
    totalDuration: Int,
    invertGlobal: Boolean,
    minDuration: Int,
): Pair<Int, Easing> {
    val from = fromProgress.coerceIn(0f, 1f)
    val to = toProgress.coerceIn(0f, 1f)
    val span = to - from
    if (kotlin.math.abs(span) < 1e-4f) {
        return minDuration.coerceAtMost(totalDuration) to fullEasing
    }

    // 全局进度 = from 时，fullEasing 的参数 u0
    val eAtFrom = if (invertGlobal) 1f - from else from
    val u0 = inverseEasingTime(fullEasing, eAtFrom)
    val durationMs = ((1f - u0) * totalDuration).toInt().coerceIn(minDuration, totalDuration)

    val local = Easing { fraction ->
        val u = u0 + (1f - u0) * fraction.coerceIn(0f, 1f)
        val e = fullEasing.transform(u)
        val global = if (invertGlobal) 1f - e else e
        ((global - from) / span).coerceIn(0f, 1f)
    }
    return durationMs to local
}

/** 二分求 `easing(u) = progress` 的 u ∈ [0,1] */
private fun inverseEasingTime(easing: Easing, progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0f) return 0f
    if (p >= 1f) return 1f
    var lo = 0f
    var hi = 1f
    repeat(18) {
        val mid = (lo + hi) * 0.5f
        if (easing.transform(mid) < p) lo = mid else hi = mid
    }
    return (lo + hi) * 0.5f
}

/** 二级页转场参数 */
internal const val NAV_ENTER_DURATION = 600
internal const val NAV_EXIT_DURATION = 320
// NAV_PARALLAX 在同包的 NavTransitionState.kt（本模块），无需 import。
// 压暗只在本文件用，保持 internal。
internal const val NAV_BG_DIM = 0.42f

/** 入场：更晚进入减速，尾段拖得更长更慢 */
internal val NAV_ENTER_EASING = OobeQuartOutSoftStartEasing
/** 退出：起步更缓，后段正常滑出 */
internal val NAV_EXIT_EASING = SecondaryPageExitEasing

private const val MIN_GESTURE_SETTLE_MS = 100
private const val MIN_GESTURE_RESTORE_MS = 200
