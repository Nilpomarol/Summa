package com.gestorfinances.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisPeriodTotals
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.BudgetProjection
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.TemplateRepository
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

class DashboardViewModel(
    private val analysisRepository: AnalysisRepository,
    private val accountRepository: AccountRepository,
    private val movementRepository: MovementRepository,
    private val tripRepository: TripRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val templateRepository: TemplateRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refresh()
    }

    /** Picks which account's balance leads the summary. Presentation only — no data is reloaded. */
    fun onAccountSelected(accountId: String) {
        _state.value = _state.value.copy(selectedAccountId = accountId)
    }

    fun refresh() {
        viewModelScope.launch {
            val previous = _state.value
            _state.value = previous.copy(isLoading = true, errorMessage = null)
            val result = withContext(ioDispatcher) {
                runCatching {
                    val today = todayProvider()
                    val month = YearMonth.from(today)
                    val fromDate = month.atDay(1)
                    val toDate = month.plusMonths(1).atDay(1)
                    val categoryRecords = categoryRepository.listActive()
                    val budgetProjections = budgetRepository.currentMonthProjections(
                        today = today,
                        templates = templateRepository.listActive(),
                        categoryParentById = categoryRecords.associate { it.id to it.parentId },
                    )
                    DashboardLoadedData(
                        month = month,
                        totals = analysisRepository.periodTotals(
                            fromDate = fromDate.toString(),
                            toDate = toDate.toString(),
                        ),
                        categories = analysisRepository.actualByCategory(
                            fromDate = fromDate.toString(),
                            toDate = toDate.toString(),
                        ).rollUpToParents(categoryRecords.associateBy { it.id }),
                        accounts = accountRepository.listActive(),
                        latestMovements = movementRepository.listActive().take(LATEST_MOVEMENTS),
                        activeTrip = tripRepository.activeToday(today.toString()),
                        overallBudgetProjection = budgetProjections.firstOrNull {
                            it.evaluation.budget.scope == BudgetScope.OVERALL_MONTH
                        },
                        budgetExceptions = budgetProjections
                            .filter { projection ->
                                projection.evaluation.budget.scope == BudgetScope.CATEGORY &&
                                    projection.status != com.gestorfinances.app.data.repository.BudgetForecastStatus.ON_TRACK
                            }
                            .sortedBy { it.remainingForecastCents }
                            .take(DASHBOARD_BUDGET_EXCEPTIONS),
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    previous.copy(
                        month = it.month,
                        totals = it.totals,
                        categories = it.categories,
                        accounts = it.accounts,
                        latestMovements = it.latestMovements,
                        activeTrip = it.activeTrip,
                        overallBudgetProjection = it.overallBudgetProjection,
                        budgetExceptions = it.budgetExceptions,
                        isLoading = false,
                        errorMessage = null,
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

    class Factory(
        private val analysisRepository: AnalysisRepository,
        private val accountRepository: AccountRepository,
        private val movementRepository: MovementRepository,
        private val tripRepository: TripRepository,
        private val categoryRepository: CategoryRepository,
        private val budgetRepository: BudgetRepository,
        private val templateRepository: TemplateRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                return DashboardViewModel(
                    analysisRepository = analysisRepository,
                    accountRepository = accountRepository,
                    movementRepository = movementRepository,
                    tripRepository = tripRepository,
                    categoryRepository = categoryRepository,
                    budgetRepository = budgetRepository,
                    templateRepository = templateRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class DashboardUiState(
    val month: YearMonth = YearMonth.now(),
    val totals: AnalysisPeriodTotals = AnalysisPeriodTotals(
        netWorthCents = 0,
        actualIncomeCents = 0,
        actualExpenseCents = 0,
        netActualCents = 0,
        accountFlowCents = 0,
        savingsRateBasisPoints = 0,
    ),
    val categories: List<AnalysisCategoryTotal> = emptyList(),
    val accounts: List<AccountSummary> = emptyList(),
    val selectedAccountId: String? = null,
    val latestMovements: List<MovementSummary> = emptyList(),
    val activeTrip: TripSummary? = null,
    val overallBudgetProjection: BudgetProjection? = null,
    val budgetExceptions: List<BudgetProjection> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    /**
     * Account whose current balance leads the summary: the one the user picked, otherwise the
     * default account, otherwise the first active one.
     */
    val mainAccount: AccountSummary?
        get() = accounts.firstOrNull { it.id == selectedAccountId }
            ?: accounts.firstOrNull { it.isDefault }
            ?: accounts.firstOrNull()

    /**
     * First active account already below its own low-balance threshold, if any. Uses the same
     * condition as the low-balance notification rule; the dashboard only surfaces it.
     */
    val lowBalanceAccount: AccountSummary?
        get() = accounts.firstOrNull { account ->
            val threshold = account.lowBalanceThresholdCents ?: return@firstOrNull false
            account.currentBalanceCents < threshold
        }
}

private const val LATEST_MOVEMENTS = 5
private const val DASHBOARD_BUDGET_EXCEPTIONS = 3

private data class DashboardLoadedData(
    val month: YearMonth,
    val totals: AnalysisPeriodTotals,
    val categories: List<AnalysisCategoryTotal>,
    val accounts: List<AccountSummary>,
    val latestMovements: List<MovementSummary>,
    val activeTrip: TripSummary?,
    val overallBudgetProjection: BudgetProjection?,
    val budgetExceptions: List<BudgetProjection>,
)
