package com.gestorfinances.desktop

import com.gestorfinances.ui.resources.dashboard_account_balance_label
import com.gestorfinances.ui.resources.account_shared_owner_share
import com.gestorfinances.app.ui.common.formatBasisPointsCompact
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.desktop.resources.home_upcoming_title
import com.gestorfinances.app.ui.common.parseIsoDateOrNull
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.domain.rules.PlanPart
import com.gestorfinances.app.domain.rules.PlanStatus
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.HeroPanel
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.LinkPill
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatCompactDateRelative
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatWeekdayLongDate
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.movementTypeIcon
import com.gestorfinances.app.ui.dashboard.DashboardUiState
import com.gestorfinances.app.ui.dashboard.DashboardViewModel
import com.gestorfinances.app.ui.dashboard.elidesCatalanDe
import com.gestorfinances.app.ui.people.PersonAvatar
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.movementAmountColor
import com.gestorfinances.desktop.resources.home_debts_title
import com.gestorfinances.desktop.resources.home_month_balance
import com.gestorfinances.desktop.resources.home_plan_part
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.account_list_title
import com.gestorfinances.ui.resources.budget_list_title
import com.gestorfinances.ui.resources.budget_over
import com.gestorfinances.ui.resources.budget_plan_others
import com.gestorfinances.ui.resources.budget_remaining
import com.gestorfinances.ui.resources.common_no_category
import com.gestorfinances.ui.resources.common_retry
import com.gestorfinances.ui.resources.dashboard_active_trip_title
import com.gestorfinances.ui.resources.dashboard_budget_of
import com.gestorfinances.ui.resources.dashboard_empty_body
import com.gestorfinances.ui.resources.dashboard_latest_movements_title
import com.gestorfinances.ui.resources.dashboard_month_in
import com.gestorfinances.ui.resources.dashboard_month_in_elided
import com.gestorfinances.ui.resources.dashboard_month_out
import com.gestorfinances.ui.resources.dashboard_month_out_elided
import com.gestorfinances.ui.resources.dashboard_month_spending_title
import com.gestorfinances.ui.resources.dashboard_net_worth_label
import com.gestorfinances.ui.resources.dashboard_pending_low_balance
import com.gestorfinances.ui.resources.dashboard_pending_may_exceed
import com.gestorfinances.ui.resources.dashboard_pending_over
import com.gestorfinances.ui.resources.dashboard_plan_per_day
import com.gestorfinances.ui.resources.dashboard_view_all
import com.gestorfinances.ui.resources.failure_load_dashboard
import com.gestorfinances.ui.resources.nav_home
import com.gestorfinances.ui.resources.person_balance_owes_you
import com.gestorfinances.ui.resources.person_balance_you_owe
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.desktop.resources.Res as DesktopRes

/** A page's content in a column that scrolls under an always-visible scrollbar. */
@Composable
internal fun ScrollPage(content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scroll),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp),
        )
    }
}

/** A table's rows, laid out as they come into view, with a scrollbar when there are more than fit. */
@Composable
internal fun ScrollList(content: LazyListScope.() -> Unit) {
    val list = rememberLazyListState()
    Box {
        LazyColumn(state = list, content = content)
        // Sized by the rows, not sizing them: a short table stays short.
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(list),
            modifier = Modifier.matchParentSize().wrapContentWidth(Alignment.End).padding(vertical = 4.dp, horizontal = 2.dp),
        )
    }
}

/**
 * Home, read from the top: anything that needs a look as a warning, what there is on the one dark
 * panel, then the month against its plan and the latest movements beside the open debts, the
 * accounts and what is coming. On a narrow window the two columns stack.
 */
