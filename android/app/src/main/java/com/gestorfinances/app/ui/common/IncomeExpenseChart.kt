package com.gestorfinances.app.ui.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry

data class IncomeExpenseChartPoint(
    val label: String,
    val bucket: String = label,
    val incomeCents: Long,
    val expenseCents: Long,
)

@Composable
fun IncomeExpenseChart(
    title: String,
    points: List<IncomeExpenseChartPoint>,
    incomeLabel: String,
    expenseLabel: String,
    emptyText: String,
    modifier: Modifier = Modifier,
    onPointClick: ((IncomeExpenseChartPoint) -> Unit)? = null,
) {
    val producer = remember { ChartEntryModelProducer() }
    val entries = remember(points) { points.toChartEntries() }
    val incomeColor = FinanceTheme.colors.income
    val expenseColor = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(entries, points.isEmpty()) {
        if (points.isNotEmpty()) {
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
                if (points.isEmpty()) {
                    Text(
                        text = emptyText,
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Chart(
                        chart = columnChart(
                            columns = listOf(
                                lineComponent(color = incomeColor, thickness = 8.dp),
                                lineComponent(color = expenseColor, thickness = 8.dp),
                            ),
                        ),
                        chartModelProducer = producer,
                        startAxis = rememberStartAxis(),
                        bottomAxis = rememberBottomAxis(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                            .chartPointTap(points = points, onPointClick = onPointClick),
                    )
                }
                ChartLegend(
                    incomeLabel = incomeLabel,
                    expenseLabel = expenseLabel,
                )
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

@Composable
private fun ChartLegend(
    incomeLabel: String,
    expenseLabel: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendItem(
            label = incomeLabel,
            color = FinanceTheme.colors.income,
        )
        LegendItem(
            label = expenseLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
        )
    }
}

private fun List<IncomeExpenseChartPoint>.toChartEntries(): List<List<FloatEntry>> {
    val incomeEntries = mutableListOf<FloatEntry>()
    val expenseEntries = mutableListOf<FloatEntry>()
    forEachIndexed { index, point ->
        val x = (index + 1).toFloat()
        incomeEntries += FloatEntry(x, point.incomeCents / 100f)
        expenseEntries += FloatEntry(x, -(point.expenseCents / 100f))
    }
    return listOf(incomeEntries, expenseEntries)
}
