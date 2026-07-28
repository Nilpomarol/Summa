package com.gestorfinances.app.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import com.gestorfinances.app.ui.common.AppDropdownMenu
import com.gestorfinances.app.ui.common.AppDropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.ui.common.AccountIconPalette
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.ColorPickerRow
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceSwitch
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IconPickerRow
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.nextFieldKeyboardActions
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    onViewAnalysis: (accountId: String, accountName: String) -> Unit = { _, _ -> },
    onMovementDetail: (MovementSummary) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    val form = state.form
    val flowDetail = state.flowDetail
    when {
        form != null -> {
            BackHandler(onBack = viewModel::onFormDismissed)
            AccountFormScreen(
                form = form,
                onFormChange = viewModel::onFormChanged,
                onBack = viewModel::onFormDismissed,
                onSave = viewModel::onSaveClicked,
                modifier = modifier,
            )
        }
        flowDetail != null -> {
            BackHandler(onBack = viewModel::onFlowDismissed)
            AccountFlowScreen(
                detail = flowDetail,
                onBack = viewModel::onFlowDismissed,
                onViewAnalysis = {
                    viewModel.onFlowDismissed()
                    onViewAnalysis(flowDetail.account.id, flowDetail.account.name)
                },
                onMovementDetail = onMovementDetail,
                modifier = modifier,
            )
        }
        else -> {
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
        }
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
            PatrimoniHeroCard(accounts = state.accounts)
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
            val totalCents = state.accounts.sumOf { it.currentBalanceCents }
            items(items = state.accounts, key = { it.id }) { account ->
                AccountCard(
                    account = account,
                    totalCents = totalCents,
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
private fun PatrimoniHeroCard(accounts: List<AccountSummary>) {
    val netWorthCents = accounts.sumOf { it.currentBalanceCents }
    val countText = if (accounts.size == 1) "1 compte" else "${accounts.size} comptes"
    val positiveAccounts = accounts.filter { it.currentBalanceCents > 0 }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = FinanceTheme.colors.heroSurface,
        contentColor = FinanceTheme.colors.heroOnSurface,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header row: label + account count badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.account_list_net_worth),
                    style = MaterialTheme.typography.labelMedium,
                    color = FinanceTheme.colors.heroOnSurfaceMuted,
                )
                if (accounts.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = FinanceTheme.colors.heroOnSurface.copy(alpha = 0.12f),
                        contentColor = FinanceTheme.colors.heroOnSurfaceMuted,
                    ) {
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main net worth figure
            MoneyText(
                cents = netWorthCents,
                color = FinanceTheme.colors.heroOnSurface,
                style = MaterialTheme.typography.displayMedium,
            )

            // Per-account breakdown (only when there are accounts)
            if (accounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                // Colored stacked bar — one segment per positive-balance account
                if (positiveAccounts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    ) {
                        positiveAccounts.forEach { account ->
                            Box(
                                modifier = Modifier
                                    .weight(account.currentBalanceCents.toFloat())
                                    .fillMaxHeight()
                                    .background(categoryColor(account.color)),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                HorizontalDivider(color = FinanceTheme.colors.heroOnSurface.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(10.dp))

                // Per-account rows
                accounts.forEach { account ->
                    AccountBreakdownRow(account = account, netWorthCents = netWorthCents)
                }
            }
        }
    }
}

@Composable
private fun AccountBreakdownRow(account: AccountSummary, netWorthCents: Long) {
    val accountColor = categoryColor(account.color)
    val fraction = if (netWorthCents > 0 && account.currentBalanceCents > 0) {
        account.currentBalanceCents.toFloat() / netWorthCents.toFloat()
    } else 0f
    val percentText = when {
        account.currentBalanceCents <= 0 -> null
        fraction < 0.01f -> "<1%"
        else -> "${(fraction * 100).toInt()}%"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(accountColor, CircleShape),
        )
        Text(
            text = account.name,
            style = MaterialTheme.typography.bodyMedium,
            color = FinanceTheme.colors.heroOnSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (percentText != null) {
            Text(
                text = percentText,
                style = MaterialTheme.typography.labelSmall,
                color = FinanceTheme.colors.heroOnSurfaceMuted,
            )
        }
        MoneyText(
            cents = account.currentBalanceCents,
            color = if (account.currentBalanceCents < 0) FinanceTheme.colors.debt
                    else FinanceTheme.colors.heroOnSurface,
            style = MaterialTheme.typography.titleSmall,
            signed = account.currentBalanceCents < 0,
        )
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
private fun AccountCard(
    account: AccountSummary,
    totalCents: Long,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onFlow: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val belowThreshold = account.lowBalanceThresholdCents?.let {
        account.currentBalanceCents < it
    } ?: false
    val accountColor = categoryColor(account.color)
    val fraction = if (totalCents > 0 && account.currentBalanceCents > 0) {
        (account.currentBalanceCents.toFloat() / totalCents.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val percentText = "${(fraction * 100).toInt()}%"

    FinanceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onFlow),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Main row: icon + name/type + balance + menu
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(
                    icon = if (account.icon != null) accountIcon(account.icon)
                           else accountTypeIcon(account.type),
                    contentDescription = null,
                    color = accountColor,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = account.type.label(),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (account.isDefault) {
                            NeutralPill(
                                text = stringResource(R.string.account_default_badge),
                                leadingIcon = Icons.Outlined.PushPin,
                            )
                        }
                    }
                }
                MoneyText(
                    cents = account.currentBalanceCents,
                    color = if (account.currentBalanceCents < 0 || belowThreshold) FinanceTheme.colors.debt
                            else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                )
                AccountRowMenu(
                    onEdit = onEdit,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onArchive = onArchive,
                )
            }

            // Progress bar + percentage (only when total is meaningful)
            if (totalCents > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(2.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(accountColor, RoundedCornerShape(2.dp)),
                        )
                    }
                    Text(
                        text = percentText,
                        style = MaterialTheme.typography.labelSmall,
                        color = FinanceTheme.colors.mutedText,
                    )
                }
            }
        }
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
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppDropdownMenuItem(
                text = { Text(stringResource(R.string.common_edit)) },
                onClick = { expanded = false; onEdit() },
            )
            AppDropdownMenuItem(
                text = { Text(stringResource(R.string.account_move_up)) },
                onClick = { expanded = false; onMoveUp() },
            )
            AppDropdownMenuItem(
                text = { Text(stringResource(R.string.account_move_down)) },
                onClick = { expanded = false; onMoveDown() },
            )
            AppDropdownMenuItem(
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

// ---------------------------------------------------------------------------
// Account form — bottom sheet
// ---------------------------------------------------------------------------

@Composable
private fun AccountFormScreen(
    form: AccountFormState,
    onFormChange: (AccountFormState) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeaderRow(
            onBack = onBack,
            title = stringResource(
                if (form.id == null) R.string.account_form_new_title
                else R.string.account_form_edit_title,
            ),
        )

        form.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Live preview
        AccountPreviewCard(form = form)

        // Name
        val nameError = form.errorField == AccountFormField.NAME
        OutlinedTextField(
            value = form.name,
            onValueChange = {
                onFormChange(form.copy(name = it, errorRes = null, errorField = null, errorMessage = null))
            },
            label = { Text(text = stringResource(R.string.account_field_name)) },
            singleLine = true,
            isError = nameError,
            supportingText = if (nameError && form.errorRes != null) {
                { Text(text = stringResource(form.errorRes)) }
            } else null,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = nextFieldKeyboardActions(),
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(nameError),
        )

        // Starting balance
        val startingBalanceError = form.errorField == AccountFormField.STARTING_BALANCE
        OutlinedTextField(
            value = form.startingBalance,
            onValueChange = {
                onFormChange(
                    form.copy(startingBalance = it, errorRes = null, errorField = null, errorMessage = null),
                )
            },
            label = { Text(text = stringResource(R.string.account_field_starting_balance)) },
            prefix = { Text(text = "€") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = doneKeyboardActions(),
            singleLine = true,
            isError = startingBalanceError,
            supportingText = if (startingBalanceError && form.errorRes != null) {
                { Text(text = stringResource(form.errorRes)) }
            } else null,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(startingBalanceError),
        )

        // Color picker
        ColorPickerRow(
            label = stringResource(R.string.account_field_color),
            selectedHex = form.colorHex,
            onSelect = { onFormChange(form.copy(colorHex = it)) },
        )

        // Icon picker
        IconPickerRow(
            label = stringResource(R.string.account_field_icon),
            options = AccountIconPalette,
            selectedKey = form.iconKey,
            onSelect = { onFormChange(form.copy(iconKey = it)) },
        )

        // Type chips
        ChipFlowSection(label = stringResource(R.string.account_field_type)) {
            AccountType.entries.forEach { type ->
                FinanceFilterChip(
                    selected = form.type == type,
                    label = type.label(),
                    onClick = { onFormChange(form.copy(type = type)) },
                )
            }
        }

        // Default toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.account_field_default),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            FinanceSwitch(
                checked = form.isDefault,
                onCheckedChange = { onFormChange(form.copy(isDefault = it)) },
            )
        }

        // Advanced options (low balance threshold)
        AdvancedAccountOptions(form = form, onFormChange = onFormChange)

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
            PrimaryButton(
                text = stringResource(
                    if (form.id == null) R.string.account_save_new
                    else R.string.account_save_changes,
                ),
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AccountPreviewCard(form: AccountFormState) {
    val balanceCents = parseEuroCents(form.startingBalance, allowNegative = true) ?: 0L
    val color = categoryColor(form.colorHex)
    val icon = accountIcon(form.iconKey)
    val nameText = form.name.ifBlank { stringResource(R.string.account_preview_placeholder) }

    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconChip(icon = icon, contentDescription = null, color = color)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nameText,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (form.name.isBlank()) {
                        FinanceTheme.colors.mutedText
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = form.type.label(),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (form.isDefault) {
                        NeutralPill(
                            text = stringResource(R.string.account_default_badge),
                            leadingIcon = Icons.Outlined.PushPin,
                        )
                    }
                }
            }
            MoneyText(
                cents = balanceCents,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun AdvancedAccountOptions(
    form: AccountFormState,
    onFormChange: (AccountFormState) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Surface(
            onClick = { onFormChange(form.copy(showAdvanced = !form.showAdvanced)) },
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.account_advanced_options),
                    style = MaterialTheme.typography.labelMedium,
                    color = FinanceTheme.colors.mutedText,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (form.showAdvanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = FinanceTheme.colors.mutedText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (form.showAdvanced) {
            val thresholdError = form.errorField == AccountFormField.LOW_BALANCE_THRESHOLD
            OutlinedTextField(
                value = form.lowBalanceThreshold,
                onValueChange = {
                    onFormChange(
                        form.copy(lowBalanceThreshold = it, errorRes = null, errorField = null, errorMessage = null),
                    )
                },
                label = { Text(text = stringResource(R.string.account_field_low_balance_threshold)) },
                prefix = { Text(text = "€") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = doneKeyboardActions(),
                singleLine = true,
                isError = thresholdError,
                supportingText = if (thresholdError && form.errorRes != null) {
                    { Text(text = stringResource(form.errorRes)) }
                } else null,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .scrollToWhen(thresholdError),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Account flow sheet (Step 4)
// ---------------------------------------------------------------------------

@Composable
private fun AccountFlowScreen(
    detail: AccountFlowDetailState,
    onBack: () -> Unit,
    onViewAnalysis: () -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = detail.account

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
    ) {
        // Header: back + icon + name/count + analysis button (single row)
        val movementCountText = if (!detail.isLoading) {
            pluralStringResource(
                R.plurals.account_flow_movement_count,
                detail.entries.size,
                detail.entries.size,
            )
        } else null
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 6.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
            IconChip(
                icon = if (account.icon != null) accountIcon(account.icon)
                       else accountTypeIcon(account.type),
                contentDescription = null,
                color = categoryColor(account.color),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (movementCountText != null) {
                    Text(
                        text = movementCountText,
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            TextButton(onClick = onViewAnalysis) {
                Icon(
                    imageVector = Icons.Outlined.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.account_flow_view_analysis))
            }
        }

        HorizontalDivider()

        // Movement list
        when {
            detail.isLoading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.account_flow_loading),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            detail.entries.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.account_flow_empty),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    items(detail.entries) { movement ->
                        MovementListItem(
                            movement = movement,
                            onClick = { onMovementDetail(movement) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }

        detail.errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}
