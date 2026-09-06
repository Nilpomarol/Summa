package com.gestorfinances.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandMore
import com.gestorfinances.app.ui.common.AppDropdownMenu
import com.gestorfinances.app.ui.common.AppDropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.BudgetProjection
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.icon
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.BudgetForecastExceptionRow
import com.gestorfinances.app.ui.common.BudgetForecastStatusPill
import com.gestorfinances.app.ui.common.FORECAST_TONE_ALPHA
import com.gestorfinances.app.ui.common.actualProgressFraction
import com.gestorfinances.app.ui.common.color
import com.gestorfinances.app.ui.common.forecastProgressFraction
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.HERO_MUTED_ALPHA
import com.gestorfinances.app.ui.common.HeroPanel
import com.gestorfinances.app.ui.common.PillShape
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.movementRowPosition
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatBasisPointsCompact
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.formatWeekdayLongDate
import com.gestorfinances.app.ui.common.formatPercentLabel
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementSourceMode
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.asEyebrow
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.dataMarkColor
import java.time.LocalDate
import java.time.YearMonth

/** Named slices of the monthly breakdown ring; everything past them is folded into one aggregate. */
private const val DASHBOARD_NAMED_CATEGORIES = 4

/** Secondary text and hairlines on the ink hero, as an alpha over its on-surface color. */
private const val HERO_RULE_ALPHA = 0.18f

/** The month budget bar is the one chunky shape on the page, so it is deliberately thick. */
private val BUDGET_BAR_HEIGHT = 14.dp

/** The per-category share rules stay hairline-thin so they rank without competing with it. */
private val CATEGORY_RULE_HEIGHT = 3.dp

/**
 * The share rule starts under its label rather than at the card edge (the icon chip plus its
 * gap), so a full 100% rule reads as belonging to its row instead of mirroring the card divider.
 */
