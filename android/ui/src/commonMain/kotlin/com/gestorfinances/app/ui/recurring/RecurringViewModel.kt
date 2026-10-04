package com.gestorfinances.app.ui.recurring

import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.movement_validation_account_required
import com.gestorfinances.ui.resources.movement_validation_amount_positive
import com.gestorfinances.ui.resources.movement_validation_amount_required
import com.gestorfinances.ui.resources.movement_validation_date_invalid
import com.gestorfinances.ui.resources.movement_validation_date_required
import com.gestorfinances.ui.resources.movement_validation_income_owner
import com.gestorfinances.ui.resources.movement_validation_shared_transfer
import com.gestorfinances.ui.resources.movement_validation_transfer_same_account
import com.gestorfinances.ui.resources.settlement_validation_person_required
import com.gestorfinances.ui.resources.split_validation_reconcile
import com.gestorfinances.ui.resources.template_validation_allocation_kept
import com.gestorfinances.ui.resources.template_validation_amount_required
import com.gestorfinances.ui.resources.template_validation_anchor_invalid
import com.gestorfinances.ui.resources.template_validation_date_flex_invalid
import com.gestorfinances.ui.resources.template_validation_interval_required
import com.gestorfinances.ui.resources.template_validation_person_required
import org.jetbrains.compose.resources.StringResource
import com.gestorfinances.app.data.repository.RecurringMonth
import com.gestorfinances.app.data.repository.BudgetRepository
import com.gestorfinances.app.domain.rules.RecurringMatcher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.data.FinancialDataRevision
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.ExpenseFunding
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSplitDraft
import com.gestorfinances.app.data.repository.MovementSplitWrite
import com.gestorfinances.app.data.repository.PersonDraft
import com.gestorfinances.app.data.repository.SplitEntryMethod
import com.gestorfinances.app.data.repository.SplitLineDraft
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.data.repository.SettlementDraft
import com.gestorfinances.app.data.repository.SplitParticipantKind
import com.gestorfinances.app.data.repository.SplitRepository
import com.gestorfinances.app.data.repository.TemplateDraft
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateSplitConfig
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.data.repository.TagRepository
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.occurrenceSplit
import com.gestorfinances.app.data.repository.occurrenceSplitConfig
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.toTemplateSplitConfig
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.DetectedRecurringCandidate
import com.gestorfinances.app.domain.rules.DetectedTemplateAction
import com.gestorfinances.app.domain.rules.ExistingTemplateSignature
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.domain.rules.RecurringAdvancer
import com.gestorfinances.app.domain.rules.RecurringCandidateMovement
import com.gestorfinances.app.domain.rules.RecurringPatternDetector
import com.gestorfinances.app.domain.rules.SettlementScope
import com.gestorfinances.app.domain.rules.toRecurrenceRule
import com.gestorfinances.app.notifications.NotificationRefresher
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.movements.ExpenseKind
import com.gestorfinances.app.ui.movements.SplitEditorState
import com.gestorfinances.app.ui.movements.defaultExpenseSplitEditor
import com.gestorfinances.app.ui.movements.toSplitEditorState
import com.gestorfinances.app.ui.movements.supportsTrip
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecurringViewModel(
    private val templateRepository: TemplateRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val tripRepository: TripRepository,
    private val tagRepository: TagRepository,
    private val movementRepository: MovementRepository,
    private val splitRepository: SplitRepository,
    private val personRepository: PersonRepository,
    private val budgetRepository: BudgetRepository? = null,
    private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val financialDataRevision: FinancialDataRevision = FinancialDataRevision(),
) : ViewModel() {
    private val _state = MutableStateFlow(RecurringUiState())
    val state: StateFlow<RecurringUiState> = _state.asStateFlow()

    init {
        // Any committed financial write, this view model's own or another overlay's, reloads it.
        viewModelScope.launch {
            financialDataRevision.value.drop(1).collect { onScreenShown() }
        }
    }

    /** Set synchronously while a confirmed occurrence is being written, so a repeated tap cannot
     * record the same occurrence twice. */
    private var confirmInFlight = false

    fun onScreenShown() {
        refresh()
        _state.value.historyDetail?.template?.let(::onHistoryClicked)
    }

    fun resetForMenuNavigation() {
        _state.value = RecurringUiState()
        refresh()
    }

    fun onAddClicked() {
        val startDate = today()
        val form = TemplateFormState(
            nextDueDate = startDate.toString(),
            dayOfMonth = startDate.dayOfMonth.toString(),
            weekday = startDate.dayOfWeek.value - 1,
        )
        _state.value = _state.value.copy(form = form)
        // It starts on the default account, as a new movement does; arriving there through the
        // form's own change also settles who the expense is for.
        _state.value.accounts.firstOrNull { it.isDefault }?.let { onFormChanged(form.copy(accountId = it.id)) }
    }

    fun onEditClicked(template: TemplateSummary) {
        _state.value = _state.value.copy(form = template.toFormState())
    }

    fun onHistoryClicked(template: TemplateSummary) {
        _state.value = _state.value.copy(
            historyDetail = RecurringHistoryDetailState(template = template, isLoading = true),
        )
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { movementRepository.listActive().filter { it.templateId == template.id } }
            }
            if (_state.value.historyDetail?.template?.id != template.id) return@launch
            _state.value = result.fold(
                onSuccess = { movements ->
                    _state.value.copy(
                        historyDetail = RecurringHistoryDetailState(
                            template = template,
                            movements = movements,
                        ),
                    )
                },
                onFailure = { error ->
                    _state.value.copy(
                        historyDetail = RecurringHistoryDetailState(
                            template = template,
                            errorMessage = error.message ?: error.javaClass.simpleName,
                        ),
                    )
                },
            )
        }
    }

    /** Loads an item's page: its history, which its amount chart reads too. */
    fun onDetailOpened(templateId: String) {
        val template = _state.value.templates.firstOrNull { it.id == templateId } ?: return
        onHistoryClicked(template)
    }

    fun onHistoryDismissed() {
        _state.value = _state.value.copy(historyDetail = null)
    }

    fun onFormChanged(form: TemplateFormState) {
        val previous = _state.value.form
        // Arriving at another account or type asks afresh who it is for, like the movement form: one
        // account's answer never carries to another. An expense starts as the owner's own, or, on a
        // shared account, split as that account splits by default; an income there must be answered.
        val ownershipKept = previous == null || (previous.accountId == form.accountId && previous.type == form.type)
        val account = form.accountId?.let { id -> _state.value.accounts.firstOrNull { it.id == id } }
        val expenseStart = if (ownershipKept || form.type != MovementType.EXPENSE || account == null) {
            null
        } else if (account.ownershipKind == AccountOwnershipKind.SHARED) {
            ExpenseKind.SHARED to account.defaultExpenseSplitEditor()
        } else {
            ExpenseKind.PERSONAL to null
        }
        val trip = form.tripId?.let { id -> _state.value.trips.firstOrNull { it.id == id } }
        val tag = form.tagId?.let { id -> _state.value.tags.firstOrNull { it.id == id } }
        val supportsCategory = form.type == MovementType.EXPENSE || form.type == MovementType.INCOME
        val isTransfer = form.type == MovementType.TRANSFER
        _state.value = _state.value.copy(
            form = form.copy(
                destinationAccountId = form.destinationAccountId.takeIf { isTransfer },
                categoryId = form.categoryId.takeIf { supportsCategory },
                tripId = form.tripId.takeIf { !isTransfer },
                tagId = form.tagId.takeIf { !isTransfer && tag?.supportsTrip(trip) == true },
                sharing = expenseStart?.first ?: form.sharing.takeIf { ownershipKept },
                splitEditor = expenseStart?.second ?: form.splitEditor.takeIf { ownershipKept },
                sharingPersonId = form.sharingPersonId.takeIf { ownershipKept },
                sharingChosen = expenseStart != null || (form.sharingChosen && ownershipKept),
                errorRes = null,
                errorField = null,
                errorMessage = null,
            ),
        )
    }

    /**
     * Who the item is for: the owner, shared, or another person. Shared starts from the account's
     * default member shares on a shared account, and from the owner alone on the owner's own, as
     * it does in the movement form.
     */
    fun onSharingSelected(owner: ExpenseKind) {
        val form = _state.value.form ?: return
        val account = _state.value.accounts.firstOrNull { it.id == form.accountId } ?: return
        val startingSplit = if (account.ownershipKind == AccountOwnershipKind.SHARED) account.defaultExpenseSplitEditor() else SplitEditorState()
        onFormChanged(
            form.copy(
                sharing = owner,
                sharingChosen = true,
                splitEditor = form.splitEditor ?: startingSplit.takeIf { owner == ExpenseKind.SHARED },
            ),
        )
    }

    fun onSplitEditorChanged(editor: SplitEditorState) {
        val form = _state.value.form ?: return
        onFormChanged(form.copy(splitEditor = editor, sharingChosen = true))
    }

    fun onSharingPersonSelected(personId: String?) {
        val form = _state.value.form ?: return
        onFormChanged(form.copy(sharingPersonId = personId, sharingChosen = true))
    }

    /** Adds a new person to the shared income's allocation, as the movement form's split editor does. */
    fun onCreatePersonInSplit(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val personId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        viewModelScope.launch {
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
                onSuccess = { people ->
                    // The person exists now, whether or not this template is ever saved.
                    financialDataRevision.markChanged()
                    val form = _state.value.form
                    _state.value = _state.value.copy(
                        people = people,
                        form = form?.copy(
                            splitEditor = form.splitEditor?.withPersonToggled(personId),
                            sharingChosen = true,
                        ),
                    )
                },
                onFailure = ::showError,
            )
        }
    }

    fun onFormDismissed() {
        _state.value = _state.value.copy(form = null)
    }

    fun onPauseClicked(template: TemplateSummary) = changeStatus(template.id, TemplateStatus.PAUSED)

    fun onResumeClicked(template: TemplateSummary) = changeStatus(template.id, TemplateStatus.ACTIVE)

    fun onEndClicked(template: TemplateSummary) {
        _state.value = _state.value.copy(endCandidate = template)
    }

    fun onEndDismissed() {
        _state.value = _state.value.copy(endCandidate = null)
    }

    fun onEndConfirmed() {
        val template = _state.value.endCandidate ?: return
        _state.value = _state.value.copy(endCandidate = null)
        changeStatus(template.id, TemplateStatus.ENDED)
    }

    /** A reminder tapped before the items had loaded, to open once they have. */
    private var pendingConfirmTemplateId: String? = null

    fun onConfirmClicked(prompt: DuePrompt) {
        val template = prompt.template
        _state.value = _state.value.copy(
            confirmPrompt = ConfirmPromptState(
                templateId = template.id,
                templateName = template.name ?: "",
                accountName = template.accountName,
                amount = template.expectedAmountCents?.let(::formatEuroInput).orEmpty(),
                date = prompt.dueDate,
                settlementPersonName = template.personName,
                splitConfig = template.occurrenceSplitConfig,
                fixedAmountCents = template.amountCents.takeIf { !template.amountIsVariable },
                pendingCount = prompt.pendingCount,
                dueDate = prompt.dueDate,
                frequency = template.frequency,
                type = template.type,
            ),
        )
    }

    /**
     * Opens the confirm sheet for [templateId]'s next occurrence, as a tapped reminder asks: the
     * one already due, or the one coming if it is not due yet. Waits for the items to load.
     */
    fun onConfirmRequested(templateId: String) {
        val state = _state.value
        val template = state.templates.firstOrNull { it.id == templateId }
        if (template == null) {
            // Not loaded yet (the app is just starting): the load opens it. A stale id is dropped there.
            pendingConfirmTemplateId = templateId
            refresh()
            return
        }
        pendingConfirmTemplateId = null
        if (template.status != TemplateStatus.ACTIVE) return
        promptFor(templateId)?.let(::onConfirmClicked)
    }

    /** The confirm sheet's quieter choices, each on the prompt it was opened for. */
    fun onConfirmSkipClicked(onSkipped: DeleteUndoHandler = {}) = confirmedPrompt()?.let { onSkipClicked(it, onSkipped) }

    fun onConfirmSkipAllClicked(onSkipped: DeleteUndoHandler = {}) = confirmedPrompt()?.let { onSkipAllClicked(it, onSkipped) }

    fun onConfirmAlreadyRecordedClicked() = confirmedPrompt()?.let(::onAlreadyRecordedClicked)

    private fun confirmedPrompt(): DuePrompt? {
        val templateId = _state.value.confirmPrompt?.templateId ?: return null
        _state.value = _state.value.copy(confirmPrompt = null)
        return promptFor(templateId)
    }

    /** The item's due occurrence, or its next one when a reminder opened it ahead of its date. */
    private fun promptFor(templateId: String): DuePrompt? {
        val state = _state.value
        return state.duePrompts.firstOrNull { it.template.id == templateId }
            ?: state.templates.firstOrNull { it.id == templateId }
                ?.let { DuePrompt(template = it, dueDate = it.nextDueDate, pendingCount = 1) }
    }

    fun onConfirmFormChanged(prompt: ConfirmPromptState) {
        _state.value = _state.value.copy(
            confirmPrompt = prompt.copy(errorRes = null, errorField = null, errorMessage = null),
        )
    }

    fun onConfirmDismissed() {
        _state.value = _state.value.copy(confirmPrompt = null)
    }

    fun onConfirmSaveClicked() {
        if (confirmInFlight) return
        val prompt = _state.value.confirmPrompt ?: return
        val template = _state.value.templates.firstOrNull { it.id == prompt.templateId } ?: return
        val amountCents = parseEuroCents(prompt.amount, allowNegative = false)
        val date = parseDate(prompt.date)
        val (errorRes, errorField) = when {
            amountCents == null -> Res.string.movement_validation_amount_required to ConfirmPromptField.AMOUNT
            amountCents <= 0L -> Res.string.movement_validation_amount_positive to ConfirmPromptField.AMOUNT
            prompt.date.isBlank() -> Res.string.movement_validation_date_required to ConfirmPromptField.DATE
            date == null -> Res.string.movement_validation_date_invalid to ConfirmPromptField.DATE
            else -> null to null
        }
        if (errorRes != null) {
            _state.value = _state.value.copy(
                confirmPrompt = prompt.copy(errorRes = errorRes, errorField = errorField),
            )
            return
        }
        val amount = requireNotNull(amountCents)
        val occurrenceDate = requireNotNull(date).toString()
        val now = Instant.now().toString()
        // A settlement occurrence goes through createSettlement so it lands with the person,
        // direction and scope its template carries -- a plain movement row cannot express those.
        val writeOccurrence: () -> Unit = if (template.type == MovementType.SETTLEMENT) {
            {
                movementRepository.createSettlement(
                    SettlementDraft(
                        id = UUID.randomUUID().toString(),
                        personId = requireNotNull(template.personId),
                        direction = requireNotNull(template.settlementDirection),
                        scope = requireNotNull(template.settlementScope),
                        amountCents = amount,
                        accountId = template.accountId,
                        date = occurrenceDate,
                        name = template.name,
                        notes = template.notes,
                        templateId = template.id,
                    ),
                    createdAt = now,
                )
            }
        } else {
            {
                movementRepository.create(
                    MovementDraft(
                        id = UUID.randomUUID().toString(),
                        type = template.type,
                        amountCents = amount,
                        date = occurrenceDate,
                        accountId = template.accountId,
                        destinationAccountId = template.destAccountId,
                        categoryId = template.categoryId,
                        tripId = template.tripId,
                        tagId = template.tagId,
                        name = template.name,
                        payee = template.payee,
                        notes = template.notes,
                        isOneTime = false,
                        splitWrite = template.occurrenceSplitConfig?.occurrenceSplit(amount)
                            ?.let(MovementSplitWrite::Replace)
                            ?: MovementSplitWrite.KeepExisting,
                        templateId = template.id,
                        expenseFunding = if (
                            template.type == MovementType.EXPENSE &&
                            accountRepository.getActive(template.accountId)?.ownershipKind == AccountOwnershipKind.SHARED
                        ) ExpenseFunding.SHARED_ACCOUNT else ExpenseFunding.OWNER,
                    ),
                    createdAt = now,
                )
            }
        }
        confirmInFlight = true
        _state.value = _state.value.copy(confirmPrompt = prompt.copy(isSaving = true))
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    movementRepository.runInTransaction {
                        writeOccurrence()
                        templateRepository.advanceCursor(template.id, template.advancedOneStep(), updatedAt = now)
                        if (prompt.updatesAmountTo(amount)) templateRepository.updateAmount(template.id, amount, updatedAt = now)
                    }
                }
            }
            confirmInFlight = false
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(confirmPrompt = null)
                    financialDataRevision.markChanged()
                    refreshNotifications()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        confirmPrompt = prompt.copy(errorMessage = it.message ?: it.javaClass.simpleName),
                    )
                },
            )
        }
    }

    /** Skips the occurrence; [onSkipped] is handed the way back to the date it was on. */
    fun onSkipClicked(prompt: DuePrompt, onSkipped: DeleteUndoHandler = {}) {
        advanceCursorTo(prompt.template, onSkipped) { prompt.template.advancedOneStep() }
    }

    fun onSkipAllClicked(prompt: DuePrompt, onSkipped: DeleteUndoHandler = {}) {
        advanceCursorTo(prompt.template, onSkipped) { prompt.template.advancedToToday(today()) }
    }

    // computeNextDueDate runs inside the runCatching/ioDispatcher block below, not on the
    // caller's thread: RecurringAdvancer.advance can throw (its occurrence ceiling), and letting
    // that happen on the UI thread would crash instead of surfacing as a benign error.
    private fun advanceCursorTo(template: TemplateSummary, onSkipped: DeleteUndoHandler, computeNextDueDate: () -> String) {
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { templateRepository.advanceCursor(template.id, computeNextDueDate(), updatedAt = now) }
            }
            result.fold(
                onSuccess = {
                    financialDataRevision.markChanged()
                    refreshNotifications()
                    onSkipped {
                        withContext(ioDispatcher) {
                            runCatching { templateRepository.advanceCursor(template.id, template.nextDueDate, updatedAt = Instant.now().toString()) }
                        }.onFailure(::showError)
                        financialDataRevision.markChanged()
                        refreshNotifications()
                    }
                },
                onFailure = ::showError,
            )
        }
    }

    /** Opens the unlinked past payments that could be [template]'s, to link several at once. */
    fun onLinkPaymentsClicked(template: TemplateSummary) {
        _state.value = _state.value.copy(bulkLink = BulkLinkState(template = template))
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val movements = movementRepository.listActive()
                    RecurringMatcher.pastCandidates(template, movements, today()) to movements.filter { it.templateId == template.id }
                }
            }
            val current = _state.value.bulkLink?.takeIf { it.template.id == template.id } ?: return@launch
            val (candidates, linked) = result.getOrDefault(emptyList<MovementSummary>() to emptyList())
            _state.value = _state.value.copy(
                bulkLink = current.copy(
                    candidates = candidates,
                    onePerOccurrenceIds = RecurringMatcher.onePerOccurrence(template, candidates, linked),
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName },
                ),
            )
        }
    }

    fun onLinkPaymentToggled(movementId: String) {
        val link = _state.value.bulkLink ?: return
        val selected = if (movementId in link.selectedIds) link.selectedIds - movementId else link.selectedIds + movementId
        _state.value = _state.value.copy(bulkLink = link.copy(selectedIds = selected))
    }

    /** Ticks one payment per expected date (or clears them, once they all are). */
    fun onLinkPaymentsMarkAllClicked() {
        val link = _state.value.bulkLink ?: return
        val selected = if (link.allMarked) emptySet() else link.onePerOccurrenceIds
        _state.value = _state.value.copy(bulkLink = link.copy(selectedIds = selected))
    }

    fun onLinkPaymentsDismissed() {
        _state.value = _state.value.copy(bulkLink = null)
    }

    /**
     * Links the picked payments to the item; any of its occurrences they already cover, up to the
     * latest of them (and its margin), no longer come due.
     */
    fun onLinkPaymentsConfirmed() {
        val link = _state.value.bulkLink ?: return
        val picked = link.candidates.filter { it.id in link.selectedIds }
        if (picked.isEmpty()) return
        val template = link.template
        _state.value = _state.value.copy(bulkLink = null)
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    movementRepository.runInTransaction {
                        movementRepository.linkToTemplate(picked.map { it.id }, templateId = template.id, updatedAt = now)
                        val latest = picked.maxOf { LocalDate.parse(it.date) }
                        val cursor = LocalDate.parse(template.nextDueDate)
                        val next = RecurringAdvancer.advance(template.toRecurrenceRule(), cursor, latest.plusDays(template.marginDays)).newCursor
                        if (next != cursor) templateRepository.advanceCursor(template.id, next.toString(), updatedAt = now)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    financialDataRevision.markChanged()
                    refreshNotifications()
                },
                onFailure = ::showError,
            )
        }
    }

    /** Opens the movements that could be [prompt]'s occurrence already recorded by hand. */
    fun onAlreadyRecordedClicked(prompt: DuePrompt) {
        _state.value = _state.value.copy(linkPicker = LinkPickerState(prompt = prompt))
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { RecurringMatcher.recordedCandidates(prompt.template, movementRepository.listActive()) }
            }
            val picker = _state.value.linkPicker?.takeIf { it.prompt == prompt } ?: return@launch
            _state.value = _state.value.copy(
                linkPicker = picker.copy(
                    candidates = result.getOrDefault(emptyList()),
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName },
                ),
            )
        }
    }

    fun onLinkPickerDismissed() {
        _state.value = _state.value.copy(linkPicker = null)
    }

    /** Links [movement] as the picked prompt's occurrence, which then no longer comes due. */
    fun onRecordedMovementPicked(movement: MovementSummary) {
        val template = _state.value.linkPicker?.prompt?.template ?: return
        _state.value = _state.value.copy(linkPicker = null)
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    movementRepository.runInTransaction {
                        movementRepository.linkToTemplate(listOf(movement.id), templateId = template.id, updatedAt = now)
                        templateRepository.advanceCursor(template.id, template.advancedOneStep(), updatedAt = now)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    financialDataRevision.markChanged()
                    refreshNotifications()
                },
                onFailure = ::showError,
            )
        }
    }

    fun onDeleteClicked(template: TemplateSummary) {
        _state.value = _state.value.copy(deleteCandidate = template)
    }

    fun onDeleteDismissed() {
        _state.value = _state.value.copy(deleteCandidate = null)
    }

    /** Deleting a template (unlike ending it) severs its movements' links too, atomically: an
     * archived template is "treated as absent", so nothing should still claim a
     * relationship to it — the movements themselves are kept, just as plain non-recurring entries,
     * and become eligible for [RecurringPatternDetector] again. */
    fun onDeleteConfirmed(onSuccess: (undo: suspend () -> Unit) -> Unit = {}) {
        val template = _state.value.deleteCandidate ?: return
        _state.value = _state.value.copy(deleteCandidate = null)
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val linkedMovementIds = movementRepository.activeMovementIdsForTemplate(template.id)
                    movementRepository.runInTransaction {
                        movementRepository.unlinkAllForTemplate(template.id, updatedAt = now)
                        templateRepository.archive(template.id, archivedAt = now)
                    }
                    RecurringDeleteOperation(
                        templateId = template.id,
                        deletedAt = now,
                        linkedMovementIds = linkedMovementIds,
                    )
                }
            }
            result.fold(
                onSuccess = { operation ->
                    financialDataRevision.markChanged()
                    refreshNotifications()
                    onSuccess { undoDelete(operation) }
                },
                onFailure = ::showError,
            )
        }
    }

    fun onSaveClicked() {
        val form = _state.value.form ?: return
        if (form.isSaving) return
        // An approximate amount may be left blank: its recent occurrences give the estimate.
        val amountCents = form.amount.takeIf { it.isNotBlank() }?.let { parseEuroCents(it, allowNegative = false) }
        val nextDue = parseDate(form.nextDueDate)
        val account = form.accountId?.let { id -> _state.value.accounts.firstOrNull { it.id == id } }
        val isTransfer = form.type == MovementType.TRANSFER
        val isSettlement = form.type == MovementType.SETTLEMENT
        val dayOfMonth = form.dayOfMonth.trim().toLongOrNull()
        val intervalCount = form.intervalCount.trim().toLongOrNull()
        val dateFlexDays = form.dateFlex.trim()
            .takeIf { it.isNotBlank() }
            ?.toLongOrNull()
        // An edit carries the stored split forward untouched (a full-row update would otherwise
        // wipe it) unless the user answers anew whose an income into a shared account is. A stored
        // split names the people of its own account and type, so it never moves to another one.
        val edited = form.id?.let { id -> _state.value.templates.firstOrNull { it.id == id } }
        val existingSplitConfig = edited?.splitConfig
        val incomeOnSharedAccount = form.type == MovementType.INCOME &&
            account?.ownershipKind == AccountOwnershipKind.SHARED
        // An expense can always be shared; an income only within its shared account.
        val allocationRedefined = (form.type == MovementType.EXPENSE || incomeOnSharedAccount) && form.sharingChosen
        val chosenDraft = form.chosenSplitDraft(amountCents)

        val (errorRes, errorField) = when {
            !form.amountIsVariable && form.amount.isBlank() ->
                Res.string.template_validation_amount_required to TemplateFormField.AMOUNT
            form.amount.isNotBlank() && amountCents == null ->
                Res.string.template_validation_amount_required to TemplateFormField.AMOUNT
            amountCents != null && amountCents <= 0L ->
                Res.string.movement_validation_amount_positive to TemplateFormField.AMOUNT
            account == null -> Res.string.movement_validation_account_required to TemplateFormField.ACCOUNT
            existingSplitConfig != null && !allocationRedefined &&
                (form.type != edited?.type || form.accountId != edited?.accountId) ->
                Res.string.template_validation_allocation_kept to TemplateFormField.ACCOUNT
            // Landing in a shared account never decides whose income it is.
            incomeOnSharedAccount && form.sharing == null ->
                Res.string.movement_validation_income_owner to TemplateFormField.INCOME_OWNER
            // An income goes to another member of its account; an expense can be for anyone.
            allocationRedefined && form.sharing == ExpenseKind.FOR_OTHER && if (form.type == MovementType.INCOME) {
                account?.members.orEmpty().none { it.personId != null && it.personId == form.sharingPersonId }
            } else {
                _state.value.people.none { it.id == form.sharingPersonId }
            } ->
                Res.string.settlement_validation_person_required to TemplateFormField.PERSON
            allocationRedefined && form.sharing == ExpenseKind.SHARED && chosenDraft == null ->
                (form.splitEditor?.calculation(amountCents ?: VARIABLE_AMOUNT_WEIGHT_CENTS)?.errorRes
                    ?: Res.string.split_validation_reconcile) to TemplateFormField.SPLIT
            isTransfer && form.destinationAccountId == null ->
                Res.string.movement_validation_account_required to TemplateFormField.DESTINATION_ACCOUNT
            isTransfer && form.destinationAccountId == form.accountId ->
                Res.string.movement_validation_transfer_same_account to TemplateFormField.DESTINATION_ACCOUNT
            // Money into or out of a shared account is a contribution or withdrawal, made from that account.
            isTransfer && account?.ownershipKind !=
                _state.value.accounts.firstOrNull { it.id == form.destinationAccountId }?.ownershipKind ->
                Res.string.movement_validation_shared_transfer to TemplateFormField.DESTINATION_ACCOUNT
            isSettlement && form.personId == null ->
                Res.string.template_validation_person_required to TemplateFormField.PERSON
            form.frequency.usesDayOfMonth() && (dayOfMonth == null || dayOfMonth !in 1L..31L) ->
                Res.string.template_validation_anchor_invalid to TemplateFormField.SCHEDULE
            form.frequency == RecurrenceFrequency.CUSTOM && (intervalCount == null || intervalCount <= 0L) ->
                Res.string.template_validation_interval_required to TemplateFormField.SCHEDULE
            form.nextDueDate.isBlank() -> Res.string.movement_validation_date_required to TemplateFormField.NEXT_DUE_DATE
            nextDue == null -> Res.string.movement_validation_date_invalid to TemplateFormField.NEXT_DUE_DATE
            form.dateFlex.isNotBlank() && (dateFlexDays == null || dateFlexDays < 0L) ->
                Res.string.template_validation_date_flex_invalid to TemplateFormField.DATE_FLEX
            else -> null to null
        }
        if (errorRes != null) {
            // Who it is for sits under Més detalls: open it, or the error would not be seen.
            val hidden = errorField == TemplateFormField.PERSON || errorField == TemplateFormField.SPLIT
            _state.value = _state.value.copy(
                form = form.copy(errorRes = errorRes, errorField = errorField, showOptional = form.showOptional || hidden),
            )
            return
        }

        val splitConfig = when {
            // Mine stores no split; Shared and another member store the allocation they describe.
            allocationRedefined -> chosenDraft?.let { MovementSplitWrite.Replace(it).toTemplateSplitConfig() }
            existingSplitConfig != null -> existingSplitConfig
            // An expense on a shared account always has a split; without a stored one it gets the
            // account's default shares, the same split the movement form gives it.
            else -> requireNotNull(account)
                .takeIf { form.type == MovementType.EXPENSE && it.ownershipKind == AccountOwnershipKind.SHARED }
                ?.defaultExpenseSplitConfig(amountCents)
        }

        val carriesLedgerDetail = !isTransfer && !isSettlement
        val draft = TemplateDraft(
            id = form.id ?: UUID.randomUUID().toString(),
            type = form.type,
            amountCents = amountCents,
            accountId = requireNotNull(account).id,
            destAccountId = if (isTransfer) form.destinationAccountId else null,
            categoryId = if (carriesLedgerDetail) form.categoryId else null,
            tripId = if (carriesLedgerDetail) form.tripId else null,
            tagId = if (carriesLedgerDetail) form.tagId else null,
            personId = if (isSettlement) form.personId else null,
            settlementDirection = if (isSettlement) form.settlementDirection else null,
            settlementScope = if (isSettlement) form.settlementScope else null,
            name = form.name.trim().ifBlank { null },
            payee = form.payee.trim().ifBlank { null },
            notes = form.notes.trim().ifBlank { null },
            splitConfig = splitConfig,
            frequency = form.frequency,
            intervalCount = if (form.frequency == RecurrenceFrequency.CUSTOM) intervalCount else null,
            customUnit = if (form.frequency == RecurrenceFrequency.CUSTOM) form.customUnit else null,
            dayOfMonth = if (form.frequency.usesDayOfMonth()) dayOfMonth else null,
            weekday = if (form.frequency.usesWeekday()) form.weekday?.toLong() else null,
            nextDueDate = requireNotNull(nextDue).toString(),
            amountIsVariable = form.amountIsVariable,
            // No longer edited here: kept as stored.
            amountFlexCents = edited?.amountFlexCents,
            dateFlexDays = dateFlexDays,
            leadNotificationDays = edited?.leadNotificationDays,
            status = form.status,
        )

        val now = Instant.now().toString()
        _state.value = _state.value.copy(form = form.copy(isSaving = true))
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    if (form.id == null) {
                        templateRepository.create(draft, createdAt = now)
                    } else {
                        templateRepository.update(draft, updatedAt = now)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(form = null)
                    financialDataRevision.markChanged()
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

    private fun changeStatus(id: String, status: TemplateStatus) {
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { templateRepository.setStatus(id, status, updatedAt = now) }
            }
            result.fold(
                onSuccess = {
                    financialDataRevision.markChanged()
                    refreshNotifications()
                },
                onFailure = ::showError,
            )
        }
    }

    private fun showError(throwable: Throwable) {
        _state.value = _state.value.copy(errorMessage = throwable.message ?: throwable.javaClass.simpleName)
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val result = withContext(ioDispatcher) {
                runCatching {
                    LoadedRecurringData(
                        templates = templateRepository.listActive(),
                        accounts = accountRepository.listActive(),
                        categories = categoryRepository.listActive(),
                        trips = tripRepository.listActive(),
                        tags = tagRepository.listActive(),
                        people = personRepository.listActive(),
                        movements = movementRepository.listActive(),
                        occurrenceCounts = movementRepository.countsByTemplate(),
                    ).let { loaded ->
                        loaded to budgetRepository?.recurringMonth(YearMonth.from(today()), today(), loaded.templates)
                    }
                }
            }
            _state.value = result.fold(
                onSuccess = { (it, month) ->
                    _state.value.copy(
                        templates = it.templates,
                        accounts = it.accounts,
                        categories = it.categories,
                        trips = it.trips,
                        tags = it.tags,
                        people = it.people,
                        duePrompts = it.templates.toDuePrompts(today()),
                        month = month,
                        today = today(),
                        monthlyPaymentStates = it.templates.monthlyPaymentStates(today(), it.movements),
                        occurrenceCounts = it.occurrenceCounts,
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
            pendingConfirmTemplateId?.let { templateId ->
                pendingConfirmTemplateId = null
                if (result.isSuccess && _state.value.templates.any { it.id == templateId }) onConfirmRequested(templateId)
            }
        }
    }

    /** User-triggered, one-shot scan — never automatic/background. The detector
     * itself stays split-blind (correct layering — see [RecurringPatternDetector]); the split each
     * candidate *would* carry is resolved here, separately, purely so the review sheet can show a
     * "Compartit" badge and a user-share/total preview instead of a raw total that hides sharing
     * entirely. */
    fun onDetectRecurringClicked() {
        _state.value = _state.value.copy(isDetecting = true, errorMessage = null)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val movements = movementRepository.listActive().mapNotNull { it.toRecurringCandidateMovementOrNull() }
                    val templates = templateRepository.listActive()
                    val candidates = RecurringPatternDetector.detect(
                        movements,
                        templates.map { it.toExistingTemplateSignature() },
                        today = today(),
                    )
                    candidates.map { candidate ->
                        DetectionReviewItem(
                            candidate = candidate,
                            splitConfig = resolveSplitConfigForCandidate(
                                candidate,
                                matchedTemplate = templates.firstOrNull { it.id == candidate.matchedTemplateId },
                            ),
                        )
                    }
                }
            }
            _state.value = result.fold(
                onSuccess = { items ->
                    _state.value.copy(
                        isDetecting = false,
                        detectionReview = DetectionReviewState(items = items),
                    )
                },
                onFailure = {
                    _state.value.copy(isDetecting = false, errorMessage = it.message ?: it.javaClass.simpleName)
                },
            )
        }
    }

    fun onDetectionItemToggled(index: Int, accepted: Boolean) {
        val review = _state.value.detectionReview ?: return
        val updated = review.items.toMutableList().also { it[index] = it[index].copy(accepted = accepted) }
        _state.value = _state.value.copy(detectionReview = review.copy(items = updated))
    }

    fun onDetectionReviewDismissed() {
        _state.value = _state.value.copy(detectionReview = null)
    }

    /** Each accepted item is applied independently (rather than aborting the whole batch on the
     * first failure) so a single bad candidate can't stop the rest from being confirmed. Any
     * failures are left checked in the review sheet (skipped/unaccepted items stay as they were)
     * so retrying only re-attempts what actually failed — an already-applied item is never
     * resubmitted, which would otherwise create a duplicate template. */
    fun onDetectionConfirmAllClicked() {
        val review = _state.value.detectionReview ?: return
        val accepted = review.items.filter { it.accepted }
        if (accepted.isEmpty()) {
            onDetectionReviewDismissed()
            return
        }
        val now = Instant.now().toString()
        viewModelScope.launch {
            val failures = withContext(ioDispatcher) {
                accepted.mapNotNull { item ->
                    runCatching { applyDetectionItem(item.candidate, now) }.exceptionOrNull()?.let { item to it }
                }
            }
            if (failures.size < accepted.size) financialDataRevision.markChanged()
            refreshNotifications()
            _state.value = _state.value.copy(
                detectionReview = if (failures.isEmpty()) {
                    null
                } else {
                    val skipped = review.items.filterNot { it.accepted }
                    DetectionReviewState(
                        items = failures.map { it.first } + skipped,
                        errorMessage = failures.joinToString("; ") { it.second.message ?: it.second.javaClass.simpleName },
                    )
                },
            )
        }
    }

    /** Each accepted item is applied independently: the create/update + movement-linking below is
     * one atomic transaction, but a failure on one item doesn't roll back another — re-running
     * detection matches an already-created template as an UPDATE rather than proposing a
     * duplicate. Re-resolves the split itself (rather than trusting [DetectionReviewItem]'s
     * preview value) so a stale review sheet never applies a split that no longer matches the
     * template/movement it would have read at confirm time. */
    private fun applyDetectionItem(candidate: DetectedRecurringCandidate, now: String) {
        val existing = candidate.matchedTemplateId?.let { templateRepository.getActive(it) }
        val splitConfig = resolveSplitConfigForCandidate(candidate, matchedTemplate = existing)
        val draft = candidate.toTemplateDraft(existing, splitConfig)
        movementRepository.runInTransaction {
            when (candidate.action) {
                DetectedTemplateAction.NEW -> templateRepository.create(draft, createdAt = now)
                DetectedTemplateAction.UPDATE -> templateRepository.update(draft, updatedAt = now)
            }
            // Link the movements that formed this pattern so they stop being re-proposed by
            // future scans and show as instances of the (now tracked) recurring template.
            movementRepository.linkToTemplate(candidate.sourceMovementIds, templateId = draft.id, updatedAt = now)
        }
    }

    /** UPDATE: carry the matched template's split forward unchanged (it's already authoritative
     * for a template that's been shared/edited since). NEW: there's no existing template to carry
     * from, so resolve it from the most recent occurrence that formed this pattern — otherwise a
     * detected shared-recurring expense would silently become a plain personal template. Shared by
     * the review-sheet preview ([onDetectRecurringClicked]) and the actual apply
     * ([applyDetectionItem]) so both agree on what a candidate's split is. */
    private fun resolveSplitConfigForCandidate(
        candidate: DetectedRecurringCandidate,
        matchedTemplate: TemplateSummary?,
    ): TemplateSplitConfig? = when (candidate.action) {
        DetectedTemplateAction.UPDATE -> matchedTemplate?.splitConfig
        DetectedTemplateAction.NEW -> candidate.sourceMovementIds.lastOrNull()
            ?.let { splitRepository.getForMovement(it) }
            ?.let { MovementSplitWrite.Replace(it).toTemplateSplitConfig() }
    }

    private suspend fun undoDelete(operation: RecurringDeleteOperation) {
        val restoredAt = Instant.now().toString()
        val result = withContext(ioDispatcher) {
            runCatching {
                movementRepository.runInTransaction {
                    templateRepository.restore(operation.templateId, operation.deletedAt, restoredAt)
                    movementRepository.restoreTemplateLinksAfterDelete(
                        movementIds = operation.linkedMovementIds,
                        templateId = operation.templateId,
                        deletedAt = operation.deletedAt,
                        restoredAt = restoredAt,
                    )
                }
            }
        }
        result.fold(
            onSuccess = {
                financialDataRevision.markChanged()
                refreshNotifications()
            },
            onFailure = ::showError,
        )
    }

    private fun refreshNotifications() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                runCatching { notificationRefresher.refreshNotifications() }
            }
        }
    }
}

