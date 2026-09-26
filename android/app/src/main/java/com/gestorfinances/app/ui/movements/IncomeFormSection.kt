package com.gestorfinances.app.ui.movements

import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.scrollToWhen

/**
 * The INCOME body: account, then the settlement toggle + settlement person
 * (a settlement records money received against a person's debt), and on a shared account whose
 * income it is. Optional metadata is disclosed by [FormOptionalSection].
 */
@Composable
internal fun IncomeFormSection(
    form: MovementFormState,
    accounts: List<AccountSummary>,
    people: List<PersonSummary>,
    onFormChange: (MovementFormState) -> Unit,
    onSettlementToggled: (Boolean) -> Unit,
    onSettlementPersonSelected: (String?) -> Unit,
    onSharedToggled: (Boolean) -> Unit,
    onSplitEditorChange: (SplitEditorState) -> Unit,
    onOtherPersonSelected: (String?) -> Unit,
    onCreatePersonInSplit: (String) -> Unit,
) {
    val accountError = form.errorField == MovementFormField.ACCOUNT
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AccountSelect(
            label = stringResource(R.string.movement_field_account),
            selectedId = form.accountId,
            accounts = accounts,
            onSelect = { onFormChange(form.copy(accountId = it)) },
            modifier = Modifier.weight(1.3f).scrollToWhen(accountError),
            isError = accountError,
            supportingText = if (accountError && form.errorRes != null) stringResource(form.errorRes) else null,
        )
        // A settlement is recorded as a new movement, so an edited income is never turned into one.
        if (form.isNew) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 22.dp),
            ) {
                FormToggleRow(
                    label = stringResource(R.string.movement_field_settlement),
                    checked = form.isSettlement,
                    onCheckedChange = onSettlementToggled,
                )
            }
        }
    }
    if (form.isSettlement) {
        val personError = form.errorField == MovementFormField.PERSON
        FormSelect(
            label = stringResource(R.string.settlement_field_person),
            options = people.map { person ->
                SelectOption(
                    id = person.id,
                    label = person.name,
                    leading = {
                        PersonMonogram(
                            label = personInitial(person.name),
                            colorHex = person.color,
                            size = 24.dp,
                        )
                    },
                )
            },
            selectedId = form.settlementPersonId,
            onSelect = onSettlementPersonSelected,
            modifier = Modifier.scrollToWhen(personError),
            isError = personError,
            supportingText = if (personError && form.errorRes != null) stringResource(form.errorRes) else null,
        )
        // This quick toggle always records a person-to-user settlement, so the outstanding debt
        // is what the person actually owes (never-block invariant: warn, don't stop, on overpay --
        // mirrors PeopleScreen's dedicated SettlementSheet).
        val selectedPerson = people.firstOrNull { it.id == form.settlementPersonId }
        val outstandingCents = selectedPerson?.balanceCents?.coerceAtLeast(0L)
        val parsedAmount = parseEuroCents(form.amount, allowNegative = false)
        if (outstandingCents != null && parsedAmount != null && parsedAmount > outstandingCents) {
            InlineBanner(
                kind = BannerKind.Alert,
                text = stringResource(R.string.settlement_warning_overpay),
            )
        }
    }
    val account = accounts.firstOrNull { it.id == form.accountId }
    if (!form.isSettlement && account?.ownershipKind == AccountOwnershipKind.SHARED) {
        IncomeOwnershipSection(
            form = form,
            account = account,
            people = people,
            onFormChange = onFormChange,
            onSharedToggled = onSharedToggled,
            onSplitEditorChange = onSplitEditorChange,
            onOtherPersonSelected = onOtherPersonSelected,
            onCreatePersonInSplit = onCreatePersonInSplit,
        )
    }
}

/**
 * Whose income a deposit into a shared account is. Nothing is chosen until the user answers, because
 * landing in a shared account never decides it; none of the answers creates debt between members.
 */
@Composable
private fun IncomeOwnershipSection(
    form: MovementFormState,
    account: AccountSummary,
    people: List<PersonSummary>,
    onFormChange: (MovementFormState) -> Unit,
    onSharedToggled: (Boolean) -> Unit,
    onSplitEditorChange: (SplitEditorState) -> Unit,
    onOtherPersonSelected: (String?) -> Unit,
    onCreatePersonInSplit: (String) -> Unit,
) {
    val ownerError = form.errorField == MovementFormField.INCOME_OWNER
    FormSelect(
        label = stringResource(R.string.movement_income_owner_title),
        options = listOf(
            SelectOption(id = ExpenseKind.PERSONAL.name, label = stringResource(R.string.movement_income_owner_mine)),
            SelectOption(id = ExpenseKind.SHARED.name, label = stringResource(R.string.movement_income_owner_shared)),
            SelectOption(id = ExpenseKind.FOR_OTHER.name, label = stringResource(R.string.movement_income_owner_other)),
        ),
        selectedId = form.expenseKind?.name,
        onSelect = { id ->
            when (id?.let(ExpenseKind::valueOf)) {
                ExpenseKind.PERSONAL -> onSharedToggled(false)
                // Through the view model, so an income saved as the owner's gets an editor to fill.
                ExpenseKind.SHARED -> onSharedToggled(true)
                ExpenseKind.FOR_OTHER -> onFormChange(form.copy(expenseKind = ExpenseKind.FOR_OTHER))
                ExpenseKind.DEBT, null -> Unit
            }
        },
        placeholder = stringResource(R.string.movement_income_owner_placeholder),
        modifier = Modifier.scrollToWhen(ownerError),
        isError = ownerError,
        supportingText = if (ownerError && form.errorRes != null) stringResource(form.errorRes) else null,
    )
    Text(
        text = stringResource(R.string.movement_income_owner_help),
        style = MaterialTheme.typography.bodySmall,
        color = FinanceTheme.colors.mutedText,
    )
    val amountCents = parseEuroCents(form.amount, allowNegative = false)
    when (form.expenseKind) {
        ExpenseKind.PERSONAL -> if (amountCents != null) {
            Text(
                text = stringResource(R.string.movement_income_counted, formatEuroCents(amountCents)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        ExpenseKind.FOR_OTHER -> {
            val memberIds = account.members.mapNotNull { it.personId }.toSet()
            val personError = form.errorField == MovementFormField.PERSON
            FormSelect(
                label = stringResource(R.string.movement_income_owner_member),
                options = people.filter { it.id in memberIds }.map { person ->
                    SelectOption(
                        id = person.id,
                        label = person.name,
                        leading = {
                            PersonMonogram(
                                label = personInitial(person.name),
                                colorHex = person.color,
                                size = 24.dp,
                            )
                        },
                    )
                },
                selectedId = form.forOtherPersonId,
                onSelect = onOtherPersonSelected,
                modifier = Modifier.scrollToWhen(personError),
                isError = personError,
                supportingText = if (personError && form.errorRes != null) stringResource(form.errorRes) else null,
            )
            Text(
                text = stringResource(R.string.movement_income_counted, formatEuroCents(0L)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        ExpenseKind.SHARED -> form.splitEditor?.let { editor ->
            SplitEditorCard(
                splitEditor = editor,
                people = people,
                amountInput = form.amount,
                onChange = onSplitEditorChange,
                onCreatePerson = onCreatePersonInSplit,
                accountIncome = true,
                modifier = Modifier.scrollToWhen(form.errorField == MovementFormField.SPLIT),
            )
        }
        ExpenseKind.DEBT, null -> Unit
    }
}
