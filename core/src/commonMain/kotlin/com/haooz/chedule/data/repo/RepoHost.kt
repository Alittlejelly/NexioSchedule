package com.haooz.chedule.data.repo

import com.haooz.chedule.data.model.Course
import com.haooz.chedule.data.model.TimeConfig
import com.haooz.chedule.data.storage.CourseRowAnchor
import com.haooz.chedule.data.storage.CourseRowStore
import com.haooz.chedule.data.storage.KeyValueStore
import com.haooz.chedule.data.storage.MigrationOutcome
import com.haooz.chedule.data.storage.ScheduleFolder
import com.haooz.chedule.data.storage.ScheduleRowSettings

/**
 * 拆出去的协作者眼中的仓库。
 *
 * 拆出去的部分只决定「做什么、按什么顺序做」；键名规则、缓存失效、变更通知仍留在
 * [CourseRepository] 一处实现。这些看起来是琐碎动作，但都是不能有第二份实现的东西：
 * - 键名拼错一次就是读不到用户数据，且不会报错；
 * - 协作者直接改表后如果各自负责失效，漏一处就是读到旧值；
 * - 迁移/恢复期间的写入绕过业务入口，必须显式回调。
 */
internal interface RepoHost {

    /** 当前实现的键值存储。**不要**在这里取 `AppStorage.store(...)`：那会拿到可能已换代的实例。 */
    val prefs: KeyValueStore

    /** 课程行表；宿主没接线时为 null（桌面早期形态与部分测试）。 */
    fun rowStore(): CourseRowStore?

    // ── 课表身份 ──────────────────────────────────────────────────────────────

    fun getScheduleNames(): List<String>

    fun saveScheduleNames(names: List<String>)

    fun getCurrentScheduleId(): String

    /** `schedule_{名}_`。全库只有这一处拼接规则。 */
    fun getScheduleKeyPrefix(scheduleId: String): String

    fun getScheduleFolders(): List<ScheduleFolder>

    // ── 课表级设置（`schedule` 行的三个字段）──────────────────────────────────

    fun rowSettingsOf(scheduleId: String): ScheduleRowSettings?

    fun writeRowSettings(
        scheduleId: String,
        currentWeek: Int? = null,
        totalWeeks: Int? = null,
        classStart: String? = null,
    ): Boolean

    fun teachingWeekReorganizationsRawOf(scheduleId: String): String?

    fun writeTeachingWeekReorganizationsRaw(scheduleId: String, raw: String): Boolean

    fun setSmartWeekendFor(scheduleId: String, smart: Boolean)

    fun setShowNonCurrentWeekFor(scheduleId: String, show: Boolean)

    // ── 课程 ──────────────────────────────────────────────────────────────────

    /** 只读 blob（未导入行表时用）。返回 null 表示没有这份数据。 */
    fun readCoursesFromBlob(scheduleId: String): List<Course>?

    /** 写课程行前必须先就位的外键目标；拿不到说明课表不存在。 */
    fun courseRowAnchor(scheduleId: String): CourseRowAnchor?

    // ── 作息 ──────────────────────────────────────────────────────────────────

    /** 该课表绑定的配置；未绑定返回 null（不要用 `getScheduleTimeConfigId` 判断，它会回退）。 */
    fun boundTimeConfigIdOrNull(scheduleId: String): Long?

    /** 旧版全局指针 `current_time_config_id`。 */
    fun resolveLegacyCurrentTimeConfigId(): Long

    fun getTimeConfig(id: Long): TimeConfig

    fun addTimeConfig(config: TimeConfig): Long

    fun setScheduleTimeConfigId(scheduleId: String, timeConfigId: Long)

    // ── 缓存、通知与迁移 ─────────────────────────────────────────────────────

    fun invalidateAllCaches()

    fun dispatchCourseChanged(action: String, courseId: String)

    fun migrateIntoDefaultFolder()

    fun migrateLegacyEntitiesOnce(): MigrationOutcome
}
