package com.haooz.chedule.data

/**
 * 捐赠明细项；数据由云端后端动态下发（见 [AppreciationFetcher]），App 端不再本地硬编码样本。
 */
data class AppreciationItem(
    val nickname: String,
    val amount: String,
    val time: String,
    val remark: String = "",
)
