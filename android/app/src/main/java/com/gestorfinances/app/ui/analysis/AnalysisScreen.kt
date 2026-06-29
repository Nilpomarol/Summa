package com.gestorfinances.app.ui.analysis

import com.gestorfinances.app.ui.analysis.components.*

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

@Composable
internal fun AnalysisScreen(
    viewModel: AnalysisViewModel,
    onDrillDown: (MovementFilters) -> Unit,
    onTripDetail: (String) -> Unit,
    onManageBudgets: () -> Unit,
    onClearCategoryFilter: () -> Unit = viewModel::clearCategoryFilter,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    AnalysisContent(
        state = state,
        onManageBudgets = onManageBudgets,
        onScopeSelected = viewModel::onScopeSelected,
        onAnalysisModeSelected = viewModel::onAnalysisModeSelected,
        onValueModeSelected = viewModel::onValueModeSelected,
        onNatureFilterSelected = viewModel::onNatureFilterSelected,
        onOneTimeModeSelected = viewModel::onOneTimeModeSelected,
        onGroupTripsAsBlocksChange = viewModel::onGroupTripsAsBlocksChanged,
        onPreviousPeriod = viewModel::onPreviousPeriodClicked,
        onNextPeriod = viewModel::onNextPeriodClicked,
        onComparePreviousChange = viewModel::onComparePreviousChanged,
        onCustomFromChange = viewModel::onCustomFromChanged,
        onCustomToChange = viewModel::onCustomToChanged,
        onResetPeriod = viewModel::onResetPeriodClicked,
        onClearAccountFilter = viewModel::clearAccountFilter,
        onClearCategoryFilter = onClearCategoryFilter,
        onDrillDown = onDrillDown,
        onTripDetail = onTripDetail,
        modifier = modifier,
    )
}

