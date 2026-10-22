package com.haooz.chedule.ui.screens

import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.TeachingWeekReorganization
import com.haooz.chedule.data.TimeConfig
import com.haooz.chedule.data.minusDays
import com.haooz.chedule.data.minusWeeks
import com.haooz.chedule.data.randomUuidV4
import com.haooz.chedule.data.todayLocalDate
import com.haooz.chedule.data.weeksBetween
import com.haooz.chedule.ui.utils.ThirdPartySharePayload
import com.haooz.chedule.ui.utils.ThirdPartyShareSource
import com.haooz.chedule.ui.utils.parseShareCourseWeekModel
import com.haooz.chedule.ui.utils.parseShareCourses
import com.haooz.chedule.ui.utils.parseShareSelectedWeeks
import com.haooz.chedule.ui.utils.parseShareSettings
import com.haooz.chedule.ui.utils.parseShareTotalWeeks
import com.haooz.chedule.viewmodel.CourseViewModel
import com.haooz.chedule.viewmodel.ScheduleViewModel
import com.haooz.chedule.viewmodel.SettingsViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

// ════════════════════════════════════════════════════════════════════════
//  导入结果**落库**：把解析好的课表 Map / 第三方 payload 写进新建课表
// ════════════════════════════════════════════════════════════════════════

