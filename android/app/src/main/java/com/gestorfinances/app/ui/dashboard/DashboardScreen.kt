package com.gestorfinances.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.TopBarIconButton
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatBasisPoints
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementSourceMode
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNewMovement: () -> Unit,
    onViewAnalysis: () -> Unit,
    onSettings: () -> Unit,
    onDrillDown: (MovementFilters) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    DashboardContent(
        state = state,
        onSettings = onSettings,
        onDrillDown = onDrillDown,
        onMovementDetail = onMovementDetail,
        onHeroAccountSelected = viewModel::onHeroAccountSelected,
        onCategoryModeChanged = viewModel::onCategoryModeChanged,
        modifier = modifier,
    )
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onSettings: () -> Unit,
    onDrillDown: (MovementFilters) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    onHeroAccountSelected: (String?) -> Unit,
    onCategoryModeChanged: (CategoryDisplayMode) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DashboardHeader(
                month = state.month,
                onSettings = onSettings,
            )
        }

        item {
            MainAccountCard(
                state = state,
                onAccountSelected = onHeroAccountSelected,
                onDrillDown = onDrillDown,
            )
        }

        state.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
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

        // Accounts section — patrimoni overview, always visible
        item {
            SectionHeader(title = stringResource(R.string.dashboard_accounts_title))
        }
        if (state.accounts.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.movement_no_accounts_body),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            item {
                PatrimoniCard(
                    accounts = state.accounts,
                    onDrillDown = onDrillDown,
                )
            }
        }

        item {
            CategorySection(
                state = state,
                onCategoryModeChanged = onCategoryModeChanged,
                onDrillDown = onDrillDown,
            )
        }

        item {
            SectionHeader(title = stringResource(R.string.dashboard_latest_movements_title))
        }
        if (state.latestMovements.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_empty_body),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(items = state.latestMovements, key = { it.id }) { movement ->
                MovementListItem(
                    movement = movement,
                    onClick = { onMovementDetail(movement) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun DashboardHeader(
    month: YearMonth,
    onSettings: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = formatMonthYear(month),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TopBarIconButton(
            icon = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.nav_settings),
            onClick = onSettings,
        )
    }
}

// ---------------------------------------------------------------------------
// Main account + KPI card (dark hero)
// ---------------------------------------------------------------------------

@Composable
private fun MainAccountCard(
    state: DashboardUiState,
    onAccountSelected: (String?) -> Unit,
    onDrillDown: (MovementFilters) -> Unit,
) {
    // Resolve the headline account: explicit selection → default account → first account.
    val heroAccount = state.accounts.firstOrNull { it.id == state.selectedHeroAccountId }
        ?: state.accounts.firstOrNull { it.isDefault }
        ?: state.accounts.firstOrNull()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = FinanceTheme.colors.heroSurface,
        contentColor = FinanceTheme.colors.heroOnSurface,
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)) {
            // Label row: account icon + name (or net worth fallback) + switch menu top-right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (heroAccount != null) {
                    IconChip(
                        icon = if (heroAccount.icon != null) accountIcon(heroAccount.icon)
                               else accountTypeIcon(heroAccount.type),
                        contentDescription = null,
                        color = categoryColor(heroAccount.color),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = heroAccount?.name ?: stringResource(R.string.dashboard_net_worth),
                    style = MaterialTheme.typography.labelMedium,
                    color = FinanceTheme.colors.heroOnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (state.accounts.isNotEmpty()) {
                    AccountSwitchMenu(
                        accounts = state.accounts,
                        onSelect = onAccountSelected,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Big headline figure
            val balanceCents = heroAccount?.currentBalanceCents ?: state.totals.netWorthCents
            val balanceModifier = if (heroAccount != null) {
                Modifier.clickable {
                    onDrillDown(
                        MovementFilters(
                            accountId = heroAccount.id,
                            sourceMode = MovementSourceMode.FLOW,
                        ),
                    )
                }
            } else {
                Modifier
            }
            Box(modifier = balanceModifier) {
                MoneyText(
                    cents = balanceCents,
                    color = if (balanceCents < 0) FinanceTheme.colors.debt
                            else FinanceTheme.colors.heroOnSurface,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 40.sp,
                        lineHeight = 46.sp,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            HeroKpiBlock(state = state, onDrillDown = onDrillDown)
        }
    }
}

@Composable
private fun AccountSwitchMenu(
    accounts: List<AccountSummary>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.common_more_options),
                tint = FinanceTheme.colors.heroOnSurfaceMuted,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = {
                        expanded = false
                        onSelect(account.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun HeroKpiBlock(
    state: DashboardUiState,
    onDrillDown: (MovementFilters) -> Unit,
) {
    val income = state.totals.actualIncomeCents
    val expense = state.totals.actualExpenseCents
    val incomeColor = FinanceTheme.colors.income

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroKpiCell(
            label = stringResource(R.string.dashboard_categories_income),
            onClick = { onDrillDown(state.month.actualPeriodFilters(type = MovementType.INCOME)) },
        ) {
            MoneyText(
                cents = income,
                color = incomeColor,
                style = MaterialTheme.typography.titleMedium,
                signed = true,
            )
        }
        HeroKpiCell(
            label = stringResource(R.string.dashboard_categories_expenses),
            onClick = { onDrillDown(state.month.actualPeriodFilters(type = MovementType.EXPENSE)) },
        ) {
            MoneyText(
                cents = -expense,
                color = FinanceTheme.colors.heroOnSurface,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        HeroKpiCell(
            label = stringResource(R.string.dashboard_savings_short),
            alignEnd = true,
            onClick = null,
        ) {
            Text(
                text = if (income > 0) {
                    formatBasisPoints(state.totals.savingsRateBasisPoints)
                } else {
                    stringResource(R.string.dashboard_savings_rate_unavailable)
                },
                color = FinanceTheme.colors.heroOnSurface,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun RowScope.HeroKpiCell(
    label: String,
    onClick: (() -> Unit)?,
    alignEnd: Boolean = false,
    value: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = FinanceTheme.colors.heroOnSurfaceMuted,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        value()
    }
}

// ---------------------------------------------------------------------------
// Patrimoni overview card (light)
// ---------------------------------------------------------------------------

@Composable
private fun PatrimoniCard(
    accounts: List<AccountSummary>,
    onDrillDown: (MovementFilters) -> Unit,
) {
    val netWorthCents = accounts.sumOf { it.currentBalanceCents }
    val positiveAccounts = accounts.filter { it.currentBalanceCents > 0 }

    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.dashboard_net_worth),
                style = MaterialTheme.typography.labelMedium,
                color = FinanceTheme.colors.mutedText,
            )
            Spacer(modifier = Modifier.height(6.dp))
            MoneyText(
                cents = netWorthCents,
                color = if (netWorthCents < 0) FinanceTheme.colors.debt
                        else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (positiveAccounts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                ) {
                    positiveAccounts.forEach { account ->
                        Box(
                            modifier = Modifier
                                .weight(account.currentBalanceCents.toFloat())
                                .fillMaxHeight()
                                .background(categoryColor(account.color)),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(6.dp))

            accounts.forEach { account ->
                PatrimoniAccountRow(
                    account = account,
                    netWorthCents = netWorthCents,
                    onClick = {
                        onDrillDown(
                            MovementFilters(
                                accountId = account.id,
                                sourceMode = MovementSourceMode.FLOW,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PatrimoniAccountRow(
    account: AccountSummary,
    netWorthCents: Long,
    onClick: () -> Unit,
) {
    val accountColor = categoryColor(account.color)
    val fraction = if (netWorthCents > 0 && account.currentBalanceCents > 0) {
        account.currentBalanceCents.toFloat() / netWorthCents.toFloat()
    } else 0f
    val percentText = when {
        account.currentBalanceCents <= 0 -> null
        fraction < 0.01f -> "<1%"
        else -> "${(fraction * 100).toInt()}%"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(accountColor, CircleShape),
        )
        Text(
            text = account.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (percentText != null) {
            Text(
                text = percentText,
                style = MaterialTheme.typography.labelSmall,
                color = FinanceTheme.colors.mutedText,
            )
        }
        MoneyText(
            cents = account.currentBalanceCents,
            color = if (account.currentBalanceCents < 0) FinanceTheme.colors.debt
                    else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            signed = account.currentBalanceCents < 0,
        )
    }
}

// ---------------------------------------------------------------------------
// Category section with toggle
// ---------------------------------------------------------------------------

@Composable
private fun CategorySection(
    state: DashboardUiState,
    onCategoryModeChanged: (CategoryDisplayMode) -> Unit,
    onDrillDown: (MovementFilters) -> Unit,
) {
    val totalCents = when (state.categoryMode) {
        CategoryDisplayMode.EXPENSES -> state.totals.actualExpenseCents
        CategoryDisplayMode.INCOME -> state.totals.actualIncomeCents
    }.coerceAtLeast(1L)

    val displayCategories = when (state.categoryMode) {
        CategoryDisplayMode.EXPENSES ->
            state.categories
                .filter { it.expenseCents > 0 }
                .sortedByDescending { it.expenseCents }
        CategoryDisplayMode.INCOME ->
            state.categories
                .filter { it.incomeCents > 0 }
                .sortedByDescending { it.incomeCents }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dashboard_category_breakdown_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
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
                )
            }
        }

        if (displayCategories.isEmpty()) {
            Text(
                text = stringResource(R.string.dashboard_no_data),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            displayCategories.forEach { category ->
                CategoryBreakdownRow(
                    category = category,
                    mode = state.categoryMode,
                    totalCents = totalCents,
                    onClick = {
                        onDrillDown(
                            if (state.categoryMode == CategoryDisplayMode.EXPENSES) {
                                state.month.actualPeriodFilters(
                                    type = MovementType.EXPENSE,
                                    categoryId = category.categoryId,
                                    uncategorizedOnly = category.categoryId == null,
                                )
                            } else {
                                state.month.actualPeriodFilters(
                                    type = MovementType.INCOME,
                                    categoryId = category.categoryId,
                                    uncategorizedOnly = category.categoryId == null,
                                )
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryBreakdownRow(
    category: AnalysisCategoryTotal,
    mode: CategoryDisplayMode,
    totalCents: Long,
    onClick: () -> Unit,
) {
    val color = categoryColor(category.categoryColor)
    val amount = if (mode == CategoryDisplayMode.EXPENSES) category.expenseCents else category.incomeCents
    val displayAmount = if (mode == CategoryDisplayMode.EXPENSES) -amount else amount
    val fraction = (amount.toFloat() / totalCents.toFloat()).coerceIn(0f, 1f)
    val pctText = when {
        fraction < 0.005f -> "<1%"
        else -> "${(fraction * 100).roundToInt()}%"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(
                icon = categoryIcon(category.categoryIcon),
                contentDescription = null,
                color = color,
                size = 36.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = category.categoryName ?: stringResource(R.string.common_no_category),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    cents = displayAmount,
                    color = if (mode == CategoryDisplayMode.INCOME) FinanceTheme.colors.income
                            else MaterialTheme.colorScheme.onSurface,
                    signed = mode == CategoryDisplayMode.INCOME,
                )
                Text(
                    text = pctText,
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                )
            }
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Extension helpers
// ---------------------------------------------------------------------------

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

private fun YearMonth.flowPeriodFilters(): MovementFilters =
    MovementFilters(
        sourceMode = MovementSourceMode.FLOW,
        dateFrom = atDay(1).toString(),
        dateTo = atEndOfMonth().toString(),
    )
