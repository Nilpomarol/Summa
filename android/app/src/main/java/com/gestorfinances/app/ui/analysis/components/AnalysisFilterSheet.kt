package com.gestorfinances.app.ui.analysis.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisOneTimeMode
import com.gestorfinances.app.ui.analysis.AnalysisNatureFilter
import com.gestorfinances.app.ui.analysis.AnalysisUiState
import com.gestorfinances.app.ui.analysis.labelRes
import com.gestorfinances.app.ui.common.FilterSelectorField
import com.gestorfinances.app.ui.common.FinanceSwitch
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnalysisFilterSheet(
    state: AnalysisUiState,
    onNatureFilterSelected: (AnalysisNatureFilter) -> Unit,
    onOneTimeModeSelected: (AnalysisOneTimeMode) -> Unit,
    onGroupTripsAsBlocksChange: (Boolean) -> Unit,
    onAccountSelected: (String, String) -> Unit,
    onClearAccountFilter: () -> Unit,
    onCategorySelected: (String, String) -> Unit,
    onClearCategoryFilter: () -> Unit,
    onResetFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    var accountExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: Icon + Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconChip(
                    icon = Icons.Outlined.FilterList,
                    contentDescription = null,
                    color = MaterialTheme.colorScheme.primary,
                    size = 48.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.analysis_filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Account selection Row with icon & DropdownMenu
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = if (state.filterAccountId != null) MaterialTheme.colorScheme.primary else FinanceTheme.colors.mutedText,
                        modifier = Modifier.size(24.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        FilterSelectorField(
                            label = stringResource(R.string.analysis_filter_account),
                            value = state.filterAccountName ?: stringResource(R.string.analysis_filter_account_all),
                            onClick = { accountExpanded = true },
                            active = state.filterAccountId != null,
                            onClear = onClearAccountFilter,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false },
                            modifier = Modifier.heightIn(max = 360.dp),
                        ) {
                            // Option: All
                            val isAllSelected = state.filterAccountId == null
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.analysis_filter_account_all),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = if (isAllSelected) MaterialTheme.colorScheme.primary else FinanceTheme.colors.mutedText,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (isAllSelected) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {
                                    onClearAccountFilter()
                                    accountExpanded = false
                                }
                            )

                            // Option: Specific Accounts
                            state.accountOptions.forEach { account ->
                                val isSelected = state.filterAccountId == account.id
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = account.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingIcon = {
                                        IconChip(
                                            icon = accountIcon(account.icon),
                                            contentDescription = null,
                                            color = categoryColor(account.color),
                                            size = 32.dp
                                        )
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        onAccountSelected(account.id, account.name)
                                        accountExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Category selection Row with icon & DropdownMenu
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Category,
                        contentDescription = null,
                        tint = if (state.filterCategoryId != null) MaterialTheme.colorScheme.primary else FinanceTheme.colors.mutedText,
                        modifier = Modifier.size(24.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        FilterSelectorField(
                            label = stringResource(R.string.analysis_filter_category),
                            value = state.filterCategoryName ?: stringResource(R.string.analysis_filter_category_all),
                            onClick = { categoryExpanded = true },
                            active = state.filterCategoryId != null,
                            onClear = onClearCategoryFilter,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                            modifier = Modifier.heightIn(max = 360.dp),
                        ) {
                            // Option: All
                            val isAllSelected = state.filterCategoryId == null
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.analysis_filter_category_all),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = categoryIcon(null),
                                        contentDescription = null,
                                        tint = if (isAllSelected) MaterialTheme.colorScheme.primary else FinanceTheme.colors.mutedText,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (isAllSelected) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {
                                    onClearCategoryFilter()
                                    categoryExpanded = false
                                }
                            )

                            // Option: Specific Categories
                            state.categoryOptions.forEach { category ->
                                val isSelected = state.filterCategoryId == category.id
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = category.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingIcon = {
                                        IconChip(
                                            icon = categoryIcon(category.icon),
                                            contentDescription = null,
                                            color = categoryColor(category.color),
                                            size = 32.dp
                                        )
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        onCategorySelected(category.id, category.name)
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Nature Segmented Control
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.analysis_filter_nature),
                    style = MaterialTheme.typography.labelMedium,
                    color = FinanceTheme.colors.mutedText,
                )
                SegmentedControl(
                    options = AnalysisNatureFilter.entries,
                    selected = state.natureFilter,
                    label = { stringResource(it.labelRes()) },
                    onSelect = onNatureFilterSelected,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // One-Time Segmented Control
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.movement_field_one_time),
                    style = MaterialTheme.typography.labelMedium,
                    color = FinanceTheme.colors.mutedText,
                )
                SegmentedControl(
                    options = AnalysisOneTimeMode.entries,
                    selected = state.oneTimeMode,
                    label = { stringResource(it.labelRes()) },
                    onSelect = onOneTimeModeSelected,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Group trips toggle Row/Card
            Surface(
                onClick = { onGroupTripsAsBlocksChange(!state.groupTripsAsBlocks) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
                color = if (state.groupTripsAsBlocks) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f) else Color.Transparent,
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription = null,
                        tint = if (state.groupTripsAsBlocks) MaterialTheme.colorScheme.primary else FinanceTheme.colors.mutedText,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.trip_analysis_group_as_block),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    FinanceSwitch(
                        checked = state.groupTripsAsBlocks,
                        onCheckedChange = onGroupTripsAsBlocksChange
                    )
                }
            }

            // Reset filters Button at the bottom
            OutlinedButton(
                onClick = onResetFilters,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.analysis_filter_reset))
            }
        }
    }
}