private val CATEGORY_RULE_INSET = 32.dp

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onDrillDown: (MovementFilters) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    onAccountAnalysis: (AccountSummary) -> Unit,
    onViewTrip: (TripSummary) -> Unit,
    onAddTripMovement: (TripSummary) -> Unit,
    onViewBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    DashboardContent(
        state = state,
        onDrillDown = onDrillDown,
        onMovementDetail = onMovementDetail,
        onAccountAnalysis = onAccountAnalysis,
        onAccountSelected = viewModel::onAccountSelected,
        onViewTrip = onViewTrip,
        onAddTripMovement = onAddTripMovement,
        onViewBudgets = onViewBudgets,
        onRetry = viewModel::onScreenShown,
        modifier = modifier,
    )
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onDrillDown: (MovementFilters) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    onAccountAnalysis: (AccountSummary) -> Unit,
    onAccountSelected: (String) -> Unit,
    onViewTrip: (TripSummary) -> Unit,
    onAddTripMovement: (TripSummary) -> Unit,
    onViewBudgets: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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

        if (state.isLoading) {
            item {
                Text(
                    text = stringResource(R.string.movement_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            DashboardHeroPanel(
                state = state,
                onAccountSelected = onAccountSelected,
                onAccountAnalysis = onAccountAnalysis,
                onDrillDown = onDrillDown,
            )
        }

        item {
            MonthlySpendingCard(
                state = state,
                onDrillDown = onDrillDown,
                onViewBudgets = onViewBudgets,
            )
        }

        // Attention item: rendered only when the loaded state already exposes the condition.
        state.lowBalanceAccount?.let { account ->
            item {
                LowBalanceBanner(account = account)
            }
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
                        TextButton(onClick = { onDrillDown(MovementFilters()) }) {
                            Text(text = stringResource(R.string.dashboard_view_all))
                        }
                    },
                )
                if (state.latestMovements.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dashboard_empty_body),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
// Page header — the wordmark and today's date, sharing one baseline
// ---------------------------------------------------------------------------

/**
 * The dashboard header. It carries the app's name and today's date, set on a shared baseline so
 * the size contrast alone does the hierarchy — no rule, no kicker.
 *
 * It deliberately does not repeat the page title: the bottom bar already marks "Inici" as the
 * current tab, so a heading here would restate it, while the app's own name appears nowhere else.
 */
@Composable
private fun DashboardHeader(today: LocalDate) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatWeekdayLongDate(today),
            modifier = Modifier
                .alignByBaseline()
                .weight(1f),
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// Hero — account, balance, net worth, and month totals in one contained card
// ---------------------------------------------------------------------------

/** Static month indicator: the dashboard always reflects the current month, so it is read-only. */
@Composable
private fun MonthIndicator(month: YearMonth) {
    val onInk = FinanceTheme.colors.heroOnSurface
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(onInk.copy(alpha = 0.12f))
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.CalendarMonth,
            contentDescription = null,
            tint = onInk.copy(alpha = HERO_MUTED_ALPHA),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = formatMonthYear(month),
            color = onInk,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small, wide-tracked label above a figure on the ink hero. */
@Composable
private fun HeroEyebrow(text: String) {
    Text(
        text = text.uppercase(),
        color = FinanceTheme.colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA),
        style = MaterialTheme.typography.labelSmall.asEyebrow(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HeroDivider() {
    HorizontalDivider(color = FinanceTheme.colors.heroOnSurface.copy(alpha = HERO_RULE_ALPHA))
}

// ---------------------------------------------------------------------------
// Financial summary — one contained card: account, balance, net worth
// ---------------------------------------------------------------------------

/**
 * The dashboard hero: one ink panel that anchors the whole page. Everything about current
 * standing (the account, its balance, net worth, and the running month totals) sits on a single
 * dark plum surface, which lets the rest of the page stay quiet paper.
 */
@Composable
private fun DashboardHeroPanel(
    state: DashboardUiState,
    onAccountSelected: (String) -> Unit,
    onAccountAnalysis: (AccountSummary) -> Unit,
    onDrillDown: (MovementFilters) -> Unit,
) {
    val colors = FinanceTheme.colors
    val account = state.mainAccount

    HeroPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (account != null) {
                    AccountSwitcher(
                        account = account,
                        accounts = state.accounts,
                        onAccountSelected = onAccountSelected,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                MonthIndicator(month = state.month)
            }

            if (account == null) {
                Text(
                    text = stringResource(R.string.movement_no_accounts_body),
                    color = colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                AvailableBalance(
                    account = account,
                    netWorthCents = state.totals.netWorthCents,
                    onAccountAnalysis = { onAccountAnalysis(account) },
                )
            }

            HeroDivider()

            MonthTotalsRow(state = state, onDrillDown = onDrillDown)
        }
    }
}

/** Compact account picker for the hero top row: leading icon, name, and the shared account menu. */
@Composable
private fun AccountSwitcher(
    account: AccountSummary,
    accounts: List<AccountSummary>,
    onAccountSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val onInk = FinanceTheme.colors.heroOnSurface
    val accessibility = stringResource(
        R.string.dashboard_account_selector_accessibility,
        account.name,
    )

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .heightIn(min = 44.dp)
                .clearAndSetSemantics { contentDescription = accessibility },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = accountTypeIcon(account.type),
                contentDescription = null,
                tint = onInk.copy(alpha = HERO_MUTED_ALPHA),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = account.name,
                color = onInk,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = onInk.copy(alpha = HERO_MUTED_ALPHA),
                modifier = Modifier.size(18.dp),
            )
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
private fun AvailableBalance(
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
        HeroEyebrow(text = stringResource(R.string.dashboard_available_balance_label))
        MoneyText(
            cents = account.currentBalanceCents,
            modifier = Modifier.clearAndSetSemantics { contentDescription = accessibility },
            color = if (account.currentBalanceCents < 0) colors.heroDebt else colors.heroOnSurface,
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 40.sp,
                lineHeight = 46.sp,
            ),
        )
        // The hero figure is the account's physical balance; on a shared account that differs
        // from the owner value the net worth below is built from, so both get named.
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_net_worth_label),
                color = colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA),
                style = MaterialTheme.typography.bodyMedium,
            )
            MoneyText(
                cents = netWorthCents,
                color = if (netWorthCents < 0) {
                    colors.heroDebt
                } else {
                    colors.heroOnSurface.copy(alpha = 0.88f)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Income, actual expense, and net flow for the selected month, in one quiet three-column row. */
@Composable
private fun MonthTotalsRow(
    state: DashboardUiState,
    onDrillDown: (MovementFilters) -> Unit,
) {
    val colors = FinanceTheme.colors
    val totals = state.totals
    val figureStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthTotalCell(
            label = stringResource(R.string.dashboard_categories_income),
            onClick = { onDrillDown(state.month.actualPeriodFilters(type = MovementType.INCOME)) },
        ) {
            MoneyText(
                cents = totals.actualIncomeCents,
                color = colors.heroIncome,
                style = figureStyle,
                signed = true,
            )
        }
        MonthTotalDivider()
        MonthTotalCell(
            label = stringResource(R.string.dashboard_summary_expense),
            onClick = { onDrillDown(state.month.actualPeriodFilters(type = MovementType.EXPENSE)) },
        ) {
            MoneyText(
                cents = totals.actualExpenseCents,
                color = colors.heroOnSurface,
                style = figureStyle,
            )
        }
        MonthTotalDivider()
        MonthTotalCell(
            label = stringResource(R.string.dashboard_flow_short),
            onClick = null,
        ) {
            MoneyText(
                cents = totals.netActualCents,
                color = if (totals.netActualCents < 0) colors.heroDebt else colors.heroIncome,
                style = figureStyle,
                signed = true,
            )
        }
    }
}

@Composable
private fun RowScope.MonthTotalCell(
    label: String,
    onClick: (() -> Unit)?,
    value: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 44.dp)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HeroEyebrow(text = label)
        value()
    }
}

@Composable
private fun MonthTotalDivider() {
    VerticalDivider(
        modifier = Modifier
            .height(34.dp)
            .padding(horizontal = 8.dp),
        color = FinanceTheme.colors.heroOnSurface.copy(alpha = HERO_RULE_ALPHA),
    )
}

// ---------------------------------------------------------------------------
// Contextual items
// ---------------------------------------------------------------------------

@Composable
private fun LowBalanceBanner(account: AccountSummary) {
    val threshold = account.lowBalanceThresholdCents ?: return
    InlineBanner(
        kind = BannerKind.Alert,
        text = stringResource(
            R.string.dashboard_low_balance_banner,
            account.name,
            formatEuroCents(account.currentBalanceCents),
            formatEuroCents(threshold),
        ),
    )
}

/** Compact link to the trip that is running today, keeping both of its existing entry points. */
@Composable
private fun ActiveTripRow(
    trip: TripSummary,
    onViewTrip: () -> Unit,
    onAddMovement: () -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onViewTrip)
                    .heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconChip(
                    icon = trip.type.icon(),
                    contentDescription = null,
                    color = categoryColor(trip.color),
                    size = 34.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dashboard_active_trip_title),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = trip.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MoneyText(
                    cents = trip.totalActualCents,
                    style = MaterialTheme.typography.titleSmall,
                )
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
}

