package com.haooz.chedule.ui.activities.today

// 今日助手卡（纯展示与纯逻辑）。
// 天气拉取与定位在平台侧（LocationManager vs CoreLocation），算好后按参数传入；
// 下课烟花走 `fireworksOverlay` 槽位，null = 该端不渲染。
// ⚠ 文件名必须与 androidApp 的 `TodayAssistant.kt` 不同：同包同名 .kt 会让 Kotlin
//   生成同名 facade 类，dex 只留一个 → 运行期 NoSuchMethodError。

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import com.haooz.chedule.ui.components.text.material.Text as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.coursePeriodCompleted
import com.haooz.chedule.data.coursePeriodOngoing
import com.haooz.chedule.data.millisBetween
import com.haooz.chedule.data.nowLocalTime
import com.haooz.chedule.data.parseHourMinute
import com.haooz.chedule.uishared.resources.Res
import com.haooz.chedule.uishared.resources.ic_widget_location
import com.haooz.chedule.uishared.resources.icon_cloudy
import com.haooz.chedule.uishared.resources.icon_cloudy_night
import com.haooz.chedule.uishared.resources.icon_float_dirt
import com.haooz.chedule.uishared.resources.icon_heavy_rain
import com.haooz.chedule.uishared.resources.icon_heavy_snow
import com.haooz.chedule.uishared.resources.icon_ice_rain
import com.haooz.chedule.uishared.resources.icon_light_rain
import com.haooz.chedule.uishared.resources.icon_light_snow
import com.haooz.chedule.uishared.resources.icon_moderate_rain
import com.haooz.chedule.uishared.resources.icon_moderate_snow
import com.haooz.chedule.uishared.resources.icon_overcast
import com.haooz.chedule.uishared.resources.icon_sunny
import com.haooz.chedule.uishared.resources.icon_sunny_night
import com.haooz.chedule.uishared.resources.icon_t_storm
import com.kyant.backdrop.Backdrop
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 中国天气网 type（中文）→ 图标
private fun getWeatherIconRes(type: String, isNight: Boolean = false): DrawableResource = when {
    type.contains("冰雹") || type.contains("雷") -> Res.drawable.icon_t_storm
    type.contains("雾") || type.contains("霾") || type.contains("浮尘") ||
        type.contains("扬沙") || type.contains("沙尘") -> Res.drawable.icon_float_dirt
    type.contains("冻雨") -> Res.drawable.icon_ice_rain
    type.contains("雨夹雪") -> Res.drawable.icon_light_snow
    type.contains("暴雨") || type.contains("大雨") -> Res.drawable.icon_heavy_rain
    type.contains("中雨") -> Res.drawable.icon_moderate_rain
    type.contains("小雨") || type.contains("阵雨") || type.contains("雨") -> Res.drawable.icon_light_rain
    type.contains("暴雪") || type.contains("大雪") -> Res.drawable.icon_heavy_snow
    type.contains("中雪") -> Res.drawable.icon_moderate_snow
    type.contains("小雪") || type.contains("雪") -> Res.drawable.icon_light_snow
    type.contains("阴") -> Res.drawable.icon_overcast
    type.contains("多云") -> if (isNight) Res.drawable.icon_cloudy_night else Res.drawable.icon_cloudy
    type.contains("晴") -> if (isNight) Res.drawable.icon_sunny_night else Res.drawable.icon_sunny
    else -> if (isNight) Res.drawable.icon_sunny_night else Res.drawable.icon_sunny
}
/** 天气图标（普通 painterResource，无 Android 图形 API 依赖）。 */
@Composable
private fun WeatherIcon(
    resource: DrawableResource,
    size: Dp,
) {
    Image(
        painter = painterResource(resource),
        contentDescription = null,
        modifier = Modifier.size(size),
    )
}

private fun parseTime(timeStr: String): LocalTime? {
    return try {
        parseHourMinute(timeStr)
    } catch (_: Exception) {
        null
    }
}
data class WeatherData(
    val temperature: Float = Float.NaN,
    val weatherType: String = "",
    val sunset: String = "",
    val sunrise: String = "",
    val notice: String = "",
    val loaded: Boolean = false,
    val needsLocation: Boolean = false,
    val apiError: Boolean = false
) {
    fun isNight(): Boolean {
        if (sunset.isBlank() || sunrise.isBlank()) return false
        val now = nowLocalTime()
        val sunsetTime = parseTime(sunset.substringAfter("T")) ?: return false
        val sunriseTime = parseTime(sunrise.substringAfter("T")) ?: return false
        return now > sunsetTime || now < sunriseTime
    }
}

