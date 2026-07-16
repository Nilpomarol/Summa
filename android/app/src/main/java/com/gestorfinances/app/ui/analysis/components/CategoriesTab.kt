package com.gestorfinances.app.ui.analysis.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisBreakdownKind
import com.gestorfinances.app.data.repository.AnalysisCategoryFrequency
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.ui.analysis.AnalysisUiState
import com.gestorfinances.app.ui.analysis.currentDisplayDivisor
import com.gestorfinances.app.ui.analysis.divideCents
import com.gestorfinances.app.ui.analysis.fallbackPeriodLabel
import com.gestorfinances.app.ui.analysis.formatForScope
import com.gestorfinances.app.ui.analysis.rowKey
import com.gestorfinances.app.ui.common.AccessibleChart
import com.gestorfinances.app.ui.common.ChartDataRow
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.Sparkline
import com.gestorfinances.app.ui.common.Treemap
import com.gestorfinances.app.ui.common.TreemapItem
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.chartTrendLabel
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatEuroCompact
import com.gestorfinances.app.ui.common.formatPercentLabel
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import kotlin.math.sqrt

private const val TREEMAP_MAX_BLOCKS = 12

@Composable
internal fun CategoriesTab(
    state: AnalysisUiState,
    contentPadding: PaddingValues,
) {
    val data = state.categories
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        tabErrorItem(state.errorMessage)
        if (data == null) {
            if (state.errorMessage == null) item { TabLoading() }
            return@LazyColumn
        }

        val expenseRows = data.categories.filter { it.expenseCents > 0 }
        if (expenseRows.isEmpty()) {
            item { EmptyAnalysisCard() }
            return@LazyColumn
        }

        // Treemap.
        item { TabSection(stringResource(R.string.analysis_treemap_title)) }
        item {
            val blocks = treemapItems(
                rows = expenseRows,
                otherLabel = stringResource(R.string.analysis_other),
                tripColor = FinanceTheme.colors.transfer,
                otherColor = FinanceTheme.colors.mutedText,
            )
            val leader = blocks.maxByOrNull { it.valueCents }
            Treemap(
                items = blocks,
                accessibilitySummary = stringResource(
                    R.string.accessibility_chart_period_summary,
                    stringResource(R.string.analysis_treemap_title),
                    state.currentRange?.formatForScope(state.scope) ?: state.fallbackPeriodLabel(),
                    formatEuroCents(blocks.sumOf { it.valueCents }),
                    leader?.let { stringResource(R.string.accessibility_chart_category_leader, it.label) }
                        ?: stringResource(R.string.dashboard_no_data),
                    stringResource(R.string.accessibility_chart_no_trend),
                ),
                accessibilityRows = blocks.map { block ->
                    ChartDataRow(block.label, formatEuroCents(block.valueCents))
                },
            )
        }

        // Breakdown list with sparklines.
        item { TabSection(stringResource(R.string.analysis_breakdown_title)) }
        val totalExpense = expenseRows.sumOf { it.expenseCents }.coerceAtLeast(1L)
        val sparkBuckets = data.trends.map { it.bucket }.distinct().sorted()
        val trendsByCategory = data.trends.groupBy { it.categoryId }
        items(items = expenseRows, key = { it.rowKey() }) { category ->
            CategoryDistributionRow(
                category = category,
                totalExpenseCents = totalExpense,
                divisor = state.currentDisplayDivisor(),
                sparkValues = sparkBuckets.map { bucket ->
                    (trendsByCategory[category.categoryId]?.firstOrNull { it.bucket == bucket }?.expenseCents ?: 0L) / 100f
                },
            )
        }

        // Frequency vs volume bubble chart.
        if (data.frequency.size >= 2) {
            item { TabSection(stringResource(R.string.analysis_scatter_title)) }
            item {
                FrequencyVolumeChart(
                    points = data.frequency,
                    divisor = state.currentDisplayDivisor(),
                    periodLabel = state.currentRange?.formatForScope(state.scope) ?: state.fallbackPeriodLabel(),
                )
            }
        }
    }
}

private fun treemapItems(
    rows: List<AnalysisCategoryTotal>,
    otherLabel: String,
    tripColor: androidx.compose.ui.graphics.Color,
    otherColor: androidx.compose.ui.graphics.Color,
): List<TreemapItem> {
    val sorted = rows.sortedByDescending { it.expenseCents }
    val total = sorted.sumOf { it.expenseCents }.coerceAtLeast(1L)
    val head = sorted.take(TREEMAP_MAX_BLOCKS)
    val tail = sorted.drop(TREEMAP_MAX_BLOCKS)
    val items = head.map { category ->
        TreemapItem(
            key = category.rowKey(),
            label = category.tripName ?: category.categoryName ?: "—",
            valueCents = category.expenseCents,
            color = if (category.rowKind == AnalysisBreakdownKind.TRIP) {
                tripColor
            } else {
                categoryColor(category.categoryColor)
            },
            subLabel = percentLabel(category.expenseCents, total),
        )
    }
    return if (tail.isEmpty()) {
        items
    } else {
        val otherCents = tail.sumOf { it.expenseCents }
        items + TreemapItem(
            key = "other",
            label = otherLabel,
            valueCents = otherCents,
            color = otherColor,
            subLabel = percentLabel(otherCents, total),
        )
    }
}

private fun percentLabel(valueCents: Long, totalCents: Long): String =
    formatPercentLabel(valueCents.toFloat() / totalCents.toFloat())

@Composable
private fun CategoryDistributionRow(
    category: AnalysisCategoryTotal,
    totalExpenseCents: Long,
    divisor: Long,
    sparkValues: List<Float>,
) {
    val color = if (category.rowKind == AnalysisBreakdownKind.TRIP) {
        FinanceTheme.colors.transfer
    } else {
        categoryColor(category.categoryColor)
    }
    val fraction = (category.expenseCents.toFloat() / totalExpenseCents.toFloat()).coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(
                icon = if (category.rowKind == AnalysisBreakdownKind.TRIP) {
                    Icons.Outlined.Flight
                } else {
                    categoryIcon(category.categoryIcon)
                },
                contentDescription = null,
                color = color,
                size = 36.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = category.tripName ?: category.categoryName ?: stringResource(R.string.common_no_category),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    cents = category.expenseCents.divideCents(divisor),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = percentLabel(category.expenseCents, totalExpenseCents),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
            if (sparkValues.count { it > 0f } >= 2) {
                Spacer(modifier = Modifier.width(12.dp))
                Sparkline(
                    values = sparkValues,
                    color = color,
                    modifier = Modifier.width(64.dp),
                    height = 18.dp,
                    accessibilitySummary = stringResource(
                        R.string.accessibility_chart_sparkline_summary,
                        category.categoryName ?: stringResource(R.string.common_no_category),
                        chartTrendLabel(sparkValues.firstOrNull()?.times(100f)?.toLong(), sparkValues.lastOrNull()?.times(100f)?.toLong()),
                        sparkValues.firstOrNull()?.times(100f)?.toLong()?.let(::formatEuroCents)
                            ?: stringResource(R.string.dashboard_no_data),
                        sparkValues.lastOrNull()?.times(100f)?.toLong()?.let(::formatEuroCents)
                            ?: stringResource(R.string.dashboard_no_data),
                    ),
                )
            }
        }
    }
}

private const val FREQUENCY_MAX_BUBBLES = 10

/**
 * Frequency-vs-volume bubble chart: x = number of movements, y = total spend, bubble area =
 * average per movement. Each bubble is labelled and the axes carry tick values, so a category's
 * spending shape (frequent-cheap vs rare-expensive) is legible at a glance.
 */
