package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.domain.rules.RecurrenceFrequency

/**
 * The INCOME body: account, then either the settlement toggle + settlement person
 * (a settlement records money received against a person's debt) or, for plain income,
 * the recurring + trip/tag tail.
 */
@Composable
internal fun IncomeFormSection(
    form: MovementFormState,
    accounts: List<AccountSummary>,
    people: List<PersonSummary>,
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    onFormChange: (MovementFormState) -> Unit,
    onSettlementToggled: (Boolean) -> Unit,
    onSettlementPersonSelected: (String?) -> Unit,
    onRecurringToggled: (Boolean) -> Unit,
    onRecurringFrequencyChanged: (RecurrenceFrequency) -> Unit,
    onTripSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
) {
    AccountSelect(
        label = stringResource(R.string.movement_field_account),
        selectedId = form.accountId,
        accounts = accounts,
        onSelect = { onFormChange(form.copy(accountId = it)) },
        modifier = Modifier.fillMaxWidth(),
    )

    FormToggleRow(
        label = stringResource(R.string.movement_field_settlement),
        checked = form.isSettlement,
        onCheckedChange = onSettlementToggled,
    )
    if (form.isSettlement) {
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
        )
    } else {
        FormRecurringSection(
            isRecurring = form.isRecurring,
            frequency = form.recurringFrequency,
            onToggle = onRecurringToggled,
            onFrequencyChange = onRecurringFrequencyChanged,
        )

        FormTripTagSection(
            trips = trips,
            tags = tags,
            tripId = form.tripId,
            tagId = form.tagId,
            onTripSelected = onTripSelected,
            onTagSelected = onTagSelected,
        )
    }
}
