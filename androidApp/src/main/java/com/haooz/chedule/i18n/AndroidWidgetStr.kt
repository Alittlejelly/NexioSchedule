package com.haooz.chedule.i18n

/** 桌面小组件（RemoteViews）文案 */
object AndroidWidgetStr {
    val tomorrow = L("明天", "Tomorrow")
    val tomorrowCourses = L("明日课程", "Tomorrow's classes")
    val onBreak = L("放假中", "On break")
    val notStarted = L("未开始", "Not started")
    val vacationNoClasses = L("假期中，暂无课程", "On break, no classes")
    val semesterNotStarted = L("学期暂未开始", "Semester not started yet")
    val noClassesTomorrow = L("明日无课", "No classes tomorrow")
    val noClassesToday = L("今日无课", "No classes today")
    val allClassesFinished = L("今日课程已上完", "Done for today")
    val endsInN = L("%d分钟结束", "Ends in %d min")
    val moreClassesN = L("还有%d节课", "%d more classes")
    val noOtherClasses = L("没有其他课程", "No other classes")

    /** 课程信息项分隔符（教室｜教师），供小组件与超级岛展开态共用 */
    val infoSeparator = L("｜", "|")
}
