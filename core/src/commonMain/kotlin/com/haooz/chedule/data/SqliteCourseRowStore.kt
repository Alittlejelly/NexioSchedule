package com.haooz.chedule.data

import com.haooz.chedule.data.db.Course as CourseRow
import com.haooz.chedule.data.db.NexioDatabase

/**
 * [CourseRowStore] 的实现。只用 [NexioDatabase] 与 `transactionWithResult`，无平台 API，
 * 所以放在 commonMain：三端一份逻辑，宿主机也能跑真 SQL。
 *
 * 一次 [writeDiff] 一个事务，中途失败整体回滚。
 * ⚠ `upsertCourse` 是 `INSERT OR REPLACE`，会级联删掉子表，必须先 upsert 课程再写周次。
 */
class SqliteCourseRowStore(private val database: NexioDatabase) : CourseRowStore {

    private val q get() = database.scheduleQueries

    /**
     * ⚠ 不许吞异常：读标记失败若被当成「未导入」，调用方会从（已被删掉的）blob 重新导入，
     * 而 blob 不存在时就是「导入一张空表」—— 直接把这张课表清空。读不了就让它抛。
     */
    override fun isImported(scheduleId: String): Boolean =
        q.readMeta(importKey(scheduleId)).executeAsOneOrNull() != null

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
        // 设置也要重同步：单表导入会直接改写 prefs 里的周次 / 开课日，
        // 留着标记的话下一次读取认为行已同步，读到的还是导入前的值
        q.deleteMeta(settingsSyncKey(scheduleId))
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
            if (isSettingsSynced(oldId)) {
                q.writeMeta(settingsSyncKey(newId), "1")
                q.deleteMeta(settingsSyncKey(oldId))
            }
        }
    }

    override fun deleteSchedule(scheduleId: String) {
        database.transactionWithResult {
            // course 由 CASCADE 跟着删；标记一起清
            q.deleteSchedule(scheduleId)
            q.deleteMeta(importKey(scheduleId))
            q.deleteMeta(settingsSyncKey(scheduleId))
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

    override fun settingsOf(scheduleId: String): ScheduleRowSettings? =
        runCatching {
            q.selectScheduleSettings(scheduleId).executeAsOneOrNull()?.let {
                ScheduleRowSettings(
                    currentWeek = it.current_week.toInt(),
                    totalWeeks = it.total_weeks.toInt(),
                    classStart = it.class_start,
                )
            }
        }.getOrNull()

    override fun writeSettings(
        scheduleId: String,
        currentWeek: Int?,
        totalWeeks: Int?,
        classStart: String?,
    ) {
        q.updateScheduleSettings(
            currentWeek = currentWeek?.toLong(),
            totalWeeks = totalWeeks?.toLong(),
            classStart = classStart,
            id = scheduleId,
        )
    }

    override fun timeConfigs(): List<StoredTimeConfig> =
        q.selectAllTimeConfigs().executeAsList().map {
            StoredTimeConfig(id = it.id, name = it.name, payload = it.payload)
        }

    override fun upsertTimeConfig(row: StoredTimeConfig) {
        database.transactionWithResult {
            q.updateTimeConfigRow(name = row.name, payload = row.payload, id = row.id)
            q.insertTimeConfigRow(id = row.id, name = row.name, payload = row.payload)
        }
    }

    override fun deleteTimeConfigRow(id: Long) {
        q.deleteTimeConfig(id)
    }

    override fun areTimeConfigsSynced(): Boolean =
        runCatching { q.readMeta(META_TIME_CONFIGS_SYNCED).executeAsOneOrNull() }.getOrNull() != null

    override fun markTimeConfigsSynced() {
        q.writeMeta(META_TIME_CONFIGS_SYNCED, "1")
    }

    override fun isSettingsSynced(scheduleId: String): Boolean =
        runCatching { q.readMeta(settingsSyncKey(scheduleId)).executeAsOneOrNull() }.getOrNull() != null

    override fun markSettingsSynced(scheduleId: String) {
        q.writeMeta(settingsSyncKey(scheduleId), "1")
    }

    override fun invalidateAll() {
        database.transactionWithResult {
            q.deleteAllCourses()
            q.deleteAllSchedules()
            q.deleteCourseImportFlags()
            q.deleteScheduleSettingsSyncFlags()
            // 作息表也作废：恢复备份会覆盖 prefs 里的 time_config_*，下次要从那边重新同步
            q.deleteTimeConfigsSyncFlag()
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
            // 归属只认调用方给的课表：course 自带的 scheduleId 可能是别的课表（复制 / 迁移残留），
            // 跟着它写会把行挂到别的课表名下，这边读不到、那边多一门，还会 REPLACE 掉对面同 id 的行
            schedule_id = scheduleId,
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

    private fun settingsSyncKey(scheduleId: String) = "$META_SETTINGS_SYNC_PREFIX$scheduleId"

    private companion object {
        const val TAG = "SqliteCourseRowStore"
        /** 与 SqliteStorage 的 `sp_imported:` 前缀区分：一个是偏好文件、一个是课程行。 */
        const val META_IMPORTED_PREFIX = "courses_imported:"
        /** 设置值是否已从 prefs 同步进行。行被整体作废后必须一起清，否则不会再同步。 */
        const val META_SETTINGS_SYNC_PREFIX = "settings_synced:"
        /** 作息配置是否已从 prefs 同步进表。 */
        const val META_TIME_CONFIGS_SYNCED = "time_configs_synced"
    }
}
