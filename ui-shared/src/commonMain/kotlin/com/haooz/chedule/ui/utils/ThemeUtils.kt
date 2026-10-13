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
// 别名指向 :miuix 里的同一个 CompositionLocal 实例，调用方无需改动。
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
 * 无壁纸时课程色自身只有 0.1~0.4 透明度，不垫一层近不透明的页底色会透出网格线。
 */
fun courseCardSolidBacking(isDark: Boolean, hasWallpaper: Boolean): Color? =
    if (hasWallpaper) null
    else schedulePageBackgroundColor(isDark).copy(alpha = COURSE_CARD_BACKING_ALPHA)

/**
 * 主题亮暗的全局快照，供 draw 阶段这类非组合上下文读取 ——
 * [isAppDarkTheme] 是 `@Composable`，绘制里调不了。由它每次组合后写入。
 * 别名指向 backdrop 的同一实例，保证写入的值就是那边读到的值。
 */
val AppThemeSnapshot = com.kyant.backdrop.AppThemeSnapshot

/**
 * 当前可见主页面背后是否为壁纸，供 draw 阶段读取（理由同 [AppThemeSnapshot]）。
 * 由宿主写入：浅色下高光默认压到 0.05，背后有壁纸时给到 0.1（近白底加白会被截断成白斑）。
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

// 不经过壁纸强制覆盖，只读 theme_mode（转调 :miuix 的实现，与其读同一份偏好）
@Composable
fun rememberAppSettingDark(): Boolean = miuixRememberAppSettingDark()

// ── 课表页「默认主题」档位 ───────────────────────────────────────────────
// 落盘是数据契约：**与全局应用主题共用 app_theme_prefs 文件但键不同**
// （键名见 ThemeMode.SCHEDULE_THEME_MODE_KEY），文件名与键名逐字不可改。

/** 与全局应用主题共用一个 prefs 文件（值逐字不可改） */
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
 * ⚠ iOS 的 `NSUserDefaultsDidChangeNotification` 只通知「变了」、**不带 key**，
 * 所以回调里是无条件复查，而非按 key 精确过滤（见 `KEY_CHANGED_ANYWHERE`）。
 */
@Composable
fun rememberScheduleThemeMode(): ThemeMode {
    val prefs = remember { AppStorage.store(SCHEDULE_THEME_PREFS_FILE) }
    val themeMode = remember { mutableStateOf(loadScheduleThemeMode()) }

    DisposableEffect(prefs) {
        // ⚠ iOS 传 `KEY_CHANGED_ANYWHERE` 哨兵（系统通知不带 key），
        //   按 key 精确过滤会让 iOS 永远收不到生效。
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
