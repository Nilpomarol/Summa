package com.gestorfinances.app.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.BudgetMonthPlan
import com.gestorfinances.app.domain.rules.PlanStatus
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.ui.trips.icon
import com.gestorfinances.app.di.AppContainer
import com.gestorfinances.app.ui.common.AppDropdownMenu
import com.gestorfinances.app.ui.common.AppDropdownMenuItem
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.HERO_MUTED_ALPHA
import com.gestorfinances.app.ui.common.HeroPanel
import com.gestorfinances.app.ui.common.HeroEyebrow
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.LinkPill
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatBasisPointsCompact
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.formatWeekdayLongDate
import com.gestorfinances.app.ui.common.movementRowPosition
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementSourceMode
import com.gestorfinances.app.ui.people.PersonAvatar
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.LocalDate
import java.time.YearMonth

private const val KEY_HERO_ACCOUNT = "hero_account_id"

/** This page visit's ViewModel, scoped to its navigation entry. */
@Composable
fun dashboardViewModel(appContainer: AppContainer): DashboardViewModel = viewModel {
    val preferences = appContainer.homePreferences
    DashboardViewModel(
        analysisRepository = appContainer.analysisRepository,
        accountRepository = appContainer.accountRepository,
        movementRepository = appContainer.movementRepository,
        tripRepository = appContainer.tripRepository,
        categoryRepository = appContainer.categoryRepository,
        budgetRepository = appContainer.budgetRepository,
        templateRepository = appContainer.templateRepository,
        personRepository = appContainer.personRepository,
        loadSelectedAccountId = { preferences.getString(KEY_HERO_ACCOUNT, null) },
        saveSelectedAccountId = { preferences.edit().putString(KEY_HERO_ACCOUNT, it).apply() },
    )
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    /** Recurring items due now; owned by the app-wide recurring state, not this page. */
    dueRecurringCount: Int,
    onDrillDown: (MovementFilters) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    onAccountAnalysis: (AccountSummary) -> Unit,
    onOpenAccount: (AccountSummary) -> Unit,
    onOpenPerson: (PersonSummary) -> Unit,
    onOpenRecurring: () -> Unit,
    onViewTrip: (TripSummary) -> Unit,
    onAddTripMovement: (TripSummary) -> Unit,
    onViewBudgets: () -> Unit,
    onAddMovement: () -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, dataVersion) {
        viewModel.onScreenShown()
    }

    DashboardContent(
        state = state,
        dueRecurringCount = dueRecurringCount,
        onDrillDown = onDrillDown,
        onMovementDetail = onMovementDetail,
        onAccountAnalysis = onAccountAnalysis,
        onAccountSelected = viewModel::onAccountSelected,
        onOpenAccount = onOpenAccount,
        onOpenPerson = onOpenPerson,
        onOpenRecurring = onOpenRecurring,
        onViewTrip = onViewTrip,
        onAddTripMovement = onAddTripMovement,
        onViewBudgets = onViewBudgets,
        onAddMovement = onAddMovement,
        onAddAccount = onAddAccount,
        onRetry = viewModel::onScreenShown,
        modifier = modifier,
    )
}

/**
 * Home: the current context. A forest hero for the account in daily use, the month's spending in
 * one compact block, a card only for what is waiting on the user, then the latest movements. Only
 * the hero and the pending list sit on surfaces; the rest reads straight off the page.
 */
