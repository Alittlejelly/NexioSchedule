/** 下课庆祝效果偏好（烟花开关）。跨平台：只走 :core 的 AppStorage，原 Context 参数从未使用。 */
package com.haooz.chedule.ui.screens

import com.haooz.chedule.data.AppStorage

/** 下课烟花 / 爆炸震动偏好。 */
object ClassEndEffectSettings {
    const val PREFS_NAME = "app_preferences"
    const val KEY_FIREWORKS = "class_end_fireworks"

    fun fireworksEnabled(): Boolean =
        AppStorage.store(PREFS_NAME).getBoolean(KEY_FIREWORKS, true)

    /** 爆炸震动跟随全局触感开关，无单独开关 */
    fun vibrateEnabled(): Boolean =
        AppStorage.store(PREFS_NAME).getBoolean(com.haooz.chedule.ui.theme.KEY_HAPTIC_FEEDBACK, true)

    fun setFireworks(enabled: Boolean) {
        // KeyValueStore 自带的事务编辑接口（平台无关），commonMain 可直接用
        AppStorage.store(PREFS_NAME).edit {
            putBoolean(KEY_FIREWORKS, enabled)
        }
    }
}
