package com.gestorfinances.app.ui.movements

import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.ContributionDirection
import com.gestorfinances.app.data.repository.ContributionDraft
import com.gestorfinances.app.data.repository.ExpenseFunding
import com.gestorfinances.app.data.repository.ExternalSplitDraft
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSplitDraft
import com.gestorfinances.app.data.repository.MovementSplitWrite
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonDraft
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.data.repository.SettlementDraft
import com.gestorfinances.app.data.repository.SplitEntryMethod
import com.gestorfinances.app.data.repository.SplitLineDraft
import com.gestorfinances.app.data.repository.SplitParticipantKind
import com.gestorfinances.app.data.repository.SplitRepository
import com.gestorfinances.app.data.repository.TemplateDraft
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.supports
import com.gestorfinances.app.data.repository.toTemplateSplitConfig
import com.gestorfinances.app.domain.rules.DuplicateDetector
import com.gestorfinances.app.domain.rules.DuplicateMovement
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.domain.rules.RecurrenceRule
import com.gestorfinances.app.domain.rules.RecurringAdvancer
import com.gestorfinances.app.domain.rules.SettlementScope
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.common.parseEuroCents
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The create/edit movement form, from an opened form to a saved or abandoned one: field changes,
 * validation, duplicate and data-loss warnings, and the write for each kind of movement.
 *
 * It owns only the form. Accounts, people, and the other lists it validates against are read from
 * [references], the ledger's loaded data; [onSaved] tells the ledger a save changed the data.
 */
class MovementEditor(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val movementRepository: MovementRepository,
    private val accountRepository: AccountRepository,
    private val personRepository: PersonRepository,
    private val splitRepository: SplitRepository?,
    private val templateRepository: TemplateRepository?,
    private val references: () -> MovementsUiState,
    private val onSaved: () -> Unit,
    private val onPeopleChanged: (List<PersonSummary>) -> Unit,
) {
    private val _form = MutableStateFlow<MovementFormState?>(null)
    val form: StateFlow<MovementFormState?> = _form.asStateFlow()

    /** Shows [form]; the ledger opens it once it has the data a new or edited form starts from. */
    fun open(form: MovementFormState) {
        _form.value = form
    }

    fun onFormChanged(form: MovementFormState) {
        val previous = _form.value
        val account = form.accountId?.let { id -> references().accounts.firstOrNull { it.id == id } }
        val withAccountDefaults = if (
            form.type == MovementType.EXPENSE &&
            account?.ownershipKind == AccountOwnershipKind.SHARED &&
            (previous?.accountId != form.accountId || form.expenseKind != ExpenseKind.SHARED)
        ) {
            form.copy(
                expenseKind = ExpenseKind.SHARED,
                splitEditor = account.defaultExpenseSplitEditor(),
                showOptional = true,
                removeExistingSplit = false,
            )
        } else if (
            form.type == MovementType.INCOME &&
            account?.ownershipKind == AccountOwnershipKind.SHARED &&
            (previous?.type != MovementType.INCOME || previous?.accountId != form.accountId)
        ) {
            // Arriving at an income on a shared account asks whose it is afresh, rather than
            // inheriting the expense form's split or another account's answer.
            form.copy(
                expenseKind = null,
                splitEditor = account.defaultExpenseSplitEditor(),
                forOtherPersonId = null,
                removeExistingSplit = false,
            )
        } else form
        _form.value = normalizeForm(withAccountDefaults)
    }

    fun onTripSelected(tripId: String?) {
        val form = _form.value ?: return
        val trip = tripId?.let { selectedId -> references().trips.firstOrNull { it.id == selectedId } }
        val tag = form.tagId?.let { tagId -> references().tags.firstOrNull { it.id == tagId } }
        val accountId = if (form.isNew && trip?.defaultAccountId != null) {
            trip.defaultAccountId
        } else {
            form.accountId
        }
        val tagId = if (tag != null && tag.supportsTrip(trip)) form.tagId else null
        onFormChanged(form.copy(tripId = tripId, tagId = tagId, accountId = accountId))
    }

    fun onTagSelected(tagId: String?) {
        val form = _form.value ?: return
        onFormChanged(form.copy(tagId = tagId))
    }

    /** Whether an account id names a shared account in the currently loaded list. */
    private fun isSharedAccount(accountId: String?): Boolean =
        accountId != null &&
            references().accounts.firstOrNull { it.id == accountId }?.ownershipKind ==
            AccountOwnershipKind.SHARED

    fun onSharedToggled(enabled: Boolean) {
        val form = _form.value ?: return
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
        val form = _form.value ?: return
        onFormChanged(form.copy(splitEditor = splitEditor, removeExistingSplit = false))
    }

    fun onSettlementToggled(enabled: Boolean) {
        val form = _form.value ?: return
        onFormChanged(form.copy(isSettlement = enabled, settlementPersonId = if (enabled) form.settlementPersonId else null))
    }

    fun onSettlementPersonSelected(personId: String?) {
        val form = _form.value ?: return
        onFormChanged(form.copy(settlementPersonId = personId))
    }

    fun onOtherPersonSelected(personId: String?) {
        val form = _form.value ?: return
        onFormChanged(form.copy(forOtherPersonId = personId))
    }

    fun onRecurringToggled(enabled: Boolean) {
        val form = _form.value ?: return
        onFormChanged(form.copy(isRecurring = enabled, showOptional = true))
    }

    fun onRecurringFrequencyChanged(frequency: RecurrenceFrequency) {
        val form = _form.value ?: return
        onFormChanged(form.copy(recurringFrequency = frequency))
    }

    fun onOptionalToggled() {
        val form = _form.value ?: return
        _form.value = form.copy(showOptional = !form.showOptional)
    }

    fun onAdvancedToggled() {
        val form = _form.value ?: return
        _form.value = form.copy(showOptional = true, showAdvanced = !form.showAdvanced)
    }

    fun onCreatePersonInSplit(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        scope.launch {
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
                    val form = _form.value ?: return@fold
                    val updatedSplit = form.splitEditor?.withPersonToggled(personId)
                    onPeopleChanged(newPeople)
                    _form.value = form.copy(splitEditor = updatedSplit ?: form.splitEditor)
                },
                onFailure = {
                    _form.value = _form.value?.copy(errorMessage = it.message ?: it.javaClass.simpleName)
                },
            )
        }
    }

    fun onFormDismissed() {
        _form.value = null
    }

    /** "Revisa" -- dismisses the duplicate/data-loss warning without saving, restoring the normal
     * Save button (field-level validation). */
    fun onWarningDismissed() {
        val form = _form.value ?: return
        _form.value = form.copy(duplicateWarning = false, pendingDataLossWarning = null)
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
        val form = _form.value ?: return
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
        _form.value = form.copy(
                errorRes = error.first,
                errorField = error.second,
                showOptional = form.showOptional || error.second == MovementFormField.TAG,
            )
    }

    /**
     * A new transfer between one of the owner's personal accounts and a shared account moves the
     * owner's own money into or out of the shared account: a contribution or a withdrawal, never a
     * transfer. Contributions do not recur, so the form offers no recurrence for one.
     */
    private fun saveTransferAsContribution(form: MovementFormState, amountCents: Long, date: String) {
        val intoShared = isSharedAccount(form.destinationAccountId)
        val draft = ContributionDraft(
            id = UUID.randomUUID().toString(),
            sharedAccountId = requireNotNull(if (intoShared) form.destinationAccountId else form.accountId),
            direction = if (intoShared) ContributionDirection.IN else ContributionDirection.OUT,
            contributorKind = SplitParticipantKind.USER,
            personId = null,
            sourceAccountId = if (intoShared) form.accountId else form.destinationAccountId,
            amountCents = amountCents,
            date = date,
            name = form.name.nullIfBlank(),
            notes = form.notes.nullIfBlank(),
        )
        val now = Instant.now().toString()
        launchSave(form) { accountRepository.createContribution(draft, createdAt = now) }
    }

    private fun launchSave(form: MovementFormState, write: () -> Unit) {
        scope.launch {
            val result = withContext(ioDispatcher) { runCatching(write) }
            result.fold(
                onSuccess = {
                    _form.value = null
                    onSaved()
                },
                onFailure = {
                    _form.value = form.copy(errorMessage = it.message ?: it.javaClass.simpleName)
                },
            )
        }
    }

    private fun saveSettlement(form: MovementFormState) {
        val required = parseRequiredFields(form)
        val activeAccountIds = references().accounts.map { it.id }.toSet()
        val activePersonIds = references().people.map { it.id }.toSet()
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
            // The movement form's debt entry is the quick "settle up" path and stays general;
            // the person detail sheet is where a scope is chosen deliberately.
            scope = SettlementScope.ALL,
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
        val activePersonIds = references().people.map { it.id }.toSet()
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
            _form.value = form.copy(
                    pendingDataLossWarning = DataLossWarning.PAYER_SWITCH,
                    errorRes = null,
                    errorField = null,
                    errorMessage = null,
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
            _form.value = form.copy(errorMessage = "Internal error: split repository unavailable")
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
        val activeAccountIds = references().accounts.map { it.id }.toSet()
        val activePersonIds = references().people.map { it.id }.toSet()
        val category = form.categoryId?.let { categoryId ->
            references().categories.firstOrNull { it.id == categoryId }
        }
        val tag = form.tagId?.let { tagId ->
            references().tags.firstOrNull { it.id == tagId }
        }
        val trip = form.tripId?.let { tripId ->
            references().trips.firstOrNull { it.id == tripId }
        }
        // Only EXPENSE+SHARED/FOR_OTHER can carry a split. `form.splitEditor`/`expenseKind` may
        // still hold a prior kind's selection (retained, not nulled, per data-loss protection),
        // so validation and the draft below must gate on the *effective* (final) kind rather than
        // the raw field, or a stale split from a since-abandoned kind would wrongly block/write.
        // An income carries the same allocation choice when it lands in a shared account.
        val incomeOnSharedAccount = form.type == MovementType.INCOME && isSharedAccount(form.accountId)
        val carriesAllocation = form.type == MovementType.EXPENSE || incomeOnSharedAccount
        val crossesOwnership = form.type == MovementType.TRANSFER &&
            isSharedAccount(form.accountId) != isSharedAccount(form.destinationAccountId)
        val isForOther = carriesAllocation && form.expenseKind == ExpenseKind.FOR_OTHER
        val isShared = carriesAllocation && form.expenseKind == ExpenseKind.SHARED
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
            // A new transfer crossing ownership is saved as a contribution or withdrawal below, but a
            // saved transfer is not rewritten into one. Blame the shared side, the field to change.
            crossesOwnership && !form.isNew ->
                R.string.movement_validation_shared_transfer to if (isSharedAccount(form.accountId)) {
                    MovementFormField.ACCOUNT
                } else {
                    MovementFormField.DESTINATION_ACCOUNT
                }
            category != null && !category.supports(form.type) ->
                R.string.movement_validation_category_invalid to MovementFormField.CATEGORY
            form.tagId != null && (form.tripId == null || tag == null || !tag.supportsTrip(trip)) ->
                R.string.tag_validation_trip_required to MovementFormField.TAG
            // Landing in a shared account never decides whose income it is.
            incomeOnSharedAccount && form.expenseKind == null ->
                R.string.movement_validation_income_owner to MovementFormField.INCOME_OWNER
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

        if (crossesOwnership) {
            saveTransferAsContribution(form, requireNotNull(amount), requireNotNull(date).toString())
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
            _form.value = form.copy(
                    pendingDataLossWarning = DataLossWarning.SPLIT_REMOVED,
                    errorRes = null,
                    errorMessage = null,
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
            _form.value = form.copy(
                    pendingDataLossWarning = DataLossWarning.RECURRING_STOP,
                    errorRes = null,
                    errorField = null,
                    errorMessage = null,
                )
            return
        }

        if (!forceSave && isDuplicate(form, requireNotNull(amount), requireNotNull(date))) {
            _form.value = form.copy(duplicateWarning = true, errorRes = null, errorMessage = null)
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
            expenseFunding = if (form.type == MovementType.EXPENSE && isSharedAccount(form.accountId)) {
                ExpenseFunding.SHARED_ACCOUNT
            } else {
                ExpenseFunding.OWNER
            },
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
            tripId = form.tripId.takeIf { form.type != MovementType.TRANSFER },
            tagId = form.tagId.takeIf { form.type != MovementType.TRANSFER },
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
     * category/tag compatibility against the current type and clearing
     * transient error/warning flags so a fresh edit re-evaluates them from scratch.
     */
    private fun normalizeForm(form: MovementFormState): MovementFormState {
        val effectiveType = if (form.type == MovementType.EXPENSE && form.expenseKind == ExpenseKind.DEBT) {
            MovementType.EXTERNAL_EXPENSE
        } else {
            form.type
        }
        val category = form.categoryId?.let { catId ->
            references().categories.firstOrNull { it.id == catId }
        }
        val tag = form.tagId?.let { tId ->
            references().tags.firstOrNull { it.id == tId }
        }
        val trip = form.tripId?.let { tripId ->
            references().trips.firstOrNull { it.id == tripId }
        }

        val finalCategoryId = if (category != null && !category.supports(effectiveType)) null else form.categoryId
        val finalTagId = if (form.tripId == null || tag == null || !tag.supportsTrip(trip)) null else form.tagId

        return form.copy(
            categoryId = finalCategoryId,
            tagId = finalTagId,
            errorRes = null,
            errorField = null,
            errorMessage = null,
            duplicateWarning = false,
            pendingDataLossWarning = null,
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
        return references().movements.any { existing ->
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
    INCOME_OWNER,
}

/** The two destructive-save shapes [MovementEditor.saveDirectMovement] warns about before writing
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
     * separately (rather than a single ambiguous id) lets [MovementEditor] detect when the
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
    /** Set by [MovementEditor]'s pre-save gate (data-loss protection) when the save-in-
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
) {
    /** True for a fresh "add" flow with no backing entity yet, as opposed to editing an existing
     * movement or external split. */
    val isNew: Boolean get() = movementId == null && externalSplitId == null
}

private fun defaultAccountId(accounts: List<AccountSummary>): String? =
    accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id

internal fun newMovementForm(
    accounts: List<AccountSummary>,
    trips: List<TripSummary>,
    tripId: String?,
    debtPayerPersonId: String? = null,
    presetAccountId: String? = null,
): MovementFormState {
    val trip = tripId?.let { selectedId -> trips.firstOrNull { it.id == selectedId } }
    val accountId = presetAccountId?.takeIf { id -> accounts.any { it.id == id } }
        ?: trip?.defaultAccountId
        ?: defaultAccountId(accounts)
    val account = accounts.firstOrNull { it.id == accountId }
    val sharedAccount = debtPayerPersonId == null && account?.ownershipKind == AccountOwnershipKind.SHARED
    return MovementFormState(
        accountId = accountId,
        tripId = trip?.id,
        date = LocalDate.now().toString(),
        expenseKind = when {
            debtPayerPersonId != null -> ExpenseKind.DEBT
            sharedAccount -> ExpenseKind.SHARED
            else -> null
        },
        splitEditor = if (sharedAccount) account?.defaultExpenseSplitEditor() else null,
        forOtherPersonId = debtPayerPersonId,
        showOptional = trip?.id != null || sharedAccount,
    )
}

private fun AccountSummary.defaultExpenseSplitEditor(): SplitEditorState =
    SplitEditorState(
        method = SplitEntryMethod.PERCENTAGE,
        selectedPersonIds = members.mapNotNull { it.personId },
        percentages = members.associate { member ->
            val participantId = member.personId ?: USER_PARTICIPANT_ID
            participantId to "%d,%02d".format(
                member.defaultExpenseBasisPoints / 100,
                member.defaultExpenseBasisPoints % 100,
            )
        },
    )

internal fun MovementSummary.toFormState(
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
        type != MovementType.EXPENSE && type != MovementType.INCOME -> null
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
