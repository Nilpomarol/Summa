package com.gestorfinances.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisIncomeExpenseBucket
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IncomeExpenseChart
import com.gestorfinances.app.ui.common.IncomeExpenseChartPoint
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.TopBarIconButton
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatBasisPoints
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.movementTypeIcon
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementSourceMode
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

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
        onNewMovement = onNewMovement,
        onViewAnalysis = onViewAnalysis,
        onSettings = onSettings,
        onDrillDown = onDrillDown,
        onMovementDetail = onMovementDetail,
        modifier = modifier,
    )
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onNewMovement: () -> Unit,
    onViewAnalysis: () -> Unit,
    onSettings: () -> Unit,
    onDrillDown: (MovementFilters) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
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
                onViewAnalysis = onViewAnalysis,
                onSettings = onSettings,
            )
        }

        item {
            NetWorthHero(netWorthCents = state.totals.netWorthCents)
        }

        item {
            KpiGrid(
                state = state,
                onDrillDown = onDrillDown,
            )
        }

        item {
            IncomeExpenseChart(
                title = stringResource(R.string.dashboard_daily_flow_title),
                points = state.month.toDailyChartPoints(state.dailyFlow),
                incomeLabel = stringResource(R.string.dashboard_month_income),
                expenseLabel = stringResource(R.string.dashboard_month_expenses),
                emptyText = stringResource(R.string.dashboard_no_data),
                onPointClick = { point ->
                    point.actualChartPointFilters()?.let(onDrillDown)
                },
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
        } else if (!state.hasMonthActivity) {
            item {
                EmptyDashboardCard(onNewMovement = onNewMovement)
            }
        }

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
            items(items = state.accounts, key = { it.id }) { account ->
                AccountOverviewRow(
                    account = account,
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

        item {
            SectionHeader(title = stringResource(R.string.dashboard_category_breakdown_title))
        }
        if (state.categories.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_no_data),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            val maxCategoryCents = state.categories.maxOf { abs(it.netCents) }.coerceAtLeast(1L)
            items(items = state.categories, key = { it.categoryId ?: "uncategorized" }) { category ->
                CategoryBreakdownRow(
                    category = category,
                    maxCents = maxCategoryCents,
                    onClick = {
                        onDrillDown(
                            state.month.actualPeriodFilters(
                                categoryId = category.categoryId,
                                uncategorizedOnly = category.categoryId == null,
                            ),
                        )
                    },
                )
            }
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
                LatestMovementRow(
                    movement = movement,
                    onClick = { onMovementDetail(movement) },
                )
            }
        }

        item {
            QuickActions(onNewMovement = onNewMovement)
        }
    }
}

@Composable
private fun DashboardHeader(
    month: YearMonth,
    onViewAnalysis: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onViewAnalysis) {
                Text(text = stringResource(R.string.dashboard_view_analysis))
            }
            TopBarIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.nav_settings),
                onClick = onSettings,
            )
        }
    }
}

@Composable
private fun NetWorthHero(netWorthCents: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = FinanceTheme.colors.heroSurface,
        contentColor = FinanceTheme.colors.heroOnSurface,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_net_worth),
                style = MaterialTheme.typography.labelMedium,
                color = FinanceTheme.colors.heroOnSurfaceMuted,
            )
            MoneyText(
                cents = netWorthCents,
                color = FinanceTheme.colors.heroOnSurface,
                style = MaterialTheme.typography.displayMedium,
            )
        }
    }
}

