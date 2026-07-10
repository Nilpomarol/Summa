package com.gestorfinances.app.ui.analysis.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.ui.analysis.AnalysisUiState
import com.gestorfinances.app.ui.analysis.FixVariableData
import com.gestorfinances.app.ui.analysis.RecurringVeteranItem
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.SankeyDiagram
import com.gestorfinances.app.ui.common.SankeyLink
import com.gestorfinances.app.ui.common.SankeyNode
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatBasisPoints
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.formatPercentLabel
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.YearMonth

private const val SANKEY_LEAVES_PER_GROUP = 4

/** 50/30/20-style threshold: fixed costs above half of income trigger the warning insight. */
private const val FIXED_RATIO_WARNING_BASIS_POINTS = 5_000L

@Composable
internal fun FixVariableTab(
    state: AnalysisUiState,
    contentPadding: PaddingValues,
) {
    val data = state.fixVariable
    val fixedColor = FinanceTheme.colors.debt
    val variableColor = FinanceTheme.colors.transfer
    val uncategorizedColor = FinanceTheme.colors.expense
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

        val income = data.totals.actualIncomeCents
        val fixedCents = data.fixedExpenseCents
        val variableCents = data.variableExpenseCents
        val hasFixVarData = fixedCents > 0 || variableCents > 0

        if (!hasFixVarData) {
            item { EmptyAnalysisCard() }
            return@LazyColumn
        }

        // (a) Fixed vs variable gauge with %-of-income and 50/30/20 insight.
        item { TabSection(stringResource(R.string.analysis_fixvar_gauge_title)) }
        item {
            FixedVariableGauge(
                fixedCents = fixedCents,
                variableCents = variableCents,
                incomeCents = income,
                ratioBasisPoints = data.fixedRatioOfIncomeBasisPoints,
            )
        }

        // (b) Oldest recurring commitments.
        item { TabSection(stringResource(R.string.analysis_fixvar_oldest_title)) }
        item { OldestRecurringCard(items = data.oldestRecurringItems) }

        // (c) Sankey: income → fixed/variable/savings → leaf categories.
        if (income > 0) {
            item { TabSection(stringResource(R.string.analysis_sankey_title)) }
            item {
                FinanceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val (columns, links) = buildSankey(
                            data = data,
                            income = income,
                            fixedCents = fixedCents,
                            variableCents = variableCents,
                            netActualCents = data.totals.netActualCents,
                            incomeLabel = stringResource(R.string.analysis_sankey_income),
                            fixedLabel = stringResource(R.string.analysis_sankey_fixed),
                            variableLabel = stringResource(R.string.analysis_sankey_variable),
                            savingsLabel = stringResource(R.string.analysis_sankey_savings),
                            uncategorizedLabel = stringResource(R.string.common_no_category),
                            incomeColor = FinanceTheme.colors.income,
                            fixedColor = fixedColor,
                            variableColor = variableColor,
                            savingsColor = MaterialTheme.colorScheme.primary,
                            uncategorizedColor = uncategorizedColor,
                        )
                        SankeyDiagram(columns = columns, links = links)
                    }
                }
            }
        }

        // (d) Fixes / Variables category breakdown — each section header carries the group's
        // total and its share of total expenses, so the split is legible without reading the gauge.
        val totalExpenseCents = fixedCents + variableCents
        if (fixedCents > 0) {
            item {
                SectionHeader(
                    title = stringResource(R.string.analysis_fixvar_group_fixed),
                    trailing = { GroupTotalBadge(cents = fixedCents, totalExpenseCents = totalExpenseCents, color = fixedColor) },
                )
            }
            item {
                CategoryGroupCard(
                    categories = data.fixedCategories,
                    groupTotalCents = fixedCents,
                )
            }
        }
        if (variableCents > 0) {
            item {
                SectionHeader(
                    title = stringResource(R.string.analysis_fixvar_group_variable),
                    trailing = { GroupTotalBadge(cents = variableCents, totalExpenseCents = totalExpenseCents, color = variableColor) },
                )
            }
            item {
                CategoryGroupCard(
                    categories = data.variableCategories,
                    groupTotalCents = variableCents,
                )
            }
        }

        // Supplementary detail: recurring cost summary. "Propers periòdics" moved to the
        // Recurring/Periòdics page (its own next-due-date list), so it no longer duplicates here.
        if (data.recurringCostSummary.hasCosts) {
            item { RecurringCostSummaryWidget(summary = data.recurringCostSummary) }
        }
    }
}

private fun buildSankey(
    data: FixVariableData,
    income: Long,
    fixedCents: Long,
    variableCents: Long,
    netActualCents: Long,
    incomeLabel: String,
    fixedLabel: String,
    variableLabel: String,
    savingsLabel: String,
    uncategorizedLabel: String,
    incomeColor: androidx.compose.ui.graphics.Color,
    fixedColor: androidx.compose.ui.graphics.Color,
    variableColor: androidx.compose.ui.graphics.Color,
    savingsColor: androidx.compose.ui.graphics.Color,
    uncategorizedColor: androidx.compose.ui.graphics.Color,
): Pair<List<List<SankeyNode>>, List<SankeyLink>> {
    // Savings must match the canonical figure shown elsewhere on this screen (derived from
    // v_actual_expense/v_actual_income), not an ad hoc income - fixed - variable subtraction —
    // the latter silently drops truly uncategorized expense (category_id IS NULL). Any such
    // spend is surfaced as its own node below instead, so the diagram stays internally consistent:
    // fixed + variable + uncategorized + savings sums back to income.
    val savings = netActualCents.coerceAtLeast(0L)
    val uncategorizedCents = (data.totals.actualExpenseCents - fixedCents - variableCents).coerceAtLeast(0L)

    val incomeNode = SankeyNode("income", incomeLabel, income, incomeColor)
    val groupNodes = buildList {
        if (fixedCents > 0) add(SankeyNode("fixed", fixedLabel, fixedCents, fixedColor))
        if (variableCents > 0) add(SankeyNode("variable", variableLabel, variableCents, variableColor))
        if (uncategorizedCents > 0) add(SankeyNode("uncategorized", uncategorizedLabel, uncategorizedCents, uncategorizedColor))
        if (savings > 0) add(SankeyNode("savings", savingsLabel, savings, savingsColor))
    }
    val fixedLeaves = data.fixedCategories.sortedByDescending { it.expenseCents }.take(SANKEY_LEAVES_PER_GROUP)
    val variableLeaves = data.variableCategories.sortedByDescending { it.expenseCents }.take(SANKEY_LEAVES_PER_GROUP)
    val leafNodes = buildList {
        fixedLeaves.forEach { add(SankeyNode("f:${it.categoryId}", it.categoryName ?: "—", it.expenseCents, categoryColor(it.categoryColor))) }
        variableLeaves.forEach { add(SankeyNode("v:${it.categoryId}", it.categoryName ?: "—", it.expenseCents, categoryColor(it.categoryColor))) }
    }

    val links = buildList {
        if (fixedCents > 0) add(SankeyLink("income", "fixed", fixedCents))
        if (variableCents > 0) add(SankeyLink("income", "variable", variableCents))
        if (uncategorizedCents > 0) add(SankeyLink("income", "uncategorized", uncategorizedCents))
        if (savings > 0) add(SankeyLink("income", "savings", savings))
        fixedLeaves.forEach { add(SankeyLink("fixed", "f:${it.categoryId}", it.expenseCents)) }
        variableLeaves.forEach { add(SankeyLink("variable", "v:${it.categoryId}", it.expenseCents)) }
    }
    val columns = if (leafNodes.isEmpty()) {
        listOf(listOf(incomeNode), groupNodes)
    } else {
        listOf(listOf(incomeNode), groupNodes, leafNodes)
    }
    return columns to links
}

@Composable
private fun FixedVariableGauge(
    fixedCents: Long,
    variableCents: Long,
    incomeCents: Long,
    ratioBasisPoints: Long?,
) {
    val total = (fixedCents + variableCents).coerceAtLeast(1L)
    val fixedFraction = fixedCents.toFloat() / total
    val fixedColor = FinanceTheme.colors.debt
    val variableColor = FinanceTheme.colors.transfer
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                val stroke = 18.dp.toPx()
                val diameter = minOf(size.width, size.height * 2) - stroke
                val topLeft = Offset((size.width - diameter) / 2f, size.height - diameter / 2f - stroke / 2f)
                val arcSize = Size(diameter, diameter)
                drawArc(
                    color = variableColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = fixedColor,
                    startAngle = 180f,
                    sweepAngle = 180f * fixedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GaugeLegend(
                    color = fixedColor,
                    label = stringResource(R.string.analysis_fixvar_fixed),
                    cents = fixedCents,
                    percent = (fixedFraction * 100f).toInt(),
                    modifier = Modifier.weight(1f),
                )
                GaugeLegend(
                    color = variableColor,
                    label = stringResource(R.string.analysis_fixvar_variable),
                    cents = variableCents,
                    percent = ((1f - fixedFraction) * 100f).toInt(),
                    modifier = Modifier.weight(1f),
                )
            }
            FixedIncomeRatioRow(incomeCents = incomeCents, ratioBasisPoints = ratioBasisPoints)
        }
    }
}

/**
 * Fixed-cost-as-%-of-income, with a 50/30/20-style status pill, on one line: icon + percentage +
 * short insight badge. Falls back to a neutral unavailable state when income can't be computed.
 */
@Composable
private fun FixedIncomeRatioRow(incomeCents: Long, ratioBasisPoints: Long?) {
    if (incomeCents > 0 && ratioBasisPoints != null) {
        val isWarning = ratioBasisPoints > FIXED_RATIO_WARNING_BASIS_POINTS
        val statusColor = if (isWarning) FinanceTheme.colors.alert else FinanceTheme.colors.income
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isWarning) Icons.Outlined.Warning else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.analysis_fixvar_income_ratio, formatBasisPoints(ratioBasisPoints)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(
                text = stringResource(
                    if (isWarning) R.string.analysis_fixvar_insight_warning else R.string.analysis_fixvar_insight_ok,
                ),
                color = statusColor,
            )
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = FinanceTheme.colors.mutedText,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.analysis_fixvar_income_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = FinanceTheme.colors.mutedText,
            )
        }
    }
}

/** Small colored status badge (design §6 pill shape) — tint background, matching-color text. */
@Composable
private fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GaugeLegend(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    cents: Long,
    percent: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(12.dp)
                .padding(end = 0.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawCircle(color) }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = "$label · $percent%", style = MaterialTheme.typography.labelMedium)
            MoneyText(cents = cents, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** "Which recurring commitments are oldest" — active fixed costs sorted by active-since date. */
@Composable
private fun OldestRecurringCard(items: List<RecurringVeteranItem>) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.analysis_fixvar_oldest_empty),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                items.forEach { item -> OldestRecurringRow(item = item) }
            }
        }
    }
}

@Composable
private fun OldestRecurringRow(item: RecurringVeteranItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(
            icon = Icons.Outlined.Autorenew,
            contentDescription = null,
            color = FinanceTheme.colors.debt,
            size = 36.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label.ifBlank { stringResource(R.string.common_no_category) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val movementCountText = pluralStringResource(
                R.plurals.account_flow_movement_count,
                item.movementCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                item.movementCount,
            )
            val subtitle = item.activeSince?.let { since ->
                stringResource(
                    R.string.analysis_fixvar_oldest_active_since,
                    formatMonthYear(YearMonth.from(since)),
                ) + " · $movementCountText"
            } ?: movementCountText
            Text(
                text = subtitle,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        MoneyText(
            cents = item.monthlyAmountCents,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/** Section-header trailing slot: the group's total amount and its share of total expenses. */
@Composable
private fun GroupTotalBadge(cents: Long, totalExpenseCents: Long, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.End) {
        MoneyText(cents = cents, color = color, style = MaterialTheme.typography.titleSmall)
        if (totalExpenseCents > 0) {
            Text(
                text = stringResource(
                    R.string.analysis_fixvar_group_pct_of_expenses,
                    formatPercentLabel(cents.toFloat() / totalExpenseCents.toFloat()),
                ),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** "Where do costs concentrate" — a Fixes/Variables category list, each row sized by a magnitude
 *  bar sized to its share of the group total (bar length = the percentage shown) so the split
 *  reads visually, not just as numbers. */
@Composable
private fun CategoryGroupCard(
    categories: List<AnalysisCategoryTotal>,
    groupTotalCents: Long,
) {
    val sorted = categories.sortedByDescending { it.expenseCents }
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            sorted.forEachIndexed { index, category ->
                CategoryGroupRow(category = category, groupTotalCents = groupTotalCents)
                if (index != sorted.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = FinanceTheme.colors.cardBorder,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryGroupRow(category: AnalysisCategoryTotal, groupTotalCents: Long) {
    val color = categoryColor(category.categoryColor)
    val shareOfGroup = if (groupTotalCents > 0) {
        (category.expenseCents.toFloat() / groupTotalCents.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
                MoneyText(cents = category.expenseCents, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = formatPercentLabel(shareOfGroup),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        LinearProgressIndicator(
            progress = { shareOfGroup },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}
