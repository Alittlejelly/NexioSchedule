@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.cinterop.useContents
import platform.Foundation.NSNumber
import platform.Foundation.NSSelectorFromString
import platform.Foundation.valueForKey
import platform.UIKit.UIDevice
import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceIdiomPad

/**
 * iOS：真实屏幕圆角（px）。
 *
 * ## 为什么之前返回 0 —— 以及为什么要改
 *
 * 原来这里「返回 0（不裁切）」，理由是「CMP 容器本身圆角」。那个理由**只对全屏静止的页面成立**：
 * 系统确实会把窗口裁到屏幕圆角，所以铺满的页面看不出问题。但转场中间态不是铺满的 ——
 * `AppNavHost` 把上层页**平移**进来（还带着缩放），这时它是一张「浮在屏幕中间的卡片」，
 * 用的是**它自己的**直角轮廓，于是现代 iPhone 的圆屏上会露出四个直角。
 *
 * 所以这里必须给出真实半径，让转场中的卡片圆角与设备物理圆角对齐。
 *
 * ## iOS 没有公开 API
 *
 * `UIScreen` 至今**没有**任何公开的圆角属性（`cornerRadius` / `displayCornerRadius` 都不是
 * 公开 API），公开途径只能拿到 `bounds` / `scale` / `safeAreaInsets`，都与圆角无关。
 * 业界通用做法是 KVC 读私有属性 `_displayCornerRadius`（返回 points）。
 *
 * ⚠ **这是私有 API，上架审核有被拒的理论风险**（字符串式 KVC 一般不会被静态扫描命中，
 * 但它确实不是公开契约）。若要彻底规避：把 [USE_PRIVATE_DISPLAY_CORNER_RADIUS] 改成
 * `false`，就只走下面的尺寸兜底表 —— 观感略差但完全不碰私有 API。
 *
 * ## 兜底顺序
 *
 * 1. 私有 `_displayCornerRadius`（精确）
 * 2. **无刘海/灵动岛 → 判为方屏，返回 0**（如 iPhone SE：它确实没有圆角，
 *    这时返回兜底表的值反而会**多**裁一刀）
 * 3. 按屏幕短边查表（粗，但比 0 强）
 *
 * ⚠ 单位：`_displayCornerRadius` 与 `bounds` 都是 **points**，而本 expect 的契约是 **px**
 * （Android actual 读的 `RoundedCorner.radius` 就是 px）—— 故最后要乘 `UIScreen.scale`。
 */
@Composable
actual fun rememberScreenCornerRadiusPx(): Float {
    // 以窗口尺寸为 key：iPad 分屏 / 自由窗口改变尺寸时圆角会变，需要重算
    val container = LocalWindowInfo.current.containerSize
    return remember(container) { resolveScreenCornerRadiusPx() }
}

/** 关掉就完全不碰私有 API（只走尺寸兜底表），用于规避上架审核风险 */
private const val USE_PRIVATE_DISPLAY_CORNER_RADIUS = true

private fun resolveScreenCornerRadiusPx(): Float = try {
    val screen = UIScreen.mainScreen
    val scale = screen.scale
    val points = privateDisplayCornerRadiusPoints() ?: fallbackCornerRadiusPoints()
    (points * scale).toFloat()
} catch (_: Exception) {
    0f
}

/**
 * KVC 读 `UIScreen._displayCornerRadius`。
 *
 * ⚠ **必须先 `respondsToSelector` 再 `valueForKey`**：KVC 撞到不存在的 key 会抛
 * `NSUnknownKeyException`，而 **Kotlin/Native 捕获不了 ObjC 异常**（那不是 Kotlin 异常，
 * 会直接终止进程）—— 所以不能靠 try/catch 兜，只能先探测。
 */
private fun privateDisplayCornerRadiusPoints(): Double? {
    if (!USE_PRIVATE_DISPLAY_CORNER_RADIUS) return null
    val screen = UIScreen.mainScreen
    val selector = NSSelectorFromString("_displayCornerRadius") ?: return null
    if (!screen.respondsToSelector(selector)) return null
    val number = screen.valueForKey("_displayCornerRadius") as? NSNumber ?: return null
    val points = number.doubleValue
    return if (points > 0.0) points else null
}

/**
 * 兜底表：私有 API 不可用时的粗略估值。
 *
 * ⚠ 数值是**实测校准**的（模拟器探针）：iPhone 18 Pro Max = 62pt、
 * iPad Pro 11" = 30pt。真机各代略有出入，但这条路只在私有 API 取不到时才走，
 * 差几个 pt 肉眼几乎看不出。
 *
 * 先判方屏：**顶部安全区为 0 = 没有刘海/灵动岛**（iPhone SE 这类），
 * 那类设备物理上就是直角屏 —— 此时必须返回 0，否则转场卡片会被多裁一刀，
 * 反而比不裁更假。
 *
 * ⚠ 平板判定必须用 [UIDevice.uiUserInterfaceIdiom]，**别用宽高比**：
 * iPad Pro 11" 是 1194/834 ≈ 1.43，随手写的「< 1.4 判平板」会把它漏成手机
 * （实测踩到：iPad 上兜底给成 55pt，真实只要 30pt）。
 */
private fun fallbackCornerRadiusPoints(): Double {
    val window = IosUiHost.keyWindow() ?: return 0.0
    val topInset = window.safeAreaInsets.useContents { top }
    if (topInset <= 0.0) return 0.0
    val isPad = UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad
    if (isPad) return 30.0
    val shortSide = UIScreen.mainScreen.bounds.useContents {
        minOf(size.width, size.height)
    }
    return when {
        shortSide >= 430.0 -> 62.0   // Pro Max 级（实测 18 Pro Max = 62pt）
        shortSide >= 390.0 -> 55.0   // 6.1" / 6.3" 主流
        else -> 44.0                 // mini 级
    }
}
