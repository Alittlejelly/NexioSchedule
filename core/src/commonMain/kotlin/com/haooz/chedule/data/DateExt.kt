package com.haooz.chedule.data

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

// kotlinx-datetime 的补齐层：补上它没有、而调用点仍在用的那几个 java.time API。

fun todayLocalDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun nowLocalTime(): LocalTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time

fun nowLocalDateTime(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** 由 epoch 毫秒取本地时间。 */
fun localDateTimeAt(epochMillis: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())

// plusDays / minusDays / plusWeeks 在 kotlinx-datetime 里不存在，只能用 DateTimeUnit 版。

fun LocalDate.plusDays(days: Long): LocalDate = plus(days, DateTimeUnit.DAY)

fun LocalDate.minusDays(days: Long): LocalDate = minus(days, DateTimeUnit.DAY)

fun LocalDate.plusWeeks(weeks: Long): LocalDate = plus(weeks, DateTimeUnit.WEEK)

fun LocalDate.minusWeeks(weeks: Long): LocalDate = minus(weeks, DateTimeUnit.WEEK)

// kotlinx-datetime 的 MIN/MAX 是 internal，取它自己支持的年份区间端点当哨兵。
// 超出这个区间 LocalDate 构造不出来，做边界哨兵足够。

val LOCAL_DATE_MIN: LocalDate = LocalDate(-999_999, 1, 1)

val LOCAL_DATE_MAX: LocalDate = LocalDate(999_999, 12, 31)

/** epoch-day 下限/上限，供 `CourseScheduleDateBounds` 做数值钳制。 */
val LOCAL_DATE_MIN_EPOCH_DAY: Long = LOCAL_DATE_MIN.toEpochDays().toLong()
val LOCAL_DATE_MAX_EPOCH_DAY: Long = LOCAL_DATE_MAX.toEpochDays().toLong()

// kotlinx-datetime 的 toEpochDays() 返回 Int、fromEpochDays() 也收 Int，
// 与 java.time 的 Long 版本不同，越界值会被静默回绕，所以统一走下面这个带检查的版本。

/** 超出可表示范围返回 null。 */
fun localDateFromEpochDays(epochDays: Long): LocalDate? =
    if (epochDays < Int.MIN_VALUE || epochDays > Int.MAX_VALUE) {
        null
    } else {
        runCatching { LocalDate.fromEpochDays(epochDays.toInt()) }.getOrNull()
    }

/** 用「下个月 1 号 - 本月 1 号」算。 */
fun LocalDate.lengthOfMonth(): Int {
    val first = LocalDate(year, monthNumber, 1)
    val next = if (monthNumber == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, monthNumber + 1, 1)
    return first.daysUntil(next)
}

// java.lang.Math 在 Kotlin/Native 上不存在，这里照抄 addExact/subtractExact 的溢出语义：溢出返回 null。

fun addExactOrNull(a: Long, b: Long): Long? {
    val result = a + b
    // 同号相加结果变号 = 溢出
    return if (((a xor result) and (b xor result)) < 0) null else result
}

fun subtractExactOrNull(a: Long, b: Long): Long? {
    val result = a - b
    // 异号相减结果变号 = 溢出
    return if (((a xor b) and (a xor result)) < 0) null else result
}

// 下面的格式化都手写，不用 kotlinx-datetime 的 Format DSL：
// 它的默认 padding 与 java 的 pattern 不完全一致，而节次时间等结果会被再次解析，差一个字符就是静默算错。

/** `HH:mm`（补零到两位）。 */
fun LocalTime.formatHourMinute(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** 解析 `HH:mm`，也接受 `H:mm`。 */
fun parseHourMinute(value: String): LocalTime? {
    val parts = value.trim().split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return runCatching { LocalTime(hour, minute) }.getOrNull()
}

/** 两个 LocalTime 相差的毫秒数（to - from）。 */
fun millisBetween(from: LocalTime, to: LocalTime): Long =
    (to.toSecondOfDay() - from.toSecondOfDay()) * 1000L

private fun pad2(value: Int): String = value.toString().padStart(2, '0')

/** `MM/dd`。 */
fun LocalDate.formatMonthDay(): String = "${pad2(monthNumber)}/${pad2(dayOfMonth)}"

/** `M/d`（不补零）。 */
fun LocalDate.formatMonthDayShort(): String = "$monthNumber/$dayOfMonth"

/** `yyyy年M月d日`。年补零到 4 位。 */
fun LocalDate.formatChineseDate(): String =
    "${year.toString().padStart(4, '0')}年${monthNumber}月${dayOfMonth}日"

/** `yyyy/MM/dd`。 */
fun LocalDate.formatSlashDate(): String =
    "${year.toString().padStart(4, '0')}/${pad2(monthNumber)}/${pad2(dayOfMonth)}"

/** `yyyyMMdd_HHmmss`。 */
fun LocalDateTime.formatCompactStamp(): String =
    "${year.toString().padStart(4, '0')}${pad2(monthNumber)}${pad2(dayOfMonth)}" +
        "_${pad2(hour)}${pad2(minute)}${pad2(second)}"

/** `yyyy-MM-dd HH:mm:ss`。 */
fun LocalDateTime.formatDisplayDateTime(): String =
    "${year.toString().padStart(4, '0')}-${pad2(monthNumber)}-${pad2(dayOfMonth)} " +
        "${pad2(hour)}:${pad2(minute)}:${pad2(second)}"

/** `yyyy-MM-dd HH:mm`（不带秒）。设置页「上次操作」文案用这个。 */
fun LocalDateTime.formatDateTimeMinute(): String =
    "${year.toString().padStart(4, '0')}-${pad2(monthNumber)}-${pad2(dayOfMonth)} " +
        "${pad2(hour)}:${pad2(minute)}"

/** ICS 的 `DTSTART` / `DTEND` 值：`yyyyMMdd'T'HHmmss`，分隔符是字面量 T。 */
fun LocalDateTime.formatIcsTimestamp(): String =
    "${year.toString().padStart(4, '0')}${pad2(monthNumber)}${pad2(dayOfMonth)}" +
        "T${pad2(hour)}${pad2(minute)}${pad2(second)}"

/** 解析 `yyyyMMdd_HHmmss`；长度或数字不合法时返回 null。 */
fun parseCompactStamp(value: String): LocalDateTime? {
    if (value.length != 15 || value[8] != '_') return null
    val digits = value.substring(0, 8) + value.substring(9)
    if (digits.any { !it.isDigit() }) return null
    val year = digits.substring(0, 4).toIntOrNull() ?: return null
    val month = digits.substring(4, 6).toIntOrNull() ?: return null
    val day = digits.substring(6, 8).toIntOrNull() ?: return null
    val hour = digits.substring(8, 10).toIntOrNull() ?: return null
    val minute = digits.substring(10, 12).toIntOrNull() ?: return null
    val second = digits.substring(12, 14).toIntOrNull() ?: return null
    return runCatching { LocalDateTime(year, month, day, hour, minute, second) }.getOrNull()
}

fun LocalDate.atStartOfDay(): LocalDateTime = LocalDateTime(this, LocalTime(0, 0))

val LocalDateTime.localDate: LocalDate get() = date

/** java.time 的 WEEKS.between 对负数向零截断，整数除法同样向零截断，等价。 */
fun weeksBetween(from: LocalDate, to: LocalDate): Int = from.daysUntil(to) / 7
