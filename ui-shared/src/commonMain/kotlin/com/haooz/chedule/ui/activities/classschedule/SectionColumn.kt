package com.haooz.chedule.ui.activities.classschedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import com.haooz.chedule.ui.components.text.material.Text as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.chedule.data.Course
import com.haooz.chedule.data.SpecialBlock
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.haooz.chedule.ui.theme.scheduleAccentColor

/**
 * 左侧时间列宽度。横带要自己留出同样宽的起始边，子块才能和 DayColumn 逐列对齐，
 * 所以调用方一律取这个常量，别再各自写死一份。
 */
@Composable
fun scheduleSectionColumnWidth(isTablet: Boolean): Dp =
    if (isTablet) 56.dp else 36.dp

@Composable
fun SectionColumn(
    totalSections: Int = 11,
    morningSections: Int = 4,
    afternoonSections: Int = 4,
    eveningSections: Int = 3,
    sectionTimes: Map<Int, String> = Course.defaultSectionTimes,
    sectionNames: Map<Int, String> = emptyMap(),
    specialBlocks: List<SpecialBlock> = emptyList(),
    // 页面层共享；为空时列内自算
    grid: SpecialGridLayout? = null,
    cardHeightPerSection: Float = 54f,
    showBreakDividers: Boolean = true,
    currentSection: Int = -1,
    isTablet: Boolean = false,
    modifier: Modifier = Modifier
) {
    val timePairs = remember(sectionTimes, totalSections) {
        (1..totalSections).map { section ->
            val timeStr = sectionTimes[section] ?: Course.defaultSectionTimes[section] ?: ""
            val parts = timeStr.split("-")
            (parts.firstOrNull() ?: "") to (parts.lastOrNull() ?: "")
        }
    }

    val effectiveGrid = grid ?: remember(
        totalSections, morningSections, afternoonSections, eveningSections,
        specialBlocks, sectionTimes, cardHeightPerSection, showBreakDividers
    ) {
        computeSpecialGridLayout(
            morningSections = morningSections,
            afternoonSections = afternoonSections,
            eveningSections = eveningSections,
            specialBlocks = specialBlocks,
            sectionTimes = sectionTimes,
            cardHeightPerSection = cardHeightPerSection,
            dividerGap = if (showBreakDividers) 24 else 0
        )
    }
    val totalHeight = effectiveGrid.totalHeight.toInt()

    val sectionWidth = scheduleSectionColumnWidth(isTablet)

    Box(
        modifier = modifier
            .width(sectionWidth)
            .height(totalHeight.dp)
    ) {
        (1..morningSections).forEach { section ->
            val (startTime, endTime) = timePairs[section - 1]
            SectionItem(section, startTime, endTime, effectiveGrid.sectionTop[section]?.toInt() ?: 0, cardHeightPerSection, section == currentSection, sectionNames)
        }

        val afternoonStart = morningSections + 1
        val afternoonEnd = morningSections + afternoonSections
        (afternoonStart..afternoonEnd).forEach { section ->
            val (startTime, endTime) = timePairs[section - 1]
            SectionItem(section, startTime, endTime, effectiveGrid.sectionTop[section]?.toInt() ?: 0, cardHeightPerSection, section == currentSection, sectionNames)
        }

        val eveningStart = morningSections + afternoonSections + 1
        val eveningEnd = morningSections + afternoonSections + eveningSections
        (eveningStart..eveningEnd).forEach { section ->
            val (startTime, endTime) = timePairs[section - 1]
            SectionItem(section, startTime, endTime, effectiveGrid.sectionTop[section]?.toInt() ?: 0, cardHeightPerSection, section == currentSection, sectionNames)
        }

        effectiveGrid.specialBands.forEach { band ->
            SpecialTimeLabel(
                startTime = band.startTime,
                endTime = band.endTime,
                top = band.top,
                height = band.height,
                sectionWidth = sectionWidth
            )
        }
    }
}

@Composable
private fun SectionItem(section: Int, startTime: String, endTime: String, yOffset: Int, cardHeightPerSection: Float = 54f, isCurrentSection: Boolean = false, sectionNames: Map<Int, String> = emptyMap()) {
    val onSurfaceColor = MiuixTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MiuixTheme.colorScheme.onSurfaceVariantActions
    val highlightColor = scheduleAccentColor()
    val baseBody2 = MiuixTheme.textStyles.body2
    val baseFootnote2 = MiuixTheme.textStyles.footnote2

    val customName = sectionNames[section]
    val displayText = customName ?: section.toString()

    val sectionStyle = remember(isCurrentSection, baseBody2) {
        baseBody2.copy(
            fontWeight = if (isCurrentSection) FontWeight.Medium else FontWeight.Normal
        )
    }
    // 自定义名称用小号 Medium，避免超出列宽
    val nameStyle = if (customName != null) {
        sectionStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    } else sectionStyle
    val sectionColor = if (isCurrentSection) highlightColor else onSurfaceColor
    val timeStyle = remember(baseFootnote2) { baseFootnote2.copy(fontSize = 10.sp) }
    val timeColor = if (isCurrentSection) highlightColor else onSurfaceVariantColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeightPerSection.dp)
            .offset(y = yOffset.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = displayText, style = nameStyle, color = sectionColor)
            Text(text = startTime, style = timeStyle, color = timeColor)
            Text(text = endTime, style = timeStyle, color = timeColor)
        }
    }
}

@Composable
private fun SpecialTimeLabel(
    startTime: String,
    endTime: String,
    top: Float,
    height: Float,
    sectionWidth: Dp
) {
    val baseFootnote2 = MiuixTheme.textStyles.footnote2
    val timeStyle = remember(baseFootnote2) { baseFootnote2.copy(fontSize = 10.sp) }
    val timeColor = MiuixTheme.colorScheme.onSurfaceVariantActions
    Box(
        modifier = Modifier
            .width(sectionWidth)
            .height(height.dp)
            .offset(y = top.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = startTime, style = timeStyle, color = timeColor)
            Text(text = endTime, style = timeStyle, color = timeColor)
        }
    }
}
