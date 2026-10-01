package com.gestorfinances.app.ui.accounts

import com.gestorfinances.app.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gestorfinances.app.ui.navigation.Route
import com.gestorfinances.app.ui.common.ListPage
import com.gestorfinances.app.ui.theme.heroIdentityColor
import java.time.YearMonth
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import com.gestorfinances.app.ui.common.HERO_MUTED_ALPHA
import com.gestorfinances.app.ui.common.HeroMonthChangeBars
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.ListHero
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import com.gestorfinances.app.ui.common.EntityListRow
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.goals.goalIcon
import com.gestorfinances.app.ui.common.CreatePersonDialog
import com.gestorfinances.app.ui.movements.personInitial
import com.gestorfinances.app.ui.movements.ShareField
import com.gestorfinances.app.ui.movements.PersonMonogram
import com.gestorfinances.app.ui.common.SegmentedControl
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import com.gestorfinances.app.data.repository.ContributionDirection
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.gestorfinances.app.ui.common.EntityAppearancePickers
import com.gestorfinances.app.ui.common.EntityFormHeader
import com.gestorfinances.app.ui.common.EntityFormSheet
import com.gestorfinances.app.ui.common.FormDisclosure
import com.gestorfinances.app.ui.common.FormReveal
import com.gestorfinances.app.ui.movements.FormToggleRow
import com.gestorfinances.app.ui.movements.MovementFormHeader
import com.gestorfinances.app.ui.common.formatBasisPointsCompact
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AccountAllocation
import com.gestorfinances.app.data.repository.AccountGoalReservation
import com.gestorfinances.app.data.repository.AccountType
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.common.AccountIconPalette
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceSwitch
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.EntityDetailHeader
import com.gestorfinances.app.ui.common.EntityActionPill
import com.gestorfinances.app.ui.common.EntityFigure
import com.gestorfinances.app.ui.common.EntityHeaderMarkSize
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.data.repository.AccountMember
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.gestorfinances.app.ui.common.EntityDetailTopBar
import com.gestorfinances.app.ui.common.EntityMenuAction
import com.gestorfinances.app.ui.common.dayGroupedRows
import java.time.LocalDate
import com.gestorfinances.app.ui.common.DistributionSegment
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.SegmentedDistributionBar
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

/** This page visit's ViewModel; [route] says whether the page opens straight into a form. */
@Composable
fun accountsViewModel(appContainer: AppContainer, route: Route.Accounts): AccountsViewModel = viewModel {
    AccountsViewModel(
        accountRepository = appContainer.accountRepository,
        goalRepository = appContainer.goalRepository,
        movementRepository = appContainer.movementRepository,
        templateRepository = appContainer.templateRepository,
        notificationRefresher = appContainer.notificationCoordinator,
        personRepository = appContainer.personRepository,
    ).apply {
        if (route.openAddForm) onAddRequested()
        route.editContributionId?.let(::editContribution)
    }
}

@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    viewModel: AccountsViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onOpenDetail: (AccountSummary) -> Unit,
    onDeleteCommitted: DeleteUndoHandler = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, dataVersion) {
        viewModel.onScreenShown()
    }

    AccountsContent(
        onBack = onBack,
        state = state,
        modifier = modifier,
        onAdd = viewModel::onAddClicked,
        onOpen = onOpenDetail,
        onRetry = viewModel::onScreenShown,
        onMoveUp = viewModel::onMoveUpClicked,
        onMoveDown = viewModel::onMoveDownClicked,
    )
    AccountFormSheet(state = state, viewModel = viewModel)
    ContributionFormSheet(state = state, viewModel = viewModel)

    state.archiveCandidate?.let {
        AccountArchiveDialog(candidate = it, viewModel = viewModel, onArchived = onDeleteCommitted)
    }
}

