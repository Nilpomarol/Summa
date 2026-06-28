package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.domain.rules.RecurrenceFrequency

/**
 * The TRANSFER body: origin + destination account row, then the recurring tail.
 * Transfers are never categorized or shared (spec §3.5).
 */
@Composable
internal fun TransferFormSection(
    form: MovementFormState,
    accounts: List<AccountSummary>,
    onFormChange: (MovementFormState) -> Unit,
    onRecurringToggled: (Boolean) -> Unit,
    onRecurringFrequencyChanged: (RecurrenceFrequency) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AccountSelect(
            label = stringResource(R.string.movement_field_origin_account),
            selectedId = form.accountId,
            accounts = accounts,
            onSelect = { onFormChange(form.copy(accountId = it)) },
            modifier = Modifier.weight(1f),
        )
        AccountSelect(
            label = stringResource(R.string.movement_field_destination_account),
            selectedId = form.destinationAccountId,
            accounts = accounts.filter { it.id != form.accountId },
            onSelect = { onFormChange(form.copy(destinationAccountId = it)) },
            modifier = Modifier.weight(1f),
        )
    }

    FormRecurringSection(
        isRecurring = form.isRecurring,
        frequency = form.recurringFrequency,
        onToggle = onRecurringToggled,
        onFrequencyChange = onRecurringFrequencyChanged,
    )
}
