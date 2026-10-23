package com.haooz.chedule.data

/**
 * 平台提示条：Android 上是 `Toast`，其他平台按各自能力降级。
 *
 * ## 为什么抽这一层
 *
 * `Toast.makeText(...)` 需要 `android.content.Context`，是 `:androidApp` UI 层页面迁进
 * `:ui-shared` 时**命中次数最多**的阻塞项（115 处调用 / 21 个文件）。
 *
 * ## 为什么是全局函数而不是 `@Composable`
 *
 * 调用点大量落在**非组合上下文**里：协程、回调、`@JavascriptInterface` 之后的主线程 post。
 * 代价是需要平台侧持有 Application 级 Context（Android 由 `NexioApplication` 注入，
 * 见 `PlatformToast.android.kt`）。
 *
 * ## ⚠ Android 侧行为必须与 `Toast.makeText` 逐字一致
 *
 * - `longDuration = false` → `Toast.LENGTH_SHORT`（约 2s）
 * - `longDuration = true`  → `Toast.LENGTH_LONG`（约 3.5s）
 * - **不排队、不合并**：连续调用就是连续弹两条，与原实现一致
 *   （`ui/web/AndroidBridge` 里那条 `currentToast?.cancel()` 的复用优化是 JS 桥接专用，
 *    不走这里，别拿它当通用语义。）
 */
expect fun showToast(message: String, longDuration: Boolean = false)

/**
 * 非 Android 平台的提示条实现钩子。
 *
 * Android 的 `actual` 走真 `Toast`，不看这里；iOS 启动注入时设成原生 HUD（`IosToast`），
 * 桌面留 null 落到日志 —— 调用方一行都不用改，各端自己决定要视觉反馈还是只记日志。
 *
 * ⚠ 和 [showToast] 一样是**全局可变状态**，只在启动注入阶段写一次，之后只读。
 */
var toastHandler: ((message: String, longDuration: Boolean) -> Unit)? = null
