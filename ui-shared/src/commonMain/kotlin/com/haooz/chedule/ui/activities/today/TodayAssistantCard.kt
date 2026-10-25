package com.haooz.chedule.ui.activities.today

// 今日助手卡（纯展示与纯逻辑）：天气/定位由平台侧算好后按参数传入，下课烟花走 `fireworksOverlay` 槽位。
// ⚠ 文件名必须与 androidApp 的 `TodayAssistant.kt` 不同：同包同名 .kt 会让 Kotlin 生成同名
//   facade 类，dex 只留一个 → 运行期 NoSuchMethodError。

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
import com.haooz.chedule.i18n.AssistantStr
import com.haooz.chedule.i18n.CommonStr
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
    // 时间区间只随 courses/sectionTimes 重算，循环内复用；保持原顺序以兼容 first-match
    val ranges = remember(courses, sectionTimes) {
        buildCourseTimeRanges(courses, sectionTimes, sortByStart = false)
    }
    fun statusAt(now: LocalTime): CourseStatus {
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
        return CourseStatus(current?.course, next?.course, message, current?.end)
    }
    // 初值必须当场算出来：留空会让新组合的助手卡先闪一帧「当前没有课程」，
    // 一秒后才跳回真实课程（借位页 / 首次进今日页都踩得到）。
    var status by remember(courses, sectionTimes) { mutableStateOf(statusAt(nowLocalTime())) }
    LaunchedEffect(ranges) {
        while (true) {
            val newStatus = statusAt(nowLocalTime())
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
    if (remainMillis < 0L) return if (isCountdownToStart) AssistantStr.alreadyStarted() else AssistantStr.alreadyEnded()
    if (remainMillis < 60_000L) {
        val seconds = ((remainMillis + 999L) / 1000L).toInt().coerceIn(1, 60)
        return if (isCountdownToStart) AssistantStr.secondsToStart(seconds) else AssistantStr.secondsLeft(seconds)
    }
    val minutes = ceilMinutesUntil(from, to)
    val duration = formatCountdownMinutes(minutes)
    return if (isCountdownToStart) AssistantStr.durationToStart(duration)
    else AssistantStr.durationLeft(duration)
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
private fun formatCountdownMinutes(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> AssistantStr.durationHourMinute(hours, mins)
        hours > 0 -> AssistantStr.durationHour(hours)
        else -> AssistantStr.durationMinute(minutes)
    }
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
        course.classroom.ifBlank { AssistantStr.roomTbd() }

    fun tomorrowNote(): String {
        val hour = now.hour
        if (hour < 20) return ""
        val n = tomorrowCourses.size
        if (n == 0) return AssistantStr.tomorrowFree()
        val first = tomorrowRanges.firstOrNull()?.course
        val firstLine = if (first != null) AssistantStr.tomorrowStartsWith(first.name) else AssistantStr.tomorrowFirstTbd()
        return AssistantStr.tomorrowCount(n, firstLine)
    }

    if (ranges.isEmpty()) {
        // 时间残缺时避免误报「今天没课」
        if (courses.isNotEmpty()) {
            return pickTip(
                listOf(
                    AssistantStr.timesMissingFillFirst(courses.size),
                    AssistantStr.timesMissingNoCountdown(courses.size),
                    AssistantStr.timesMissingFixIt(courses.size)
                ),
                seed = now.hour
            )
        }
        val hour = now.hour
        val note = tomorrowNote()
        val pool = when (hour) {
            in 5..8 -> listOf(
                AssistantStr.freeMorningAlarm(),
                AssistantStr.freeMorningBed(),
                AssistantStr.freeMorningSleepIn()
            )
            in 9..11 -> listOf(
                AssistantStr.freeAmPlanIt(),
                AssistantStr.freeAmLibrary(),
                AssistantStr.freeAmPrecious()
            )
            in 12..13 -> listOf(
                AssistantStr.freeNoonMeals(),
                AssistantStr.freeNoonStomach(),
                AssistantStr.freeNoonFreedom()
            )
            in 14..17 -> listOf(
                AssistantStr.freePmRealWork(),
                AssistantStr.freePmSunshine(),
                AssistantStr.freePmProgressBar()
            )
            in 18..21 -> listOf(
                AssistantStr.freeEveUnwind(),
                AssistantStr.freeEvePhone(),
                AssistantStr.freeEveStayUp()
            )
            in 22..23 -> listOf(
                AssistantStr.freeNightWrapUp(),
                AssistantStr.freeNightSleep(),
                AssistantStr.freeNightFaceYourself()
            )
            else -> listOf(AssistantStr.noClassToday())
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
                gap != null && gap <= 3 -> AssistantStr.gapBackToBack(gap)
                gap != null && gap <= 10 -> AssistantStr.gapQuickRefuel(gap)
                gap != null && gap <= 15 -> AssistantStr.gapCatchBreath(gap)
                gap != null && gap <= 30 -> AssistantStr.gapLeaveRoom(gap)
                else -> ""
            }
            val remaining = ceilMinutesUntil(now, ongoing.end)
            val pool = when {
                remaining <= 1 -> listOf(
                    AssistantStr.endWrapUpNow(),
                    AssistantStr.endAtTheBell(),
                    AssistantStr.endFinalSeconds()
                )
                remaining <= 5 -> listOf(
                    AssistantStr.endMinWindDown(remaining),
                    AssistantStr.endMinBackpack(remaining),
                    AssistantStr.endMinHangIn(remaining)
                )
                remaining <= 15 -> listOf(
                    AssistantStr.ongoingKeepRhythm(remaining),
                    AssistantStr.ongoingSecondHalf(remaining),
                    AssistantStr.ongoingKeyPart(remaining)
                )
                remaining <= 45 -> listOf(
                    AssistantStr.ongoingMinutesLeft(remaining),
                    AssistantStr.ongoingOwnPace(remaining),
                    AssistantStr.ongoingNoEarlyDone(remaining)
                )
                else -> listOf(
                    AssistantStr.ongoingMinutesLeft(remaining),
                    AssistantStr.ongoingJustStarted(remaining),
                    AssistantStr.ongoingSaveFocus(remaining)
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
                    AssistantStr.breakGrabSeat(),
                    AssistantStr.breakBellAboutToRing(),
                    AssistantStr.breakZeroBuffer()
                )
                remainToNext <= 5 -> listOf(
                    AssistantStr.breakHeadBack(remainToNext, room),
                    AssistantStr.breakDontCutClose(remainToNext, room),
                    AssistantStr.breakOnYourFeet(room)
                )
                remainToNext <= 10 -> listOf(
                    AssistantStr.breakWatchTheClock(remainToNext, room),
                    AssistantStr.breakClassWaiting(remainToNext, room, name),
                    AssistantStr.breakPlanRoute(remainToNext, room)
                )
                remainToNext <= 20 -> listOf(
                    AssistantStr.breakNextRoom(remainToNext, room),
                    AssistantStr.breakChill(remainToNext, name, room),
                    AssistantStr.breakNoNewStarts(remainToNext)
                )
                remainToNext <= 45 -> listOf(
                    AssistantStr.breakDontWander(remainToNext, room),
                    AssistantStr.breakLongBreak(remainToNext, name),
                    AssistantStr.breakRecover(remainToNext)
                )
                else -> listOf(
                    AssistantStr.breakOwnRhythm(remainToNext, room),
                    AssistantStr.breakBigGap(remainToNext, name, room),
                    AssistantStr.breakTimeYours(remainToNext, room)
                )
            }
            pickTip(pool, seed = now.hour * 31 + remainToNext / 5)
        }

        // 今天课已上完
        prev != null && next == null -> {
            val pool = when {
                completedCount >= totalCount -> listOf(
                    AssistantStr.doneClaimKnowledge(totalCount),
                    AssistantStr.doneNextLevel(totalCount),
                    AssistantStr.doneOffClock(totalCount)
                )
                else -> listOf(
                    AssistantStr.doneOnPlan(completedCount, totalCount),
                    AssistantStr.doneProgress(completedCount, totalCount),
                    AssistantStr.doneSteadyFinish(totalCount - completedCount)
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
                    AssistantStr.planHeavy(totalCount),
                    AssistantStr.planHeavyRation(totalCount),
                    AssistantStr.planHeavyBattery(totalCount)
                )
                totalCount >= 3 -> listOf(
                    AssistantStr.planNormal(totalCount),
                    AssistantStr.planNormalPace(totalCount),
                    AssistantStr.planNormalFollow(totalCount)
                )
                else -> listOf(
                    AssistantStr.planFewDontFloat(totalCount),
                    AssistantStr.planFewSpendWisely(totalCount),
                    AssistantStr.planLightDay(totalCount)
                )
            }
            val dayPlan = pickTip(dayPlanPool, seed = hour)
            val pool = when {
                minutes > 180 -> listOf(
                    AssistantStr.firstFarHours(dayPlan, minutes / 60),
                    AssistantStr.firstFarOwnRhythm(dayPlan),
                    AssistantStr.firstFarDontCrash(dayPlan)
                )
                minutes > 90 -> listOf(
                    AssistantStr.first90Room(dayPlan, minutes, room),
                    AssistantStr.first90Until(dayPlan, name, minutes),
                    AssistantStr.first90NoRush(dayPlan, minutes)
                )
                minutes > 30 -> listOf(
                    AssistantStr.first30GetReady(dayPlan, minutes, room),
                    AssistantStr.first30PackUp(minutes, name, room),
                    AssistantStr.first30WarmUp(dayPlan, minutes)
                )
                minutes > 15 -> listOf(
                    AssistantStr.first15HeadOut(minutes, room),
                    AssistantStr.first15RouteSet(minutes, name),
                    AssistantStr.first15Buffer(minutes, room)
                )
                minutes > 5 -> listOf(
                    AssistantStr.first5NoDawdle(minutes, room),
                    AssistantStr.first5SteadyWalk(minutes, name),
                    AssistantStr.first5LeaveNow(minutes, room)
                )
                minutes >= 1 -> listOf(
                    AssistantStr.first1KeepPace(minutes, room),
                    AssistantStr.first1NoSprint(minutes, room),
                    AssistantStr.first1TakeSeat(minutes, name)
                )
                else -> {
                    val periodHint = when {
                        afternoonCount > 0 && hour in 12..13 ->
                            AssistantStr.hintAfternoonMore(afternoonCount)
                        eveningCount > 0 && hour in 14..17 ->
                            AssistantStr.hintEveningMore(eveningCount)
                        else -> ""
                    }
                    listOf(
                        AssistantStr.rushHurryIn(room) + periodHint,
                        AssistantStr.rushBellRinging(room) + periodHint,
                        AssistantStr.rushStraightIn(name) + periodHint
                    )
                }
            }
            pickTip(pool, seed = hour * 31 + minutes / 15)
        }

        else -> pickTip(
            listOf(
                AssistantStr.readyFollowSchedule(totalCount),
                AssistantStr.readyDayPlanned(totalCount),
                AssistantStr.readyScheduleSet(totalCount)
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
                currentCourse != null -> AssistantStr.inClass()
                nextCourse != null -> AssistantStr.nextClass()
                courses.isEmpty() -> ""
                else -> ""
            }
            val name = when {
                currentCourse != null -> currentCourse.name
                nextCourse != null -> nextCourse.name
                courses.isEmpty() -> AssistantStr.noCourseTodayRest()
                else -> AssistantStr.noCourseNow()
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
                                    text = if (hasLocationPermission) AssistantStr.locationFailedTapRetry() else AssistantStr.locationPermissionNeeded(),
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
                                    text = AssistantStr.weatherServiceTapRetry(),
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
                            text = CommonStr.loading(),
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
