package com.gestorfinances.app.ui.analysis.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisBreakdownKind
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisIncomeExpenseBucket
import com.gestorfinances.app.data.repository.AnalysisPeriodTotals
import com.gestorfinances.app.ui.analysis.AnalysisPeriodRange
import com.gestorfinances.app.ui.analysis.AnalysisScope
import com.gestorfinances.app.ui.analysis.AnalysisUiState
import com.gestorfinances.app.ui.analysis.formatForScope
import com.gestorfinances.app.ui.analysis.incomeExpensePoints
import com.gestorfinances.app.ui.analysis.labelRes
import com.gestorfinances.app.ui.analysis.rowKey
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.AppDropdownMenu
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MonthPickerContent
import com.gestorfinances.app.ui.common.RootPageHeader
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.TopBarIconButton
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.YearMonth
import kotlin.math.abs

private val mobileScopes = listOf(AnalysisScope.MONTH, AnalysisScope.YEAR, AnalysisScope.ALL_TIME)
private const val MAX_CATEGORIES = 6
private const val MAX_YEARS = 5

@Composable
internal fun AnalysisOverview(
    state: AnalysisUiState,
    onScopeSelected: (AnalysisScope) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onYearSelected: (Int) -> Unit,
    onOpenFilters: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val current = state.resum
    val comparison = state.comparativa
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RootPageHeader(
                title = stringResource(R.string.nav_analysis),
                trailing = {
                    AnalysisFilterAction(
                        active = state.hasActiveFilters,
                        onClick = onOpenFilters,
                    )
                },
            )
        }

        item {
            SegmentedControl(
                options = mobileScopes,
                selected = state.scope.takeIf { it in mobileScopes } ?: AnalysisScope.MONTH,
                label = { stringResource(it.mobileLabelRes()) },
                onSelect = onScopeSelected,
            )
        }

        item {
            PeriodNavigator(
                state = state,
                onPrevious = onPreviousPeriod,
                onNext = onNextPeriod,
                onMonthSelected = onMonthSelected,
                onYearSelected = onYearSelected,
            )
        }

        state.errorMessage?.let { message ->
            item { InlineBanner(kind = BannerKind.Error, text = message) }
        }

        if (current == null) {
            if (state.errorMessage == null) item { TabLoading() }
            return@LazyColumn
        }

        item {
            AnalysisHeroCard(
                scope = state.scope,
                totals = current.totals,
                comparisonTotals = comparison?.previousTotals,
                comparisonRange = comparison?.comparisonRange,
            )
        }

        if (state.scope != AnalysisScope.ALL_TIME && comparison?.comparisonRange != null && state.currentRange != null) {
            val currentPoints = incomeExpensePoints(
                range = state.currentRange,
                scope = state.scope,
                buckets = comparison.currentChartBuckets,
            )
            val previousPoints = incomeExpensePoints(
                range = comparison.comparisonRange,
                scope = state.scope,
                buckets = comparison.previousChartBuckets,
            )
            val comparisonIsPartial = comparison.comparisonRange.toDateExclusive < when (state.scope) {
                AnalysisScope.MONTH -> comparison.comparisonRange.fromDate.plusMonths(1)
                AnalysisScope.YEAR -> comparison.comparisonRange.fromDate.plusYears(1)
                AnalysisScope.ALL_TIME, AnalysisScope.CUSTOM -> comparison.comparisonRange.toDateExclusive
            }
            val visibleCurrent = if (comparisonIsPartial) currentPoints.take(previousPoints.size) else currentPoints
            item {
                val monthLabels = stringArrayResource(R.array.analysis_month_short)
                SpendingEvolutionChart(
                    current = visibleCurrent.map { point ->
                        SpendingEvolutionPoint(
                            label = point.evolutionLabel(state.scope, monthLabels),
                            expenseCents = point.expenseCents,
                        )
                    },
                    previous = previousPoints.map { point ->
                        SpendingEvolutionPoint(
                            label = point.evolutionLabel(state.scope, monthLabels),
                            expenseCents = point.expenseCents,
                        )
                    },
                    currentCaption = state.currentRange.formatForScope(state.scope),
                    previousCaption = comparison.comparisonRange.formatForScope(state.scope),
                    mode = if (state.scope == AnalysisScope.MONTH) {
                        SpendingEvolutionMode.CUMULATIVE_LINE
                    } else {
                        SpendingEvolutionMode.GROUPED_BARS
                    },
                )
            }
        }

        if (state.scope == AnalysisScope.ALL_TIME) {
            val years = annualBuckets(current.chartBuckets)
            if (years.size >= 2) {
                item { SectionHeader(title = stringResource(R.string.analysis_mobile_yearly_evolution)) }
                item { YearlyEvolutionCard(years = years) }
            }
        }

        val categories = current.topCategories
            .filter { it.expenseCents > 0 }
            .sortedByDescending { it.expenseCents }
        if (categories.isEmpty()) {
            item { EmptyAnalysisCard() }
        } else {
            item { SectionHeader(title = stringResource(R.string.analysis_top_categories_title)) }
            item {
                CategoryOverviewCard(
                    categories = categories.take(MAX_CATEGORIES),
                    totalExpenseCents = current.totals.actualExpenseCents,
                    previousCategories = comparison?.previousByCategory.orEmpty(),
                    showComparison = state.scope != AnalysisScope.ALL_TIME,
                )
            }
        }
    }
}

@Composable
private fun AnalysisFilterAction(active: Boolean, onClick: () -> Unit) {
    Box {
        TopBarIconButton(
            icon = Icons.Outlined.FilterList,
            contentDescription = stringResource(R.string.analysis_filters_open),
            onClick = onClick,
        )
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraLarge),
            )
        }
    }
}

@Composable
private fun PeriodNavigator(
    state: AnalysisUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onYearSelected: (Int) -> Unit,
) {
    var pickerExpanded by remember { mutableStateOf(false) }
    val label = when (state.scope) {
        AnalysisScope.MONTH -> formatMonthYear(state.month)
        AnalysisScope.YEAR -> state.year.toString()
        AnalysisScope.ALL_TIME -> state.activityMonths.minOrNull()?.let { first ->
            stringResource(R.string.analysis_mobile_since, formatMonthYear(first))
        } ?: stringResource(R.string.analysis_scope_all_time)
        AnalysisScope.CUSTOM -> state.currentRange?.formatForScope(state.scope).orEmpty()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (state.canMovePeriod) {
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (state.canMovePeriod) Modifier.clickable { pickerExpanded = true } else Modifier)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = FinanceTheme.colors.mutedText,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AppDropdownMenu(
                expanded = pickerExpanded,
                onDismissRequest = { pickerExpanded = false },
            ) {
                when (state.scope) {
                    AnalysisScope.MONTH -> MonthPickerContent(
                        initial = state.month,
                        availableMonths = state.activityMonths,
                        onSelect = {
                            onMonthSelected(it)
                            pickerExpanded = false
                        },
                    )
                    AnalysisScope.YEAR -> state.activityMonths.map { it.year }.distinct().sortedDescending().forEach { year ->
                        TextButton(
                            onClick = {
                                onYearSelected(year)
                                pickerExpanded = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = year.toString(),
                                fontWeight = if (year == state.year) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    AnalysisScope.ALL_TIME, AnalysisScope.CUSTOM -> Unit
                }
            }
        }
        if (state.canMovePeriod) {
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.common_next),
                )
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

@Composable
private fun AnalysisHeroCard(
    scope: AnalysisScope,
    totals: AnalysisPeriodTotals,
    comparisonTotals: AnalysisPeriodTotals?,
    comparisonRange: AnalysisPeriodRange?,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(
                    if (scope == AnalysisScope.ALL_TIME) {
                        R.string.analysis_mobile_total_expense
                    } else {
                        R.string.analysis_summary_expense
                    },
                ),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.titleSmall,
            )
            MoneyText(
                cents = totals.actualExpenseCents,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineLarge,
            )
            if (comparisonTotals != null && comparisonRange != null) {
                ComparisonPill(
                    currentCents = totals.actualExpenseCents,
                    previousCents = comparisonTotals.actualExpenseCents,
                    previousLabel = comparisonRange.formatForScope(scope),
                )
            }
            HorizontalDivider(color = FinanceTheme.colors.cardBorder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SummaryValue(
                    label = stringResource(R.string.analysis_summary_income),
                    cents = totals.actualIncomeCents,
                    color = FinanceTheme.colors.income,
                    modifier = Modifier.weight(1f),
                )
                SummaryValue(
                    label = stringResource(R.string.analysis_comparison_net_label),
                    cents = totals.netActualCents,
                    color = if (totals.netActualCents < 0) FinanceTheme.colors.debt else MaterialTheme.colorScheme.onSurface,
                    signed = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryValue(
    label: String,
    cents: Long,
    color: Color,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, color = FinanceTheme.colors.mutedText, style = MaterialTheme.typography.labelMedium)
        MoneyText(cents = cents, color = color, style = MaterialTheme.typography.titleMedium, signed = signed)
    }
}

@Composable
private fun ComparisonPill(currentCents: Long, previousCents: Long, previousLabel: String) {
    val delta = currentCents - previousCents
    val percent = simplePercentChange(currentCents, previousCents)
    val increased = delta > 0
    val color = if (increased) FinanceTheme.colors.debt else FinanceTheme.colors.income
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = color.copy(alpha = 0.10f),
        contentColor = color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (increased) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (percent == null) {
                    stringResource(
                        R.string.analysis_mobile_amount_vs_period,
                        signedEuroDelta(delta),
                        previousLabel,
                    )
                } else {
                    stringResource(
                        R.string.analysis_mobile_percent_amount_vs_period,
                        signedPercent(percent),
                        signedEuroDelta(delta),
                        previousLabel,
                    )
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun CategoryOverviewCard(
    categories: List<AnalysisCategoryTotal>,
    totalExpenseCents: Long,
    previousCategories: List<AnalysisCategoryTotal>,
    showComparison: Boolean,
) {
    val previousByKey = previousCategories.associateBy { it.rowKey() }
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            categories.forEachIndexed { index, category ->
                if (index > 0) HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                CategoryOverviewRow(
                    category = category,
                    totalExpenseCents = totalExpenseCents,
                    previousCents = previousByKey[category.rowKey()]?.expenseCents,
                    showComparison = showComparison,
                )
            }
        }
    }
}

@Composable
private fun CategoryOverviewRow(
    category: AnalysisCategoryTotal,
    totalExpenseCents: Long,
    previousCents: Long?,
    showComparison: Boolean,
) {
    val color = if (category.rowKind == AnalysisBreakdownKind.TRIP) {
        FinanceTheme.colors.transfer
    } else {
        categoryColor(category.categoryColor)
    }
    val name = category.tripName ?: category.categoryName ?: stringResource(R.string.common_no_category)
    val share = if (totalExpenseCents > 0) category.expenseCents * 100 / totalExpenseCents else 0L
    Column(
        modifier = Modifier.padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconChip(
                icon = if (category.rowKind == AnalysisBreakdownKind.TRIP) Icons.Outlined.Flight else categoryIcon(category.categoryIcon),
                contentDescription = null,
                color = color,
                size = 36.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(R.string.analysis_mobile_share, share),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(cents = category.expenseCents, style = MaterialTheme.typography.titleSmall)
                if (showComparison) {
                    val previous = previousCents ?: 0L
                    val delta = category.expenseCents - previous
                    val percent = simplePercentChange(category.expenseCents, previous)
                    Text(
                        text = if (percent == null) {
                            stringResource(R.string.analysis_mobile_amount_vs_short, signedEuroDelta(delta))
                        } else {
                            stringResource(
                                R.string.analysis_mobile_percent_amount_vs_short,
                                signedPercent(percent),
                                signedEuroDelta(delta),
                            )
                        },
                        color = comparisonColor(delta),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = {
                if (totalExpenseCents <= 0L) 0f
                else (category.expenseCents.toFloat() / totalExpenseCents).coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

private fun annualBuckets(buckets: List<AnalysisIncomeExpenseBucket>): List<AnalysisIncomeExpenseBucket> =
    buckets.groupBy { it.bucket.take(4) }
        .map { (year, rows) ->
            AnalysisIncomeExpenseBucket(
                bucket = year,
                incomeCents = rows.sumOf { it.incomeCents },
                expenseCents = rows.sumOf { it.expenseCents },
                netCents = rows.sumOf { it.netCents },
                savingsRateBasisPoints = 0L,
            )
        }
        .sortedByDescending { it.bucket }
        .take(MAX_YEARS)

@Composable
private fun YearlyEvolutionCard(years: List<AnalysisIncomeExpenseBucket>) {
    val max = years.maxOf { it.expenseCents }.coerceAtLeast(1L)
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            years.forEach { year ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = year.bucket, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(40.dp))
                    LinearProgressIndicator(
                        progress = { (year.expenseCents.toFloat() / max).coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(8.dp),
                        color = FinanceTheme.colors.expense,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        drawStopIndicator = {},
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        MoneyText(cents = year.expenseCents, style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = stringResource(
                                R.string.analysis_mobile_year_net,
                                signedEuroDelta(year.netCents),
                            ),
                            color = if (year.netCents < 0) FinanceTheme.colors.debt else FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

private fun simplePercentChange(current: Long, previous: Long): Long? =
    if (previous == 0L) null else (current - previous) * 100L / abs(previous)

private fun signedPercent(value: Long): String = if (value > 0) "+$value%" else "$value%"

private fun signedEuroDelta(value: Long): String = when {
    value > 0 -> "+${formatEuroCents(value)}"
    else -> formatEuroCents(value)
}

@Composable
private fun comparisonColor(delta: Long): Color = when {
    delta > 0 -> FinanceTheme.colors.debt
    delta < 0 -> FinanceTheme.colors.income
    else -> FinanceTheme.colors.mutedText
}

private fun AnalysisScope.mobileLabelRes(): Int = when (this) {
    AnalysisScope.MONTH -> R.string.analysis_scope_month
    AnalysisScope.YEAR -> R.string.analysis_scope_year
    AnalysisScope.ALL_TIME -> R.string.analysis_mobile_history
    AnalysisScope.CUSTOM -> labelRes()
}

private fun com.gestorfinances.app.ui.common.IncomeExpenseChartPoint.evolutionLabel(
    scope: AnalysisScope,
    monthLabels: Array<String>,
): String = when (scope) {
    AnalysisScope.YEAR -> runCatching { YearMonth.parse(bucket) }
        .getOrNull()
        ?.monthValue
        ?.let { monthLabels.getOrNull(it - 1) }
        ?: label
    else -> label
}
