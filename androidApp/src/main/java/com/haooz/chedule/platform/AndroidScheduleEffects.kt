package com.haooz.chedule.platform

import android.app.Application
import com.haooz.chedule.data.ScheduleChangeSink
import com.haooz.chedule.reminder.CourseReminderHelper
import com.haooz.chedule.wearable.WearableScheduleSync
import com.haooz.chedule.widget.WidgetUpdateCache

/**
 * [ScheduleChangeSink] 的 Android 实现 —— `NexioApplication.onCreate` 注入。
 *
 * 这一层是「平台机制」的边界：下面三个东西都是 Android 专有
 * （`AlarmManager` 提醒服务 / 小米穿戴 SDK / `AppWidgetManager`），
 * iOS 侧要分别用 `UNUserNotificationCenter` / 无 / WidgetKit 各自实现。
 *
 * ⚠ 别把 Context 之外的东西往上带：接口只说「要重排 / 要同步 / 要刷新」，
 *   具体机制留在这一层，iOS 才不会为了一个假抽象从零实现一遍。
 */
class AndroidScheduleEffects(
    private val app: Application,
) : ScheduleChangeSink {

    override fun rescheduleReminders() {
        // 单参重载内部用 `CourseRepository()`（无参构造即单例），与 VM 里那个是同一个实例。
        CourseReminderHelper.startReminderService(app)
    }

    override fun syncWearable(reason: String) {
        WearableScheduleSync.onScheduleChanged(reason)
    }

    override fun refreshWidgets() {
        WidgetUpdateCache.updateInstalledWidgets(app)
    }
}
