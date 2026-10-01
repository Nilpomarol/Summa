package com.gestorfinances.app.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.GoalAllocation
import com.gestorfinances.app.data.repository.GoalStatus
import com.gestorfinances.app.data.repository.GoalSummary
import com.gestorfinances.app.domain.rules.GoalFundingMode
import com.gestorfinances.app.domain.rules.GoalProgress
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.EntityActionPill
import com.gestorfinances.app.ui.common.EntityDetailHeader
import com.gestorfinances.app.ui.common.EntityDetailTopBar
import com.gestorfinances.app.ui.common.EntityFigure
import com.gestorfinances.app.ui.common.EntityHeaderMarkSize
import com.gestorfinances.app.ui.common.EntityListRow
import com.gestorfinances.app.ui.common.EntityMenuAction
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementRowPosition
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.dayGroupedRows
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatPercentLabel
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.themedIdentityColor

/** A goal's own page: what it holds against its target, the pace to get there, and its reservations. */
@Composable
fun GoalDetailPage(
    goalId: String,
    onBack: () -> Unit,
    viewModel: GoalsViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onOpenAccount: (accountId: String) -> Unit,
    onDeleteCommitted: DeleteUndoHandler,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, goalId, dataVersion) {
        viewModel.onGoalOpened(goalId)
    }

    val goal = state.goals.firstOrNull { it.id == goalId }
    val detail = state.detail
    if (goal == null || detail == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageHeaderRow(onBack = onBack)
            state.errorMessage?.let { message ->
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_goals,
                    onRetry = { viewModel.onGoalOpened(goalId) },
                )
            }
        }
    } else {
        GoalDetailContent(
            goal = goal,
            detail = detail,
            state = state,
            onBack = onBack,
            onRetry = { viewModel.onGoalOpened(goalId) },
            onEdit = { viewModel.onEditClicked(goal) },
            onStatusChange = { viewModel.onStatusChanged(goal, it) },
            onComplete = { viewModel.onCompleteClicked(goal) },
            onArchive = { viewModel.onArchiveClicked(goal) },
            onReserve = { viewModel.onAddAllocationClicked(goal) },
            onRelease = { viewModel.onAddAllocationClicked(goal, release = true) },
            onEditAllocation = viewModel::onEditAllocationClicked,
            onOpenAccount = onOpenAccount,
            modifier = modifier,
        )
    }

    GoalFormSheet(state = state, viewModel = viewModel)
    state.allocationForm?.let { form ->
        AllocationFormHost(
            form = form,
            state = state,
            viewModel = viewModel,
            onDelete = { viewModel.onDeleteAllocationClicked(it, onDeleteCommitted) },
        )
    }

    state.completeCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = viewModel::onCompleteDismissed,
            title = { Text(text = stringResource(R.string.goal_complete_title)) },
            text = { Text(text = stringResource(R.string.goal_complete_body, formatEuroCents(candidate.savedCents))) },
            confirmButton = {
                TextButton(onClick = { viewModel.onCompleteConfirmed(release = true) }) {
                    Text(text = stringResource(R.string.goal_complete_release))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onCompleteConfirmed(release = false) }) {
                    Text(text = stringResource(R.string.goal_complete_keep))
                }
            },
        )
    }

    state.archiveCandidate?.let {
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(R.string.goal_archive_confirm_title)) },
            text = { Text(text = stringResource(R.string.goal_archive_warning)) },
            confirmButton = {
                DestructiveTextButton(
                    onClick = {
                        viewModel.onArchiveConfirmed { undo ->
                            onDeleteCommitted(undo)
                            onBack()
                        }
                    },
                ) {
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
private fun GoalDetailContent(
    goal: GoalSummary,
    detail: GoalDetailState,
    state: GoalsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onStatusChange: (GoalStatus) -> Unit,
    onComplete: () -> Unit,
    onArchive: () -> Unit,
    onReserve: () -> Unit,
    onRelease: () -> Unit,
    onEditAllocation: (GoalAllocation) -> Unit,
    onOpenAccount: (accountId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = goal.progress(state.today)
    val sharesAccounts = goal.fundingMode == GoalFundingMode.ALLOCATIONS
    val completed = goal.status == GoalStatus.COMPLETED
    val byAccount = detail.reservations.filterValues { it != 0L }
    // An account now holding less than its goals reserve; one is enough to say so.
    val shortfall = byAccount.keys.firstNotNullOfOrNull { accountId ->
        state.accountAllocations[accountId]?.unallocatedCents?.takeIf { it < 0L }?.let { accountId to -it }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
    ) {
        item {
            EntityDetailTopBar(
                onBack = onBack,
                menu = buildList {
                    add(EntityMenuAction(stringResource(R.string.common_edit), onEdit))
                    if (goal.status == GoalStatus.ACTIVE) {
                        add(EntityMenuAction(stringResource(R.string.goal_pause), onClick = { onStatusChange(GoalStatus.PAUSED) }))
                    } else {
                        add(EntityMenuAction(stringResource(R.string.goal_resume), onClick = { onStatusChange(GoalStatus.ACTIVE) }))
                    }
                    if (!completed) add(EntityMenuAction(stringResource(R.string.goal_mark_completed), onComplete))
                    add(EntityMenuAction(stringResource(R.string.common_archive), onArchive, destructive = true))
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            EntityDetailHeader(
                leading = {
                    IdentityIconTile(
                        icon = goalIcon(goal.icon),
                        color = categoryColor(goal.color),
                        size = EntityHeaderMarkSize,
                    )
                },
                name = goal.name,
                subtitle = listOfNotNull(
                    if (sharesAccounts) {
                        stringResource(R.string.goal_subtitle_allocations)
                    } else {
                        stringResource(R.string.goal_subtitle_dedicated, goal.accountName.orEmpty())
                    },
                    when (goal.status) {
                        GoalStatus.ACTIVE -> null
                        GoalStatus.PAUSED -> stringResource(R.string.goal_section_paused)
                        GoalStatus.COMPLETED -> stringResource(R.string.goal_status_completed)
                    },
                ).joinToString(" · "),
                figures = {
                    EntityFigure(label = stringResource(R.string.goal_detail_saved)) {
                        MoneyText(cents = goal.savedCents, style = MaterialTheme.typography.headlineLarge)
                    }
                    Box(modifier = Modifier.weight(1f))
                    EntityFigure(label = stringResource(R.string.goal_detail_target), horizontalAlignment = Alignment.End) {
                        MoneyText(cents = goal.targetAmountCents, style = MaterialTheme.typography.headlineSmall)
                    }
                },
                details = { GoalProgressDetails(goal = goal, progress = progress) },
                actions = if (sharesAccounts || (progress.reached && !completed)) {
                    {
                        if (sharesAccounts && !completed) {
                            EntityActionPill(text = stringResource(R.string.goal_action_reserve), onClick = onReserve, icon = Icons.Outlined.Add)
                        }
                        if (sharesAccounts && goal.savedCents > 0L) {
                            EntityActionPill(text = stringResource(R.string.goal_action_release), onClick = onRelease, icon = Icons.Outlined.Remove)
                        }
                        if (progress.reached && !completed) {
                            EntityActionPill(text = stringResource(R.string.goal_action_complete), onClick = onComplete, icon = Icons.Outlined.Check)
                        }
                    }
                } else {
                    null
                },
                // A dedicated goal's history is its account's own ledger, while that account is live.
                link = goal.accountId?.takeIf { id -> !sharesAccounts && state.accounts.any { it.id == id } }?.let { accountId ->
                    {
                        EntityActionPill(
                            text = goal.accountName.orEmpty(),
                            onClick = { onOpenAccount(accountId) },
                            chevron = true,
                        )
                    }
                },
            )
        }
        detail.errorRes?.let { error ->
            item {
                InlineBanner(
                    kind = BannerKind.Error,
                    text = stringResource(error),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = onRetry,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        shortfall?.let { (accountId, cents) ->
            item {
                InlineBanner(
                    kind = BannerKind.Alert,
                    text = stringResource(
                        R.string.goal_shortfall,
                        state.accounts.firstOrNull { it.id == accountId }?.name.orEmpty(),
                        formatEuroCents(cents),
                    ),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        if (!sharesAccounts) {
            item {
                Text(
                    text = stringResource(R.string.goal_dedicated_explainer, goal.accountName.orEmpty()),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 28.dp),
                )
            }
            return@LazyColumn
        }

        // Money kept in more than one account says how much sits where.
        if (byAccount.size > 1) {
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item { SectionHeader(title = stringResource(R.string.goal_detail_by_account)) }
            val rows = byAccount.entries.sortedByDescending { it.value }
            rows.forEachIndexed { index, (accountId, cents) ->
                item(key = "account-$accountId") {
                    val account = state.accounts.firstOrNull { it.id == accountId }
                    EntityListRow(
                        leading = { AccountMark(account) },
                        title = account?.name ?: detail.allocations.firstOrNull { it.accountId == accountId }?.accountName.orEmpty(),
                        isLast = index == rows.lastIndex,
                        onClick = account?.let { { onOpenAccount(accountId) } },
                        trailing = { MoneyText(cents = cents, style = MaterialTheme.typography.titleMedium) },
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
        item { SectionHeader(title = stringResource(R.string.goal_allocations_title)) }
        if (detail.allocations.isEmpty()) {
            if (!detail.isLoading) {
                item {
                    Text(
                        text = stringResource(R.string.goal_allocations_empty),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        } else {
            dayGroupedRows(
                rows = detail.allocations,
                dateOf = { it.date },
                key = { it.id },
                today = state.today,
            ) { allocation, position ->
                AllocationRow(
                    allocation = allocation,
                    account = state.accounts.firstOrNull { it.id == allocation.accountId },
                    isLast = position == MovementRowPosition.LAST || position == MovementRowPosition.ONLY,
                    onClick = { onEditAllocation(allocation) },
                )
            }
        }
    }
}

/** The bar of what the goal holds, its share and what is missing, and the pace its date asks for. */
@Composable
private fun GoalProgressDetails(goal: GoalSummary, progress: GoalProgress) {
    val colors = FinanceTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BudgetProgressBar(
            fraction = progressFraction(goal.savedCents, goal.targetAmountCents),
            color = if (progress.reached) colors.income else themedIdentityColor(categoryColor(goal.color)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (goal.savedCents <= 0L) "0%" else formatPercentLabel(goal.savedCents.toFloat() / goal.targetAmountCents),
                modifier = Modifier.weight(1f),
                color = colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
            )
            val completed = goal.status == GoalStatus.COMPLETED
            Text(
                text = when {
                    completed -> stringResource(R.string.goal_status_completed)
                    progress.reached -> stringResource(R.string.goal_state_reached)
                    else -> stringResource(R.string.goal_remaining, formatEuroCents(progress.remainingCents))
                },
                color = if (completed || progress.reached) colors.income else colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        val targetDate = goal.targetDate
        if (targetDate != null && !progress.reached && goal.status == GoalStatus.ACTIVE) {
            Text(
                text = if (progress.overdue) {
                    stringResource(R.string.goal_pace_overdue, formatCompactDate(targetDate), formatEuroCents(progress.remainingCents))
                } else {
                    stringResource(
                        R.string.goal_pace,
                        formatEuroCents(progress.monthlyPaceCents ?: 0L),
                        progress.monthsRemaining ?: 0,
                        formatCompactDate(targetDate),
                    )
                },
                color = if (progress.overdue) colors.debt else colors.mutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** One reservation or release: the account it touched, its note, and the signed amount. */
@Composable
private fun AllocationRow(allocation: GoalAllocation, account: AccountSummary?, isLast: Boolean, onClick: () -> Unit) {
    val release = allocation.amountCents < 0L
    EntityListRow(
        leading = { AccountMark(account) },
        title = allocation.accountName,
        subtitle = allocation.notes ?: stringResource(
            if (release) R.string.goal_allocation_kind_release else R.string.goal_allocation_kind_reserve,
        ),
        isLast = isLast,
        onClick = onClick,
        trailing = {
            MoneyText(
                cents = allocation.amountCents,
                color = if (release) FinanceTheme.colors.mutedText else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                signed = true,
            )
        },
    )
}

@Composable
private fun AccountMark(account: AccountSummary?) {
    IdentityIconTile(
        icon = account?.let { it.icon?.let(::accountIcon) ?: accountTypeIcon(it.type) } ?: accountIcon(null),
        color = categoryColor(account?.color),
    )
}
