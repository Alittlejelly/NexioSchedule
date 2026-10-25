package com.haooz.chedule.i18n

/**
 * 今日助手卡文案。多为随时间轮换的动态模板句，插值一律 %s/%d 按出现顺序传参。
 *
 * 与天气 API 返回值比较的中文天气类型词（"晴"、"多云" 等）属于数据键，
 * 保留在调用方（TodayAssistantCard.kt 的 getWeatherIconRes），不进本命名空间。
 */
object AssistantStr {

    // ── 卡片主行：状态标签与占位 ──
    val inClass = L("正在上课", "In class")
    val nextClass = L("下节课", "Next class")
    val noCourseTodayRest = L("今天没有课程，好好休息吧！", "No classes today — enjoy the break!")
    val noCourseNow = L("当前没有课程", "No class right now")

    // ── 剩余时间口径（最后一分钟秒级，其余分钟级） ──
    val alreadyStarted = L("已开始", "Started")
    val alreadyEnded = L("已结束", "Ended")
    val secondsToStart = L("%d 秒后", "in %d s")
    val secondsLeft = L("还剩 %d 秒", "%d s left")
    val durationHourMinute = L("%d小时%d分钟", "%d hr %d min")
    val durationHour = L("%d小时", "%d hr")
    val durationMinute = L("%d 分钟", "%d min")
    val durationToStart = L("%s后", "in %s")
    val durationLeft = L("还剩 %s", "%s left")

    // ── 晚间追加的明日预告 ──
    val tomorrowFree = L(" · 明天没课，可以睡到自然醒", " · No classes tomorrow — sleep in")
    val tomorrowFirstTbd = L("第一节待定", "first period TBD")
    val tomorrowStartsWith = L("%s 开头", "first up %s")
    val tomorrowCount = L(" · 明天 %d 节，%s", " · %d classes tomorrow, %s")

    // ── 教室 ──
    val roomTbd = L("教室待定", "Room TBD")

    // ── 节次时间残缺 ──
    val timesMissingFillFirst = L("今天有 %d 节课，但节次时间没填全，先去课表补齐再排日程", "You have %d classes today, but some period times are missing. Fill those in on the schedule first")
    val timesMissingNoCountdown = L("课表里有 %d 节，时间字段是空的，补完才好倒计时", "%d classes on the schedule, but the time fields are empty. Add them to get a countdown")
    val timesMissingFixIt = L("有 %d 节课，但节次时间残缺，去课表修一下", "%d classes but no times — the period times are incomplete. Fix them on the schedule")

    // ── 今天没课（分时段轮换） ──
    val freeMorningAlarm = L("今天没课，闹钟可以往后挪一格", "No classes today — push that alarm back a notch")
    val freeMorningBed = L("没课的早晨，床比计划表靠谱", "On a class-free morning, the bed beats the planner")
    val freeMorningSleepIn = L("今天空着，多睡可以，睡到中午就是另一回事了", "An open day today — extra sleep is fine, sleeping till noon is another story")
    val freeAmPlanIt = L("今天没课，上午自己排，别一睁眼就交给了床", "No classes today — plan the morning yourself, don't hand it straight to the bed")
    val freeAmLibrary = L("上午空档，图书馆有座，你有借口", "Open morning — the library has seats, and now you have an excuse")
    val freeAmPrecious = L("没课的上午很贵，刷完就没了", "A class-free morning is precious — scroll it away and it's gone")
    val freeNoonMeals = L("下午也没课，吃饭按点，别用零食糊弄", "The afternoon is free too — eat proper meals, don't get by on snacks")
    val freeNoonStomach = L("中午自由，胃要照顾好，下午才站得住", "Free at noon — take care of your stomach and the afternoon will hold up")
    val freeNoonFreedom = L("没课的饭点，是你今天最实在的自由", "A mealtime with no class is the most honest freedom you'll get today")
    val freePmRealWork = L("今天没课，做点正事，也别把自己排崩", "No classes today — get something real done, but don't overload yourself")
    val freePmSunshine = L("下午阳光很好，适合出门，也适合焦虑——你选", "A lovely afternoon out there — good for going outside, also good for spiraling. Your pick")
    val freePmProgressBar = L("空着的下午，进度条容易一直是 0%", "An empty afternoon — the progress bar loves to sit at 0%")
    val freeEveUnwind = L("今天没课，适当放空，别熬到明天一起还债", "No classes today — zone out a little; don't save it all up as tomorrow's debt")
    val freeEvePhone = L("晚上自由，手机会替你安排——你要不要抢回来", "A free evening, and your phone will happily plan it for you — unless you take it back")
    val freeEveStayUp = L("没课的夜更容易晚睡，自己收着点", "Class-free nights make late nights easy — keep yourself in check")
    val freeNightWrapUp = L("今天没课，早点收，夜里的时间不增值", "No classes today — wrap up early; late-night hours earn no interest")
    val freeNightSleep = L("该睡了，自由日的尽头通常是熬夜", "Time for bed — free days usually end in staying up too late")
    val freeNightFaceYourself = L("收工吧，明天有没有课都得起床面对自己", "Call it a day — class or not tomorrow, you still have to get up and face yourself")
    val noClassToday = L("今天没有课", "No classes today")

