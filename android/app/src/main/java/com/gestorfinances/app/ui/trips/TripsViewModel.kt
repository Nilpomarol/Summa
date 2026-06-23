package com.gestorfinances.app.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.TripAnalysisRepository
import com.gestorfinances.app.data.repository.TripAnalysisSummary
import com.gestorfinances.app.data.repository.TripCategoryActual
import com.gestorfinances.app.data.repository.TripDailyActual
import com.gestorfinances.app.data.repository.TripDraft
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripStatus
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.TripTagActual
import com.gestorfinances.app.data.repository.TripType
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TripsViewModel(
    private val tripRepository: TripRepository,
    private val tripAnalysisRepository: TripAnalysisRepository,
    private val movementRepository: MovementRepository,
    private val accountRepository: AccountRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(TripsUiState())
    val state: StateFlow<TripsUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refresh()
    }

    fun onStatusFilterChanged(status: TripStatus?) {
        _state.value = _state.value.copy(statusFilter = status)
    }

    fun onAddClicked() {
        _state.value = _state.value.copy(form = TripFormState())
    }

    fun onEditClicked(trip: TripSummary) {
        _state.value = _state.value.copy(form = trip.toFormState(), detail = null)
    }

    fun onDetailClicked(trip: TripSummary) {
        _state.value = _state.value.copy(
            detail = TripDetailState(trip = trip, isLoading = true),
        )
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    TripDetailState(
                        trip = tripRepository.getActive(trip.id) ?: trip,
                        summary = tripAnalysisRepository.summary(trip.id),
                        dailyActual = tripAnalysisRepository.actualByDay(trip.id),
                        categoryActual = tripAnalysisRepository.actualByCategory(trip.id),
                        tagActual = tripAnalysisRepository.actualByTag(trip.id),
                        movements = movementRepository.listActive().filter { it.tripId == trip.id },
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = { _state.value.copy(detail = it) },
                onFailure = {
                    _state.value.copy(
                        detail = TripDetailState(
                            trip = trip,
                            errorMessage = it.message ?: it.javaClass.simpleName,
                        ),
                    )
                },
            )
        }
    }

    fun onDetailClicked(tripId: String) {
        _state.value.trips.firstOrNull { it.id == tripId }?.let {
            onDetailClicked(it)
            return
        }
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { tripRepository.getActive(tripId) }
            }
            result.fold(
                onSuccess = { trip ->
                    if (trip != null) {
                        onDetailClicked(trip)
                    }
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        errorMessage = it.message ?: it.javaClass.simpleName,
                    )
                },
            )
        }
    }

    fun onDetailDismissed() {
        _state.value = _state.value.copy(detail = null)
    }

    fun onArchiveClicked(trip: TripSummary) {
        _state.value = _state.value.copy(archiveCandidate = trip, detail = null)
    }

    fun onArchiveDismissed() {
        _state.value = _state.value.copy(archiveCandidate = null)
    }

    fun onArchiveConfirmed() {
        val trip = _state.value.archiveCandidate ?: return
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { tripRepository.archive(trip.id, archivedAt = now) }
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

    fun onFormChanged(form: TripFormState) {
        _state.value = _state.value.copy(form = form.copy(errorRes = null, errorMessage = null))
    }

    fun onFormDismissed() {
        _state.value = _state.value.copy(form = null)
    }

    fun onSaveClicked() {
        val form = _state.value.form ?: return
        val name = form.name.trim()
        val startDate = parseDateOrNull(form.startDate)
        val endDate = parseDateOrNull(form.endDate)
        val activeAccountIds = _state.value.accounts.map { it.id }.toSet()

        val errorRes = when {
            name.isEmpty() -> R.string.trip_validation_name_required
            form.startDate.isNotBlank() && startDate == null -> R.string.trip_validation_start_date_invalid
            form.endDate.isNotBlank() && endDate == null -> R.string.trip_validation_end_date_invalid
            startDate != null && endDate != null && endDate < startDate -> R.string.trip_validation_date_order
            form.defaultAccountId != null && form.defaultAccountId !in activeAccountIds -> R.string.movement_validation_account_required
            else -> null
        }

        if (errorRes != null) {
            _state.value = _state.value.copy(form = form.copy(errorRes = errorRes))
            return
        }

        val now = Instant.now().toString()
        val draft = TripDraft(
            id = form.id ?: UUID.randomUUID().toString(),
            name = name,
            type = form.type,
            status = form.status,
            startDate = startDate?.toString(),
            endDate = endDate?.toString(),
            icon = form.icon.trim().ifBlank { null },
            color = form.color.trim().ifBlank { null },
            notes = form.notes.trim().ifBlank { null },
            defaultAccountId = form.defaultAccountId,
        )

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    if (form.id == null) {
                        tripRepository.create(draft, createdAt = now)
                    } else {
                        tripRepository.update(draft, updatedAt = now)
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
                    LoadedTripData(
                        trips = tripRepository.listActive(),
                        accounts = accountRepository.listActive(),
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        trips = it.trips,
                        accounts = it.accounts,
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
        private val tripRepository: TripRepository,
        private val tripAnalysisRepository: TripAnalysisRepository,
        private val movementRepository: MovementRepository,
        private val accountRepository: AccountRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TripsViewModel::class.java)) {
                return TripsViewModel(
                    tripRepository = tripRepository,
                    tripAnalysisRepository = tripAnalysisRepository,
                    movementRepository = movementRepository,
                    accountRepository = accountRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class TripsUiState(
    val trips: List<TripSummary> = emptyList(),
    val accounts: List<AccountSummary> = emptyList(),
    val statusFilter: TripStatus? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: TripFormState? = null,
    val detail: TripDetailState? = null,
    val archiveCandidate: TripSummary? = null,
) {
    val visibleTrips: List<TripSummary>
        get() = trips.filter { statusFilter == null || it.status == statusFilter }
}

data class TripFormState(
    val id: String? = null,
    val name: String = "",
    val type: TripType = TripType.TRIP,
    val status: TripStatus = TripStatus.ACTIVE,
    val startDate: String = "",
    val endDate: String = "",
    val icon: String = "",
    val color: String = "",
    val notes: String = "",
    val defaultAccountId: String? = null,
    val errorRes: Int? = null,
    val errorMessage: String? = null,
)

data class TripDetailState(
    val trip: TripSummary,
    val summary: TripAnalysisSummary = TripAnalysisSummary(0L, 0L),
    val dailyActual: List<TripDailyActual> = emptyList(),
    val categoryActual: List<TripCategoryActual> = emptyList(),
    val tagActual: List<TripTagActual> = emptyList(),
    val movements: List<MovementSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

private data class LoadedTripData(
    val trips: List<TripSummary>,
    val accounts: List<AccountSummary>,
)

private fun TripSummary.toFormState(): TripFormState =
    TripFormState(
        id = id,
        name = name,
        type = type,
        status = status,
        startDate = startDate.orEmpty(),
        endDate = endDate.orEmpty(),
        icon = icon.orEmpty(),
        color = color.orEmpty(),
        notes = notes.orEmpty(),
        defaultAccountId = defaultAccountId,
    )

private fun parseDateOrNull(raw: String): LocalDate? =
    raw.trim().takeIf { it.isNotEmpty() }?.let {
        try {
            LocalDate.parse(it)
        } catch (_: DateTimeParseException) {
            null
        }
    }
