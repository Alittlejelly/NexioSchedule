package com.haooz.chedule.platform

import android.app.Application
import com.haooz.chedule.data.ScheduleChangeSink
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.wearable.WearableScheduleSync
import com.haooz.chedule.widget.WidgetUpdateCache

/**
 * [ScheduleChangeSink] 的 Android 实现（`NexioApplication.onCreate` 注入）。
 * 平台机制边界：AlarmManager / 小米穿戴 SDK / AppWidgetManager 均 Android 专有，iOS 各自实现。
 * ⚠ 接口只说「重排/同步/刷新」，具体机制留此层，勿把 Context 之外的东西带上，免 iOS 为假抽象从零实现。
 */
class AndroidScheduleEffects(
    private val app: Application,
) : ScheduleChangeSink {

    override fun rescheduleReminders() {
        // 内部用 `CourseRepository()`（无参构造即单例），与 VM 中同一实例。
        CourseReminderHelper.startReminderService(app)
    }

    override fun syncWearable(reason: String) {
        WearableScheduleSync.onScheduleChanged(reason)
    }

    override fun refreshWidgets() {
        WidgetUpdateCache.updateInstalledWidgets(app)
    }
}
