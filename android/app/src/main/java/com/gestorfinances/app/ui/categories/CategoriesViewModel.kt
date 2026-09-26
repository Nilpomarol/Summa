package com.gestorfinances.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetRepository
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
        _state.value.flowDetail?.category?.let(::onFlowClicked)
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

    fun onArchiveConfirmed(onSuccess: (undo: () -> Unit) -> Unit = {}) {
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

    private fun undoDelete(operation: CategoryDeleteOperation) {
        val restoredAt = Instant.now().toString()
        viewModelScope.launch {
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
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        form = form.copy(errorMessage = it.message ?: it.javaClass.simpleName),
                    )
                },
            )
        }
    }

    fun onFlowClicked(category: CategoryRecord) {
        _state.value = _state.value.copy(
            flowDetail = CategoryFlowDetailState(
                category = category,
                isLoading = true,
            ),
        )
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val entries = movementRepository.listActiveForCategory(category.id)
                    val budgetEvaluation = categoryBudgetEvaluation(category.id)
                    entries to budgetEvaluation
                }
            }
            _state.value = result.fold(
                onSuccess = { (entries, budgetEvaluation) ->
                    _state.value.copy(
                        flowDetail = CategoryFlowDetailState(
                            category = category,
                            entries = entries,
                            budgetEvaluation = budgetEvaluation,
                            isLoading = false,
                        ),
                    )
                },
                onFailure = {
                    _state.value.copy(
                        flowDetail = CategoryFlowDetailState(
                            category = category,
                            isLoading = false,
                            errorMessage = it.message ?: it.javaClass.simpleName,
                        ),
                    )
                },
            )
        }
    }

    /** Active CATEGORY-scope budget for [categoryId], evaluated against the current month. */
    private fun categoryBudgetEvaluation(categoryId: String): BudgetEvaluation? {
        val month = YearMonth.from(LocalDate.now())
        return budgetRepository.evaluateAll(
            fromDate = month.atDay(1).toString(),
            toDate = month.atEndOfMonth().toString(),
        ).firstOrNull { it.budget.scope == BudgetScope.CATEGORY && it.budget.categoryId == categoryId }
    }

    fun onFlowDismissed() {
        _state.value = _state.value.copy(flowDetail = null)
    }

    private fun refreshCategories() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val result = withContext(ioDispatcher) {
                runCatching {
                    val categories = categoryRepository.listActive()
                    val monthSpend = loadMonthSpend()
                    val yearSpend = loadYearSpend()
                    Triple(categories, monthSpend, yearSpend)
                }
            }
            _state.value = result.fold(
                onSuccess = { (categories, monthSpend, yearSpend) ->
                    _state.value.copy(
                        categories = categories,
                        monthSpend = monthSpend,
                        yearSpend = yearSpend,
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

    private fun loadMonthSpend(): Map<String, Long> {
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val from = month.atDay(1).toString()
        val to = month.atEndOfMonth().toString()
        return runCatching {
            val spend = analysisRepository.actualByCategory(fromDate = from, toDate = to)
            buildMap {
                for (row in spend) {
                    val id = row.categoryId ?: continue
                    put(id, maxOf(row.expenseCents, row.incomeCents))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun loadYearSpend(): Map<String, Long> {
        val today = LocalDate.now()
        val from = LocalDate.of(today.year, 1, 1).toString()
        val to = LocalDate.of(today.year, 12, 31).toString()
        return runCatching {
            val spend = analysisRepository.actualByCategory(fromDate = from, toDate = to)
            buildMap {
                for (row in spend) {
                    val id = row.categoryId ?: continue
                    put(id, maxOf(row.expenseCents, row.incomeCents))
                }
            }
        }.getOrDefault(emptyMap())
    }
}

data class CategoriesUiState(
    val categories: List<CategoryRecord> = emptyList(),
    val monthSpend: Map<String, Long> = emptyMap(),
    val yearSpend: Map<String, Long> = emptyMap(),
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

/** Identifies which field a category-form validation error belongs to (field-level validation). */
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
