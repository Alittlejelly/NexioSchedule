package com.haooz.chedule.ui.utils

/**
 * 各功能统一埋点入口：稳定短名，便于崩溃轨迹对账。
 *
 * 2026-10-10 从 `:androidApp` 的 `ui/utils/CrashLogHelper.kt` 抽到 `:core` —— 原先它只是
 * `CrashLogHelper` 的语法糖，却让 `viewmodel/` 等**平台中立**的代码反向依赖整个
 * Android 专有的崩溃记录器（`synchronized` + `AtomicLong` + 环形缓冲 + 写文件）。
 *
 * ## 接线方式（与 AppStorage / AppFiles / PlatformToast 同一套路）
 *
 * `:core` 只认这个入口，真正的轨迹记录由宿主注入：
 * ```
 * FeatureLog.init(
 *     trace = { f, e, d -> CrashLogHelper.trace(f, e, d) },
 *     recording = { f, e, d -> CrashLogHelper.traceRecording(f, e, d) },
 *     isRecording = { CrashLogHelper.isRecording },
 * )
 * ```
 * 见 `NexioApplication.onCreate`。**未注入时静默丢弃**（埋点丢失不该反过来搞崩业务）。
 *
 * ⚠ 包名刻意保持 `com.haooz.chedule.ui.utils` 不变 —— 21 处调用方的 import 一行不用改。
 */
object FeatureLog {

    private var traceBackend: ((feature: String, event: String, detail: String) -> Unit)? = null
    private var recordingBackend: ((feature: String, event: String, detail: String) -> Unit)? = null
    private var recordingActive: (() -> Boolean)? = null

    /** 由宿主注入轨迹记录实现。重复调用会覆盖（便于测试）。 */
    fun init(
        trace: (feature: String, event: String, detail: String) -> Unit,
        recording: (feature: String, event: String, detail: String) -> Unit,
        isRecording: () -> Boolean,
    ) {
        traceBackend = trace
        recordingBackend = recording
        recordingActive = isRecording
    }

    fun t(feature: String, event: String, detail: String = "") {
        traceBackend?.invoke(feature, event, detail)
    }

    fun switchSchedule(event: String, detail: String = "") =
        t("切换课表", event, detail)

    fun main(event: String, detail: String = "") =
        t("主页", event, detail)

    fun settings(page: String) =
        t("设置", "open", page)

    fun today(event: String, detail: String = "") =
        t("今日页", event, detail)

    fun schedule(event: String, detail: String = "") =
        t("课程表", event, detail)

    fun course(event: String, detail: String = "") =
        t("课程", event, detail)

    fun appearance(event: String, detail: String = "") =
        t("课表外观", event, detail)

    fun shift(event: String, detail: String = "") =
        t("排班课表", event, detail)

    fun import(event: String, detail: String = "") =
        t("课表导入", event, detail)

    fun backup(event: String, detail: String = "") =
        t("备份迁移", event, detail)

    fun reminder(event: String, detail: String = "") =
        t("课程提醒", event, detail)

    /** 课程提醒页详细流程：仅「开始录制」后写入 */
    fun reminderFlow(event: String, detail: String = "") {
        recordingBackend?.invoke("课程提醒", event, detail)
    }

    /**
     * lambda 版：未录制时 detail **不求值**（与迁移前的 `inline` + `if (!isRecording) return` 同义）。
     *
     * ⚠ 这里刻意**不用 `inline`**。原因：本模块 android 变体的 JVM target 跟随 JDK（21），
     * 而 `:androidApp` 是 17，跨模块 inline 会直接编译失败：
     *   "Cannot inline bytecode built with JVM target 21 into bytecode that is being built with JVM target 17"
     * 而 KMP 的 android 目标在当前 AGP 版本下**不暴露 `compileOptions`**（试过 `android { compileOptions }`
     * 与 `kotlin { compilerOptions { jvmTarget } }` 都不被识别），无法从本模块对齐 target。
     *
     * 代价仅是「lambda 对象会被构造一次」，**lambda 体仍然不执行** —— 原优化（未录制时不读 SP、
     * 不抓栈）完整保留，因为判断发生在调用之前。
     */
    fun reminderFlow(event: String, detail: () -> String) {
        if (recordingActive?.invoke() != true) return
        recordingBackend?.invoke("课程提醒", event, detail())
    }

    fun holiday(event: String, detail: String = "") =
        t("节假日调休", event, detail)

    fun widget(event: String, detail: String = "") =
        t("桌面小部件", event, detail)

    fun preference(event: String, detail: String = "") =
        t("应用偏好", event, detail)

    fun update(event: String, detail: String = "") =
        t("更新设置", event, detail)

    fun about(event: String, detail: String = "") =
        t("关于应用", event, detail)

    fun timeConfig(event: String, detail: String = "") =
        t("课表节数时间", event, detail)

    fun webdav(event: String, detail: String = "") =
        t("WebDAV", event, detail)

    fun shareSchedule(event: String, detail: String = "") =
        t("口令分享", event, detail)
}
