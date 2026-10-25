package com.haooz.chedule.data

import kotlin.concurrent.Volatile

/**
 * core 层显示文案的语言镜像。
 *
 * core 不依赖 :ui-shared（compose），读不到应用语言设置；由 :ui-shared 的 AppLanguage 在
 * init/set 时把解析结果写进来。宿主进程启动（Android Application / 桌面 main / iOS bootstrap）
 * 也应尽早调一次 AppLanguage.init —— 小部件、通知等非组合上下文可能在首帧前就要取词。
 *
 * 默认 true（中文）：未初始化时输出与历史行为逐字一致。
 * 数据身份（通知去重 ID、Intent extra、回查比较）不走这里，仍用稳定形态的中文输出。
 */
object DisplayLang {
    @Volatile
    var isZh: Boolean = true
}

/**
 * core 层的最小双语取词，语义等同 `:ui-shared` 的 `L(zh, en)`。
 * 供无法依赖 UI 模块、但错误信息会直接展示给用户的数据层使用。
 */
fun displayText(zh: String, en: String): String = if (DisplayLang.isZh) zh else en
