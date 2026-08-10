package com.gestorfinances.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.BudgetDraft
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetPeriod
import com.gestorfinances.app.data.repository.BudgetProjection
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.BudgetSummary
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.DuplicateActiveBudgetException
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

    fun onScreenShown(contextTripId: String? = null) {
        _state.value = _state.value.copy(contextTripId = contextTripId)
        refresh(openContextForm = contextTripId != null)
    }

    /** Clears the period and expanded/form state for a fresh visit from the Més menu. */
    fun resetForMenuNavigation() {
        _state.value = BudgetsUiState(selectedMonth = YearMonth.from(today()))
        refresh()
    }

    fun onMonthSelected(month: YearMonth) {
        if (month !in _state.value.activityMonths || month == _state.value.selectedMonth) return
        _state.value = _state.value.copy(selectedMonth = month)
        refresh()
    }

    fun onPastTripsExpandedToggled() {
        _state.value = _state.value.copy(pastTripsExpanded = !_state.value.pastTripsExpanded)
    }

    fun onAddClicked(categoryId: String? = null) {
        val state = _state.value
        _state.value = state.copy(
            form = if (categoryId != null) {
                BudgetFormState(scope = BudgetScope.CATEGORY, categoryId = categoryId)
            } else {
                newBudgetForm(state.contextTripId, state.trips)
            },
        )
    }

    fun onAddOverallClicked() {
        val existing = _state.value.evaluations.firstOrNull {
            it.budget.scope == BudgetScope.OVERALL_MONTH
        }
        _state.value = _state.value.copy(
            form = existing?.budget?.toFormState() ?: BudgetFormState(
                scope = BudgetScope.OVERALL_MONTH,
                period = BudgetPeriod.MONTHLY,
            ),
        )
    }

    fun onEditClicked(budget: BudgetSummary) {
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

    fun onArchiveConfirmed(onSuccess: (undo: () -> Unit) -> Unit = {}) {
        val budget = _state.value.archiveCandidate ?: return
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { budgetRepository.archive(budget.id, archivedAt = now) }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(archiveCandidate = null)
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

    private fun undoDelete(budgetId: String, deletedAt: String) {
        val restoredAt = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { budgetRepository.restore(budgetId, deletedAt, restoredAt) }
            }
            result.fold(
                onSuccess = {
                    refresh()
                    refreshNotifications()
                },
                onFailure = { _state.value = _state.value.copy(errorMessage = it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    fun onSaveClicked() {
        val form = _state.value.form ?: return
        val limit = parseEuroCents(form.limit, allowNegative = false)
        val threshold = form.threshold.trim().toLongOrNull()

        val (errorRes, errorField) = when {
            form.scope == BudgetScope.CATEGORY && form.categoryId == null ->
                R.string.budget_validation_category_required to BudgetFormField.CATEGORY
            form.scope == BudgetScope.TRIP && form.tripId == null ->
                R.string.budget_validation_trip_required to BudgetFormField.TRIP
            limit == null || limit <= 0L -> R.string.budget_validation_limit_positive to BudgetFormField.LIMIT
            form.threshold.isNotBlank() && (threshold == null || threshold !in 1L..100L) ->
                R.string.budget_validation_threshold_range to BudgetFormField.THRESHOLD
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
                    _state.value = _state.value.copy(form = null)
                    refresh()
                    refreshNotifications()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        form = if (it is DuplicateActiveBudgetException) {
                            form.copy(errorRes = R.string.budget_validation_duplicate)
                        } else {
                            form.copy(errorMessage = it.message ?: it.javaClass.simpleName)
                        },
                    )
                },
            )
        }
    }

    private fun refresh(openContextForm: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val selectedMonth = _state.value.selectedMonth
            val currentMonth = YearMonth.from(today())
            val result = withContext(ioDispatcher) {
                runCatching {
                    val activityMonths = analysisRepository?.activityMonths().orEmpty()
                    val effectiveMonth = selectedMonth.takeIf { it in activityMonths || activityMonths.isEmpty() }
                        ?: requireNotNull(activityMonths.firstOrNull())
                    val categories = categoryRepository.listActive().filter { it.supportsExpense }
                    val evaluations = budgetRepository.evaluateAll(
                        fromDate = effectiveMonth.atDay(1).toString(),
                        toDate = effectiveMonth.atEndOfMonth().toString(),
                    )
                    LoadedBudgetData(
                        evaluations = evaluations,
                        projections = if (effectiveMonth == currentMonth) {
                            budgetRepository.currentMonthProjections(
                                today = today(),
                                templates = templateRepository?.listActive().orEmpty(),
                                categoryParentById = categories.associate { it.id to it.parentId },
                            )
                        } else {
                            emptyList()
                        },
                        categories = categories,
                        trips = tripRepository.listActive(),
                        activityMonths = activityMonths,
                        selectedMonth = effectiveMonth,
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        evaluations = it.evaluations,
                        projections = it.projections,
                        categories = it.categories,
                        trips = it.trips,
                        activityMonths = it.activityMonths,
                        selectedMonth = it.selectedMonth,
                        form = if (openContextForm) {
                            contextBudgetForm(
                                contextTripId = _state.value.contextTripId,
                                evaluations = it.evaluations,
                                trips = it.trips,
                            )
                        } else {
                            _state.value.form
                        },
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

    private fun refreshNotifications() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                runCatching { notificationRefresher.refreshNotifications() }
            }
        }
    }

    class Factory(
        private val budgetRepository: BudgetRepository,
        private val categoryRepository: CategoryRepository,
        private val tripRepository: TripRepository,
        private val templateRepository: TemplateRepository? = null,
        private val analysisRepository: AnalysisRepository? = null,
        private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BudgetsViewModel::class.java)) {
                return BudgetsViewModel(
                    budgetRepository = budgetRepository,
                    categoryRepository = categoryRepository,
                    tripRepository = tripRepository,
                    templateRepository = templateRepository,
                    analysisRepository = analysisRepository,
                    notificationRefresher = notificationRefresher,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class BudgetsUiState(
    val evaluations: List<BudgetEvaluation> = emptyList(),
    val projections: List<BudgetProjection> = emptyList(),
    val categories: List<CategoryRecord> = emptyList(),
    val trips: List<TripSummary> = emptyList(),
    val activityMonths: List<YearMonth> = emptyList(),
    val contextTripId: String? = null,
    val selectedMonth: YearMonth = YearMonth.now(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: BudgetFormState? = null,
    val archiveCandidate: BudgetSummary? = null,
    val pastTripsExpanded: Boolean = false,
)

/** Identifies which field a budget-form validation error belongs to (field-level validation). */
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
    val errorRes: Int? = null,
    val errorField: BudgetFormField? = null,
    val errorMessage: String? = null,
)

private data class LoadedBudgetData(
    val evaluations: List<BudgetEvaluation>,
    val projections: List<BudgetProjection>,
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

private fun newBudgetForm(
    contextTripId: String?,
    trips: List<TripSummary>,
): BudgetFormState {
    val trip = contextTripId?.let { tripId -> trips.firstOrNull { it.id == tripId } }
    return if (trip != null) {
        BudgetFormState(
            scope = BudgetScope.TRIP,
            period = BudgetPeriod.ONE_OFF,
            tripId = trip.id,
        )
    } else {
        BudgetFormState()
    }
}

private fun contextBudgetForm(
    contextTripId: String?,
    evaluations: List<BudgetEvaluation>,
    trips: List<TripSummary>,
): BudgetFormState? {
    val tripId = contextTripId ?: return null
    val existing = evaluations.firstOrNull { it.budget.scope == BudgetScope.TRIP && it.budget.tripId == tripId }
    return existing?.budget?.toFormState() ?: newBudgetForm(tripId, trips)
}
