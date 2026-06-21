package com.gestorfinances.app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.CategoryRepository
import com.gestorfinances.app.data.repository.ExternalSplitDraft
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.PersonBalanceItem
import com.gestorfinances.app.data.repository.PersonDraft
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.data.repository.SettlementDraft
import com.gestorfinances.app.data.repository.SplitRepository
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
import kotlinx.coroutines.withContext

class PeopleViewModel(
    private val personRepository: PersonRepository,
    private val categoryRepository: CategoryRepository,
    private val splitRepository: SplitRepository,
    private val movementRepository: MovementRepository,
    private val accountRepository: AccountRepository,
    private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(PeopleUiState())
    val state: StateFlow<PeopleUiState> = _state.asStateFlow()

    fun onScreenShown() {
        refreshPeople()
    }

    fun onAddClicked() {
        _state.value = _state.value.copy(form = PersonFormState())
    }

    fun onEditClicked(person: PersonSummary) {
        _state.value = _state.value.copy(form = person.toFormState())
    }

    fun onPersonPaidForMeClicked(person: PersonSummary) {
        _state.value = _state.value.copy(
            externalSplitForm = ExternalSplitFormState(
                payerPersonId = person.id,
                payerPersonName = person.name,
                date = LocalDate.now().toString(),
            ),
        )
    }

    fun onSettleUpClicked(person: PersonSummary) {
        if (person.balanceCents == 0L) return
        val direction = if (person.balanceCents > 0L) {
            SettlementDirection.PERSON_TO_USER
        } else {
            SettlementDirection.USER_TO_PERSON
        }
        val outstanding = kotlin.math.abs(person.balanceCents)
        val accounts = _state.value.accounts
        _state.value = _state.value.copy(
            settlementForm = SettlementFormState(
                personId = person.id,
                personName = person.name,
                outstandingCents = outstanding,
                direction = direction,
                amount = formatEuroInput(outstanding),
                accountId = accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id,
                date = LocalDate.now().toString(),
            ),
        )
    }

    fun onSettlementFormChanged(form: SettlementFormState) {
        _state.value = _state.value.copy(
            settlementForm = form.copy(errorRes = null, errorMessage = null),
        )
    }

    fun onSettlementDismissed() {
        _state.value = _state.value.copy(settlementForm = null)
    }

    fun onSettlementSaveClicked() {
        val form = _state.value.settlementForm ?: return
        val amount = parseEuroCents(form.amount, allowNegative = false)
        val date = parseDate(form.date)
        val account = form.accountId?.let { accountId ->
            _state.value.accounts.firstOrNull { it.id == accountId }
        }

        val errorRes = when {
            amount == null -> R.string.movement_validation_amount_required
            amount <= 0L -> R.string.movement_validation_amount_positive
            account == null -> R.string.settlement_validation_account_required
            form.date.isBlank() -> R.string.movement_validation_date_required
            date == null -> R.string.movement_validation_date_invalid
            else -> null
        }

        if (errorRes != null) {
            _state.value = _state.value.copy(settlementForm = form.copy(errorRes = errorRes))
            return
        }

        val now = Instant.now().toString()
        val draft = SettlementDraft(
            id = UUID.randomUUID().toString(),
            personId = form.personId,
            direction = form.direction,
            amountCents = requireNotNull(amount),
            accountId = requireNotNull(account).id,
            date = requireNotNull(date).toString(),
            notes = form.notes.trim().ifBlank { null },
        )

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { movementRepository.createSettlement(draft, createdAt = now) }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(settlementForm = null)
                    refreshPeople()
                    refreshNotifications()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        settlementForm = form.copy(errorMessage = it.message ?: it.javaClass.simpleName),
                    )
                },
            )
        }
    }

    fun onPersonDetailClicked(person: PersonSummary) {
        _state.value = _state.value.copy(
            detail = PersonDetailState(person = person, isLoading = true),
        )
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val currentPerson = personRepository.getActive(person.id) ?: person
                    PersonDetailState(
                        person = currentPerson,
                        items = personRepository.balanceItemsForPerson(person.id),
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = { _state.value.copy(detail = it) },
                onFailure = {
                    _state.value.copy(
                        detail = PersonDetailState(
                            person = person,
                            errorMessage = it.message ?: it.javaClass.simpleName,
                        ),
                    )
                },
            )
        }
    }

    fun onPersonDetailDismissed() {
        _state.value = _state.value.copy(detail = null)
    }

    fun onArchiveClicked(person: PersonSummary) {
        _state.value = _state.value.copy(archiveCandidate = person)
    }

    fun onArchiveDismissed() {
        _state.value = _state.value.copy(archiveCandidate = null)
    }

    fun onArchiveConfirmed() {
        val person = _state.value.archiveCandidate ?: return
        val now = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { personRepository.archive(person.id, archivedAt = now) }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(archiveCandidate = null)
                    refreshPeople()
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

    fun onFormChanged(form: PersonFormState) {
        _state.value = _state.value.copy(form = form)
    }

    fun onFormDismissed() {
        _state.value = _state.value.copy(form = null)
    }

    fun onExternalSplitFormChanged(form: ExternalSplitFormState) {
        _state.value = _state.value.copy(
            externalSplitForm = form.copy(errorRes = null, errorMessage = null),
        )
    }

    fun onExternalSplitDismissed() {
        _state.value = _state.value.copy(externalSplitForm = null)
    }

    fun onExternalSplitSaveClicked() {
        val form = _state.value.externalSplitForm ?: return
        val total = parseEuroCents(form.totalAmount, allowNegative = false)
        val userShare = parseEuroCents(form.userShare, allowNegative = false)
        val date = parseDate(form.date)
        val category = form.categoryId?.let { categoryId ->
            _state.value.categories.firstOrNull { it.id == categoryId }
        }

        val errorRes = when {
            total == null -> R.string.movement_validation_amount_required
            total <= 0L -> R.string.split_validation_total_positive
            userShare == null -> R.string.split_validation_user_share_required
            userShare > total -> R.string.split_validation_user_share_not_over_total
            form.date.isBlank() -> R.string.movement_validation_date_required
            date == null -> R.string.movement_validation_date_invalid
            category != null && !category.supportsExpense -> R.string.movement_validation_category_invalid
            else -> null
        }

        if (errorRes != null) {
            _state.value = _state.value.copy(externalSplitForm = form.copy(errorRes = errorRes))
            return
        }

        val now = Instant.now().toString()
        val draft = ExternalSplitDraft(
            id = UUID.randomUUID().toString(),
            payerPersonId = form.payerPersonId,
            totalAmountCents = requireNotNull(total),
            userShareCents = requireNotNull(userShare),
            date = requireNotNull(date).toString(),
            description = form.description.trim().ifBlank { null },
            categoryId = form.categoryId,
        )

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { splitRepository.createExternalPaidByPerson(draft, createdAt = now) }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(externalSplitForm = null)
                    refreshPeople()
                    refreshNotifications()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        externalSplitForm = form.copy(errorMessage = it.message ?: it.javaClass.simpleName),
                    )
                },
            )
        }
    }

    fun onSaveClicked() {
        val form = _state.value.form ?: return
        val name = form.name.trim()
        val notes = form.notes.trim().ifBlank { null }

        if (name.isEmpty()) {
            _state.value = _state.value.copy(
                form = form.copy(errorRes = R.string.person_validation_name_required),
            )
            return
        }

        val now = Instant.now().toString()
        val draft = PersonDraft(
            id = form.id ?: UUID.randomUUID().toString(),
            name = name,
            avatar = null,
            color = null,
            notes = notes,
        )

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    if (form.id == null) {
                        personRepository.create(draft, createdAt = now)
                    } else {
                        personRepository.update(draft, updatedAt = now)
                    }
                }
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(form = null)
                    refreshPeople()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        form = form.copy(errorMessage = it.message ?: it.javaClass.simpleName),
                    )
                },
            )
        }
    }

    private fun refreshPeople() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val result = withContext(ioDispatcher) {
                runCatching {
                    LoadedPeopleData(
                        people = personRepository.listActive(),
                        categories = categoryRepository.listActive(),
                        accounts = accountRepository.listActive(),
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        people = it.people,
                        categories = it.categories,
                        accounts = it.accounts,
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
        private val personRepository: PersonRepository,
        private val categoryRepository: CategoryRepository,
        private val splitRepository: SplitRepository,
        private val movementRepository: MovementRepository,
        private val accountRepository: AccountRepository,
        private val notificationRefresher: NotificationRefresher = NotificationRefresher.NoOp,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PeopleViewModel::class.java)) {
                return PeopleViewModel(
                    personRepository = personRepository,
                    categoryRepository = categoryRepository,
                    splitRepository = splitRepository,
                    movementRepository = movementRepository,
                    accountRepository = accountRepository,
                    notificationRefresher = notificationRefresher,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class PeopleUiState(
    val people: List<PersonSummary> = emptyList(),
    val categories: List<CategoryRecord> = emptyList(),
    val accounts: List<AccountSummary> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: PersonFormState? = null,
    val externalSplitForm: ExternalSplitFormState? = null,
    val settlementForm: SettlementFormState? = null,
    val detail: PersonDetailState? = null,
    val archiveCandidate: PersonSummary? = null,
)

data class PersonDetailState(
    val person: PersonSummary,
    val items: List<PersonBalanceItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class PersonFormState(
    val id: String? = null,
    val name: String = "",
    val notes: String = "",
    val errorRes: Int? = null,
    val errorMessage: String? = null,
)

data class ExternalSplitFormState(
    val payerPersonId: String,
    val payerPersonName: String,
    val totalAmount: String = "",
    val userShare: String = "",
    val date: String = "",
    val description: String = "",
    val categoryId: String? = null,
    val errorRes: Int? = null,
    val errorMessage: String? = null,
)

data class SettlementFormState(
    val personId: String,
    val personName: String,
    val outstandingCents: Long,
    val direction: SettlementDirection,
    val amount: String = "",
    val accountId: String? = null,
    val date: String = "",
    val notes: String = "",
    val errorRes: Int? = null,
    val errorMessage: String? = null,
)

private data class LoadedPeopleData(
    val people: List<PersonSummary>,
    val categories: List<CategoryRecord>,
    val accounts: List<AccountSummary>,
)

val PeopleUiState.totalOwedToUserCents: Long
    get() = people.filter { it.balanceCents > 0L }.sumOf { it.balanceCents }

val PeopleUiState.totalUserOwesCents: Long
    get() = people.filter { it.balanceCents < 0L }.sumOf { -it.balanceCents }

val PeopleUiState.netBalanceCents: Long
    get() = people.sumOf { it.balanceCents }

private fun PersonSummary.toFormState(): PersonFormState =
    PersonFormState(
        id = id,
        name = name,
        notes = notes.orEmpty(),
    )

private val CategoryRecord.supportsExpense: Boolean
    get() = kind == CategoryKind.EXPENSE || kind == CategoryKind.BOTH

private fun parseDate(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }
