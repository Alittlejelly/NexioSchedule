package com.haooz.chedule.data

/**
 * 上课地点在缩略态（超级岛槽位 / 灵动岛胶囊 / 实况通知右侧 / 息屏标题）与
 * 岛展开后「地点」行的显示规则。
 *
 * ## 问题
 *
 * 这两处由系统排版，超出可用宽度的部分从尾部丢弃：「2号综合楼B3027」显示成
 * 「2号综合楼B3」，被截掉的正是最要紧的房号。
 *
 * ## 两种精简方式
 *
 * 公共部分是一样的格式清理：摘括号注解、剥「××校区 / ××校园 / ××学区」前缀、
 * 丢空白、全角字母数字转半角。
 *
 * - [METHOD_REGEX]：再套内置楼名缩写表（「第五教学楼」→「五教」）。
 * - [METHOD_AI]：只套 AI 生成的楼名缩写表，上面那套正则**完全不参与**。
 *
 * 压缩靠缩写表而不是按宽度切字：按宽度切会切出「学楼C509」「合楼B3027」
 * 这种半个词，缩写压完既短又成词。
 *
 * 缩写表覆盖不到的楼名原样保留，宁可让系统去截，也不由 App 决定删哪个字。
 *
 * ## 作用范围
 *
 * 缩略态与岛展开的「地点」行都用精简结果，两处都是窄条。
 * 通知正文（bigText）仍用课表原文，那里是整行排版，放得下完整地点。
 */
object ClassroomLabel {

    /** 内置规则：格式清理 + 内置楼名缩写表。 */
    const val METHOD_REGEX = 0

    /** AI 精简：格式清理 + 用户贴回来的楼名缩写表（内置表兜底）。 */
    const val METHOD_AI = 1

    const val KEY_ENABLED = "classroom_label_enabled"
    const val KEY_METHOD = "classroom_label_method"

    /**
     * AI 精简产出的缩写，每行一条「原名=缩写」。
     *
     * 由端上的 AI 一键生成后写进来（见设置页「精简方式 = AI 精简」那个按钮）。
     */
    const val KEY_ABBREVIATIONS = "classroom_label_abbreviations"

    /**
     * 内置的楼名缩写表（长的在前）。
     *
     * 顺序敏感：「第五教学楼」必须排在「教学楼」前面，否则前者会被后者先吃掉，
     * 变成「第五教」。同理「综合楼」「实验楼」要排在单字规则之前。
     */
    val BUILDING_ABBREVIATIONS = listOf(
        "第一教学楼" to "一教", "第二教学楼" to "二教", "第三教学楼" to "三教",
        "第四教学楼" to "四教", "第五教学楼" to "五教", "第六教学楼" to "六教",
        "第七教学楼" to "七教", "第八教学楼" to "八教", "第九教学楼" to "九教",
        "第十教学楼" to "十教",
        "教学楼" to "教",
        "综合楼" to "综",
        "实验楼" to "实",
        "图书馆" to "图",
        "体育馆" to "体",
        "办公楼" to "办",
        "宿舍楼" to "宿",
        "食堂" to "食",
        "阶梯教室" to "阶",
        "报告厅" to "报",
    )

    /** 校区前缀最多剥几层。合并办学时会叠（「新校区·南校区」），限制层数防死循环。 */
    private const val CAMPUS_STRIP_ROUNDS = 3

    /**
     * 「××校区 / ××校园 / ××学区」前缀。
     *
     * CJK 区间显式写成 `\u4E00-\u9FA5` 而不是 `\p{L}`：后者在 JS / Native 正则实现上支持度不一致，
     * 而 :core 的 commonMain 要过 linuxX64 编译门禁。
     */
    private val CAMPUS_PREFIX = Regex(
        "^[（(\\[【]?[\\u4E00-\\u9FA5A-Za-z0-9·\\-—]{0,8}(?:校区|校园|学区)[）)\\]】]?[·\\-—、,，:：]?"
    )

    /**
     * 成对的括号注解：「（新）」「(东)」「[北]」。
     *
     * 只认成对的，不配对的不动。用户地点里偶尔有「A302(1)」这种编号，
     * 那是信息不是注解。
     */
    private val BRACKET_NOTE = Regex("[（(\\[【][^）)\\]】]{1,6}[）)\\]】]")

    /**
     * 缩写「原名」的长度上限。
     *
     * 挡一类误操作：用户把提示词当结果粘回来时，提示词正文里带 `=` 的说明句
     * （「每行输出一条，格式为「原地点里的楼名=缩写」」）会被当成一条规则解析出来。
     * 那种「原名」长得不像楼名，直接丢掉。
     */
    const val MAX_ABBREV_ORIGIN = 16

    /** 从偏好文件读规则。所有键都不存在时得到「关闭」，与升级前的显示完全一致。 */
    fun read(store: KeyValueStore): ClassroomLabelRule = ClassroomLabelRule(
        enabled = store.getBoolean(KEY_ENABLED, false),
        method = store.getInt(KEY_METHOD, METHOD_REGEX),
        abbreviations = store.getString(KEY_ABBREVIATIONS, ""),
    )

