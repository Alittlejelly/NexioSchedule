package com.haooz.chedule.data

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 执行阻塞式 IO 的调度器。public：`:ui-shared` 的页面（如 License 全文加载）也要用。
 *
 * ## 为什么不能直接用 `Dispatchers.IO`
 *
 * `Dispatchers.IO` **不是跨平台 API**：JVM / Android 上是 public，Kotlin/Native（含 iOS）
 * 是 **internal** —— 直接引用报 `Cannot access 'val IO': it is internal in
 * 'kotlinx.coroutines.Dispatchers'`。
 *
 * ## Native 侧用 `Dispatchers.Default` 的理由
 *
 * Kotlin/Native 的 `Dispatchers.Default` 背后是**多线程工作池**（不是单线程），
 * 把网络/JSON 这类阻塞操作放进去不会卡住主线程，语义上与 JVM 的 IO 池足够接近；
 * Native 平台本身不提供独立的 IO 池（coroutines 1.9.0 未公开）。
 */
expect val ioDispatcher: CoroutineDispatcher
