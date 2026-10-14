package com.haooz.chedule.ui.utils

import android.content.Context
import com.haooz.chedule.data.DiagnosticRecorder

/**
 * [DiagnosticRecorder] 的 Android 实现 —— 包装全局单例 [CrashLogHelper]。
 *
 * `CrashLogHelper` 的方法带 `context`（原实现里多为占位/落盘路径），本包装层在构造时
 * 捕获宿主注入的 context，接口层保持无参（`:ui-shared` 的「关于」页只依赖跨平台接口）。
 * 宿主（AppRouteContent / TabletSettingsScreen）在调用点创建本实例并注入。
 */
class AndroidDiagnosticRecorder(private val context: Context) : DiagnosticRecorder {
    override val isRecording: Boolean get() = CrashLogHelper.isRecording
    override val hasReadyRecording: Boolean get() = CrashLogHelper.hasReadyRecording
    override val readyReason: String get() = CrashLogHelper.readyReason
    override val maxRecordMs: Long get() = CrashLogHelper.MAX_RECORD_MS
    override fun recordingElapsedMs(): Long = CrashLogHelper.recordingElapsedMs()
    override fun ensureRecovered() = CrashLogHelper.ensureRecovered(context)
    override fun ensureRecordingNotExpired() = CrashLogHelper.ensureRecordingNotExpired()
    override fun startRecording(): Boolean = CrashLogHelper.startRecording(context)
    override fun stopRecording(reason: String): Boolean = CrashLogHelper.stopRecording(context, reason)
    override fun shareReadyRecording(): Boolean = CrashLogHelper.shareReadyRecording(context)
}
