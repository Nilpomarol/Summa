package com.gestorfinances.app.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountFlowEntry
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.theme.FinanceTheme

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    AccountsContent(
        state = state,
        modifier = modifier,
        onAdd = viewModel::onAddClicked,
        onEdit = viewModel::onEditClicked,
        onArchive = viewModel::onArchiveClicked,
        onFlow = viewModel::onFlowClicked,
        onMoveUp = viewModel::onMoveUpClicked,
        onMoveDown = viewModel::onMoveDownClicked,
    )

    state.form?.let { form ->
        AccountFormDialog(
            form = form,
            onFormChange = viewModel::onFormChanged,
            onDismiss = viewModel::onFormDismissed,
            onSave = viewModel::onSaveClicked,
        )
    }

    state.archiveCandidate?.let {
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(R.string.account_archive_confirm_title)) },
            text = { Text(text = stringResource(R.string.account_archive_warning)) },
            confirmButton = {
                DestructiveTextButton(onClick = viewModel::onArchiveConfirmed) {
                    Text(text = stringResource(R.string.common_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onArchiveDismissed) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    state.flowDetail?.let { detail ->
        AccountFlowDialog(
            detail = detail,
            onDismiss = viewModel::onFlowDismissed,
        )
    }
}

@Composable
private fun AccountsContent(
    state: AccountsUiState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onEdit: (AccountSummary) -> Unit,
    onArchive: (AccountSummary) -> Unit,
    onFlow: (AccountSummary) -> Unit,
    onMoveUp: (AccountSummary) -> Unit,
    onMoveDown: (AccountSummary) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.account_list_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        item {
            NetWorthHero(netWorthCents = state.accounts.sumOf { it.currentBalanceCents })
        }

        state.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (state.isLoading) {
            item {
                Text(
                    text = stringResource(R.string.account_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (state.accounts.isEmpty()) {
            item {
                EmptyAccountsCard(onAdd = onAdd)
            }
        } else {
            items(items = state.accounts, key = { it.id }) { account ->
                AccountRow(
                    account = account,
                    onEdit = { onEdit(account) },
                    onArchive = { onArchive(account) },
                    onFlow = { onFlow(account) },
                    onMoveUp = { onMoveUp(account) },
                    onMoveDown = { onMoveDown(account) },
                )
            }
            item {
                PrimaryButton(
                    text = stringResource(R.string.account_list_add),
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NetWorthHero(netWorthCents: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = FinanceTheme.colors.heroSurface,
        contentColor = FinanceTheme.colors.heroOnSurface,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.account_list_net_worth),
                style = MaterialTheme.typography.labelMedium,
                color = FinanceTheme.colors.heroOnSurfaceMuted,
            )
            MoneyText(
                cents = netWorthCents,
                color = FinanceTheme.colors.heroOnSurface,
                style = MaterialTheme.typography.displayMedium,
            )
        }
    }
}

@Composable
private fun EmptyAccountsCard(onAdd: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.account_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.account_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton(
                text = stringResource(R.string.account_list_add),
                onClick = onAdd,
            )
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountSummary,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onFlow: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val belowThreshold = account.lowBalanceThresholdCents?.let {
        account.currentBalanceCents < it
    } ?: false
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onFlow)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(
            icon = accountTypeIcon(account.type),
            contentDescription = null,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            tint = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (account.isDefault) {
                    NeutralPill(text = stringResource(R.string.account_default_badge))
                }
            }
            Text(
                text = account.type.label(),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        MoneyText(
            cents = account.currentBalanceCents,
            color = if (belowThreshold) {
                FinanceTheme.colors.debt
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.titleMedium,
        )
        AccountRowMenu(
            onEdit = onEdit,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onArchive = onArchive,
        )
    }
}

@Composable
private fun AccountRowMenu(
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onArchive: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.common_more_options),
                tint = FinanceTheme.colors.mutedText,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_edit)) },
                onClick = { expanded = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.account_move_up)) },
                onClick = { expanded = false; onMoveUp() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.account_move_down)) },
                onClick = { expanded = false; onMoveDown() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.common_archive),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { expanded = false; onArchive() },
            )
        }
    }
}

@Composable
private fun AccountFormDialog(
    form: AccountFormState,
    onFormChange: (AccountFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (form.id == null) R.string.account_form_new_title else R.string.account_form_edit_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                form.errorRes?.let {
                    Text(
                        text = stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                form.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onFormChange(form.copy(name = it, errorRes = null, errorMessage = null)) },
                    label = { Text(text = stringResource(R.string.account_field_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.startingBalance,
                    onValueChange = {
                        onFormChange(form.copy(startingBalance = it, errorRes = null, errorMessage = null))
                    },
                    label = { Text(text = stringResource(R.string.account_field_starting_balance)) },
                    prefix = { Text(text = "€") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipFlowSection(label = stringResource(R.string.account_field_type)) {
                    AccountType.entries.forEach { type ->
                        FinanceFilterChip(
                            selected = form.type == type,
                            label = type.label(),
                            onClick = { onFormChange(form.copy(type = type)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = form.isDefault,
                        onCheckedChange = { onFormChange(form.copy(isDefault = it)) },
                    )
                    Text(text = stringResource(R.string.account_field_default))
                }
                OutlinedTextField(
                    value = form.lowBalanceThreshold,
                    onValueChange = {
                        onFormChange(form.copy(lowBalanceThreshold = it, errorRes = null, errorMessage = null))
                    },
                    label = { Text(text = stringResource(R.string.account_field_low_balance_threshold)) },
                    prefix = { Text(text = "€") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(
                    text = stringResource(
                        if (form.id == null) R.string.account_save_new else R.string.account_save_changes,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun AccountFlowDialog(
    detail: AccountFlowDetailState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.account_flow_title, detail.account.name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.account_flow_current_balance,
                        formatEuroCents(detail.account.currentBalanceCents),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.account_flow_starting_balance,
                        formatEuroCents(detail.account.startingBalanceCents),
                    ),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                detail.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                when {
                    detail.isLoading -> Text(
                        text = stringResource(R.string.account_flow_loading),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    detail.entries.isEmpty() -> Text(
                        text = stringResource(R.string.account_flow_empty),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> detail.entries.forEach { entry ->
                        AccountFlowRow(entry = entry)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_back))
            }
        },
    )
}

@Composable
private fun AccountFlowRow(entry: AccountFlowEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title(),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = entry.date,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        MoneyText(
            cents = entry.deltaCents,
            color = if (entry.deltaCents > 0) {
                FinanceTheme.colors.income
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.titleMedium,
            signed = true,
        )
    }
}

@Composable
private fun AccountFlowEntry.title(): String =
    when (type) {
        MovementType.TRANSFER -> if (deltaCents < 0) {
            stringResource(
                R.string.account_flow_transfer_to,
                destinationAccountName ?: stringResource(R.string.movement_destination_missing),
            )
        } else {
            stringResource(R.string.account_flow_transfer_from, originAccountName)
        }
        else -> name ?: payee ?: categoryName ?: type.label()
    }

