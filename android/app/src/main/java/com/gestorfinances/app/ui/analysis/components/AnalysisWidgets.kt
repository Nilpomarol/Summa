package com.gestorfinances.app.ui.analysis.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisAccountFlowBucket
import com.gestorfinances.app.data.repository.AnalysisBreakdownKind
import com.gestorfinances.app.data.repository.AnalysisBucket
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisCategoryTrendPoint
import com.gestorfinances.app.data.repository.AnalysisIncomeExpenseBucket
import com.gestorfinances.app.data.repository.AnalysisLargestExpense
import com.gestorfinances.app.data.repository.AnalysisMerchantTotal
import com.gestorfinances.app.data.repository.AnalysisNetWorthPoint
import com.gestorfinances.app.data.repository.AnalysisOneTimeMode
import com.gestorfinances.app.data.repository.AnalysisPeriodTotals
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IncomeExpenseChart
import com.gestorfinances.app.ui.common.IncomeExpenseChartPoint
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.TrendLineChart
import com.gestorfinances.app.ui.common.TrendSeries
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatBasisPoints
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatLongDate
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementOneTimeMode as MovementFilterOneTimeMode
import com.gestorfinances.app.ui.movements.MovementSourceMode
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

import com.gestorfinances.app.ui.analysis.*

@Composable
internal fun CategoryBreakdownRow(
    category: AnalysisCategoryTotal,
    maxCents: Long,
    divisor: Long,
    onClick: () -> Unit,
) {
    val color = if (category.rowKind == AnalysisBreakdownKind.TRIP) {
        FinanceTheme.colors.transfer
    } else {
        categoryColor(category.categoryColor)
    }
    val amount = category.netCents
    val displayAmount = amount.divideCents(divisor)
    val title = if (category.rowKind == AnalysisBreakdownKind.TRIP) {
        category.tripName?.let { stringResource(R.string.trip_analysis_block_title, it) }
            ?: stringResource(R.string.nav_trips)
    } else {
        category.categoryName ?: stringResource(R.string.common_no_category)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(10.dp))
            MoneyText(
                cents = displayAmount,
                color = if (amount >= 0) FinanceTheme.colors.income else MaterialTheme.colorScheme.onSurface,
                signed = true,
            )
        }
        LinearProgressIndicator(
            progress = { (abs(amount).toFloat() / maxCents.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
internal fun AccountFlowBreakdownRow(
    flow: AnalysisAccountFlowBucket,
    divisor: Long,
    onClick: () -> Unit,
) {
    val displayAmount = flow.deltaCents.divideCents(divisor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = flow.accountName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = flow.bucket,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        MoneyText(
            cents = displayAmount,
            color = if (flow.deltaCents >= 0) FinanceTheme.colors.income else MaterialTheme.colorScheme.onSurface,
            signed = true,
        )
    }
}

@Composable
internal fun EmptyAnalysisCard() {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.analysis_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.analysis_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun RecurringCostSummaryWidget(summary: RecurringCostSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = stringResource(R.string.recurring_cost_summary_title))
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(
                        icon = Icons.Outlined.Autorenew,
                        contentDescription = null,
                        color = MaterialTheme.colorScheme.primary,
                        size = 36.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(
                            R.string.recurring_cost_summary_body,
                            formatEuroCents(summary.monthlyExpenseCents),
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                summary.items.take(MAX_RECURRING_COST_ITEMS).forEach { item ->
                    RecurringCostItemRow(item = item)
                }
            }
        }
    }
}

@Composable
internal fun RecurringCostItemRow(item: RecurringCostItem) {
    val title = item.label
        ?: item.categoryName
        ?: stringResource(R.string.common_no_category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.categoryName?.takeIf { it != title }?.let { category ->
                Text(
                    text = category,
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        MoneyText(
            cents = item.monthlyExpenseCents,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
internal fun LargestExpenseRow(
    expense: AnalysisLargestExpense,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(
            icon = categoryIcon(expense.categoryIcon),
            contentDescription = null,
            color = categoryColor(expense.categoryColor),
            size = 36.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = expense.label
                    ?: expense.categoryName
                    ?: stringResource(R.string.common_no_category),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatLongDate(expense.date),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        MoneyText(
            cents = expense.amountCents,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun MerchantRow(merchant: AnalysisMerchantTotal) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = merchant.merchantLabel ?: stringResource(R.string.analysis_merchant_none),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.analysis_merchant_count,
                    merchant.movementCount.toInt(),
                    merchant.movementCount.toInt(),
                ),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        MoneyText(
            cents = merchant.totalCents,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun SavingsRateRow(
    bucket: AnalysisIncomeExpenseBucket,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = bucket.bucket,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatBasisPoints(bucket.savingsRateBasisPoints),
                color = if (bucket.savingsRateBasisPoints >= 0) {
                    FinanceTheme.colors.income
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.titleSmall,
            )
        }
        LinearProgressIndicator(
            progress = {
                (bucket.savingsRateBasisPoints.toFloat() / 10000f).coerceIn(0f, 1f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = FinanceTheme.colors.income,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
internal fun NetWorthTrendWidget(points: List<AnalysisNetWorthPoint>) {
    val series = listOf(
        TrendSeries(
            label = stringResource(R.string.analysis_net_worth_title),
            color = MaterialTheme.colorScheme.primary,
            pointsEuros = points.sortedBy { it.bucket }.map { it.netWorthCents / 100f },
        ),
    )
    TrendLineChart(
        title = stringResource(R.string.analysis_net_worth_title),
        series = series,
        emptyText = stringResource(R.string.dashboard_no_data),
    )
}

@Composable
internal fun CategoryTrendsWidget(trends: List<AnalysisCategoryTrendPoint>) {
    val buckets = trends.map { it.bucket }.distinct().sorted()
    val byCategory = trends.groupBy { it.categoryId }
    val topCategories = byCategory.entries
        .sortedByDescending { entry -> entry.value.sumOf { it.expenseCents } }
        .take(MAX_TREND_SERIES)
    val noCategory = stringResource(R.string.common_no_category)
    val series = topCategories.map { (_, rows) ->
        val byBucket = rows.associate { it.bucket to it.expenseCents }
        TrendSeries(
            label = rows.first().categoryName ?: noCategory,
            color = categoryColor(rows.first().categoryColor),
            pointsEuros = buckets.map { (byBucket[it] ?: 0L) / 100f },
        )
    }
    TrendLineChart(
        title = stringResource(R.string.analysis_category_trends_title),
        series = series,
        emptyText = stringResource(R.string.dashboard_no_data),
    )
}

@Composable
internal fun SpendingHeatmapWidget(cells: List<HeatmapCell>) {
    val maxCents = cells.maxOfOrNull { it.expenseCents }?.coerceAtLeast(1L) ?: 1L
    // Spend intensity is a neutral ink ramp: expense is ink, not a hue, and indigo is
    // reserved for action (design §2.4 / §2.3). Darker cell = more spent that day.
    val base = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = stringResource(R.string.analysis_heatmap_title))
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeatmapGrid(cells = cells, maxCents = maxCents, base = base, ink = ink)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.analysis_heatmap_legend_less),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    listOf(0f, 0.33f, 0.66f, 1f).forEach { intensity ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = heatColor(intensity, base, ink),
                                    shape = MaterialTheme.shapes.extraSmall,
                                ),
                        )
                    }
                    Text(
                        text = stringResource(R.string.analysis_heatmap_legend_more),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeatmapGrid(
    cells: List<HeatmapCell>,
    maxCents: Long,
    base: androidx.compose.ui.graphics.Color,
    ink: androidx.compose.ui.graphics.Color,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cells.forEach { cell ->
            val intensity = cell.expenseCents.toFloat() / maxCents.toFloat()
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = heatColor(intensity, base, ink),
                        shape = MaterialTheme.shapes.extraSmall,
                    ),
            )
        }
    }
}

internal fun heatColor(
    intensity: Float,
    base: androidx.compose.ui.graphics.Color,
    ink: androidx.compose.ui.graphics.Color,
): androidx.compose.ui.graphics.Color {
    if (intensity <= 0f) return base
    return lerp(base, ink, (0.15f + 0.85f * intensity.coerceIn(0f, 1f)))
}

internal fun LazyListScope.analysisWidgets(
    state: AnalysisUiState,
    onDrillDown: (MovementFilters) -> Unit,
) {
    if (state.recurringCostSummary.hasCosts) {
        item {
            RecurringCostSummaryWidget(summary = state.recurringCostSummary)
        }
    }

    if (state.netWorthPoints.size >= 2) {
        item {
            NetWorthTrendWidget(points = state.netWorthPoints)
        }
    }

    if (state.largestExpenses.isNotEmpty()) {
        item {
            SectionHeader(title = stringResource(R.string.analysis_largest_expenses_title))
        }
        items(items = state.largestExpenses, key = { it.sourceId }) { expense ->
            LargestExpenseRow(
                expense = expense,
                onClick = { state.largestExpenseFilters(expense)?.let(onDrillDown) },
            )
        }
    }

    if (state.topMerchants.isNotEmpty()) {
        item {
            SectionHeader(title = stringResource(R.string.analysis_top_merchants_title))
        }
        items(items = state.topMerchants, key = { it.merchantLabel ?: "_none" }) { merchant ->
            MerchantRow(merchant = merchant)
        }
    }

    val savingsBuckets = state.chartBuckets.filter { it.incomeCents > 0 }
    if (savingsBuckets.size >= 2) {
        item {
            SectionHeader(title = stringResource(R.string.analysis_savings_rate_period_title))
        }
        items(items = savingsBuckets, key = { it.bucket }) { bucket ->
            SavingsRateRow(
                bucket = bucket,
                onClick = { state.savingsBucketFilters(bucket.bucket)?.let(onDrillDown) },
            )
        }
    }

    if (state.categoryTrends.isNotEmpty()) {
        item {
            CategoryTrendsWidget(trends = state.categoryTrends)
        }
    }

    if (state.heatmapDays.isNotEmpty() && state.currentRange != null) {
        item {
            SpendingHeatmapWidget(cells = state.heatmapCells())
        }
    }
}