@Composable
private fun KpiGrid(
    state: DashboardUiState,
    onDrillDown: (MovementFilters) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(
                label = stringResource(R.string.dashboard_month_income),
                cents = state.totals.actualIncomeCents,
                color = FinanceTheme.colors.income,
                onClick = {
                    onDrillDown(
                        state.month.actualPeriodFilters(type = MovementType.INCOME),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            KpiCard(
                label = stringResource(R.string.dashboard_month_expenses),
                cents = -state.totals.actualExpenseCents,
                color = MaterialTheme.colorScheme.onSurface,
                onClick = {
                    onDrillDown(
                        state.month.actualPeriodFilters(type = MovementType.EXPENSE),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(
                label = stringResource(R.string.dashboard_net_flow),
                cents = state.totals.accountFlowCents,
                color = if (state.totals.accountFlowCents >= 0) {
                    FinanceTheme.colors.income
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                signed = true,
                onClick = {
                    onDrillDown(
                        state.month.flowPeriodFilters(),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            SavingsRateCard(
                state = state,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    cents: Long,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    FinanceCard(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MoneyText(
                cents = cents,
                color = color,
                style = MaterialTheme.typography.titleMedium,
                signed = signed,
            )
        }
    }
}

@Composable
private fun SavingsRateCard(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
) {
    FinanceCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_savings_rate),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (state.totals.actualIncomeCents > 0) {
                    formatBasisPoints(state.totals.savingsRateBasisPoints)
                } else {
                    stringResource(R.string.dashboard_savings_rate_unavailable)
                },
                color = if (state.totals.netActualCents >= 0) {
                    FinanceTheme.colors.income
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun EmptyDashboardCard(onNewMovement: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.dashboard_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton(
                text = stringResource(R.string.movement_list_add),
                onClick = onNewMovement,
                leadingIcon = Icons.Filled.Add,
            )
        }
    }
}

@Composable
private fun AccountOverviewRow(
    account: AccountSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(
            icon = accountTypeIcon(account.type),
            contentDescription = null,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            tint = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = account.type.label(),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        MoneyText(cents = account.currentBalanceCents)
    }
}

@Composable
private fun CategoryBreakdownRow(
    category: AnalysisCategoryTotal,
    maxCents: Long,
    onClick: () -> Unit,
) {
    val color = categoryColor(category.categoryColor)
    val amount = category.netCents
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
            MoneyText(
                cents = amount,
                color = if (amount >= 0) FinanceTheme.colors.income else MaterialTheme.colorScheme.onSurface,
                signed = true,
            )
        }
        LinearProgressIndicator(
            progress = { (abs(amount).toFloat() / maxCents.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun LatestMovementRow(
    movement: MovementSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(
            icon = movementTypeIcon(movement.type),
            contentDescription = null,
            color = FinanceTheme.colors.amountColor(movement.type),
            size = 36.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movement.title(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = movement.date,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        MoneyText(
            cents = movement.signedAmountCents(),
            color = FinanceTheme.colors.amountColor(movement.type),
            signed = movement.type != MovementType.EXPENSE && movement.type != MovementType.TRANSFER,
        )
    }
}

@Composable
private fun QuickActions(onNewMovement: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = stringResource(R.string.dashboard_quick_actions_title))
        PrimaryButton(
            text = stringResource(R.string.movement_list_add),
            onClick = onNewMovement,
            leadingIcon = Icons.Filled.Add,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun MovementSummary.signedAmountCents(): Long =
    if (type == MovementType.EXPENSE) -amountCents else amountCents

@Composable
private fun MovementSummary.title(): String =
    name ?: payee ?: categoryName ?: type.label()

private fun YearMonth.toDailyChartPoints(
    buckets: List<AnalysisIncomeExpenseBucket>,
): List<IncomeExpenseChartPoint> {
    val byDay = buckets.associateBy { it.bucket }
    val points = mutableListOf<IncomeExpenseChartPoint>()
    for (day in 1..lengthOfMonth()) {
        val date = atDay(day).toString()
        val bucket = byDay[date]
        points += IncomeExpenseChartPoint(
            label = day.toString(),
            bucket = date,
            incomeCents = bucket?.incomeCents ?: 0L,
            expenseCents = bucket?.expenseCents ?: 0L,
        )
    }
    return points
}

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

private fun IncomeExpenseChartPoint.actualChartPointFilters(): MovementFilters? {
    val date = runCatching { LocalDate.parse(bucket) }.getOrNull() ?: return null
    return MovementFilters(
        sourceMode = MovementSourceMode.ACTUAL,
        dateFrom = date.toString(),
        dateTo = date.toString(),
    )
}
