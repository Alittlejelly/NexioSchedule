package com.haooz.chedule.ui.web

import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.CourseRepository
import com.haooz.chedule.data.KeyValueEditor
import com.haooz.chedule.data.NexioLog
import com.haooz.chedule.data.optInt
import com.haooz.chedule.data.optBoolean
import com.haooz.chedule.data.optString
import com.haooz.chedule.data.parseJsonArray
import com.haooz.chedule.data.parseJsonObject
import kotlin.random.Random
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** 契约性拒绝：脚本会在 `await` 处拿到 Error，不是崩溃。 */
class EduBridgeReject(message: String) : Exception(message)

/**
 * 教务导入桥的跨平台内核：四类负载（课程 / 配置 / 时间段 / 组合作息）的解析、落盘与调用顺序约束都在此，Android 与 iOS 共用一份（两端各写一遍必然漂移）。
 *
 * ⚠ 与云端脚本仓库是契约：JSON 字段名、[PREFS_NAME] 键名、默认值（20 / 45 / 10 / 1）逐字不能改，改了存量用户的作息配置会静默错位。
 *
 * 平台侧只留三件事：调用 [handle]、兑现 Promise、对话框三件套与 toast。
 */
class EduImportBridgeCore(
    private val onCoursesImported: (List<Course>) -> Unit,
    private val onTaskCompleted: () -> Unit,
) {

    /** 本次导入的目标课表；null = 当前课表 */
    private var importTableId: String? = null

    // 组合作息的顺序状态（契约 2.1.5 / 2.1.7）：saveComboSchedule 需以已提交的基础时间段为骨架；combo 提交后本次不再接受 savePresetTimeSlots（否则方案绑定被覆盖）。
    // ⚠ 只在 [setImportTableId]（每次执行前必调）重置，不靠页面生命周期（会跨轮次残留）。
    private var baseTimeSlotsSaved = false
    private var comboSaved = false

    /** 同名课程复用同色（与 Android 迁移前逐字一致，别改成随机） */
    private val nameColors = mutableMapOf<String, Long>()
    private var colorIndex = 0

    fun setImportTableId(tableId: String?) {
        importTableId = tableId
        baseTimeSlotsSaved = false
        comboSaved = false
    }

    /**
     * @return 回给 JS 的字面量（`"true"`）；null = 内核不负责，交给平台。
     * @throws EduBridgeReject 契约拒绝或解析失败，平台侧应转成 Promise reject。
     */
    fun handle(method: String, args: List<Any?>): String? = when (method) {
        "saveImportedCourses" -> saveCourses(args.firstOrNull()?.toString().orEmpty())
        "saveCourseConfig" -> saveConfig(args.firstOrNull()?.toString().orEmpty())
        "savePresetTimeSlots" -> saveTimeSlots(args.firstOrNull()?.toString().orEmpty())
        "saveComboSchedule" -> saveCombo(args.firstOrNull()?.toString().orEmpty())
        "notifyTaskCompletion" -> {
            importTableId = null
            onTaskCompleted()
            "true"
        }
        else -> null
    }

    // ── 1.1 课程 ────────────────────────────────────────────────────

    private fun saveCourses(json: String): String {
        val courses = try {
            parseJsonArray(json).mapNotNull { (it as? JsonObject) }
        } catch (e: Exception) {
            throw EduBridgeReject("课程数据解析失败: ${e.message}")
        }.map(::toCourse)

        if (courses.isEmpty()) throw EduBridgeReject("课程数据为空")

        storeTarget()
        onCoursesImported(courses)

        val badDay = courses.count { it.dayOfWeek !in 1..7 }
        if (badDay > 0) {
            // 这类课落库后日期边界计算会跳过 → 表上看不见；不 reject 是怕偶发脏值毁掉整批
            NexioLog.w(TAG, "$badDay 门课程 day 不在 1..7，导入后可能不显示")
        }
        return "true"
    }

    private fun toCourse(o: JsonObject): Course {
        val name = o.optString("name")
        val weeks = (o["weeks"] as? JsonArray)
            ?.mapNotNull { it.toString().trim('"').toIntOrNull() }
            .orEmpty()
            .sorted()
        val startSection = o.optInt("startSection", 1)
        return Course(
            id = o.optString("id").takeIf { it.isNotBlank() } ?: randomId(),
            name = name,
            classroom = o.optString("position"),
            teacher = o.optString("teacher"),
            dayOfWeek = o.optInt("day", 1),
            startSection = startSection,
            endSection = o.optInt("endSection", startSection),
            startWeek = weeks.minOrNull() ?: 1,
            endWeek = weeks.maxOrNull() ?: 20,
            weekType = Course.WEEK_TYPE_ALL,
            selectedWeeks = weeks,
            colorRes = o.optInt("color", 0).takeIf { it != 0 }?.toLong()
                ?: colorForName(name),
            isCustomTime = o.optBoolean("isCustomTime"),
            customStartTime = o.optString("customStartTime").takeIf { it.isNotBlank() },
            customEndTime = o.optString("customEndTime").takeIf { it.isNotBlank() },
            scheduleId = importTableId ?: "",
        )
    }

    private fun colorForName(name: String): Long {
        val key = name.trim()
        nameColors[key]?.let { return it }
        val color = Course.courseColors[colorIndex % Course.courseColors.size]
        colorIndex++
        nameColors[key] = color
        return color
    }

    private fun randomId(): String =
        Random.Default.nextLong().toString(36) + Random.Default.nextLong().toString(36)

    // ── 1.3 课表配置 ────────────────────────────────────────────────

    private fun saveConfig(json: String): String {
        val config = try {
            parseJsonObject(json)
        } catch (e: Exception) {
            throw EduBridgeReject("课表配置解析失败: ${e.message}")
        }
        val totalWeeks = config.optInt("semesterTotalWeeks", 20)
        val classDuration = config.optInt("defaultClassDuration", 45)
        val breakDuration = config.optInt("defaultBreakDuration", 10)
        val firstDay = config.optInt("firstDayOfWeek", 1)
        val startDate = CourseRepository.normalizeClassStartDate(
            config.optString("semesterStartDate").takeIf { it.isNotBlank() }
        )
        AppStorage.store(PREFS_NAME).edit {
            // 显式写/清：本次没传的字段不能残留上一次导入的值
            if (startDate != null) putString("semester_start_date", startDate)
            else remove("semester_start_date")
            if (totalWeeks > 0) putInt("semester_total_weeks", totalWeeks)
            else remove("semester_total_weeks")
            putInt("default_class_duration", classDuration)
            putInt("default_break_duration", breakDuration)
            putInt("first_day_of_week", firstDay)
            writeTarget(this)
        }
        NexioLog.d(TAG, "课表配置: start=$startDate weeks=$totalWeeks firstDay=$firstDay")
        return "true"
    }

    // ── 1.2 预设时间段 ──────────────────────────────────────────────

    private fun saveTimeSlots(json: String): String {
        if (comboSaved) throw EduBridgeReject("组合作息已提交，本次导入不再接受基础时间段")
        val slots = try {
            parseJsonArray(json)
        } catch (e: Exception) {
            throw EduBridgeReject("时间段数据解析失败: ${e.message}")
        }
        if (slots.isEmpty()) throw EduBridgeReject("时间段数据为空")
        AppStorage.store(PREFS_NAME).edit {
            putString("preset_time_slots", json)
            writeTarget(this)
        }
        baseTimeSlotsSaved = true
        return "true"
    }

    // ── 1.4 组合作息方案 ────────────────────────────────────────────

    private fun saveCombo(json: String): String {
        // 官方契约 2.1.7：没有骨架就无从做差量合并
        if (!baseTimeSlotsSaved) {
            throw EduBridgeReject("请先调用 savePresetTimeSlots 提交基础时间段")
        }
        val combo = try {
            parseJsonObject(json)
        } catch (e: Exception) {
            throw EduBridgeReject("组合作息解析失败: ${e.message}")
        }
        val count = (combo["publicSchedules"] as? JsonArray)?.size ?: 0
        if (count == 0) throw EduBridgeReject("组合作息没有 publicSchedules")
        // 原样存，结构校验与差量合并交给消费端（与 preset_time_slots 同套路）
        AppStorage.store(PREFS_NAME).edit {
            putString("combo_schedule", json)
            writeTarget(this)
        }
        comboSaved = true
        return "true"
    }

    // ── 目标课表登记 ────────────────────────────────────────────────

    private fun storeTarget() {
        AppStorage.store(PREFS_NAME).edit { writeTarget(this) }
    }

    /**
     * ⚠ null 必须显式 remove —— `KeyValueEditor.putString` 只收非空值，不像
     * SharedPreferences 的 `putString(k, null)` 会隐式移除该键。
     */
    private fun writeTarget(editor: KeyValueEditor) {
        val target = importTableId
        if (target != null) editor.putString(TARGET_KEY, target)
        else editor.remove(TARGET_KEY)
    }

    private companion object {
        const val TAG = "EduImportBridge"
        const val PREFS_NAME = "edu_import_prefs"
        const val TARGET_KEY = "target_schedule_id"
    }
}
