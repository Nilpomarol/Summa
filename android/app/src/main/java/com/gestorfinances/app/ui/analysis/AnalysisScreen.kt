package com.gestorfinances.app.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AnalysisBreakdownKind
import com.gestorfinances.app.data.repository.AnalysisCategoryTotal
import com.gestorfinances.app.data.repository.AnalysisSpendingByKind
import com.gestorfinances.app.di.AppContainer
import com.gestorfinances.app.ui.analysis.components.AmountLine
import com.gestorfinances.app.ui.analysis.components.MonthBars
import com.gestorfinances.app.ui.analysis.components.PaceChart
import com.gestorfinances.app.ui.common.AppDropdownMenu
import com.gestorfinances.app.ui.common.AppDropdownMenuItem
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.DistributionSegment
import com.gestorfinances.app.ui.common.EntityListRow
import com.gestorfinances.app.ui.common.HERO_MUTED_ALPHA
import com.gestorfinances.app.ui.common.HeroCaption
import com.gestorfinances.app.ui.common.HeroMonthPicker
import com.gestorfinances.app.ui.common.HeroToggle
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.LinkPill
import com.gestorfinances.app.ui.common.ListHero
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.RootPageHeader
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.SegmentedDistributionBar
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatMonth
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.heroTint
import com.gestorfinances.app.ui.navigation.Route
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** This page visit's ViewModel, starting from the account or category [route] was opened with. */
@Composable
fun analysisViewModel(appContainer: AppContainer, route: Route.Analysis): AnalysisViewModel = viewModel {
    AnalysisViewModel(
        analysisRepository = appContainer.analysisRepository,
        accountRepository = appContainer.accountRepository,
        categoryRepository = appContainer.categoryRepository,
    ).apply {
        if (route.accountId != null && route.accountName != null) setAccountFilter(route.accountId, route.accountName)
        route.categoryId?.let(::openCategoryOnLoad)
    }
}

/**
 * The Anàlisi tab: a month or year of spending on the forest hero against what is usual, then how
 * the month is going, where the money went, how it came about, the last twelve months, and net
 * worth. A category opens a sheet of its own; a trip opens its page.
 */
@Composable
internal fun AnalysisScreen(
    viewModel: AnalysisViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onOpenCategory: (categoryId: String) -> Unit,
    onOpenTrip: (tripId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel, dataVersion) { viewModel.onScreenShown(dataVersion) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RootPageHeader(
                title = stringResource(R.string.nav_analysis),
                trailing = {
                    AccountChip(
                        state = state,
                        onSelect = viewModel::setAccountFilter,
                        onClear = viewModel::clearAccountFilter,
                    )
                },
            )
        }
        state.errorMessage?.let { message ->
            item { InlineFailureBanner(diagnostic = message, messageRes = R.string.failure_load_analysis, onRetry = viewModel::refresh) }
        }
        val page = state.page
        item(key = "hero") {
            AnalysisHero(
                state = state,
                page = page,
                onScopeSelected = viewModel::onScopeSelected,
                onMonthSelected = viewModel::onMonthSelected,
                onYearSelected = viewModel::onYearSelected,
            )
        }
        if (page == null) return@LazyColumn

        page.pace?.takeIf { it.typical != null && page.periodIsRunning }?.let { pace ->
            item { SectionHeader(title = stringResource(R.string.analysis_section_pace), modifier = SectionGap) }
            item(key = "pace") { PaceSection(pace = pace) }
        }

        if (page.categories.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.analysis_empty_title),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            item { SectionHeader(title = stringResource(R.string.analysis_section_where), modifier = SectionGap) }
            item(key = "where") {
                WhereSection(
                    page = page,
                    onCategory = viewModel::onCategoryClicked,
                    onTrip = onOpenTrip,
                )
            }
            item { SectionHeader(title = stringResource(R.string.analysis_section_how), modifier = SectionGap) }
            item(key = "how") { HowSection(byKind = page.byKind) }
        }

        if (page.trend.any { it.expenseCents != 0L }) {
            item {
                SectionHeader(
                    title = stringResource(if (state.scope == AnalysisScope.YEAR) R.string.analysis_section_trend_year else R.string.analysis_section_trend),
                    modifier = SectionGap,
                )
            }
            item(key = "trend") {
                MonthBars(
                    months = page.trend.map { it.month },
                    values = page.trend.map { it.expenseCents },
                    initialSelection = page.trend.indexOfFirst { it.month == state.month }.takeIf { it >= 0 && state.scope == AnalysisScope.MONTH },
                    readout = { index ->
                        val month = page.trend[index]
                        stringResource(
                            R.string.analysis_trend_readout,
                            formatMonthYear(month.month),
                            formatEuroCents(month.expenseCents),
                            formatEuroCents(month.incomeCents),
                        )
                    },
                    onOpen = viewModel::onMonthSelected,
                )
            }
        }

        if (page.netWorth.size >= 2) {
            item { SectionHeader(title = stringResource(R.string.analysis_section_net_worth), modifier = SectionGap) }
            item(key = "net-worth") { NetWorthSection(points = page.netWorth) }
        }
    }

    state.categoryDetail?.let { detail ->
        CategoryDetailSheet(
            detail = detail,
            state = state,
            onOpenCategory = { id ->
                viewModel.onCategoryDetailDismissed()
                onOpenCategory(id)
            },
            onDismiss = viewModel::onCategoryDetailDismissed,
        )
    }
}

