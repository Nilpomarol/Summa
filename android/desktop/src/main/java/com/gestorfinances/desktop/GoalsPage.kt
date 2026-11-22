package com.gestorfinances.desktop

import com.gestorfinances.app.ui.common.OpenDialogs
import org.jetbrains.compose.resources.pluralStringResource
import com.gestorfinances.ui.resources.goal_hero_next_overdue
import com.gestorfinances.ui.resources.goal_hero_next
import com.gestorfinances.ui.resources.goal_hero_caption
import com.gestorfinances.desktop.resources.goals_of_target
import com.gestorfinances.desktop.resources.goals_column_running
import com.gestorfinances.app.ui.common.heroTint
import com.gestorfinances.app.ui.common.formatPercentLabel
import com.gestorfinances.app.ui.common.ListHero
import com.gestorfinances.app.ui.common.HeroStatBox
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.AppIconButton
import com.gestorfinances.app.data.repository.GoalAllocation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.gestorfinances.app.ui.common.AppTextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.domain.rules.GoalFundingMode
import com.gestorfinances.app.data.repository.GoalStatus
import com.gestorfinances.app.data.repository.GoalSummary
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.DestructiveButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.rememberFormDismissGuard
import com.gestorfinances.app.ui.goals.AllocationFormSheet
import com.gestorfinances.app.ui.goals.GoalFormSheet
import com.gestorfinances.app.ui.goals.GoalProgressDetails
import com.gestorfinances.app.ui.goals.GoalsUiState
import com.gestorfinances.app.ui.goals.GoalsViewModel
import com.gestorfinances.app.ui.goals.compareValues
import com.gestorfinances.app.ui.goals.goalIcon
import com.gestorfinances.app.ui.goals.goalLine
import com.gestorfinances.app.ui.goals.progressFraction
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.desktop.resources.column_account
import com.gestorfinances.desktop.resources.column_date
import com.gestorfinances.desktop.resources.column_free
import com.gestorfinances.desktop.resources.column_goal
import com.gestorfinances.desktop.resources.column_note
import com.gestorfinances.desktop.resources.column_progress
import com.gestorfinances.desktop.resources.column_reserved
import com.gestorfinances.desktop.resources.goals_accounts_title
import com.gestorfinances.ui.resources.account_balance_label
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_cancel
import com.gestorfinances.ui.resources.common_edit
import com.gestorfinances.ui.resources.common_retry
import com.gestorfinances.ui.resources.failure_load_goals
import com.gestorfinances.ui.resources.goal_action_complete
import com.gestorfinances.ui.resources.goal_action_release
import com.gestorfinances.ui.resources.goal_action_reserve
import com.gestorfinances.ui.resources.goal_allocation_kind_release
import com.gestorfinances.ui.resources.goal_allocation_kind_reserve
import com.gestorfinances.ui.resources.goal_allocations_empty
import com.gestorfinances.ui.resources.goal_allocations_title
import com.gestorfinances.ui.resources.goal_archive_confirm_title
import com.gestorfinances.ui.resources.goal_archive_warning
import com.gestorfinances.ui.resources.goal_complete_body
import com.gestorfinances.ui.resources.goal_complete_keep
import com.gestorfinances.ui.resources.goal_complete_release
import com.gestorfinances.ui.resources.goal_complete_title
import com.gestorfinances.ui.resources.goal_dedicated_explainer
import com.gestorfinances.ui.resources.goal_detail_saved
import com.gestorfinances.ui.resources.goal_detail_target
import com.gestorfinances.ui.resources.goal_empty_body
import com.gestorfinances.ui.resources.goal_empty_title
import com.gestorfinances.ui.resources.goal_hero_free
import com.gestorfinances.ui.resources.goal_hero_remaining
import com.gestorfinances.ui.resources.goal_hero_reserved
import com.gestorfinances.ui.resources.goal_list_add
import com.gestorfinances.ui.resources.goal_list_title
import com.gestorfinances.ui.resources.goal_pause
import com.gestorfinances.ui.resources.goal_resume
import com.gestorfinances.ui.resources.goal_section_active
import com.gestorfinances.ui.resources.goal_section_completed
import com.gestorfinances.ui.resources.goal_section_paused
import com.gestorfinances.ui.resources.goal_shortfall
import com.gestorfinances.ui.resources.goal_subtitle_allocations
import com.gestorfinances.ui.resources.goal_subtitle_dedicated
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

