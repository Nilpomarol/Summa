package com.gestorfinances.app.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AnalysisBucket
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisPeriodTotals
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.AnalysisSpendingByKind
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

/**
 * The Anàlisi page: a month or a year of the owner's spending, measured against what is usual
 * (the typical month, or the year before up to the same day), where it went, how it came about,
 * how it has moved over the last twelve months, and how net worth has gone. Every figure is a
 * canonical one; the page only averages and lines them up.
 */
class AnalysisViewModel(
    private val analysisRepository: AnalysisRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val initialToday = todayProvider()
    private val _state = MutableStateFlow(
        AnalysisUiState(month = YearMonth.from(initialToday), year = initialToday.year, today = initialToday),
    )
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    private var loadedSignature: String? = null
    private var optionsLoaded = false

    /** The movement data revision the page was last shown for, part of the loaded signature. */
    private var dataVersion = 0L

    fun onScreenShown(dataVersion: Long = this.dataVersion) {
        if (dataVersion != this.dataVersion) {
            this.dataVersion = dataVersion
            // A write can add an activity month or an account to choose from.
            optionsLoaded = false
        }
        loadOptions()
        refresh()
    }

    /** Opens the page on one category's sheet, as its category page links here. */
    fun openCategoryOnLoad(categoryId: String) {
        _state.value = _state.value.copy(pendingCategoryId = categoryId)
    }

    private fun loadOptions() {
        if (optionsLoaded) return
        optionsLoaded = true
        viewModelScope.launch {
            val (accounts, categories, months) = withContext(ioDispatcher) {
                Triple(accountRepository.listActive(), categoryRepository.listActive(), analysisRepository.activityMonths())
            }
            _state.value = _state.value.let { state ->
                val month = state.month.takeIf { it in months } ?: months.firstOrNull() ?: state.month
                val years = months.map { it.year }.distinct()
                state.copy(
                    month = month,
                    year = state.year.takeIf { it in years } ?: month.year,
                    accountOptions = accounts,
                    categories = categories,
                    activityMonths = months,
                )
            }
            refresh()
        }
    }

    fun refresh() {
        val snapshot = _state.value.copy(today = todayProvider())
        val range = resolveAnalysisRange(snapshot.scope, snapshot.month, snapshot.year)
        val signature = listOf(dataVersion, snapshot.scope, range.fromDate, snapshot.filterAccountId).joinToString("|")
        val changed = signature != loadedSignature
        loadedSignature = signature
        _state.value = snapshot.copy(page = if (changed) null else snapshot.page, errorMessage = null)
        if (!changed && snapshot.page != null) return

        viewModelScope.launch {
            val result = withContext(ioDispatcher) { runCatching { loadPage(snapshot, range) } }
            if (loadedSignature != signature) return@launch
            result.fold(
                onSuccess = { page ->
                    _state.value = _state.value.copy(page = page)
                    _state.value.pendingCategoryId?.let { id ->
                        _state.value = _state.value.copy(pendingCategoryId = null)
                        onCategoryClicked(id)
                    }
                },
                onFailure = { error -> _state.value = _state.value.copy(errorMessage = error.message ?: error.javaClass.simpleName) },
            )
        }
    }

    fun onScopeSelected(scope: AnalysisScope) {
        _state.value = _state.value.copy(scope = scope)
        refresh()
    }

    fun onMonthSelected(month: YearMonth) {
        _state.value = _state.value.copy(month = month, year = month.year, scope = AnalysisScope.MONTH)
        refresh()
    }

    fun onYearSelected(year: Int) {
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

    /** Opens a category's sheet: its subcategories this period and its last twelve months. */
    fun onCategoryClicked(categoryId: String) {
        val state = _state.value
        val range = resolveAnalysisRange(state.scope, state.month, state.year)
        _state.value = state.copy(categoryDetail = CategoryDetailState(categoryId = categoryId))
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val children = state.categories.filter { it.parentId == categoryId }.map { it.id }.toSet()
                    val subcategories = if (children.isEmpty()) {
                        emptyList()
                    } else {
                        analysisRepository.actualByCategory(
                            fromDate = range.fromDate.toString(),
                            toDate = range.toDateExclusive.toString(),
                            accountId = state.filterAccountId,
                        ).filter { it.categoryId in children || it.categoryId == categoryId }
                            .filter { it.expenseCents != 0L }
                            .sortedByDescending { it.expenseCents }
                    }
                    val months = trendMonths(state)
                    val byMonth = analysisRepository.incomeVsExpense(
                        fromDate = months.first().atDay(1).toString(),
                        toDate = months.last().plusMonths(1).atDay(1).toString(),
                        bucket = AnalysisBucket.MONTH,
                        accountId = state.filterAccountId,
                        categoryId = categoryId,
                    ).associate { it.bucket to it.expenseCents }
                    subcategories to months.map { MonthAmount(it, byMonth[it.toString()] ?: 0L) }
                }
            }
            val detail = _state.value.categoryDetail?.takeIf { it.categoryId == categoryId } ?: return@launch
            _state.value = _state.value.copy(
                categoryDetail = result.fold(
                    onSuccess = { (subcategories, months) -> detail.copy(subcategories = subcategories, months = months, isLoading = false) },
                    onFailure = { detail.copy(isLoading = false, errorMessage = it.message ?: it.javaClass.simpleName) },
                ),
            )
        }
    }

    fun onCategoryDetailDismissed() {
        _state.value = _state.value.copy(categoryDetail = null)
    }

    private fun loadPage(s: AnalysisUiState, range: AnalysisPeriodRange): AnalysisPage {
        val from = range.fromDate.toString()
        val to = range.toDateExclusive.toString()
        val accountId = s.filterAccountId
        val categoriesById = categoryRepository.listActive().associateBy { it.id }
        val totals = analysisRepository.periodTotals(fromDate = from, toDate = to, accountId = accountId)
        val breakdown = analysisRepository.actualBreakdown(fromDate = from, toDate = to, groupTrips = true, accountId = accountId)
            .rollUpToParents(categoriesById)
        val categories = breakdown.filter { it.expenseCents > 0L }.sortedByDescending { it.expenseCents }

        // What is usual: the typical month (the average of the months before that have any activity,
        // up to three), or the year before up to the same day.
        val baseline = baselineOf(s, range)
        val baselineDivisor = baseline?.months?.coerceAtLeast(1) ?: 1
        val baselineTotals = baseline?.let { analysisRepository.periodTotals(it.range.fromDate.toString(), it.range.toDateExclusive.toString(), accountId = accountId) }
        val usualByCategory = baseline?.let { usual ->
            analysisRepository.actualByCategory(usual.range.fromDate.toString(), usual.range.toDateExclusive.toString(), accountId = accountId)
                .rollUpToParents(categoriesById)
                .associate { it.rowKey() to it.expenseCents / baselineDivisor }
        }.orEmpty()

        val pace = if (s.scope == AnalysisScope.MONTH) pace(s, range, baseline) else null
        val months = trendMonths(s)
        val trendBuckets = analysisRepository.incomeVsExpense(
            fromDate = months.first().atDay(1).toString(),
            toDate = months.last().plusMonths(1).atDay(1).toString(),
            bucket = AnalysisBucket.MONTH,
            accountId = accountId,
        ).associateBy { it.bucket }
        val trend = months.map { month ->
            val bucket = trendBuckets[month.toString()]
            TrendMonth(month = month, expenseCents = bucket?.expenseCents ?: 0L, incomeCents = bucket?.incomeCents ?: 0L)
        }
        // Net worth at each month's end, or today for the month still running; none ahead of today.
        val netWorth = months.filter { !it.atDay(1).isAfter(s.today) }.map { month ->
            val asOf = minOf(month.atEndOfMonth(), s.today)
            MonthAmount(month, analysisRepository.netWorthAt(asOf.toString(), accountId))
        }
        // By this point of the period: a running month against the typical month up to the same day.
        val usual = pace?.typical?.getOrNull(pace.current.lastIndex)
            ?: baselineTotals?.actualExpenseCents?.div(baselineDivisor)
        return AnalysisPage(
            totals = totals,
            usualExpenseCents = usual,
            periodIsRunning = !s.today.isBefore(range.fromDate) && s.today.isBefore(range.toDateExclusive),
            baselineKind = baseline?.kind,
            categories = categories,
            incomeCategories = breakdown.filter { it.incomeCents > 0L }.sortedByDescending { it.incomeCents },
            usualByCategory = usualByCategory,
            byKind = analysisRepository.spendingByKind(fromDate = from, toDate = to, accountId = accountId),
            pace = pace,
            trend = trend,
            netWorth = netWorth,
        )
    }

    private fun baselineOf(s: AnalysisUiState, range: AnalysisPeriodRange): Baseline? =
        when (s.scope) {
            AnalysisScope.MONTH -> {
                val before = s.activityMonths.filter { it < s.month }.sortedDescending().take(TYPICAL_MONTHS)
                if (before.isEmpty()) {
                    null
                } else {
                    Baseline(
                        range = AnalysisPeriodRange(before.min().atDay(1), s.month.atDay(1), AnalysisBucket.DAY),
                        months = before.size,
                        kind = BaselineKind.TYPICAL_MONTH,
                    )
                }
            }
            AnalysisScope.YEAR -> previousAnalysisRange(range, s.scope)
                ?.let { comparablePreviousRange(range, it, s.scope, s.today) }
                ?.takeIf { previous -> s.activityMonths.any { it.year == previous.fromDate.year } }
                ?.let { Baseline(range = it, months = 1, kind = BaselineKind.PREVIOUS_YEAR) }
        }

    /** Spending day by day through the month (up to today while it runs), and the typical month's. */
    private fun pace(s: AnalysisUiState, range: AnalysisPeriodRange, baseline: Baseline?): Pace {
        val length = s.month.lengthOfMonth()
        val lastDay = if (YearMonth.from(s.today) == s.month) s.today.dayOfMonth else length
        val daily = analysisRepository.incomeVsExpense(
            fromDate = range.fromDate.toString(),
            toDate = range.toDateExclusive.toString(),
            bucket = AnalysisBucket.DAY,
            accountId = s.filterAccountId,
        ).associate { it.bucket to it.expenseCents }
        val current = (1..lastDay).map { day -> daily[s.month.atDay(day).toString()] ?: 0L }.runningSum()
        val typical = baseline?.let { usual ->
            // The typical month's day-by-day: each earlier month's spending on its day, averaged,
            // its last days folded into this month's last one when it is shorter.
            val byDay = LongArray(length)
            analysisRepository.incomeVsExpense(
                fromDate = usual.range.fromDate.toString(),
                toDate = usual.range.toDateExclusive.toString(),
                bucket = AnalysisBucket.DAY,
                accountId = s.filterAccountId,
            ).forEach { bucket ->
                val day = runCatching { LocalDate.parse(bucket.bucket).dayOfMonth }.getOrNull() ?: return@forEach
                byDay[day.coerceAtMost(length) - 1] += bucket.expenseCents
            }
            byDay.map { it / usual.months }.runningSum()
        }
        return Pace(current = current, typical = typical, daysInMonth = length)
    }

    /** The twelve months the trend covers: the year's, or the twelve up to the chosen month. */
    private fun trendMonths(s: AnalysisUiState): List<YearMonth> =
        when (s.scope) {
            AnalysisScope.MONTH -> (TREND_MONTHS - 1 downTo 0).map { s.month.minusMonths(it.toLong()) }
            AnalysisScope.YEAR -> (1..12).map { YearMonth.of(s.year, it) }
        }
}

