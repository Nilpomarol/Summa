package com.gestorfinances.desktop

import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import com.gestorfinances.app.data.repository.AnalysisBucket
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.desktop.resources.accounts_column_month
import com.gestorfinances.desktop.resources.accounts_column_share
import com.gestorfinances.desktop.resources.column_account
import com.gestorfinances.desktop.resources.column_last_movement
import com.gestorfinances.desktop.resources.column_reserved
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.data.repository.AccountAllocation
import com.gestorfinances.app.data.repository.AccountGoalReservation
import com.gestorfinances.app.data.repository.AccountLedgerEntry
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.ui.accounts.AccountFlowDetailState
import com.gestorfinances.app.ui.accounts.AccountOwnership
import com.gestorfinances.app.ui.accounts.AccountsUiState
import com.gestorfinances.app.ui.accounts.AccountsViewModel
import com.gestorfinances.app.ui.common.AppIconButton
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DistributionSegment
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SegmentedDistributionBar
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.formatCompactDateRelative
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.parseIsoDateOrNull
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.desktop.resources.column_amount
import com.gestorfinances.desktop.resources.column_balance_after
import com.gestorfinances.desktop.resources.column_category
import com.gestorfinances.desktop.resources.column_date
import com.gestorfinances.desktop.resources.column_name
import com.gestorfinances.ui.resources.account_balance_label
import com.gestorfinances.ui.resources.account_default_badge
import com.gestorfinances.ui.resources.account_empty_body
import com.gestorfinances.ui.resources.account_flow_empty
import com.gestorfinances.ui.resources.account_flow_view_analysis
import com.gestorfinances.ui.resources.account_goals_free
import com.gestorfinances.ui.resources.account_goals_title
import com.gestorfinances.ui.resources.account_goals_whole
import com.gestorfinances.ui.resources.account_list_add
import com.gestorfinances.ui.resources.account_list_net_worth
import com.gestorfinances.ui.resources.account_list_title
import com.gestorfinances.ui.resources.account_list_your_share
import com.gestorfinances.ui.resources.account_move_down
import com.gestorfinances.ui.resources.account_move_up
import com.gestorfinances.ui.resources.account_reorder
import com.gestorfinances.ui.resources.account_shared_badge
import com.gestorfinances.ui.resources.account_withdrawal_action
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_edit
import com.gestorfinances.ui.resources.common_retry
import com.gestorfinances.ui.resources.dashboard_pending_low_balance
import com.gestorfinances.ui.resources.entity_add_movement
import com.gestorfinances.ui.resources.failure_load_accounts
import com.gestorfinances.ui.resources.movement_type_contribution
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import com.gestorfinances.app.ui.common.DestructiveButton
import com.gestorfinances.app.ui.common.HERO_MUTED_ALPHA
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.HeroMonthChangeBars
import com.gestorfinances.app.ui.common.LinkPill
import com.gestorfinances.app.ui.common.ListHero
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.theme.heroIdentityColor
import com.gestorfinances.desktop.resources.movements_column_context
import com.gestorfinances.ui.resources.account_detail_your_share
import com.gestorfinances.ui.resources.account_distribution_accessibility
import com.gestorfinances.ui.resources.account_list_monthly_change
import com.gestorfinances.ui.resources.account_list_net_worth_this_month
import com.gestorfinances.ui.resources.movement_your_share_short
import kotlin.math.abs
import java.time.YearMonth
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

/**
 * The accounts, and the one that is opened, each over the whole page: first net worth on the dark
 * panel, as on the phone, over a table of every account; a click opens an account in its place, with what
 * it holds and who owns it, the goals saving in it, and its ledger as a statement in the Movements
 * table's own rows. The arrow goes back to the accounts.
 */
@Composable
fun AccountsPage(
    viewModel: AccountsViewModel,
    analysis: AnalysisRepository,
    dataVersion: Long,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onAddMovement: (accountId: String) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    onOpenGoal: (goalId: String) -> Unit,
    onViewAnalysis: (AccountSummary) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel, dataVersion) { viewModel.onScreenShown() }
    LaunchedEffect(viewModel, selectedId, dataVersion) { selectedId?.let(viewModel::onAccountDetailOpened) }
    // An opened account shows at once from what the list already holds; its ledger follows.
    val detail = state.flowDetail?.takeIf { it.account.id == selectedId }
        ?: state.accounts.firstOrNull { it.id == selectedId }?.let { AccountFlowDetailState(account = it, isLoading = true) }

    when {
        selectedId == null -> AccountList(state, viewModel, analysis, dataVersion, onSelect)
        // Opened from another page before even the accounts have loaded: the page waits rather than flash the list.
        detail == null -> Unit
        else -> AccountPane(
            detail = detail,
            state = state,
            viewModel = viewModel,
            onBack = { onSelect(null) },
            onAddMovement = { onAddMovement(detail.account.id) },
            onMovementDetail = onMovementDetail,
            onOpenGoal = onOpenGoal,
            onViewAnalysis = { onViewAnalysis(detail.account) },
        )
    }
}

@Composable
private fun AccountList(
    state: AccountsUiState,
    viewModel: AccountsViewModel,
    analysis: AnalysisRepository,
    dataVersion: Long,
    onSelect: (String?) -> Unit,
) {
    val muted = FinanceTheme.colors.mutedText
    val activity by produceState<AccountActivity?>(null, analysis, dataVersion, state.accounts) {
        value = withContext(Dispatchers.IO) { runCatching { loadAccountActivity(analysis, LocalDate.now()) }.getOrNull() }
    }
    // Moving accounts about is a mode of its own: the rows stay plain until it is asked for.
    var reordering by remember { mutableStateOf(false) }
    ScrollPage {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(SharedRes.string.account_list_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (state.accounts.size > 1) {
                FinanceFilterChip(
                    selected = reordering,
                    label = stringResource(SharedRes.string.account_reorder),
                    onClick = { reordering = !reordering },
                    trailingIcon = Icons.Outlined.SwapHoriz,
                )
            }
            PrimaryButton(text = stringResource(SharedRes.string.account_list_add), onClick = viewModel::onAddClicked, leadingIcon = Icons.Outlined.Add)
        }
        if (state.errorMessage != null) {
            InlineBanner(
                kind = BannerKind.Error,
                text = stringResource(SharedRes.string.failure_load_accounts),
                actionLabel = stringResource(SharedRes.string.common_retry),
                onAction = viewModel::onScreenShown,
            )
        }
        if (state.isLoading) return@ScrollPage
        if (state.accounts.isEmpty()) {
            Text(stringResource(SharedRes.string.account_empty_body), color = muted)
            return@ScrollPage
        }
        NetWorthHero(state.accounts, state.netWorthMonthEndsCents, onOpen = onSelect, modifier = Modifier.fillMaxWidth())
        AccountTable(state, activity, viewModel, reordering, onSelect)
    }
}

/** What the list says of each account beyond its balance; both from the canonical account flow. */
internal data class AccountActivity(
    /** What entered less what left each account this month. */
    val monthChangeCents: Map<String, Long>,
    /** The last day anything moved in each account, up to today. */
    val lastDate: Map<String, String>,
)

internal fun loadAccountActivity(analysis: AnalysisRepository, today: LocalDate): AccountActivity {
    val month = YearMonth.from(today)
    val thisMonth = analysis.accountFlowOverTime(month.atDay(1).toString(), month.plusMonths(1).atDay(1).toString(), AnalysisBucket.MONTH)
    val days = analysis.accountFlowOverTime("0001-01-01", today.plusDays(1).toString(), AnalysisBucket.DAY)
    return AccountActivity(
        monthChangeCents = thisMonth.filter { it.accountId != null }.groupBy { it.accountId!! }.mapValues { (_, buckets) -> buckets.sumOf { it.deltaCents } },
        lastDate = days.filter { it.accountId != null }.groupBy { it.accountId!! }.mapValues { (_, buckets) -> buckets.maxOf { it.bucket } },
    )
}

/**
 * Every account as a row across the page: what it is, its part of what the owner holds, when it
 * last moved, what this month did to it, what goals reserve in it, and its balance. A row opens
 * the account. A narrow window keeps the name, the month and the balance.
 */
@Composable
private fun AccountTable(
    state: AccountsUiState,
    activity: AccountActivity?,
    viewModel: AccountsViewModel,
    reordering: Boolean,
    onSelect: (String?) -> Unit,
) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val header = MaterialTheme.typography.labelMedium
    val held = state.accounts.sumOf { it.ownerValueCents.coerceAtLeast(0L) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 860.dp
        FinanceCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(Res.string.column_account), Modifier.weight(1.3f), style = header, color = muted)
                if (wide) {
                    Text(stringResource(Res.string.accounts_column_share), Modifier.weight(1f), style = header, color = muted)
                    Text(stringResource(Res.string.column_last_movement), Modifier.width(ACTIVITY_WIDTH), style = header, color = muted)
                    Text(stringResource(Res.string.column_reserved), Modifier.width(FIGURE_WIDTH), style = header, color = muted, textAlign = TextAlign.End)
                }
                Text(stringResource(Res.string.accounts_column_month), Modifier.width(FIGURE_WIDTH), style = header, color = muted, textAlign = TextAlign.End)
                Text(stringResource(SharedRes.string.account_balance_label), Modifier.width(BALANCE_WIDTH), style = header, color = muted, textAlign = TextAlign.End)
                Spacer(Modifier.width(TRAILING_WIDTH))
            }
            state.accounts.forEachIndexed { index, account ->
                val low = account.lowBalanceThresholdCents?.let { account.currentBalanceCents < it } ?: false
                HorizontalDivider(color = colors.cardBorder)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(role = Role.Button) { onSelect(account.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1.3f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IdentityIconTile(
                            icon = account.icon?.let(::accountIcon) ?: accountTypeIcon(account.type),
                            color = categoryColor(account.color),
                            size = 36.dp,
                        )
                        Column {
                            Text(account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(account.typeLine(), style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (wide) {
                        // How much of what the owner holds sits here; an account in the red holds none of it.
                        val share = if (held > 0L) account.ownerValueCents.coerceAtLeast(0L).toFloat() / held else 0f
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BudgetProgressBar(
                                fraction = share,
                                color = themedIdentityColor(categoryColor(account.color)),
                                modifier = Modifier.weight(1f).height(8.dp),
                            )
                            Text(
                                (share * 100).roundToInt().let { if (it == 0 && share > 0f) "<1%" else "$it%" },
                                Modifier.width(40.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = muted,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                            )
                        }
                        Text(
                            activity?.lastDate?.get(account.id)?.let { formatCompactDateRelative(it) } ?: if (activity == null) "" else "—",
                            Modifier.width(ACTIVITY_WIDTH),
                            style = MaterialTheme.typography.bodyMedium,
                            color = muted,
                            maxLines = 1,
                        )
                        val reserved = state.accountAllocations[account.id]?.allocatedCents?.takeIf { it != 0L }
                        Box(Modifier.width(FIGURE_WIDTH), contentAlignment = Alignment.CenterEnd) {
                            if (reserved != null) MoneyText(cents = reserved, style = MaterialTheme.typography.bodyMedium, color = muted)
                            else Text("—", color = muted)
                        }
                    }
                    Box(Modifier.width(FIGURE_WIDTH), contentAlignment = Alignment.CenterEnd) {
                        activity?.let {
                            val change = it.monthChangeCents[account.id] ?: 0L
                            MoneyText(
                                cents = change,
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    change > 0L -> colors.income
                                    change < 0L -> MaterialTheme.colorScheme.onSurface
                                    else -> muted
                                },
                                signed = true,
                            )
                        }
                    }
                    Column(Modifier.width(BALANCE_WIDTH), horizontalAlignment = Alignment.End) {
                        MoneyText(
                            cents = account.currentBalanceCents,
                            color = if (account.currentBalanceCents < 0 || low) colors.debt else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (account.ownershipKind == AccountOwnershipKind.SHARED) {
                            Text(
                                stringResource(SharedRes.string.account_list_your_share, formatEuroCents(account.ownerValueCents)),
                                style = MaterialTheme.typography.labelSmall,
                                color = muted,
                                maxLines = 1,
                            )
                        }
                    }
                    Row(Modifier.width(TRAILING_WIDTH), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        if (reordering) {
                            ReorderArrows(viewModel, account, first = index == 0, last = index == state.accounts.lastIndex)
                        } else {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

private val ACTIVITY_WIDTH = 116.dp
private val FIGURE_WIDTH = 112.dp
private val BALANCE_WIDTH = 140.dp
private val TRAILING_WIDTH = 56.dp

/** The order here is the order everywhere accounts are listed or chosen. */
@Composable
private fun ReorderArrows(viewModel: AccountsViewModel, account: AccountSummary, first: Boolean, last: Boolean) {
    AppIconButton(onClick = { viewModel.onMoveUpClicked(account) }, enabled = !first, modifier = Modifier.size(28.dp)) {
        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(SharedRes.string.account_move_up), modifier = Modifier.size(20.dp))
    }
    AppIconButton(onClick = { viewModel.onMoveDownClicked(account) }, enabled = !last, modifier = Modifier.size(28.dp)) {
        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(SharedRes.string.account_move_down), modifier = Modifier.size(20.dp))
    }
}

/**
 * Net worth on the dark panel, as the phone's accounts page opens: how much it moved this month,
 * how much it moved in each of the last twelve, and how it splits across the accounts that hold money.
 */
@Composable
private fun NetWorthHero(accounts: List<AccountSummary>, monthEndsCents: List<Long>, onOpen: (String) -> Unit, modifier: Modifier) {
    val colors = FinanceTheme.colors
    val netWorthCents = accounts.sumOf { it.ownerValueCents }
    val positiveAccounts = accounts.filter { it.ownerValueCents > 0 }
    val positiveBalanceCents = positiveAccounts.sumOf { it.ownerValueCents }
    ListHero(
        eyebrow = stringResource(SharedRes.string.account_list_net_worth),
        watermark = Icons.Outlined.AccountBalanceWallet,
        cents = netWorthCents,
        figureColor = if (netWorthCents < 0) colors.heroDebt else colors.heroOnSurface,
        modifier = modifier,
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
                HeroCaption(text = stringResource(SharedRes.string.account_list_net_worth_this_month))
            }
        }
        Spacer(Modifier.height(12.dp))
        // What each of the last twelve months added or took away, this month last.
        val ends = monthEndsCents + netWorthCents
        val thisMonth = YearMonth.now()
        HeroMonthChangeBars(
            title = stringResource(SharedRes.string.account_list_monthly_change),
            changes = (1 until ends.size).map { i -> thisMonth.minusMonths((ends.size - 1 - i).toLong()) to ends[i] - ends[i - 1] },
        )
        if (positiveAccounts.size > 1) {
            Spacer(Modifier.height(14.dp))
            // How it splits across the accounts that hold money: the one under the pointer names
            // itself with what it holds, and a click opens it.
            HeroSegments(
                segments = positiveAccounts.map { account ->
                    HeroSegment(account.name, account.ownerValueCents, heroIdentityColor(categoryColor(account.color))) { onOpen(account.id) }
                },
                totalCents = positiveBalanceCents,
                rest = stringResource(SharedRes.string.account_distribution_accessibility),
            )
        }
    }
}

@Composable
private fun AccountPane(
    detail: AccountFlowDetailState,
    state: AccountsUiState,
    viewModel: AccountsViewModel,
    onBack: () -> Unit,
    onAddMovement: () -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    onOpenGoal: (String) -> Unit,
    onViewAnalysis: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val account = detail.account
    val shared = account.ownershipKind == AccountOwnershipKind.SHARED
    val low = account.lowBalanceThresholdCents?.let { account.currentBalanceCents < it } ?: false
    // The account stays in view and its statement scrolls under it, drawing only the rows on screen:
    // a long ledger opens as fast as a short one.
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(SharedRes.string.account_list_title))
            }
            IdentityIconTile(icon = account.icon?.let(::accountIcon) ?: accountTypeIcon(account.type), color = categoryColor(account.color), size = 48.dp)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(account.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(account.typeLine(), color = muted, style = MaterialTheme.typography.bodyMedium)
            }
            SecondaryButton(text = stringResource(SharedRes.string.common_edit), onClick = { viewModel.onEditClicked(account) })
            DestructiveButton(text = stringResource(SharedRes.string.common_archive), onClick = { viewModel.onArchiveClicked(account) })
        }
        // An action that fails here (archiving, say) reports on the list's state: show it too.
        if ((detail.errorMessage ?: state.errorMessage) != null) {
            InlineBanner(
                kind = BannerKind.Error,
                text = stringResource(SharedRes.string.failure_load_accounts),
                actionLabel = stringResource(SharedRes.string.common_retry),
                onAction = { viewModel.onAccountDetailOpened(account.id) },
            )
        }
        // What it holds, straight on the page as the phone has it: no card around the figure.
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(SharedRes.string.account_balance_label), style = MaterialTheme.typography.labelLarge, color = muted)
                MoneyText(
                    cents = account.currentBalanceCents,
                    color = if (account.currentBalanceCents < 0 || low) colors.debt else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 42.sp),
                    signed = account.currentBalanceCents < 0,
                )
                if (low) Text(stringResource(SharedRes.string.dashboard_pending_low_balance), color = colors.debt, style = MaterialTheme.typography.bodyMedium)
            }
            if (shared) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(SharedRes.string.account_detail_your_share), style = MaterialTheme.typography.labelLarge, color = muted)
                    MoneyText(cents = account.ownerValueCents, style = MaterialTheme.typography.headlineSmall, signed = account.ownerValueCents < 0)
                }
            }
        }
        if (shared && account.members.isNotEmpty()) AccountOwnership(account.members)
        if (detail.goals.isNotEmpty()) AccountGoalsBar(detail.goals, state.accountAllocations[account.id], onOpenGoal)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(text = stringResource(SharedRes.string.entity_add_movement), onClick = onAddMovement, leadingIcon = Icons.Outlined.Add)
            if (shared) {
                SecondaryButton(text = stringResource(SharedRes.string.movement_type_contribution), onClick = { viewModel.onContributionClicked(account) })
                SecondaryButton(text = stringResource(SharedRes.string.account_withdrawal_action), onClick = { viewModel.onWithdrawalClicked(account) })
            }
            Spacer(Modifier.weight(1f))
            LinkPill(text = stringResource(SharedRes.string.account_flow_view_analysis), onClick = onViewAnalysis)
        }
        when {
            detail.isLoading -> Unit
            detail.entries.isEmpty() -> Text(stringResource(SharedRes.string.account_flow_empty), color = muted)
            else -> Statement(account, detail.entries, onMovementDetail, Modifier.weight(1f))
        }
    }
}

/**
 * The account's ledger in the Movements table's own rows, newest first under its months: each
 * movement with what it did to this account and, when the whole ledger adds up to the balance,
 * the balance it left.
 */
@Composable
private fun Statement(account: AccountSummary, entries: List<AccountLedgerEntry>, onOpen: (MovementSummary) -> Unit, modifier: Modifier) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val header = MaterialTheme.typography.labelMedium
    val sorted = remember(entries) { entries.sortedWith(compareByDescending<AccountLedgerEntry> { it.movement.date }.thenByDescending { it.movement.createdAt }) }
    val balances = remember(account, sorted) { runningBalances(account, sorted) }
    // Each row knows whether it opens a month.
    val rows = remember(sorted) {
        var month: YearMonth? = null
        sorted.map { entry ->
            val own = parseIsoDateOrNull(entry.movement.date)?.let(YearMonth::from)
            own.takeIf { it != month }.also { month = own }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = LedgerColumns(
            tick = false,
            category = maxWidth >= 620.dp,
            account = false,
            context = maxWidth >= 820.dp,
            balance = balances != null,
            delete = false,
        )
        FinanceCard(Modifier.fillMaxWidth()) {
            TableRow(
                columns = columns,
                height = 36.dp,
                tick = {},
                leading = {},
                name = { Text(stringResource(Res.string.column_name), style = header, color = muted) },
                category = { Text(stringResource(Res.string.column_category), style = header, color = muted) },
                account = {},
                context = { Text(stringResource(Res.string.movements_column_context), style = header, color = muted) },
                date = { Text(stringResource(Res.string.column_date), style = header, color = muted) },
                amount = { Text(stringResource(Res.string.column_amount), style = header, color = muted) },
                trailing = {},
                balance = { Text(stringResource(Res.string.column_balance_after), style = header, color = muted) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            )
            ScrollList {
            itemsIndexed(sorted, key = { _, entry -> entry.movement.id }) { index, entry ->
                val movement = entry.movement
                rows[index]?.let { MonthHeading(it) } ?: HorizontalDivider(color = colors.cardBorder)
                MovementRow(
                    movement = movement,
                    columns = columns,
                    ticked = false,
                    onTick = {},
                    onOpen = { onOpen(movement) },
                    onDelete = {},
                    seenFrom = account.id,
                    // What it did to this account leads; the owner's part of a shared one goes beneath.
                    amount = {
                        Column(horizontalAlignment = Alignment.End) {
                            // Figures as the ledger writes them, so a statement and the ledger read alike.
                            MoneyText(
                                cents = entry.deltaCents,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = if (entry.deltaCents > 0L) colors.income else MaterialTheme.colorScheme.onSurface,
                                signed = true,
                            )
                            if (movement.isShared && movement.userShareCents != abs(entry.deltaCents)) {
                                Text(
                                    stringResource(SharedRes.string.movement_your_share_short, formatEuroCents(movement.userShareCents)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.shared,
                                    maxLines = 1,
                                )
                            }
                        }
                    },
                    balance = {
                        balances?.let { MoneyText(cents = it[index], color = muted, style = MaterialTheme.typography.bodyMedium) }
                    },
                )
            }
            }
        }
    }
}

@Composable
private fun AccountSummary.typeLine(): String =
    listOfNotNull(
        type.label(),
        stringResource(SharedRes.string.account_shared_badge).takeIf { ownershipKind == AccountOwnershipKind.SHARED },
        stringResource(SharedRes.string.account_default_badge).takeIf { isDefault },
    ).joinToString(" · ")

/**
 * The goals saving in this account, straight on the page under its balance as the ownership bar
 * is: one bar split between them and what is still free, and each goal named beneath it with what
 * it keeps here. A name opens the goal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountGoalsBar(goals: List<AccountGoalReservation>, allocation: AccountAllocation?, onOpenGoal: (String) -> Unit) {
    val colors = FinanceTheme.colors
    val balance = allocation?.balanceCents ?: 0L
    val tones = goals.map { themedIdentityColor(categoryColor(it.color)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(SharedRes.string.account_goals_title), style = MaterialTheme.typography.labelLarge, color = colors.mutedText, modifier = Modifier.weight(1f))
            allocation?.takeIf { it.allocatedCents != 0L }?.let {
                Text(
                    stringResource(SharedRes.string.account_goals_free, formatEuroCents(it.unallocatedCents)),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (it.unallocatedCents < 0L) colors.debt else colors.mutedText,
                )
            }
        }
        if (allocation != null && balance > 0L && allocation.allocatedCents > 0L) {
            SegmentedDistributionBar(
                segments = goals.mapIndexed { index, goal -> DistributionSegment(tones[index], (goal.reservedCents.toFloat() / balance).coerceIn(0f, 1f)) } +
                    DistributionSegment(colors.progressTrack, (allocation.unallocatedCents.toFloat() / balance).coerceIn(0f, 1f)),
                contentDescription = stringResource(SharedRes.string.account_goals_title),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            goals.forEachIndexed { index, goal ->
                Row(
                    Modifier.clip(MaterialTheme.shapes.small).clickable { onOpenGoal(goal.goalId) }.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(8.dp).background(tones[index], CircleShape))
                    Text(goal.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(
                        if (goal.dedicated) stringResource(SharedRes.string.account_goals_whole) else formatEuroCents(goal.reservedCents),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The balance left after each of [newestFirst], counted back from the account's canonical balance.
 * Null unless the ledger's canonical deltas add up from the starting balance to that balance: a
 * column that did not reconcile would be worse than none.
 */
internal fun runningBalances(account: AccountSummary, newestFirst: List<AccountLedgerEntry>): List<Long>? {
    if (account.startingBalanceCents + newestFirst.sumOf { it.deltaCents } != account.currentBalanceCents) return null
    var balance = account.currentBalanceCents
    return newestFirst.map { entry -> balance.also { balance -= entry.deltaCents } }
}
