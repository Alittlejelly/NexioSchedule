package com.haooz.chedule.ui.utils

import com.haooz.chedule.data.Course
import com.haooz.chedule.data.randomUuidV4

// 第三方 App 课表导入（WakeUp / 星链）的数据契约。
// 抓取器在宿主侧，由页面注入（见 BackupAndMigrationScreen 的 onImportThirdParty）。

/** 第三方课表分享口令来源（WakeUp / 星链） */
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

/** 分享口令取回结果，供共享页直接消费；取回动作本身由宿主注入。 */
data class ShareCodeFetchResult(
    val code: String,
    val scheduleName: String,
    val scheduleData: Map<String, Any>,
)