private fun List<Long>.runningSum(): List<Long> {
    var total = 0L
    return map { total += it; total }
}

private const val TYPICAL_MONTHS = 3
private const val TREND_MONTHS = 12

enum class AnalysisScope { MONTH, YEAR }

enum class BaselineKind { TYPICAL_MONTH, PREVIOUS_YEAR }

private data class Baseline(val range: AnalysisPeriodRange, val months: Int, val kind: BaselineKind)

data class AnalysisPeriodRange(
    val fromDate: LocalDate,
    val toDateExclusive: LocalDate,
    val bucket: AnalysisBucket,
)

data class MonthAmount(val month: YearMonth, val cents: Long)

data class TrendMonth(val month: YearMonth, val expenseCents: Long, val incomeCents: Long)

/** Spending building up through the month, and the typical month's, both as running totals by day. */
data class Pace(val current: List<Long>, val typical: List<Long>?, val daysInMonth: Int)

data class AnalysisPage(
    val totals: AnalysisPeriodTotals,
    /** What the period usually comes to by this point: the typical month (to the same day while it
     * runs), or the year before to the same day. */
    val usualExpenseCents: Long?,
    /** Today falls in the period, so its figures are still growing. */
    val periodIsRunning: Boolean,
    val baselineKind: BaselineKind?,
    val categories: List<AnalysisCategoryTotal>,
    /** Where the period's income came from, largest first. */
    val incomeCategories: List<AnalysisCategoryTotal> = emptyList(),
    /** Each row's usual spending over a whole period, by [rowKey]. */
    val usualByCategory: Map<String, Long>,
    val byKind: AnalysisSpendingByKind,
    val pace: Pace?,
    val trend: List<TrendMonth>,
    val netWorth: List<MonthAmount>,
)

