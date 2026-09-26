package com.gestorfinances.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountAllocation
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.GoalAllocation
import com.gestorfinances.app.data.repository.GoalAllocationDraft
import com.gestorfinances.app.data.repository.GoalDraft
import com.gestorfinances.app.data.repository.GoalRepository
import com.gestorfinances.app.data.repository.GoalFundingConflictException
import com.gestorfinances.app.data.repository.GoalStatus
import com.gestorfinances.app.data.repository.GoalSummary
import com.gestorfinances.app.data.repository.NegativeGoalAllocationException
import com.gestorfinances.app.data.repository.OverAllocationWarning
import com.gestorfinances.app.domain.rules.GoalFundingMode
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.common.parseEuroCents
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GoalsViewModel(
    private val goalRepository: GoalRepository,
    private val accountRepository: AccountRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(GoalsUiState())
    val state: StateFlow<GoalsUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refresh()
    }

    fun onAddClicked() {
        _state.value = _state.value.copy(form = GoalFormState(today = today().toString(), accountId = _state.value.accountFilterId))
    }

    fun showForAccount(accountId: String) {
        _state.value = GoalsUiState(accountFilterId = accountId)
        refresh()
    }

    fun onEditClicked(goal: GoalSummary) {
        _state.value = _state.value.copy(detail = null, form = goal.toFormState())
    }

    fun onFormChanged(form: GoalFormState) {
        if (_state.value.form?.isSaving == true) return
        _state.value = _state.value.copy(
            form = form.copy(errorRes = null, errorField = null, errorMessage = null),
        )
    }

    fun onFormDismissed() {
        if (_state.value.form?.isSaving == true) return
        _state.value = _state.value.copy(form = null)
    }

    fun onGoalClicked(goal: GoalSummary) {
        _state.value = _state.value.copy(detail = GoalDetailState(goalId = goal.id))
        refreshDetail(goal.id)
    }

    fun onDetailDismissed() {
        _state.value = _state.value.copy(detail = null)
    }

    fun onStatusChanged(goal: GoalSummary, status: GoalStatus) {
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { goalRepository.setStatus(goal.id, status, updatedAt = now) }
            }
            result.fold(
                onSuccess = { refresh() },
                onFailure = { _state.value = _state.value.copy(detail = _state.value.detail?.copy(errorRes = R.string.failure_save_goal)) },
            )
        }
    }

    fun onDeleteClicked(goal: GoalSummary) {
        _state.value = _state.value.copy(archiveCandidate = goal)
    }

    fun onDeleteEditingGoalClicked() {
        if (_state.value.form?.isSaving == true) return
        val id = _state.value.form?.id ?: return
        val goal = _state.value.goals.firstOrNull { it.id == id } ?: return
        // Modal sheets render in their own dialog layer. Close it before opening the confirmation
        // so the confirmation is visible and can receive interaction.
        _state.value = _state.value.copy(form = null, detail = null, archiveCandidate = goal)
    }

    fun onArchiveDismissed() {
        _state.value = _state.value.copy(archiveCandidate = null)
    }

    fun onArchiveConfirmed(onSuccess: (undo: () -> Unit) -> Unit = {}) {
        val goal = _state.value.archiveCandidate ?: return
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { goalRepository.archive(goal.id, archivedAt = now) }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(archiveCandidate = null)
                    refresh()
                    onSuccess { undoDelete(goal.id, deletedAt = now) }
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        archiveCandidate = null,
                        errorMessage = it.diagnostic(),
                    )
                },
            )
        }
    }

    private fun undoDelete(goalId: String, deletedAt: String) {
        val restoredAt = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { goalRepository.restore(goalId, deletedAt, restoredAt) }
            }
            result.fold(
                onSuccess = { refresh() },
                onFailure = { _state.value = _state.value.copy(errorMessage = it.diagnostic()) },
            )
        }
    }

    fun onSaveClicked() {
        val form = _state.value.form ?: return
        if (form.isSaving) return
        val target = parseEuroCents(form.target, allowNegative = false)

        val (errorRes, errorField) = when {
            form.name.isBlank() -> R.string.goal_validation_name_required to GoalFormField.NAME
            target == null || target <= 0L -> R.string.goal_validation_target_positive to GoalFormField.TARGET
            form.fundingMode == GoalFundingMode.DEDICATED_ACCOUNT && form.accountId == null ->
                R.string.goal_validation_account_required to GoalFormField.ACCOUNT
            else -> null to null
        }
        if (errorRes != null) {
            _state.value = _state.value.copy(form = form.copy(errorRes = errorRes, errorField = errorField))
            return
        }

        val draft = GoalDraft(
            id = form.id ?: UUID.randomUUID().toString(),
            name = form.name.trim(),
            targetAmountCents = requireNotNull(target),
            targetDate = form.targetDate.ifBlank { null },
            accountId = form.accountId,
            fundingMode = form.fundingMode,
            icon = form.icon.ifBlank { null },
            color = form.color.ifBlank { null },
            notes = form.notes.ifBlank { null },
        )
        _state.value = _state.value.copy(form = form.copy(isSaving = true))
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    if (form.id == null) {
                        goalRepository.create(draft, createdAt = now)
                    } else {
                        goalRepository.update(draft, updatedAt = now)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(form = null)
                    refresh()
                },
                onFailure = {
                    _state.value = _state.value.copy(form = if (it is GoalFundingConflictException) form.copy(errorRes = R.string.goal_funding_conflict) else form.copy(errorMessage = it.diagnostic()))
                },
            )
        }
    }

    fun onAddAllocationClicked(goal: GoalSummary, release: Boolean = false) {
        _state.value = _state.value.copy(
            allocationForm = AllocationFormState(
                goalId = goal.id,
                release = release,
                accountId = if (release) _state.value.detail?.reservations?.entries?.firstOrNull { it.value > 0L }?.key else goal.accountId ?: defaultAllocationAccountId(),
                date = today().toString(),
            ),
        )
    }

    fun onEditAllocationClicked(allocation: GoalAllocation) {
        _state.value = _state.value.copy(allocationForm = allocation.toFormState())
    }

    fun onAllocationFormChanged(form: AllocationFormState) {
        if (_state.value.allocationForm?.isSaving == true) return
        _state.value = _state.value.copy(
            allocationForm = form.copy(
                errorRes = null,
                errorField = null,
                errorMessage = null,
                overAllocation = null,
            ),
        )
    }

    fun onAllocationFormDismissed() {
        if (_state.value.allocationForm?.isSaving == true) return
        _state.value = _state.value.copy(allocationForm = null)
    }

    fun onDeleteAllocationClicked(allocation: GoalAllocation, onSuccess: (undo: () -> Unit) -> Unit) {
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { goalRepository.archiveAllocation(allocation.id, archivedAt = now) }
            }
            result.fold(
                onSuccess = {
                    // The root Undo snackbar must be reachable above the page, not behind a sheet.
                    _state.value = _state.value.copy(detail = null)
                    refresh()
                    onSuccess {
                        viewModelScope.launch {
                            val restored = withContext(ioDispatcher) {
                                runCatching { goalRepository.restoreAllocation(allocation.id, now, Instant.now().toString()) }
                            }
                            restored.fold(
                                onSuccess = { refresh() },
                                onFailure = { showDetailFailure(it) },
                            )
                        }
                    }
                },
                onFailure = { showDetailFailure(it) },
            )
        }
    }

    private fun showDetailFailure(failure: Throwable) {
        val error = when (failure) {
            is NegativeGoalAllocationException -> R.string.goal_delete_allocation_invalid
            is GoalFundingConflictException -> R.string.goal_funding_conflict
            else -> R.string.failure_save_goal_allocation
        }
        val detail = _state.value.detail
        _state.value = if (detail != null) {
            _state.value.copy(detail = detail.copy(errorRes = error))
        } else {
            _state.value.copy(actionErrorRes = error)
        }
    }

    /**
     * Saves an allocation. Reserving more than the account still holds stays valid — an account
     * value can legitimately drop after the plan was made — so the first attempt surfaces a
     * dismissible warning and a second confirms it.
     */
    fun onSaveAllocationClicked(confirmOverAllocation: Boolean = false) {
        val form = _state.value.allocationForm ?: return
        if (form.isSaving) return
        val amount = parseEuroCents(form.amount, allowNegative = false)

        val (errorRes, errorField) = when {
            form.accountId == null -> R.string.goal_validation_account_required to AllocationFormField.ACCOUNT
            amount == null || amount == 0L ->
                R.string.goal_validation_allocation_nonzero to AllocationFormField.AMOUNT
            form.date.isBlank() -> R.string.goal_validation_date_required to AllocationFormField.DATE
            else -> null to null
        }
        if (errorRes != null) {
            _state.value = _state.value.copy(
                allocationForm = form.copy(errorRes = errorRes, errorField = errorField),
            )
            return
        }

        val accountId = requireNotNull(form.accountId)
        val amountCents = requireNotNull(amount) * if (form.release) -1 else 1
        val draft = GoalAllocationDraft(
            id = form.id ?: UUID.randomUUID().toString(),
            goalId = form.goalId,
            accountId = accountId,
            date = form.date,
            amountCents = amountCents,
            notes = form.notes.ifBlank { null },
        )
        _state.value = _state.value.copy(allocationForm = form.copy(isSaving = true))
        val now = Instant.now().toString()
        viewModelScope.launch {
            val warningResult = if (confirmOverAllocation) {
                Result.success(null)
            } else {
                withContext(ioDispatcher) {
                    runCatching {
                        goalRepository.overAllocationWarning(
                            accountId = accountId,
                            amountCents = amountCents,
                            replacingAllocationId = form.id,
                        )
                    }
                }
            }
            if (warningResult.isFailure) {
                _state.value = _state.value.copy(allocationForm = form.copy(errorMessage = warningResult.exceptionOrNull()!!.diagnostic()))
                return@launch
            }
            val warning = warningResult.getOrNull()
            if (warning != null) {
                _state.value = _state.value.copy(
                    allocationForm = form.copy(overAllocation = warning),
                )
                return@launch
            }

            val result = withContext(ioDispatcher) {
                runCatching {
                    if (form.id == null) {
                        goalRepository.allocate(draft, createdAt = now)
                    } else {
                        goalRepository.updateAllocation(draft, updatedAt = now)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(allocationForm = null)
                    refresh()
                },
                onFailure = { failure ->
                    _state.value = _state.value.copy(
                        allocationForm = if (failure is NegativeGoalAllocationException) {
                            form.copy(
                                errorRes = R.string.goal_validation_release_too_large,
                                errorField = AllocationFormField.AMOUNT,
                            )
                        } else if (failure is GoalFundingConflictException) {
                            form.copy(errorRes = R.string.goal_funding_conflict)
                        } else {
                            form.copy(errorMessage = failure.diagnostic())
                        },
                    )
                },
            )
        }
    }

    private fun defaultAllocationAccountId(): String? {
        val dedicated = _state.value.dedicatedAccountIds
        return _state.value.accounts.firstOrNull { it.id !in dedicated }?.id
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null, actionErrorRes = null)
            val result = withContext(ioDispatcher) {
                runCatching {
                    val goals = goalRepository.listActive()
                    LoadedGoalData(
                        goals = goals,
                        accounts = accountRepository.listActive(),
                        accountAllocations = goalRepository.accountAllocations(),
                        dedicatedAccountIds = goalRepository.dedicatedAccountIds(),
                        fundingAccounts = goals.associate { goal -> goal.id to goalRepository.accountReservations(goal.id).filterValues { it > 0L }.keys },
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = { loaded ->
                    _state.value.copy(
                        goals = loaded.goals,
                        accounts = loaded.accounts,
                        accountAllocations = loaded.accountAllocations.associateBy { it.accountId },
                        dedicatedAccountIds = loaded.dedicatedAccountIds,
                        fundingAccounts = loaded.fundingAccounts,
                        today = today(),
                        isLoading = false,
                    )
                },
                onFailure = {
                    _state.value.copy(isLoading = false, errorMessage = it.diagnostic())
                },
            )
            _state.value.detail?.let { refreshDetail(it.goalId) }
        }
    }

    private fun refreshDetail(goalId: String) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { goalRepository.allocations(goalId) to goalRepository.accountReservations(goalId) }
            }
            result.fold(
                onSuccess = { allocations ->
                    val detail = _state.value.detail ?: return@fold
                    if (detail.goalId != goalId) return@fold
                    _state.value = _state.value.copy(detail = detail.copy(allocations = allocations.first, reservations = allocations.second, isLoading = false, errorRes = null))
                },
                onFailure = { _state.value = _state.value.copy(detail = _state.value.detail?.copy(isLoading = false, errorRes = R.string.failure_load_goals)) },
            )
        }
    }
}

