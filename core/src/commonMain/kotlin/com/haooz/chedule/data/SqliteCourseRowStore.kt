package com.haooz.chedule.data

import com.haooz.chedule.data.db.Course as CourseRow
import com.haooz.chedule.data.db.NexioDatabase

/**
 * [CourseRowStore] 的实现：直接吃 SQLDelight 生成的查询。
 *
 * 放在 commonMain 而不是 androidMain 是刻意的：它只用 [NexioDatabase]（生成在 commonMain）
 * 和 `transactionWithResult`，没有任何平台 API。放这里 = 三端同一份读写逻辑，
 * 也让宿主机单测能用 JVM 驱动跑真 SQL（Android 实现里那些外键 / 级联行为，只在真库上才验得出来）。
 *
 * ## 为什么写的是「差量」而不是「整表覆盖」
 *
 * 旧实现是 `saveCourses(list)` → 序列化整张课表 → 写一个几百 KB 的字符串。改一节课的备注
 * 也要重写全部课程。这里按 id 比对出真正变化的课，只 UPDATE 那几行 —— 写放大从**数百 KB**
 * 降到**几十字节**，这是整次改造最主要的那笔收益。
 *
 * ## 事务边界
 *
 * 一次 [writeDiff] 一个事务：锚点、删除、更新全在里面。中途失败整体回滚，不会留下
 * 「课删了但周次还在」这种半套状态。
 *
 * ⚠ 顺序有讲究：`upsertCourse` 是 `INSERT OR REPLACE`，而 `foreign_keys=ON` 下 REPLACE 会
 * **先删旧行**，级联把该课的 course_week / course_week_note 一起删掉。所以必须
 * 「先 upsert 课程，再写周次」—— 反过来写等于刚插进去就被级联删掉。
 */
class SqliteCourseRowStore(private val database: NexioDatabase) : CourseRowStore {

    private val q get() = database.scheduleQueries

    override fun isImported(scheduleId: String): Boolean =
        runCatching { q.readMeta(importKey(scheduleId)).executeAsOneOrNull() }.getOrNull() != null

    override fun load(scheduleId: String): List<Course> {
        val courses = q.selectCoursesOf(scheduleId).executeAsList()
        if (courses.isEmpty()) return emptyList()
        // 周次 / 周备注各一条查询取整张课表，再在内存里分组 —— 不用按课循环查（40 门课会变 80 次往返）
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
            // 整表重来：先清空再灌，避免上次失败留下的半截行混进来
            q.deleteCoursesOfSchedule(anchor.scheduleId)
            courses.forEach { writeCourse(anchor.scheduleId, it) }

            // ── 自校验：写进去的必须能原样读回来 ──
            // 对拍不过就抛异常 → 事务整体回滚 + 不写导入标记 → 读取继续走 JSON。
            // 宁可这次迁移白做，也不能把「读出来少了字段」当成导入成功。
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
        NexioLog.w(TAG, "课程已迁入行表：schedule=${anchor.scheduleId}，${courses.size} 门")
    }

    override fun writeDiff(anchor: CourseRowAnchor, previous: List<Course>, next: List<Course>) {
        val diff = diffCourses(previous, next)
        // 没有变化就不开事务：翻周次之类的操作会频繁触发 save，空写一次都不该付
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
        database.transactionWithResult {
            val flag = q.readMeta(importKey(oldId)).executeAsOneOrNull()
            // 顺序：先建新锚点（课程的 schedule_id 外键要指向它），再搬课程，最后删旧锚点
            q.copyScheduleRow(newId, newId, oldId)
            q.moveCoursesToSchedule(newId, oldId)
            q.deleteSchedule(oldId)
            q.deleteMeta(importKey(oldId))
            // 标记跟着搬：不搬的话新名字会被判成「未导入」，转而从冻结的旧 JSON 重建
            if (flag != null) q.writeMeta(importKey(newId), flag)
        }
    }

    override fun deleteSchedule(scheduleId: String) {
        database.transactionWithResult {
            // course 由 ON DELETE CASCADE 跟着走；顺序无所谓，但标记要一起清
            q.deleteSchedule(scheduleId)
            q.deleteMeta(importKey(scheduleId))
        }
    }

    override fun invalidateAll() {
        database.transactionWithResult {
            q.deleteAllCourses()
            q.deleteAllSchedules()
            q.deleteCourseImportFlags()
        }
    }

    // ── 内部 ──────────────────────────────────────────────────────────────────

    /** 事务内回读（不走 [load]，避免重复建 map 且必须读同一事务快照）。 */
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

    /** 外键前置：time_config 行、schedule 行。两者都是 INSERT OR IGNORE，重复调用无副作用。 */
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

    /** 写一门课 + 它的周次 / 周备注。**必须先在外部 upsert 课程本体**，见类注释的级联说明。 */
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
        // upsertCourse 的 REPLACE 已经把子表级联删掉，这里重新写一遍即可（故意不先 delete）
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
        /** 与 SqliteStorage 的 `sp_imported:` 区分开：一个是「偏好文件导过没」，一个是「课程行导过没」。 */
        const val META_IMPORTED_PREFIX = "courses_imported:"
    }
}
