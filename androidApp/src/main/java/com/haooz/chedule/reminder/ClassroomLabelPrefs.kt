package com.haooz.chedule.reminder

import android.content.SharedPreferences
import com.haooz.chedule.data.ClassroomLabel
import com.haooz.chedule.data.ClassroomLabelRule
import com.haooz.chedule.data.asKeyValueStore

/**
 * 缩略态（超级岛槽位/灵动岛胶囊/实况通知右侧/息屏标题）地点精简规则的读取口。
 * 规则本身（如何精简、可用宽度）在 :core 的 [ClassroomLabel]，两端共用；此处把 SP 交给 read 而非手抄字段，
 * 手抄多一处需同步，漏键表现为设置改了通知没反应且编译期看不出。
 * Receiver/Service 直读 SP 比走 AppStorage 少一层「Application 是否已 init」的假设。
 */
internal fun SharedPreferences.classroomLabelRule(): ClassroomLabelRule =
    ClassroomLabel.read(asKeyValueStore())

/** 缩略态用的地点文案。规则关闭时与课表原文逐字相同。 */
internal fun SharedPreferences.shortClassroom(classroom: String): String =
    ClassroomLabel.apply(classroom, classroomLabelRule())

/** 缩略态用的课程名文案。与地点共用同一套规则与对照表。 */
internal fun SharedPreferences.shortCourse(course: String): String =
    ClassroomLabel.applyCourse(course, classroomLabelRule())