// ---------------------------------------------------------------------------
// Month spending — where the month stands against its budget, then where it went
// ---------------------------------------------------------------------------

/**
 * This month spent, as one card. It answers two questions with two different denominators, which
 * is why they stay visually separate: the top half measures spending against the monthly limit,
 * the bottom half splits that spending into shares of itself.
 */
@Composable
private fun MonthlySpendingCard(
    state: DashboardUiState,
    onDrillDown: (MovementFilters) -> Unit,
    onViewBudgets: () -> Unit,
) {
    val projection = state.overallBudgetProjection
    FinanceCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (projection != null) Modifier.clickable(onClick = onViewBudgets) else Modifier),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpendingHeadline(
                state = state,
                projection = projection,
                onViewBudgets = onViewBudgets,
            )

            if (projection != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BudgetBar(projection = projection)
                    Text(
                        text = stringResource(
                            R.string.budget_forecast_final_amount,
                            formatEuroCents(projection.forecastCents),
                        ),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.budgetExceptions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.budgetExceptions.forEach { exception ->
                            BudgetForecastExceptionRow(exception)
                        }
                    }
                }
            }

            HorizontalDivider(color = FinanceTheme.colors.cardBorder)

            ExpenseCategoryBreakdown(state = state, onDrillDown = onDrillDown)
        }
    }
}

/** The month figure against its limit, with the budget verdict sitting alongside it. */
@Composable
private fun SpendingHeadline(
    state: DashboardUiState,
    projection: BudgetProjection?,
    onViewBudgets: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            PaperEyebrow(text = stringResource(R.string.dashboard_month_spending_title))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MoneyText(
                    cents = state.totals.actualExpenseCents,
                    style = MaterialTheme.typography.displaySmall,
                )
                projection?.let {
                    Text(
                        text = stringResource(
                            R.string.budget_forecast_of_limit,
                            formatEuroCents(it.evaluation.budget.limitAmountCents),
                        ),
                        modifier = Modifier.padding(bottom = 3.dp),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (projection != null) {
            BudgetForecastStatusPill(
                status = projection.status,
                remainingCents = projection.remainingForecastCents,
                color = projection.status.color(),
            )
        } else {
            TextButton(onClick = onViewBudgets) {
                Text(text = stringResource(R.string.category_flow_define_budget))
            }
        }
    }
}

/**
 * The budget bar: full width is the monthly limit. Spending already recorded is solid; the
 * lighter tail beyond it is where the month is projected to land, so the two tones read as
 * "spent" and "not yet" without needing a legend to decode them.
 */
@Composable
private fun BudgetBar(projection: BudgetProjection) {
    val statusColor = projection.status.color()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val recorded = FinanceTheme.colors.expense
    val actual = projection.actualProgressFraction()
    val forecast = projection.forecastProgressFraction()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BUDGET_BAR_HEIGHT)
            .clip(PillShape)
            .background(track)
            .drawBehind {
                fun fill(fraction: Float, color: Color) {
                    val width = size.width * fraction.coerceIn(0f, 1f)
                    if (width > 0f) drawRect(color = color, size = Size(width, size.height))
                }
                fill(forecast, statusColor.copy(alpha = FORECAST_TONE_ALPHA))
                fill(actual, recorded)
            },
    )
}

