package com.haooz.chedule.viewmodel

import androidx.compose.runtime.Composable

/**
 * iOS：进程级单例。iOS 没有 Activity / `LocalViewModelStoreOwner`，`viewModel()` 会抛
 * `UnsupportedOperationException`（见 `ViewModelFactory` 的 KDoc），故退一档用进程级单例 ——
 * 语义等价于「整个 App 只有一个 Activity」，与 Android 单 Activity 架构的实例边界一致。
 *
 * ⚠ 副作用：页面退出不会清 VM。四个 VM 都只是 `:core` 仓储的观察窗口，无页面生命周期资源，
 * 长住进程安全。
 */
@Composable
actual fun courseViewModel(): CourseViewModel = ViewModelSingletonHolder.course

@Composable
actual fun settingsViewModel(): SettingsViewModel = ViewModelSingletonHolder.settings

@Composable
actual fun scheduleViewModel(): ScheduleViewModel = ViewModelSingletonHolder.schedule

@Composable
actual fun shiftViewModel(): ShiftViewModel = ViewModelSingletonHolder.shift
