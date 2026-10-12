package com.haooz.chedule.viewmodel

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel

/**
 * ViewModel 工厂 —— **跨平台**。
 *
 * ## 为什么必须有这一层
 *
 * `androidx.lifecycle.viewmodel.compose.viewModel()` 在 Android 上依赖
 * `LocalViewModelStoreOwner` + `SavedStateViewModelFactory`；**Kotlin/Native 上一个都没有**，
 * 调下去直接抛：
 *
 * ```
 * kotlin.UnsupportedOperationException:
 *   `Factory.create(String, CreationExtras)` is not implemented.
 *   at androidx.lifecycle.SavedStateViewModelFactory#create
 * ```
 *
 * 编译期不报（klib 里有符号），运行期才炸 —— 而且只在 iOS，Android 一直好好的是这类
 * 问题最阴的地方。
 *
 * ## 为什么不直接在 iOS 上 `remember { XxxViewModel() }`
 *
 * 语义不对。Android 的 `viewModel()` 拿的是 **Activity 级**共享实例：改完课表名，
 * 设置页与课表页看到的是同一份状态。换成 `remember` 就变成每页一个实例，
 * 「在一个页面改了设置，另一个页面不刷新」这类 bug 会无声出现。
 *
 * 所以正解是**两端都提供统一的获取入口**：
 * - Android → 原样走 `viewModel()`（Activity 级，零回归）
 * - iOS / 桌面 → 拿**进程级单例**（等价于「整个 App 一个 Activity」）
 *
 * 这四个 ViewModel 都是无参构造、只依赖 `:core` 的 `CourseRepository`，
 * 进程级单例是安全的（仓库层本身在 `:core` 已是单例化仓储）。
 */
@Composable
expect fun courseViewModel(): CourseViewModel

@Composable
expect fun settingsViewModel(): SettingsViewModel

@Composable
expect fun scheduleViewModel(): ScheduleViewModel

@Composable
expect fun shiftViewModel(): ShiftViewModel

/**
 * 进程级 ViewModel 容器（desktop / ios 用）。
 *
 * Android 侧不需要 —— `viewModel()` 由 `LocalViewModelStoreOwner` 管着，
 * 天然按 Activity 作用域存活。iOS/桌面没有那个 Owner，就用这个对象兜底：
 * 进程级活一次，语义等价于「整个 App 一个 Activity」。
 */
internal object ViewModelSingletonHolder {
    val course: CourseViewModel by lazy { CourseViewModel() }
    val settings: SettingsViewModel by lazy { SettingsViewModel() }
    val schedule: ScheduleViewModel by lazy { ScheduleViewModel() }
    val shift: ShiftViewModel by lazy { ShiftViewModel() }
}
