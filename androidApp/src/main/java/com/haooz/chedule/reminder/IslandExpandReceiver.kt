package com.haooz.chedule.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.haooz.chedule.data.NexioLog
/**
 * 超级岛展开态触发器：倒计时结束后发广播触发展开态弹出。
 */
class IslandExpandReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "IslandExpandReceiver"
        const val EXTRA_COURSE_NAME = "course_name"
        const val EXTRA_CLASSROOM = "classroom"
        const val EXTRA_SECTION = "section"
        const val EXTRA_START_TIME = "start_time"
        const val EXTRA_END_TIME = "end_time"
        // 自定义时间课没有节次，切换到「已上课 / 课中」时据此跳过 section 展示
        const val EXTRA_IS_CUSTOM_TIME = "is_custom_time"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_COURSE_START_MILLIS = "course_start_millis"
    }

    override fun onReceive(context: Context, intent: Intent) {
        NexioLog.d(TAG, "Received island expand broadcast")

        // 闹钟可能因 Doze 严重延迟，到达时岛已是另一门课倒计时。校验开始时间仍与当前岛状态一致，
        // 不一致即迟到旧闹钟直接丢弃。测试岛与真实岛独立 PREF，按 notificationId 选对应 state。
        val notificationId2 = intent.getIntExtra(EXTRA_NOTIFICATION_ID, IslandNotificationHelper.ISLAND_NOTIFICATION_ID)
        val expectedStart = intent.getLongExtra(EXTRA_COURSE_START_MILLIS, -1L)
        if (expectedStart > 0L) {
            val current = IslandNotificationHelper.IslandState.snapshotFor(context, notificationId2)
            if (current == null || current.startMillis != expectedStart) {
                NexioLog.d(TAG, "Stale expand alarm (expected=$expectedStart current=${current?.startMillis}), ignored")
                return
            }
        }
        if (IslandNotificationHelper.IslandState.isSwitched(
                context,
                testMode = IslandNotificationHelper.isIslandTestId(notificationId2)
            )
        ) {
            NexioLog.d(TAG, "Already switched, ignored")
            return
        }

        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: "课程"
        val classroom = intent.getStringExtra(EXTRA_CLASSROOM) ?: ""
        val section = intent.getStringExtra(EXTRA_SECTION) ?: ""
        val startTime = intent.getStringExtra(EXTRA_START_TIME) ?: ""
        val endTime = intent.getStringExtra(EXTRA_END_TIME) ?: ""
        val isCustomTime = intent.getBooleanExtra(EXTRA_IS_CUSTOM_TIME, false)
        // 传倒计时岛的 ID；onClassStart 内部按开关分流到「已上课」或「课中」独立 ID
        val notificationId = intent.getIntExtra(
            EXTRA_NOTIFICATION_ID,
            IslandNotificationHelper.ISLAND_NOTIFICATION_ID
        )

        IslandNotificationHelper.onClassStart(
            context = context,
            courseName = courseName,
            classroom = classroom,
            section = section,
            startTime = startTime,
            endTime = endTime,
            notificationId = notificationId,
            // 测试岛 ID 必须映射到 testMode，否则 snapshot 读真实课表 state，课中分流会错
            testMode = IslandNotificationHelper.isIslandTestId(notificationId),
            isCustomTime = isCustomTime
        )
    }
}
