package com.haooz.chedule.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 材质画布相对可见底栏向外的余量。
 *
 * `drawBackdrop` 采样时 `clipRect(0,0,w,h)`、表面色也只铺节点矩形，形状画到节点外的部分
 * 既没采样也没底色。而胶囊按压缩放、圆钮上下拖动都要越界，所以画布做大一圈，
 * [BarShape] 内部再把原点补回来。
 */
internal val MaterialBleedX = 16.dp
internal val MaterialBleedY = 28.dp

/** 分离态：颈半高 / 底栏高度。越大颈越粗、融合越明显 */
internal const val NeckStrength = 0.36f

/** 重叠态：每单位「自然交点距离 hh」额外增加的颈半宽，决定重叠后腰变粗的快慢 */
private const val NeckOverlapGrow = 0.35f

/** 相交判定的提前量（dp），让重叠段在真正相切之前接管，消除两段曲线的换挡点 */
private const val FusionOverlapLeadDp = 4f

/** 融合强度曲线的指数：越小，贴住阈值的头一段桥越粗 */
private const val FusionRampPower = 0.22f

/** 山丘凸起：从多远开始鼓包（dp） */
private const val FusionBulgeRangeDp = 10f

/** 山丘凸起：最大隆起高度（dp） */
private const val FusionBulgeMaxDp = 2.6f

/** 山丘凸起：隆起半宽 / 高度，换算成圆弧张角 `phi = halfW / r` */
private const val FusionBulgeWidthRatio = 10f

/** 山丘凸起：张角下限（rad），再窄就成了小尖包 */
private const val FusionBulgeMinAngle = 0.35f

/** 山丘凸起的钟形胖瘦（高斯 sigma，相对张角 0..1） */
private const val FusionBulgeSigma = 0.2f

/** [BarShape] 的参数：全是 draw 期才读的 lambda，拖动 / 按压都不进组合期 */
internal class BarShapeParams(
    val panelOffset: () -> Float,
    /** 胶囊按压缩放，只作用于胶囊 */
    val pressScale: () -> Float,
    val trailingTranslation: () -> Offset,
    /** 圆钮的按压缩放 + 拉伸倍率（rx / ry，1 = 静止未按下） */
    val trailingScale: () -> Offset,
    /** 圆钮占掉的额外宽度（间距 + 直径 + 拖动余量），px；0 = 没有圆钮 */
    val extraPx: Float,
    /** 胶囊与圆钮的静止间距，px */
    val gapPx: Float,
    /** 融球颈的最大半高，px */
    val maxNeckHalfPx: Float,
    /** 画布向外的余量，px；形状内部按它把原点补回来 */
    val bleedX: Float,
    val bleedY: Float,
)

/**
 * 整条底栏的轮廓：胶囊 ∪ 圆钮 ∪ 融球颈。
 *
 * 三者并进同一条 Path，所以整块材质是一个轮廓 —— 折射、高光、描边都沿融合后的边缘走，
 * 交界处不会出现两套各自独立的边。
 */
internal class BarShape(private val p: BarShapeParams) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val h = size.height - p.bleedY * 2f
        val bx = p.bleedX
        val cy = p.bleedY + h / 2f
        val radius = h / 2f
        val pillPx = size.width - p.extraPx - bx * 2f

        val s = p.pressScale()
        val po = p.panelOffset()
        val halfW = pillPx / 2f * s
        val halfH = h / 2f * s
        val cx = bx + pillPx / 2f + po
        val pillRadius = minOf(halfW, halfH)

        val capsule = Path().apply {
            addRoundRect(
                RoundRect(
                    left = cx - halfW,
                    top = cy - halfH,
                    right = cx + halfW,
                    bottom = cy + halfH,
                    radiusX = pillRadius,
                    radiusY = pillRadius,
                )
            )
        }
        if (p.extraPx <= 0f) return Outline.Generic(capsule)

        val t = p.trailingTranslation()
        // 沿拖动方向拉伸后是 rx ≠ ry 的椭圆
        val ts = p.trailingScale()
        val circleRx = radius * ts.x
        val circleRy = radius * ts.y
        val circleCx = bx + pillPx + p.gapPx + radius + t.x
        val circleCy = cy + t.y
        val circle = Path().apply {
            addOval(Rect(circleCx - circleRx, circleCy - circleRy, circleCx + circleRx, circleCy + circleRy))
        }

        val merged = Path()
        merged.op(capsule, circle, PathOperation.Union)

        // 融合强度只看两团材质表面之间的最短距离，不看拖动位移：
        // 胶囊是「轴线膨胀 pillRadius」，圆钮是椭圆，用轴线最近点计算，
        // 圆钮被拖到胶囊正上 / 正下方时同样成立。按 tab、整体放大这类「形状变了但没靠近」
        // 的情况因此不会凭空长出颈。
        val axisLeft = cx - halfW + pillRadius
        val axisRight = cx + halfW - pillRadius
        val axisX = circleCx.coerceIn(minOf(axisLeft, axisRight), maxOf(axisLeft, axisRight))
        val vx = circleCx - axisX
        val vy = circleCy - cy
        val dist = sqrt(vx * vx + vy * vy)
        val ang = atan2(vy, vx)

        /** 椭圆在角度 [a] 方向上的极半径（不是参数方程的 t）。rx == ry 时退化成正圆。 */
        fun ellipseRadiusAt(rx: Float, ry: Float, a: Float): Float {
            val c = cos(a)
            val s = sin(a)
            val d = (ry * c) * (ry * c) + (rx * s) * (rx * s)
            return if (d <= 1e-6f) 0f else rx * ry / sqrt(d)
        }

        val circleAvgR = (circleRx + circleRy) / 2f
        // 沿连线方向量间隙：纵向拉伸后（ry > rx）用平均半径会把间隙算小，山丘和桥提前出现
        val circleDirR = ellipseRadiusAt(circleRx, circleRy, ang)
        val surfaceGap = dist - pillRadius - circleDirR
        val overlapLeadPx = with(density) { FusionOverlapLeadDp.dp.toPx() }
        // 相交判定整体外移到吸附范围之外，gap 从 10dp 一路贴近到重叠都由同一个量驱动，
        // hh 在 gap = lead 处恰为 0，桥与山丘同时从零宽起步。
        val effDist = (dist - overlapLeadPx).coerceAtLeast(1f)
        val intersectHh = if (effDist < pillRadius + circleDirR) {
            val proj =
                (effDist * effDist + pillRadius * pillRadius - circleDirR * circleDirR) / (2f * effDist)
            sqrt((pillRadius * pillRadius - proj * proj).coerceAtLeast(0f))
        } else {
            0f
        }
        // 阈值 = 2 × 山丘最大高度，让「两座山丘碰上」与「桥出现」落在同一时刻
        val neckThresholdPx = with(density) { (FusionBulgeMaxDp * 2f).dp.toPx() }
        val k = if (surfaceGap >= neckThresholdPx) {
            0f
        } else {
            ((neckThresholdPx - surfaceGap) / neckThresholdPx).coerceIn(0f, 1f).pow(FusionRampPower)
        }
        if (dist < 0.5f) return Outline.Generic(merged)
        val anchorX = axisX
        // 连线方向 / 法向单位向量，吸附凸起与融合桥共用
        val ux = vx / dist
        val uy = vy / dist
        val nx = -uy
        val ny = ux
        val pi = PI.toFloat()

        // 山丘强度只由间隙决定，融合之后也不撤：两座山丘先贴到一起，桥再把那道缝填实
        val bulgeRangePx = with(density) { FusionBulgeRangeDp.dp.toPx() }
        val bulgeT = if (surfaceGap >= bulgeRangePx) {
            0f
        } else {
            ((bulgeRangePx - surfaceGap) / (bulgeRangePx - neckThresholdPx)).coerceIn(0f, 1f)
        }

        // 高度分布取归一化高斯：两端恰好 0（与本体完全重合）、导数为 0（无棱）。
        // 张角与高度外提成同一组参数，桥的锚点才能按角度查到山丘的实际半径 ——
        // 一律按最高点算的话，锚点处山丘已衰减，锚点悬空就是上下各一根尖刺。
        val bulgeH = with(density) { FusionBulgeMaxDp.dp.toPx() } * (bulgeT * bulgeT)
        val gaussEdge = exp(-0.25f / (2f * FusionBulgeSigma * FusionBulgeSigma))
        val phiPill = ((bulgeH * FusionBulgeWidthRatio) / pillRadius)
            .coerceIn(FusionBulgeMinAngle, 1.2f)
        val phiCircle = ((bulgeH * FusionBulgeWidthRatio) / circleAvgR)
            .coerceIn(FusionBulgeMinAngle, 1.2f)
        val bulgeCenterPill = ang
        val bulgeCenterCircle = ang + pi

        /** 某个角度上的山丘高度系数（0..1）；落在张角之外为 0 */
        fun bumpAt(angle: Float, center: Float, phi: Float): Float {
            val t = (angle - (center - phi)) / (2f * phi)
            if (t <= 0f || t >= 1f) return 0f
            val dx = t - 0.5f
            val rawG = exp(-(dx * dx) / (2f * FusionBulgeSigma * FusionBulgeSigma))
            return ((rawG - gaussEdge) / (1f - gaussEdge)).coerceIn(0f, 1f)
        }

        /** 胶囊侧 / 圆钮侧在给定角度上的实际半径（椭圆极半径 + 该处山丘高度） */
        fun pillRadiusAt(a: Float) = pillRadius + bulgeH * bumpAt(a, bulgeCenterPill, phiPill)
        fun circleRadiusAt(a: Float) =
            ellipseRadiusAt(circleRx, circleRy, a) + bulgeH * bumpAt(a, bulgeCenterCircle, phiCircle)

        /** 山丘扇形；[rx] / [ry] 必须与本体一致，否则扇形的底边压不到边界上，union 后是一圈毛刺 */
        fun hillPath(
            ccx: Float,
            ccy: Float,
            rx: Float,
            ry: Float,
            center: Float,
            phi: Float,
        ): Path? {
            if (bulgeH < 0.3f) return null
            return Path().apply {
                val steps = 56
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val a = (center - phi) + 2f * phi * t
                    val r = ellipseRadiusAt(rx, ry, a) + bulgeH * bumpAt(a, center, phi)
                    val x = ccx + r * cos(a)
                    val y = ccy + r * sin(a)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }

        fun bulgeAt(): Path? {
            if (bulgeH < 0.3f) return null
            val a = hillPath(axisX, cy, pillRadius, pillRadius, bulgeCenterPill, phiPill)
            val b = hillPath(circleCx, circleCy, circleRx, circleRy, bulgeCenterCircle, phiCircle)
            if (a == null || b == null) return null
            return Path().apply { op(a, b, PathOperation.Union) }
        }

        fun unionWith(extra: Path?): Outline.Generic? {
            if (extra == null) return null
            val out = Path()
            return if (out.op(merged, extra, PathOperation.Union)) Outline.Generic(out) else null
        }

        val bulge = bulgeAt()

        // 还没进入融合区间：只有两座山丘各自鼓着
        if (k <= 0.001f) return unionWith(bulge) ?: Outline.Generic(merged)

        // 两段相加：k 覆盖刚进入融合那一段，hh 在重叠后接管。取大不行 —— neckBase 会成天花板。
        val neck = (p.maxNeckHalfPx * k + intersectHh * NeckOverlapGrow)
            .coerceAtMost(pillRadius)

        // 桥不分支：分离时接上，重叠时继续撑宽。
        // hh < neck 时桥比自然轮廓宽、凸出来，接口保持平滑；hh 长过 neck 后桥被并集吞掉。
        // phi 用「山丘已隆起后的半径」反解，锚点角度才够开。
        val phi1 = asin((neck / (pillRadius + bulgeH)).coerceIn(-1f, 1f))
        // 圆钮侧用「朝胶囊那一点的椭圆极半径」反解：竖椭圆下用平均半径会让上下锚点一内一外，曲线断裂
        val circleNearR = ellipseRadiusAt(circleRx, circleRy, ang + pi)
        val phi2 = asin((neck / (circleNearR + bulgeH).coerceAtLeast(0.5f)).coerceIn(-1f, 1f))

        fun onPill(a: Float): Offset {
            val r = pillRadiusAt(a)
            return Offset(anchorX + r * cos(a), cy + r * sin(a))
        }

        // 与 hillPath 同一套极半径，锚点正好落在山丘曲线上
        fun onCircle(a: Float): Offset {
            val r = circleRadiusAt(a)
            return Offset(circleCx + r * cos(a), circleCy + r * sin(a))
        }

        // 各自朝向对方那侧：差一个 PI，符号要反过来才是同一侧
        val pu = onPill(ang + phi1)
        val pl = onPill(ang - phi1)
        val cu = onCircle(ang + pi - phi2)
        val cl = onCircle(ang + pi + phi2)

        // 控制点各自朝连线收，颈才凹出腰。收腰量随重叠深度递减到 0：
        // neck 顶到 pillRadius 时桥已覆盖整个圆，几何再也变不了，pinch 归零才让形状继续从花生过渡到整圆。
        val overlapT = (intersectHh / pillRadius).coerceIn(0f, 1f)
        val pinch = neck * 0.55f * (1f - overlapT)
        val ctrlU = Offset((pu.x + cu.x) / 2f - nx * pinch, (pu.y + cu.y) / 2f - ny * pinch)
        val ctrlL = Offset((pl.x + cl.x) / 2f + nx * pinch, (pl.y + cl.y) / 2f + ny * pinch)

        val neckPath = Path().apply {
            moveTo(pu.x, pu.y)
            quadraticTo(ctrlU.x, ctrlU.y, cu.x, cu.y)
            // 这一笔走在圆内部、会被圆盖住，只为闭合轮廓
            lineTo(cl.x, cl.y)
            quadraticTo(ctrlL.x, ctrlL.y, pl.x, pl.y)
            close()
        }

        // 山丘负责「贴住之前就有的肉」，桥负责「贴住之后那道缝」
        val withNeck = Path()
        if (!withNeck.op(merged, neckPath, PathOperation.Union)) return Outline.Generic(merged)
        if (bulge != null) {
            val combined = Path()
            if (combined.op(withNeck, bulge, PathOperation.Union)) return Outline.Generic(combined)
        }
        return Outline.Generic(withNeck)
    }
}
