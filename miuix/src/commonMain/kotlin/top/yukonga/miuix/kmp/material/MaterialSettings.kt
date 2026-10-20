package top.yukonga.miuix.kmp.material

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * 材质档位相关的两组能力，由 App 注入。
 *
 * 背景：本 fork 在很多 UI 上按「性能档」降级（关透镜、假渐进模糊、描边降级），
 * 但档位存在 App 的偏好存储里。模块不能反向依赖 :app，于是把这三项抽成
 * CompositionLocal —— App 在根部 provide，模块内任何位置都能读到，
 * 不必逐个调用点加参数（ProgressiveBlurTopBar 有 28 个调用点）。
 *
 * 与 [top.yukonga.miuix.kmp.utils.LocalPredictiveBackEnabled] 同思路：
 * 「App 的行为偏好」用 CompositionLocal 注入，「平台能力」用 expect/actual。
 */

/**
 * 是否启用 chrome 透镜（折射）。
 *
 * 原读 App 的 AppMaterialSettings.chromeLensEnabled()：通透玻璃档开，清透磨砂及更省档关。
 * 默认关 —— 与不注入时的安全行为一致。
 */
val LocalChromeLensEnabled = compositionLocalOf { false }

/**
 * 渐进模糊是否用「假」实现（等值 blur + alpha 淡出，不跑 AGSL 多重采样）。
 *
 * 原读 AppMaterialSettings.progressiveBlurUseFake()：仅性能档为真。
 * 默认 false（跑完整管线）。
 */
val LocalUseFakeProgressiveBlur = compositionLocalOf { false }

/**
 * 玻璃件（搜索框 / 输入框等）的采样模糊半径（dp）。
 *
 * 原读 App 的 AppMaterialSettings.glassBlurDp()：通透玻璃档更低（有折射，玻璃更通透），
 * 清透磨砂 / 性能档 8。默认 8 —— 与不注入时（即「无折射」档）的行为一致。
 */
val LocalGlassBlurDp = compositionLocalOf { 8f }

/**
 * 弹出面板（下拉菜单面板）的采样模糊半径（dp）。
 *
 * 原读 App 的 AppMaterialSettings.glassPanelBlurDp()：通透玻璃档恒定，其余档 24。
 * 默认 24 —— 与不注入时一致。
 *
 * ⚠ 弹窗 / 下拉菜单渲染在 root host，读不到声明页面的 CompositionLocal，
 * 必须随 [top.yukonga.miuix.kmp.utils.MiuixOverlayHostState] 的主题快照一起带过去。
 */
val LocalGlassPanelBlurDp = compositionLocalOf { 24f }

/**
 * 对话框 / 贴底弹窗玻璃的采样模糊半径（dp）。
 *
 * 原读 App 的 AppMaterialSettings.glassDialogBlurDp()：通透玻璃档更低，其余档 24。
 * 默认 24 —— 与不注入时一致。
 *
 * ⚠ 同 [LocalGlassPanelBlurDp]：对话框渲染在 root host，必须随 MiuixOverlayTheme 快照带过去。
 */
val LocalGlassDialogBlurDp = compositionLocalOf { 24f }

/**
 * 玻璃底色 alpha 的降幅（通透玻璃档专用，0 = 不降）：浅色 / 深色各一档。
 *
 * 原读 App 的 AppMaterialSettings.glassSurfaceAlphaDrop(isLight)：通透玻璃档 >0，其余档 0。
 * 默认全 0 —— 不注入时完全不改变原有底色。
 *
 * ⚠ 同 [LocalGlassBlurDp]：弹窗渲染在 root host，必须随 MiuixOverlayTheme 快照带过去。
 */
@Immutable
data class GlassSurfaceAlphaDrop(
    val light: Float = 0f,
    val dark: Float = 0f,
)

val LocalGlassSurfaceAlphaDrop = compositionLocalOf { GlassSurfaceAlphaDrop() }