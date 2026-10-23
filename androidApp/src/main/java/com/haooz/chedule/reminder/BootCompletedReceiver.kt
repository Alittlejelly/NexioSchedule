/** 开机完成 / 应用更新 / 时区变化接收器 - 重新注册课程提醒闹钟 */
package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 需重调度的场景：BOOT_COMPLETED 重启丢全部闹钟；MY_PACKAGE_REPLACED 更新丢全部闹钟；
        // TIME_CHANGED/TIMEZONE_CHANGED 绝对时间闹钟失真。
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // 重启/升级会重置系统勿扰与铃声状态，先丢过期接管标记，否则本节课剩余时间不再进勿扰。
                ClassDndHelper.dropStaleSessionAfterReboot(context)
                // 任何上述情况都重置全部闹钟（含 widget refresh）；startReminderService 内挂上 widget 刷新链，
                // 覆盖「用小组件但不开提醒」的用户。
                CourseReminderHelper.startReminderService(context)
            }
        }
    }
}
