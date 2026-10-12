@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.data

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile

/**
 * [AppFile] 的 iOS 实现：包装 `NSFileManager`（`java.io.File` 在 Kotlin/Native 上不存在）。
 *
 * 与 [FileAppFile]（Android / JVM 版）**逐条对齐**，别凭直觉改语义：
 *
 * | 行为 | `FileAppFile` | 这里 |
 * |---|---|---|
 * | [writeBytes] | 自动 `parentFile?.mkdirs()` | 自动建父目录（`withIntermediateDirectories = true`） |
 * | [readBytes] 文件缺失 | 抛 `FileNotFoundException` | `contentsAtPath` 返回 null → **抛**（不是返回空数组） |
 * | [exists] | 文件与目录都算 true | 同（`fileExistsAtPath` 不区分类型） |
 * | [resolve] | `File(parent, relative)` | 字符串按 `/` 拼接 |
 *
 * ## 路径基准（别选错）
 *
 * [AppFiles.init] 的 `root` 应传 **Documents**，对应 Android 的 `context.filesDir`，
 * 用 [defaultDocumentsDir] 取。
 * ⚠ **别换成 Caches** —— 系统在存储空间不足时会清空它，而这里存的是课程数据与
 * 学校索引；也**别换成 Application Support / tmp**（tmp 同样会被系统清理）。
 *
 * ⚠ 落盘路径必须逐字保持：`repo/index/school_index.pb` / `repo/schools/resources`。
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
        // atomically = true：先写临时文件再 rename —— 与 Android 的 writeBytes 一样，
        // 读到中途崩溃也不会留下半截内容（读到的是旧值或新值，不会是中间态）。
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
 * 建目录（含父级）。已存在时静默成功 —— 与 `File.mkdirs()` 一致：
 * 目录已存在返回 false 也不该抛，否则「反复写同一个目录」的调用点会崩。
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
 * iOS 沙盒的 Documents 目录 —— 对应 Android 的 `context.filesDir`。
 *
 * 用法（iOS 入口，越早越好；对应 `NexioApplication.onCreate` 的 `AppFiles.init`）：
 * ```kotlin
 * AppFiles.init(
 *     root = NsFileAppFile(defaultDocumentsDir()),
 *     readAsset = { path -> /* NSBundle 读内置资源 */ },
 * )
 * ```
 */
fun defaultDocumentsDir(): String =
    (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String)
        ?: error("无法定位 Documents 目录")
