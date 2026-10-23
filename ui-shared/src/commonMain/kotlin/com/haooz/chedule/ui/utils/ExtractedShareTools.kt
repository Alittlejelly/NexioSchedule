package com.haooz.chedule.ui.utils

/**
 * 教务通用工具中已提取到课表导入页的适配器 ID。
 *
 * 2026-10-10 从 `ScheduleImport.kt` 抽成独立文件迁入 `:ui-shared`：它本身只是几个常量，却和 WebView / Clipboard / Gson / Handler 混在一个 821 行文件里，导致 `AdapterSelectionScreen` / `SchoolSelectionScreen` 这两个平台中立页面反向依赖整个 Android 专有导入模块，卡住迁移。
 *
 * ⚠ 值本身是与云端脚本仓库的契约（`adapterId` 匹配），不要改。
 */
object ExtractedShareTools {
    const val WAKEUP = "WakeUp"
    const val STARLINK = "StarLink"
    val ADAPTER_IDS = setOf(WAKEUP, STARLINK)

    const val GLOBAL_TOOLS_SCHOOL_ID = "GLOBAL_TOOLS"
    const val GLOBAL_TOOLS_RESOURCE = "GLOBAL_TOOLS"
    const val WAKEUP_JS = "wake_up.js"
}
