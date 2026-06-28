package com.gestorfinances.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.CategoryDraft
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.ui.common.EntityColorPalette
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
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
        _state.value = _state.value.copy(archiveCandidate = category)
    }

    fun onArchiveDismissed() {
        _state.value = _state.value.copy(archiveCandidate = null)
    }

    fun onArchiveConfirmed() {
        val category = _state.value.archiveCandidate ?: return
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { categoryRepository.archive(category.id, archivedAt = now) }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(archiveCandidate = null)
                    refreshCategories()
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

        val errorRes = when {
            name.isEmpty() -> R.string.category_validation_name_required
            form.parentId != null && (parent == null || parent.parentId != null || parent.id == form.id) ->
                R.string.category_validation_parent_invalid
            form.parentId != null && hasActiveChildren -> R.string.category_validation_parent_has_children
            else -> null
        }

        if (errorRes != null) {
            _state.value = _state.value.copy(form = form.copy(errorRes = errorRes))
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
            val result = withContext(Dispatchers.IO) {
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
            val result = withContext(Dispatchers.IO) {
                runCatching { movementRepository.listActiveForCategory(category.id) }
            }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        flowDetail = CategoryFlowDetailState(
                            category = category,
                            entries = it,
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

    fun onFlowDismissed() {
        _state.value = _state.value.copy(flowDetail = null)
    }

    private fun refreshCategories() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val result = withContext(Dispatchers.IO) {
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

    class Factory(
        private val categoryRepository: CategoryRepository,
        private val analysisRepository: AnalysisRepository,
        private val movementRepository: MovementRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CategoriesViewModel::class.java)) {
                return CategoriesViewModel(
                    categoryRepository = categoryRepository,
                    analysisRepository = analysisRepository,
                    movementRepository = movementRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class CategoriesUiState(
    val categories: List<CategoryRecord> = emptyList(),
    val monthSpend: Map<String, Long> = emptyMap(),
    val yearSpend: Map<String, Long> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: CategoryFormState? = null,
    val archiveCandidate: CategoryRecord? = null,
    val flowDetail: CategoryFlowDetailState? = null,
)

data class CategoryFlowDetailState(
    val category: CategoryRecord,
    val entries: List<MovementSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

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
