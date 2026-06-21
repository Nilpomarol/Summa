package com.gestorfinances.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.BudgetDraft
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.BudgetSummary
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
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
    private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(BudgetsUiState())
    val state: StateFlow<BudgetsUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refresh()
    }

    fun onAddClicked() {
        _state.value = _state.value.copy(form = BudgetFormState())
    }

    fun onEditClicked(budget: BudgetSummary) {
        _state.value = _state.value.copy(form = budget.toFormState())
    }

    fun onFormChanged(form: BudgetFormState) {
        _state.value = _state.value.copy(form = form.copy(errorRes = null, errorMessage = null))
    }

    fun onFormDismissed() {
        _state.value = _state.value.copy(form = null)
    }

    fun onDeleteClicked(budget: BudgetSummary) {
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { budgetRepository.archive(budget.id, archivedAt = now) }
            }
            result.fold(onSuccess = { refresh() }, onFailure = ::showError)
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

        val errorRes = when {
            form.categoryId == null -> R.string.budget_validation_category_required
            limit == null || limit <= 0L -> R.string.budget_validation_limit_positive
            form.threshold.isNotBlank() && (threshold == null || threshold !in 1L..100L) ->
                R.string.budget_validation_threshold_range
            !startValid -> R.string.movement_validation_date_invalid
            else -> null
        }
        if (errorRes != null) {
            _state.value = _state.value.copy(form = form.copy(errorRes = errorRes))
            return
        }

        val draft = BudgetDraft(
            id = form.id ?: UUID.randomUUID().toString(),
            categoryId = requireNotNull(form.categoryId),
            limitAmountCents = requireNotNull(limit),
            alertThresholdPercent = threshold,
            startDate = form.startDate.trim().ifBlank { null },
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

    private fun showError(throwable: Throwable) {
        _state.value = _state.value.copy(errorMessage = throwable.message ?: throwable.javaClass.simpleName)
    }

    private fun refresh() {
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
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        evaluations = it.evaluations,
                        categories = it.categories,
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
        private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BudgetsViewModel::class.java)) {
                return BudgetsViewModel(
                    budgetRepository = budgetRepository,
                    categoryRepository = categoryRepository,
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
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: BudgetFormState? = null,
)

data class BudgetFormState(
    val id: String? = null,
    val categoryId: String? = null,
    val limit: String = "",
    val threshold: String = "",
    val startDate: String = "",
    val errorRes: Int? = null,
    val errorMessage: String? = null,
)

private data class LoadedBudgetData(
    val evaluations: List<BudgetEvaluation>,
    val categories: List<CategoryRecord>,
)

private val CategoryRecord.supportsExpense: Boolean
    get() = kind == CategoryKind.EXPENSE || kind == CategoryKind.BOTH

private fun BudgetSummary.toFormState(): BudgetFormState =
    BudgetFormState(
        id = id,
        categoryId = categoryId,
        limit = formatEuroInput(limitAmountCents),
        threshold = alertThresholdPercent?.toString().orEmpty(),
        startDate = startDate.orEmpty(),
    )

private fun parseDate(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }
