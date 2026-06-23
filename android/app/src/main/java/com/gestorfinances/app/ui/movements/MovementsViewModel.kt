package com.gestorfinances.app.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementSplitWrite
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.RefundDraft
import com.gestorfinances.app.data.repository.RefundSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.TagRepository
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.domain.rules.DuplicateDetector
import com.gestorfinances.app.domain.rules.DuplicateMovement
import com.gestorfinances.app.notifications.NotificationRefresher
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.common.parseEuroCents
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

class MovementsViewModel(
    private val movementRepository: MovementRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val personRepository: PersonRepository,
    private val tripRepository: TripRepository,
    private val tagRepository: TagRepository,
    private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(MovementsUiState())
    val state: StateFlow<MovementsUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refresh()
    }

    fun onAddClicked() {
        onAddClicked(tripId = null)
    }

    fun onAddClicked(tripId: String?) {
        viewModelScope.launch {
            val result = loadMovementData()
            result.fold(
                onSuccess = {
                    val form = if (it.accounts.isEmpty()) {
                        null
                    } else {
                        newMovementForm(it, tripId)
                    }
                    _state.value = _state.value.copy(
                        movements = it.movements,
                        accounts = it.accounts,
                        categories = it.categories,
                        people = it.people,
                        trips = it.trips,
                        tags = it.tags,
                        isLoading = false,
                        errorMessage = null,
                        form = form,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = it.message ?: it.javaClass.simpleName,
                    )
                },
            )
        }
    }

    fun onEditClicked(movement: MovementSummary) {
        _state.value = _state.value.copy(
            form = movement.toFormState(),
            detailMovement = null,
        )
    }

    fun onArchiveClicked(movement: MovementSummary) {
        _state.value = _state.value.copy(
            archiveCandidate = movement,
            detailMovement = null,
        )
    }

    fun onDetailClicked(movement: MovementSummary) {
        _state.value = _state.value.copy(detailMovement = movement, detailRefunds = emptyList())
        if (movement.type == MovementType.EXPENSE) {
            loadDetailRefunds(movement.id)
        }
    }

    fun onDetailDismissed() {
        _state.value = _state.value.copy(detailMovement = null, detailRefunds = emptyList())
    }

    private fun loadDetailRefunds(expenseId: String) {
        viewModelScope.launch {
            val refunds = withContext(ioDispatcher) {
                runCatching { movementRepository.refundsForExpense(expenseId) }.getOrDefault(emptyList())
            }
            if (_state.value.detailMovement?.id == expenseId) {
                _state.value = _state.value.copy(detailRefunds = refunds)
            }
        }
    }

    fun onAddRefundClicked(expense: MovementSummary) {
        val refundedSoFar = _state.value.detailRefunds.sumOf { it.amountCents }
        _state.value = _state.value.copy(
            detailMovement = null,
            refundForm = RefundFormState(
                expenseId = expense.id,
                expenseName = expense.name ?: expense.payee ?: expense.categoryName.orEmpty(),
                expenseIsShared = expense.isShared,
                remainingCents = (expense.amountCents - refundedSoFar).coerceAtLeast(0L),
                accountId = expense.accountId,
                categoryId = expense.categoryId,
                date = LocalDate.now().toString(),
            ),
        )
    }

    fun onRefundFormChanged(form: RefundFormState) {
        _state.value = _state.value.copy(refundForm = form.copy(errorRes = null, errorMessage = null))
    }

    fun onRefundDismissed() {
        _state.value = _state.value.copy(refundForm = null)
    }

    fun onRefundSaveClicked() {
        val form = _state.value.refundForm ?: return
        val amount = parseEuroCents(form.amount, allowNegative = false)
        val actual = if (form.expenseIsShared && form.actualAmount.isNotBlank()) {
            parseEuroCents(form.actualAmount, allowNegative = false)
        } else {
            null
        }
        val date = parseDate(form.date)
        val activeAccountIds = _state.value.accounts.map { it.id }.toSet()

        val errorRes = when {
            amount == null -> R.string.refund_validation_amount_required
            amount <= 0L -> R.string.movement_validation_amount_positive
            form.accountId == null || form.accountId !in activeAccountIds ->
                R.string.movement_validation_account_required
            form.date.isBlank() -> R.string.movement_validation_date_required
            date == null -> R.string.movement_validation_date_invalid
            actual != null && actual > amount -> R.string.refund_validation_actual_over
            else -> null
        }
        if (errorRes != null) {
            _state.value = _state.value.copy(refundForm = form.copy(errorRes = errorRes))
            return
        }

        val now = Instant.now().toString()
        val draft = RefundDraft(
            id = UUID.randomUUID().toString(),
            refundsExpenseId = form.expenseId,
            amountCents = requireNotNull(amount),
            accountId = requireNotNull(form.accountId),
            categoryId = form.categoryId,
            date = requireNotNull(date).toString(),
            name = null,
            payee = null,
            notes = form.notes.nullIfBlank(),
            actualRefundCents = actual,
        )
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { movementRepository.createRefund(draft, createdAt = now) }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(refundForm = null)
                    refresh(dataChanged = true)
                    refreshNotifications()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        refundForm = form.copy(errorMessage = it.message ?: it.javaClass.simpleName),
                    )
                },
            )
        }
    }

    fun onFiltersChanged(filters: MovementFilters) {
        _state.value = _state.value.copy(filters = filters.withDateValidation())
    }

    fun onDrillDown(filters: MovementFilters) {
        _state.value = _state.value.copy(
            filters = filters.withDateValidation(),
            detailMovement = null,
            form = null,
        )
        refresh()
    }

    fun onClearFiltersClicked() {
        _state.value = _state.value.copy(filters = MovementFilters())
    }

    fun onArchiveDismissed() {
        _state.value = _state.value.copy(archiveCandidate = null)
    }

    fun onArchiveConfirmed() {
        val movement = _state.value.archiveCandidate ?: return
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { movementRepository.archive(movement.id, archivedAt = now) }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(archiveCandidate = null)
                    refresh(dataChanged = true)
                    refreshNotifications()
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

    fun onFormChanged(form: MovementFormState) {
        _state.value = _state.value.copy(form = normalizeForm(form))
    }

    fun onTripSelected(tripId: String?) {
        val form = _state.value.form ?: return
        val trip = tripId?.let { selectedId -> _state.value.trips.firstOrNull { it.id == selectedId } }
        val tag = form.tagId?.let { tagId -> _state.value.tags.firstOrNull { it.id == tagId } }
        val accountId = if (form.id == null && trip?.defaultAccountId != null) {
            trip.defaultAccountId
        } else {
            form.accountId
        }
        val tagId = if (tag != null && tag.supportsTrip(tripId)) form.tagId else null
        onFormChanged(form.copy(tripId = tripId, tagId = tagId, accountId = accountId))
    }

    fun onTagSelected(tagId: String?) {
        val form = _state.value.form ?: return
        onFormChanged(form.copy(tagId = tagId))
    }

    fun onSharedToggled(enabled: Boolean) {
        val form = _state.value.form ?: return
        val nextForm = if (enabled) {
            form.copy(
                splitEditor = form.splitEditor ?: SplitEditorState(),
                removeExistingSplit = false,
            )
        } else {
            form.copy(
                splitEditor = null,
                removeExistingSplit = form.existingSplit,
            )
        }
        onFormChanged(nextForm)
    }

    fun onSplitEditorChanged(splitEditor: SplitEditorState) {
        val form = _state.value.form ?: return
        onFormChanged(form.copy(splitEditor = splitEditor, removeExistingSplit = false))
    }

    fun onFormDismissed() {
        _state.value = _state.value.copy(form = null)
    }

    fun onSaveClicked() = attemptSave(forceSave = false)

    /** Save anyway after the duplicate warning was shown (never block — warn). */
    fun onDuplicateOverrideClicked() = attemptSave(forceSave = true)

    private fun attemptSave(forceSave: Boolean) {
        val form = _state.value.form ?: return
        val amount = parseEuroCents(form.amount, allowNegative = false)
        val date = parseDate(form.date)
        val activeAccountIds = _state.value.accounts.map { it.id }.toSet()
        val category = form.categoryId?.let { categoryId ->
            _state.value.categories.firstOrNull { it.id == categoryId }
        }
        val tag = form.tagId?.let { tagId ->
            _state.value.tags.firstOrNull { it.id == tagId }
        }
        val splitDraft = form.splitEditor?.toMovementSplitDraft(amount)

        val errorRes = when {
            amount == null -> R.string.movement_validation_amount_required
            amount <= 0L -> R.string.movement_validation_amount_positive
            form.date.isBlank() -> R.string.movement_validation_date_required
            date == null -> R.string.movement_validation_date_invalid
            form.accountId == null || form.accountId !in activeAccountIds ->
                R.string.movement_validation_account_required
            form.type == MovementType.TRANSFER &&
                (form.destinationAccountId == null || form.destinationAccountId !in activeAccountIds) ->
                R.string.movement_validation_destination_required
            form.type == MovementType.TRANSFER && form.accountId == form.destinationAccountId ->
                R.string.movement_validation_transfer_same_account
            category != null && !category.supports(form.type) -> R.string.movement_validation_category_invalid
            form.tagId != null && (form.tripId == null || tag == null || !tag.supportsTrip(form.tripId)) ->
                R.string.tag_validation_trip_required
            form.splitEditor != null && splitDraft == null ->
                form.splitEditor.calculation(amount).errorRes ?: R.string.split_validation_reconcile
            else -> null
        }

        if (errorRes != null) {
            _state.value = _state.value.copy(form = form.copy(errorRes = errorRes))
            return
        }

        if (!forceSave && isDuplicate(form, requireNotNull(amount), requireNotNull(date))) {
            _state.value = _state.value.copy(
                form = form.copy(duplicateWarning = true, errorRes = null, errorMessage = null),
            )
            return
        }

        val now = Instant.now().toString()
        val draft = MovementDraft(
            id = form.id ?: UUID.randomUUID().toString(),
            type = form.type,
            amountCents = requireNotNull(amount),
            date = requireNotNull(date).toString(),
            accountId = requireNotNull(form.accountId),
            destinationAccountId = form.destinationAccountId.takeIf { form.type == MovementType.TRANSFER },
            categoryId = form.categoryId.takeIf { form.type != MovementType.TRANSFER },
            tripId = form.tripId,
            tagId = form.tagId,
            name = form.name.nullIfBlank(),
            payee = form.payee.nullIfBlank(),
            notes = form.notes.nullIfBlank(),
            isOneTime = form.type == MovementType.EXPENSE && form.isOneTime,
            splitWrite = when {
                form.splitEditor != null -> MovementSplitWrite.Replace(requireNotNull(splitDraft))
                form.removeExistingSplit -> MovementSplitWrite.Remove
                else -> MovementSplitWrite.KeepExisting
            },
        )

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    if (form.id == null) {
                        movementRepository.create(draft, createdAt = now)
                    } else {
                        movementRepository.update(draft, updatedAt = now)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(form = null)
                    refresh(dataChanged = true)
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

    private fun refresh(dataChanged: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val result = loadMovementData()
            val dataVersion = if (dataChanged) {
                _state.value.dataVersion + 1L
            } else {
                _state.value.dataVersion
            }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        movements = it.movements,
                        accounts = it.accounts,
                        categories = it.categories,
                        people = it.people,
                        trips = it.trips,
                        tags = it.tags,
                        isLoading = false,
                        dataVersion = dataVersion,
                    )
                },
                onFailure = {
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = it.message ?: it.javaClass.simpleName,
                        dataVersion = dataVersion,
                    )
                },
            )
        }
    }

    private suspend fun loadMovementData(): Result<LoadedMovementData> =
        withContext(ioDispatcher) {
            runCatching {
                LoadedMovementData(
                    movements = movementRepository.listActive(),
                    accounts = accountRepository.listActive(),
                    categories = categoryRepository.listActive(),
                    people = personRepository.listActive(),
                    trips = tripRepository.listActive(),
                    tags = tagRepository.listActive(),
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

    private fun normalizeForm(form: MovementFormState): MovementFormState {
        val typeChangedForm = when (form.type) {
            MovementType.TRANSFER -> form.copy(
                categoryId = null,
                isOneTime = false,
                splitEditor = null,
                removeExistingSplit = form.existingSplit || form.removeExistingSplit,
            )
            MovementType.INCOME -> form.copy(
                isOneTime = false,
                splitEditor = null,
                removeExistingSplit = form.existingSplit || form.removeExistingSplit,
            )
            MovementType.EXPENSE -> form
            else -> form
        }
        val category = typeChangedForm.categoryId?.let { categoryId ->
            _state.value.categories.firstOrNull { it.id == categoryId }
        }
        val tag = typeChangedForm.tagId?.let { tagId ->
            _state.value.tags.firstOrNull { it.id == tagId }
        }
        val categoryId = if (category != null && !category.supports(typeChangedForm.type)) {
            null
        } else {
            typeChangedForm.categoryId
        }
        val tagId = if (typeChangedForm.tripId == null || tag == null || !tag.supportsTrip(typeChangedForm.tripId)) {
            null
        } else {
            typeChangedForm.tagId
        }
        return typeChangedForm.copy(
            categoryId = categoryId,
            tagId = tagId,
            errorRes = null,
            errorMessage = null,
            duplicateWarning = false,
        )
    }

    // Warn (never block) when an active movement matches account + amount + date(±1) + name.
    private fun isDuplicate(
        form: MovementFormState,
        amountCents: Long,
        date: LocalDate,
    ): Boolean {
        val accountId = form.accountId ?: return false
        val candidateName = form.name.trim().ifEmpty { form.payee.trim() }
        if (candidateName.isEmpty()) return false
        val candidate = DuplicateMovement(accountId, amountCents, date, candidateName)
        return _state.value.movements.any { existing ->
            if (existing.id == form.id) return@any false
            val existingDate = parseDate(existing.date) ?: return@any false
            DuplicateDetector.isDuplicate(
                existing = DuplicateMovement(
                    accountId = existing.accountId,
                    amountCents = existing.amountCents,
                    date = existingDate,
                    name = existing.name ?: existing.payee ?: "",
                ),
                candidate = candidate,
            )
        }
    }

    class Factory(
        private val movementRepository: MovementRepository,
        private val accountRepository: AccountRepository,
        private val categoryRepository: CategoryRepository,
        private val personRepository: PersonRepository,
        private val tripRepository: TripRepository,
        private val tagRepository: TagRepository,
        private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MovementsViewModel::class.java)) {
                return MovementsViewModel(
                    movementRepository = movementRepository,
                    accountRepository = accountRepository,
                    categoryRepository = categoryRepository,
                    personRepository = personRepository,
                    tripRepository = tripRepository,
                    tagRepository = tagRepository,
                    notificationRefresher = notificationRefresher,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class MovementsUiState(
    val movements: List<MovementSummary> = emptyList(),
    val accounts: List<AccountSummary> = emptyList(),
    val categories: List<CategoryRecord> = emptyList(),
    val people: List<PersonSummary> = emptyList(),
    val trips: List<TripSummary> = emptyList(),
    val tags: List<TagSummary> = emptyList(),
    val filters: MovementFilters = MovementFilters(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: MovementFormState? = null,
    val detailMovement: MovementSummary? = null,
    val detailRefunds: List<RefundSummary> = emptyList(),
    val refundForm: RefundFormState? = null,
    val archiveCandidate: MovementSummary? = null,
    val dataVersion: Long = 0L,
) {
    val visibleMovements: List<MovementSummary>
        get() = movements.filter { filters.matches(it) }
}

data class MovementFilters(
    val query: String = "",
    val type: MovementType? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val tripId: String? = null,
    val tagId: String? = null,
    val uncategorizedOnly: Boolean = false,
    val sourceMode: MovementSourceMode? = null,
    val categoryNature: CategoryNature? = null,
    val oneTimeMode: MovementOneTimeMode = MovementOneTimeMode.INCLUDE,
    val dateFrom: String = "",
    val dateTo: String = "",
    val errorRes: Int? = null,
) {
    val hasAdvancedFilters: Boolean
        get() = accountId != null ||
            categoryId != null ||
            tripId != null ||
            tagId != null ||
            uncategorizedOnly ||
            dateFrom.isNotBlank() ||
            dateTo.isNotBlank() ||
            sourceMode != null ||
            categoryNature != null ||
            oneTimeMode != MovementOneTimeMode.INCLUDE
}

enum class MovementSourceMode {
    ACTUAL,
    FLOW,
}

enum class MovementOneTimeMode {
    INCLUDE,
    EXCLUDE,
    ONLY,
}

data class MovementFormState(
    val id: String? = null,
    val type: MovementType = MovementType.EXPENSE,
    val amount: String = "",
    val date: String = "",
    val accountId: String? = null,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val tripId: String? = null,
    val tagId: String? = null,
    val name: String = "",
    val payee: String = "",
    val notes: String = "",
    val isOneTime: Boolean = false,
    val existingSplit: Boolean = false,
    val removeExistingSplit: Boolean = false,
    val splitEditor: SplitEditorState? = null,
    val duplicateWarning: Boolean = false,
    val errorRes: Int? = null,
    val errorMessage: String? = null,
)

data class RefundFormState(
    val expenseId: String,
    val expenseName: String,
    val expenseIsShared: Boolean,
    val remainingCents: Long,
    val amount: String = "",
    val actualAmount: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
    val date: String = "",
    val notes: String = "",
    val errorRes: Int? = null,
    val errorMessage: String? = null,
)

private data class LoadedMovementData(
    val movements: List<MovementSummary>,
    val accounts: List<AccountSummary>,
    val categories: List<CategoryRecord>,
    val people: List<PersonSummary>,
    val trips: List<TripSummary>,
    val tags: List<TagSummary>,
)

private fun defaultAccountId(accounts: List<AccountSummary>): String? =
    accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id

private fun newMovementForm(
    data: LoadedMovementData,
    tripId: String?,
): MovementFormState {
    val trip = tripId?.let { selectedId -> data.trips.firstOrNull { it.id == selectedId } }
    return MovementFormState(
        accountId = trip?.defaultAccountId ?: defaultAccountId(data.accounts),
        tripId = trip?.id,
        date = LocalDate.now().toString(),
    )
}

private fun MovementFilters.withDateValidation(): MovementFilters {
    val from = parseDateOrNull(dateFrom)
    val to = parseDateOrNull(dateTo)
    val error = when {
        dateFrom.isNotBlank() && from == null -> R.string.movement_filter_date_invalid
        dateTo.isNotBlank() && to == null -> R.string.movement_filter_date_invalid
        from != null && to != null && from > to -> R.string.movement_filter_date_order_invalid
        else -> null
    }
    return copy(errorRes = error)
}

private fun MovementFilters.matches(movement: MovementSummary): Boolean {
    if (type != null && movement.type != type) return false
    if (sourceMode == MovementSourceMode.ACTUAL && movement.type !in actualMovementTypes) return false
    if (accountId != null && movement.accountId != accountId && movement.destinationAccountId != accountId) {
        return false
    }
    if (categoryId != null && movement.categoryId != categoryId) return false
    if (tripId != null && movement.tripId != tripId) return false
    if (tagId != null && movement.tagId != tagId) return false
    if (uncategorizedOnly && movement.categoryId != null) return false
    if (categoryNature != null && movement.categoryNature != categoryNature) return false
    when (oneTimeMode) {
        MovementOneTimeMode.INCLUDE -> Unit
        MovementOneTimeMode.EXCLUDE -> if (movement.isOneTime) return false
        MovementOneTimeMode.ONLY -> if (!movement.isOneTime) return false
    }

    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isNotEmpty()) {
        val searchable = listOfNotNull(
            movement.name,
            movement.payee,
            movement.notes,
            movement.categoryName,
            movement.tripName,
            movement.tagName,
            movement.accountName,
            movement.destinationAccountName,
        ).joinToString(separator = " ").lowercase()
        if (normalizedQuery !in searchable) return false
    }

    val movementDate = parseDateOrNull(movement.date) ?: return false
    parseDateOrNull(dateFrom)?.let { if (movementDate < it) return false }
    parseDateOrNull(dateTo)?.let { if (movementDate > it) return false }
    return true
}

private fun MovementSummary.toFormState(): MovementFormState =
    MovementFormState(
        id = id,
        type = type,
        amount = formatEuroInput(amountCents),
        date = date,
        accountId = accountId,
        destinationAccountId = destinationAccountId,
        categoryId = categoryId,
        tripId = tripId,
        tagId = tagId,
        name = name.orEmpty(),
        payee = payee.orEmpty(),
        notes = notes.orEmpty(),
        isOneTime = isOneTime,
        existingSplit = isShared,
    )

private fun CategoryRecord.supports(type: MovementType): Boolean =
    when (type) {
        MovementType.EXPENSE -> kind == CategoryKind.EXPENSE || kind == CategoryKind.BOTH
        MovementType.INCOME -> kind == CategoryKind.INCOME || kind == CategoryKind.BOTH
        else -> false
    }

private fun TagSummary.supportsTrip(tripId: String?): Boolean =
    tripId != null && (this.tripId == null || this.tripId == tripId)

private fun parseDate(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }

private fun parseDateOrNull(raw: String): LocalDate? =
    raw.trim().takeIf { it.isNotEmpty() }?.let(::parseDate)

private fun String.nullIfBlank(): String? =
    trim().takeIf { it.isNotEmpty() }

private val actualMovementTypes = setOf(
    MovementType.EXPENSE,
    MovementType.INCOME,
    MovementType.REFUND,
)
