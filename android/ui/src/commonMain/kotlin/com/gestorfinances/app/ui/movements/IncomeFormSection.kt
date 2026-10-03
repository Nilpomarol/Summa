package com.gestorfinances.app.ui.movements

import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.movement_income_counted
import com.gestorfinances.ui.resources.movement_income_owner_help
import com.gestorfinances.ui.resources.movement_income_owner_member
import com.gestorfinances.ui.resources.movement_income_owner_mine
import com.gestorfinances.ui.resources.movement_income_owner_other
import com.gestorfinances.ui.resources.movement_income_owner_placeholder
import com.gestorfinances.ui.resources.movement_income_owner_shared
import com.gestorfinances.ui.resources.movement_income_owner_title
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.scrollToWhen

/**
 * Income asks whose money it is only when the selected account is shared.
 */
@Composable
fun IncomeOwnershipDetails(
    form: MovementFormState,
    accounts: List<AccountSummary>,
    people: List<PersonSummary>,
    onFormChange: (MovementFormState) -> Unit,
    onSharedToggled: (Boolean) -> Unit,
    onSplitEditorChange: (SplitEditorState) -> Unit,
    onOtherPersonSelected: (String?) -> Unit,
    onCreatePersonInSplit: (String) -> Unit,
) {
    val account = accounts.firstOrNull { it.id == form.accountId }
    if (account?.ownershipKind == AccountOwnershipKind.SHARED) {
        IncomeOwnershipSection(
            owner = form.expenseKind,
            amount = form.amount,
            account = account,
            people = people,
            otherPersonId = form.forOtherPersonId,
            splitEditor = form.splitEditor,
            ownerErrorText = form.errorRes?.takeIf { form.errorField == MovementFormField.INCOME_OWNER }?.let { stringResource(it) },
            personErrorText = form.errorRes?.takeIf { form.errorField == MovementFormField.PERSON }?.let { stringResource(it) },
            splitError = form.errorField == MovementFormField.SPLIT,
            onOwnerSelected = { owner ->
                when (owner) {
                    ExpenseKind.PERSONAL -> onSharedToggled(false)
                    // Through the view model, so an income saved as the owner's gets an editor to fill.
                    ExpenseKind.SHARED -> onSharedToggled(true)
                    ExpenseKind.FOR_OTHER -> onFormChange(form.copy(expenseKind = ExpenseKind.FOR_OTHER))
                    ExpenseKind.DEBT -> Unit
                }
            },
            onOtherPersonSelected = onOtherPersonSelected,
            onSplitEditorChange = onSplitEditorChange,
            onCreatePersonInSplit = onCreatePersonInSplit,
        )
    }
}

/**
 * Whose income a deposit into a shared account is: the owner's ([ExpenseKind.PERSONAL]), shared
 * ([ExpenseKind.SHARED], allocated by [splitEditor]), or another member's ([ExpenseKind.FOR_OTHER]).
 * Nothing is chosen until the user answers, because landing in a shared account never decides it;
 * none of the answers creates debt between members. The movement form and the recurring template
 * form both ask it here.
 */
@Composable
fun IncomeOwnershipSection(
    owner: ExpenseKind?,
    amount: String,
    account: AccountSummary,
    people: List<PersonSummary>,
    otherPersonId: String?,
    splitEditor: SplitEditorState?,
    ownerErrorText: String?,
    personErrorText: String?,
    splitError: Boolean,
    onOwnerSelected: (ExpenseKind) -> Unit,
    onOtherPersonSelected: (String?) -> Unit,
    onSplitEditorChange: (SplitEditorState) -> Unit,
    onCreatePersonInSplit: (String) -> Unit,
) {
    val ownerError = ownerErrorText != null
    FormSelect(
        label = stringResource(Res.string.movement_income_owner_title),
        options = listOf(
            SelectOption(id = ExpenseKind.PERSONAL.name, label = stringResource(Res.string.movement_income_owner_mine)),
            SelectOption(id = ExpenseKind.SHARED.name, label = stringResource(Res.string.movement_income_owner_shared)),
            SelectOption(id = ExpenseKind.FOR_OTHER.name, label = stringResource(Res.string.movement_income_owner_other)),
        ),
        selectedId = owner?.name,
        onSelect = { id -> id?.let(ExpenseKind::valueOf)?.let(onOwnerSelected) },
        placeholder = stringResource(Res.string.movement_income_owner_placeholder),
        modifier = Modifier.scrollToWhen(ownerError),
        isError = ownerError,
        supportingText = ownerErrorText,
    )
    Text(
        text = stringResource(Res.string.movement_income_owner_help),
        style = MaterialTheme.typography.bodySmall,
        color = FinanceTheme.colors.mutedText,
    )
    val amountCents = parseEuroCents(amount, allowNegative = false)
    when (owner) {
        ExpenseKind.PERSONAL -> if (amountCents != null) {
            Text(
                text = stringResource(Res.string.movement_income_counted, formatEuroCents(amountCents)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        ExpenseKind.FOR_OTHER -> {
            val memberIds = account.members.mapNotNull { it.personId }.toSet()
            val personError = personErrorText != null
            FormSelect(
                label = stringResource(Res.string.movement_income_owner_member),
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
                selectedId = otherPersonId,
                onSelect = onOtherPersonSelected,
                modifier = Modifier.scrollToWhen(personError),
                isError = personError,
                supportingText = personErrorText,
            )
            Text(
                text = stringResource(Res.string.movement_income_counted, formatEuroCents(0L)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        ExpenseKind.SHARED -> splitEditor?.let { editor ->
            SplitEditorCard(
                splitEditor = editor,
                people = people,
                amountInput = amount,
                onChange = onSplitEditorChange,
                onCreatePerson = onCreatePersonInSplit,
                accountIncome = true,
                modifier = Modifier.scrollToWhen(splitError),
            )
        }
        ExpenseKind.DEBT, null -> Unit
    }
}
