package com.gestorfinances.app.ui.analysis

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AnalysisBucket
import com.gestorfinances.app.data.repository.AnalysisCategoryFrequency
import com.gestorfinances.app.data.repository.AnalysisCategoryNature
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisCategoryTrendPoint
import com.gestorfinances.app.data.repository.AnalysisIncomeExpenseBucket
import com.gestorfinances.app.data.repository.AnalysisNetWorthPoint
import com.gestorfinances.app.data.repository.AnalysisOneTimeMode
import com.gestorfinances.app.data.repository.AnalysisPeriodTotals
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.AnalysisWeekdaySpend
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.ui.common.rollUpToParents
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
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
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val movementRepository: MovementRepository,
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

    /** Tracks the data inputs (range + filters) that the loaded tab caches belong to. */
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
            update { it.copy(accountOptions = accounts, categoryOptions = categories) }
        }
    }

    /** Re-resolves the range, invalidates stale tab caches, then (re)loads the current tab. */
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
            loadedSignature = null
            _state.value = snapshot.copy(
                currentRange = null,
                customErrorRes = validation.errorRes,
                errorMessage = null,
                loadingTabs = emptySet(),
            ).clearedTabs()
            return
        }
        // The comparison period is independent of the main period — it only reseeds on scope
        // change (onScopeSelected) or an explicit "Restableix" (onResetComparison), never here.
        val signature = signatureOf(snapshot, range)
        val changed = signature != loadedSignature
        loadedSignature = signature
        _state.value = snapshot.copy(
            currentRange = range,
            customErrorRes = null,
            errorMessage = null,
            loadingTabs = if (changed) emptySet() else snapshot.loadingTabs,
        ).let { if (changed) it.clearedTabs() else it }

        viewModelScope.launch {
            val divisor = withContext(ioDispatcher) { computeAverageDivisor(snapshot, range) }
            update { it.copy(currentAverageDivisor = divisor) }
            loadTab(_state.value.selectedTab)
        }
    }

    /**
     * Re-derives the default comparison period (immediately preceding the current main period) for
     * [scope]. Used on scope change and on "Restableix" — the comparison period otherwise stays put
     * while the user navigates the main period.
     */
    private fun reseedComparisonForScope(s: AnalysisUiState, scope: AnalysisScope): AnalysisUiState {
        val range = resolveAnalysisRange(
            scope = scope,
            month = s.month,
            year = s.year,
            customFrom = s.customFrom,
            customTo = s.customTo,
        ).range ?: return s
        val previous = previousAnalysisRange(range = range, scope = scope) ?: return s
        return when (scope) {
            AnalysisScope.MONTH -> s.copy(comparisonMonth = YearMonth.from(previous.fromDate))
            AnalysisScope.YEAR -> s.copy(comparisonYear = previous.fromDate.year)
            AnalysisScope.CUSTOM -> s.copy(
                comparisonCustomFrom = previous.fromDate.toString(),
                comparisonCustomTo = previous.toDateExclusive.minusDays(1).toString(),
            )
            AnalysisScope.ALL_TIME -> s
        }
    }

    fun onTabSelected(tab: AnalysisTab) {
        if (_state.value.selectedTab == tab) return
        _state.value = _state.value.copy(selectedTab = tab)
        loadTab(tab)
    }

    fun onScopeSelected(scope: AnalysisScope) {
        val snapshot = _state.value
        _state.value = reseedComparisonForScope(snapshot, scope).copy(scope = scope, comparisonTouched = false)
        refresh()
    }

    fun onValueModeSelected(valueMode: AnalysisValueMode) {
        // Display-only transform (totals ÷ divisor) — no data reload needed.
        _state.value = _state.value.copy(valueMode = valueMode)
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
        _state.value = _state.value.copy(month = month)
        refresh()
    }

    fun onYearSelected(year: Int) {
        _state.value = _state.value.copy(year = year)
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

    fun onComparisonMonthSelected(month: YearMonth) {
        _state.value = _state.value.copy(comparisonMonth = month, comparisonTouched = true)
        refresh()
    }

    fun onComparisonYearSelected(year: Int) {
        _state.value = _state.value.copy(comparisonYear = year, comparisonTouched = true)
        refresh()
    }

    fun onComparisonCustomFromChanged(value: String) {
        _state.value = _state.value.copy(comparisonCustomFrom = value, comparisonTouched = true)
        refresh()
    }

    fun onComparisonCustomToChanged(value: String) {
        _state.value = _state.value.copy(comparisonCustomTo = value, comparisonTouched = true)
        refresh()
    }

    fun onResetComparison() {
        val snapshot = _state.value
        _state.value = reseedComparisonForScope(snapshot, snapshot.scope).copy(comparisonTouched = false)
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
            AnalysisScope.MONTH -> state.copy(month = state.month.plusMonths(delta))
            AnalysisScope.YEAR -> state.copy(year = state.year + delta.toInt())
            AnalysisScope.ALL_TIME, AnalysisScope.CUSTOM -> state
        }
        refresh()
    }

    private inline fun update(block: (AnalysisUiState) -> AnalysisUiState) {
        _state.value = block(_state.value)
    }

    private fun loadTab(tab: AnalysisTab) {
        val snapshot = _state.value
        val range = snapshot.currentRange ?: return
        if (tab in snapshot.loadingTabs || tab.isLoaded(snapshot)) return
        _state.value = snapshot.copy(loadingTabs = snapshot.loadingTabs + tab)
        viewModelScope.launch {
            try {
                when (tab) {
                    AnalysisTab.RESUM -> {
                        val data = withContext(ioDispatcher) { loadResum(snapshot, range) }
                        update { it.copy(resum = data, errorMessage = null) }
                    }
                    AnalysisTab.CATEGORIES -> {
                        val data = withContext(ioDispatcher) { loadCategories(snapshot, range) }
                        update { it.copy(categories = data, errorMessage = null) }
                    }
                    AnalysisTab.COMPARATIVA -> {
                        val data = withContext(ioDispatcher) { loadComparativa(snapshot, range) }
                        update { it.copy(comparativa = data, errorMessage = null) }
                    }
                    AnalysisTab.HISTORIC -> {
                        val data = withContext(ioDispatcher) { loadHistoric(snapshot, range) }
                        update { it.copy(historic = data, errorMessage = null) }
                    }
                    AnalysisTab.FIX_VARIABLE -> {
                        val data = withContext(ioDispatcher) { loadFixVariable(snapshot, range) }
                        update { it.copy(fixVariable = data, errorMessage = null) }
                    }
                }
            } catch (error: Throwable) {
                update { it.copy(errorMessage = error.message ?: error.javaClass.simpleName) }
            } finally {
                update { it.copy(loadingTabs = it.loadingTabs - tab) }
            }
        }
    }

    private fun loadResum(s: AnalysisUiState, range: AnalysisPeriodRange): ResumData {
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

    private fun loadCategories(s: AnalysisUiState, range: AnalysisPeriodRange): CategoriesData {
        val categories = analysisRepository.actualBreakdown(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            groupTrips = s.groupTripsAsBlocks,
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        // Sparklines want the last 6 months regardless of the selected period.
        val sparkFrom = YearMonth.from(range.toDateExclusive.minusDays(1)).minusMonths(5).atDay(1)
        val trends = analysisRepository.categoryTrends(
            fromDate = sparkFrom.toString(),
            toDate = range.toDateExclusive.toString(),
            bucket = AnalysisBucket.MONTH,
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        val frequency = analysisRepository.categoryFrequency(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        // Roll child categories up into their container so the breakdown, sparklines, and scatter
        // present a parent-with-children as one rolled-up entry (matching the Categories screen).
        val categoriesById = categoryRepository.listActive().associateBy { it.id }
        return CategoriesData(
            categories = categories.rollUpToParents(categoriesById),
            trends = trends.rollUpToParents(categoriesById),
            frequency = frequency.rollUpToParents(categoriesById),
        )
    }

    private fun loadComparativa(s: AnalysisUiState, range: AnalysisPeriodRange): ComparativaData {
        val comparisonValidation = comparisonAnalysisRange(
            scope = s.scope,
            comparisonMonth = s.comparisonMonth,
            comparisonYear = s.comparisonYear,
            comparisonCustomFrom = s.comparisonCustomFrom,
            comparisonCustomTo = s.comparisonCustomTo,
            currentRange = range,
        )
        val comparisonRange = comparisonValidation.range
        val current = analysisRepository.actualByCategory(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        val currentTotals = analysisRepository.periodTotals(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        val currentChartBuckets = analysisRepository.incomeVsExpense(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            bucket = range.bucket,
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        val previous = comparisonRange?.let {
            analysisRepository.actualByCategory(
                fromDate = it.fromDate.toString(),
                toDate = it.toDateExclusive.toString(),
                oneTimeMode = s.oneTimeMode,
                categoryNature = s.queryNature(),
                accountId = s.filterAccountId,
                categoryId = s.filterCategoryId,
            )
        }.orEmpty()
        val previousTotals = comparisonRange?.let {
            analysisRepository.periodTotals(
                fromDate = it.fromDate.toString(),
                toDate = it.toDateExclusive.toString(),
                oneTimeMode = s.oneTimeMode,
                categoryNature = s.queryNature(),
                accountId = s.filterAccountId,
                categoryId = s.filterCategoryId,
            )
        }
        val previousChartBuckets = comparisonRange?.let {
            analysisRepository.incomeVsExpense(
                fromDate = it.fromDate.toString(),
                toDate = it.toDateExclusive.toString(),
                oneTimeMode = s.oneTimeMode,
                categoryNature = s.queryNature(),
                bucket = range.bucket,
                accountId = s.filterAccountId,
                categoryId = s.filterCategoryId,
            )
        }.orEmpty()
        return ComparativaData(
            currentByCategory = current,
            previousByCategory = previous,
            currentTotals = currentTotals,
            previousTotals = previousTotals,
            comparisonRange = comparisonRange,
            currentChartBuckets = currentChartBuckets,
            previousChartBuckets = previousChartBuckets,
        )
    }

    private fun loadHistoric(s: AnalysisUiState, range: AnalysisPeriodRange): HistoricData {
        val trends = analysisRepository.categoryTrends(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            bucket = range.bucket,
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        val weekday = analysisRepository.weekdaySpend(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        val netWorth = analysisRepository.netWorthOverTime(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            bucket = range.bucket,
        )
        val heatmap = if (range.isHeatmapBounded()) {
            analysisRepository.incomeVsExpense(
                fromDate = range.fromDate.toString(),
                toDate = range.toDateExclusive.toString(),
                oneTimeMode = s.oneTimeMode,
                categoryNature = s.queryNature(),
                bucket = AnalysisBucket.DAY,
                accountId = s.filterAccountId,
                categoryId = s.filterCategoryId,
            )
        } else {
            emptyList()
        }
        val totals = analysisRepository.periodTotals(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        val incomeExpenseBuckets = analysisRepository.incomeVsExpense(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = s.queryNature(),
            bucket = range.bucket,
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        return HistoricData(
            trends = trends,
            weekday = weekday,
            netWorth = netWorth,
            heatmapDays = heatmap,
            incomeExpenseBuckets = incomeExpenseBuckets,
            totals = totals,
            daysOfBuffer = calculateDaysOfBuffer(
                scope = s.scope,
                range = range,
                totals = totals,
                bucketCount = incomeExpenseBuckets.size,
            ),
        )
    }

    private fun loadFixVariable(s: AnalysisUiState, range: AnalysisPeriodRange): FixVariableData {
        val totals = analysisRepository.periodTotals(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = null,
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        )
        // Note: the Fixed/Variable tab intentionally stays at the leaf level — `nature` is a
        // per-category attribute, so rolling children of possibly-mixed nature into one parent
        // bucket would misrepresent it.
        val fixedCategories = analysisRepository.actualByCategory(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = AnalysisCategoryNature.FIXED,
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        ).filter { it.expenseCents > 0 }
        val variableCategories = analysisRepository.actualByCategory(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            oneTimeMode = s.oneTimeMode,
            categoryNature = AnalysisCategoryNature.VARIABLE,
            accountId = s.filterAccountId,
            categoryId = s.filterCategoryId,
        ).filter { it.expenseCents > 0 }
        val templates = templateRepository.listActive()
        return FixVariableData(
            totals = totals,
            fixedCategories = fixedCategories,
            variableCategories = variableCategories,
            recurringCostSummary = buildRecurringCostSummary(templates),
            oldestRecurringItems = buildOldestRecurringItems(templates, movementRepository.countsByTemplate()),
        )
    }

    private fun computeAverageDivisor(s: AnalysisUiState, range: AnalysisPeriodRange): Long =
        if (s.scope == AnalysisScope.ALL_TIME) {
            analysisRepository.incomeVsExpense(
                fromDate = range.fromDate.toString(),
                toDate = range.toDateExclusive.toString(),
                oneTimeMode = s.oneTimeMode,
                categoryNature = s.queryNature(),
                bucket = AnalysisBucket.MONTH,
                accountId = s.filterAccountId,
                categoryId = s.filterCategoryId,
            ).size.coerceAtLeast(1).toLong()
        } else {
            deterministicDivisor(range)
        }

    class Factory(
        private val analysisRepository: AnalysisRepository,
        private val templateRepository: TemplateRepository,
        private val accountRepository: AccountRepository,
        private val categoryRepository: CategoryRepository,
        private val movementRepository: MovementRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AnalysisViewModel::class.java)) {
                return AnalysisViewModel(
                    analysisRepository = analysisRepository,
                    templateRepository = templateRepository,
                    accountRepository = accountRepository,
                    categoryRepository = categoryRepository,
                    movementRepository = movementRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

enum class AnalysisScope { MONTH, YEAR, ALL_TIME, CUSTOM }

enum class AnalysisValueMode { TOTALS, AVERAGES }

enum class AnalysisNatureFilter { ALL, FIXED, VARIABLE }

enum class AnalysisTab { RESUM, CATEGORIES, COMPARATIVA, HISTORIC, FIX_VARIABLE }

data class AnalysisPeriodRange(
    val fromDate: LocalDate,
    val toDateExclusive: LocalDate,
    val bucket: AnalysisBucket,
)

/** Bottom-line data for the Resum tab. */
data class ResumData(
    val totals: AnalysisPeriodTotals,
    val chartBuckets: List<AnalysisIncomeExpenseBucket>,
    val topCategories: List<AnalysisCategoryTotal>,
)

/** Distribution data for the Categories tab. */
data class CategoriesData(
    val categories: List<AnalysisCategoryTotal>,
    val trends: List<AnalysisCategoryTrendPoint>,
    val frequency: List<AnalysisCategoryFrequency>,
)

/** Period-over-period data for the Comparativa tab. */
data class ComparativaData(
    val currentByCategory: List<AnalysisCategoryTotal>,
    val previousByCategory: List<AnalysisCategoryTotal>,
    val currentTotals: AnalysisPeriodTotals,
    val previousTotals: AnalysisPeriodTotals?,
    val comparisonRange: AnalysisPeriodRange?,
    val currentChartBuckets: List<AnalysisIncomeExpenseBucket>,
    val previousChartBuckets: List<AnalysisIncomeExpenseBucket>,
)

/** Long-term pattern data for the Històric tab. */
data class HistoricData(
    val trends: List<AnalysisCategoryTrendPoint>,
    val weekday: List<AnalysisWeekdaySpend>,
    val netWorth: List<AnalysisNetWorthPoint>,
    val heatmapDays: List<AnalysisIncomeExpenseBucket>,
    val incomeExpenseBuckets: List<AnalysisIncomeExpenseBucket>,
    val totals: AnalysisPeriodTotals,
    /** Days the current net worth would cover at the scope's average daily expense; null when it
     *  can't be calculated reliably (no expense observed over the scope) — never a fake zero. */
    val daysOfBuffer: Long?,
)

/** Planning/resilience data for the Fix vs Variable tab. */
data class FixVariableData(
    val totals: AnalysisPeriodTotals,
    val fixedCategories: List<AnalysisCategoryTotal>,
    val variableCategories: List<AnalysisCategoryTotal>,
    val recurringCostSummary: RecurringCostSummary,
    val oldestRecurringItems: List<RecurringVeteranItem>,
) {
    val fixedExpenseCents: Long get() = fixedCategories.sumOf { it.expenseCents }
    val variableExpenseCents: Long get() = variableCategories.sumOf { it.expenseCents }

    /**
     * Fixed costs as a share of income, in basis points (scale 10000 = 100.00%), matching
     * [AnalysisPeriodTotals.savingsRateBasisPoints]'s convention. Null when income is unavailable
     * — the caller must never show a misleading 0%.
     */
    val fixedRatioOfIncomeBasisPoints: Long?
        get() = if (totals.actualIncomeCents > 0) {
            fixedExpenseCents * 10_000L / totals.actualIncomeCents
        } else {
            null
        }
}

data class AnalysisUiState(
    val scope: AnalysisScope = AnalysisScope.MONTH,
    val valueMode: AnalysisValueMode = AnalysisValueMode.TOTALS,
    val natureFilter: AnalysisNatureFilter = AnalysisNatureFilter.ALL,
    val oneTimeMode: AnalysisOneTimeMode = AnalysisOneTimeMode.INCLUDE,
    val month: YearMonth = YearMonth.now(),
    val year: Int = LocalDate.now().year,
    val customFrom: String = "",
    val customTo: String = "",
    val comparisonMonth: YearMonth = month.minusMonths(1),
    val comparisonYear: Int = year - 1,
    val comparisonCustomFrom: String = "",
    val comparisonCustomTo: String = "",
    val comparisonTouched: Boolean = false,
    val groupTripsAsBlocks: Boolean = true,
    val selectedTab: AnalysisTab = AnalysisTab.RESUM,
    val currentRange: AnalysisPeriodRange? = null,
    val currentAverageDivisor: Long = 1,
    val filterAccountId: String? = null,
    val filterAccountName: String? = null,
    val filterCategoryId: String? = null,
    val filterCategoryName: String? = null,
    val accountOptions: List<AccountSummary> = emptyList(),
    val categoryOptions: List<CategoryRecord> = emptyList(),
    val resum: ResumData? = null,
    val categories: CategoriesData? = null,
    val comparativa: ComparativaData? = null,
    val historic: HistoricData? = null,
    val fixVariable: FixVariableData? = null,
    val loadingTabs: Set<AnalysisTab> = emptySet(),
    val errorMessage: String? = null,
    @StringRes val customErrorRes: Int? = null,
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

internal fun AnalysisTab.isLoaded(state: AnalysisUiState): Boolean =
    when (this) {
        AnalysisTab.RESUM -> state.resum != null
        AnalysisTab.CATEGORIES -> state.categories != null
        AnalysisTab.COMPARATIVA -> state.comparativa != null
        AnalysisTab.HISTORIC -> state.historic != null
        AnalysisTab.FIX_VARIABLE -> state.fixVariable != null
    }

private fun AnalysisUiState.clearedTabs(): AnalysisUiState =
    copy(resum = null, categories = null, comparativa = null, historic = null, fixVariable = null)

private fun signatureOf(s: AnalysisUiState, range: AnalysisPeriodRange): String =
    listOf(
        s.scope,
        range.fromDate,
        range.toDateExclusive,
        s.comparisonMonth,
        s.comparisonYear,
        s.comparisonCustomFrom,
        s.comparisonCustomTo,
        s.oneTimeMode,
        s.natureFilter,
        s.groupTripsAsBlocks,
        s.filterAccountId,
        s.filterCategoryId,
    ).joinToString(separator = "|")

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
    val nextDueDate: String,
)

/** An active fixed-cost recurring commitment, for the "oldest recurring" section. */
data class RecurringVeteranItem(
    val templateId: String,
    val label: String,
    val categoryName: String?,
    val monthlyAmountCents: Long,
    val activeSince: LocalDate?,
    val movementCount: Long,
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

/**
 * Resolves the user-selected comparison period. The bucket mirrors [currentRange] so both periods
 * are sampled at the same granularity (e.g. both daily for a month scope) — this keeps the
 * comparative cumulative chart index-aligned. ALL_TIME has no comparison window.
 */
internal fun comparisonAnalysisRange(
    scope: AnalysisScope,
    comparisonMonth: YearMonth,
    comparisonYear: Int,
    comparisonCustomFrom: String,
    comparisonCustomTo: String,
    currentRange: AnalysisPeriodRange,
): AnalysisRangeValidation =
    when (scope) {
        AnalysisScope.MONTH -> AnalysisRangeValidation(
            range = AnalysisPeriodRange(
                fromDate = comparisonMonth.atDay(1),
                toDateExclusive = comparisonMonth.plusMonths(1).atDay(1),
                bucket = currentRange.bucket,
            ),
        )
        AnalysisScope.YEAR -> AnalysisRangeValidation(
            range = AnalysisPeriodRange(
                fromDate = LocalDate.of(comparisonYear, 1, 1),
                toDateExclusive = LocalDate.of(comparisonYear + 1, 1, 1),
                bucket = currentRange.bucket,
            ),
        )
        AnalysisScope.CUSTOM -> {
            val parsed = resolveCustomRange(comparisonCustomFrom, comparisonCustomTo)
            if (parsed.range != null) {
                AnalysisRangeValidation(range = parsed.range.copy(bucket = currentRange.bucket))
            } else {
                parsed
            }
        }
        AnalysisScope.ALL_TIME -> AnalysisRangeValidation(range = null)
    }

private fun resolveCustomRange(
    customFrom: String,
    customTo: String,
): AnalysisRangeValidation {
    val from = parseDate(customFrom)
    val toInclusive = parseDate(customTo)
    if (from == null || toInclusive == null) {
        return AnalysisRangeValidation(range = null, errorRes = R.string.movement_filter_date_invalid)
    }
    if (from > toInclusive) {
        return AnalysisRangeValidation(range = null, errorRes = R.string.movement_filter_date_order_invalid)
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

/** A daily heatmap is only meaningful for a bounded span; cap it at roughly one year. */
internal fun AnalysisPeriodRange.isHeatmapBounded(): Boolean {
    val days = ChronoUnit.DAYS.between(fromDate, toDateExclusive)
    return days in 1..366
}

private fun parseDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value.trim()) }.getOrNull()

private fun AnalysisNatureFilter.toQueryNature(): AnalysisCategoryNature? =
    when (this) {
        AnalysisNatureFilter.ALL -> null
        AnalysisNatureFilter.FIXED -> AnalysisCategoryNature.FIXED
        AnalysisNatureFilter.VARIABLE -> AnalysisCategoryNature.VARIABLE
    }

/** Period count used to turn totals into per-period averages (non-all-time scopes). */
internal fun deterministicDivisor(range: AnalysisPeriodRange): Long =
    when (range.bucket) {
        AnalysisBucket.DAY -> ChronoUnit.DAYS.between(range.fromDate, range.toDateExclusive)
        AnalysisBucket.MONTH -> {
            val start = YearMonth.from(range.fromDate)
            val end = YearMonth.from(range.toDateExclusive.minusDays(1))
            ChronoUnit.MONTHS.between(start, end) + 1
        }
        AnalysisBucket.YEAR -> (range.toDateExclusive.year - range.fromDate.year).toLong()
    }.coerceAtLeast(1)

/**
 * Days of expense runway the current net worth ([AnalysisPeriodTotals.netWorthCents] — the live
 * global balance, not date-filtered) would cover at the average daily expense observed over the
 * selected historic scope. Null when it can't be calculated reliably (no expense observed over
 * the scope, so there is no meaningful daily rate to divide by) — the caller must never
 * substitute a misleading zero for "unknown". ALL_TIME's range bounds are a wide placeholder
 * rather than the real data span, so its day count is approximated from the number of monthly
 * buckets actually returned instead of a literal calendar-day diff. A negative net worth clamps
 * to zero days (no runway) rather than a confusing negative day count.
 */
internal fun calculateDaysOfBuffer(
    scope: AnalysisScope,
    range: AnalysisPeriodRange,
    totals: AnalysisPeriodTotals,
    bucketCount: Int,
): Long? {
    val daysInRange = if (scope == AnalysisScope.ALL_TIME) {
        (bucketCount * 30L).coerceAtLeast(1L)
    } else {
        ChronoUnit.DAYS.between(range.fromDate, range.toDateExclusive).coerceAtLeast(1L)
    }
    val avgDailyExpenseCents = totals.actualExpenseCents / daysInRange
    if (avgDailyExpenseCents <= 0L) return null
    return (totals.netWorthCents / avgDailyExpenseCents).coerceAtLeast(0L)
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
                nextDueDate = template.nextDueDate,
            )
        }
        .sortedByDescending { it.monthlyExpenseCents }

    return RecurringCostSummary(
        monthlyExpenseCents = items.sumOf { it.monthlyExpenseCents },
        items = items,
    )
}

/**
 * Active fixed-cost recurring commitments sorted oldest-first by [TemplateSummary.createdAt] (a
 * UTC instant, per AGENTS.md invariant #5) — "which recurring commitments are oldest" for the
 * Fix/Var tab. Uses the same ACTIVE/EXPENSE/fixed-amount eligibility as [buildRecurringCostSummary].
 * [movementCountsByTemplate] (keyed by template id, e.g. [MovementRepository.countsByTemplate])
 * supplies how many movements each template has generated so far; missing entries default to 0.
 */
internal fun buildOldestRecurringItems(
    templates: List<TemplateSummary>,
    movementCountsByTemplate: Map<String, Long> = emptyMap(),
    limit: Int = MAX_OLDEST_RECURRING_ITEMS,
): List<RecurringVeteranItem> =
    templates
        .mapNotNull { template ->
            val monthlyCents = template.monthlyRecurringExpenseCents() ?: return@mapNotNull null
            val activeSince = runCatching {
                Instant.parse(template.createdAt).atZone(ZoneOffset.UTC).toLocalDate()
            }.getOrNull()
            RecurringVeteranItem(
                templateId = template.id,
                label = template.name?.takeIf { it.isNotBlank() }
                    ?: template.payee?.takeIf { it.isNotBlank() }
                    ?: template.categoryName.orEmpty(),
                categoryName = template.categoryName,
                monthlyAmountCents = monthlyCents,
                activeSince = activeSince,
                movementCount = movementCountsByTemplate[template.id] ?: 0L,
            )
        }
        .sortedWith(compareBy(nullsLast()) { it.activeSince })
        .take(limit)

const val MAX_OLDEST_RECURRING_ITEMS = 5

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
