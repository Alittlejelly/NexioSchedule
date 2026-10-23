package com.haooz.chedule.data

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 一位小数的展示格式 —— `"%.1f".format(value)` / `String.format("%.1f", value)` 的跨平台等价。
 *
 * ## 为什么需要它
 *
 * `String.format` 与它的扩展形式 `"…".format(…)` 都是 **JVM 专有**，commonMain 里没有；
 * 且它按 Locale 输出数码字形（阿拉伯语 locale 下 "1.5" 会变成非 ASCII 数字），
 * 跨平台场景本来也不该用。
 *
 * ## 与 `%.1f` 的行为对齐
 *
 * `%.1f` 是 HALF_UP 四舍五入（1.25 → "1.3"），这里先 `×10` 后 `roundToInt()` 得到同样的结果；
 * 然后手工拼整数部分与小数位，避免浮点 `toString()` 冒出 "1.2000001" 这类表示。
 * 负数也支持（`-0.5` → "-0.5"）；**不做千分位、不做补零**。
 */
fun Float.formatOneDecimal(): String {
    val scaled = (this * 10f).roundToInt()
    val sign = if (scaled < 0) "-" else ""
    val magnitude = abs(scaled)
    return "$sign${magnitude / 10}.${magnitude % 10}"
}
