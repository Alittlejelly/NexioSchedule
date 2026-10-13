package com.haooz.chedule.ui.utils

import com.haooz.chedule.data.IosCourseLiveActivity
import com.haooz.chedule.data.IosCourseNotifier
import com.haooz.chedule.data.ReminderController

/**
 * iOS [ReminderController] over local notifications and the Live Activity island.
 *
 * | Capability | Android | iOS |
 * |---|---|---|
 * | Pre-class / tomorrow | exact alarms | local notices, may fire late |
 * | Notification permission | runtime permission | system dialog on first schedule |
 * | In-class island | ongoing notice + Shizuku | Live Activity, tap or foreground only |
 * | Exact alarm / battery / DND / Shizuku / promoted | available | no system API |
 *
 * Every method without an iOS counterpart reports "not granted" so the shared page
 * falls through to its existing guidance branch. Returning true instead would show an
 * "authorized" state that does nothing, which is worse than hiding the entry.
 */
class IosReminderController : ReminderController {

    override fun startReminderService() = IosCourseNotifier.reschedule()

    override fun stopReminderService() = IosCourseNotifier.stop()

    /**
     * iOS's in-class reminder is the Live Activity switch, which lands on the same
     * `in_class_reminder_enabled` key as Android's. Behaviour differs because the
     * island can only start from a tap or while foregrounded.
     */
    override fun isInClassEnabled(): Boolean = IosCourseNotifier.isLiveActivityEnabled()

    override fun getInClassTimingMode(): Int = IosCourseNotifier.getInClassTimingMode()

    override fun getInClassLeadMinutes(): Int = IosCourseNotifier.getInClassLeadMinutes()

    /** No promoted-notification counterpart. */
    override fun canPostPromotedNotifications(): Boolean = false

    override fun hasNotificationPermission(): Boolean = IosCourseNotifier.isAuthorized()

    /** iOS forbids third-party DND control. */
    override fun isDndPermissionGranted(): Boolean = false

    /**
     * HyperOS island probe, kept false on purpose: the shared page hides
     * Shizuku-specific rows when this is false. The island container itself is
     * reported separately by [supportsLiveActivity].
     */
    override fun isIslandSupported(): Boolean = false

    override fun isShizukuRunning(): Boolean = false

    override fun isShizukuAuthorized(): Boolean = false

    override fun requestShizukuPermission(onResult: (Boolean) -> Unit) = onResult(false)

    /** iOS has no exact-alarm grant; notices are dispatched by the system. */
    override fun canScheduleExactAlarms(): Boolean = false

    override fun isIgnoringBatteryOptimizations(): Boolean = false

    override val supportsPromotedNotifications: Boolean get() = false

    /**
     * Requests notification permission. Already-granted calls back immediately,
     * otherwise the system dialog appears; a previously denied state never prompts
     * again and reports false.
     */
    override fun requestNotificationPermission(onResult: (granted: Boolean) -> Unit) {
        IosCourseNotifier.ensureAuthorization { granted -> onResult(granted) }
    }

    /**
     * DND access is an Android-only concept, so opening a settings page here would
     * only confuse. Calls back and lets the page show its own guidance.
     */
    override fun openNotificationAccessSettings(onResult: () -> Unit) = onResult()

    override fun openPromotedNotificationSettings(onResult: () -> Unit) = onResult()

    override fun openExactAlarmSettings(onResult: () -> Unit) = onResult()

    override fun openBatteryOptimizationSettings(onResult: () -> Unit) = onResult()

    override fun openAppDetailsSettings(onResult: () -> Unit) = onResult()

    /**
     * Whether this device can show a Live Activity. Always true at the 26.1 minimum;
     * hardware without an island falls back to lock-screen-only, decided by the system.
     */
    override fun supportsLiveActivity(): Boolean = true
}
