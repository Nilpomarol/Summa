package com.gestorfinances.app.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.MovementDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSplitDraft
import com.gestorfinances.app.data.repository.MovementSplitWrite
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.SplitEntryMethod
import com.gestorfinances.app.data.repository.SplitLineDraft
import com.gestorfinances.app.data.repository.SplitParticipantKind
import com.gestorfinances.app.data.repository.TemplateDraft
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateSplitConfig
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.domain.rules.RecurrenceRule
import com.gestorfinances.app.domain.rules.RecurringAdvancer
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

/** Movement types a template can carry (templates CHECK: expense/income/transfer). */
val templateTypes: List<MovementType> =
    listOf(MovementType.EXPENSE, MovementType.INCOME, MovementType.TRANSFER)

class RecurringViewModel(
    private val templateRepository: TemplateRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val movementRepository: MovementRepository,
    private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(RecurringUiState())
    val state: StateFlow<RecurringUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refresh()
    }

    fun onAddClicked() {
        _state.value = _state.value.copy(
            form = TemplateFormState(nextDueDate = LocalDate.now().toString()),
        )
    }

    fun onEditClicked(template: TemplateSummary) {
        _state.value = _state.value.copy(form = template.toFormState())
    }

    fun onFormChanged(form: TemplateFormState) {
        _state.value = _state.value.copy(form = form.copy(errorRes = null, errorMessage = null))
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
            ),
        )
    }

    fun onConfirmFormChanged(prompt: ConfirmPromptState) {
        _state.value = _state.value.copy(
            confirmPrompt = prompt.copy(errorRes = null, errorMessage = null),
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
        val errorRes = when {
            amountCents == null -> R.string.movement_validation_amount_required
            amountCents <= 0L -> R.string.movement_validation_amount_positive
            prompt.date.isBlank() -> R.string.movement_validation_date_required
            date == null -> R.string.movement_validation_date_invalid
            else -> null
        }
        if (errorRes != null) {
            _state.value = _state.value.copy(confirmPrompt = prompt.copy(errorRes = errorRes))
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
                    movementRepository.create(draft, createdAt = now)
                    templateRepository.advanceCursor(template.id, template.advancedOneStep(), updatedAt = now)
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
        advanceCursorTo(prompt.template.id, prompt.template.advancedOneStep())
    }

    fun onSkipAllClicked(prompt: DuePrompt) {
        advanceCursorTo(prompt.template.id, prompt.template.advancedToToday(today()))
    }

    private fun advanceCursorTo(templateId: String, nextDueDate: String) {
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { templateRepository.advanceCursor(templateId, nextDueDate, updatedAt = now) }
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
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { templateRepository.archive(template.id, archivedAt = now) }
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

        val errorRes = when {
            !form.amountIsVariable && form.amount.isBlank() -> R.string.template_validation_amount_required
            !form.amountIsVariable && amountCents == null -> R.string.template_validation_amount_required
            !form.amountIsVariable && amountCents != null && amountCents <= 0L ->
                R.string.movement_validation_amount_positive
            account == null -> R.string.movement_validation_account_required
            isTransfer && form.destinationAccountId == null -> R.string.movement_validation_account_required
            isTransfer && form.destinationAccountId == form.accountId ->
                R.string.movement_validation_transfer_same_account
            form.frequency.usesDayOfMonth() && (dayOfMonth == null || dayOfMonth !in 1L..31L) ->
                R.string.template_validation_anchor_invalid
            form.frequency == RecurrenceFrequency.CUSTOM && (intervalCount == null || intervalCount <= 0L) ->
                R.string.template_validation_interval_required
            form.nextDueDate.isBlank() -> R.string.movement_validation_date_required
            nextDue == null -> R.string.movement_validation_date_invalid
            else -> null
        }
        if (errorRes != null) {
            _state.value = _state.value.copy(form = form.copy(errorRes = errorRes))
            return
        }

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
            splitConfig = null,
            frequency = form.frequency,
            intervalCount = if (form.frequency == RecurrenceFrequency.CUSTOM) intervalCount else null,
            customUnit = if (form.frequency == RecurrenceFrequency.CUSTOM) form.customUnit else null,
            dayOfMonth = if (form.frequency.usesDayOfMonth()) dayOfMonth else null,
            weekday = if (form.frequency.usesWeekday()) form.weekday?.toLong() else null,
            nextDueDate = requireNotNull(nextDue).toString(),
            amountIsVariable = form.amountIsVariable,
            amountFlexCents = parseEuroCents(form.amountFlex, allowNegative = false),
            dateFlexDays = form.dateFlex.trim().toLongOrNull()?.takeIf { it >= 0L },
            leadNotificationDays = form.leadDays.trim().toLongOrNull()?.takeIf { it >= 0L },
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
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    val (expense, income) = it.templates.monthlyTotals(today())
                    _state.value.copy(
                        templates = it.templates,
                        accounts = it.accounts,
                        categories = it.categories,
                        duePrompts = it.templates.toDuePrompts(today()),
                        monthlyExpenseCents = expense,
                        monthlyIncomeCents = income,
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
    val duePrompts: List<DuePrompt> = emptyList(),
    val monthlyExpenseCents: Long = 0L,
    val monthlyIncomeCents: Long = 0L,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: TemplateFormState? = null,
    val confirmPrompt: ConfirmPromptState? = null,
    val endCandidate: TemplateSummary? = null,
) {
    val monthlyNetCents: Long get() = monthlyIncomeCents - monthlyExpenseCents
    val hasMonthlySummary: Boolean get() = monthlyExpenseCents != 0L || monthlyIncomeCents != 0L
}

/** A virtual occurrence due for an active template (not yet in the ledger). */
data class DuePrompt(
    val template: TemplateSummary,
    val dueDate: String,
    val pendingCount: Int,
)

data class ConfirmPromptState(
    val templateId: String,
    val templateName: String,
    val accountName: String,
    val amount: String = "",
    val date: String = "",
    val errorRes: Int? = null,
    val errorMessage: String? = null,
)

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
    val errorMessage: String? = null,
)

private data class LoadedRecurringData(
    val templates: List<TemplateSummary>,
    val accounts: List<AccountSummary>,
    val categories: List<CategoryRecord>,
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
private fun List<TemplateSummary>.monthlyTotals(today: LocalDate): Pair<Long, Long> {
    val month = YearMonth.from(today)
    val monthEnd = month.atEndOfMonth()
    var expense = 0L
    var income = 0L
    forEach { template ->
        if (template.status != TemplateStatus.ACTIVE) return@forEach
        val amount = template.amountCents
        if (template.amountIsVariable || amount == null) return@forEach
        val cursor = parseDate(template.nextDueDate) ?: return@forEach
        val occurrences = runCatching {
            RecurringAdvancer.advance(template.toRecurrenceRule(), cursor = cursor, today = monthEnd).dueDates
        }.getOrNull() ?: return@forEach
        val count = occurrences.count { YearMonth.from(it) == month }
        when (template.type) {
            MovementType.EXPENSE -> expense += amount * count
            MovementType.INCOME -> income += amount * count
            else -> Unit
        }
    }
    return expense to income
}

/** Day a scheduled template lands on, for day-ordered listing. */
fun TemplateSummary.effectiveDayOfMonth(): Int =
    dayOfMonth?.toInt() ?: runCatching { LocalDate.parse(nextDueDate).dayOfMonth }.getOrDefault(99)

private fun TemplateSummary.toRecurrenceRule(): RecurrenceRule =
    RecurrenceRule(
        frequency = frequency,
        dayOfMonth = dayOfMonth?.toInt(),
        intervalCount = intervalCount,
        customUnit = customUnit,
    )

/** Cursor after materializing/skipping a single occurrence. */
private fun TemplateSummary.advancedOneStep(): String {
    val cursor = LocalDate.parse(nextDueDate)
    return RecurringAdvancer.advance(toRecurrenceRule(), cursor = cursor, today = cursor).newCursor.toString()
}

/** Cursor after skipping the whole backlog up to [today]. */
private fun TemplateSummary.advancedToToday(today: LocalDate): String {
    val cursor = LocalDate.parse(nextDueDate)
    return RecurringAdvancer.advance(toRecurrenceRule(), cursor = cursor, today = today).newCursor.toString()
}

/** Carry the template's split forward, but only when it reconciles with the occurrence amount. */
private fun TemplateSplitConfig?.toSplitWrite(
    type: MovementType,
    amountCents: Long,
): MovementSplitWrite {
    val config = this ?: return MovementSplitWrite.KeepExisting
    if (type != MovementType.EXPENSE) return MovementSplitWrite.KeepExisting
    if (config.lines.sumOf { it.owedAmountCents } != amountCents) return MovementSplitWrite.KeepExisting
    val entryMethod = SplitEntryMethod.entries.firstOrNull { it.dbValue == config.entryMethod }
        ?: return MovementSplitWrite.KeepExisting
    val lines = config.lines.map { line ->
        if (line.party == "user") {
            SplitLineDraft(SplitParticipantKind.USER, personId = null, owedAmountCents = line.owedAmountCents)
        } else {
            SplitLineDraft(SplitParticipantKind.PERSON, personId = line.party, owedAmountCents = line.owedAmountCents)
        }
    }
    return MovementSplitWrite.Replace(MovementSplitDraft(entryMethod = entryMethod, lines = lines))
}

fun RecurrenceFrequency.usesDayOfMonth(): Boolean =
    this == RecurrenceFrequency.MONTHLY || this == RecurrenceFrequency.YEARLY

fun RecurrenceFrequency.usesWeekday(): Boolean =
    this == RecurrenceFrequency.WEEKLY || this == RecurrenceFrequency.FORTNIGHTLY

val CategoryRecord.supportsExpense: Boolean
    get() = kind == CategoryKind.EXPENSE || kind == CategoryKind.BOTH

val CategoryRecord.supportsIncome: Boolean
    get() = kind == CategoryKind.INCOME || kind == CategoryKind.BOTH

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

private fun parseDate(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }
