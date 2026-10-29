package top.yukonga.miuix.kmp.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.concurrent.Volatile

/**
 * 强制深色主题的覆盖开关。null = 不覆盖（跟随外层主题）。
 * BlurBottomSheet 内部提供 null，阻止外层 App 的强制深色渗进弹窗；MiuixTheme 的 forceDark 也读它。
 */
val LocalForcedDarkTheme = staticCompositionLocalOf<Boolean?> { null }

/**
 * App 偏好里的「是否深色」，由**宿主**在根部 provide。
 *
 * ⚠ 默认 `null` 而非 `false`：默认 false 会让没 provide 的宿主一律亮色，比跟随系统更糟。
 *
 * 注意：弹窗会把它覆盖成 `null`（阻止强制深色渗进弹窗），所以**不能**只靠它读
 * 「用户选的是深色还是浅色」——那是 [AppSettingDarkBridge] 的事。
 */
val LocalAppSettingDark = staticCompositionLocalOf<Boolean?> { null }

/**
 * 宿主注入的「应用设置深浅色」数据源。
 *
 * ## 为什么需要注入而不是自己读
 *
 * 本模块不依赖 `:core`（拿不到存储）。原先 Android 侧是直连 `SharedPreferences` 读 `theme_mode`，
 * 存储换成 SQLite 之后那条路读的是迁移前的旧文件 —— 表现为「设置里切了深浅色，弹窗不跟着变」。
 *
 * 兜底顺序：注入的 [read] → [LocalAppSettingDark]（宿主主题根部下发）→ 系统深浅色。
 * 弹窗内部 Local 被覆盖成 null，靠第一档拿到正确值。
 */
object AppSettingDarkBridge {

    /** 返回 `"dark"` / `"light"` / 其它（视为跟随系统）。未注入为 null。 */
    @Volatile
    var read: (() -> String)? = null

    /** 订阅偏好变化，返回取消订阅。未注入为 null（不监听，仍可读当前值）。 */
    @Volatile
    var subscribe: ((onChange: () -> Unit) -> (() -> Unit))? = null
}

/**
 * App 偏好里记录的「是否深色」（跟随系统 / 强制浅 / 强制深）。
 *
 * 真实数据在宿主的存储里，由 [AppSettingDarkBridge] 注入；宿主没注入时退回
 * [LocalAppSettingDark]，再退回系统深浅色（与旧 Skia 侧行为一致，非退化）。
 */
@Composable
fun rememberAppSettingDark(): Boolean {
    val read = AppSettingDarkBridge.read ?: return LocalAppSettingDark.current ?: isSystemInDarkTheme()
    val mode = remember(read) { mutableStateOf(read()) }
    DisposableEffect(read) {
        val unsubscribe = AppSettingDarkBridge.subscribe?.invoke { mode.value = read() }
        onDispose { unsubscribe?.invoke() }
    }
    return when (mode.value) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
}

/**
 * 宿主是否**真的**在用系统壁纸取色（Monet）—— 偏好已开启且当前平台支持
 * （Android 要求 SDK >= 31，其它平台恒为 false）。
 *
 * 默认 `false` 而非 null：不取色即走内置亮暗主题，未接入本 Local 的宿主行为与从前一致。
 */
val LocalMonetColorEnabled = staticCompositionLocalOf { false }
