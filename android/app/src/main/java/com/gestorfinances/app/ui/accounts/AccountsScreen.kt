package com.gestorfinances.app.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.gestorfinances.app.data.repository.AccountAllocation
import com.gestorfinances.app.ui.common.formatBasisPointsCompact
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.common.AccountIconPalette
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.ColorPickerRow
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceSwitch
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.rememberFormDismissGuard
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IconPickerRow
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.movementRowPosition
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.RootPageHeader
import com.gestorfinances.app.ui.common.DistributionSegment
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.SegmentedDistributionBar
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.nextFieldKeyboardActions
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.themedIdentityColor

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    onViewAnalysis: (accountId: String, accountName: String) -> Unit = { _, _ -> },
    onMovementDetail: (MovementSummary) -> Unit = {},
    onViewGoals: (String) -> Unit = {},
    onDeleteCommitted: DeleteUndoHandler = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    val form = state.form
    val flowDetail = state.flowDetail
    val contributionForm = state.contributionForm
    when {
        form != null -> {
            val requestFormDismissal = rememberFormDismissGuard(
                formKey = form.id ?: "new-account",
                currentValue = form,
                hasMeaningfulChanges = { initial, current ->
                    initial.copy(showAdvanced = false, errorRes = null, errorField = null, errorMessage = null) !=
                        current.copy(showAdvanced = false, errorRes = null, errorField = null, errorMessage = null)
                },
                onDiscard = viewModel::onFormDismissed,
            )
            BackHandler(onBack = requestFormDismissal)
            AccountFormScreen(
                form = form,
                onFormChange = viewModel::onFormChanged,
                onOwnershipChange = viewModel::onOwnershipChanged,
                onBack = requestFormDismissal,
                onSave = viewModel::onSaveClicked,
                modifier = modifier,
            )
        }
        contributionForm != null -> {
            BackHandler(onBack = viewModel::onContributionDismissed)
            ContributionFormScreen(
                form = contributionForm,
                accounts = state.accounts,
                people = state.people.filter { person ->
                    state.accounts.firstOrNull { it.id == contributionForm.sharedAccountId }
                        ?.members?.any { it.personId == person.id } == true
                },
                onFormChange = viewModel::onContributionFormChanged,
                onBack = viewModel::onContributionDismissed,
                onSave = viewModel::onContributionSaveClicked,
                modifier = modifier,
            )
        }
        flowDetail != null -> {
            BackHandler(onBack = viewModel::onFlowDismissed)
            AccountFlowScreen(
                detail = flowDetail,
                onBack = viewModel::onFlowDismissed,
                onRetry = { viewModel.onFlowClicked(flowDetail.account) },
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
                onViewGoals = onViewGoals,
                onEdit = viewModel::onEditClicked,
                onArchive = viewModel::onArchiveClicked,
                onFlow = viewModel::onFlowClicked,
                onRetry = viewModel::onScreenShown,
                onMoveUp = viewModel::onMoveUpClicked,
                onMoveDown = viewModel::onMoveDownClicked,
                onContribution = viewModel::onContributionClicked,
            )
        }
    }

    state.archiveCandidate?.let {
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(R.string.account_archive_confirm_title)) },
            text = {
                Text(
                    text = if (it.activeTemplateCount == 0) {
                        stringResource(R.string.account_archive_warning)
                    } else {
                        pluralStringResource(
                            R.plurals.account_archive_active_templates_warning,
                            it.activeTemplateCount,
                            it.activeTemplateCount,
                        )
                    },
                )
            },
            confirmButton = {
                DestructiveTextButton(
                    onClick = { viewModel.onArchiveConfirmed(onSuccess = onDeleteCommitted) },
                ) {
                    Text(
                        text = if (it.activeTemplateCount == 0) {
                            stringResource(R.string.common_archive)
                        } else {
                            stringResource(R.string.account_archive_pause_and_archive)
                        },
                    )
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
    onViewGoals: (String) -> Unit,
    onEdit: (AccountSummary) -> Unit,
    onArchive: (AccountSummary) -> Unit,
    onFlow: (AccountSummary) -> Unit,
    onRetry: () -> Unit,
    onMoveUp: (AccountSummary) -> Unit,
    onMoveDown: (AccountSummary) -> Unit,
    onContribution: (AccountSummary) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RootPageHeader(title = stringResource(R.string.account_list_title))
        }

        item {
            AccountSummaryCard(accounts = state.accounts)
        }

        state.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_accounts,
                    onRetry = onRetry,
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
                AccountCard(
                    account = account,
                    allocation = state.accountAllocations[account.id],
                    dedicatedGoal = state.dedicatedGoals[account.id],
                    onViewGoals = { onViewGoals(account.id) },
                    onEdit = { onEdit(account) },
                    onArchive = { onArchive(account) },
                    onFlow = { onFlow(account) },
                    onMoveUp = { onMoveUp(account) },
                    onMoveDown = { onMoveDown(account) },
                    onContribution = { onContribution(account) },
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
private fun AccountSummaryCard(accounts: List<AccountSummary>) {
    val netWorthCents = accounts.sumOf { it.ownerValueCents }
    val countText = pluralStringResource(R.plurals.account_list_count, accounts.size, accounts.size)
    val positiveAccounts = accounts.filter { it.ownerValueCents > 0 }
    val positiveBalanceCents = positiveAccounts.sumOf { it.ownerValueCents }

    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: label + account count badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.account_list_net_worth),
                    style = MaterialTheme.typography.titleSmall,
                )
                NeutralPill(text = countText)
            }

            MoneyText(
                cents = netWorthCents,
                color = if (netWorthCents < 0) FinanceTheme.colors.debt else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineLarge,
                signed = netWorthCents < 0,
            )

            if (positiveAccounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                SegmentedDistributionBar(
                    segments = positiveAccounts.map { account ->
                        DistributionSegment(
                            color = themedIdentityColor(categoryColor(account.color)),
                            fraction = account.ownerValueCents.toFloat() /
                                positiveBalanceCents.toFloat(),
                        )
                    },
                    contentDescription = stringResource(R.string.account_distribution_accessibility),
                    modifier = Modifier.padding(end = 16.dp),
                )
                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider(color = FinanceTheme.colors.cardBorder)
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
    val accountColor = themedIdentityColor(categoryColor(account.color))
    val fraction = if (netWorthCents > 0 && account.ownerValueCents > 0) {
        account.ownerValueCents.toFloat() / netWorthCents.toFloat()
    } else 0f
    val percentText = when {
        account.ownerValueCents <= 0 -> null
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
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (percentText != null) {
            Text(
                text = percentText,
                style = MaterialTheme.typography.labelSmall,
                color = FinanceTheme.colors.mutedText,
            )
        }
        MoneyText(
            cents = account.ownerValueCents,
            color = if (account.ownerValueCents < 0) FinanceTheme.colors.debt
                    else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            signed = account.ownerValueCents < 0,
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
    allocation: AccountAllocation?,
    dedicatedGoal: String?,
    onViewGoals: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onFlow: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onContribution: () -> Unit,
) {
    val belowThreshold = account.lowBalanceThresholdCents?.let {
        account.currentBalanceCents < it
    } ?: false
    val accountColor = themedIdentityColor(categoryColor(account.color))
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
                        if (account.ownershipKind == AccountOwnershipKind.SHARED) {
                            NeutralPill(text = stringResource(R.string.account_shared_badge))
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
                    onContribution = onContribution.takeIf { account.ownershipKind == AccountOwnershipKind.SHARED },
                )
            }
            if (account.ownershipKind == AccountOwnershipKind.SHARED) {
                Text(
                    text = stringResource(
                        R.string.account_shared_value,
                        formatEuroCents(account.ownerValueCents),
                        formatBasisPointsCompact(account.ownerOwnershipBasisPoints),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceTheme.colors.mutedText,
                )
            }
            if (dedicatedGoal != null) {
                Text(stringResource(R.string.goal_account_dedicated, dedicatedGoal), style = MaterialTheme.typography.bodySmall)
            } else allocation?.let {
                Text(stringResource(R.string.goal_account_reservations, formatEuroCents(it.allocatedCents), formatEuroCents(it.unallocatedCents)), style = MaterialTheme.typography.bodySmall)
                if (it.unallocatedCents < 0) {
                    InlineBanner(kind = BannerKind.Alert, text = stringResource(R.string.goal_shortfall, account.name, formatEuroCents(-it.unallocatedCents)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onViewGoals) { Text(stringResource(R.string.goal_account_link)) }
                if (account.ownershipKind == AccountOwnershipKind.SHARED) {
                    TextButton(onClick = onContribution) {
                        Text(stringResource(R.string.account_contribution_add))
                    }
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
    onContribution: (() -> Unit)? = null,
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
            if (onContribution != null) {
                AppDropdownMenuItem(
                    text = { Text(stringResource(R.string.account_contribution_add)) },
                    onClick = { expanded = false; onContribution() },
                )
            }
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
    onOwnershipChange: (AccountOwnershipKind) -> Unit,
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
            InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_account)
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
            keyboardActions = doneKeyboardActions(onSave),
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.account_shared_toggle), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.account_shared_toggle_help), style = MaterialTheme.typography.bodySmall, color = FinanceTheme.colors.mutedText)
            }
            FinanceSwitch(
                checked = form.ownershipKind == AccountOwnershipKind.SHARED,
                onCheckedChange = { onOwnershipChange(if (it) AccountOwnershipKind.SHARED else AccountOwnershipKind.PERSONAL) },
            )
        }
        if (form.errorField == AccountFormField.OWNERSHIP && form.errorRes != null) {
            Text(
                text = stringResource(form.errorRes),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (form.ownershipKind == AccountOwnershipKind.SHARED) {
            Text(stringResource(R.string.account_members_title), style = MaterialTheme.typography.titleSmall)
            form.members.forEachIndexed { index, member ->
                AccountMemberEditor(
                    member = member,
                    onChange = { changed ->
                        onFormChange(form.copy(members = form.members.toMutableList().also { it[index] = changed }))
                    },
                )
            }
            MemberPercentTotals(
                members = form.members,
                onSplitEqually = { onFormChange(form.copy(members = form.members.splitEqually())) },
            )
            if (form.errorField == AccountFormField.MEMBERS && form.errorRes != null) {
                Text(stringResource(form.errorRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
        AdvancedAccountOptions(form = form, onFormChange = onFormChange, onSave = onSave)

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
private fun AccountMemberEditor(
    member: AccountMemberFormState,
    onChange: (AccountMemberFormState) -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    member.name.ifBlank { stringResource(R.string.account_member_owner) },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (member.personId == null) {
                    NeutralPill(text = stringResource(R.string.account_member_owner))
                } else {
                    FinanceSwitch(
                        checked = member.enabled,
                        onCheckedChange = {
                            onChange(
                                member.copy(
                                    enabled = it,
                                    ownershipPercent = if (it) member.ownershipPercent else "0,00",
                                    defaultExpensePercent = if (it) member.defaultExpensePercent else "0,00",
                                ),
                            )
                        },
                    )
                }
            }
            if (member.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = member.ownershipPercent,
                        onValueChange = { onChange(member.copy(ownershipPercent = it)) },
                        label = { Text(stringResource(R.string.account_member_ownership)) },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = member.defaultExpensePercent,
                        onValueChange = { onChange(member.copy(defaultExpensePercent = it)) },
                        label = { Text(stringResource(R.string.account_member_expense_split)) },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Running totals for both percentage columns, so a shared account reaches 100% in the editor
 * rather than at save time, plus the even split the arithmetic otherwise leaves to the user.
 */
@Composable
private fun MemberPercentTotals(
    members: List<AccountMemberFormState>,
    onSplitEqually: () -> Unit,
) {
    if (members.none { it.enabled }) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MemberPercentTotal(
            label = stringResource(R.string.account_member_ownership),
            basisPoints = members.basisPointTotal { it.ownershipPercent },
            modifier = Modifier.weight(1f),
        )
        MemberPercentTotal(
            label = stringResource(R.string.account_member_expense_split),
            basisPoints = members.basisPointTotal { it.defaultExpensePercent },
            modifier = Modifier.weight(1f),
        )
    }
    TextButton(onClick = onSplitEqually) {
        Text(stringResource(R.string.account_member_split_equally))
    }
}

@Composable
private fun MemberPercentTotal(
    label: String,
    basisPoints: Long?,
    modifier: Modifier = Modifier,
) {
    val complete = basisPoints == 10_000L
    val text = when {
        basisPoints == null -> "$label · ${stringResource(R.string.account_member_total_invalid)}"
        complete -> "$label ${formatBasisPointsCompact(basisPoints)}"
        basisPoints < 10_000L -> "$label ${formatBasisPointsCompact(basisPoints)} · " +
            stringResource(R.string.account_member_total_remaining, formatBasisPointsCompact(10_000L - basisPoints))
        else -> "$label ${formatBasisPointsCompact(basisPoints)} · " +
            stringResource(R.string.account_member_total_excess, formatBasisPointsCompact(basisPoints - 10_000L))
    }
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = if (complete) FinanceTheme.colors.mutedText else MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun ContributionFormScreen(
    form: ContributionFormState,
    accounts: List<AccountSummary>,
    people: List<PersonSummary>,
    onFormChange: (ContributionFormState) -> Unit,
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
        val sharedAccountName = accounts.firstOrNull { it.id == form.sharedAccountId }?.name.orEmpty()
        PageHeaderRow(onBack = onBack, title = stringResource(R.string.account_contribution_title, sharedAccountName))
        form.errorMessage?.let { InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_movement) }
        form.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = form.amount,
            onValueChange = { onFormChange(form.copy(amount = it)) },
            label = { Text(stringResource(R.string.movement_field_amount)) },
            prefix = { Text("€") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FormDatePicker(
            label = stringResource(R.string.movement_field_date),
            date = form.date,
            onDateChange = { onFormChange(form.copy(date = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
        FormSelect(
            label = stringResource(R.string.account_contribution_member),
            options = buildList {
                add(SelectOption(id = null, label = stringResource(R.string.account_member_owner)))
                people.forEach { add(SelectOption(id = it.id, label = it.name)) }
            },
            selectedId = form.personId,
            onSelect = { onFormChange(form.copy(personId = it, sourceAccountId = null)) },
        )
        if (form.personId == null) {
            val noSource = stringResource(R.string.account_contribution_external_source)
            FormSelect(
                label = stringResource(R.string.account_contribution_source),
                options = buildList {
                    add(SelectOption(id = null, label = noSource))
                    accounts.filter { it.id != form.sharedAccountId && it.ownershipKind == AccountOwnershipKind.PERSONAL }
                        .forEach { add(SelectOption(id = it.id, label = it.name)) }
                },
                selectedId = form.sourceAccountId,
                onSelect = { onFormChange(form.copy(sourceAccountId = it)) },
                placeholder = noSource,
            )
        }
        OutlinedTextField(
            value = form.name,
            onValueChange = { onFormChange(form.copy(name = it)) },
            label = { Text(stringResource(R.string.movement_field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.notes,
            onValueChange = { onFormChange(form.copy(notes = it)) },
            label = { Text(stringResource(R.string.movement_field_notes)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_cancel)) }
            PrimaryButton(text = stringResource(R.string.account_contribution_save), onClick = onSave, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AdvancedAccountOptions(
    form: AccountFormState,
    onFormChange: (AccountFormState) -> Unit,
    onSave: () -> Unit,
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
                keyboardActions = doneKeyboardActions(onSave),
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
    onRetry: () -> Unit,
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

        // Who this account belongs to. Membership is otherwise only visible inside the edit form.
        if (account.ownershipKind == AccountOwnershipKind.SHARED && account.members.isNotEmpty()) {
            val owner = stringResource(R.string.account_member_owner)
            Text(
                text = stringResource(
                    R.string.account_members_summary,
                    account.members.joinToString(" · ") { member ->
                        "${member.personName ?: owner} ${formatBasisPointsCompact(member.ownershipBasisPoints)}"
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = FinanceTheme.colors.mutedText,
            )
            HorizontalDivider()
        }

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
                    itemsIndexed(detail.entries) { index, movement ->
                        MovementListItem(
                            movement = movement,
                            onClick = { onMovementDetail(movement) },
                            position = movementRowPosition(index, detail.entries.size),
                        )
                    }
                }
            }
        }

        detail.errorMessage?.let { message ->
            InlineFailureBanner(
                diagnostic = message,
                messageRes = R.string.failure_load_accounts,
                onRetry = onRetry,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}
