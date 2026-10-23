@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.haooz.chedule.data.NexioLog
import com.haooz.chedule.data.showToast
import com.haooz.chedule.data.toByteArray
import com.haooz.chedule.ui.components.SharedFileIntent
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL

/**
 * iOS 侧「外部分享进来一个文件」的接收器 —— Swift 的 `.onOpenURL` 写，`CourseScheduleApp` 读。
 *
 * ⚠ 必须用 Compose 的 `mutableStateOf` 存：Swift 调 [onOpenURL] 不在组合作用域，普通字段
 * 值会更新但**不触发重组合** → 弹窗永远不出来（表现为「点了打开没反应」）。
 * `version` 单调递增，同一 URL 再次分享靠它触发 `LaunchedEffect` 重跑。
 */
object IosSharedFileReceiver {

    /** 待处理的外部分享；Swift `.onOpenURL` 写，Compose 读。 */
    var pending: SharedFileIntent? by mutableStateOf(null)
        private set

    private var counter: Int = 0

    /**
     * Swift 侧 `.onOpenURL` 入口。
     *
     * @param url 文件 URL（`file://...`）；传字符串而非 `NSURL`，避免 Foundation 类型进 Swift 签名。
     */
    fun onOpenURL(url: String) {
        if (url.isBlank()) return
        counter += 1
        pending = SharedFileIntent(url, counter)
        NexioLog.i("IosShare", "收到外部分享：$url（v$counter）")
    }

    /** 已取走该请求（`CourseScheduleApp` 的 `onShareIntentConsumed`）。 */
    fun clear() {
        pending = null
    }

    /**
     * 读该 URL 的全部文本（`CourseScheduleApp` 的 `onReadSharedFileText`）。
     *
     * ⚠ 外部分享文件在 app 沙箱外，必须走安全作用域
     * （`startAccessingSecurityScopedResource()`），否则读到 null（静默失败）。
     * 失败时此处已提示，页面不重复报错。
     */
    fun readText(urlString: String): String? {
        val url = NSURL.URLWithString(urlString) ?: run {
            showToast("导入失败: 无效的文件地址")
            return null
        }
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
}
