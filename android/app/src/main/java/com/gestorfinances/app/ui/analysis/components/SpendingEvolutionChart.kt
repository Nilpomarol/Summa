package com.gestorfinances.app.ui.analysis.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.AccessibleChart
import com.gestorfinances.app.ui.common.ChartDataRow
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatEuroCompact
import com.gestorfinances.app.ui.theme.FinanceTheme
import kotlin.math.max

internal data class SpendingEvolutionPoint(
    val label: String,
    val expenseCents: Long,
)

internal enum class SpendingEvolutionMode { CUMULATIVE_LINE, GROUPED_BARS }

@Composable
internal fun SpendingEvolutionChart(
    current: List<SpendingEvolutionPoint>,
    previous: List<SpendingEvolutionPoint>,
    currentCaption: String,
    previousCaption: String,
    mode: SpendingEvolutionMode,
) {
    if (current.isEmpty() && previous.isEmpty()) return
    val currentTotal = current.sumOf { it.expenseCents }
    val previousTotal = previous.sumOf { it.expenseCents }
    val currentColor = MaterialTheme.colorScheme.primary
    val previousColor = FinanceTheme.colors.mutedText.copy(alpha = 0.55f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.analysis_mobile_spending_evolution))
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AccessibleChart(
                    summary = stringResource(
                        R.string.analysis_mobile_evolution_accessibility,
                        currentCaption,
                        formatEuroCents(currentTotal),
                        previousCaption,
                        formatEuroCents(previousTotal),
                    ),
                    dataRows = evolutionDataRows(current, previous, currentCaption, previousCaption),
                    showDataControl = false,
                ) {
                    EvolutionCanvas(
                        current = current,
                        previous = previous,
                        mode = mode,
                        currentColor = currentColor,
                        previousColor = previousColor,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    EvolutionLegend(caption = currentCaption, color = currentColor)
                    EvolutionLegend(caption = previousCaption, color = previousColor)
                }
            }
        }
    }
}

@Composable
private fun EvolutionCanvas(
    current: List<SpendingEvolutionPoint>,
    previous: List<SpendingEvolutionPoint>,
    mode: SpendingEvolutionMode,
    currentColor: Color,
    previousColor: Color,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = FinanceTheme.colors.mutedText)
    val gridColor = FinanceTheme.colors.cardBorder
    val currentValues = if (mode == SpendingEvolutionMode.CUMULATIVE_LINE) current.runningExpense() else current.map { it.expenseCents }
    val previousValues = if (mode == SpendingEvolutionMode.CUMULATIVE_LINE) previous.runningExpense() else previous.map { it.expenseCents }
    val maxValue = max(currentValues.maxOrNull() ?: 0L, previousValues.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val labels = if (current.isNotEmpty()) current.map { it.label } else previous.map { it.label }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(174.dp),
    ) {
        val left = 48.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        val chartWidth = (right - left).coerceAtLeast(1f)
        val chartHeight = (bottom - top).coerceAtLeast(1f)

        for (step in 0..2) {
            val value = maxValue * step / 2
            val y = bottom - chartHeight * step / 2f
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
            val measured = textMeasurer.measure(formatEuroCompact(value), labelStyle)
            drawText(measured, topLeft = Offset(left - measured.size.width - 6.dp.toPx(), y - measured.size.height / 2f))
        }

        when (mode) {
            SpendingEvolutionMode.CUMULATIVE_LINE -> {
                drawEvolutionLine(previousValues, previousColor, left, top, chartWidth, chartHeight, maxValue, 1.5.dp.toPx())
                drawEvolutionLine(currentValues, currentColor, left, top, chartWidth, chartHeight, maxValue, 2.5.dp.toPx())
            }
            SpendingEvolutionMode.GROUPED_BARS -> {
                val count = max(currentValues.size, previousValues.size).coerceAtLeast(1)
                val groupWidth = chartWidth / count
                val barWidth = (groupWidth * 0.28f).coerceAtMost(12.dp.toPx())
                repeat(count) { index ->
                    val center = left + groupWidth * (index + 0.5f)
                    previousValues.getOrNull(index)?.let { value ->
                        drawEvolutionBar(value, maxValue, center - barWidth - 1.dp.toPx(), bottom, chartHeight, barWidth, previousColor)
                    }
                    currentValues.getOrNull(index)?.let { value ->
                        drawEvolutionBar(value, maxValue, center + 1.dp.toPx(), bottom, chartHeight, barWidth, currentColor)
                    }
                }
            }
        }

        if (labels.isNotEmpty()) {
            val indices = listOf(0, labels.lastIndex / 2, labels.lastIndex).distinct()
            indices.forEach { index ->
                val x = if (labels.size == 1) left else left + chartWidth * index / labels.lastIndex.toFloat()
                val measured = textMeasurer.measure(labels[index], labelStyle)
                drawText(measured, topLeft = Offset((x - measured.size.width / 2f).coerceIn(left, right - measured.size.width), bottom + 5.dp.toPx()))
            }
        }
    }
}

private fun DrawScope.drawEvolutionLine(
    values: List<Long>,
    color: Color,
    left: Float,
    top: Float,
    chartWidth: Float,
    chartHeight: Float,
    maxValue: Long,
    strokeWidth: Float,
) {
    if (values.isEmpty()) return
    val path = Path()
    values.forEachIndexed { index, value ->
        val x = if (values.size == 1) left else left + chartWidth * index / values.lastIndex.toFloat()
        val y = top + chartHeight * (1f - value.toFloat() / maxValue)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = strokeWidth))
    val last = values.last()
    val lastX = if (values.size == 1) left else left + chartWidth
    drawCircle(
        color = color,
        radius = strokeWidth * 1.5f,
        center = Offset(lastX, top + chartHeight * (1f - last.toFloat() / maxValue)),
    )
}

private fun DrawScope.drawEvolutionBar(
    value: Long,
    maxValue: Long,
    x: Float,
    bottom: Float,
    chartHeight: Float,
    width: Float,
    color: Color,
) {
    val height = (chartHeight * value.toFloat() / maxValue).coerceAtLeast(1f)
    drawRoundRect(
        color = color,
        topLeft = Offset(x, bottom - height),
        size = Size(width, height),
        cornerRadius = CornerRadius(width / 2f, width / 2f),
    )
}

@Composable
private fun EvolutionLegend(caption: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(5.dp)
                .background(color, MaterialTheme.shapes.extraSmall),
        )
        Text(text = caption, color = FinanceTheme.colors.mutedText, style = MaterialTheme.typography.labelSmall)
    }
}

private fun List<SpendingEvolutionPoint>.runningExpense(): List<Long> {
    var total = 0L
    return map { point ->
        total += point.expenseCents
        total
    }
}

private fun evolutionDataRows(
    current: List<SpendingEvolutionPoint>,
    previous: List<SpendingEvolutionPoint>,
    currentCaption: String,
    previousCaption: String,
): List<ChartDataRow> =
    (0 until max(current.size, previous.size)).map { index ->
        val currentPoint = current.getOrNull(index)
        val previousPoint = previous.getOrNull(index)
        ChartDataRow(
            label = currentPoint?.label ?: previousPoint?.label.orEmpty(),
            value = listOfNotNull(
                currentPoint?.let { "$currentCaption ${formatEuroCents(it.expenseCents)}" },
                previousPoint?.let { "$previousCaption ${formatEuroCents(it.expenseCents)}" },
            ).joinToString(" · "),
        )
    }
