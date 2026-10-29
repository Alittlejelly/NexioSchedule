package com.haooz.chedule.data

import android.content.Context
import java.io.File

/** 上一版回退通道的账本文件（`storage_fallback_state.xml`）。里面没有业务数据，直接删。 */
private const val LEGACY_FALLBACK_LEDGER = "storage_fallback_state"

/**
 * 旧 SharedPreferences → `nexio.db` 的迁移：**全部搬进库，然后把旧存储彻底删掉**。
 *
 * 在 `Application.onCreate` 里同步跑，返回时旧数据已经全在库里、`shared_prefs` 目录已经不存在。
 *
 * 严格，不做兜底：
 * - 任何一个命名空间搬不动就抛异常，**一个文件都不删**，调用方（`onCreate`）直接失败；
 * - 没有迁移标记、没有重试状态、没有冷快照、没有第二套存储；下次启动只是把同一件事重做一遍。
 * - 只有「数据已经确实在库里」的实体表搬运允许失败后继续 —— 那些 blob 本身就是库里的键，
 *   行表为空时读路径会自己再导一次，删源文件不会丢数据。
 */
object LegacyStorageMigration {

    private const val TAG = "LegacyStorageMigration"

    /** 上一版键值库（`nexio_kv.db`，只在中间构建里出现过）。 */
    private const val LEGACY_KV_DB = "nexio_kv.db"

    /** 迁移 + 清空旧存储。可重复调用：旧目录已经不存在时是空操作。 */
    fun run(context: Context) {
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        // 只迁「还没迁过」的名字。迁过的名字下又出现的文件不是我们的数据 ——
        // 系统 WebView 每次启动都会重建自己的 prefs 文件，照删就是每轮都把它的状态清掉。
        val pending = legacyPrefNames(prefsDir).filterNot { SqliteStorage.isImported(context, it) }

        if (pending.isNotEmpty()) {
            // 1) 全部搬进 `pref` 表：写入 + 同事务回读逐键对拍 + 标记。失败直接抛。
            for (namespace in pending) SqliteStorage.store(context, namespace)

            // 2) 旧结构搬进实体表：课程 blob → 行表（搬成功后 blob 立刻删）、作息 → time_config、
            //    三个设置字段 → schedule 行。数据此时已在库里，个别课表失败只记日志。
            migrateEntityTables(context)

            // 3) 删掉刚迁完的文件
            var deleted = 0
            for (namespace in pending) if (deletePrefFiles(prefsDir, namespace)) deleted++
            NexioLog.w(TAG, "旧存储已全部迁入 nexio.db 并删除：${pending.size} 个命名空间 / $deleted 个文件")
        }

        // 回退账本不是业务数据，删；空目录本身也删掉（Android 需要时会自己重建）
        deletePrefFiles(prefsDir, LEGACY_FALLBACK_LEDGER)
        if (prefsDir.list().isNullOrEmpty()) prefsDir.delete()
        deleteLegacyKvDb(context)
    }

    /**
     * 把 prefs 里的旧结构搬进实体表。逐个课表做，一个课表出错不连累其他课表：
     * 课程读完即完成「blob → 行表 → 删 blob」，设置三项与作息在各自的首次访问里各同步一次。
     */
    private fun migrateEntityTables(context: Context) {
        val repository = CourseRepository()
        for (name in repository.getScheduleNames()) {
            runCatching { repository.getCoursesForSchedule(name) }
                .onFailure { NexioLog.w(TAG, "课程搬入行表失败（读路径会再导一次）：$name", it) }
            runCatching { repository.getCurrentWeek(name) }
            runCatching { repository.getTotalWeeks(name) }
            runCatching { repository.getClassStartTime(name) }
        }
        // 作息配置：prefs 的 time_config_{id} → time_config 表（一次性标记）
        runCatching { repository.getTimeConfigIds() }
            .onFailure { NexioLog.w(TAG, "作息搬入 time_config 表失败", it) }
    }

    private fun deleteLegacyKvDb(context: Context) {
        runCatching {
            val base = context.getDatabasePath(LEGACY_KV_DB)
            listOf(base, File("${base.path}-wal"), File("${base.path}-shm"), File("${base.path}-journal"))
                .forEach { if (it.exists()) it.delete() }
        }.onFailure { NexioLog.w(TAG, "删除旧键值库失败", it) }
    }
}

/**
 * 旧 SharedPreferences 留下的命名空间，**不含**回退账本（那不是业务数据，删文件即可）。
 * 排序只为日志可复现。
 *
 * ⚠ 不能只认 `<名>.xml`：Android 写偏好会留 `<名>.xml.bak`，另外实测有设备上只剩
 * `<名>.bak` 的（老版本自己做的副本）。漏了它们就等于 shared_prefs 里还留着旧数据。
 */
internal fun legacyPrefNames(prefsDir: File): List<String> =
    prefsDir.listFiles()
        ?.asSequence()
        ?.mapNotNull { it.prefNamespaceOf() }
        ?.filter { it.isNotEmpty() && it != LEGACY_FALLBACK_LEDGER }
        ?.distinct()
        ?.sorted()
        ?.toList()
        .orEmpty()

/** `x.xml` / `x.xml.bak` / `x.bak` → `x`。其余文件（`notes.txt` 之类）不是偏好文件。 */
private fun File.prefNamespaceOf(): String? {
    if (!isFile) return null
    return when {
        name.endsWith(".xml.bak") -> name.removeSuffix(".xml.bak")
        name.endsWith(".xml") -> name.removeSuffix(".xml")
        name.endsWith(".bak") -> name.removeSuffix(".bak")
        else -> null
    }
}

/** 删掉某个命名空间在 shared_prefs 下的全部落盘文件（`.xml` / `.xml.bak` / `.bak`）。 */
internal fun deletePrefFiles(prefsDir: File, namespace: String): Boolean {
    val prefixes = listOf("$namespace.xml", "$namespace.bak")
    val files = prefsDir.listFiles()
        ?.filter { file -> file.isFile && prefixes.any { file.name.startsWith(it) } }
        .orEmpty()
    var deleted = false
    files.forEach { if (it.delete()) deleted = true }
    return deleted
}
