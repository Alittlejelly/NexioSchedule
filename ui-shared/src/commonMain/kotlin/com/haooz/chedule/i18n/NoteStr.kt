package com.haooz.chedule.i18n

/**
 * 课程备注文案。
 *
 * 备注正文是用户自己写的原文，不参与本地化；这里只有入口、范围与占位提示。
 */
object NoteStr {

    /** 卡片底部没备注时的入口 */
    val addNote = L("添加备注", "Add note")

    /** 有备注时的前缀标签 */
    val noteLabel = L("备注", "Note")

    val notePlaceholder = L("临时通知、要带的东西…", "Notice, things to bring…")

    /** 范围 chip：只写当前这一周 */
    val scopeThisWeek = L("仅第 %d 周", "Week %d only")

    /** 范围 chip：这条课的所有周 */
    val scopeAllWeeks = L("所有周", "All weeks")
}
