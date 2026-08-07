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
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.scrollToWhen

/**
 * The INCOME body: account, then the settlement toggle + settlement person
 * (a settlement records money received against a person's debt). Optional metadata is disclosed
 * by [FormOptionalSection].
 */
@Composable
internal fun IncomeFormSection(
    form: MovementFormState,
    accounts: List<AccountSummary>,
    people: List<PersonSummary>,
    onFormChange: (MovementFormState) -> Unit,
    onSettlementToggled: (Boolean) -> Unit,
    onSettlementPersonSelected: (String?) -> Unit,
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
}
