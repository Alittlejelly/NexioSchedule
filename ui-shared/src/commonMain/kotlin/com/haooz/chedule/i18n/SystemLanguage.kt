package com.haooz.chedule.i18n

/**
 * 系统首选语言的 BCP-47 主子标签（如 "zh" / "en"）。
 * 实现只需返回首选语言，不需要地区；无法判定时返回空串（按非中文处理）。
 */
expect fun systemLanguageTag(): String