@Composable
internal fun AnalysisContent(
    state: AnalysisUiState,
    onManageBudgets: () -> Unit,
    onScopeSelected: (AnalysisScope) -> Unit,
    onAnalysisModeSelected: (AnalysisMode) -> Unit,
    onValueModeSelected: (AnalysisValueMode) -> Unit,
    onNatureFilterSelected: (AnalysisNatureFilter) -> Unit,
    onOneTimeModeSelected: (AnalysisOneTimeMode) -> Unit,
    onGroupTripsAsBlocksChange: (Boolean) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onComparePreviousChange: (Boolean) -> Unit,
    onCustomFromChange: (String) -> Unit,
    onCustomToChange: (String) -> Unit,
    onResetPeriod: () -> Unit,
    onClearAccountFilter: () -> Unit,
    onClearCategoryFilter: () -> Unit,
    onDrillDown: (MovementFilters) -> Unit,
    onTripDetail: (String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.analysis_title),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.hasActivePeriodControl) {
                            TextButton(onClick = onResetPeriod) {
                                Text(text = stringResource(R.string.common_clear_filters))
                            }
                        }
                        TextButton(onClick = onManageBudgets) {
                            Text(text = stringResource(R.string.budget_list_title))
                        }
                    }
                },
            )
        }

        state.filterAccountName?.let { accountName ->
            item {
                FinanceFilterChip(
                    selected = true,
                    label = stringResource(R.string.analysis_account_filter, accountName),
                    onClick = onClearAccountFilter,
                )
            }
        }

        state.filterCategoryName?.let { categoryName ->
            item {
                FinanceFilterChip(
                    selected = true,
                    label = stringResource(R.string.analysis_category_filter, categoryName),
                    onClick = onClearCategoryFilter,
                )
            }
        }

        item {
            SegmentedControl(
                options = AnalysisScope.entries,
                selected = state.scope,
                label = { stringResource(it.labelRes()) },
                onSelect = onScopeSelected,
            )
        }

        item {
            AnalysisModeControls(
                state = state,
                onAnalysisModeSelected = onAnalysisModeSelected,
                onValueModeSelected = onValueModeSelected,
            )
        }

        item {
            PeriodSelector(
                state = state,
                onPreviousPeriod = onPreviousPeriod,
                onNextPeriod = onNextPeriod,
            )
        }

        if (state.scope == AnalysisScope.CUSTOM) {
            item {
                CustomDateFields(
                    state = state,
                    onCustomFromChange = onCustomFromChange,
                    onCustomToChange = onCustomToChange,
                )
            }
        }

        if (state.canCompare) {
            item {
                ComparePreviousRow(
                    checked = state.comparePrevious,
                    onCheckedChange = onComparePreviousChange,
                )
            }
        }

        if (state.analysisMode == AnalysisMode.ACTUAL) {
            item {
                ActualFilterControls(
                    state = state,
                    onNatureFilterSelected = onNatureFilterSelected,
                    onOneTimeModeSelected = onOneTimeModeSelected,
                    onGroupTripsAsBlocksChange = onGroupTripsAsBlocksChange,
                )
            }
        }

        state.errorMessage?.let { message ->
            item {
                InlineBanner(kind = BannerKind.Error, text = message)
            }
        }

        state.customErrorRes?.let { errorRes ->
            item {
                InlineBanner(
                    kind = BannerKind.Error,
                    text = stringResource(errorRes),
                )
            }
        }

        if (state.isLoading) {
            item {
                Text(
                    text = stringResource(R.string.movement_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (!state.hasActivity && !state.recurringCostSummary.hasCosts) {
            item {
                EmptyAnalysisCard()
            }
        }

        item {
            SummaryGrid(
                state = state,
                onDrillDown = onDrillDown,
            )
        }

        if (state.previousTotals != null && state.previousRange != null) {
            item {
                Text(
                    text = "${stringResource(R.string.analysis_period_previous)}: ${state.previousRange.formatForScope(state.scope)}",
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            IncomeExpenseChart(
                title = stringResource(
                    if (state.analysisMode == AnalysisMode.ACTUAL) {
                        R.string.analysis_income_vs_expense_title
                    } else {
                        R.string.analysis_account_flow_title
                    },
                ),
                points = state.toChartPoints(),
                incomeLabel = stringResource(
                    if (state.analysisMode == AnalysisMode.ACTUAL) {
                        R.string.analysis_summary_income
                    } else {
                        R.string.analysis_flow_positive
                    },
                ),
                expenseLabel = stringResource(
                    if (state.analysisMode == AnalysisMode.ACTUAL) {
                        R.string.analysis_summary_expense
                    } else {
                        R.string.analysis_flow_negative
                    },
                ),
                emptyText = stringResource(R.string.dashboard_no_data),
                onPointClick = { point ->
                    state.chartPointFilters(point)?.let(onDrillDown)
                },
            )
        }

        item {
            SectionHeader(title = stringResource(R.string.analysis_breakdown_title))
        }

        if (state.analysisMode == AnalysisMode.ACTUAL && state.categories.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_no_data),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (state.analysisMode == AnalysisMode.ACTUAL) {
            val maxCategoryCents = state.categories.maxOf { abs(it.netCents) }.coerceAtLeast(1L)
            items(items = state.categories, key = { it.rowKey() }) { category ->
                CategoryBreakdownRow(
                    category = category,
                    maxCents = maxCategoryCents,
                    divisor = state.currentDisplayDivisor(),
                    onClick = {
                        if (category.rowKind == AnalysisBreakdownKind.TRIP && category.tripId != null) {
                            onTripDetail(category.tripId)
                        } else {
                            state.periodFilters(
                                categoryId = category.categoryId,
                                uncategorizedOnly = category.categoryId == null,
                            )?.let(onDrillDown)
                        }
                    },
                )
            }
        } else if (state.flowBuckets.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_no_data),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(
                items = state.flowBuckets,
                key = { "${it.bucket}-${it.accountId}" },
            ) { flow ->
                AccountFlowBreakdownRow(
                    flow = flow,
                    divisor = state.currentDisplayDivisor(),
                    onClick = {
                        state.flowBucketFilters(flow)?.let(onDrillDown)
                    },
                )
            }
        }

        if (
            state.analysisMode == AnalysisMode.ACTUAL &&
            (state.hasActivity || state.recurringCostSummary.hasCosts)
        ) {
            analysisWidgets(state = state, onDrillDown = onDrillDown)
        }
    }
}



















































internal data class HeatmapCell(
    val date: LocalDate,
    val expenseCents: Long,
)

internal fun AnalysisUiState.heatmapCells(): List<HeatmapCell> {
    val range = currentRange ?: return emptyList()
    val byDay = heatmapDays.associate { it.bucket to it.expenseCents }
    val cells = mutableListOf<HeatmapCell>()
    var cursor = range.fromDate
    while (cursor < range.toDateExclusive) {
        cells += HeatmapCell(date = cursor, expenseCents = byDay[cursor.toString()] ?: 0L)
        cursor = cursor.plusDays(1)
    }
    return cells
}

internal fun AnalysisUiState.largestExpenseFilters(expense: AnalysisLargestExpense): MovementFilters? =
    periodFilters(type = MovementType.EXPENSE)?.copy(
        dateFrom = expense.date,
        dateTo = expense.date,
    )

internal fun AnalysisUiState.savingsBucketFilters(bucket: String): MovementFilters? {
    val range = currentRange ?: return null
    val (fromDate, toDate) = range.bucket.toMovementDateRange(bucket) ?: return null
    return periodFilters()?.copy(dateFrom = fromDate, dateTo = toDate)
}

const val MAX_TREND_SERIES = 4
const val MAX_RECURRING_COST_ITEMS = 3

@StringRes
internal fun AnalysisScope.labelRes(): Int =
    when (this) {
        AnalysisScope.MONTH -> R.string.analysis_scope_month
        AnalysisScope.YEAR -> R.string.analysis_scope_year
        AnalysisScope.ALL_TIME -> R.string.analysis_scope_all_time
        AnalysisScope.CUSTOM -> R.string.analysis_scope_custom
    }

@StringRes
internal fun AnalysisMode.labelRes(): Int =
    when (this) {
        AnalysisMode.ACTUAL -> R.string.analysis_mode_actual
        AnalysisMode.FLOW -> R.string.analysis_mode_flow
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
internal fun AnalysisOneTimeMode.labelRes(): Int =
    when (this) {
        AnalysisOneTimeMode.INCLUDE -> R.string.analysis_one_time_include
        AnalysisOneTimeMode.EXCLUDE -> R.string.analysis_one_time_exclude
        AnalysisOneTimeMode.ONLY -> R.string.analysis_one_time_only
    }

@Composable
internal fun AnalysisUiState.fallbackPeriodLabel(): String =
    when (scope) {
        AnalysisScope.MONTH -> formatMonthYear(month)
        AnalysisScope.YEAR -> year.toString()
        AnalysisScope.ALL_TIME -> stringResource(R.string.analysis_scope_all_time)
        AnalysisScope.CUSTOM -> "${formatLongDate(customFrom)} - ${formatLongDate(customTo)}"
    }

@Composable
internal fun AnalysisPeriodRange.formatForScope(scope: AnalysisScope): String =
    when (scope) {
        AnalysisScope.MONTH -> formatMonthYear(YearMonth.from(fromDate))
        AnalysisScope.YEAR -> fromDate.year.toString()
        AnalysisScope.ALL_TIME -> stringResource(R.string.analysis_scope_all_time)
        AnalysisScope.CUSTOM -> "${formatLongDate(fromDate.toString())} - ${formatLongDate(toDateExclusive.minusDays(1).toString())}"
    }

internal fun AnalysisUiState.toChartPoints(): List<IncomeExpenseChartPoint> {
    val range = currentRange ?: return emptyList()
    if (analysisMode == AnalysisMode.FLOW) {
        return flowBuckets
            .distinctBy { it.bucket }
            .map { it.toFlowChartPoint() }
    }
    return when (range.bucket) {
        AnalysisBucket.DAY -> range.toDailyPoints(chartBuckets)
        AnalysisBucket.MONTH -> if (scope == AnalysisScope.ALL_TIME) {
            chartBuckets.map { it.toChartPoint() }
        } else {
            range.toMonthlyPoints(chartBuckets)
        }
        AnalysisBucket.YEAR -> chartBuckets.map { it.toChartPoint() }
    }
}

internal fun AnalysisPeriodRange.toDailyPoints(
    buckets: List<AnalysisIncomeExpenseBucket>,
): List<IncomeExpenseChartPoint> {
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

internal fun AnalysisPeriodRange.toMonthlyPoints(
    buckets: List<AnalysisIncomeExpenseBucket>,
): List<IncomeExpenseChartPoint> {
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

internal fun AnalysisIncomeExpenseBucket.toChartPoint(): IncomeExpenseChartPoint =
    IncomeExpenseChartPoint(
        label = bucket,
        bucket = bucket,
        incomeCents = incomeCents,
        expenseCents = expenseCents,
    )

internal fun AnalysisAccountFlowBucket.toFlowChartPoint(): IncomeExpenseChartPoint {
    val delta = bucketDeltaCents
    return IncomeExpenseChartPoint(
        label = bucket,
        bucket = bucket,
        incomeCents = delta.coerceAtLeast(0),
        expenseCents = (-delta).coerceAtLeast(0),
    )
}

internal fun AnalysisUiState.periodFilters(
    type: MovementType? = null,
    categoryId: String? = null,
    uncategorizedOnly: Boolean = false,
): MovementFilters? {
    val range = currentRange ?: return null
    val sourceMode = when (analysisMode) {
        AnalysisMode.ACTUAL -> MovementSourceMode.ACTUAL
        AnalysisMode.FLOW -> MovementSourceMode.FLOW
    }
    return MovementFilters(
        type = if (sourceMode == MovementSourceMode.ACTUAL) type else null,
        categoryId = if (sourceMode == MovementSourceMode.ACTUAL) categoryId else null,
        uncategorizedOnly = sourceMode == MovementSourceMode.ACTUAL && uncategorizedOnly,
        sourceMode = sourceMode,
        categoryNature = if (sourceMode == MovementSourceMode.ACTUAL) {
            natureFilter.toMovementCategoryNature()
        } else {
            null
        },
        oneTimeMode = if (sourceMode == MovementSourceMode.ACTUAL) {
            oneTimeMode.toMovementOneTimeMode()
        } else {
            MovementFilterOneTimeMode.INCLUDE
        },
        dateFrom = range.fromDate.toString(),
        dateTo = range.toDateExclusive.minusDays(1).toString(),
    )
}

internal fun AnalysisUiState.flowBucketFilters(flow: AnalysisAccountFlowBucket): MovementFilters? {
    val range = currentRange ?: return null
    val (fromDate, toDate) = range.bucket.toMovementDateRange(flow.bucket) ?: return null
    return MovementFilters(
        accountId = flow.accountId,
        sourceMode = MovementSourceMode.FLOW,
        dateFrom = fromDate,
        dateTo = toDate,
    )
}

internal fun AnalysisUiState.chartPointFilters(point: IncomeExpenseChartPoint): MovementFilters? {
    val range = currentRange ?: return null
    val (fromDate, toDate) = range.bucket.toMovementDateRange(point.bucket) ?: return null
    return periodFilters()?.copy(
        dateFrom = fromDate,
        dateTo = toDate,
    )
}

internal fun AnalysisCategoryTotal.rowKey(): String =
    when (rowKind) {
        AnalysisBreakdownKind.TRIP -> "trip:${tripId ?: tripName.orEmpty()}"
        AnalysisBreakdownKind.CATEGORY -> "category:${categoryId ?: "uncategorized"}"
    }

internal fun AnalysisBucket.toMovementDateRange(bucket: String): Pair<String, String>? =
    runCatching {
        when (this) {
            AnalysisBucket.DAY -> {
                val date = LocalDate.parse(bucket)
                date.toString() to date.toString()
            }
            AnalysisBucket.MONTH -> {
                val month = YearMonth.parse(bucket)
                month.atDay(1).toString() to month.atEndOfMonth().toString()
            }
            AnalysisBucket.YEAR -> {
                val year = bucket.toInt()
                LocalDate.of(year, 1, 1).toString() to LocalDate.of(year, 12, 31).toString()
            }
        }
    }.getOrNull()

internal fun AnalysisNatureFilter.toMovementCategoryNature(): CategoryNature? =
    when (this) {
        AnalysisNatureFilter.ALL -> null
        AnalysisNatureFilter.FIXED -> CategoryNature.FIXED
        AnalysisNatureFilter.VARIABLE -> CategoryNature.VARIABLE
    }

internal fun AnalysisOneTimeMode.toMovementOneTimeMode(): MovementFilterOneTimeMode =
    when (this) {
        AnalysisOneTimeMode.INCLUDE -> MovementFilterOneTimeMode.INCLUDE
        AnalysisOneTimeMode.EXCLUDE -> MovementFilterOneTimeMode.EXCLUDE
        AnalysisOneTimeMode.ONLY -> MovementFilterOneTimeMode.ONLY
    }

internal fun AnalysisUiState.displayCents(cents: Long): Long =
    cents.divideCents(currentDisplayDivisor())

internal fun AnalysisUiState.displayPreviousCents(cents: Long): Long =
    cents.divideCents(previousDisplayDivisor())

internal fun AnalysisUiState.currentDisplayDivisor(): Long =
    if (valueMode == AnalysisValueMode.AVERAGES) currentAverageDivisor else 1L

internal fun AnalysisUiState.previousDisplayDivisor(): Long =
    if (valueMode == AnalysisValueMode.AVERAGES) previousAverageDivisor else 1L

internal fun Long.divideCents(divisor: Long): Long =
    if (divisor <= 1) this else this / divisor
