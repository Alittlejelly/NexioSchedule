package com.haooz.chedule.viewmodel

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel

/**
 * ViewModel 工厂 —— 跨平台。
 *
 * ⚠ 必须有这一层：`viewModel()` 在 Kotlin/Native 上直接抛 `UnsupportedOperationException: Factory.create(String, CreationExtras) is not implemented`（编译期不报、运行期才炸，且只在 iOS）。也不能换成 `remember { XxxViewModel() }` —— 那会变成每页一个实例，而 Android 的 `viewModel()` 是 Activity 级共享，「一个页面改了设置、另一个页面不刷新」这类 bug 会无声出现。
 *
 * Android 原样走 `viewModel()`；iOS / 桌面拿进程级单例（等价「整个 App 一个 Activity」）。这四个 VM 都无参构造、只依赖 `:core` 的 `CourseRepository`，单例是安全的。
 */
@Composable
expect fun courseViewModel(): CourseViewModel

@Composable
expect fun settingsViewModel(): SettingsViewModel

@Composable
expect fun scheduleViewModel(): ScheduleViewModel

@Composable
expect fun shiftViewModel(): ShiftViewModel

/** 进程级 ViewModel 容器（desktop / ios 用；Android 由 `LocalViewModelStoreOwner` 管着）。 */
internal object ViewModelSingletonHolder {
    val course: CourseViewModel by lazy { CourseViewModel() }
    val settings: SettingsViewModel by lazy { SettingsViewModel() }
    val schedule: ScheduleViewModel by lazy { ScheduleViewModel() }
    val shift: ShiftViewModel by lazy { ShiftViewModel() }
}
