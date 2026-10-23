package com.haooz.chedule.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

// ════════════════════════════════════════════════════════════════════════
//  ICS（`text/calendar`）导入 —— 纯文本解析，无 Android / java 依赖
//  （「课表导入」页要进 `:ui-shared` commonMain，留在 Android 模块里那页就迁不动）。
//
//  与 [parseFullScheduleJson]（JSON 导入通道，见 `ScheduleImportCodec.kt`）并列：
//  两者都返回 `Triple(是否成功, 提示消息, 课表数据 Map)`，数据交由
//  `:ui-shared` 的 `applyScheduleData` 写入。
// ════════════════════════════════════════════════════════════════════════

/**
 * 解析 ICS 文本 → 可导入的课表数据 Map。
 *
 * 同名课（同星期 + 同起始分钟 + 同教室 + 同教师）合并周次，日期对放在 `datePairs` 里，
 * 节次映射（分钟 → 第几节）由下游 `applyScheduleData` 用用户的时间配置完成。
 */
fun parseIcsFile(text: String): Triple<Boolean, String, Map<String, Any>?> {
    return try {
        // 同名课在同星期同时段时合并周次
        val courseGroups = mutableMapOf<String, MutableList<Map<String, Any>>>()
        val lines = text.lines()

        var currentEvent = mutableMapOf<String, String>()
        var inEvent = false

        for (line in lines) {
            val trimmed = line.trim().trimEnd('\r', '\n')
            when {
                trimmed == "BEGIN:VEVENT" -> {
                    inEvent = true
                    currentEvent = mutableMapOf()
                }
                trimmed == "END:VEVENT" -> {
                    inEvent = false
                    if (currentEvent.isNotEmpty()) {
                        val parsed = parseIcsEvent(currentEvent)
                        if (parsed != null) {
                            // 键含教室/教师，避免不同地点的同名课被误合并
                            val mergeKey = "${parsed["name"]}_${parsed["dayOfWeek"]}_${parsed["startTotalMinutes"]}_${parsed["classroom"]}_${parsed["teacher"]}"
                            courseGroups.getOrPut(mergeKey) { mutableListOf() }.add(parsed)
                        }
                    }
                }
                inEvent -> {
                    val colonIndex = trimmed.indexOf(':')
                    if (colonIndex > 0) {
                        var key = trimmed.substring(0, colonIndex)
                        val value = trimmed.substring(colonIndex + 1)
                        key = key.substringBefore(';')
                        currentEvent[key] = value
                    }
                }
            }
        }

        if (courseGroups.isEmpty()) {
            return Triple(false, "未找到课程事件", null)
        }

        // List<List<String>> 承载日期对，避免 Pair 序列化问题
        val mergedCourses = mutableListOf<Map<String, Any>>()
        for ((_, courseEvents) in courseGroups) {
            val firstEvent = courseEvents.first()
            val datePairs = mutableListOf<List<String>>()
            for (event in courseEvents) {
                val sd = event["startDate"] as? String
                val ud = event["untilDate"] as? String
                if (!sd.isNullOrEmpty()) {
                    datePairs.add(listOf(sd, ud ?: ""))
                }
            }
            mergedCourses.add(firstEvent + mapOf("datePairs" to datePairs))
        }

        val data = mapOf(
            "schedule_name" to "ICS导入课表",
            "courses" to mergedCourses,
            "settings" to emptyMap<String, Any>()
        )
        Triple(true, "成功", data)
    } catch (e: Exception) {
        Triple(false, "ICS解析失败: ${e.message}", null)
    }
}

private fun parseIcsEvent(event: Map<String, String>): Map<String, Any>? {
    val summary = event["SUMMARY"] ?: return null
    val location = event["LOCATION"] ?: ""

    val dtstart = event["DTSTART"] ?: return null
    val dtend = event["DTEND"] ?: return null

    // ICS: YYYYMMDDTHHMMSS[.Z]，全天事件仅 YYYYMMDD
    val startRaw = dtstart.substringAfter(":")
    val endRaw = dtend.substringAfter(":")

    val isAllDay = startRaw.length == 8 && !startRaw.contains('T')

    val startDateStr = startRaw.take(8)
    val startTimeStr = if (isAllDay) "080000" else startRaw.drop(9).take(6)
    val endTimeStr = if (isAllDay) "090000" else endRaw.drop(9).take(6)

    val startYear = startDateStr.substring(0, 4).toIntOrNull() ?: return null
    val startMonth = startDateStr.substring(4, 6).toIntOrNull() ?: return null
    val startDay = startDateStr.substring(6, 8).toIntOrNull() ?: return null

    val startHour = startTimeStr.substring(0, 2).toIntOrNull() ?: 8
    val startMinute = startTimeStr.substring(2, 4).toIntOrNull() ?: 0
    val endHour = endTimeStr.substring(0, 2).toIntOrNull() ?: (startHour + 1)
    val endMinute = endTimeStr.substring(2, 4).toIntOrNull() ?: 0

    // 只返回分钟数，节次映射由 applyScheduleData 用用户配置完成
    val startTotalMinutes = startHour * 60 + startMinute
    val endTotalMinutes = endHour * 60 + endMinute

    val startDate = LocalDate(startYear, startMonth, startDay)
    val dayOfWeek = startDate.dayOfWeek.isoDayNumber

    val rrule = event["RRULE"] ?: ""
    var untilStr = ""
    var countStr = ""
    var byDayStr = ""

    for (part in rrule.split(";")) {
        when {
            part.startsWith("UNTIL=") -> untilStr = part.substringAfter("UNTIL=").take(8)
            part.startsWith("COUNT=") -> countStr = part.substringAfter("COUNT=")
            part.startsWith("BYDAY=") -> byDayStr = part.substringAfter("BYDAY=")
        }
    }

    if (byDayStr.isNotEmpty()) {
        val dayMap = mapOf("MO" to 1, "TU" to 2, "WE" to 3, "TH" to 4, "FR" to 5, "SA" to 6, "SU" to 7)
        val byDays = byDayStr.split(",").mapNotNull { dayMap[it.trim()] }
        if (byDays.isNotEmpty() && dayOfWeek !in byDays) {
            return null
        }
    }

    // LOCATION 约定：前导空格=仅教师；尾随/无空格=仅教室；中间空格分隔两者
    val classroom: String
    val teacher: String
    when {
        location.startsWith(" ") -> {
            classroom = ""
            teacher = location.trim()
        }
        location.trimEnd().endsWith(" ") || !location.contains(" ") -> {
            classroom = location.trim()
            teacher = ""
        }
        else -> {
            val spaceIndex = location.indexOf(' ')
            classroom = location.substring(0, spaceIndex).trim()
            teacher = location.substring(spaceIndex + 1).trim()
        }
    }

    return mapOf(
        "name" to summary,
        "classroom" to classroom,
        "teacher" to teacher,
        "dayOfWeek" to dayOfWeek,
        "startTotalMinutes" to startTotalMinutes,
        "endTotalMinutes" to endTotalMinutes,
        "selectedWeeks" to emptyList<Int>(),
        "startDate" to startDateStr,
        "untilDate" to untilStr,
        "count" to countStr,
        "isAllDay" to isAllDay
    )
}
