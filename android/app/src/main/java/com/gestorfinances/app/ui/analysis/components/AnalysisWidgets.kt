package com.gestorfinances.app.ui.analysis.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisBreakdownKind
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisCategoryTrendPoint
import com.gestorfinances.app.data.repository.AnalysisIncomeExpenseBucket
import com.gestorfinances.app.data.repository.AnalysisNetWorthPoint
import com.gestorfinances.app.data.repository.AnalysisPeriodTotals
import com.gestorfinances.app.data.repository.AnalysisWeekdaySpend
import com.gestorfinances.app.ui.analysis.RecurringCostItem
import com.gestorfinances.app.ui.analysis.RecurringCostSummary
import com.gestorfinances.app.ui.analysis.divideCents
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.ChartDataRow
import com.gestorfinances.app.ui.common.HeatmapCell
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.RadarAxis
import com.gestorfinances.app.ui.common.RadarChart
import com.gestorfinances.app.ui.common.SavingsRateBar
import com.gestorfinances.app.ui.common.SavingsRateChart
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.SpendingHeatmap
import com.gestorfinances.app.ui.common.TrendLineChart
import com.gestorfinances.app.ui.common.TrendSeries
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatBasisPoints
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatLongDate
import com.gestorfinances.app.ui.common.formatMonth
import com.gestorfinances.app.ui.common.formatPercentLabel
import com.gestorfinances.app.ui.common.formatWeekdayDate
import com.gestorfinances.app.ui.common.chartTrendLabel
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

const val MAX_TREND_SERIES = 4
const val MAX_RECURRING_COST_ITEMS = 3

/** A category (or trip) row: icon, name, net amount, and a weight bar (design §6). */
@Composable
internal fun CategoryBreakdownRow(
    category: AnalysisCategoryTotal,
    maxCents: Long,
    divisor: Long,
    totalCents: Long,
) {
    val color = if (category.rowKind == AnalysisBreakdownKind.TRIP) {
        FinanceTheme.colors.transfer
    } else {
        categoryColor(category.categoryColor)
    }
    val amount = category.netCents
    val displayAmount = amount.divideCents(divisor)
    val fraction = (abs(amount).toFloat() / totalCents.toFloat()).coerceIn(0f, 1f)
    val pctText = formatPercentLabel(fraction)
    val title = if (category.rowKind == AnalysisBreakdownKind.TRIP) {
        category.tripName?.let { stringResource(R.string.trip_analysis_block_title, it) }
            ?: stringResource(R.string.nav_trips)
    } else {
        category.categoryName ?: stringResource(R.string.common_no_category)
    }
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
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    cents = displayAmount,
                    color = if (amount >= 0) FinanceTheme.colors.income else FinanceTheme.colors.debt,
                    signed = true,
                )
                Text(
                    text = pctText,
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                )
            }
        }
        LinearProgressIndicator(
            progress = { (abs(amount).toFloat() / maxCents.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

@Composable
internal fun SummaryKPIsCard(
    totals: AnalysisPeriodTotals,
    divisor: Long,
) {
    val net = (totals.actualIncomeCents - totals.actualExpenseCents).divideCents(divisor)
    val savingsPositive = totals.savingsRateBasisPoints >= 0
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                KpiCell(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.ArrowUpward,
                    accent = FinanceTheme.colors.income,
                    label = stringResource(R.string.analysis_summary_income),
                ) {
                    MoneyText(
                        cents = totals.actualIncomeCents.divideCents(divisor),
                        color = FinanceTheme.colors.income,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                KpiCell(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.ArrowDownward,
                    accent = FinanceTheme.colors.debt,
                    label = stringResource(R.string.analysis_summary_expense),
                ) {
                    MoneyText(
                        cents = (-totals.actualExpenseCents).divideCents(divisor),
                        color = FinanceTheme.colors.debt,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            // Proportional income (green) vs expense (red) split — the period balance at a glance.
            IncomeExpenseSplitBar(
                incomeCents = totals.actualIncomeCents,
                expenseCents = totals.actualExpenseCents,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                KpiCell(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.SwapVert,
                    accent = if (net >= 0) FinanceTheme.colors.income else FinanceTheme.colors.debt,
                    label = stringResource(R.string.dashboard_net_flow),
                ) {
                    MoneyText(
                        cents = net,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        signed = true,
                    )
                }
                KpiCell(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Savings,
                    accent = if (savingsPositive) FinanceTheme.colors.income else FinanceTheme.colors.debt,
                    label = stringResource(R.string.dashboard_savings_short),
                ) {
                    Text(
                        text = if (totals.actualIncomeCents > 0) {
                            formatBasisPoints(totals.savingsRateBasisPoints)
                        } else {
                            stringResource(R.string.dashboard_savings_rate_unavailable)
                        },
                        color = if (savingsPositive) FinanceTheme.colors.income else FinanceTheme.colors.debt,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/** A KPI tile: tinted icon chip + label on top, the value composable below at full cell width. */
@Composable
private fun KpiCell(
    icon: ImageVector,
    accent: Color,
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconChip(icon = icon, contentDescription = null, color = accent, size = 34.dp)
            Text(
                text = label,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        value()
    }
}

/** Horizontal bar whose green/red widths are the income/expense proportions of the period. */
@Composable
private fun IncomeExpenseSplitBar(
    incomeCents: Long,
    expenseCents: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(MaterialTheme.shapes.small),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (incomeCents + expenseCents <= 0L) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            if (incomeCents > 0L) {
                Box(
                    modifier = Modifier
                        .weight(incomeCents.toFloat())
                        .fillMaxHeight()
                        .background(FinanceTheme.colors.income),
                )
            }
            if (expenseCents > 0L) {
                Box(
                    modifier = Modifier
                        .weight(expenseCents.toFloat())
                        .fillMaxHeight()
                        .background(FinanceTheme.colors.debt),
                )
            }
        }
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

/**
 * Històric tab hero: "how financially safe am I?" — days of runway the current net worth would
 * cover at the scope's average daily expense, plus the average savings rate for the same scope.
 * Stronger visual treatment than regular widgets (dark [FinanceTheme.colors.heroSurface], design
 * §7) so it reads as the tab's headline, not another chart card.
 */
@Composable
internal fun BufferHeroCard(
    daysOfBuffer: Long?,
    savingsRateBasisPoints: Long,
    hasIncome: Boolean,
) {
    val savingsPositive = savingsRateBasisPoints >= 0
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = FinanceTheme.colors.heroSurface,
        contentColor = FinanceTheme.colors.heroOnSurface,
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(
                    icon = Icons.Outlined.Shield,
                    contentDescription = null,
                    color = FinanceTheme.colors.income,
                    size = 34.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.analysis_buffer_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = FinanceTheme.colors.heroOnSurfaceMuted,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (daysOfBuffer != null) {
                Text(
                    text = stringResource(R.string.analysis_buffer_days_value, daysOfBuffer),
                    color = FinanceTheme.colors.heroOnSurface,
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 40.sp),
                )
                Text(
                    // Disambiguates from the Fix/Var tab's own "Dies de marge" card, which is
                    // based on fixed recurring costs only — this one uses total actual expense.
                    text = stringResource(R.string.analysis_buffer_basis_caption),
                    color = FinanceTheme.colors.heroOnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    text = stringResource(R.string.analysis_buffer_no_expense_data),
                    color = FinanceTheme.colors.heroOnSurfaceMuted,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = FinanceTheme.colors.heroOnSurface.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.analysis_savings_rate_average_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = FinanceTheme.colors.heroOnSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (hasIncome) {
                        formatBasisPoints(savingsRateBasisPoints)
                    } else {
                        stringResource(R.string.dashboard_savings_rate_unavailable)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (!hasIncome) {
                        FinanceTheme.colors.heroOnSurfaceMuted
                    } else if (savingsPositive) {
                        FinanceTheme.colors.income
                    } else {
                        FinanceTheme.colors.debt
                    },
                )
            }
        }
    }
}

/**
 * Històric tab: "am I saving more or less over time?" — one bar per historical period from
 * [buckets] (already at the scope's natural bucket granularity), positive/negative colored by
 * surplus vs. deficit, with a smoothed trend line (see [SavingsRateChart]).
 */
@Composable
internal fun SavingsRateTrendWidget(buckets: List<AnalysisIncomeExpenseBucket>) {
    val orderedBuckets = buckets.sortedBy { it.bucket }
    val bars = orderedBuckets
        .map { SavingsRateBar(label = formatBucketLabel(it.bucket), basisPoints = it.savingsRateBasisPoints) }
    val title = stringResource(R.string.analysis_savings_rate_period_title)
    val period = bars.firstOrNull()?.label?.let { first ->
        bars.lastOrNull()?.label?.let { last -> if (first == last) first else "$first - $last" }
    } ?: stringResource(R.string.analysis_scope_all_time)
    val best = bars.maxByOrNull { it.basisPoints }
    val worst = bars.minByOrNull { it.basisPoints }
    val trend = chartTrendLabel(bars.firstOrNull()?.basisPoints, bars.lastOrNull()?.basisPoints)
    SavingsRateChart(
        title = title,
        bars = bars,
        positiveColor = FinanceTheme.colors.income,
        negativeColor = FinanceTheme.colors.debt,
        positiveLabel = stringResource(R.string.analysis_savings_rate_surplus),
        negativeLabel = stringResource(R.string.analysis_savings_rate_deficit),
        emptyText = stringResource(R.string.dashboard_no_data),
        accessibilitySummary = stringResource(
            R.string.accessibility_chart_savings_summary,
            title,
            period,
            best?.let { "${it.label} ${formatBasisPoints(it.basisPoints)}" } ?: stringResource(R.string.dashboard_no_data),
            worst?.let { "${it.label} ${formatBasisPoints(it.basisPoints)}" } ?: stringResource(R.string.dashboard_no_data),
            if ((bars.lastOrNull()?.basisPoints ?: 0L) >= 0L) {
                stringResource(R.string.analysis_savings_rate_surplus)
            } else {
                stringResource(R.string.analysis_savings_rate_deficit)
            },
            trend,
        ),
        accessibilityRows = bars.map { bar ->
            ChartDataRow(bar.label, formatBasisPoints(bar.basisPoints))
        },
    )
}

/** Short display label for an `analysisIncomeVsExpense` bucket string ("YYYY-MM-DD"/"YYYY-MM"/"YYYY"). */
private fun formatBucketLabel(bucket: String): String =
    when (bucket.length) {
        10 -> runCatching { LocalDate.parse(bucket).dayOfMonth.toString() }.getOrDefault(bucket)
        7 -> runCatching { formatMonth(YearMonth.parse(bucket)) }.getOrDefault(bucket)
        else -> bucket
    }

/** Històric tab: spending heatmap wrapped in the same section-header + card shell as the other widgets. */
@Composable
internal fun SpendingHeatmapWidget(cells: List<HeatmapCell>, periodLabel: String) {
    val title = stringResource(R.string.analysis_heatmap_title)
    val totalCents = cells.sumOf { it.expenseCents }
    val highest = cells.maxByOrNull { it.expenseCents }
    val trend = chartTrendLabel(cells.firstOrNull()?.expenseCents, cells.lastOrNull()?.expenseCents)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = title)
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                SpendingHeatmap(
                    cells = cells,
                    accessibilitySummary = stringResource(
                        R.string.accessibility_chart_heatmap_summary,
                        title,
                        periodLabel,
                        formatEuroCents(totalCents),
                        highest?.let { formatWeekdayDate(it.date.toString()) } ?: stringResource(R.string.dashboard_no_data),
                        trend,
                    ),
                    accessibilityRows = cells.map { cell ->
                        ChartDataRow(formatWeekdayDate(cell.date.toString()), formatEuroCents(cell.expenseCents))
                    },
                )
            }
        }
    }
}

/** Històric tab: day-of-week radar wrapped in the same section-header + card shell as the other widgets. */
@Composable
internal fun WeekdayRadarWidget(weekday: List<AnalysisWeekdaySpend>, periodLabel: String) {
    val title = stringResource(R.string.analysis_weekday_title)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = title)
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                val labels = stringArrayResource(R.array.analysis_weekday_short)
                val byWeekday = weekday.associate { it.weekday to it.expenseCents }
                val axes = (0..6).map { day ->
                    RadarAxis(
                        label = labels.getOrElse(day) { day.toString() },
                        value = (byWeekday[day] ?: 0L).coerceAtLeast(0L) / 100f,
                    )
                }
                val totalCents = weekday.sumOf { it.expenseCents }
                val highest = weekday.maxByOrNull { it.expenseCents }
                RadarChart(
                    axes = axes,
                    color = MaterialTheme.colorScheme.primary,
                    accessibilitySummary = stringResource(
                        R.string.accessibility_chart_radar_summary,
                        title,
                        periodLabel,
                        formatEuroCents(totalCents),
                        highest?.let { highestDay ->
                            labels.getOrElse(highestDay.weekday) { highestDay.weekday.toString() }
                        } ?: stringResource(R.string.dashboard_no_data),
                        chartTrendLabel(weekday.firstOrNull()?.expenseCents, weekday.lastOrNull()?.expenseCents),
                    ),
                    accessibilityRows = axes.map { axis ->
                        ChartDataRow(axis.label, formatEuroCents((axis.value * 100f).toLong()))
                    },
                )
            }
        }
    }
}

@Composable
internal fun NetWorthTrendWidget(points: List<AnalysisNetWorthPoint>) {
    val sorted = points.sortedBy { it.bucket }
    val title = stringResource(R.string.analysis_net_worth_title)
    val series = listOf(
        TrendSeries(
            label = title,
            color = MaterialTheme.colorScheme.primary,
            pointsEuros = sorted.map { it.netWorthCents / 100f },
        ),
    )
    val latest = sorted.lastOrNull()?.netWorthCents
    val first = sorted.firstOrNull()?.netWorthCents
    val period = sorted.firstOrNull()?.bucket?.let { firstBucket ->
        sorted.lastOrNull()?.bucket?.let { lastBucket ->
            "${formatBucketLabel(firstBucket)} - ${formatBucketLabel(lastBucket)}"
        }
    } ?: stringResource(R.string.analysis_scope_all_time)
    TrendLineChart(
        title = title,
        series = series,
        labels = sorted.map { formatBucketLabel(it.bucket) },
        emptyText = stringResource(R.string.dashboard_no_data),
        accessibilitySummary = stringResource(
            R.string.accessibility_chart_period_summary,
            title,
            period,
            latest?.let(::formatEuroCents) ?: stringResource(R.string.dashboard_no_data),
            if ((latest ?: 0L) >= 0L) stringResource(R.string.accessibility_chart_result_positive)
            else stringResource(R.string.accessibility_chart_result_negative),
            chartTrendLabel(first, latest),
        ),
        accessibilityRows = sorted.map { point ->
            ChartDataRow(formatBucketLabel(point.bucket), formatEuroCents(point.netWorthCents))
        },
        trailing = latest?.let {
            {
                MoneyText(
                    cents = it,
                    color = if (it >= 0) FinanceTheme.colors.income else FinanceTheme.colors.debt,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
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
    val title = stringResource(R.string.analysis_stacked_title)
    val firstTotal = trends.filter { it.bucket == buckets.firstOrNull() }.sumOf { it.expenseCents }
    val lastTotal = trends.filter { it.bucket == buckets.lastOrNull() }.sumOf { it.expenseCents }
    val leader = series.firstOrNull()?.label ?: stringResource(R.string.dashboard_no_data)
    TrendLineChart(
        title = title,
        series = series,
        labels = buckets.map { formatBucketLabel(it) },
        emptyText = stringResource(R.string.dashboard_no_data),
        accessibilitySummary = stringResource(
            R.string.accessibility_chart_period_summary,
            title,
            if (buckets.isEmpty()) stringResource(R.string.analysis_scope_all_time)
            else "${formatBucketLabel(buckets.first())} - ${formatBucketLabel(buckets.last())}",
            formatEuroCents(trends.sumOf { it.expenseCents }),
            stringResource(R.string.accessibility_chart_category_leader, leader),
            chartTrendLabel(firstTotal, lastTotal),
        ),
        accessibilityRows = buckets.map { bucket ->
            val values = series.map { line ->
                val euros = line.pointsEuros.getOrNull(buckets.indexOf(bucket)) ?: 0f
                "${line.label}: ${formatEuroCents((euros * 100f).toLong())}"
            }
            ChartDataRow(formatBucketLabel(bucket), values.joinToString(" · "))
        },
    )
}

/**
 * "Cost dels periòdics" — the monthly-equivalent cost of active fixed recurring templates.
 * The header carries the total (design §6 trailing slot); each row below is sized by a magnitude
 * bar proportional to its share of that total (bar length = the percentage shown), so the biggest
 * recurring commitments read visually rather than as a plain list of numbers.
 */
@Composable
internal fun RecurringCostSummaryWidget(summary: RecurringCostSummary) {
    val items = summary.items.take(MAX_RECURRING_COST_ITEMS)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = stringResource(R.string.recurring_cost_summary_title),
            trailing = {
                MoneyText(
                    cents = summary.monthlyExpenseCents,
                    color = FinanceTheme.colors.debt,
                    style = MaterialTheme.typography.titleSmall,
                )
            },
        )
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                items.forEachIndexed { index, item ->
                    RecurringCostItemRow(item = item, totalCents = summary.monthlyExpenseCents)
                    if (index != items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = FinanceTheme.colors.cardBorder,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringCostItemRow(item: RecurringCostItem, totalCents: Long) {
    val title = item.label
        ?: item.categoryName
        ?: stringResource(R.string.common_no_category)
    val shareOfTotal = if (totalCents > 0) {
        (item.monthlyExpenseCents.toFloat() / totalCents.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(
                icon = Icons.Outlined.Autorenew,
                contentDescription = null,
                color = FinanceTheme.colors.debt,
                size = 36.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val nextDueText = stringResource(R.string.recurring_next_due, formatLongDate(item.nextDueDate))
                val subtitle = item.categoryName?.takeIf { it != title }?.let { category ->
                    "$category · $nextDueText"
                } ?: nextDueText
                Text(
                    text = subtitle,
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    cents = item.monthlyExpenseCents,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = formatPercentLabel(shareOfTotal),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        LinearProgressIndicator(
            progress = { shareOfTotal },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = FinanceTheme.colors.debt,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

