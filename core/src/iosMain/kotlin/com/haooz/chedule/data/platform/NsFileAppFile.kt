@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.data.platform
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile

/**
 * [AppFile] 的 iOS 实现：包装 `NSFileManager`（Kotlin/Native 无 `java.io.File`）。
 * 语义与 [FileAppFile]（Android / JVM 版）逐条对齐：[writeBytes] 自动建父目录，
 * [readBytes] 文件缺失**抛异常**（不是返回空数组），[resolve] 按 `/` 拼接。
 *
 * ⚠ [AppFiles.init] 的 `root` 传 **Documents**（用 [defaultDocumentsDir] 取），
 * 别换成 Caches / Application Support / tmp —— 系统在空间不足时会清空这些目录。
 * ⚠ 落盘路径必须逐字保持：`repo/index/school_index.pb` / `repo/schools/resources`，
 * 抄错一个字符 = 内置学校索引首启引导静默失效（见 `SchoolRepository`）。
 */
class NsFileAppFile(private val filePath: String) : AppFile {

    override val path: String get() = filePath

    override fun exists(): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(filePath)

    override fun readBytes(): ByteArray =
        NSFileManager.defaultManager.contentsAtPath(filePath)?.toByteArray()
            ?: error("文件不存在或读取失败: $filePath")

    override fun writeBytes(bytes: ByteArray) {
        parentOf(filePath)?.let { createDirectory(it) }
        // atomically = true：先写临时文件再 rename，中途崩溃不会留半截内容（读到旧值或新值）
        if (!bytes.toNSData().writeToFile(filePath, atomically = true)) {
            error("文件写入失败: $filePath")
        }
    }

    override fun mkdirs() {
        createDirectory(filePath)
    }

    override fun delete(): Boolean =
        NSFileManager.defaultManager.removeItemAtPath(filePath, null)

    override fun resolve(relative: String): AppFile = NsFileAppFile("$filePath/$relative")

    override fun toString(): String = "NsFileAppFile($filePath)"
}

/**
 * 建目录（含父级）。已存在时静默成功（与 `File.mkdirs()` 一致）：
 * 返回 false 也不该抛，否则「反复写同一目录」的调用点会崩。
 */
private fun createDirectory(path: String) {
    NSFileManager.defaultManager.createDirectoryAtPath(
        path,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
}

/** 取父目录路径；已在根目录（无父级）时返回 null。 */
private fun parentOf(path: String): String? {
    val trimmed = path.trimEnd('/')
    val idx = trimmed.lastIndexOf('/')
    return if (idx > 0) trimmed.substring(0, idx) else null
}

/**
 * iOS 沙盒的 Documents 目录 —— 对应 Android 的 `context.filesDir`，用作 [AppFiles.init] 的 `root`。
 */
fun defaultDocumentsDir(): String =
    (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String)
        ?: error("无法定位 Documents 目录")
