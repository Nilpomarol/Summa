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