/** An account's own page: its balance and the ledger that reconciles with it. */
@Composable
fun AccountDetailPage(
    accountId: String,
    onBack: () -> Unit,
    viewModel: AccountsViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onViewAnalysis: (accountId: String, accountName: String) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    onAddMovement: (accountId: String) -> Unit,
    onOpenGoal: (goalId: String) -> Unit,
    onDeleteCommitted: DeleteUndoHandler,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, accountId, dataVersion) {
        viewModel.onAccountDetailOpened(accountId)
    }

    val flowDetail = state.flowDetail
    when {
        flowDetail != null -> AccountFlowScreen(
            // An action that fails here (archiving, say) reports on the list's state: show it too.
            detail = flowDetail.copy(errorMessage = flowDetail.errorMessage ?: state.errorMessage),
            allocation = state.accountAllocations[flowDetail.account.id],
            onOpenGoal = onOpenGoal,
            onBack = onBack,
            onRetry = { viewModel.onAccountDetailOpened(accountId) },
            onAddMovement = { onAddMovement(flowDetail.account.id) },
            onContribution = { viewModel.onContributionClicked(flowDetail.account) },
            onWithdrawal = { viewModel.onWithdrawalClicked(flowDetail.account) },
            onEdit = { viewModel.onEditClicked(flowDetail.account) },
            onArchive = { viewModel.onArchiveClicked(flowDetail.account) },
            onViewAnalysis = { onViewAnalysis(flowDetail.account.id, flowDetail.account.name) },
            onMovementDetail = onMovementDetail,
            modifier = modifier,
        )
        else -> Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageHeaderRow(onBack = onBack)
            val errorMessage = state.errorMessage
            if (errorMessage != null) {
                InlineFailureBanner(
                    diagnostic = errorMessage,
                    messageRes = R.string.failure_load_accounts,
                    onRetry = { viewModel.onAccountDetailOpened(accountId) },
                )
            } else {
                Text(
                    text = stringResource(R.string.account_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    AccountFormSheet(state = state, viewModel = viewModel)
    ContributionFormSheet(state = state, viewModel = viewModel)

    state.archiveCandidate?.let {
        AccountArchiveDialog(
            candidate = it,
            viewModel = viewModel,
            onArchived = { undo ->
                onDeleteCommitted(undo)
                onBack()
            },
        )
    }
}

/** The account create/edit sheet over whichever account page opened it. */
@Composable
private fun AccountFormSheet(state: AccountsUiState, viewModel: AccountsViewModel) {
    var appearanceOpen by rememberSaveable(state.form?.id) { mutableStateOf(false) }
    EntityFormSheet(
        form = state.form,
        key = { it.id ?: "new-account" },
        changed = { initial, current -> initial.comparable() != current.comparable() },
        onDiscard = viewModel::onFormDismissed,
        onSave = viewModel::onSaveClicked,
        title = { stringResource(if (it.id == null) R.string.account_form_new_title else R.string.account_form_edit_title) },
        saveLabel = { stringResource(if (it.id == null) R.string.account_save_new else R.string.account_save_changes) },
    ) { form ->
        AccountFormFields(
            form = form,
            people = state.people,
            appearanceOpen = appearanceOpen,
            onToggleAppearance = { appearanceOpen = !appearanceOpen },
            onFormChange = { viewModel.onFormChanged(it.copy(errorRes = null, errorField = null, errorMessage = null)) },
            onOwnershipChange = viewModel::onOwnershipChanged,
            onCreatePerson = viewModel::onCreatePersonForAccount,
            onSave = viewModel::onSaveClicked,
        )
    }
}

/** What a discard check compares: validation and the open details group are not edits. */
private fun AccountFormState.comparable(): AccountFormState =
    copy(showAdvanced = false, errorRes = null, errorField = null, errorMessage = null)

@Composable
private fun AccountArchiveDialog(
    candidate: AccountArchiveCandidate,
    viewModel: AccountsViewModel,
    onArchived: DeleteUndoHandler,
) {
    AlertDialog(
        onDismissRequest = viewModel::onArchiveDismissed,
        title = { Text(text = stringResource(R.string.account_archive_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (candidate.activeTemplateCount == 0) {
                        stringResource(R.string.account_archive_warning)
                    } else {
                        pluralStringResource(
                            R.plurals.account_archive_active_templates_warning,
                            candidate.activeTemplateCount,
                            candidate.activeTemplateCount,
                        )
                    },
                )
                if (candidate.goalNames.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.account_archive_goals_warning,
                            candidate.goalNames.size,
                            joinNames(candidate.goalNames),
                        ),
                        color = FinanceTheme.colors.alert,
                    )
                }
            }
        },
        confirmButton = {
            DestructiveTextButton(onClick = { viewModel.onArchiveConfirmed(onSuccess = onArchived) }) {
                Text(
                    text = if (candidate.activeTemplateCount == 0) {
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

/** "A", "A i B", "A, B i C". */
@Composable
private fun joinNames(names: List<String>): String =
    if (names.size == 1) names.single()
    else stringResource(R.string.common_list_and, names.dropLast(1).joinToString(", "), names.last())

/** A shared account's contribution/withdrawal sheet over whichever account page opened it. */
@Composable
private fun ContributionFormSheet(state: AccountsUiState, viewModel: AccountsViewModel) {
    var detailsOpen by rememberSaveable(state.contributionForm?.id) { mutableStateOf(false) }
    EntityFormSheet(
        form = state.contributionForm,
        key = { it.id ?: "new-contribution:${it.sharedAccountId}" },
        changed = { initial, current ->
            initial.copy(errorRes = null, errorMessage = null) != current.copy(errorRes = null, errorMessage = null)
        },
        onDiscard = viewModel::onContributionDismissed,
        onSave = viewModel::onContributionSaveClicked,
        title = { form ->
            stringResource(
                if (form.direction == ContributionDirection.OUT) R.string.account_withdrawal_title else R.string.account_contribution_title,
                state.accounts.firstOrNull { it.id == form.sharedAccountId }?.name.orEmpty(),
            )
        },
        saveLabel = { form ->
            stringResource(
                if (form.direction == ContributionDirection.OUT) R.string.account_withdrawal_save else R.string.account_contribution_save,
            )
        },
    ) { form ->
        ContributionFormFields(
            form = form,
            accounts = state.accounts,
            people = state.people.filter { person ->
                state.accounts.firstOrNull { it.id == form.sharedAccountId }
                    ?.members?.any { it.personId == person.id } == true
            },
            detailsOpen = detailsOpen,
            onToggleDetails = { detailsOpen = !detailsOpen },
            onFormChange = { viewModel.onContributionFormChanged(it.copy(errorRes = null, errorMessage = null)) },
        )
    }
}

@Composable
private fun AccountsContent(
    onBack: () -> Unit,
    state: AccountsUiState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onOpen: (AccountSummary) -> Unit,
    onRetry: () -> Unit,
    onMoveUp: (AccountSummary) -> Unit,
    onMoveDown: (AccountSummary) -> Unit,
) {
    var reordering by rememberSaveable { mutableStateOf(false) }
    ListPage(
        title = stringResource(R.string.account_list_title),
        onBack = onBack,
        addLabel = stringResource(R.string.account_list_add),
        onAdd = onAdd,
        modifier = modifier,
        actions = {
            if (reordering) {
                TextButton(onClick = { reordering = false }) { Text(stringResource(R.string.common_done)) }
            }
        },
        menu = if (!reordering && state.accounts.size > 1) {
            listOf(EntityMenuAction(stringResource(R.string.account_reorder), onClick = { reordering = true }))
        } else {
            emptyList()
        },
    ) {
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.account_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.account_empty_body),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            item { NetWorthHero(accounts = state.accounts, monthEndsCents = state.netWorthMonthEndsCents) }
            item {
                // One item, so the rows sit flush and read as one list between their dividers.
                Column {
                    state.accounts.forEachIndexed { index, account ->
                        AccountListRow(
                            account = account,
                            shortfallCents = state.accountAllocations[account.id]
                                ?.unallocatedCents?.takeIf { it < 0 }?.let { -it },
                            isLast = index == state.accounts.lastIndex,
                            onOpen = if (reordering) null else ({ onOpen(account) }),
                            reorder = if (reordering) {
                                ReorderControls(
                                    onMoveUp = if (index > 0) ({ onMoveUp(account) }) else null,
                                    onMoveDown = if (index < state.accounts.lastIndex) ({ onMoveDown(account) }) else null,
                                )
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Net worth on the forest hero: how much it moved this month, how much it moved in each of the
 * last twelve, and how it splits across the accounts that hold money.
 */
@Composable
private fun NetWorthHero(accounts: List<AccountSummary>, monthEndsCents: List<Long>) {
    val colors = FinanceTheme.colors
    val netWorthCents = accounts.sumOf { it.ownerValueCents }
    val positiveAccounts = accounts.filter { it.ownerValueCents > 0 }
    val positiveBalanceCents = positiveAccounts.sumOf { it.ownerValueCents }
    ListHero(
        eyebrow = stringResource(R.string.account_list_net_worth),
        watermark = Icons.Outlined.AccountBalanceWallet,
        cents = netWorthCents,
        figureColor = if (netWorthCents < 0) colors.heroDebt else colors.heroOnSurface,
    ) {
        monthEndsCents.lastOrNull()?.let { lastMonthEnd ->
            val changeCents = netWorthCents - lastMonthEnd
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MoneyText(
                    cents = changeCents,
                    color = when {
                        changeCents > 0 -> colors.heroIncome
                        changeCents < 0 -> colors.heroDebt
                        else -> colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    signed = changeCents != 0L,
                )
                HeroCaption(text = stringResource(R.string.account_list_net_worth_this_month))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // What each of the last twelve months added or took away, this month last.
        val ends = monthEndsCents + netWorthCents
        val thisMonth = YearMonth.now()
        HeroMonthChangeBars(
            title = stringResource(R.string.account_list_monthly_change),
            changes = (1 until ends.size).map { i ->
                thisMonth.minusMonths((ends.size - 1 - i).toLong()) to ends[i] - ends[i - 1]
            },
        )
        if (positiveAccounts.size > 1) {
            Spacer(modifier = Modifier.height(14.dp))
            SegmentedDistributionBar(
                segments = positiveAccounts.map { account ->
                    DistributionSegment(
                        color = heroIdentityColor(categoryColor(account.color)),
                        fraction = account.ownerValueCents.toFloat() / positiveBalanceCents.toFloat(),
                    )
                },
                contentDescription = stringResource(R.string.account_distribution_accessibility),
            )
        }
    }
}

/** A row's move buttons while the list is being reordered; null where the row cannot move that way. */
private class ReorderControls(val onMoveUp: (() -> Unit)?, val onMoveDown: (() -> Unit)?)

@Composable
private fun AccountListRow(
    account: AccountSummary,
    shortfallCents: Long?,
    isLast: Boolean,
    onOpen: (() -> Unit)?,
    reorder: ReorderControls?,
) {
    val belowThreshold = account.lowBalanceThresholdCents?.let { account.currentBalanceCents < it } ?: false
    val shared = account.ownershipKind == AccountOwnershipKind.SHARED
    EntityListRow(
        leading = {
            IdentityIconTile(
                icon = if (account.icon != null) accountIcon(account.icon) else accountTypeIcon(account.type),
                color = categoryColor(account.color),
            )
        },
        title = account.name,
        subtitle = listOfNotNull(
            account.type.label(),
            stringResource(R.string.account_default_badge).takeIf { account.isDefault },
            stringResource(R.string.account_shared_badge).takeIf { shared },
        ).joinToString(" · "),
        below = shortfallCents?.let { cents ->
            {
                Text(
                    text = stringResource(R.string.account_list_shortfall, formatEuroCents(cents)),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceTheme.colors.alert,
                )
            }
        },
        isLast = isLast,
        onClick = onOpen,
        trailing = {
            if (reorder != null) {
                Row {
                    IconButton(onClick = { reorder.onMoveUp?.invoke() }, enabled = reorder.onMoveUp != null) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.account_move_up))
                    }
                    IconButton(onClick = { reorder.onMoveDown?.invoke() }, enabled = reorder.onMoveDown != null) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.account_move_down))
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(
                        cents = account.currentBalanceCents,
                        color = if (account.currentBalanceCents < 0 || belowThreshold) FinanceTheme.colors.debt
                                else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (shared) {
                        Text(
                            text = stringResource(R.string.account_list_your_share, formatEuroCents(account.ownerValueCents)),
                            style = MaterialTheme.typography.bodySmall,
                            color = FinanceTheme.colors.mutedText,
                        )
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Account form — bottom sheet
// ---------------------------------------------------------------------------

/**
 * The account form's fields: its tile and name, type and starting balance, whether it is the
 * default, and whether it is shared (which opens who owns and pays what), with the low-balance
 * alert under Més detalls.
 */
@Composable
private fun AccountFormFields(
    form: AccountFormState,
    people: List<PersonSummary>,
    appearanceOpen: Boolean,
    onToggleAppearance: () -> Unit,
    onFormChange: (AccountFormState) -> Unit,
    onOwnershipChange: (AccountOwnershipKind) -> Unit,
    onCreatePerson: (String) -> Unit,
    onSave: () -> Unit,
) {
    val errorText = form.errorRes?.let { stringResource(it) }
    form.errorMessage?.let {
        InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_account)
    }
    EntityFormHeader(
        icon = form.iconKey?.let(::accountIcon) ?: accountTypeIcon(form.type),
        color = categoryColor(form.colorHex),
        name = form.name,
        onNameChange = { onFormChange(form.copy(name = it)) },
        nameLabel = stringResource(R.string.account_field_name),
        onTileClick = onToggleAppearance,
        nameError = errorText?.takeIf { form.errorField == AccountFormField.NAME },
    )
    EntityAppearancePickers(
        open = appearanceOpen,
        colorHex = form.colorHex,
        onColor = { onFormChange(form.copy(colorHex = it)) },
        iconOptions = AccountIconPalette,
        iconKey = form.iconKey,
        onIcon = { onFormChange(form.copy(iconKey = it)) },
    )
    FormSelect(
        label = stringResource(R.string.account_field_type),
        options = AccountType.entries.map { SelectOption(id = it.name, label = it.label()) },
        selectedId = form.type.name,
        onSelect = { id -> id?.let { onFormChange(form.copy(type = AccountType.valueOf(it))) } },
    )
    run {
        val startingBalanceError = form.errorField == AccountFormField.STARTING_BALANCE
        OutlinedTextField(
            value = form.startingBalance,
            onValueChange = { onFormChange(form.copy(startingBalance = it)) },
            label = { Text(text = stringResource(R.string.account_field_starting_balance)) },
            prefix = { Text(text = "€") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = doneKeyboardActions(onSave),
            singleLine = true,
            isError = startingBalanceError,
            supportingText = errorText?.takeIf { startingBalanceError }?.let { { Text(text = it) } },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(startingBalanceError),
        )
    }
    FormToggleRow(
        label = stringResource(R.string.account_field_default),
        checked = form.isDefault,
        onCheckedChange = { onFormChange(form.copy(isDefault = it)) },
    )
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
    if (form.errorField == AccountFormField.OWNERSHIP && errorText != null) {
        Text(text = errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    FormReveal(visible = form.ownershipKind == AccountOwnershipKind.SHARED) {
        AccountMembersSection(form = form, people = people, onFormChange = onFormChange, onCreatePerson = onCreatePerson)
    }

    // A threshold already set stays in view.
    val thresholdShown = form.showAdvanced || form.lowBalanceThreshold.isNotBlank()
    FormDisclosure(open = thresholdShown, onToggle = { onFormChange(form.copy(showAdvanced = !form.showAdvanced)) }) {
        val thresholdError = form.errorField == AccountFormField.LOW_BALANCE_THRESHOLD
        OutlinedTextField(
            value = form.lowBalanceThreshold,
            onValueChange = { onFormChange(form.copy(lowBalanceThreshold = it)) },
            label = { Text(text = stringResource(R.string.account_field_low_balance_threshold)) },
            prefix = { Text(text = "€") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = doneKeyboardActions(onSave),
            singleLine = true,
            isError = thresholdError,
            supportingText = errorText?.takeIf { thresholdError }?.let { { Text(text = it) } },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().scrollToWhen(thresholdError),
        )
    }
}

/**
 * Who owns the shared account and who pays how much by default, in the split editor's shape: a row
 * per member with their share and a way out, a row to add someone, then equal parts or typed
 * percentages (one figure each, or one for the balance and one for expenses), and a line only when
 * a column does not reach 100%.
 */
@Composable
private fun AccountMembersSection(
    form: AccountFormState,
    people: List<PersonSummary>,
    onFormChange: (AccountFormState) -> Unit,
    onCreatePerson: (String) -> Unit,
) {
    val colors = FinanceTheme.colors
    val members = form.members
    var custom by rememberSaveable(form.id) { mutableStateOf(members != members.splitEqually()) }
    var separate by rememberSaveable(form.id) {
        mutableStateOf(members.any { it.enabled && it.ownershipPercent != it.defaultExpensePercent })
    }
    var creatingPerson by remember { mutableStateOf(false) }
    val twoColumns = custom && separate
    fun change(next: List<AccountMemberFormState>) = onFormChange(form.copy(members = next))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.account_members_title), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.account_member_ownership_help),
            style = MaterialTheme.typography.bodySmall,
            color = colors.mutedText,
        )
        Column {
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Spacer(modifier = Modifier.weight(1f))
                    listOf(R.string.account_member_column_balance, R.string.account_member_expense_split).forEach {
                        Text(
                            text = stringResource(it),
                            modifier = Modifier.width(MEMBER_SHARE_WIDTH),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.mutedText,
                        )
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                }
            }
            members.forEachIndexed { index, member ->
                if (!member.enabled) return@forEachIndexed
                val name = member.name.ifBlank { stringResource(R.string.account_member_owner) }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PersonMonogram(
                        label = personInitial(name),
                        colorHex = people.firstOrNull { it.id == member.personId }?.color,
                        size = 32.dp,
                    )
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        !custom -> Text(
                            text = listOf(member).basisPointTotal { it.ownershipPercent }?.let(::formatBasisPointsCompact).orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        !separate -> ShareField(
                            value = member.ownershipPercent,
                            onValueChange = { change(members.withPercent(index, it, ownership = true, expenses = true)) },
                            suffix = "%",
                        )
                        else -> {
                            ShareField(
                                value = member.ownershipPercent,
                                onValueChange = { change(members.withPercent(index, it, ownership = true, expenses = false)) },
                                suffix = "%",
                                width = MEMBER_SHARE_WIDTH,
                            )
                            ShareField(
                                value = member.defaultExpensePercent,
                                onValueChange = { change(members.withPercent(index, it, ownership = false, expenses = true)) },
                                suffix = "%",
                                width = MEMBER_SHARE_WIDTH,
                            )
                        }
                    }
                    if (member.personId != null) {
                        IconButton(onClick = { change(members.withMember(index, enabled = false, equal = !custom)) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.split_remove_person),
                                tint = colors.mutedText,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(32.dp))
                    }
                }
                HorizontalDivider(color = colors.cardBorder)
            }
            Spacer(modifier = Modifier.height(10.dp))
            FormSelect(
                label = "",
                options = buildList {
                    members.forEach { member ->
                        if (member.enabled || member.personId == null) return@forEach
                        add(
                            SelectOption(
                                id = member.personId,
                                label = member.name,
                                leading = {
                                    PersonMonogram(
                                        label = personInitial(member.name),
                                        colorHex = people.firstOrNull { it.id == member.personId }?.color,
                                        size = 24.dp,
                                    )
                                },
                            ),
                        )
                    }
                    add(
                        SelectOption(
                            id = MEMBER_CREATE_PERSON,
                            label = stringResource(R.string.movement_create_person_title),
                            leading = {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        ),
                    )
                },
                selectedId = null,
                onSelect = { id ->
                    when (id) {
                        MEMBER_CREATE_PERSON -> creatingPerson = true
                        null -> Unit
                        else -> change(members.withMember(members.indexOfFirst { it.personId == id }, enabled = true, equal = !custom))
                    }
                },
                placeholder = stringResource(R.string.split_add_person),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SegmentedControl(
            options = listOf(false, true),
            selected = custom,
            label = { stringResource(if (it) R.string.split_method_percentage else R.string.split_method_equal) },
            onSelect = {
                custom = it
                if (!it) change(members.splitEqually())
            },
        )
        if (custom) {
            FormToggleRow(
                label = stringResource(R.string.account_member_separate_expenses),
                checked = separate,
                onCheckedChange = { on ->
                    separate = on
                    if (!on) change(members.map { it.copy(defaultExpensePercent = it.ownershipPercent) })
                },
            )
            if (separate) {
                Text(
                    text = stringResource(R.string.account_member_expense_split_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedText,
                )
            }
        }

        MemberPercentWarning(stringResource(R.string.account_member_ownership), members.basisPointTotal { it.ownershipPercent })
        MemberPercentWarning(stringResource(R.string.account_member_expense_split), members.basisPointTotal { it.defaultExpensePercent })
        OwnershipPreview(form)
        if (form.errorField == AccountFormField.MEMBERS && form.errorRes != null) {
            Text(stringResource(form.errorRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (creatingPerson) {
        CreatePersonDialog(
            onConfirm = { name ->
                creatingPerson = false
                onCreatePerson(name)
            },
            onDismiss = { creatingPerson = false },
        )
    }
}

private const val MEMBER_CREATE_PERSON = "__create_person__"
private val MEMBER_SHARE_WIDTH = 88.dp

/**
 * What the owner's percentage means for this account, as a share of its balance. The euro value is
 * left to the canonical view, which rounds it, rather than being worked out again here.
 */
@Composable
private fun OwnershipPreview(form: AccountFormState) {
    val ownerBasisPoints = form.members.filter { it.personId == null }.basisPointTotal { it.ownershipPercent } ?: return
    val balanceCents = form.currentBalanceCents ?: parseEuroCents(form.startingBalance, allowNegative = true) ?: return
    Text(
        text = stringResource(
            R.string.account_member_ownership_preview,
            formatEuroCents(balanceCents),
            formatBasisPointsCompact(ownerBasisPoints),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = FinanceTheme.colors.mutedText,
    )
}

/** Said only when a percentage column does not reach exactly 100%: a balanced one needs no comment. */
@Composable
private fun MemberPercentWarning(label: String, basisPoints: Long?) {
    val text = when {
        basisPoints == null -> stringResource(R.string.account_member_total_invalid)
        basisPoints == 10_000L -> return
        basisPoints < 10_000L -> stringResource(R.string.account_member_total_remaining, formatBasisPointsCompact(10_000L - basisPoints))
        else -> stringResource(R.string.account_member_total_excess, formatBasisPointsCompact(basisPoints - 10_000L))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = FinanceTheme.colors.alert,
            modifier = Modifier.size(16.dp),
        )
        Text(text = "$label: $text", color = FinanceTheme.colors.alert, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * A contribution or withdrawal laid out like the movement form: the shared account's tile beside
 * the concept over the amount, what it does to each balance, who and where, then the concept and
 * notes under Més detalls.
 */
@Composable
private fun ContributionFormFields(
    form: ContributionFormState,
    accounts: List<AccountSummary>,
    people: List<PersonSummary>,
    detailsOpen: Boolean,
    onToggleDetails: () -> Unit,
    onFormChange: (ContributionFormState) -> Unit,
) {
    val shared = accounts.firstOrNull { it.id == form.sharedAccountId }
    val sharedAccountName = shared?.name.orEmpty()
    val withdrawal = form.direction == ContributionDirection.OUT
    form.errorMessage?.let { InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_movement) }
    MovementFormHeader(
        icon = shared?.let { it.icon?.let(::accountIcon) ?: accountTypeIcon(it.type) } ?: accountIcon(null),
        iconColor = categoryColor(shared?.color),
        title = form.name.ifBlank { sharedAccountName },
        titleIsPlaceholder = form.name.isBlank(),
        amount = form.amount,
        onAmountChange = { onFormChange(form.copy(amount = it)) },
        amountColor = MaterialTheme.colorScheme.onSurface,
        amountError = form.errorRes?.let { stringResource(it) },
    )
    Text(
        text = stringResource(if (withdrawal) R.string.account_withdrawal_effect else R.string.account_contribution_effect),
        style = MaterialTheme.typography.bodySmall,
        color = FinanceTheme.colors.mutedText,
    )
    // What each balance does, as the amount itself: the resulting balances and the owner's share
    // stay with the canonical views rather than being worked out here.
    parseEuroCents(form.amount, allowNegative = false)?.takeIf { it > 0 }?.let { cents ->
        val signed = { value: Long -> formatEuroCents(value).let { if (value > 0) "+$it" else it } }
        val sharedChange = signed(if (withdrawal) -cents else cents)
        val ownerAccountName = accounts.firstOrNull { it.id == form.sourceAccountId }?.name
            ?.takeIf { form.personId == null }
        Text(
            text = if (ownerAccountName != null) {
                stringResource(
                    R.string.account_contribution_changes_both,
                    sharedAccountName,
                    sharedChange,
                    ownerAccountName,
                    signed(if (withdrawal) cents else -cents),
                )
            } else {
                stringResource(R.string.account_contribution_changes_shared, sharedAccountName, sharedChange)
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FormSelect(
            label = stringResource(if (withdrawal) R.string.account_withdrawal_member else R.string.account_contribution_member),
            options = buildList {
                add(SelectOption(id = null, label = stringResource(R.string.account_member_owner)))
                people.forEach { add(SelectOption(id = it.id, label = it.name)) }
            },
            selectedId = form.personId,
            onSelect = { onFormChange(form.copy(personId = it, sourceAccountId = null)) },
            modifier = Modifier.weight(1f),
        )
        FormDatePicker(
            label = stringResource(R.string.movement_field_date),
            date = form.date,
            onDateChange = { onFormChange(form.copy(date = it)) },
            modifier = Modifier.weight(1f),
        )
    }
    if (form.personId == null) {
        val noSource = stringResource(R.string.account_contribution_external_source)
        FormSelect(
            label = stringResource(if (withdrawal) R.string.account_withdrawal_destination else R.string.account_contribution_source),
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
    // What is already written stays in view.
    val detailsShown = detailsOpen || form.name.isNotBlank() || form.notes.isNotBlank()
    FormDisclosure(open = detailsShown, onToggle = onToggleDetails) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { onFormChange(form.copy(name = it)) },
            label = { Text(stringResource(R.string.movement_field_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.notes,
            onValueChange = { onFormChange(form.copy(notes = it)) },
            label = { Text(stringResource(R.string.movement_field_notes)) },
            minLines = 2,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// Account page
// ---------------------------------------------------------------------------

@Composable
private fun AccountFlowScreen(
    detail: AccountFlowDetailState,
    allocation: AccountAllocation?,
    onOpenGoal: (goalId: String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onViewAnalysis: () -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    onAddMovement: () -> Unit,
    onContribution: () -> Unit,
    onWithdrawal: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = detail.account
    val shared = account.ownershipKind == AccountOwnershipKind.SHARED
    val today = remember { LocalDate.now() }
    val belowThreshold = account.lowBalanceThresholdCents?.let { account.currentBalanceCents < it } ?: false

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
    ) {
        item {
            EntityDetailTopBar(
                onBack = onBack,
                menu = buildList {
                    // Taking money back out is rarer than paying in, so it waits behind the overflow.
                    if (shared) add(EntityMenuAction(stringResource(R.string.account_withdrawal_action), onWithdrawal))
                    add(EntityMenuAction(stringResource(R.string.common_edit), onEdit))
                    add(EntityMenuAction(stringResource(R.string.common_archive), onArchive, destructive = true))
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            EntityDetailHeader(
                leading = {
                    IdentityIconTile(
                        icon = if (account.icon != null) accountIcon(account.icon) else accountTypeIcon(account.type),
                        color = categoryColor(account.color),
                        size = EntityHeaderMarkSize,
                    )
                },
                name = account.name,
                subtitle = listOfNotNull(
                    account.type.label(),
                    stringResource(R.string.account_shared_badge).takeIf { shared },
                    stringResource(R.string.account_default_badge).takeIf { account.isDefault },
                ).joinToString(" · "),
                figures = {
                    EntityFigure(label = stringResource(R.string.account_balance_label)) {
                        MoneyText(
                            cents = account.currentBalanceCents,
                            color = if (account.currentBalanceCents < 0 || belowThreshold) {
                                FinanceTheme.colors.debt
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.headlineLarge,
                            signed = account.currentBalanceCents < 0,
                        )
                    }
                    if (shared) {
                        // Right-aligned, over the owner's end of the ownership bar below it.
                        Box(modifier = Modifier.weight(1f))
                        EntityFigure(
                            label = stringResource(R.string.account_detail_your_share),
                            horizontalAlignment = Alignment.End,
                        ) {
                            MoneyText(
                                cents = account.ownerValueCents,
                                style = MaterialTheme.typography.headlineSmall,
                                signed = account.ownerValueCents < 0,
                            )
                        }
                    }
                },
                details = if (shared && account.members.isNotEmpty()) {
                    { AccountOwnership(account.members) }
                } else {
                    null
                },
                link = {
                    EntityActionPill(
                        text = stringResource(R.string.account_flow_view_analysis),
                        onClick = onViewAnalysis,
                        chevron = true,
                    )
                },
                actions = {
                    EntityActionPill(
                        text = stringResource(R.string.entity_add_movement),
                        onClick = onAddMovement,
                        icon = Icons.Outlined.Add,
                    )
                    if (shared) {
                        EntityActionPill(
                            text = stringResource(R.string.movement_type_contribution),
                            onClick = onContribution,
                        )
                    }
                },
            )
        }
        detail.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_accounts,
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        if (detail.goals.isNotEmpty()) {
            item {
                AccountGoals(
                    goals = detail.goals,
                    allocation = allocation,
                    onOpenGoal = onOpenGoal,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        when {
            detail.isLoading -> item { LedgerMessage(stringResource(R.string.account_flow_loading)) }
            detail.entries.isEmpty() -> item { LedgerMessage(stringResource(R.string.account_flow_empty)) }
            // Each row leads with what the movement did to this account, so the ledger reconciles
            // with the balance above it.
            else -> dayGroupedRows(
                rows = detail.entries,
                dateOf = { it.movement.date },
                key = { it.movement.id },
                today = today,
            ) { entry, position ->
                MovementListItem(
                    movement = entry.movement,
                    onClick = { onMovementDetail(entry.movement) },
                    accountDeltaCents = entry.deltaCents,
                    showDate = false,
                    position = position,
                )
            }
        }
    }
}

/**
 * The goals saving in this account, each a tap from its page: one that owns the whole account, or
 * those reserving part of it with what they hold, under what is reserved and what is still free.
 */
@Composable
private fun AccountGoals(
    goals: List<AccountGoalReservation>,
    allocation: AccountAllocation?,
    onOpenGoal: (goalId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.account_goals_title))
        allocation?.takeIf { it.allocatedCents != 0L }?.let {
            Row(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                Text(
                    text = stringResource(R.string.account_goals_reserved, formatEuroCents(it.allocatedCents)) + " · ",
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.account_goals_free, formatEuroCents(it.unallocatedCents)),
                    color = if (it.unallocatedCents < 0L) FinanceTheme.colors.debt else FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        goals.forEachIndexed { index, goal ->
            EntityListRow(
                leading = { IdentityIconTile(icon = goalIcon(goal.icon), color = categoryColor(goal.color)) },
                title = goal.name,
                subtitle = stringResource(R.string.account_goals_whole).takeIf { goal.dedicated },
                isLast = index == goals.lastIndex,
                onClick = { onOpenGoal(goal.goalId) },
                trailing = { MoneyText(cents = goal.reservedCents, style = MaterialTheme.typography.titleMedium) },
            )
        }
    }
}

/** Who owns how much of a shared account: one bar, one coloured mark per member beneath it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountOwnership(members: List<AccountMember>) {
    val owner = stringResource(R.string.account_member_owner)
    val palette = listOf(
        FinanceTheme.colors.shared,
        FinanceTheme.colors.transfer,
        FinanceTheme.colors.settlement,
        FinanceTheme.colors.refund,
    )
    // The other members first, each in a distinct colour; the owner last, in the app's own, so
    // their part ends under the "La teva part" figure above the bar.
    val ordered = members.sortedBy { it.personId == null }
    var next = 0
    val colors = ordered.map { if (it.personId == null) MaterialTheme.colorScheme.primary else palette[next++ % palette.size] }
    val summary = ordered.joinToString(", ") { "${it.personName ?: owner} ${formatBasisPointsCompact(it.ownershipBasisPoints)}" }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedDistributionBar(
            segments = ordered.mapIndexed { index, member ->
                DistributionSegment(color = colors[index], fraction = member.ownershipBasisPoints / 10_000f)
            },
            contentDescription = stringResource(R.string.account_members_summary, summary),
        )
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ordered.forEachIndexed { index, member ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clearAndSetSemantics { },
                ) {
                    Box(modifier = Modifier.size(8.dp).background(colors[index], CircleShape))
                    Text(
                        text = member.personName ?: owner,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = formatBasisPointsCompact(member.ownershipBasisPoints),
                        style = MaterialTheme.typography.labelMedium,
                        color = FinanceTheme.colors.mutedText,
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerMessage(text: String) {
    Text(
        text = text,
        color = FinanceTheme.colors.mutedText,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 12.dp),
    )
}
