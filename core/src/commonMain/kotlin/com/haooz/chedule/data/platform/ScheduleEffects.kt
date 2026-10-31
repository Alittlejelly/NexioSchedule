package com.haooz.chedule.data.platform

import kotlin.concurrent.Volatile

/**
 * 课程 / 课表数据变更后的**平台副作用**：重排提醒、同步手表、刷新小组件。
 *
 * ⚠ 设计红线：只暴露「发生了什么」，**不暴露「用什么机制做」** ——
 * iOS 没有 `AlarmManager` / `AppWidgetManager`，手表也不是小米穿戴；一旦把
 * `startReminderService(context, repository)` 这类签名搬进接口，iOS 就得为一个
 * 假抽象从零实现一遍。
 *
 * 抽这层的原因：这三个动作就是 `CourseViewModel` / `ScheduleViewModel` 需要的**全部**
 * 平台能力。原先为了拿 `Context` 被迫继承 `AndroidViewModel`，把引用它们的 19 个页面
 * 全卡在了 `:androidApp`。
 *
 * 未注入时静默丢弃、不崩（同 `PlatformToast` / `FeatureLog`）。
 */
interface ScheduleChangeSink {
    /** 课程或课表数据已变更，需要重新调度提醒。 */
    fun rescheduleReminders()

    /** 通知手表同步。`reason` 仅用于日志归因（如 `"course-mutate"`）。 */
    fun syncWearable(reason: String)

    /** 刷新已添加到桌面的小组件。 */
    fun refreshWidgets()
}

/**
 * 全局副作用出口。
 *
 * 用**全局注入**而不是构造器参数：调用方普遍用 AndroidX 的 `viewModel()` 拿实例
 *（默认工厂只认无参构造），构造器注入要为每个调用点写自定义 Factory。
 */
object ScheduleEffects {
    // 必须用 kotlin.concurrent.Volatile：不限定的 @Volatile 在 Kotlin/Native 上 Unresolved
    @Volatile
    private var sink: ScheduleChangeSink? = null

    fun init(sink: ScheduleChangeSink) {
        this.sink = sink
    }

    fun rescheduleReminders() {
        sink?.rescheduleReminders()
    }

    fun syncWearable(reason: String) {
        sink?.syncWearable(reason)
    }

    fun refreshWidgets() {
        sink?.refreshWidgets()
    }
}
