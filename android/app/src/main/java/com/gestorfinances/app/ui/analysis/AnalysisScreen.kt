package com.gestorfinances.app.ui.analysis

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisAccountFlowBucket
import com.gestorfinances.app.data.repository.AnalysisBucket
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisCategoryTrendPoint
import com.gestorfinances.app.data.repository.AnalysisIncomeExpenseBucket
import com.gestorfinances.app.data.repository.AnalysisLargestExpense
import com.gestorfinances.app.data.repository.AnalysisMerchantTotal
import com.gestorfinances.app.data.repository.AnalysisNetWorthPoint
import com.gestorfinances.app.data.repository.AnalysisOneTimeMode
import com.gestorfinances.app.data.repository.AnalysisPeriodTotals
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IncomeExpenseChart
import com.gestorfinances.app.ui.common.IncomeExpenseChartPoint
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.TrendLineChart
import com.gestorfinances.app.ui.common.TrendSeries
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatBasisPoints
import com.gestorfinances.app.ui.common.formatLongDate
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementOneTimeMode as MovementFilterOneTimeMode
import com.gestorfinances.app.ui.movements.MovementSourceMode
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel,
    onDrillDown: (MovementFilters) -> Unit,
    onManageBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    AnalysisContent(
        state = state,
        onManageBudgets = onManageBudgets,
        onScopeSelected = viewModel::onScopeSelected,
        onAnalysisModeSelected = viewModel::onAnalysisModeSelected,
        onValueModeSelected = viewModel::onValueModeSelected,
        onNatureFilterSelected = viewModel::onNatureFilterSelected,
        onOneTimeModeSelected = viewModel::onOneTimeModeSelected,
        onPreviousPeriod = viewModel::onPreviousPeriodClicked,
        onNextPeriod = viewModel::onNextPeriodClicked,
        onComparePreviousChange = viewModel::onComparePreviousChanged,
        onCustomFromChange = viewModel::onCustomFromChanged,
        onCustomToChange = viewModel::onCustomToChanged,
        onResetPeriod = viewModel::onResetPeriodClicked,
        onDrillDown = onDrillDown,
        modifier = modifier,
    )
}

