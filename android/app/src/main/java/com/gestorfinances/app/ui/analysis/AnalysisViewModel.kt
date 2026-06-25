package com.gestorfinances.app.ui.analysis

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisAccountFlowBucket
import com.gestorfinances.app.data.repository.AnalysisBucket
import com.gestorfinances.app.data.repository.AnalysisCategoryNature
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisCategoryTrendPoint
import com.gestorfinances.app.data.repository.AnalysisIncomeExpenseBucket
import com.gestorfinances.app.data.repository.AnalysisLargestExpense
import com.gestorfinances.app.data.repository.AnalysisMerchantTotal
import com.gestorfinances.app.data.repository.AnalysisNetWorthPoint
import com.gestorfinances.app.data.repository.AnalysisOneTimeMode
import com.gestorfinances.app.data.repository.AnalysisPeriodTotals
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnalysisViewModel(
    private val analysisRepository: AnalysisRepository,
    private val templateRepository: TemplateRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val initialToday = todayProvider()
    private val initialMonth = YearMonth.from(initialToday)
    private val _state = MutableStateFlow(
        AnalysisUiState(
            month = initialMonth,
            year = initialToday.year,
            customFrom = initialMonth.atDay(1).toString(),
            customTo = initialMonth.atEndOfMonth().toString(),
        ),
    )
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refresh()
    }

    fun refresh() {
        val snapshot = _state.value
        val validation = resolveAnalysisRange(
            scope = snapshot.scope,
            month = snapshot.month,
            year = snapshot.year,
            customFrom = snapshot.customFrom,
            customTo = snapshot.customTo,
        )
        val range = validation.range
        if (range == null) {
            _state.value = snapshot.copy(
                isLoading = false,
                customErrorRes = validation.errorRes,
                errorMessage = null,
            )
            return
        }
        val previousRange = if (snapshot.comparePrevious) {
            previousAnalysisRange(range = range, scope = snapshot.scope)
        } else {
            null
        }
        val categoryNature = snapshot.natureFilter.toQueryNature()

        viewModelScope.launch {
            _state.value = snapshot.copy(
                isLoading = true,
                currentRange = range,
                previousRange = previousRange,
                customErrorRes = null,
                errorMessage = null,
            )
            val result = withContext(ioDispatcher) {
                runCatching {
                    val totals = analysisRepository.periodTotals(
                        fromDate = range.fromDate.toString(),
                        toDate = range.toDateExclusive.toString(),
                        oneTimeMode = snapshot.oneTimeMode,
                        categoryNature = categoryNature,
                    )
                    val previousTotals = previousRange?.let {
                        analysisRepository.periodTotals(
                            fromDate = it.fromDate.toString(),
                            toDate = it.toDateExclusive.toString(),
                            oneTimeMode = snapshot.oneTimeMode,
                            categoryNature = categoryNature,
                        )
                    }
                    val isActual = snapshot.analysisMode == AnalysisMode.ACTUAL
                    val chartBucketsData = if (isActual) {
                        analysisRepository.incomeVsExpense(
                            fromDate = range.fromDate.toString(),
                            toDate = range.toDateExclusive.toString(),
                            oneTimeMode = snapshot.oneTimeMode,
                            categoryNature = categoryNature,
                            bucket = range.bucket,
                        )
                    } else {
                        emptyList()
                    }
                    // The heatmap needs per-day expense: reuse chartBuckets when the range is
                    // already daily, and otherwise query daily only when the span is bounded.
                    val heatmapData = when {
                        !isActual || !range.isHeatmapBounded() -> emptyList()
                        range.bucket == AnalysisBucket.DAY -> chartBucketsData
                        else -> analysisRepository.incomeVsExpense(
                            fromDate = range.fromDate.toString(),
                            toDate = range.toDateExclusive.toString(),
                            oneTimeMode = snapshot.oneTimeMode,
                            categoryNature = categoryNature,
                            bucket = AnalysisBucket.DAY,
                        )
                    }
                    AnalysisLoadedData(
                        totals = totals,
                        previousTotals = previousTotals,
                        categories = if (isActual) {
                            analysisRepository.actualBreakdown(
                                fromDate = range.fromDate.toString(),
                                toDate = range.toDateExclusive.toString(),
                                oneTimeMode = snapshot.oneTimeMode,
                                categoryNature = categoryNature,
                                groupTrips = snapshot.groupTripsAsBlocks,
                            )
                        } else {
                            emptyList()
                        },
                        chartBuckets = chartBucketsData,
                        flowBuckets = if (snapshot.analysisMode == AnalysisMode.FLOW) {
                            analysisRepository.accountFlowOverTime(
                                fromDate = range.fromDate.toString(),
                                toDate = range.toDateExclusive.toString(),
                                bucket = range.bucket,
                            )
                        } else {
                            emptyList()
                        },
                        largestExpenses = if (snapshot.analysisMode == AnalysisMode.ACTUAL) {
                            analysisRepository.largestExpenses(
                                fromDate = range.fromDate.toString(),
                                toDate = range.toDateExclusive.toString(),
                                oneTimeMode = snapshot.oneTimeMode,
                                categoryNature = categoryNature,
                            )
                        } else {
                            emptyList()
                        },
                        topMerchants = if (snapshot.analysisMode == AnalysisMode.ACTUAL) {
                            analysisRepository.topMerchants(
                                fromDate = range.fromDate.toString(),
                                toDate = range.toDateExclusive.toString(),
                                oneTimeMode = snapshot.oneTimeMode,
                                categoryNature = categoryNature,
                            )
                        } else {
                            emptyList()
                        },
                        categoryTrends = if (snapshot.analysisMode == AnalysisMode.ACTUAL) {
                            analysisRepository.categoryTrends(
                                fromDate = range.fromDate.toString(),
                                toDate = range.toDateExclusive.toString(),
                                bucket = range.bucket,
                                oneTimeMode = snapshot.oneTimeMode,
                                categoryNature = categoryNature,
                            )
                        } else {
                            emptyList()
                        },
                        netWorthPoints = if (snapshot.analysisMode == AnalysisMode.ACTUAL) {
                            analysisRepository.netWorthOverTime(
                                fromDate = range.fromDate.toString(),
                                toDate = range.toDateExclusive.toString(),
                                bucket = range.bucket,
                            )
                        } else {
                            emptyList()
                        },
                        heatmapDays = heatmapData,
                        recurringCostSummary = if (snapshot.analysisMode == AnalysisMode.ACTUAL) {
                            buildRecurringCostSummary(templateRepository.listActive())
                        } else {
                            RecurringCostSummary()
                        },
                    )
                }
            }
            val currentDivisor = result.getOrNull()?.let { loaded ->
                averageDivisor(
                    range = range,
                    scope = snapshot.scope,
                    analysisMode = snapshot.analysisMode,
                    chartBuckets = loaded.chartBuckets,
                    flowBuckets = loaded.flowBuckets,
                )
            } ?: 1L
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        totals = it.totals,
                        previousTotals = it.previousTotals,
                        categories = it.categories,
                        chartBuckets = it.chartBuckets,
                        flowBuckets = it.flowBuckets,
                        largestExpenses = it.largestExpenses,
                        topMerchants = it.topMerchants,
                        categoryTrends = it.categoryTrends,
                        netWorthPoints = it.netWorthPoints,
                        heatmapDays = it.heatmapDays,
                        recurringCostSummary = it.recurringCostSummary,
                        currentAverageDivisor = currentDivisor,
                        previousAverageDivisor = previousRange?.let {
                            previousAverageDivisor(range, previousRange, currentDivisor)
                        } ?: 1L,
                        isLoading = false,
                    )
                },
                onFailure = {
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = it.message ?: it.javaClass.simpleName,
                    )
                },
            )
        }
    }

    fun onScopeSelected(scope: AnalysisScope) {
        val state = _state.value
        _state.value = state.copy(
            scope = scope,
            comparePrevious = if (scope == AnalysisScope.ALL_TIME) false else state.comparePrevious,
        )
        refresh()
    }

    fun onAnalysisModeSelected(analysisMode: AnalysisMode) {
        _state.value = _state.value.copy(analysisMode = analysisMode)
        refresh()
    }

    fun onValueModeSelected(valueMode: AnalysisValueMode) {
        _state.value = _state.value.copy(valueMode = valueMode)
        refresh()
    }

    fun onNatureFilterSelected(natureFilter: AnalysisNatureFilter) {
        _state.value = _state.value.copy(natureFilter = natureFilter)
        refresh()
    }

    fun onOneTimeModeSelected(oneTimeMode: AnalysisOneTimeMode) {
        _state.value = _state.value.copy(oneTimeMode = oneTimeMode)
        refresh()
    }

    fun onGroupTripsAsBlocksChanged(groupTripsAsBlocks: Boolean) {
        _state.value = _state.value.copy(groupTripsAsBlocks = groupTripsAsBlocks)
        refresh()
    }

    fun onPreviousPeriodClicked() {
        shiftPeriod(delta = -1)
    }

    fun onNextPeriodClicked() {
        shiftPeriod(delta = 1)
    }

    fun onComparePreviousChanged(compare: Boolean) {
        val state = _state.value
        if (state.scope == AnalysisScope.ALL_TIME && compare) return
        _state.value = state.copy(comparePrevious = compare)
        refresh()
    }

    fun onCustomFromChanged(value: String) {
        _state.value = _state.value.copy(customFrom = value)
        if (_state.value.scope == AnalysisScope.CUSTOM) refresh()
    }

    fun onCustomToChanged(value: String) {
        _state.value = _state.value.copy(customTo = value)
        if (_state.value.scope == AnalysisScope.CUSTOM) refresh()
    }

    fun onResetPeriodClicked() {
        val today = todayProvider()
        val month = YearMonth.from(today)
        _state.value = AnalysisUiState(
            month = month,
            year = today.year,
            customFrom = month.atDay(1).toString(),
            customTo = month.atEndOfMonth().toString(),
        )
        refresh()
    }

    fun setAccountFilter(accountId: String, accountName: String) {
        _state.value = _state.value.copy(
            filterAccountId = accountId,
            filterAccountName = accountName,
        )
    }

    fun clearAccountFilter() {
        _state.value = _state.value.copy(filterAccountId = null, filterAccountName = null)
    }

    private fun shiftPeriod(delta: Long) {
        val state = _state.value
        _state.value = when (state.scope) {
            AnalysisScope.MONTH -> state.copy(month = state.month.plusMonths(delta))
            AnalysisScope.YEAR -> state.copy(year = state.year + delta.toInt())
            AnalysisScope.ALL_TIME,
            AnalysisScope.CUSTOM,
            -> state
        }
        refresh()
    }

    class Factory(
        private val analysisRepository: AnalysisRepository,
        private val templateRepository: TemplateRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AnalysisViewModel::class.java)) {
                return AnalysisViewModel(
                    analysisRepository = analysisRepository,
                    templateRepository = templateRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

enum class AnalysisScope {
    MONTH,
    YEAR,
    ALL_TIME,
    CUSTOM,
}

enum class AnalysisMode {
    ACTUAL,
    FLOW,
}

enum class AnalysisValueMode {
    TOTALS,
    AVERAGES,
}

enum class AnalysisNatureFilter {
    ALL,
    FIXED,
    VARIABLE,
}

data class AnalysisPeriodRange(
    val fromDate: LocalDate,
    val toDateExclusive: LocalDate,
    val bucket: AnalysisBucket,
)

data class AnalysisUiState(
    val scope: AnalysisScope = AnalysisScope.MONTH,
    val analysisMode: AnalysisMode = AnalysisMode.ACTUAL,
    val valueMode: AnalysisValueMode = AnalysisValueMode.TOTALS,
    val natureFilter: AnalysisNatureFilter = AnalysisNatureFilter.ALL,
    val oneTimeMode: AnalysisOneTimeMode = AnalysisOneTimeMode.INCLUDE,
    val month: YearMonth = YearMonth.now(),
    val year: Int = LocalDate.now().year,
    val customFrom: String = "",
    val customTo: String = "",
    val comparePrevious: Boolean = false,
    val groupTripsAsBlocks: Boolean = true,
    val currentRange: AnalysisPeriodRange? = null,
    val previousRange: AnalysisPeriodRange? = null,
    val totals: AnalysisPeriodTotals = emptyAnalysisTotals(),
    val previousTotals: AnalysisPeriodTotals? = null,
    val categories: List<AnalysisCategoryTotal> = emptyList(),
    val chartBuckets: List<AnalysisIncomeExpenseBucket> = emptyList(),
    val flowBuckets: List<AnalysisAccountFlowBucket> = emptyList(),
    val largestExpenses: List<AnalysisLargestExpense> = emptyList(),
    val topMerchants: List<AnalysisMerchantTotal> = emptyList(),
    val categoryTrends: List<AnalysisCategoryTrendPoint> = emptyList(),
    val netWorthPoints: List<AnalysisNetWorthPoint> = emptyList(),
    val heatmapDays: List<AnalysisIncomeExpenseBucket> = emptyList(),
    val recurringCostSummary: RecurringCostSummary = RecurringCostSummary(),
    val currentAverageDivisor: Long = 1,
    val previousAverageDivisor: Long = 1,
    val filterAccountId: String? = null,
    val filterAccountName: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    @StringRes val customErrorRes: Int? = null,
) {
    val hasActivity: Boolean
        get() = when (analysisMode) {
            AnalysisMode.ACTUAL -> totals.actualIncomeCents != 0L || totals.actualExpenseCents != 0L
            AnalysisMode.FLOW -> totals.accountFlowCents != 0L
        }

    val canMovePeriod: Boolean
        get() = scope == AnalysisScope.MONTH || scope == AnalysisScope.YEAR

    val canCompare: Boolean
        get() = scope != AnalysisScope.ALL_TIME

    val hasActivePeriodControl: Boolean
        get() = comparePrevious ||
            scope != AnalysisScope.MONTH ||
            analysisMode != AnalysisMode.ACTUAL ||
            valueMode != AnalysisValueMode.TOTALS ||
            natureFilter != AnalysisNatureFilter.ALL ||
            oneTimeMode != AnalysisOneTimeMode.INCLUDE ||
            !groupTripsAsBlocks
}

data class RecurringCostSummary(
    val monthlyExpenseCents: Long = 0L,
    val items: List<RecurringCostItem> = emptyList(),
) {
    val hasCosts: Boolean get() = monthlyExpenseCents > 0L
}

data class RecurringCostItem(
    val templateId: String,
    val label: String?,
    val categoryName: String?,
    val monthlyExpenseCents: Long,
)

internal data class AnalysisRangeValidation(
    val range: AnalysisPeriodRange?,
    @StringRes val errorRes: Int? = null,
)

internal fun resolveAnalysisRange(
    scope: AnalysisScope,
    month: YearMonth,
    year: Int,
    customFrom: String,
    customTo: String,
): AnalysisRangeValidation =
    when (scope) {
        AnalysisScope.MONTH -> AnalysisRangeValidation(
            range = AnalysisPeriodRange(
                fromDate = month.atDay(1),
                toDateExclusive = month.plusMonths(1).atDay(1),
                bucket = AnalysisBucket.DAY,
            ),
        )
        AnalysisScope.YEAR -> AnalysisRangeValidation(
            range = AnalysisPeriodRange(
                fromDate = LocalDate.of(year, 1, 1),
                toDateExclusive = LocalDate.of(year + 1, 1, 1),
                bucket = AnalysisBucket.MONTH,
            ),
        )
        AnalysisScope.ALL_TIME -> AnalysisRangeValidation(
            range = AnalysisPeriodRange(
                fromDate = LocalDate.of(1, 1, 1),
                toDateExclusive = LocalDate.of(9999, 12, 31),
                bucket = AnalysisBucket.MONTH,
            ),
        )
        AnalysisScope.CUSTOM -> resolveCustomRange(customFrom = customFrom, customTo = customTo)
    }

internal fun previousAnalysisRange(
    range: AnalysisPeriodRange,
    scope: AnalysisScope,
): AnalysisPeriodRange? =
    when (scope) {
        AnalysisScope.MONTH -> range.copy(
            fromDate = range.fromDate.minusMonths(1),
            toDateExclusive = range.toDateExclusive.minusMonths(1),
        )
        AnalysisScope.YEAR -> range.copy(
            fromDate = range.fromDate.minusYears(1),
            toDateExclusive = range.toDateExclusive.minusYears(1),
        )
        AnalysisScope.CUSTOM -> {
            val days = ChronoUnit.DAYS.between(range.fromDate, range.toDateExclusive)
            range.copy(
                fromDate = range.fromDate.minusDays(days),
                toDateExclusive = range.fromDate,
            )
        }
        AnalysisScope.ALL_TIME -> null
    }

private fun resolveCustomRange(
    customFrom: String,
    customTo: String,
): AnalysisRangeValidation {
    val from = parseDate(customFrom)
    val toInclusive = parseDate(customTo)
    if (from == null || toInclusive == null) {
        return AnalysisRangeValidation(
            range = null,
            errorRes = R.string.movement_filter_date_invalid,
        )
    }
    if (from > toInclusive) {
        return AnalysisRangeValidation(
            range = null,
            errorRes = R.string.movement_filter_date_order_invalid,
        )
    }
    val toExclusive = toInclusive.plusDays(1)
    val days = ChronoUnit.DAYS.between(from, toExclusive)
    return AnalysisRangeValidation(
        range = AnalysisPeriodRange(
            fromDate = from,
            toDateExclusive = toExclusive,
            bucket = if (days <= 93) AnalysisBucket.DAY else AnalysisBucket.MONTH,
        ),
    )
}

private data class AnalysisLoadedData(
    val totals: AnalysisPeriodTotals,
    val previousTotals: AnalysisPeriodTotals?,
    val categories: List<AnalysisCategoryTotal>,
    val chartBuckets: List<AnalysisIncomeExpenseBucket>,
    val flowBuckets: List<AnalysisAccountFlowBucket>,
    val largestExpenses: List<AnalysisLargestExpense>,
    val topMerchants: List<AnalysisMerchantTotal>,
    val categoryTrends: List<AnalysisCategoryTrendPoint>,
    val netWorthPoints: List<AnalysisNetWorthPoint>,
    val heatmapDays: List<AnalysisIncomeExpenseBucket>,
    val recurringCostSummary: RecurringCostSummary,
)

/** A daily heatmap is only meaningful for a bounded span; cap it at roughly one year. */
private fun AnalysisPeriodRange.isHeatmapBounded(): Boolean {
    val days = ChronoUnit.DAYS.between(fromDate, toDateExclusive)
    return days in 1..366
}

private fun emptyAnalysisTotals(): AnalysisPeriodTotals =
    AnalysisPeriodTotals(
        netWorthCents = 0,
        actualIncomeCents = 0,
        actualExpenseCents = 0,
        netActualCents = 0,
        accountFlowCents = 0,
        savingsRateBasisPoints = 0,
    )

private fun parseDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value.trim()) }.getOrNull()

