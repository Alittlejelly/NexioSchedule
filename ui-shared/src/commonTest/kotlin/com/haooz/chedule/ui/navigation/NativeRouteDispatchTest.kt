package com.haooz.chedule.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
