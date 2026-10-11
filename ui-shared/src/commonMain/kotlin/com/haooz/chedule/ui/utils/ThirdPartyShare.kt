package com.haooz.chedule.ui.utils

import com.haooz.chedule.data.Course
import com.haooz.chedule.data.randomUuidV4

// ════════════════════════════════════════════════════════════════════════
//  第三方 App 课表导入的数据契约（WakeUp / 星链）
//
//  2026-10-11 自 `:androidApp` 的 `ScheduleImport.kt` 拆出并迁入 `:ui-shared`
//  （第二十三批）。拆出的原因：**页面要引用这些类型**（口令来源枚举驱动弹窗标题、
//  payload 驱动确认框文案），而 `ScheduleImport.kt` 里的抓取器本体用了
//  WebView / OkHttp / Gson，是纯 Android 实现 —— 类型跟实现挤在一个文件里，
//  页面就永远进不了 commonMain。
//
//  抓取器（[ThirdPartyShareImporter]）**仍在 `:androidApp`**，由宿主注入给页面
//  （见 `BackupAndMigrationScreen` 的 `onImportThirdParty`）。
// ════════════════════════════════════════════════════════════════════════

/** WakeUp / 星链课表分享口令导入：原生请求 + 无界面脚本执行 */
enum class ThirdPartyShareSource(val displayName: String, val inputLabel: String) {
    WakeUp("WakeUp课程表口令导入", "分享口令"),
    StarLink("星链课表分享码导入", "分享码"),
}

/** 第三方口令解析结果：课程 + 可选时间段/学期配置 */
data class ThirdPartySharePayload(
    val source: ThirdPartyShareSource,
    val courses: List<ThirdPartyCourse>,
    val timeSlots: List<ThirdPartyTimeSlot> = emptyList(),
    val semesterStartDate: String? = null,
    val semesterTotalWeeks: Int? = null,
) {
    val courseCount: Int get() = courses.size
}

data class ThirdPartyCourse(
    val name: String,
    val teacher: String = "",
    val position: String = "",
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int> = emptyList(),
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
) {
    fun toCourse(scheduleId: String, color: Long): Course {
        val sortedWeeks = weeks.sorted().ifEmpty { listOf(1) }
        return Course(
            id = randomUuidV4(),
            name = name,
            classroom = position,
            teacher = teacher,
            dayOfWeek = day.coerceIn(1, 7),
            startSection = startSection.coerceAtLeast(1),
            endSection = endSection.coerceAtLeast(startSection),
            startWeek = sortedWeeks.first(),
            endWeek = sortedWeeks.last(),
            weekType = Course.WEEK_TYPE_ALL,
            selectedWeeks = sortedWeeks,
            colorRes = color,
            scheduleId = scheduleId,
            isCustomTime = isCustomTime,
            customStartTime = customStartTime,
            customEndTime = customEndTime,
        )
    }
}

data class ThirdPartyTimeSlot(
    val number: Int,
    val startTime: String,
    val endTime: String,
)

/**
 * 分享口令取回结果（`ShareCodeApi.fetchShare` 的成功值）。
 *
 * 取回本身走 OkHttp + Gson（Android 专有），但**结果只是三个字段** ——
 * 抽到这里让共享页能直接消费，宿主只注入「取回」这个动作。
 */
data class ShareCodeFetchResult(
    val code: String,
    val scheduleName: String,
    val scheduleData: Map<String, Any>,
)
