package com.gestorfinances.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.CategoryDraft
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CategoriesViewModel(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CategoriesUiState())
    val state: StateFlow<CategoriesUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refreshCategories()
    }

    fun onAddClicked() {
        val nextOrder = (_state.value.categories.maxOfOrNull { it.displayOrder } ?: -1L) + 1L
        _state.value = _state.value.copy(form = CategoryFormState(displayOrder = nextOrder))
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
            icon = null,
            color = null,
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

    private fun refreshCategories() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val result = withContext(Dispatchers.IO) {
                runCatching { categoryRepository.listActive() }
            }
            _state.value = result.fold(
                onSuccess = { _state.value.copy(categories = it, isLoading = false) },
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
        private val categoryRepository: CategoryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CategoriesViewModel::class.java)) {
                return CategoriesViewModel(categoryRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class CategoriesUiState(
    val categories: List<CategoryRecord> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: CategoryFormState? = null,
    val archiveCandidate: CategoryRecord? = null,
)

data class CategoryFormState(
    val id: String? = null,
    val name: String = "",
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val nature: CategoryNature = CategoryNature.VARIABLE,
    val parentId: String? = null,
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
        displayOrder = displayOrder,
    )
