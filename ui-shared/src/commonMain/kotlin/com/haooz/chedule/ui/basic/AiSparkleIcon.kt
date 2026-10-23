package com.haooz.chedule.ui.basic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * AI 图标（三颗四角星）。原始素材是 1024×1024 SVG，路径经 [PathParser] 直接解析
 * SVG 的 `d` 搬进 [ImageVector]，免去手抄上千坐标（易错且难核对）。
 *
 * 填充色沿用素材金色（`#FAB907`）。`Icon` 默认用 `LocalContentColor` 覆盖，
 * 想保留原色传 `tint = Color.Unspecified`。
 */
val AiSparkleIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "AiSparkle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f,
    ).addPath(
        pathData = PathParser().parsePathString(AI_SPARKLE_PATH).toNodes(),
        fill = SolidColor(AI_SPARKLE_COLOR),
    ).build()
}

private val AI_SPARKLE_COLOR = Color(0xFFFAB907)

/** 三颗星：左上大星、左下小星、右下大星，与原素材逐字一致。 */
private const val AI_SPARKLE_PATH =
    "M387.368 165.027l-32.9-98.1c-1.5-5.7-6.6-9.7-12.4-9.4-5.7-0.3-10.9 3.6-12.4 9.4l-32.9 98.1" +
        "c-2.7 8.1-9.1 14.5-16.9 18.1l-73 32.6c-4.5 2.1-7.8 6.6-7.5 11.8-0.3 5.1 3 9.7 7.5 11.8" +
        "l73.3 32.3c7.8 3.6 13.9 10.3 16.6 18.4l33.2 97.8c1.8 4.8 6.6 8.4 12.1 8.4s10.3-3.6 12.1-8.4" +
        "l33.2-97.8c2.7-8.1 8.7-14.8 16.6-18.4l73.3-32.3c4.5-2.1 7.8-6.6 7.5-11.8 0.3-5.1-3-9.7-7.5-11.8" +
        "l-73-32.6c-7.8-3.7-14.1-10-16.9-18.1z" +
        "M313.468 806.127l-18.4-55.2c-1.5-5.7-6.6-9.7-12.4-9.4-5.7-0.3-10.9 3.6-12.4 9.4l-18.4 55.2" +
        "c-2.7 8.1-9.1 14.5-16.9 18.1l-40.4 18.1c-4.5 2.1-7.8 6.6-7.5 11.8-0.3 5.1 3 9.7 7.5 11.8" +
        "l40.7 17.8c7.8 3.6 13.9 10.3 16.6 18.4l18.7 54.9c1.8 4.8 6.6 8.4 12.1 8.4s10.3-3.6 12.1-8.4" +
        "l18.7-54.9c2.7-8.1 8.7-14.8 16.6-18.4l40.7-17.8c4.5-2.1 7.8-6.6 7.5-11.8 0.3-5.1-3-9.7-7.5-11.8" +
        "l-40.4-18.1c-7.9-3.6-14.2-9.9-16.9-18.1z" +
        "M721.868 509.127l-43.8-129.4c-2.4-7.2-8.7-12.4-16.6-12.4s-14.2 5.1-16.6 12.4l-43.7 129.4" +
        "c-3.9 11.2-11.8 20.2-22.6 25l-96.5 42.8c-6.3 2.7-10.3 9.1-10.3 15.7-0.3 6.9 3.6 13.3 10 16" +
        "l97.1 42.8c10.6 4.5 18.7 13.9 22.3 24.7l44 129.7c2.4 6.6 9.1 11.5 16.3 11.5s13.9-4.8 16.3-11.5" +
        "l44-129.7c3.6-10.9 11.8-20.2 22.3-24.7l97.1-42.8c6.3-2.7 10.3-9.1 10-16 0-6.6-3.9-13-10.3-15.7" +
        "l-96.5-42.8c-10.8-4.8-18.6-13.8-22.5-25z"
