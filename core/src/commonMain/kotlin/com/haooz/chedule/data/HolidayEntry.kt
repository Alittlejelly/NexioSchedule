package com.haooz.chedule.data

import kotlinx.datetime.LocalDate

// ════════════════════════════════════════════════════════════════════════
//  节假日条目 —— 纯数据类型
//
//  只承载数据，不碰存储或平台 API（查询逻辑见 [HolidayEntries]）。
//  ⚠ 本类型落盘的 JSON（`holiday_settings` 的 `entries_{年}`）是持久化格式，
//  键名/字段名换库时必须逐字对齐，属于数据兼容红线。
// ════════════════════════════════════════════════════════════════════════

/** 节假日与调休数据。假期跳过提醒，调休按配置的课表周次和星期调度。 */
data class HolidayEntry(
    val date: String,
    val endDate: String = "",
    val name: String,
    val type: Int,
    /**
     * 调休「上哪一天的课」的**绝对日期**（yyyy-MM-dd，空 = 未配置）。
     *
     * ⚠ 唯一可信来源。周次是相对课表学期开始时间算出的，同一 (周次,星期) 在不同课表下指向不同日期。
     */
    val followDate: String = "",
    /** 旧数据兼容：仅用于迁移/老备份还原，读取一律走 [followDate] */
    val followWeek: Int = -1,
    /** 旧数据兼容：仅用于迁移/老备份还原，读取一律走 [followDate] */
    val followWeekday: Int = -1,
    val custom: Boolean = false,
) {
    companion object {
        /** 假期 */
        const val TYPE_HOLIDAY = 0

        /** 调休工作日 */
        const val TYPE_WORKSWAP = 1
    }

    fun matches(target: String): Boolean {
        val targetDate = runCatching { LocalDate.parse(target) }.getOrNull() ?: return false
        val startDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
        val lastDate = if (endDate.isBlank()) {
            startDate
        } else {
            runCatching { LocalDate.parse(endDate) }.getOrNull() ?: return false
        }
        return targetDate >= startDate && targetDate <= lastDate
    }

    /** 调休跟随的绝对日期；未配置/格式坏 → null */
    fun followLocalDate(): LocalDate? =
        followDate.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** 是否已配好跟随日期（老数据用 followWeek/followWeekday 也暂时算已配，等迁移） */
    fun hasFollowMapping(): Boolean =
        followLocalDate() != null || (followWeek > 0 && followWeekday in 1..7)
}

/**
 * 假期条目的**纯查询**逻辑：给定条目集合 → 查某天的条目，不碰任何存储或平台 API。
 */
object HolidayEntries {
    fun entriesForDate(entriesByYear: Map<Int, List<HolidayEntry>>, date: LocalDate): List<HolidayEntry> {
        val storageYearPriority = buildList {
            add(date.year)
            add(date.year - 1)
            addAll(entriesByYear.keys.filter { it < date.year - 1 }.sortedDescending())
        }.distinct()

        val yearRank = storageYearPriority.withIndex().associate { it.value to it.index }
        return storageYearPriority.flatMap { year ->
            entriesByYear[year].orEmpty()
                .filter { it.matches(date.toString()) }
                .map { year to it }
        }.sortedWith(
            compareByDescending<Pair<Int, HolidayEntry>> { it.second.custom }
                .thenBy { yearRank.getValue(it.first) }
        ).map { it.second }
    }