    // ── 正在上课 ──
    val endWrapUpNow = L("马上收尾", "Wrapping up any second now")
    val endAtTheBell = L("到点边缘，准备收势", "Right at the bell — start winding down")
    val endFinalSeconds = L("最后几十秒，别提前弹射", "Final seconds — don't bolt early")
    val endMinWindDown = L("还剩 %d 分钟，准备收势", "%d min left — start winding down")
    val endMinBackpack = L("还剩 %d 分钟，书包可以先热身", "%d min left — your backpack can start warming up")
    val endMinHangIn = L("还剩 %d 分钟，再撑一下就到", "%d min left — hang in there, almost done")
    val ongoingKeepRhythm = L("还剩 %d 分钟，保持节奏", "%d min left — keep the rhythm")
    val ongoingSecondHalf = L("还剩 %d 分钟，后半段别掉线", "%d min left — don't zone out in the back half")
    val ongoingKeyPart = L("还剩 %d 分钟，重点一般在这截", "%d min left — the key part is usually right here")
    val ongoingMinutesLeft = L("还剩 %d 分钟", "%d min left")
    val ongoingOwnPace = L("还剩 %d 分钟，按自己的步子走", "%d min left — go at your own pace")
    val ongoingNoEarlyDone = L("还剩 %d 分钟，别提前进入下课模式", "%d min left — don't switch to end-of-class mode early")
    val ongoingJustStarted = L("还剩 %d 分钟，刚上不久，稳住", "%d min left — you just got started, settle in")
    val ongoingSaveFocus = L("还剩 %d 分钟，后面还长，别透支注意力", "%d min left — still a long stretch, don't burn through your focus")

    // ── 连堂间隙 ──
    val gapBackToBack = L(" · 连堂只歇 %d 分钟，东西别收太彻底", " · Back-to-back with only %d min between — don't pack up too thoroughly")
    val gapQuickRefuel = L(" · 下课后仅 %d 分钟，补给要快", " · Only %d min after class — refuel fast")
    val gapCatchBreath = L(" · 下课后还有 %d 分钟，够喘口气", " · %d min after class — enough to catch your breath")
    val gapLeaveRoom = L(" · 下课后有 %d 分钟，可离开教室", " · %d min after class — you can leave the room")

    // ── 课间 ──
    val breakGrabSeat = L("马上上课，座位先落定", "Class any second — claim your seat")
    val breakBellAboutToRing = L("铃要响了，回座", "The bell is about to ring — head back")
    val breakZeroBuffer = L("零缓冲，直接进教室", "Zero buffer — walk straight in")
    val breakHeadBack = L("还剩 %d 分钟，该回座位了 · %s", "%d min left — time to head back · %s")
    val breakDontCutClose = L("还剩 %d 分钟 · %s，别压点", "%d min left · %s — don't cut it close")
    val breakOnYourFeet = L("课间进入收尾 · %s，起身吧", "Break is wrapping up · %s — on your feet")
    val breakWatchTheClock = L("课间还剩 %d 分钟，别压点 · %s", "%d min left in the break — don't cut it close · %s")
    val breakClassWaiting = L("还剩 %d 分钟 · %s，%s 在等", "%d min left · %s — %s is waiting")
    val breakPlanRoute = L("课间还剩 %d 分钟，路线先想好 · %s", "%d min left in the break — plan your route · %s")
    val breakNextRoom = L("课间还剩 %d 分钟，下节去 %s", "%d min left in the break — next class is in %s")
    val breakChill = L("休息还剩 %d 分钟 · %s · %s", "%d min left · %s · %s")
    val breakNoNewStarts = L("还剩 %d 分钟，够缓一下，别开新局", "%d min left — enough to recharge; don't start anything new")
    val breakDontWander = L("休息还剩 %d 分钟，下节 %s，别走远", "%d min left — next class is in %s, don't wander far")
    val breakLongBreak = L("长课间还剩 %d 分钟 · %s", "%d min left in this long break · %s")
    val breakRecover = L("还剩 %d 分钟，可以回血，别掉段", "%d min left — a good moment to recover; don't lose momentum")
    val breakOwnRhythm = L("距下节还有 %d 分钟 · %s，按自己节奏来", "%d min until the next class · %s — take it at your own pace")
    val breakBigGap = L("大空档还剩 %d 分钟 · %s · %s", "%d min left in this big gap · %s · %s")
    val breakTimeYours = L("还有 %d 分钟才上课，时间归你安排 · %s", "%d min until class — this time is yours · %s")