private fun AnalysisNatureFilter.toQueryNature(): AnalysisCategoryNature? =
    when (this) {
        AnalysisNatureFilter.ALL -> null
        AnalysisNatureFilter.FIXED -> AnalysisCategoryNature.FIXED
        AnalysisNatureFilter.VARIABLE -> AnalysisCategoryNature.VARIABLE
    }

internal fun averageDivisor(
    range: AnalysisPeriodRange,
    scope: AnalysisScope,
    analysisMode: AnalysisMode,
    chartBuckets: List<AnalysisIncomeExpenseBucket>,
    flowBuckets: List<AnalysisAccountFlowBucket>,
): Long {
    if (scope == AnalysisScope.ALL_TIME) {
        val count = when (analysisMode) {
            AnalysisMode.ACTUAL -> chartBuckets.size
            AnalysisMode.FLOW -> flowBuckets.distinctBy { it.bucket }.size
        }
        return count.coerceAtLeast(1).toLong()
    }
    return when (range.bucket) {
        AnalysisBucket.DAY -> ChronoUnit.DAYS.between(range.fromDate, range.toDateExclusive)
        AnalysisBucket.MONTH -> {
            val start = YearMonth.from(range.fromDate)
            val end = YearMonth.from(range.toDateExclusive.minusDays(1))
            ChronoUnit.MONTHS.between(start, end) + 1
        }
        AnalysisBucket.YEAR -> {
            val years = range.toDateExclusive.year - range.fromDate.year
            years.toLong().coerceAtLeast(1)
        }
    }.coerceAtLeast(1)
}

private fun previousAverageDivisor(
    currentRange: AnalysisPeriodRange,
    previousRange: AnalysisPeriodRange,
    currentDivisor: Long,
): Long =
    if (currentRange.bucket == previousRange.bucket) {
        currentDivisor
    } else {
        averageDivisor(
            range = previousRange,
            scope = AnalysisScope.CUSTOM,
            analysisMode = AnalysisMode.ACTUAL,
            chartBuckets = emptyList(),
            flowBuckets = emptyList(),
        )
    }

internal fun buildRecurringCostSummary(templates: List<TemplateSummary>): RecurringCostSummary {
    val items = templates
        .mapNotNull { template ->
            val monthlyCents = template.monthlyRecurringExpenseCents() ?: return@mapNotNull null
            RecurringCostItem(
                templateId = template.id,
                label = template.name?.takeIf { it.isNotBlank() }
                    ?: template.payee?.takeIf { it.isNotBlank() },
                categoryName = template.categoryName,
                monthlyExpenseCents = monthlyCents,
            )
        }
        .sortedByDescending { it.monthlyExpenseCents }

    return RecurringCostSummary(
        monthlyExpenseCents = items.sumOf { it.monthlyExpenseCents },
        items = items,
    )
}

private fun TemplateSummary.monthlyRecurringExpenseCents(): Long? {
    if (status != TemplateStatus.ACTIVE || type != MovementType.EXPENSE || amountIsVariable) {
        return null
    }
    val amount = amountCents?.takeIf { it > 0L } ?: return null
    val monthlyCents = when (frequency) {
        RecurrenceFrequency.WEEKLY -> amount.scaleRounded(numerator = 52L, denominator = 12L)
        RecurrenceFrequency.FORTNIGHTLY -> amount.scaleRounded(numerator = 26L, denominator = 12L)
        RecurrenceFrequency.MONTHLY -> amount
        RecurrenceFrequency.YEARLY -> amount.scaleRounded(numerator = 1L, denominator = 12L)
        RecurrenceFrequency.CUSTOM -> customMonthlyExpenseCents(amount)
    } ?: return null
    return monthlyCents.takeIf { it > 0L }
}

private fun TemplateSummary.customMonthlyExpenseCents(amount: Long): Long? {
    val interval = intervalCount?.takeIf { it > 0L } ?: return null
    return when (customUnit) {
        CustomRecurrenceUnit.DAYS -> amount.scaleRounded(numerator = 365L, denominator = interval * 12L)
        CustomRecurrenceUnit.WEEKS -> amount.scaleRounded(numerator = 52L, denominator = interval * 12L)
        CustomRecurrenceUnit.MONTHS -> amount.scaleRounded(numerator = 1L, denominator = interval)
        CustomRecurrenceUnit.YEARS -> amount.scaleRounded(numerator = 1L, denominator = interval * 12L)
        null -> null
    }
}

private fun Long.scaleRounded(numerator: Long, denominator: Long): Long =
    (this * numerator + denominator / 2L) / denominator
