package com.haooz.chedule.viewmodel

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Android：走 `viewModel()`，由 `LocalViewModelStoreOwner`（Activity）管作用域。
 * ⚠ 与迁移前逐字一致，勿改（同 Activity 内所有页面共享实例）。
 */
@Composable
actual fun courseViewModel(): CourseViewModel = viewModel()

@Composable
actual fun settingsViewModel(): SettingsViewModel = viewModel()

@Composable
actual fun scheduleViewModel(): ScheduleViewModel = viewModel()

@Composable
actual fun shiftViewModel(): ShiftViewModel = viewModel()
