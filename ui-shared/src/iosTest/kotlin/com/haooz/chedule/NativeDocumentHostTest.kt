package com.haooz.chedule

import com.haooz.chedule.ui.navigation.AppRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import com.haooz.chedule.ui.utils.NativeDocumentAction
import com.haooz.chedule.ui.utils.NativeDocumentEditorChrome

class NativeDocumentHostTest {
    @Test
    fun legacyPageRemainsRetainedBehindTheNativeDocumentStack() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.ScheduleImport)
        host.router.navigate(AppRoute.Changelog)
        assertSame(AppRoute.ScheduleImport, host.router.current)
        assertEquals(1, host.router.depth)
        assertEquals(1, host.nativeRouteIds.size)
        assertEquals("更新日志", host.nativeRouteTitle(host.nativeRouteIds.single()))
    }

    @Test
    fun repeatedDocumentsHaveIndependentInstanceIdentities() {
        val host = IosAppHost()
        host.navigateNativeDocument("license")
        host.navigateNativeDocument("license")
        val ids = host.nativeRouteIds
        assertEquals(2, ids.size)
        assertNotEquals(ids[0], ids[1])
        assertEquals("开源协议", host.nativeRouteTitle(ids[1]))
    }

    @Test
    fun completedPopOnlyRemovesNativePagesAboveTheConfirmedDepth() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.ScheduleImport)
        host.navigateNativeDocument("changelog")
        host.navigateNativeDocument("privacy_policy")
        val first = host.nativeRouteIds.first()
        host.completeNativePop(1)
        assertEquals(listOf(first), host.nativeRouteIds)
        assertSame(AppRoute.ScheduleImport, host.router.current)
        host.completeNativePop(0)
        assertEquals(emptyList(), host.nativeRouteIds)
        assertSame(AppRoute.ScheduleImport, host.router.current)
    }

    @Test
    fun programmaticCompletionRetainsStateUntilUIKitConfirmsThePop() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.ScheduleImport)
        host.navigateNativeDocument("changelog")
        val id = host.nativeRouteIds.single()
        val page = host.nativePage(id)
        var value = "original"
        page.registry.registerProvider("form") { value }
        assertTrue(host.router.popBack())
        assertEquals(emptyList(), host.nativeRouteIds)
        assertSame(page, host.nativePage(id))
        assertSame(AppRoute.ScheduleImport, host.router.current)
        value = "saved after completion request"
        host.completeNativePop(0)
        host.navigateNativeDocument("changelog")
        val reopened = host.nativePage(host.nativeRouteIds.single())
        assertEquals(value, reopened.registry.consumeRestored("form"))
        assertNull(reopened.controller)
    }

    @Test
    fun undisplayedPushCanBeReconciledWithoutAUIKitTransition() {
        val host = IosAppHost()
        host.navigateNativeDocument("changelog")
        assertTrue(host.router.popBack())
        assertTrue(host.nativePopPending)
        assertEquals(emptyList(), host.nativeRouteIds)
        // synchronizeStack confirms the already-matching empty UIKit stack (no didShow).
        host.completeNativePop(0)
        assertFalse(host.nativePopPending)
        assertFalse(host.nativePageActive)
        host.navigateNativeDocument("license")
        assertEquals(1, host.nativeRouteIds.size)
    }

    @Test
    fun duplicateDocumentsHaveIndependentSaveableKeysAndRestoration() {
        val host = IosAppHost()
        host.navigateNativeDocument("license")
        host.navigateNativeDocument("license")
        val first = host.nativePage(host.nativeRouteIds[0])
        val second = host.nativePage(host.nativeRouteIds[1])
        assertNotEquals(first.stateKey, second.stateKey)
        first.registry.registerProvider("scroll") { listOf(3, 12) }
        second.registry.registerProvider("scroll") { listOf(7, 40) }
        host.completeNativePop(1)
        host.navigateNativeDocument("license")
        val restoredSecond = host.nativePage(host.nativeRouteIds[1])
        assertEquals(listOf(7, 40), restoredSecond.registry.consumeRestored("scroll"))
        assertSame(first, host.nativePage(host.nativeRouteIds[0]))
        host.completeNativePop(0)
        host.navigateNativeDocument("license")
        assertEquals(listOf(3, 12), host.nativePage(host.nativeRouteIds.single()).registry.consumeRestored("scroll"))
    }

    @Test
    fun inFlightAndCancelledTransitionsDoNotChangeNativeOrLegacyState() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.ScheduleImport)
        host.navigateNativeDocument("privacy_policy")
        val ids = host.nativeRouteIds
        host.setNativeTransitionInFlight(true)
        host.router.navigate(AppRoute.License)
        assertTrue(host.router.popBack())
        assertEquals(ids, host.nativeRouteIds)
        host.setNativeTransitionInFlight(false)
        assertEquals(ids, host.nativeRouteIds)
        assertSame(AppRoute.ScheduleImport, host.router.current)
        host.completeNativePop(-1)
        host.completeNativePop(100)
        assertEquals(ids, host.nativeRouteIds)
    }

    @Test
    fun nativeActionsCannotFireForCoveredPagesOrDuringTransitions() {
        val host = IosAppHost()
        host.navigateNativeDocument("changelog")
        val first = host.nativeRouteIds.single()
        var invocations = 0
        host.nativePage(first).chrome.action = NativeDocumentAction("play.fill", "Test") { invocations++ }
        host.performNativePageAction(first)
        assertEquals(1, invocations)
        host.setNativeTransitionInFlight(true)
        assertFalse(host.nativePageActionEnabled(first))
        host.performNativePageAction(first)
        host.setNativeTransitionInFlight(false)
        host.navigateNativeDocument("license")
        host.performNativePageAction(first)
        assertEquals(1, invocations)
    }

    @Test
    fun allEligibleDocumentsKeepTheirOriginalLegacyParent() {
        val documents = listOf(
            AppRoute.Changelog to "更新日志",
            AppRoute.License to "开源协议",
            AppRoute.PrivacyPolicy to "隐私政策",
            AppRoute.Communication to "交流与反馈",
            AppRoute.AppreciateAuthor to "捐赠支持",
            AppRoute.PreferenceSettings to "应用偏好设置",
            AppRoute.UpdateSettings to "更新设置",
            AppRoute.LocalBackup to "本地备份",
            AppRoute.AiImport to "AI 文本导入",
            AppRoute.WidgetIntro to "桌面小部件",
            AppRoute.CourseReminder to "课程提醒",
            AppRoute.HolidaySettings to "节假日与调休",
            AppRoute.WebDavSettings to "WebDAV 云备份",
            AppRoute.ScheduleExport to "课表导出",
            AppRoute.ScheduleBackup to "课表备份",
            AppRoute.About to "关于应用",
            AppRoute.CourseTimeSettings to "课表节数与时间",
        )
        for ((route, title) in documents) {
            val host = IosAppHost()
            host.router.navigate(AppRoute.ScheduleImport)
            host.router.navigate(route)
            assertSame(AppRoute.ScheduleImport, host.router.current)
            assertEquals(1, host.router.depth)
            assertEquals(title, host.nativeRouteTitle(host.nativeRouteIds.single()))
            host.completeNativePop(0)
            assertTrue(host.router.popBack())
            assertFalse(host.router.hasOverlay)
        }
    }

    @Test
    fun backupChildPagesShareOnlyTheNativeStack() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.ScheduleImport)
        host.router.navigate(AppRoute.ScheduleBackup)
        val backup = host.nativeRouteIds.single()
        host.router.navigate(AppRoute.WebDavSettings)
        assertEquals(2, host.nativeRouteIds.size)
        assertTrue(host.router.popBack())
        assertEquals(listOf(backup), host.nativeRouteIds)
        host.completeNativePop(1)
        host.router.navigate(AppRoute.LocalBackup)
        assertEquals(2, host.nativeRouteIds.size)
        assertSame(AppRoute.ScheduleImport, host.router.current)
        assertEquals(1, host.router.depth)
    }

    @Test
    fun importAndComplexMorphRoutesRemainComposeOwned() {
        for (route in listOf(
            AppRoute.ScheduleImport, AppRoute.EducationalImport, AppRoute.CourseManage,
        )) {
            val host = IosAppHost()
            assertFalse(route.isNativeDocument())
            host.router.navigate(route)
            assertEquals(emptyList(), host.nativeRouteIds)
            assertSame(route, host.router.current)
        }
    }

    @Test
    fun nativeActionStateAndLatestCallbackKeepTheirOriginalSemantics() {
        val host = IosAppHost()
        host.navigateNativeDocument("changelog")
        val id = host.nativeRouteIds.single()
        val chrome = host.nativePage(id).chrome
        var result = ""
        chrome.action = NativeDocumentAction("arrow.clockwise", "更新", enabled = false, busy = true) { result = "busy" }
        assertTrue(host.nativePageActionBusy(id))
        assertFalse(host.nativePageActionEnabled(id))
        host.performNativePageAction(id)
        assertEquals("", result)
        chrome.action = NativeDocumentAction("checkmark", "已连接", successful = true) { result = "latest callback" }
        assertTrue(host.nativePageActionSuccessful(id))
        assertEquals("checkmark", host.nativePageActionSymbol(id))
        assertEquals("已连接", host.nativePageActionLabel(id))
        host.performNativePageAction(id)
        assertEquals("latest callback", result)
    }

    @Test
    fun aboutChildrenAndReturnStayWithinTheNativeStack() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.About)
        val about = host.nativeRouteIds.single()
        host.router.navigate(AppRoute.Changelog)
        assertEquals(2, host.nativeRouteIds.size)
        assertFalse(host.router.hasOverlay)
        assertTrue(host.router.popBack())
        host.completeNativePop(1)
        assertEquals(listOf(about), host.nativeRouteIds)
        assertTrue(host.router.popBack())
        host.completeNativePop(0)
        assertFalse(host.nativePageActive)
        assertFalse(host.router.hasOverlay)
    }

    @Test
    fun timeEditorOwnsChromeAndBackUntilItsMorphExitCompletes() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.CourseTimeSettings)
        val id = host.nativeRouteIds.single()
        val chrome = host.nativePage(id).chrome
        var clicks = 0
        chrome.action = NativeDocumentAction("play.fill", "Test") { clicks++ }
        chrome.contentOverlayActive = true
        assertTrue(host.nativeContentOverlayActive)
        assertFalse(host.nativePageActionEnabled(id))
        host.performNativePageAction(id)
        assertEquals(0, clicks)
        assertTrue(host.router.popBack())
        assertFalse(host.nativePopPending)
        host.router.navigate(AppRoute.License)
        assertEquals(listOf(id), host.nativeRouteIds)
        chrome.contentOverlayActive = false
        assertFalse(host.nativeContentOverlayActive)
        assertTrue(host.nativePageActionEnabled(id))
        assertTrue(host.router.popBack())
        assertTrue(host.nativePopPending)
        host.completeNativePop(0)
        assertFalse(host.nativePageActive)
    }

    @Test
    fun nativeEditorOverridesRetainedParentAndRestoresItsLatestChrome() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.CourseTimeSettings)
        val id = host.nativeRouteIds.single()
        val chrome = host.nativePage(id).chrome
        var cancelled = 0
        var saved = 0
        var parentClicks = 0
        chrome.title = "课表节数与时间"
        chrome.contentOverlayActive = true
        chrome.editor = NativeDocumentEditorChrome(
            "编辑作息",
            NativeDocumentAction("xmark", "取消") { cancelled++ },
            NativeDocumentAction("checkmark", "保存并关闭") { saved++ },
        )
        // The live parent can publish again without taking ownership from the editor.
        chrome.action = NativeDocumentAction("arrow.clockwise", "Parent") { parentClicks++ }
        assertEquals("编辑作息", host.nativeRouteTitle(id))
        assertEquals("xmark", host.nativePageLeadingSymbol(id))
        assertEquals("checkmark", host.nativePageActionSymbol(id))
        assertTrue(host.nativePageActionEnabled(id))
        host.performNativePageAction(id)
        host.performNativePageLeadingAction(id)
        assertEquals(1, saved)
        assertEquals(1, cancelled)
        assertEquals(0, parentClicks)
        assertTrue(host.router.popBack())
        assertFalse(host.nativePopPending)
        host.router.navigate(AppRoute.License)
        assertEquals(listOf(id), host.nativeRouteIds)
        // The editor owns back even if the parent flag has already been cleared.
        chrome.contentOverlayActive = false
        assertTrue(host.nativeContentOverlayActive)
        chrome.editor = null
        assertEquals("课表节数与时间", host.nativeRouteTitle(id))
        assertEquals("chevron.backward", host.nativePageLeadingSymbol(id))
        assertEquals("arrow.clockwise", host.nativePageActionSymbol(id))
        host.performNativePageLeadingAction(id)
        assertTrue(host.nativePopPending)
    }

    @Test
    fun nativeEditorControlsAreGuardedDuringClosingAndOuterTransitions() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.CourseTimeSettings)
        val id = host.nativeRouteIds.single()
        val chrome = host.nativePage(id).chrome
        var clicks = 0
        val cancel = NativeDocumentAction("xmark", "取消") { clicks++ }
        val save = NativeDocumentAction("checkmark", "保存并关闭") { clicks++ }
        chrome.editor = NativeDocumentEditorChrome("编辑作息", cancel, save)
        host.setNativeTransitionInFlight(true)
        assertFalse(host.nativePageLeadingEnabled(id))
        assertFalse(host.nativePageActionEnabled(id))
        host.performNativePageLeadingAction(id)
        host.performNativePageAction(id)
        host.setNativeTransitionInFlight(false)
        chrome.editor = NativeDocumentEditorChrome(
            "编辑作息", cancel.copy(enabled = false), save.copy(enabled = false),
        )
        host.performNativePageLeadingAction(id)
        host.performNativePageAction(id)
        assertEquals(0, clicks)
        assertFalse(host.nativePopPending)
    }

    @Test
    fun parentBackAcceptsClosingMorphTapAndPopsOnlyAfterSaveAndExit() {
        val host = IosAppHost()
        host.router.navigate(AppRoute.CourseTimeSettings)
        val id = host.nativeRouteIds.single()
        val chrome = host.nativePage(id).chrome
        chrome.contentOverlayActive = true
        chrome.editor = NativeDocumentEditorChrome(
            "编辑作息",
            NativeDocumentAction("xmark", "取消") {},
            NativeDocumentAction("checkmark", "保存并关闭") {},
        )
        // Entering chrome is usable from the first frame, without progress thresholds.
        assertTrue(host.nativePageActionEnabled(id))
        assertTrue(host.nativePageLeadingEnabled(id))
        // Closing intent restores the parent's back item immediately.
        chrome.editor = null
        assertEquals("chevron.backward", host.nativePageLeadingSymbol(id))
        assertTrue(host.nativePageLeadingEnabled(id))
        host.performNativePageLeadingAction(id)
        host.performNativePageLeadingAction(id)
        host.reconcileNativeEditorExit()
        assertFalse(host.nativePopPending)
        assertEquals(listOf(id), host.nativeRouteIds)
        // The original save callback runs before the content overlay is disposed.
        chrome.contentOverlayActive = false
        host.reconcileNativeEditorExit()
        assertTrue(host.nativePopPending)
        assertEquals(emptyList(), host.nativeRouteIds)
        host.completeNativePop(0)
        host.reconcileNativeEditorExit()
        assertFalse(host.nativePopPending)
    }

    @Test
    fun unknownOrUnmigratedRoutesDoNotEnterTheNativeStack() {
        val host = IosAppHost()
        host.navigateNativeDocument("missing")
        host.navigateNativeDocument("course_manage")
        assertEquals(emptyList(), host.nativeRouteIds)
        host.router.navigate(AppRoute.CourseManage)
        assertSame(AppRoute.CourseManage, host.router.current)
    }

    @Test
    fun mainHeaderActionDispatchInvokesCallbacks() {
        val host = IosAppHost()
        assertFalse(host.showsNativeHeader)
        var switched = false
        var jumpedWeek = false
        var courseManaged = false
        var customized = false
        var jumpedDate = false

        host.performMainAction("switchSchedule")
        assertFalse(switched)

        // Inject callbacks via HostNavigationState
        val navigation = host.navigation
        navigation.showsNativeHeader = true
        navigation.onOpenSwitchSchedule = { switched = true }
        navigation.onJumpWeek = { jumpedWeek = true }
        navigation.onCourseManage = { courseManaged = true }
        navigation.onEnterCustomize = { customized = true }
        navigation.onJumpToDate = { jumpedDate = true }

        assertTrue(host.showsNativeHeader)
        host.performMainAction("switchSchedule")
        assertTrue(switched)
        host.performMainAction("jumpWeek")
        assertTrue(jumpedWeek)
        host.performMainAction("courseManage")
        assertTrue(courseManaged)
        host.performMainAction("customize")
        assertTrue(customized)
        host.performMainAction("jumpToDate")
        assertTrue(jumpedDate)
    }
}
