package com.gestorfinances.app.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AutoCatRuleRepository
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
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.data.repository.toTemplateSplitConfig
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.supports
import com.gestorfinances.app.domain.rules.AutoCategorizeMovement
import com.gestorfinances.app.domain.rules.AutoCategorizeRule
import com.gestorfinances.app.domain.rules.AutoCategorizer
import com.gestorfinances.app.domain.rules.DuplicateDetector
import com.gestorfinances.app.domain.rules.DuplicateMovement
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.domain.rules.RecurrenceRule
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

    /**
     * Starts a fresh ledger visit from global navigation. Contextual navigation deliberately uses
     * [onDrillDown] instead, so Back can reveal the ledger exactly as the person left it.
     */
    fun resetForMenuNavigation() {
        _state.value = _state.value.copy(
            filters = MovementFilters(),
            form = null,
            detailMovement = null,
            detailRefunds = emptyList(),
            detailSplit = null,
            refundForm = null,
            archiveCandidate = null,
            errorMessage = null,
        )
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

    fun onEditClicked(movement: MovementSummary, onReady: () -> Unit = {}) {
        if (movement.type == MovementType.SETTLEMENT || movement.type == MovementType.REFUND) return
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val split = when {
                        movement.isShared -> splitRepository?.getForMovement(movement.id)
                        movement.type == MovementType.EXTERNAL_EXPENSE -> splitRepository?.getForMovementById(movement.id)
                        else -> null
                    }
                    // Load the real template so the form can show the actual linked
                    // frequency/status instead of silently defaulting or ignoring it.
                    val linkedTemplate = movement.templateId?.let { templateRepository?.getActive(it) }
                    split to linkedTemplate
                }
            }
            result.fold(
                onSuccess = { (splitDraft, template) ->
                    // Keep the detail data intact so dismissing the edit form can restore it.
                    _state.value = _state.value.copy(form = movement.toFormState(splitDraft, template))
                    onReady()
                },
                onFailure = {
                    _state.value = _state.value.copy(errorMessage = it.message ?: it.javaClass.simpleName)
                },
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
        _state.value = _state.value.copy(
            detailMovement = movement,
            detailRefunds = emptyList(),
            detailSplit = null,
        )
        if (movement.type == MovementType.EXPENSE) {
            loadDetailRefunds(movement.id)
        }
        if (movement.isShared || movement.type == MovementType.EXTERNAL_EXPENSE) {
            loadDetailSplit(movement)
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
        _state.value = _state.value.copy(detailMovement = null, detailRefunds = emptyList(), detailSplit = null)
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

    private fun loadDetailSplit(movement: MovementSummary) {
        viewModelScope.launch {
            val split = withContext(ioDispatcher) {
                runCatching {
                    if (movement.type == MovementType.EXTERNAL_EXPENSE) {
                        splitRepository?.getForMovementById(movement.id)
                    } else {
                        splitRepository?.getForMovement(movement.id)
                    }
                }.getOrNull()
            }
            if (_state.value.detailMovement?.id == movement.id) {
                _state.value = _state.value.copy(detailSplit = split)
            }
        }
    }

    fun onAddRefundClicked(expense: MovementSummary) {
        val refundedSoFar = _state.value.detailRefunds.sumOf { it.amountCents }
        // detailMovement stays set: the refund form is a local swap on top of the movement-detail
        // page (MovementDetailScreen), not a separate destination — cancelling it must reveal the
        // detail page again, not the underlying screen the detail page itself was opened from.
        _state.value = _state.value.copy(
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
        _state.value = _state.value.copy(
            refundForm = form.copy(errorRes = null, errorField = null, errorMessage = null),
        )
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

        val (errorRes, errorField) = when {
            amount == null -> R.string.refund_validation_amount_required to RefundFormField.AMOUNT
            amount <= 0L -> R.string.movement_validation_amount_positive to RefundFormField.AMOUNT
            form.accountId == null || form.accountId !in activeAccountIds ->
                R.string.movement_validation_account_required to RefundFormField.ACCOUNT
            form.date.isBlank() -> R.string.movement_validation_date_required to RefundFormField.DATE
            date == null -> R.string.movement_validation_date_invalid to RefundFormField.DATE
            actual != null && actual > amount -> R.string.refund_validation_actual_over to RefundFormField.ACTUAL_AMOUNT
            else -> null to null
        }
        if (errorRes != null) {
            _state.value = _state.value.copy(refundForm = form.copy(errorRes = errorRes, errorField = errorField))
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

    fun onArchiveConfirmed(revertDueDate: Boolean = false, onSuccess: () -> Unit = {}) {
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
                    _state.value = _state.value.copy(archiveCandidate = null, detailMovement = null)
                    refresh(dataChanged = true)
                    refreshNotifications()
                    onSuccess()
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
            // splitEditor is retained (not nulled) so an off/on round trip within the same
            // session doesn't lose manually-entered split state; it's hidden while the kind is
            // PERSONAL and stripped at draft-build time in saveDirectMovement if the save actually goes
            // through with sharing off.
            form.copy(
                expenseKind = ExpenseKind.PERSONAL,
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
        onFormChanged(form.copy(isRecurring = enabled, showOptional = true))
    }

    fun onRecurringFrequencyChanged(frequency: RecurrenceFrequency) {
        val form = _state.value.form ?: return
        onFormChanged(form.copy(recurringFrequency = frequency))
    }

    fun onOptionalToggled() {
        val form = _state.value.form ?: return
        _state.value = _state.value.copy(form = form.copy(showOptional = !form.showOptional))
    }

    fun onAdvancedToggled() {
        val form = _state.value.form ?: return
        _state.value = _state.value.copy(
            form = form.copy(showOptional = true, showAdvanced = !form.showAdvanced),
        )
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

    /** "Revisa" -- dismisses the duplicate/data-loss warning without saving, restoring the normal
     * Save button (field-level validation). */
    fun onWarningDismissed() {
        val form = _state.value.form ?: return
        _state.value = _state.value.copy(
            form = form.copy(duplicateWarning = false, pendingDataLossWarning = null),
        )
    }

    fun onSaveClicked() = attemptSave(forceSave = false, acceptDataLoss = false)

    /** Save anyway after the duplicate warning was shown (never block — warn). By the time this
     * warning is showing, the data-loss gate below has already run clean for the current form (it
     * runs first), so it's safe to accept both at once here. */
    fun onDuplicateOverrideClicked() = attemptSave(forceSave = true, acceptDataLoss = true)

    /** Save anyway after the pending data-loss warning was shown (data-loss protection — never
     * block, warn). This gate runs before the duplicate check, so accepting it re-runs the save
     * from the top with `forceSave = false` — a duplicate that only becomes apparent on this
     * re-run still gets its own warning rather than being silently bypassed. */
    fun onDataLossOverrideClicked() = attemptSave(forceSave = false, acceptDataLoss = true)

    /** "Finalitza la plantilla" choice on the [DataLossWarning.RECURRING_STOP] banner (recurrence consistency): the template is marked [TemplateStatus.ENDED] atomically with this save. */
    fun onRecurrenceStopEndClicked() = attemptSave(forceSave = false, acceptDataLoss = true, endTemplate = true)

    /** "Només desvincula aquest moviment" choice on the same banner: the old behavior -- this
     * movement is detached (`templateId = null` on save, already computed from `form.isRecurring`
     * being false) while the template itself keeps running untouched. */
    fun onRecurrenceStopUnlinkClicked() = attemptSave(forceSave = false, acceptDataLoss = true, endTemplate = false)

    private fun attemptSave(forceSave: Boolean, acceptDataLoss: Boolean, endTemplate: Boolean = false) {
        val form = _state.value.form ?: return
        when {
            form.type == MovementType.INCOME && form.isSettlement -> saveSettlement(form)
            form.type == MovementType.EXPENSE && form.expenseKind == ExpenseKind.DEBT ->
                saveExternalDebt(form, acceptDataLoss)
            else -> saveDirectMovement(form, forceSave, acceptDataLoss, endTemplate)
        }
    }

    private data class RequiredFields(
        val amountCents: Long?,
        val date: LocalDate?,
    )

    private fun parseRequiredFields(form: MovementFormState) = RequiredFields(
        amountCents = parseEuroCents(form.amount, allowNegative = false),
        date = parseDate(form.date),
    )

    private fun RequiredFields.validationError(
        form: MovementFormState,
    ): Pair<Int, MovementFormField>? = when {
        amountCents == null -> R.string.movement_validation_amount_required to MovementFormField.AMOUNT
        amountCents <= 0L -> R.string.movement_validation_amount_positive to MovementFormField.AMOUNT
        form.date.isBlank() -> R.string.movement_validation_date_required to MovementFormField.DATE
        date == null -> R.string.movement_validation_date_invalid to MovementFormField.DATE
        else -> null
    }

    private fun showValidationError(
        form: MovementFormState,
        error: Pair<Int, MovementFormField>,
    ) {
        _state.value = _state.value.copy(
            form = form.copy(
                errorRes = error.first,
                errorField = error.second,
                showOptional = form.showOptional || error.second == MovementFormField.TAG,
            ),
        )
    }

    private fun launchSave(form: MovementFormState, write: () -> Unit) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { runCatching(write) }
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

    private fun saveSettlement(form: MovementFormState) {
        val required = parseRequiredFields(form)
        val activeAccountIds = _state.value.accounts.map { it.id }.toSet()
        val activePersonIds = _state.value.people.map { it.id }.toSet()
        val error = required.validationError(form) ?: when {
            form.accountId == null || form.accountId !in activeAccountIds ->
                R.string.movement_validation_account_required to MovementFormField.ACCOUNT
            form.settlementPersonId == null || form.settlementPersonId !in activePersonIds ->
                R.string.settlement_validation_person_required to MovementFormField.PERSON
            else -> null
        }
        if (error != null) {
            showValidationError(form, error)
            return
        }

        val now = Instant.now().toString()
        val draft = SettlementDraft(
            id = UUID.randomUUID().toString(),
            personId = requireNotNull(form.settlementPersonId),
            direction = SettlementDirection.PERSON_TO_USER,
            amountCents = requireNotNull(required.amountCents),
            accountId = requireNotNull(form.accountId),
            date = requireNotNull(required.date).toString(),
            notes = form.notes.nullIfBlank(),
        )
        launchSave(form) {
            movementRepository.createSettlement(draft, createdAt = now)
        }
    }

    private fun saveExternalDebt(form: MovementFormState, acceptDataLoss: Boolean) {
        val required = parseRequiredFields(form)
        val activePersonIds = _state.value.people.map { it.id }.toSet()
        val error = required.validationError(form) ?: when {
            form.forOtherPersonId == null || form.forOtherPersonId !in activePersonIds ->
                R.string.settlement_validation_person_required to MovementFormField.PERSON
            else -> null
        }
        if (error != null) {
            showValidationError(form, error)
            return
        }

        // Switching from a direct movement to an external split drops fields that external splits
        // cannot store. Warn before crossing that boundary (never block).
        if (!acceptDataLoss && form.movementId != null) {
            _state.value = _state.value.copy(
                form = form.copy(
                    pendingDataLossWarning = DataLossWarning.PAYER_SWITCH,
                    errorRes = null,
                    errorField = null,
                    errorMessage = null,
                ),
            )
            return
        }

        val now = Instant.now().toString()
        val draft = ExternalSplitDraft(
            id = UUID.randomUUID().toString(),
            payerPersonId = requireNotNull(form.forOtherPersonId),
            totalAmountCents = requireNotNull(required.amountCents),
            userShareCents = requireNotNull(required.amountCents),
            date = requireNotNull(required.date).toString(),
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

        launchSave(form) {
            movementRepository.runInTransaction {
                when {
                    form.externalSplitId != null -> repo.replaceExternalSplit(form.externalSplitId, draft, now)
                    form.movementId != null -> {
                        movementRepository.archive(form.movementId, archivedAt = now)
                        repo.createExternalPaidByPerson(draft, createdAt = now)
                    }
                    else -> repo.createExternalPaidByPerson(draft, createdAt = now)
                }
            }
        }
    }

    private fun saveDirectMovement(
        form: MovementFormState,
        forceSave: Boolean,
        acceptDataLoss: Boolean,
        endTemplate: Boolean,
    ) {
        val required = parseRequiredFields(form)
        val amount = required.amountCents
        val date = required.date
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
        // Only EXPENSE+SHARED/FOR_OTHER can carry a split. `form.splitEditor`/`expenseKind` may
        // still hold a prior kind's selection (retained, not nulled, per data-loss protection),
        // so validation and the draft below must gate on the *effective* (final) kind rather than
        // the raw field, or a stale split from a since-abandoned kind would wrongly block/write.
        val isForOther = form.type == MovementType.EXPENSE && form.expenseKind == ExpenseKind.FOR_OTHER
        val isShared = form.type == MovementType.EXPENSE && form.expenseKind == ExpenseKind.SHARED
        val effectiveSplitEditor = form.splitEditor.takeIf { isShared }
        val splitDraft = effectiveSplitEditor?.toMovementSplitDraft(amount)
        // Whether the *kind* can carry a split at all -- true for SHARED even when splitEditor is
        // currently null (that null means "unchanged," not "none": KeepExisting below leaves the
        // stored split as-is). Only false here means the save would actually drop it.
        val finalKindCarriesSplit = isForOther || isShared

        val error = required.validationError(form) ?: when {
            form.accountId == null || form.accountId !in activeAccountIds ->
                R.string.movement_validation_account_required to MovementFormField.ACCOUNT
            form.type == MovementType.TRANSFER &&
                (form.destinationAccountId == null || form.destinationAccountId !in activeAccountIds) ->
                R.string.movement_validation_destination_required to MovementFormField.DESTINATION_ACCOUNT
            form.type == MovementType.TRANSFER && form.accountId == form.destinationAccountId ->
                R.string.movement_validation_transfer_same_account to MovementFormField.DESTINATION_ACCOUNT
            category != null && !category.supports(form.type) ->
                R.string.movement_validation_category_invalid to MovementFormField.CATEGORY
            form.tagId != null && (form.tripId == null || tag == null || !tag.supportsTrip(trip)) ->
                R.string.tag_validation_trip_required to MovementFormField.TAG
            isForOther && (form.forOtherPersonId == null || form.forOtherPersonId !in activePersonIds) ->
                R.string.settlement_validation_person_required to MovementFormField.PERSON
            effectiveSplitEditor != null && splitDraft == null ->
                (effectiveSplitEditor.calculation(amount).errorRes ?: R.string.split_validation_reconcile) to
                    MovementFormField.SPLIT
            else -> null
        }

        if (error != null) {
            showValidationError(form, error)
            return
        }

        // Data-loss gate (data-loss protection): an existing stored split that the final kind
        // can't carry (explicit un-share, or a type/kind switch away from SHARED/FOR_OTHER) would
        // otherwise be silently dropped by the `MovementSplitWrite.Remove` branch below. Warn once,
        // save only after the user accepts (never block).
        // Known limitation: `pendingDataLossWarning` holds a single value, so if a
        // save would *both* drop a split and stop a linked recurrence in the same edit, only this
        // warning surfaces first; accepting it (`acceptDataLoss = true`) also skips the
        // `isRecurrenceStop` gate below on the same re-run and defaults to "unlink" (the template
        // is left ACTIVE, never ended) without asking. Not data loss and not a hard block, just an
        // unannounced default in this rare compound case -- a known limitation.
        val willRemoveExistingSplit = !form.isNew && form.existingSplit && !finalKindCarriesSplit
        if (!acceptDataLoss && willRemoveExistingSplit) {
            _state.value = _state.value.copy(
                form = form.copy(
                    pendingDataLossWarning = DataLossWarning.SPLIT_REMOVED,
                    errorRes = null,
                    errorMessage = null,
                ),
            )
            return
        }

        // Recurring toggle-off (recurrence consistency): turning recurrence off on a movement
        // that's still linked to a template is ambiguous -- does the whole series stop, or does
        // only this occurrence detach? Warn once (never block) and let the two accept paths above
        // decide: [onRecurrenceStopEndClicked] also ends the template below; [onRecurrenceStopUnlinkClicked]
        // just proceeds (the draft's `templateId` is already null below since `form.isRecurring` is false).
        val isRecurrenceStop = !form.isRecurring && form.templateId != null
        if (!acceptDataLoss && isRecurrenceStop) {
            _state.value = _state.value.copy(
                form = form.copy(
                    pendingDataLossWarning = DataLossWarning.RECURRING_STOP,
                    errorRes = null,
                    errorField = null,
                    errorMessage = null,
                ),
            )
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
                isForOther && form.forOtherPersonId != null ->
                    MovementSplitWrite.Replace(
                        MovementSplitDraft(
                            entryMethod = SplitEntryMethod.EXACT,
                            lines = listOf(
                                SplitLineDraft(SplitParticipantKind.USER, null, 0L),
                                SplitLineDraft(SplitParticipantKind.PERSON, form.forOtherPersonId, requireNotNull(amount)),
                            ),
                        ),
                    )
                effectiveSplitEditor != null -> MovementSplitWrite.Replace(requireNotNull(splitDraft).let { d ->
                    // Preserve percentages if the method is PERCENTAGE
                    if (d.entryMethod == SplitEntryMethod.PERCENTAGE) {
                        d.copy(lines = d.lines.map { line ->
                            val participantId = line.personId ?: USER_PARTICIPANT_ID
                            val rawPercent = effectiveSplitEditor.percentages[participantId].orEmpty()
                            line.copy(owedPercent = parsePercentBasisPoints(rawPercent)?.toDouble()?.div(100.0))
                        })
                    } else d
                })
                willRemoveExistingSplit -> MovementSplitWrite.Remove
                else -> MovementSplitWrite.KeepExisting
            },
            templateId = recurringTemplateId,
        )

        launchSave(form) {
            movementRepository.runInTransaction {
                // Template first so the movement's template_id FK resolves, both writes atomic.
                if (isNewRecurrence && recurringTemplateId != null) {
                    // draft.splitWrite is KeepExisting when the user toggled "make recurring" on
                    // an already-shared movement without touching the split editor. Read the
                    // movement's current split so the new template still carries it forward.
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
                // "Finalitza la plantilla" ends the template atomically with this movement save.
                if (endTemplate && form.templateId != null) {
                    requireNotNull(templateRepository) { "template repository unavailable" }
                        .setStatus(form.templateId, TemplateStatus.ENDED, updatedAt = now)
                }
                when {
                    form.externalSplitId != null -> {
                        // Switching back from an external split archives the old entity first so
                        // the replacement movement is not double-counted.
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
        val dayOfMonth: Long? = when (form.recurringFrequency) {
            RecurrenceFrequency.MONTHLY, RecurrenceFrequency.YEARLY -> date.dayOfMonth.toLong()
            else -> null
        }
        val nextDue = if (form.recurringFrequency == RecurrenceFrequency.CUSTOM) {
            // The quick form does not expose a custom interval yet. Preserve its defensive
            // fallback until that frequency can supply a complete custom rule.
            date.plusMonths(1)
        } else {
            RecurringAdvancer.nextOccurrence(
                RecurrenceRule(
                    frequency = form.recurringFrequency,
                    dayOfMonth = dayOfMonth?.toInt(),
                ),
                date,
            )
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
            notes = form.notes.nullIfBlank(),
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

    /**
     * Runs on every form edit. Used to be a per-type "wipe whatever the new type/kind doesn't
     * render" pass -- that silently destroyed entered data on a type/kind switch (data-loss protection): a
     * shared expense flipped to TRANSFER and back lost its split, a payer switch lost the other
     * person, etc. By design, the form now retains every entered value (`splitEditor`,
     * `forOtherPersonId`, `settlementPersonId`, `payee`, `notes`, `isOneTime`, `accountId`, …) for
     * the life of the form regardless of type/kind -- the UI already renders only the
     * type/kind-relevant section, so retained-but-hidden state is invisible to the user.
     * Incompatible values are stripped only at draft-build time in [saveDirectMovement]; a stored split
     * that the final kind can't carry goes through [MovementFormState.pendingDataLossWarning]
     * instead of being silently dropped here. The only normalization left is read-only:
     * category/tag compatibility against the current type, the auto-cat suggestion, and clearing
     * transient error/warning flags so a fresh edit re-evaluates them from scratch.
     */
    private fun normalizeForm(form: MovementFormState): MovementFormState {
        val effectiveType = if (form.type == MovementType.EXPENSE && form.expenseKind == ExpenseKind.DEBT) {
            MovementType.EXTERNAL_EXPENSE
        } else {
            form.type
        }
        val category = form.categoryId?.let { catId ->
            _state.value.categories.firstOrNull { it.id == catId }
        }
        val tag = form.tagId?.let { tId ->
            _state.value.tags.firstOrNull { it.id == tId }
        }
        val trip = form.tripId?.let { tripId ->
            _state.value.trips.firstOrNull { it.id == tripId }
        }

        val finalCategoryId = if (category != null && !category.supports(effectiveType)) null else form.categoryId
        val finalTagId = if (form.tripId == null || tag == null || !tag.supportsTrip(trip)) null else form.tagId

        return form.copy(
            categoryId = finalCategoryId,
            tagId = finalTagId,
            suggestedCategoryId = suggestCategoryId(form),
            errorRes = null,
            errorField = null,
            errorMessage = null,
            duplicateWarning = false,
            pendingDataLossWarning = null,
        )
    }

    /** Read-only category suggestion (category suggestion): matches active `auto_cat_rules` against the
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
    val detailSplit: MovementSplitDraft? = null,
    val refundForm: RefundFormState? = null,
    val archiveCandidate: ArchiveCandidate? = null,
    val dataVersion: Long = 0L,
) {
    val visibleMovements: List<MovementSummary>
        get() = movements.filter { filters.matches(it) }

    /** True while any of this ViewModel's own pages/dialogs (`AppOverlay.MovementForm`/
     * `MovementDetail`, or the local archive/refund state nested inside the latter) is open --
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
    /** Number of filter-sheet/drill-down criteria that are active on the ledger. */
    val activeFilterCount: Int
        get() = listOf(
            accountId != null,
            categoryId != null || uncategorizedOnly,
            tripId != null,
            tagId != null,
            dateFrom.isNotBlank() || dateTo.isNotBlank(),
            sourceMode != null,
            categoryNature != null,
            oneTimeMode != MovementOneTimeMode.INCLUDE,
        ).count { it }

    val hasAdvancedFilters: Boolean
        get() = activeFilterCount > 0
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

/** Identifies which field a movement-form validation error belongs to (field-level validation) --
 * paired with [MovementFormState.errorRes] so the form can highlight the offending control and
 * scroll it into view instead of only showing a message the user may have scrolled past. Kept as
 * a single field (not a list): the validation chain in `saveDirectMovement` already stops at the first
 * failing condition. */
enum class MovementFormField {
    AMOUNT,
    DATE,
    ACCOUNT,
    DESTINATION_ACCOUNT,
    CATEGORY,
    TAG,
    PERSON,
    SPLIT,
}

/** The two destructive-save shapes [MovementsViewModel.saveDirectMovement] warns about before writing
 * (data-loss protection) -- never a hard block, always a dismissible "save anyway." */
enum class DataLossWarning {
    /** An existing stored split will be removed because the final kind can't carry one. */
    SPLIT_REMOVED,
    /** An existing direct movement/external split will be archived and recreated on the other
     * side of the movement/external-split boundary (the C8 payer-switch path), dropping payee,
     * notes, and any recurrence link -- `splits` rows carry none of those fields. */
    PAYER_SWITCH,
    /** Recurrence was toggled off on a movement still linked to a template (recurrence consistency): ambiguous whether the whole series should stop or just this occurrence
     * should detach -- offers both choices instead of silently doing either. */
    RECURRING_STOP,
}

data class MovementFormState(
    /** Set when editing an existing direct movement ([com.gestorfinances.app.data.repository.MovementType]
     * other than EXTERNAL_EXPENSE) -- `movements.id`. Mutually exclusive with [externalSplitId]:
     * an in-progress edit's backing entity is always one or the other, never both. Tracking them
     * separately (rather than a single ambiguous id) lets [MovementsViewModel] detect when the
     * user switches "Qui ha pagat?" to/from "Un altre" mid-edit and archive-and-recreate instead
     * of silently writing to the wrong table. */
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
    /** UI toggle-state only (drives the sharing-switch's checked state) — save-path removal is
     * computed independently in `saveDirectMovement` via `finalKindCarriesSplit`. */
    val removeExistingSplit: Boolean = false,
    val splitEditor: SplitEditorState? = null,
    val duplicateWarning: Boolean = false,
    /** Set by [MovementsViewModel]'s pre-save gate (data-loss protection) when the save-in-
     * progress would remove a stored split or cross the movement/external-split boundary --
     * mirrors [duplicateWarning]'s dismissible "save anyway" mechanism, never a hard block. */
    val pendingDataLossWarning: DataLossWarning? = null,
    val errorRes: Int? = null,
    /** Which control [errorRes] refers to (field-level validation) -- null for a save-time
     * failure that isn't attributable to a single field. */
    val errorField: MovementFormField? = null,
    val errorMessage: String? = null,
    val isSettlement: Boolean = false,
    val settlementPersonId: String? = null,
    val expenseKind: ExpenseKind? = null,
    val forOtherPersonId: String? = null,
    val isRecurring: Boolean = false,
    val recurringFrequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val templateId: String? = null,
    /** The linked template's status when [templateId] is set -- loaded
     * on edit so the form can reflect the template's real state; null for a new/unlinked movement. */
    val templateStatus: TemplateStatus? = null,
    val showOptional: Boolean = false,
    val showAdvanced: Boolean = false,
    /** Stable UI disclosure for the expense payer/beneficiary controls. */
    /** Read-only auto-categorization hint (category suggestion); never applied without the user tapping it. */
    val suggestedCategoryId: String? = null,
) {
    /** True for a fresh "add" flow with no backing entity yet, as opposed to editing an existing
     * movement or external split. */
    val isNew: Boolean get() = movementId == null && externalSplitId == null
}

/** Identifies which field a refund-form validation error belongs to (field-level validation). */
enum class RefundFormField {
    AMOUNT,
    ACTUAL_AMOUNT,
    ACCOUNT,
    DATE,
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
    val errorField: RefundFormField? = null,
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
        showOptional = trip?.id != null,
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

private fun MovementSummary.toFormState(
    splitDraft: MovementSplitDraft? = null,
    template: TemplateSummary? = null,
): MovementFormState {
    // DEBT (type 4): stored as EXTERNAL_EXPENSE — map back to EXPENSE + expenseKind=DEBT.
    // DEBT carries no recurrence support (external splits have no template link).
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
            showOptional = tripId != null || tagId != null,
        )
    }

    val userLine = splitDraft?.lines?.firstOrNull { it.participantKind == SplitParticipantKind.USER }
    val personLines = splitDraft?.lines?.filter { it.participantKind == SplitParticipantKind.PERSON } ?: emptyList()

    // FOR_OTHER (type 3): user owes 0, exactly one person owes the full amount.
    if (userLine != null && userLine.owedAmountCents == 0L &&
        personLines.size == 1 && personLines.first().owedAmountCents == amountCents
    ) {
        val showAdvanced = isOneTime || isRecurring || !payee.isNullOrEmpty() || !notes.isNullOrEmpty()
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
            isRecurring = isRecurring,
            templateId = templateId,
            recurringFrequency = template?.frequency ?: RecurrenceFrequency.MONTHLY,
            templateStatus = template?.status,
            showOptional = tripId != null || tagId != null || showAdvanced,
            showAdvanced = showAdvanced,
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
    val showAdvanced = isOneTime || isRecurring || isShared || !payee.isNullOrEmpty() || !notes.isNullOrEmpty()

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
        recurringFrequency = template?.frequency ?: RecurrenceFrequency.MONTHLY,
        templateStatus = template?.status,
        expenseKind = expenseKind,
        showOptional = tripId != null || tagId != null || showAdvanced,
        showAdvanced = showAdvanced,
    )
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

private val actualMovementTypes = setOf(
    MovementType.EXPENSE,
    MovementType.INCOME,
    MovementType.REFUND,
    MovementType.EXTERNAL_EXPENSE,
)
