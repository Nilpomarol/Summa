package com.gestorfinances.app.ui.movements

import com.gestorfinances.app.domain.rules.RecurringMatcher
import com.gestorfinances.app.domain.rules.toRecurrenceRule
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.movement_validation_account_required
import com.gestorfinances.ui.resources.movement_validation_amount_positive
import com.gestorfinances.ui.resources.movement_validation_amount_required
import com.gestorfinances.ui.resources.movement_validation_category_invalid
import com.gestorfinances.ui.resources.movement_validation_context_required
import com.gestorfinances.ui.resources.movement_validation_date_invalid
import com.gestorfinances.ui.resources.movement_validation_date_required
import com.gestorfinances.ui.resources.movement_validation_destination_required
import com.gestorfinances.ui.resources.movement_validation_income_owner
import com.gestorfinances.ui.resources.movement_validation_preserved_payer_amount
import com.gestorfinances.ui.resources.movement_validation_refunded_expense_type
import com.gestorfinances.ui.resources.movement_validation_shared_transfer
import com.gestorfinances.ui.resources.movement_validation_transfer_same_account
import com.gestorfinances.ui.resources.settlement_validation_person_required
import com.gestorfinances.ui.resources.split_validation_reconcile
import com.gestorfinances.ui.resources.tag_validation_trip_required
import org.jetbrains.compose.resources.StringResource
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.ExpenseFunding
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSplitDraft
import com.gestorfinances.app.data.repository.MovementSplitWrite
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonDraft
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.PersonSummary
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
    private val personRepository: PersonRepository,
    private val splitRepository: SplitRepository?,
    private val templateRepository: TemplateRepository?,
    private val references: () -> MovementsUiState,
    private val onSaved: () -> Unit,
    private val onPeopleChanged: (List<PersonSummary>) -> Unit,
) {
    private val _form = MutableStateFlow<MovementFormState?>(null)
    val form: StateFlow<MovementFormState?> = _form.asStateFlow()

    /** Set synchronously when a write is launched and cleared when it finishes, so a second tap
     * before the first save completes cannot submit the same movement again. */
    private var saveInFlight = false

    /** Active recurring items, loaded as a new form opens, to recognise the movement as one of them. */
    private var templates: List<TemplateSummary> = emptyList()

    /** Shows [form]; the ledger opens it once it has the data a new or edited form starts from. */
    fun open(form: MovementFormState) {
        _form.value = form
        val repository = templateRepository ?: return
        if (!form.isNew) return
        scope.launch {
            templates = withContext(ioDispatcher) { runCatching { repository.listActive() }.getOrDefault(emptyList()) }
            _form.value?.let { _form.value = withRecurringMatch(it) }
        }
    }

    /**
     * Looks for the recurring item a new movement is the pending occurrence of, so saving can link
     * it as that occurrence (offered ticked, and kept unticked once the person unticks it).
     */
    private fun withRecurringMatch(form: MovementFormState): MovementFormState {
        val amount = parseEuroCents(form.amount, allowNegative = false)?.takeIf { it > 0L }
        val date = parseDate(form.date)
        val paidByPerson = form.type == MovementType.EXPENSE && form.expenseKind == ExpenseKind.DEBT
        val match = if (form.isNew && form.templateId == null && !form.isRecurring && !paidByPerson && amount != null && date != null) {
            RecurringMatcher.pendingOccurrenceOf(
                templates = templates,
                type = form.type,
                accountId = form.accountId,
                destinationAccountId = form.destinationAccountId,
                categoryId = form.categoryId,
                amountCents = amount,
                date = date,
            )
        } else {
            null
        }
        val suggestion = match?.let {
            RecurringMatchSuggestion(
                templateId = it.id,
                name = it.name ?: it.payee ?: it.categoryName.orEmpty(),
                dueDate = it.nextDueDate,
                fixedAmountCents = it.amountCents.takeIf { _ -> !it.amountIsVariable },
            )
        }
        return form.copy(
            recurringMatch = suggestion,
            linkToRecurring = if (suggestion?.templateId == form.recurringMatch?.templateId) form.linkToRecurring else true,
        )
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
                showOptional = true,
            )
        } else form
        _form.value = withRecurringMatch(normalizeForm(withAccountDefaults))
    }

    fun onTripSelected(tripId: String?) {
        val form = _form.value ?: return
        val trip = tripId?.let { selectedId -> references().trips.firstOrNull { it.id == selectedId } }
        val tag = form.tagId?.let { tagId -> references().tags.firstOrNull { it.id == tagId } }
        // The trip's account replaces only the app default a new form starts with, never one the
        // person picked or the page the form was opened from preset.
        val accountId = if (
            form.isNew && trip?.defaultAccountId != null &&
            form.accountId == defaultAccountId(references().accounts)
        ) {
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

    fun onOtherPersonSelected(personId: String?) {
        val form = _form.value ?: return
        val splitEditor = if (form.payerSplit && personId != null) {
            form.splitEditor.withPayerPerson(personId)
        } else {
            form.splitEditor
        }
        onFormChanged(form.copy(forOtherPersonId = personId, splitEditor = splitEditor))
    }

    /** "La meva part" of an expense someone else paid: all of it, or a split with the payer in it. */
    fun onPayerSplitToggled(enabled: Boolean) {
        val form = _form.value ?: return
        val payer = form.forOtherPersonId
        onFormChanged(
            form.copy(
                payerSplit = enabled,
                splitEditor = if (enabled && payer != null) form.splitEditor.withPayerPerson(payer) else form.splitEditor,
            ),
        )
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

    /** Shows a failure of work done for the form, such as creating a category from it. */
    fun showFailure(error: Throwable) {
        _form.value = _form.value?.copy(errorMessage = error.message ?: error.javaClass.simpleName)
    }

    /** "Revisa" -- dismisses the pending warning without saving, restoring the normal Save button
     *. It ends the save attempt, so its answers are dropped too. */
    fun onWarningDismissed() {
        val form = _form.value ?: return
        _form.value = form.copy(duplicateWarning = false, pendingDataLossWarning = null, saveDecisions = SaveDecisions())
    }

    /** Starts a save attempt with nothing answered yet. */
    fun onSaveClicked() = attemptSave(SaveDecisions())

    /** Save anyway after the duplicate warning (never block — warn). */
    fun onDuplicateOverrideClicked() = continueSave { it.copy(duplicateAccepted = true) }

    /** Accepts the [DataLossWarning.SPLIT_REMOVED] warning (data-loss protection — never block, warn). */
    fun onSplitRemovalAcceptedClicked() = continueSave { it.copy(splitRemovalAccepted = true) }

    /** "Finalitza la plantilla" choice on the [DataLossWarning.RECURRING_STOP] banner: the template is marked [TemplateStatus.ENDED] atomically with this save. */
    fun onRecurrenceStopEndClicked() = continueSave { it.copy(recurrenceStop = RecurrenceStopChoice.END_SERIES) }

    /** "Només desvincula aquest moviment" choice on the same banner: the old behavior -- this
     * movement is detached (`templateId = null` on save, already computed from `form.isRecurring`
     * being false) while the template itself keeps running untouched. */
    fun onRecurrenceStopUnlinkClicked() = continueSave { it.copy(recurrenceStop = RecurrenceStopChoice.DETACH) }

    /** Records one answer to the warning being shown and carries on with the same save attempt,
     * so the next unanswered question (if any) is asked and no answered one comes back. */
    private fun continueSave(answer: (SaveDecisions) -> SaveDecisions) {
        val form = _form.value ?: return
        attemptSave(answer(form.saveDecisions))
    }

    private fun attemptSave(decisions: SaveDecisions) {
        if (saveInFlight) return
        // Only the warning this attempt raises next is shown.
        val form = _form.value?.copy(saveDecisions = decisions, duplicateWarning = false, pendingDataLossWarning = null)
            ?: return
        saveDirectMovement(form)
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
    ): Pair<StringResource, MovementFormField>? = when {
        amountCents == null -> Res.string.movement_validation_amount_required to MovementFormField.AMOUNT
        amountCents <= 0L -> Res.string.movement_validation_amount_positive to MovementFormField.AMOUNT
        form.date.isBlank() -> Res.string.movement_validation_date_required to MovementFormField.DATE
        date == null -> Res.string.movement_validation_date_invalid to MovementFormField.DATE
        else -> null
    }

    private fun showValidationError(
        form: MovementFormState,
        error: Pair<StringResource, MovementFormField>,
    ) {
        _form.value = form.copy(
                errorRes = error.first,
                errorField = error.second,
                showOptional = form.showOptional || error.second in listOf(MovementFormField.TAG, MovementFormField.PERSON, MovementFormField.SPLIT, MovementFormField.INCOME_OWNER),
            )
    }

    private fun launchSave(form: MovementFormState, write: () -> Unit) {
        saveInFlight = true
        _form.value = form.copy(isSaving = true)
        scope.launch {
            val result = withContext(ioDispatcher) { runCatching(write) }
            saveInFlight = false
            result.fold(
                onSuccess = {
                    _form.value = null
                    onSaved()
                },
                onFailure = {
                    _form.value = form.copy(isSaving = false, errorMessage = it.message ?: it.javaClass.simpleName)
                },
            )
        }
    }

    private fun saveDirectMovement(
        form: MovementFormState,
    ) {
        val decisions = form.saveDecisions
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
        // Someone else paid: no account moves, the whole amount is owed to them, and it never recurs.
        val paidByPerson = form.type == MovementType.EXPENSE && form.expenseKind == ExpenseKind.DEBT
        val isRecurring = form.isRecurring && !paidByPerson
        // A new movement recognised as a recurring item's pending occurrence is saved as that occurrence.
        val occurrenceOf = form.recurringMatch?.takeIf { form.linkToRecurring && form.isNew && !isRecurring && !paidByPerson }
        // The owner advances the money of a split they paid; the payer does when someone else paid.
        val effectiveSplitEditor = when {
            isShared -> form.splitEditor?.copy(payerParticipantId = USER_PARTICIPANT_ID)
            paidByPerson && form.payerSplit -> form.splitEditor
            else -> null
        }
        val splitDraft = effectiveSplitEditor?.toMovementSplitDraft(amount)
        // Whether the *kind* can carry a split at all -- true for SHARED even when splitEditor is
        // currently null (that null means "unchanged," not "none": KeepExisting below leaves the
        // stored split as-is). Only false here means the save would actually drop it.
        val finalKindCarriesSplit = isForOther || isShared || paidByPerson

        val preservedPayerAmountCents = form.preservedPayerAmountCents.takeIf { paidByPerson && !form.payerSplit }

        val error = required.validationError(form) ?: when {
            form.type !in formMovementTypes ->
                Res.string.movement_validation_context_required to MovementFormField.TYPE
            // A refund only ever refers to an expense.
            form.hasActiveRefunds && form.type != MovementType.EXPENSE ->
                Res.string.movement_validation_refunded_expense_type to MovementFormField.TYPE
            paidByPerson && (form.forOtherPersonId == null || form.forOtherPersonId !in activePersonIds) ->
                Res.string.settlement_validation_person_required to MovementFormField.PERSON
            // The form cannot rebuild a stored split it keeps, so it does not guess one for a new total.
            preservedPayerAmountCents != null && amount != preservedPayerAmountCents ->
                Res.string.movement_validation_preserved_payer_amount to MovementFormField.AMOUNT
            !paidByPerson && (form.accountId == null || form.accountId !in activeAccountIds) ->
                Res.string.movement_validation_account_required to MovementFormField.ACCOUNT
            form.type == MovementType.TRANSFER &&
                (form.destinationAccountId == null || form.destinationAccountId !in activeAccountIds) ->
                Res.string.movement_validation_destination_required to MovementFormField.DESTINATION_ACCOUNT
            form.type == MovementType.TRANSFER && form.accountId == form.destinationAccountId ->
                Res.string.movement_validation_transfer_same_account to MovementFormField.DESTINATION_ACCOUNT
            // Contributions and withdrawals are created from the shared account.
            crossesOwnership ->
                Res.string.movement_validation_shared_transfer to if (isSharedAccount(form.accountId)) {
                    MovementFormField.ACCOUNT
                } else {
                    MovementFormField.DESTINATION_ACCOUNT
                }
            category != null && !category.supports(form.type) ->
                Res.string.movement_validation_category_invalid to MovementFormField.CATEGORY
            form.tagId != null && (form.tripId == null || tag == null || !tag.supportsTrip(trip)) ->
                Res.string.tag_validation_trip_required to MovementFormField.TAG
            // Landing in a shared account never decides whose income it is.
            incomeOnSharedAccount && form.expenseKind == null ->
                Res.string.movement_validation_income_owner to MovementFormField.INCOME_OWNER
            isForOther && (form.forOtherPersonId == null || form.forOtherPersonId !in activePersonIds) ->
                Res.string.settlement_validation_person_required to MovementFormField.PERSON
            effectiveSplitEditor != null && splitDraft == null ->
                (effectiveSplitEditor.calculation(amount).errorRes ?: Res.string.split_validation_reconcile) to
                    MovementFormField.SPLIT
            else -> null
        }

        if (error != null) {
            showValidationError(form, error)
            return
        }

        // Data-loss gate: an existing stored split that the final kind
        // can't carry (explicit un-share, or a type/kind switch away from SHARED/FOR_OTHER) would
        // otherwise be silently dropped by the `MovementSplitWrite.Remove` branch below. Warn once,
        // save only after the user accepts (never block). Each gate below asks its own question and
        // is passed only by its own answer in [SaveDecisions].
        val willRemoveExistingSplit = !form.isNew && form.existingSplit && !finalKindCarriesSplit
        if (!decisions.splitRemovalAccepted && willRemoveExistingSplit) {
            _form.value = form.copy(
                    pendingDataLossWarning = DataLossWarning.SPLIT_REMOVED,
                    errorRes = null,
                    errorMessage = null,
                )
            return
        }

        // Recurring toggle-off: turning recurrence off on a movement
        // that's still linked to a template is ambiguous -- does the whole series stop, or does
        // only this occurrence detach? Warn once (never block) and let the answer decide:
        // [RecurrenceStopChoice.END_SERIES] also ends the template below; [RecurrenceStopChoice.DETACH]
        // just proceeds (the draft's `templateId` is already null below since `isRecurring` is false).
        // An expense someone else paid cannot stay linked, so it stops the recurrence the same way.
        val isRecurrenceStop = !isRecurring && form.templateId != null
        if (decisions.recurrenceStop == null && isRecurrenceStop) {
            _form.value = form.copy(
                    pendingDataLossWarning = DataLossWarning.RECURRING_STOP,
                    errorRes = null,
                    errorField = null,
                    errorMessage = null,
                )
            return
        }

        if (!decisions.duplicateAccepted && !paidByPerson && isDuplicate(form, requireNotNull(amount), requireNotNull(date))) {
            _form.value = form.copy(duplicateWarning = true, errorRes = null, errorMessage = null)
            return
        }

        val now = Instant.now().toString()
        // A new recurring movement seeds a template and links to it, so the saved movement reads as
        // recurring (🔁) and future occurrences are scheduled.
        // We also allow toggling recurrence ON for an existing movement that wasn't recurring.
        val isNewRecurrence = isRecurring && form.templateId == null
        val recurringTemplateId = if (isNewRecurrence && templateRepository != null) {
            UUID.randomUUID().toString()
        } else {
            form.templateId.takeIf { isRecurring }
        }

        val draft = MovementDraft(
            id = form.movementId ?: UUID.randomUUID().toString(),
            type = form.type,
            amountCents = requireNotNull(amount),
            date = requireNotNull(date).toString(),
            accountId = if (paidByPerson) null else requireNotNull(form.accountId),
            destinationAccountId = form.destinationAccountId.takeIf { form.type == MovementType.TRANSFER },
            categoryId = form.categoryId.takeIf { form.type != MovementType.TRANSFER },
            tripId = form.tripId,
            tagId = form.tagId,
            name = form.name.nullIfBlank(),
            payee = form.payee.nullIfBlank(),
            notes = form.notes.nullIfBlank(),
            isOneTime = form.type == MovementType.EXPENSE && form.isOneTime,
            splitWrite = when {
                // A stored split the form cannot rebuild is left exactly as it is.
                paidByPerson && preservedPayerAmountCents != null -> MovementSplitWrite.KeepExisting
                paidByPerson && effectiveSplitEditor == null -> MovementSplitWrite.Replace(
                    MovementSplitDraft(
                        entryMethod = SplitEntryMethod.EXACT,
                        lines = listOf(SplitLineDraft(SplitParticipantKind.USER, null, requireNotNull(amount))),
                    ),
                )
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
                // Also drops the owed line of an expense someone else paid once the owner has paid it.
                !form.isNew && !finalKindCarriesSplit -> MovementSplitWrite.Remove
                else -> MovementSplitWrite.KeepExisting
            },
            templateId = recurringTemplateId ?: occurrenceOf?.templateId,
            expenseFunding = if (form.type == MovementType.EXPENSE && !paidByPerson && isSharedAccount(form.accountId)) {
                ExpenseFunding.SHARED_ACCOUNT
            } else {
                ExpenseFunding.OWNER
            },
            payerPersonId = form.forOtherPersonId.takeIf { paidByPerson },
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
                if (decisions.recurrenceStop == RecurrenceStopChoice.END_SERIES && form.templateId != null) {
                    requireNotNull(templateRepository) { "template repository unavailable" }
                        .setStatus(form.templateId, TemplateStatus.ENDED, updatedAt = now)
                }
                // The occurrence it is no longer comes due, unless the item moved on meanwhile.
                occurrenceOf?.let { match ->
                    val repository = requireNotNull(templateRepository) { "template repository unavailable" }
                    val template = repository.getActive(match.templateId)
                    if (template != null && template.nextDueDate == match.dueDate) {
                        val next = RecurringAdvancer.nextOccurrence(template.toRecurrenceRule(), LocalDate.parse(template.nextDueDate))
                        repository.advanceCursor(template.id, next.toString(), updatedAt = now)
                    }
                    if (form.updateRecurringAmount && match.fixedAmountCents != null && amount != match.fixedAmountCents) {
                        repository.updateAmount(match.templateId, requireNotNull(amount), updatedAt = now)
                    }
                }
                if (form.movementId == null) {
                    movementRepository.create(draft, createdAt = now)
                } else {
                    movementRepository.update(draft, updatedAt = now)
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
     * Runs on every form edit. The form keeps every entered value for its whole life whatever the
     * type or kind (a switch to TRANSFER and back must not lose a split): only the relevant section
     * is rendered, incompatible values are stripped when the draft is built in [saveDirectMovement],
     * and a stored split the final kind cannot carry goes through
     * [MovementFormState.pendingDataLossWarning]. What is normalized here is category/tag
     * compatibility with the current type, and the transient error, warning and save-answer flags.
     */
    private fun normalizeForm(form: MovementFormState): MovementFormState {
        val category = form.categoryId?.let { catId ->
            references().categories.firstOrNull { it.id == catId }
        }
        val tag = form.tagId?.let { tId ->
            references().tags.firstOrNull { it.id == tId }
        }
        val trip = form.tripId?.let { tripId ->
            references().trips.firstOrNull { it.id == tripId }
        }

        val finalCategoryId = if (category != null && !category.supports(form.type)) null else form.categoryId
        val finalTagId = if (form.tripId == null || tag == null || !tag.supportsTrip(trip)) null else form.tagId

        return form.copy(
            categoryId = finalCategoryId,
            tagId = finalTagId,
            errorRes = null,
            errorField = null,
            errorMessage = null,
            duplicateWarning = false,
            pendingDataLossWarning = null,
            saveDecisions = SaveDecisions(),
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
            if (existing.id == form.movementId) return@any false
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

/** Identifies which field a movement-form validation error belongs to --
 * paired with [MovementFormState.errorRes] so the form can highlight the offending control and
 * scroll it into view instead of only showing a message the user may have scrolled past. Kept as
 * a single field (not a list): the validation chain in `saveDirectMovement` already stops at the first
 * failing condition. */
enum class MovementFormField {
    TYPE,
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
 * -- never a hard block, always a dismissible "save anyway." */
enum class DataLossWarning {
    /** An existing stored split will be removed because the final kind can't carry one. */
    SPLIT_REMOVED,
    /** Recurrence was toggled off on a movement still linked to a template: ambiguous whether the whole series should stop or just this occurrence
     * should detach -- offers both choices instead of silently doing either. */
    RECURRING_STOP,
}

/**
 * The answers the user has given to save warnings during the current save attempt: one answer
 * per question, so answering one never answers another. Save starts an attempt with none; an
 * edit to the form or "Revisa" ends it.
 */
data class SaveDecisions(
    val splitRemovalAccepted: Boolean = false,
    /** What happens to the series when recurrence is turned off; null until the user chooses. */
    val recurrenceStop: RecurrenceStopChoice? = null,
    val duplicateAccepted: Boolean = false,
)

enum class RecurrenceStopChoice {
    /** "Finalitza la plantilla": the template ends together with this save. */
    END_SERIES,
    /** "Només desvincula aquest moviment": the template keeps running. */
    DETACH,
}

data class MovementFormState(
    /** The movement being edited, whoever paid it; null while adding one. */
    val movementId: String? = null,
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
    /** Set by [MovementEditor]'s pre-save gate when the save-in-
     * progress would remove a stored split or stop a linked recurrence -- mirrors
     * [duplicateWarning]'s dismissible "save anyway" mechanism, never a hard block. */
    val pendingDataLossWarning: DataLossWarning? = null,
    val saveDecisions: SaveDecisions = SaveDecisions(),
    val errorRes: StringResource? = null,
    /** Which control [errorRes] refers to -- null for a save-time
     * failure that isn't attributable to a single field. */
    val errorField: MovementFormField? = null,
    val errorMessage: String? = null,
    val expenseKind: ExpenseKind? = null,
    val forOtherPersonId: String? = null,
    val isRecurring: Boolean = false,
    val recurringFrequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val templateId: String? = null,
    /** The linked template's status when [templateId] is set -- loaded
     * on edit so the form can reflect the template's real state; null for a new/unlinked movement. */
    val templateStatus: TemplateStatus? = null,
    /** The recurring item a new movement looks like the pending occurrence of. */
    val recurringMatch: RecurringMatchSuggestion? = null,
    /** Whether saving links the movement to [recurringMatch] as that occurrence. */
    val linkToRecurring: Boolean = true,
    /** Whether saving also sets [recurringMatch]'s fixed amount to this movement's. */
    val updateRecurringAmount: Boolean = false,
    val showOptional: Boolean = false,
    /** An expense someone else paid is split instead of the owner owing all of it. */
    val payerSplit: Boolean = false,
    /** True while this form's write is running; Save is disabled until it finishes. */
    val isSaving: Boolean = false,
    /** An edited expense that active refunds refer to, which therefore must stay an expense. */
    val hasActiveRefunds: Boolean = false,
    /** The stored amount of an edited expense someone else paid whose split is not the owner owing
     * all of it (a part, or no active line at all). Its split is saved back untouched instead of
     * the whole-amount debt a new entry records, so the amount must stay as stored. */
    val preservedPayerAmountCents: Long? = null,
) {
    /** True for a fresh "add" flow, as opposed to editing an existing movement. */
    val isNew: Boolean get() = movementId == null
}

/** A recurring item a new movement is recognised as, named for the form, with the occurrence date it would fill. */
data class RecurringMatchSuggestion(
    val templateId: String,
    val name: String,
    val dueDate: String,
    /** Its amount when fixed, which a different amount here may replace. */
    val fixedAmountCents: Long?,
)

private fun defaultAccountId(accounts: List<AccountSummary>): String? =
    accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id

fun newMovementForm(
    accounts: List<AccountSummary>,
    trips: List<TripSummary>,
    tripId: String?,
    debtPayerPersonId: String? = null,
    presetAccountId: String? = null,
    categories: List<CategoryRecord> = emptyList(),
    presetCategoryId: String? = null,
    presetTag: TagSummary? = null,
): MovementFormState {
    val category = (presetCategoryId ?: presetTag?.categoryId)?.let { id -> categories.firstOrNull { it.id == id } }
    val trip = (tripId ?: presetTag?.tripId)?.let { selectedId -> trips.firstOrNull { it.id == selectedId } }
    val accountId = presetAccountId?.takeIf { id -> accounts.any { it.id == id } }
        ?: trip?.defaultAccountId?.takeIf { id -> accounts.any { it.id == id } }
        ?: defaultAccountId(accounts)
    val account = accounts.firstOrNull { it.id == accountId }
    val sharedAccount = debtPayerPersonId == null && account?.ownershipKind == AccountOwnershipKind.SHARED
    return MovementFormState(
        type = if (category?.kind == CategoryKind.INCOME) MovementType.INCOME else MovementType.EXPENSE,
        categoryId = category?.id,
        tagId = presetTag?.id,
        accountId = accountId,
        tripId = trip?.id,
        date = LocalDate.now().toString(),
        expenseKind = when {
            debtPayerPersonId != null -> ExpenseKind.DEBT
            sharedAccount -> ExpenseKind.SHARED
            else -> ExpenseKind.PERSONAL
        },
        splitEditor = if (sharedAccount) account?.defaultExpenseSplitEditor() else null,
        forOtherPersonId = debtPayerPersonId,
        showOptional = trip?.id != null || sharedAccount || debtPayerPersonId != null,
    )
}

/** The split an expense on this shared account starts with: its members' default shares. */
fun AccountSummary.defaultExpenseSplitEditor(): SplitEditorState =
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

/** A stored split of [amountCents] as the split editor shows it, the owner paying. */
fun MovementSplitDraft.toSplitEditorState(amountCents: Long): SplitEditorState =
    SplitEditorState(
        method = entryMethod,
        selectedPersonIds = lines.filter { it.participantKind == SplitParticipantKind.PERSON }.mapNotNull { it.personId },
        payerParticipantId = USER_PARTICIPANT_ID,
        exactAmounts = if (entryMethod == SplitEntryMethod.EXACT) {
            lines.associate { line ->
                (line.personId ?: USER_PARTICIPANT_ID) to formatEuroInput(line.owedAmountCents)
            }
        } else {
            emptyMap()
        },
        percentages = if (entryMethod == SplitEntryMethod.PERCENTAGE) {
            lines.associate { line ->
                val participantId = line.personId ?: USER_PARTICIPANT_ID
                val percentStr = line.owedPercent?.let { "%.2f".format(it).replace(".", ",") }
                    ?: if (amountCents > 0) "%.2f".format(line.owedAmountCents.toDouble() * 100.0 / amountCents).replace(".", ",") else ""
                participantId to percentStr
            }
        } else {
            emptyMap()
        },
    )

fun MovementSummary.toFormState(
    splitDraft: MovementSplitDraft? = null,
    template: TemplateSummary? = null,
): MovementFormState {
    // DEBT (type 4): an expense a person paid. No account moved and it never recurs. The owner owing
    // the whole amount is rewritten from the amount on save; a split that reconciles with the amount
    // opens in the split editor. Anything else (no active lines: the owner owes nothing, never proof
    // that they owe it all) is kept exactly as stored.
    if (paidByPerson) {
        val owesWholeAmount = splitDraft?.lines?.singleOrNull()?.let { line ->
            line.participantKind == SplitParticipantKind.USER && line.owedAmountCents == amountCents
        } == true
        val editableSplit = splitDraft?.takeIf { draft ->
            !owesWholeAmount &&
                draft.lines.any { it.participantKind == SplitParticipantKind.USER } &&
                draft.lines.sumOf { it.owedAmountCents } == amountCents
        }
        return MovementFormState(
            movementId = id,
            type = MovementType.EXPENSE,
            amount = formatEuroInput(amountCents),
            date = date,
            categoryId = categoryId,
            tripId = tripId,
            tagId = tagId,
            name = name.orEmpty(),
            payee = payee.orEmpty(),
            notes = notes.orEmpty(),
            isOneTime = isOneTime,
            expenseKind = ExpenseKind.DEBT,
            forOtherPersonId = payerId,
            payerSplit = editableSplit != null,
            splitEditor = editableSplit?.toSplitEditorState(amountCents)?.let { editor ->
                payerId?.let(editor::withPayer) ?: editor
            },
            preservedPayerAmountCents = amountCents.takeUnless { owesWholeAmount || editableSplit != null },
            // Who paid is chosen among the details, so they open with it.
            showOptional = true,
        )
    }

    val userLine = splitDraft?.lines?.firstOrNull { it.participantKind == SplitParticipantKind.USER }
    val personLines = splitDraft?.lines?.filter { it.participantKind == SplitParticipantKind.PERSON } ?: emptyList()

    // FOR_OTHER (type 3): user owes 0, exactly one person owes the full amount.
    if (userLine != null && userLine.owedAmountCents == 0L &&
        personLines.size == 1 && personLines.first().owedAmountCents == amountCents
    ) {
        val hasDetails = isOneTime || !payee.isNullOrEmpty() || !notes.isNullOrEmpty()
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
            showOptional = tripId != null || tagId != null || isRecurring || isShared || hasDetails,
        )
    }

    // SHARED (type 2): split exists; PERSONAL (type 1): no split.
    val splitEditor = splitDraft?.toSplitEditorState(amountCents)

    val expenseKind = when {
        type != MovementType.EXPENSE && type != MovementType.INCOME -> null
        splitEditor != null -> ExpenseKind.SHARED
        else -> ExpenseKind.PERSONAL
    }
    val hasDetails = isOneTime || !payee.isNullOrEmpty() || !notes.isNullOrEmpty()

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
        showOptional = tripId != null || tagId != null || isRecurring || isShared || hasDetails,
    )
}
