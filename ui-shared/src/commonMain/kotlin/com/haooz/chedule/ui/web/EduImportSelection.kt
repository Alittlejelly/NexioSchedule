package com.haooz.chedule.ui.web

import com.haooz.chedule.data.school.AdapterData
import com.haooz.chedule.data.school.SchoolData

/**
 * 学校选择页 → 网页导入页 的选中项传递（路由只有稳定 id，没有参数通道）。
 * 选择页 navigate 前写入，网页页进入时读取。
 *
 * ⚠ 网页页退出时清空，否则下次进来会拿到上一轮的学校。
 */
object EduImportSelection {

    var school: SchoolData? = null
        private set

    var adapter: AdapterData? = null
        private set

    fun set(school: SchoolData, adapter: AdapterData) {
        this.school = school
        this.adapter = adapter
    }

    fun clear() {
        school = null
        adapter = null
    }
}
