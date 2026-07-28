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
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripSummary
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

enum class CategoryDisplayMode { EXPENSES, INCOME }

class DashboardViewModel(
    private val analysisRepository: AnalysisRepository,
    private val accountRepository: AccountRepository,
    private val movementRepository: MovementRepository,
    private val tripRepository: TripRepository,
    private val categoryRepository: CategoryRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refresh()
    }

    fun onCategoryModeChanged(mode: CategoryDisplayMode) {
        _state.value = _state.value.copy(categoryMode = mode)
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val result = withContext(ioDispatcher) {
                runCatching {
                    val today = todayProvider()
                    val month = YearMonth.from(today)
                    val fromDate = month.atDay(1)
                    val toDate = month.plusMonths(1).atDay(1)
                    DashboardLoadedData(
                        month = month,
                        totals = analysisRepository.periodTotals(
                            fromDate = fromDate.toString(),
                            toDate = toDate.toString(),
                        ),
                        categories = analysisRepository.actualByCategory(
                            fromDate = fromDate.toString(),
                            toDate = toDate.toString(),
                        ).rollUpToParents(categoryRepository.listActive().associateBy { it.id }).take(6),
                        accounts = accountRepository.listActive(),
                        latestMovements = movementRepository.listActive().take(5),
                        activeTrip = tripRepository.activeToday(today.toString()),
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    DashboardUiState(
                        month = it.month,
                        totals = it.totals,
                        categories = it.categories,
                        accounts = it.accounts,
                        latestMovements = it.latestMovements,
                        activeTrip = it.activeTrip,
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

    class Factory(
        private val analysisRepository: AnalysisRepository,
        private val accountRepository: AccountRepository,
        private val movementRepository: MovementRepository,
        private val tripRepository: TripRepository,
        private val categoryRepository: CategoryRepository,
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
    val latestMovements: List<MovementSummary> = emptyList(),
    val activeTrip: TripSummary? = null,
    val categoryMode: CategoryDisplayMode = CategoryDisplayMode.EXPENSES,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val hasMonthActivity: Boolean
        get() = totals.actualIncomeCents != 0L || totals.actualExpenseCents != 0L
}

private data class DashboardLoadedData(
    val month: YearMonth,
    val totals: AnalysisPeriodTotals,
    val categories: List<AnalysisCategoryTotal>,
    val accounts: List<AccountSummary>,
    val latestMovements: List<MovementSummary>,
    val activeTrip: TripSummary?,
)
