/**
 * 全局文字变化动效的开关与并发预算。
 *
 * 开关是「组合期可读」的 Compose 状态：关掉之后所有 [Text] 包装器直接返回原始 Text，
 * 不建 Box、不建 graphicsLayer、不读动画进度 —— 也就是零代价。
 */
package com.haooz.chedule.ui.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.haooz.chedule.data.AppStorage

object TextMotionSettings {

    const val KEY_TEXT_MOTION = "text_motion_enabled"
    private const val PREFS_NAME = "app_preferences"

    /** 默认开启：全局文字动效是本功能的默认形态，用户可在设置里关掉。 */
    private const val DEFAULT_ENABLED = true

    /** 当前是否启用。组合期可读，写入走 [apply] / [load]。 */
    var enabled by mutableStateOf(DEFAULT_ENABLED)
        private set

    fun apply(enabled: Boolean) {
        this.enabled = enabled
    }

    /** 启动时从偏好载入，走 [AppStorage] 按名字取（与其它偏好同一个 `app_preferences` 文件）。 */
    fun load() {
        enabled = AppStorage.store(PREFS_NAME).getBoolean(KEY_TEXT_MOTION, DEFAULT_ENABLED)
    }
}

/**
 * 同时进行中的文字动效配额。
 *
 * 除了「一个字变了」，还存在「整屏文字同帧全变」的场景（切周、换课表、切主题 / 语言）。
 * 那种时候每个文字都要建一份离屏模糊层，几百个叠在一帧里必然掉帧。这里给一个硬上限：
 * 超出配额的直接换字、不播动效 —— 宁可少几处动效，也不要卡一帧。
 *
 * 计数器只在主线程读写，不做并发保护。
 */
internal object TextMotionBudget {

    /** 同一时刻最多允许多少处文字做动效。 */
    private const val MAX_CONCURRENT = 48

    private var active = 0

    fun tryAcquire(): Boolean {
        if (active >= MAX_CONCURRENT) return false
        active += 1
        return true
    }

    fun release() {
        if (active > 0) active -= 1
    }
}