@Composable
private fun ExpenseCategoryBreakdown(
    state: DashboardUiState,
    onDrillDown: (MovementFilters) -> Unit,
) {
    val slices = categorySlices(
        categories = state.categories,
        totalCents = state.totals.actualExpenseCents,
        noCategoryLabel = stringResource(R.string.common_no_category),
        aggregateLabel = stringResource(R.string.analysis_other),
    )

    if (slices.isEmpty()) {
        Text(
            text = stringResource(R.string.dashboard_no_data),
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        slices.forEach { slice ->
            CategoryShareRow(
                slice = slice,
                onClick = if (slice.categoryId != null || slice.isUncategorized) {
                    {
                        onDrillDown(
                            state.month.actualPeriodFilters(
                                type = MovementType.EXPENSE,
                                categoryId = slice.categoryId,
                                uncategorizedOnly = slice.isUncategorized,
                            ),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/** Wide-tracked label for a block on a paper card, mirroring the eyebrows on the ink hero. */
@Composable
private fun PaperEyebrow(text: String) {
    Text(
        text = text.uppercase(),
        color = FinanceTheme.colors.mutedText,
        style = MaterialTheme.typography.labelSmall.asEyebrow(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** One ranked category in the compact monthly breakdown. */
private data class CategorySlice(
    val label: String,
    val color: Color,
    val icon: ImageVector,
    val amountCents: Long,
    val fraction: Float,
    val categoryId: String?,
    val isUncategorized: Boolean,
)

private fun categorySlices(
    categories: List<AnalysisCategoryTotal>,
    totalCents: Long,
    noCategoryLabel: String,
    aggregateLabel: String,
): List<CategorySlice> {
    fun amountOf(row: AnalysisCategoryTotal): Long = row.expenseCents

    val denominator = totalCents.coerceAtLeast(1L)
    fun fractionOf(amount: Long): Float =
        (amount.toFloat() / denominator.toFloat()).coerceIn(0f, 1f)

    val ranked = categories
        .filter { amountOf(it) > 0L }
        .sortedByDescending { amountOf(it) }
    if (ranked.isEmpty()) return emptyList()

    val named = ranked.take(DASHBOARD_NAMED_CATEGORIES).map { row ->
        val amount = amountOf(row)
        CategorySlice(
            label = row.categoryName ?: noCategoryLabel,
            color = categoryColor(row.categoryColor),
            icon = categoryIcon(row.categoryIcon),
            amountCents = amount,
            fraction = fractionOf(amount),
            categoryId = row.categoryId,
            isUncategorized = row.categoryId == null,
        )
    }
    val rest = ranked.drop(DASHBOARD_NAMED_CATEGORIES)
    if (rest.isEmpty()) return named

    val restAmount = rest.sumOf { amountOf(it) }
    return named + CategorySlice(
        label = aggregateLabel,
        color = categoryColor(null),
        icon = categoryIcon(null),
        amountCents = restAmount,
        fraction = fractionOf(restAmount),
        categoryId = null,
        isUncategorized = false,
    )
}

/**
 * One category in the ranking: the figures on a line, and a rule under them drawn to that
 * category's share of the month in its own colour. The rules line up down the card, so the
 * ranking reads as a shape before any number is.
 */
@Composable
private fun CategoryShareRow(
    slice: CategorySlice,
    onClick: (() -> Unit)?,
) {
    val shareColor = dataMarkColor(slice.color)
    val fraction = slice.fraction.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accessibility = stringResource(
        R.string.dashboard_category_progress_accessibility,
        slice.label,
        formatPercentLabel(fraction),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = accessibility }
            .padding(vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconChip(
                icon = slice.icon,
                contentDescription = null,
                color = slice.color,
                size = 22.dp,
            )
            Text(
                text = slice.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MoneyText(
                cents = slice.amountCents,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = formatPercentLabel(fraction),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
                modifier = Modifier.width(34.dp),
            )
        }
        Box(
            modifier = Modifier
                .padding(start = CATEGORY_RULE_INSET)
                .fillMaxWidth()
                .height(CATEGORY_RULE_HEIGHT)
                .clip(PillShape)
                .background(track)
                .drawBehind {
                    val width = size.width * fraction
                    if (width > 0f) drawRect(color = shareColor, size = Size(width, size.height))
                },
        )
    }
}

// ---------------------------------------------------------------------------
// Extension helpers
// ---------------------------------------------------------------------------

private fun accountChipIcon(account: AccountSummary): ImageVector =
    if (account.icon != null) accountIcon(account.icon) else accountTypeIcon(account.type)

private fun YearMonth.actualPeriodFilters(
    type: MovementType? = null,
    categoryId: String? = null,
    uncategorizedOnly: Boolean = false,
): MovementFilters =
    MovementFilters(
        type = type,
        categoryId = categoryId,
        uncategorizedOnly = uncategorizedOnly,
        sourceMode = MovementSourceMode.ACTUAL,
        dateFrom = atDay(1).toString(),
        dateTo = atEndOfMonth().toString(),
    )
