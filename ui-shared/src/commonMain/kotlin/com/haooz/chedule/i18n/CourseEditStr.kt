package com.haooz.chedule.i18n

/** 课程添加/编辑/详情页与特殊课程安排文案 */
object CourseEditStr {

    // ---- 弹窗标题 ----
    val addCourse = L("添加课程", "Add Course")
    val editCourse = L("编辑课程", "Edit Course")
    val deleteCourse = L("删除课程", "Delete Course")
    val deleteCourseConfirm = L(
        "确定要删除课程「%s」吗？\n此操作不可撤销。",
        "Delete course \"%s\"?\nThis action cannot be undone.",
    )
    val selectSectionTitle = L("选择上课节次", "Select Periods")
    val selectTimeTitle = L("选择上课时间", "Select Time")
    val selectColorTitle = L("选择颜色", "Select Color")

    // ---- 校验提示 ----
    val enterCourseName = L("请输入课程名称", "Please enter the course name")
    val selectCourseWeeks = L("请选择上课周次", "Please select the course weeks")
    val selectWeeksFirst = L("请先选择上课周次", "Please select the course weeks first")
    val timeSameError = L("开始与结束时间不能相同", "Start and end time cannot be the same")

    // ---- 表单字段 ----
    val courseName = L("课程名称", "Course name")
    val required = L("必填", "Required")
    val location = L("地点", "Location")
    val teacher = L("教师", "Teacher")
    val classDay = L("上课星期", "Weekday")
    val customTime = L("自定义时间", "Custom time")
    val classTime = L("上课时间", "Class time")
    val classPeriods = L("上课节次", "Periods")
    val periodsRange = L("第%d - %d节", "Periods %d-%d")
    val classWeeks = L("上课周次", "Weeks")
    val all = L("全部", "All")
    val oddWeeks = L("单周", "Odd weeks")
    val evenWeeks = L("双周", "Even weeks")
    val courseColor = L("课程颜色", "Course color")
    val customColorLabel = L("自定义颜色", "Custom color")
    val notSetTime = L("未设置时间", "Unscheduled")
    val coursesShareConfig = L("包含 %d 个相同配置的课程", "%d courses share this configuration")

    // ---- 节次/时间选择弹窗 ----
    val start = L("开始", "Start")
    val end = L("结束", "End")

    /** 星期一–星期日的单字格子标签（一…日 / Mon…Sun） */
    val dayLabels: List<L10n> = listOf(
        L("一", "Mon"),
        L("二", "Tue"),
        L("三", "Wed"),
        L("四", "Thu"),
        L("五", "Fri"),
        L("六", "Sat"),
        L("日", "Sun"),
    )

    // ---- 特殊课程安排 ----
    val name = L("名称", "Name")
    val addSpecialItem = L("添加安排", "Add Item")
    val editSpecialItem = L("编辑安排", "Edit Item")
    val deleteSpecialItem = L("删除该安排", "Delete This Item")
    val noSpecialItems = L("还没有安排，点这里添加", "No items yet. Tap to add")
    val unnamed = L("未命名", "Unnamed")
    val pickDaysHint = L(
        "点选下方星期（不连续可分段保存）",
        "Tap weekdays below (non-contiguous selections are saved as separate ranges)",
    )
    val weekDayN = L("周%s", "%s")
    val weekDayRange = L("周%s–周%s", "%s–%s")
}
