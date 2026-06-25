package com.gestorfinances.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountFlowEntry
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor

/**
 * Shared movement row — used by AccountFlowSheet and (from P5R-3) the main movements list.
 * Redesigning this composable in P5R-3 automatically updates both call sites.
 */
@Composable
fun MovementListItem(
    entry: AccountFlowEntry,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(
            icon = movementTypeIcon(entry.type),
            contentDescription = null,
            color = FinanceTheme.colors.amountColor(entry.type),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.itemTitle(),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = entry.date,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        MoneyText(
            cents = entry.deltaCents,
            color = FinanceTheme.colors.amountColor(entry.type),
            style = MaterialTheme.typography.titleMedium,
            signed = true,
        )
    }
}

@Composable
private fun AccountFlowEntry.itemTitle(): String =
    when (type) {
        MovementType.TRANSFER -> if (deltaCents < 0) {
            stringResource(
                R.string.account_flow_transfer_to,
                destinationAccountName ?: stringResource(R.string.movement_destination_missing),
            )
        } else {
            stringResource(R.string.account_flow_transfer_from, originAccountName ?: "?")
        }
        else -> name ?: payee ?: categoryName ?: type.label()
    }
