package com.haooz.chedule.data

import kotlin.concurrent.Volatile

/**
 * 课程 / 课表数据变更后的**平台副作用**：重排提醒、同步手表、刷新小组件。
 *
 * ## 为什么抽这一层
 *
 * `CourseViewModel` / `ScheduleViewModel` 原本为了拿 `Context` 去直接调
 * `CourseReminderHelper` / `WidgetUpdateCache` / `WearableScheduleSync`，
 * 被迫继承 `AndroidViewModel(application)` —— 于是引用这两个 VM 的 **19 个页面**
 * 全部卡在 `:androidApp` 搬不进 `:ui-shared`。
 *
 * 这三个动作就是它们需要的**全部**平台能力，接口因此很窄。
 *
 * ## ⚠ 设计红线：窄接口，不是「把平台模块搬个家」
 *
 * iOS 没有 `AlarmManager` / `AppWidgetManager`，手表也不是小米穿戴。
 * 所以这里**只暴露「发生了什么」**（课程变了、要重排、要刷新），
 * **不暴露「用什么机制做」**—— 具体怎么排提醒、怎么刷小组件，各平台自己实现。
 * 一旦把 `startReminderService(context, repository)` 这类签名直接搬进接口，
 * iOS 侧就得为一个假的抽象从零实现一遍，还不如不抽。
 *
 * ## 未注入时的行为
 *
 * 与 `PlatformToast` / `FeatureLog` 一致：**未注入时静默丢弃、不崩**。
 * 桌面端没有小组件和提醒，不注入即可；Android 由 `NexioApplication.onCreate` 注入。
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
 * （默认工厂只认无参构造），构造器注入要为每个调用点写自定义 Factory，成本远高于收益。
 * 与 `AppStorage.init` / `FeatureLog.init` / `PlatformToast` 同一套路。
 */
object ScheduleEffects {
    // 必须用 kotlin.concurrent.Volatile：不限定的 @Volatile 靠 JVM 专有的默认导入
    // kotlin.jvm.* 解析，Kotlin/Native 上直接「Unresolved reference」（见 AppStorage）。
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