/** Which account the page reads: all of them, or one, picked from a small chip by the title. */
@Composable
private fun AccountChip(state: AnalysisUiState, onSelect: (String, String) -> Unit, onClear: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable { expanded = true }
                .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.filterAccountName ?: stringResource(R.string.analysis_filter_account_all),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppDropdownMenuItem(
                text = { Text(stringResource(R.string.analysis_filter_account_all)) },
                onClick = { expanded = false; onClear() },
            )
            state.accountOptions.forEach { account ->
                AppDropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = { expanded = false; onSelect(account.id, account.name) },
                )
            }
        }
    }
}

/**
 * The period's spending on the forest hero, how it compares with what is usual by this point, and
 * its income and saving as a quieter line. The month/year switch and the period sit by the eyebrow.
 */
@Composable
private fun AnalysisHero(
    state: AnalysisUiState,
    page: AnalysisPage?,
    onScopeSelected: (AnalysisScope) -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onYearSelected: (Int) -> Unit,
) {
    val colors = FinanceTheme.colors
    ListHero(
        eyebrow = stringResource(R.string.analysis_hero_eyebrow),
        cents = page?.totals?.actualExpenseCents ?: 0L,
        watermark = Icons.Outlined.BarChart,
        eyebrowTrailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeroToggle(
                    options = AnalysisScope.entries,
                    selected = state.scope,
                    label = {
                        stringResource(if (it == AnalysisScope.MONTH) R.string.analysis_scope_month else R.string.analysis_scope_year)
                    },
                    onSelect = onScopeSelected,
                )
                when (state.scope) {
                    AnalysisScope.MONTH -> HeroMonthPicker(
                        month = state.month,
                        months = state.activityMonths.ifEmpty { listOf(state.month) },
                        onMonthSelected = onMonthSelected,
                    )
                    AnalysisScope.YEAR -> HeroYearPicker(
                        year = state.year,
                        years = state.activityMonths.map { it.year }.distinct().sortedDescending().ifEmpty { listOf(state.year) },
                        onYearSelected = onYearSelected,
                    )
                }
            }
        },
    ) {
        if (page == null) return@ListHero
        // A month barely begun says nothing yet against a typical one: a few euros read as a swing.
        val tooEarly = state.scope == AnalysisScope.MONTH && state.month == YearMonth.now() &&
            LocalDate.now().dayOfMonth < COMPARISON_FROM_DAY
        page.usualExpenseCents?.takeIf { it > 0L && !tooEarly }?.let { usual ->
            val spent = page.totals.actualExpenseCents
            val percent = ((spent - usual) * 100 / usual).toInt()
            val typical = page.baselineKind == BaselineKind.TYPICAL_MONTH
            val text = when {
                percent == 0 -> stringResource(if (typical) R.string.analysis_vs_typical_same else R.string.analysis_vs_last_year_same)
                percent < 0 -> stringResource(if (typical) R.string.analysis_vs_typical_less else R.string.analysis_vs_last_year_less, abs(percent))
                else -> stringResource(if (typical) R.string.analysis_vs_typical_more else R.string.analysis_vs_last_year_more, percent)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val tone = if (percent > 0) colors.heroDebt else colors.heroIncome
                Icon(
                    imageVector = if (percent > 0) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                    contentDescription = null,
                    tint = tone,
                    modifier = Modifier.size(18.dp),
                )
                Text(text = text, color = tone, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        HeroCaption(
            text = stringResource(
                R.string.analysis_hero_income_saving,
                formatEuroCents(page.totals.actualIncomeCents),
                formatEuroCents(page.totals.netActualCents),
            ),
        )
    }
}

/** The year a hero covers, as a chip that opens the years with activity. */
@Composable
private fun HeroYearPicker(year: Int, years: List<Int>, onYearSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val onHero = FinanceTheme.colors.heroOnSurface
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(heroTint(onHero, 0.12f))
                .clickable { expanded = true }
                .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = year.toString(), color = onHero, style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = onHero.copy(alpha = HERO_MUTED_ALPHA), modifier = Modifier.size(16.dp))
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            years.forEach { option ->
                AppDropdownMenuItem(text = { Text(option.toString()) }, onClick = { expanded = false; onYearSelected(option) })
            }
        }
    }
}