data class RecurringUiState(
    val templates: List<TemplateSummary> = emptyList(),
    val accounts: List<AccountSummary> = emptyList(),
    val categories: List<CategoryRecord> = emptyList(),
    val trips: List<TripSummary> = emptyList(),
    val tags: List<TagSummary> = emptyList(),
    val people: List<PersonSummary> = emptyList(),
    val duePrompts: List<DuePrompt> = emptyList(),
    /** This month's recurring money, from canonical figures; null until loaded. */
    val month: RecurringMonth? = null,
    val today: LocalDate = LocalDate.now(),
    val monthlyPaymentStates: Map<String, TemplateMonthPaymentState> = emptyMap(),
    /** Completed, linked movements per template; used only for recurrence history in the UI. */
    val occurrenceCounts: Map<String, Long> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: TemplateFormState? = null,
    val confirmPrompt: ConfirmPromptState? = null,
    val endCandidate: TemplateSummary? = null,
    val deleteCandidate: TemplateSummary? = null,
    val isDetecting: Boolean = false,
    val detectionReview: DetectionReviewState? = null,
    val historyDetail: RecurringHistoryDetailState? = null,
    val linkPicker: LinkPickerState? = null,
    val bulkLink: BulkLinkState? = null,
) {

    /** True while any of this ViewModel's own dialogs (rendered by `RecurringOverlays`) is open --
     * used to make the auto-triggered due-reminders sheet step aside for them, then reappear. */
    val hasOpenDialog: Boolean get() =
        confirmPrompt != null || endCandidate != null || deleteCandidate != null ||
            form != null || detectionReview != null || linkPicker != null || bulkLink != null
}

