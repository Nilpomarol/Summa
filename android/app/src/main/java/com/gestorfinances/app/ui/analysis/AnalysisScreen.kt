package com.gestorfinances.app.ui.analysis

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisBreakdownKind
import com.gestorfinances.app.data.repository.AnalysisBucket
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisIncomeExpenseBucket
import com.gestorfinances.app.ui.analysis.components.AnalysisFilterSheet
import com.gestorfinances.app.ui.analysis.components.AnalysisOverview
import com.gestorfinances.app.ui.common.HeatmapCell
import com.gestorfinances.app.ui.common.IncomeExpenseChartPoint
import com.gestorfinances.app.ui.common.formatCompactDateRange
import com.gestorfinances.app.ui.common.formatExpandedDate
import com.gestorfinances.app.ui.common.formatMonth
import com.gestorfinances.app.ui.common.formatMonthYear
import java.time.YearMonth

@Composable
internal fun AnalysisScreen(
    viewModel: AnalysisViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    AnalysisContent(
        state = state,
        onScopeSelected = viewModel::onScopeSelected,
        onPreviousPeriod = viewModel::onPreviousPeriodClicked,
        onNextPeriod = viewModel::onNextPeriodClicked,
        onMonthSelected = viewModel::onMonthSelected,
        onYearSelected = viewModel::onYearSelected,
        onNatureFilterSelected = viewModel::onNatureFilterSelected,
        onOneTimeModeSelected = viewModel::onOneTimeModeSelected,
        onGroupTripsAsBlocksChange = viewModel::onGroupTripsAsBlocksChanged,
        onAccountSelected = viewModel::setAccountFilter,
        onClearAccountFilter = viewModel::clearAccountFilter,
        onCategorySelected = viewModel::setCategoryFilter,
        onClearCategoryFilter = viewModel::clearCategoryFilter,
        onResetFilters = viewModel::resetFilters,
        modifier = modifier,
    )
}

@Composable
internal fun AnalysisContent(
    state: AnalysisUiState,
    onScopeSelected: (AnalysisScope) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onYearSelected: (Int) -> Unit,
    onNatureFilterSelected: (AnalysisNatureFilter) -> Unit,
    onOneTimeModeSelected: (com.gestorfinances.app.data.repository.AnalysisOneTimeMode) -> Unit,
    onGroupTripsAsBlocksChange: (Boolean) -> Unit,
    onAccountSelected: (String, String) -> Unit,
    onClearAccountFilter: () -> Unit,
    onCategorySelected: (String, String) -> Unit,
    onClearCategoryFilter: () -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFilters by remember { mutableStateOf(false) }
    AnalysisOverview(
        state = state,
        onScopeSelected = onScopeSelected,
        onPreviousPeriod = onPreviousPeriod,
        onNextPeriod = onNextPeriod,
        onMonthSelected = onMonthSelected,
        onYearSelected = onYearSelected,
        onOpenFilters = { showFilters = true },
        contentPadding = tabPadding,
        modifier = modifier,
    )

    if (showFilters) {
        AnalysisFilterSheet(
            state = state,
            onNatureFilterSelected = onNatureFilterSelected,
            onOneTimeModeSelected = onOneTimeModeSelected,
            onGroupTripsAsBlocksChange = onGroupTripsAsBlocksChange,
            onAccountSelected = onAccountSelected,
            onClearAccountFilter = onClearAccountFilter,
            onCategorySelected = onCategorySelected,
            onClearCategoryFilter = onClearCategoryFilter,
            onResetFilters = onResetFilters,
            onDismiss = { showFilters = false },
        )
    }
}

private val tabPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp)

// ---------------------------------------------------------------------------
// Shared helpers used across the tabs
// ---------------------------------------------------------------------------

@StringRes
internal fun AnalysisTab.labelRes(): Int =
    when (this) {
        AnalysisTab.RESUM -> R.string.analysis_tab_resum
        AnalysisTab.CATEGORIES -> R.string.analysis_tab_categories
        AnalysisTab.COMPARATIVA -> R.string.analysis_tab_comparativa
        AnalysisTab.HISTORIC -> R.string.analysis_tab_historic
        AnalysisTab.FIX_VARIABLE -> R.string.analysis_tab_fix_variable
    }

@StringRes
internal fun AnalysisScope.labelRes(): Int =
    when (this) {
        AnalysisScope.MONTH -> R.string.analysis_scope_month
        AnalysisScope.YEAR -> R.string.analysis_scope_year
        AnalysisScope.ALL_TIME -> R.string.analysis_scope_all_time
        AnalysisScope.CUSTOM -> R.string.analysis_scope_custom
    }

@StringRes
internal fun AnalysisValueMode.labelRes(): Int =
    when (this) {
        AnalysisValueMode.TOTALS -> R.string.analysis_mode_total
        AnalysisValueMode.AVERAGES -> R.string.analysis_mode_average
    }

@StringRes
internal fun AnalysisNatureFilter.labelRes(): Int =
    when (this) {
        AnalysisNatureFilter.ALL -> R.string.analysis_filter_all_natures
        AnalysisNatureFilter.FIXED -> R.string.analysis_filter_fixed
        AnalysisNatureFilter.VARIABLE -> R.string.analysis_filter_variable
    }

@StringRes
internal fun com.gestorfinances.app.data.repository.AnalysisOneTimeMode.labelRes(): Int =
    when (this) {
        com.gestorfinances.app.data.repository.AnalysisOneTimeMode.INCLUDE -> R.string.analysis_one_time_include
        com.gestorfinances.app.data.repository.AnalysisOneTimeMode.EXCLUDE -> R.string.analysis_one_time_exclude
        com.gestorfinances.app.data.repository.AnalysisOneTimeMode.ONLY -> R.string.analysis_one_time_only
    }

@Composable
internal fun AnalysisPeriodRange.formatForScope(scope: AnalysisScope): String =
    when (scope) {
        AnalysisScope.MONTH -> formatMonthYear(YearMonth.from(fromDate))
        AnalysisScope.YEAR -> fromDate.year.toString()
        AnalysisScope.ALL_TIME -> stringResource(R.string.analysis_scope_all_time)
        AnalysisScope.CUSTOM ->
            "${formatExpandedDate(fromDate.toString())} - ${formatExpandedDate(toDateExclusive.minusDays(1).toString())}"
    }

@Composable
internal fun AnalysisUiState.fallbackPeriodLabel(): String =
    when (scope) {
        AnalysisScope.MONTH -> formatMonthYear(month)
        AnalysisScope.YEAR -> year.toString()
        AnalysisScope.ALL_TIME -> stringResource(R.string.analysis_scope_all_time)
        AnalysisScope.CUSTOM -> "${formatExpandedDate(customFrom)} - ${formatExpandedDate(customTo)}"
    }

/**
 * Current vs comparison period labels (Comparativa tab: KPI captions, chart legend). For month
 * scope, when both months fall in the same year the year is dropped from both labels ("Juliol" /
 * "Juny" instead of "Juliol 2026" / "Juny 2026") since repeating it twice is redundant. For custom
 * scope, uses the compact `d MMM` date form (instead of the expanded date pair, which
 * overflows KPI cards) and likewise drops the year from both labels when all four endpoints share
 * one common year.
 */
@Composable
internal fun periodComparisonLabels(
    scope: AnalysisScope,
    currentRange: AnalysisPeriodRange?,
    comparisonRange: AnalysisPeriodRange,
    fallbackCurrentLabel: String,
): Pair<String, String> {
    if (scope == AnalysisScope.MONTH && currentRange != null) {
        val currentMonth = YearMonth.from(currentRange.fromDate)
        val comparisonMonth = YearMonth.from(comparisonRange.fromDate)
        if (currentMonth.year == comparisonMonth.year) {
            return formatMonth(currentMonth) to formatMonth(comparisonMonth)
        }
    }
    if (scope == AnalysisScope.CUSTOM && currentRange != null) {
        val currentTo = currentRange.toDateExclusive.minusDays(1)
        val comparisonTo = comparisonRange.toDateExclusive.minusDays(1)
        val commonYear = currentRange.fromDate.year == currentTo.year &&
            currentTo.year == comparisonRange.fromDate.year &&
            comparisonRange.fromDate.year == comparisonTo.year
        return formatCompactDateRange(currentRange.fromDate, currentTo, includeYear = !commonYear) to
            formatCompactDateRange(comparisonRange.fromDate, comparisonTo, includeYear = !commonYear)
    }
    val currentLabel = currentRange?.formatForScope(scope) ?: fallbackCurrentLabel
    return currentLabel to comparisonRange.formatForScope(scope)
}

internal fun AnalysisUiState.currentDisplayDivisor(): Long =
    if (valueMode == AnalysisValueMode.AVERAGES) currentAverageDivisor else 1L

internal fun AnalysisUiState.displayCents(cents: Long): Long = cents.divideCents(currentDisplayDivisor())

internal fun Long.divideCents(divisor: Long): Long = if (divisor <= 1) this else this / divisor

internal fun AnalysisCategoryTotal.rowKey(): String =
    when (rowKind) {
        AnalysisBreakdownKind.TRIP -> "trip:${tripId ?: tripName.orEmpty()}"
        AnalysisBreakdownKind.CATEGORY -> "category:${categoryId ?: "uncategorized"}"
    }

/**
 * Converts income/expense buckets into per-bucket chart points, filling gaps so the cumulative
 * line stays continuous (a zero-spend day carries the running total flat).
 */
internal fun incomeExpensePoints(
    range: AnalysisPeriodRange,
    scope: AnalysisScope,
    buckets: List<AnalysisIncomeExpenseBucket>,
): List<IncomeExpenseChartPoint> =
    when (range.bucket) {
        AnalysisBucket.DAY -> range.dailyPoints(buckets)
        AnalysisBucket.MONTH -> if (scope == AnalysisScope.ALL_TIME) {
            buckets.map { it.toChartPoint() }
        } else {
            range.monthlyPoints(buckets)
        }
        AnalysisBucket.YEAR -> buckets.map { it.toChartPoint() }
    }

private fun AnalysisPeriodRange.dailyPoints(buckets: List<AnalysisIncomeExpenseBucket>): List<IncomeExpenseChartPoint> {
    val byDay = buckets.associateBy { it.bucket }
    val points = mutableListOf<IncomeExpenseChartPoint>()
    var cursor = fromDate
    while (cursor < toDateExclusive) {
        val key = cursor.toString()
        val bucket = byDay[key]
        points += IncomeExpenseChartPoint(
            label = cursor.dayOfMonth.toString(),
            bucket = key,
            incomeCents = bucket?.incomeCents ?: 0L,
            expenseCents = bucket?.expenseCents ?: 0L,
        )
        cursor = cursor.plusDays(1)
    }
    return points
}

private fun AnalysisPeriodRange.monthlyPoints(buckets: List<AnalysisIncomeExpenseBucket>): List<IncomeExpenseChartPoint> {
    val byMonth = buckets.associateBy { it.bucket }
    val points = mutableListOf<IncomeExpenseChartPoint>()
    var cursor = YearMonth.from(fromDate)
    val lastMonth = YearMonth.from(toDateExclusive.minusDays(1))
    while (!cursor.isAfter(lastMonth)) {
        val key = cursor.toString()
        val bucket = byMonth[key]
        points += IncomeExpenseChartPoint(
            label = key,
            bucket = key,
            incomeCents = bucket?.incomeCents ?: 0L,
            expenseCents = bucket?.expenseCents ?: 0L,
        )
        cursor = cursor.plusMonths(1)
    }
    return points
}

private fun AnalysisIncomeExpenseBucket.toChartPoint(): IncomeExpenseChartPoint =
    IncomeExpenseChartPoint(
        label = bucket,
        bucket = bucket,
        incomeCents = incomeCents,
        expenseCents = expenseCents,
    )

/** Builds the day-by-day heatmap cells for a bounded range, filling zero-spend days. */
internal fun heatmapCellsFor(
    range: AnalysisPeriodRange,
    days: List<AnalysisIncomeExpenseBucket>,
): List<HeatmapCell> {
    val byDay = days.associate { it.bucket to it.expenseCents }
    val cells = mutableListOf<HeatmapCell>()
    var cursor = range.fromDate
    while (cursor < range.toDateExclusive) {
        cells += HeatmapCell(date = cursor, expenseCents = byDay[cursor.toString()] ?: 0L)
        cursor = cursor.plusDays(1)
    }
    return cells
}
