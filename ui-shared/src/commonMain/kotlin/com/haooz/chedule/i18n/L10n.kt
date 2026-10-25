package com.haooz.chedule.i18n

/**
 * 双语字符串。创建时同时给出中文与英文文案，调用 [invoke] 时按当前语言取值。
 *
 * 在组合内读取会跟踪 [AppLanguage] 的快照状态，语言切换后引用它的 UI 自动重组；
 * 非 Composable 上下文（ViewModel、通知、分享编解码等）同样按调用时刻的语言取值。
 *
 * 占位符：`%s` / `%d` 按出现顺序替换为 [invoke] 的实参；无占位符时不需要传参。
 *
 * ```
 * val ok = L("确定", "OK")
 * val weekN = L("第%d周", "Week %d")
 * Text(ok())
 * Text(weekN(currentWeek))
 * ```
 */
class L10n(private val zh: String, private val en: String) {

    operator fun invoke(vararg args: Any?): String {
        val template = if (AppLanguage.isZh) zh else en
        if (args.isEmpty()) return template
        val sb = StringBuilder(template.length + 24)
        var argIndex = 0
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '%' && i + 1 < template.length && (template[i + 1] == 's' || template[i + 1] == 'd') && argIndex < args.size) {
                sb.append(args[argIndex++]?.toString() ?: "")
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}

/** 创建双语字符串，见 [L10n]。 */
fun L(zh: String, en: String): L10n = L10n(zh, en)
