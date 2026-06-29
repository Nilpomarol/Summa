package com.gestorfinances.app.ui.analysis.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.gestorfinances.app.data.repository.AnalysisBreakdownKind
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
import com.gestorfinances.app.ui.common.formatEuroCents
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

import com.gestorfinances.app.ui.analysis.*

@Composable
internal fun SummaryGrid(
    state: AnalysisUiState,
    onDrillDown: (MovementFilters) -> Unit,
) {
    when (state.analysisMode) {
        AnalysisMode.ACTUAL -> ActualSummaryGrid(state = state, onDrillDown = onDrillDown)
        AnalysisMode.FLOW -> FlowSummaryGrid(state = state, onDrillDown = onDrillDown)
    }
}

@Composable
internal fun ActualSummaryGrid(
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
                color = FinanceTheme.colors.debt,
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
                    FinanceTheme.colors.debt
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
internal fun FlowSummaryGrid(
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
                FinanceTheme.colors.debt
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
internal fun MoneyMetricCard(
    label: String,
    cents: Long,
    previousCents: Long?,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = FinanceTheme.colors.heroSurface,
        contentColor = FinanceTheme.colors.heroOnSurface,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                color = FinanceTheme.colors.heroOnSurfaceMuted,
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
internal fun ComparisonMoney(
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
        color = if (delta >= 0) FinanceTheme.colors.income else FinanceTheme.colors.debt,
        style = MaterialTheme.typography.labelMedium,
        signed = true,
    )
}

@Composable
internal fun RateMetricCard(
    currentBasisPoints: Long,
    previousBasisPoints: Long?,
    hasIncome: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = FinanceTheme.colors.heroSurface,
        contentColor = FinanceTheme.colors.heroOnSurface,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.analysis_savings_rate_title),
                color = FinanceTheme.colors.heroOnSurfaceMuted,
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
                    FinanceTheme.colors.debt
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
                    color = if (delta >= 0) FinanceTheme.colors.income else FinanceTheme.colors.debt,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