@Composable
private fun FrequencyVolumeChart(
    points: List<AnalysisCategoryFrequency>,
    divisor: Long,
    periodLabel: String,
) {
    val shown = points.sortedByDescending { it.totalCents }.take(FREQUENCY_MAX_BUBBLES)
    val maxCount = shown.maxOf { it.movementCount }.coerceAtLeast(1L)
    val maxTotal = shown.maxOf { it.totalCents }.coerceAtLeast(1L)
    val maxAvg = shown.maxOf { it.totalCents.toFloat() / it.movementCount.coerceAtLeast(1L) }
        .coerceAtLeast(1f)

    val gridColor = FinanceTheme.colors.cardBorder
    val tickColor = FinanceTheme.colors.mutedText
    val labelColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val tickStyle = MaterialTheme.typography.labelSmall.copy(color = tickColor)
    val bubbleLabelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)

    val leader = shown.firstOrNull()?.categoryName ?: stringResource(R.string.dashboard_no_data)
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AccessibleChart(
                summary = stringResource(
                    R.string.accessibility_chart_bubble_summary,
                    stringResource(R.string.analysis_scatter_title),
                    periodLabel,
                    shown.size,
                    leader,
                    stringResource(R.string.accessibility_chart_no_trend),
                ),
                dataRows = shown.map { point ->
                    ChartDataRow(
                        point.categoryName ?: stringResource(R.string.common_no_category),
                        "${point.movementCount} · ${formatEuroCents(point.totalCents.divideCents(divisor))}",
                    )
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    ) {
                val leftGutter = 46.dp.toPx()
                val bottomGutter = 18.dp.toPx()
                val topPad = 18.dp.toPx()
                val rightPad = 8.dp.toPx()
                val plotLeft = leftGutter
                val plotRight = size.width - rightPad
                val plotBottom = size.height - bottomGutter
                val plotTop = topPad
                val plotW = (plotRight - plotLeft).coerceAtLeast(1f)
                val plotH = (plotBottom - plotTop).coerceAtLeast(1f)

                // Axis lines.
                drawLine(gridColor, Offset(plotLeft, plotTop), Offset(plotLeft, plotBottom), 1f)
                drawLine(gridColor, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), 1f)

                // Y (€) gridlines + ticks.
                for (i in 0..2) {
                    val valueCents = maxTotal * i / 2
                    val y = plotBottom - (valueCents.toFloat() / maxTotal) * plotH
                    if (i > 0) drawLine(gridColor, Offset(plotLeft, y), Offset(plotRight, y), 1f)
                    val text = textMeasurer.measure(formatEuroCompact(valueCents.divideCents(divisor)), tickStyle)
                    drawText(
                        text,
                        topLeft = Offset(plotLeft - text.size.width - 4.dp.toPx(), y - text.size.height / 2f),
                    )
                }

                // X (count) ticks.
                val countTicks = listOf(0L, maxCount / 2, maxCount).distinct()
                countTicks.forEach { count ->
                    val x = plotLeft + (count.toFloat() / maxCount) * plotW
                    val text = textMeasurer.measure(count.toString(), tickStyle)
                    drawText(
                        text,
                        topLeft = Offset(x - text.size.width / 2f, plotBottom + 3.dp.toPx()),
                    )
                }

                // Bubbles (largest first so labels for big categories win the overlap check).
                val drawnLabels = mutableListOf<Rect>()
                val minR = 6.dp.toPx()
                val maxR = 16.dp.toPx()
                shown.forEach { p ->
                    val cx = plotLeft + (p.movementCount.toFloat() / maxCount) * plotW
                    val cy = plotBottom - (p.totalCents.toFloat() / maxTotal) * plotH
                    val avg = p.totalCents.toFloat() / p.movementCount.coerceAtLeast(1L)
                    val radius = minR + (maxR - minR) * sqrt(avg / maxAvg)
                    val color = categoryColor(p.categoryColor)
                    drawCircle(color = color.copy(alpha = 0.85f), radius = radius, center = Offset(cx, cy))

                    val name = p.categoryName ?: return@forEach
                    val text = textMeasurer.measure(name, bubbleLabelStyle, maxLines = 1)
                    val gap = 4.dp.toPx()
                    // Prefer placing the label to the right of the bubble; flip left near the edge.
                    val toRight = cx + radius + gap + text.size.width <= plotRight
                    val lx = if (toRight) cx + radius + gap else cx - radius - gap - text.size.width
                    val ly = cy - text.size.height / 2f
                    val rect = Rect(lx, ly, lx + text.size.width, ly + text.size.height)
                    if (lx >= plotLeft && drawnLabels.none { it.overlaps(rect) }) {
                        drawText(text, topLeft = Offset(lx, ly))
                        drawnLabels += rect
                    }
                }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = stringResource(R.string.analysis_scatter_y),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            text = stringResource(R.string.analysis_scatter_x),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        text = stringResource(R.string.analysis_scatter_bubble_hint),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
