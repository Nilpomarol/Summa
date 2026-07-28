package com.gestorfinances.app.ui.analysis.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.ui.analysis.AnalysisUiState
import com.gestorfinances.app.ui.analysis.fallbackPeriodLabel
import com.gestorfinances.app.ui.analysis.formatForScope
import com.gestorfinances.app.ui.analysis.heatmapCellsFor

/**
 * Financial history dashboard. Ordered as a narrative, not a list of unrelated charts: first
 * "how safe am I" (buffer hero), then "how has my position evolved" (net worth), then
 * "am I saving more or less" (savings rate), then behavioral detail (heatmap, weekday, category
 * trends).
 */
@Composable
internal fun HistoricTab(
    state: AnalysisUiState,
    contentPadding: PaddingValues,
) {
    val data = state.historic
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        tabErrorItem(state.errorMessage)
        if (data == null) {
            if (state.errorMessage == null) item { TabLoading() }
            return@LazyColumn
        }

        // 1. Buffer / margin hero — always first; handles its own empty/partial states.
        item {
            BufferHeroCard(
                daysOfBuffer = data.daysOfBuffer,
                savingsRateBasisPoints = data.totals.savingsRateBasisPoints,
                hasIncome = data.totals.actualIncomeCents > 0,
            )
        }

        // 2. Net worth trend.
        if (data.netWorth.size >= 2) {
            item { NetWorthTrendWidget(points = data.netWorth) }
        }

        // 3. Savings rate trend.
        if (data.incomeExpenseBuckets.isNotEmpty()) {
            item { SavingsRateTrendWidget(buckets = data.incomeExpenseBuckets) }
        }

        // 4. Spending heatmap.
        val range = state.currentRange
        if (range != null && data.heatmapDays.isNotEmpty()) {
            item {
                SpendingHeatmapWidget(
                    cells = heatmapCellsFor(range, data.heatmapDays),
                    periodLabel = range.formatForScope(state.scope),
                )
            }
        }

        // 5. Weekday radar.
        if (data.weekday.any { it.expenseCents != 0L }) {
            item {
                WeekdayRadarWidget(
                    weekday = data.weekday,
                    periodLabel = range?.formatForScope(state.scope) ?: state.fallbackPeriodLabel(),
                )
            }
        }

        // 6. Category trends.
        if (data.trends.isNotEmpty()) {
            item { CategoryTrendsWidget(trends = data.trends) }
        }

        val hasDetail = data.netWorth.size >= 2 ||
            data.incomeExpenseBuckets.isNotEmpty() ||
            (range != null && data.heatmapDays.isNotEmpty()) ||
            data.weekday.any { it.expenseCents != 0L } ||
            data.trends.isNotEmpty()
        if (!hasDetail && data.daysOfBuffer == null) {
            item { EmptyAnalysisCard() }
        }
    }
}
