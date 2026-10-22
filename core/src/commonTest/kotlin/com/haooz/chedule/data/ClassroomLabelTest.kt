package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ClassroomLabel] 的行为锁定。
 *
 * 两组真实反馈串是重点：`2号综合楼B3027` 被系统截成 `2号综合楼B3`（issue #69），
 * `新校区J02西414` 被截成 `新校区J02西4`（前缀把房号挤出去了）。
 *
 * 另外固化一条设计红线：精简只做格式清理与缩写，房号与楼栋永远不由程序删；
 * 缩写表覆盖不到的楼名原样保留。`超宽也不丢字样` 钉的就是这条，不能省。
 */
class ClassroomLabelTest {

    private val regexRule = ClassroomLabelRule(enabled = true, method = ClassroomLabel.METHOD_REGEX)

    private fun ai(abbreviations: String) = ClassroomLabelRule(
        enabled = true,
        method = ClassroomLabel.METHOD_AI,
        abbreviations = abbreviations,
    )

    private fun shorten(raw: String): String = ClassroomLabel.apply(raw, regexRule)

    @Test
    fun `关闭时与原文逐字相同`() {
        // 升级后的老用户没有这份偏好，行为必须一点不变
        val rule = ClassroomLabelRule()
        for (raw in listOf("新校区J02西414", "第五教学楼C509", "2号综合楼B3027")) {
            assertEquals(raw, ClassroomLabel.apply(raw, rule))
        }
    }

    @Test
    fun `空串与空白串原样返回`() {
        assertEquals("", shorten(""))
        assertEquals("   ", shorten("   "))
    }

    @Test
    fun `剥掉校区前缀且房号完整`() {
        // 前缀 6 个半角去掉后，楼栋 J02 与房号西414 全在
        assertEquals("J02西414", shorten("新校区J02西414"))
    }

    @Test
    fun `把常见楼名缩成成词的形式`() {
        // 不做缩写的话只能靠宽度裁，会切出「学楼」「合楼」这种半个词
        assertEquals("五教C509", shorten("第五教学楼C509"))
        assertEquals("五教C509", shorten("南校区第五教学楼C509"))
        assertEquals("二教A302", shorten("东校区第二教学楼A302"))
        assertEquals("图B1-101", shorten("图书馆B1-101"))
        assertEquals("实D区203", shorten("实验楼D区203"))
    }

    @Test
    fun `缩写不能把长楼名吃成半截`() {
        // 「第五教学楼」必须排在「教学楼」前面，否则会变成不合语感的「第五教」
        assertEquals("五教C509", shorten("第五教学楼C509"))
        assertEquals("十教C509", shorten("第十教学楼C509"))
        // 已经缩到「二教」的输入不能再被动一次
        assertEquals("二教A302", shorten("二教A302"))
    }

    @Test
    fun `缩写要认得没带序号的教学楼`() {
        assertEquals("教A302", shorten("教学楼A302"))
    }

    @Test
    fun `摘掉括号注解`() {
        // 「（新）」「(东)」这类注解是补充说明，留着占缩略态的宽度
        assertEquals("博学楼A302", shorten("博学楼（新）A302"))
    }

    @Test
    fun `超宽也不丢字样`() {
        // 缩写表覆盖不到的楼名保持原样，不按宽度丢字
        assertEquals("博学楼A302", shorten("博学楼（新）A302"))
        assertEquals("2号综B3027", shorten("2号综合楼B3027"))
    }

    @Test
    fun `支持括号与分隔符包裹的前缀`() {
        assertEquals("J02西414", shorten("（新校区）J02西414"))
        assertEquals("博学楼A302", shorten("[南校区]博学楼A302"))
        assertEquals("博学楼A302", shorten("东校区-博学楼A302"))
        assertEquals("博学楼A302", shorten("新校区·博学楼A302"))
    }

    @Test
    fun `整串就是一个校区名时保留原文`() {
        // 剥完剩下空串没有意义，退回原文而不是显示空白
        assertEquals("新校区", shorten("新校区"))
        assertEquals("南校区", shorten("南校区"))
    }

