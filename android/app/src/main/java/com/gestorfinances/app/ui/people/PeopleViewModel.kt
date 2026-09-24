package com.gestorfinances.app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountRepository
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.MovementRepository
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.PersonBalanceItem
import com.gestorfinances.app.data.repository.PersonDraft
import com.gestorfinances.app.data.repository.PersonRepository
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.data.repository.SettlementDraft
import com.gestorfinances.app.domain.rules.SettlementScope
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
            settlementForm = form.copy(errorRes = null, errorField = null, errorMessage = null),
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

        val (errorRes, errorField) = when {
            amount == null -> R.string.movement_validation_amount_required to SettlementFormField.AMOUNT
            amount <= 0L -> R.string.movement_validation_amount_positive to SettlementFormField.AMOUNT
            account == null -> R.string.settlement_validation_account_required to SettlementFormField.ACCOUNT
            form.date.isBlank() -> R.string.movement_validation_date_required to SettlementFormField.DATE
            date == null -> R.string.movement_validation_date_invalid to SettlementFormField.DATE
            else -> null to null
        }

        if (errorRes != null) {
            _state.value = _state.value.copy(
                settlementForm = form.copy(errorRes = errorRes, errorField = errorField),
            )
            return
        }

        val now = Instant.now().toString()
        val draft = SettlementDraft(
            id = UUID.randomUUID().toString(),
            personId = form.personId,
            direction = form.direction,
            scope = form.scope,
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
                    // The settlement form is a local swap on top of the person-detail page (not a
                    // separate destination), so saving reveals that page again -- reload it so the
                    // balance it shows reflects the settlement just recorded, instead of the value
                    // from before this save.
                    _state.value.detail?.person?.takeIf { it.id == form.personId }
                        ?.let { onPersonDetailClicked(it) }
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
                    val items = personRepository.balanceItemsForPerson(person.id)
                    val history = items.mapNotNull { item ->
                        movementRepository.getActive(item.sourceId)?.let { movement ->
                            PersonHistoryEntry(item, movement)
                        }
                    }
                    PersonDetailState(
                        person = currentPerson,
                        items = items,
                        history = history,
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

    fun onCopyMessageClicked() {
        val detail = _state.value.detail ?: return
        val message = buildPersonDebtMessage(detail.items, detail.person.balanceCents)
        _state.value = _state.value.copy(detail = detail.copy(copyMessage = message))
    }

    fun onCopyMessageHandled() {
        val detail = _state.value.detail ?: return
        _state.value = _state.value.copy(detail = detail.copy(copyMessage = null))
    }

    fun onArchiveClicked(person: PersonSummary) {
        _state.value = _state.value.copy(archiveCandidate = person)
    }

    fun onArchiveDismissed() {
        _state.value = _state.value.copy(archiveCandidate = null)
    }

    fun onArchiveConfirmed(onSuccess: (undo: () -> Unit) -> Unit = {}) {
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
                    onSuccess { undoDelete(person.id, deletedAt = now) }
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

    private fun undoDelete(personId: String, deletedAt: String) {
        val restoredAt = Instant.now().toString()
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { personRepository.restore(personId, deletedAt, restoredAt) }
            }
            result.fold(
                onSuccess = {
                    refreshPeople()
                    refreshNotifications()
                },
                onFailure = { _state.value = _state.value.copy(errorMessage = it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    fun onFormChanged(form: PersonFormState) {
        _state.value = _state.value.copy(form = form)
    }

    fun onFormDismissed() {
        _state.value = _state.value.copy(form = null)
    }

    fun onSaveClicked() {
        val form = _state.value.form ?: return
        val name = form.name.trim()
        val notes = form.notes.trim().ifBlank { null }

        if (name.isEmpty()) {
            _state.value = _state.value.copy(
                form = form.copy(
                    errorRes = R.string.person_validation_name_required,
                    errorField = PersonFormField.NAME,
                ),
            )
            return
        }

        val now = Instant.now().toString()
        val draft = PersonDraft(
            id = form.id ?: UUID.randomUUID().toString(),
            name = name,
            avatar = null,
            color = form.color,
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
                        accounts = accountRepository.listActive(),
                    )
                }
            }
            _state.value = result.fold(
                onSuccess = {
                    _state.value.copy(
                        people = it.people,
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
}

data class PeopleUiState(
    val people: List<PersonSummary> = emptyList(),
    val accounts: List<AccountSummary> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val form: PersonFormState? = null,
    val settlementForm: SettlementFormState? = null,
    val detail: PersonDetailState? = null,
    val archiveCandidate: PersonSummary? = null,
)

data class PersonHistoryEntry(
    val item: PersonBalanceItem,
    val movement: MovementSummary,
)

data class PersonDetailState(
    val person: PersonSummary,
    val items: List<PersonBalanceItem> = emptyList(),
    val history: List<PersonHistoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val copyMessage: PersonDebtMessage? = null,
)

/** Identifies which field a person-form validation error belongs to (field-level validation). */
enum class PersonFormField {
    NAME,
}

data class PersonFormState(
    val id: String? = null,
    val name: String = "",
    val notes: String = "",
    val color: String? = null,
    val errorRes: Int? = null,
    val errorField: PersonFormField? = null,
    val errorMessage: String? = null,
)

/** Identifies which field a settlement-form validation error belongs to (field-level validation). */
enum class SettlementFormField {
    AMOUNT,
    ACCOUNT,
    DATE,
}

data class SettlementFormState(
    val personId: String,
    val personName: String,
    val outstandingCents: Long,
    val direction: SettlementDirection,
    val scope: SettlementScope = SettlementScope.ALL,
    val amount: String = "",
    val accountId: String? = null,
    val date: String = "",
    val notes: String = "",
    val errorRes: Int? = null,
    val errorField: SettlementFormField? = null,
    val errorMessage: String? = null,
)

private data class LoadedPeopleData(
    val people: List<PersonSummary>,
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
        color = color,
    )

private fun parseDate(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }
