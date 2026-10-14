package com.haooz.chedule.ui.utils

import kotlin.concurrent.Volatile

/**
 * 预测性返回动画全局开关（应用偏好设置中可切换，默认开启）。
 *
 * 关闭后：各组件仍拦截返回（关闭行为不变），但手势进度不再驱动动画，
 * 返回时直接关闭、无手势跟随动画。
 */
object PredictiveBackSettings {

    const val KEY_PREDICTIVE_BACK_ANIMATION = "predictive_back_animation_enabled"

    // 必须显式 import kotlin.concurrent.Volatile：不限定的 @Volatile 靠 JVM 专有的
    // 默认导入 kotlin.jvm.* 解析，commonMain 在非 JVM 目标上编不过（纯度门禁实测拦下）。
    // JVM/Android 上 kotlin.concurrent.Volatile 就是 kotlin.jvm.Volatile 的 typealias，行为零变化。
    @Volatile
    var enabled: Boolean = true
}
