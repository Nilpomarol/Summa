package com.gestorfinances.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.icon
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.formatPercentLabel
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementSourceMode
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.YearMonth

/** Named slices of the monthly breakdown ring; everything past them is folded into one aggregate. */
private const val DASHBOARD_NAMED_CATEGORIES = 4

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onDrillDown: (MovementFilters) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    onAccountAnalysis: (AccountSummary) -> Unit,
    onViewTrip: (TripSummary) -> Unit,
    onAddTripMovement: (TripSummary) -> Unit,
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
        onCategoryModeChanged = viewModel::onCategoryModeChanged,
        onViewTrip = onViewTrip,
        onAddTripMovement = onAddTripMovement,
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
    onCategoryModeChanged: (CategoryDisplayMode) -> Unit,
    onViewTrip: (TripSummary) -> Unit,
    onAddTripMovement: (TripSummary) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        state.errorMessage?.let { message ->
            item {
                InlineBanner(kind = BannerKind.Error, text = message)
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
            DashboardHeroCard(
                state = state,
                onAccountSelected = onAccountSelected,
                onAccountAnalysis = onAccountAnalysis,
                onDrillDown = onDrillDown,
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
            MonthCategorySection(
                state = state,
                onCategoryModeChanged = onCategoryModeChanged,
                onDrillDown = onDrillDown,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    state.latestMovements.forEach { movement ->
                        MovementListItem(
                            movement = movement,
                            onClick = { onMovementDetail(movement) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Hero — account, balance, net worth, and month totals in one contained card
// ---------------------------------------------------------------------------

/** Static month indicator: the dashboard always reflects the current month, so it is read-only. */
@Composable
private fun MonthIndicator(month: YearMonth) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = FinanceTheme.colors.mutedText,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = formatMonthYear(month),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Financial summary — one contained card: account, balance, net worth
// ---------------------------------------------------------------------------

/**
 * The dashboard hero: one contained card that folds the account switcher, the current balance,
 * net worth, and the current month's totals into a single dense surface. The month indicator is
 * read-only; the balance and net worth reflect current standing.
 */
@Composable
private fun DashboardHeroCard(
    state: DashboardUiState,
    onAccountSelected: (String) -> Unit,
    onAccountAnalysis: (AccountSummary) -> Unit,
    onDrillDown: (MovementFilters) -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val account = state.mainAccount

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
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.dashboard_available_balance_label),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    AvailableBalance(
                        account = account,
                        onAccountAnalysis = { onAccountAnalysis(account) },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_net_worth_label),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        MoneyText(
                            cents = state.totals.netWorthCents,
                            color = if (state.totals.netWorthCents < 0) {
                                FinanceTheme.colors.debt
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            HorizontalDivider(color = FinanceTheme.colors.cardBorder)

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
                tint = FinanceTheme.colors.mutedText,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = FinanceTheme.colors.mutedText,
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
    onAccountAnalysis: () -> Unit,
) {
    val accessibility = stringResource(
        R.string.dashboard_account_balance_accessibility,
        account.name,
        formatEuroCents(account.currentBalanceCents),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAccountAnalysis),
    ) {
        MoneyText(
            cents = account.currentBalanceCents,
            modifier = Modifier.clearAndSetSemantics { contentDescription = accessibility },
            color = if (account.currentBalanceCents < 0) {
                FinanceTheme.colors.debt
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 36.sp,
                lineHeight = 42.sp,
            ),
        )
    }
}

/** Income, actual expense, and savings for the selected month, in one quiet three-column row. */
@Composable
private fun MonthTotalsRow(
    state: DashboardUiState,
    onDrillDown: (MovementFilters) -> Unit,
) {
    val totals = state.totals
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
                color = FinanceTheme.colors.income,
                style = MaterialTheme.typography.bodyMedium,
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
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        MonthTotalDivider()
        MonthTotalCell(
            label = stringResource(R.string.dashboard_savings_short),
            onClick = null,
        ) {
            MoneyText(
                cents = totals.netActualCents,
                color = if (totals.netActualCents < 0) {
                    FinanceTheme.colors.debt
                } else {
                    FinanceTheme.colors.income
                },
                style = MaterialTheme.typography.bodyMedium,
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
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        value()
    }
}

@Composable
private fun MonthTotalDivider() {
    VerticalDivider(
        modifier = Modifier
            .height(32.dp)
            .padding(horizontal = 8.dp),
        color = FinanceTheme.colors.cardBorder,
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
// Category breakdown — an open section, not a card
// ---------------------------------------------------------------------------

@Composable
private fun MonthCategorySection(
    state: DashboardUiState,
    onCategoryModeChanged: (CategoryDisplayMode) -> Unit,
    onDrillDown: (MovementFilters) -> Unit,
) {
    val totalCents = when (state.categoryMode) {
        CategoryDisplayMode.EXPENSES -> state.totals.actualExpenseCents
        CategoryDisplayMode.INCOME -> state.totals.actualIncomeCents
    }
    val totalLabel = when (state.categoryMode) {
        CategoryDisplayMode.EXPENSES -> stringResource(R.string.dashboard_summary_expense)
        CategoryDisplayMode.INCOME -> stringResource(R.string.dashboard_categories_income)
    }
    val slices = categorySlices(
        categories = state.categories,
        mode = state.categoryMode,
        totalCents = totalCents,
        noCategoryLabel = stringResource(R.string.common_no_category),
        aggregateLabel = stringResource(R.string.analysis_other),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(title = stringResource(R.string.dashboard_categories_title))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = totalLabel,
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
                MoneyText(
                    cents = totalCents,
                    color = if (state.categoryMode == CategoryDisplayMode.INCOME) {
                        FinanceTheme.colors.income
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FinanceFilterChip(
                    selected = state.categoryMode == CategoryDisplayMode.EXPENSES,
                    label = stringResource(R.string.dashboard_categories_expenses),
                    onClick = { onCategoryModeChanged(CategoryDisplayMode.EXPENSES) },
                )
                FinanceFilterChip(
                    selected = state.categoryMode == CategoryDisplayMode.INCOME,
                    label = stringResource(R.string.dashboard_categories_income),
                    onClick = { onCategoryModeChanged(CategoryDisplayMode.INCOME) },
                    selectedColor = FinanceTheme.colors.income,
                )
            }
        }

        if (slices.isEmpty()) {
            Text(
                text = stringResource(R.string.dashboard_no_data),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            CategoryDistributionBar(
                slices = slices,
                totalLabel = totalLabel,
                totalCents = totalCents,
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                slices.forEach { slice ->
                    CategoryLegendRow(
                        slice = slice,
                        onClick = if (slice.categoryId != null || slice.isUncategorized) {
                            {
                                onDrillDown(
                                    state.month.actualPeriodFilters(
                                        type = if (state.categoryMode == CategoryDisplayMode.EXPENSES) {
                                            MovementType.EXPENSE
                                        } else {
                                            MovementType.INCOME
                                        },
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
    }
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
    mode: CategoryDisplayMode,
    totalCents: Long,
    noCategoryLabel: String,
    aggregateLabel: String,
): List<CategorySlice> {
    fun amountOf(row: AnalysisCategoryTotal): Long =
        if (mode == CategoryDisplayMode.EXPENSES) row.expenseCents else row.incomeCents

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

/** Bold visual anchor: a thick, rounded segmented bar; the rows below carry the precise breakdown. */
@Composable
private fun CategoryDistributionBar(
    slices: List<CategorySlice>,
    totalLabel: String,
    totalCents: Long,
) {
    val accessibility = stringResource(
        R.string.dashboard_category_donut_accessibility,
        totalLabel,
        formatEuroCents(totalCents),
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clearAndSetSemantics { contentDescription = accessibility },
    ) {
        val gap = 3.dp.toPx()
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        var startX = 0f
        slices.forEachIndexed { index, slice ->
            val rawWidth = size.width * slice.fraction
            val isLast = index == slices.lastIndex
            val segmentWidth = if (isLast) size.width - startX else (rawWidth - gap)
            if (segmentWidth > 0f) {
                drawRoundRect(
                    color = slice.color,
                    topLeft = Offset(startX, 0f),
                    size = Size(segmentWidth.coerceAtLeast(size.height), size.height),
                    cornerRadius = radius,
                )
            }
            startX += rawWidth
        }
    }
}

@Composable
private fun CategoryLegendRow(
    slice: CategorySlice,
    onClick: (() -> Unit)?,
) {
    val percentText = formatPercentLabel(slice.fraction)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 36.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconChip(
            icon = slice.icon,
            contentDescription = null,
            color = slice.color,
            size = 20.dp,
        )
        Text(
            text = slice.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MoneyText(
            cents = slice.amountCents,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = percentText,
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp),
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
