package com.gestorfinances.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.AccountFlowEntry
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.notifications.NotificationRefresher
import com.gestorfinances.app.ui.common.EntityColorPalette
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.common.parseEuroCents
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
    private val movementRepository: MovementRepository,
    private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountsUiState())
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    fun onScreenShown() {
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
        _state.value = _state.value.copy(archiveCandidate = account)
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
                runCatching { movementRepository.accountFlowForAccount(account.id) }
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

    fun onArchiveConfirmed() {
        val account = _state.value.archiveCandidate ?: return
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { accountRepository.archive(account.id, archivedAt = now) }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(archiveCandidate = null)
                    refreshAccounts()
                    refreshNotifications()
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

        val errorRes = when {
            name.isEmpty() -> R.string.account_validation_name_required
            startingBalance == null -> R.string.account_validation_starting_balance_invalid
            form.lowBalanceThreshold.isNotBlank() && lowBalanceThreshold == null ->
                R.string.account_validation_low_balance_invalid
            else -> null
        }

        if (errorRes != null) {
            _state.value = _state.value.copy(form = form.copy(errorRes = errorRes))
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
                runCatching { accountRepository.listActive() }
            }
            _state.value = result.fold(
                onSuccess = { _state.value.copy(accounts = it, isLoading = false) },
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
            .sortedWith(compareBy<AccountSummary> { it.displayOrder }.thenBy { it.name.lowercase() })
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
        private val movementRepository: MovementRepository,
        private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AccountsViewModel::class.java)) {
                return AccountsViewModel(accountRepository, movementRepository, notificationRefresher) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class AccountsUiState(
    val accounts: List<AccountSummary> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: AccountFormState? = null,
    val archiveCandidate: AccountSummary? = null,
    val flowDetail: AccountFlowDetailState? = null,
)

data class AccountFlowDetailState(
    val account: AccountSummary,
    val entries: List<AccountFlowEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

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
