package com.haooz.chedule.viewmodel

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 桌面：原样走 `viewModel()` —— 桌面端已在 `PageContainer` 里
 * `CompositionLocalProvider(LocalViewModelStoreOwner provides owner)` 补了 Owner
 * （每页一个 store，离开即 clear），无需特殊处理。见 `desktopApp/.../Main.kt` 的
 * `DesktopViewModelStoreOwner`。
 */
@Composable
actual fun courseViewModel(): CourseViewModel = viewModel()

@Composable
actual fun settingsViewModel(): SettingsViewModel = viewModel()

@Composable
actual fun scheduleViewModel(): ScheduleViewModel = viewModel()

@Composable
actual fun shiftViewModel(): ShiftViewModel = viewModel()
