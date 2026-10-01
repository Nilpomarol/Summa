package com.gestorfinances.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountDraft
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.CategoryDraft
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.ui.common.parseEuroCents
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingViewModel(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onFormChanged(form: OnboardingFormState) {
        if (_state.value.isSaving) return
        _state.value = _state.value.copy(form = form.copy(errorRes = null, errorMessage = null))
    }

    fun onCreateClicked(defaultCategories: List<DefaultCategorySeed>) {
        if (_state.value.isLoading || _state.value.isSaving || !_state.value.needsOnboarding ||
            _state.value.loadErrorMessage != null
        ) return
        val form = _state.value.form
        val accountName = form.accountName.trim()
        val startingBalance = parseEuroCents(form.startingBalance, allowNegative = true)
        val errorRes = when {
            accountName.isEmpty() -> R.string.account_validation_name_required
            startingBalance == null -> R.string.account_validation_starting_balance_invalid
            else -> null
        }

        if (errorRes != null) {
            _state.value = _state.value.copy(form = form.copy(errorRes = errorRes))
            return
        }

        _state.value = _state.value.copy(isSaving = true)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val now = Instant.now().toString()
                    accountRepository.runInTransaction {
                        accountRepository.create(
                            draft = AccountDraft(
                                id = UUID.randomUUID().toString(),
                                name = accountName,
                                startingBalanceCents = requireNotNull(startingBalance),
                                type = AccountType.BANK,
                                icon = null,
                                color = null,
                                isDefault = true,
                                displayOrder = 0L,
                                lowBalanceThresholdCents = null,
                            ),
                            createdAt = now,
                        )
                        if (categoryRepository.listActive().isEmpty()) {
                            categoryRepository.createAll(defaultCategories.map { it.toDraft() }, createdAt = now)
                        }
                    }
                }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        needsOnboarding = false,
                        isLoading = false,
                        isSaving = false,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        form = form.copy(errorMessage = it.message ?: it.javaClass.simpleName),
                    )
                },
            )
        }
    }

    fun onRetry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, loadErrorMessage = null)
            val result = withContext(ioDispatcher) {
                runCatching { !accountRepository.hasAnyAccount() }
            }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        needsOnboarding = it,
                        isLoading = false,
                    )
                },
                onFailure = {
                    _state.value.copy(
                        isLoading = false,
                        needsOnboarding = true,
                        loadErrorMessage = it.message ?: it.javaClass.simpleName,
                    )
                },
            )
        }
    }
}

data class OnboardingUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val needsOnboarding: Boolean = true,
    val loadErrorMessage: String? = null,
    val form: OnboardingFormState = OnboardingFormState(),
)

data class OnboardingFormState(
    val accountName: String = "",
    val startingBalance: String = "0",
    val errorRes: Int? = null,
    val errorMessage: String? = null,
)

data class DefaultCategorySeed(
    val name: String,
    val kind: CategoryKind,
    val nature: CategoryNature,
    val icon: String,
    val color: String,
    val displayOrder: Long,
)

private fun DefaultCategorySeed.toDraft(): CategoryDraft =
    CategoryDraft(
        id = UUID.randomUUID().toString(),
        name = name,
        kind = kind,
        nature = nature,
        parentId = null,
        icon = icon,
        color = color,
        displayOrder = displayOrder,
    )
