package com.gestorfinances.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.budget_validation_category_required
import com.gestorfinances.ui.resources.budget_validation_duplicate
import com.gestorfinances.ui.resources.budget_validation_limit_positive
import com.gestorfinances.ui.resources.budget_validation_overlap
import com.gestorfinances.ui.resources.budget_validation_threshold_range
import com.gestorfinances.ui.resources.budget_validation_trip_required
import org.jetbrains.compose.resources.StringResource
import com.gestorfinances.app.data.repository.BudgetDraft
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetPeriod
import com.gestorfinances.app.data.repository.BudgetMonthPlan
import com.gestorfinances.app.data.repository.PlanIncome
import com.gestorfinances.app.data.repository.RecentSpending
import com.gestorfinances.app.data.repository.SpendingHistory
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.BudgetSummary
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.DefaultPlanInclusions
import com.gestorfinances.app.data.repository.DuplicateActiveBudgetException
import com.gestorfinances.app.data.repository.OverlappingCompartmentException
import com.gestorfinances.app.data.repository.supportsExpense
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.notifications.NotificationRefresher
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.common.parseEuroCents
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BudgetsViewModel(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val tripRepository: TripRepository,
    private val templateRepository: TemplateRepository? = null,
    private val analysisRepository: AnalysisRepository? = null,
    private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(BudgetsUiState())
    val state: StateFlow<BudgetsUiState> = _state.asStateFlow()

    /** The budget a category or trip page asked to edit, opened once the budgets have loaded. */
    private var pendingTarget: BudgetTarget? = null

    fun onScreenShown() {
        refresh()
    }

    /**
     * Opens the budget of a category (its partida, else its yearly limit) or of a trip, or a new
     * one for it: what a category or trip page's budget action does, in a sheet over that page.
     */
    fun editBudgetFor(categoryId: String? = null, tripId: String? = null) {
        val target = BudgetTarget(categoryId, tripId)
        if (_state.value.isLoading) {
            pendingTarget = target
            refresh()
        } else {
            _state.value = _state.value.copy(form = formFor(target))
        }
    }

    private fun formFor(target: BudgetTarget): BudgetFormState {
        val budgets = _state.value.evaluations.map { it.budget }
        val existing = if (target.tripId != null) {
            budgets.firstOrNull { it.scope == BudgetScope.TRIP && it.tripId == target.tripId }
        } else {
            budgets.filter { it.scope == BudgetScope.CATEGORY && it.categoryId == target.categoryId }
                .minByOrNull { it.period != BudgetPeriod.MONTHLY }
        }
        val inclusions = _state.value.plan?.inclusions ?: DefaultPlanInclusions
        return existing?.toFormState() ?: if (target.tripId != null) {
            BudgetFormState(scope = BudgetScope.TRIP, period = BudgetPeriod.ONE_OFF, tripId = target.tripId)
        } else {
            BudgetFormState(
                scope = BudgetScope.CATEGORY,
                categoryId = target.categoryId,
                includeTripExpenses = inclusions.includeTripExpenses,
                includeExtraordinaryExpenses = inclusions.includeExtraordinaryExpenses,
            )
        }
    }

    fun onMonthSelected(month: YearMonth) {
        if (month !in _state.value.activityMonths || month == _state.value.selectedMonth) return
        _state.value = _state.value.copy(selectedMonth = month)
        refresh()
    }

    /**
     * The budget is edited as it stands today, and a change applies from this month on: an earlier
     * month left on screen would show a different budget and never reflect the change.
     */
    private fun showCurrentMonth() {
        val current = YearMonth.from(today())
        if (_state.value.selectedMonth.isBefore(current)) {
            _state.value = _state.value.copy(selectedMonth = current)
            refresh()
        }
    }

    /** A new partida of the monthly plan, counting what the plan counts. */
    fun onAddClicked(categoryId: String? = null) {
        showCurrentMonth()
        val state = _state.value
        val inclusions = state.plan?.inclusions ?: DefaultPlanInclusions
        _state.value = state.copy(
            form = BudgetFormState(
                scope = BudgetScope.CATEGORY,
                categoryId = categoryId,
                includeTripExpenses = inclusions.includeTripExpenses,
                includeExtraordinaryExpenses = inclusions.includeExtraordinaryExpenses,
            ),
        )
    }

    /**
     * Opens a proposed plan built from recent months: the categories that spend most as partides
     * (recurring payments included), the rest left to Altres, and a total that covers all.
     */
    fun onCreatePlanClicked() {
        showCurrentMonth()
        val state = _state.value
        val spending = state.recentSpending ?: return
        val topLevel = state.categories.filter { it.parentId == null }
        val candidates = topLevel
            .mapNotNull { category -> spending.byCategory[category.id]?.takeIf { it >= PLAN_SUGGESTION_MIN_CENTS }?.let { category to it } }
            .sortedByDescending { it.second }
            .take(PLAN_SUGGESTION_MAX_PARTIDES)
        val suggested = candidates.map { (category, cents) ->
            PlanSetupRow(categoryId = category.id, selected = true, amount = formatEuroInput(roundUpToTen(cents)))
        }
        val others = spending.uncategorisedCents + topLevel
            .filter { category -> candidates.none { it.first.id == category.id } }
            .sumOf { spending.byCategory[it.id] ?: 0L }
        val total = suggested.sumOf { parseEuroCents(it.amount, allowNegative = false) ?: 0L } + roundUpToTen(others)
        _state.value = state.copy(
            planSetup = PlanSetupState(
                rows = suggested,
                total = formatEuroInput(roundUpToTen(total)),
            ),
        )
    }

    fun onPlanSetupChanged(setup: PlanSetupState) {
        _state.value = _state.value.copy(planSetup = setup.copy(errorRes = null, errorMessage = null))
    }

    fun onPlanSetupDismissed() {
        _state.value = _state.value.copy(planSetup = null)
    }

    fun onPlanSetupSaved() {
        val setup = _state.value.planSetup ?: return
        if (setup.isSaving) return
        val total = parseEuroCents(setup.total, allowNegative = false)
        val chosen = setup.rows.filter { it.selected }
        val amounts = chosen.map { parseEuroCents(it.amount, allowNegative = false) }
        if (total == null || total <= 0L || amounts.any { it == null || it <= 0L }) {
            _state.value = _state.value.copy(planSetup = setup.copy(errorRes = Res.string.budget_validation_limit_positive))
            return
        }
        val inclusions = _state.value.plan?.inclusions ?: DefaultPlanInclusions
        val draft = { scope: BudgetScope, categoryId: String?, cents: Long ->
            BudgetDraft(
                id = UUID.randomUUID().toString(),
                categoryId = categoryId,
                limitAmountCents = cents,
                alertThresholdPercent = null,
                scope = scope,
                period = BudgetPeriod.MONTHLY,
                includeTripExpenses = inclusions.includeTripExpenses,
                includeExtraordinaryExpenses = inclusions.includeExtraordinaryExpenses,
            )
        }
        _state.value = _state.value.copy(planSetup = setup.copy(isSaving = true))
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    budgetRepository.createPlan(
                        total = draft(BudgetScope.OVERALL_MONTH, null, total),
                        partides = chosen.zip(amounts).map { (row, cents) -> draft(BudgetScope.CATEGORY, row.categoryId, requireNotNull(cents)) },
                        createdAt = now,
                    )
                }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(planSetup = null, revision = _state.value.revision + 1)
                    refresh()
                    refreshNotifications()
                },
                onFailure = { failure ->
                    val errorRes = when (failure) {
                        is OverlappingCompartmentException -> Res.string.budget_validation_overlap
                        is DuplicateActiveBudgetException -> Res.string.budget_validation_duplicate
                        else -> null
                    }
                    _state.value = _state.value.copy(
                        planSetup = setup.copy(
                            errorRes = errorRes,
                            errorMessage = if (errorRes == null) failure.message ?: failure.javaClass.simpleName else null,
                        ),
                    )
                },
            )
        }
    }

    /** A yearly category limit, outside the monthly plan. */
    fun onAddYearlyClicked() {
        showCurrentMonth()
        _state.value = _state.value.copy(form = BudgetFormState(scope = BudgetScope.CATEGORY, period = BudgetPeriod.YEARLY))
    }

    fun onAddOverallClicked() {
        showCurrentMonth()
        val existing = _state.value.evaluations.firstOrNull {
            it.budget.scope == BudgetScope.OVERALL_MONTH
        }
        _state.value = _state.value.copy(
            form = existing?.budget?.toFormState() ?: BudgetFormState(
                scope = BudgetScope.OVERALL_MONTH,
                period = BudgetPeriod.MONTHLY,
                // Trips have budgets of their own, so the monthly plan leaves them out by default.
                includeTripExpenses = DefaultPlanInclusions.includeTripExpenses,
                includeExtraordinaryExpenses = DefaultPlanInclusions.includeExtraordinaryExpenses,
            ),
        )
    }

    fun onEditClicked(budget: BudgetSummary) {
        showCurrentMonth()
        _state.value = _state.value.copy(form = budget.toFormState())
    }

    fun onFormChanged(form: BudgetFormState) {
        _state.value = _state.value.copy(
            form = form.copy(
                errorRes = null,
                errorField = null,
                errorMessage = null,
            ),
        )
    }

    fun onFormDismissed() {
        _state.value = _state.value.copy(form = null)
    }

    fun onDeleteClicked(budget: BudgetSummary) {
        _state.value = _state.value.copy(archiveCandidate = budget)
    }

    fun onDeleteEditingBudgetClicked() {
        val id = _state.value.form?.id ?: return
        val budget = _state.value.evaluations.firstOrNull { it.budget.id == id }?.budget ?: return
        // Modal sheets render in their own dialog layer. Close it before opening the archive
        // confirmation so the confirmation is visible and can receive interaction.
        _state.value = _state.value.copy(form = null, archiveCandidate = budget)
    }

    fun onArchiveDismissed() {
        _state.value = _state.value.copy(archiveCandidate = null)
    }

    fun onArchiveConfirmed(onSuccess: (undo: suspend () -> Unit) -> Unit = {}) {
        val budget = _state.value.archiveCandidate ?: return
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { budgetRepository.archive(budget.id, archivedAt = now) }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(archiveCandidate = null, revision = _state.value.revision + 1)
                    refresh()
                    onSuccess { undoDelete(budget.id, deletedAt = now) }
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        archiveCandidate = null,
                        errorMessage = it.message ?: it.javaClass.simpleName,
                    )
                },
            )
            if (result.isSuccess) {
                refreshNotifications()
            }
        }
    }

    private suspend fun undoDelete(budgetId: String, deletedAt: String) {
        val restoredAt = Instant.now().toString()
        val result = withContext(ioDispatcher) {
            runCatching { budgetRepository.restore(budgetId, deletedAt, restoredAt) }
        }
        result.fold(
            onSuccess = {
                _state.value = _state.value.copy(revision = _state.value.revision + 1)
                refresh()
                refreshNotifications()
            },
            onFailure = { _state.value = _state.value.copy(errorMessage = it.message ?: it.javaClass.simpleName) },
        )
    }

    fun onSaveClicked() {
        val form = _state.value.form ?: return
        val limit = parseEuroCents(form.limit, allowNegative = false)
        val threshold = form.threshold.trim().toLongOrNull()

        val (errorRes, errorField) = when {
            form.scope == BudgetScope.CATEGORY && form.categoryId == null ->
                Res.string.budget_validation_category_required to BudgetFormField.CATEGORY
            form.scope == BudgetScope.TRIP && form.tripId == null ->
                Res.string.budget_validation_trip_required to BudgetFormField.TRIP
            limit == null || limit <= 0L -> Res.string.budget_validation_limit_positive to BudgetFormField.LIMIT
            form.threshold.isNotBlank() && (threshold == null || threshold !in 1L..100L) ->
                Res.string.budget_validation_threshold_range to BudgetFormField.THRESHOLD
            else -> null to null
        }
        if (errorRes != null) {
            _state.value = _state.value.copy(form = form.copy(errorRes = errorRes, errorField = errorField))
            return
        }

        val draft = BudgetDraft(
            id = form.id ?: UUID.randomUUID().toString(),
            categoryId = if (form.scope == BudgetScope.CATEGORY) requireNotNull(form.categoryId) else null,
            limitAmountCents = requireNotNull(limit),
            alertThresholdPercent = threshold,
            tripId = if (form.scope == BudgetScope.TRIP) requireNotNull(form.tripId) else null,
            scope = form.scope,
            period = form.period,
            includeTripExpenses = form.includeTripExpenses,
            includeExtraordinaryExpenses = form.includeExtraordinaryExpenses,
        )
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    if (form.id == null) {
                        budgetRepository.create(draft, createdAt = now)
                    } else {
                        budgetRepository.update(draft, updatedAt = now)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(form = null, revision = _state.value.revision + 1)
                    refresh()
                    refreshNotifications()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        form = if (it is DuplicateActiveBudgetException) {
                            form.copy(errorRes = Res.string.budget_validation_duplicate)
                        } else if (it is OverlappingCompartmentException) {
                            form.copy(errorRes = Res.string.budget_validation_overlap, errorField = BudgetFormField.CATEGORY)
                        } else {
                            form.copy(errorMessage = it.message ?: it.javaClass.simpleName)
                        },
                    )
                },
            )
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val selectedMonth = _state.value.selectedMonth
            val result = withContext(ioDispatcher) {
                runCatching {
                    val activityMonths = analysisRepository?.activityMonths().orEmpty()
                    val effectiveMonth = selectedMonth.takeIf { it in activityMonths || activityMonths.isEmpty() }
                        ?: requireNotNull(activityMonths.firstOrNull())
                    val allCategories = categoryRepository.listActive()
                    val categories = allCategories.filter { it.supportsExpense }
                    val evaluations = budgetRepository.evaluateAll(
                        fromDate = effectiveMonth.atDay(1).toString(),
                        toDate = effectiveMonth.atEndOfMonth().toString(),
                    )
                    val parents = allCategories.associate { it.id to it.parentId }
                    val templates = templateRepository?.listActive().orEmpty()
                    val plan = budgetRepository.monthPlan(
                        month = effectiveMonth,
                        today = today(),
                        templates = templates,
                        categoryParentById = parents,
                    )
                    val trips = tripRepository.listActive()
                    LoadedBudgetData(
                        evaluations = evaluations,
                        plan = plan,
                        // A plan that counts trip spending already has it in its total.
                        tripCents = if (plan.inclusions.includeTripExpenses) 0L else budgetRepository.tripSpendingIn(effectiveMonth, today(), trips),
                        recentSpending = budgetRepository.recentSpending(YearMonth.from(today()), parents, plan.inclusions),
                        income = budgetRepository.planIncome(effectiveMonth, today(), templates),
                        history = budgetRepository.spendingHistory(YearMonth.from(today()), HISTORY_MONTHS, parents, plan.inclusions),
                        categories = categories,
                        trips = trips,
                        activityMonths = activityMonths,
                        selectedMonth = effectiveMonth,
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        evaluations = it.evaluations,
                        plan = it.plan,
                        recentSpending = it.recentSpending,
                        history = it.history,
                        income = it.income,
                        tripCents = it.tripCents,
                        today = today(),
                        categories = it.categories,
                        trips = it.trips,
                        activityMonths = it.activityMonths,
                        selectedMonth = it.selectedMonth,
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
            pendingTarget?.takeIf { result.isSuccess }?.let { target ->
                pendingTarget = null
                _state.value = _state.value.copy(form = formFor(target))
            }
        }
    }

    private fun refreshNotifications() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                runCatching { notificationRefresher.refreshNotifications() }
            }
        }
    }
}