    @Test
    fun `全角字母数字转半角`() {
        assertEquals("B3027", shorten("Ｂ３０２７"))
    }

    @Test
    fun `丢掉空白`() {
        assertEquals("博A201", shorten("  博  A201 "))
        assertEquals("3号楼201", shorten("3 号 楼 201"))
    }

    @Test
    fun `不动本来就放得下的地点`() {
        assertEquals("博A201", shorten("博A201"))
        assertEquals("教三201", shorten("教三201"))
    }

    @Test
    fun `AI 方式按对照表改写`() {
        // 「明德楼」不在内置表里，靠 AI 给的这条才压得下来
        assertEquals("明德A302", ClassroomLabel.apply("明德楼A302", ai("明德楼=明德")))
    }

    @Test
    fun `AI 方式不跑正则那一套`() {
        // 这是刻意分开的：AI 看着课表原文归纳，键也是原文片段。若先剥前缀、缩楼名，
        // AI 的键就对不上，表现为「AI 精简填了却没反应」。
        val raw = "新校区第五教学楼C509"
        // 没有对照表时一个字都不动（不剥前缀、不缩楼名）
        assertEquals(raw, ClassroomLabel.apply(raw, ai("")))
        // 有对照表时只替换命中部分，前缀照旧留着
        assertEquals(
            "新校区五教C509",
            ClassroomLabel.apply(raw, ai("第五教学楼=五教")),
        )
    }

    @Test
    fun `AI 方式的键按课表原文写`() {
        // 键要带前缀才匹配得上（因为不剥前缀）；写成剥过的形态反而不生效
        assertEquals(
            "J02西414",
            ClassroomLabel.apply("新校区J02西414", ai("新校区J02西=J02西")),
        )
        assertEquals(
            "新校区J02西414",
            ClassroomLabel.apply("新校区J02西414", ai("J02西=J02西")),
        )
    }

    @Test
    fun `正则方式不套用 AI 对照表`() {
        // 选了正则方式时，AI 留下的对照表不该偷偷生效
        val rule = regexRule.copy(abbreviations = "明德楼=明德")
        assertEquals("明德楼A302", ClassroomLabel.apply("明德楼A302", rule))
    }

    @Test
    fun `两种方式都关闭时完全不动`() {
        for (method in listOf(ClassroomLabel.METHOD_REGEX, ClassroomLabel.METHOD_AI)) {
            val rule = ClassroomLabelRule(
                enabled = false,
                method = method,
                abbreviations = "第五教学楼=五教",
            )
            assertEquals("新校区第五教学楼C509", ClassroomLabel.apply("新校区第五教学楼C509", rule))
        }
    }

    @Test
    fun `解析缩写文本`() {
        val parsed = ClassroomLabel.parseAbbreviations(
            """
            # 这是注释
            第五教学楼=五号楼
            实验楼：实验
            这行没有等号
            =只有缩写
            只有原名=
            等长替换=等长替换
            """.trimIndent(),
        )
        // 「实验楼：实验」用中文冒号也要认；等长替换被丢弃（缩了等于没缩）
        assertEquals(listOf("第五教学楼" to "五号楼", "实验楼" to "实验"), parsed)
    }

    @Test
    fun `读取偏好默认关闭`() {
        assertEquals(ClassroomLabelRule(), ClassroomLabel.read(InMemoryKeyValueStore()))
    }

    @Test
    fun `读出来的规则直接可用`() {
        // 回归用：设置页开了开关、选了 AI 方式后，通知链路读出来的规则必须真的生效。
        // 曾经 Android 侧手抄字段漏掉键，界面改了通知没反应，而编译期没有任何提示。
        val store = InMemoryKeyValueStore(
            mapOf(
                ClassroomLabel.KEY_ENABLED to true,
                ClassroomLabel.KEY_METHOD to ClassroomLabel.METHOD_AI,
                ClassroomLabel.KEY_ABBREVIATIONS to "明德楼=明德",
            ),
        )
        assertEquals("明德A302", ClassroomLabel.apply("明德楼A302", ClassroomLabel.read(store)))
    }
}
