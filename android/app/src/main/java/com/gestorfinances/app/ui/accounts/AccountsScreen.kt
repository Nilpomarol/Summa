package com.gestorfinances.app.ui.accounts

import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.movement_type_contribution
import org.jetbrains.compose.resources.stringResource
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

// ---------------------------------------------------------------------------
// Account page
// ---------------------------------------------------------------------------