@Composable
fun HomePage(
    viewModel: DashboardViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onViewMovements: () -> Unit,
    onOpenMovement: (MovementSummary) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenRecurring: (String?) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel, dataVersion) { viewModel.onScreenShown() }

    ScrollPage {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.nav_home), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            Text(formatWeekdayLongDate(state.today), style = MaterialTheme.typography.bodyMedium, color = FinanceTheme.colors.mutedText)
        }
        if (state.errorMessage != null) {
            InlineBanner(
                kind = BannerKind.Error,
                text = stringResource(Res.string.failure_load_dashboard),
                actionLabel = stringResource(Res.string.common_retry),
                onAction = viewModel::refresh,
            )
        }
        if (state.hasLoaded) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 760.dp
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Hero(state, wide)
                    Warnings(state, onOpenAccounts, onOpenBudgets)
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                MonthCard(state, onOpenBudgets)
                                LatestMovements(state.latestMovements, onViewMovements, onOpenMovement)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                DebtsCard(state, onOpenPeople)
                                AccountsCard(state, viewModel::onAccountSelected, onOpenAccounts)
                                UpcomingCard(state, onOpenRecurring)
                                ActiveTrip(state)
                            }
                        }
                    } else {
                        DebtsCard(state, onOpenPeople)
                        MonthCard(state, onOpenBudgets)
                        AccountsCard(state, viewModel::onAccountSelected, onOpenAccounts)
                        UpcomingCard(state, onOpenRecurring)
                        ActiveTrip(state)
                        LatestMovements(state.latestMovements, onViewMovements, onOpenMovement)
                    }
                }
            }
        }
    }
}

/** The page's one dark panel: the chosen account's balance over everything owned, and that account's month in, out and what is left of it. */
@Composable
private fun Hero(state: DashboardUiState, wide: Boolean) {
    val colors = FinanceTheme.colors
    HeroPanel(Modifier.fillMaxWidth()) {
        val account = state.mainAccount
        // As on the phone: the account in daily use leads, with everything owned as quiet context under it.
        val worth: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    account?.let { stringResource(Res.string.dashboard_account_balance_label) + " · " + it.name }
                        ?: stringResource(Res.string.dashboard_net_worth_label),
                    color = colors.heroOnSurfaceMuted,
                    style = MaterialTheme.typography.labelLarge,
                )
                MoneyText(
                    cents = account?.currentBalanceCents ?: state.netWorthCents,
                    color = colors.heroOnSurface,
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 40.sp, lineHeight = 48.sp),
                )
                if (account != null) {
                    // A shared account's physical balance and the owner's patrimonial share differ.
                    if (account.ownershipKind == AccountOwnershipKind.SHARED) {
                        Text(
                            stringResource(
                                Res.string.account_shared_owner_share,
                                formatEuroCents(account.ownerValueCents),
                                formatBasisPointsCompact(account.ownerOwnershipBasisPoints),
                            ),
                            color = colors.heroOnSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(Res.string.dashboard_net_worth_label),
                            color = colors.heroOnSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.alignByBaseline(),
                        )
                        MoneyText(
                            cents = state.netWorthCents,
                            color = if (state.netWorthCents < 0) colors.heroDebt else colors.heroOnSurface,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
            }
        }
        val flow = state.mainAccountMonthFlow
        val figures: @Composable (Modifier) -> Unit = { each ->
            if (account != null && flow != null) {
                val elided = elidesCatalanDe(account.name)
                val balance = flow.inCents - flow.outCents
                HeroFigure(
                    stringResource(if (elided) Res.string.dashboard_month_in_elided else Res.string.dashboard_month_in, account.name),
                    flow.inCents,
                    colors.heroIncome,
                    each,
                )
                HeroFigure(
                    stringResource(if (elided) Res.string.dashboard_month_out_elided else Res.string.dashboard_month_out, account.name),
                    flow.outCents,
                    colors.heroOnSurface,
                    each,
                )
                HeroFigure(
                    stringResource(DesktopRes.string.home_month_balance),
                    balance,
                    if (balance < 0) colors.heroDebt else colors.heroIncome,
                    each,
                    signed = true,
                )
            }
        }
        if (wide) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp).height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { worth() }
                figures(
                    Modifier
                        .fillMaxHeight()
                        .padding(start = 12.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(colors.heroOnSurface.copy(alpha = 0.08f))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        } else {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                worth()
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { figures(Modifier) }
            }
        }
    }
}

@Composable
private fun HeroFigure(label: String, cents: Long, color: Color, modifier: Modifier, signed: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
        Text(label, color = FinanceTheme.colors.heroOnSurfaceMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        MoneyText(cents = cents, color = color, style = MaterialTheme.typography.titleLarge, signed = signed)
    }
}