/** The day of the month from which it is compared with a typical one. */
private const val COMPARISON_FROM_DAY = 5

/** This month's running total against the typical month's, with how far apart they are today. */
@Composable
private fun PaceSection(pace: Pace) {
    val typical = pace.typical ?: return
    val today = pace.current.lastIndex
    val gap = pace.current.last() - (typical.getOrNull(today) ?: 0L)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (today + 1 >= COMPARISON_FROM_DAY) Text(
            text = stringResource(
                if (gap > 0L) R.string.analysis_pace_above else R.string.analysis_pace_below,
                formatEuroCents(abs(gap)),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (gap > 0L) FinanceTheme.colors.debt else FinanceTheme.colors.income,
        )
        PaceChart(pace = pace)
    }
}

/**
 * Where the money went: one bar split by the biggest rows, then every row with its share and how it
 * compares with its usual (its average, once the period has closed).
 */
@Composable
private fun WhereSection(page: AnalysisPage, onCategory: (String) -> Unit, onTrip: (String) -> Unit) {
    val total = page.totals.actualExpenseCents.coerceAtLeast(1L)
    val colors = FinanceTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SegmentedDistributionBar(
            segments = page.categories.take(DISTRIBUTION_SEGMENTS).map { row ->
                DistributionSegment(color = themedIdentityColor(row.markColor()), fraction = row.expenseCents.toFloat() / total)
            } + listOfNotNull(
                page.categories.drop(DISTRIBUTION_SEGMENTS).sumOf { it.expenseCents }.takeIf { it > 0L }?.let {
                    DistributionSegment(color = colors.mutedText, fraction = it.toFloat() / total)
                },
            ),
            contentDescription = stringResource(R.string.analysis_section_where),
            modifier = Modifier.padding(vertical = 6.dp),
        )
        var showAll by remember(page) { mutableStateOf(false) }
        val hidden = page.categories.size - SHOWN_ROWS
        val rows = if (showAll || hidden <= 0) page.categories else page.categories.take(SHOWN_ROWS)
        rows.forEachIndexed { index, row ->
            val share = (row.expenseCents * 100 / total).toInt()
            val usual = page.usualByCategory[row.rowKey()]
            val comparison = when {
                usual == null || usual == 0L -> null
                page.periodIsRunning -> stringResource(R.string.analysis_row_usual, formatEuroCents(usual))
                else -> {
                    val delta = row.expenseCents - usual
                    stringResource(
                        if (delta > 0L) R.string.analysis_row_more else R.string.analysis_row_less,
                        formatEuroCents(abs(delta)),
                    )
                }
            }
            EntityListRow(
                leading = { IdentityIconTile(icon = row.markIcon(), color = row.markColor()) },
                title = row.displayName(),
                subtitle = listOfNotNull(stringResource(R.string.analysis_row_share, share), comparison).joinToString(" · "),
                isLast = index == rows.lastIndex,
                onClick = when {
                    row.rowKind == AnalysisBreakdownKind.TRIP -> row.tripId?.let { id -> { onTrip(id) } }
                    else -> row.categoryId?.let { id -> { onCategory(id) } }
                },
                trailing = { MoneyText(cents = row.expenseCents, style = MaterialTheme.typography.titleMedium) },
            )
        }
        if (hidden > 0) {
            Box(modifier = Modifier.padding(top = 6.dp)) {
                LinkPill(
                    text = if (showAll) stringResource(R.string.analysis_show_fewer) else stringResource(R.string.analysis_show_more, hidden),
                    onClick = { showAll = !showAll },
                )
            }
        }
    }
}

