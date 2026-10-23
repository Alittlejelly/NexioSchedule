@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.data

import com.haooz.chedule.ui.utils.IosUiHost
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

/**
 * [BackupFileController] 的 iOS 实现 —— 备份目录扫描（`Documents/Neixo_Schedule`）、
 * 系统文件选择器（`UIDocumentPickerViewController`）、备份文件的写 / 读 / 删。
 *
 * ⚠ 备份目录用 **Documents**（[AppFiles.root]）：Info.plist 开了 `UIFileSharingEnabled`
 * 与 `LSSupportsOpeningDocumentsInPlace`，用户能在「文件」App 里直接看到并拷走备份 ——
 * 与 Android 放公共 Download 的意图一致。
 *
 * ⚠ `UIDocumentPickerViewController.delegate` 是**弱引用**：delegate 必须由本类强引用持有
 *（[pickDelegate]），否则 picker 一弹出来就被回收，回调永远不来（表现为「选完没反应」）。
 *
 * ⚠ 别和 [IosScheduleFileController] 合并：两者都是文件 I/O，但「应用自己的备份目录」
 * 与「用户选定的任意位置」生命周期、失败文案完全不同（见 KMP 迁移文档的两条 I/O 分工线）。
 */
class IosBackupFileController : BackupFileController {

    /** 强引用，见类注释 */
    private var pickDelegate: PickerDelegate? = null

    /** 备份目录：`Documents/Neixo_Schedule` */
    private val backupDirPath: String
        get() = AppFiles.root.resolve(BACKUP_DIR_NAME).path

    // ————————————————————————— 备份列表（目录扫描） —————————————————————————

    override fun listBackups(): List<BackupFileInfo> {
        val fm = NSFileManager.defaultManager
        val dir = backupDirPath
        val names = fm.contentsOfDirectoryAtPath(dir, null)?.filterIsInstance<String>()
            ?: return emptyList()
        return names
            .filter { it.endsWith(".json", ignoreCase = true) }
            .map { name -> buildInfo(fm, "$dir/$name", name) }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /** @return (展示元数据, 最后修改时间) —— 后者只用于排序 */
    private fun buildInfo(
        fm: NSFileManager,
        fullPath: String,
        fileName: String,
    ): Pair<BackupFileInfo, Double> {
        val attrs = fm.attributesOfItemAtPath(fullPath, null)
        val modifiedAt = (attrs?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970 ?: 0.0
        val sizeBytes = (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L

        val baseName = fileName.removeSuffix(".json")
        // 非全量备份：正则第 1 组为课表名，匹配不上则整个名字当课表名（与 Android 逐条一致）
        val (scheduleName, isFullBackup) = parseBackupFileBaseName(baseName)
        val scheduleCount = try {
            if (sizeBytes > MAX_BACKUP_PARSE_BYTES) 0
            else countSchedulesInBackupJson(readTextAt(fullPath) ?: "")
        } catch (_: Exception) {
            0
        }

        val info = BackupFileInfo(
            // id 用绝对路径（与 Android 一致），读/删直接当路径用
            id = fullPath,
            fileName = fileName,
            type = if (isFullBackup) "全部备份" else "单独课表",
            scheduleName = scheduleName,
            scheduleCount = scheduleCount,
            timestamp = BACKUP_NAME_REGEX.matchEntire(baseName)
                ?.let { formatBackupTimestamp(it.groupValues[2]) }
                ?: "未知时间",
            size = formatBackupSize(sizeBytes),
            isFullBackup = isFullBackup,
        )
        return info to modifiedAt
    }

    // ————————————————————————— 系统选择器导入 —————————————————————————

    override fun pickAndReadBackup(onResult: (json: String?, displayName: String?) -> Unit) {
        val picker = UIDocumentPickerViewController(
            // 放开类型：各来源对 .json 的 UTI 上报不一致，限定会选不到文件；
            // 合法性由 parseBackupPayload 把关（与 Android 放开 mime 同理）
            forOpeningContentTypes = listOf(UTTypeItem),
            // 系统先拷一份到沙盒再交给我们 —— 免去 security-scoped 的权限纠缠
            asCopy = true,
        )
        val delegate = PickerDelegate(
            onPicked = { urls ->
                val url = urls.firstOrNull()
                if (url == null) {
                    onResult(null, null)
                } else {
                    // asCopy 后文件已在沙盒内且不超 8 MB，同步读的代价可接受
                    val json = readPickedFile(url)
                    onResult(json, url.lastPathComponent)
                }
            },
            onCancelled = { onResult(null, null) },
        )
        picker.delegate = delegate
        pickDelegate = delegate

        val host = IosUiHost.topViewController()
        if (host == null) {
            pickDelegate = null
            showToast("导入失败: 无法打开文件选择器")
            onResult(null, null)
            return
        }
        host.presentViewController(picker, animated = true, completion = null)
    }

    /** 读用户选中的文件（含 8 MB 上限）。失败时宿主内部已 toast，调用方按「取消」处理。 */
    private fun readPickedFile(url: NSURL): String? {
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            val data = NSData.dataWithContentsOfURL(url)
            if (data == null) {
                showToast("无法识别备份文件：读取失败")
                return null
            }
            if (data.length > MAX_BACKUP_PARSE_BYTES.toULong()) {
                showToast("无法识别备份文件：超过 8 MB 限制")
                return null
            }
            return data.toByteArray().decodeToString()
        } catch (e: Exception) {
            showToast("无法识别备份文件：${e.message}")
            return null
        } finally {
            if (scoped) url.stopAccessingSecurityScopedResource()
        }
    }

    // ————————————————————————— 写 / 读 / 删 —————————————————————————

    override fun saveBackupFile(fileName: String, json: String): Boolean {
        // AppFile.writeBytes 自动建父目录，失败抛异常（接口约定）
        AppFiles.root.resolve(BACKUP_DIR_NAME).resolve(fileName)
            .writeBytes(json.encodeToByteArray())
        return true
    }

    override fun readBackupFile(id: String): String? {
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(id)) return null
        val sizeBytes = (fm.attributesOfItemAtPath(id, null)?.get(NSFileSize) as? NSNumber)
            ?.longLongValue ?: 0L
        if (sizeBytes > MAX_BACKUP_PARSE_BYTES) {
            throw IllegalStateException("备份文件超过 8 MB 限制")
        }
        return readTextAt(id)
    }

    override fun deleteBackupFile(id: String): Boolean =
        NSFileManager.defaultManager.removeItemAtPath(id, null)

    // ————————————————————————— 辅助 —————————————————————————

    private fun readTextAt(path: String): String? =
        NSFileManager.defaultManager.contentsAtPath(path)?.toByteArray()?.decodeToString()

    /**
     * 备份文件名时间戳 `yyyyMMdd_HHmmss` → 展示用 `yyyy-MM-dd HH:mm:ss`。
     * 文件名存的是本地时间，直接切字符串、无需时区换算（与 Android `SimpleDateFormat` 同结果）。
     */
    private fun formatBackupTimestamp(raw: String): String {
        if (raw.length != 15 || raw[8] != '_') return "未知时间"
        return "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)} " +
            "${raw.substring(9, 11)}:${raw.substring(11, 13)}:${raw.substring(13, 15)}"
    }

    /** 选择器回调桥。delegate 由外层强引用持有。 */
    private class PickerDelegate(
        private val onPicked: (List<NSURL>) -> Unit,
        private val onCancelled: () -> Unit,
    ) : NSObject(), UIDocumentPickerDelegateProtocol {

        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) {
            onPicked(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onCancelled()
        }
    }
}
