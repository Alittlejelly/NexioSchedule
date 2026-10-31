package com.haooz.chedule.ui.utils

import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.platform.randomUuidV4
import com.haooz.chedule.i18n.MiscStr

// 第三方 App 课表导入（WakeUp / 星链）的数据契约。
// 抓取器在宿主侧，由页面注入（见 BackupAndMigrationScreen 的 onImportThirdParty）。

/** 第三方课表分享口令来源（WakeUp / 星链） */
enum class ThirdPartyShareSource {
    WakeUp,
    StarLink;

    /** 展示名（导入入口 / 确认弹窗标题）；枚举常量只初始化一次，改为按调用时刻语言取值 */
    val displayName: String
        get() = when (this) {
            WakeUp -> MiscStr.wakeUpImportTitle()
            StarLink -> MiscStr.starLinkImportTitle()
        }

    /** 口令输入框标签 */
    val inputLabel: String
        get() = when (this) {
            WakeUp -> MiscStr.wakeUpInputLabel()
            StarLink -> MiscStr.starLinkInputLabel()
        }
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

/**
 * 与适配脚本一致：合并连续节次与同节次周次。
 *
 * 原先是 androidApp `ThirdPartyShareImporter` 的成员方法，2026-10-13 上提到这里 ——
 * iOS 的星链导入要用同一套合并规则，两边各写一份必然漂移。纯函数，搬过来逐字未改。
 *
 * 两步合并：
 * 1. 同课程（名称/教师/地点/星期/周次全同）且节次连续 → 合成一节（1-2 + 3-4 → 1-4）；
 *    完全相同的一对去重。
 * 2. 同课程同节次 → 周次取并集（星链会把 1-8 周拆成多条下发）。
 */
fun mergeAndDistinctCourses(courses: List<ThirdPartyCourse>): List<ThirdPartyCourse> {
    if (courses.size <= 1) return courses
    val list = courses.map { c ->
        c.copy(
            name = c.name.trim(),
            teacher = c.teacher.trim(),
            position = c.position.trim(),
            weeks = c.weeks.sorted(),
        )
    }

    val sorted1 = list.sortedWith(
        compareBy(
            { it.name },
            { it.teacher },
            { it.position },
            { it.day },
            { it.weeks.joinToString(",") },
            { it.startSection },
        )
    )
    val step1 = mutableListOf<ThirdPartyCourse>()
    var current = sorted1.first()
    for (i in 1 until sorted1.size) {
        val next = sorted1[i]
        val sameCourseAndWeeks =
            current.name == next.name &&
                current.teacher == next.teacher &&
                current.position == next.position &&
                current.day == next.day &&
                current.weeks == next.weeks
        val continuous = current.endSection + 1 == next.startSection
        val duplicate = current.startSection == next.startSection &&
            current.endSection == next.endSection
        when {
            sameCourseAndWeeks && continuous ->
                current = current.copy(endSection = next.endSection)
            sameCourseAndWeeks && duplicate -> Unit
            else -> {
                step1.add(current)
                current = next
            }
        }
    }
    step1.add(current)

    val sorted2 = step1.sortedWith(
        compareBy(
            { it.name },
            { it.teacher },
            { it.position },
            { it.day },
            { it.startSection },
            { it.endSection },
        )
    )
    val step2 = mutableListOf<ThirdPartyCourse>()
    var cur = sorted2.first()
    for (i in 1 until sorted2.size) {
        val nxt = sorted2[i]
        val same =
            cur.name == nxt.name &&
                cur.teacher == nxt.teacher &&
                cur.position == nxt.position &&
                cur.day == nxt.day &&
                cur.startSection == nxt.startSection &&
                cur.endSection == nxt.endSection
        if (same) {
            cur = cur.copy(weeks = (cur.weeks + nxt.weeks).distinct().sorted())
        } else {
            step2.add(cur)
            cur = nxt
        }
    }
    step2.add(cur)
    return step2
}