/** Review list produced by a "Detecta periòdics" scan — nothing is created/updated
 * until the user confirms; each item is pre-checked and can be unchecked (skipped) individually. */
data class DetectionReviewState(
    val items: List<DetectionReviewItem>,
    val errorMessage: String? = null,
)

data class DetectionReviewItem(
    val candidate: DetectedRecurringCandidate,
    val accepted: Boolean = true,
    /** Read-only: the split this candidate would carry into its template, resolved purely for the
     * review-sheet preview — see [RecurringViewModel.resolveSplitConfigForCandidate]. */
    val splitConfig: TemplateSplitConfig? = null,
)

/**
 * A virtual occurrence of an active template (not yet in the ledger) whose window has opened: it
 * is expected from [TemplateSummary.marginDays] before its date and overdue once as many have
 * passed after it.
 */
data class DuePrompt(
    val template: TemplateSummary,
    val dueDate: String,
    val pendingCount: Int,
)

/** The movements that could be a due occurrence already recorded by hand, to link one as it. */
/** An item's unlinked past payments to pick from, and the ones picked. */
data class BulkLinkState(
    val template: TemplateSummary,
    val candidates: List<MovementSummary> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    /** One payment per expected date not yet covered, the nearest to it: what "mark all" ticks. */
    val onePerOccurrenceIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val allMarked: Boolean get() = onePerOccurrenceIds.isNotEmpty() && selectedIds == onePerOccurrenceIds
}

data class LinkPickerState(
    val prompt: DuePrompt,
    val candidates: List<MovementSummary> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/** Identifies which field a due-payment confirm-prompt validation error belongs to. */
enum class ConfirmPromptField {
    AMOUNT,
    DATE,
}

data class ConfirmPromptState(
    val templateId: String,
    val templateName: String,
    val accountName: String,
    val amount: String = "",
    val date: String = "",
    /** Settlement occurrences show who is being settled with instead of a split preview. */
    val settlementPersonName: String? = null,
    val errorRes: StringResource? = null,
    val errorField: ConfirmPromptField? = null,
    val errorMessage: String? = null,
    /** Read-only: the template's carried-forward split, for a live share preview. Never edited
     * here -- [occurrenceSplit] rescales it to the confirmed amount, for the preview and the save alike. */
    val splitConfig: TemplateSplitConfig? = null,
    /** True while the occurrence is being written; the confirm action is disabled until it finishes. */
    val isSaving: Boolean = false,
    /** A fixed item's amount, which a different confirmed amount may replace. */
    val fixedAmountCents: Long? = null,
    /** Whether confirming also sets the item's amount to this occurrence's. */
    val updateAmount: Boolean = false,
    /** How many occurrences are due, so the sheet offers skipping them all when there are several. */
    val pendingCount: Int = 1,
    /** The occurrence the prompt is for, which skipping names by its month, year or day. */
    val dueDate: String = "",
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    /** An income's occurrence is received ("cobrament"), not paid. */
    val type: MovementType = MovementType.EXPENSE,
) {
    /** True when confirming [amountCents] changes the item's fixed amount too. */
    fun updatesAmountTo(amountCents: Long): Boolean =
        updateAmount && fixedAmountCents != null && amountCents != fixedAmountCents
}

/** Identifies which field a template-form validation error belongs to. */
enum class TemplateFormField {
    AMOUNT,
    ACCOUNT,
    INCOME_OWNER,
    SPLIT,
    DESTINATION_ACCOUNT,
    PERSON,
    SCHEDULE,
    NEXT_DUE_DATE,
    DATE_FLEX,
}

data class TemplateFormState(
    val id: String? = null,
    val type: MovementType = MovementType.EXPENSE,
    val amount: String = "",
    val amountIsVariable: Boolean = false,
    val accountId: String? = null,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val tripId: String? = null,
    val tagId: String? = null,
    val personId: String? = null,
    val settlementDirection: SettlementDirection = SettlementDirection.PERSON_TO_USER,
    val settlementScope: SettlementScope = SettlementScope.ALL,
    val name: String = "",
    val payee: String = "",
    val notes: String = "",
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val dayOfMonth: String = "",
    val weekday: Int? = null,
    val intervalCount: String = "",
    val customUnit: CustomRecurrenceUnit = CustomRecurrenceUnit.MONTHS,
    val nextDueDate: String = "",
    val dateFlex: String = "",
    val status: TemplateStatus = TemplateStatus.ACTIVE,
    /**
     * Who the item is for: [ExpenseKind.PERSONAL] (the owner), [ExpenseKind.SHARED] (split as
     * [splitEditor] says), or [ExpenseKind.FOR_OTHER] ([sharingPersonId], wholly). Any expense can
     * be shared; an income only within its shared account, where it stays null until answered.
     */
    val sharing: ExpenseKind? = null,
    val splitEditor: SplitEditorState? = null,
    val sharingPersonId: String? = null,
    /** True once the question was answered in this form, so the stored split is rebuilt from the
     * answer; otherwise an edit keeps the stored split exactly. */
    val sharingChosen: Boolean = false,
    val showOptional: Boolean = false,
    /** True while it is being written, so a second tap cannot create it twice. */
    val isSaving: Boolean = false,
    val errorRes: StringResource? = null,
    val errorField: TemplateFormField? = null,
    val errorMessage: String? = null,
)

private data class LoadedRecurringData(
    val templates: List<TemplateSummary>,
    val accounts: List<AccountSummary>,
    val categories: List<CategoryRecord>,
    val trips: List<TripSummary>,
    val tags: List<TagSummary>,
    val people: List<PersonSummary>,
    val movements: List<MovementSummary>,
    val occurrenceCounts: Map<String, Long>,
)

private fun List<TemplateSummary>.toDuePrompts(today: LocalDate): List<DuePrompt> =
    filter { it.status == TemplateStatus.ACTIVE }
        .mapNotNull { template ->
            val cursor = parseDate(template.nextDueDate) ?: return@mapNotNull null
            val due = runCatching {
                RecurringAdvancer.advance(template.toRecurrenceRule(), cursor = cursor, today = today.plusDays(template.marginDays))
            }.getOrNull() ?: return@mapNotNull null
            due.dueDates.firstOrNull()?.let { first ->
                DuePrompt(template = template, dueDate = first.toString(), pendingCount = due.dueDates.size)
            }
        }
        .sortedBy { it.dueDate }

enum class TemplateMonthPaymentState {
    NONE,
    PAID,
    PENDING,
    PARTIALLY_PAID,
}

private data class RecurringDeleteOperation(
    val templateId: String,
    val deletedAt: String,
    val linkedMovementIds: List<String>,
)

data class RecurringHistoryDetailState(
    val template: TemplateSummary,
    val movements: List<MovementSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Whether each item's occurrences this month are paid, still pending, or both: linked movements
 * dated this month are paid, occurrences left before the month ends are pending.
 */
private fun List<TemplateSummary>.monthlyPaymentStates(
    today: LocalDate,
    movements: List<MovementSummary>,
): Map<String, TemplateMonthPaymentState> {
    val month = YearMonth.from(today)
    val posted = movements
        .filter { it.templateId != null && runCatching { YearMonth.from(LocalDate.parse(it.date)) }.getOrNull() == month }
        .groupingBy { it.templateId }
        .eachCount()
    return associate { template ->
        val cursor = parseDate(template.nextDueDate)
        val pending = if (template.status != TemplateStatus.ACTIVE || cursor == null) {
            0
        } else {
            runCatching {
                RecurringAdvancer.advance(template.toRecurrenceRule(), cursor = cursor, today = month.atEndOfMonth()).dueDates
            }.getOrDefault(emptyList()).count { YearMonth.from(it) == month }
        }
        val paid = posted[template.id] ?: 0
        template.id to when {
            paid > 0 && pending > 0 -> TemplateMonthPaymentState.PARTIALLY_PAID
            pending > 0 -> TemplateMonthPaymentState.PENDING
            paid > 0 -> TemplateMonthPaymentState.PAID
            else -> TemplateMonthPaymentState.NONE
        }
    }
}

/**
 * The account's default expense split as a template stores it, the same lines the movement form
 * writes for that default. A variable amount keeps the shares as weights over 100 €, which
 * [com.gestorfinances.app.data.repository.occurrenceSplit] rescales to each occurrence.
 */
private fun AccountSummary.defaultExpenseSplitConfig(amountCents: Long?): TemplateSplitConfig? =
    defaultExpenseSplitEditor().toMovementSplitDraft(amountCents ?: VARIABLE_AMOUNT_WEIGHT_CENTS)
        ?.let { MovementSplitWrite.Replace(it).toTemplateSplitConfig() }

/** A variable amount's allocation is entered and stored as shares of 100 EUR, weights that
 * [com.gestorfinances.app.data.repository.occurrenceSplit] rescales to each occurrence. */
const val VARIABLE_AMOUNT_WEIGHT_CENTS = 10_000L

/**
 * The split the answer to whose an income is describes, built as the movement form builds it:
 * Shared from the split editor, another member as owner 0 and that member the whole amount.
 * Null for Mine, and for an answer not yet complete.
 */
private fun TemplateFormState.chosenSplitDraft(amountCents: Long?): MovementSplitDraft? {
    val totalCents = amountCents ?: VARIABLE_AMOUNT_WEIGHT_CENTS
    return when (sharing) {
        ExpenseKind.SHARED -> splitEditor?.toMovementSplitDraft(totalCents)
        ExpenseKind.FOR_OTHER -> sharingPersonId?.let { memberId ->
            MovementSplitDraft(
                entryMethod = SplitEntryMethod.EXACT,
                lines = listOf(
                    SplitLineDraft(SplitParticipantKind.USER, null, 0L),
                    SplitLineDraft(SplitParticipantKind.PERSON, memberId, totalCents),
                ),
            )
        }
        else -> null
    }
}

/**
 * Whose a template's income is, as the ownership question shows it: no stored split is the owner's;
 * an owner line of nothing with one other line is that member's; anything else is shared.
 */
private fun TemplateSummary.sharingForm(): Triple<ExpenseKind, SplitEditorState?, String?> {
    val config = splitConfig ?: return Triple(ExpenseKind.PERSONAL, null, null)
    val others = config.lines.filter { it.party != "user" }
    val ownerWeight = config.lines.filter { it.party == "user" }.sumOf { it.owedAmountCents }
    if (ownerWeight == 0L && others.size == 1) return Triple(ExpenseKind.FOR_OTHER, null, others.single().party)
    val totalCents = amountCents ?: config.lines.sumOf { it.owedAmountCents }
    return Triple(ExpenseKind.SHARED, config.occurrenceSplit(totalCents)?.toSplitEditorState(totalCents), null)
}

/** Cursor after materializing/skipping a single occurrence. */
private fun TemplateSummary.advancedOneStep(): String {
    val cursor = LocalDate.parse(nextDueDate)
    return RecurringAdvancer.nextOccurrence(toRecurrenceRule(), cursor).toString()
}

/** Cursor after skipping the whole backlog up to [today]. */
private fun TemplateSummary.advancedToToday(today: LocalDate): String {
    val cursor = LocalDate.parse(nextDueDate)
    return RecurringAdvancer.advance(toRecurrenceRule(), cursor = cursor, today = today).newCursor.toString()
}

/** A single row of the confirm-sheet split preview: either the user's own share, or a named
 * person's. [personName] is null for an unresolvable person id (e.g. an archived person) so the
 * UI can fall back to a generic label rather than showing a raw id. */
data class SplitPreviewLine(
    val isUser: Boolean,
    val personName: String?,
    val amountCents: Long,
)

/** Live preview of how [amountCents] would be split if confirmed now: the lines of
 * [occurrenceSplit], the very split the confirmed movement is saved with. Empty when there's
 * nothing to preview (no split, or a malformed config the occurrence won't carry). */
fun TemplateSplitConfig?.previewShares(amountCents: Long, people: List<PersonSummary>): List<SplitPreviewLine> {
    val split = this?.occurrenceSplit(amountCents) ?: return emptyList()
    return split.lines.map { line ->
        SplitPreviewLine(
            isUser = line.participantKind == SplitParticipantKind.USER,
            personName = line.personId?.let { id -> people.firstOrNull { it.id == id }?.name },
            amountCents = line.owedAmountCents,
        )
    }
}

fun RecurrenceFrequency.usesDayOfMonth(): Boolean =
    this == RecurrenceFrequency.MONTHLY || this == RecurrenceFrequency.YEARLY

fun RecurrenceFrequency.usesWeekday(): Boolean =
    this == RecurrenceFrequency.WEEKLY || this == RecurrenceFrequency.FORTNIGHTLY

private fun TemplateSummary.toFormState(): TemplateFormState {
    val (sharing, splitEditor, sharingPersonId) = if (type == MovementType.INCOME || type == MovementType.EXPENSE) {
        sharingForm()
    } else {
        Triple(null, null, null)
    }
    return TemplateFormState(
        id = id,
        type = type,
        amount = amountCents?.let(::formatEuroInput).orEmpty(),
        amountIsVariable = amountIsVariable,
        accountId = accountId,
        destinationAccountId = destAccountId,
        categoryId = categoryId,
        tripId = tripId,
        tagId = tagId,
        personId = personId,
        settlementDirection = settlementDirection ?: SettlementDirection.PERSON_TO_USER,
        settlementScope = settlementScope ?: SettlementScope.ALL,
        name = name.orEmpty(),
        payee = payee.orEmpty(),
        notes = notes.orEmpty(),
        frequency = frequency,
        dayOfMonth = dayOfMonth?.toString().orEmpty(),
        weekday = weekday?.toInt(),
        intervalCount = intervalCount?.toString().orEmpty(),
        customUnit = customUnit ?: CustomRecurrenceUnit.MONTHS,
        nextDueDate = nextDueDate,
        dateFlex = dateFlexDays?.toString().orEmpty(),
        status = status,
        sharing = sharing,
        splitEditor = splitEditor,
        sharingPersonId = sharingPersonId,
        showOptional = tripId != null || tagId != null || !payee.isNullOrBlank() || !notes.isNullOrBlank() ||
            (type == MovementType.EXPENSE && splitConfig != null),
    )
}

/** Pattern-detection candidates are scoped to EXPENSE/INCOME — a recurring
 * transfer's identity also depends on its destination account, which this detector doesn't track. */
private fun MovementSummary.toRecurringCandidateMovementOrNull(): RecurringCandidateMovement? {
    val account = accountId ?: return null
    if (type != MovementType.EXPENSE && type != MovementType.INCOME) return null
    return RecurringCandidateMovement(
        movementId = id,
        accountId = account,
        type = type,
        categoryId = categoryId,
        tripId = tripId,
        tagId = tagId,
        name = name,
        payee = payee,
        amountCents = amountCents,
        date = LocalDate.parse(date),
        templateId = templateId,
    )
}

private fun TemplateSummary.toExistingTemplateSignature(): ExistingTemplateSignature =
    ExistingTemplateSignature(
        templateId = id,
        accountId = accountId,
        type = type,
        categoryId = categoryId,
        tripId = tripId,
        tagId = tagId,
        name = name,
        payee = payee,
    )

/**
 * [existing] is the matched template being updated (null for a NEW candidate). The detector only
 * ever derives schedule/amount/status fields — [TemplateRepository.update] is a full-row overwrite
 * (see `updateTemplate` in Templates.sq), so anything it doesn't derive (notes, date flexibility,
 * the lead-notification override) must be carried forward from the existing row or confirming an
 * "Actualitza" candidate would silently wipe it. `intervalCount`/`customUnit` are deliberately NOT
 * carried forward: the detector never proposes CUSTOM frequency, and the templates CHECK constraint
 * requires both to be null whenever frequency isn't CUSTOM.
 *
 * [splitConfig] is resolved by the caller rather than derived here: for UPDATE it's the existing
 * template's own `split_config` (unchanged), and for NEW it's resolved from the most recent source
 * movement's actual split, since there's no existing template to carry it from — see
 * `applyDetectionItem`.
 */
private fun DetectedRecurringCandidate.toTemplateDraft(existing: TemplateSummary?, splitConfig: TemplateSplitConfig?): TemplateDraft =
    TemplateDraft(
        id = matchedTemplateId ?: UUID.randomUUID().toString(),
        type = type,
        amountCents = amountCents,
        accountId = accountId,
        destAccountId = null,
        categoryId = categoryId,
        tripId = existing?.tripId ?: tripId,
        tagId = existing?.tagId ?: tagId,
        name = name,
        payee = payee,
        notes = existing?.notes,
        splitConfig = splitConfig,
        frequency = frequency,
        intervalCount = null,
        customUnit = null,
        dayOfMonth = dayOfMonth?.toLong(),
        weekday = weekday?.toLong(),
        nextDueDate = suggestedNextDueDate.toString(),
        amountIsVariable = amountIsVariable,
        amountFlexCents = amountFlexCents,
        dateFlexDays = existing?.dateFlexDays ?: dateFlexDays?.toLong(),
        leadNotificationDays = existing?.leadNotificationDays,
        status = suggestedStatus,
    )

private fun parseDate(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }
