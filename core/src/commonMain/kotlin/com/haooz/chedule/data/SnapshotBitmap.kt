package com.haooz.chedule.data

/**
 * 课程卡「快照位图」—— 渲染产物的**不透明句柄**。
 *
 * ## 为什么需要它
 *
 * 课程卡点开时有个「卡片放大飞入详情页」的转场效果：宿主先把卡片画成一张位图
 * （Android 上是 `android.graphics.Bitmap`），再把它一路传进详情页当转场素材。
 * 于是 `CourseManageScreen` / `CourseDetailScreen` / `CourseEditScreen` /
 * `SwitchScheduleScreen` / `TodayScreen` / `MainScheduleScreen` /
 * `CustomizeScheduleScreen` **七个页面**的参数表里都带着 `android.graphics.Bitmap?`，
 * 全卡在 `:androidApp` 搬不进 `:ui-shared`。
 *
 * ## 为什么用 expect/actual 而不是抽个接口
 *
 * 页面**从头到尾只是把它拿着往下传**（`snapshot: Bitmap?` → `onClick(…, snapshot)`），
 * 从不读取像素、不绘制、不释放 —— 真正的生产（截图）与消费（`asImageBitmap()`）
 * 都在 `MainActivity` / `AppRouteContent` 这些**留在 :androidApp 的宿主代码**里。
 *
 * 所以需要的不是「能力接口」，而是一个**跨平台能写出参数类型的名字**：
 * - Android：`actual typealias = android.graphics.Bitmap` → 宿主那边传 `Bitmap`
 *   完全无缝，**行为零变化**（红线）。
 * - JVM 桌面 / Kotlin/Native：空占位类。桌面与 iOS 没有这种 Activity 转场，
 *   这个参数**永远是 null**。
 *
 * ⚠ 别在 commonMain 里对它做任何操作（没有 `.asImageBitmap()`、没有宽高）。
 *   一旦某个页面真要用像素，说明它不该迁 —— 回来重新设计，别往这个类上堆成员。
 */
expect class SnapshotBitmap
