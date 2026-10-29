package com.haooz.chedule.data

import com.haooz.chedule.data.db.Course as CourseRow
import com.haooz.chedule.data.db.NexioDatabase

/**
 * [CourseRowStore] 的实现。只用 [NexioDatabase] 与 `transactionWithResult`，无平台 API，
 * 因此放在 commonMain：三端一份逻辑，宿主机也能用 JVM 驱动跑真 SQL。
 *
 * 一次 [writeDiff] 一个事务，锚点、删除、更新都在里面，中途失败整体回滚。
 *
 * ⚠ `upsertCourse` 是 `INSERT OR REPLACE`，`foreign_keys=ON` 时 REPLACE 先删旧行、
 * 级联删掉该课的 course_week / course_week_note。所以必须先 upsert 课程再写周次。
 */
class SqliteCourseRowStore(private val database: NexioDatabase) : CourseRowStore {

    private val q get() = database.scheduleQueries

    override fun isImported(scheduleId: String): Boolean =
        runCatching { q.readMeta(importKey(scheduleId)).executeAsOneOrNull() }.getOrNull() != null

    override fun load(scheduleId: String): List<Course> {
        val courses = q.selectCoursesOf(scheduleId).executeAsList()
        if (courses.isEmpty()) return emptyList()
        // 周次 / 周备注各一条查询取整表再分组，不按课循环查
        val weeks = q.selectWeeksOfSchedule(scheduleId).executeAsList()
            .groupBy({ it.course_id }, { it.week.toInt() })
        val notes = q.selectWeekNotesOfSchedule(scheduleId).executeAsList()
            .groupBy({ it.course_id }, { it.week.toInt() to it.note })
        return courses.map { row ->
            row.toCourse(
                weeks = weeks[row.id].orEmpty(),
                weekNotes = notes[row.id].orEmpty().toMap(),
            )
        }
    }

    override fun importAll(anchor: CourseRowAnchor, courses: List<Course>) {
        database.transactionWithResult {
            ensureAnchors(anchor)
            // 先清空再灌，避免上次失败留下的半截行
            q.deleteCoursesOfSchedule(anchor.scheduleId)
            courses.forEach { writeCourse(anchor.scheduleId, it) }

            // 自校验：对拍不过就抛异常，事务回滚且不写导入标记，读取继续走 JSON
            val readBack = readInTx(anchor.scheduleId)
            if (!sameCourseContent(courses, readBack)) {
                val lost = courses.size - readBack.size
                error(
                    "课程行表自校验失败（schedule=${anchor.scheduleId}）：写入 ${courses.size} 门，" +
                        "读回 ${readBack.size} 门（差 $lost），已整体回滚"
                )
            }
            q.writeMeta(importKey(anchor.scheduleId), courses.size.toString())
        }
        NexioLog.w(TAG, "课程已导入行表：schedule=${anchor.scheduleId}，${courses.size} 门")
    }

    override fun writeDiff(anchor: CourseRowAnchor, previous: List<Course>, next: List<Course>) {
        val diff = diffCourses(previous, next)
        // 无变化不开事务
        if (diff.deleted.isEmpty() && diff.updated.isEmpty()) return

        database.transactionWithResult {
            ensureAnchors(anchor)
            diff.deleted.forEach { q.deleteCourse(it) }
            diff.updated.forEach { writeCourse(anchor.scheduleId, it) }
        }
    }

    override fun markStale(scheduleId: String) {
        q.deleteMeta(importKey(scheduleId))
    }

    override fun renameSchedule(oldId: String, newId: String) {
        // 同名等于「REPLACE 自己」：先级联删光课程，再把空行插回来、搬 0 门课、最后连锚点一起删掉，
        // 而导入标记会被原样写回 —— 课表变空且再也不会从 JSON 重建。UI 挡了同名，存储层也得挡。
        if (oldId == newId) return
        database.transactionWithResult {
            val flag = q.readMeta(importKey(oldId)).executeAsOneOrNull()
            // 顺序：先建新锚点（课程的 schedule_id 外键要指向它），再搬课程，最后删旧锚点
            q.copyScheduleRow(newId, newId, oldId)
            q.moveCoursesToSchedule(newId, oldId)
            q.deleteSchedule(oldId)
            q.deleteMeta(importKey(oldId))
            // 标记一起搬，否则新名字被判成未导入，会从冻结的 JSON 重建
            if (flag != null) q.writeMeta(importKey(newId), flag)
        }
    }

    override fun deleteSchedule(scheduleId: String) {
        database.transactionWithResult {
            // course 由 CASCADE 跟着删；标记一起清
            q.deleteSchedule(scheduleId)
            q.deleteMeta(importKey(scheduleId))
        }
    }

