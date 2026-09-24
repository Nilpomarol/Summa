package com.gestorfinances.app.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AnalysisBucket
import com.gestorfinances.app.data.repository.AnalysisCategoryNature
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisIncomeExpenseBucket
import com.gestorfinances.app.data.repository.AnalysisOneTimeMode
import com.gestorfinances.app.data.repository.AnalysisPeriodTotals
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.ui.common.rollUpToParents
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnalysisViewModel(
    private val analysisRepository: AnalysisRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val initialToday = todayProvider()
    private val initialMonth = YearMonth.from(initialToday)
    private val _state = MutableStateFlow(
        AnalysisUiState(
            month = initialMonth,
            year = initialToday.year,
        ),
    )
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    private var loadedSignature: String? = null
    private var filterOptionsLoaded = false

    fun onScreenShown() {
        loadFilterOptions()
        refresh()
    }

    private fun loadFilterOptions() {
        if (filterOptionsLoaded) return
        filterOptionsLoaded = true
        viewModelScope.launch {
            val accounts = withContext(ioDispatcher) { accountRepository.listActive() }
            val categories = withContext(ioDispatcher) { categoryRepository.listActive() }
            val months = withContext(ioDispatcher) { analysisRepository.activityMonths() }
            update { state ->
                val selectedMonth = state.month.takeIf { it in months } ?: months.firstOrNull() ?: state.month
                val availableYears = months.map { it.year }.distinct().sorted()
                val selectedYear = state.year.takeIf { it in availableYears } ?: selectedMonth.year
                state.copy(
                    month = selectedMonth,
                    year = selectedYear,
                    accountOptions = accounts,
                    categoryOptions = categories,
                    activityMonths = months,
                )
            }
            refresh()
        }
    }

    fun refresh() {
        val snapshot = _state.value
        val range = resolveAnalysisRange(snapshot.scope, snapshot.month, snapshot.year)
        val signature = signatureOf(snapshot, range)
        val signatureChanged = signature != loadedSignature
        val needsLoad = signatureChanged || snapshot.resum == null ||
            (snapshot.scope != AnalysisScope.ALL_TIME && snapshot.comparison == null)
        loadedSignature = signature
        _state.value = snapshot.copy(
            currentRange = range,
            resum = if (signatureChanged) null else snapshot.resum,
            comparison = if (signatureChanged) null else snapshot.comparison,
            errorMessage = null,
        )
        if (!needsLoad) return

        viewModelScope.launch {
            try {
                val (summary, comparison) = withContext(ioDispatcher) {
                    val loadedSummary = loadSummary(snapshot, range)
                    loadedSummary to loadComparison(snapshot, range, loadedSummary.chartBuckets)
                }
                update { state ->
                    if (loadedSignature == signature) {
                        state.copy(
                            resum = summary,
                            comparison = comparison,
                            errorMessage = null,
                        )
                    } else {
                        state
                    }
                }
            } catch (error: Throwable) {
                update { state ->
                    if (loadedSignature == signature) {
                        state.copy(
                            errorMessage = error.message ?: error.javaClass.simpleName,
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun onScopeSelected(scope: AnalysisScope) {
        _state.value = _state.value.copy(scope = scope)
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

    fun onPreviousPeriodClicked() = shiftPeriod(delta = -1)

    fun onNextPeriodClicked() = shiftPeriod(delta = 1)

    fun onMonthSelected(month: YearMonth) {
        if (month !in _state.value.activityMonths) return
        _state.value = _state.value.copy(month = month)
        refresh()
    }

    fun onYearSelected(year: Int) {
        if (year !in _state.value.activityMonths.map { it.year }) return
        _state.value = _state.value.copy(year = year)
        refresh()
    }

    fun setAccountFilter(accountId: String, accountName: String) {
        _state.value = _state.value.copy(filterAccountId = accountId, filterAccountName = accountName)
        refresh()
    }

    fun clearAccountFilter() {
        _state.value = _state.value.copy(filterAccountId = null, filterAccountName = null)
        refresh()
    }

    fun setCategoryFilter(categoryId: String, categoryName: String) {
        _state.value = _state.value.copy(filterCategoryId = categoryId, filterCategoryName = categoryName)
        refresh()
    }

    fun clearCategoryFilter() {
        _state.value = _state.value.copy(filterCategoryId = null, filterCategoryName = null)
        refresh()
    }

    fun resetFilters() {
        _state.value = _state.value.copy(
            natureFilter = AnalysisNatureFilter.ALL,
            oneTimeMode = AnalysisOneTimeMode.INCLUDE,
            groupTripsAsBlocks = true,
            filterAccountId = null,
            filterAccountName = null,
            filterCategoryId = null,
            filterCategoryName = null,
        )
        refresh()
    }

    private fun shiftPeriod(delta: Long) {
        val state = _state.value
        _state.value = when (state.scope) {
            AnalysisScope.MONTH -> state.activityMonths.shiftFrom(state.month, delta)
                ?.let { state.copy(month = it) } ?: state
            AnalysisScope.YEAR -> state.activityMonths.map { it.year }.distinct().sorted()
                .shiftFrom(state.year, delta)?.let { state.copy(year = it) } ?: state
            AnalysisScope.ALL_TIME -> state
        }
        refresh()
    }

    private inline fun update(block: (AnalysisUiState) -> AnalysisUiState) {
        _state.value = block(_state.value)
    }

    private fun loadSummary(s: AnalysisUiState, range: AnalysisPeriodRange): ResumData {
        val totals = analysisRepository.periodTotals(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        val chartBuckets = analysisRepository.incomeVsExpense(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            bucket = range.bucket,
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        val categories = analysisRepository.actualBreakdown(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            groupTrips = s.groupTripsAsBlocks,
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        ).rollUpToParents(categoryRepository.listActive().associateBy { it.id })
        return ResumData(totals = totals, chartBuckets = chartBuckets, topCategories = categories)
    }

    private fun loadComparison(
        s: AnalysisUiState,
        range: AnalysisPeriodRange,
        currentChartBuckets: List<AnalysisIncomeExpenseBucket>,
    ): ComparisonData? {
        val comparisonRange = previousAnalysisRange(range, s.scope)?.let { previous ->
            comparablePreviousRange(
                currentRange = range,
                previousRange = previous,
                scope = s.scope,
                today = todayProvider(),
            )
        } ?: return null
        val categoriesById = categoryRepository.listActive().associateBy { it.id }
        val previousByCategory = analysisRepository.actualByCategory(
            fromDate = comparisonRange.fromDate.toString(),
            toDate = comparisonRange.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        ).rollUpToParents(categoriesById)
        val previousTotals = analysisRepository.periodTotals(
            fromDate = comparisonRange.fromDate.toString(),
            toDate = comparisonRange.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        val previousChartBuckets = analysisRepository.incomeVsExpense(
            fromDate = comparisonRange.fromDate.toString(),
            toDate = comparisonRange.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            bucket = range.bucket,
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        return ComparisonData(
            previousByCategory = previousByCategory,
            previousTotals = previousTotals,
            comparisonRange = comparisonRange,
            currentChartBuckets = currentChartBuckets,
            previousChartBuckets = previousChartBuckets,
        )
    }
}

enum class AnalysisScope { MONTH, YEAR, ALL_TIME }

enum class AnalysisNatureFilter { ALL, FIXED, VARIABLE }

data class AnalysisPeriodRange(
    val fromDate: LocalDate,
    val toDateExclusive: LocalDate,
    val bucket: AnalysisBucket,
)

data class ResumData(
    val totals: AnalysisPeriodTotals,
    val chartBuckets: List<AnalysisIncomeExpenseBucket>,
    val topCategories: List<AnalysisCategoryTotal>,
)

data class ComparisonData(
    val previousByCategory: List<AnalysisCategoryTotal>,
    val previousTotals: AnalysisPeriodTotals,
    val comparisonRange: AnalysisPeriodRange,
    val currentChartBuckets: List<AnalysisIncomeExpenseBucket>,
    val previousChartBuckets: List<AnalysisIncomeExpenseBucket>,
)

data class AnalysisUiState(
    val scope: AnalysisScope = AnalysisScope.MONTH,
    val natureFilter: AnalysisNatureFilter = AnalysisNatureFilter.ALL,
    val oneTimeMode: AnalysisOneTimeMode = AnalysisOneTimeMode.INCLUDE,
    val month: YearMonth = YearMonth.now(),
    val year: Int = LocalDate.now().year,
    val groupTripsAsBlocks: Boolean = true,
    val currentRange: AnalysisPeriodRange? = null,
    val filterAccountId: String? = null,
    val filterAccountName: String? = null,
    val filterCategoryId: String? = null,
    val filterCategoryName: String? = null,
    val accountOptions: List<AccountSummary> = emptyList(),
    val categoryOptions: List<CategoryRecord> = emptyList(),
    val activityMonths: List<YearMonth> = emptyList(),
    val resum: ResumData? = null,
    val comparison: ComparisonData? = null,
    val errorMessage: String? = null,
) {
    val canMovePeriod: Boolean
        get() = scope == AnalysisScope.MONTH || scope == AnalysisScope.YEAR

    val hasActiveFilters: Boolean
        get() = natureFilter != AnalysisNatureFilter.ALL ||
            oneTimeMode != AnalysisOneTimeMode.INCLUDE ||
            !groupTripsAsBlocks ||
            filterAccountId != null ||
            filterCategoryId != null

    fun queryNature(): AnalysisCategoryNature? = natureFilter.toQueryNature()
}

private fun <T : Comparable<T>> List<T>.shiftFrom(value: T, delta: Long): T? {
    val sorted = distinct().sorted()
    val index = sorted.indexOf(value)
    if (index == -1) return null
    return sorted.getOrNull(index + delta.toInt())
}

private fun signatureOf(s: AnalysisUiState, range: AnalysisPeriodRange): String =
    listOf(
        s.scope,
        range.fromDate,
        range.toDateExclusive,
        s.oneTimeMode,
        s.natureFilter,
        s.groupTripsAsBlocks,
        s.filterAccountId,
        s.filterCategoryId,
    ).joinToString(separator = "|")

internal fun resolveAnalysisRange(
    scope: AnalysisScope,
    month: YearMonth,
    year: Int,
): AnalysisPeriodRange =
    when (scope) {
        AnalysisScope.MONTH -> AnalysisPeriodRange(
            fromDate = month.atDay(1),
            toDateExclusive = month.plusMonths(1).atDay(1),
            bucket = AnalysisBucket.DAY,
        )
        AnalysisScope.YEAR -> AnalysisPeriodRange(
            fromDate = LocalDate.of(year, 1, 1),
            toDateExclusive = LocalDate.of(year + 1, 1, 1),
            bucket = AnalysisBucket.MONTH,
        )
        AnalysisScope.ALL_TIME -> AnalysisPeriodRange(
            fromDate = LocalDate.of(1, 1, 1),
            toDateExclusive = LocalDate.of(9999, 12, 31),
            bucket = AnalysisBucket.MONTH,
        )
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
        AnalysisScope.ALL_TIME -> null
    }

/** Limits a current month/year comparison to the equivalent day in the preceding period. */
internal fun comparablePreviousRange(
    currentRange: AnalysisPeriodRange,
    previousRange: AnalysisPeriodRange,
    scope: AnalysisScope,
    today: LocalDate,
): AnalysisPeriodRange {
    if (today < currentRange.fromDate || today >= currentRange.toDateExclusive) return previousRange
    val equivalentEnd = when (scope) {
        AnalysisScope.MONTH -> previousRange.fromDate.plusDays(today.dayOfMonth.toLong())
        AnalysisScope.YEAR -> today.minusYears(1).plusDays(1)
        AnalysisScope.ALL_TIME -> return previousRange
    }
    return previousRange.copy(toDateExclusive = minOf(equivalentEnd, previousRange.toDateExclusive))
}

private fun AnalysisNatureFilter.toQueryNature(): AnalysisCategoryNature? =
    when (this) {
        AnalysisNatureFilter.ALL -> null
        AnalysisNatureFilter.FIXED -> AnalysisCategoryNature.FIXED
        AnalysisNatureFilter.VARIABLE -> AnalysisCategoryNature.VARIABLE
    }