private data class CourseStatus(
    val currentCourse: Course? = null,
    val nextCourse: Course? = null,
    val timeMessage: String = "",
    /** 当前课结束时刻；跨过该时刻时触发下课庆祝 */
    val currentEnd: LocalTime? = null
)

@Composable
private fun rememberCourseStatus(
    courses: List<Course>,
    sectionTimes: Map<Int, String>
): CourseStatus {
    var status by remember { mutableStateOf(CourseStatus()) }
    // 时间区间只随 courses/sectionTimes 重算，循环内复用；保持原顺序以兼容 first-match
    val ranges = remember(courses, sectionTimes) {
        buildCourseTimeRanges(courses, sectionTimes, sortByStart = false)
    }
    LaunchedEffect(ranges) {
        while (true) {
            val now = nowLocalTime()
            // 与 generateSmartTip 同一判定：开始含、结束不含，避免整点卡在「正在上课」。
            // 跨午夜课（23:00-00:30）由 coursePeriodOngoing 按「start→午夜」段判定
            val current = ranges.find { coursePeriodOngoing(it.start, it.end, now) }
            val next = ranges.find { now < it.start }
            val message = when {
                current != null -> formatRemainingUntil(now, current.end, isCountdownToStart = false)
                next != null -> formatRemainingUntil(now, next.start, isCountdownToStart = true)
                courses.isEmpty() -> ""
                else -> ""
            }
            val newStatus = CourseStatus(current?.course, next?.course, message, current?.end)
            // 文案未变化时不写状态，避免每秒重组
            if (newStatus != status) status = newStatus
            delay(1000L.milliseconds)
        }
    }
    return status
}

/** 最后一分钟切换为秒级，其余仍用分钟口径。 */
private fun formatRemainingUntil(
    from: LocalTime,
    to: LocalTime,
    isCountdownToStart: Boolean
): String {
    val remainMillis = millisBetween(from, to, allowRollover = true)
    if (remainMillis < 0L) return if (isCountdownToStart) "已开始" else "已结束"
    if (remainMillis < 60_000L) {
        val seconds = ((remainMillis + 999L) / 1000L).toInt().coerceIn(1, 60)
        return if (isCountdownToStart) "$seconds 秒后" else "还剩 $seconds 秒"
    }
    val minutes = ceilMinutesUntil(from, to)
    return if (isCountdownToStart) formatCountdownMinutes(minutes, trailing = "后")
    else "还剩 ${formatCountdownMinutes(minutes)}"
}
private data class CourseTimeRange(
    val course: Course,
    val start: LocalTime,
    val end: LocalTime
)
private fun buildCourseTimeRanges(
    courses: List<Course>,
    sectionTimes: Map<Int, String>,
    sortByStart: Boolean = true
): List<CourseTimeRange> {
    val ranges = courses.mapNotNull { course ->
        val startStr = course.getEffectiveStartTime(sectionTimes) ?: return@mapNotNull null
        val endStr = course.getEffectiveEndTime(sectionTimes) ?: return@mapNotNull null
        val start = parseTime(startStr) ?: return@mapNotNull null
        val end = parseTime(endStr) ?: return@mapNotNull null
        CourseTimeRange(course, start, end)
    }
    return if (sortByStart) ranges.sortedBy { it.start } else ranges
}

// 向上取整，与顶部倒计时、系统岛倒计时共用同一套「剩余分钟」口径
private fun ceilMinutesUntil(from: LocalTime, to: LocalTime): Int {
    val remain = millisBetween(from, to, allowRollover = true)
    if (remain <= 0L) return 0
    return ((remain + 59_999L) / 60_000L).toInt()
}
private fun formatCountdownMinutes(minutes: Int, trailing: String = ""): String {
    val hours = minutes / 60
    val mins = minutes % 60
    val body = when {
        hours > 0 && mins > 0 -> "${hours}小时${mins}分钟"
        hours > 0 -> "${hours}小时"
        else -> "${minutes} 分钟"
    }
    return body + trailing
}