/** How the spending came about: recurring payments, ordinary variable spending, and one-offs. */
@Composable
private fun HowSection(byKind: AnalysisSpendingByKind) {
    val total = byKind.totalCents
    if (total <= 0L) return
    val colors = FinanceTheme.colors
    val parts = listOf(
        Triple(R.string.analysis_kind_recurring, byKind.recurringCents, MaterialTheme.colorScheme.primary),
        Triple(R.string.analysis_kind_variable, byKind.variableCents, colors.mutedText),
        Triple(R.string.analysis_kind_one_off, byKind.oneOffCents, colors.alert),
    ).filter { it.second > 0L }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SegmentedDistributionBar(
            segments = parts.map { (_, cents, color) -> DistributionSegment(color = color, fraction = cents.toFloat() / total) },
            contentDescription = stringResource(R.string.analysis_section_how),
        )
        parts.forEach { (label, cents, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
                Text(text = stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.analysis_row_share, (cents * 100 / total).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                )
                MoneyText(cents = cents, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Net worth month by month: the latest figure and its change over the months shown. */
@Composable
private fun NetWorthSection(points: List<com.gestorfinances.app.ui.analysis.MonthAmount>) {
    val change = points.last().cents - points.first().cents
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyText(cents = points.last().cents, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(
                    R.string.analysis_net_worth_change,
                    (if (change > 0L) "+" else "") + formatEuroCents(change),
                    points.size - 1,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (change < 0L) FinanceTheme.colors.debt else FinanceTheme.colors.income,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        AmountLine(points = points)
    }
}

/**
 * A category's sheet: what it came to in the period, its last twelve months against their average,
 * its subcategories, and the way to its own page (where its movements are).
 */
@Composable
private fun CategoryDetailSheet(
    detail: CategoryDetailState,
    state: AnalysisUiState,
    onOpenCategory: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val category = state.categories.firstOrNull { it.id == detail.categoryId }
    val row = state.page?.categories?.firstOrNull { it.categoryId == detail.categoryId }
    val period = when (state.scope) {
        AnalysisScope.MONTH -> formatMonth(state.month).lowercase()
        AnalysisScope.YEAR -> state.year.toString()
    }
    AppModalBottomSheet(onDismissRequest = onDismiss, maxHeightFraction = 0.9f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IdentityIconTile(icon = categoryIcon(category?.icon), color = categoryColor(category?.color), size = 44.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = category?.name ?: row?.categoryName.orEmpty(), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(R.string.analysis_detail_period, period, formatEuroCents(row?.expenseCents ?: 0L)),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            detail.errorMessage?.let { InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_load_analysis) }
            if (detail.months.any { it.cents != 0L }) {
                SectionHeader(title = stringResource(R.string.analysis_section_trend))
                MonthBars(
                    months = detail.months.map { it.month },
                    values = detail.months.map { it.cents },
                    initialSelection = detail.months.indexOfFirst { it.month == state.month }.takeIf { it >= 0 },
                    readout = { index ->
                        stringResource(R.string.analysis_detail_readout, formatMonthYear(detail.months[index].month), formatEuroCents(detail.months[index].cents))
                    },
                    onOpen = null,
                )
            }
            if (detail.subcategories.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.analysis_detail_subcategories))
                Column {
                    detail.subcategories.forEachIndexed { index, sub ->
                        val isSelf = sub.categoryId == detail.categoryId
                        EntityListRow(
                            leading = { IdentityIconTile(icon = categoryIcon(sub.categoryIcon), color = categoryColor(sub.categoryColor), size = 36.dp) },
                            title = if (isSelf) stringResource(R.string.analysis_detail_self, sub.categoryName.orEmpty()) else sub.categoryName.orEmpty(),
                            isLast = index == detail.subcategories.lastIndex,
                            onClick = sub.categoryId?.takeIf { !isSelf }?.let { id -> { onOpenCategory(id) } },
                            dividerInset = 48.dp,
                            trailing = { MoneyText(cents = sub.expenseCents, style = MaterialTheme.typography.bodyLarge) },
                        )
                    }
                }
            }
            Row {
                Spacer(modifier = Modifier.weight(1f))
                LinkPill(text = stringResource(R.string.analysis_detail_open), onClick = { onOpenCategory(detail.categoryId) })
            }
        }
    }
}

private const val DISTRIBUTION_SEGMENTS = 6

/** Rows shown in "On van els diners" before the rest fold behind a pill. */
private const val SHOWN_ROWS = 8

/** Room above a page section's heading, so sections read apart. */
private val SectionGap = Modifier.padding(top = 12.dp)

internal fun AnalysisCategoryTotal.rowKey(): String =
    when (rowKind) {
        AnalysisBreakdownKind.TRIP -> "trip:${tripId ?: tripName.orEmpty()}"
        AnalysisBreakdownKind.CATEGORY -> "category:${categoryId ?: "uncategorized"}"
    }

@Composable
private fun AnalysisCategoryTotal.displayName(): String =
    tripName ?: categoryName ?: stringResource(R.string.common_no_category)

private fun AnalysisCategoryTotal.markIcon() =
    if (rowKind == AnalysisBreakdownKind.TRIP) Icons.Outlined.Flight else categoryIcon(categoryIcon)

@Composable
private fun AnalysisCategoryTotal.markColor(): Color =
    if (rowKind == AnalysisBreakdownKind.TRIP) FinanceTheme.colors.transfer else categoryColor(categoryColor)
