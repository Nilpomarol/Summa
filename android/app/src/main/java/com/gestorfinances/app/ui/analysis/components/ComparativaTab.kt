package com.gestorfinances.app.ui.analysis.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.ui.analysis.AnalysisScope
import com.gestorfinances.app.ui.analysis.AnalysisUiState
import com.gestorfinances.app.ui.analysis.displayCents
import com.gestorfinances.app.ui.analysis.fallbackPeriodLabel
import com.gestorfinances.app.ui.analysis.incomeExpensePoints
import com.gestorfinances.app.ui.analysis.periodComparisonLabels
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IncomeExpenseChart
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.WaterfallChart
import com.gestorfinances.app.ui.common.WaterfallStep
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatBasisPoints
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.YearMonth
import kotlin.math.abs

private const val WATERFALL_MAX_STEPS = 6
private const val NOTABLE_SWINGS_COUNT = 3

private enum class CategoryDeltaMode { EXPENSE, INCOME }

private data class CategoryDelta(
    val categoryId: String?,
    val name: String,
    val categoryIcon: String?,
    val categoryColor: String?,
    val currentCents: Long,
    val previousCents: Long,
    val deltaCents: Long,
)

@Composable
internal fun ComparativaTab(
    state: AnalysisUiState,
    contentPadding: PaddingValues,
    onComparisonMonthSelected: (YearMonth) -> Unit,
    onComparisonYearSelected: (Int) -> Unit,
    onComparisonCustomFromChange: (String) -> Unit,
    onComparisonCustomToChange: (String) -> Unit,
    onResetComparison: () -> Unit,
) {
    val data = state.comparativa
    // UI-only toggle (design §6.3) — both expenseCents and incomeCents are already in
    // AnalysisCategoryTotal, so no ViewModel/reload is needed to switch it.
    var categoryMode by remember { mutableStateOf(CategoryDeltaMode.EXPENSE) }
    // Computed once here (composable context) rather than inside the item {} blocks that need
    // it — periodComparisonLabels is @Composable, and the LazyListScope builder body is not.
    val periodLabels = data?.comparisonRange?.let { comparisonRange ->
        periodComparisonLabels(
            scope = state.scope,
            currentRange = state.currentRange,
            comparisonRange = comparisonRange,
            fallbackCurrentLabel = state.fallbackPeriodLabel(),
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Comparison-period picker lives in the tab (not the shared header) — only this tab needs it.
        if (state.scope != AnalysisScope.ALL_TIME) {
            item {
                ComparisonNavigator(
                    state = state,
                    onComparisonMonthSelected = onComparisonMonthSelected,
                    onComparisonYearSelected = onComparisonYearSelected,
                    onComparisonCustomFromChange = onComparisonCustomFromChange,
                    onComparisonCustomToChange = onComparisonCustomToChange,
                    onResetComparison = onResetComparison,
                )
            }
        }

        tabErrorItem(state.errorMessage)
        if (data == null) {
            if (state.errorMessage == null) item { TabLoading() }
            return@LazyColumn
        }
        val previousTotals = data.previousTotals
        if (data.comparisonRange == null || previousTotals == null) {
            item {
                FinanceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.analysis_comparativa_unavailable),
                        modifier = Modifier.padding(18.dp),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            return@LazyColumn
        }

        val deltas = categoryDeltas(data.currentByCategory, data.previousByCategory)
        val (currentPeriodLabel, comparisonPeriodLabel) = requireNotNull(periodLabels)

        // KPI cards: current vs comparison period, 2x2 grid (light FinanceCard, not hero).
        item { TabSection(stringResource(R.string.analysis_comparativa_deltas_title)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ComparativeKpiCard(
                        label = "${stringResource(R.string.movement_type_expense)} $currentPeriodLabel",
                        currentCents = state.displayCents(data.currentTotals.actualExpenseCents),
                        previousCents = state.displayCents(previousTotals.actualExpenseCents),
                        comparisonPeriodLabel = comparisonPeriodLabel,
                        lowerIsBetter = true,
                        modifier = Modifier.weight(1f),
                    )
                    ComparativeKpiCard(
                        label = "${stringResource(R.string.movement_type_income)} $currentPeriodLabel",
                        currentCents = state.displayCents(data.currentTotals.actualIncomeCents),
                        previousCents = state.displayCents(previousTotals.actualIncomeCents),
                        comparisonPeriodLabel = comparisonPeriodLabel,
                        lowerIsBetter = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ComparativeKpiCard(
                        label = "${stringResource(R.string.analysis_comparison_net_label)} $currentPeriodLabel",
                        currentCents = state.displayCents(data.currentTotals.netActualCents),
                        previousCents = state.displayCents(previousTotals.netActualCents),
                        comparisonPeriodLabel = comparisonPeriodLabel,
                        lowerIsBetter = false,
                        modifier = Modifier.weight(1f),
                    )
                    SavingsRateKpiCard(
                        currentBasisPoints = data.currentTotals.savingsRateBasisPoints,
                        previousBasisPoints = previousTotals.savingsRateBasisPoints,
                        hasCurrentIncome = data.currentTotals.actualIncomeCents > 0,
                        hasPreviousIncome = previousTotals.actualIncomeCents > 0,
                        comparisonPeriodLabel = comparisonPeriodLabel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Comparative cumulative chart: current (solid) vs comparison period (ghost).
        val currentPoints = state.currentRange
            ?.let { incomeExpensePoints(it, state.scope, data.currentChartBuckets) } ?: emptyList()
        val previousPoints = incomeExpensePoints(data.comparisonRange, state.scope, data.previousChartBuckets)
        item {
            IncomeExpenseChart(
                title = stringResource(R.string.analysis_comparison_chart_title),
                points = currentPoints,
                previous = previousPoints,
                incomeLabel = stringResource(R.string.analysis_comparison_income_label),
                expenseLabel = stringResource(R.string.analysis_comparison_expense_label),
                currentCaption = currentPeriodLabel,
                previousCaption = comparisonPeriodLabel,
                emptyText = stringResource(R.string.dashboard_no_data),
            )
        }

        // Notable swings: top-3 categories by absolute impact (honest "where money went differently").
        val notableSwings = deltas.take(NOTABLE_SWINGS_COUNT)
        if (notableSwings.isNotEmpty()) {
            item { TabSection(stringResource(R.string.analysis_notable_swings_title)) }
            item {
                NotableSwingsRow(deltas = notableSwings, divisor = state.currentDisplayDivisorOrOne())
            }
        }

        // Category comparison list: full list (either period), dual bars, Despeses/Ingressos toggle.
        item { TabSection(stringResource(R.string.analysis_category_comparison_title)) }
        item {
            SegmentedControl(
                options = CategoryDeltaMode.entries,
                selected = categoryMode,
                label = { mode ->
                    stringResource(
                        if (mode == CategoryDeltaMode.EXPENSE) {
                            R.string.analysis_comparison_expense_label
                        } else {
                            R.string.analysis_comparison_income_label
                        },
                    )
                },
                onSelect = { categoryMode = it },
            )
        }
        val categoryRows = categoryDeltas(data.currentByCategory, data.previousByCategory, categoryMode)
        val categoryRowMax = categoryRows.maxOfOrNull { maxOf(it.currentCents, it.previousCents) }?.coerceAtLeast(1L) ?: 1L
        items(items = categoryRows, key = { it.categoryId ?: "uncategorized" }) { row ->
            CategoryComparisonRow(delta = row, maxCents = categoryRowMax, divisor = state.currentDisplayDivisorOrOne())
        }

        // Waterfall: previous expense → current expense. Always visible, relocated to the end
        // (final layout: KPI row → chart → notable swings → category list → waterfall).
        item { TabSection(stringResource(R.string.analysis_waterfall_title)) }
        item {
            FinanceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    WaterfallChart(
                        startLabel = stringResource(R.string.analysis_period_previous),
                        startCents = previousTotals.actualExpenseCents,
                        steps = waterfallSteps(deltas),
                        endLabel = stringResource(R.string.analysis_period_current),
                        endCents = data.currentTotals.actualExpenseCents,
                        increaseColor = FinanceTheme.colors.debt,
                        decreaseColor = FinanceTheme.colors.income,
                    )
                }
            }
        }
    }
}

private fun AnalysisCategoryTotal.amountFor(mode: CategoryDeltaMode): Long =
    when (mode) {
        CategoryDeltaMode.EXPENSE -> expenseCents
        CategoryDeltaMode.INCOME -> incomeCents
    }

/** Union of both periods' categories, zero-filled on either side, sorted by absolute impact. */
private fun categoryDeltas(
    current: List<AnalysisCategoryTotal>,
    previous: List<AnalysisCategoryTotal>,
    mode: CategoryDeltaMode = CategoryDeltaMode.EXPENSE,
): List<CategoryDelta> {
    val cur = current.filter { it.amountFor(mode) > 0 }.associateBy { it.categoryId }
    val prev = previous.filter { it.amountFor(mode) > 0 }.associateBy { it.categoryId }
    val ids = cur.keys + prev.keys
    return ids.map { id ->
        val c = cur[id]
        val p = prev[id]
        val currentCents = c?.amountFor(mode) ?: 0L
        val previousCents = p?.amountFor(mode) ?: 0L
        CategoryDelta(
            categoryId = id,
            name = c?.categoryName ?: p?.categoryName ?: "—",
            categoryIcon = c?.categoryIcon ?: p?.categoryIcon,
            categoryColor = c?.categoryColor ?: p?.categoryColor,
            currentCents = currentCents,
            previousCents = previousCents,
            deltaCents = currentCents - previousCents,
        )
    }.filter { it.deltaCents != 0L }.sortedByDescending { abs(it.deltaCents) }
}

private fun waterfallSteps(
    deltas: List<CategoryDelta>,
): List<WaterfallStep> {
    val head = deltas.take(WATERFALL_MAX_STEPS)
    val tailSum = deltas.drop(WATERFALL_MAX_STEPS).sumOf { it.deltaCents }
    val steps = head.map { delta ->
        WaterfallStep(
            label = delta.name,
            deltaCents = delta.deltaCents,
        )
    }
    return if (tailSum != 0L) {
        steps + WaterfallStep(label = "…", deltaCents = tailSum, onClick = null)
    } else {
        steps
    }
}

/** Current value large, previous small/muted, delta chip with abs + % change (design §1 edge-case math). */
@Composable
private fun ComparativeKpiCard(
    label: String,
    currentCents: Long,
    previousCents: Long,
    comparisonPeriodLabel: String,
    lowerIsBetter: Boolean,
    modifier: Modifier = Modifier,
) {
    val deltaCents = currentCents - previousCents
    val good = if (lowerIsBetter) deltaCents <= 0 else deltaCents >= 0
    val deltaColor = if (good) FinanceTheme.colors.income else FinanceTheme.colors.debt
    FinanceCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MoneyText(cents = currentCents, style = MaterialTheme.typography.titleLarge)
            PreviousValueCaption(comparisonPeriodLabel) {
                MoneyText(cents = previousCents, color = FinanceTheme.colors.mutedText, style = MaterialTheme.typography.labelSmall)
            }
            DeltaPill(
                deltaCents = deltaCents,
                percentText = percentChangeLabel(currentCents, previousCents),
                color = deltaColor,
            )
        }
    }
}

/** The 4th KPI card: savings rate is a basis-points ratio, not money, so it formats and skips differently. */
@Composable
private fun SavingsRateKpiCard(
    currentBasisPoints: Long,
    previousBasisPoints: Long,
    hasCurrentIncome: Boolean,
    hasPreviousIncome: Boolean,
    comparisonPeriodLabel: String,
    modifier: Modifier = Modifier,
) {
    FinanceCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.analysis_savings_rate_title),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (hasCurrentIncome) {
                    formatBasisPoints(currentBasisPoints)
                } else {
                    stringResource(R.string.dashboard_savings_rate_unavailable)
                },
                color = if (!hasCurrentIncome) {
                    FinanceTheme.colors.mutedText
                } else if (currentBasisPoints >= 0) {
                    FinanceTheme.colors.income
                } else {
                    FinanceTheme.colors.debt
                },
                style = MaterialTheme.typography.titleLarge,
            )
            if (hasCurrentIncome && hasPreviousIncome) {
                PreviousValueCaption(comparisonPeriodLabel) {
                    Text(
                        text = formatBasisPoints(previousBasisPoints),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                val deltaBp = currentBasisPoints - previousBasisPoints
                val color = if (deltaBp >= 0) FinanceTheme.colors.income else FinanceTheme.colors.debt
                Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.12f)) {
                    Text(
                        text = formatBasisPointsDeltaPp(deltaBp, stringResource(R.string.analysis_savings_rate_delta_suffix)),
                        color = color,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** "<previous value> vs <period>" caption row shared by the money and savings-rate KPI cards. */
@Composable
private fun PreviousValueCaption(comparisonPeriodLabel: String, previousValue: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        previousValue()
        Text(
            text = stringResource(R.string.analysis_kpi_vs_previous, comparisonPeriodLabel),
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Top-3 category-impact chips, sorted by absolute cents impact (the honest "where money went differently"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotableSwingsRow(deltas: List<CategoryDelta>, divisor: Long) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        deltas.forEach { delta -> NotableSwingChip(delta = delta, divisor = divisor) }
    }
}

@Composable
private fun NotableSwingChip(delta: CategoryDelta, divisor: Long) {
    val color = if (delta.deltaCents > 0) FinanceTheme.colors.debt else FinanceTheme.colors.income
    Surface(shape = MaterialTheme.shapes.medium, color = color.copy(alpha = 0.12f)) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = delta.name,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                MoneyText(
                    cents = delta.deltaCents.dividedBy(divisor),
                    color = color,
                    style = MaterialTheme.typography.labelMedium,
                    signed = true,
                )
                Text(
                    text = percentChangeLabel(delta.currentCents, delta.previousCents),
                    color = color,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun DeltaPill(deltaCents: Long, percentText: String, color: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MoneyText(cents = deltaCents, color = color, style = MaterialTheme.typography.labelMedium, signed = true)
            Text(text = "($percentText)", color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** delta*100/previous (integer division); previous==0 → New; current==0 && previous>0 → Eliminated (design §1). */
internal sealed class PercentChange {
    internal data object New : PercentChange()
    internal data object Eliminated : PercentChange()
    internal data class Value(val pct: Long) : PercentChange()
}

internal fun percentChange(current: Long, previous: Long): PercentChange =
    when {
        previous == 0L -> PercentChange.New
        current == 0L -> PercentChange.Eliminated
        else -> PercentChange.Value((current - previous) * 100 / previous)
    }

@Composable
private fun percentChangeLabel(current: Long, previous: Long): String =
    when (val change = percentChange(current, previous)) {
        PercentChange.New -> stringResource(R.string.analysis_delta_new)
        PercentChange.Eliminated -> stringResource(R.string.analysis_delta_eliminated)
        is PercentChange.Value -> {
            val signed = if (change.pct > 0) "+${change.pct}" else "${change.pct}"
            stringResource(R.string.analysis_delta_percent, signed)
        }
    }

/** One-decimal percentage-point delta (10bp = 0.1pp), rounded half-up, e.g. "+2,4 pp" / "-1,3 pp". */
internal fun formatBasisPointsDeltaPp(deltaBp: Long, suffix: String): String {
    val sign = when {
        deltaBp > 0 -> "+"
        deltaBp < 0 -> "-"
        else -> ""
    }
    val tenths = (abs(deltaBp) + 5) / 10
    return "$sign${tenths / 10},${tenths % 10}$suffix"
}

/**
 * One category row: icon/name (left), current value + previous value/% change (right), and dual
 * bars below — thick = current period, thin = comparison period, both normalized to [maxCents]
 * (the list-wide max of either period) so bar lengths are comparable across the whole list, not
 * just within one row.
 */
@Composable
private fun CategoryComparisonRow(delta: CategoryDelta, maxCents: Long, divisor: Long) {
    val color = categoryColor(delta.categoryColor)
    val changeColor = when {
        delta.deltaCents > 0 -> FinanceTheme.colors.debt
        delta.deltaCents < 0 -> FinanceTheme.colors.income
        else -> FinanceTheme.colors.mutedText
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(
                icon = categoryIcon(delta.categoryIcon),
                contentDescription = null,
                color = color,
                size = 32.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = delta.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    cents = delta.currentCents.dividedBy(divisor),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MoneyText(
                        cents = delta.previousCents.dividedBy(divisor),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = percentChangeLabel(delta.currentCents, delta.previousCents),
                        color = changeColor,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        // drawStopIndicator = {} — Material3's default "stop indicator" dot at the track's end
        // (design intent: signal a fixed goal) is meaningless here and reads as a rendering glitch.
        LinearProgressIndicator(
            progress = { (delta.currentCents.toFloat() / maxCents.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
        LinearProgressIndicator(
            progress = { (delta.previousCents.toFloat() / maxCents.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = color.copy(alpha = 0.4f),
            trackColor = Color.Transparent,
            drawStopIndicator = {},
        )
    }
}

private fun AnalysisUiState.currentDisplayDivisorOrOne(): Long =
    if (valueMode == com.gestorfinances.app.ui.analysis.AnalysisValueMode.AVERAGES) currentAverageDivisor else 1L

private fun Long.dividedBy(divisor: Long): Long = if (divisor <= 1) this else this / divisor
