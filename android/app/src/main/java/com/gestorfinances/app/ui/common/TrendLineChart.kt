package com.gestorfinances.app.ui.common

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.patrykandpatrick.vico.compose.axis.axisLabelComponent
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import kotlin.math.roundToLong

/** A named line series; [pointsEuros] holds one value per shared x-axis bucket. */
data class TrendSeries(
    val label: String,
    val color: Color,
    val pointsEuros: List<Float>,
)

/**
 * Multi-series line chart over a shared bucket axis (design baseline Charts). A single-series chart
 * (net worth) gets the indigo line + fill-area treatment; multi-series charts (category trends)
 * draw one colored line per series with no fill. Euro-formatted gridlines on the start axis and
 * [labels] (thinned so they never collide) on the bottom axis match the rest of the app's custom
 * chart styling, rather than Vico's unstyled defaults.
 *
 * [labels], if provided, must be index-aligned with each [TrendSeries.pointsEuros] (label\[i\]
 * names the bucket at pointsEuros\[i\]) — a mismatched length or order silently mislabels the axis
 * rather than crashing, since out-of-range indices just render blank.
 */
@Composable
fun TrendLineChart(
    title: String,
    series: List<TrendSeries>,
    emptyText: String,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    trailing: (@Composable () -> Unit)? = null,
    accessibilitySummary: String,
    accessibilityRows: List<ChartDataRow> = emptyList(),
) {
    val producer = remember { ChartEntryModelProducer() }
    val hasData = series.isNotEmpty() && series.any { it.pointsEuros.size >= 2 }
    val entries = remember(series) {
        series.map { line ->
            line.pointsEuros.mapIndexed { index, value -> FloatEntry((index + 1).toFloat(), value) }
        }
    }
    val axisLabelColor = FinanceTheme.colors.mutedText
    val gridlineColor = FinanceTheme.colors.cardBorder

    LaunchedEffect(entries, hasData) {
        if (hasData) {
            producer.setEntries(entries)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader(title = title, trailing = trailing)
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AccessibleChart(
                    summary = accessibilitySummary,
                    dataRows = accessibilityRows,
                ) {
                    if (!hasData) {
                        Text(
                            text = emptyText,
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chart(
                                chart = lineChart(
                                    lines = series.map { line ->
                                        lineSpec(
                                            lineColor = line.color,
                                            lineThickness = 2.5.dp,
                                            // Single-series charts (net worth) get a fill area under the
                                            // line per design baseline; multi-series trends stay plain so the
                                            // overlapping categories don't turn into a muddy wash.
                                            lineBackgroundShader = if (series.size == 1) {
                                                DynamicShaders.fromBrush(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            line.color.copy(alpha = 0.16f),
                                                            line.color.copy(alpha = 0f),
                                                        ),
                                                    ),
                                                )
                                            } else {
                                                null
                                            },
                                        )
                                    },
                                ),
                                chartModelProducer = producer,
                                // Real value axis: euro gridlines let the trend be read off the scale.
                                startAxis = rememberStartAxis(
                                    label = axisLabelComponent(color = axisLabelColor, textSize = 11.sp),
                                    axis = null,
                                    tick = null,
                                    guideline = lineComponent(color = gridlineColor, thickness = 1.dp),
                                    itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 5),
                                    valueFormatter = { value, _ -> formatEuroCompact((value.toDouble() * 100).roundToLong()) },
                                ),
                                bottomAxis = rememberBottomAxis(
                                    label = axisLabelComponent(color = axisLabelColor, textSize = 11.sp),
                                    axis = null,
                                    tick = null,
                                    guideline = null,
                                    // Thin out period labels so they never collide (~6 across the width).
                                    itemPlacer = AxisItemPlacer.Horizontal.default(
                                        spacing = (labels.size / 6).coerceAtLeast(1),
                                    ),
                                    valueFormatter = { value, _ ->
                                        val index = value.toInt() - 1
                                        if (index in labels.indices) labels[index] else ""
                                    },
                                ),
                                chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = false),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(208.dp),
                            )
                            if (series.size > 1) {
                                TrendLegend(series = series)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrendLegend(series: List<TrendSeries>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        series.forEach { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .width(18.dp)
                        .height(5.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = line.color,
                    content = {},
                )
                Text(
                    text = line.label,
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
