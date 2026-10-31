package com.haooz.chedule.data.platform

/**
 * 诊断日志录制控制器 —— 平台能力抽象（注入模式，同 `FeatureLog`/`PlatformToast`）。真实实现是 Android
 * 侧的功能轨迹记录 + 崩溃/被杀自动结束 + 系统分享，桌面/iOS 用 [NoopDiagnosticRecorder]。
 */
interface DiagnosticRecorder {
    /** 是否正在录制 */
    val isRecording: Boolean
    /** 是否有可分享的成品录制 */
    val hasReadyRecording: Boolean
    /** 成品来源："" / "crash" / "process_killed" / "timeout_30min" / "manual" 等 */
    val readyReason: String
    /** 单次录制时长上限（ms） */
    val maxRecordMs: Long
    /** 已录制时长（ms，封顶 maxRecordMs） */
    fun recordingElapsedMs(): Long
    /** 启动时幂等恢复上次被中断的录制（读 hasReadyRecording 前调用一次） */
    fun ensureRecovered()
    /** 录制超时检查（录制循环每 tick 调用） */
    fun ensureRecordingNotExpired()
    /** 开始录制 */
    fun startRecording(): Boolean
    /** 结束录制；结束后 hasReadyRecording=true */
    fun stopRecording(reason: String): Boolean
    /** 分享成品录制（系统分享面板） */
    fun shareReadyRecording(): Boolean
}

/** 桌面/iOS 的空实现：诊断录制是 Android 专有能力，非 Android 平台恒为「无录制能力」。 */
object NoopDiagnosticRecorder : DiagnosticRecorder {
    override val isRecording: Boolean get() = false
    override val hasReadyRecording: Boolean get() = false
    override val readyReason: String get() = ""
    override val maxRecordMs: Long get() = 0L
    override fun recordingElapsedMs(): Long = 0L
    override fun ensureRecovered() {}
    override fun ensureRecordingNotExpired() {}
    override fun startRecording(): Boolean = false
    override fun stopRecording(reason: String): Boolean = false
    override fun shareReadyRecording(): Boolean = false
}
