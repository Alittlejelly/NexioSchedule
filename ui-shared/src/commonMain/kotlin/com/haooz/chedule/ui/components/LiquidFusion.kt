package com.haooz.chedule.ui.components

/**
 * 融球（metaball）参数的唯一来源：两团液态玻璃靠近时「鼓包 → 拉颈 → 并体」的全部可调量。
 *
 * 一口气描述三个阶段的量都在这里，因为它们彼此联动：
 * - 未接触：[bulgeRangeDp] 之内就开始鼓山丘，山丘高度由 [bulgeMaxDp] 决定；
 * - 刚接触：颈出现的阈值恒等于 `2 × bulgeMaxDp`（两座山丘正好碰上），不单独给参数；
 * - 已重叠：颈宽度由 [neckOverlapGrow] 接管，重叠判定的提前量是 [overlapLeadDp]。
 *
 * 同一份实例必须同时喂给形状（[BarShape]）和折射（[barNeckGeometryPx] +
 * [BottomBarRefractionShader]）—— 两边各算一套必然对不上，颈边会留一条错位缝。
 *
 * 默认值就是首页底栏当前的手感。改任何一个字段都要连带看四种状态：没接近、刚鼓、细颈、已重叠。
 */
data class LiquidFusion(
    /**
     * 颈半高 / 参考尺寸（底栏传的是容器高度）。越大颈越粗、融合越明显；
     * 上限被本体半径夹住，所以调大到某个值之后只剩「重叠后腰粗」这一个可见变化。
     */
    val neckStrength: Float = 0.36f,
    /** 山丘从多远开始鼓包（dp）。太大会在明显没靠近时就鼓起来 */
    val bulgeRangeDp: Float = 10f,
    /** 山丘最大隆起高度（dp）。颈的阈值 = 2× 本值，所以它同时决定「多近才拉丝」 */
    val bulgeMaxDp: Float = 2.6f,
    /** 山丘半宽 / 高度，换算成圆弧张角 `phi = halfW / r` */
    val bulgeWidthRatio: Float = 10f,
    /** 山丘张角下限（rad）。再窄就不是包，是小尖刺 */
    val bulgeMinAngle: Float = 0.35f,
    /** 山丘张角上限（rad）。在细本体上 `halfW / r` 会算得过大，得封顶 */
    val bulgeMaxAngle: Float = 1.2f,
    /** 山丘钟形胖瘦（高斯 sigma，相对张角 0..1） */
    val bulgeSigma: Float = 0.2f,
    /** 重叠态：每单位自然交点距离额外增加的颈半宽，决定重叠后腰变粗的快慢 */
    val neckOverlapGrow: Float = 0.35f,
    /** 相交判定的提前量（dp）。让重叠段在真正相切之前接管，消除两段曲线的换挡点 */
    val overlapLeadDp: Float = 4f,
    /** 融合强度曲线的指数。越小，贴住阈值的头一段桥越粗 */
    val rampPower: Float = 0.22f,
) {
    companion object {
        /** 首页底栏在用的那一套。全局改手感就改这里 */
        val Default = LiquidFusion()
    }
}
