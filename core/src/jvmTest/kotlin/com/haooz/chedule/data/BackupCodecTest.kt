package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 本地备份「恢复门禁」[parseBackupPayload] 的**真实备份**回归测试。
 *
 * [parseBackupPayload] 原用 Gson，2026-10-11 换 kotlinx-serialization。它是**恢复门禁**：
 * 全量恢复会先清空全部课表再写入，若门禁误放行无关 JSON，用户课表会被清空。
 * 本测试用两份**用户真实备份**固化「换库后门禁行为不变」—— 数据兼容的直接证明。
 *
 * 夹具（`core/src/jvmTest/resources/`，2026-10-11 从本机导出的冻结快照）：
 * - `backup_full_export.json`：全量备份（扁平 `schedule_*` 键值）→ 应识别为 [BackupPayload.Full]
 * - `backup_single_schedule.json`：单课表备份「数字经济3」（23 门课）→ 应识别为 [BackupPayload.Single]
 */
class BackupCodecTest {

    private fun loadFixture(name: String): String {
        val stream = javaClass.getResourceAsStream(name)
            ?: error("找不到测试夹具 $name")
        return stream.use { it.readBytes() }.toString(Charsets.UTF_8)
    }

    @Test
    fun `全量备份被识别为 Full 且通过结构校验`() {
        val json = loadFixture("/backup_full_export.json")
        val payload = parseBackupPayload(json)
        assertTrue(payload is BackupPayload.Full, "真实全量备份应识别为 Full，实际：$payload")
        // Full.data 应保留原始扁平键（供 importAllPreferences 写回）
        assertTrue(payload.data.keys.any { it.startsWith("schedule_") }, "Full.data 应含 schedule_* 键")
    }

    @Test
    fun `单课表备份被识别为 Single 且字段正确`() {
        val json = loadFixture("/backup_single_schedule.json")
        val payload = parseBackupPayload(json)
        assertTrue(payload is BackupPayload.Single, "真实单课表备份应识别为 Single，实际：$payload")
        payload as BackupPayload.Single
        assertEquals("数字经济3", payload.scheduleName, "课表名应逐字保留")
        assertEquals(23, payload.courses.size, "课程数量应与真实备份一致（23 门）")

        val settings = payload.semesterSettings
        assertNotNull(settings, "单课表备份应解析出学期设置")
        assertEquals(20, settings.totalWeeks, "总周数应为 20")
        assertNotNull(settings.classStartTime, "开学日期应解析成功")
        // normalizeClassStartDate 归一化后的格式（"2026/09/02" → 内部标准格式），只需非空且含年份
        assertTrue(settings.classStartTime!!.startsWith("2026"), "开学日期年份应为 2026")
    }

    @Test
    fun `非法输入抛 IllegalArgumentException 而非静默放行`() {
        // 门禁语义：不认识的 JSON 必须拒绝，否则全量恢复会清空用户课表
        assertFailsWith<IllegalArgumentException> { parseBackupPayload("{\"foo\":1}") }
        assertFailsWith<IllegalArgumentException> { parseBackupPayload("not json at all") }
        // 普通课表 JSON（含 settings 顶层键）应被拒（走错入口）
        assertFailsWith<IllegalArgumentException> {
            parseBackupPayload("{\"settings\":{},\"courses\":[]}")
        }
    }

    @Test
    fun `encodeBackupJson 写出的单课表备份能被恢复门禁读回（写读 round-trip）`() {
        // 构造单课表备份 Map（含 time_config 走 toJsonMap，含一个 null 值验证剔除）
        val timeConfig = TimeConfig(morningSections = 5, afternoonSections = 4, eveningSections = 3)
        val backup = mapOf<String, Any?>(
            "schedule_name" to "测试表",
            "semester_settings" to mapOf(
                "class_start_time" to "2026/09/02",
                "current_week" to 6,
                "total_weeks" to 20,
                "smart_weekend" to true,
            ),
            "courses" to listOf(
                mapOf(
                    "name" to "高等数学", "classroom" to "A101", "teacher" to "张三",
                    "dayOfWeek" to 1, "startSection" to 1, "endSection" to 2,
                    "startWeek" to 1, "endWeek" to 16, "weekType" to 0,
                    "selectedWeeks" to listOf(1, 2, 3),
                    "nullField" to null,   // 应被 skip-nulls 剔除
                )
            ),
            "time_config" to timeConfig.toJsonMap(),
        )

        val json = encodeBackupJson(backup)
        // skip-nulls：null 字段不出现在输出（等价 Gson 默认行为）
        assertTrue(!json.contains("nullField"), "null 字段应被剔除：$json")

        // 写出的 JSON 能被恢复门禁读回 → Single
        val payload = parseBackupPayload(json)
        assertTrue(payload is BackupPayload.Single, "写读 round-trip 后应为 Single，实际：$payload")
        payload as BackupPayload.Single
        assertEquals("测试表", payload.scheduleName)
        assertEquals(1, payload.courses.size)
        assertEquals("高等数学", payload.courses[0]["name"])
        assertEquals(20, payload.semesterSettings?.totalWeeks)

        // time_config 经 toJsonMap → 写 → parse → timeConfigFromJsonMap 应无损还原关键字段
        @Suppress("UNCHECKED_CAST")
        val writtenTc = payload.timeConfig as? Map<String, Any?>
        assertNotNull(writtenTc, "time_config 应被写读保留")
        val restored = timeConfigFromJsonMap(writtenTc!!)
        assertEquals(5, restored.morningSections, "morningSections 应 round-trip 一致")
        assertEquals(3, restored.eveningSections, "eveningSections 应 round-trip 一致")
    }

    @Test
    fun `真实备份文件经 encodeBackupJson 重写后仍可被门禁读回`() {
        // 真实单课表备份 → parse → 重新 encode → 再 parse，课程数稳定（证明写侧不破坏读侧）
        val original = loadFixture("/backup_single_schedule.json")
        val payload1 = parseBackupPayload(original)
        assertTrue(payload1 is BackupPayload.Single)
        payload1 as BackupPayload.Single
        // 用原备份的结构重建 Map 再编码（简化：直接编码 courses + semester 字段）
        val rebuilt = mapOf<String, Any?>(
            "schedule_name" to payload1.scheduleName,
            "semester_settings" to mapOf("total_weeks" to (payload1.semesterSettings?.totalWeeks ?: 20)),
            "courses" to payload1.courses,
        )
        val json2 = encodeBackupJson(rebuilt)
        val payload2 = parseBackupPayload(json2)
        assertTrue(payload2 is BackupPayload.Single)
        assertEquals(payload1.courses.size, (payload2 as BackupPayload.Single).courses.size, "重写后课程数不变")
    }
}
