package com.haooz.chedule.data

import androidx.compose.ui.graphics.ImageBitmap
import com.haooz.chedule.ui.utils.decodeImageBitmapPng
import com.haooz.chedule.ui.utils.encodeImageBitmapPng

/**
 * 「切换课表」卡片快照的落盘存储。
 *
 * ## 为什么必须落盘
 *
 * 进入切换课表页时要复用「上次退出录到的卡片」，但：
 * - `GraphicsLayer` 的录制内容**活不过组合销毁**（`record{}` 里 `drawLayer(x)` 只存引用）；
 * - `ImageBitmap` **活不过进程**（重进应用就没了）。
 *
 * 只有文件能留到下次启动。
 *
 * ## 生命周期
 *
 * 保持到**下次从切换课表页退出**为止：每次退出覆盖写，进入只读、绝不录制。
 * 所以这份快照可能来自上一次会话 —— 日程内容若在别处变过，首帧形变会先用它，
 * 真实内容随后接上（形变本来就只有几百毫秒）。
 *
 * ⚠ 文件名是数据兼容契约，别改。
 */
object SwitchCardSnapshotStore {

    /** 落地文件名，与存量快照兼容绑定，别改。 */
    const val FILE_NAME = "switch_card_snapshot.png"

    /** 读回上次退出固化的卡片快照；没有/读失败/未初始化都返回 null。 */
    suspend fun load(): ImageBitmap? {
        if (!AppFiles.isReady) return null
        return try {
            val file = AppFiles.root.resolve(FILE_NAME)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return null
            decodeImageBitmapPng(bytes)
        } catch (_: Exception) {
            null
        }
    }

    /** 覆盖写入卡片快照，成功返回 true。编码/落盘失败都不抛。 */
    suspend fun save(image: ImageBitmap): Boolean {
        if (!AppFiles.isReady) return false
        val bytes = encodeImageBitmapPng(image) ?: return false
        return try {
            AppFiles.root.resolve(FILE_NAME).writeBytes(bytes)
            true
        } catch (_: Exception) {
            false
        }
    }
}
