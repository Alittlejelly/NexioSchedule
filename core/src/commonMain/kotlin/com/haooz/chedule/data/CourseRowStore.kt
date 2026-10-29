package com.haooz.chedule.data

import kotlin.concurrent.Volatile

/**
 * 写行表前必须先就位的两行外键目标：`time_config(id)` 与 `schedule(id)`。
 *
 * `current_week` / `total_weeks` / `class_start` 写进行表只为让行自洽，读仍以 prefs 为准。
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
 * 课程行表访问口。实现缺失时（[CourseStorage.rows] 为 null）调用方退回 JSON。
 */
interface CourseRowStore {

    /** 该课表是否已从 JSON 导入。true 时行表是真相。 */
    fun isImported(scheduleId: String): Boolean

    /** 读一个课表的全部课程（含周次 / 周备注）。 */
    fun load(scheduleId: String): List<Course>

    /** 首次导入：整表写入 + 同一事务内回读对拍 + 写导入标记。对拍不一致抛异常并整体回滚。 */
    fun importAll(anchor: CourseRowAnchor, courses: List<Course>)

    /** 行级差量写：只写 [previous] 与 [next] 之间变了的课。 */
    fun writeDiff(anchor: CourseRowAnchor, previous: List<Course>, next: List<Course>)

    /** 清掉导入标记，让该课表退回以 JSON 为准，下次读取重新导入。 */
    fun markStale(scheduleId: String)

    /**
     * 课表改名：把行数据搬到新名字下。不能删了等重新导入 ——
     * 导入之后 JSON blob 不再更新，重建只会读到导入那一刻的课表。
     */
    fun renameSchedule(oldId: String, newId: String)

    /** 删整张课表的数据（含锚点行，课程由外键级联删除）。 */
    fun deleteSchedule(scheduleId: String)

    // ── 作息绑定 ──────────────────────────────────────────────────────────────
    // 绑定是 `schedule.time_config_id`（NOT NULL 外键）这一列，不另存键。
    // 键名里带课表身份的话，改名要搬键、删表要删键、孤儿只能靠键名猜；作为列它跟着行走。

    /** 这张课表绑的作息配置 id。没有对应的行（未写过 / 库不可用）返回 null。 */
    fun bindingOf(scheduleId: String): Long?

    /** 写绑定，顺带补齐两行外键目标。幂等。 */
    fun setBinding(anchor: CourseRowAnchor)

    /** 全部课表的绑定。孤儿配置清理对它求差集。 */
    fun bindings(): Map<String, Long>

    // ── 课表设置 ──────────────────────────────────────────────────────────────
    // current_week / total_weeks / class_start 是 `schedule` 行上的列，prefs 键是写穿镜像
    // （备份导出遍历 prefs，没有行表的平台也靠它）。

    /** 三个设置值。行不存在返回 null。 */
    fun settingsOf(scheduleId: String): ScheduleRowSettings?

    /** 写三个设置值；传 null 的字段保持库里的值。行不存在时静默无效，调用方需先补建行。 */
    fun writeSettings(scheduleId: String, currentWeek: Int?, totalWeeks: Int?, classStart: String?)

    /** 该课表的设置是否已从 prefs 同步进行，见 `CourseRepository` 的一次性同步。 */
    fun isSettingsSynced(scheduleId: String): Boolean

    fun markSettingsSynced(scheduleId: String)

    // ── 作息配置 ──────────────────────────────────────────────────────────────
    // payload 是完整 TimeConfig JSON，一行一份；prefs 的 time_config_{id} 是写穿镜像。

    fun timeConfigs(): List<StoredTimeConfig>

    /** 写一整行：先 UPDATE，不存在再 INSERT OR IGNORE（父表不能用 REPLACE，见 .sq 注释）。 */
    fun upsertTimeConfig(row: StoredTimeConfig)

    fun deleteTimeConfigRow(id: Long)

    /** 作息是否已从 prefs 同步进表。一次性标记，整库恢复后要清。 */
    fun areTimeConfigsSynced(): Boolean

    fun markTimeConfigsSynced()

    /** 全部作废（整库恢复备份后）。下次读取从恢复出的 JSON 重新导入。 */
    fun invalidateAll()
}

/**
 * [CourseRowStore] 的注册点，启动时装一次。
 *
 * 只有「这个平台根本没有行表」（桌面 / iOS / 测试）才返回 null；注册过但拿不到就抛，
 * 不做静默降级。
 */
object CourseStorage {

    @Volatile
    private var provider: (() -> CourseRowStore?)? = null

    @Volatile
    private var resolved: CourseRowStore? = null

    /** 由平台侧在启动时调用。重复调用等价于重置。 */
    fun install(provider: () -> CourseRowStore?) {
        this.provider = provider
        resolved = null
    }

    val rows: CourseRowStore?
        get() {
            resolved?.let { return it }
            val p = provider ?: return null
            return p().also { resolved = it }
        }
}

/** 差分结果：删了哪些、更新了哪些。 */
internal data class CourseRowDiff(
    val deleted: List<String>,
    val updated: List<Course>,
)

/** `time_config` 表的一行。payload 是完整 TimeConfig JSON（含 id 与 name）。 */
data class StoredTimeConfig(val id: Long, val name: String, val payload: String)

/** `schedule` 行上的三个设置值。 */
data class ScheduleRowSettings(
    val currentWeek: Int,
    val totalWeeks: Int,
    val classStart: String?,
)

/**
 * 按 id 比对出变化的课。整对象判等，不逐字段手写 ——
 * 手写比对表在加字段时会漏，表现为「改了没写盘」。
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
 * 行表读回结果与内存对象是否等价，供导入自校验。
 * 只放行两处归一化：`selectedWeeks` 比排序后结果、`weekNotes` 是 Map（与顺序无关）。
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
