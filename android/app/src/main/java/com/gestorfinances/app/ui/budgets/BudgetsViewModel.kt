package com.gestorfinances.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.BudgetDraft
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetPeriod
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.BudgetSummary
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.notifications.NotificationRefresher
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.common.parseEuroCents
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
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

    fun onEditClicked(budget: BudgetSummary) {
        _state.value = _state.value.copy(form = budget.toFormState())
    }

    fun onFormChanged(form: BudgetFormState) {
        _state.value = _state.value.copy(
            form = form.copy(errorRes = null, errorField = null, errorMessage = null),
        )
    }

    fun onFormDismissed() {
        _state.value = _state.value.copy(form = null)
    }

    fun onDeleteClicked(budget: BudgetSummary) {
        _state.value = _state.value.copy(archiveCandidate = budget)
    }

    fun onArchiveDismissed() {
        _state.value = _state.value.copy(archiveCandidate = null)
    }

    fun onArchiveConfirmed() {
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

    fun onSaveClicked() {
        val form = _state.value.form ?: return
        val limit = parseEuroCents(form.limit, allowNegative = false)
        val threshold = form.threshold.trim().toLongOrNull()
        val startValid = form.startDate.isBlank() || parseDate(form.startDate) != null

        val (errorRes, errorField) = when {
            form.scope == BudgetScope.CATEGORY && form.categoryId == null ->
                R.string.budget_validation_category_required to BudgetFormField.CATEGORY
            form.scope == BudgetScope.TRIP && form.tripId == null ->
                R.string.budget_validation_trip_required to BudgetFormField.TRIP
            limit == null || limit <= 0L -> R.string.budget_validation_limit_positive to BudgetFormField.LIMIT
            form.threshold.isNotBlank() && (threshold == null || threshold !in 1L..100L) ->
                R.string.budget_validation_threshold_range to BudgetFormField.THRESHOLD
            !startValid -> R.string.movement_validation_date_invalid to BudgetFormField.START_DATE
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
            startDate = form.startDate.trim().ifBlank { null },
            tripId = if (form.scope == BudgetScope.TRIP) requireNotNull(form.tripId) else null,
            scope = form.scope,
            period = if (form.scope == BudgetScope.TRIP) BudgetPeriod.ONE_OFF else BudgetPeriod.MONTHLY,
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
                        form = form.copy(errorMessage = it.message ?: it.javaClass.simpleName),
                    )
                },
            )
        }
    }

    private fun refresh(openContextForm: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val month = YearMonth.from(today())
            val result = withContext(ioDispatcher) {
                runCatching {
                    LoadedBudgetData(
                        evaluations = budgetRepository.evaluateAll(
                            fromDate = month.atDay(1).toString(),
                            toDate = month.atEndOfMonth().toString(),
                        ),
                        categories = categoryRepository.listActive().filter { it.supportsExpense },
                        trips = tripRepository.listActive(),
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        evaluations = it.evaluations,
                        categories = it.categories,
                        trips = it.trips,
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
        private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BudgetsViewModel::class.java)) {
                return BudgetsViewModel(
                    budgetRepository = budgetRepository,
                    categoryRepository = categoryRepository,
                    tripRepository = tripRepository,
                    notificationRefresher = notificationRefresher,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class BudgetsUiState(
    val evaluations: List<BudgetEvaluation> = emptyList(),
    val categories: List<CategoryRecord> = emptyList(),
    val trips: List<TripSummary> = emptyList(),
    val contextTripId: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: BudgetFormState? = null,
    val archiveCandidate: BudgetSummary? = null,
)

/** Identifies which field a budget-form validation error belongs to (audit U8, `docs/17` WP2). */
enum class BudgetFormField {
    CATEGORY,
    TRIP,
    LIMIT,
    THRESHOLD,
    START_DATE,
}

data class BudgetFormState(
    val id: String? = null,
    val scope: BudgetScope = BudgetScope.CATEGORY,
    val categoryId: String? = null,
    val tripId: String? = null,
    val limit: String = "",
    val threshold: String = "80",
    val startDate: String = "",
    val errorRes: Int? = null,
    val errorField: BudgetFormField? = null,
    val errorMessage: String? = null,
)

private data class LoadedBudgetData(
    val evaluations: List<BudgetEvaluation>,
    val categories: List<CategoryRecord>,
    val trips: List<TripSummary>,
)

private val CategoryRecord.supportsExpense: Boolean
    get() = kind == CategoryKind.EXPENSE || kind == CategoryKind.BOTH

private fun BudgetSummary.toFormState(): BudgetFormState =
    BudgetFormState(
        id = id,
        scope = scope,
        categoryId = categoryId,
        tripId = tripId,
        limit = formatEuroInput(limitAmountCents),
        threshold = alertThresholdPercent?.toString().orEmpty(),
        startDate = startDate.orEmpty(),
    )

private fun newBudgetForm(
    contextTripId: String?,
    trips: List<TripSummary>,
): BudgetFormState {
    val trip = contextTripId?.let { tripId -> trips.firstOrNull { it.id == tripId } }
    return if (trip != null) {
        BudgetFormState(
            scope = BudgetScope.TRIP,
            tripId = trip.id,
            startDate = trip.startDate.orEmpty(),
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

private fun parseDate(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }
