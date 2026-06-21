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
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry

/** A named line series; [pointsEuros] holds one value per shared x-axis bucket. */
data class TrendSeries(
    val label: String,
    val color: Color,
    val pointsEuros: List<Float>,
)

/**
 * Multi-series line chart over a shared bucket axis (design §6 Charts). A single-series chart
 * (net worth) gets the indigo line + fill-area treatment; multi-series charts (category trends)
 * draw one colored line per series with no fill.
 */
@Composable
fun TrendLineChart(
    title: String,
    series: List<TrendSeries>,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    val producer = remember { ChartEntryModelProducer() }
    val hasData = series.isNotEmpty() && series.any { it.pointsEuros.size >= 2 }
    val entries = remember(series) {
        series.map { line ->
            line.pointsEuros.mapIndexed { index, value -> FloatEntry((index + 1).toFloat(), value) }
        }
    }

    LaunchedEffect(entries, hasData) {
        if (hasData) {
            producer.setEntries(entries)
        }
    }

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
                if (!hasData) {
                    Text(
                        text = emptyText,
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Chart(
                        chart = lineChart(
                            lines = series.map { line ->
                                lineSpec(
                                    lineColor = line.color,
                                    // Single-series charts (net worth) get an 8% fill area
                                    // sparkline per design §6; multi-series trends stay plain.
                                    lineBackgroundShader = if (series.size == 1) {
                                        DynamicShaders.fromBrush(
                                            Brush.verticalGradient(
                                                listOf(
                                                    line.color.copy(alpha = 0.08f),
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
                        startAxis = rememberStartAxis(),
                        bottomAxis = rememberBottomAxis(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp),
                    )
                    if (series.size > 1) {
                        TrendLegend(series = series)
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