fun applyScheduleData(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    scheduleName: String,
    data: Map<String, Any>
): Pair<Boolean, String> {
    try {
        if (scheduleName in scheduleViewModel.scheduleNames.value) {
            return Pair(false, "课表「$scheduleName」已存在")
        }

        val settings = parseShareSettings(data)
        val reorganizationFieldPresent = settings?.containsKey("teaching_week_reorganizations") == true
        if (reorganizationFieldPresent) {
            val importedStartDate = settings["class_start_time"] as? String
            require(CourseRepository.normalizeClassStartDate(importedStartDate) != null) {
                "包含教学周重组的分享课表必须有有效开学日期"
            }
        }
        val importTotalWeeks = parseShareTotalWeeks(settings)
        val importedReorganizations = TeachingWeekReorganization.fromBackupValue(
            value = settings?.get("teaching_week_reorganizations"),
            present = reorganizationFieldPresent,
            totalWeeks = importTotalWeeks,
            preserveOutOfRangeRules = true,
        )
        val coursesData = parseShareCourses(data)
        // Keep stored course selections intact when the source semester was shortened later.
        val courseWeekHorizon = CourseRepository.MAX_TOTAL_WEEKS
        val shareCourseWeekModels = coursesData.map {
            parseShareCourseWeekModel(it, maxWeeks = courseWeekHorizon)
        }
        val selectedWeeksByCourse = coursesData.map {
            parseShareSelectedWeeks(it, maxWeeks = courseWeekHorizon)
        }
        val courses = mutableListOf<Course>()
        val courseNameColorMap = mutableMapOf<String, Long>()
        var colorIndex = 0

        val classStartTime = viewModel.classStartTime.value
        val defaultClassStartDate = try {
            LocalDate.parse(classStartTime.replace("/", "-"))
        } catch (_: Exception) {
            val today = todayLocalDate()
            today.minusDays((today.dayOfWeek.isoDayNumber - 1).toLong()).minusWeeks(16)
        }

        // ICS 无开学日设置时，用最早课程所在周的周一反推
        var icsClassStartDate: LocalDate? = null
        coursesData.forEach { courseMap ->
            val startDateStr = courseMap["startDate"] as? String
            if (startDateStr != null && startDateStr.length == 8) {
                val date = try {
                    LocalDate(
                        startDateStr.substring(0, 4).toInt(),
                        startDateStr.substring(4, 6).toInt(),
                        startDateStr.substring(6, 8).toInt()
                    )
                } catch (_: Exception) { null }
                if (date != null) {
                    val monday = date.minusDays((date.dayOfWeek.isoDayNumber - 1).toLong())
                    if (icsClassStartDate == null || monday < icsClassStartDate) {
                        icsClassStartDate = monday
                    }
                }
            }
        }
        val classStartDate = icsClassStartDate ?: defaultClassStartDate

        val classStartMonday = classStartDate.minusDays((classStartDate.dayOfWeek.isoDayNumber - 1).toLong())

        val userSectionTimes = settingsViewModel.sectionTimes.value
        fun findSectionByTime(startMinutes: Int, endMinutes: Int): Pair<Int, Int>? {
            var foundStart: Int? = null
            var foundEnd: Int? = null
            for ((section, timeRange) in userSectionTimes) {
                val parts = timeRange.split("-")
                if (parts.size != 2) continue
                val rangeStartParts = parts[0].split(":")
                val rangeEndParts = parts[1].split(":")
                if (rangeStartParts.size != 2 || rangeEndParts.size != 2) continue
                val rangeStart = (rangeStartParts[0].toIntOrNull() ?: continue) * 60 + (rangeStartParts[1].toIntOrNull() ?: continue)
                val rangeEnd = (rangeEndParts[0].toIntOrNull() ?: continue) * 60 + (rangeEndParts[1].toIntOrNull() ?: continue)
                if (startMinutes in rangeStart until rangeEnd) {
                    foundStart = section
                }
                // 结束时间允许落在节次末尾
                if (endMinutes in (rangeStart + 1)..rangeEnd) {
                    foundEnd = section
                }
            }
            if (foundStart != null && foundEnd != null) {
                return Pair(foundStart, foundEnd)
            }
            // 无精确匹配时退回起始节次
            if (foundStart != null) {
                return Pair(foundStart, foundStart)
            }
            return null
        }

        coursesData.forEachIndexed { courseIndex, courseMap ->
            val name = courseMap["name"] as? String ?: return@forEachIndexed
            val classroom = courseMap["classroom"] as? String ?: ""
            val teacher = courseMap["teacher"] as? String ?: ""
            val dayOfWeek = (courseMap["dayOfWeek"] as? Number)?.toInt() ?: return@forEachIndexed
            var selectedWeeks = selectedWeeksByCourse[courseIndex]
            val explicitWeekModel = shareCourseWeekModels[courseIndex]
            val hasExplicitWeekModel = explicitWeekModel != null

            // JSON 导出有 startSection；ICS 需按时间映射节次
            val directStartSection = (courseMap["startSection"] as? Number)?.toInt()
            val directEndSection = (courseMap["endSection"] as? Number)?.toInt()
            val sectionPair = if (directStartSection != null && directEndSection != null) {
                Pair(directStartSection, directEndSection)
            } else {
                val startTotalMinutes = (courseMap["startTotalMinutes"] as? Number)?.toInt()
                val endTotalMinutes = (courseMap["endTotalMinutes"] as? Number)?.toInt()
                if (startTotalMinutes != null && endTotalMinutes != null) {
                    findSectionByTime(startTotalMinutes, endTotalMinutes)
                } else null
            }

            if (sectionPair == null) return@forEachIndexed
            val (startSection, endSection) = sectionPair

            if (selectedWeeks.isEmpty() && !hasExplicitWeekModel) {
                @Suppress("UNCHECKED_CAST")
                val datePairs = courseMap["datePairs"] as? List<List<String>>
                if (!datePairs.isNullOrEmpty()) {
                    val allWeeks = mutableSetOf<Int>()
                    for (pair in datePairs) {
                        if (pair.isEmpty()) continue
                        val sd = pair[0]
                        val ud = if (pair.size > 1) pair[1] else ""
                        if (sd.length == 8) {
                            val courseStartDate = try {
                                LocalDate(
                                    sd.substring(0, 4).toInt(),
                                    sd.substring(4, 6).toInt(),
                                    sd.substring(6, 8).toInt()
                                )
                            } catch (_: Exception) { null }

                            if (courseStartDate != null) {
                                val courseMonday = courseStartDate.minusDays((courseStartDate.dayOfWeek.isoDayNumber - 1).toLong())
                                val startWeek = weeksBetween(classStartMonday, courseMonday) + 1

                                val endWeek = if (ud.length == 8) {
                                    val untilDate = try {
                                        LocalDate(
                                            ud.substring(0, 4).toInt(),
                                            ud.substring(4, 6).toInt(),
                                            ud.substring(6, 8).toInt()
                                        )
                                    } catch (_: Exception) { null }
                                    if (untilDate != null) {
                                        val daysDiff = (untilDate.dayOfWeek.isoDayNumber - dayOfWeek + 7) % 7
                                        val lastCourseDate = untilDate.minusDays(daysDiff.toLong())
                                        val lastCourseMonday = lastCourseDate.minusDays((lastCourseDate.dayOfWeek.isoDayNumber - 1).toLong())
                                        weeksBetween(classStartMonday, lastCourseMonday) + 1
                                    } else {
                                        startWeek
                                    }
                                } else {
                                    startWeek
                                }

                                allWeeks.addAll(startWeek..endWeek)
                            }
                        }
                    }
                    selectedWeeks = allWeeks.sorted()
                }
            }

            if (selectedWeeks.isEmpty() && !hasExplicitWeekModel) {
                val countStr = courseMap["count"] as? String
                val startDateStr = courseMap["startDate"] as? String
                if (!countStr.isNullOrEmpty() && !startDateStr.isNullOrEmpty() && startDateStr.length == 8) {
                    val count = countStr.toIntOrNull()
                    if (count != null && count > 0) {
                        val courseStartDate = try {
                            LocalDate(
                                startDateStr.substring(0, 4).toInt(),
                                startDateStr.substring(4, 6).toInt(),
                                startDateStr.substring(6, 8).toInt()
                            )
                        } catch (_: Exception) { null }
                        if (courseStartDate != null) {
                            val courseMonday = courseStartDate.minusDays((courseStartDate.dayOfWeek.isoDayNumber - 1).toLong())
                            val startWeek = weeksBetween(classStartMonday, courseMonday) + 1
                            selectedWeeks = (startWeek until startWeek + count).toList()
                        }
                    }
                }
            }

            if (selectedWeeks.isNotEmpty() || hasExplicitWeekModel) {
                // 导出带 colorRes 时原样还原；旧分享包没有则按课名轮询调色板
                val exportedColor = (courseMap["colorRes"] as? Number)?.toLong()
                val colorRes = exportedColor ?: courseNameColorMap.getOrPut(name) {
                    val color = Course.courseColors[colorIndex % Course.courseColors.size]
                    colorIndex++
                    color
                }
                courses.add(
                    Course(
                        id = randomUuidV4(),
                        name = name,
                        classroom = classroom,
                        teacher = teacher,
                        dayOfWeek = dayOfWeek,
                        startSection = startSection,
                        endSection = endSection,
                        isCustomTime = (courseMap["isCustomTime"] as? Boolean) ?: false,
                        customStartTime = courseMap["customStartTime"] as? String,
                        customEndTime = courseMap["customEndTime"] as? String,
                        startWeek = explicitWeekModel?.startWeek ?: selectedWeeks.min(),
                        endWeek = explicitWeekModel?.endWeek ?: selectedWeeks.max(),
                        weekType = explicitWeekModel?.weekType ?: Course.WEEK_TYPE_ALL,
                        selectedWeeks = selectedWeeks,
                        colorRes = colorRes
                    )
                )
            }
        }

        require(courses.size == coursesData.size) {
            "分享课表中的课程数据无法完整导入"
        }
        scheduleViewModel.addSchedule(scheduleName)
        scheduleViewModel.saveCoursesToSchedule(scheduleName, courses)

        // 先刷摘要再继续，否则切换页课程数会短暂错
        scheduleViewModel.refreshScheduleList()

        @Suppress("UNCHECKED_CAST")
        val times = data["times"] as? Map<String, Any>
        // 全套作息（夏令时/冬令时）。旧版本分享的没有这个字段 —— 留空，
        // 由 addTimeConfig 播种单作息，并走下面那段「按 times 写时间」的兼容路径
        val importedRoutines = (data["routines"] as? List<*>)
            ?.mapIndexedNotNull { index, raw ->
                com.haooz.chedule.data.TimeRoutine.fromRaw(raw, fallbackId = index + 1L)
            }
            // 一律按顺序重排 id：payload 自带的 id 可能和 fallback 撞上（A 的 id=2、B 缺 id 落在
            // index=1 就都是 2），重复 id 会让按 id 查找/删除的逻辑行为不确定。这里是全新配置、
            // 没有任何外部引用指向这些 id，重排是安全的。
            ?.mapIndexed { index, routine -> routine.copy(id = (index + 1).toLong()) }
            ?: emptyList()
        val importedMorningSections = (settings?.get("morning_sections") as? Number)?.toInt()
        val importedAfternoonSections = (settings?.get("afternoon_sections") as? Number)?.toInt()
        val importedEveningSections = (settings?.get("evening_sections") as? Number)?.toInt()
        val importedClassDuration = (settings?.get("class_duration") as? Number)?.toInt()?.takeIf { it > 0 }
        val importedBreakDuration = (settings?.get("break_duration") as? Number)?.toInt()?.takeIf { it > 0 }

        if (importedMorningSections != null || importedAfternoonSections != null || importedEveningSections != null || times != null) {
            val sectionTimesMap = mutableMapOf<String, String>()
            if (times != null) {
                @Suppress("UNCHECKED_CAST")
                (times["morning"] as? Map<String, String>)?.forEach { (k, v) -> sectionTimesMap["morning_$k"] = v }
                @Suppress("UNCHECKED_CAST")
                (times["afternoon"] as? Map<String, String>)?.forEach { (k, v) -> sectionTimesMap["afternoon_$k"] = v }
                @Suppress("UNCHECKED_CAST")
                (times["evening"] as? Map<String, String>)?.forEach { (k, v) -> sectionTimesMap["evening_$k"] = v }
            }
            @Suppress("UNCHECKED_CAST")
            val importedSectionNames = (times?.get("section_names") as? Map<String, String>) ?: emptyMap()

            val newConfig = TimeConfig(
                name = scheduleName,
                morningSections = importedMorningSections ?: 4,
                afternoonSections = importedAfternoonSections ?: 4,
                eveningSections = importedEveningSections ?: 4,
                classDuration = importedClassDuration ?: 45,
                shortBreak = importedBreakDuration ?: 10,
                sectionTimes = sectionTimesMap,
                sectionNames = importedSectionNames,
                routines = importedRoutines
            )
            val newConfigId = scheduleViewModel.addTimeConfig(newConfig)
            scheduleViewModel.setScheduleTimeConfigId(scheduleName, newConfigId)
        } else {
            val currentScheduleTimeConfigId = scheduleViewModel.getCurrentScheduleTimeConfigId()
            if (currentScheduleTimeConfigId != 0L) {
                scheduleViewModel.setScheduleTimeConfigId(scheduleName, currentScheduleTimeConfigId)
            }
        }

        if (settings != null) {
            scheduleViewModel.switchToSchedule(scheduleName)

            (settings["class_start_time"] as? String)?.let { viewModel.setClassStartTime(it) }
            (settings["current_week"] as? Number)?.toInt()?.let { viewModel.setCurrentWeek(it) }
            (settings["total_weeks"] as? Number)?.toInt()?.let { viewModel.setTotalWeeks(it) }
            if (reorganizationFieldPresent && !CourseRepository.getInstance()
                    .setTeachingWeekReorganizations(
                        rules = importedReorganizations,
                        scheduleId = scheduleName,
                        preserveOutOfRangeRules = true,
                    )
            ) {
                throw IllegalArgumentException("教学周重组规则与课表总周数冲突")
            }
            (settings["smart_weekend"] as? Boolean)?.let {
                settingsViewModel.setSmartWeekend(it)
            }
            @Suppress("UNCHECKED_CAST")
            (settings["show_weekend_days"] as? List<Number>)?.let {
                if (it.isNotEmpty()) settingsViewModel.setSmartWeekend(true)
            }
            (settings["show_non_current_week"] as? Boolean)?.let {
                settingsViewModel.setShowNonCurrentWeek(it)
            }
            (settings["morning_sections"] as? Number)?.toInt()?.let { settingsViewModel.setMorningSections(it) }
            (settings["afternoon_sections"] as? Number)?.toInt()?.let { settingsViewModel.setAfternoonSections(it) }
            (settings["evening_sections"] as? Number)?.toInt()?.let { settingsViewModel.setEveningSections(it) }

            // 官方文档 config：firstDayOfWeek（1=周一…7=周日，默认 1）。App 表格固定周一起始，
            // 学校以其它日为周起始时课程 day 相对该周起始，换算回绝对星期防落列错位（与教务导入一致）。
            (settings["first_day_of_week"] as? Number)?.toInt()?.takeIf { it in 2..7 }?.let { firstDay ->
                val repository = CourseRepository.getInstance()
                val currentCourses = repository.getAllCourses()
                if (currentCourses.isNotEmpty()) {
                    val remapped = currentCourses.map { course ->
                        val day = course.dayOfWeek
                        if (day in 1..7) {
                            val absolute = (day - 1 + (firstDay - 1)) % 7 + 1
                            if (absolute != day) course.copy(dayOfWeek = absolute) else course
                        } else {
                            course
                        }
                    }
                    if (remapped.indices.any { remapped[it].dayOfWeek != currentCourses[it].dayOfWeek }) {
                        viewModel.replaceCourses(remapped)
                    }
                }
            }

            @Suppress("UNCHECKED_CAST")
            val times = data["times"] as? Map<String, Any>
            // 带了全套作息时不再按 times 回写：times 是「分享方导出当天生效那套」，
            // 接收方此刻生效的可能是另一套（跨季节导入），回写会把那套覆盖掉
            if (times != null && importedRoutines.isEmpty()) {
                val morningTimes = mutableMapOf<Int, String>()
                val afternoonTimes = mutableMapOf<Int, String>()
                val eveningTimes = mutableMapOf<Int, String>()

                @Suppress("UNCHECKED_CAST")
                (times["morning"] as? Map<String, String>)?.forEach { (k, v) ->
                    k.toIntOrNull()?.let { morningTimes[it] = v }
                }
                @Suppress("UNCHECKED_CAST")
                (times["afternoon"] as? Map<String, String>)?.forEach { (k, v) ->
                    k.toIntOrNull()?.let { afternoonTimes[it] = v }
                }
                @Suppress("UNCHECKED_CAST")
                (times["evening"] as? Map<String, String>)?.forEach { (k, v) ->
                    k.toIntOrNull()?.let { eveningTimes[it] = v }
                }

                if (morningTimes.isNotEmpty()) settingsViewModel.saveMorningTimes(morningTimes)
                if (afternoonTimes.isNotEmpty()) settingsViewModel.saveAfternoonTimes(afternoonTimes)
                if (eveningTimes.isNotEmpty()) settingsViewModel.saveEveningTimes(eveningTimes)
            }
        }

        viewModel.reloadCourses()
        settingsViewModel.refreshSettings()
        scheduleViewModel.refreshScheduleList()

        return Pair(true, "成功导入课表「$scheduleName」\n共${courses.size}门课程")
    } catch (e: Exception) {
        return Pair(false, "导入失败: ${e.message}")
    }
}