data class BudgetsUiState(
    val evaluations: List<BudgetEvaluation> = emptyList(),
    /** The selected month's plan: its total, fixed payments, partides and the rest. */
    val plan: BudgetMonthPlan? = null,
    /** What recent months spent per category, which suggested amounts start from. */
    val recentSpending: RecentSpending? = null,
    /** The months before this one, which the budget sheets chart against the amount being set. */
    val history: SpendingHistory? = null,
    /** The selected month's income, which the plan's expected saving comes from. */
    val income: PlanIncome? = null,
    /** What trips take from the selected month's saving when the plan leaves them out: spent, plus what their budgets still plan for it. */
    val tripCents: Long = 0L,
    val planSetup: PlanSetupState? = null,
    val today: LocalDate = LocalDate.now(),
    val categories: List<CategoryRecord> = emptyList(),
    val trips: List<TripSummary> = emptyList(),
    val activityMonths: List<YearMonth> = emptyList(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: BudgetFormState? = null,
    val archiveCandidate: BudgetSummary? = null,
    /** Counts committed budget changes, so a page hosting the budget sheet knows to reload. */
    val revision: Int = 0,
)

private data class BudgetTarget(val categoryId: String?, val tripId: String?)

/** A proposed plan being adjusted before it is created: one row per suggested partida. */
data class PlanSetupState(
    val rows: List<PlanSetupRow>,
    val total: String,
    val isSaving: Boolean = false,
    val errorRes: StringResource? = null,
    val errorMessage: String? = null,
)

data class PlanSetupRow(
    val categoryId: String,
    val selected: Boolean,
    val amount: String,
)

/** Suggestions round up to whole tens of euros: a plan reads in round figures. */
fun roundUpToTen(cents: Long): Long = (cents + 999L) / 1_000L * 1_000L

private const val PLAN_SUGGESTION_MIN_CENTS = 2_000L
private const val PLAN_SUGGESTION_MAX_PARTIDES = 6
private const val HISTORY_MONTHS = 6

/** Identifies which field a budget-form validation error belongs to. */
enum class BudgetFormField {
    CATEGORY,
    TRIP,
    LIMIT,
    THRESHOLD,
}

data class BudgetFormState(
    val id: String? = null,
    val scope: BudgetScope = BudgetScope.CATEGORY,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val categoryId: String? = null,
    val tripId: String? = null,
    val limit: String = "",
    val threshold: String = "80",
    val includeTripExpenses: Boolean = true,
    val includeExtraordinaryExpenses: Boolean = true,
    val errorRes: StringResource? = null,
    val errorField: BudgetFormField? = null,
    val errorMessage: String? = null,
)

private data class LoadedBudgetData(
    val evaluations: List<BudgetEvaluation>,
    val plan: BudgetMonthPlan,
    val recentSpending: RecentSpending,
    val history: SpendingHistory,
    val income: PlanIncome,
    val tripCents: Long,
    val categories: List<CategoryRecord>,
    val trips: List<TripSummary>,
    val activityMonths: List<YearMonth>,
    val selectedMonth: YearMonth,
)

private fun BudgetSummary.toFormState(): BudgetFormState =
    BudgetFormState(
        id = id,
        scope = scope,
        period = period,
        categoryId = categoryId,
        tripId = tripId,
        limit = formatEuroInput(limitAmountCents),
        threshold = alertThresholdPercent?.toString().orEmpty(),
        includeTripExpenses = includeTripExpenses,
        includeExtraordinaryExpenses = includeExtraordinaryExpenses,
    )
