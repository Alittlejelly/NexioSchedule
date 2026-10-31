package com.haooz.chedule.data

import com.haooz.chedule.data.CourseRepository.Companion.DEFAULT_FOLDER_ID
import com.haooz.chedule.data.CourseRepository.Companion.DEFAULT_FOLDER_NAME
import com.haooz.chedule.data.CourseRepository.Companion.KEY_CLASS_START_TIME
import com.haooz.chedule.data.CourseRepository.Companion.KEY_COURSES
import com.haooz.chedule.data.CourseRepository.Companion.KEY_CURRENT_WEEK
import com.haooz.chedule.data.CourseRepository.Companion.KEY_DEFAULT_FOLDER_MIGRATED
import com.haooz.chedule.data.CourseRepository.Companion.KEY_SCHEDULE_FOLDERS
import com.haooz.chedule.data.CourseRepository.Companion.KEY_SHOW_NON_CURRENT_WEEK
import com.haooz.chedule.data.CourseRepository.Companion.KEY_SMART_WEEKEND
import com.haooz.chedule.data.CourseRepository.Companion.KEY_TEACHING_WEEK_REORGANIZATIONS
import com.haooz.chedule.data.CourseRepository.Companion.KEY_TIME_CONFIG_IDS
import com.haooz.chedule.data.CourseRepository.Companion.KEY_TOTAL_WEEKS
import com.haooz.chedule.data.CourseRepository.Companion.SCHEDULE_KEY_PREFIX
import com.haooz.chedule.data.CourseRepository.Companion.SCHEDULE_TIME_CONFIG_PREFIX
import com.haooz.chedule.data.CourseRepository.Companion.TIME_CONFIG_PREFIX

/**
 * 旧版本（1.6.7 及更早）的本地数据 → 实体表的一次性搬迁。
 *
 * 这个文件的存在意义就是「把过去的存储布局清干净」：
 * - 只读旧键，只写新表；
 * - 搬完删源（`purgePrefKeys`），之后库里不该再有第二份数据；
 * - 跑在启动 / 恢复备份时，**没有界面在等它**，所以失败宁可重跑也不许半套。
 *
 * 读路径不经过这里：唯一例外是 `CourseRepository.loadCourses` 把 `schedule_*_courses`
 * 当作「待导入源」，那段属于读路径，留在仓库里。
 *
 * ⚠ 幂等是硬要求：`ENTITY_MIGRATION_VERSION` 每 +1，所有用户都会重跑一遍。
 *   每一步都必须「已经是目标状态就跳过」，尤其是 [giveOwnTimeConfig]。
 */