    override fun bindingOf(scheduleId: String): Long? =
        runCatching { q.selectScheduleBinding(scheduleId).executeAsOneOrNull() }.getOrNull()

    override fun setBinding(anchor: CourseRowAnchor) {
        database.transactionWithResult {
            // 外键目标先就位
            ensureAnchors(anchor)
            q.updateScheduleBinding(anchor.timeConfigId, anchor.scheduleId)
        }
    }

    override fun bindings(): Map<String, Long> =
        q.selectAllScheduleBindings().executeAsList().associate { it.id to it.time_config_id }

    override fun invalidateAll() {
        database.transactionWithResult {
            q.deleteAllCourses()
            q.deleteAllSchedules()
            q.deleteCourseImportFlags()
        }
    }

    // ── 内部 ──────────────────────────────────────────────────────────────────

    /** 事务内回读，读同一事务快照。 */
    private fun readInTx(scheduleId: String): List<Course> {
        val courses = q.selectCoursesOf(scheduleId).executeAsList()
        val weeks = q.selectWeeksOfSchedule(scheduleId).executeAsList()
            .groupBy({ it.course_id }, { it.week.toInt() })
        val notes = q.selectWeekNotesOfSchedule(scheduleId).executeAsList()
            .groupBy({ it.course_id }, { it.week.toInt() to it.note })
        return courses.map {
            it.toCourse(weeks[it.id].orEmpty(), notes[it.id].orEmpty().toMap())
        }
    }

    /** 补齐外键目标行。两者都是 INSERT OR IGNORE，可重复调用。 */
    private fun ensureAnchors(anchor: CourseRowAnchor) {
        q.ensureTimeConfigRow(
            id = anchor.timeConfigId,
            name = anchor.timeConfigName,
            payload = anchor.timeConfigPayload,
        )
        q.ensureScheduleRow(
            id = anchor.scheduleId,
            name = anchor.scheduleId,
            time_config_id = anchor.timeConfigId,
            sort_order = anchor.sortOrder.toLong(),
            current_week = anchor.currentWeek.toLong(),
            total_weeks = anchor.totalWeeks.toLong(),
            class_start = anchor.classStart,
        )
    }

    /** 写一门课 + 它的周次 / 周备注。顺序见类注释。 */
    private fun writeCourse(scheduleId: String, course: Course) {
        q.upsertCourse(
            id = course.id,
            schedule_id = course.scheduleId.ifBlank { scheduleId },
            name = course.name,
            classroom = course.classroom,
            teacher = course.teacher,
            day_of_week = course.dayOfWeek.toLong(),
            start_section = course.startSection.toLong(),
            end_section = course.endSection.toLong(),
            start_week = course.startWeek.toLong(),
            end_week = course.endWeek.toLong(),
            week_type = course.weekType.toLong(),
            color_res = course.colorRes,
            note = course.note,
            last_modified = course.lastModified,
            is_custom_time = if (course.isCustomTime) 1L else 0L,
            custom_start_time = course.customStartTime,
            custom_end_time = course.customEndTime,
        )
        // REPLACE 已级联删掉子表，这里重新写
        course.selectedWeeks.distinct().forEach { q.insertWeek(course.id, it.toLong()) }
        course.weekNotes.forEach { (week, note) -> q.insertWeekNote(course.id, week.toLong(), note) }
    }

    private fun CourseRow.toCourse(weeks: List<Int>, weekNotes: Map<Int, String>): Course = Course(
        id = id,
        name = name,
        classroom = classroom,
        teacher = teacher,
        dayOfWeek = day_of_week.toInt(),
        startSection = start_section.toInt(),
        endSection = end_section.toInt(),
        startWeek = start_week.toInt(),
        endWeek = end_week.toInt(),
        weekType = week_type.toInt(),
        colorRes = color_res,
        selectedWeeks = weeks,
        scheduleId = schedule_id,
        lastModified = last_modified,
        isCustomTime = is_custom_time != 0L,
        customStartTime = custom_start_time,
        customEndTime = custom_end_time,
        note = note,
        weekNotes = weekNotes,
    )

    private fun importKey(scheduleId: String) = "$META_IMPORTED_PREFIX$scheduleId"

    private companion object {
        const val TAG = "SqliteCourseRowStore"
        /** 与 SqliteStorage 的 `sp_imported:` 前缀区分：一个是偏好文件、一个是课程行。 */
        const val META_IMPORTED_PREFIX = "courses_imported:"
    }
}