    // ── 今日课已上完 ──
    val doneClaimKnowledge = L("今天 %d 节已收官，知识点自己认领，没认领的算漏网", "%d classes done today — claim what you learned; whatever's unclaimed slipped through")
    val doneNextLevel = L("今日 %d 节通关，作业群才是下一关", "%d classes cleared today — the homework group chat is the next level")
    val doneOffClock = L("今天 %d 节结束，人可以放，进度自己盯", "%d classes done today — you're off the clock, but keep an eye on your progress")
    val doneOnPlan = L("已上完 %d/%d 节，后面的按原计划走", "%d/%d classes done — stick to the plan for the rest")
    val doneProgress = L("进度 %d/%d，别在收尾段掉链子", "Progress %d/%d — don't fumble the finish")
    val doneSteadyFinish = L("还剩 %d 节，稳着走完", "%d classes to go — finish steady")

    // ── 全天强度预告 ──
    val planHeavy = L("今天共 %d 节，强度不低", "%d classes today — that's a heavy load")
    val planHeavyRation = L("满课向：%d 节，分配好体力", "Full-schedule day: %d classes — ration your energy")
    val planHeavyBattery = L("今日 %d 节，别在上午就把电用完", "%d classes today — don't run out of battery by noon")
    val planNormal = L("今天 %d 节，正常强度", "%d classes today — a normal load")
    val planNormalPace = L("今日 %d 节，节奏正常", "%d classes today — standard pace")
    val planNormalFollow = L("共 %d 节，按课表走就行", "%d classes in total — just follow the schedule")
    val planFewDontFloat = L("今天就 %d 节，别太飘", "Just %d classes today — don't get carried away")
    val planFewSpendWisely = L("课不多，%d 节，省着点浪费", "Not many classes — %d. Spend the slack wisely")
    val planLightDay = L("轻量日：%d 节，自由也别全刷掉", "Light day: %d classes — free time, but don't scroll it all away")

    // ── 第一节课前倒计时 ──
    val firstFarHours = L("%s · 第一节还有 %d 小时以上", "%s · First class is over %d hours away")
    val firstFarOwnRhythm = L("%s · 离上课还早，先按自己的节奏来", "%s · Plenty of time before class — ease into your own rhythm")
    val firstFarDontCrash = L("%s · 还有大把时间，别一开局就瘫", "%s · Tons of time — don't crash out at the start")
    val first90Room = L("%s · 还有 %d 分钟，%s", "%s · %d min to go · %s")
    val first90Until = L("%s · 距 %s 还有 %d 分钟", "%s · %s starts in %d min")
    val first90NoRush = L("%s · 还有 %d 分钟，不用急，也别完全躺", "%s · %d min to go — no rush, but don't fully check out either")
    val first30GetReady = L("%s · 还有 %d 分钟，可以准备了 · %s", "%s · %d min to go — time to get ready · %s")
    val first30PackUp = L("还有 %d 分钟 · %s · %s，东西可以理了", "%d min to go · %s · %s — start getting your things together")
    val first30WarmUp = L("%s · 还有 %d 分钟，预热一下状态", "%s · %d min to go — warm up a little")
    val first15HeadOut = L("还有 %d 分钟，该动身了 · %s", "%d min to go — time to head out · %s")
    val first15RouteSet = L("还有 %d 分钟 · %s，路线可以定了", "%d min to go · %s — you can lock in your route")
    val first15Buffer = L("还剩 %d 分钟 · %s，别把缓冲用光", "%d min left · %s — don't use up all your buffer")
    val first5NoDawdle = L("还有 %d 分钟 · %s，路上别磨", "%d min to go · %s — no dawdling on the way")
    val first5SteadyWalk = L("还剩 %d 分钟 · %s，匀速过去就行", "%d min left · %s — a steady walk will do")
    val first5LeaveNow = L("还有 %d 分钟 · %s，现在走刚好", "%d min to go · %s — leaving now is just right")
    val first1KeepPace = L("还有 %d 分钟 · %s，保持匀速", "%d min to go · %s — keep an even pace")
    val first1NoSprint = L("还剩 %d 分钟 · %s，别冲刺也别散步", "%d min left · %s — no sprinting, no strolling")
    val first1TakeSeat = L("还有 %d 分钟，%s，到了先落座", "%d min to go — %s. Grab a seat when you arrive")
    val hintAfternoonMore = L(" · 下午还有 %d 节", " · %d more this afternoon")
    val hintEveningMore = L(" · 晚上还有 %d 节", " · %d more this evening")
    val rushHurryIn = L("快进教室 · %s", "Hurry into the classroom · %s")
    val rushBellRinging = L("铃在响了 · %s", "The bell is ringing · %s")
    val rushStraightIn = L("直接进教室 · %s", "Head straight to class · %s")

    // ── 兜底：有课 ──
    val readyFollowSchedule = L("今天有 %d 节课，按课表走就行", "%d classes today — just follow the schedule")
    val readyDayPlanned = L("今日共 %d 节，日程已排好", "%d classes in total today — your day is all set")
    val readyScheduleSet = L("课表就绪：%d 节", "Schedule ready: %d classes")

    // ── 天气/定位 ──
    val locationFailedTapRetry = L("无法获取位置信息·点击重试", "Can't get your location · Tap to retry")
    val locationPermissionNeeded = L("需要定位权限·点击授权", "Location permission needed · Tap to allow")
    val weatherServiceTapRetry = L("天气服务异常·点击重试", "Weather service error · Tap to retry")
}
