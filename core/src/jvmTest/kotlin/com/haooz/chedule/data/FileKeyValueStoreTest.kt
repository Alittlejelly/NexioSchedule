package com.haooz.chedule.data

import com.haooz.chedule.data.storage.FileKeyValueStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [FileKeyValueStore]（桌面实现）的契约回归。
 *
 * 语义断言与 [KeyValueStoreRoundTripTest]（基于 InMemory）保持同一口径 ——
 * 任何 KeyValueStore 实现都必须通过同一组断言。本文件额外覆盖**只有文件实现才有的风险**：
 * 类型标签落盘 / 跨实例持久化 / 文件损坏隔离。
 */
class FileKeyValueStoreTest {

    /** 每个用例独立临时目录，互不污染，跑完即弃。 */
    private fun withStore(block: (FileKeyValueStore, File) -> Unit) {
        val dir = File(
            System.getProperty("java.io.tmpdir"),
            "nexio-kvtest-${System.nanoTime()}",
        )
        try {
            val file = File(dir, "test_prefs.json")
            block(FileKeyValueStore(file), file)
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 与 InMemory 同口径的契约断言 ──────────────────────────────────────────

    @Test
    fun `写入后能原样读出各类型`() = withStore { s, _ ->
        s.edit {
            putString("name", "计算机学院")
            putInt("week", 20)
            putLong("version", 1_700_000_000_000L)
            putFloat("scale", 1.5f)
            putBoolean("enabled", true)
            putStringSet("days", setOf("周一", "周三"))
        }

        assertEquals("计算机学院", s.getString("name", ""))
        assertEquals(20, s.getInt("week", 0))
        assertEquals(1_700_000_000_000L, s.getLong("version", 0L))
        assertEquals(1.5f, s.getFloat("scale", 0f))
        assertTrue(s.getBoolean("enabled", false))
        assertEquals(setOf("周一", "周三"), s.getStringSet("days", emptySet()))
    }

    @Test
    fun `不存在的键返回调用方给的默认值`() = withStore { s, _ ->
        assertEquals("x", s.getString("缺失", "x"))
        assertEquals(7, s.getInt("缺失", 7))
        assertEquals(7L, s.getLong("缺失", 7L))
        assertEquals(7f, s.getFloat("缺失", 7f))
        assertFalse(s.getBoolean("缺失", false))
    }

    @Test
    fun `数字类型窄化与放大都安全`() = withStore { s, _ ->
        s.edit { putLong("n", 42L) }
        assertEquals(42, s.getInt("n", -1))
        s.edit { putInt("n", 42) }
        assertEquals(42L, s.getLong("n", -1L))
    }

    @Test
    fun `getStringSet 返回副本可就地修改`() = withStore { s, _ ->
        s.edit { putStringSet("days", setOf("周一")) }
        val got = s.getStringSet("days", emptySet())
        (got as MutableSet).add("周三")
        assertEquals(setOf("周一"), s.getStringSet("days", emptySet()), "返回值必须是副本，不能反写存储")
    }

    @Test
    fun `all 返回快照改动不影响存储`() = withStore { s, _ ->
        s.edit { putInt("a", 1) }
        val snapshot = s.all()
        (snapshot as MutableMap)["a"] = 999
        assertEquals(1, s.getInt("a", 0), "all() 必须返回拷贝")
    }

    @Test
    fun `edit 内 remove 与 clear 可与其他写入混用且保序`() = withStore { s, _ ->
        // put 在前 clear 在后 → clear 赢（与 InMemory / SharedPreferences 操作序一致）
        s.edit {
            putString("x", "1")
            clear()
        }
        assertTrue(s.all().isEmpty(), "put 之后的 clear 必须把 put 清掉")

        s.edit {
            clear()
            putString("y", "2")
        }
        assertEquals("2", s.getString("y", ""))
        assertFalse(s.contains("x"))
    }

    @Test
    fun `remove 与 clear 生效`() = withStore { s, _ ->
        s.edit { putString("a", "1"); putString("b", "2") }
        s.remove("a")
        assertFalse(s.contains("a"))
        assertTrue(s.contains("b"))
        s.clear()
        assertTrue(s.all().isEmpty())
    }

    // ── 文件实现特有的断言 ────────────────────────────────────────────────────

    /** 落盘 → 新实例读回，值和**类型**都必须一致（类型标签的核心目的）。 */
    @Test
    fun `跨实例持久化且类型标签不丢`() = withStore { s, file ->
        s.edit {
            putInt("copy_int", 42)          // 复制课表按 is Int 分支
            putLong("copy_long", 42L)       // 同一个数字，类型必须区分
            putFloat("f", 1.5f)
            putBoolean("b", true)
            putStringSet("set", setOf("a", "b"))
            putString("s", "值")
        }

        val reopened = FileKeyValueStore(file)
        val all = reopened.all()

        // is Int / is Long 的分支匹配 —— 判错类型 = 复制课表静默丢设置
        assertEquals(42, all["copy_int"], "Int 必须以 Int 还原")
        assertEquals(42L, all["copy_long"], "Long 必须以 Long 还原")
        assertTrue(all["copy_int"] is Int, "all() 里 copy_int 必须是 Int")
        assertTrue(all["copy_long"] is Long, "all() 里 copy_long 必须是 Long")
        assertEquals(1.5f, all["f"])
        assertEquals(true, all["b"])
        assertEquals(setOf("a", "b"), all["set"])
        assertEquals("值", all["s"])
    }

    /** 备份 → 清空 → 恢复 的完整往返（exportAllPreferences 的数据通路）。 */
    @Test
    fun `导出再导入结果一致`() = withStore { s, file ->
        s.edit {
            putString("course", "数据结构")
            putInt("sectionCount", 14)
            putBoolean("remindEnabled", true)
            putStringSet("weekdays", setOf("1", "3", "5"))
        }
        val backup = LinkedHashMap(s.all())
        s.clear()
        assertTrue(s.all().isEmpty())

        // 恢复：另一个实例往同一文件写（模拟 restoreAllPreferences 的写回）
        val restored = FileKeyValueStore(file)
        restored.edit {
            backup.forEach { (k, v) ->
                when (v) {
                    is String -> putString(k, v)
                    is Int -> putInt(k, v)
                    is Long -> putLong(k, v)
                    is Float -> putFloat(k, v)
                    is Boolean -> putBoolean(k, v)
                    is Set<*> -> putStringSet(k, v.map { it.toString() }.toSet())
                    else -> Unit
                }
            }
        }
        assertEquals(backup, FileKeyValueStore(file).all(), "round-trip 后二次导出必须与首次一致")
    }

    /** 中文 / 引号 / 换行等特殊字符必须原样穿过文件。 */
    @Test
    fun `特殊字符落盘后原样读回`() = withStore { s, file ->
        val tricky = "引号\" 反斜杠\\ 换行\n 回车\r 制表\t 中文🙂 <&>"
        s.edit { putString("t", tricky) }
        assertEquals(tricky, FileKeyValueStore(file).getString("t", ""))
    }

    /** 损坏文件必须隔离保留现场，不静默清空（本项目最忌讳静默丢数据）。 */
    @Test
    fun `损坏文件被隔离而不是静默清空`() = withStore { _, file ->
        file.parentFile?.mkdirs()
        file.writeText("{ 这不是合法 JSON")

        val s = FileKeyValueStore(file)
        assertTrue(s.all().isEmpty(), "损坏库按空库启动")

        val leftovers = file.parentFile!!.listFiles()!!.map { it.name }
        assertTrue(
            leftovers.any { it.contains(".corrupt-") },
            "坏文件必须保留为 .corrupt-* 供排查，实际: ${leftovers.joinToString()}",
        )
        assertFalse(file.exists(), "坏文件本体应已被移走")
    }

    /** remove 一个不存在的键不应触发写盘（避免无谓 IO）—— 行为面则要求不抛异常。 */
    @Test
    fun `remove 不存在的键不抛异常`() = withStore { s, _ ->
        s.remove("从未写入")
        assertTrue(s.all().isEmpty())
    }
}
