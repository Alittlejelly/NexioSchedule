package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.haooz.chedule.data.AppStorage
import com.haooz.chedule.data.KEY_CHANGED_ANYWHERE
import com.haooz.chedule.data.ThemeMode
import com.haooz.chedule.data.getStringOrNull
import top.yukonga.miuix.kmp.theme.rememberAppSettingDark as miuixRememberAppSettingDark

// 壁纸强制主题：非 null 时 isAppDarkTheme 直接用该值，今日页/课程表页按壁纸亮暗锁定。
// 已搬进 :miuix 的 theme 包（BlurBottomSheet 在模块内要用它提供 null 阻止渗色），
// 这里保留一个别名，调用方无需改动，且与模块内是同一个 CompositionLocal 实例。
val LocalForcedDarkTheme = top.yukonga.miuix.kmp.theme.LocalForcedDarkTheme

/**
 * 课程表 / 排班页底色（无壁纸时铺满页面的那一层）。
 * 需要与页面底色对齐的元素统一取这里，不要各处抄 #F7F7F7 / #000000 字面量。
 */
fun schedulePageBackgroundColor(isDark: Boolean): Color =
    if (isDark) Color(0xFF000000) else Color(0xFFF7F7F7)

/** 无壁纸时课程卡垫底色的不透明度（浮层与网格卡片共用，保持两者观感一致） */
private const val COURSE_CARD_BACKING_ALPHA = 0.92f

/**
 * 课程卡片在**无壁纸**时的垫底色：页底色 @COURSE_CARD_BACKING_ALPHA；有壁纸返回 null。
 *
 * 无壁纸时课程色自身只有 0.1~0.4 透明度，卡片压在网格上会透出格线，垫一层接近
 * 不透明的页底色才立得住。有壁纸时玻璃层采样的是不透明壁纸层，垫色会被盖住。
 *
 * 网格里的课程卡与长按拖拽浮层统一取这里，避免两处各抄一份后漂移。
 */
fun courseCardSolidBacking(isDark: Boolean, hasWallpaper: Boolean): Color? =
    if (hasWallpaper) null
    else schedulePageBackgroundColor(isDark).copy(alpha = COURSE_CARD_BACKING_ALPHA)

/**
 * 主题亮暗的全局快照，供 draw 阶段这类非组合上下文读取 ——
 * [isAppDarkTheme] 是 `@Composable`，绘制里调不了，而高光亮度要按主题分流。
 *
 * 由 [isAppDarkTheme] 每次组合后写入。写同值不触发失效，开销可忽略。
 *
 * 实例已搬到 backdrop（InteractiveHighlight 在 :miuix 内读它），这里只做别名，
 * 保证写入的值就是 :miuix 读到的值。
 */
val AppThemeSnapshot = com.kyant.backdrop.AppThemeSnapshot

/**
 * 当前可见主页面背后是否为壁纸，理由同 [AppThemeSnapshot]：draw 阶段读不到页面状态，
 * 而高光亮不亮取决于背后是壁纸还是近白底。
 *
 * 由宿主（Android 的 MainActivity / 桌面壳）写入。典型用途：浅色模式下高光默认压到 0.05
 * （近白底加白会被截断成白斑），但今日页 / 课程表页有壁纸时背景不是近白 —— 此时给到 0.1。
 */
val PageBackdropSnapshot = com.kyant.backdrop.PageBackdropSnapshot

/** 当前生效的深浅色；顺带把结果写进 [AppThemeSnapshot] 供 draw 阶段读取 */
@Composable
fun isAppDarkTheme(): Boolean {
    val forced = LocalForcedDarkTheme.current
    if (forced != null) {
        SideEffect { AppThemeSnapshot.isDark.value = forced }
        return forced
    }
    val dark = rememberAppSettingDark()
    SideEffect { AppThemeSnapshot.isDark.value = dark }
    return dark
}

// 不经过壁纸强制覆盖，只读 theme_mode
// 实现已搬进 :miuix（theme/AppDarkTheme），因为 BlurBottomSheet 在 :miuix 内也需要读
// 同一个偏好，构成反向依赖。这里转调，行为与迁移前一致。
@Composable
fun rememberAppSettingDark(): Boolean = miuixRememberAppSettingDark()

// ── 课表页「默认主题」档位（第四十三批：随 CourseScheduleApp 迁入共享层）────────────
//
// 原实现在 :androidApp 的 ActivitySystemBars.kt（rememberScheduleThemeMode +
// ScheduleAppearance.get/setThemeMode）。落盘位置是数据契约：**与全局应用主题共用
// app_theme_prefs 文件但不同的键**（键名见 ThemeMode.SCHEDULE_THEME_MODE_KEY），
// 这里逐字照搬，只把入口换成跨平台的 AppStorage / KeyValueStore。

/** 与全局应用主题共用一个 prefs 文件（照抄 ScheduleAppearance.FILE_THEME，同值） */
private const val SCHEDULE_THEME_PREFS_FILE = "app_theme_prefs"

/** 读课表页「默认主题」档位（无值回落 ThemeMode.FOLLOW_WALLPAPER） */
fun loadScheduleThemeMode(): ThemeMode = ThemeMode.fromPrefsValue(
    AppStorage.store(SCHEDULE_THEME_PREFS_FILE)
        .getStringOrNull(ThemeMode.SCHEDULE_THEME_MODE_KEY)
)

/** 写课表页「默认主题」档位（CustomizeScheduleScreen 点「应用」时落盘） */
fun saveScheduleThemeMode(mode: ThemeMode) {
    AppStorage.store(SCHEDULE_THEME_PREFS_FILE).edit {
        putString(ThemeMode.SCHEDULE_THEME_MODE_KEY, mode.prefsValue)
    }
}

/**
 * 仅影响今日页/课程表页，与全局 theme_mode 隔离。
 *
 * 走 KeyValueStore.registerListener。⚠ iOS 的 `NSUserDefaultsDidChangeNotification`
 * 只通知「有东西变了」、**不带 key**，所以回调里是**无条件复查**而非按 key 精确过滤
 * （见 `UserDefaultsStore.registerListener` 与常量 `KEY_CHANGED_ANYWHERE`）。
 */
@Composable
fun rememberScheduleThemeMode(): ThemeMode {
    val prefs = remember { AppStorage.store(SCHEDULE_THEME_PREFS_FILE) }
    val themeMode = remember { mutableStateOf(loadScheduleThemeMode()) }

    DisposableEffect(prefs) {
        // ⚠ 同 CourseScheduleTheme：iOS 传 `KEY_CHANGED_ANYWHERE` 哨兵（系统只通知
        //   「有东西变了」，不带 key），按 key 精确过滤会让 iOS 永远收不到生效 → 无条件复查。
        val token = prefs.registerListener { key ->
            if (key == KEY_CHANGED_ANYWHERE ||
                key == ThemeMode.SCHEDULE_THEME_MODE_KEY
            ) {
                themeMode.value = loadScheduleThemeMode()
            }
        }
        onDispose {
            prefs.unregisterListener(token)
        }
    }

    return themeMode.value
}