@Composable
private fun AnalysisContent(
    state: AnalysisUiState,
    onManageBudgets: () -> Unit,
    onScopeSelected: (AnalysisScope) -> Unit,
    onAnalysisModeSelected: (AnalysisMode) -> Unit,
    onValueModeSelected: (AnalysisValueMode) -> Unit,
    onNatureFilterSelected: (AnalysisNatureFilter) -> Unit,
    onOneTimeModeSelected: (AnalysisOneTimeMode) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onComparePreviousChange: (Boolean) -> Unit,
    onCustomFromChange: (String) -> Unit,
    onCustomToChange: (String) -> Unit,
    onResetPeriod: () -> Unit,
    onDrillDown: (MovementFilters) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.analysis_title),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.hasActivePeriodControl) {
                            TextButton(onClick = onResetPeriod) {
                                Text(text = stringResource(R.string.common_clear_filters))
                            }
                        }
                        TextButton(onClick = onManageBudgets) {
                            Text(text = stringResource(R.string.budget_list_title))
                        }
                    }
                },
            )
        }

        item {
            SegmentedControl(
                options = AnalysisScope.entries,
                selected = state.scope,
                label = { stringResource(it.labelRes()) },
                onSelect = onScopeSelected,
            )
        }

        item {
            AnalysisModeControls(
                state = state,
                onAnalysisModeSelected = onAnalysisModeSelected,
                onValueModeSelected = onValueModeSelected,
            )
        }

        item {
            PeriodSelector(
                state = state,
                onPreviousPeriod = onPreviousPeriod,
                onNextPeriod = onNextPeriod,
            )
        }

        if (state.scope == AnalysisScope.CUSTOM) {
            item {
                CustomDateFields(
                    state = state,
                    onCustomFromChange = onCustomFromChange,
                    onCustomToChange = onCustomToChange,
                )
            }
        }

        if (state.canCompare) {
            item {
                ComparePreviousRow(
                    checked = state.comparePrevious,
                    onCheckedChange = onComparePreviousChange,
                )
            }
        }

        if (state.analysisMode == AnalysisMode.ACTUAL) {
            item {
                ActualFilterControls(
                    state = state,
                    onNatureFilterSelected = onNatureFilterSelected,
                    onOneTimeModeSelected = onOneTimeModeSelected,
                )
            }
        }

        state.errorMessage?.let { message ->
            item {
                InlineBanner(kind = BannerKind.Error, text = message)
            }
        }

        state.customErrorRes?.let { errorRes ->
            item {
                InlineBanner(
                    kind = BannerKind.Error,
                    text = stringResource(errorRes),
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
        } else if (!state.hasActivity) {
            item {
                EmptyAnalysisCard()
            }
        }

        item {
            SummaryGrid(
                state = state,
                onDrillDown = onDrillDown,
            )
        }

        if (state.previousTotals != null && state.previousRange != null) {
            item {
                Text(
                    text = "${stringResource(R.string.analysis_period_previous)}: ${state.previousRange.formatForScope(state.scope)}",
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            IncomeExpenseChart(
                title = stringResource(
                    if (state.analysisMode == AnalysisMode.ACTUAL) {
                        R.string.analysis_income_vs_expense_title
                    } else {
                        R.string.analysis_account_flow_title
                    },
                ),
                points = state.toChartPoints(),
                incomeLabel = stringResource(
                    if (state.analysisMode == AnalysisMode.ACTUAL) {
                        R.string.analysis_summary_income
                    } else {
                        R.string.analysis_flow_positive
                    },
                ),
                expenseLabel = stringResource(
                    if (state.analysisMode == AnalysisMode.ACTUAL) {
                        R.string.analysis_summary_expense
                    } else {
                        R.string.analysis_flow_negative
                    },
                ),
                emptyText = stringResource(R.string.dashboard_no_data),
                onPointClick = { point ->
                    state.chartPointFilters(point)?.let(onDrillDown)
                },
            )
        }

        item {
            SectionHeader(title = stringResource(R.string.analysis_breakdown_title))
        }

        if (state.analysisMode == AnalysisMode.ACTUAL && state.categories.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_no_data),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (state.analysisMode == AnalysisMode.ACTUAL) {
            val maxCategoryCents = state.categories.maxOf { abs(it.netCents) }.coerceAtLeast(1L)
            items(items = state.categories, key = { it.categoryId ?: "uncategorized" }) { category ->
                CategoryBreakdownRow(
                    category = category,
                    maxCents = maxCategoryCents,
                    divisor = state.currentDisplayDivisor(),
                    onClick = {
                        state.periodFilters(
                            categoryId = category.categoryId,
                            uncategorizedOnly = category.categoryId == null,
                        )?.let(onDrillDown)
                    },
                )
            }
        } else if (state.flowBuckets.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_no_data),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(
                items = state.flowBuckets,
                key = { "${it.bucket}-${it.accountId}" },
            ) { flow ->
                AccountFlowBreakdownRow(
                    flow = flow,
                    divisor = state.currentDisplayDivisor(),
                    onClick = {
                        state.flowBucketFilters(flow)?.let(onDrillDown)
                    },
                )
            }
        }

        if (state.analysisMode == AnalysisMode.ACTUAL && state.hasActivity) {
            analysisWidgets(state = state, onDrillDown = onDrillDown)
        }
    }
}

@Composable
private fun AnalysisModeControls(
    state: AnalysisUiState,
    onAnalysisModeSelected: (AnalysisMode) -> Unit,
    onValueModeSelected: (AnalysisValueMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SegmentedControl(
            options = AnalysisMode.entries,
            selected = state.analysisMode,
            label = { stringResource(it.labelRes()) },
            onSelect = onAnalysisModeSelected,
        )
        SegmentedControl(
            options = AnalysisValueMode.entries,
            selected = state.valueMode,
            label = { stringResource(it.labelRes()) },
            onSelect = onValueModeSelected,
        )
    }
}

@Composable
private fun ActualFilterControls(
    state: AnalysisUiState,
    onNatureFilterSelected: (AnalysisNatureFilter) -> Unit,
    onOneTimeModeSelected: (AnalysisOneTimeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChipFlowSection(label = stringResource(R.string.analysis_filter_nature)) {
            AnalysisNatureFilter.entries.forEach { filter ->
                FinanceFilterChip(
                    selected = state.natureFilter == filter,
                    label = stringResource(filter.labelRes()),
                    onClick = { onNatureFilterSelected(filter) },
                )
            }
        }
        ChipFlowSection(label = stringResource(R.string.movement_field_one_time)) {
            AnalysisOneTimeMode.entries.forEach { mode ->
                FinanceFilterChip(
                    selected = state.oneTimeMode == mode,
                    label = stringResource(mode.labelRes()),
                    onClick = { onOneTimeModeSelected(mode) },
                )
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    state: AnalysisUiState,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.canMovePeriod) {
                TextButton(onClick = onPreviousPeriod) {
                    Text(text = stringResource(R.string.common_back))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.analysis_period_current),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = state.currentRange?.formatForScope(state.scope)
                        ?: state.fallbackPeriodLabel(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.canMovePeriod) {
                TextButton(onClick = onNextPeriod) {
                    Text(text = stringResource(R.string.common_next))
                }
            }
        }
    }
}

@Composable
private fun CustomDateFields(
    state: AnalysisUiState,
    onCustomFromChange: (String) -> Unit,
    onCustomToChange: (String) -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.customFrom,
                    onValueChange = onCustomFromChange,
                    label = { Text(text = stringResource(R.string.movement_filter_date_from)) },
                    supportingText = { Text(text = stringResource(R.string.movement_date_format_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.customTo,
                    onValueChange = onCustomToChange,
                    label = { Text(text = stringResource(R.string.movement_filter_date_to)) },
                    supportingText = { Text(text = stringResource(R.string.movement_date_format_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ComparePreviousRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
            Text(
                text = stringResource(R.string.analysis_compare_previous),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SummaryGrid(
    state: AnalysisUiState,
    onDrillDown: (MovementFilters) -> Unit,
) {
    when (state.analysisMode) {
        AnalysisMode.ACTUAL -> ActualSummaryGrid(state = state, onDrillDown = onDrillDown)
        AnalysisMode.FLOW -> FlowSummaryGrid(state = state, onDrillDown = onDrillDown)
    }
}

@Composable
private fun ActualSummaryGrid(
    state: AnalysisUiState,
    onDrillDown: (MovementFilters) -> Unit,
) {
    val previous = state.previousTotals
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyMetricCard(
                label = stringResource(R.string.analysis_summary_expense),
                cents = state.displayCents(-state.totals.actualExpenseCents),
                previousCents = previous?.let { state.displayPreviousCents(-it.actualExpenseCents) },
                color = MaterialTheme.colorScheme.onSurface,
                onClick = {
                    state.periodFilters(type = MovementType.EXPENSE)?.let(onDrillDown)
                },
                modifier = Modifier.weight(1f),
            )
            MoneyMetricCard(
                label = stringResource(R.string.analysis_summary_income),
                cents = state.displayCents(state.totals.actualIncomeCents),
                previousCents = previous?.let { state.displayPreviousCents(it.actualIncomeCents) },
                color = FinanceTheme.colors.income,
                onClick = {
                    state.periodFilters(type = MovementType.INCOME)?.let(onDrillDown)
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyMetricCard(
                label = stringResource(R.string.analysis_summary_net),
                cents = state.displayCents(state.totals.netActualCents),
                previousCents = previous?.let { state.displayPreviousCents(it.netActualCents) },
                color = if (state.totals.netActualCents >= 0) {
                    FinanceTheme.colors.income
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                signed = true,
                onClick = {
                    state.periodFilters()?.let(onDrillDown)
                },
                modifier = Modifier.weight(1f),
            )
            RateMetricCard(
                currentBasisPoints = state.totals.savingsRateBasisPoints,
                previousBasisPoints = previous?.savingsRateBasisPoints,
                hasIncome = state.totals.actualIncomeCents > 0,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FlowSummaryGrid(
    state: AnalysisUiState,
    onDrillDown: (MovementFilters) -> Unit,
) {
    val previous = state.previousTotals
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MoneyMetricCard(
            label = stringResource(R.string.dashboard_net_worth),
            cents = state.totals.netWorthCents,
            previousCents = null,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        MoneyMetricCard(
            label = stringResource(R.string.dashboard_net_flow),
            cents = state.displayCents(state.totals.accountFlowCents),
            previousCents = previous?.let { state.displayPreviousCents(it.accountFlowCents) },
            color = if (state.totals.accountFlowCents >= 0) {
                FinanceTheme.colors.income
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            signed = true,
            onClick = {
                state.periodFilters()?.let(onDrillDown)
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MoneyMetricCard(
    label: String,
    cents: Long,
    previousCents: Long?,
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
            previousCents?.let {
                ComparisonMoney(previousCents = it, currentCents = cents)
            }
        }
    }
}

@Composable
private fun ComparisonMoney(
    previousCents: Long,
    currentCents: Long,
) {
    val delta = currentCents - previousCents
    Text(
        text = stringResource(R.string.analysis_period_previous),
        color = FinanceTheme.colors.mutedText,
        style = MaterialTheme.typography.labelSmall,
    )
    MoneyText(
        cents = previousCents,
        color = FinanceTheme.colors.mutedText,
        style = MaterialTheme.typography.labelMedium,
    )
    Text(
        text = stringResource(R.string.analysis_delta),
        color = FinanceTheme.colors.mutedText,
        style = MaterialTheme.typography.labelSmall,
    )
    MoneyText(
        cents = delta,
        color = if (delta >= 0) FinanceTheme.colors.income else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelMedium,
        signed = true,
    )
}

@Composable
private fun RateMetricCard(
    currentBasisPoints: Long,
    previousBasisPoints: Long?,
    hasIncome: Boolean,
    modifier: Modifier = Modifier,
) {
    FinanceCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.analysis_savings_rate_title),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (hasIncome) {
                    formatBasisPoints(currentBasisPoints)
                } else {
                    stringResource(R.string.dashboard_savings_rate_unavailable)
                },
                color = if (currentBasisPoints >= 0) {
                    FinanceTheme.colors.income
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.titleMedium,
            )
            previousBasisPoints?.let {
                Text(
                    text = stringResource(R.string.analysis_period_previous),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = formatBasisPoints(it),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringResource(R.string.analysis_delta),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
                val delta = currentBasisPoints - it
                Text(
                    text = formatBasisPoints(delta),
                    color = if (delta >= 0) FinanceTheme.colors.income else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun CategoryBreakdownRow(
    category: AnalysisCategoryTotal,
    maxCents: Long,
    divisor: Long,
    onClick: () -> Unit,
) {
    val color = categoryColor(category.categoryColor)
    val amount = category.netCents
    val displayAmount = amount.divideCents(divisor)
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
                cents = displayAmount,
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
private fun AccountFlowBreakdownRow(
    flow: AnalysisAccountFlowBucket,
    divisor: Long,
    onClick: () -> Unit,
) {
    val displayAmount = flow.deltaCents.divideCents(divisor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = flow.accountName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = flow.bucket,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        MoneyText(
            cents = displayAmount,
            color = if (flow.deltaCents >= 0) FinanceTheme.colors.income else MaterialTheme.colorScheme.onSurface,
            signed = true,
        )
    }
}

@Composable
private fun EmptyAnalysisCard() {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.analysis_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.analysis_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun LazyListScope.analysisWidgets(
    state: AnalysisUiState,
    onDrillDown: (MovementFilters) -> Unit,
) {
    if (state.netWorthPoints.size >= 2) {
        item {
            NetWorthTrendWidget(points = state.netWorthPoints)
        }
    }

    if (state.largestExpenses.isNotEmpty()) {
        item {
            SectionHeader(title = stringResource(R.string.analysis_largest_expenses_title))
        }
        items(items = state.largestExpenses, key = { it.sourceId }) { expense ->
            LargestExpenseRow(
                expense = expense,
                onClick = { state.largestExpenseFilters(expense)?.let(onDrillDown) },
            )
        }
    }

    if (state.topMerchants.isNotEmpty()) {
        item {
            SectionHeader(title = stringResource(R.string.analysis_top_merchants_title))
        }
        items(items = state.topMerchants, key = { it.merchantLabel ?: "_none" }) { merchant ->
            MerchantRow(merchant = merchant)
        }
    }

    val savingsBuckets = state.chartBuckets.filter { it.incomeCents > 0 }
    if (savingsBuckets.size >= 2) {
        item {
            SectionHeader(title = stringResource(R.string.analysis_savings_rate_period_title))
        }
        items(items = savingsBuckets, key = { it.bucket }) { bucket ->
            SavingsRateRow(
                bucket = bucket,
                onClick = { state.savingsBucketFilters(bucket.bucket)?.let(onDrillDown) },
            )
        }
    }

    if (state.categoryTrends.isNotEmpty()) {
        item {
            CategoryTrendsWidget(trends = state.categoryTrends)
        }
    }

    if (state.heatmapDays.isNotEmpty() && state.currentRange != null) {
        item {
            SpendingHeatmapWidget(cells = state.heatmapCells())
        }
    }
}

@Composable
private fun LargestExpenseRow(
    expense: AnalysisLargestExpense,
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
            icon = categoryIcon(expense.categoryIcon),
            contentDescription = null,
            color = categoryColor(expense.categoryColor),
            size = 36.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = expense.label
                    ?: expense.categoryName
                    ?: stringResource(R.string.common_no_category),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatLongDate(expense.date),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        MoneyText(
            cents = expense.amountCents,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MerchantRow(merchant: AnalysisMerchantTotal) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = merchant.merchantLabel ?: stringResource(R.string.analysis_merchant_none),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.analysis_merchant_count,
                    merchant.movementCount.toInt(),
                    merchant.movementCount.toInt(),
                ),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        MoneyText(
            cents = merchant.totalCents,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SavingsRateRow(
    bucket: AnalysisIncomeExpenseBucket,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = bucket.bucket,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatBasisPoints(bucket.savingsRateBasisPoints),
                color = if (bucket.savingsRateBasisPoints >= 0) {
                    FinanceTheme.colors.income
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.titleSmall,
            )
        }
        LinearProgressIndicator(
            progress = {
                (bucket.savingsRateBasisPoints.toFloat() / 10000f).coerceIn(0f, 1f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = FinanceTheme.colors.income,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun NetWorthTrendWidget(points: List<AnalysisNetWorthPoint>) {
    val series = listOf(
        TrendSeries(
            label = stringResource(R.string.analysis_net_worth_title),
            color = MaterialTheme.colorScheme.primary,
            pointsEuros = points.sortedBy { it.bucket }.map { it.netWorthCents / 100f },
        ),
    )
    TrendLineChart(
        title = stringResource(R.string.analysis_net_worth_title),
        series = series,
        emptyText = stringResource(R.string.dashboard_no_data),
    )
}

@Composable
private fun CategoryTrendsWidget(trends: List<AnalysisCategoryTrendPoint>) {
    val buckets = trends.map { it.bucket }.distinct().sorted()
    val byCategory = trends.groupBy { it.categoryId }
    val topCategories = byCategory.entries
        .sortedByDescending { entry -> entry.value.sumOf { it.expenseCents } }
        .take(MAX_TREND_SERIES)
    val noCategory = stringResource(R.string.common_no_category)
    val series = topCategories.map { (_, rows) ->
        val byBucket = rows.associate { it.bucket to it.expenseCents }
        TrendSeries(
            label = rows.first().categoryName ?: noCategory,
            color = categoryColor(rows.first().categoryColor),
            pointsEuros = buckets.map { (byBucket[it] ?: 0L) / 100f },
        )
    }
    TrendLineChart(
        title = stringResource(R.string.analysis_category_trends_title),
        series = series,
        emptyText = stringResource(R.string.dashboard_no_data),
    )
}

@Composable
private fun SpendingHeatmapWidget(cells: List<HeatmapCell>) {
    val maxCents = cells.maxOfOrNull { it.expenseCents }?.coerceAtLeast(1L) ?: 1L
    // Spend intensity is a neutral ink ramp: expense is ink, not a hue, and indigo is
    // reserved for action (design §2.4 / §2.3). Darker cell = more spent that day.
    val base = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = stringResource(R.string.analysis_heatmap_title))
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeatmapGrid(cells = cells, maxCents = maxCents, base = base, ink = ink)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.analysis_heatmap_legend_less),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    listOf(0f, 0.33f, 0.66f, 1f).forEach { intensity ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = heatColor(intensity, base, ink),
                                    shape = MaterialTheme.shapes.extraSmall,
                                ),
                        )
                    }
                    Text(
                        text = stringResource(R.string.analysis_heatmap_legend_more),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeatmapGrid(
    cells: List<HeatmapCell>,
    maxCents: Long,
    base: androidx.compose.ui.graphics.Color,
    ink: androidx.compose.ui.graphics.Color,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cells.forEach { cell ->
            val intensity = cell.expenseCents.toFloat() / maxCents.toFloat()
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = heatColor(intensity, base, ink),
                        shape = MaterialTheme.shapes.extraSmall,
                    ),
            )
        }
    }
}

private fun heatColor(
    intensity: Float,
    base: androidx.compose.ui.graphics.Color,
    ink: androidx.compose.ui.graphics.Color,
): androidx.compose.ui.graphics.Color {
    if (intensity <= 0f) return base
    return lerp(base, ink, (0.15f + 0.85f * intensity.coerceIn(0f, 1f)))
}

private data class HeatmapCell(
    val date: LocalDate,
    val expenseCents: Long,
)

private fun AnalysisUiState.heatmapCells(): List<HeatmapCell> {
    val range = currentRange ?: return emptyList()
    val byDay = heatmapDays.associate { it.bucket to it.expenseCents }
    val cells = mutableListOf<HeatmapCell>()
    var cursor = range.fromDate
    while (cursor < range.toDateExclusive) {
        cells += HeatmapCell(date = cursor, expenseCents = byDay[cursor.toString()] ?: 0L)
        cursor = cursor.plusDays(1)
    }
    return cells
}

private fun AnalysisUiState.largestExpenseFilters(expense: AnalysisLargestExpense): MovementFilters? =
    periodFilters(type = MovementType.EXPENSE)?.copy(
        dateFrom = expense.date,
        dateTo = expense.date,
    )

private fun AnalysisUiState.savingsBucketFilters(bucket: String): MovementFilters? {
    val range = currentRange ?: return null
    val (fromDate, toDate) = range.bucket.toMovementDateRange(bucket) ?: return null
    return periodFilters()?.copy(dateFrom = fromDate, dateTo = toDate)
}

private const val MAX_TREND_SERIES = 4

@StringRes
private fun AnalysisScope.labelRes(): Int =
    when (this) {
        AnalysisScope.MONTH -> R.string.analysis_scope_month
        AnalysisScope.YEAR -> R.string.analysis_scope_year
        AnalysisScope.ALL_TIME -> R.string.analysis_scope_all_time
        AnalysisScope.CUSTOM -> R.string.analysis_scope_custom
    }

@StringRes
private fun AnalysisMode.labelRes(): Int =
    when (this) {
        AnalysisMode.ACTUAL -> R.string.analysis_mode_actual
        AnalysisMode.FLOW -> R.string.analysis_mode_flow
    }

@StringRes
private fun AnalysisValueMode.labelRes(): Int =
    when (this) {
        AnalysisValueMode.TOTALS -> R.string.analysis_mode_total
        AnalysisValueMode.AVERAGES -> R.string.analysis_mode_average
    }

@StringRes
private fun AnalysisNatureFilter.labelRes(): Int =
    when (this) {
        AnalysisNatureFilter.ALL -> R.string.analysis_filter_all_natures
        AnalysisNatureFilter.FIXED -> R.string.analysis_filter_fixed
        AnalysisNatureFilter.VARIABLE -> R.string.analysis_filter_variable
    }

@StringRes
private fun AnalysisOneTimeMode.labelRes(): Int =
    when (this) {
        AnalysisOneTimeMode.INCLUDE -> R.string.analysis_one_time_include
        AnalysisOneTimeMode.EXCLUDE -> R.string.analysis_one_time_exclude
        AnalysisOneTimeMode.ONLY -> R.string.analysis_one_time_only
    }

@Composable
private fun AnalysisUiState.fallbackPeriodLabel(): String =
    when (scope) {
        AnalysisScope.MONTH -> formatMonthYear(month)
        AnalysisScope.YEAR -> year.toString()
        AnalysisScope.ALL_TIME -> stringResource(R.string.analysis_scope_all_time)
        AnalysisScope.CUSTOM -> "${formatLongDate(customFrom)} - ${formatLongDate(customTo)}"
    }

@Composable
private fun AnalysisPeriodRange.formatForScope(scope: AnalysisScope): String =
    when (scope) {
        AnalysisScope.MONTH -> formatMonthYear(YearMonth.from(fromDate))
        AnalysisScope.YEAR -> fromDate.year.toString()
        AnalysisScope.ALL_TIME -> stringResource(R.string.analysis_scope_all_time)
        AnalysisScope.CUSTOM -> "${formatLongDate(fromDate.toString())} - ${formatLongDate(toDateExclusive.minusDays(1).toString())}"
    }

private fun AnalysisUiState.toChartPoints(): List<IncomeExpenseChartPoint> {
    val range = currentRange ?: return emptyList()
    if (analysisMode == AnalysisMode.FLOW) {
        return flowBuckets
            .distinctBy { it.bucket }
            .map { it.toFlowChartPoint() }
    }
    return when (range.bucket) {
        AnalysisBucket.DAY -> range.toDailyPoints(chartBuckets)
        AnalysisBucket.MONTH -> if (scope == AnalysisScope.ALL_TIME) {
            chartBuckets.map { it.toChartPoint() }
        } else {
            range.toMonthlyPoints(chartBuckets)
        }
        AnalysisBucket.YEAR -> chartBuckets.map { it.toChartPoint() }
    }
}

private fun AnalysisPeriodRange.toDailyPoints(
    buckets: List<AnalysisIncomeExpenseBucket>,
): List<IncomeExpenseChartPoint> {
    val byDay = buckets.associateBy { it.bucket }
    val points = mutableListOf<IncomeExpenseChartPoint>()
    var cursor = fromDate
    while (cursor < toDateExclusive) {
        val key = cursor.toString()
        val bucket = byDay[key]
        points += IncomeExpenseChartPoint(
            label = cursor.dayOfMonth.toString(),
            bucket = key,
            incomeCents = bucket?.incomeCents ?: 0L,
            expenseCents = bucket?.expenseCents ?: 0L,
        )
        cursor = cursor.plusDays(1)
    }
    return points
}

private fun AnalysisPeriodRange.toMonthlyPoints(
    buckets: List<AnalysisIncomeExpenseBucket>,
): List<IncomeExpenseChartPoint> {
    val byMonth = buckets.associateBy { it.bucket }
    val points = mutableListOf<IncomeExpenseChartPoint>()
    var cursor = YearMonth.from(fromDate)
    val lastMonth = YearMonth.from(toDateExclusive.minusDays(1))
    while (!cursor.isAfter(lastMonth)) {
        val key = cursor.toString()
        val bucket = byMonth[key]
        points += IncomeExpenseChartPoint(
            label = key,
            bucket = key,
            incomeCents = bucket?.incomeCents ?: 0L,
            expenseCents = bucket?.expenseCents ?: 0L,
        )
        cursor = cursor.plusMonths(1)
    }
    return points
}

private fun AnalysisIncomeExpenseBucket.toChartPoint(): IncomeExpenseChartPoint =
    IncomeExpenseChartPoint(
        label = bucket,
        bucket = bucket,
        incomeCents = incomeCents,
        expenseCents = expenseCents,
    )

private fun AnalysisAccountFlowBucket.toFlowChartPoint(): IncomeExpenseChartPoint {
    val delta = bucketDeltaCents
    return IncomeExpenseChartPoint(
        label = bucket,
        bucket = bucket,
        incomeCents = delta.coerceAtLeast(0),
        expenseCents = (-delta).coerceAtLeast(0),
    )
}

private fun AnalysisUiState.periodFilters(
    type: MovementType? = null,
    categoryId: String? = null,
    uncategorizedOnly: Boolean = false,
): MovementFilters? {
    val range = currentRange ?: return null
    val sourceMode = when (analysisMode) {
        AnalysisMode.ACTUAL -> MovementSourceMode.ACTUAL
        AnalysisMode.FLOW -> MovementSourceMode.FLOW
    }
    return MovementFilters(
        type = if (sourceMode == MovementSourceMode.ACTUAL) type else null,
        categoryId = if (sourceMode == MovementSourceMode.ACTUAL) categoryId else null,
        uncategorizedOnly = sourceMode == MovementSourceMode.ACTUAL && uncategorizedOnly,
        sourceMode = sourceMode,
        categoryNature = if (sourceMode == MovementSourceMode.ACTUAL) {
            natureFilter.toMovementCategoryNature()
        } else {
            null
        },
        oneTimeMode = if (sourceMode == MovementSourceMode.ACTUAL) {
            oneTimeMode.toMovementOneTimeMode()
        } else {
            MovementFilterOneTimeMode.INCLUDE
        },
        dateFrom = range.fromDate.toString(),
        dateTo = range.toDateExclusive.minusDays(1).toString(),
    )
}

private fun AnalysisUiState.flowBucketFilters(flow: AnalysisAccountFlowBucket): MovementFilters? {
    val range = currentRange ?: return null
    val (fromDate, toDate) = range.bucket.toMovementDateRange(flow.bucket) ?: return null
    return MovementFilters(
        accountId = flow.accountId,
        sourceMode = MovementSourceMode.FLOW,
        dateFrom = fromDate,
        dateTo = toDate,
    )
}

private fun AnalysisUiState.chartPointFilters(point: IncomeExpenseChartPoint): MovementFilters? {
    val range = currentRange ?: return null
    val (fromDate, toDate) = range.bucket.toMovementDateRange(point.bucket) ?: return null
    return periodFilters()?.copy(
        dateFrom = fromDate,
        dateTo = toDate,
    )
}

private fun AnalysisBucket.toMovementDateRange(bucket: String): Pair<String, String>? =
    runCatching {
        when (this) {
            AnalysisBucket.DAY -> {
                val date = LocalDate.parse(bucket)
                date.toString() to date.toString()
            }
            AnalysisBucket.MONTH -> {
                val month = YearMonth.parse(bucket)
                month.atDay(1).toString() to month.atEndOfMonth().toString()
            }
            AnalysisBucket.YEAR -> {
                val year = bucket.toInt()
                LocalDate.of(year, 1, 1).toString() to LocalDate.of(year, 12, 31).toString()
            }
        }
    }.getOrNull()

private fun AnalysisNatureFilter.toMovementCategoryNature(): CategoryNature? =
    when (this) {
        AnalysisNatureFilter.ALL -> null
        AnalysisNatureFilter.FIXED -> CategoryNature.FIXED
        AnalysisNatureFilter.VARIABLE -> CategoryNature.VARIABLE
    }

private fun AnalysisOneTimeMode.toMovementOneTimeMode(): MovementFilterOneTimeMode =
    when (this) {
        AnalysisOneTimeMode.INCLUDE -> MovementFilterOneTimeMode.INCLUDE
        AnalysisOneTimeMode.EXCLUDE -> MovementFilterOneTimeMode.EXCLUDE
        AnalysisOneTimeMode.ONLY -> MovementFilterOneTimeMode.ONLY
    }

private fun AnalysisUiState.displayCents(cents: Long): Long =
    cents.divideCents(currentDisplayDivisor())

private fun AnalysisUiState.displayPreviousCents(cents: Long): Long =
    cents.divideCents(previousDisplayDivisor())

private fun AnalysisUiState.currentDisplayDivisor(): Long =
    if (valueMode == AnalysisValueMode.AVERAGES) currentAverageDivisor else 1L

private fun AnalysisUiState.previousDisplayDivisor(): Long =
    if (valueMode == AnalysisValueMode.AVERAGES) previousAverageDivisor else 1L

private fun Long.divideCents(divisor: Long): Long =
    if (divisor <= 1) this else this / divisor