/** What needs a look, dressed as a warning: a low balance, and parts of the plan over or heading over. */
@Composable
private fun Warnings(state: DashboardUiState, onOpenAccounts: () -> Unit, onOpenBudgets: () -> Unit) {
    val colors = FinanceTheme.colors
    val lowBalance = state.lowBalanceAccount
    if (lowBalance == null && state.planWarnings.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lowBalance?.let {
            Warning(
                icon = Icons.Outlined.WarningAmber,
                name = it.name,
                label = stringResource(Res.string.dashboard_pending_low_balance),
                cents = it.currentBalanceCents,
                color = colors.alert,
                onClick = onOpenAccounts,
            )
        }
        state.planWarnings.forEach { warning ->
            val over = warning.part.status == PlanStatus.OVER
            Warning(
                icon = if (over) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber,
                name = warning.budget?.let { it.categoryName ?: stringResource(Res.string.common_no_category) }
                    ?: stringResource(Res.string.budget_plan_others),
                label = stringResource(if (over) Res.string.dashboard_pending_over else Res.string.dashboard_pending_may_exceed),
                cents = warning.excessCents,
                color = if (over) colors.debt else colors.alert,
                onClick = onOpenBudgets,
            )
        }
    }
}

@Composable
private fun Warning(icon: ImageVector, name: String, label: String, cents: Long, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.40f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
        MoneyText(cents = cents, color = color, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}

/** This month's spending against the plan when there is one, then the plan's parts, fullest first. */
@Composable
private fun MonthCard(state: DashboardUiState, onOpenBudgets: () -> Unit) {
    val colors = FinanceTheme.colors
    val month = state.monthPlan
    HomeCard(
        title = stringResource(Res.string.dashboard_month_spending_title),
        trailing = { LinkPill(text = stringResource(Res.string.budget_list_title), onClick = onOpenBudgets) },
    ) {
        val total = month?.plan?.total
        val planned = total?.plannedCents
        Row(verticalAlignment = Alignment.Bottom) {
            MoneyText(
                cents = total?.actualCents ?: state.monthExpenseCents,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.alignByBaseline(),
            )
            if (total != null && planned != null) {
                Text(
                    stringResource(Res.string.dashboard_budget_of, formatEuroCents(planned)),
                    color = colors.mutedText,
                    modifier = Modifier.alignByBaseline().padding(start = 6.dp).weight(1f),
                )
                val over = total.actualCents > planned
                Text(
                    stringResource(if (over) Res.string.budget_over else Res.string.budget_remaining, formatEuroCents(abs(planned - total.actualCents))),
                    color = if (over) colors.debt else colors.income,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        if (total != null && planned != null && planned > 0L) {
            BudgetProgressBar(
                fraction = (total.actualCents.toFloat() / planned).coerceIn(0f, 1f),
                color = if (total.actualCents > planned) colors.debt else MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(10.dp),
            )
            total.perDayCents?.let {
                Text(
                    stringResource(Res.string.dashboard_plan_per_day, formatEuroCents(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedText,
                )
            }
        }
        if (month != null) {
            val parts = month.compartments
                .map { it to month.plan.compartments.getValue(it.id) }
                .filter { (_, part) -> (part.plannedCents ?: 0L) > 0L }
                .sortedByDescending { (_, part) -> part.actualCents.toFloat() / part.plannedCents!! }
                .take(HOME_PLAN_PARTS)
            if (parts.isNotEmpty()) HorizontalDivider(color = colors.cardBorder)
            parts.forEach { (budget, part) ->
                PlanPartRow(
                    icon = categoryIcon(budget.categoryIcon),
                    color = categoryColor(budget.categoryColor),
                    name = budget.categoryName ?: stringResource(Res.string.common_no_category),
                    part = part,
                )
            }
        }
    }
}

private const val HOME_PLAN_PARTS = 5

@Composable
private fun PlanPartRow(icon: ImageVector, color: Color, name: String, part: PlanPart) {
    val planned = part.plannedCents ?: return
    val over = part.actualCents > planned
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IdentityIconTile(icon = icon, color = color, size = 28.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(DesktopRes.string.home_plan_part, formatEuroCents(part.actualCents), formatEuroCents(planned)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) FinanceTheme.colors.debt else FinanceTheme.colors.mutedText,
                )
            }
            BudgetProgressBar(
                fraction = (part.actualCents.toFloat() / planned).coerceIn(0f, 1f),
                color = if (over) FinanceTheme.colors.debt else color,
            )
        }
    }
}

@Composable
private fun LatestMovements(movements: List<MovementSummary>, onViewAll: () -> Unit, onOpen: (MovementSummary) -> Unit) {
    HomeCard(
        title = stringResource(Res.string.dashboard_latest_movements_title),
        trailing = { LinkPill(text = stringResource(Res.string.dashboard_view_all), onClick = onViewAll) },
    ) {
        if (movements.isEmpty()) {
            Text(stringResource(Res.string.dashboard_empty_body), color = FinanceTheme.colors.mutedText)
        }
        movements.forEachIndexed { index, movement ->
            if (index > 0) HorizontalDivider(color = FinanceTheme.colors.cardBorder)
            Row(
                Modifier.clip(MaterialTheme.shapes.small).clickable { onOpen(movement) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IdentityIconTile(
                    icon = movement.categoryIcon?.let(::categoryIcon) ?: movementTypeIcon(movement.type),
                    color = categoryColor(movement.categoryColor),
                    size = 36.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        movement.name ?: movement.payee ?: movement.type.label(),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatCompactDateRelative(movement.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = FinanceTheme.colors.mutedText,
                    )
                }
                MoneyText(
                    cents = if (movement.type == MovementType.EXPENSE) -movement.amountCents else movement.amountCents,
                    color = FinanceTheme.colors.movementAmountColor(movement),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

/** Every account with its balance; choosing one makes it the account the hero's month follows. */
@Composable
private fun AccountsCard(state: DashboardUiState, onSelect: (String) -> Unit, onOpenAccounts: () -> Unit) {
    HomeCard(
        title = stringResource(Res.string.account_list_title),
        trailing = { LinkPill(text = stringResource(Res.string.dashboard_view_all), onClick = onOpenAccounts) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            state.accounts.forEach { account ->
                val selected = account.id == state.mainAccount?.id
                val accent = MaterialTheme.colorScheme.primary
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable { onSelect(account.id) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IdentityIconTile(
                        icon = account.icon?.let(::accountIcon) ?: accountTypeIcon(account.type),
                        color = categoryColor(account.color),
                        size = 36.dp,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            account.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(account.type.label(), style = MaterialTheme.typography.bodySmall, color = FinanceTheme.colors.mutedText)
                    }
                    MoneyText(cents = account.currentBalanceCents, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}

/** Recurring movements overdue or coming in the next two weeks, each under its date; the late ones stand out. */
@Composable
private fun UpcomingCard(state: DashboardUiState, onOpenRecurring: (String?) -> Unit) {
    if (state.upcomingRecurring.isEmpty()) return
    val colors = FinanceTheme.colors
    HomeCard(
        title = stringResource(DesktopRes.string.home_upcoming_title),
        trailing = { LinkPill(text = stringResource(Res.string.dashboard_view_all), onClick = { onOpenRecurring(null) }) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            state.upcomingRecurring.forEach { template ->
                val date = parseIsoDateOrNull(template.nextDueDate)
                val late = date != null && !date.isAfter(state.today)
                val tone = if (late) colors.alert else MaterialTheme.colorScheme.primary
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onOpenRecurring(template.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        Modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(tone.copy(alpha = 0.14f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(date?.dayOfMonth?.toString() ?: "?", style = MaterialTheme.typography.labelLarge, color = tone, fontWeight = FontWeight.Medium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            template.name ?: template.payee ?: template.categoryName ?: template.type.label(),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatCompactDateRelative(template.nextDueDate, state.today),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (late) colors.alert else colors.mutedText,
                        )
                    }
                    template.expectedAmountCents?.let { cents ->
                        MoneyText(
                            cents = if (template.type == MovementType.INCOME) cents else -cents,
                            color = if (template.type == MovementType.INCOME) colors.income else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
        }
    }
}

/** People with an open balance either way. */
@Composable
private fun DebtsCard(state: DashboardUiState, onOpenPeople: () -> Unit) {
    if (state.openDebts.isEmpty()) return
    val colors = FinanceTheme.colors
    HomeCard(
        title = stringResource(DesktopRes.string.home_debts_title),
        trailing = { LinkPill(text = stringResource(Res.string.dashboard_view_all), onClick = onOpenPeople) },
    ) {
        state.openDebts.forEach { person ->
            val owesYou = person.balanceCents > 0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonAvatar(person, size = 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(if (owesYou) Res.string.person_balance_owes_you else Res.string.person_balance_you_owe),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mutedText,
                    )
                }
                MoneyText(
                    cents = abs(person.balanceCents),
                    color = if (owesYou) colors.income else colors.debt,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

@Composable
private fun ActiveTrip(state: DashboardUiState) {
    val trip = state.activeTrip ?: return
    HomeCard(stringResource(Res.string.dashboard_active_trip_title)) {
        Text(trip.name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun HomeCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinanceCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(title = title, trailing = trailing)
            content()
        }
    }
}
