package com.haooz.chedule.data

// 版本比较独立成文件：纯字符串逻辑、无平台 API，供 `:ui-shared`（`UpdateDialog`）共用。

/**
 * 解析版本号为数字序列。
 *
 * 格式：`[v]MAJOR.MINOR.PATCH[-DATE]` 或 `[v]MAJOR.MINOR.PATCH.BETA[-DATE]`
 * 例：`1.5.0-0905` → `[1,5,0]`；`1.5.0.2-0905` → `[1,5,0,2]`
 * 日期后缀（`-` 之后）与构建元数据（`+` 之后）不参与比较；解析不出数字的段按 0 处理。
 */
fun parseVersion(raw: String): List<Int> {
    val cleaned = raw.trim().removePrefix("v").removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
    return cleaned.split('.').map { it.toIntOrNull() ?: 0 }
}

/** 是否 beta 版（第 4 段版本号存在） */
fun isBetaVersion(raw: String): Boolean = parseVersion(raw).size >= 4

/** 比较版本：[remote] 是否比 [local] 更新。忽略日期后缀；段数不同时短的一方补 0。 */
fun isNewerVersion(remote: String, local: String): Boolean {
    val r = parseVersion(remote)
    val l = parseVersion(local)
    val max = maxOf(r.size, l.size)
    for (i in 0 until max) {
        val rv = r.getOrElse(i) { 0 }
        val lv = l.getOrElse(i) { 0 }
        if (rv > lv) return true
        if (rv < lv) return false
    }
    return false
}
