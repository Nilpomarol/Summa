package com.gestorfinances.app.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.TagDraft
import com.gestorfinances.app.data.repository.TagRepository
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripSummary
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

    fun onEditClicked(tag: TagSummary) {
        _state.value = _state.value.copy(form = tag.toFormState())
    }

    fun onArchiveClicked(tag: TagSummary) {
        _state.value = _state.value.copy(archiveCandidate = tag)
    }

    fun onArchiveDismissed() {
        _state.value = _state.value.copy(archiveCandidate = null)
    }

    fun onArchiveConfirmed() {
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

    fun onFormChanged(form: TagFormState) {
        _state.value = _state.value.copy(form = form.copy(errorRes = null, errorMessage = null))
    }

    fun onFormDismissed() {
        _state.value = _state.value.copy(form = null)
    }

    fun onSaveClicked() {
        val form = _state.value.form ?: return
        val name = form.name.trim()
        val activeTripIds = _state.value.trips.map { it.id }.toSet()
        val errorRes = when {
            name.isEmpty() -> R.string.tag_validation_name_required
            form.tripId != null && form.tripId !in activeTripIds -> R.string.tag_validation_trip_required
            else -> null
        }
        if (errorRes != null) {
            _state.value = _state.value.copy(form = form.copy(errorRes = errorRes))
            return
        }

        val now = Instant.now().toString()
        val draft = TagDraft(
            id = form.id ?: UUID.randomUUID().toString(),
            name = name,
            icon = form.icon.trim().ifBlank { null },
            color = form.color.trim().ifBlank { null },
            tripId = form.tripId,
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
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        tags = it.tags,
                        trips = it.trips,
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

    class Factory(
        private val tagRepository: TagRepository,
        private val tripRepository: TripRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TagsViewModel::class.java)) {
                return TagsViewModel(
                    tagRepository = tagRepository,
                    tripRepository = tripRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class TagsUiState(
    val contextTripId: String? = null,
    val tags: List<TagSummary> = emptyList(),
    val trips: List<TripSummary> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: TagFormState? = null,
    val archiveCandidate: TagSummary? = null,
) {
    val visibleTags: List<TagSummary>
        get() = if (contextTripId == null) {
            tags
        } else {
            tags.filter { it.tripId == null || it.tripId == contextTripId }
        }
}

data class TagFormState(
    val id: String? = null,
    val name: String = "",
    val icon: String = "",
    val color: String = "",
    val tripId: String? = null,
    val errorRes: Int? = null,
    val errorMessage: String? = null,
)

private data class LoadedTagData(
    val tags: List<TagSummary>,
    val trips: List<TripSummary>,
)

private fun TagSummary.toFormState(): TagFormState =
    TagFormState(
        id = id,
        name = name,
        icon = icon.orEmpty(),
        color = color.orEmpty(),
        tripId = tripId,
    )
