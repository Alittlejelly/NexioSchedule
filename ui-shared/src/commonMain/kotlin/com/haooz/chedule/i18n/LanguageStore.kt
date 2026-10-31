package com.haooz.chedule.i18n

import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.getStringOrNull

/**
 * 语言偏好的持久化，独立偏好文件（与 [com.haooz.chedule.data.appearance.AppearanceStore] 同一套惯例）。
 * 只存用户的 [LanguagePref] 选择；「跟随系统」的解析结果不落盘，每次启动重新探测。
 *
 * 公开给宿主模块：进程启动（Application/main/bootstrap）时先恢复一次，
 * 让小部件、通知等非组合上下文在首帧前拿到正确语言。
 */
object LanguageStore {
    private const val FILE = "nexio_i18n"
    private const val KEY_PREF = "app_language"

    private fun store() = AppStorage.store(FILE)

    fun load(): LanguagePref {
        val raw = store().getStringOrNull(KEY_PREF) ?: return LanguagePref.SYSTEM
        return runCatching { LanguagePref.valueOf(raw) }.getOrDefault(LanguagePref.SYSTEM)
    }

    fun save(pref: LanguagePref) {
        store().edit { putString(KEY_PREF, pref.name) }
    }
}
