package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.scrollToWhen

/**
 * The TRANSFER body: origin and destination side by side.
 * Transfers are never categorized or shared.
 */
@Composable
internal fun TransferAccountFields(
    form: MovementFormState,
    accounts: List<AccountSummary>,
    onFormChange: (MovementFormState) -> Unit,
) {
    val accountError = form.errorField == MovementFormField.ACCOUNT
    val destinationError = form.errorField == MovementFormField.DESTINATION_ACCOUNT
    val errorText = if (form.errorRes != null) stringResource(form.errorRes) else null
    val source = accounts.firstOrNull { it.id == form.accountId }
    val destinations = accounts.filter { it.id != form.accountId && it.ownershipKind == source?.ownershipKind }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FormSelect(
            label = stringResource(R.string.movement_field_origin_account),
            options = accountOptions(accounts),
            selectedId = form.accountId,
            onSelect = { id -> id?.let { onFormChange(form.copy(accountId = it)) } },
            modifier = Modifier.weight(1f).scrollToWhen(accountError),
            isError = accountError,
            supportingText = if (accountError) errorText else null,
        )
        FormSelect(
            label = stringResource(R.string.movement_field_destination_account),
            options = accountOptions(destinations),
            selectedId = form.destinationAccountId,
            onSelect = { id -> id?.let { onFormChange(form.copy(destinationAccountId = it)) } },
            modifier = Modifier.weight(1f).scrollToWhen(destinationError),
            isError = destinationError,
            supportingText = if (destinationError) errorText else null,
        )
    }
    if (source != null && destinations.isEmpty()) {
        InlineBanner(
            kind = BannerKind.Info,
            text = stringResource(
                if (accounts.any { it.ownershipKind != source.ownershipKind }) {
                    R.string.movement_transfer_use_shared_account
                } else {
                    R.string.movement_transfer_needs_second_account
                },
            ),
        )
    }
}
