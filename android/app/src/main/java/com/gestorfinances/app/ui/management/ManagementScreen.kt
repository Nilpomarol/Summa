package com.gestorfinances.app.ui.management

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.theme.FinanceTheme

@Composable
fun ManagementSheet(
    onDestinationSelected: (ManagementDestination) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedDestination by remember { mutableStateOf<ManagementDestination?>(null) }
    val sections = listOf(
        ManagementSection(
            titleRes = R.string.management_group_setup,
            destinations = listOf(
                ManagementDestination.ACCOUNTS,
                ManagementDestination.CATEGORIES,
            ),
        ),
        ManagementSection(
            titleRes = R.string.management_group_planning,
            destinations = listOf(
                ManagementDestination.BUDGETS,
                ManagementDestination.RECURRING,
            ),
        ),
        ManagementSection(
            titleRes = R.string.management_group_shared,
            destinations = listOf(
                ManagementDestination.PEOPLE,
                ManagementDestination.EVENTS,
                ManagementDestination.TAGS,
            ),
        ),
        ManagementSection(
            titleRes = R.string.management_group_app,
            destinations = listOf(ManagementDestination.SETTINGS),
        ),
    )

    AppModalBottomSheet(
        onDismissRequest = {
            onDismiss()
            selectedDestination?.let(onDestinationSelected)
        },
        dismissRequested = selectedDestination != null,
        maxHeightFraction = 0.72f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sections.forEach { section ->
                ManagementSection(
                    section = section,
                    onDestinationSelected = { selectedDestination = it },
                )
            }
        }
    }
}

@Composable
private fun ManagementSection(
    section: ManagementSection,
    onDestinationSelected: (ManagementDestination) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(section.titleRes),
            style = MaterialTheme.typography.labelLarge,
            color = FinanceTheme.colors.mutedText,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .semantics { heading() },
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
        ) {
            Column {
                section.destinations.forEachIndexed { index, destination ->
                    ManagementRow(
                        destination = destination,
                        onClick = { onDestinationSelected(destination) },
                    )
                    if (index < section.destinations.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 62.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(
            icon = destination.icon,
            contentDescription = null,
            color = destination.tintColor(),
            size = 36.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = stringResource(destination.titleRes),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(destination.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = FinanceTheme.colors.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = FinanceTheme.colors.mutedText,
        )
    }
}

@Composable
private fun ManagementDestination.tintColor() = when (this) {
    ManagementDestination.ACCOUNTS, ManagementDestination.BUDGETS -> MaterialTheme.colorScheme.primary
    ManagementDestination.CATEGORIES, ManagementDestination.TAGS -> MaterialTheme.colorScheme.tertiary
    ManagementDestination.PEOPLE, ManagementDestination.EVENTS -> MaterialTheme.colorScheme.secondary
    ManagementDestination.RECURRING, ManagementDestination.SETTINGS -> FinanceTheme.colors.mutedText
}

private data class ManagementSection(
    @StringRes val titleRes: Int,
    val destinations: List<ManagementDestination>,
)

enum class ManagementDestination(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
) {
    ACCOUNTS(R.string.management_accounts_title, R.string.management_accounts_description, Icons.Filled.AccountBalanceWallet),
    CATEGORIES(R.string.management_categories_title, R.string.management_categories_description, Icons.Filled.Sell),
    PEOPLE(R.string.management_people_title, R.string.management_people_description, Icons.Filled.Groups),
    EVENTS(R.string.management_events_title, R.string.management_events_description, Icons.Filled.CalendarMonth),
    TAGS(R.string.management_tags_title, R.string.management_tags_description, Icons.AutoMirrored.Filled.Label),
    RECURRING(R.string.management_recurring_title, R.string.management_recurring_description, Icons.Filled.Autorenew),
    BUDGETS(R.string.management_budgets_title, R.string.management_budgets_description, Icons.Filled.Savings),
    SETTINGS(R.string.management_settings_title, R.string.management_settings_description, Icons.Filled.Settings),
}
