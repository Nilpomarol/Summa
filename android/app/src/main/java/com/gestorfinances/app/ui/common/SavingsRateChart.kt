package com.gestorfinances.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.ui.theme.FinanceTheme
import kotlin.math.abs

private const val SAVINGS_RATE_MAX_BARS = 24

/** One historical period's savings rate (basis points, i.e. hundredths of a percent), for [SavingsRateChart]. */
data class SavingsRateBar(
    val label: String,
    val basisPoints: Long,
)

/**
 * Vertical bars of savings rate per period — [positiveColor] above the zero line (surplus),
 * [negativeColor] below it (deficit) — with a smoothed trend line over the bar values so the
 * direction of travel reads at a glance. Shows at most the most recent [SAVINGS_RATE_MAX_BARS]
 * periods to stay legible.
 */
@Composable
fun SavingsRateChart(
    title: String,
    bars: List<SavingsRateBar>,
    positiveColor: Color,
    negativeColor: Color,
    positiveLabel: String,
    negativeLabel: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    accessibilitySummary: String,
    accessibilityRows: List<ChartDataRow> = emptyList(),
) {
    val shown = bars.takeLast(SAVINGS_RATE_MAX_BARS)
    val measurer = rememberTextMeasurer()
    val axisLabelColor = FinanceTheme.colors.mutedText
    val gridColor = FinanceTheme.colors.cardBorder
    val trendColor = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = axisLabelColor, fontSize = 9.sp)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader(title = title)
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AccessibleChart(
                    summary = accessibilitySummary,
                    dataRows = accessibilityRows,
                ) {
                    if (shown.isEmpty()) {
                        Text(
                            text = emptyText,
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .padding(bottom = 18.dp),
                            ) {
                        val maxAbs = shown.maxOf { abs(it.basisPoints) }.coerceAtLeast(100L)
                        val slot = size.width / shown.size
                        val barWidth = (slot * 0.55f).coerceAtMost(28.dp.toPx())
                        val centerY = size.height / 2f
                        val halfHeight = size.height / 2f
                        val cornerRadius = CornerRadius(2.dp.toPx())
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))

                        fun xOf(index: Int) = slot * index + slot / 2f
                        fun yOf(value: Long) = centerY - (value.toFloat() / maxAbs.toFloat()) * halfHeight * 0.72f

                        // Zero baseline — surplus reads above it, deficit below.
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, centerY),
                            end = Offset(size.width, centerY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect,
                        )

                        // Bars.
                        shown.forEachIndexed { index, bar ->
                            val x = xOf(index)
                            val barColor = if (bar.basisPoints >= 0) positiveColor else negativeColor
                            val barY = yOf(bar.basisPoints)
                            val top = minOf(centerY, barY)
                            val bottom = maxOf(centerY, barY)
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(x - barWidth / 2f, top),
                                size = Size(barWidth, (bottom - top).coerceAtLeast(2f)),
                                cornerRadius = cornerRadius,
                            )
                        }

                        // Smoothed trend line through each bar's value — consecutive midpoints
                        // joined by quadratic Beziers, the standard cheap way to round a polyline
                        // without a spline library.
                        val points = shown.mapIndexed { index, bar -> Offset(xOf(index), yOf(bar.basisPoints)) }
                        if (points.size >= 2) {
                            val trendPath = Path().apply {
                                moveTo(points[0].x, points[0].y)
                                for (i in 0 until points.size - 1) {
                                    val current = points[i]
                                    val next = points[i + 1]
                                    quadraticTo(
                                        current.x,
                                        current.y,
                                        (current.x + next.x) / 2f,
                                        (current.y + next.y) / 2f,
                                    )
                                }
                                lineTo(points.last().x, points.last().y)
                            }
                            drawPath(path = trendPath, color = trendColor, style = Stroke(width = 2.dp.toPx()))
                        }

                        // Labels (period below, rate value above/below the bar), thinned so they
                        // never collide (~6 across the width) — same cadence for both.
                        val spacing = (shown.size / 6).coerceAtLeast(1)
                        shown.forEachIndexed { index, bar ->
                            if (index % spacing != 0) return@forEachIndexed
                            val x = xOf(index)

                            val nameLayout = measurer.measure(
                                AnnotatedString(bar.label),
                                style = labelStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                constraints = Constraints(maxWidth = slot.toInt().coerceAtLeast(1)),
                            )
                            drawText(
                                textLayoutResult = nameLayout,
                                topLeft = Offset(x - nameLayout.size.width / 2f, size.height + 3f),
                            )

                            val barColor = if (bar.basisPoints >= 0) positiveColor else negativeColor
                            val valueLayout = measurer.measure(
                                AnnotatedString(formatBasisPoints(bar.basisPoints)),
                                style = labelStyle.copy(color = barColor),
                                maxLines = 1,
                                constraints = Constraints(maxWidth = (slot * 1.6f).toInt().coerceAtLeast(1)),
                            )
                            val barY = yOf(bar.basisPoints)
                            val valueY = if (bar.basisPoints >= 0) {
                                barY - valueLayout.size.height - 2.dp.toPx()
                            } else {
                                barY + 2.dp.toPx()
                            }
                            drawText(
                                textLayoutResult = valueLayout,
                                topLeft = Offset(x - valueLayout.size.width / 2f, valueY),
                            )
                        }
                            }
                            SavingsRateLegend(
                                positiveLabel = positiveLabel,
                                negativeLabel = negativeLabel,
                                positiveColor = positiveColor,
                                negativeColor = negativeColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavingsRateLegend(
    positiveLabel: String,
    negativeLabel: String,
    positiveColor: Color,
    negativeColor: Color,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SavingsRateLegendItem(label = positiveLabel, color = positiveColor)
        SavingsRateLegendItem(label = negativeLabel, color = negativeColor)
    }
}

@Composable
private fun SavingsRateLegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier
                .width(18.dp)
                .height(5.dp),
            shape = MaterialTheme.shapes.extraSmall,
            color = color,
            content = {},
        )
        Text(
            text = label,
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
