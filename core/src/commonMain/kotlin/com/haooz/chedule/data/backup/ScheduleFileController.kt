package com.haooz.chedule.data.backup

import com.haooz.chedule.data.platform.showToast

/**
 * 课表数据的导入/导出能力（注入模式，同 [BackupFileController] / `UpdateController`）。
 *
 * 与 [BackupFileController] 的分工：后者管「应用自己的备份目录」（固定目录、列表扫描、
 * 读/写/删），本接口管「用户选定的任意位置」。两者生命周期与失败文案都不同，不合并。
 *
 * 两者都要拉起系统文件选择器，所以都是异步、靠回调返回结果，失败由实现直接 toast。
 */
interface ScheduleFileController {

    /**
     * 拉起系统文件选择器选一个文本文件，读出全部内容后回调。
     *
     * @param mimeTypes 期望的 mime 列表。各厂商文件管理器上报的 mime 不一致，
     *   限定具体类型会让用户选不到文件，所以实际都是全匹配，合法性由下游把关。
     *   ⚠ 这里**不能**写出通配符的字面量：星号紧跟斜杠再跟星号正好是块注释的结束符，
     *   会提前闭合 KDoc。要提就写「全匹配通配」。
     * @param onResult 读到内容回调文本；用户取消或读取失败回调 `null`
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
 * 桌面/iOS 的空实现：选文件直接回调「用户取消」，导出给一条提示 ——
 * 免得共享页静默失败到让人怀疑「点了没反应」。
 */
object NoopScheduleFileController : ScheduleFileController {
    override fun openTextFile(mimeTypes: List<String>, onResult: (String?) -> Unit) = onResult(null)
    override fun saveTextFile(fileName: String, mimeType: String, content: String) =
        showToast("当前平台不支持导出文件")
}
