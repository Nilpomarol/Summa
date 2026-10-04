package com.gestorfinances.app.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.movement_filter_date_invalid
import com.gestorfinances.ui.resources.movement_filter_date_order_invalid
import com.gestorfinances.ui.resources.movement_validation_account_required
import com.gestorfinances.ui.resources.movement_validation_amount_positive
import com.gestorfinances.ui.resources.movement_validation_date_invalid
import com.gestorfinances.ui.resources.movement_validation_date_required
import com.gestorfinances.ui.resources.refund_validation_actual_over
import com.gestorfinances.ui.resources.refund_validation_amount_required
import org.jetbrains.compose.resources.StringResource
import com.gestorfinances.app.data.FinancialDataRevision
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryDraft
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementSplitDraft
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.RefundDraft
import com.gestorfinances.app.data.repository.RefundSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SplitRepository
import com.gestorfinances.app.data.repository.TagRepository
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TripRepository
import com.gestorfinances.app.data.repository.TripSummary
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
import kotlinx.coroutines.flow.drop
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
    private val financialDataRevision: FinancialDataRevision = FinancialDataRevision(),
) : ViewModel() {
    private val _state = MutableStateFlow(MovementsUiState())
    val state: StateFlow<MovementsUiState> = _state.asStateFlow()

    /** Set while a refund is being written, so a repeated tap cannot record it twice. */
    private var refundSaveInFlight = false

    /** The create/edit form. The ledger opens it and refreshes after it saves. */
    val editor = MovementEditor(
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        movementRepository = movementRepository,
        personRepository = personRepository,
        splitRepository = splitRepository,
        templateRepository = templateRepository,
        references = { _state.value },
        onSaved = {
            financialDataRevision.markChanged()
            refreshNotifications()
        },
        onPeopleChanged = { people ->
            _state.value = _state.value.copy(people = people)
            // The person exists now, whether or not this movement is ever saved.
            financialDataRevision.markChanged()
        },
    )

    init {
        // Any committed financial write, this view model's own or another overlay's, reloads the ledger.
        viewModelScope.launch {
            financialDataRevision.value.drop(1).collect { refresh() }
        }
    }

    fun onScreenShown() {
        refresh()
    }

    /** Creates a top-level category for the open form's type and selects it there. */
    fun onCreateCategory(name: String, iconKey: String?, colorHex: String) {
        val type = editor.form.value?.type ?: return
        val kind = if (type == MovementType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val result = withContext(ioDispatcher) {
                runCatching {
                    categoryRepository.create(
                        CategoryDraft(
                            id = id,
                            name = name,
                            kind = kind,
                            nature = CategoryNature.VARIABLE,
                            parentId = null,
                            icon = iconKey,
                            color = colorHex,
                            displayOrder = (_state.value.categories.maxOfOrNull { it.displayOrder } ?: -1L) + 1L,
                        ),
                        createdAt = Instant.now().toString(),
                    )
                    categoryRepository.listActive()
                }
            }
            result.fold(
                onSuccess = { categories ->
                    _state.value = _state.value.copy(categories = categories)
                    editor.form.value?.let { editor.onFormChanged(it.copy(categoryId = id)) }
                    // The category exists now, whether or not this movement is ever saved.
                    financialDataRevision.markChanged()
                },
                onFailure = { editor.showFailure(it) },
            )
        }
    }

    /**
     * Starts a fresh ledger visit from global navigation. Contextual navigation deliberately uses
     * [onDrillDown] instead, so Back can reveal the ledger exactly as the person left it.
     */
    fun resetForMenuNavigation() {
        _state.value = _state.value.copy(
            filters = MovementFilters(),
            detailMovement = null,
            detailRefunds = emptyList(),
            detailSplit = null,
            refundForm = null,
            archiveCandidate = null,
            errorMessage = null,
        )
        editor.onFormDismissed()
        refresh()
    }

    fun onAddClicked() {
        onAddClicked(tripId = null)
    }

    /** [onNoAccounts] runs instead of opening a form when there is no account to record it in. */
    fun onAddClicked(
        tripId: String?,
        debtPayerPersonId: String? = null,
        accountId: String? = null,
        categoryId: String? = null,
        tagId: String? = null,
        onNoAccounts: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = loadMovementData()
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        movements = it.movements,
                        accounts = it.accounts,
                        categories = it.categories,
                        people = it.people,
                        trips = it.trips,
                        tags = it.tags,
                        isLoading = false,
                        errorMessage = null,
                    )
                    if (it.accounts.isEmpty() && debtPayerPersonId == null) {
                        onNoAccounts()
                        editor.onFormDismissed()
                    } else {
                        editor.open(
                            newMovementForm(
                                it.accounts, it.trips, tripId, debtPayerPersonId, accountId, it.categories, categoryId,
                                presetTag = tagId?.let { id -> it.tags.firstOrNull { tag -> tag.id == id } },
                            ),
                        )
                    }
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
        if (movement.type == MovementType.SETTLEMENT || movement.type == MovementType.REFUND || movement.type == MovementType.CONTRIBUTION) return
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val split = when {
                        movement.isShared || movement.paidByPerson -> splitRepository?.getForMovement(movement.id)
                        else -> null
                    }
                    // Load the real template so the form can show the actual linked
                    // frequency/status instead of silently defaulting or ignoring it.
                    val linkedTemplate = movement.templateId?.let { templateRepository?.getActive(it) }
                    val hasActiveRefunds = movement.type == MovementType.EXPENSE &&
                        movementRepository.refundsForExpense(movement.id).isNotEmpty()
                    movement.toFormState(split, linkedTemplate).copy(hasActiveRefunds = hasActiveRefunds)
                }
            }
            result.fold(
                onSuccess = { form ->
                    // Keep the detail data intact so dismissing the edit form can restore it.
                    editor.open(form)
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
        viewModelScope.launch {
            val revertibleTemplate = withContext(ioDispatcher) {
                movement.templateId?.let { templateId ->
                    templateRepository?.getActive(templateId)?.takeIf { template ->
                        template.status == TemplateStatus.ACTIVE &&
                            RecurringAdvancer.isImmediatePriorOccurrence(
                                template.toRecurrenceRule(),
                                LocalDate.parse(movement.date),
                                LocalDate.parse(template.nextDueDate),
                            )
                    }
                }
            }
            val activeRefundCount = if (movement.type == MovementType.EXPENSE) {
                withContext(ioDispatcher) { movementRepository.refundsForExpense(movement.id).size }
            } else {
                0
            }
            _state.value = _state.value.copy(
                archiveCandidate = ArchiveCandidate(
                    movement = movement,
                    revertibleTemplateId = revertibleTemplate?.id,
                    revertibleTemplateNextDueDate = revertibleTemplate?.nextDueDate,
                    activeRefundCount = activeRefundCount,
                ),
            )
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
        if (movement.isShared || movement.paidByPerson) {
            loadDetailSplit(movement)
        }
    }

    /** [onLoaded] runs only once the movement is found, so a caller never opens an empty sheet. */
    fun onDetailSourceClicked(sourceId: String, onLoaded: () -> Unit = {}) {
        viewModelScope.launch {
            val movement = withContext(ioDispatcher) {
                runCatching { movementRepository.getActive(sourceId) }.getOrNull()
            } ?: return@launch

            onDetailClicked(movement)
            onLoaded()
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
                    splitRepository?.getForMovement(movement.id)
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
                date = LocalDate.now().toString(),
            ),
        )
    }

    /** Opens a recorded refund in the same form, to correct or delete it. */
    fun onRefundClicked(expense: MovementSummary, refund: RefundSummary) {
        val refundedByOthers = _state.value.detailRefunds.sumOf { it.amountCents } - refund.amountCents
        _state.value = _state.value.copy(
            refundForm = RefundFormState(
                refundId = refund.id,
                expenseId = expense.id,
                expenseName = expense.name ?: expense.payee ?: expense.categoryName.orEmpty(),
                expenseIsShared = expense.isShared,
                remainingCents = (expense.amountCents - refundedByOthers).coerceAtLeast(0L),
                amount = formatEuroInput(refund.amountCents),
                actualAmount = refund.actualRefundCents?.let(::formatEuroInput).orEmpty(),
                accountId = refund.accountId,
                date = refund.date,
                notes = refund.notes.orEmpty(),
            ),
        )
    }

    /** Deleting a refund goes through the same confirmation and undo as any movement. */
    fun onRefundDeleteClicked() {
        val refundId = _state.value.refundForm?.refundId ?: return
        viewModelScope.launch {
            val refund = withContext(ioDispatcher) {
                runCatching { movementRepository.getActive(refundId) }.getOrNull()
            } ?: return@launch
            onArchiveClicked(refund)
        }
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
        if (refundSaveInFlight) return
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
            amount == null -> Res.string.refund_validation_amount_required to RefundFormField.AMOUNT
            amount <= 0L -> Res.string.movement_validation_amount_positive to RefundFormField.AMOUNT
            form.accountId == null || form.accountId !in activeAccountIds ->
                Res.string.movement_validation_account_required to RefundFormField.ACCOUNT
            form.date.isBlank() -> Res.string.movement_validation_date_required to RefundFormField.DATE
            date == null -> Res.string.movement_validation_date_invalid to RefundFormField.DATE
            actual != null && actual > amount -> Res.string.refund_validation_actual_over to RefundFormField.ACTUAL_AMOUNT
            else -> null to null
        }
        if (errorRes != null) {
            _state.value = _state.value.copy(refundForm = form.copy(errorRes = errorRes, errorField = errorField))
            return
        }

        val now = Instant.now().toString()
        val draft = RefundDraft(
            id = form.refundId ?: UUID.randomUUID().toString(),
            refundsExpenseId = form.expenseId,
            amountCents = requireNotNull(amount),
            accountId = requireNotNull(form.accountId),
            date = requireNotNull(date).toString(),
            name = null,
            payee = null,
            notes = form.notes.nullIfBlank(),
            actualRefundCents = actual,
        )
        refundSaveInFlight = true
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    if (form.refundId == null) {
                        movementRepository.createRefund(draft, createdAt = now)
                    } else {
                        movementRepository.updateRefund(draft, updatedAt = now)
                    }
                }
            }
            refundSaveInFlight = false
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(refundForm = null)
                    // The detail it was made from lists it, and the next one is measured against it.
                    loadDetailRefunds(form.expenseId)
                    financialDataRevision.markChanged()
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
        )
        editor.onFormDismissed()
        refresh()
    }

    fun onClearFiltersClicked() {
        _state.value = _state.value.copy(filters = _state.value.filters.cleared())
    }

    /**
     * Deletes several movements at once, each as recoverable as one deleted alone. Nothing about a
     * recurring series is rewound: that choice belongs to deleting its one occurrence.
     */
    fun onArchiveMany(movements: List<MovementSummary>) {
        if (movements.isEmpty()) return
        val now = Instant.now().toString()
        writeMany {
            movementRepository.runInTransaction {
                movements.forEach { movement ->
                    if (movement.type == MovementType.CONTRIBUTION) {
                        accountRepository.archiveContribution(movement.id, archivedAt = now)
                    } else {
                        movementRepository.archive(movement.id, archivedAt = now)
                    }
                }
            }
        }
    }

    /** Files several movements under one category; they must all be of the [type] the category supports. */
    fun onCategoryChangedForMany(movements: List<MovementSummary>, type: MovementType, categoryId: String?) {
        val ids = movements.filter { it.type == type }.map { it.id }
        if (ids.isEmpty()) return
        val now = Instant.now().toString()
        writeMany { movementRepository.setCategory(ids, type, categoryId, updatedAt = now) }
    }

    private fun writeMany(write: () -> Unit) {
        viewModelScope.launch {
            withContext(ioDispatcher) { runCatching(write) }.fold(
                onSuccess = {
                    financialDataRevision.markChanged()
                    refreshNotifications()
                },
                onFailure = { _state.value = _state.value.copy(errorMessage = it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    fun onArchiveDismissed() {
        _state.value = _state.value.copy(archiveCandidate = null)
    }

    fun onArchiveConfirmed(
        revertDueDate: Boolean = false,
        onSuccess: (undo: suspend () -> Unit) -> Unit = {},
    ) {
        val candidate = _state.value.archiveCandidate ?: return
        val movement = candidate.movement
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    movementRepository.runInTransaction {
                        if (movement.type == MovementType.CONTRIBUTION) {
                            accountRepository.archiveContribution(movement.id, archivedAt = now)
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
                    MovementDeleteOperation(
                        movementId = movement.id,
                        movementType = movement.type,
                        deletedAt = now,
                        revertedTemplateId = candidate.revertibleTemplateId.takeIf { revertDueDate },
                        revertedTemplateDueDate = movement.date.takeIf { revertDueDate },
                        originalTemplateNextDueDate = candidate.revertibleTemplateNextDueDate.takeIf { revertDueDate },
                    )
                }
            }
            result.fold(
                onSuccess = { operation ->
                    _state.value = _state.value.copy(archiveCandidate = null, detailMovement = null, refundForm = null)
                    financialDataRevision.markChanged()
                    refreshNotifications()
                    onSuccess { undoDelete(operation) }
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


    private suspend fun undoDelete(operation: MovementDeleteOperation) {
        val restoredAt = Instant.now().toString()
        val result = withContext(ioDispatcher) {
            runCatching {
                movementRepository.runInTransaction {
                    if (operation.movementType == MovementType.CONTRIBUTION) {
                        accountRepository.restoreContribution(operation.movementId, operation.deletedAt, restoredAt)
                    } else {
                        movementRepository.restore(operation.movementId, operation.deletedAt, restoredAt)
                    }
                    val templateId = operation.revertedTemplateId
                    val deletedDueDate = operation.revertedTemplateDueDate
                    val nextDueDate = operation.originalTemplateNextDueDate
                    if (templateId != null && deletedDueDate != null && nextDueDate != null) {
                        requireNotNull(templateRepository) { "template repository unavailable" }
                            .restoreCursorAfterDelete(
                                id = templateId,
                                deletedDueDate = deletedDueDate,
                                nextDueDate = nextDueDate,
                                deletedAt = operation.deletedAt,
                                restoredAt = restoredAt,
                            )
                    }
                }
            }
        }
        result.fold(
            onSuccess = {
                financialDataRevision.markChanged()
                refreshNotifications()
            },
            onFailure = { _state.value = _state.value.copy(errorMessage = it.message ?: it.javaClass.simpleName) },
        )
    }


    private fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val result = loadMovementData()
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
    val detailMovement: MovementSummary? = null,
    val detailRefunds: List<RefundSummary> = emptyList(),
    val detailSplit: MovementSplitDraft? = null,
    val refundForm: RefundFormState? = null,
    val archiveCandidate: ArchiveCandidate? = null,
) {
    val visibleMovements: List<MovementSummary>
        // A refund shows on its expense, except where the money itself is the subject: an
        // account's movements, or refunds asked for by type.
        get() = movements.filter {
            filters.matches(it) && (it.type != MovementType.REFUND || filters.accountId != null || filters.type == MovementType.REFUND)
        }

    /** True while the movement detail, or the archive/refund state nested inside it, is open --
     * used with [MovementEditor.form] to keep other app-level auto-triggered overlays (e.g. the
     * due-reminders sheet) from appearing on top of one of these. */
    val hasOpenDialog: Boolean get() =
        detailMovement != null || refundForm != null || archiveCandidate != null
}

data class ArchiveCandidate(
    val movement: MovementSummary,
    /** Non-null iff [movement] is provably the template's immediate prior occurrence -- offers
     * the "mark it as due again" choice in the archive-confirmation dialog. */
    val revertibleTemplateId: String? = null,
    val revertibleTemplateNextDueDate: String? = null,
    val activeRefundCount: Int = 0,
)

private data class MovementDeleteOperation(
    val movementId: String,
    val movementType: MovementType,
    val deletedAt: String,
    val revertedTemplateId: String?,
    val revertedTemplateDueDate: String?,
    val originalTemplateNextDueDate: String?,
)

data class MovementFilters(
    val query: String = "",
    val type: MovementType? = null,
    /** Contextual narrowing to expenses a person paid. */
    val paidByPersonOnly: Boolean = false,
    val accountId: String? = null,
    val categoryId: String? = null,
    /** A drill-down from a rolled-up figure: a category together with its subcategories. */
    val categoryIds: Set<String>? = null,
    val tripId: String? = null,
    val tagId: String? = null,
    val uncategorizedOnly: Boolean = false,
    val sourceMode: MovementSourceMode? = null,
    val oneTimeMode: MovementOneTimeMode = MovementOneTimeMode.INCLUDE,
    val dateFrom: String = "",
    val dateTo: String = "",
    /** Bounds on the movement's amount, as typed; blank or unreadable means no bound. */
    val amountMin: String = "",
    val amountMax: String = "",
    val sharedOnly: Boolean = false,
    val recurringOnly: Boolean = false,
    val errorRes: StringResource? = null,
) {
    /** Number of filter-sheet/drill-down criteria that are active on the ledger. */
    val activeFilterCount: Int
        get() = listOf(
            accountId != null,
            categoryId != null || categoryIds != null || uncategorizedOnly,
            tripId != null,
            tagId != null,
            dateFrom.isNotBlank() || dateTo.isNotBlank(),
            amountMin.isNotBlank() || amountMax.isNotBlank(),
            sharedOnly,
            recurringOnly,
            sourceMode != null,
            oneTimeMode != MovementOneTimeMode.INCLUDE,
            paidByPersonOnly,
            type != null && type !in primaryMovementFilterTypes,
        ).count { it }

    /** Drops every filter-sheet and drill-down criterion; the search and a quick type chip stay. */
    fun cleared(): MovementFilters =
        MovementFilters(query = query, type = type?.takeIf { it in primaryMovementFilterTypes && !paidByPersonOnly })
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


/** Identifies which field a refund-form validation error belongs to. */
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
    /** Set when correcting a recorded refund instead of adding one. */
    val refundId: String? = null,
    val amount: String = "",
    val actualAmount: String = "",
    val accountId: String? = null,
    val date: String = "",
    val notes: String = "",
    val errorRes: StringResource? = null,
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
)


fun MovementFilters.withDateValidation(): MovementFilters {
    val from = parseDateOrNull(dateFrom)
    val to = parseDateOrNull(dateTo)
    val error = when {
        dateFrom.isNotBlank() && from == null -> Res.string.movement_filter_date_invalid
        dateTo.isNotBlank() && to == null -> Res.string.movement_filter_date_invalid
        from != null && to != null && from > to -> Res.string.movement_filter_date_order_invalid
        else -> null
    }
    return copy(errorRes = error)
}

private fun MovementFilters.matches(movement: MovementSummary): Boolean {
    if (type != null) {
        if (movement.type != type) return false
    }
    if (paidByPersonOnly && !movement.paidByPerson) return false
    if (sourceMode == MovementSourceMode.ACTUAL && movement.type !in actualMovementTypes) return false
    if (accountId != null && movement.accountId != accountId && movement.destinationAccountId != accountId) {
        return false
    }
    if (categoryId != null && movement.categoryId != categoryId) return false
    if (categoryIds != null && movement.categoryId !in categoryIds) return false
    if (tripId != null && movement.tripId != tripId) return false
    if (tagId != null && movement.tagId != tagId) return false
    if (uncategorizedOnly && movement.categoryId != null) return false
    if (sharedOnly && !movement.isShared && !movement.paidByPerson) return false
    if (recurringOnly && !movement.isRecurring) return false
    parseEuroCents(amountMin, allowNegative = false)?.let { if (movement.amountCents < it) return false }
    parseEuroCents(amountMax, allowNegative = false)?.let { if (movement.amountCents > it) return false }
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


fun parseDate(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }

private fun parseDateOrNull(raw: String): LocalDate? =
    raw.trim().takeIf { it.isNotEmpty() }?.let(::parseDate)

fun String.nullIfBlank(): String? =
    trim().takeIf { it.isNotEmpty() }

private val actualMovementTypes = setOf(
    MovementType.EXPENSE,
    MovementType.INCOME,
    MovementType.REFUND,
)
