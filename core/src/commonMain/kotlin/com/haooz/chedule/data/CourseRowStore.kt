package com.haooz.chedule.data

import kotlin.concurrent.Volatile

/**
 * 课表在实体表里的「锚点」参数。
 *
 * 为什么存课表要带这一坨：`course.schedule_id` 是**外键**，指向 `schedule(id)`；而
 * `schedule.time_config_id` 是 **NOT NULL 外键**，指向 `time_config(id)`。所以写课程之前，
 * 必须先保证这两行存在 —— 否则 SQLite 直接拒绝写入（这正是我们要的：宁可写失败，也不要写出孤儿数据）。
 *
 * 现阶段 `schedule` 表只承担「外键锚点」职责：`current_week` / `total_weeks` / `class_start`
 * 仍以 prefs 为准，这里的值只是顺手写进去，好让下一步（作息绑定改 id）能直接接管。
 */
data class CourseRowAnchor(
    val scheduleId: String,
    val timeConfigId: Long,
    val timeConfigName: String,
    val timeConfigPayload: String,
    val sortOrder: Int,
    val currentWeek: Int,
    val totalWeeks: Int,
    val classStart: String?,
)

/**
 * 课程行表的访问口子（实现放平台侧：只有那边拿得到 SQLDelight driver）。
 *
 * 抽成接口不是为了「以后可能换实现」，而是为了让 [CourseRepository] 留在 commonMain ——
 * 桌面端 / iOS 没有驱动时 [CourseStorage.rows] 为 null，自动退回 JSON blob，
 * 三端共用同一份业务逻辑，而不是在各自平台复制一遍读写。
 */
interface CourseRowStore {

    /** 该课表是否已经从 JSON 导入过。只有 true 时行表才是真相。 */
    fun isImported(scheduleId: String): Boolean

    /** 读一个课表的全部课程（含周次 / 周备注）。没课时返回空列表。 */
    fun load(scheduleId: String): List<Course>

    /**
     * 首次导入：整表写入 + **同一事务内回读逐字段对拍** + 写导入标记。
     * 对拍不一致直接抛异常，事务整体回滚（此时行表仍是空的，读取继续走 JSON）。
     */
    fun importAll(anchor: CourseRowAnchor, courses: List<Course>)

    /**
     * 行级差量写：只碰 [previous] 与 [next] 之间真正变了的课。
     * 改一门课 = 1 行 UPDATE + 它自己的周次行，而不是序列化整张课表。
     */
    fun writeDiff(anchor: CourseRowAnchor, previous: List<Course>, next: List<Course>)

    /** 清掉导入标记，让课表退回「以 JSON 为准」（行表写失败时的兜底，下次读取会重新导入）。 */
    fun markStale(scheduleId: String)

    /**
     * 课表改名：把行数据搬到新名字下。
     *
     * 必须「搬」而不是「删掉等重新导入」—— 迁移之后 JSON blob 就冻结了，
     * 让新名字从 blob 重建等于回滚到迁移前那一版课表。
     */
    fun renameSchedule(oldId: String, newId: String)

    /** 删整张课表的数据（含锚点行，课程由外键级联删除）。 */
    fun deleteSchedule(scheduleId: String)

    /**
     * 全部作废（整库恢复备份后用）。
     *
     * 恢复会把整个课表集合换掉，逐张比对新旧名字不现实；清空行表和标记，
     * 下一次读取会从恢复出来的 JSON 重新导入 —— 导入本身带自校验，不会把坏数据盖章成已迁移。
     */
    fun invalidateAll()
}

/**
 * 平台侧的 [CourseRowStore] 注册点。与 [AppStorage] 同一套路：启动时装一次，业务侧随时取。
 *
 * 取不到（桌面端 / 测试 / 初始化前）返回 null，调用方退回 JSON —— **不能抛异常**：
 * 存储换代期间，「没有数据库」必须是可用状态，否则老用户第一次启动就是白屏。
 */
object CourseStorage {

    @Volatile
    private var provider: (() -> CourseRowStore?)? = null

    @Volatile
    private var resolved: CourseRowStore? = null

    /** 由平台侧在 Application 启动时调用。重复调用等价于重置。 */
    fun install(provider: () -> CourseRowStore?) {
        this.provider = provider
        resolved = null
    }

    val rows: CourseRowStore?
        get() {
            resolved?.let { return it }
            val p = provider ?: return null
            // 初始化失败（库打不开等）返回 null，不缓存失败结果 —— 下次还能重试
            val created = runCatching { p() }.getOrNull() ?: return null
            resolved = created
            return created
        }
}

/** 写了什么、删了什么。拆出来是为了能在没有数据库的宿主机上单测这段判断。 */
internal data class CourseRowDiff(
    val deleted: List<String>,
    val updated: List<Course>,
)

/**
 * 按 id 比对出真正变化的课。
 *
 * 用整对象判等（[Course] 是 data class，含 lastModified）而不是逐字段手写比对：
 * 加字段时忘了同步比对表，就会出现「改了备注但没写盘」这种最难查的问题。
 */
internal fun diffCourses(previous: List<Course>, next: List<Course>): CourseRowDiff {
    val previousById = previous.associateBy { it.id }
    val nextIds = next.mapTo(HashSet(next.size)) { it.id }
    return CourseRowDiff(
        deleted = previousById.keys.filter { it !in nextIds },
        updated = next.filter { previousById[it.id] != it },
    )
}

/**
 * 行表读回结果与内存对象是否等价。导入后的自校验用它。
 *
 * 只对**存储会归一化**的地方放行：
 * - `selectedWeeks` 落表后按 week 升序返回，原 List 可能是乱序 → 比排序后的结果；
 * - `weekNotes` 是 Map，Kotlin 的 Map 相等本来就与顺序无关。
 * 其余字段必须逐字段相等 —— 这里刻意不「差不多就算过」，宁可让导入失败回退 JSON。
 */
internal fun sameCourseContent(expected: List<Course>, actual: List<Course>): Boolean {
    if (expected.size != actual.size) return false
    val byId = actual.associateBy { it.id }
    if (byId.size != actual.size) return false // 行表里出现重复 id
    for (course in expected) {
        val other = byId[course.id] ?: return false
        if (course.copy(selectedWeeks = course.selectedWeeks.distinct().sorted()) !=
            other.copy(selectedWeeks = other.selectedWeeks.distinct().sorted())
        ) {
            return false
        }
    }
    return true
}