    /**
     * 为「只能整表下发、且只能线性扫数组取第一条命中」的弱算力端（小米穿戴 rpk）
     * 挑出可直接下发的条目，**返回顺序即优先级**。
     *
     * ⚠ 为什么要多这一层：手表端没有 `custom` 这个概念，只能按数组顺序第一条命中即采用。
     * 直接把按年 map 摊平下发，命中哪条取决于「年份升序 + 年内插入顺序」——
     * 自定义条目、当年条目会被更早年份的同名条目盖掉，调休跟错课。
     * 这里按 [entriesForDate] 的同一套优先级排好序，再剔掉被高优先级条目完全遮住的条目，
     * 使得**对任意日期**，对端线性扫到的第一条与手机端 [entriesForDate] 的首条一致。
     *
     * @param today 决定年份优先级锚点（当年 > 上一年 > 更早倒序），与手机端查询口径一致。
     * @param windowFirst/windowLast 只下发与该日期窗口有交集的条目，顺带挡掉历史年份存量。
     */
    fun forFlatPush(
        entriesByYear: Map<Int, List<HolidayEntry>>,
        today: LocalDate,
        windowFirst: LocalDate,
        windowLast: LocalDate,
    ): List<HolidayEntry> {
        if (windowLast < windowFirst) return emptyList()

        // 与 entriesForDate 逐字同款：当年 > 上一年 > 更早的年份倒序。
        // 不在表里的年份（未来年份等）排到末尾。
        val yearRank = buildList {
            add(today.year)
            add(today.year - 1)
            addAll(entriesByYear.keys.filter { it < today.year - 1 }.sortedDescending())
        }.distinct().withIndex().associate { it.value to it.index }

        // 摊平顺序必须与 entriesForDate 的迭代顺序一致：yearRank 升序、同年内保持存储顺序。
        // 之后再按「稳定排序 + 自然下标」复刻它的 custom 降序。
        //
        // ⚠ 千万不要在这里补 start/end 之类的排序键：同年同 custom 且区间重叠时
        // （中秋 10/05-10/06 套在国庆 10/01-10/07 里）entriesForDate 靠稳定排序保留
        // 存储顺序，一旦按 start 重排就会选到不同的条目，两端直接换错课。
        // 年份优先级只能拿 today 当锚点（一份包要服务窗口内所有日期），所以**跨年日期上的
        // 首条可能仍与手机端不同**。因此剔除冗余时必须保守：只删「无论哪天都不可能赢」的条目。
        val ranked = entriesByYear.entries
            .sortedBy { yearRank[it.key] ?: yearRank.size }
            .flatMap { (year, entries) -> entries.map { year to it } }
            .withIndex()
            .mapNotNull { (naturalIndex, yearAndEntry) ->
                val (year, entry) = yearAndEntry
                val start = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                    ?: return@mapNotNull null
                // 与 HolidayEntry.matches 同款语义：endDate 空 = 单日；
                // 坏值在手机端永远匹配不上，这里同样丢掉。
                val end = if (entry.endDate.isBlank()) {
                    start
                } else {
                    runCatching { LocalDate.parse(entry.endDate) }.getOrNull()
                        ?: return@mapNotNull null
                }
                if (end < windowFirst || start > windowLast) return@mapNotNull null
                Ranked(entry, naturalIndex, year, start, end)
            }
            .sortedWith(
                compareByDescending<Ranked> { it.entry.custom }.thenBy { it.naturalIndex }
            )

        val kept = mutableListOf<HolidayEntry>()
        val keptRanges = mutableListOf<Ranked>()
        for (item in ranked) {
            if (keptRanges.any { it.shadows(item) }) continue
            kept += item.entry
            keptRanges += item
        }
        return kept
    }

    /**
     * `kept` 能不能把 `item` 当作冗余剔掉。
     *
     * 判据来自**手环端实际的两条独立扫描**（`schedule.js`）：
     * - `findHolidayEntry(date, 0)`：只扫 type=0，命中即算假期；`resolveDisplayDayKey` 里
     *   假期**无条件压倒**调休。手机端同款：`HolidayCourseExclusion` 用
     *   `entriesForDate(date).any { type == TYPE_HOLIDAY }`。
     * - `findWorkSwap(date)`：只扫 type=1，且**优先返回配了 follow 映射的那条**。
     *
     * 由此得到两条硬约束：
     * 1. **调休不能遮蔽假期**：区间被调休整段盖住的假期一旦删掉，手环就不再认为那天是假期，
     *    会在假期照常显示课程 —— 而手机端仍判为假期空课表，两端直接打架。
     * 2. **跨年条目不能互相遮蔽**：一条包要服务窗口内所有日期，而手机端的年份优先级是
     *   「查询日所在年」优先。今天在 2026 时 2026 的寒假排前面，但查 2027-01-01 时手机端
     *   优先 2027 的元旦；此时若按 today 的排序把元旦删掉，手环就会显示成「寒假」。
     */
    private fun Ranked.shadows(item: Ranked): Boolean {
        val typeAllowed = when {
            // 假期压倒调休：只要区间被某条假期完整盖住，调休永远用不上，可删（与年份无关）
            type == HolidayEntry.TYPE_HOLIDAY && item.type == HolidayEntry.TYPE_WORKSWAP -> true
            // 同类型之间才谈遮蔽，且必须同年 —— 不同年的优先级随查询日变化，删了会选错
            type == item.type -> year == item.year
            else -> false
        }
        return typeAllowed && start <= item.start && item.end <= end
    }

    private data class Ranked(
        val entry: HolidayEntry,
        /** 摊平后的自然下标，用来在 custom 打平时复刻稳定排序的存储顺序 */
        val naturalIndex: Int,
        /** 所属年份桶（跨年剔除判据用） */
        val year: Int,
        val start: LocalDate,
        val end: LocalDate,
    ) {
        val type: Int get() = entry.type
    }
}
