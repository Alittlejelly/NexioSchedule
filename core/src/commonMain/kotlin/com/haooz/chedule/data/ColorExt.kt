package com.haooz.chedule.data

/**
 * RGB ↔ HSV 转换 —— `android.graphics.Color.RGBToHSV` / `HSVToColor` 的**逐行纯数学移植**。
 * 零依赖（core 不引 compose），返回/接收与 AOSP 相同的 Int/FloatArray 形态。
 *
 * 迁移自 CourseCard 的「课程色饱和度提升」：原实现直接调 android.graphics.Color，
 * 那是 Android 专有 API，commonMain 编不过（iOS/桌面都没有）。
 *
 * ## 为什么逐行移植而不是自己写标准公式
 *
 * 舍入路径必须与 AOSP 一致 —— 浮点公式的写法很多，细节差一点（int 截断 vs 四舍五入、
 * 先乘后截 vs 先截后乘）颜色就会差 1/255，表现为「桌面端课程卡颜色与 Android 不一致」。
 * 这里每个分支、每次 `* 255f` 后的 `.toInt()`（AOSP 语义 = 向零截断）都对齐 AOSP 源码，
 * 并用 golden vectors（见 commonTest）锁定。
 */

/**
 * @param result 长度 ≥ 3 的输出数组：[0]=色相(0..360) [1]=饱和度(0..1) [2]=明度(0..1)
 * 对应 AOSP `Color.RGBToHSV(red, green, blue, hsv)`。
 */
fun rgbToHsv(red: Int, green: Int, blue: Int, result: FloatArray) {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val v = max / 255f
    // AOSP: float delta = max - min（int 相减后转 float，后续 (g-b)/delta 是浮点除法）
    val delta = (max - min).toFloat()
    val s = if (max == 0) 0f else delta / max

    if (max == min) {
        result[0] = 0f
    } else {
        var h = if (red == max) {
            (green - blue) / delta
        } else if (green == max) {
            2f + (blue - red) / delta
        } else {
            4f + (red - green) / delta
        }
        h *= 60f
        if (h < 0f) h += 360f
        result[0] = h
    }
    result[1] = s
    result[2] = v
}

/**
 * HSV → 打包 ARGB（alpha=0xFF）。对应 AOSP `Color.HSVToColor(hsv)`。
 * 通道用 AOSP 同款向零截断（`(v * 255f).toInt()`）。
 */
fun hsvToRgb(hsv: FloatArray): Int {
    val h = hsv[0]
    val s = hsv[1]
    val v = hsv[2]

    if (s == 0f) {
        val vint = (v * 255f).toInt()
        return (0xFF shl 24) or (vint shl 16) or (vint shl 8) or vint
    }

    val h60 = h / 60f
    val i = h60.toInt()
    val f = h60 - i
    val p = v * (1f - s)
    val q = v * (1f - s * f)
    val t = v * (1f - s * (1f - f))
    val r: Int
    val g: Int
    val b: Int
    when (i) {
        0 -> { r = (v * 255f).toInt(); g = (t * 255f).toInt(); b = (p * 255f).toInt() }
        1 -> { r = (q * 255f).toInt(); g = (v * 255f).toInt(); b = (p * 255f).toInt() }
        2 -> { r = (p * 255f).toInt(); g = (v * 255f).toInt(); b = (t * 255f).toInt() }
        3 -> { r = (p * 255f).toInt(); g = (q * 255f).toInt(); b = (v * 255f).toInt() }
        4 -> { r = (t * 255f).toInt(); g = (p * 255f).toInt(); b = (v * 255f).toInt() }
        5 -> { r = (v * 255f).toInt(); g = (p * 255f).toInt(); b = (q * 255f).toInt() }
        // AOSP HSVToColor 对 i 越界直接返回 0（不透明黑之外的非法值），这里对齐
        else -> return 0
    }
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
