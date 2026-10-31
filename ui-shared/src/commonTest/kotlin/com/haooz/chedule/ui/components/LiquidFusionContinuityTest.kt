package com.haooz.chedule.ui.components

import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 融球山丘端点的连续性回归（[fusionBumpAt]，双胶囊焊缝在用）。
 *
 * 归一化高斯在张角端点只是函数值为 0，一阶导不为 0，山丘根部与本体之间会留一道折角；
 * 乘上 `1-(2t-1)^4` 窗之后端点值与斜率同时归零。这里不渲染，只验这两条不变量。
 */
class LiquidFusionContinuityTest {

    private val fusion = LiquidFusion.Default

    @Test
    fun hillRootHasZeroSlope() {
        val phi = 0.6f
        val sigma = fusion.bulgeSigma
        val gaussEdge = exp(-0.25f / (2f * sigma * sigma))
        val h = 0.01f
        // 张角端点（t=1）函数值为 0；再往里一点斜率也应接近 0。
        // 旧的高斯在端点斜率约 0.5，山丘根部会留一道折角。
        val atEdge = fusionBumpAt(phi, 0f, phi, sigma, gaussEdge)
        val justInside = fusionBumpAt(phi - h, 0f, phi, sigma, gaussEdge)
        assertTrue(abs(atEdge) < 1e-6f, "端点值应为 0，实际 $atEdge")
        assertTrue(
            (justInside - atEdge) / h < 0.05f,
            "端点斜率应接近 0，实际 ${(justInside - atEdge) / h}",
        )
    }
}
