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
import com.gestorfinances.app.ui.common.IncomeExpenseChartPoint
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
        onRetry = viewModel::onScreenShown,
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
    onRetry: () -> Unit,
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
        onRetry = onRetry,
        contentPadding = contentPadding,
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

private val contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp)

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
    }

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