@Composable
internal fun DashboardContent(
    state: DashboardUiState,
    dueRecurringCount: Int,
    onDrillDown: (MovementFilters) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    onAccountAnalysis: (AccountSummary) -> Unit,
    onAccountSelected: (String) -> Unit,
    onOpenAccount: (AccountSummary) -> Unit,
    onOpenPerson: (PersonSummary) -> Unit,
    onOpenRecurring: () -> Unit,
    onViewTrip: (TripSummary) -> Unit,
    onAddTripMovement: (TripSummary) -> Unit,
    onViewBudgets: () -> Unit,
    onAddMovement: () -> Unit,
    onAddAccount: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            DashboardHeader(today = state.today)
        }

        state.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_dashboard,
                    onRetry = onRetry,
                )
            }
        }

        if (state.isLoading && !state.hasLoaded) {
            item {
                Text(
                    text = stringResource(R.string.movement_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (!state.hasLoaded) return@LazyColumn

        if (state.mainAccount != null) item {
            DashboardHeroPanel(
                state = state,
                onAccountSelected = onAccountSelected,
                onAccountAnalysis = onAccountAnalysis,
            )
        }

        if (state.latestMovements.isNotEmpty() || state.monthPlan != null) item {
            MonthSection(
                state = state,
                onDrillDown = onDrillDown,
                onViewBudgets = onViewBudgets,
            )
        }

        val lowBalance = state.lowBalanceAccount
        if (dueRecurringCount > 0 || lowBalance != null || state.planWarnings.isNotEmpty() ||
            state.openDebts.isNotEmpty()
        ) item {
            PendingSection(
                dueRecurringCount = dueRecurringCount,
                lowBalanceAccount = lowBalance,
                planWarnings = state.planWarnings,
                openDebts = state.openDebts,
                onOpenRecurring = onOpenRecurring,
                onOpenAccount = onOpenAccount,
                onViewBudgets = onViewBudgets,
                onOpenPerson = onOpenPerson,
            )
        }

        state.activeTrip?.let { trip ->
            item {
                ActiveTripRow(
                    trip = trip,
                    onViewTrip = { onViewTrip(trip) },
                    onAddMovement = { onAddTripMovement(trip) },
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader(
                    title = stringResource(R.string.dashboard_latest_movements_title),
                    trailing = {
                        if (state.latestMovements.isNotEmpty()) {
                            LinkPill(text = stringResource(R.string.dashboard_view_all), onClick = { onDrillDown(MovementFilters()) })
                        }
                    },
                )
                if (state.latestMovements.isEmpty()) {
                    Text(
                        text = stringResource(if (state.accounts.isEmpty()) R.string.account_empty_body else R.string.dashboard_empty_body),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = if (state.accounts.isEmpty()) onAddAccount else onAddMovement) {
                        Text(text = stringResource(
                            if (state.accounts.isEmpty()) R.string.account_list_add else R.string.movement_list_add,
                        ))
                    }
                } else {
                    Column {
                        state.latestMovements.forEachIndexed { index, movement ->
                            MovementListItem(
                                movement = movement,
                                onClick = { onMovementDetail(movement) },
                                position = movementRowPosition(index, state.latestMovements.size),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page header — the mark, the name, and today's date
// ---------------------------------------------------------------------------

/**
 * The app's mark and name, with today's date across from them. The bottom bar already says this
 * is "Inici", so the header carries identity and the day instead of repeating the page title.
 */
@Composable
private fun DashboardHeader(today: LocalDate) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaMarkTile()
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = formatWeekdayLongDate(today),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The launcher icon in miniature: the mark on its forest tile, so it reads on either theme. */
@Composable
private fun SummaMarkTile() {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(FinanceTheme.colors.heroSurface, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.summa_mark),
            contentDescription = null,
            modifier = Modifier.height(20.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Hero — the account in daily use: balance and this month's flow through it
// ---------------------------------------------------------------------------

/** Small, wide-tracked label above a figure on the forest hero. */
@Composable
private fun DashboardHeroPanel(
    state: DashboardUiState,
    onAccountSelected: (String) -> Unit,
    onAccountAnalysis: (AccountSummary) -> Unit,
) {
    val account = state.mainAccount ?: return
    val colors = FinanceTheme.colors
    HeroPanel(modifier = Modifier.fillMaxWidth()) {
        // The mark, oversized and nearly transparent, bleeding off the panel's right edge. It
        // sits in a box matched to the panel so it never sets the panel's height itself.
        Box(modifier = Modifier.matchParentSize()) {
            Image(
                painter = painterResource(R.drawable.summa_mark),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.heroOnSurface.copy(alpha = 0.06f)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 12.dp)
                    .requiredHeight(210.dp),
            )
        }
        Column(modifier = Modifier.padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    HeroEyebrow(text = stringResource(R.string.dashboard_account_balance_label))
                }
                AccountSwitcher(
                    account = account,
                    accounts = state.accounts,
                    onAccountSelected = onAccountSelected,
                )
            }
            AccountBalance(
                account = account,
                netWorthCents = state.netWorthCents,
                onAccountAnalysis = { onAccountAnalysis(account) },
            )
            state.mainAccountMonthFlow?.let { flow ->
                val elided = elidesCatalanDe(account.name)
                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = colors.heroOnSurface.copy(alpha = 0.14f))
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    HeroFlowFigure(
                        label = stringResource(
                            if (elided) R.string.dashboard_month_in_elided else R.string.dashboard_month_in,
                            account.name,
                        ),
                        cents = flow.inCents,
                        icon = Icons.Outlined.SouthWest,
                        color = colors.heroIncome,
                        modifier = Modifier.weight(1f),
                    )
                    HeroFlowFigure(
                        label = stringResource(
                            if (elided) R.string.dashboard_month_out_elided else R.string.dashboard_month_out,
                            account.name,
                        ),
                        cents = flow.outCents,
                        icon = Icons.Outlined.NorthEast,
                        color = colors.heroDebt,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Account pill for the hero's top edge: icon, name, and the shared account menu. */
@Composable
private fun AccountSwitcher(
    account: AccountSummary,
    accounts: List<AccountSummary>,
    onAccountSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val onHero = FinanceTheme.colors.heroOnSurface
    val accessibility = if (accounts.size > 1) stringResource(
        R.string.dashboard_account_selector_accessibility,
        account.name,
    ) else account.name

    Box(modifier = Modifier.padding(start = 12.dp)) {
        Box(
            modifier = Modifier
                .widthIn(max = 200.dp)
                .heightIn(min = 44.dp)
                .clearAndSetSemantics { contentDescription = accessibility }
                .then(if (accounts.size > 1) Modifier.clickable { expanded = true } else Modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier
                    .background(onHero.copy(alpha = 0.10f), CircleShape)
                    .padding(start = 10.dp, end = if (accounts.size > 1) 8.dp else 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = accountChipIcon(account),
                    contentDescription = null,
                    tint = onHero.copy(alpha = HERO_MUTED_ALPHA),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = account.name,
                    color = onHero,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (accounts.size > 1) Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = onHero.copy(alpha = HERO_MUTED_ALPHA),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { option ->
                AppDropdownMenuItem(
                    text = {
                        Text(
                            text = option.name,
                            style = if (option.id == account.id) {
                                MaterialTheme.typography.titleSmall
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                        )
                    },
                    leadingIcon = {
                        IconChip(
                            icon = accountChipIcon(option),
                            contentDescription = null,
                            color = categoryColor(option.color),
                            size = 28.dp,
                        )
                    },
                    trailingIcon = {
                        MoneyText(
                            cents = option.currentBalanceCents,
                            color = if (option.currentBalanceCents < 0) {
                                FinanceTheme.colors.debt
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = {
                        onAccountSelected(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountBalance(
    account: AccountSummary,
    netWorthCents: Long,
    onAccountAnalysis: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val accessibility = stringResource(
        R.string.dashboard_account_balance_accessibility,
        account.name,
        formatEuroCents(account.currentBalanceCents),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAccountAnalysis),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MoneyText(
            cents = account.currentBalanceCents,
            modifier = Modifier.clearAndSetSemantics { contentDescription = accessibility },
            color = if (account.currentBalanceCents < 0) colors.heroDebt else colors.heroOnSurface,
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 42.sp, lineHeight = 48.sp),
        )
        // A shared account's physical balance and the owner's patrimonial share differ.
        if (account.ownershipKind == AccountOwnershipKind.SHARED) {
            Text(
                text = stringResource(
                    R.string.account_shared_owner_share,
                    formatEuroCents(account.ownerValueCents),
                    formatBasisPointsCompact(account.ownerOwnershipBasisPoints),
                ),
                color = colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // Everything owned, as quiet context under the account in daily use.
        Row {
            Text(
                text = stringResource(R.string.dashboard_net_worth_label),
                modifier = Modifier.alignByBaseline(),
                color = colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.width(6.dp))
            MoneyText(
                cents = netWorthCents,
                modifier = Modifier.alignByBaseline(),
                color = if (netWorthCents < 0) colors.heroDebt else colors.heroOnSurface.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** One side of the month's flow: a direction glyph in a soft disc, its label, and the amount. */
@Composable
private fun HeroFlowFigure(
    label: String,
    cents: Long,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val onHero = FinanceTheme.colors.heroOnSurface
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(onHero.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(
                text = label,
                color = onHero.copy(alpha = HERO_MUTED_ALPHA),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MoneyText(cents = cents, color = color, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---------------------------------------------------------------------------
// This month — spending against the monthly plan, one figure, one bar, one caption
// ---------------------------------------------------------------------------

@Composable
private fun MonthSection(
    state: DashboardUiState,
    onDrillDown: (MovementFilters) -> Unit,
    onViewBudgets: () -> Unit,
) {
    val plan = state.monthPlan
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = formatMonthYear(state.month),
            trailing = { LinkPill(text = stringResource(R.string.budget_list_title), onClick = onViewBudgets) },
        )
        if (plan != null) {
            PlanSummary(plan = plan, onClick = onViewBudgets)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDrillDown(state.month.actualPeriodFilters(type = MovementType.EXPENSE)) },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.dashboard_month_spending_title),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
                MoneyText(cents = state.monthExpenseCents, style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = stringResource(R.string.dashboard_month_scope),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * The plan's total: spent of planned and what is left, a bar with the month's forecast run ahead,
 * and the one thing worth saying now: heading over, ahead of pace, or what is left per day.
 */
@Composable
private fun PlanSummary(plan: BudgetMonthPlan, onClick: () -> Unit) {
    val total = plan.plan.total
    val planned = total.plannedCents ?: return
    val colors = FinanceTheme.colors
    val over = total.actualCents > planned
    val barColor = if (over) colors.debt else MaterialTheme.colorScheme.secondary
    val (status, statusColor) = when (total.status) {
        PlanStatus.MAY_EXCEED ->
            stringResource(R.string.budget_part_may_exceed, formatEuroCents(total.forecastCents - planned)) to colors.alert
        PlanStatus.AHEAD_OF_PACE ->
            stringResource(R.string.budget_part_ahead, formatEuroCents(total.actualCents - (total.expectedByTodayCents ?: 0L))) to colors.alert
        PlanStatus.ON_TRACK ->
            // On the month's last day the daily figure is just what is left, already shown.
            total.perDayCents?.takeIf { it < planned - total.actualCents }?.let { stringResource(R.string.dashboard_plan_per_day, formatEuroCents(it)) } to colors.mutedText
        else -> null to colors.mutedText
    }
    val caption = listOfNotNull(
        status,
        when {
            !plan.inclusions.includeTripExpenses && !plan.inclusions.includeExtraordinaryExpenses -> R.string.dashboard_budget_excludes_both
            !plan.inclusions.includeTripExpenses -> R.string.dashboard_budget_excludes_trips
            !plan.inclusions.includeExtraordinaryExpenses -> R.string.dashboard_budget_excludes_extraordinary
            else -> null
        }?.let { stringResource(it) },
    ).joinToString(" · ").replaceFirstChar { it.uppercase() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            MoneyText(
                cents = total.actualCents,
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = stringResource(R.string.dashboard_budget_of, formatEuroCents(planned)),
                modifier = Modifier
                    .alignByBaseline()
                    .padding(start = 6.dp)
                    .weight(1f),
                color = colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    if (over) R.string.budget_over else R.string.budget_remaining,
                    formatEuroCents(kotlin.math.abs(planned - total.actualCents)),
                ),
                modifier = Modifier.alignByBaseline(),
                color = if (over) colors.debt else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
        ForecastBar(
            actual = (total.actualCents.toFloat() / planned).coerceIn(0f, 1f),
            forecast = (total.forecastCents.toFloat() / planned).coerceIn(0f, 1f),
            color = barColor,
        )
        if (caption.isNotEmpty()) {
            Text(text = caption, color = statusColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Spent so far in full colour, the rest of the month's forecast as a paler run ahead of it. */
@Composable
private fun ForecastBar(actual: Float, forecast: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(FinanceTheme.colors.progressTrack, CircleShape),
    ) {
        if (forecast > actual) Box(
            modifier = Modifier
                .fillMaxWidth(forecast)
                .fillMaxHeight()
                .background(color.copy(alpha = 0.35f), CircleShape),
        )
        if (actual > 0f) Box(
            modifier = Modifier
                .fillMaxWidth(actual)
                .fillMaxHeight()
                .background(color, CircleShape),
        )
    }
}

// ---------------------------------------------------------------------------
// Pending — everything waiting on the user, one line each in one card
// ---------------------------------------------------------------------------

private val PendingLeadingSize = 28.dp

@Composable
private fun PendingSection(
    dueRecurringCount: Int,
    lowBalanceAccount: AccountSummary?,
    planWarnings: List<PlanWarning>,
    openDebts: List<PersonSummary>,
    onOpenRecurring: () -> Unit,
    onOpenAccount: (AccountSummary) -> Unit,
    onViewBudgets: () -> Unit,
    onOpenPerson: (PersonSummary) -> Unit,
) {
    val colors = FinanceTheme.colors
    val rows = buildList<@Composable () -> Unit> {
        if (dueRecurringCount > 0) add {
            PendingRow(
                leading = { StatusTile(icon = Icons.Outlined.EventRepeat, color = colors.alert) },
                title = pluralStringResource(R.plurals.dashboard_pending_recurring, dueRecurringCount, dueRecurringCount),
                trailing = {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.mutedText)
                },
                onClick = onOpenRecurring,
            )
        }
        lowBalanceAccount?.let { account ->
            if (account.lowBalanceThresholdCents == null) return@let
            add {
                PendingRow(
                    leading = { StatusTile(icon = Icons.Outlined.WarningAmber, color = colors.alert) },
                    title = account.name,
                    trailing = {
                        PendingAmount(
                            label = stringResource(R.string.dashboard_pending_low_balance),
                            cents = account.currentBalanceCents,
                            color = colors.alert,
                        )
                    },
                    onClick = { onOpenAccount(account) },
                )
            }
        }
        planWarnings.forEach { warning ->
            val budget = warning.budget
            val isOver = warning.part.status == PlanStatus.OVER
            add {
                PendingRow(
                    leading = {
                        IdentityIconTile(
                            icon = budget?.let { categoryIcon(it.categoryIcon) } ?: Icons.Outlined.MoreHoriz,
                            color = budget?.let { categoryColor(it.categoryColor) } ?: colors.mutedText,
                            size = PendingLeadingSize,
                        )
                    },
                    title = budget?.let { it.displayName ?: stringResource(R.string.common_no_category) }
                        ?: stringResource(R.string.budget_plan_others),
                    trailing = {
                        PendingAmount(
                            label = stringResource(if (isOver) R.string.dashboard_pending_over else R.string.dashboard_pending_may_exceed),
                            cents = warning.excessCents,
                            color = if (isOver) colors.debt else colors.alert,
                        )
                    },
                    onClick = onViewBudgets,
                )
            }
        }
        openDebts.forEach { person ->
            val owesYou = person.balanceCents > 0
            add {
                PendingRow(
                    leading = { PersonAvatar(person = person, size = PendingLeadingSize) },
                    title = person.name,
                    trailing = {
                        PendingAmount(
                            label = stringResource(if (owesYou) R.string.person_balance_owes_you else R.string.person_balance_you_owe),
                            cents = kotlin.math.abs(person.balanceCents),
                            color = if (owesYou) colors.income else colors.debt,
                        )
                    },
                    onClick = { onOpenPerson(person) },
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.dashboard_pending_title))
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(
                        modifier = Modifier.padding(start = 14.dp + PendingLeadingSize + 12.dp),
                        color = colors.cardBorder,
                    )
                    row()
                }
            }
        }
    }
}

/** One line: who or what on the left, its state on the right. */
@Composable
private fun PendingRow(
    leading: @Composable () -> Unit,
    title: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** A muted state label followed by the amount it refers to, e.g. "Et deu 97,83 €". */
@Composable
private fun PendingAmount(label: String, cents: Long, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
        MoneyText(cents = cents, color = color, style = MaterialTheme.typography.titleSmall)
    }
}

/** A warning-toned tile for pending items that are not an entity of their own. */
@Composable
private fun StatusTile(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(PendingLeadingSize)
            .background(color.copy(alpha = 0.14f), MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Active trip
// ---------------------------------------------------------------------------

/** The trip running today, straight on the page: open it, or add a movement to it. */
@Composable
private fun ActiveTripRow(
    trip: TripSummary,
    onViewTrip: () -> Unit,
    onAddMovement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onViewTrip)
                .heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IdentityIconTile(icon = trip.type.icon(), color = categoryColor(trip.color))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dashboard_active_trip_title),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = trip.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MoneyText(cents = trip.totalActualCents, style = MaterialTheme.typography.titleSmall)
        }
        IconButton(onClick = onAddMovement) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.dashboard_active_trip_add_movement),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun accountChipIcon(account: AccountSummary): ImageVector =
    if (account.icon != null) accountIcon(account.icon) else accountTypeIcon(account.type)

internal fun YearMonth.actualPeriodFilters(type: MovementType? = null): MovementFilters =
    MovementFilters(
        type = type,
        sourceMode = MovementSourceMode.ACTUAL,
        dateFrom = atDay(1).toString(),
        dateTo = atEndOfMonth().toString(),
    )