    /**
     * 按 [rule] 生成缩略态地点文案。
     *
     * 关闭时结果与课表原文逐字相同。
     *
     * 两种方式互斥，各自只走自己的规则：
     * - [METHOD_REGEX]：格式清理（剥校区前缀、摘括号注解、丢空白、全角转半角）+ 内置缩写表
     * - [METHOD_AI]：只套 AI 给的「原名=缩写」对照表，**不跑上面那套正则**
     *
     * AI 方式刻意不叠正则：AI 是看着课表原文归纳的，键也是原文片段。若先被正则改写过
     * （剥了前缀、缩了楼名），AI 的键就对不上，表现为「AI 精简填了却没反应」。
     */
    fun apply(raw: String, rule: ClassroomLabelRule): String {
        if (raw.isBlank() || !rule.enabled) return raw
        return when (rule.method) {
            METHOD_AI -> replaceByTable(raw, rule.abbreviations)
            else -> abbreviatize(cleanUp(raw))
        }
    }

    /** 解析用户缩写文本。格式：每行 `原名=缩写`。空行、`#` 注释与不带分隔符的行忽略。 */
    fun parseAbbreviations(text: String): List<Pair<String, String>> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val sep = line.indexOfFirst { it == '=' || it == '：' || it == ':' }
            if (sep <= 0) continue
            val from = line.substring(0, sep).trim()
            val to = line.substring(sep + 1).trim()
            if (from.isEmpty() || to.isEmpty()) continue
            if (from.length > MAX_ABBREV_ORIGIN) continue
            // 缩写比原名还长就没有意义，直接忽略，免得用户以为生效了
            if (to.length >= from.length) continue
            result += from to to
        }
        return result
    }

    /** 只清格式，不按宽度丢字。 */
    private fun cleanUp(raw: String): String {
        var label = normalize(raw)
        if (label.isEmpty()) return raw

        // 先摘掉括号注解（「博学楼（新）A302」→「博学楼A302」）。
        // 放在剥离前缀之前做，否则「（新校区）J02西414」的括号会被前缀规则先吃掉一半。
        label = label.replace(BRACKET_NOTE, "")
        if (label.isEmpty()) return raw

        var rounds = 0
        while (rounds++ < CAMPUS_STRIP_ROUNDS) {
            val stripped = label.replaceFirst(CAMPUS_PREFIX, "")
            // 剥不动、或剥完什么都不剩（整串就是「新校区」）都停手
            if (stripped == label || stripped.isBlank()) break
            label = stripped
        }
        return label
    }

    /**
     * 楼名缩写：把「第五教学楼」压成「五教」这类。只走内置表。
     *
     * 按原词长度降序走，保证「第五教学楼」先于「教学楼」被替换。
     */
    private fun abbreviatize(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        for ((from, to) in BUILDING_ABBREVIATIONS) {
            if (from.isNotEmpty()) out = out.replace(from, to)
        }
        return out
    }

    /**
     * 按「原名=缩写」对照表逐条替换。只给 [METHOD_AI] 用。
     *
     * 做的是纯文本替换，不剥前缀、不缩楼名：AI 看到的原文是什么，键就该是什么。
     * 按原名长度降序，避免「第五教学楼」被更短的「教学楼」先吃掉。
     */
    private fun replaceByTable(text: String, table: String): String {
        val entries = parseAbbreviations(table)
        if (entries.isEmpty()) return text
        var out = text
        for ((from, to) in entries.sortedByDescending { it.first.length }) {
            out = out.replace(from, to)
        }
        return out
    }

    /**
     * 去掉全部空白、全角字母数字转半角（「Ｂ３０２７」这样的一串省下 4 个半角）。
     *
     * 空白在地点串里没有信息量（「3 号 楼 201」和「3号楼201」指同一间教室），
     * 留着占缩略态的宽度。
     */
    private fun normalize(raw: String): String = buildString(raw.length) {
        for (c in raw) {
            val half = toHalfWidth(c)
            if (half == ' ' || half == '\t' || half == '\n' || half == '\r') continue
            append(half)
        }
    }

    private fun toHalfWidth(c: Char): Char = when {
        c.code == 0x3000 -> ' '
        c.code in 0xFF01..0xFF5E -> (c.code - 0xFEE0).toChar()
        else -> c
    }
}

/**
 * [ClassroomLabel.apply] 的参数。默认值 = 关闭，即原样显示。
 *
 * @param enabled 关闭时结果就是课表原文。
 * @param method [ClassroomLabel.METHOD_REGEX] 或 [ClassroomLabel.METHOD_AI]。
 * @param abbreviations [ClassroomLabel.METHOD_AI] 下生效的「原名=缩写」文本。
 */
data class ClassroomLabelRule(
    val enabled: Boolean = false,
    val method: Int = ClassroomLabel.METHOD_REGEX,
    val abbreviations: String = "",
)
