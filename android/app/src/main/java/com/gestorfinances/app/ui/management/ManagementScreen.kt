package com.gestorfinances.app.ui.management

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.IconChip

@Composable
fun ManagementSheet(
    onDestinationSelected: (ManagementDestination) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedDestination by remember { mutableStateOf<ManagementDestination?>(null) }
    val groups = listOf(
        R.string.management_group_finances to listOf(
            ManagementDestination.ACCOUNTS,
            ManagementDestination.CATEGORIES,
            ManagementDestination.BUDGETS,
        ),
        R.string.management_group_organization to listOf(
            ManagementDestination.PEOPLE,
            ManagementDestination.EVENTS,
            ManagementDestination.RECURRING,
        ),
        R.string.management_group_app to listOf(ManagementDestination.SETTINGS),
    )

    AppModalBottomSheet(
        onDismissRequest = {
            onDismiss()
            selectedDestination?.let(onDestinationSelected)
        },
        dismissRequested = selectedDestination != null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.management_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            groups.forEach { (groupTitle, destinations) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(groupTitle),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column {
                            destinations.forEachIndexed { index, destination ->
                                ManagementRow(
                                    destination = destination,
                                    onClick = { selectedDestination = destination },
                                )
                                if (index < destinations.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 56.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagementRow(
    destination: ManagementDestination,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconChip(
            icon = destination.icon,
            contentDescription = null,
            color = MaterialTheme.colorScheme.primary,
            size = 32.dp,
        )
        Text(
            text = stringResource(destination.titleRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
