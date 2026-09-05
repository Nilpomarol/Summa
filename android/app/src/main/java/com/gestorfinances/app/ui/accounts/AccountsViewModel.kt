package com.gestorfinances.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountAllocation
import com.gestorfinances.app.data.repository.GoalRepository
import com.gestorfinances.app.domain.rules.GoalFundingMode
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.TemplateRepository
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.notifications.NotificationRefresher
import com.gestorfinances.app.ui.common.EntityColorPalette
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.sortedByDisplayOrderThenName
import java.time.Instant
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
) : ViewModel() {
    private val _state = MutableStateFlow(AccountsUiState())
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refreshAccounts()
    }

    fun resetForMenuNavigation() {
        _state.value = AccountsUiState()
        refreshAccounts()
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
            ),
        )
    }

    fun onEditClicked(account: AccountSummary) {
        _state.value = _state.value.copy(form = account.toFormState())
    }

    fun onMoveUpClicked(account: AccountSummary) {
        moveAccount(account, offset = -1)
    }

    fun onMoveDownClicked(account: AccountSummary) {
        moveAccount(account, offset = 1)
    }

    fun onArchiveClicked(account: AccountSummary) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    templateRepository.listActive().count {
                        it.status == TemplateStatus.ACTIVE &&
                            (it.accountId == account.id || it.destAccountId == account.id)
                    }
                }
            }
            _state.value = result.fold(
                onSuccess = { count -> _state.value.copy(archiveCandidate = AccountArchiveCandidate(account, count)) },
                onFailure = { _state.value.copy(errorMessage = it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    fun onFlowClicked(account: AccountSummary) {
        _state.value = _state.value.copy(
            flowDetail = AccountFlowDetailState(
                account = account,
                isLoading = true,
            ),
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { movementRepository.listActiveForAccount(account.id) }
            }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        flowDetail = AccountFlowDetailState(
                            account = account,
                            entries = it,
                            isLoading = false,
                        ),
                    )
                },
                onFailure = {
                    _state.value.copy(
                        flowDetail = AccountFlowDetailState(
                            account = account,
                            isLoading = false,
                            errorMessage = it.message ?: it.javaClass.simpleName,
                        ),
                    )
                },
            )
        }
    }

    fun onFlowDismissed() {
        _state.value = _state.value.copy(flowDetail = null)
    }

    fun onArchiveDismissed() {
        _state.value = _state.value.copy(archiveCandidate = null)
    }

    fun onArchiveConfirmed(onSuccess: (undo: () -> Unit) -> Unit = {}) {
        val account = _state.value.archiveCandidate?.account ?: return
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
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

    private fun undoDelete(operation: AccountDeleteOperation) {
        val restoredAt = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
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
            name.isEmpty() -> R.string.account_validation_name_required to AccountFormField.NAME
            startingBalance == null ->
                R.string.account_validation_starting_balance_invalid to AccountFormField.STARTING_BALANCE
            form.lowBalanceThreshold.isNotBlank() && lowBalanceThreshold == null ->
                R.string.account_validation_low_balance_invalid to AccountFormField.LOW_BALANCE_THRESHOLD
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
        )

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
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
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        form = form.copy(errorMessage = it.message ?: it.javaClass.simpleName),
                    )
                },
            )
        }
    }

    private fun refreshAccounts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Triple(accountRepository.listActive(), goalRepository.accountAllocations(),
                        goalRepository.listActive().filter { it.fundingMode == GoalFundingMode.DEDICATED_ACCOUNT }.associate { requireNotNull(it.accountId) to it.name })
                }
            }
            _state.value = result.fold(
                onSuccess = { _state.value.copy(accounts = it.first, accountAllocations = it.second.associateBy { row -> row.accountId }, dedicatedGoals = it.third, isLoading = false) },
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
            withContext(Dispatchers.IO) {
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
            val result = withContext(Dispatchers.IO) {
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

    class Factory(
        private val accountRepository: AccountRepository,
        private val goalRepository: GoalRepository,
        private val movementRepository: MovementRepository,
        private val templateRepository: TemplateRepository,
        private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AccountsViewModel::class.java)) {
                return AccountsViewModel(accountRepository, goalRepository, movementRepository, templateRepository, notificationRefresher) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class AccountsUiState(
    val accounts: List<AccountSummary> = emptyList(),
    val accountAllocations: Map<String, AccountAllocation> = emptyMap(),
    val dedicatedGoals: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: AccountFormState? = null,
    val archiveCandidate: AccountArchiveCandidate? = null,
    val flowDetail: AccountFlowDetailState? = null,
)

data class AccountArchiveCandidate(
    val account: AccountSummary,
    val activeTemplateCount: Int,
)

private data class AccountDeleteOperation(
    val accountId: String,
    val deletedAt: String,
    val wasDefault: Boolean,
    val pausedTemplateIds: List<String>,
)

data class AccountFlowDetailState(
    val account: AccountSummary,
    val entries: List<MovementSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/** Identifies which field an account-form validation error belongs to (field-level validation). */
enum class AccountFormField {
    NAME,
    STARTING_BALANCE,
    LOW_BALANCE_THRESHOLD,
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
    val errorRes: Int? = null,
    val errorField: AccountFormField? = null,
    val errorMessage: String? = null,
)

private fun AccountSummary.toFormState(): AccountFormState =
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
    )

private fun defaultIconKeyForType(type: AccountType): String =
    when (type) {
        AccountType.BANK -> "account_balance"
        AccountType.CASH -> "payments"
        AccountType.SAVINGS -> "savings"
        AccountType.INVESTMENT -> "trending_up"
        AccountType.OTHER -> "wallet"
    }