data class CategoryDetailState(
    val categoryId: String,
    val subcategories: List<AnalysisCategoryTotal> = emptyList(),
    val months: List<MonthAmount> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

data class AnalysisUiState(
    val scope: AnalysisScope = AnalysisScope.MONTH,
    val month: YearMonth = YearMonth.now(),
    val year: Int = LocalDate.now().year,
    val today: LocalDate = LocalDate.now(),
    val filterAccountId: String? = null,
    val filterAccountName: String? = null,
    val accountOptions: List<AccountSummary> = emptyList(),
    val categories: List<CategoryRecord> = emptyList(),
    val activityMonths: List<YearMonth> = emptyList(),
    val page: AnalysisPage? = null,
    val categoryDetail: CategoryDetailState? = null,
    val pendingCategoryId: String? = null,
    val errorMessage: String? = null,
)

fun resolveAnalysisRange(
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
    }

fun previousAnalysisRange(
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
    }

/** Limits a current month/year comparison to the equivalent day in the preceding period. */
fun comparablePreviousRange(
    currentRange: AnalysisPeriodRange,
    previousRange: AnalysisPeriodRange,
    scope: AnalysisScope,
    today: LocalDate,
): AnalysisPeriodRange {
    if (today < currentRange.fromDate || today >= currentRange.toDateExclusive) return previousRange
    val equivalentEnd = when (scope) {
        AnalysisScope.MONTH -> previousRange.fromDate.plusDays(today.dayOfMonth.toLong())
        AnalysisScope.YEAR -> today.minusYears(1).plusDays(1)
    }
    return previousRange.copy(toDateExclusive = minOf(equivalentEnd, previousRange.toDateExclusive))
}
