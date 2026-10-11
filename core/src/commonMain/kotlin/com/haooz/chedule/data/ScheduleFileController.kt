package com.haooz.chedule.data

/**
 * 课表数据文件的**读写能力抽象**（注入模式，同 [BackupFileController] / `UpdateController`）。
 *
 * 2026-10-11 为「课表导入 / 导出」页（`BackupAndMigrationScreen`）迁进 `:ui-shared` 而抽。
 * 那一页的 JSON / ICS 导入导出原先直接调 `ContentResolver` +
 * `ActivityResultContracts.OpenDocument` / `CreateDocument`，全是 Android 专有能力 ——
 * 不抽掉页面就进不了 commonMain。
 *
 * ## 与 [BackupFileController] 的分工
 *
 * [BackupFileController] 管「**应用自己的备份目录**」（固定目录、列表扫描、读/写/删）；
 * 本接口管「**用户选定的任意位置**」（导入时选一个文件读、导出时另存为一个新文件）。
 * 两者都是文件 I/O，但生命周期与失败文案完全不同，不合并。
 *
 * ## 约定
 *
 * - ⚙ **不带 Context 参数**：Android 包装在构造时捕获 context（同 `AndroidBackupFileController`），
 *   接口层保持纯跨平台签名，commonMain 里没有任何 android / java import。
 * - ⚙ **异步**：两者都要拉起系统文件选择器，结果在平台侧回调里返回 —— 所以没有返回值，
 *   成功/失败由实现直接 [showToast]（文案与原页面逐字一致）。
 * - ⚙ **失败即回调 null**：[openTextFile] 在用户取消 / 读取失败时都回调 `null`，
 *   页面靠 `null` 判断「没内容可导入」，不再自己分支。
 */
interface ScheduleFileController {

    /**
     * 拉起系统文件选择器选一个文本文件，读出全部内容后回调。
     *
     * @param mimeTypes 期望的 mime 列表（Android 侧与 OpenDocument 一致。
     *   原 JSON 导入传「application/json + 全匹配通配」、ICS 传「text/calendar + 全匹配通配」
     *   —— 实际都等价于全匹配，因为各厂商文件管理器上报的 mime 不一致，
     *   限定具体类型会让用户选不到文件；
     *   合法性由下游 `parseFullScheduleJson` / `parseIcsFile` 把关。）
     *
     * ⚠ 这里**不能**写出通配符的字面量：星号紧跟斜杠再跟星号，正好是块注释的结束符，
     * 会提前闭合 KDoc，报一堆莫名其妙的 `Expecting member declaration`。要提就写「全匹配通配」。
     * @param onResult 读到内容回调文本；用户取消或读取失败回调 `null`
     *   （失败文案由实现按原页面 toast：`导入失败: <原因>`）。
     */
    fun openTextFile(mimeTypes: List<String>, onResult: (String?) -> Unit)

    /**
     * 拉起系统「另存为」（CreateDocument）选择器，把 [content] 写到用户选定的位置。
     *
     * @param fileName 建议文件名（如 `我的课表.json`）
     * @param mimeType `application/json` / `text/calendar`
     * @param content UTF-8 文本
     */
    fun saveTextFile(fileName: String, mimeType: String, content: String)
}

/**
 * 桌面/iOS 的空实现：系统文件选择器是只有 Android 宿主实现了的能力。
 *
 * 非 Android 平台：选文件直接回调「用户取消」（页面静默），导出给一条提示 ——
 * 这样共享页不会静默失败到让人怀疑「点了没反应」。
 */
object NoopScheduleFileController : ScheduleFileController {
    override fun openTextFile(mimeTypes: List<String>, onResult: (String?) -> Unit) = onResult(null)
    override fun saveTextFile(fileName: String, mimeType: String, content: String) =
        showToast("当前平台不支持导出文件")
}
