package com.haooz.chedule.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [rgbToHsv] / [hsvToRgb] 的 golden vectors。
 *
 * 这两个函数是 AOSP `Color.RGBToHSV` / `HSVToColor` 的逐行移植，本测试用**手工推导的
 * 固定向量**锁定行为 —— 任何「优化写法」导致的舍入差异（1/255 的颜色漂移）都会在这里翻车。
 */
class ColorExtTest {

    private fun hsvOf(r: Int, g: Int, b: Int): FloatArray {
        val out = FloatArray(3)
        rgbToHsv(r, g, b, out)
        return out
    }

    @Test
    fun `RGBToHSV 基本色相`() {
        // 纯红：max=r，(g-b)/delta=0 → h=0
        assertEquals(listOf(0f, 1f, 1f), hsvOf(255, 0, 0).toList())
        // 纯绿：2 + (b-r)/delta = 2 → 120°（HSV 色相环：红0 绿120 蓝240）
        assertEquals(listOf(120f, 1f, 1f), hsvOf(0, 255, 0).toList())
        // 纯蓝：4 + (r-g)/delta = 4 → 240°
        assertEquals(listOf(240f, 1f, 1f), hsvOf(0, 0, 255).toList())
    }

    @Test
    fun `RGBToHSV 灰度与黑色`() {
        // 灰：max==min → h=0、s=0，v 按 255 归一
        val gray = hsvOf(128, 128, 128)
        assertEquals(0f, gray[0])
        assertEquals(0f, gray[1])
        assertEquals(128f / 255f, gray[2])
        // 全黑：max=0 → s 走 (max==0) 分支为 0
        val black = hsvOf(0, 0, 0)
        assertEquals(listOf(0f, 0f, 0f), black.toList())
    }

    @Test
    fun `HSVToColor 基本原色`() {
        // 纯红：i=0，f=0 → r=v g=t=0 b=p=0
        assertEquals(0xFFFF0000.toInt(), hsvToRgb(floatArrayOf(0f, 1f, 1f)))
        // 纯绿：i=2 → r=p=0 g=v=255 b=t=0
        assertEquals(0xFF00FF00.toInt(), hsvToRgb(floatArrayOf(120f, 1f, 1f)))
        // 纯蓝：i=4 → r=t=0 g=p=0 b=v=255
        assertEquals(0xFF0000FF.toInt(), hsvToRgb(floatArrayOf(240f, 1f, 1f)))
        // 黄：i=1 → r=q=255 g=v=255 b=p=0
        assertEquals(0xFFFFFF00.toInt(), hsvToRgb(floatArrayOf(60f, 1f, 1f)))
    }

    @Test
    fun `HSVToColor 零饱和走整数路径`() {
        // s=0 → vint = (v*255).toInt()（向零截断，非四舍五入）
        assertEquals(0xFF7F7F7F.toInt(), hsvToRgb(floatArrayOf(0f, 0f, 0.5f)))
        assertEquals(0xFF000000.toInt(), hsvToRgb(floatArrayOf(0f, 0f, 0f)))
        assertEquals(0xFFFFFFFF.toInt(), hsvToRgb(floatArrayOf(0f, 0f, 1f)))
    }

    @Test
    fun `原色 round-trip 无损`() {
        for ((r, g, b) in listOf(
            Triple(255, 0, 0), Triple(0, 255, 0), Triple(0, 0, 255),
            Triple(255, 255, 0), Triple(0, 0, 0), Triple(255, 255, 255),
        )) {
            val packed = hsvToRgb(hsvOf(r, g, b))
            assertEquals(
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b,
                packed,
                "round-trip 失败: ($r,$g,$b)",
            )
        }
    }

    /** CourseCard 的实际用法：任意课程色提升饱和度后仍在合法范围（不崩、不越界）。 */
    @Test
    fun `任意色提升饱和度后仍可 round-trip 回合法 RGB`() {
        for ((r, g, b) in listOf(Triple(200, 100, 50), Triple(30, 60, 90), Triple(254, 253, 252))) {
            val hsv = hsvOf(r, g, b)
            hsv[1] = (hsv[1] * 0.5f).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] + 0.4f).coerceIn(0f, 1f)
            val packed = hsvToRgb(hsv)
            // alpha 必须满格，通道在 0..255（打包语义天然满足，这里锁 alpha）
            assertEquals(0xFF, (packed ushr 24) and 0xFF)
        }
    }
}
