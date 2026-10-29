package com.haooz.chedule.reminder

import com.haooz.chedule.data.ClassroomLabel
import com.haooz.chedule.data.ClassroomLabelRule
import com.haooz.chedule.data.KeyValueStore

/**
 * 缩略态（超级岛槽位/灵动岛胶囊/实况通知右侧/息屏标题）地点精简规则的读取口。
 * 规则本身（如何精简、可用宽度）在 :core 的 [ClassroomLabel]，两端共用；此处把 store 交给 read
 * 而非手抄字段，手抄多一处需同步，漏键表现为设置改了通知没反应且编译期看不出。
 *
 * 存储走 [KeyValueStore]（Android 上即 SQLite），不再直连 SharedPreferences ——
 * 直连点会让「设置写进新存储、通知读旧文件」成为静默不一致。
 */
internal fun KeyValueStore.classroomLabelRule(): ClassroomLabelRule =
    ClassroomLabel.read(this)

/** 缩略态用的地点文案。规则关闭时与课表原文逐字相同。 */
internal fun KeyValueStore.shortClassroom(classroom: String): String =
    ClassroomLabel.apply(classroom, classroomLabelRule())

/** 缩略态用的课程名文案。与地点共用同一套规则与对照表。 */
internal fun KeyValueStore.shortCourse(course: String): String =
    ClassroomLabel.applyCourse(course, classroomLabelRule())