/** 将 WakeUp / 星链解析结果写入新建课表（课程 + 可选时间段/学期配置） */
fun applyThirdPartySharePayload(
    courseViewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsViewModel: SettingsViewModel,
    scheduleName: String,
    payload: ThirdPartySharePayload,
): Pair<Boolean, String> {
    return try {
        if (scheduleName in scheduleViewModel.scheduleNames.value) {
            return false to "课表「$scheduleName」已存在"
        }
        scheduleViewModel.addSchedule(scheduleName)

        val colorMap = mutableMapOf<String, Long>()
        var colorIndex = 0
        val courses = payload.courses.map { item ->
            val color = colorMap.getOrPut(item.name) {
                Course.courseColors[colorIndex % Course.courseColors.size].also { colorIndex++ }
            }
            item.toCourse(scheduleName, color)
        }
        if (courses.isEmpty()) {
            return false to "未解析到课程数据"
        }
        scheduleViewModel.saveCoursesToSchedule(scheduleName, courses)

        if (payload.timeSlots.isNotEmpty()) {
            val repository = CourseRepository()
            val maxSlot = payload.timeSlots.maxOf { it.number }
            val morningSections = settingsViewModel.morningSections.value.coerceAtLeast(1)
            val afternoonSections = settingsViewModel.afternoonSections.value.coerceAtLeast(1)
            var eveningSections = settingsViewModel.eveningSections.value.coerceAtLeast(1)
            val capacity = morningSections + afternoonSections + eveningSections
            if (maxSlot > capacity) {
                eveningSections = (maxSlot - morningSections - afternoonSections).coerceAtLeast(1)
            }
            val morningTimes = mutableMapOf<Int, String>()
            val afternoonTimes = mutableMapOf<Int, String>()
            val eveningTimes = mutableMapOf<Int, String>()
            for (slot in payload.timeSlots) {
                val timeStr = "${slot.startTime}-${slot.endTime}"
                when {
                    slot.number <= morningSections -> morningTimes[slot.number] = timeStr
                    slot.number <= morningSections + afternoonSections ->
                        afternoonTimes[slot.number - morningSections] = timeStr
                    else -> eveningTimes[slot.number - morningSections - afternoonSections] = timeStr
                }
            }
            repository.applyTimeImportToSchedule(
                scheduleName,
                morningSections,
                afternoonSections,
                eveningSections,
                morningTimes,
                afternoonTimes,
                eveningTimes,
            )
        } else {
            // 无时间段时复用当前课表的时间配置
            val currentConfigId = scheduleViewModel.getCurrentScheduleTimeConfigId()
            if (currentConfigId != 0L) {
                scheduleViewModel.setScheduleTimeConfigId(scheduleName, currentConfigId)
            }
        }

        scheduleViewModel.switchToSchedule(scheduleName)
        payload.semesterStartDate?.takeIf { it.isNotBlank() }?.let {
            courseViewModel.setClassStartTime(it)
        }
        payload.semesterTotalWeeks?.takeIf { it > 0 }?.let {
            courseViewModel.setTotalWeeks(it)
        }

        courseViewModel.reloadCourses()
        settingsViewModel.refreshSettings()
        scheduleViewModel.refreshScheduleList()
        true to "成功导入课表「$scheduleName」\n共 ${courses.size} 门课程"
    } catch (e: Exception) {
        false to "导入失败: ${e.message}"
    }
}
