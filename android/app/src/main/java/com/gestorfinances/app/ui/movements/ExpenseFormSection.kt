package com.gestorfinances.app.ui.movements

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
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
import com.gestorfinances.app.ui.common.scrollToWhen

/**
 * The compact EXPENSE body. Payer and account/person share one row; sharing and claims are
 * disclosed from the common optional area so all three movement types have the same base body.
 *
 * The four [ExpenseKind]s map 1:1 to the user's mental model:
 * - [ExpenseKind.PERSONAL] — user paid, for self.
 * - [ExpenseKind.SHARED]   — user paid, split among participants (SplitEditorCard).
 * - [ExpenseKind.FOR_OTHER] — user paid on behalf of one person who owes 100%.
 * - [ExpenseKind.DEBT]     — someone else paid; user owes the amount (no account, no movement).
 */
@Composable
internal fun ExpenseFormSection(
    form: MovementFormState,
    accounts: List<AccountSummary>,
    people: List<PersonSummary>,
    onFormChange: (MovementFormState) -> Unit,
    onSharedToggled: (Boolean) -> Unit,
    onSplitEditorChange: (SplitEditorState) -> Unit,
    onOtherPersonSelected: (String?) -> Unit,
    onCreatePersonInSplit: (String) -> Unit,
) {
    val personError = form.errorField == MovementFormField.PERSON
    val personErrorText = if (personError && form.errorRes != null) stringResource(form.errorRes) else null
    val accountError = form.errorField == MovementFormField.ACCOUNT
    val accountErrorText = if (accountError && form.errorRes != null) stringResource(form.errorRes) else null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (form.expenseKind == ExpenseKind.DEBT) {
            FormSelect(
                label = stringResource(R.string.movement_debt_payer),
                options = people.map { person -> SelectOption(id = person.id, label = person.name, leading = {
                    PersonMonogram(personInitial(person.name), person.color, size = 24.dp)
                }) },
                selectedId = form.forOtherPersonId,
                onSelect = onOtherPersonSelected,
                modifier = Modifier.weight(1.3f).scrollToWhen(personError),
                isError = personError,
                supportingText = personErrorText,
            )
        } else {
            AccountSelect(
                label = stringResource(R.string.movement_field_account),
                selectedId = form.accountId,
                accounts = accounts,
                onSelect = { onFormChange(form.copy(accountId = it)) },
                modifier = Modifier.weight(1.3f).scrollToWhen(accountError),
                isError = accountError,
                supportingText = accountErrorText,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 22.dp),
        ) {
            FormToggleRow(
                label = stringResource(R.string.movement_whopaid_me_compact),
                checked = form.expenseKind != ExpenseKind.DEBT,
                onCheckedChange = { isMe ->
                    onFormChange(
                        if (isMe) form.copy(expenseKind = ExpenseKind.PERSONAL, forOtherPersonId = null)
                        else form.copy(expenseKind = ExpenseKind.DEBT),
                    )
                },
            )
        }
    }
}

/** Expense sharing and reimbursement choices, rendered after the common optional disclosure. */
@Composable
internal fun ExpenseDetailsSection(
    form: MovementFormState,
    people: List<PersonSummary>,
    onFormChange: (MovementFormState) -> Unit,
    onSharedToggled: (Boolean) -> Unit,
    onSplitEditorChange: (SplitEditorState) -> Unit,
    onOtherPersonSelected: (String?) -> Unit,
    onCreatePersonInSplit: (String) -> Unit,
) {
    val personError = form.errorField == MovementFormField.PERSON
    val personErrorText = if (personError && form.errorRes != null) stringResource(form.errorRes) else null
    if (form.expenseKind != ExpenseKind.DEBT) {
        LabeledSegmentedControl(
            label = stringResource(R.string.movement_forwhom_title),
            options = listOf(ExpenseKind.PERSONAL, ExpenseKind.SHARED, ExpenseKind.FOR_OTHER),
            selected = form.expenseKind ?: ExpenseKind.PERSONAL,
            optionLabel = { kind ->
                when (kind) {
                    ExpenseKind.PERSONAL -> stringResource(R.string.movement_forwhom_personal)
                    ExpenseKind.SHARED -> stringResource(R.string.movement_forwhom_shared)
                    ExpenseKind.FOR_OTHER -> stringResource(R.string.movement_forwhom_other)
                    ExpenseKind.DEBT -> ""
                }
            },
            onSelect = { kind ->
                when (kind) {
                    ExpenseKind.PERSONAL -> onSharedToggled(false)
                    ExpenseKind.SHARED -> onSharedToggled(true)
                    ExpenseKind.FOR_OTHER -> onFormChange(form.copy(expenseKind = ExpenseKind.FOR_OTHER))
                    ExpenseKind.DEBT -> Unit
                }
            },
        )
        if (form.expenseKind == ExpenseKind.FOR_OTHER) {
            FormSelect(
                label = stringResource(R.string.movement_forwhom_other),
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
                selectedId = form.forOtherPersonId,
                onSelect = onOtherPersonSelected,
                modifier = Modifier.scrollToWhen(personError),
                isError = personError,
                supportingText = personErrorText,
            )
        }
        if (form.expenseKind == ExpenseKind.SHARED) {
            val sharedEnabled = form.splitEditor != null || (form.existingSplit && !form.removeExistingSplit)
            val splitError = form.errorField == MovementFormField.SPLIT
            if (form.splitEditor != null) {
                SplitEditorCard(
                    splitEditor = form.splitEditor,
                    people = people,
                    amountInput = form.amount,
                    onChange = onSplitEditorChange,
                    onCreatePerson = onCreatePersonInSplit,
                    modifier = Modifier.scrollToWhen(splitError),
                )
            } else if (sharedEnabled) {
                InlineBanner(
                    kind = BannerKind.Info,
                    text = stringResource(R.string.movement_existing_split_unchanged),
                )
            }
        }
    }
}