data class GoalsUiState(
    val goals: List<GoalSummary> = emptyList(),
    val accounts: List<AccountSummary> = emptyList(),
    val accountAllocations: Map<String, AccountAllocation> = emptyMap(),
    val dedicatedAccountIds: Set<String> = emptySet(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: GoalFormState? = null,
    val allocationForm: AllocationFormState? = null,
    val detail: GoalDetailState? = null,
    val archiveCandidate: GoalSummary? = null,
    val actionErrorRes: Int? = null,
    val accountFilterId: String? = null,
    val fundingAccounts: Map<String, Set<String>> = emptyMap(),
) {
    val visibleGoals: List<GoalSummary> get() = goals.filter {
        accountFilterId == null || it.accountId == accountFilterId || accountFilterId in fundingAccounts[it.id].orEmpty()
    }
    val activeGoals: List<GoalSummary> get() = visibleGoals.filter { it.status == GoalStatus.ACTIVE }
    val pausedGoals: List<GoalSummary> get() = visibleGoals.filter { it.status == GoalStatus.PAUSED }
    val completedGoals: List<GoalSummary> get() = visibleGoals.filter { it.status == GoalStatus.COMPLETED }

    /** Accounts that hold planning allocations, with what is still free to assign. */
    val allocatedAccounts: List<AccountAllocation>
        get() = accountAllocations.values
            .filter { it.accountId !in dedicatedAccountIds }
            .sortedBy { allocation -> accounts.firstOrNull { it.id == allocation.accountId }?.name.orEmpty() }
}

/** The open goal sheet and the allocations loaded for it. */
data class GoalDetailState(
    val goalId: String,
    val reservations: Map<String, Long> = emptyMap(),
    val allocations: List<GoalAllocation> = emptyList(),
    val errorRes: Int? = null,
    val isLoading: Boolean = true,
)

enum class GoalFormField { NAME, TARGET, ACCOUNT }

data class GoalFormState(
    val id: String? = null,
    val name: String = "",
    val target: String = "",
    val targetDate: String = "",
    val accountId: String? = null,
    val fundingMode: GoalFundingMode = GoalFundingMode.ALLOCATIONS,
    val icon: String = "",
    val color: String = "",
    val notes: String = "",
    val today: String = "",
    val showOptional: Boolean = false,
    val isSaving: Boolean = false,
    val errorRes: Int? = null,
    val errorField: GoalFormField? = null,
    val errorMessage: String? = null,
)

enum class AllocationFormField { ACCOUNT, AMOUNT, DATE }

data class AllocationFormState(
    val id: String? = null,
    val goalId: String,
    val accountId: String? = null,
    val date: String = "",
    val amount: String = "",
    val notes: String = "",
    val errorRes: Int? = null,
    val errorField: AllocationFormField? = null,
    val errorMessage: String? = null,
    val overAllocation: OverAllocationWarning? = null,
    val release: Boolean = false,
    val isSaving: Boolean = false,
)

private data class LoadedGoalData(
    val goals: List<GoalSummary>,
    val accounts: List<AccountSummary>,
    val accountAllocations: List<AccountAllocation>,
    val dedicatedAccountIds: Set<String>,
    val fundingAccounts: Map<String, Set<String>>,
)

private fun Throwable.diagnostic(): String = message ?: javaClass.simpleName

private fun GoalSummary.toFormState(): GoalFormState =
    GoalFormState(
        id = id,
        name = name,
        target = formatEuroInput(targetAmountCents),
        targetDate = targetDate.orEmpty(),
        accountId = accountId,
        fundingMode = fundingMode,
        icon = icon.orEmpty(),
        color = color.orEmpty(),
        notes = notes.orEmpty(),
    )

private fun GoalAllocation.toFormState(): AllocationFormState =
    AllocationFormState(
        id = id,
        goalId = goalId,
        accountId = accountId,
        date = date,
        amount = formatEuroInput(kotlin.math.abs(amountCents)),
        release = amountCents < 0,
        notes = notes.orEmpty(),
    )