/**
 * Savings goals as a page of cards under what they hold on the dark panel, then what only fits
 * here: each account goals reserve from, with what it holds, what is reserved and what is still
 * free. A goal opens over the whole page with its progress and its reservations.
 */
@Composable
fun GoalsPage(
    viewModel: GoalsViewModel,
    dataVersion: Long,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onOpenAccount: (accountId: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel, dataVersion) { viewModel.onScreenShown() }
    LaunchedEffect(viewModel, selectedId, dataVersion) { selectedId?.let(viewModel::onGoalOpened) }
    val goal = state.goals.firstOrNull { it.id == selectedId }

    if (goal == null) {
        GoalsOverview(state, viewModel, onSelect, onOpenAccount)
    } else {
        GoalPane(goal, state, viewModel, onBack = { onSelect(null) }, onOpenAccount = onOpenAccount)
    }

    GoalFormSheet(state = state, viewModel = viewModel)
    state.allocationForm?.let { form ->
        // Reserving from a card, the goal's page (and its figures by account) may not be the one loaded.
        val detail = state.detail?.takeIf { it.goalId == form.goalId }
        val editing = detail?.allocations?.firstOrNull { it.id == form.id }
        val requestDismiss = rememberFormDismissGuard(
            formKey = form.id ?: "new-allocation",
            currentValue = form,
            hasMeaningfulChanges = { initial, current -> initial.compareValues() != current.compareValues() },
            onDiscard = viewModel::onAllocationFormDismissed,
        )
        AllocationFormSheet(
            form = form,
            goal = state.goals.firstOrNull { it.id == form.goalId },
            accounts = state.accounts.filter { it.id !in state.dedicatedAccountIds },
            accountAllocations = state.accountAllocations,
            reservations = detail?.reservations.orEmpty(),
            editingAllocation = editing,
            onFormChange = viewModel::onAllocationFormChanged,
            onDismiss = requestDismiss,
            onSave = { viewModel.onSaveAllocationClicked() },
            onConfirmOverAllocation = { viewModel.onSaveAllocationClicked(confirmOverAllocation = true) },
            onDelete = editing?.let { allocation -> { viewModel.onDeleteAllocationClicked(allocation) {} } },
        )
    }
    state.completeCandidate?.let { candidate ->
        OpenDialogs.Track()
        AlertDialog(
            onDismissRequest = viewModel::onCompleteDismissed,
            title = { Text(stringResource(SharedRes.string.goal_complete_title)) },
            text = { Text(stringResource(SharedRes.string.goal_complete_body, formatEuroCents(candidate.savedCents))) },
            confirmButton = {
                AppTextButton(onClick = { viewModel.onCompleteConfirmed(release = true) }) { Text(stringResource(SharedRes.string.goal_complete_release)) }
            },
            dismissButton = {
                AppTextButton(onClick = { viewModel.onCompleteConfirmed(release = false) }) { Text(stringResource(SharedRes.string.goal_complete_keep)) }
            },
        )
    }
    state.archiveCandidate?.let { candidate ->
        OpenDialogs.Track()
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(stringResource(SharedRes.string.goal_archive_confirm_title)) },
            text = { Text(stringResource(SharedRes.string.goal_archive_warning)) },
            confirmButton = {
                AppTextButton(onClick = { viewModel.onArchiveConfirmed { if (selectedId == candidate.id) onSelect(null) } }) {
                    Text(stringResource(SharedRes.string.common_archive), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { AppTextButton(onClick = viewModel::onArchiveDismissed) { Text(stringResource(SharedRes.string.common_cancel)) } },
        )
    }
}

@Composable
private fun GoalsOverview(state: GoalsUiState, viewModel: GoalsViewModel, onSelect: (String?) -> Unit, onOpenAccount: (String) -> Unit) {
    val muted = FinanceTheme.colors.mutedText
    ScrollPage {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(SharedRes.string.goal_list_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            PrimaryButton(text = stringResource(SharedRes.string.goal_list_add), onClick = viewModel::onAddClicked, leadingIcon = Icons.Outlined.Add)
        }
        if (state.errorMessage != null) {
            InlineBanner(
                kind = BannerKind.Error,
                text = stringResource(SharedRes.string.failure_load_goals),
                actionLabel = stringResource(SharedRes.string.common_retry),
                onAction = viewModel::onScreenShown,
            )
        }
        state.actionErrorRes?.let { InlineBanner(kind = BannerKind.Error, text = stringResource(it)) }
        if (state.isLoading) return@ScrollPage
        if (state.goals.isEmpty()) {
            Text(stringResource(SharedRes.string.goal_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(SharedRes.string.goal_empty_body), color = muted)
            return@ScrollPage
        }
        GoalsHero(state, onSelect)
        // Each state is a group of its own, under its name and how many it holds.
        listOf(
            SharedRes.string.goal_section_active to state.activeGoals,
            SharedRes.string.goal_section_paused to state.pausedGoals,
            SharedRes.string.goal_section_completed to state.completedGoals,
        ).filter { it.second.isNotEmpty() }.forEach { (title, goals) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(goals.size.toString(), style = MaterialTheme.typography.bodyMedium, color = muted)
                }
                CardGrid(goals, minWidth = 300.dp) { goal, modifier ->
                    GoalCard(goal, state, onOpen = { onSelect(goal.id) }, onReserve = { viewModel.onAddAllocationClicked(goal) }, modifier = modifier)
                }
            }
        }
        AccountsTable(state, onOpenAccount)
    }
}

/**
 * What the active goals hold on the dark panel, as the phone's goals page opens: how far that is
 * towards them all, what is missing and what is still free in their accounts, and the next one due.
 */
@Composable
private fun GoalsHero(state: GoalsUiState, onOpen: (String) -> Unit) {
    val colors = FinanceTheme.colors
    val active = state.activeGoals
    val saved = active.sumOf { it.savedCents }
    val target = active.sumOf { it.targetAmountCents }
    val next = active.filter { it.targetDate != null && it.remainingCents > 0L }.minByOrNull { it.targetDate.orEmpty() }
    ListHero(
        eyebrow = stringResource(SharedRes.string.goal_hero_reserved),
        cents = saved,
        watermark = Icons.Outlined.Savings,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val caption = pluralStringResource(SharedRes.plurals.goal_hero_caption, active.size, formatEuroCents(target), active.size)
        if (target > 0L) {
            // What is reserved, goal by goal in each one's colour, out of all they aim for.
            Spacer(Modifier.height(12.dp))
            HeroSegments(
                active.map { goal -> HeroSegment(goal.name, goal.savedCents, categoryColor(goal.color)) { onOpen(goal.id) } },
                totalCents = maxOf(target, saved),
                rest = caption,
            )
        } else {
            HeroCaption(caption)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroStatBox(stringResource(SharedRes.string.goal_detail_target), target, colors.heroOnSurface)
            HeroStatBox(stringResource(SharedRes.string.goal_hero_remaining), active.sumOf { it.remainingCents }, colors.heroOnSurface)
            if (state.accountAllocations.values.any { it.allocatedCents != 0L }) {
                val free = state.freeInReservedAccountsCents
                HeroStatBox(stringResource(SharedRes.string.goal_hero_free), free, if (free < 0L) colors.heroDebt else colors.heroIncome)
            }
        }
        next?.let { goal ->
            val progress = goal.progress(state.today)
            Spacer(Modifier.height(12.dp))
            HeroCaption(
                if (progress.overdue) {
                    stringResource(SharedRes.string.goal_hero_next_overdue, goal.name, formatCompactDate(goal.targetDate.orEmpty()))
                } else {
                    stringResource(
                        SharedRes.string.goal_hero_next,
                        goal.name,
                        formatEuroCents(progress.monthlyPaceCents ?: 0L),
                        formatCompactDate(goal.targetDate.orEmpty()),
                    )
                },
            )
        }
    }
}

/** As many cards to a row as fit at [minWidth], shared out evenly between the rows, each row filling the width. */
@Composable
internal fun <T> CardGrid(items: List<T>, minWidth: Dp, card: @Composable (T, Modifier) -> Unit) {
    val gap = 12.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val most = ((maxWidth + gap) / (minWidth + gap)).toInt().coerceAtLeast(1)
        val rows = ((items.size + most - 1) / most).coerceAtLeast(1)
        // The first rows take the one more when they do not divide evenly: 10 in threes is 3, 3, 2, 2.
        val sizes = List(rows) { index -> items.size / rows + if (index < items.size % rows) 1 else 0 }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            var from = 0
            sizes.forEach { size ->
                val row = items.subList(from, from + size)
                from += size
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { item -> card(item, Modifier.weight(1f).fillMaxHeight()) }
                    // One card alone does not stretch across the whole page.
                    if (items.size == 1 && most > 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * A goal's card: what it holds of its target as a figure and a bar, how it is going in a line (in
 * the debt colour once its date has passed, in the income one once reached), and the way to
 * reserve for it without opening it.
 */
@Composable
private fun GoalCard(goal: GoalSummary, state: GoalsUiState, onOpen: () -> Unit, onReserve: () -> Unit, modifier: Modifier) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val progress = goal.progress(state.today)
    val line = goalLine(
        goal,
        progress,
        state.fundingAccounts[goal.id].orEmpty().mapNotNull { id -> state.accounts.firstOrNull { it.id == id }?.name },
    )
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (hovered) 2.dp else 1.dp, if (hovered) accent else colors.cardBorder),
        shadowElevation = if (hovered) 6.dp else 0.dp,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IdentityIconTile(icon = goalIcon(goal.icon), color = categoryColor(goal.color), size = 40.dp)
                Text(goal.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = if (hovered) accent else muted, modifier = Modifier.size(20.dp))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyText(cents = goal.savedCents, style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(Res.string.goals_of_target, formatEuroCents(goal.targetAmountCents)),
                    Modifier.weight(1f).padding(bottom = 3.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    maxLines = 1,
                )
                Text(
                    formatPercentLabel(progressFraction(goal.savedCents, goal.targetAmountCents)),
                    Modifier.padding(bottom = 3.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (progress.reached) colors.income else muted,
                )
            }
            BudgetProgressBar(
                fraction = progressFraction(goal.savedCents, goal.targetAmountCents),
                color = if (progress.reached) colors.income else themedIdentityColor(categoryColor(goal.color)),
                modifier = Modifier.height(8.dp),
            )
            Row(Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    line.orEmpty(),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (progress.reached || progress.overdue) FontWeight.Medium else null,
                    color = when {
                        goal.status != GoalStatus.ACTIVE -> muted
                        progress.reached -> colors.income
                        progress.overdue && goal.targetDate != null -> colors.debt
                        else -> muted
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (goal.fundingMode == GoalFundingMode.ALLOCATIONS && goal.status == GoalStatus.ACTIVE) {
                    SecondaryButton(text = stringResource(SharedRes.string.goal_action_reserve), onClick = onReserve)
                }
            }
        }
    }
}

/**
 * Where the reserved money sits: each account goals reserve from, how much of what it holds is
 * reserved as a bar, and what is left free. An account in the red holds less than its goals reserve.
 */
@Composable
private fun AccountsTable(state: GoalsUiState, onOpenAccount: (String) -> Unit) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val header = MaterialTheme.typography.labelMedium
    val reserving = state.accountAllocations.values.filter { it.allocatedCents != 0L }
    if (reserving.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.goals_accounts_title), style = MaterialTheme.typography.titleMedium)
            Text(reserving.size.toString(), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val barred = maxWidth >= 640.dp
            FinanceCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(Res.string.column_account), Modifier.weight(1f), style = header, color = muted)
                    if (barred) Box(Modifier.weight(1f))
                    Text(stringResource(SharedRes.string.account_balance_label), Modifier.width(108.dp), style = header, color = muted, textAlign = TextAlign.End)
                    Text(stringResource(Res.string.column_reserved), Modifier.width(108.dp), style = header, color = muted, textAlign = TextAlign.End)
                    Text(stringResource(Res.string.column_free), Modifier.width(108.dp), style = header, color = muted, textAlign = TextAlign.End)
                }
                reserving.forEach { allocation ->
                    val account = state.accounts.firstOrNull { it.id == allocation.accountId } ?: return@forEach
                    val short = allocation.unallocatedCents < 0L
                    HorizontalDivider(color = colors.cardBorder)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onOpenAccount(account.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(10.dp).background(categoryColor(account.color), CircleShape))
                            Text(account.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (barred) {
                            // How much of what the account holds is spoken for.
                            BudgetProgressBar(
                                fraction = progressFraction(allocation.allocatedCents, allocation.balanceCents.coerceAtLeast(1L)),
                                color = if (short) colors.debt else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f).height(8.dp),
                            )
                        }
                        Text(formatEuroCents(allocation.balanceCents), Modifier.width(108.dp), color = muted, textAlign = TextAlign.End, maxLines = 1)
                        Text(formatEuroCents(allocation.allocatedCents), Modifier.width(108.dp), textAlign = TextAlign.End, maxLines = 1)
                        Text(
                            formatEuroCents(allocation.unallocatedCents),
                            Modifier.width(108.dp),
                            fontWeight = FontWeight.Medium,
                            color = if (short) colors.debt else colors.income,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalPane(goal: GoalSummary, state: GoalsUiState, viewModel: GoalsViewModel, onBack: () -> Unit, onOpenAccount: (String) -> Unit) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val detail = state.detail?.takeIf { it.goalId == goal.id }
    val progress = goal.progress(state.today)
    val sharesAccounts = goal.fundingMode == GoalFundingMode.ALLOCATIONS
    val completed = goal.status == GoalStatus.COMPLETED
    val byAccount = detail?.reservations.orEmpty().filterValues { it != 0L }
    val allocations = remember(detail?.allocations) {
        detail?.allocations.orEmpty().sortedWith(compareByDescending<GoalAllocation> { it.date }.thenByDescending { it.createdAt })
    }
    // What the goal held after each reservation; none of it when they do not add up to what it holds now.
    val running = remember(goal.savedCents, allocations) { runningDebt(goal.savedCents, allocations.map { it.amountCents }) }
    ScrollPage {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(SharedRes.string.goal_list_title))
            }
            IdentityIconTile(icon = goalIcon(goal.icon), color = categoryColor(goal.color), size = 48.dp)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(goal.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (sharesAccounts) {
                        stringResource(SharedRes.string.goal_subtitle_allocations)
                    } else {
                        stringResource(SharedRes.string.goal_subtitle_dedicated, goal.accountName.orEmpty())
                    },
                    color = muted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SecondaryButton(text = stringResource(SharedRes.string.common_edit), onClick = { viewModel.onEditClicked(goal) })
            DestructiveButton(text = stringResource(SharedRes.string.common_archive), onClick = { viewModel.onArchiveClicked(goal) })
        }
        detail?.errorRes?.let { error ->
            InlineBanner(
                kind = BannerKind.Error,
                text = stringResource(error),
                actionLabel = stringResource(SharedRes.string.common_retry),
                onAction = { viewModel.onGoalOpened(goal.id) },
            )
        }
        state.actionErrorRes?.let { InlineBanner(kind = BannerKind.Error, text = stringResource(it)) }
        // An account now holding less than its goals reserve; one is enough to say so.
        byAccount.keys.firstNotNullOfOrNull { accountId ->
            state.accountAllocations[accountId]?.unallocatedCents?.takeIf { it < 0L }?.let { accountId to -it }
        }?.let { (accountId, cents) ->
            InlineBanner(
                kind = BannerKind.Alert,
                text = stringResource(
                    SharedRes.string.goal_shortfall,
                    state.accounts.firstOrNull { it.id == accountId }?.name.orEmpty(),
                    formatEuroCents(cents),
                ),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val standing = @Composable {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Where it stands, straight on the page: no card around the figures.
                    Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Bottom) {
                        Figure(stringResource(SharedRes.string.goal_detail_saved)) {
                            MoneyText(cents = goal.savedCents, style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 42.sp))
                        }
                        Figure(stringResource(SharedRes.string.goal_detail_target)) {
                            MoneyText(cents = goal.targetAmountCents, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    // What is left is said under the bar, with the pace it asks for.
                    GoalProgressDetails(goal = goal, progress = progress)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (sharesAccounts && !completed) {
                            PrimaryButton(
                                text = stringResource(SharedRes.string.goal_action_reserve),
                                onClick = { viewModel.onAddAllocationClicked(goal) },
                                leadingIcon = Icons.Outlined.Add,
                            )
                        }
                        if (sharesAccounts && goal.savedCents > 0L) {
                            SecondaryButton(
                                text = stringResource(SharedRes.string.goal_action_release),
                                onClick = { viewModel.onAddAllocationClicked(goal, release = true) },
                            )
                        }
                        if (!completed) {
                            SecondaryButton(text = stringResource(SharedRes.string.goal_action_complete), onClick = { viewModel.onCompleteClicked(goal) })
                        }
                        SecondaryButton(
                            text = stringResource(if (goal.status == GoalStatus.ACTIVE) SharedRes.string.goal_pause else SharedRes.string.goal_resume),
                            onClick = { viewModel.onStatusChanged(goal, if (goal.status == GoalStatus.ACTIVE) GoalStatus.PAUSED else GoalStatus.ACTIVE) },
                        )
                    }
                }
            }
            // How what it holds grew, a point per reservation, oldest first.
            val growth: (@Composable () -> Unit)? = running?.takeIf { it.size >= 2 }?.let { held ->
                {
                    val oldestFirst = allocations.reversed()
                    HoverLine(
                        titles = oldestFirst.map { formatCompactDate(it.date) },
                        values = held.reversed(),
                        change = { index ->
                            val cents = oldestFirst[index].amountCents
                            ((if (cents > 0L) "+" else "") + formatEuroCents(cents)) to if (cents < 0L) colors.debt else colors.income
                        },
                    )
                }
            }
            if (growth != null && maxWidth >= 900.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Box(Modifier.weight(1f)) { standing() }
                    Box(Modifier.width(440.dp)) { growth() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    standing()
                    growth?.invoke()
                }
            }
        }
        if (!sharesAccounts) {
            Text(stringResource(SharedRes.string.goal_dedicated_explainer, goal.accountName.orEmpty()), color = muted)
            // A dedicated goal's history is its account's own ledger, while that account is live.
            goal.accountId?.takeIf { id -> state.accounts.any { it.id == id } }?.let { accountId ->
                SecondaryButton(text = goal.accountName.orEmpty(), onClick = { onOpenAccount(accountId) })
            }
            return@ScrollPage
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(SharedRes.string.goal_allocations_title), style = MaterialTheme.typography.titleMedium)
            if (allocations.isNotEmpty()) Text(allocations.size.toString(), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        if (allocations.isEmpty()) {
            if (detail?.isLoading == false) Text(stringResource(SharedRes.string.goal_allocations_empty), color = muted)
        } else {
            Reservations(allocations, running, onEdit = viewModel::onEditAllocationClicked)
        }
    }
}

/** A goal's reservations, newest first: what each put in or took out, and what the goal held after it. */
@Composable
private fun Reservations(allocations: List<GoalAllocation>, running: List<Long>?, onEdit: (GoalAllocation) -> Unit) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val header = MaterialTheme.typography.labelMedium
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val noted = maxWidth >= 560.dp
        FinanceCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(Res.string.column_date), Modifier.width(96.dp), style = header, color = muted)
                Text(stringResource(Res.string.column_account), Modifier.weight(1f), style = header, color = muted)
                if (noted) Text(stringResource(Res.string.column_note), Modifier.weight(1.4f), style = header, color = muted)
                Text(stringResource(Res.string.column_reserved), Modifier.width(112.dp), style = header, color = muted, textAlign = TextAlign.End)
                if (running != null) Text(stringResource(Res.string.goals_column_running), Modifier.width(112.dp), style = header, color = muted, textAlign = TextAlign.End)
            }
            allocations.forEachIndexed { index, allocation ->
                val release = allocation.amountCents < 0L
                HorizontalDivider(color = colors.cardBorder)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onEdit(allocation) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatCompactDate(allocation.date), Modifier.width(96.dp), color = muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(allocation.accountName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (noted) {
                        Text(
                            allocation.notes ?: stringResource(
                                if (release) SharedRes.string.goal_allocation_kind_release else SharedRes.string.goal_allocation_kind_reserve,
                            ),
                            Modifier.weight(1.4f),
                            color = muted,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        (if (release) "" else "+") + formatEuroCents(allocation.amountCents),
                        Modifier.width(112.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (release) colors.debt else colors.income,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                    if (running != null) {
                        Text(formatEuroCents(running[index]), Modifier.width(112.dp), color = muted, textAlign = TextAlign.End, maxLines = 1)
                    }
                }
            }
        }
    }
}
