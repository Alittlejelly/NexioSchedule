/**
 * 全局文字组件（Miuix 版）：签名与 [top.yukonga.miuix.kmp.basic.Text] 一致，
 * 文字被改写时叠加模糊聚焦过渡（见 [com.haooz.chedule.ui.components.text.TextMotionBox]）。
 *
 * ⚠ 刻意的包名设计：本文件在 `text.miuix` 子包里，函数**就叫** `Text`，
 * 调用点 import 本包即可拿到带过渡的版本。
 */
package com.haooz.chedule.ui.components.text.miuix

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.haooz.chedule.ui.components.text.TextMotionBox
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Miuix 文字 + 变化模糊过渡。 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    autoSize: TextAutoSize? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = MiuixTheme.textStyles.main,
) {
    TextMotionBox(key = text, modifier = modifier, textAlign = textAlign) { shown, layerModifier ->
        top.yukonga.miuix.kmp.basic.Text(
            text = shown, modifier = layerModifier, color = color, autoSize = autoSize,
            fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing,
            textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
            overflow = overflow, softWrap = softWrap, maxLines = maxLines,
            minLines = minLines, onTextLayout = onTextLayout, style = style,
        )
    }
}

/** Miuix 文字（[ColorProducer] 版）+ 变化模糊过渡。 */
@Composable
fun Text(
    text: String,
    color: ColorProducer,
    modifier: Modifier = Modifier,
    autoSize: TextAutoSize? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = MiuixTheme.textStyles.main,
) {
    TextMotionBox(key = text, modifier = modifier, textAlign = textAlign) { shown, layerModifier ->
        top.yukonga.miuix.kmp.basic.Text(
            text = shown, color = color, modifier = layerModifier, autoSize = autoSize,
            fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing,
            textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
            overflow = overflow, softWrap = softWrap, maxLines = maxLines,
            minLines = minLines, onTextLayout = onTextLayout, style = style,
        )
    }
}

/** Miuix 文字（[AnnotatedString] 版）+ 变化模糊过渡。 */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    autoSize: TextAutoSize? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = MiuixTheme.textStyles.main,
) {
    TextMotionBox(key = text, modifier = modifier, textAlign = textAlign) { shown, layerModifier ->
        top.yukonga.miuix.kmp.basic.Text(
            text = shown, modifier = layerModifier, color = color, autoSize = autoSize,
            fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing,
            textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight,
            overflow = overflow, softWrap = softWrap, maxLines = maxLines,
            minLines = minLines, inlineContent = inlineContent,
            onTextLayout = onTextLayout, style = style,
        )
    }
}