// 稳定轮换：同一钟点+时间桶内固定一条，避免每秒乱跳
private fun pickTip(variants: List<String>, seed: Int): String {
    if (variants.isEmpty()) return ""
    val idx = ((seed % variants.size) + variants.size) % variants.size
    return variants[idx]
}
private fun generateSmartTip(
    courses: List<Course>,
    tomorrowCourses: List<Course>,
    sectionTimes: Map<Int, String>,
    morningSections: Int,
    afternoonSections: Int
): String? {
    val now = nowLocalTime()
    val ranges = buildCourseTimeRanges(courses, sectionTimes)
    val tomorrowRanges = buildCourseTimeRanges(tomorrowCourses, sectionTimes)

    fun roomOf(course: Course): String =
        course.classroom.ifBlank { "教室待定" }

    fun tomorrowNote(): String {
        val hour = now.hour
        if (hour < 20) return ""
        val n = tomorrowCourses.size
        if (n == 0) return " · 明天没课，可以睡到自然醒"
        val first = tomorrowRanges.firstOrNull()?.course
        val firstLine = if (first != null) "${first.name} 开头" else "第一节待定"
        return " · 明天 $n 节，$firstLine"
    }

    if (ranges.isEmpty()) {
        // 时间残缺时避免误报「今天没课」
        if (courses.isNotEmpty()) {
            return pickTip(
                listOf(
                    "今天有 ${courses.size} 节课，但节次时间没填全，先去课表补齐再排日程",
                    "课表里有 ${courses.size} 节，时间字段是空的，补完才好倒计时",
                    "有课，没点——节次时间残缺，去课表修一下"
                ),
                seed = now.hour
            )
        }
        val hour = now.hour
        val note = tomorrowNote()
        val pool = when (hour) {
            in 5..8 -> listOf(
                "今天没课，闹钟可以往后挪一格",
                "没课的早晨，床比计划表靠谱",
                "今天空着，多睡可以，睡到中午就是另一回事了"
            )
            in 9..11 -> listOf(
                "今天没课，上午自己排，别一睁眼就交给了床",
                "上午空档，图书馆有座，你有借口",
                "没课的上午很贵，刷完就没了"
            )
            in 12..13 -> listOf(
                "下午也没课，吃饭按点，别用零食糊弄",
                "中午自由，胃要照顾好，下午才站得住",
                "没课的饭点，是你今天最实在的自由"
            )
            in 14..17 -> listOf(
                "今天没课，做点正事，也别把自己排崩",
                "下午阳光很好，适合出门，也适合焦虑——你选",
                "空着的下午，进度条容易一直是 0%"
            )
            in 18..21 -> listOf(
                "今天没课，适当放空，别熬到明天一起还债",
                "晚上自由，手机会替你安排——你要不要抢回来",
                "没课的夜更容易晚睡，自己收着点"
            )
            in 22..23 -> listOf(
                "今天没课，早点收，夜里的时间不增值",
                "该睡了，自由日的尽头通常是熬夜",
                "收工吧，明天有没有课都得起床面对自己"
            )
            else -> listOf("今天没有课")
        }
        return pickTip(pool, seed = hour) + note
    }

    val ongoing = ranges.find { coursePeriodOngoing(it.start, it.end, now) }
    val next = ranges.find { now < it.start }
    val prev = ranges.lastOrNull { coursePeriodCompleted(it.start, it.end, now) }

    val eveningCount = courses.count {
        it.periodIndex(sectionTimes, morningSections, afternoonSections) == Course.PERIOD_EVENING
    }
    val afternoonCount = courses.count {
        it.periodIndex(sectionTimes, morningSections, afternoonSections) == Course.PERIOD_AFTERNOON
    }
    val totalCount = courses.size
    val completedCount = ranges.count { coursePeriodCompleted(it.start, it.end, now) }

    return when {
        // 正在上课：剩余 + 连堂，多套冷静说法
        ongoing != null -> {
            // 连堂判断只对同日课有意义；跨午夜课结束点在次日，当天没有「下一节」
            val nextAfter = if (ongoing.end > ongoing.start) {
                ranges.find { it.start > ongoing.end }
            } else null
            val gap = nextAfter?.let { ceilMinutesUntil(ongoing.end, it.start) }
            val gapPart = when {
                gap != null && gap <= 3 -> " · 连堂只歇 $gap 分钟，东西别收太彻底"
                gap != null && gap <= 10 -> " · 下课后仅 $gap 分钟，补给要快"
                gap != null && gap <= 15 -> " · 下课后还有 $gap 分钟，够喘口气"
                gap != null && gap <= 30 -> " · 下课后有 $gap 分钟，可离开教室"
                else -> ""
            }
            val remaining = ceilMinutesUntil(now, ongoing.end)
            val pool = when {
                remaining <= 1 -> listOf(
                    "马上收尾",
                    "到点边缘，准备收势",
                    "最后几十秒，别提前弹射"
                )
                remaining <= 5 -> listOf(
                    "还剩 $remaining 分钟，准备收势",
                    "还剩 $remaining 分钟，书包可以先热身",
                    "还剩 $remaining 分钟，再撑一下就到"
                )
                remaining <= 15 -> listOf(
                    "还剩 $remaining 分钟，保持节奏",
                    "还剩 $remaining 分钟，后半段别掉线",
                    "还剩 $remaining 分钟，重点一般在这截"
                )
                remaining <= 45 -> listOf(
                    "还剩 $remaining 分钟",
                    "还剩 $remaining 分钟，按自己的步子走",
                    "还剩 $remaining 分钟，别提前进入下课模式"
                )
                else -> listOf(
                    "还剩 $remaining 分钟",
                    "还剩 $remaining 分钟，刚上不久，稳住",
                    "还剩 $remaining 分钟，后面还长，别透支注意力"
                )
            }
            pickTip(pool, seed = now.hour * 31 + remaining / 5) + gapPart
        }

        // 课间：剩余 + 去向，多套说法
        prev != null && next != null -> {
            val remainToNext = ceilMinutesUntil(now, next.start)
            val room = roomOf(next.course)
            val name = next.course.name
            val pool = when {
                remainToNext <= 1 -> listOf(
                    "马上上课，座位先落定",
                    "铃要响了，回座",
                    "零缓冲，直接进教室"
                )
                remainToNext <= 5 -> listOf(
                    "还剩 $remainToNext 分钟，该回座位了 · $room",
                    "还剩 $remainToNext 分钟 · $room，别压点",
                    "课间进入收尾 · $room，起身吧"
                )
                remainToNext <= 10 -> listOf(
                    "课间还剩 $remainToNext 分钟，别压点 · $room",
                    "还剩 $remainToNext 分钟 · $room，$name 在等",
                    "课间还剩 $remainToNext 分钟，路线先想好 · $room"
                )
                remainToNext <= 20 -> listOf(
                    "课间还剩 $remainToNext 分钟，下节去 $room",
                    "休息还剩 $remainToNext 分钟 · $name · $room",
                    "还剩 $remainToNext 分钟，够缓一下，别开新局"
                )
                remainToNext <= 45 -> listOf(
                    "休息还剩 $remainToNext 分钟，下节 $room，别走远",
                    "长课间还剩 $remainToNext 分钟 · $name",
                    "还剩 $remainToNext 分钟，可以回血，别掉段"
                )
                else -> listOf(
                    "距下节还有 $remainToNext 分钟 · $room，按自己节奏来",
                    "大空档还剩 $remainToNext 分钟 · $name · $room",
                    "还有 $remainToNext 分钟才上课，时间归你安排 · $room"
                )
            }
            pickTip(pool, seed = now.hour * 31 + remainToNext / 5)
        }

        // 今天课已上完
        prev != null && next == null -> {
            val pool = when {
                completedCount >= totalCount -> listOf(
                    "今天 $totalCount 节已收官，知识点自己认领，没认领的算漏网",
                    "今日 $totalCount 节通关，作业群才是下一关",
                    "今天 $totalCount 节结束，人可以放，进度自己盯"
                )
                else -> listOf(
                    "已上完 $completedCount/$totalCount 节，后面的按原计划走",
                    "进度 $completedCount/$totalCount，别在收尾段掉链子",
                    "还剩 ${totalCount - completedCount} 节，稳着走完"
                )
            }
            pickTip(pool, seed = now.hour) + tomorrowNote()
        }

        // 第一节课还没开始
        next != null -> {
            val minutes = ceilMinutesUntil(now, next.start)
            val room = roomOf(next.course)
            val name = next.course.name
            val hour = now.hour
            val dayPlanPool = when {
                totalCount >= 5 -> listOf(
                    "今天共 $totalCount 节，强度不低",
                    "满课向：$totalCount 节，分配好体力",
                    "今日 $totalCount 节，别在上午就把电用完"
                )
                totalCount >= 3 -> listOf(
                    "今天 $totalCount 节，正常强度",
                    "今日 $totalCount 节，节奏正常",
                    "共 $totalCount 节，按课表走就行"
                )
                else -> listOf(
                    "今天就 $totalCount 节，别太飘",
                    "课不多，$totalCount 节，省着点浪费",
                    "轻量日：$totalCount 节，自由也别全刷掉"
                )
            }
            val dayPlan = pickTip(dayPlanPool, seed = hour)
            val pool = when {
                minutes > 180 -> listOf(
                    "$dayPlan · 第一节还有 ${minutes / 60} 小时以上",
                    "$dayPlan · 离上课还早，先按自己的节奏来",
                    "$dayPlan · 还有大把时间，别一开局就瘫"
                )
                minutes > 90 -> listOf(
                    "$dayPlan · 还有 $minutes 分钟，$room",
                    "$dayPlan · 距 $name 还有 $minutes 分钟",
                    "$dayPlan · 还有 $minutes 分钟，不用急，也别完全躺"
                )
                minutes > 30 -> listOf(
                    "$dayPlan · 还有 $minutes 分钟，可以准备了 · $room",
                    "还有 $minutes 分钟 · $name · $room，东西可以理了",
                    "$dayPlan · 还有 $minutes 分钟，预热一下状态"
                )
                minutes > 15 -> listOf(
                    "还有 $minutes 分钟，该动身了 · $room",
                    "还有 $minutes 分钟 · $name，路线可以定了",
                    "还剩 $minutes 分钟 · $room，别把缓冲用光"
                )
                minutes > 5 -> listOf(
                    "还有 $minutes 分钟 · $room，路上别磨",
                    "还剩 $minutes 分钟 · $name，匀速过去就行",
                    "还有 $minutes 分钟 · $room，现在走刚好"
                )
                minutes >= 1 -> listOf(
                    "还有 $minutes 分钟 · $room，保持匀速",
                    "还剩 $minutes 分钟 · $room，别冲刺也别散步",
                    "还有 $minutes 分钟，$name，到了先落座"
                )
                else -> {
                    val periodHint = when {
                        afternoonCount > 0 && hour in 12..13 ->
                            " · 下午还有 $afternoonCount 节"
                        eveningCount > 0 && hour in 14..17 ->
                            " · 晚上还有 $eveningCount 节"
                        else -> ""
                    }
                    listOf(
                        "快进教室 · $room$periodHint",
                        "铃在响了 · $room$periodHint",
                        "直接进教室 · $name$periodHint"
                    )
                }
            }
            pickTip(pool, seed = hour * 31 + minutes / 15)
        }

        else -> pickTip(
            listOf(
                "今天有 $totalCount 节课，按课表走就行",
                "今日共 $totalCount 节，日程已排好",
                "课表就绪：$totalCount 节"
            ),
            seed = now.hour
        )
    }
}

