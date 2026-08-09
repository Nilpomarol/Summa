package com.gestorfinances.app.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSplitDraft
import com.gestorfinances.app.data.repository.MovementSplitWrite
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SplitEntryMethod
import com.gestorfinances.app.data.repository.SplitLineDraft
import com.gestorfinances.app.data.repository.SplitParticipantKind
import com.gestorfinances.app.data.repository.SplitRepository
import com.gestorfinances.app.data.repository.TemplateDraft
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateSplitConfig
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.data.repository.supportsExpense
import com.gestorfinances.app.data.repository.supportsIncome
import com.gestorfinances.app.data.repository.toTemplateSplitConfig
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.DetectedRecurringCandidate
import com.gestorfinances.app.domain.rules.DetectedTemplateAction
import com.gestorfinances.app.domain.rules.ExistingTemplateSignature
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.domain.rules.RecurringAdvancer
import com.gestorfinances.app.domain.rules.RecurringCandidateMovement
import com.gestorfinances.app.domain.rules.RecurringPatternDetector
import com.gestorfinances.app.domain.rules.SplitCalculator
import com.gestorfinances.app.domain.rules.toRecurrenceRule
import com.gestorfinances.app.notifications.NotificationRefresher
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.common.parseEuroCents
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecurringViewModel(
    private val templateRepository: TemplateRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val movementRepository: MovementRepository,
    private val splitRepository: SplitRepository,
    private val personRepository: PersonRepository,
    private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(RecurringUiState())
    val state: StateFlow<RecurringUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refresh()
    }

    fun resetForMenuNavigation() {
        _state.value = RecurringUiState()
        refresh()
    }

    fun onAddClicked() {
        _state.value = _state.value.copy(
            form = TemplateFormState(nextDueDate = today().toString()),
        )
    }

    fun onEditClicked(template: TemplateSummary) {
        _state.value = _state.value.copy(form = template.toFormState())
    }

    fun onFormChanged(form: TemplateFormState) {
        _state.value = _state.value.copy(
            form = form.copy(errorRes = null, errorField = null, errorMessage = null),
        )
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

    fun onConfirmClicked(prompt: DuePrompt) {
        val template = prompt.template
        _state.value = _state.value.copy(
            confirmPrompt = ConfirmPromptState(
                templateId = template.id,
                templateName = template.name ?: "",
                accountName = template.accountName,
                amount = template.amountCents?.let(::formatEuroInput).orEmpty(),
                date = prompt.dueDate,
                splitConfig = template.splitConfig,
            ),
        )
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
        val prompt = _state.value.confirmPrompt ?: return
        val template = _state.value.templates.firstOrNull { it.id == prompt.templateId } ?: return
        val amountCents = parseEuroCents(prompt.amount, allowNegative = false)
        val date = parseDate(prompt.date)
        val (errorRes, errorField) = when {
            amountCents == null -> R.string.movement_validation_amount_required to ConfirmPromptField.AMOUNT
            amountCents <= 0L -> R.string.movement_validation_amount_positive to ConfirmPromptField.AMOUNT
            prompt.date.isBlank() -> R.string.movement_validation_date_required to ConfirmPromptField.DATE
            date == null -> R.string.movement_validation_date_invalid to ConfirmPromptField.DATE
            else -> null to null
        }
        if (errorRes != null) {
            _state.value = _state.value.copy(
                confirmPrompt = prompt.copy(errorRes = errorRes, errorField = errorField),
            )
            return
        }
        val amount = requireNotNull(amountCents)
        val draft = MovementDraft(
            id = UUID.randomUUID().toString(),
            type = template.type,
            amountCents = amount,
            date = requireNotNull(date).toString(),
            accountId = template.accountId,
            destinationAccountId = template.destAccountId,
            categoryId = template.categoryId,
            name = template.name,
            payee = template.payee,
            notes = template.notes,
            isOneTime = false,
            splitWrite = template.splitConfig.toSplitWrite(template.type, amount),
            templateId = template.id,
        )
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    movementRepository.runInTransaction {
                        movementRepository.create(draft, createdAt = now)
                        templateRepository.advanceCursor(template.id, template.advancedOneStep(), updatedAt = now)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(confirmPrompt = null)
                    refresh()
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

    fun onSkipClicked(prompt: DuePrompt) {
        advanceCursorTo(prompt.template.id) { prompt.template.advancedOneStep() }
    }

    fun onSkipAllClicked(prompt: DuePrompt) {
        advanceCursorTo(prompt.template.id) { prompt.template.advancedToToday(today()) }
    }

    // computeNextDueDate runs inside the runCatching/ioDispatcher block below, not on the
    // caller's thread: RecurringAdvancer.advance can throw (F3's occurrence ceiling), and letting
    // that happen on the UI thread would crash instead of surfacing as a benign error.
    private fun advanceCursorTo(templateId: String, computeNextDueDate: () -> String) {
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { templateRepository.advanceCursor(templateId, computeNextDueDate(), updatedAt = now) }
            }
            result.fold(
                onSuccess = {
                    refresh()
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
     * archived template is "treated as absent" (invariant #6), so nothing should still claim a
     * relationship to it — the movements themselves are kept, just as plain non-recurring entries,
     * and become eligible for [RecurringPatternDetector] again. */
    fun onDeleteConfirmed() {
        val template = _state.value.deleteCandidate ?: return
        _state.value = _state.value.copy(deleteCandidate = null)
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    movementRepository.runInTransaction {
                        movementRepository.unlinkAllForTemplate(template.id, updatedAt = now)
                        templateRepository.archive(template.id, archivedAt = now)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    refresh()
                    refreshNotifications()
                },
                onFailure = ::showError,
            )
        }
    }

    fun onSaveClicked() {
        val form = _state.value.form ?: return
        val amountCents = if (form.amountIsVariable) null else parseEuroCents(form.amount, allowNegative = false)
        val nextDue = parseDate(form.nextDueDate)
        val account = form.accountId?.let { id -> _state.value.accounts.firstOrNull { it.id == id } }
        val isTransfer = form.type == MovementType.TRANSFER
        val dayOfMonth = form.dayOfMonth.trim().toLongOrNull()
        val intervalCount = form.intervalCount.trim().toLongOrNull()
        val amountFlexCents = form.amountFlex.trim()
            .takeIf { it.isNotBlank() }
            ?.let { parseEuroCents(it, allowNegative = false) }
        val dateFlexDays = form.dateFlex.trim()
            .takeIf { it.isNotBlank() }
            ?.toLongOrNull()
        val leadNotificationDays = form.leadDays.trim()
            .takeIf { it.isNotBlank() }
            ?.toLongOrNull()

        val (errorRes, errorField) = when {
            !form.amountIsVariable && form.amount.isBlank() ->
                R.string.template_validation_amount_required to TemplateFormField.AMOUNT
            !form.amountIsVariable && amountCents == null ->
                R.string.template_validation_amount_required to TemplateFormField.AMOUNT
            !form.amountIsVariable && amountCents != null && amountCents <= 0L ->
                R.string.movement_validation_amount_positive to TemplateFormField.AMOUNT
            account == null -> R.string.movement_validation_account_required to TemplateFormField.ACCOUNT
            isTransfer && form.destinationAccountId == null ->
                R.string.movement_validation_account_required to TemplateFormField.DESTINATION_ACCOUNT
            isTransfer && form.destinationAccountId == form.accountId ->
                R.string.movement_validation_transfer_same_account to TemplateFormField.DESTINATION_ACCOUNT
            form.frequency.usesDayOfMonth() && (dayOfMonth == null || dayOfMonth !in 1L..31L) ->
                R.string.template_validation_anchor_invalid to TemplateFormField.SCHEDULE
            form.frequency == RecurrenceFrequency.CUSTOM && (intervalCount == null || intervalCount <= 0L) ->
                R.string.template_validation_interval_required to TemplateFormField.SCHEDULE
            form.nextDueDate.isBlank() -> R.string.movement_validation_date_required to TemplateFormField.NEXT_DUE_DATE
            nextDue == null -> R.string.movement_validation_date_invalid to TemplateFormField.NEXT_DUE_DATE
            form.amountFlex.isNotBlank() && amountFlexCents == null ->
                R.string.template_validation_amount_flex_invalid to TemplateFormField.AMOUNT_FLEX
            form.dateFlex.isNotBlank() && (dateFlexDays == null || dateFlexDays < 0L) ->
                R.string.template_validation_date_flex_invalid to TemplateFormField.DATE_FLEX
            form.leadDays.isNotBlank() && (leadNotificationDays == null || leadNotificationDays < 0L) ->
                R.string.notification_validation_lead_days to TemplateFormField.LEAD_DAYS
            else -> null to null
        }
        if (errorRes != null) {
            _state.value = _state.value.copy(form = form.copy(errorRes = errorRes, errorField = errorField))
            return
        }

        // The manual edit form has no split_config field of its own (splits are configured via the
        // pattern-detection flow or seeded directly), so on edit it must carry the existing
        // template's splitConfig forward unchanged — otherwise TemplateRepository.update's
        // full-row overwrite would silently wipe it (see toTemplateDraft(existing) below, same fix
        // already applied to the detection-confirm path).
        val existingSplitConfig = form.id?.let { id -> _state.value.templates.firstOrNull { it.id == id }?.splitConfig }

        val draft = TemplateDraft(
            id = form.id ?: UUID.randomUUID().toString(),
            type = form.type,
            amountCents = amountCents,
            accountId = requireNotNull(account).id,
            destAccountId = if (isTransfer) form.destinationAccountId else null,
            categoryId = if (isTransfer) null else form.categoryId,
            name = form.name.trim().ifBlank { null },
            payee = form.payee.trim().ifBlank { null },
            notes = form.notes.trim().ifBlank { null },
            splitConfig = existingSplitConfig,
            frequency = form.frequency,
            intervalCount = if (form.frequency == RecurrenceFrequency.CUSTOM) intervalCount else null,
            customUnit = if (form.frequency == RecurrenceFrequency.CUSTOM) form.customUnit else null,
            dayOfMonth = if (form.frequency.usesDayOfMonth()) dayOfMonth else null,
            weekday = if (form.frequency.usesWeekday()) form.weekday?.toLong() else null,
            nextDueDate = requireNotNull(nextDue).toString(),
            amountIsVariable = form.amountIsVariable,
            amountFlexCents = amountFlexCents,
            dateFlexDays = dateFlexDays,
            leadNotificationDays = leadNotificationDays,
            status = form.status,
        )

        val now = Instant.now().toString()
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
                    refresh()
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
                    refresh()
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
                        people = personRepository.listActive(),
                        movements = movementRepository.listActive(),
                        occurrenceCounts = movementRepository.countsByTemplate(),
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    val calendar = it.templates.monthlyCalendar(today(), it.movements)
                    _state.value.copy(
                        templates = it.templates,
                        accounts = it.accounts,
                        categories = it.categories,
                        people = it.people,
                        duePrompts = it.templates.toDuePrompts(today()),
                        monthlyExpenseCents = calendar.scheduledExpenseCents,
                        monthlyIncomeCents = calendar.scheduledIncomeCents,
                        monthlyPaidCents = calendar.paidCents,
                        monthlyRemainingCents = calendar.remainingCents,
                        monthlyPaidExpenseCents = calendar.paidExpenseCents,
                        monthlyPaidIncomeCents = calendar.paidIncomeCents,
                        monthlyRemainingExpenseCents = calendar.remainingExpenseCents,
                        monthlyRemainingIncomeCents = calendar.remainingIncomeCents,
                        monthlyPaymentStates = calendar.paymentStates,
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
        }
    }

    /** User-triggered, one-shot scan (product rule) — never automatic/background. The detector
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
            refresh()
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

    private fun refreshNotifications() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                runCatching { notificationRefresher.refreshNotifications() }
            }
        }
    }

    class Factory(
        private val templateRepository: TemplateRepository,
        private val accountRepository: AccountRepository,
        private val categoryRepository: CategoryRepository,
        private val movementRepository: MovementRepository,
        private val splitRepository: SplitRepository,
        private val personRepository: PersonRepository,
        private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RecurringViewModel::class.java)) {
                return RecurringViewModel(
                    templateRepository = templateRepository,
                    accountRepository = accountRepository,
                    categoryRepository = categoryRepository,
                    movementRepository = movementRepository,
                    splitRepository = splitRepository,
                    personRepository = personRepository,
                    notificationRefresher = notificationRefresher,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class RecurringUiState(
    val templates: List<TemplateSummary> = emptyList(),
    val accounts: List<AccountSummary> = emptyList(),
    val categories: List<CategoryRecord> = emptyList(),
    val people: List<PersonSummary> = emptyList(),
    val duePrompts: List<DuePrompt> = emptyList(),
    val monthlyExpenseCents: Long = 0L,
    val monthlyIncomeCents: Long = 0L,
    /** Amount already materialised as recurring movements in the current calendar month. */
    val monthlyPaidCents: Long = 0L,
    /** Fixed recurring amount still scheduled for this calendar month. */
    val monthlyRemainingCents: Long = 0L,
    val monthlyPaidExpenseCents: Long = 0L,
    val monthlyPaidIncomeCents: Long = 0L,
    val monthlyRemainingExpenseCents: Long = 0L,
    val monthlyRemainingIncomeCents: Long = 0L,
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
) {
    val monthlyNetCents: Long get() = monthlyIncomeCents - monthlyExpenseCents
    val hasMonthlySummary: Boolean get() = templates.isNotEmpty()

    /** True while any of this ViewModel's own dialogs (rendered by `RecurringOverlays`) is open --
     * used to make the auto-triggered due-reminders sheet step aside for them, then reappear. */
    val hasOpenDialog: Boolean get() =
        confirmPrompt != null || endCandidate != null || deleteCandidate != null ||
            form != null || detectionReview != null
}

/** Review list produced by a "Detecta periòdics" scan (product rule) — nothing is created/updated
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

/** A virtual occurrence due for an active template (not yet in the ledger). */
data class DuePrompt(
    val template: TemplateSummary,
    val dueDate: String,
    val pendingCount: Int,
)

/** Identifies which field a due-payment confirm-prompt validation error belongs to (field-level validation). */
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
    val errorRes: Int? = null,
    val errorField: ConfirmPromptField? = null,
    val errorMessage: String? = null,
    /** Read-only: the template's carried-forward split, for a live share preview. Never edited
     * here -- see [toSplitWrite] for how it's rescaled to the confirmed amount at save time. */
    val splitConfig: TemplateSplitConfig? = null,
)

/** Identifies which field a template-form validation error belongs to (field-level validation). */
enum class TemplateFormField {
    AMOUNT,
    ACCOUNT,
    DESTINATION_ACCOUNT,
    SCHEDULE,
    NEXT_DUE_DATE,
    AMOUNT_FLEX,
    DATE_FLEX,
    LEAD_DAYS,
}

data class TemplateFormState(
    val id: String? = null,
    val type: MovementType = MovementType.EXPENSE,
    val amount: String = "",
    val amountIsVariable: Boolean = false,
    val accountId: String? = null,
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val name: String = "",
    val payee: String = "",
    val notes: String = "",
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val dayOfMonth: String = "",
    val weekday: Int? = null,
    val intervalCount: String = "",
    val customUnit: CustomRecurrenceUnit = CustomRecurrenceUnit.MONTHS,
    val nextDueDate: String = "",
    val amountFlex: String = "",
    val dateFlex: String = "",
    val leadDays: String = "",
    val status: TemplateStatus = TemplateStatus.ACTIVE,
    val errorRes: Int? = null,
    val errorField: TemplateFormField? = null,
    val errorMessage: String? = null,
)

private data class LoadedRecurringData(
    val templates: List<TemplateSummary>,
    val accounts: List<AccountSummary>,
    val categories: List<CategoryRecord>,
    val people: List<PersonSummary>,
    val movements: List<MovementSummary>,
    val occurrenceCounts: Map<String, Long>,
)

private fun List<TemplateSummary>.toDuePrompts(today: LocalDate): List<DuePrompt> =
    filter { it.status == TemplateStatus.ACTIVE }
        .mapNotNull { template ->
            val cursor = parseDate(template.nextDueDate) ?: return@mapNotNull null
            val due = runCatching {
                RecurringAdvancer.advance(template.toRecurrenceRule(), cursor = cursor, today = today)
            }.getOrNull() ?: return@mapNotNull null
            due.dueDates.firstOrNull()?.let { first ->
                DuePrompt(template = template, dueDate = first.toString(), pendingCount = due.dueDates.size)
            }
        }
        .sortedBy { it.dueDate }

/**
 * This month's recurring load from active, fixed-amount templates: occurrences whose date falls
 * in the current calendar month × amount, summed by type. Variable-amount templates and transfers
 * are excluded (unknown amount / neither income nor expense). Returns (expense, income) magnitudes.
 */
private data class MonthlyRecurringCalendar(
    val scheduledExpenseCents: Long,
    val scheduledIncomeCents: Long,
    val paidCents: Long,
    val remainingCents: Long,
    val paidExpenseCents: Long,
    val paidIncomeCents: Long,
    val remainingExpenseCents: Long,
    val remainingIncomeCents: Long,
    val paymentStates: Map<String, TemplateMonthPaymentState>,
)

enum class TemplateMonthPaymentState {
    NONE,
    PAID,
    PENDING,
    PARTIALLY_PAID,
}

/**
 * Display-only current-month calendar. Posted linked movements are "paid"; the schedule cursor
 * supplies the remaining fixed occurrences. Their sum is the amount represented on this page,
 * without changing any finance or recurrence rule.
 */
private fun List<TemplateSummary>.monthlyCalendar(
    today: LocalDate,
    movements: List<MovementSummary>,
): MonthlyRecurringCalendar {
    val month = YearMonth.from(today)
    val monthEnd = month.atEndOfMonth()
    var expense = 0L
    var income = 0L
    var paidExpense = 0L
    var paidIncome = 0L
    val postedOccurrences = mutableMapOf<String, Int>()
    movements
        .filter { it.templateId != null && runCatching { YearMonth.from(LocalDate.parse(it.date)) }.getOrNull() == month }
        .forEach { movement ->
            when (movement.type) {
                MovementType.EXPENSE -> {
                    paidExpense += movement.amountCents
                    movement.templateId?.let { id -> postedOccurrences[id] = (postedOccurrences[id] ?: 0) + 1 }
                }
                MovementType.INCOME -> {
                    paidIncome += movement.amountCents
                    movement.templateId?.let { id -> postedOccurrences[id] = (postedOccurrences[id] ?: 0) + 1 }
                }
                else -> Unit
            }
        }
    val paid = paidExpense + paidIncome
    var remaining = 0L
    val pendingOccurrences = mutableMapOf<String, Int>()
    forEach { template ->
        if (template.status != TemplateStatus.ACTIVE) return@forEach
        val cursor = parseDate(template.nextDueDate) ?: return@forEach
        val occurrences = runCatching {
            RecurringAdvancer.advance(template.toRecurrenceRule(), cursor = cursor, today = monthEnd).dueDates
        }.getOrNull() ?: return@forEach
        val count = occurrences.count { YearMonth.from(it) == month }
        pendingOccurrences[template.id] = count
        val amount = template.amountCents
        if (template.amountIsVariable || amount == null) return@forEach
        when (template.type) {
            MovementType.EXPENSE -> {
                expense += amount * count
                remaining += amount * count
            }
            MovementType.INCOME -> {
                income += amount * count
                remaining += amount * count
            }
            else -> Unit
        }
    }
    return MonthlyRecurringCalendar(
        scheduledExpenseCents = expense,
        scheduledIncomeCents = income,
        paidCents = paid,
        remainingCents = remaining,
        paidExpenseCents = paidExpense,
        paidIncomeCents = paidIncome,
        remainingExpenseCents = expense,
        remainingIncomeCents = income,
        paymentStates = associate { template ->
            val posted = postedOccurrences[template.id] ?: 0
            val pending = pendingOccurrences[template.id] ?: 0
            template.id to when {
                posted > 0 && pending > 0 -> TemplateMonthPaymentState.PARTIALLY_PAID
                pending > 0 -> TemplateMonthPaymentState.PENDING
                posted > 0 -> TemplateMonthPaymentState.PAID
                else -> TemplateMonthPaymentState.NONE
            }
        },
    )
}

/** Day a scheduled template lands on, for day-ordered listing. */
fun TemplateSummary.effectiveDayOfMonth(): Int =
    dayOfMonth?.toInt() ?: runCatching { LocalDate.parse(nextDueDate).dayOfMonth }.getOrDefault(99)

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

/**
 * Carry the template's split forward, rescaling its line weights to [amountCents] when the
 * confirmed occurrence amount differs from the config's own stored sum (variable-amount
 * templates, an amount edited since the split was set, or a NEW-detected candidate whose split
 * came from a single source movement while its amount is a group median) — see
 * [SplitCalculator.rescale] / `shared/golden/template_split_rescale.json`. Falls back to
 * [MovementSplitWrite.KeepExisting] (no split applied) only when the config itself is malformed.
 */
private fun TemplateSplitConfig?.toSplitWrite(
    type: MovementType,
    amountCents: Long,
): MovementSplitWrite {
    val config = this ?: return MovementSplitWrite.KeepExisting
    if (type != MovementType.EXPENSE) return MovementSplitWrite.KeepExisting
    val entryMethod = SplitEntryMethod.entries.firstOrNull { it.dbValue == config.entryMethod }
        ?: return MovementSplitWrite.KeepExisting
    val shares = config.rescaledShares(amountCents) ?: return MovementSplitWrite.KeepExisting
    val lines = config.lines.zip(shares).map { (line, share) ->
        if (line.party == "user") {
            SplitLineDraft(SplitParticipantKind.USER, personId = null, owedAmountCents = share)
        } else {
            SplitLineDraft(SplitParticipantKind.PERSON, personId = line.party, owedAmountCents = share)
        }
    }
    return MovementSplitWrite.Replace(MovementSplitDraft(entryMethod = entryMethod, lines = lines))
}

/** Each line's [TemplateSplitConfigLine.owedAmountCents] rescaled to [amountCents], in the same
 * order as [TemplateSplitConfig.lines] — see [SplitCalculator.rescale]. Null iff the config's
 * `payer` doesn't match any line (malformed config). Shared by [toSplitWrite] (the actual write)
 * and [previewShares] (the confirm-sheet preview), so what the user sees is exactly what gets
 * saved. */
private fun TemplateSplitConfig.rescaledShares(amountCents: Long): List<Long>? {
    val payerIndex = lines.indexOfFirst { it.party == payer }
    if (payerIndex < 0) return null
    val rescaled = SplitCalculator.rescale(
        weightsCents = lines.map { it.owedAmountCents },
        totalCents = amountCents,
        payerIndex = payerIndex,
    )
    return rescaled.sharesCents.takeIf { rescaled.valid }
}

/** The user's own share of [amountCents] under this split — the figure a template row/due-prompt
 * card should show as the primary amount (with [amountCents] itself as the secondary "total"),
 * matching how [com.gestorfinances.app.ui.common.MovementListItem] displays a shared movement.
 * Null iff the config is malformed (falls back to showing the plain total). */
fun TemplateSplitConfig.userShareCents(amountCents: Long): Long? {
    val shares = rescaledShares(amountCents) ?: return null
    val userIndex = lines.indexOfFirst { it.party == "user" }
    return userIndex.takeIf { it >= 0 }?.let { shares[it] }
}

/** A single row of the confirm-sheet split preview: either the user's own share, or a named
 * person's. [personName] is null for an unresolvable person id (e.g. an archived person) so the
 * UI can fall back to a generic label rather than showing a raw id. */
data class SplitPreviewLine(
    val isUser: Boolean,
    val personName: String?,
    val amountCents: Long,
)

/** Live preview of how [amountCents] would be split if confirmed now, using the same rescale rule
 * [toSplitWrite] applies at save time. Empty when there's nothing to preview (no split, or a
 * malformed config that will fall back to [MovementSplitWrite.KeepExisting]). */
fun TemplateSplitConfig?.previewShares(amountCents: Long, people: List<PersonSummary>): List<SplitPreviewLine> {
    val config = this ?: return emptyList()
    val shares = config.rescaledShares(amountCents) ?: return emptyList()
    return config.lines.zip(shares).map { (line, share) ->
        if (line.party == "user") {
            SplitPreviewLine(isUser = true, personName = null, amountCents = share)
        } else {
            SplitPreviewLine(isUser = false, personName = people.firstOrNull { it.id == line.party }?.name, amountCents = share)
        }
    }
}

fun RecurrenceFrequency.usesDayOfMonth(): Boolean =
    this == RecurrenceFrequency.MONTHLY || this == RecurrenceFrequency.YEARLY

fun RecurrenceFrequency.usesWeekday(): Boolean =
    this == RecurrenceFrequency.WEEKLY || this == RecurrenceFrequency.FORTNIGHTLY

private fun TemplateSummary.toFormState(): TemplateFormState =
    TemplateFormState(
        id = id,
        type = type,
        amount = amountCents?.let(::formatEuroInput).orEmpty(),
        amountIsVariable = amountIsVariable,
        accountId = accountId,
        destinationAccountId = destAccountId,
        categoryId = categoryId,
        name = name.orEmpty(),
        payee = payee.orEmpty(),
        notes = notes.orEmpty(),
        frequency = frequency,
        dayOfMonth = dayOfMonth?.toString().orEmpty(),
        weekday = weekday?.toInt(),
        intervalCount = intervalCount?.toString().orEmpty(),
        customUnit = customUnit ?: CustomRecurrenceUnit.MONTHS,
        nextDueDate = nextDueDate,
        amountFlex = amountFlexCents?.let(::formatEuroInput).orEmpty(),
        dateFlex = dateFlexDays?.toString().orEmpty(),
        leadDays = leadNotificationDays?.toString().orEmpty(),
        status = status,
    )

/** Pattern-detection candidates are scoped to EXPENSE/INCOME (product rule) — a recurring
 * transfer's identity also depends on its destination account, which this detector doesn't track. */
private fun MovementSummary.toRecurringCandidateMovementOrNull(): RecurringCandidateMovement? {
    val account = accountId ?: return null
    if (type != MovementType.EXPENSE && type != MovementType.INCOME) return null
    return RecurringCandidateMovement(
        movementId = id,
        accountId = account,
        type = type,
        categoryId = categoryId,
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
        dateFlexDays = existing?.dateFlexDays,
        leadNotificationDays = existing?.leadNotificationDays,
        status = suggestedStatus,
    )

private fun parseDate(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }
