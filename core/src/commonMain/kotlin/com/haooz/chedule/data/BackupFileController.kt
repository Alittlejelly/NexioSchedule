package com.haooz.chedule.data

/**
 * 本地备份文件控制器 —— 平台能力抽象（注入模式，同 `UpdateController` / `ReminderController`）。
 *
 * 真实实现是 `:androidApp` 的 `AndroidBackupFileController`：包装备份目录扫描（公共
 * Download + 应用私有目录的 `java.io.File`）、SAF 系统文件选择器（`ActivityResultContracts
 * .OpenDocument` + `contentResolver`）、备份文件的读/写/删。这些全是 Android 专有能力
 * —— 故 `:ui-shared` 的「本地备份」页只依赖本接口，由宿主注入实现；
 * 桌面/iOS 用 [NoopBackupFileController]。
 *
 * ## 约定
 *
 * - ⚙ **不带 Context 参数**：Android 包装在构造时捕获 context（同 `AndroidDiagnosticRecorder` /
 *   `AndroidUpdateController`），接口层保持纯跨平台签名，`:core` commonMain 里没有任何
 *   android / java import。
 * - ⚙ **阻塞约定**：[listBackups] / [saveBackupFile] / [readBackupFile] / [deleteBackupFile]
 *   都是阻塞 IO（读写磁盘 + 逐个解析备份 JSON 统计课表数），commonMain 没有 `Dispatchers.IO`，
 *   调用方用 `withContext(ioDispatcher)` 包住再调（与原实现「页面用 withContext(Dispatchers.IO)
 *   包扫描/读写」一一对应）。[pickAndReadBackup] 是异步发起系统选择器，读文件在平台侧
 *   的 IO 线程完成后回到主线程回调。
 * - ⚙ **文件句柄用不透明 id 字符串**：原页面持有 `java.io.File`（列表项的恢复/删除入参），
 *   commonMain 无 File，统一改成 [BackupFileInfo.id]（Android = 文件绝对路径），
 *   Android 包装负责 String ↔ File 互转。
 *
 * ## 格式与门禁的归属（本接口**不管**格式）
 *
 * 备份 JSON 的序列化/反序列化与恢复门禁全部在 `:core` 的 `BackupCodec.kt`
 * （[encodeBackupJson] / [parseBackupPayload]）—— 页面自己调用，本接口只负责「文件在哪、
 * 怎么读写」。**不要**把格式逻辑塞进平台包装。
 */
interface BackupFileController {

    /**
     * 扫描备份目录，返回备份列表（按文件最后修改时间倒序，与原 `scanBackupFiles` 一致）。
     *
     * 会逐个读备份 JSON 统计课表数，**必须在 IO 线程调**（`withContext(ioDispatcher)`）。
     */
    fun listBackups(): List<BackupFileInfo>

    /**
     * 拉起系统文件选择器（SAF / OpenDocument）选一份备份并读出内容。
     *
     * @param onResult `(json, displayName)`：
     *   - 读取成功：`json` = 文件内容（已做 8 MB 上限校验），`displayName` = 显示名（尽力而为，可为 null）。
     *   - 用户取消：`(null, null)`。
     *   - 读取失败：Android 侧以与原页面一致的文案直接 toast
     *     （`无法识别备份文件：<原因>`），随后回调 `(null, null)`。
     */
    fun pickAndReadBackup(onResult: (json: String?, displayName: String?) -> Unit)

    /**
     * 把 [json] 写入备份目录下的 [fileName] 文件（UTF-8）。
     *
     * ⚙ 阻塞（写磁盘），需在 `withContext(ioDispatcher)` 内调用。
     * 失败时实现**抛异常**（Android 侧保留原页面的
     * `IllegalStateException("备份目录不可写：<绝对路径>")` 与写入异常），调用方 try/catch 后
     * toast `备份失败: <原因>`；返回 `true` 表示写入成功。
     */
    fun saveBackupFile(fileName: String, json: String): Boolean

    /**
     * 读取 [BackupFileInfo.id] 对应备份文件的内容（含 8 MB 上限校验）。
     *
     * ⚙ 阻塞（读磁盘），需在 `withContext(ioDispatcher)` 内调用。
     * 文件不存在返回 null；超过 8 MB / 读取异常时**抛异常**（页面按
     * `无法识别备份文件：<原因>` toast，与原实现一致）。
     */
    fun readBackupFile(id: String): String?

    /**
     * 删除 [BackupFileInfo.id] 对应的备份文件。
     *
     * ⚙ 阻塞（删磁盘文件），需在 `withContext(ioDispatcher)` 内调用。
     * 文件不存在或不可写返回 false（对应原 `File.delete()` 的返回值语义）。
     */
    fun deleteBackupFile(id: String): Boolean
}

/**
 * 一条本地备份的展示元数据（扫描目录时解析文件名 / 时间 / 大小 / 是否全量得出）。
 *
 * 字段与原 `:androidApp` `LocalBackupScreen` 里的私有 `BackupFileInfo` 一一对应，
 * 唯一变化：文件句柄 `File` 换成不透明 id [id]（Android = 文件绝对路径），
 * 交给 [BackupFileController] 的方法定位文件。
 */
data class BackupFileInfo(
    /** 平台不透明句柄（Android = 备份文件的绝对路径）；传回控制器做读/删 */
    val id: String,
    /** 文件名（含 .json 后缀，作为列表 key 与删除弹窗文案） */
    val fileName: String,
    /** 「全部备份」/「单独课表」 */
    val type: String,
    /** 单独课表备份的课表名（全量备份为 null） */
    val scheduleName: String?,
    /** 备份内包含的课表数（解析 JSON 统计；解析失败为 0） */
    val scheduleCount: Int,
    /** 由文件名时间戳解析出的展示时间（`yyyy-MM-dd HH:mm:ss`；解析不出为「未知时间」） */
    val timestamp: String,
    /** 人类可读的文件大小（B / KB / MB） */
    val size: String,
    /** 是否全量备份（文件名以「全部备份」开头） */
    val isFullBackup: Boolean,
)

// ── 扫描/读取共用的纯逻辑（跨平台；Android 包装与未来平台实现复用）──────────

/** 解析单个备份文件前允许的最大体积，避免把超大/无关文件整个读进内存（与原页面常量同值） */
const val MAX_BACKUP_PARSE_BYTES: Long = 8L * 1024 * 1024

/** 备份目录名（原页面 `BACKUP_DIR_NAME`，Android 公共 Download / 私有目录下的子目录） */
const val BACKUP_DIR_NAME: String = "Neixo_Schedule"

/** 文件名形如「<课表名>_yyyyMMdd_HHmmss」，用正则解析，不再依赖固定的后缀长度（逐字沿用原页面） */
val BACKUP_NAME_REGEX: Regex = Regex("^(.+)_(\\d{8}_\\d{6})$")

/**
 * 从备份 JSON 文本统计包含的课表数（原页面 `countSchedulesInBackup` 的纯逻辑部分）：
 * 单课表备份（含 `schedule_name` + `courses`）为 1；全量备份按
 * `schedule_<名>_courses` 键的前缀去重计数；解析失败为 0。
 */
fun countSchedulesInBackupJson(json: String): Int = try {
    // kotlinx：只读统计，plain Map 类型与原 Gson 一致
    @Suppress("UNCHECKED_CAST")
    val data = jsonToPlainValue(parseJsonObject(json)) as Map<String, Any>
    if (data.containsKey("schedule_name") && data.containsKey("courses")) {
        1
    } else {
        val prefixes = mutableSetOf<String>()
        for (key in data.keys) {
            if (key.startsWith("schedule_") && key.contains("_courses")) {
                val name = key.removePrefix("schedule_").substringBeforeLast("_courses")
                prefixes.add(name)
            }
        }
        prefixes.size
    }
} catch (_: Exception) {
    0
}

/** 人类可读的文件大小（B / KB / MB；逐字沿用原页面的三段判断） */
fun formatBackupSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}

/**
 * 解析备份文件名（不含扩展名）→（课表名 or null, 是否全量）。
 *
 * 原逻辑：以「全部备份」开头为全量；非全量时用 [BACKUP_NAME_REGEX] 取第 1 组为课表名，
 * 匹配不上则整个名字当课表名。
 */
fun parseBackupFileBaseName(baseName: String): Pair<String?, Boolean> {
    val isFullBackup = baseName.startsWith("全部备份")
    if (isFullBackup) return null to true
    val scheduleName = BACKUP_NAME_REGEX.matchEntire(baseName)?.groupValues?.get(1) ?: baseName
    return scheduleName to false
}

/**
 * 桌面/iOS 的空实现：本地文件备份是当前只有 Android 宿主实现的能力 ——
 * 非 Android 平台恒为「无备份文件」：列表空、选文件直接回调取消、写入返回 false、
 * 读不到文件、删不掉文件。
 */
object NoopBackupFileController : BackupFileController {
    override fun listBackups(): List<BackupFileInfo> = emptyList()
    // 没有系统选择器可拉起，直接回调「用户取消」（null, null），页面静默
    override fun pickAndReadBackup(onResult: (String?, String?) -> Unit) = onResult(null, null)
    // 恒 false：页面按「备份失败」toast 走原有失败分支
    override fun saveBackupFile(fileName: String, json: String): Boolean = false
    override fun readBackupFile(id: String): String? = null
    override fun deleteBackupFile(id: String): Boolean = false
}
