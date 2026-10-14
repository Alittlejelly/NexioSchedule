/** [NativeTextField] 的 skiko actual（JVM 桌面 / iOS / wasmJs）：BasicTextField 兜底。 */
package top.yukonga.miuix.kmp.basic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 与 Android actual 的视觉对齐点：主题色光标与选中高亮、hint 颜色、
 * 字重默认 Medium、`textStyle.color` 未指定时回退 `onSurface`、IME Done。
 * 差异（平台固有，无法对齐）：没有系统长按菜单与原生选择手柄。
 */
@Composable
actual fun NativeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    hint: String,
    textStyle: TextStyle,
    singleLine: Boolean,
    maxLines: Int,
    maxLength: Int,
    textAlign: TextAlign,
    enabled: Boolean,
) {
    val themeColor = MiuixTheme.colorScheme.primary
    val textColor = textStyle.color.takeIf { it.isSpecified }
        ?: MiuixTheme.colorScheme.onSurface
    val hintColor = MiuixTheme.colorScheme.onSurfaceVariantActions

    BasicTextField(
        value = value,
        onValueChange = { input ->
            // Android 侧靠 EditText 的 InputFilter.LengthFilter 截断，这里等价：输入侧截断
            onValueChange(if (input.length > maxLength) input.substring(0, maxLength) else input)
        },
        modifier = modifier,
        enabled = enabled,
        textStyle = textStyle.copy(
            color = textColor,
            // Android 侧按 textStyle.fontWeight（缺省 Medium）设 EditText typeface
            fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
            textAlign = textAlign,
        ),
        cursorBrush = SolidColor(themeColor),
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        decorationBox = { innerTextField ->
            // hint 与输入区同位叠放，等价 EditText 的内部 hint（有内容即隐藏）
            //
            // ⚠ 两处都必须 `fillMaxWidth()`：`textAlign` 只在**有多余宽度**时才生效。
            // 不撑满的话 Box 与 Text 都是 wrap content，`TextAlign.End` 形同虚设
            // —— hint 靠左、输入文本对不齐，而 Android 侧 EditText 自己填满给定宽度并
            // 按 `textAlignment` 同时对齐 hint 与文本，两端表现就不一致了。
            // （所有调用点都给定宽：`fillMaxWidth(0.65f)` 或 `width(56.dp)`，撑满即所需宽度。）
            //
            // ⚠ Box 的 `contentAlignment` 也要跟着 `textAlign` 走：
            // 若 innerTextField 按 wrap-content 测量（不同 Compose 版本行为不同），
            // 光靠 `textStyle.textAlign` 是无效的（没有多余宽度可对齐），
            // 此时靠 Box 把它放到 End/Center 才对得上 Android 侧 EditText 的表现；
            // 若它本来就填满宽度，contentAlignment 是 no-op，由 textAlign 生效。
            // 两种情况下结果一致 —— 不要只留其中一种。
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = when (textAlign) {
                    TextAlign.Center -> Alignment.Center
                    TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                },
            ) {
                if (value.isEmpty() && hint.isNotEmpty()) {
                    Text(
                        text = hint,
                        modifier = Modifier.fillMaxWidth(),
                        style = textStyle.copy(
                            color = hintColor,
                            fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
                            textAlign = textAlign,
                        ),
                    )
                }
                innerTextField()
            }
        },
    )
}
