package com.gestorfinances.app.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.TagDraft
import com.gestorfinances.app.data.repository.TagRepository
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.TripType
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TagsViewModel(
    private val tagRepository: TagRepository,
    private val tripRepository: TripRepository,
    private val categoryRepository: CategoryRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(TagsUiState())
    val state: StateFlow<TagsUiState> = _state.asStateFlow()

    fun onScreenShown(contextTripId: String?) {
        if (_state.value.contextTripId != contextTripId) {
            _state.value = TagsUiState(contextTripId = contextTripId)
        }
        refresh()
    }

    fun onAddClicked() {
        _state.value = _state.value.copy(
            form = TagFormState(tripId = _state.value.contextTripId),
        )
    }

    fun onSearchChanged(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun onEditClicked(tag: TagSummary) {
        _state.value = _state.value.copy(form = tag.toFormState())
    }

    fun onArchiveClicked(tag: TagSummary) {
        _state.value = _state.value.copy(archiveCandidate = tag)
    }

    fun onArchiveDismissed() {
        _state.value = _state.value.copy(archiveCandidate = null)
    }

    fun onArchiveConfirmed(onSuccess: (undo: () -> Unit) -> Unit = {}) {
        val tag = _state.value.archiveCandidate ?: return
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { tagRepository.archive(tag.id, archivedAt = now) }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(archiveCandidate = null)
                    refresh()
                    onSuccess { undoDelete(tag.id, deletedAt = now) }
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

    private fun undoDelete(tagId: String, deletedAt: String) {
        val restoredAt = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { tagRepository.restore(tagId, deletedAt, restoredAt) }
            }
            result.fold(
                onSuccess = { refresh() },
                onFailure = { _state.value = _state.value.copy(errorMessage = it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    fun onFormChanged(form: TagFormState) {
        _state.value = _state.value.copy(
            form = form.copy(errorRes = null, errorField = null, errorMessage = null),
        )
    }

    fun onFormDismissed() {
        _state.value = _state.value.copy(form = null)
    }

    fun onSaveClicked() {
        val form = _state.value.form ?: return
        val name = form.name.trim()
        val activeTripIds = _state.value.trips.map { it.id }.toSet()
        val (errorRes, errorField) = when {
            name.isEmpty() -> R.string.tag_validation_name_required to TagFormField.NAME
            form.tripId != null && form.tripId !in activeTripIds ->
                R.string.tag_validation_trip_required to TagFormField.TRIP
            // Mirrors the schema's CHECK (trip_id IS NULL OR trip_type IS NULL): a tag is
            // global, event-type-scoped, or trip-specific — never two of those at once.
            form.tripId != null && form.tripType != null ->
                R.string.tag_validation_scope_exclusive to TagFormField.TRIP
            else -> null to null
        }
        if (errorRes != null) {
            _state.value = _state.value.copy(form = form.copy(errorRes = errorRes, errorField = errorField))
            return
        }

        val now = Instant.now().toString()
        val draft = TagDraft(
            id = form.id ?: UUID.randomUUID().toString(),
            name = name,
            icon = form.icon.trim().ifBlank { null },
            color = form.color.trim().ifBlank { null },
            tripId = form.tripId,
            categoryId = form.categoryId,
            tripType = form.tripType,
        )

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    if (form.id == null) {
                        tagRepository.create(draft, createdAt = now)
                    } else {
                        tagRepository.update(draft, updatedAt = now)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(form = null)
                    refresh()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        form = form.copy(errorMessage = it.message ?: it.javaClass.simpleName),
                    )
                },
            )
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val result = withContext(ioDispatcher) {
                runCatching {
                    LoadedTagData(
                        tags = tagRepository.listActive(),
                        trips = tripRepository.listActive(),
                        categories = categoryRepository.listActive(),
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        tags = it.tags,
                        trips = it.trips,
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
}

data class TagsUiState(
    val contextTripId: String? = null,
    val tags: List<TagSummary> = emptyList(),
    val trips: List<TripSummary> = emptyList(),
    val categories: List<CategoryRecord> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val form: TagFormState? = null,
    val archiveCandidate: TagSummary? = null,
) {
    val visibleTags: List<TagSummary>
        get() {
            val scopedTags = if (contextTripId == null) {
                tags
            } else {
                tags.filter { it.tripId == null || it.tripId == contextTripId }
            }
            val query = searchQuery.trim()
            return if (query.isEmpty()) {
                scopedTags
            } else {
                scopedTags.filter { tag ->
                    tag.name.contains(query, ignoreCase = true) ||
                        tag.categoryName?.contains(query, ignoreCase = true) == true ||
                        tag.tripName?.contains(query, ignoreCase = true) == true
                }
            }
        }
}

/** Identifies which field a tag-form validation error belongs to (field-level validation). */
enum class TagFormField {
    NAME,
    TRIP,
}

data class TagFormState(
    val id: String? = null,
    val name: String = "",
    val icon: String = "",
    val color: String = "",
    val tripId: String? = null,
    val categoryId: String? = null,
    val tripType: TripType? = null,
    val errorRes: Int? = null,
    val errorField: TagFormField? = null,
    val errorMessage: String? = null,
)

private data class LoadedTagData(
    val tags: List<TagSummary>,
    val trips: List<TripSummary>,
    val categories: List<CategoryRecord>,
)

private fun TagSummary.toFormState(): TagFormState =
    TagFormState(
        id = id,
        name = name,
        icon = icon.orEmpty(),
        color = color.orEmpty(),
        tripId = tripId,
        categoryId = categoryId,
        tripType = tripType,
    )
