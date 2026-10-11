package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 本地备份「恢复门禁」[parseBackupPayload] 的**真实备份**回归测试。
 *
 * ## 背景：R2 序列化通道 + 用户数据安全
 * [parseBackupPayload] 原用 Gson，2026-10-11 换 kotlinx-serialization。它是**恢复门禁**：
 * 全量恢复会先清空全部课表再写入，若门禁误放行无关 JSON，用户课表会被清空。
 * 本测试用两份**用户真实备份**固化「换库后门禁行为不变」—— 这是数据兼容的直接证明。
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
}
