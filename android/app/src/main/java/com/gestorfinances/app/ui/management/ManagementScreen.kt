package com.gestorfinances.app.ui.management

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.RootPageHeader
import com.gestorfinances.app.ui.theme.FinanceTheme

/** The Més hub: every secondary area has a normal home here, grouped by what it is for. */
@Composable
fun MoreScreen(
    onDestinationSelected: (ManagementDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { RootPageHeader(title = stringResource(R.string.nav_management)) }
        items(ManagementGroup.entries) { group ->
            ManagementGroupCard(group = group, onDestinationSelected = onDestinationSelected)
        }
    }
}

@Composable
private fun ManagementGroupCard(
    group: ManagementGroup,
    onDestinationSelected: (ManagementDestination) -> Unit,
) {
    val destinations = ManagementDestination.entries.filter { it.group == group }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        group.titleRes?.let { titleRes ->
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.labelLarge,
                color = FinanceTheme.colors.mutedText,
                modifier = Modifier
                    .padding(start = 4.dp, top = 8.dp)
                    .semantics { heading() },
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
        ) {
            Column {
                destinations.forEachIndexed { index, destination ->
                    ManagementRow(
                        destination = destination,
                        onClick = { onDestinationSelected(destination) },
                    )
                    if (index < destinations.lastIndex) {
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
            color = MaterialTheme.colorScheme.primary,
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
                maxLines = 2,
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

/** Hub groups, in display order; the last has no heading. */
enum class ManagementGroup(@StringRes val titleRes: Int?) {
    MONEY(R.string.management_group_money),
    PLANNING(R.string.management_group_planning),
    ORGANIZATION(R.string.management_group_organization),
    OTHER(null),
}

enum class ManagementDestination(
    val group: ManagementGroup,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
) {
    ACCOUNTS(ManagementGroup.MONEY, R.string.management_accounts_title, R.string.management_accounts_description, Icons.Outlined.AccountBalanceWallet),
    GOALS(ManagementGroup.MONEY, R.string.management_goals_title, R.string.management_goals_description, Icons.Outlined.Savings),
    BUDGETS(ManagementGroup.PLANNING, R.string.management_budgets_title, R.string.management_budgets_description, Icons.Outlined.PieChart),
    RECURRING(ManagementGroup.PLANNING, R.string.management_recurring_title, R.string.management_recurring_description, Icons.Outlined.Autorenew),
    CATEGORIES(ManagementGroup.ORGANIZATION, R.string.management_categories_title, R.string.management_categories_description, Icons.Outlined.Category),
    TRIPS(ManagementGroup.ORGANIZATION, R.string.management_events_title, R.string.management_events_description, Icons.Outlined.Luggage),
    TAGS(ManagementGroup.ORGANIZATION, R.string.management_tags_title, R.string.management_tags_description, Icons.AutoMirrored.Outlined.Label),
    PEOPLE(ManagementGroup.OTHER, R.string.management_people_title, R.string.management_people_description, Icons.Outlined.Groups),
    SETTINGS(ManagementGroup.OTHER, R.string.management_settings_title, R.string.management_settings_description, Icons.Outlined.Settings),
}
