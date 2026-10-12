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
 * iOS 侧「外部分享进来一个文件」的接收器。
 *
 * ## 与 Android 的对应关系
 *
 * Android 是 `MainActivity.onNewIntent` 解析 `Intent`，摊平成
 * `shareIntentUri` + `shareIntentVersion` 两个字段，`CourseScheduleApp` 组合时读它们。
 * iOS 没有 Intent —— 文件是通过 SwiftUI 的 `.onOpenURL` 送进来的
 * （用户在「文件」App / 其它 app 里点「用 Nexio课程表 打开」）。
 *
 * ## 为什么用 Compose 的 `mutableStateOf`
 *
 * Swift 调 [onOpenURL] 时不在 Compose 的组合作用域里。用普通字段的话，值虽然更新了，
 * **Compose 不会重组合** → 弹窗永远不出来（表现为「点了打开没反应」）。
 * 用 `mutableStateOf` 存，`CourseScheduleApp` 组合期读它时就自动订阅了。
 *
 * `version` 单调递增：同一个 URL 再次分享时靠它触发 `LaunchedEffect` 重跑
 * （与 Android 的 `shareIntentVersion` 同一套语义）。
 */
object IosSharedFileReceiver {

    /** 待处理的外部分享（null = 无）。Swift 的 `.onOpenURL` 写，Compose 读。 */
    var pending: SharedFileIntent? by mutableStateOf(null)
        private set

    private var counter: Int = 0

    /**
     * Swift 侧 `.onOpenURL` 入口。
     *
     * @param url 打开的文件 URL（`file://...`）。传字符串而不是 `NSURL`，
     *   免得把 Foundation 类型带进 Swift 的调用签名。
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
     * 与文件选择器同样要走**安全作用域**：外部分享进来的文件在 app 沙箱外，
     * 不 `startAccessingSecurityScopedResource()` 会读到 null（静默失败）。
     * 失败时这里已经提示过，页面不再重复报错（与该回调的约定一致）。
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
