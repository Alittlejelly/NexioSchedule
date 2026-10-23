package com.haooz.chedule.data

/**
 * 跨平台互斥执行。
 *
 * ## 为什么需要它
 *
 * 保护 `HolidayManager`「读 prefs → 算 → 写 prefs」的复合操作；丢锁会出现
 * 「迁移结果覆盖用户刚保存的编辑」这类编译全绿、只有并发时才发生的问题。
 * Kotlin/Native 没有 `synchronized`（实测：`Unresolved reference`），故 expect/actual。
 *
 * ## 各平台实现（⚠ 差异是有意的，别改成「统一」）
 *
 * | 平台 | 实现 | 效果 |
 * |---|---|---|
 * | Android / JVM | `synchronized(lock)` | **与原来的 `@Synchronized` 逐字等价** —— Android 行为零变化（红线） |
 * | Kotlin/Native | 直通调用 | **暂时没有互斥**：Native 上没有可用原语，且 iOS 侧目前没有并发写这条路径 |
 *
 * > ⚠ **iOS 接进来之前必须回看这里**：若 iOS 侧也会「后台迁移 + 前台保存」并发，
 * > 需要给 nativeMain 换成真正的实现（例如 `kotlinx.atomicfu` 的 `SynchronizedObject`，
 * > 或把复合写操作收敛到单线程/单协程）。当前 nativeMain 的直通实现是**已知缺口**，
 * > 不是「等价实现」。
 *
 * 刻意用 **非 inline**：inline 的 expect/actual 跨模块有额外限制，而调用只在保存时发生一次。
 */
expect fun <T> synchronizedOn(lock: Any, block: () -> T): T
