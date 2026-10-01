package com.gestorfinances.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.data.repository.BudgetPeriod
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.CategoryDraft
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.ui.common.EntityColorPalette
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

class CategoriesViewModel(
    private val categoryRepository: CategoryRepository,
    private val analysisRepository: AnalysisRepository,
    private val movementRepository: MovementRepository,
    private val budgetRepository: BudgetRepository,
    private val templateRepository: TemplateRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(CategoriesUiState())
    val state: StateFlow<CategoriesUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refreshCategories()
    }

    fun onAddClicked() {
        val nextOrder = (_state.value.categories.maxOfOrNull { it.displayOrder } ?: -1L) + 1L
        _state.value = _state.value.copy(
            form = CategoryFormState(
                displayOrder = nextOrder,
                colorHex = EntityColorPalette.first().hex,
            ),
        )
    }

    fun onEditClicked(category: CategoryRecord) {
        _state.value = _state.value.copy(form = category.toFormState())
    }

    fun onArchiveClicked(category: CategoryRecord) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    CategoryArchiveCandidate(
                        category = category,
                        activeTemplateCount = templateRepository.listActive().count {
                            it.status == TemplateStatus.ACTIVE && it.categoryId == category.id
                        },
                        budgetCount = budgetRepository.listActive().count { it.categoryId == category.id },
                        childCount = categoryRepository.listActive().count { it.parentId == category.id },
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = { _state.value.copy(archiveCandidate = it) },
                onFailure = { _state.value.copy(errorMessage = it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    fun onArchiveDismissed() {
        _state.value = _state.value.copy(archiveCandidate = null)
    }

    fun onArchiveConfirmed(onSuccess: (undo: suspend () -> Unit) -> Unit = {}) {
        val category = _state.value.archiveCandidate?.category ?: return
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val pausedTemplateIds = templateRepository.listActive()
                        .filter { it.status == TemplateStatus.ACTIVE && it.categoryId == category.id }
                        .map { it.id }
                    val archivedBudgetIds = budgetRepository.listActive()
                        .filter { it.categoryId == category.id }
                        .map { it.id }
                    val movedChildren = categoryRepository.listActive()
                        .filter { it.parentId == category.id }
                    movementRepository.runInTransaction {
                        pausedTemplateIds.forEach {
                            templateRepository.setStatus(it, TemplateStatus.PAUSED, updatedAt = now)
                        }
                        archivedBudgetIds.forEach { budgetRepository.archive(it, archivedAt = now) }
                        movedChildren.forEach { child ->
                            categoryRepository.update(child.toDraft(parentId = null), updatedAt = now)
                        }
                        categoryRepository.archive(category.id, archivedAt = now)
                    }
                    CategoryDeleteOperation(
                        categoryId = category.id,
                        deletedAt = now,
                        pausedTemplateIds = pausedTemplateIds,
                        archivedBudgetIds = archivedBudgetIds,
                        movedChildren = movedChildren,
                    )
                }
            }
            result.fold(
                onSuccess = { operation ->
                    _state.value = _state.value.copy(archiveCandidate = null)
                    refreshCategories()
                    onSuccess { undoDelete(operation) }
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        archiveCandidate = null,
                        errorMessage = it.message ?: it.javaClass.simpleName,
                    )
                },
            )
        }
    }

    private suspend fun undoDelete(operation: CategoryDeleteOperation) {
        val restoredAt = Instant.now().toString()
        val result = withContext(ioDispatcher) {
            runCatching {
                movementRepository.runInTransaction {
                    categoryRepository.restore(operation.categoryId, operation.deletedAt, restoredAt)
                    operation.movedChildren.forEach { child ->
                        categoryRepository.restoreParentAfterDelete(
                            id = child.id,
                            parentId = operation.categoryId,
                            deletedAt = operation.deletedAt,
                            restoredAt = restoredAt,
                        )
                    }
                    operation.archivedBudgetIds.forEach {
                        budgetRepository.restore(it, operation.deletedAt, restoredAt)
                    }
                    operation.pausedTemplateIds.forEach {
                        templateRepository.restoreActiveStatusAfterDelete(
                            id = it,
                            deletedAt = operation.deletedAt,
                            restoredAt = restoredAt,
                        )
                    }
                }
            }
        }
        result.fold(
            onSuccess = { refreshCategories() },
            onFailure = { _state.value = _state.value.copy(errorMessage = it.message ?: it.javaClass.simpleName) },
        )
    }

    fun onFormChanged(form: CategoryFormState) {
        _state.value = _state.value.copy(form = form)
    }

    fun onFormDismissed() {
        _state.value = _state.value.copy(form = null)
    }

    fun onSaveClicked() {
        val form = _state.value.form ?: return
        val name = form.name.trim()
        val parent = form.parentId?.let { parentId ->
            _state.value.categories.firstOrNull { it.id == parentId }
        }
        val hasActiveChildren = form.id != null &&
            _state.value.categories.any { it.parentId == form.id }

        val (errorRes, errorField) = when {
            name.isEmpty() -> R.string.category_validation_name_required to CategoryFormField.NAME
            form.parentId != null && (parent == null || parent.parentId != null || parent.id == form.id) ->
                R.string.category_validation_parent_invalid to CategoryFormField.PARENT
            form.parentId != null && hasActiveChildren ->
                R.string.category_validation_parent_has_children to CategoryFormField.PARENT
            else -> null to null
        }

        if (errorRes != null) {
            _state.value = _state.value.copy(form = form.copy(errorRes = errorRes, errorField = errorField))
            return
        }

        val now = Instant.now().toString()
        val draft = CategoryDraft(
            id = form.id ?: UUID.randomUUID().toString(),
            name = name,
            kind = form.kind,
            nature = form.nature,
            parentId = form.parentId,
            icon = form.iconKey,
            color = form.colorHex,
            displayOrder = form.displayOrder,
        )

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    if (form.id == null) {
                        categoryRepository.create(draft, createdAt = now)
                    } else {
                        categoryRepository.update(draft, updatedAt = now)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(form = null)
                    refreshCategories()
                    // The category page the form was opened over now shows different details.
                    _state.value.flowDetail?.let { onCategoryDetailOpened(it.category.id) }
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        form = form.copy(errorMessage = it.message ?: it.javaClass.simpleName),
                    )
                },
            )
        }
    }

    /** Opens the category page: the category with its movements (its subcategories' too) and budget. */
    fun onCategoryDetailOpened(categoryId: String) {
        refreshCategories()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val category = requireNotNull(categoryRepository.getActive(categoryId)) { "Category not found." }
                    CategoryFlowDetailState(
                        category = category,
                        entries = movementRepository.listActiveForCategory(categoryId),
                        budgetEvaluation = categoryBudgetEvaluation(categoryId),
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = { _state.value.copy(flowDetail = it) },
                onFailure = { error ->
                    val message = error.message ?: error.javaClass.simpleName
                    val open = _state.value.flowDetail
                    if (open != null) {
                        _state.value.copy(flowDetail = open.copy(errorMessage = message))
                    } else {
                        _state.value.copy(errorMessage = message)
                    }
                },
            )
        }
    }

    /**
     * The category's budget this month: its partida (measured as the monthly plan measures it,
     * without recurring payments), else its yearly limit.
     */
    private fun categoryBudgetEvaluation(categoryId: String): BudgetEvaluation? {
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val evaluation = budgetRepository.evaluateAll(
            fromDate = month.atDay(1).toString(),
            toDate = month.atEndOfMonth().toString(),
        ).filter { it.budget.scope == BudgetScope.CATEGORY && it.budget.categoryId == categoryId }
            .minByOrNull { it.budget.period != BudgetPeriod.MONTHLY }
            ?: return null
        if (evaluation.budget.period != BudgetPeriod.MONTHLY) return evaluation
        val partida = budgetRepository.monthPlan(
            month = month,
            today = today,
            templates = emptyList(),
            categoryParentById = categoryRepository.listActive().associate { it.id to it.parentId },
        ).plan.compartments[evaluation.budget.id] ?: return evaluation
        return evaluation.copy(actualCents = partida.actualCents)
    }

    private fun refreshCategories() {
        viewModelScope.launch {
            // Only a first load shows as loading: a reload keeps the list on screen.
            _state.value = _state.value.copy(isLoading = _state.value.categories.isEmpty(), errorMessage = null)
            val result = withContext(ioDispatcher) {
                runCatching {
                    val categories = categoryRepository.listActive()
                    Triple(categories, loadMonthSpend(), loadYearSpend()) to loadTotals()
                }
            }
            _state.value = result.fold(
                onSuccess = { (spend, totals) ->
                    val (categories, month, year) = spend
                    // An income category's figure is its income; any other's is what it spent. One
                    // that takes both also has an income figure, shown under Ingressos.
                    val incomeOnly = categories.filter { it.kind == CategoryKind.INCOME }.map { it.id }.toSet()
                    val figure = { flows: Map<String, CategoryFlow> ->
                        flows.mapValues { (id, flow) -> if (id in incomeOnly) flow.incomeCents else flow.expenseCents }
                    }
                    _state.value.copy(
                        categories = categories,
                        monthSpend = figure(month),
                        yearSpend = figure(year),
                        monthIncome = month.mapValues { it.value.incomeCents },
                        yearIncome = year.mapValues { it.value.incomeCents },
                        totals = totals,
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

    /** Canonical actual spend (or income) per category for this month, both ends inclusive. */
    private fun loadMonthSpend(): Map<String, CategoryFlow> {
        val month = YearMonth.now()
        return spendByCategory(month.atDay(1), month.plusMonths(1).atDay(1))
    }

    private fun loadYearSpend(): Map<String, CategoryFlow> {
        val year = LocalDate.now().year
        return spendByCategory(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1))
    }

    /** The analysis queries take an exclusive end: [until] is the first day after the period. */
    private fun spendByCategory(from: LocalDate, until: LocalDate): Map<String, CategoryFlow> =
        runCatching {
            analysisRepository.actualByCategory(fromDate = from.toString(), toDate = until.toString())
                .mapNotNull { row -> row.categoryId?.let { it to CategoryFlow(row.expenseCents, row.incomeCents) } }
                .toMap()
        }.getOrDefault(emptyMap())

    /** Canonical actual income and expense for this month and this year, uncategorised included. */
    private fun loadTotals(): CategoryPeriodTotals {
        val month = YearMonth.now()
        val year = LocalDate.now().year
        val monthTotals = analysisRepository.periodTotals(month.atDay(1).toString(), month.plusMonths(1).atDay(1).toString())
        val yearTotals = analysisRepository.periodTotals(LocalDate.of(year, 1, 1).toString(), LocalDate.of(year + 1, 1, 1).toString())
        return CategoryPeriodTotals(
            monthExpenseCents = monthTotals.actualExpenseCents,
            monthIncomeCents = monthTotals.actualIncomeCents,
            yearExpenseCents = yearTotals.actualExpenseCents,
            yearIncomeCents = yearTotals.actualIncomeCents,
        )
    }
}

data class CategoryPeriodTotals(
    val monthExpenseCents: Long,
    val monthIncomeCents: Long,
    val yearExpenseCents: Long,
    val yearIncomeCents: Long,
)

data class CategoriesUiState(
    val categories: List<CategoryRecord> = emptyList(),
    val monthSpend: Map<String, Long> = emptyMap(),
    val yearSpend: Map<String, Long> = emptyMap(),
    /** Income per category, for the Ingressos section (a category that takes both appears there too). */
    val monthIncome: Map<String, Long> = emptyMap(),
    val yearIncome: Map<String, Long> = emptyMap(),
    val totals: CategoryPeriodTotals? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: CategoryFormState? = null,
    val archiveCandidate: CategoryArchiveCandidate? = null,
    val flowDetail: CategoryFlowDetailState? = null,
)

data class CategoryArchiveCandidate(
    val category: CategoryRecord,
    val activeTemplateCount: Int,
    val budgetCount: Int,
    val childCount: Int,
)

private data class CategoryDeleteOperation(
    val categoryId: String,
    val deletedAt: String,
    val pausedTemplateIds: List<String>,
    val archivedBudgetIds: List<String>,
    val movedChildren: List<CategoryRecord>,
)

data class CategoryFlowDetailState(
    val category: CategoryRecord,
    val entries: List<MovementSummary> = emptyList(),
    val budgetEvaluation: BudgetEvaluation? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/** Identifies which field a category-form validation error belongs to. */
enum class CategoryFormField {
    NAME,
    PARENT,
}

data class CategoryFormState(
    val id: String? = null,
    val name: String = "",
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val nature: CategoryNature = CategoryNature.VARIABLE,
    val parentId: String? = null,
    val colorHex: String? = null,
    val iconKey: String? = null,
    val displayOrder: Long = 0,
    val errorRes: Int? = null,
    val errorField: CategoryFormField? = null,
    val errorMessage: String? = null,
)

private fun CategoryRecord.toFormState(): CategoryFormState =
    CategoryFormState(
        id = id,
        name = name,
        kind = kind,
        nature = nature,
        parentId = parentId,
        colorHex = color,
        iconKey = icon,
        displayOrder = displayOrder,
    )

private fun CategoryRecord.toDraft(parentId: String?): CategoryDraft =
    CategoryDraft(id, name, kind, nature, parentId, icon, color, displayOrder)

/** A category's canonical actual expense and income over a period. */
private data class CategoryFlow(val expenseCents: Long, val incomeCents: Long)
