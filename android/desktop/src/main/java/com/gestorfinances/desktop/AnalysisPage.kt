package com.gestorfinances.desktop

import com.gestorfinances.ui.resources.analysis_section_income_trend
import com.gestorfinances.ui.resources.analysis_section_income
import com.gestorfinances.app.ui.trips.BreakdownEntry
import com.gestorfinances.ui.resources.analysis_pace_typical
import com.gestorfinances.ui.resources.analysis_average
import com.gestorfinances.desktop.resources.budgets_column_spent
import com.gestorfinances.desktop.resources.analysis_months_title
import com.gestorfinances.desktop.resources.analysis_months_narrow_hint
import com.gestorfinances.desktop.resources.analysis_months_by_category
import com.gestorfinances.desktop.resources.analysis_months_by_account
import com.gestorfinances.desktop.resources.analysis_last_year
import com.gestorfinances.desktop.resources.analysis_kind_months_hint
import com.gestorfinances.desktop.resources.analysis_income_amount
import com.gestorfinances.desktop.resources.analysis_category_movements_empty
import com.gestorfinances.desktop.resources.analysis_category_movements
import com.gestorfinances.desktop.resources.analysis_accounts_hint
import com.gestorfinances.app.ui.theme.themedIdentityColor
import com.gestorfinances.app.ui.movements.MovementsViewModel
import com.gestorfinances.app.ui.common.ListHero
import com.gestorfinances.app.ui.common.HeroStatBox
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.analysis.CategoryDetailState
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.gestorfinances.app.ui.common.AppIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.gestorfinances.app.ui.common.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.data.repository.AnalysisBreakdownKind
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.ui.analysis.AccountChip
import com.gestorfinances.app.ui.analysis.AnalysisPage
import com.gestorfinances.app.ui.analysis.AnalysisScope
import com.gestorfinances.app.ui.analysis.AnalysisUiState
import com.gestorfinances.app.ui.analysis.AnalysisViewModel
import com.gestorfinances.app.ui.analysis.BaselineKind
import com.gestorfinances.app.ui.analysis.HowSection
import com.gestorfinances.app.ui.analysis.NetWorthSection
import com.gestorfinances.app.ui.analysis.PaceSection
import com.gestorfinances.app.ui.analysis.components.MonthBars
import com.gestorfinances.app.ui.analysis.displayName
import com.gestorfinances.app.ui.analysis.markColor
import com.gestorfinances.app.ui.analysis.markIcon
import com.gestorfinances.app.ui.analysis.rowKey
import com.gestorfinances.app.ui.common.AppDropdownMenu
import com.gestorfinances.app.ui.common.AppDropdownMenuItem
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MonthPickerContent
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatMonth
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.formatShortMonth
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.desktop.resources.analysis_matrix_hint
import com.gestorfinances.desktop.resources.analysis_next
import com.gestorfinances.desktop.resources.analysis_previous
import com.gestorfinances.desktop.resources.analysis_saving
import com.gestorfinances.desktop.resources.analysis_saving_rate
import com.gestorfinances.desktop.resources.analysis_total_expense
import com.gestorfinances.desktop.resources.analysis_trips
import com.gestorfinances.desktop.resources.analysis_view_movements
import com.gestorfinances.desktop.resources.column_amount
import com.gestorfinances.desktop.resources.column_average
import com.gestorfinances.desktop.resources.column_category
import com.gestorfinances.desktop.resources.column_difference
import com.gestorfinances.desktop.resources.column_share
import com.gestorfinances.desktop.resources.column_trend
import com.gestorfinances.desktop.resources.column_usual
import com.gestorfinances.ui.resources.analysis_detail_period
import com.gestorfinances.ui.resources.analysis_detail_readout
import com.gestorfinances.ui.resources.analysis_detail_self
import com.gestorfinances.ui.resources.analysis_detail_subcategories
import com.gestorfinances.ui.resources.analysis_empty_title
import com.gestorfinances.ui.resources.analysis_hero_eyebrow
import com.gestorfinances.ui.resources.analysis_kind_one_off
import com.gestorfinances.ui.resources.analysis_kind_recurring
import com.gestorfinances.ui.resources.analysis_kind_variable
import com.gestorfinances.ui.resources.analysis_net_worth_change
import com.gestorfinances.app.ui.common.formatPercentLabel
import com.gestorfinances.ui.resources.analysis_scope_month
import com.gestorfinances.ui.resources.analysis_scope_year
import com.gestorfinances.ui.resources.analysis_section_how
import com.gestorfinances.ui.resources.analysis_section_net_worth
import com.gestorfinances.ui.resources.analysis_section_pace
import com.gestorfinances.ui.resources.analysis_section_trend
import com.gestorfinances.ui.resources.analysis_section_trend_year
import com.gestorfinances.ui.resources.analysis_section_where
import com.gestorfinances.ui.resources.analysis_trend_readout
import com.gestorfinances.ui.resources.analysis_vs_last_year_less
import com.gestorfinances.ui.resources.analysis_vs_last_year_more
import com.gestorfinances.ui.resources.analysis_vs_last_year_same
import com.gestorfinances.ui.resources.analysis_vs_typical_less
import com.gestorfinances.ui.resources.analysis_vs_typical_more
import com.gestorfinances.ui.resources.analysis_vs_typical_same
import com.gestorfinances.ui.resources.budget_field_total
import com.gestorfinances.ui.resources.common_no_category
import com.gestorfinances.ui.resources.common_retry
import com.gestorfinances.ui.resources.failure_load_analysis
import com.gestorfinances.ui.resources.movement_filter_income
import com.gestorfinances.ui.resources.nav_analysis
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

/**
 * Analysis on a wide window. The period on the dark panel, then the phone's charts side by side
 * (pace, the last twelve months, net worth, how the money was spent), where it went as a table,
 * and what only fits here: the twelve months by category, by kind of spending and by account.
 * A category opens over the whole page with its months and its movements.
 */
@Composable
fun AnalysisPage(
    viewModel: AnalysisViewModel,
    analysis: AnalysisRepository,
    dataVersion: Long,
    movements: MovementsViewModel,
    onOpenMovements: (MovementFilters) -> Unit,
    onOpenMovement: (MovementSummary) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel, dataVersion) { viewModel.onScreenShown(dataVersion) }
    // The open category follows the period: on the phone its sheet would have been closed by now.
    LaunchedEffect(state.scope, state.month, state.year, state.filterAccountId) {
        state.categoryDetail?.let { viewModel.onCategoryClicked(it.categoryId) }
    }
    val page = state.page
    val months = page?.trend?.map { it.month }
    val tables by produceState<AnalysisTables?>(null, months, state.filterAccountId, state.categories, dataVersion) {
        value = if (months.isNullOrEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { loadAnalysisTables(analysis, state.categories, months, state.filterAccountId, state.today) }.getOrNull()
            }
        }
    }
    val openMovements = { categoryIds: Set<String>?, uncategorized: Boolean, from: String, to: String ->
        onOpenMovements(
            MovementFilters(
                type = MovementType.EXPENSE,
                accountId = state.filterAccountId,
                categoryIds = categoryIds,
                uncategorizedOnly = uncategorized,
                dateFrom = from,
                dateTo = to,
            ),
        )
    }
    val range = periodDates(state)

    val detail = state.categoryDetail
    val category = detail?.let { open -> state.categories.firstOrNull { it.id == open.categoryId } }
    if (page != null && detail != null && category != null) {
        val ids = remember(category.id, state.categories) { state.categories.filter { it.parentId == category.id }.map { it.id }.toSet() + category.id }
        CategoryPane(
            state = state,
            page = page,
            detail = detail,
            category = category,
            categoryIds = ids,
            range = range,
            movements = movements,
            onBack = viewModel::onCategoryDetailDismissed,
            onMonth = viewModel::onMonthSelected,
            onOpenMovement = onOpenMovement,
            onOpenInMovements = { openMovements(ids, false, range.first, range.second) },
        )
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        ScrollPage {
            val controls = @Composable {
                SegmentedControl(
                    options = AnalysisScope.entries,
                    selected = state.scope,
                    label = {
                        stringResource(
                            if (it == AnalysisScope.MONTH) SharedRes.string.analysis_scope_month else SharedRes.string.analysis_scope_year,
                        )
                    },
                    onSelect = viewModel::onScopeSelected,
                    compact = true,
                )
                PeriodStepper(state, onMonth = viewModel::onMonthSelected, onYear = viewModel::onYearSelected)
                AccountChip(state = state, onSelect = viewModel::setAccountFilter, onClear = viewModel::clearAccountFilter)
            }
            if (width >= 760.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(SharedRes.string.nav_analysis), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    controls()
                }
            } else {
                Text(stringResource(SharedRes.string.nav_analysis), style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { controls() }
            }
            if (state.errorMessage != null) {
                InlineBanner(
                    kind = BannerKind.Error,
                    text = stringResource(SharedRes.string.failure_load_analysis),
                    actionLabel = stringResource(SharedRes.string.common_retry),
                    onAction = viewModel::refresh,
                )
            }
            if (page == null) return@ScrollPage

            AnalysisHero(state, page)

            // The charts, as many to a row as the window has room for; a short last row fills it.
            val cards = buildList<@Composable (Modifier) -> Unit> {
                page.pace?.takeIf { it.typical != null && page.periodIsRunning }?.let { pace ->
                    add { modifier -> HomeCard(stringResource(SharedRes.string.analysis_section_pace), modifier) { HoverPace(pace) } }
                }
                if (page.trend.any { it.expenseCents != 0L }) {
                    add { modifier ->
                        HomeCard(
                            stringResource(
                                if (state.scope == AnalysisScope.YEAR) SharedRes.string.analysis_section_trend_year else SharedRes.string.analysis_section_trend,
                            ),
                            modifier,
                        ) { TrendBars(state, page, viewModel::onMonthSelected) }
                    }
                }
                if (page.netWorth.size >= 2) {
                    add { modifier ->
                        HomeCard(stringResource(SharedRes.string.analysis_section_net_worth), modifier) { HoverLine(page.netWorth) }
                    }
                }
                if (page.byKind.totalCents > 0L) {
                    add { modifier -> HomeCard(stringResource(SharedRes.string.analysis_section_how), modifier) { HowBars(page.byKind) } }
                }
                if (page.incomeCategories.isNotEmpty()) {
                    add { modifier ->
                        HomeCard(stringResource(SharedRes.string.analysis_section_income), modifier) {
                            Breakdown(
                                page.incomeCategories.map {
                                    BreakdownEntry(name = it.displayName(), icon = it.markIcon(), color = it.markColor(), cents = it.incomeCents, isRest = it.categoryId == null && it.tripId == null)
                                },
                                days = 0L,
                            )
                        }
                    }
                }
                if (page.trend.any { it.incomeCents != 0L }) {
                    add { modifier ->
                        HomeCard(stringResource(SharedRes.string.analysis_section_income_trend), modifier) { IncomeBars(state, page, viewModel::onMonthSelected) }
                    }
                }
            }
            val perRow = when {
                width >= 1240.dp -> 4
                width >= 640.dp -> 2
                else -> 1
            }
            cards.chunked(perRow).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { card -> card(Modifier.weight(1f).fillMaxHeight()) }
                }
            }

            if (page.categories.isEmpty()) {
                Text(stringResource(SharedRes.string.analysis_empty_title), color = FinanceTheme.colors.mutedText)
            } else {
                HomeCard(stringResource(SharedRes.string.analysis_section_where)) {
                    WhereTable(page, tables, width, onCategory = viewModel::onCategoryClicked)
                }
            }

            tables?.let { loaded ->
                MonthTables(
                    state = state,
                    tables = loaded,
                    shownMonths = if (width >= 900.dp) loaded.months.size else NARROW_MONTHS,
                    onMonth = viewModel::onMonthSelected,
                    onCell = { row, month ->
                        openMovements(
                            row.categoryId?.let { id -> state.categories.filter { it.id == id || it.parentId == id }.map { it.id }.toSet() + id },
                            row.categoryId == null,
                            month.atDay(1).toString(),
                            month.atEndOfMonth().toString(),
                        )
                    },
                )
            }
        }
    }
}

/** How many of the twelve months a month table shows when the window is too narrow for them all. */
private const val NARROW_MONTHS = 6

/** The period, with a step to the one before and after among those with activity. */
@Composable
private fun PeriodStepper(state: AnalysisUiState, onMonth: (YearMonth) -> Unit, onYear: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val months = state.activityMonths.ifEmpty { listOf(state.month) }.sorted()
    val years = months.map { it.year }.distinct()
    val monthly = state.scope == AnalysisScope.MONTH
    val previous: (() -> Unit)? = if (monthly) {
        months.lastOrNull { it < state.month }?.let { { onMonth(it) } }
    } else {
        years.lastOrNull { it < state.year }?.let { { onYear(it) } }
    }
    val next: (() -> Unit)? = if (monthly) {
        months.firstOrNull { it > state.month }?.let { { onMonth(it) } }
    } else {
        years.firstOrNull { it > state.year }?.let { { onYear(it) } }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIconButton(onClick = { previous?.invoke() }, enabled = previous != null) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(Res.string.analysis_previous))
        }
        Box {
            AppTextButton(onClick = { expanded = true }) {
                Text(
                    if (monthly) formatMonthYear(state.month) else state.year.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (monthly) {
                    MonthPickerContent(initial = state.month, availableMonths = months) {
                        expanded = false
                        onMonth(it)
                    }
                } else {
                    years.sortedDescending().forEach { year ->
                        AppDropdownMenuItem(text = { Text(year.toString()) }, onClick = { expanded = false; onYear(year) })
                    }
                }
            }
        }
        AppIconButton(onClick = { next?.invoke() }, enabled = next != null) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(Res.string.analysis_next))
        }
    }
}

/**
 * The period on the dark panel: what it spent, against what is usual, and beside it the income,
 * what was saved (and at what rate) and the usual it is measured against.
 */
@Composable
private fun AnalysisHero(state: AnalysisUiState, page: AnalysisPage) {
    val colors = FinanceTheme.colors
    val totals = page.totals
    val typical = page.baselineKind == BaselineKind.TYPICAL_MONTH
    val usual = page.usualExpenseCents?.takeIf { it > 0L }
    // The first days of a month say nothing yet about how it compares.
    val percent = usual
        ?.takeIf { !(page.periodIsRunning && state.scope == AnalysisScope.MONTH && state.today.dayOfMonth < 5) }
        ?.let { ((totals.actualExpenseCents - it) * 100 / it).toInt() }
    val period = if (state.scope == AnalysisScope.MONTH) formatMonthYear(state.month) else state.year.toString()
    ListHero(
        eyebrow = stringResource(SharedRes.string.analysis_hero_eyebrow) + " · " + period,
        cents = totals.actualExpenseCents,
        watermark = Icons.Outlined.BarChart,
        modifier = Modifier.fillMaxWidth(),
    ) {
        percent?.let {
            Text(
                when {
                    it == 0 -> stringResource(if (typical) SharedRes.string.analysis_vs_typical_same else SharedRes.string.analysis_vs_last_year_same)
                    it < 0 -> stringResource(if (typical) SharedRes.string.analysis_vs_typical_less else SharedRes.string.analysis_vs_last_year_less, abs(it))
                    else -> stringResource(if (typical) SharedRes.string.analysis_vs_typical_more else SharedRes.string.analysis_vs_last_year_more, it)
                },
                color = if (it > 0) colors.heroDebt else colors.heroIncome,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroStatBox(stringResource(SharedRes.string.movement_filter_income), totals.actualIncomeCents, colors.heroIncome)
            HeroStatBox(
                stringResource(Res.string.analysis_saving) +
                    if (totals.actualIncomeCents > 0L) " · ${totals.savingsRateBasisPoints / 100} %" else "",
                totals.netActualCents,
                if (totals.netActualCents < 0L) colors.heroDebt else colors.heroOnSurface,
            )
            usual?.let {
                HeroStatBox(
                    stringResource(if (typical) SharedRes.string.analysis_pace_typical else Res.string.analysis_last_year),
                    it,
                    colors.heroOnSurface,
                )
            }
        }
    }
}

/** The period's months as bars against their average: the pointed one reads out, a click opens it. */
@Composable
private fun TrendBars(state: AnalysisUiState, page: AnalysisPage, onMonth: (YearMonth) -> Unit) {
    val muted = FinanceTheme.colors.mutedText
    // Of the months that have ended: the one still running would pull it down.
    val spent = page.trend.filter { it.month < YearMonth.from(state.today) }.map { it.expenseCents }.filter { it != 0L }
    val average = if (spent.isEmpty()) null else spent.sum() / spent.size
    HoverBars(
        months = page.trend.map { it.month },
        values = page.trend.map { it.expenseCents },
        line = average,
        onMonth = onMonth,
        current = state.month.takeIf { state.scope == AnalysisScope.MONTH },
    ) { index ->
        if (index == null) {
            average?.let { Text(stringResource(SharedRes.string.analysis_average, formatEuroCents(it)), style = MaterialTheme.typography.bodySmall, color = muted) }
        } else {
            val month = page.trend[index]
            Text(formatMonthYear(month.month).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = muted, maxLines = 1)
            Text(formatEuroCents(month.expenseCents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                stringResource(Res.string.analysis_income_amount, formatEuroCents(month.incomeCents)),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** What came in each month against the average: the pointed one says what was left of it, a click opens it. */
@Composable
private fun IncomeBars(state: AnalysisUiState, page: AnalysisPage, onMonth: (YearMonth) -> Unit) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val earned = page.trend.filter { it.month < YearMonth.from(state.today) }.map { it.incomeCents }.filter { it != 0L }
    val average = if (earned.isEmpty()) null else earned.sum() / earned.size
    HoverBars(
        months = page.trend.map { it.month },
        values = page.trend.map { it.incomeCents },
        line = average,
        onMonth = onMonth,
        current = state.month.takeIf { state.scope == AnalysisScope.MONTH },
    ) { index ->
        if (index == null) {
            average?.let { Text(stringResource(SharedRes.string.analysis_average, formatEuroCents(it)), style = MaterialTheme.typography.bodySmall, color = muted) }
        } else {
            val month = page.trend[index]
            val saved = month.incomeCents - month.expenseCents
            Text(formatMonthYear(month.month).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = muted, maxLines = 1)
            Text(formatEuroCents(month.incomeCents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                stringResource(Res.string.analysis_saving) + " " + formatEuroCents(saved),
                style = MaterialTheme.typography.bodySmall,
                color = if (saved < 0L) colors.debt else muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Where the money went, as a table: each row's share as a bar, its usual, how far from it (once the
 * period has closed: a running one is not there yet) and its twelve months. A category opens.
 */
@Composable
private fun WhereTable(page: AnalysisPage, tables: AnalysisTables?, width: Dp, onCategory: (String) -> Unit) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val total = page.totals.actualExpenseCents.coerceAtLeast(1L)
    val header = MaterialTheme.typography.labelLarge
    val showBar = width >= 700.dp
    val showUsual = width >= 560.dp
    val showDelta = !page.periodIsRunning && width >= 640.dp
    val showTrend = width >= 900.dp
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.column_category), Modifier.weight(1.2f), style = header, color = muted)
            if (showBar) Box(Modifier.weight(1f))
            Text(stringResource(Res.string.column_share), Modifier.width(56.dp), style = header, color = muted, textAlign = TextAlign.End)
            Text(stringResource(Res.string.column_amount), Modifier.width(104.dp), style = header, color = muted, textAlign = TextAlign.End)
            if (showUsual) Text(stringResource(Res.string.column_usual), Modifier.width(104.dp), style = header, color = muted, textAlign = TextAlign.End)
            if (showDelta) Text(stringResource(Res.string.column_difference), Modifier.width(104.dp), style = header, color = muted, textAlign = TextAlign.End)
            if (showTrend) Text(stringResource(Res.string.column_trend), Modifier.width(128.dp), style = header, color = muted, textAlign = TextAlign.End)
        }
        page.categories.forEach { row ->
            HorizontalDivider(color = colors.cardBorder)
            val usual = page.usualByCategory[row.rowKey()]?.takeIf { it != 0L }
            val isTrip = row.rowKind == AnalysisBreakdownKind.TRIP
            val open = row.categoryId?.takeIf { !isTrip }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .then(open?.let { id -> Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { onCategory(id) } } ?: Modifier)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IdentityIconTile(icon = row.markIcon(), color = row.markColor(), size = 32.dp)
                    Text(row.displayName(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (showBar) {
                    BudgetProgressBar(
                        fraction = (row.expenseCents.toFloat() / total).coerceIn(0f, 1f),
                        color = themedIdentityColor(row.markColor()),
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                    )
                }
                Text(
                    shareLabel(row.expenseCents, total),
                    Modifier.width(56.dp),
                    color = muted,
                    textAlign = TextAlign.End,
                )
                Text(
                    formatEuroCents(row.expenseCents),
                    Modifier.width(104.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                if (showUsual) Text(usual?.let(::formatEuroCents) ?: "—", Modifier.width(104.dp), color = muted, textAlign = TextAlign.End, maxLines = 1)
                if (showDelta) {
                    val delta = usual?.let { row.expenseCents - it }
                    Text(
                        delta?.let { (if (it > 0L) "+" else "") + formatEuroCents(it) } ?: "—",
                        Modifier.width(104.dp),
                        color = when {
                            delta == null || delta == 0L -> muted
                            delta > 0L -> colors.debt
                            else -> colors.income
                        },
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
                if (showTrend) {
                    Box(Modifier.width(128.dp), contentAlignment = Alignment.CenterEnd) {
                        tables?.categories?.firstOrNull { !isTrip && it.key == "category:${row.categoryId ?: "none"}" }?.let {
                            Sparkline(it.cents, row.markColor(), Modifier.width(104.dp).height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sparkline(values: List<Long>, color: Color, modifier: Modifier) {
    val top = values.maxOrNull()?.takeIf { it > 0L }?.toFloat() ?: return
    Canvas(modifier) {
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { index, cents ->
            val y = size.height - (size.height - 2f) * cents / top - 1f
            if (index == 0) path.moveTo(0f, y) else path.lineTo(index * step, y)
        }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
        val last = values.last()
        drawCircle(color, radius = 2.5.dp.toPx(), center = Offset(size.width, size.height - (size.height - 2f) * last / top - 1f))
    }
}

/**
 * A category over the whole page: what it came to in the period straight on the page beside its
 * twelve months, its subcategories, and the period's movements in it, scrolling under the rest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPane(
    state: AnalysisUiState,
    page: AnalysisPage,
    detail: CategoryDetailState,
    category: CategoryRecord,
    categoryIds: Set<String>,
    range: Pair<String, String>,
    movements: MovementsViewModel,
    onBack: () -> Unit,
    onMonth: (YearMonth) -> Unit,
    onOpenMovement: (MovementSummary) -> Unit,
    onOpenInMovements: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val row = page.categories.firstOrNull { it.categoryId == category.id }
    val spent = row?.expenseCents ?: 0L
    val usual = page.usualByCategory["category:${category.id}"]?.takeIf { it != 0L }
    val period = if (state.scope == AnalysisScope.MONTH) formatMonthYear(state.month).replaceFirstChar { it.uppercase() } else state.year.toString()

    // The ledger is the Movements page's own; it loads here when that page has not been opened yet.
    LaunchedEffect(movements) { movements.onScreenShown() }
    val ledger by movements.state.collectAsState()
    val rows = remember(ledger.movements, categoryIds, range, state.filterAccountId) {
        ledger.movements.filter { movement ->
            movement.type == MovementType.EXPENSE &&
                movement.categoryId in categoryIds &&
                movement.date >= range.first && movement.date <= range.second &&
                (state.filterAccountId == null || movement.accountId == state.filterAccountId)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(SharedRes.string.nav_analysis))
            }
            IdentityIconTile(icon = categoryIcon(category.icon), color = categoryColor(category.color), size = 48.dp)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(category.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(period, color = muted, style = MaterialTheme.typography.bodyMedium)
            }
            SecondaryButton(text = stringResource(Res.string.analysis_view_movements), onClick = onOpenInMovements)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val figures = @Composable {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Where it stands, straight on the page: no card around the figures.
                    Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Bottom) {
                        Figure(stringResource(Res.string.budgets_column_spent)) {
                            MoneyText(cents = spent, style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 42.sp))
                        }
                        Figure(stringResource(Res.string.column_share)) {
                            Text(
                                shareLabel(spent, page.totals.actualExpenseCents),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        if (usual != null) {
                            Figure(stringResource(Res.string.column_usual)) { MoneyText(cents = usual, style = MaterialTheme.typography.headlineSmall) }
                            if (!page.periodIsRunning) {
                                Figure(stringResource(Res.string.column_difference)) {
                                    val delta = spent - usual
                                    Text(
                                        (if (delta > 0L) "+" else "") + formatEuroCents(delta),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = if (delta > 0L) colors.debt else colors.income,
                                    )
                                }
                            }
                        }
                    }
                    if (detail.subcategories.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            detail.subcategories.forEach { sub ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(10.dp).background(categoryColor(sub.categoryColor), CircleShape))
                                    Text(
                                        if (sub.categoryId == detail.categoryId) {
                                            stringResource(SharedRes.string.analysis_detail_self, sub.categoryName.orEmpty())
                                        } else {
                                            sub.categoryName.orEmpty()
                                        },
                                        color = muted,
                                        maxLines = 1,
                                    )
                                    MoneyText(cents = sub.expenseCents, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                                }
                            }
                        }
                    }
                }
            }
            val chart = @Composable {
                val values = detail.months.map { it.cents }
                val nonZero = detail.months.filter { it.month < YearMonth.from(state.today) }.map { it.cents }.filter { it != 0L }
                val average = if (nonZero.isEmpty()) null else nonZero.sum() / nonZero.size
                HoverBars(
                    months = detail.months.map { it.month },
                    values = values,
                    line = average,
                    onMonth = onMonth,
                    current = state.month.takeIf { state.scope == AnalysisScope.MONTH },
                ) { index ->
                    if (index == null) {
                        average?.let { Text(stringResource(SharedRes.string.analysis_average, formatEuroCents(it)), style = MaterialTheme.typography.bodySmall, color = muted) }
                    } else {
                        Text(formatMonthYear(detail.months[index].month).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = muted)
                        Text(formatEuroCents(values[index]), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
            val charted = detail.months.any { it.cents != 0L }
            if (maxWidth >= 900.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Box(Modifier.weight(1f)) { figures() }
                    if (charted) Box(Modifier.width(440.dp)) { chart() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    figures()
                    if (charted) chart()
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.analysis_category_movements), style = MaterialTheme.typography.titleMedium)
            if (rows.isNotEmpty()) Text(rows.size.toString(), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        if (rows.isEmpty()) {
            if (!ledger.isLoading) Text(stringResource(Res.string.analysis_category_movements_empty), color = muted)
        } else {
            PartMovements(rows, onOpenMovement, Modifier.weight(1f))
        }
    }
}

/** One row of a month table, already as text: a table only lays it out. */
internal class MonthTableRow(
    val name: String,
    val cells: List<String>,
    val total: String,
    val average: String,
    val mark: Color? = null,
    val strong: Boolean = false,
    /** 0..1 per month: how strongly the cell is tinted, the row's biggest month the most. */
    val heat: List<Float>? = null,
    val tones: List<Color?>? = null,
    val onCell: ((Int) -> Unit)? = null,
)

@Composable
private fun amountRow(
    name: String,
    cents: List<Long>,
    started: List<Boolean>,
    closed: List<Boolean>,
    mark: Color? = null,
    strong: Boolean = false,
    heat: Boolean = false,
    signed: Boolean = false,
    onCell: ((Int) -> Unit)? = null,
): MonthTableRow {
    val colors = FinanceTheme.colors
    val top = cents.maxOrNull()?.takeIf { it > 0L }?.toFloat()
    return MonthTableRow(
        name = name,
        cells = cents.mapIndexed { index, value -> if (started[index]) wholeEuros(value, signed) else "" },
        total = wholeEuros(cents.sum(), signed),
        average = wholeEuros(closedAverage(cents, closed), signed),
        mark = mark,
        strong = strong,
        heat = top?.takeIf { heat }?.let { max -> cents.map { (it / max).coerceIn(0f, 1f) } },
        tones = if (signed) cents.map { if (it < 0L) colors.debt else if (it > 0L) colors.income else null } else null,
        onCell = onCell,
    )
}

/** A part of a whole as the other pages write it: nothing is 0%, a sliver is "<1%". */
internal fun shareLabel(part: Long, whole: Long): String =
    if (part <= 0L || whole <= 0L) "0%" else formatPercentLabel(part.toFloat() / whole)

/** Whole euros with the thousands point: a twelve-month table has no room for cents. */
internal fun wholeEuros(cents: Long, signed: Boolean = false): String {
    val euros = (cents / 100.0).roundToLong()
    return (if (signed && euros > 0L) "+" else "") + String.format(Locale.forLanguageTag("ca-ES"), "%,d", euros)
}

/** What the twelve months are told by: a table at a time. */
private enum class MonthView { CATEGORIES, KINDS, ACCOUNTS }

/**
 * The twelve months as one table, told by category, by how the spending came about or by what each
 * account did, as chosen above it. A figure of a category opens its movements; a month, that month.
 */
@Composable
private fun MonthTables(
    state: AnalysisUiState,
    tables: AnalysisTables,
    shownMonths: Int,
    onMonth: (YearMonth) -> Unit,
    onCell: (MonthRow, YearMonth) -> Unit,
) {
    val muted = FinanceTheme.colors.mutedText
    var view by remember { mutableStateOf(MonthView.CATEGORIES) }
    val views = MonthView.entries.filter { it != MonthView.ACCOUNTS || tables.accounts.isNotEmpty() }
    HomeCard(
        title = stringResource(Res.string.analysis_months_title),
        trailing = {
            SegmentedControl(
                options = views,
                selected = view,
                label = {
                    stringResource(
                        when (it) {
                            MonthView.CATEGORIES -> Res.string.analysis_months_by_category
                            MonthView.KINDS -> SharedRes.string.analysis_section_how
                            MonthView.ACCOUNTS -> Res.string.analysis_months_by_account
                        },
                    )
                },
                onSelect = { view = it },
                compact = true,
            )
        },
    ) {
        Text(
            listOfNotNull(
                stringResource(
                    when (view) {
                        MonthView.CATEGORIES -> Res.string.analysis_matrix_hint
                        MonthView.KINDS -> Res.string.analysis_kind_months_hint
                        MonthView.ACCOUNTS -> Res.string.analysis_accounts_hint
                    },
                ),
                if (shownMonths < tables.months.size) stringResource(Res.string.analysis_months_narrow_hint, shownMonths, tables.months.size) else null,
            ).joinToString(" "),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        val (rows, summary) = when (view) {
            MonthView.CATEGORIES -> categoryRows(tables, onCell)
            MonthView.KINDS -> kindRows(tables)
            MonthView.ACCOUNTS -> accountRows(tables)
        }
        MonthTable(
            labels = tables.months.map { formatShortMonth(it) },
            rows = rows,
            summary = summary,
            shown = shownMonths,
            selected = state.month.takeIf { state.scope == AnalysisScope.MONTH }?.let(tables.months::indexOf),
            onColumn = { onMonth(tables.months[it]) },
        )
    }
}

/** Every category month by month, over the months' spending, income, saving and its rate. */
@Composable
private fun categoryRows(tables: AnalysisTables, onCell: (MonthRow, YearMonth) -> Unit): Pair<List<MonthTableRow>, List<MonthTableRow>> {
    val colors = FinanceTheme.colors
    val rows = tables.categories.map { row ->
        amountRow(
            name = when {
                row.isTrips -> stringResource(Res.string.analysis_trips)
                else -> row.name ?: stringResource(SharedRes.string.common_no_category)
            },
            cents = row.cents,
            started = tables.started,
            closed = tables.closed,
            mark = if (row.isTrips) colors.transfer else categoryColor(row.color),
            heat = true,
            onCell = if (row.isTrips) null else ({ index -> onCell(row, tables.months[index]) }),
        )
    }
    val saving = tables.income.zip(tables.expense) { income, expense -> income - expense }
    val incomeTotal = tables.income.sum()
    val summary = listOf(
        amountRow(stringResource(Res.string.analysis_total_expense), tables.expense, tables.started, tables.closed, strong = true),
        amountRow(stringResource(SharedRes.string.movement_filter_income), tables.income, tables.started, tables.closed),
        amountRow(stringResource(Res.string.analysis_saving), saving, tables.started, tables.closed, signed = true),
        MonthTableRow(
            name = stringResource(Res.string.analysis_saving_rate),
            cells = tables.income.mapIndexed { index, income ->
                if (tables.started[index] && income > 0L) "${saving[index] * 100 / income} %" else ""
            },
            total = if (incomeTotal > 0L) "${saving.sum() * 100 / incomeTotal} %" else "—",
            average = "",
            tones = saving.map { if (it < 0L) colors.debt else null },
        ),
    )
    return rows to summary
}

/** How each month's spending came about: recurring payments, ordinary spending, one-offs. */
@Composable
private fun kindRows(tables: AnalysisTables): Pair<List<MonthTableRow>, List<MonthTableRow>> {
    val colors = FinanceTheme.colors
    val rows = listOf(
        amountRow(
            stringResource(SharedRes.string.analysis_kind_recurring),
            tables.byKind.map { it.recurringCents },
            tables.started,
            tables.closed,
            mark = MaterialTheme.colorScheme.primary,
            heat = true,
        ),
        amountRow(stringResource(SharedRes.string.analysis_kind_variable), tables.byKind.map { it.variableCents }, tables.started, tables.closed, mark = colors.mutedText, heat = true),
        amountRow(stringResource(SharedRes.string.analysis_kind_one_off), tables.byKind.map { it.oneOffCents }, tables.started, tables.closed, mark = colors.alert, heat = true),
    )
    return rows to listOf(amountRow(stringResource(SharedRes.string.budget_field_total), tables.byKind.map { it.totalCents }, tables.started, tables.closed, strong = true))
}

/** What each account's balance did each month. */
@Composable
private fun accountRows(tables: AnalysisTables): Pair<List<MonthTableRow>, List<MonthTableRow>> {
    val rows = tables.accounts.map { amountRow(it.name.orEmpty(), it.cents, tables.started, tables.closed, signed = true) }
    val total = tables.months.indices.map { index -> tables.accounts.sumOf { it.cents[index] } }
    return rows to listOf(amountRow(stringResource(SharedRes.string.budget_field_total), total, tables.started, tables.closed, strong = true, signed = true))
}

/**
 * [rows] over [summary], a column per label (the last [shown] of them: months here, a trip's days
 * elsewhere) and the totals of them all. The row and the column under the pointer stand out, to
 * read a figure across and down.
 */
@Composable
internal fun MonthTable(
    labels: List<String>,
    rows: List<MonthTableRow>,
    summary: List<MonthTableRow>,
    shown: Int,
    selected: Int?,
    onColumn: (Int) -> Unit,
    averageLabel: String = stringResource(Res.string.column_average),
) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val tint = MaterialTheme.colorScheme.primary
    val wash = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val columns = (labels.size - shown).coerceAtLeast(0) until labels.size
    val pointedColumn = remember { mutableStateOf<Int?>(null) }
    val pointedRow = remember { mutableStateOf<Int?>(null) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(NAME_WEIGHT))
            columns.forEach { column ->
                val marked = column == selected || pointedColumn.value == column
                Text(
                    labels[column],
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .pointing(column, pointedColumn)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onColumn(column) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (marked) FontWeight.Medium else null,
                    color = if (marked) MaterialTheme.colorScheme.onSurface else muted,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
            Text(
                stringResource(SharedRes.string.budget_field_total),
                Modifier.weight(SUMMARY_WEIGHT),
                style = MaterialTheme.typography.labelLarge,
                color = muted,
                textAlign = TextAlign.End,
            )
            Text(
                averageLabel,
                Modifier.weight(SUMMARY_WEIGHT),
                style = MaterialTheme.typography.labelLarge,
                color = muted,
                textAlign = TextAlign.End,
            )
        }
        (rows + summary).forEachIndexed { index, row ->
            HorizontalDivider(color = colors.cardBorder, thickness = if (index == rows.size && rows.isNotEmpty()) 2.dp else 1.dp)
            val style: TextStyle = if (row.strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
            Row(
                Modifier.fillMaxWidth().pointing(index, pointedRow).background(if (pointedRow.value == index) wash else Color.Transparent),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(NAME_WEIGHT).padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.mark?.let { Box(Modifier.size(10.dp).background(it, CircleShape)) }
                    Text(row.name, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                columns.forEach { cell ->
                    val text = row.cells[cell]
                    val heat = row.heat?.get(cell) ?: 0f
                    val opens = row.onCell != null && text.isNotEmpty()
                    Text(
                        text,
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 1.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .pointing(cell, pointedColumn)
                            .background(
                                when {
                                    heat > 0f -> tint.copy(alpha = 0.03f + 0.17f * heat)
                                    pointedColumn.value == cell -> wash
                                    else -> Color.Transparent
                                },
                            )
                            .then(if (opens) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { row.onCell?.invoke(cell) } else Modifier)
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        style = style,
                        fontWeight = if (pointedColumn.value == cell && pointedRow.value == index) FontWeight.Medium else null,
                        color = row.tones?.get(cell) ?: MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
                Text(row.total, Modifier.weight(SUMMARY_WEIGHT), style = style, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, maxLines = 1)
                Text(row.average, Modifier.weight(SUMMARY_WEIGHT), style = style, color = muted, textAlign = TextAlign.End, maxLines = 1)
            }
        }
    }
}

/** The period's first and last day, as the movement filters take them. */
private fun periodDates(state: AnalysisUiState): Pair<String, String> =
    when (state.scope) {
        AnalysisScope.MONTH -> state.month.atDay(1).toString() to state.month.atEndOfMonth().toString()
        AnalysisScope.YEAR -> "${state.year}-01-01" to "${state.year}-12-31"
    }

private const val NAME_WEIGHT = 2.6f
private const val SUMMARY_WEIGHT = 1.3f
