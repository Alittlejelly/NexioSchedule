package com.haooz.chedule.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 语言偏好：跟随系统，或强制指定。 */
enum class LanguagePref {
    /** 跟随系统语言（识别不了的语种回退英文）。 */
    SYSTEM,

    /** 简体中文。 */
    ZH,

    /** English。 */
    EN,
}

/** 解析后的应用语言。 */
enum class AppLang { ZH, EN }

/**
 * 应用语言中枢。
 *
 * - `pref` 是用户选择（含「跟随系统」），由宿主持久化，启动时 [init]、设置页变更时 [set]。
 * - `resolved` 是最终生效语言，底层是 Compose 快照状态：组合内读取的地方随切换自动重组，
 *   非 Composable 代码（通知、小部件、ViewModel）读取的则是当前值。
 *
 * 界面文案一律经由 [L10n]（`L(...)`）取值，不要直接缓存 `isZh` 的判断结果。
 */
object AppLanguage {

    var pref: LanguagePref by mutableStateOf(LanguagePref.SYSTEM)
        private set

    /** 当前生效语言。SYSTEM 偏好下每次读取都会重新探测系统语言。 */
    val resolved: AppLang
        get() = when (pref) {
            LanguagePref.ZH -> AppLang.ZH
            LanguagePref.EN -> AppLang.EN
            LanguagePref.SYSTEM ->
                if (systemLanguageTag().startsWith("zh", ignoreCase = true)) AppLang.ZH else AppLang.EN
        }

    /** 当前是否中文界面。 */
    val isZh: Boolean get() = resolved == AppLang.ZH

    /** 启动时恢复持久化的偏好；未初始化前按「跟随系统」。 */
    fun init(saved: LanguagePref) {
        pref = saved
        syncCoreMirror()
    }

    /** 用户切换语言；宿主负责把返回前的新值持久化。 */
    fun set(newPref: LanguagePref) {
        pref = newPref
        syncCoreMirror()
    }

    /** core 层（通知/小部件/数据类显示变体）读不到 Compose 状态，用镜像同步结果。 */
    private fun syncCoreMirror() {
        com.haooz.chedule.data.DisplayLang.isZh = resolved == AppLang.ZH
    }
}
