package com.gestorfinances.app.ui.accounts

import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.movement_type_contribution
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gestorfinances.app.ui.common.ListPage
import com.gestorfinances.app.ui.theme.heroIdentityColor
import java.time.YearMonth
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import com.gestorfinances.app.ui.common.HERO_MUTED_ALPHA
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
import com.gestorfinances.app.ui.common.AppIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.gestorfinances.app.ui.common.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.ui.resources.account_balance_label
import com.gestorfinances.ui.resources.account_default_badge
import com.gestorfinances.ui.resources.account_detail_your_share
import com.gestorfinances.ui.resources.account_flow_empty
import com.gestorfinances.ui.resources.account_flow_loading
import com.gestorfinances.ui.resources.account_flow_view_analysis
import com.gestorfinances.ui.resources.account_goals_free
import com.gestorfinances.ui.resources.account_goals_reserved
import com.gestorfinances.ui.resources.account_goals_title
import com.gestorfinances.ui.resources.account_goals_whole
import com.gestorfinances.ui.resources.account_member_owner
import com.gestorfinances.ui.resources.account_members_summary
import com.gestorfinances.ui.resources.account_shared_badge
import com.gestorfinances.ui.resources.account_withdrawal_action
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_edit
import com.gestorfinances.ui.resources.entity_add_movement
import com.gestorfinances.ui.resources.failure_load_accounts
import org.jetbrains.compose.resources.StringResource
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

@Composable
fun AccountFlowScreen(
    detail: AccountFlowDetailState,
    allocation: AccountAllocation?,
    /** Null where there is no goal page to open. */
    onOpenGoal: ((goalId: String) -> Unit)?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    /** Null where there is no analysis page to open. */
    onViewAnalysis: (() -> Unit)?,
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
                    if (shared) add(EntityMenuAction(stringResource(Res.string.account_withdrawal_action), onWithdrawal))
                    add(EntityMenuAction(stringResource(Res.string.common_edit), onEdit))
                    add(EntityMenuAction(stringResource(Res.string.common_archive), onArchive, destructive = true))
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
                    stringResource(Res.string.account_shared_badge).takeIf { shared },
                    stringResource(Res.string.account_default_badge).takeIf { account.isDefault },
                ).joinToString(" · "),
                figures = {
                    EntityFigure(label = stringResource(Res.string.account_balance_label)) {
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
                            label = stringResource(Res.string.account_detail_your_share),
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
                link = onViewAnalysis?.let {
                    {
                        EntityActionPill(
                            text = stringResource(Res.string.account_flow_view_analysis),
                            onClick = it,
                            chevron = true,
                        )
                    }
                },
                actions = {
                    EntityActionPill(
                        text = stringResource(Res.string.entity_add_movement),
                        onClick = onAddMovement,
                        icon = Icons.Outlined.Add,
                    )
                    if (shared) {
                        EntityActionPill(
                            text = stringResource(Res.string.movement_type_contribution),
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
                    messageRes = Res.string.failure_load_accounts,
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
            detail.isLoading -> item { LedgerMessage(stringResource(Res.string.account_flow_loading)) }
            detail.entries.isEmpty() -> item { LedgerMessage(stringResource(Res.string.account_flow_empty)) }
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
fun AccountGoals(
    goals: List<AccountGoalReservation>,
    allocation: AccountAllocation?,
    onOpenGoal: ((goalId: String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(Res.string.account_goals_title))
        allocation?.takeIf { it.allocatedCents != 0L }?.let {
            Row(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                Text(
                    text = stringResource(Res.string.account_goals_reserved, formatEuroCents(it.allocatedCents)) + " · ",
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(Res.string.account_goals_free, formatEuroCents(it.unallocatedCents)),
                    color = if (it.unallocatedCents < 0L) FinanceTheme.colors.debt else FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        goals.forEachIndexed { index, goal ->
            EntityListRow(
                leading = { IdentityIconTile(icon = goalIcon(goal.icon), color = categoryColor(goal.color)) },
                title = goal.name,
                subtitle = stringResource(Res.string.account_goals_whole).takeIf { goal.dedicated },
                isLast = index == goals.lastIndex,
                onClick = onOpenGoal?.let { { it(goal.goalId) } },
                trailing = { MoneyText(cents = goal.reservedCents, style = MaterialTheme.typography.titleMedium) },
            )
        }
    }
}

/** Who owns how much of a shared account: one bar, one coloured mark per member beneath it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountOwnership(members: List<AccountMember>) {
    val owner = stringResource(Res.string.account_member_owner)
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
            contentDescription = stringResource(Res.string.account_members_summary, summary),
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
fun LedgerMessage(text: String) {
    Text(
        text = text,
        color = FinanceTheme.colors.mutedText,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 12.dp),
    )
}
