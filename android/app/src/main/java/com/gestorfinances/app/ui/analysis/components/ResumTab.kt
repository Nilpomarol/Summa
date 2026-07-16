package com.gestorfinances.app.ui.analysis.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.analysis.AnalysisUiState
import com.gestorfinances.app.ui.analysis.currentDisplayDivisor
import com.gestorfinances.app.ui.analysis.displayCents
import com.gestorfinances.app.ui.analysis.formatForScope
import com.gestorfinances.app.ui.analysis.incomeExpensePoints
import com.gestorfinances.app.ui.analysis.rowKey
import com.gestorfinances.app.ui.common.ChartDataRow
import com.gestorfinances.app.ui.common.IncomeExpenseChart
import com.gestorfinances.app.ui.common.chartBalanceLabel
import com.gestorfinances.app.ui.common.chartTrendLabel
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.theme.FinanceTheme
import kotlin.math.abs

private const val TOP_CATEGORY_PREVIEW = 5

@Composable
internal fun ResumTab(
    state: AnalysisUiState,
    contentPadding: PaddingValues,
) {
    val data = state.resum
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        tabErrorItem(state.errorMessage)

        if (data == null) {
            if (state.errorMessage == null) item { TabLoading() }
            return@LazyColumn
        }

        val totals = data.totals
        item {
            SummaryKPIsCard(
                totals = totals,
                divisor = state.currentDisplayDivisor(),
            )
        }

        val range = state.currentRange
        if (range != null) {
            item {
                val points = incomeExpensePoints(range, state.scope, data.chartBuckets)
                val incomeCents = points.sumOf { it.incomeCents }
                val expenseCents = points.sumOf { it.expenseCents }
                IncomeExpenseChart(
                    title = stringResource(R.string.analysis_income_vs_expense_title),
                    points = points,
                    incomeLabel = stringResource(R.string.analysis_summary_income),
                    expenseLabel = stringResource(R.string.analysis_summary_expense),
                    emptyText = stringResource(R.string.dashboard_no_data),
                    accessibilitySummary = stringResource(
                        R.string.accessibility_chart_income_expense_summary,
                        stringResource(R.string.analysis_income_vs_expense_title),
                        range.formatForScope(state.scope),
                        formatEuroCents(incomeCents),
                        formatEuroCents(expenseCents),
                        chartBalanceLabel(incomeCents, expenseCents),
                        chartTrendLabel(
                            points.firstOrNull()?.incomeCents?.minus(points.firstOrNull()?.expenseCents ?: 0L),
                            points.lastOrNull()?.incomeCents?.minus(points.lastOrNull()?.expenseCents ?: 0L),
                        ),
                    ),
                    accessibilityRows = points.map { point ->
                        ChartDataRow(
                            point.label,
                            "${stringResource(R.string.analysis_summary_income)} ${formatEuroCents(point.incomeCents)} · " +
                                "${stringResource(R.string.analysis_summary_expense)} ${formatEuroCents(point.expenseCents)}",
                        )
                    },
                )
            }
        }

        if (data.topCategories.isEmpty()) {
            item { EmptyAnalysisCard() }
        } else {
            item { TabSection(stringResource(R.string.analysis_top_categories_title)) }
            val top = data.topCategories.take(TOP_CATEGORY_PREVIEW)
            val maxCents = top.maxOf { abs(it.netCents) }.coerceAtLeast(1L)
            val totalCents = top.sumOf { abs(it.netCents) }.coerceAtLeast(1L)
            items(items = top, key = { it.rowKey() }) { category ->
                CategoryBreakdownRow(
                    category = category,
                    maxCents = maxCents,
                    divisor = state.currentDisplayDivisor(),
                    totalCents = totalCents,
                )
            }
        }
    }
}
