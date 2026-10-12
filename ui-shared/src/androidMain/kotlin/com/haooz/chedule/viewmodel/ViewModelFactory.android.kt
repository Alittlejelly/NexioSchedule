package com.haooz.chedule.viewmodel

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Android：原样走 `viewModel()` —— 由 `LocalViewModelStoreOwner`（Activity）管作用域。
 *
 * 这是迁移前的行为，**一个字都没改**：同一 Activity 内的所有页面共享同一实例，
 * 改完课表名，设置页与课表页立刻同步。iOS/桌面侧见 `ViewModelFactory` 的 expect 说明。
 */
@Composable
actual fun courseViewModel(): CourseViewModel = viewModel()

@Composable
actual fun settingsViewModel(): SettingsViewModel = viewModel()

@Composable
actual fun scheduleViewModel(): ScheduleViewModel = viewModel()

@Composable
actual fun shiftViewModel(): ShiftViewModel = viewModel()
