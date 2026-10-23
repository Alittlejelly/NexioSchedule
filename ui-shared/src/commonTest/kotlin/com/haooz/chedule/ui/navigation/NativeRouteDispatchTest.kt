package com.haooz.chedule.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NativeRouteDispatchTest {
    @Test
    fun unhostedRoutesKeepTheirExistingStackSemantics() {
        val router = AppRouter()
        router.navigate(AppRoute.About)
        router.navigate(AppRoute.Changelog)
        assertEquals(2, router.depth)
        assertTrue(router.popBack())
        assertSame(AppRoute.About, router.current)
        assertSame(AppRoute.Changelog, router.lastPopped)
    }

    @Test
    fun consumedNativeRouteDoesNotReplaceOrAnimateTheRetainedLegacyPage() {
        val router = AppRouter()
        router.navigate(AppRoute.About)
        var received: AppRoute? = null
        router.nativeRouteHandler = {
            received = it
            it == AppRoute.Changelog
        }
        router.navigate(AppRoute.Changelog)
        assertSame(AppRoute.Changelog, received)
        assertEquals(listOf(AppRoute.About), router.routes.toList())
        assertSame(AppRoute.About, router.current)
        assertEquals(NavDirection.Push, router.lastDirection)
    }

    @Test
    fun declinedNativeRouteStillUsesComposeNavigation() {
        val router = AppRouter()
        router.nativeRouteHandler = { it == AppRoute.Changelog }
        router.navigate(AppRoute.CourseManage)
        assertEquals(1, router.depth)
        assertSame(AppRoute.CourseManage, router.current)
        assertTrue(router.popBack())
        assertFalse(router.hasOverlay)
    }

    @Test
    fun nativeDispatchDoesNotOverwritePendingLegacyPopMetadata() {
        val router = AppRouter()
        router.navigate(AppRoute.About)
        router.navigate(AppRoute.CourseManage)
        router.popBack()
        router.nativeRouteHandler = { true }
        router.navigate(AppRoute.License)
        assertEquals(NavDirection.Pop, router.lastDirection)
        assertSame(AppRoute.CourseManage, router.lastPopped)
        assertSame(AppRoute.About, router.current)
    }

    @Test
    fun nativeCompletionDoesNotPopTheRetainedLegacyParent() {
        val router = AppRouter()
        router.navigate(AppRoute.About)
        var nativeBacks = 0
        router.nativeBackHandler = { nativeBacks++; true }
        assertTrue(router.popBack())
        assertEquals(1, nativeBacks)
        assertSame(AppRoute.About, router.current)
        assertEquals(NavDirection.Push, router.lastDirection)
    }

    @Test
    fun declinedNativeBackFallsThroughToTheOriginalStack() {
        val router = AppRouter()
        router.nativeBackHandler = { false }
        router.navigate(AppRoute.CourseManage)
        assertTrue(router.popBack())
        assertSame(AppRoute.CourseManage, router.lastPopped)
        assertFalse(router.popBack())
    }

    @Test
    fun sharedMotionCurvesHaveStableEndpointsAndMonotonicProgress() {
        assertEquals(600, NAV_ENTER_DURATION)
        assertEquals(320, NAV_EXIT_DURATION)
        for (easing in listOf(NAV_ENTER_EASING, NAV_EXIT_EASING)) {
            assertEquals(0f, easing.transform(0f), 0.0001f)
            assertEquals(1f, easing.transform(1f), 0.0001f)
            var previous = 0f
            for (step in 1..120) {
                val progress = easing.transform(step / 120f)
                assertTrue(progress >= previous)
                assertTrue(progress in 0f..1f)
                previous = progress
            }
        }
    }

    /**
     * 连点同一入口只压一层：压出多层同名页得按好几次返回才回得去（用户实测）；
     * 转场 600ms 内点击照样收得到。
     */
    @Test
    fun repeatedNavigateOfTheTopRouteDoesNotStackDuplicates() {
        val router = AppRouter()
        router.navigate(AppRoute.About)
        router.navigate(AppRoute.About)
        router.navigate(AppRoute.About)
        assertEquals(1, router.depth)
        assertSame(AppRoute.About, router.current)
    }

    /** 只挡「已在栈顶」：A → B → A 仍是合法路径，非栈顶的同名路由照样能入栈。 */
    @Test
    fun sameRouteCanStillBePushedAgainOnceItLeftTheTop() {
        val router = AppRouter()
        router.navigate(AppRoute.About)
        router.navigate(AppRoute.Changelog)
        router.navigate(AppRoute.About)
        assertEquals(3, router.depth)
        assertSame(AppRoute.About, router.current)
    }

    /** 出栈后还能再进同一页（退场动画期间连点入口不该被吞）。 */
    @Test
    fun sameRouteCanBeReenteredRightAfterPop() {
        val router = AppRouter()
        router.navigate(AppRoute.About)
        router.popBack()
        router.navigate(AppRoute.About)
        assertEquals(1, router.depth)
        assertSame(AppRoute.About, router.current)
    }

    /**
     * 栈内层用裸 id（重开同一页才恢复滚动位置），重复路由顺延 `#2`/`#3`：同一帧两次
     * `SaveableStateProvider(同 key)` 会让 `SaveableStateHolderImpl` 抛异常崩屏。
     */
    @Test
    fun inStackLayersKeepBareRouteIdAndDeduplicateRepeats() {
        val (keys, exitKey) = layerKeys(
            listOf(AppRoute.About, AppRoute.About, AppRoute.CourseManage),
            exiting = null,
        )
        assertEquals(listOf(AppRoute.About.id, "${AppRoute.About.id}#2", AppRoute.CourseManage.id), keys)
        assertNull(exitKey)
    }

    /**
     * 退场层必须**沿用它在栈里时的 key**：换 key 会让 `ReusableContent(key)` 把这一层整体
     * 重建（滚动位置在退场动画中当场归零），且 `removeState` 删新 key、旧 key 那份状态永远
     * 留着 —— 下次再进这一页还是旧滚动位置。
     */
    @Test
    fun exitingLayerKeepsTheKeyItHadInTheStack() {
        val stackBeforePop = listOf(AppRoute.About, AppRoute.CourseManage)
        val keyInStack = layerKeys(stackBeforePop, null).first.last()

        val exiting = ExitingLayer(AppRoute.CourseManage, poppedLayerKey(stackBeforePop))
        assertEquals(keyInStack, exiting.key)

        // 出栈后这一帧：栈内 key 是同一套分配的前 N 个，退场层是第 N+1 个，仍不撞
        val (keys, exitKey) = layerKeys(listOf(AppRoute.About), exiting)
        assertEquals(listOf(AppRoute.About.id), keys)
        assertEquals(keyInStack, exitKey)
        assertTrue(keys.none { it == exitKey }, "退场层与栈内层撞了 key：$keys vs $exitKey")
    }

    /** 同名路由重复入栈时，出栈那一层用的是 `#2`，出栈后仍不与栈内的裸 id 撞。 */
    @Test
    fun exitingLayerKeySurvivesDuplicateRoutesInStack() {
        val stackBeforePop = listOf(AppRoute.About, AppRoute.About)
        val exiting = ExitingLayer(AppRoute.About, poppedLayerKey(stackBeforePop))
        assertEquals("${AppRoute.About.id}#2", exiting.key)

        val (keys, exitKey) = layerKeys(listOf(AppRoute.About), exiting)
        assertEquals(listOf(AppRoute.About.id), keys)
        assertEquals(exiting.key, exitKey)
        assertTrue(keys.none { it == exitKey }, "退场层与栈内层撞了 key：$keys vs $exitKey")
    }
}
