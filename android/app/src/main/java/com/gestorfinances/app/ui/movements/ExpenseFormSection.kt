package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.layout.fillMaxWidth
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
 * The EXPENSE body of the movement form: the 4-type cascade ("Qui ha pagat?" → "Per a qui?").
 * Optional trip, recurrence, and advanced details are disclosed by [FormOptionalSection].
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
    val sharedEnabled = form.splitEditor != null || (form.existingSplit && !form.removeExistingSplit)

    // Level 1: Qui ha pagat?
    LabeledSegmentedControl(
        label = stringResource(R.string.movement_whopaid_title),
        options = listOf(true, false),
        selected = form.expenseKind != ExpenseKind.DEBT,
        optionLabel = { isMe ->
            if (isMe) stringResource(R.string.movement_whopaid_me)
            else stringResource(R.string.movement_whopaid_other)
        },
        onSelect = { isMe ->
            if (isMe) {
                onFormChange(form.copy(expenseKind = ExpenseKind.PERSONAL, forOtherPersonId = null))
            } else {
                onFormChange(form.copy(expenseKind = ExpenseKind.DEBT))
            }
        },
    )
    val personError = form.errorField == MovementFormField.PERSON
    val personErrorText = if (personError && form.errorRes != null) stringResource(form.errorRes) else null
    if (form.expenseKind == ExpenseKind.DEBT) {
        // Una altra persona paid: payer person picker
        FormSelect(
            label = stringResource(R.string.movement_debt_payer),
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
    } else {
        // Jo paid: account + level 2
        val accountError = form.errorField == MovementFormField.ACCOUNT
        AccountSelect(
            label = stringResource(R.string.movement_field_account),
            selectedId = form.accountId,
            accounts = accounts,
            onSelect = { onFormChange(form.copy(accountId = it)) },
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(accountError),
            isError = accountError,
            supportingText = if (accountError && form.errorRes != null) stringResource(form.errorRes) else null,
        )
        // Level 2: Per a qui?
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
        // FOR_OTHER: beneficiary person picker
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
        // SHARED: split editor or "unchanged" banner
        if (form.expenseKind == ExpenseKind.SHARED) {
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
