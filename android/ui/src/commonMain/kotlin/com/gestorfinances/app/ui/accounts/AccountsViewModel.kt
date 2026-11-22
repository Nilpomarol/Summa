package com.gestorfinances.app.ui.accounts

import com.gestorfinances.app.data.repository.PersonDraft
import kotlinx.coroutines.CoroutineDispatcher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.account_validation_low_balance_invalid
import com.gestorfinances.ui.resources.account_validation_name_required
import com.gestorfinances.ui.resources.account_validation_shared_history
import com.gestorfinances.ui.resources.account_validation_shared_members
import com.gestorfinances.ui.resources.account_validation_shared_percentages
import com.gestorfinances.ui.resources.account_validation_starting_balance_invalid
import com.gestorfinances.ui.resources.movement_validation_amount_positive
import com.gestorfinances.ui.resources.movement_validation_date_invalid
import com.gestorfinances.ui.resources.movement_validation_transfer_same_account
import org.jetbrains.compose.resources.StringResource
import com.gestorfinances.app.data.repository.AccountAllocation
import com.gestorfinances.app.data.repository.AccountGoalReservation
import com.gestorfinances.app.data.repository.GoalRepository
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountLedgerEntry
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.AccountMemberDraft
import com.gestorfinances.app.data.repository.ContributionDirection
import com.gestorfinances.app.data.repository.ContributionDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SplitParticipantKind
import com.gestorfinances.app.notifications.NotificationRefresher
import com.gestorfinances.app.ui.common.EntityColorPalette
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.sortedByDisplayOrderThenName
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.math.RoundingMode
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountsViewModel(
    private val accountRepository: AccountRepository,
    private val goalRepository: GoalRepository,
    private val movementRepository: MovementRepository,
    private val templateRepository: TemplateRepository,
    private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    private val personRepository: PersonRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountsUiState())
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    /** A save under way: a second tap on the button must not record it twice. */
    private var saving = false
    private var addFormRequested = false

    /** Set while a contribution is being written, so a repeated tap cannot record it twice. */
    private var contributionSaveInFlight = false

    /** Opens the new-account form once the next load finishes, so its defaults see real data. */
    fun onAddRequested() {
        addFormRequested = true
    }

    fun onScreenShown() {
        refreshAccounts()
        reloadFlow()
    }

    fun onAddClicked() {
        val accounts = _state.value.accounts
        val nextOrder = (accounts.maxOfOrNull { it.displayOrder } ?: -1L) + 1L
        _state.value = _state.value.copy(
            form = AccountFormState(
                colorHex = EntityColorPalette.first().hex,
                iconKey = defaultIconKeyForType(AccountType.BANK),
                isDefault = accounts.none { it.isDefault },
                displayOrder = nextOrder,
                members = defaultMemberForms(_state.value.people),
            ),
        )
    }

    fun onContributionClicked(account: AccountSummary) = showContributionFor(account.id)

    fun onWithdrawalClicked(account: AccountSummary) =
        showContributionFor(account.id, direction = ContributionDirection.OUT)

    /** Opens the member-money form for a shared account, in the given direction. */
    private fun showContributionFor(
        accountId: String,
        direction: ContributionDirection = ContributionDirection.IN,
    ) {
        _state.value = _state.value.copy(
            contributionForm = ContributionFormState(
                sharedAccountId = accountId,
                direction = direction,
                date = LocalDate.now().toString(),
            ),
        )
    }

    /** Opens the contribution form on an existing contribution so it can be corrected. */
    fun editContribution(id: String) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { requireNotNull(accountRepository.getContribution(id)) { "Contribution not found." } }
            }
            _state.value = result.fold(
                onSuccess = { recorded ->
                    _state.value.copy(
                        contributionForm = ContributionFormState(
                            sharedAccountId = recorded.sharedAccountId,
                            id = recorded.id,
                            direction = recorded.direction,
                            amount = formatEuroInput(recorded.amountCents),
                            date = recorded.date,
                            personId = recorded.personId,
                            sourceAccountId = recorded.sourceAccountId,
                            name = recorded.name.orEmpty(),
                            notes = recorded.notes.orEmpty(),
                        ),
                    )
                },
                onFailure = { _state.value.copy(errorMessage = it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    fun onContributionFormChanged(form: ContributionFormState) {
        _state.value = _state.value.copy(contributionForm = form.copy(errorRes = null, errorMessage = null))
    }

    fun onContributionDismissed() {
        _state.value = _state.value.copy(contributionForm = null)
    }

    fun onContributionSaveClicked() {
        if (contributionSaveInFlight) return
        val form = _state.value.contributionForm ?: return
        val amount = parseEuroCents(form.amount, allowNegative = false)
        val error = when {
            amount == null || amount <= 0 -> Res.string.movement_validation_amount_positive
            runCatching { LocalDate.parse(form.date) }.isFailure -> Res.string.movement_validation_date_invalid
            form.sourceAccountId == form.sharedAccountId -> Res.string.movement_validation_transfer_same_account
            else -> null
        }
        if (error != null) {
            _state.value = _state.value.copy(contributionForm = form.copy(errorRes = error))
            return
        }
        val now = Instant.now().toString()
        contributionSaveInFlight = true
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val draft = ContributionDraft(
                        id = form.id ?: UUID.randomUUID().toString(),
                        sharedAccountId = form.sharedAccountId,
                        direction = form.direction,
                        contributorKind = if (form.personId == null) SplitParticipantKind.USER else SplitParticipantKind.PERSON,
                        personId = form.personId,
                        sourceAccountId = form.sourceAccountId.takeIf { form.personId == null },
                        amountCents = requireNotNull(amount),
                        date = form.date,
                        name = form.name.trim().ifEmpty { null },
                        notes = form.notes.trim().ifEmpty { null },
                    )
                    if (form.id == null) {
                        accountRepository.createContribution(draft, createdAt = now)
                    } else {
                        accountRepository.updateContribution(draft, updatedAt = now)
                    }
                }
            }
            contributionSaveInFlight = false
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(contributionForm = null)
                    refreshAccounts()
                    // The account page the form was opened over now holds different figures.
                    reloadFlow()
                },
                onFailure = { _state.value = _state.value.copy(contributionForm = form.copy(errorMessage = it.message ?: it.javaClass.simpleName)) },
            )
        }
    }

    fun onEditClicked(account: AccountSummary) {
        _state.value = _state.value.copy(form = account.toFormState(_state.value.people))
    }

    fun onOwnershipChanged(kind: AccountOwnershipKind) {
        val form = _state.value.form ?: return
        // Making an account shared makes nobody a member: the owner chooses who is, then how to split.
        val members = if (form.members.isEmpty()) defaultMemberForms(_state.value.people) else form.members
        onFormChanged(form.copy(ownershipKind = kind, members = members))
    }

    /** Creates a person from the account form and adds them to it as a member. */
    fun onCreatePersonForAccount(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            val now = Instant.now().toString()
            val personId = UUID.randomUUID().toString()
            val result = withContext(ioDispatcher) {
                runCatching {
                    val people = requireNotNull(personRepository) { "Person repository is unavailable." }
                    people.create(
                        PersonDraft(id = personId, name = trimmedName, avatar = null, color = null, notes = null),
                        createdAt = now,
                    )
                    people.listActive()
                }
            }
            result.fold(
                onSuccess = { people ->
                    val form = _state.value.form ?: return@fold
                    _state.value = _state.value.copy(
                        people = people,
                        form = form.copy(
                            // Equal parts stay equal; typed percentages are left as typed.
                            members = (form.members + AccountMemberFormState(personId, trimmedName, true, "0,00", "0,00"))
                                .let { if (form.members == form.members.splitEqually()) it.splitEqually() else it },
                            errorRes = null,
                            errorField = null,
                        ),
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

    fun onMoveUpClicked(account: AccountSummary) {
        moveAccount(account, offset = -1)
    }

    fun onMoveDownClicked(account: AccountSummary) {
        moveAccount(account, offset = 1)
    }

    fun onArchiveClicked(account: AccountSummary) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    AccountArchiveCandidate(
                        account = account,
                        activeTemplateCount = templateRepository.listActive().count {
                            it.status == TemplateStatus.ACTIVE &&
                                (it.accountId == account.id || it.destAccountId == account.id)
                        },
                        goalNames = goalRepository.accountGoalReservations(account.id).map { it.name }.distinct(),
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = { candidate -> _state.value.copy(archiveCandidate = candidate) },
                onFailure = { _state.value.copy(errorMessage = it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    /** The account as it stands now with its ledger and its goals: the page shows all, so all load together. */
    private fun loadFlow(accountId: String): AccountFlowDetailState =
        AccountFlowDetailState(
            account = requireNotNull(accountRepository.getActive(accountId)) { "Account not found." },
            entries = movementRepository.listActiveForAccount(accountId),
            goals = goalRepository.accountGoalReservations(accountId),
        )

    /** Reloads an open account page in place after its data changed, without a loading flash. */
    private fun reloadFlow() {
        val open = _state.value.flowDetail ?: return
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { runCatching { loadFlow(open.account.id) } }
            result.onSuccess { fresh ->
                if (_state.value.flowDetail?.account?.id == fresh.account.id) {
                    _state.value = _state.value.copy(flowDetail = fresh)
                }
            }
        }
    }

    /** Opens the account page: the account with its ledger, plus the accounts and people its forms use. */
    fun onAccountDetailOpened(accountId: String) {
        refreshAccounts()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { runCatching { loadFlow(accountId) } }
            _state.value = result.fold(
                onSuccess = { fresh -> _state.value.copy(flowDetail = fresh) },
                onFailure = { error ->
                    val message = error.message ?: error.javaClass.simpleName
                    val open = _state.value.flowDetail
                    if (open != null) {
                        _state.value.copy(flowDetail = open.copy(errorMessage = message))
                    } else {
                        _state.value.copy(errorMessage = message)
                    }
                },
            )
        }
    }

    fun onArchiveDismissed() {
        _state.value = _state.value.copy(archiveCandidate = null)
    }

    fun onArchiveConfirmed(onSuccess: (undo: suspend () -> Unit) -> Unit = {}) {
        val account = _state.value.archiveCandidate?.account ?: return
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val pausedTemplateIds = templateRepository.listActive()
                        .filter {
                            it.status == TemplateStatus.ACTIVE &&
                                (it.accountId == account.id || it.destAccountId == account.id)
                        }
                        .map { it.id }
                    movementRepository.runInTransaction {
                        pausedTemplateIds.forEach {
                            templateRepository.setStatus(it, TemplateStatus.PAUSED, updatedAt = now)
                        }
                        accountRepository.archive(account.id, archivedAt = now)
                    }
                    AccountDeleteOperation(
                        accountId = account.id,
                        deletedAt = now,
                        wasDefault = account.isDefault,
                        pausedTemplateIds = pausedTemplateIds,
                    )
                }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(archiveCandidate = null)
                    refreshAccounts()
                    refreshNotifications()
                    onSuccess { undoDelete(it) }
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

    private suspend fun undoDelete(operation: AccountDeleteOperation) {
        val restoredAt = Instant.now().toString()
        val result = withContext(ioDispatcher) {
            runCatching {
                movementRepository.runInTransaction {
                    accountRepository.restore(
                        id = operation.accountId,
                        deletedAt = operation.deletedAt,
                        restoredAt = restoredAt,
                        wasDefault = operation.wasDefault,
                    )
                    operation.pausedTemplateIds.forEach {
                        templateRepository.restoreActiveStatusAfterDelete(
                            id = it,
                            deletedAt = operation.deletedAt,
                            restoredAt = restoredAt,
                        )
                    }
                }
            }
        }
        result.fold(
            onSuccess = {
                refreshAccounts()
                refreshNotifications()
            },
            onFailure = { _state.value = _state.value.copy(errorMessage = it.message ?: it.javaClass.simpleName) },
        )
    }

    fun onFormChanged(form: AccountFormState) {
        _state.value = _state.value.copy(form = form)
    }

    fun onFormDismissed() {
        _state.value = _state.value.copy(form = null)
    }

    fun onSaveClicked() {
        val form = _state.value.form ?: return
        val name = form.name.trim()
        val startingBalance = parseEuroCents(form.startingBalance, allowNegative = true)
        val lowBalanceThreshold = form.lowBalanceThreshold
            .takeIf { it.isNotBlank() }
            ?.let { parseEuroCents(it, allowNegative = true) }

        val (errorRes, errorField) = when {
            name.isEmpty() -> Res.string.account_validation_name_required to AccountFormField.NAME
            startingBalance == null ->
                Res.string.account_validation_starting_balance_invalid to AccountFormField.STARTING_BALANCE
            form.lowBalanceThreshold.isNotBlank() && lowBalanceThreshold == null ->
                Res.string.account_validation_low_balance_invalid to AccountFormField.LOW_BALANCE_THRESHOLD
            form.ownershipKind == AccountOwnershipKind.SHARED && form.members.count { it.enabled } < 2 ->
                Res.string.account_validation_shared_members to AccountFormField.MEMBERS
            form.ownershipKind == AccountOwnershipKind.SHARED && form.memberDraftsOrNull() == null ->
                Res.string.account_validation_shared_percentages to AccountFormField.MEMBERS
            else -> null to null
        }

        if (errorRes != null) {
            _state.value = _state.value.copy(form = form.copy(errorRes = errorRes, errorField = errorField))
            return
        }

        val now = Instant.now().toString()
        val draft = AccountDraft(
            id = form.id ?: UUID.randomUUID().toString(),
            name = name,
            startingBalanceCents = requireNotNull(startingBalance),
            type = form.type,
            icon = form.iconKey,
            color = form.colorHex,
            isDefault = form.isDefault,
            displayOrder = form.displayOrder,
            lowBalanceThresholdCents = lowBalanceThreshold,
            ownershipKind = form.ownershipKind,
            members = form.memberDraftsOrNull().orEmpty(),
        )

        if (saving) return
        saving = true
        viewModelScope.launch {
            // Un-sharing is refused rather than silently stranding the expenses and contributions
            // that named this account as shared; ask the repository before it has to throw.
            if (form.id != null && form.ownershipKind == AccountOwnershipKind.PERSONAL) {
                val hasSharedHistory = withContext(ioDispatcher) {
                    runCatching { accountRepository.hasSharedHistory(form.id) }.getOrDefault(false)
                }
                if (hasSharedHistory) {
                    _state.value = _state.value.copy(
                        form = form.copy(
                            errorRes = Res.string.account_validation_shared_history,
                            errorField = AccountFormField.OWNERSHIP,
                        ),
                    )
                    return@launch
                }
            }
            val result = withContext(ioDispatcher) {
                runCatching {
                    if (form.id == null) {
                        accountRepository.create(draft, createdAt = now)
                    } else {
                        accountRepository.update(draft, updatedAt = now)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(form = null)
                    refreshAccounts()
                    refreshNotifications()
                    reloadFlow()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        form = form.copy(errorMessage = it.message ?: it.javaClass.simpleName),
                    )
                },
            )
        }.invokeOnCompletion { saving = false }
    }

    private fun refreshAccounts() {
        viewModelScope.launch {
            // Only a first load shows as loading: a reload keeps the list on screen.
            _state.value = _state.value.copy(isLoading = _state.value.accounts.isEmpty(), errorMessage = null)
            val result = withContext(ioDispatcher) {
                runCatching {
                    val accounts = accountRepository.listActive()
                    val allocations = goalRepository.accountAllocations()
                    val people = personRepository?.listActive().orEmpty()
                    // The last twelve month ends, oldest first: the hero's trend line and this month's change.
                    val thisMonth = YearMonth.now()
                    val monthEnds = (12 downTo 1).map { back ->
                        accountRepository.netWorthAsOf(thisMonth.minusMonths(back.toLong()).atEndOfMonth().toString())
                    }
                    LoadedAccounts(accounts, allocations, people, monthEnds)
                }
            }
            _state.value = result.fold(
                onSuccess = { _state.value.copy(accounts = it.accounts, accountAllocations = it.allocations.associateBy { row -> row.accountId }, netWorthMonthEndsCents = it.netWorthMonthEndsCents, people = it.people, isLoading = false) },
                onFailure = {
                    _state.value.copy(
                        isLoading = false,
                        errorMessage = it.message ?: it.javaClass.simpleName,
                    )
                },
            )
            if (addFormRequested && result.isSuccess) {
                addFormRequested = false
                onAddClicked()
            }
        }
    }

    private fun refreshNotifications() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                runCatching { notificationRefresher.refreshNotifications() }
            }
        }
    }

    private fun moveAccount(account: AccountSummary, offset: Int) {
        val ordered = _state.value.accounts
            .sortedByDisplayOrderThenName(displayOrder = { it.displayOrder }, name = { it.name })
            .toMutableList()
        val fromIndex = ordered.indexOfFirst { it.id == account.id }
        val toIndex = (fromIndex + offset).coerceIn(0, ordered.lastIndex)
        if (fromIndex == -1 || fromIndex == toIndex) return

        val moved = ordered.removeAt(fromIndex)
        ordered.add(toIndex, moved)
        val now = Instant.now().toString()

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    ordered.forEachIndexed { index, item ->
                        accountRepository.update(item.toDraft(displayOrder = index.toLong()), updatedAt = now)
                    }
                }
            }
            result.fold(
                onSuccess = { refreshAccounts() },
                onFailure = {
                    _state.value = _state.value.copy(errorMessage = it.message ?: it.javaClass.simpleName)
                },
            )
        }
    }
}

data class AccountsUiState(
    val accounts: List<AccountSummary> = emptyList(),
    val accountAllocations: Map<String, AccountAllocation> = emptyMap(),
    /** Net worth at each of the last twelve month ends, oldest first; the last is last month's. */
    val netWorthMonthEndsCents: List<Long> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: AccountFormState? = null,
    val archiveCandidate: AccountArchiveCandidate? = null,
    val flowDetail: AccountFlowDetailState? = null,
    val people: List<PersonSummary> = emptyList(),
    val contributionForm: ContributionFormState? = null,
)

private data class LoadedAccounts(
    val accounts: List<AccountSummary>,
    val allocations: List<AccountAllocation>,
    val people: List<PersonSummary>,
    val netWorthMonthEndsCents: List<Long>,
)

data class AccountArchiveCandidate(
    val account: AccountSummary,
    val activeTemplateCount: Int,
    /** Goals holding money in the account (or dedicated to it); they keep counting it once archived. */
    val goalNames: List<String> = emptyList(),
)

private data class AccountDeleteOperation(
    val accountId: String,
    val deletedAt: String,
    val wasDefault: Boolean,
    val pausedTemplateIds: List<String>,
)

data class AccountFlowDetailState(
    val account: AccountSummary,
    val entries: List<AccountLedgerEntry> = emptyList(),
    /** The goals holding money in this account. */
    val goals: List<AccountGoalReservation> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/** Identifies which field an account-form validation error belongs to. */
enum class AccountFormField {
    NAME,
    STARTING_BALANCE,
    LOW_BALANCE_THRESHOLD,
    OWNERSHIP,
    MEMBERS,
}

data class AccountFormState(
    val id: String? = null,
    val name: String = "",
    val startingBalance: String = "0",
    val type: AccountType = AccountType.BANK,
    val colorHex: String? = null,
    val iconKey: String? = null,
    val isDefault: Boolean = false,
    val displayOrder: Long = 0,
    val lowBalanceThreshold: String = "",
    val showAdvanced: Boolean = false,
    val ownershipKind: AccountOwnershipKind = AccountOwnershipKind.PERSONAL,
    val members: List<AccountMemberFormState> = emptyList(),
    /** An existing account's balance now, which ownership is a share of; null for a new account. */
    val currentBalanceCents: Long? = null,
    val errorRes: StringResource? = null,
    val errorField: AccountFormField? = null,
    val errorMessage: String? = null,
)

data class AccountMemberFormState(
    val personId: String?,
    val name: String,
    val enabled: Boolean,
    val ownershipPercent: String,
    val defaultExpensePercent: String,
)

data class ContributionFormState(
    val sharedAccountId: String,
    /** The contribution being corrected, or null for a new one. */
    val id: String? = null,
    val direction: ContributionDirection = ContributionDirection.IN,
    val amount: String = "",
    val date: String,
    val personId: String? = null,
    val sourceAccountId: String? = null,
    val name: String = "",
    val notes: String = "",
    val errorRes: StringResource? = null,
    val errorMessage: String? = null,
)

private fun AccountSummary.toFormState(people: List<PersonSummary>): AccountFormState =
    AccountFormState(
        id = id,
        name = name,
        startingBalance = formatEuroInput(startingBalanceCents),
        type = type,
        colorHex = color ?: EntityColorPalette.first().hex,
        iconKey = icon ?: defaultIconKeyForType(type),
        isDefault = isDefault,
        displayOrder = displayOrder,
        lowBalanceThreshold = lowBalanceThresholdCents?.let(::formatEuroInput) ?: "",
        ownershipKind = ownershipKind,
        currentBalanceCents = currentBalanceCents,
        // A personal account has no members yet: making it shared starts from the owner alone.
        members = if (members.isEmpty()) defaultMemberForms(people) else buildList {
            addAll(members.map {
            AccountMemberFormState(
                personId = it.personId,
                name = it.personName.orEmpty(),
                enabled = true,
                ownershipPercent = it.ownershipBasisPoints.toPercentInput(),
                defaultExpensePercent = it.defaultExpenseBasisPoints.toPercentInput(),
            )
            })
            val existingPersonIds = members.mapNotNull { it.personId }.toSet()
            people.filter { it.id !in existingPersonIds }.forEach {
                add(AccountMemberFormState(it.id, it.name, false, "0,00", "0,00"))
            }
        },
    )

private fun AccountSummary.toDraft(displayOrder: Long): AccountDraft =
    AccountDraft(
        id = id,
        name = name,
        startingBalanceCents = startingBalanceCents,
        type = type,
        icon = icon,
        color = color,
        isDefault = isDefault,
        displayOrder = displayOrder,
        lowBalanceThresholdCents = lowBalanceThresholdCents,
        ownershipKind = ownershipKind,
        members = members.map { AccountMemberDraft(it.participantKind, it.personId, it.ownershipBasisPoints, it.defaultExpenseBasisPoints) },
    )

private fun AccountFormState.memberDraftsOrNull(): List<AccountMemberDraft>? {
    if (ownershipKind == AccountOwnershipKind.PERSONAL) return emptyList()
    val enabled = members.filter { it.enabled }
    val drafts = enabled.map { member ->
        AccountMemberDraft(
            participantKind = if (member.personId == null) SplitParticipantKind.USER else SplitParticipantKind.PERSON,
            personId = member.personId,
            ownershipBasisPoints = member.ownershipPercent.toBasisPointsOrNull() ?: return null,
            defaultExpenseBasisPoints = member.defaultExpensePercent.toBasisPointsOrNull() ?: return null,
        )
    }
    return drafts.takeIf {
        it.sumOf(AccountMemberDraft::ownershipBasisPoints) == 10_000L &&
            it.sumOf(AccountMemberDraft::defaultExpenseBasisPoints) == 10_000L
    }
}

private fun String.toBasisPointsOrNull(): Long? =
    runCatching {
        replace(',', '.').toBigDecimalOrNull()?.multiply(java.math.BigDecimal(100))
            ?.setScale(0, RoundingMode.UNNECESSARY)?.longValueExact()?.takeIf { it in 0..10_000 }
    }.getOrNull()

private fun Long.toPercentInput(): String = "%d,%02d".format(this / 100, this % 100)

/**
 * Basis-point total of one percentage column across the enabled members, or null when any of
 * them is unreadable. The form editor shows it live so 100% is reached before saving, not after.
 */
fun List<AccountMemberFormState>.basisPointTotal(
    column: (AccountMemberFormState) -> String,
): Long? = filter { it.enabled }
    .fold(0L) { total, member -> total + (column(member).toBasisPointsOrNull() ?: return null) }

/** Spreads 100% evenly over the enabled members, giving the leftover basis points to the first. */
fun List<AccountMemberFormState>.splitEqually(): List<AccountMemberFormState> {
    val enabledCount = count { it.enabled }
    if (enabledCount == 0) return this
    val share = 10_000L / enabledCount
    var leftover = 10_000L - share * enabledCount
    return map { member ->
        if (!member.enabled) return@map member
        val percent = (share + leftover).toPercentInput()
        leftover = 0L
        member.copy(ownershipPercent = percent, defaultExpensePercent = percent)
    }
}

/**
 * A percentage typed for the member at [index], into either column or both; between two members
 * the other takes the rest of the hundred.
 */
fun List<AccountMemberFormState>.withPercent(
    index: Int,
    value: String,
    ownership: Boolean,
    expenses: Boolean,
): List<AccountMemberFormState> {
    fun AccountMemberFormState.set(percent: String) = copy(
        ownershipPercent = if (ownership) percent else ownershipPercent,
        defaultExpensePercent = if (expenses) percent else defaultExpensePercent,
    )
    val enabled = indices.filter { this[it].enabled }
    val other = enabled.singleOrNull { it != index }?.takeIf { enabled.size == 2 }
    val rest = value.toBasisPointsOrNull()?.let { (10_000L - it).toPercentInput() }
    return mapIndexed { i, member ->
        when {
            i == index -> member.set(value)
            i == other && rest != null -> member.set(rest)
            else -> member
        }
    }
}

/**
 * The member at [index] in or out of the account. With [equal] parts the hundred is shared out
 * evenly again; with typed percentages the others keep theirs and the totals say what is off.
 */
fun List<AccountMemberFormState>.withMember(index: Int, enabled: Boolean, equal: Boolean): List<AccountMemberFormState> =
    mapIndexed { i, member ->
        if (i == index) member.copy(enabled = enabled, ownershipPercent = "0,00", defaultExpensePercent = "0,00") else member
    }.let { if (equal) it.splitEqually() else it }

private fun defaultMemberForms(people: List<PersonSummary>): List<AccountMemberFormState> =
    listOf(AccountMemberFormState(null, "", true, "100,00", "100,00")) +
        people.map { AccountMemberFormState(it.id, it.name, false, "0,00", "0,00") }

private fun defaultIconKeyForType(type: AccountType): String =
    when (type) {
        AccountType.BANK -> "account_balance"
        AccountType.CASH -> "payments"
        AccountType.SAVINGS -> "savings"
        AccountType.INVESTMENT -> "trending_up"
        AccountType.OTHER -> "wallet"
    }
