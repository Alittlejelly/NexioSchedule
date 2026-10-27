package com.haooz.chedule.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.capsule.Continuity
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * 两颗胶囊并排、中间按 [LiquidFusion] 缝合的轮廓，**每一半各自可形变**。
 *
 * 和底栏（[BarShape]）同一套手感：左胶囊当「本体」（轴线膨胀端头半径），右胶囊的左端头当「另一团」，
 * 间隙就是表面 gap —— 走进 [LiquidFusion] 的阈值就鼓山丘、拉颈，两颗按钮读起来是一块玻璃。
 *
 * 参数全是 draw 期才读的 lambda：按压缩放、跟手位移都不进组合期，和 [BarShape] 同理。
 * 形变只改几何，不做 graphicsLayer 变换 —— 因为整块材质是一条 Path，缩放某一半只能改 Path。
 */
internal class FusionPairParams(
    /** 两半的静止间距，px */
    val gapPx: Float,
    val fusion: LiquidFusion,
    /** 材质画布相对可见矩形的余量，px；形状内部按它把原点补回来 */
    val bleedX: Float,
    val bleedY: Float,
    /** 左半的 (sx, sy) 倍率：1 = 静止。按压 + 沿拖动方向拉伸 */
    val leftScale: () -> Offset,
    /** 左半的跟手位移，px */
    val leftTranslation: () -> Offset,
    val rightScale: () -> Offset,
    val rightTranslation: () -> Offset,
    /** 把每次求出的轮廓交出去：按压高光要按真实轮廓裁，不能只在矩形节点里画 */
    val outlineSink: (Path) -> Unit = {},
)

/**
 * 某一帧的形状快照 —— **轮廓和折射必须读同一份**，各算各的迟早对不上。
 *
 * 坐标是材质节点坐标（含 bleed）；[neck] 为 null 表示这两团离得太远，连山丘都没有。
 */
internal class FusionPairFrame(
    val leftCx: Float,
    val leftCy: Float,
    val leftHalfW: Float,
    val leftHalfH: Float,
    val leftRx: Float,
    val leftRy: Float,
    val rightCx: Float,
    val rightCy: Float,
    val rightHalfW: Float,
    val rightHalfH: Float,
    val rightRx: Float,
    val rightRy: Float,
    /** 静止（未形变）胶囊尺寸：Continuity 只吃等比尺寸，路径按它生成再整体缩放 */
    val baseW: Float,
    val baseH: Float,
    val leftSx: Float,
    val leftSy: Float,
    val rightSx: Float,
    val rightSy: Float,
    val neck: BarNeckGeometry?,
)

internal fun FusionPairParams.frame(size: Size, density: Density): FusionPairFrame? {
    // 可见矩形 = 画布挖掉一圈 bleed；按压放大 / 跟手位移都在里面发生，超出部分由 bleed 兜住
    val bx = bleedX
    val by = bleedY
    val h0 = size.height - by * 2f
    val visibleWidth = size.width - bx * 2f
    val cw0 = (visibleWidth - gapPx) / 2f
    if (h0 <= 1f || cw0 <= 1f) return null
    val r0 = h0 / 2f

    val ls = leftScale()
    val lt = leftTranslation()
    val rs = rightScale()
    val rt = rightTranslation()

    // 左胶囊：静止矩形 [0, cw0] 按 (sx, sy) 缩放后整体跟着位移走
    val lw = (cw0 * ls.x).coerceAtLeast(1f)
    val lh = (h0 * ls.y).coerceAtLeast(1f)
    val lcx = bx + cw0 / 2f + lt.x
    val lcy = by + r0 + lt.y
    val lrx = (r0 * ls.x).coerceAtLeast(0.5f)
    val lry = (r0 * ls.y).coerceAtLeast(0.5f)

    // 右胶囊同理，静止矩形 [cw0 + gap, visibleWidth]
    val rw = (cw0 * rs.x).coerceAtLeast(1f)
    val rh = (h0 * rs.y).coerceAtLeast(1f)
    val rcx = bx + visibleWidth - cw0 / 2f + rt.x
    val rcy = by + r0 + rt.y
    val rrx = (r0 * rs.x).coerceAtLeast(0.5f)
    val rry = (r0 * rs.y).coerceAtLeast(0.5f)

    // 颈 / 山丘：左胶囊的右端头 ↔ 右胶囊的左端头（端头中心就是胶囊端点圆/椭圆的圆心）
    val leftCapX = lcx + lw / 2f - lrx
    val rightCapX = rcx - rw / 2f + rrx

    val neck = barNeckGeometryPx(
        pillCenterX = lcx,
        pillCenterY = lcy,
        pillHalfWidth = lw / 2f,
        pillHalfHeight = lh / 2f,
        circleCenterX = rightCapX,
        circleCenterY = rcy,
        circleRadiusX = rrx,
        circleRadiusY = rry,
        maxNeckHalfPx = h0 * fusion.neckStrength,
        fusion = fusion,
        density = density,
        // 形变后的端头是椭圆（rx ≠ ry）：按正圆算会把轴端点摆错，颈的锚点落进本体内部，
        // 接缝处就是一道折痕。两轴半径照实给，切线也改用椭圆的真实切线。
        pillRadiusX = lrx,
        pillRadiusY = lry,
        ellipseTangents = true,
    )

    return FusionPairFrame(
        leftCx = lcx, leftCy = lcy, leftHalfW = lw / 2f, leftHalfH = lh / 2f,
        leftRx = lrx, leftRy = lry,
        rightCx = rcx, rightCy = rcy, rightHalfW = rw / 2f, rightHalfH = rh / 2f,
        rightRx = rrx, rightRy = rry,
        baseW = cw0, baseH = h0,
        leftSx = ls.x, leftSy = ls.y, rightSx = rs.x, rightSy = rs.y,
        neck = neck,
    )
}

internal class FusionPairShape(private val p: FusionPairParams) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val outline = buildOutline(size, density)
        (outline as? Outline.Generic)?.path?.let(p.outlineSink)
        return outline
    }

    private fun buildOutline(size: Size, density: Density): Outline {
        val f = p.frame(size, density) ?: return Outline.Generic(Path())

        val left = capsulePath(
            left = f.leftCx - f.leftHalfW, top = f.leftCy - f.leftHalfH,
            baseWidth = f.baseW, baseHeight = f.baseH,
            sx = f.leftSx, sy = f.leftSy,
        )
        val right = capsulePath(
            left = f.rightCx - f.rightHalfW, top = f.rightCy - f.rightHalfH,
            baseWidth = f.baseW, baseHeight = f.baseH,
            sx = f.rightSx, sy = f.rightSy,
        )

        val merged = Path().apply { op(left, right, PathOperation.Union) }

        val geom = f.neck ?: return Outline.Generic(merged)

        val bulge = run {
            val a = fusionHillPath(
                centerX = f.leftCx + f.leftHalfW - f.leftRx, centerY = f.leftCy,
                rx = f.leftRx, ry = f.leftRy,
                angleCenter = geom.bulgeAngle, phi = geom.phiPill,
                bulgeH = geom.bulgeH, sigma = p.fusion.bulgeSigma, gaussEdge = geom.gaussEdge,
            )
            val b = fusionHillPath(
                centerX = f.rightCx - f.rightHalfW + f.rightRx, centerY = f.rightCy,
                rx = f.rightRx, ry = f.rightRy,
                angleCenter = geom.bulgeAngle + PI_F, phi = geom.phiCircle,
                bulgeH = geom.bulgeH, sigma = p.fusion.bulgeSigma, gaussEdge = geom.gaussEdge,
            )
            if (a == null || b == null) null else Path().apply { op(a, b, PathOperation.Union) }
        }

        if (!geom.hasNeck) return unionWith(merged, bulge) ?: Outline.Generic(merged)

        // 颈的八个点取自共享几何，两端沿本体切线切出去，与胶囊的夹角恒为 0
        val neck = Path().apply {
            moveTo(geom.pu.x, geom.pu.y)
            cubicTo(geom.c1u.x, geom.c1u.y, geom.c2u.x, geom.c2u.y, geom.cu.x, geom.cu.y)
            lineTo(geom.cl.x, geom.cl.y)
            cubicTo(geom.c2l.x, geom.c2l.y, geom.c1l.x, geom.c1l.y, geom.pl.x, geom.pl.y)
            close()
        }
        val withNeck = Path()
        if (!withNeck.op(merged, neck, PathOperation.Union)) return Outline.Generic(merged)
        return unionWith(withNeck, bulge) ?: Outline.Generic(withNeck)
    }

    private fun unionWith(base: Path, extra: Path?): Outline.Generic? {
        if (extra == null) return null
        val out = Path()
        return if (out.op(base, extra, PathOperation.Union)) Outline.Generic(out) else null
    }

    /**
     * 连续圆角胶囊（Continuity，与卡片 / 玻璃按钮同一套曲率，不是纯圆弧）。
     *
     * 端头要跟着 (sx, sy) 非等比缩放，而 Continuity 只吃等比尺寸 —— 所以先生成静止尺寸的
     * 路径再整体缩放。缩放在前、平移在后（Compose 的 Matrix 操作是后乘，写 translate 再 scale
     * 得到的才是 T·S）。
     */
    private fun capsulePath(
        left: Float,
        top: Float,
        baseWidth: Float,
        baseHeight: Float,
        sx: Float,
        sy: Float,
    ): Path = Path().apply {
        val base = (Continuity.Default.createCapsuleOutline(Size(baseWidth, baseHeight))
            as? Outline.Generic)?.path
        if (base != null) {
            addPath(base)
        } else {
            addRoundRect(
                RoundRect(0f, 0f, baseWidth, baseHeight, baseHeight / 2f, baseHeight / 2f)
            )
        }
        transform(
            Matrix().apply {
                translate(left, top)
                scale(sx, sy)
            }
        )
    }

    private companion object {
        const val PI_F = 3.1415927f
    }
}

