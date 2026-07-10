package com.gestorfinances.app.ui.management

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.theme.FinanceTheme

@Composable
fun ManagementScreen(
    onDestinationSelected: (ManagementDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf(ManagementDestination.ACCOUNTS, ManagementDestination.CATEGORIES),
        listOf(ManagementDestination.PEOPLE, ManagementDestination.EVENTS),
        listOf(ManagementDestination.RECURRING, ManagementDestination.BUDGETS),
        listOf(ManagementDestination.SETTINGS),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.management_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { destination ->
                    ManagementTile(
                        destination = destination,
                        onClick = { onDestinationSelected(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ManagementTile(
    destination: ManagementDestination,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(88.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconChip(
                icon = destination.icon,
                contentDescription = null,
                color = destination.accentColor(),
            )
            Text(
                text = stringResource(destination.titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ManagementDestination.accentColor(): Color = when (this) {
    ManagementDestination.ACCOUNTS -> MaterialTheme.colorScheme.primary
    ManagementDestination.CATEGORIES -> FinanceTheme.colors.income
    ManagementDestination.PEOPLE -> FinanceTheme.colors.settlement
    ManagementDestination.EVENTS -> FinanceTheme.colors.refund
    ManagementDestination.RECURRING -> FinanceTheme.colors.transfer
    ManagementDestination.BUDGETS -> FinanceTheme.colors.alert
    ManagementDestination.SETTINGS -> MaterialTheme.colorScheme.onSurfaceVariant
}

enum class ManagementDestination(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    ACCOUNTS(R.string.management_accounts_title, Icons.Filled.AccountBalanceWallet),
    CATEGORIES(R.string.management_categories_title, Icons.Filled.Sell),
    PEOPLE(R.string.management_people_title, Icons.Filled.Groups),
    EVENTS(R.string.management_events_title, Icons.Filled.CalendarMonth),
    RECURRING(R.string.management_recurring_title, Icons.Filled.Autorenew),
    BUDGETS(R.string.management_budgets_title, Icons.Filled.Savings),
    SETTINGS(R.string.management_settings_title, Icons.Filled.Settings),
}
