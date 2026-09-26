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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
            ) {
                Column {
                    ManagementDestination.entries.forEachIndexed { index, destination ->
                        ManagementRow(
                            destination = destination,
                            onClick = { selectedDestination = destination },
                        )
                        if (index < ManagementDestination.entries.lastIndex) {
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
    ManagementDestination.ACCOUNTS -> MaterialTheme.colorScheme.primary
    ManagementDestination.PEOPLE, ManagementDestination.EVENTS -> MaterialTheme.colorScheme.secondary
    ManagementDestination.SETTINGS -> FinanceTheme.colors.mutedText
}

enum class ManagementDestination(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
) {
    ACCOUNTS(R.string.management_accounts_title, R.string.management_accounts_description, Icons.Filled.AccountBalanceWallet),
    PEOPLE(R.string.management_people_title, R.string.management_people_description, Icons.Filled.Groups),
    EVENTS(R.string.management_events_title, R.string.management_events_description, Icons.Filled.CalendarMonth),
    SETTINGS(R.string.management_settings_title, R.string.management_settings_description, Icons.Filled.Settings),
}