/** 山丘某个角度上的高度系数（0..1）；与 [BarShape] 内的 bumpAt 同一条归一化高斯。 */
private fun fusionBumpAt(
    angle: Float,
    center: Float,
    phi: Float,
    sigma: Float,
    gaussEdge: Float,
): Float {
    // atan 在 ±π 跳变；角度差先折到 (-π, π] 再比，否则张角会被劈成两半
    var da = angle - center
    da -= 6.2831853f * kotlin.math.floor(da / 6.2831853f + 0.5f)
    val t = (da + phi) / (2f * phi)
    if (t <= 0f || t >= 1f) return 0f
    val dx = t - 0.5f
    val rawG = exp(-(dx * dx) / (2f * sigma * sigma))
    return ((rawG - gaussEdge) / (1f - gaussEdge)).coerceIn(0f, 1f)
}

/** 山丘扇形的采样轮廓；[bulgeH] 太小直接返回 null（省一次 union）。 */
private fun fusionHillPath(
    centerX: Float,
    centerY: Float,
    rx: Float,
    ry: Float,
    angleCenter: Float,
    phi: Float,
    bulgeH: Float,
    sigma: Float,
    gaussEdge: Float,
): Path? {
    if (bulgeH < 0.3f) return null
    return Path().apply {
        val steps = 56
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val a = (angleCenter - phi) + 2f * phi * t
            val radius = ellipseRadiusAt(rx, ry, a) +
                bulgeH * fusionBumpAt(a, angleCenter, phi, sigma, gaussEdge)
            val x = centerX + radius * cos(a)
            val y = centerY + radius * sin(a)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * 这两颗按钮的折射 shader。
 *
 * `lens()` 对自定义 Shape 直接跳过（`cornerRadii` 只认圆角矩形），所以必须自己给一份：
 * 用解析 SDF 描述**整条融合轮廓** —— 左胶囊 ∪ 右胶囊 ∪ 融合颈，
 * 于是边缘一圈折射沿「两颗按钮 + 中间的腰」连续走，接缝处不会断成两截。
 *
 * 端头支持椭圆（rx / ry 分开给）：拖拽时胶囊被非等比拉伸，按正圆算折射会与真实边缘错开。
 */
internal const val FusionPairRefractionShader = """
uniform shader content;

uniform float2 offset;
uniform float2 lc;
uniform float2 lh;
uniform float2 lr;
uniform float2 rc;
uniform float2 rh;
uniform float2 rr;
uniform float2 pu;
uniform float2 c1u;
uniform float2 c2u;
uniform float2 cu;
uniform float2 pl;
uniform float2 c1l;
uniform float2 c2l;
uniform float2 cl;
uniform float neckRadius;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float bulgeH;
uniform float bulgeAngleL;
uniform float bulgeAngleR;
uniform float bulgePhiL;
uniform float bulgePhiR;
uniform float bulgeGaussEdge;
uniform float bulgeSigma;

float bumpAt(float angle, float center, float phi) {
    float da = angle - center;
    da = da - 6.2831853 * floor(da / 6.2831853 + 0.5);
    float t = (da + phi) / (2.0 * phi);
    if (t <= 0.0 || t >= 1.0) {
        return 0.0;
    }
    float dx = t - 0.5;
    float rawG = exp(-(dx * dx) / (2.0 * bulgeSigma * bulgeSigma));
    return clamp((rawG - bulgeGaussEdge) / (1.0 - bulgeGaussEdge), 0.0, 1.0);
}

float sdEllipse(float2 p, float2 c, float2 ab) {
    float2 d = p - c;
    float t = atan(d.y / max(ab.y, 1e-4), d.x / max(ab.x, 1e-4));
    for (int i = 0; i < 4; i++) {
        float ct = cos(t);
        float st = sin(t);
        float2 q = float2(ab.x * ct, ab.y * st);
        float2 dq = float2(-ab.x * st, ab.y * ct);
        float2 d2q = float2(-ab.x * ct, -ab.y * st);
        float f = dot(q - d, dq);
        float df = dot(dq, dq) + dot(q - d, d2q);
        t = t - f / (abs(df) > 1e-4 ? df : 1e-4);
    }
    float2 q = float2(ab.x * cos(t), ab.y * sin(t));
    float dist = length(d - q);
    return (length(d / ab) < 1.0) ? -dist : dist;
}

// 胶囊 SDF：轴线在 x 上，端头是椭圆（rx == ry 时就是正圆）
float sdCapsule(float2 p, float2 c, float2 halfSize, float2 radii, float angleC, float phi) {
    float2 q = p - c;
    float2 axisHalf = max(halfSize - radii, float2(0.0, 0.0));
    float2 nearest = float2(clamp(q.x, -axisHalf.x, axisHalf.x), 0.0);
    float2 d = q - nearest;
    float d0 = sdEllipse(d, float2(0.0, 0.0), radii);
    if (bulgeH > 0.01 && length(d) > 1e-4) {
        d0 = d0 - bulgeH * bumpAt(atan(d.y, d.x), angleC, phi);
    }
    return d0;
}

float2 cBez(float2 a, float2 b, float2 c, float2 d, float t) {
    float mt = 1.0 - t;
    float mt2 = mt * mt;
    float t2 = t * t;
    return mt2 * mt * a + (3.0 * mt2 * t) * b + (3.0 * mt * t2) * c + t2 * t * d;
}

float2 dBez(float2 a, float2 b, float2 c, float2 d, float t) {
    float mt = 1.0 - t;
    return 3.0 * mt * mt * (b - a) + 6.0 * mt * t * (c - b) + 3.0 * t * t * (d - c);
}

float2 d2Bez(float2 a, float2 b, float2 c, float2 d, float t) {
    float mt = 1.0 - t;
    return 6.0 * mt * (c - 2.0 * b + a) + 6.0 * t * (d - 2.0 * c + b);
}

float nearestT(float2 p, float2 a, float2 b, float2 c, float2 d) {
    float bestD = 1e9;
    float bestT = 0.0;
    for (int k = 0; k < 2; k++) {
        float t = (k == 0) ? 0.25 : 0.75;
        for (int i = 0; i < 5; i++) {
            float2 q = cBez(a, b, c, d, t);
            float2 dq = dBez(a, b, c, d, t);
            float2 d2q = d2Bez(a, b, c, d, t);
            float f = dot(q - p, dq);
            float df = dot(dq, dq) + dot(q - p, d2q);
            t = clamp(t - f / (abs(df) > 1e-4 ? df : 1e-4), 0.0, 1.0);
        }
        float dd = length(cBez(a, b, c, d, t) - p);
        if (dd < bestD) {
            bestD = dd;
            bestT = t;
        }
    }
    return bestT;
}

float sdNeck(float2 p) {
    float tu = nearestT(p, pu, c1u, c2u, cu);
    float tl = nearestT(p, pl, c1l, c2l, cl);
    float2 qu = cBez(pu, c1u, c2u, cu, tu);
    float2 ql = cBez(pl, c1l, c2l, cl, tl);

    float2 du = dBez(pu, c1u, c2u, cu, tu);
    float2 dl = dBez(pl, c1l, c2l, cl, tl);
    if (tu < 0.02 || tu > 0.98) {
        du = cu - pu;
    }
    if (tl < 0.02 || tl > 0.98) {
        dl = cl - pl;
    }
    float sU = (du.x * (p.y - qu.y) - du.y * (p.x - qu.x)) / max(length(du), 1e-4);
    float sL = (dl.x * (p.y - ql.y) - dl.y * (p.x - ql.x)) / max(length(dl), 1e-4);

    float2 ax = cu - pu;
    ax = ax / max(length(ax), 1e-4);
    float dLong = max(-dot(p - pu, ax), dot(p - cu, ax));
    return max(sU, -sL) + max(0.0, dLong);
}

float sceneSdf(float2 p) {
    float d = min(
        sdCapsule(p, lc, lh, lr, bulgeAngleL, bulgePhiL),
        sdCapsule(p, rc, rh, rr, bulgeAngleR, bulgePhiR)
    );
    if (neckRadius > 0.001) {
        d = min(d, sdNeck(p));
    }
    return d;
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 p = coord + offset;
    float sd = sceneSdf(p);
    float refrH = refractionHeight;
    if (-sd >= refrH) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refrH) * refractionAmount;

    float eps = 0.75;
    float2 g = float2(
        sceneSdf(p + float2(eps, 0.0)) - sceneSdf(p - float2(eps, 0.0)),
        sceneSdf(p + float2(0.0, eps)) - sceneSdf(p - float2(0.0, eps))
    );
    float gl = length(g);
    float2 grad = gl > 0.0001 ? g / gl : float2(0.0, 0.0);
    return content.eval(coord + d * grad);
}
"""
