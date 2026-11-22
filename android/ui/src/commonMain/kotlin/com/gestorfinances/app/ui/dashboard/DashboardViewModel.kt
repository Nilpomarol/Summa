package com.gestorfinances.app.ui.dashboard

import com.gestorfinances.app.data.repository.withoutRefundRows
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.BudgetMonthPlan
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.BudgetSummary
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.domain.rules.PlanPart
import com.gestorfinances.app.domain.rules.PlanStatus
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
    private val personRepository: PersonRepository,
    /** The account the user last put in the hero, remembered across app starts. */
    loadSelectedAccountId: () -> String? = { null },
    private val saveSelectedAccountId: (String) -> Unit = {},
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState(selectedAccountId = loadSelectedAccountId()))
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refresh()
    }

    /** Picks which account leads the summary, remembers it, and loads that account's month. */
    fun onAccountSelected(accountId: String) {
        saveSelectedAccountId(accountId)
        _state.value = _state.value.copy(selectedAccountId = accountId)
        viewModelScope.launch {
            val month = _state.value.month
            val flow = withContext(ioDispatcher) { runCatching { accountMonthFlow(accountId, month) } }
            flow.onSuccess { loaded ->
                if (_state.value.selectedAccountId == accountId) _state.value = _state.value.copy(accountMonthFlow = loaded)
            }
        }
    }

    /** Canonical actual income and expense (the owner's share) of movements on one account this month. */
    private fun accountMonthFlow(accountId: String, month: YearMonth): AccountMonthFlow {
        val totals = analysisRepository.periodTotals(
            fromDate = month.atDay(1).toString(),
            toDate = month.plusMonths(1).atDay(1).toString(),
            accountId = accountId,
        )
        return AccountMonthFlow(accountId, inCents = totals.actualIncomeCents, outCents = totals.actualExpenseCents)
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
                    val templates = templateRepository.listActive()
                    val plan = budgetRepository.monthPlan(
                        month = month,
                        today = today,
                        templates = templates,
                        categoryParentById = categoryRecords.associate { it.id to it.parentId },
                    )
                    val totals = analysisRepository.periodTotals(
                        fromDate = fromDate.toString(),
                        toDate = toDate.toString(),
                    )
                    val accounts = accountRepository.listActive()
                    DashboardLoadedData(
                        today = today,
                        month = month,
                        netWorthCents = totals.netWorthCents,
                        monthExpenseCents = totals.actualExpenseCents,
                        accounts = accounts,
                        accountMonthFlow = mainAccountOf(accounts, previous.selectedAccountId)
                            ?.let { accountMonthFlow(it.id, month) },
                        openDebts = personRepository.listActive()
                            .filter { it.balanceCents != 0L }
                            .sortedByDescending { kotlin.math.abs(it.balanceCents) },
                        latestMovements = movementRepository.listActive().withoutRefundRows().take(LATEST_MOVEMENTS),
                        activeTrip = tripRepository.activeToday(today.toString()),
                        monthPlan = plan.takeIf { it.total != null },
                        planWarnings = planWarnings(plan),
                        upcomingRecurring = upcomingRecurring(templates, today),
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        today = it.today,
                        month = it.month,
                        netWorthCents = it.netWorthCents,
                        monthExpenseCents = it.monthExpenseCents,
                        hasLoaded = true,
                        accounts = it.accounts,
                        accountMonthFlow = it.accountMonthFlow,
                        openDebts = it.openDebts,
                        latestMovements = it.latestMovements,
                        activeTrip = it.activeTrip,
                        monthPlan = it.monthPlan,
                        planWarnings = it.planWarnings,
                        upcomingRecurring = it.upcomingRecurring,
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
}

data class DashboardUiState(
    val today: LocalDate = LocalDate.now(),
    val month: YearMonth = YearMonth.now(),
    val netWorthCents: Long = 0,
    val monthExpenseCents: Long = 0,
    val hasLoaded: Boolean = false,
    val accounts: List<AccountSummary> = emptyList(),
    val selectedAccountId: String? = null,
    /** This month's movement through [mainAccount]; null until loaded or while it is switching. */
    val accountMonthFlow: AccountMonthFlow? = null,
    /** People with an open balance either way, largest first. */
    val openDebts: List<PersonSummary> = emptyList(),
    val latestMovements: List<MovementSummary> = emptyList(),
    val activeTrip: TripSummary? = null,
    /** This month's plan, when it has a monthly total. */
    val monthPlan: BudgetMonthPlan? = null,
    /** Partides (and the rest) over their plan or heading over it, worst first. */
    val planWarnings: List<PlanWarning> = emptyList(),
    /** Recurring movements overdue or due within the next two weeks, soonest first. */
    val upcomingRecurring: List<TemplateSummary> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    /**
     * Account whose current balance leads the summary: the one the user picked, otherwise the
     * default account, otherwise the first active one.
     */
    val mainAccount: AccountSummary?
        get() = mainAccountOf(accounts, selectedAccountId)

    /** The month flow, only once it belongs to the account the hero shows. */
    val mainAccountMonthFlow: AccountMonthFlow?
        get() = accountMonthFlow?.takeIf { it.accountId == mainAccount?.id }

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

data class AccountMonthFlow(val accountId: String, val inCents: Long, val outCents: Long)

/** A part of the monthly plan worth a look: a partida, or the rest when [budget] is null. */
data class PlanWarning(val budget: BudgetSummary?, val part: PlanPart) {
    /** How far over the plan it is, or is heading. */
    val excessCents: Long
        get() = (if (part.status == PlanStatus.OVER) part.actualCents else part.forecastCents) - (part.plannedCents ?: 0L)
}

private fun planWarnings(plan: BudgetMonthPlan): List<PlanWarning> =
    (plan.compartments.map { PlanWarning(it, plan.plan.compartments.getValue(it.id)) } + PlanWarning(null, plan.plan.others))
        .filter { it.part.status == PlanStatus.OVER || it.part.status == PlanStatus.MAY_EXCEED }
        .sortedWith(compareBy<PlanWarning> { it.part.status != PlanStatus.OVER }.thenByDescending { it.excessCents })
        .take(DASHBOARD_PLAN_WARNINGS)

private fun upcomingRecurring(templates: List<TemplateSummary>, today: LocalDate): List<TemplateSummary> {
    val horizon = today.plusDays(UPCOMING_RECURRING_DAYS).toString()
    return templates
        .filter { it.status == TemplateStatus.ACTIVE && it.nextDueDate <= horizon }
        .sortedBy { it.nextDueDate }
        .take(UPCOMING_RECURRING)
}

private const val UPCOMING_RECURRING_DAYS = 14L
private const val UPCOMING_RECURRING = 5

private fun mainAccountOf(accounts: List<AccountSummary>, selectedAccountId: String?): AccountSummary? =
    accounts.firstOrNull { it.id == selectedAccountId }
        ?: accounts.firstOrNull { it.isDefault }
        ?: accounts.firstOrNull()

private const val LATEST_MOVEMENTS = 5
private const val DASHBOARD_PLAN_WARNINGS = 3

private data class DashboardLoadedData(
    val today: LocalDate,
    val month: YearMonth,
    val netWorthCents: Long,
    val monthExpenseCents: Long,
    val accounts: List<AccountSummary>,
    val accountMonthFlow: AccountMonthFlow?,
    val openDebts: List<PersonSummary>,
    val latestMovements: List<MovementSummary>,
    val activeTrip: TripSummary?,
    val monthPlan: BudgetMonthPlan?,
    val planWarnings: List<PlanWarning>,
    val upcomingRecurring: List<TemplateSummary>,
)
