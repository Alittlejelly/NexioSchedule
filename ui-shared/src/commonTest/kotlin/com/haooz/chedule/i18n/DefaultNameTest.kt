package com.haooz.chedule.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/** 默认名称跟随语言:存储值为任一语言默认名时按当前语言显示,自定义值原样返回 */
class DefaultNameTest {

    @Test
    fun storedChineseDefaultShowsEnglishWhenEn() {
        AppLanguage.init(LanguagePref.EN)
        assertEquals("Holiday", "节假日".orDefaultName(HolidayStr.defaultHolidayName))
        assertEquals("Adjusted workday", "调休工作日".orDefaultName(HolidayStr.defaultWorkswapName))
        assertEquals("Routine", "作息".orDefaultName(TimeCfgStr.defaultRoutineName))
        assertEquals("Default routine", "默认作息".orDefaultName(TimeCfgStr.seededRoutineName))
        assertEquals("Default folder", "默认文件夹".orDefaultName(SwitchStr.defaultFolderName))
    }

    @Test
    fun storedEnglishDefaultShowsChineseWhenZh() {
        AppLanguage.init(LanguagePref.ZH)
        assertEquals("节假日", "Holiday".orDefaultName(HolidayStr.defaultHolidayName))
        assertEquals("作息", "Routine".orDefaultName(TimeCfgStr.defaultRoutineName))
        assertEquals("默认作息", "Default routine".orDefaultName(TimeCfgStr.seededRoutineName))
        assertEquals("默认文件夹", "Default folder".orDefaultName(SwitchStr.defaultFolderName))
    }

    @Test
    fun customNamesPassThrough() {
        AppLanguage.init(LanguagePref.EN)
        assertEquals("我的文件夹", "我的文件夹".orDefaultName(SwitchStr.defaultFolderName))
        assertEquals("早八作息", "早八作息".orDefaultName(TimeCfgStr.defaultRoutineName, ImportStr.defaultRoutineName))
    }

    @Test
    fun eduImportRoutineDefaultAlsoMatches() {
        AppLanguage.init(LanguagePref.EN)
        assertEquals("Routine", "作息方案".orDefaultName(TimeCfgStr.defaultRoutineName, ImportStr.defaultRoutineName))
    }
}