internal class LegacyEntityMigration(
    private val prefs: KeyValueStore,
    private val rows: CourseRowStore?,
    private val host: Host,
) {

    /**
     * 迁移需要仓库提供的能力。
     *
     * 仓库那边持有缓存、键名前缀规则和「锚点」这些不变量；这里只负责决定「搬什么、先搬谁」。
     */
    internal interface Host {
        fun scheduleNames(): List<String>

        /** `schedule_{名}_`，键名规则只由仓库实现一次。 */
        fun scheduleKeyPrefix(scheduleId: String): String

        /** 走读取口：顺带触发一次「blob → 行表」导入并回填缓存。 */
        fun loadCourses(scheduleId: String): List<Course>

        fun timeConfig(id: Long): TimeConfig

        fun addTimeConfig(config: TimeConfig): Long

        /** 旧版全局指针 `current_time_config_id`。 */
        fun legacyGlobalTimeConfigId(): Long

        fun anchorFor(scheduleId: String, timeConfigId: Long): CourseRowAnchor?

        /** 行不存在时补建锚点行（锚点里带着三个设置字段的旧值）。 */
        fun createScheduleRowIfAbsent(scheduleId: String)

        /** 刚写过 `time_config` 表：作息缓存整体作废。 */
        fun onTimeConfigsWritten()

        /** 刚直接写过 `schedule.time_config_id`：绑定缓存整体作废。 */
        fun onBindingsWritten()

        fun folders(): List<ScheduleFolder>

        fun saveFolders(folders: List<ScheduleFolder>)
    }

    /** 全量搬迁。没接线行表的宿主返回 [MigrationOutcome.NoRowStore]。 */
    fun run(): MigrationOutcome {
        val rows = rows ?: return MigrationOutcome.NoRowStore
        if (runCatching { rows.isFullyMigrated() }.getOrDefault(false)) return MigrationOutcome.AlreadyDone

        val names = host.scheduleNames()
        var failed = migrateEntities(names, rows)
        if (failed.isNotEmpty()) {
            // 第一轮失败基本只有两种：坏 JSON（已留档）、锚点缺失（已自愈），第二轮几乎必然过。
            NexioLog.w(TAG, "迁移第一轮未完成 ${failed.size} 张，立即重试：$failed")
            failed = migrateEntities(failed, rows)
        }

        if (failed.isNotEmpty()) {
            NexioLog.e(TAG, "迁移仍有 ${failed.size} 张未完成，下次启动重试：$failed")
            return MigrationOutcome.Incomplete(failed)
        }
        runCatching { rows.markFullyMigrated() }
            .onFailure { NexioLog.e(TAG, "写迁移标记失败，下次启动会重跑", it) }
        // 全部进表之后才删盘上的副本：先删后写等于删用户数据
        purgePrefKeys(names)
        NexioLog.w(TAG, "迁移完成：${names.size} 张课表已全部落在实体表")
        return MigrationOutcome.Done(names.size)
    }

    /**
     * 首次使用时把现有课表收进「默认文件夹」，只跑一次（标记 `default_folder_migrated`）。
     *
     * 恢复备份会清掉标记再跑：已有同 id 文件夹必须**并入**而不是再 add，
     * 否则 LazyColumn 的 key 冲突会闪退。
     */
    fun migrateIntoDefaultFolder() {
        if (prefs.getBoolean(KEY_DEFAULT_FOLDER_MIGRATED, false)) return
        val names = host.scheduleNames()
        val folders = host.folders().toMutableList()
        val ungrouped = if (folders.isEmpty()) names
        else names.filter { name -> folders.none { name in it.schedules } }
        if (ungrouped.isNotEmpty()) {
            val index = folders.indexOfFirst { it.id == DEFAULT_FOLDER_ID }
            if (index >= 0) {
                val existing = folders[index]
                val merged = (existing.schedules + ungrouped).distinct()
                folders[index] = existing.copy(schedules = names.filter { it in merged })
            } else {
                folders.add(ScheduleFolder(DEFAULT_FOLDER_ID, DEFAULT_FOLDER_NAME, ungrouped))
            }
            host.saveFolders(folders)
        }
        prefs.edit { putBoolean(KEY_DEFAULT_FOLDER_MIGRATED, true) }
    }

    // ── 搬迁顺序 ──────────────────────────────────────────────────────────────
    // 作息 → 绑定 → 课程 → 课表级设置 → 文件夹。
    // 前两步是课程的前置条件（`schedule.time_config_id` 是 NOT NULL 外键），顺序不能换。

    /** 搬一批课表，返回**没进表**的那些。以行表标记为准，不信返回值。 */
    private fun migrateEntities(names: List<String>, rows: CourseRowStore): List<String> {
        if (!migrateTimeConfigs(rows)) {
            NexioLog.e(TAG, "迁移：作息搬入 time_config 表失败，本轮作废")
            return names
        }
        host.onTimeConfigsWritten()

        // 绑定先于课程：课程导入会建 schedule 行，锚点里的 time_config_id 一旦回退到
        // 共用那套，两张课表就共用一行了（改一套另一套跟着变）。
        migrateBindings(names, rows)

        val failed = names.filter { name ->
            // 走读取口，顺带把 blob 导进表（导入成功会自己删 blob）。
            // 三个设置字段由锚点带进行，读它们没有副作用，所以这里不碰。
            runCatching { host.loadCourses(name) }
                .onFailure { NexioLog.e(TAG, "迁移：$name 课程读取异常", it) }
            !runCatching { rows.isImported(name) }.getOrDefault(false)
        }.toMutableSet()

        // prefs.all() 每次都是全量复制，按课表循环读会白复制 N 遍
        val snapshot = prefs.all()
        names.forEach { name ->
            if (!migrateDisplaySettings(name, snapshot, rows)) failed += name
            if (!migrateReorganizations(name, rows)) failed += name
        }
        // 文件夹是全局一份：搬不动就等于整轮没完成
        if (!migrateFolders(rows)) return names
        return failed.toList()
    }

    /**
     * `time_config_ids` + `time_config_{id}` → `time_config` 表。
     *
     * 「本来就没有配置」算成功；单条 decode 不了就跳过（旧版当时也读不出来）。
     */
    private fun migrateTimeConfigs(rows: CourseRowStore): Boolean {
        val ids = parseLegacyConfigIds()
        if (ids.isEmpty()) return true
        return runCatching {
            ids.forEach { id ->
                val raw = prefs.getStringOrNull("$TIME_CONFIG_PREFIX$id") ?: return@forEach
                val config = parseTimeConfigSnapshotOrNull(raw)
                    ?.let { TimeConfig.sanitize(id, it) } ?: return@forEach
                rows.upsertTimeConfig(StoredTimeConfig(id, config.name, encodeTimeConfig(config)))
            }
        }.onFailure { NexioLog.e(TAG, "迁移：作息写表失败", it) }.isSuccess
    }

    /** `time_config_ids` 有两种历史写法：逗号分隔 / JSON 数组。 */
    private fun parseLegacyConfigIds(): List<Long> {
        val raw = prefs.getStringOrNull(KEY_TIME_CONFIG_IDS)?.trim() ?: return emptyList()
        return if (raw.startsWith("[")) {
            runCatching { decodeLongList(raw) }.getOrDefault(emptyList())
        } else {
            raw.split(",").mapNotNull { it.trim().toLongOrNull() }
        }
    }

    /** 两个显示开关 → `schedule` 列。「两个键都不存在」算成功（这张表本来就没设过）。 */
    private fun migrateDisplaySettings(
        scheduleId: String,
        snapshot: Map<String, Any?>,
        rows: CourseRowStore,
    ): Boolean {
        val prefix = host.scheduleKeyPrefix(scheduleId)
        val smart = snapshot["$prefix$KEY_SMART_WEEKEND"] as? Boolean
        val show = snapshot["$prefix$KEY_SHOW_NON_CURRENT_WEEK"] as? Boolean
        if (smart == null && show == null) return true
        host.createScheduleRowIfAbsent(scheduleId)
        return runCatching {
            smart?.let { rows.writeSmartWeekend(scheduleId, it) }
            show?.let { rows.writeShowNonCurrentWeek(scheduleId, it) }
        }.onFailure { NexioLog.e(TAG, "迁移：$scheduleId 显示开关写行失败", it) }.isSuccess
    }

    /** 教学周重组规则（整份 JSON）→ `teaching_week_reorganizations` 一行。没有键算成功。 */
    private fun migrateReorganizations(scheduleId: String, rows: CourseRowStore): Boolean {
        val raw = prefs.getStringOrNull("${host.scheduleKeyPrefix(scheduleId)}$KEY_TEACHING_WEEK_REORGANIZATIONS")
            ?: return true
        host.createScheduleRowIfAbsent(scheduleId)
        return runCatching { rows.writeTeachingWeekReorganizations(scheduleId, raw) }
            .onFailure { NexioLog.e(TAG, "迁移：$scheduleId 教学周重组规则写行失败", it) }
            .isSuccess
    }

    /** 文件夹（整份 JSON）→ `folder` 表，下标即顺序。解析失败返回 false，让整轮重跑。 */
    private fun migrateFolders(rows: CourseRowStore): Boolean {
        val json = prefs.getStringOrNull(KEY_SCHEDULE_FOLDERS) ?: return true
        if (json.isBlank()) return true
        val folders = runCatching { decodeScheduleFolders(json) }.getOrElse { e ->
            NexioLog.e(TAG, "迁移：文件夹 JSON 无法解析", e)
            return false
        }
        return runCatching {
            folders.forEachIndexed { index, folder ->
                rows.upsertFolder(StoredFolder(folder.id, folder.name, folder.schedules, index))
            }
        }.onFailure { NexioLog.e(TAG, "迁移：文件夹写表失败", it) }.isSuccess
    }

    /**
     * prefs 的 `schedule_time_config_{名}` → `schedule.time_config_id`。
     *
     * 只搬「有值 且 id 在作息表里真实存在」的：老数据可能有指向已删配置的孤儿绑定，
     * 写进去会被外键拒（RESTRICT 的目标不存在）。
     */
    private fun migrateBindings(names: List<String>, rows: CourseRowStore) {
        val all = prefs.all()
        val liveIds = runCatching { rows.timeConfigs().map { it.id }.toSet() }.getOrDefault(emptySet())
        names.forEach { name ->
            val id = (all["$SCHEDULE_TIME_CONFIG_PREFIX$name"] as? Number)?.toLong()
            if (id != null && id > 0L && id in liveIds) {
                val anchor = host.anchorFor(name, id) ?: return@forEach
                runCatching { rows.setBinding(anchor) }
                    .onFailure { NexioLog.e(TAG, "迁移：$name 绑定搬入行表失败", it) }
            } else {
                giveOwnTimeConfig(name, rows)
            }
        }
        // 绑定是直接写行的，没走仓库 setter；课程导入时又把「未绑定」读成 0 缓存住了，
        // 不作废的话本次进程内 getScheduleTimeConfigId 会一直回退到旧全局指针。
        host.onBindingsWritten()
    }

    /**
     * 给一张课表复制一套独立作息。旧版语义是「共用全局那套」，照搬过来就是两张课表共用一行。
     *
     * 幂等：已经绑过就直接返回。判断必须看**行**，不能看绑定缓存（迁移期间它是 0）。
     */
    private fun giveOwnTimeConfig(scheduleId: String, rows: CourseRowStore) {
        val alreadyBound = runCatching { rows.bindingOf(scheduleId) }.getOrNull()
        if (alreadyBound != null && alreadyBound > 0L) return
        runCatching {
            val base = host.timeConfig(host.legacyGlobalTimeConfigId())
            val newId = host.addTimeConfig(base.copy(id = 0L, name = scheduleId))
            if (newId == 0L) return@runCatching
            val anchor = host.anchorFor(scheduleId, newId) ?: return@runCatching
            rows.setBinding(anchor)
        }.onFailure { NexioLog.e(TAG, "迁移：$scheduleId 复制独立作息失败", it) }
    }

    /**
     * 迁移确认完成后，删掉 prefs 里的实体残留键。
     *
     * 只在全部课表都进表之后调用 —— 表没写完就删等于删用户数据。
     * 不删 `courses_corrupt`（坏数据留档，人工还能捞）和 `current_time_config_id`（遗留全局指针）。
     */
    private fun purgePrefKeys(names: List<String>) {
        val prefixes = names.map { host.scheduleKeyPrefix(it) }
        val removable = prefs.all().keys.filter { key ->
            when {
                key == KEY_TIME_CONFIG_IDS -> true
                key == KEY_SCHEDULE_FOLDERS -> true
                key.startsWith(TIME_CONFIG_PREFIX) ->
                    key.removePrefix(TIME_CONFIG_PREFIX).toLongOrNull() != null
                key.startsWith(SCHEDULE_TIME_CONFIG_PREFIX) -> true
                // 死键按后缀匹配、不按课表名：已删课表留下的那批同样要清（同样没有读者）
                key.startsWith(SCHEDULE_KEY_PREFIX) && DEAD_KEY_SUFFIXES.any { key.endsWith("_$it") } -> true
                else -> prefixes.any { prefix ->
                    key == "${prefix}$KEY_COURSES" ||
                        key == "${prefix}$KEY_CURRENT_WEEK" ||
                        key == "${prefix}$KEY_TOTAL_WEEKS" ||
                        key == "${prefix}$KEY_CLASS_START_TIME" ||
                        key == "${prefix}$KEY_SMART_WEEKEND" ||
                        key == "${prefix}$KEY_SHOW_NON_CURRENT_WEEK" ||
                        key == "${prefix}$KEY_TEACHING_WEEK_REORGANIZATIONS"
                }
            }
        }
        if (removable.isEmpty()) return
        prefs.edit { removable.forEach { remove(it) } }
        NexioLog.w(TAG, "迁移后已清理 ${removable.size} 个残留键")
    }

    /**
     * 1.6.7 的「课表级作息」键后缀。作息搬进 `time_config` 表后已无读者，
     * 不清就会跟着备份导出、恢复时再写回来。
     *
     * ⚠ `active_routine` 不在表里：`syncRoutineAfterDateChange` 还在读写它。
     */
    private val DEAD_KEY_SUFFIXES = setOf(
        "morning_start", "morning_start_min",
        "afternoon_start", "afternoon_start_min",
        "evening_start", "evening_start_min",
        "morning_sections", "afternoon_sections", "evening_sections",
        "class_duration", "short_break", "section_times",
        "long_break_enabled",
        "long_break_morning", "long_break_morning_section",
        "long_break_afternoon", "long_break_afternoon_section",
        "long_break_evening", "long_break_evening_section",
        "quick_time_enabled",
        "today_show_wallpaper",
    )

    private companion object {
        const val TAG = "LegacyEntityMigration"
    }
}
