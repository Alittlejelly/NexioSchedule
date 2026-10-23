package com.haooz.chedule.data

import androidx.compose.ui.graphics.ImageBitmap
import com.haooz.chedule.ui.utils.decodeImageBitmapPng
import com.haooz.chedule.ui.utils.encodeImageBitmapPng

/**
 * 「切换课表」卡片快照落盘存储。
 *
 * 需落盘的原因：`GraphicsLayer.record{}` 只存引用、活不过组合销毁，
 * `ImageBitmap` 活不过进程，仅文件能留到下次启动。
 * 生命周期：保留至下次从切换课表页退出，每次退出覆盖写、进入只读。
 * 快照可能来自上次会话，首帧形变先复用它再接真实内容（形变仅数百毫秒）。
 *
 * ⚠ 文件名是数据兼容契约，勿改。
 */
object SwitchCardSnapshotStore {

    /** 快照落盘文件名；数据兼容契约，勿改。 */
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
