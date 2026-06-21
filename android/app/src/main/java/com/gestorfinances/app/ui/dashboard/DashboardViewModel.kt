package com.gestorfinances.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisIncomeExpenseBucket
import com.gestorfinances.app.data.repository.AnalysisPeriodTotals
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSummary
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
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val result = withContext(ioDispatcher) {
                runCatching {
                    val month = YearMonth.from(todayProvider())
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
                        ).take(6),
                        dailyFlow = analysisRepository.dailyIncomeVsExpense(
                            fromDate = fromDate.toString(),
                            toDate = toDate.toString(),
                        ),
                        accounts = accountRepository.listActive(),
                        latestMovements = movementRepository.listActive().take(5),
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    DashboardUiState(
                        month = it.month,
                        totals = it.totals,
                        categories = it.categories,
                        dailyFlow = it.dailyFlow,
                        accounts = it.accounts,
                        latestMovements = it.latestMovements,
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                return DashboardViewModel(
                    analysisRepository = analysisRepository,
                    accountRepository = accountRepository,
                    movementRepository = movementRepository,
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
    val dailyFlow: List<AnalysisIncomeExpenseBucket> = emptyList(),
    val accounts: List<AccountSummary> = emptyList(),
    val latestMovements: List<MovementSummary> = emptyList(),
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
    val dailyFlow: List<AnalysisIncomeExpenseBucket>,
    val accounts: List<AccountSummary>,
    val latestMovements: List<MovementSummary>,
)
