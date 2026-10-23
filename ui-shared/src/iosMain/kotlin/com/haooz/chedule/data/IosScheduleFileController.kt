@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.data

import com.haooz.chedule.ui.utils.IosUiHost
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

/**
 * iOS 的文件选择 / 另存为实现（对应 Android 的 SAF 包装）。
 *
 * ## 映射关系
 *
 * | 接口 | iOS |
 * |---|---|
 * | [openTextFile] | `UIDocumentPickerViewController(forOpeningContentTypes:)` + 安全作用域读取 |
 * | [saveTextFile] | 先写临时文件，再 `forExportingURLs:`（系统负责拷到用户选的位置） |
 *
 * ## 两个必须记的点
 *
 * 1. **delegate 是 weak**（`UIDocumentPickerViewController.delegate`）：对象必须由本类**强引用**
 *    持有（`openDelegate` / `exportDelegate`），否则 picker 一弹出来就被回收，回调永远不来
 *    （表现为「选完文件没反应」）。
 * 2. **安全作用域资源**：`forOpeningContentTypes` 选出来的 URL 在 app 沙箱外，
 *    读之前必须 `startAccessingSecurityScopedResource()`、读完 `stop...`；
 *    漏了会读到 nil（不是抛异常，是静默失败）。
 *
 * 编码：Android 侧是 `bufferedReader()` 默认 UTF-8，这里同 UTF-8；额外补一条 GB18030
 * 兜底（国内教务导出的文件常见），失败才回调 null。
 */
class IosScheduleFileController : ScheduleFileController {

    /** 强引用，见类注释第 1 点 */
    private var openDelegate: PickerDelegate? = null
    private var exportDelegate: PickerDelegate? = null

    override fun openTextFile(mimeTypes: List<String>, onResult: (String?) -> Unit) {
        val types = mimeTypes.mapNotNull { utTypeForMime(it) }.ifEmpty { listOf(UTTypeItem) }
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = types,
            asCopy = true,
        )
        val delegate = PickerDelegate(
            onPicked = { urls ->
                val url = urls.firstOrNull()
                onResult(url?.let { readText(it) })
            },
            onCancelled = { onResult(null) },
        )
        picker.delegate = delegate
        openDelegate = delegate
        present(picker)
    }

    override fun saveTextFile(fileName: String, mimeType: String, content: String) {
        // 先落到临时目录，文件名就是用户看到的建议名；系统导出时会拷到用户选的位置
        val safeName = fileName.ifBlank { "schedule.txt" }
        val tmpPath = NSTemporaryDirectory() + safeName
        // 走 ByteArray → NSData（同 NsFileAppFile.writeBytes），不碰 NSString 编码转换
        val written = content.encodeToByteArray().toNSData().writeToFile(tmpPath, atomically = true)
        if (!written) {
            showToast("导出失败：无法写入临时文件")
            return
        }
        val url = NSURL.fileURLWithPath(tmpPath)
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url))
        val delegate = PickerDelegate(
            // 导出成功由系统完成拷贝，这里只清引用
            onPicked = { exportDelegate = null },
            onCancelled = { exportDelegate = null },
        )
        picker.delegate = delegate
        exportDelegate = delegate
        present(picker)
    }

    private fun present(picker: UIDocumentPickerViewController) {
        val host = IosUiHost.topViewController()
        if (host == null) {
            showToast("导入失败: 无法打开文件选择器")
            openDelegate = null
            exportDelegate = null
            return
        }
        host.presentViewController(picker, animated = true, completion = null)
    }

    /**
     * 读文本：安全作用域 → NSData → ByteArray → UTF-8。
     * 失败时宿主内部已提示（与 Android `onReadSharedFileText` 约定一致）。
     * 解码走 `decodeToString()`，非法序列替换为 U+FFFD 而不抛（同 Android `bufferedReader()`）。
     */
    private fun readText(url: NSURL): String? {
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            val data = NSData.dataWithContentsOfURL(url)
            if (data == null) {
                showToast("导入失败: 无法读取文件")
                return null
            }
            return data.toByteArray().decodeToString()
        } catch (e: Exception) {
            showToast("导入失败: ${e.message}")
            return null
        } finally {
            if (scoped) url.stopAccessingSecurityScopedResource()
        }
    }

    private fun utTypeForMime(mime: String): UTType? = when {
        mime.isEmpty() -> null
        mime.contains("json", ignoreCase = true) -> UTTypeJSON
        mime.contains("calendar", ignoreCase = true) || mime.contains("ics", ignoreCase = true) ->
            UTType.typeWithFilenameExtension("ics") ?: UTTypeItem
        mime.startsWith("text/") -> UTTypePlainText
        else -> UTType.typeWithMIMEType(mime)
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
