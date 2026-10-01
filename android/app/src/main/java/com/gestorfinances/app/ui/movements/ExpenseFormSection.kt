package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.theme.FinanceTheme

private const val PERSON_OPTION_PREFIX = "person:"

private fun personOptionId(personId: String) = PERSON_OPTION_PREFIX + personId

private fun personOptions(people: List<PersonSummary>): List<SelectOption> = people.map { person ->
    SelectOption(
        id = personOptionId(person.id),
        label = person.name,
        leading = { PersonMonogram(personInitial(person.name), person.color, size = 24.dp) },
    )
}

private const val PAID_BY_ME = "me"

/**
 * The account an expense is paid from. When a person paid instead (chosen in "Qui ha pagat" under
 * Més detalls), the field stays in place, locked, and names the payer, so the change shows here too.
 */
@Composable
internal fun ExpenseAccountField(
    form: MovementFormState,
    accounts: List<AccountSummary>,
    people: List<PersonSummary>,
    fundedBySharedAccount: Boolean,
    onFormChange: (MovementFormState) -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        if (fundedBySharedAccount) R.string.movement_field_paid_from else R.string.movement_field_account,
    )
    if (form.expenseKind == ExpenseKind.DEBT) {
        val payer = people.firstOrNull { it.id == form.forOtherPersonId }?.name ?: "—"
        FieldFrame(
            label = label,
            focused = false,
            modifier = modifier,
            surfaceModifier = Modifier.clickable(onClick = onOpenDetails),
        ) {
            Text(
                text = stringResource(R.string.movement_paid_by_person, payer),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }
    val isError = form.errorField == MovementFormField.ACCOUNT
    AccountSelect(
        label = label,
        selectedId = form.accountId,
        accounts = accounts,
        onSelect = { onFormChange(form.copy(accountId = it)) },
        modifier = modifier.scrollToWhen(isError),
        isError = isError,
        supportingText = if (isError && form.errorRes != null) stringResource(form.errorRes) else null,
    )
}

/** Who paid: the owner, from the account, or a person, who then holds the expense in their name. */
@Composable
private fun ExpensePayerField(
    form: MovementFormState,
    people: List<PersonSummary>,
    onFormChange: (MovementFormState) -> Unit,
    onOtherPersonSelected: (String?) -> Unit,
) {
    val paidByPerson = form.expenseKind == ExpenseKind.DEBT
    val isError = paidByPerson && form.errorField == MovementFormField.PERSON
    FormSelect(
        label = stringResource(R.string.split_payer_title),
        options = listOf(SelectOption(id = PAID_BY_ME, label = stringResource(R.string.split_payer_user))) +
            personOptions(people),
        selectedId = if (paidByPerson) form.forOtherPersonId?.let(::personOptionId) else PAID_BY_ME,
        onSelect = { id ->
            when {
                id == null -> Unit
                id == PAID_BY_ME -> if (paidByPerson) onFormChange(form.copy(expenseKind = ExpenseKind.PERSONAL))
                else -> {
                    onFormChange(form.copy(expenseKind = ExpenseKind.DEBT))
                    onOtherPersonSelected(id.removePrefix(PERSON_OPTION_PREFIX))
                }
            }
        },
        modifier = Modifier.fillMaxWidth().scrollToWhen(isError),
        isError = isError,
        supportingText = if (isError && form.errorRes != null) stringResource(form.errorRes) else null,
    )
}

/**
 * Whose expense it is. Paid by the owner: only theirs, shared, or all for someone else. Paid by a
 * person: the owner's part is all of it or a split with the payer.
 */
@Composable
internal fun ExpenseShareRows(
    form: MovementFormState,
    people: List<PersonSummary>,
    fundedBySharedAccount: Boolean,
    onFormChange: (MovementFormState) -> Unit,
    onSharedToggled: (Boolean) -> Unit,
    onPayerSplitToggled: (Boolean) -> Unit,
    onSplitEditorChange: (SplitEditorState) -> Unit,
    onOtherPersonSelected: (String?) -> Unit,
    onCreatePersonInSplit: (String) -> Unit,
) {
    val personError = form.errorField == MovementFormField.PERSON
    val splitError = form.errorField == MovementFormField.SPLIT
    val sharedLabel = stringResource(R.string.movement_forwhom_shared)
    val splitCard: @Composable () -> Unit = {
        form.splitEditor?.let { editor ->
            SplitEditorCard(
                splitEditor = editor,
                people = people,
                amountInput = form.amount,
                fundedByAccount = fundedBySharedAccount,
                onChange = onSplitEditorChange,
                onCreatePerson = onCreatePersonInSplit,
                lockedIds = setOfNotNull(form.forOtherPersonId.takeIf { form.expenseKind == ExpenseKind.DEBT }),
                modifier = Modifier.scrollToWhen(splitError),
            )
        }
    }

    // A shared account always pays its own expense, so only the owner's accounts ask who paid.
    if (!fundedBySharedAccount) ExpensePayerField(form, people, onFormChange, onOtherPersonSelected)
    when {
        form.expenseKind == ExpenseKind.DEBT -> {
            FormSelect(
                label = stringResource(R.string.movement_my_share_label),
                options = listOf(
                    SelectOption(id = MY_SHARE_ALL, label = stringResource(R.string.movement_my_share_all)),
                    SelectOption(id = MY_SHARE_SPLIT, label = sharedLabel),
                ),
                selectedId = if (form.payerSplit) MY_SHARE_SPLIT else MY_SHARE_ALL,
                onSelect = { id -> onPayerSplitToggled(id == MY_SHARE_SPLIT) },
            )
            if (form.payerSplit) splitCard()
        }
        // A shared account always finances its own expense and the database requires the split
        // that says who consumed it, so the choice isn't the user's to make here.
        fundedBySharedAccount -> {
            InlineBanner(kind = BannerKind.Info, text = stringResource(R.string.movement_shared_account_funding))
            splitCard()
        }
        else -> {
            val kind = form.expenseKind ?: ExpenseKind.PERSONAL
            ForWhomFields(
                kind = kind,
                otherPersonId = form.forOtherPersonId,
                people = people,
                amountInput = form.amount,
                onKindSelected = { selected ->
                    when (selected) {
                        ExpenseKind.SHARED -> onSharedToggled(true)
                        ExpenseKind.FOR_OTHER -> onFormChange(form.copy(expenseKind = ExpenseKind.FOR_OTHER))
                        else -> onSharedToggled(false)
                    }
                },
                onOtherPersonSelected = onOtherPersonSelected,
                modifier = Modifier.scrollToWhen(personError),
                errorText = form.errorRes?.takeIf { personError }?.let { stringResource(it) },
            )
            if (kind == ExpenseKind.SHARED) {
                val editor = form.splitEditor
                if (editor != null) {
                    SplitEditorCard(
                        splitEditor = editor,
                        people = people,
                        amountInput = form.amount,
                        onChange = onSplitEditorChange,
                        onCreatePerson = onCreatePersonInSplit,
                        modifier = Modifier.scrollToWhen(splitError),
                    )
                } else if (form.existingSplit && !form.removeExistingSplit) {
                    InlineBanner(kind = BannerKind.Info, text = stringResource(R.string.movement_existing_split_unchanged))
                }
            }
        }
    }
}

private const val MY_SHARE_ALL = "all"
private const val MY_SHARE_SPLIT = "split"
