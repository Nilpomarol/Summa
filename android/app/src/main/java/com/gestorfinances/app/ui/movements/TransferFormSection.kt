package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.data.repository.ContributionDirection
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.scrollToWhen

/**
 * The TRANSFER body: origin + destination account row. Optional recurrence/details are disclosed
 * by [FormOptionalSection].
 * Transfers are never categorized or shared (product rule).
 */
@Composable
internal fun TransferFormSection(
    form: MovementFormState,
    accounts: List<AccountSummary>,
    onFormChange: (MovementFormState) -> Unit,
    onRecordContribution: (
        sharedAccountId: String,
        amount: String,
        ownerAccountId: String?,
        direction: ContributionDirection,
    ) -> Unit,
) {
    val accountError = form.errorField == MovementFormField.ACCOUNT
    val destinationError = form.errorField == MovementFormField.DESTINATION_ACCOUNT
    val errorText = if (form.errorRes != null) stringResource(form.errorRes) else null
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
            modifier = Modifier
                .weight(1f)
                .scrollToWhen(accountError),
            isError = accountError,
            supportingText = if (accountError) errorText else null,
        )
        AccountSelect(
            label = stringResource(R.string.movement_field_destination_account),
            selectedId = form.destinationAccountId,
            accounts = accounts.filter { it.id != form.accountId },
            onSelect = { onFormChange(form.copy(destinationAccountId = it)) },
            modifier = Modifier
                .weight(1f)
                .scrollToWhen(destinationError),
            isError = destinationError,
            supportingText = if (destinationError) errorText else null,
        )
    }

    // Money crossing between personal and shared ownership is a contribution or a withdrawal, not a
    // transfer; between two shared accounts it is an ordinary transfer.
    // Saying so while the accounts are being picked beats failing at save with nowhere to go.
    val sharedOwnership = { id: String? -> accounts.firstOrNull { it.id == id }?.ownershipKind }
    val sourceIsShared = sharedOwnership(form.accountId) == AccountOwnershipKind.SHARED
    val destinationIsShared = sharedOwnership(form.destinationAccountId) == AccountOwnershipKind.SHARED
    when {
        sourceIsShared && destinationIsShared -> Unit
        sourceIsShared -> Column {
            InlineBanner(
                kind = BannerKind.Info,
                text = stringResource(R.string.movement_transfer_out_of_shared),
            )
            TextButton(
                onClick = {
                    onRecordContribution(
                        requireNotNull(form.accountId),
                        form.amount,
                        form.destinationAccountId,
                        ContributionDirection.OUT,
                    )
                },
            ) {
                Text(stringResource(R.string.movement_transfer_out_of_shared_action))
            }
        }
        destinationIsShared -> Column {
            InlineBanner(
                kind = BannerKind.Info,
                text = stringResource(R.string.movement_transfer_into_shared),
            )
            TextButton(
                onClick = {
                    onRecordContribution(
                        requireNotNull(form.destinationAccountId),
                        form.amount,
                        form.accountId,
                        ContributionDirection.IN,
                    )
                },
            ) {
                Text(stringResource(R.string.movement_transfer_into_shared_action))
            }
        }
    }
}
