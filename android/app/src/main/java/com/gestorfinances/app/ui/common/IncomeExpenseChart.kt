package com.gestorfinances.app.ui.common

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
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
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import kotlin.math.roundToLong

/** One period bucket's income/expense; the chart turns these into running cumulative totals. */
data class IncomeExpenseChartPoint(
    val label: String,
    val bucket: String = label,
    val incomeCents: Long,
    val expenseCents: Long,
)

/**
 * Cumulative income and expense over the period (design §6 Charts). Each line is a running total,
 * so a one-off salary/rent shows as a step and small daily movements as gentle slope — both stay
 * visible regardless of size. The vertical gap between the two lines is the running net.
 *
 * When [previous] is non-empty, it overlays a comparison period as reduced-alpha, thinner ghost
 * lines (index-aligned with [points] — bucket N of each period shares the same x position
 * regardless of calendar date), turning this into the comparative cumulative chart.
 */
@Composable
fun IncomeExpenseChart(
    title: String,
    points: List<IncomeExpenseChartPoint>,
    incomeLabel: String,
    expenseLabel: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    previous: List<IncomeExpenseChartPoint> = emptyList(),
    currentCaption: String? = null,
    previousCaption: String? = null,
    accessibilitySummary: String,
    accessibilityRows: List<ChartDataRow> = emptyList(),
    onPointClick: ((IncomeExpenseChartPoint) -> Unit)? = null,
) {
    val hasComparison = previous.isNotEmpty()
    val hasData = points.isNotEmpty() || previous.isNotEmpty()
    val producer = remember { ChartEntryModelProducer() }
    val entries = remember(points, previous) { toCumulativeEntries(points, previous) }
    val labels = remember(points, previous) {
        val count = maxOf(points.size, previous.size)
        (0 until count).map { index -> points.getOrNull(index)?.label ?: previous.getOrNull(index)?.label ?: "" }
    }
    val incomeColor = FinanceTheme.colors.income
    val expenseColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisLabelColor = FinanceTheme.colors.mutedText
    val gridlineColor = FinanceTheme.colors.cardBorder
    // Comparison lines are desaturated toward the muted tone (not just faded via alpha), so they
    // read as a distinctly duller twin rather than a faint copy of the current-period color.
    val comparisonIncomeColor = comparisonTone(incomeColor, axisLabelColor)
    val comparisonExpenseColor = comparisonTone(expenseColor, axisLabelColor)

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
                                    lines = if (hasComparison) {
                                        listOf(
                                            lineSpec(lineColor = incomeColor, lineThickness = 2.5.dp),
                                            lineSpec(lineColor = expenseColor, lineThickness = 2.5.dp),
                                            lineSpec(lineColor = comparisonIncomeColor, lineThickness = 1.5.dp),
                                            lineSpec(lineColor = comparisonExpenseColor, lineThickness = 1.5.dp),
                                        )
                                    } else {
                                        listOf(
                                            lineSpec(lineColor = incomeColor, lineThickness = 2.5.dp),
                                            lineSpec(lineColor = expenseColor, lineThickness = 2.5.dp),
                                        )
                                    },
                                ),
                                chartModelProducer = producer,
                                // Real value axis: euro gridlines let the running totals be read off the scale.
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
                                    // Thin out date labels so they never collide (~6 across the width).
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
                                    .height(if (hasComparison) 240.dp else 208.dp)
                                    .chartPointTap(points = points, onPointClick = onPointClick),
                            )
                            ChartLegend(
                                incomeLabel = incomeLabel,
                                expenseLabel = expenseLabel,
                                incomeColor = incomeColor,
                                expenseColor = expenseColor,
                                comparisonIncomeColor = comparisonIncomeColor,
                                comparisonExpenseColor = comparisonExpenseColor,
                                hasComparison = hasComparison,
                                currentCaption = currentCaption,
                                previousCaption = previousCaption,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.chartPointTap(
    points: List<IncomeExpenseChartPoint>,
    onPointClick: ((IncomeExpenseChartPoint) -> Unit)?,
): Modifier =
    if (onPointClick == null) {
        this
    } else {
        pointerInput(points, onPointClick) {
            detectTapGestures { offset ->
                if (points.isEmpty() || size.width <= 0) return@detectTapGestures
                val index = ((offset.x / size.width) * points.size)
                    .toInt()
                    .coerceIn(0, points.lastIndex)
                onPointClick(points[index])
            }
        }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartLegend(
    incomeLabel: String,
    expenseLabel: String,
    incomeColor: Color,
    expenseColor: Color,
    comparisonIncomeColor: Color,
    comparisonExpenseColor: Color,
    hasComparison: Boolean,
    currentCaption: String?,
    previousCaption: String?,
) {
    if (hasComparison && currentCaption != null && previousCaption != null) {
        // Two rows (current above comparison) so the same color at two alphas lines up for
        // easy comparison, instead of interleaving four swatches in one row.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendItem(label = "$expenseLabel $currentCaption", color = expenseColor)
                LegendItem(label = "$incomeLabel $currentCaption", color = incomeColor)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendItem(label = "$expenseLabel $previousCaption", color = comparisonExpenseColor)
                LegendItem(label = "$incomeLabel $previousCaption", color = comparisonIncomeColor)
            }
        }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem(label = incomeLabel, color = incomeColor)
            LegendItem(label = expenseLabel, color = expenseColor)
        }
    }
}

/** Desaturates [base] toward [muted] rather than just lowering alpha, so a comparison-period line
 *  reads as a distinctly duller twin instead of a faint copy of the current-period color. */
private fun comparisonTone(base: Color, muted: Color): Color = lerp(base, muted, 0.45f).copy(alpha = 0.7f)

@Composable
private fun LegendItem(
    label: String,
    color: Color,
) {
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Running cumulative totals: one line per series (income/expense, current and optional previous). */
private fun toCumulativeEntries(
    current: List<IncomeExpenseChartPoint>,
    previous: List<IncomeExpenseChartPoint>,
): List<List<FloatEntry>> {
    fun cumulative(points: List<IncomeExpenseChartPoint>, selector: (IncomeExpenseChartPoint) -> Long): List<FloatEntry> {
        var running = 0L
        return points.mapIndexed { index, point ->
            running += selector(point)
            FloatEntry((index + 1).toFloat(), running / 100f)
        }
    }
    val entries = mutableListOf(
        cumulative(current) { it.incomeCents },
        cumulative(current) { it.expenseCents },
    )
    if (previous.isNotEmpty()) {
        entries += cumulative(previous) { it.incomeCents }
        entries += cumulative(previous) { it.expenseCents }
    }
    return entries
}
