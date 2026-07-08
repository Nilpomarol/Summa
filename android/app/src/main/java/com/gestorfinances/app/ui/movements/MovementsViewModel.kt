package com.gestorfinances.app.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AutoCatRuleRepository
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.ExternalSplitDraft
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementSplitWrite
import com.gestorfinances.app.data.repository.MovementSplitDraft
import com.gestorfinances.app.data.repository.SplitEntryMethod
import com.gestorfinances.app.data.repository.SplitLineDraft
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonDraft
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.RefundDraft
import com.gestorfinances.app.data.repository.RefundSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.data.repository.SettlementDraft
import com.gestorfinances.app.data.repository.SplitRepository
import com.gestorfinances.app.data.repository.SplitParticipantKind
import com.gestorfinances.app.data.repository.TagRepository
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TemplateDraft
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateSplitConfig
import com.gestorfinances.app.data.repository.TemplateSplitConfigLine
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.domain.rules.AutoCategorizeMovement
import com.gestorfinances.app.domain.rules.AutoCategorizeRule
import com.gestorfinances.app.domain.rules.AutoCategorizer
import com.gestorfinances.app.domain.rules.DuplicateDetector
import com.gestorfinances.app.domain.rules.DuplicateMovement
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.domain.rules.RecurringAdvancer
import com.gestorfinances.app.domain.rules.toRecurrenceRule
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
    private val splitRepository: SplitRepository? = null,
    private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val templateRepository: TemplateRepository? = null,
    private val autoCatRuleRepository: AutoCatRuleRepository? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(MovementsUiState())
    val state: StateFlow<MovementsUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refresh()
    }

    fun onAddClicked() {
        onAddClicked(tripId = null)
    }

    fun onAddClicked(tripId: String?, debtPayerPersonId: String? = null) {
        viewModelScope.launch {
            val result = loadMovementData()
            result.fold(
                onSuccess = {
                    val form = if (it.accounts.isEmpty() && debtPayerPersonId == null) {
                        null
                    } else {
                        newMovementForm(it, tripId, debtPayerPersonId)
                    }
                    _state.value = _state.value.copy(
                        movements = it.movements,
                        accounts = it.accounts,
                        categories = it.categories,
                        people = it.people,
                        trips = it.trips,
                        tags = it.tags,
                        autoCatRules = it.autoCatRules,
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
        if (movement.type == MovementType.SETTLEMENT || movement.type == MovementType.REFUND) return
        viewModelScope.launch {
            val splitDraft = when {
                movement.isShared -> withContext(ioDispatcher) {
                    splitRepository?.getForMovement(movement.id)
                }
                movement.type == MovementType.EXTERNAL_EXPENSE -> withContext(ioDispatcher) {
                    splitRepository?.getForMovementById(movement.id)
                }
                else -> null
            }
            _state.value = _state.value.copy(
                form = movement.toFormState(splitDraft),
                detailMovement = null
            )
        }
    }

    /** [ArchiveCandidate.revertibleTemplateId] is set only when [movement] is provably the
     * immediate prior occurrence (see [isImmediatePriorOccurrence]) of a still-ACTIVE template --
     * the archive dialog then offers to roll that template's due date back to this movement's
     * date. Paused/ended templates are excluded: their due date isn't currently in play (they're
     * not scanned for due-prompts), so silently rewinding it would be confusing, not useful. */
    fun onArchiveClicked(movement: MovementSummary) {
        _state.value = _state.value.copy(detailMovement = null)
        viewModelScope.launch {
            val revertibleTemplateId = withContext(ioDispatcher) {
                movement.templateId?.let { templateId ->
                    templateRepository?.getActive(templateId)?.takeIf { template ->
                        template.status == TemplateStatus.ACTIVE &&
                            RecurringAdvancer.isImmediatePriorOccurrence(
                                template.toRecurrenceRule(),
                                LocalDate.parse(movement.date),
                                LocalDate.parse(template.nextDueDate),
                            )
                    }?.id
                }
            }
            _state.value = _state.value.copy(archiveCandidate = ArchiveCandidate(movement, revertibleTemplateId))
        }
    }

    fun onDetailClicked(movement: MovementSummary) {
        _state.value = _state.value.copy(detailMovement = movement, detailRefunds = emptyList())
        if (movement.type == MovementType.EXPENSE) {
            loadDetailRefunds(movement.id)
        }
    }

    fun onDetailSourceClicked(sourceId: String) {
        viewModelScope.launch {
            val movement = withContext(ioDispatcher) {
                runCatching { movementRepository.getActive(sourceId) }.getOrNull()
            } ?: return@launch

            onDetailClicked(movement)
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

    fun onArchiveConfirmed(revertDueDate: Boolean = false) {
        val candidate = _state.value.archiveCandidate ?: return
        val movement = candidate.movement
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    movementRepository.runInTransaction {
                        if (movement.type == MovementType.EXTERNAL_EXPENSE) {
                            requireNotNull(splitRepository) { "split repository unavailable" }
                                .archiveExternalSplit(movement.id, archivedAt = now)
                        } else {
                            movementRepository.archive(movement.id, archivedAt = now)
                        }
                        if (revertDueDate) {
                            candidate.revertibleTemplateId?.let { templateId ->
                                // Passing an earlier date "reverts" the cursor; safe here because
                                // isImmediatePriorOccurrence already proved movement.date is
                                // exactly the step immediately preceding the current cursor.
                                requireNotNull(templateRepository) { "template repository unavailable" }
                                    .advanceCursor(templateId, movement.date, updatedAt = now)
                            }
                        }
                    }
                }
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
        val accountId = if (form.isNew && trip?.defaultAccountId != null) {
            trip.defaultAccountId
        } else {
            form.accountId
        }
        val tagId = if (tag != null && tag.supportsTrip(trip)) form.tagId else null
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
                expenseKind = ExpenseKind.SHARED,
                splitEditor = form.splitEditor ?: SplitEditorState(),
                removeExistingSplit = false,
            )
        } else {
            form.copy(
                expenseKind = ExpenseKind.PERSONAL,
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

    fun onSettlementToggled(enabled: Boolean) {
        val form = _state.value.form ?: return
        onFormChanged(form.copy(isSettlement = enabled, settlementPersonId = if (enabled) form.settlementPersonId else null))
    }

    fun onSettlementPersonSelected(personId: String?) {
        val form = _state.value.form ?: return
        onFormChanged(form.copy(settlementPersonId = personId))
    }

    fun onOtherPersonSelected(personId: String?) {
        val form = _state.value.form ?: return
        onFormChanged(form.copy(forOtherPersonId = personId))
    }

    fun onRecurringToggled(enabled: Boolean) {
        val form = _state.value.form ?: return
        onFormChanged(form.copy(isRecurring = enabled))
    }

    fun onRecurringFrequencyChanged(frequency: RecurrenceFrequency) {
        val form = _state.value.form ?: return
        onFormChanged(form.copy(recurringFrequency = frequency))
    }

    fun onAdvancedToggled() {
        val form = _state.value.form ?: return
        _state.value = _state.value.copy(form = form.copy(showAdvanced = !form.showAdvanced))
    }

    fun onCreatePersonInSplit(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            val now = Instant.now().toString()
            val personId = UUID.randomUUID().toString()
            val result = withContext(ioDispatcher) {
                runCatching {
                    personRepository.create(
                        PersonDraft(id = personId, name = trimmedName, avatar = null, color = null, notes = null),
                        createdAt = now,
                    )
                    personRepository.listActive()
                }
            }
            result.fold(
                onSuccess = { newPeople ->
                    val form = _state.value.form ?: return@fold
                    val updatedSplit = form.splitEditor?.withPersonToggled(personId)
                    _state.value = _state.value.copy(
                        people = newPeople,
                        form = form.copy(splitEditor = updatedSplit ?: form.splitEditor),
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        form = _state.value.form?.copy(errorMessage = it.message ?: it.javaClass.simpleName),
                    )
                },
            )
        }
    }

    fun onFormDismissed() {
        _state.value = _state.value.copy(form = null)
    }

    fun onSaveClicked() = attemptSave(forceSave = false)

    /** Save anyway after the duplicate warning was shown (never block — warn). */
    fun onDuplicateOverrideClicked() = attemptSave(forceSave = true)

    private fun attemptSave(forceSave: Boolean) {
        val form = _state.value.form ?: return

        // Settlement save path (income + liquidació toggle)
        if (form.type == MovementType.INCOME && form.isSettlement) {
            val amount = parseEuroCents(form.amount, allowNegative = false)
            val date = parseDate(form.date)
            val activeAccountIds = _state.value.accounts.map { it.id }.toSet()
            val activePersonIds = _state.value.people.map { it.id }.toSet()
            val errorRes = when {
                amount == null -> R.string.movement_validation_amount_required
                amount <= 0L -> R.string.movement_validation_amount_positive
                form.date.isBlank() -> R.string.movement_validation_date_required
                date == null -> R.string.movement_validation_date_invalid
                form.accountId == null || form.accountId !in activeAccountIds ->
                    R.string.movement_validation_account_required
                form.settlementPersonId == null || form.settlementPersonId !in activePersonIds ->
                    R.string.settlement_validation_person_required
                else -> null
            }
            if (errorRes != null) {
                _state.value = _state.value.copy(form = form.copy(errorRes = errorRes))
                return
            }
            val now = Instant.now().toString()
            val draft = SettlementDraft(
                id = UUID.randomUUID().toString(),
                personId = requireNotNull(form.settlementPersonId),
                direction = SettlementDirection.PERSON_TO_USER,
                amountCents = requireNotNull(amount),
                accountId = requireNotNull(form.accountId),
                date = requireNotNull(date).toString(),
                notes = form.notes.nullIfBlank(),
            )
            viewModelScope.launch {
                val result = withContext(ioDispatcher) {
                    runCatching { movementRepository.createSettlement(draft, createdAt = now) }
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
            return
        }

        // DEBT path (type 4): someone else paid — stored as an external split.
        if (form.type == MovementType.EXPENSE && form.expenseKind == ExpenseKind.DEBT) {
            val amount = parseEuroCents(form.amount, allowNegative = false)
            val date = parseDate(form.date)
            val activePersonIds = _state.value.people.map { it.id }.toSet()
            val errorRes = when {
                amount == null -> R.string.movement_validation_amount_required
                amount <= 0L -> R.string.movement_validation_amount_positive
                form.date.isBlank() -> R.string.movement_validation_date_required
                date == null -> R.string.movement_validation_date_invalid
                form.forOtherPersonId == null || form.forOtherPersonId !in activePersonIds ->
                    R.string.settlement_validation_person_required
                else -> null
            }
            if (errorRes != null) {
                _state.value = _state.value.copy(form = form.copy(errorRes = errorRes))
                return
            }
            val now = Instant.now().toString()
            val draft = ExternalSplitDraft(
                id = UUID.randomUUID().toString(),
                payerPersonId = requireNotNull(form.forOtherPersonId),
                totalAmountCents = requireNotNull(amount),
                userShareCents = requireNotNull(amount),
                date = requireNotNull(date).toString(),
                description = form.name.nullIfBlank(),
                categoryId = form.categoryId,
                tripId = form.tripId,
                tagId = form.tagId,
            )
            val repo = splitRepository
            if (repo == null) {
                _state.value = _state.value.copy(
                    form = form.copy(errorMessage = "Internal error: split repository unavailable"),
                )
                return
            }
            viewModelScope.launch {
                val result = withContext(ioDispatcher) {
                    runCatching {
                        movementRepository.runInTransaction {
                            when {
                                form.externalSplitId != null ->
                                    repo.replaceExternalSplit(form.externalSplitId, draft, now)
                                form.movementId != null -> {
                                    // Kind switch: the edited entity used to be a direct movement
                                    // (personal/shared/for-other) and the payer was just switched
                                    // to "someone else" -- archive the old movement (and its split,
                                    // if any) and create a fresh external split so it isn't
                                    // double-counted (audit BLOCKER).
                                    movementRepository.archive(form.movementId, archivedAt = now)
                                    repo.createExternalPaidByPerson(draft, createdAt = now)
                                }
                                else -> repo.createExternalPaidByPerson(draft, createdAt = now)
                            }
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
            return
        }

        val amount = parseEuroCents(form.amount, allowNegative = false)
        val date = parseDate(form.date)
        val activeAccountIds = _state.value.accounts.map { it.id }.toSet()
        val activePersonIds = _state.value.people.map { it.id }.toSet()
        val category = form.categoryId?.let { categoryId ->
            _state.value.categories.firstOrNull { it.id == categoryId }
        }
        val tag = form.tagId?.let { tagId ->
            _state.value.tags.firstOrNull { it.id == tagId }
        }
        val trip = form.tripId?.let { tripId ->
            _state.value.trips.firstOrNull { it.id == tripId }
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
            form.tagId != null && (form.tripId == null || tag == null || !tag.supportsTrip(trip)) ->
                R.string.tag_validation_trip_required
            form.expenseKind == ExpenseKind.FOR_OTHER &&
                (form.forOtherPersonId == null || form.forOtherPersonId !in activePersonIds) ->
                R.string.settlement_validation_person_required
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
        // A new recurring movement seeds a template and links to it, so the saved movement reads as
        // recurring (🔁) and future occurrences are scheduled.
        // We also allow toggling recurrence ON for an existing movement that wasn't recurring.
        val isNewRecurrence = form.isRecurring && form.templateId == null
        val recurringTemplateId = if (isNewRecurrence && templateRepository != null) {
            UUID.randomUUID().toString()
        } else {
            form.templateId.takeIf { form.isRecurring }
        }

        val draft = MovementDraft(
            id = form.movementId ?: UUID.randomUUID().toString(),
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
                form.expenseKind == ExpenseKind.FOR_OTHER && form.forOtherPersonId != null ->
                    MovementSplitWrite.Replace(
                        MovementSplitDraft(
                            entryMethod = SplitEntryMethod.EXACT,
                            lines = listOf(
                                SplitLineDraft(SplitParticipantKind.USER, null, 0L),
                                SplitLineDraft(SplitParticipantKind.PERSON, form.forOtherPersonId, requireNotNull(amount)),
                            ),
                        ),
                    )
                form.splitEditor != null -> MovementSplitWrite.Replace(requireNotNull(splitDraft).let { d ->
                    // Preserve percentages if the method is PERCENTAGE
                    if (d.entryMethod == SplitEntryMethod.PERCENTAGE) {
                        d.copy(lines = d.lines.map { line ->
                            val participantId = line.personId ?: USER_PARTICIPANT_ID
                            val rawPercent = form.splitEditor.percentages[participantId].orEmpty()
                            line.copy(owedPercent = parsePercentBasisPoints(rawPercent)?.toDouble()?.div(100.0))
                        })
                    } else d
                })
                form.removeExistingSplit -> MovementSplitWrite.Remove
                else -> MovementSplitWrite.KeepExisting
            },
            templateId = recurringTemplateId,
        )

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    movementRepository.runInTransaction {
                        // Template first so the movement's template_id FK resolves, both writes atomic.
                        if (isNewRecurrence && recurringTemplateId != null) {
                            // draft.splitWrite is KeepExisting when the user toggled "make
                            // recurring" on an already-shared movement without touching the split
                            // editor — the movement's own write correctly leaves it untouched, but
                            // there is no split data in that value to carry into the new template.
                            // Read the movement's actual current split in that case so the
                            // template still gets one.
                            val splitForTemplate = resolveSplitForTemplateCarryForward(form, draft.splitWrite)
                            createQuickTemplate(
                                recurringTemplateId,
                                form,
                                requireNotNull(amount),
                                requireNotNull(date),
                                now,
                                splitForTemplate,
                            )
                        }
                        when {
                            form.externalSplitId != null -> {
                                // Kind switch: the edited entity used to be a DEBT ("someone else
                                // paid") external split and the payer was just switched back to
                                // "jo" -- archive the old split and create a fresh movement so it
                                // isn't double-counted (audit BLOCKER).
                                requireNotNull(splitRepository) { "split repository unavailable" }
                                    .archiveExternalSplit(form.externalSplitId, archivedAt = now)
                                movementRepository.create(draft, createdAt = now)
                            }
                            form.movementId == null -> movementRepository.create(draft, createdAt = now)
                            else -> movementRepository.update(draft, updatedAt = now)
                        }
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

    /**
     * [splitWrite] carries no split data when it's [MovementSplitWrite.KeepExisting] — that value
     * only means "don't touch the movement's split," which is fine for the movement's own write
     * but leaves [createQuickTemplate] with nothing to persist into a brand-new template. When
     * that happens for an edit of an already-shared movement, read the split that's actually
     * there so the template being created alongside it still carries it forward.
     */
    private fun resolveSplitForTemplateCarryForward(
        form: MovementFormState,
        splitWrite: MovementSplitWrite,
    ): MovementSplitWrite {
        if (splitWrite !is MovementSplitWrite.KeepExisting) return splitWrite
        val movementId = form.movementId ?: return splitWrite
        val existing = splitRepository?.getForMovement(movementId) ?: return splitWrite
        return MovementSplitWrite.Replace(existing)
    }

    private fun createQuickTemplate(
        templateId: String,
        form: MovementFormState,
        amountCents: Long,
        date: LocalDate,
        createdAt: String,
        splitWrite: MovementSplitWrite,
    ) {
        val repo = templateRepository ?: return
        val nextDue = when (form.recurringFrequency) {
            RecurrenceFrequency.WEEKLY -> date.plusDays(7)
            RecurrenceFrequency.FORTNIGHTLY -> date.plusDays(14)
            RecurrenceFrequency.MONTHLY -> date.plusMonths(1)
            RecurrenceFrequency.YEARLY -> date.plusYears(1)
            RecurrenceFrequency.CUSTOM -> date.plusMonths(1)
        }
        val dayOfMonth: Long? = when (form.recurringFrequency) {
            RecurrenceFrequency.MONTHLY, RecurrenceFrequency.YEARLY -> date.dayOfMonth.toLong()
            else -> null
        }
        val draft = TemplateDraft(
            id = templateId,
            type = form.type,
            amountCents = amountCents,
            accountId = requireNotNull(form.accountId),
            destAccountId = form.destinationAccountId.takeIf { form.type == MovementType.TRANSFER },
            categoryId = form.categoryId.takeIf { form.type != MovementType.TRANSFER },
            name = form.name.nullIfBlank(),
            payee = form.payee.nullIfBlank(),
            notes = null,
            splitConfig = splitWrite.toTemplateSplitConfig(),
            frequency = form.recurringFrequency,
            intervalCount = null,
            customUnit = null,
            dayOfMonth = dayOfMonth,
            weekday = null,
            nextDueDate = nextDue.toString(),
            amountIsVariable = false,
            amountFlexCents = null,
            dateFlexDays = null,
            leadNotificationDays = null,
            status = TemplateStatus.ACTIVE,
        )
        repo.create(draft, createdAt = createdAt)
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
                        autoCatRules = it.autoCatRules,
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
                    autoCatRules = autoCatRuleRepository?.listActive().orEmpty(),
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
        var normalized = form

        when (form.type) {
            MovementType.TRANSFER -> {
                normalized = normalized.copy(
                    categoryId = null,
                    isOneTime = false,
                    splitEditor = null,
                    removeExistingSplit = form.existingSplit || form.removeExistingSplit,
                    isSettlement = false,
                    settlementPersonId = null,
                    expenseKind = null,
                    forOtherPersonId = null,
                )
            }
            MovementType.INCOME -> {
                normalized = normalized.copy(
                    isOneTime = false,
                    splitEditor = null,
                    removeExistingSplit = form.existingSplit || form.removeExistingSplit,
                    expenseKind = null,
                    forOtherPersonId = null,
                )
            }
            MovementType.EXPENSE -> {
                normalized = normalized.copy(
                    isSettlement = false,
                    settlementPersonId = null,
                )
                when (form.expenseKind) {
                    ExpenseKind.DEBT -> {
                        // Someone else paid — no account, no split editor.
                        normalized = normalized.copy(
                            accountId = null,
                            destinationAccountId = null,
                            isOneTime = false,
                            splitEditor = null,
                            removeExistingSplit = form.existingSplit || form.removeExistingSplit,
                        )
                    }
                    ExpenseKind.FOR_OTHER -> {
                        // I paid, one person owes the full amount — no split editor.
                        normalized = normalized.copy(
                            splitEditor = null,
                            removeExistingSplit = false,
                        )
                    }
                    ExpenseKind.SHARED -> {
                        normalized = normalized.copy(forOtherPersonId = null)
                    }
                    ExpenseKind.PERSONAL, null -> {
                        normalized = normalized.copy(
                            splitEditor = null,
                            forOtherPersonId = null,
                        )
                    }
                }
            }
            else -> {}
        }

        // Category compatibility against the effective type.
        val effectiveType = if (normalized.type == MovementType.EXPENSE && normalized.expenseKind == ExpenseKind.DEBT) {
            MovementType.EXTERNAL_EXPENSE
        } else {
            normalized.type
        }
        val category = normalized.categoryId?.let { catId ->
            _state.value.categories.firstOrNull { it.id == catId }
        }
        val tag = normalized.tagId?.let { tId ->
            _state.value.tags.firstOrNull { it.id == tId }
        }
        val trip = normalized.tripId?.let { tripId ->
            _state.value.trips.firstOrNull { it.id == tripId }
        }

        val finalCategoryId = if (category != null && !category.supports(effectiveType)) null else normalized.categoryId
        val finalTagId = if (normalized.tripId == null || tag == null || !tag.supportsTrip(trip)) null else normalized.tagId

        return normalized.copy(
            categoryId = finalCategoryId,
            tagId = finalTagId,
            suggestedCategoryId = suggestCategoryId(normalized),
            errorRes = null,
            errorMessage = null,
            duplicateWarning = false,
        )
    }

    /** Read-only category suggestion (audit F1): matches active `auto_cat_rules` against the
     * in-progress form. Returns null unless enough fields are filled in to run a match. */
    private fun suggestCategoryId(form: MovementFormState): String? {
        if (form.type != MovementType.EXPENSE && form.type != MovementType.INCOME) return null
        val rules = _state.value.autoCatRules
        if (rules.isEmpty()) return null
        val accountId = form.accountId ?: return null
        val amountCents = parseEuroCents(form.amount, allowNegative = false) ?: return null
        val date = parseDate(form.date) ?: return null
        val movement = AutoCategorizeMovement(
            name = form.name.nullIfBlank(),
            payee = form.payee.nullIfBlank(),
            amountCents = amountCents,
            date = date,
            accountId = accountId,
        )
        return AutoCategorizer.findMatch(movement, rules)?.action?.categoryId
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
            if (existing.id == form.movementId || existing.id == form.externalSplitId) return@any false
            val existingDate = parseDate(existing.date) ?: return@any false
            DuplicateDetector.isDuplicate(
                existing = DuplicateMovement(
                    accountId = existing.accountId.orEmpty(),
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
        private val splitRepository: SplitRepository? = null,
        private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
        private val templateRepository: TemplateRepository? = null,
        private val autoCatRuleRepository: AutoCatRuleRepository? = null,
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
                    splitRepository = splitRepository,
                    notificationRefresher = notificationRefresher,
                    templateRepository = templateRepository,
                    autoCatRuleRepository = autoCatRuleRepository,
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
    val autoCatRules: List<AutoCategorizeRule> = emptyList(),
    val filters: MovementFilters = MovementFilters(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: MovementFormState? = null,
    val detailMovement: MovementSummary? = null,
    val detailRefunds: List<RefundSummary> = emptyList(),
    val refundForm: RefundFormState? = null,
    val archiveCandidate: ArchiveCandidate? = null,
    val dataVersion: Long = 0L,
) {
    val visibleMovements: List<MovementSummary>
        get() = movements.filter { filters.matches(it) }

    /** True while any of this ViewModel's own dialogs (rendered by `MovementDialogHost`) is open --
     * used to keep other app-level auto-triggered overlays (e.g. the due-reminders sheet) from
     * appearing on top of one of these. */
    val hasOpenDialog: Boolean get() =
        form != null || detailMovement != null || refundForm != null || archiveCandidate != null
}

data class ArchiveCandidate(
    val movement: MovementSummary,
    /** Non-null iff [movement] is provably the template's immediate prior occurrence -- offers
     * the "mark it as due again" choice in the archive-confirmation dialog. */
    val revertibleTemplateId: String? = null,
)

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

enum class ExpenseKind {
    PERSONAL,   // type 1: Jo paid, just me
    SHARED,     // type 2: Jo paid, split with others
    FOR_OTHER,  // type 3: Jo paid, the other person owes the full amount
    DEBT,       // type 4: Una altra persona paid, I owe them
}

data class MovementFormState(
    /** Set when editing an existing direct movement ([com.gestorfinances.app.data.repository.MovementType]
     * other than EXTERNAL_EXPENSE) -- `movements.id`. Mutually exclusive with [externalSplitId]:
     * an in-progress edit's backing entity is always one or the other, never both. Tracking them
     * separately (rather than a single ambiguous id) lets [MovementsViewModel] detect when the
     * user switches "Qui ha pagat?" to/from "Un altre" mid-edit and archive-and-recreate instead
     * of silently writing to the wrong table (audit BLOCKER). */
    val movementId: String? = null,
    /** Set when editing an existing DEBT ("Un altre ha pagat") expense -- `splits.id`, per
     * `v_movement_summary`'s external-expense branch. See [movementId]. */
    val externalSplitId: String? = null,
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
    val isSettlement: Boolean = false,
    val settlementPersonId: String? = null,
    val expenseKind: ExpenseKind? = null,
    val forOtherPersonId: String? = null,
    val isRecurring: Boolean = false,
    val recurringFrequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val templateId: String? = null,
    val showAdvanced: Boolean = false,
    /** Read-only auto-categorization hint (audit F1); never applied without the user tapping it. */
    val suggestedCategoryId: String? = null,
) {
    /** True for a fresh "add" flow with no backing entity yet, as opposed to editing an existing
     * movement or external split. */
    val isNew: Boolean get() = movementId == null && externalSplitId == null
}

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
    val autoCatRules: List<AutoCategorizeRule> = emptyList(),
)

private fun defaultAccountId(accounts: List<AccountSummary>): String? =
    accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id

private fun newMovementForm(
    data: LoadedMovementData,
    tripId: String?,
    debtPayerPersonId: String? = null,
): MovementFormState {
    val trip = tripId?.let { selectedId -> data.trips.firstOrNull { it.id == selectedId } }
    return MovementFormState(
        accountId = trip?.defaultAccountId ?: defaultAccountId(data.accounts),
        tripId = trip?.id,
        date = LocalDate.now().toString(),
        expenseKind = if (debtPayerPersonId != null) ExpenseKind.DEBT else null,
        forOtherPersonId = debtPayerPersonId,
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
    if (type != null) {
        val matchesType = when (type) {
            MovementType.EXPENSE -> movement.type == MovementType.EXPENSE || movement.type == MovementType.EXTERNAL_EXPENSE
            else -> movement.type == type
        }
        if (!matchesType) return false
    }
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

private fun MovementSummary.toFormState(splitDraft: MovementSplitDraft? = null): MovementFormState {
    // DEBT (type 4): stored as EXTERNAL_EXPENSE — map back to EXPENSE + expenseKind=DEBT.
    if (type == MovementType.EXTERNAL_EXPENSE) {
        return MovementFormState(
            externalSplitId = id,
            type = MovementType.EXPENSE,
            amount = formatEuroInput(amountCents),
            date = date,
            categoryId = categoryId,
            tripId = tripId,
            tagId = tagId,
            name = name.orEmpty(),
            expenseKind = ExpenseKind.DEBT,
            forOtherPersonId = payerId,
        )
    }

    val userLine = splitDraft?.lines?.firstOrNull { it.participantKind == SplitParticipantKind.USER }
    val personLines = splitDraft?.lines?.filter { it.participantKind == SplitParticipantKind.PERSON } ?: emptyList()

    // FOR_OTHER (type 3): user owes 0, exactly one person owes the full amount.
    if (userLine != null && userLine.owedAmountCents == 0L &&
        personLines.size == 1 && personLines.first().owedAmountCents == amountCents
    ) {
        return MovementFormState(
            movementId = id,
            type = type,
            amount = formatEuroInput(amountCents),
            date = date,
            accountId = accountId,
            categoryId = categoryId,
            tripId = tripId,
            tagId = tagId,
            name = name.orEmpty(),
            payee = payee.orEmpty(),
            notes = notes.orEmpty(),
            isOneTime = isOneTime,
            existingSplit = true,
            expenseKind = ExpenseKind.FOR_OTHER,
            forOtherPersonId = personLines.first().personId,
            showAdvanced = isOneTime || !payee.isNullOrEmpty() || !notes.isNullOrEmpty(),
        )
    }

    // SHARED (type 2): split exists; PERSONAL (type 1): no split.
    val splitEditor = splitDraft?.let { draft ->
        SplitEditorState(
            method = draft.entryMethod,
            selectedPersonIds = personLines.mapNotNull { it.personId },
            payerParticipantId = USER_PARTICIPANT_ID,
            exactAmounts = if (draft.entryMethod == SplitEntryMethod.EXACT) {
                draft.lines.associate { line ->
                    (line.personId ?: USER_PARTICIPANT_ID) to formatEuroInput(line.owedAmountCents)
                }
            } else {
                emptyMap()
            },
            percentages = if (draft.entryMethod == SplitEntryMethod.PERCENTAGE) {
                draft.lines.associate { line ->
                    val participantId = line.personId ?: USER_PARTICIPANT_ID
                    val percentStr = line.owedPercent?.let { "%.2f".format(it).replace(".", ",") }
                        ?: if (amountCents > 0) "%.2f".format(line.owedAmountCents.toDouble() * 100.0 / amountCents).replace(".", ",") else ""
                    participantId to percentStr
                }
            } else {
                emptyMap()
            },
        )
    }

    val expenseKind = when {
        type != MovementType.EXPENSE -> null
        splitEditor != null -> ExpenseKind.SHARED
        else -> ExpenseKind.PERSONAL
    }

    return MovementFormState(
        movementId = id,
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
        splitEditor = splitEditor,
        isRecurring = isRecurring,
        templateId = templateId,
        expenseKind = expenseKind,
        showAdvanced = isOneTime || isRecurring || isShared || !payee.isNullOrEmpty() || !notes.isNullOrEmpty(),
    )
}

private fun CategoryRecord.supports(type: MovementType): Boolean =
    when (type) {
        MovementType.EXPENSE, MovementType.EXTERNAL_EXPENSE -> kind == CategoryKind.EXPENSE || kind == CategoryKind.BOTH
        MovementType.INCOME -> kind == CategoryKind.INCOME || kind == CategoryKind.BOTH
        else -> false
    }

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

/**
 * Carries a quick-created recurring template's split forward (§4.4): recurring templates can only
 * be created from [ExpenseKind.PERSONAL]/[ExpenseKind.SHARED]/[ExpenseKind.FOR_OTHER] (DEBT has no
 * template support), so the user is always the payer. Returns null when the movement itself has no
 * split (plain personal expense/income/transfer) — `RecurringViewModel.toSplitWrite` already treats
 * a null `split_config` as "no split to carry forward."
 */
private fun MovementSplitWrite.toTemplateSplitConfig(): TemplateSplitConfig? {
    val draft = (this as? MovementSplitWrite.Replace)?.draft ?: return null
    return TemplateSplitConfig(
        entryMethod = draft.entryMethod.dbValue,
        payer = "user",
        lines = draft.lines.map { line ->
            TemplateSplitConfigLine(
                party = line.personId ?: "user",
                owedAmountCents = line.owedAmountCents,
            )
        },
    )
}

private val actualMovementTypes = setOf(
    MovementType.EXPENSE,
    MovementType.INCOME,
    MovementType.REFUND,
    MovementType.EXTERNAL_EXPENSE,
)
