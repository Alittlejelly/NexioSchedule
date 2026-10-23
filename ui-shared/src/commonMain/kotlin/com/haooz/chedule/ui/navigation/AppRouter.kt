package com.haooz.chedule.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * 供深处的 Composable（如 `SettingsScreen`）发起路由跳转，**不经过 Activity 引用**。
 *
 * 在 `MainActivity.setContent` 的最外层 provide。⚠ 只在主线程（组合）里用，不加锁。
 */
val LocalAppRouter = staticCompositionLocalOf<AppRouter> {
    error("LocalAppRouter 未提供：请在 MainActivity.setContent 最外层 CompositionLocalProvider")
}

/** 最近一次导航的方向，供转场动画判方向。 */
enum class NavDirection { Push, Pop }

/**
 * 子页叠加路由器：主界面（`CourseScheduleApp`）永远保持组合、不参与路由 save/restore，子页以叠加层压在其上；栈内子页整栈常驻，出栈才销毁组合（saveable 状态由 `SaveableStateHolder` 托管，见 [AppNavHost]）。静止时底层页暂停绘制，等价 Activity back stack。
 *
 * ⚠ 主界面不能塞进 `AnimatedContent` + `SaveableStateHolder`：`rememberSaveable` 救得回，`remember { mutableStateOf }` 救不回，`CourseScheduleApp` 里大量此类状态会丢（切设置再回来 pager 位置变了）。
 *
 * 用 [rememberAppRouter] 创建，`rememberSaveable` 保存当前栈，配置变更/进程被杀后都能回到原页面。只在主线程（组合）改。
 */
@Stable
class AppRouter {

    private val stack = mutableStateListOf<AppRoute>()

    /** An optional platform owner can consume selected routes without changing this stack. */
    internal var nativeRouteHandler: ((AppRoute) -> Boolean)? = null

    /** A platform-owned page can consume programmatic completion/back without popping this stack. */
    internal var nativeBackHandler: (() -> Boolean)? = null

    /** 是否有子页叠加在主界面之上。false 时返回键交给 Android 宿主自己处理。 */
    val hasOverlay: Boolean get() = stack.isNotEmpty()

    /** 当前叠加的路由；没有子页时为 null（此时主界面直接可见）。 */
    val current: AppRoute? get() = stack.lastOrNull()

    val depth: Int get() = stack.size

    var lastDirection: NavDirection = NavDirection.Push
        private set

    /**
     * 最近一次被 pop 出去的路由：已不在栈里，但退出转场还要把它画完 ——
     * [AppNavHost] 靠它在动画期间继续渲染这一层。
     */
    var lastPopped: AppRoute? = null
        private set

    /** 栈顶下面那一层；不足两层时为 null（此时下层是主界面，不在路由里）。 */
    val underTop: AppRoute? get() = if (stack.size >= 2) stack[stack.size - 2] else null

    /**
     * 当前路由栈的**只读视图**（下标 0 = 最深的子页，末位 = 栈顶）。
     *
     * 供 [AppNavHost] 整栈常驻渲染。直接改这个 list 是不允许的 —— 走 [navigate] / [popBack]。
     */
    val routes: List<AppRoute> get() = stack

    /**
     * 入栈。只挡「已在栈顶」：A → B → A 合法，非栈顶同名路由照样入栈，故 [layerKeys] 的裸 id/`#2`/`#3` 去重仍需保留。
     */
    fun navigate(route: AppRoute) {
        if (nativeRouteHandler?.invoke(route) == true) return
        if (stack.lastOrNull() == route) return
        stack.add(route)
        lastDirection = NavDirection.Push
    }

    /**
     * 出栈。返回 false 表示栈已经空了 —— 此时返回键应交给 Android 宿主
     *（MainActivity 的预测性返回 /「退出即隐藏后台」）。
     */
    fun popBack(): Boolean {
        if (nativeBackHandler?.invoke() == true) return true
        if (stack.isEmpty()) return false
        lastPopped = stack.removeAt(stack.lastIndex)
        lastDirection = NavDirection.Pop
        return true
    }

    internal fun stackIds(): List<String> = stack.map { it.id }
}

/**
 * 创建并记住一个空的叠加路由器。
 *
 * ⚠ 栈**从空开始**：主界面不是路由，是常驻底座。
 */
@Composable
fun rememberAppRouter(): AppRouter {
    return rememberSaveable(saver = appRouterSaver) { AppRouter() }
}

/** 保存**当前栈**而不是初始值 —— 用初始值会在旋转后把用户弹回首页。 */
private val appRouterSaver: Saver<AppRouter, Any> = listSaver(
    save = { it.stackIds() },
    restore = { ids -> AppRouter().also { r -> ids.mapNotNull(AppRoute::fromId).forEach(r::navigate) } },
)