@Composable
fun TodayAssistantCard(
    /** 天气与定位状态：宿主用自己的平台能力算好后传入（Android=LocationManager，iOS=CoreLocation） */
    weather: WeatherData,
    hasLocationPermission: Boolean,
    onRequestLocation: () -> Unit,
    courses: List<Course>,
    tomorrowCourses: List<Course> = emptyList(),
    sectionTimes: Map<Int, String>,
    morningSections: Int,
    afternoonSections: Int,
    showClassroom: Boolean = true,
    showTeacher: Boolean = true,
    wallpaperBackdrop: Backdrop? = null,
    blurRadius: Float = 0f,
    surfaceOpacity: Float,
    /** 下课烟花（Android = FireworksOverlay，依赖 LocalContext/nanoTime）；null = 该端无此效果 */
    fireworksOverlay: (@Composable (trigger: Int, modifier: Modifier) -> Unit)? = null,
) {
    val courseStatus = rememberCourseStatus(courses, sectionTimes)
    var smartTip by remember { mutableStateOf("") }
    LaunchedEffect(courses, tomorrowCourses, sectionTimes, morningSections, afternoonSections) {
        while (true) {
            val newTip = generateSmartTip(
                courses, tomorrowCourses, sectionTimes, morningSections, afternoonSections
            ).orEmpty()
            // 与顶部倒计时同频轮询；文案未变化时不写状态，避免每秒重组
            if (newTip != smartTip) smartTip = newTip
            delay(1_000L.milliseconds)
        }
    }

    var fireworksTrigger by remember { mutableIntStateOf(0) }
    var lastCurrentEnd by remember { mutableStateOf<LocalTime?>(null) }
    // 只在「今天最后一节课」下课瞬间触发；中途下课/回看历史不触发
    LaunchedEffect(courseStatus.currentEnd) {
        val prevEnd = lastCurrentEnd
        lastCurrentEnd = courseStatus.currentEnd
        if (prevEnd == null) return@LaunchedEffect
        if (courseStatus.currentEnd == prevEnd) return@LaunchedEffect
        val justEndedMillis = millisBetween(prevEnd, nowLocalTime())
        if (justEndedMillis !in 0L..2500L) return@LaunchedEffect
        // 最后一节结束后：无当前课、也无下一节
        val isLastOfDay = courseStatus.currentCourse == null && courseStatus.nextCourse == null
        if (isLastOfDay) {
            fireworksTrigger++
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        BlurCard(
            wallpaperBackdrop = wallpaperBackdrop,
            blurRadius = blurRadius,
            surfaceOpacity = surfaceOpacity,
            showEdgeLight = wallpaperBackdrop != null && blurRadius > 0f,
            modifier = Modifier.fillMaxWidth()
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val currentCourse = courseStatus.currentCourse
            val nextCourse = courseStatus.nextCourse

            val label = when {
                currentCourse != null -> "正在上课"
                nextCourse != null -> "下节课"
                courses.isEmpty() -> ""
                else -> ""
            }
            val name = when {
                currentCourse != null -> currentCourse.name
                nextCourse != null -> nextCourse.name
                courses.isEmpty() -> "今天没有课程，好好休息吧！"
                else -> "当前没有课程"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // weight 限制左侧宽度，过长课程名换行，右侧剩余时间保持完整
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (label.isNotBlank()) {
                        Text(
                            text = label,
                            style = MiuixTheme.textStyles.body1.copy(fontSize = 17.sp),
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = name,
                        style = MiuixTheme.textStyles.body1.copy(fontSize = 17.sp),
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
                if (courseStatus.timeMessage.isNotBlank()) {
                    Text(
                        text = courseStatus.timeMessage,
                        style = MiuixTheme.textStyles.footnote1.copy(fontSize = 15.sp),
                        color = MiuixTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                    )
                }
            }

            val location = when {
                currentCourse != null -> currentCourse.classroom
                nextCourse != null -> nextCourse.classroom
                else -> ""
            }
            val teacher = when {
                currentCourse != null -> currentCourse.teacher
                nextCourse != null -> nextCourse.teacher
                else -> ""
            }
            val displayLocation = if (showClassroom) location else ""
            val displayTeacher = if (showTeacher) teacher else ""
            if (displayLocation.isNotBlank() || displayTeacher.isNotBlank()) {
                Text(
                    text = buildString {
                        if (displayLocation.isNotBlank()) append(displayLocation)
                        if (displayLocation.isNotBlank() && displayTeacher.isNotBlank()) append(" | ")
                        if (displayTeacher.isNotBlank()) append(displayTeacher)
                    },
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .height(0.5.dp)
                    .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.07f))
            )
            Spacer(modifier = Modifier.height(2.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (smartTip.isNotBlank()) {
                    Text(
                        text = smartTip,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (weather.loaded && !weather.temperature.isNaN()) {
                        WeatherIcon(
                            resource = getWeatherIconRes(weather.weatherType, weather.isNight()),
                            size = 23.dp,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${weather.temperature.toInt()}°C ${weather.weatherType} · ${weather.notice}",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    } else if (weather.needsLocation) {
                        Box(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(
                                        Res.drawable.ic_widget_location
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (hasLocationPermission) "无法获取位置信息·点击重试" else "需要定位权限·点击授权",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    modifier = Modifier.clickable { onRequestLocation() }
                                )
                            }
                        }
                    } else if (weather.apiError) {
                        Box(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(
                                        Res.drawable.ic_widget_location
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "天气服务异常·点击重试",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    modifier = Modifier.clickable { onRequestLocation() }
                                )
                            }
                        }
                    } else {
                        WeatherIcon(
                            resource = Res.drawable.icon_overcast,
                            size = 23.dp,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "加载中...",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            }
        }
    }
        fireworksOverlay?.invoke(
            fireworksTrigger,
            Modifier
                .matchParentSize()
                .graphicsLayer { clip = false },
        )
    }
}
