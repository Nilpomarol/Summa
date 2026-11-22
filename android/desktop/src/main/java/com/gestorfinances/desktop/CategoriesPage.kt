package com.gestorfinances.desktop

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.YearMonth
import java.time.LocalDate
import com.gestorfinances.ui.resources.common_expanded
import com.gestorfinances.ui.resources.common_collapsed
import com.gestorfinances.ui.resources.budget_remaining
import com.gestorfinances.ui.resources.budget_over
import com.gestorfinances.ui.resources.analysis_average
import com.gestorfinances.desktop.resources.trips_add_budget
import com.gestorfinances.desktop.resources.recurring_month_average
import com.gestorfinances.desktop.resources.people_history
import com.gestorfinances.desktop.resources.column_budget
import com.gestorfinances.desktop.resources.categories_expand_all
import com.gestorfinances.desktop.resources.categories_column_share
import com.gestorfinances.desktop.resources.categories_collapse_all
import com.gestorfinances.app.ui.common.formatPercentLabel
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.AppTextButton
import com.gestorfinances.app.ui.common.AppIconButton
import com.gestorfinances.app.ui.budgets.BudgetsUiState
import com.gestorfinances.app.domain.rules.PlanStatus
import com.gestorfinances.app.domain.rules.PlanPart
import com.gestorfinances.app.data.repository.AnalysisRepository
import com.gestorfinances.app.data.repository.AnalysisBreakdownKind
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.produceState
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.ui.budgets.BudgetSheetHost
import com.gestorfinances.app.ui.budgets.BudgetsViewModel
import com.gestorfinances.app.ui.categories.CategoriesUiState
import com.gestorfinances.app.ui.categories.CategoriesViewModel
import com.gestorfinances.app.ui.categories.CategoryArchiveDialog
import com.gestorfinances.app.ui.categories.CategoryFlowDetailState
import com.gestorfinances.app.ui.categories.CategoryFormSheet
import com.gestorfinances.app.ui.categories.label
import com.gestorfinances.app.ui.categories.sortedForDisplay
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DestructiveButton
import com.gestorfinances.app.ui.common.EntityBudgetBar
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SearchField
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.desktop.resources.column_category
import com.gestorfinances.ui.resources.budget_field_total
import com.gestorfinances.ui.resources.category_empty_title
import com.gestorfinances.ui.resources.category_flow_empty
import com.gestorfinances.ui.resources.category_flow_view_analysis
import com.gestorfinances.ui.resources.category_list_add
import com.gestorfinances.ui.resources.category_list_title
import com.gestorfinances.ui.resources.category_search_empty
import com.gestorfinances.ui.resources.category_search_placeholder
import com.gestorfinances.ui.resources.category_section_expense
import com.gestorfinances.ui.resources.category_section_income
import com.gestorfinances.ui.resources.category_spend_month
import com.gestorfinances.ui.resources.category_spend_year
import com.gestorfinances.ui.resources.category_uncategorized_body
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_edit
import com.gestorfinances.ui.resources.common_no_category
import com.gestorfinances.ui.resources.common_retry
import com.gestorfinances.ui.resources.entity_add_movement
import com.gestorfinances.ui.resources.failure_load_categories
import com.gestorfinances.ui.resources.trip_action_budget
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

/**
 * Categories as a tree table over the whole page: this month and this year side by side (where the
 * phone shows one at a time), each one's share of the year, its budget and what it averages a
 * month. A category opens over the whole page with its figures, its months and its movements.
 */
@Composable
fun CategoriesPage(
    viewModel: CategoriesViewModel,
    budgets: BudgetsViewModel,
    analysis: AnalysisRepository,
    dataVersion: Long,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    /** Opens the category in Analysis, in [YearMonth] when one is given. */
    onViewAnalysis: (categoryId: String, YearMonth?) -> Unit,
    onOpenUncategorized: () -> Unit,
    onAddMovement: (categoryId: String) -> Unit,
    onOpenMovement: (MovementSummary) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val budgetState by budgets.state.collectAsState()
    LaunchedEffect(viewModel, dataVersion) { viewModel.onScreenShown() }
    LaunchedEffect(budgets, dataVersion) { budgets.onScreenShown() }
    LaunchedEffect(viewModel, selectedId, dataVersion) { selectedId?.let(viewModel::onCategoryDetailOpened) }
    val detail = state.flowDetail?.takeIf { it.category.id == selectedId }
    val today = remember(dataVersion) { LocalDate.now() }

    if (selectedId == null || detail == null) {
        // Until the opened category has loaded, the table stays.
        CategoryTree(state, budgetState, analysis, dataVersion, today, viewModel, onSelect, onOpenUncategorized)
    } else {
        CategoryPane(
            detail = detail,
            state = state,
            analysis = analysis,
            dataVersion = dataVersion,
            today = today,
            viewModel = viewModel,
            onBack = { onSelect(null) },
            onSelect = onSelect,
            onViewAnalysis = { month -> onViewAnalysis(detail.category.id, month) },
            onBudget = { budgets.editBudgetFor(categoryId = detail.category.id) },
            onAddMovement = { onAddMovement(detail.category.id) },
            onOpenMovement = onOpenMovement,
        )
    }

    CategoryFormSheet(state = state, viewModel = viewModel)
    BudgetSheetHost(viewModel = budgets, onChanged = { selectedId?.let(viewModel::onCategoryDetailOpened) }, onDeleteCommitted = {})
    state.archiveCandidate?.let { candidate ->
        CategoryArchiveDialog(
            candidate = candidate,
            viewModel = viewModel,
            onArchived = { if (selectedId == candidate.category.id) onSelect(null) },
        )
    }
}

private enum class Section { EXPENSE, INCOME }

/** Which columns the window has room for; the rest are dropped rather than squeezed. */
private data class TreeColumns(val share: Boolean, val budget: Boolean, val average: Boolean)

@Composable
private fun CategoryTree(
    state: CategoriesUiState,
    budgetState: BudgetsUiState,
    analysis: AnalysisRepository,
    dataVersion: Long,
    today: LocalDate,
    viewModel: CategoriesViewModel,
    onSelect: (String?) -> Unit,
    onOpenUncategorized: () -> Unit,
) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    var section by remember { mutableStateOf(Section.EXPENSE) }
    var query by remember { mutableStateOf("") }
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    val month = if (section == Section.EXPENSE) state.monthSpend else state.monthIncome
    val year = if (section == Section.EXPENSE) state.yearSpend else state.yearIncome
    val childrenByParent = state.categories.groupBy { it.parentId }
    val parents = state.categories.filter {
        it.parentId == null && when (section) {
            Section.EXPENSE -> it.kind == CategoryKind.EXPENSE || it.kind == CategoryKind.BOTH
            Section.INCOME -> it.kind == CategoryKind.INCOME || it.kind == CategoryKind.BOTH
        }
    }.sortedForDisplay()
    // A parent's figure rolls up its own and its subcategories', as on its page.
    fun rolled(figures: Map<String, Long>, parent: CategoryRecord): Long =
        (figures[parent.id] ?: 0L) + childrenByParent[parent.id].orEmpty().sumOf { figures[it.id] ?: 0L }
    val needle = query.trim()
    // A parent that matches shows all its subcategories; otherwise only the ones that match.
    val visible = parents.mapNotNull { parent ->
        val children = childrenByParent[parent.id].orEmpty().sortedForDisplay()
        when {
            needle.isEmpty() || parent.name.contains(needle, ignoreCase = true) -> parent to children
            else -> children.filter { it.name.contains(needle, ignoreCase = true) }.takeIf { it.isNotEmpty() }?.let { parent to it }
        }
    }
    val withChildren = visible.filter { it.second.isNotEmpty() }.map { it.first.id }.toSet()
    val yearTotal = state.totals?.let { if (section == Section.EXPENSE) it.yearExpenseCents else it.yearIncomeCents } ?: 0L
    // This month's partides, by the category each covers: only while the plan shown is this month's.
    val partides = budgetState.plan?.takeIf { section == Section.EXPENSE && budgetState.selectedMonth == YearMonth.from(today) }?.let { plan ->
        plan.compartments.mapNotNull { budget -> budget.categoryId?.let { id -> plan.plan.compartments[budget.id]?.let { id to it } } }.toMap()
    }.orEmpty()
    // What no category was chosen for: canonical figures, as every other row's.
    val uncategorized by produceState<Pair<Long, Long>?>(null, dataVersion, today) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                fun spent(from: LocalDate, until: LocalDate) = analysis.actualByCategory(from.toString(), until.toString())
                    .filter { it.categoryId == null && it.rowKind == AnalysisBreakdownKind.CATEGORY }
                    .sumOf { it.expenseCents }
                val first = YearMonth.from(today).atDay(1)
                spent(first, first.plusMonths(1)) to spent(LocalDate.of(today.year, 1, 1), LocalDate.of(today.year + 1, 1, 1))
            }.getOrNull()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = TreeColumns(share = maxWidth >= 940.dp, budget = maxWidth >= 780.dp && partides.isNotEmpty(), average = maxWidth >= 640.dp)
        ScrollPage {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(SharedRes.string.category_list_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                SegmentedControl(
                    options = Section.entries,
                    selected = section,
                    label = {
                        stringResource(if (it == Section.EXPENSE) SharedRes.string.category_section_expense else SharedRes.string.category_section_income)
                    },
                    onSelect = { section = it },
                    compact = true,
                )
                PrimaryButton(text = stringResource(SharedRes.string.category_list_add), onClick = viewModel::onAddClicked, leadingIcon = Icons.Outlined.Add)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The search keeps to the left and the fold control to the table's right edge.
                Box(Modifier.weight(1f)) {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(SharedRes.string.category_search_placeholder),
                        modifier = Modifier.widthIn(max = 360.dp),
                    )
                }
                if (withChildren.isNotEmpty()) {
                    val allCollapsed = collapsed.containsAll(withChildren)
                    AppTextButton(onClick = { collapsed = if (allCollapsed) emptySet() else withChildren }) {
                        Text(stringResource(if (allCollapsed) Res.string.categories_expand_all else Res.string.categories_collapse_all))
                    }
                }
            }
            if (state.errorMessage != null) {
                InlineBanner(
                    kind = BannerKind.Error,
                    text = stringResource(SharedRes.string.failure_load_categories),
                    actionLabel = stringResource(SharedRes.string.common_retry),
                    onAction = viewModel::onScreenShown,
                )
            }
            if (state.isLoading) return@ScrollPage
            if (visible.isEmpty()) {
                Text(
                    stringResource(if (needle.isEmpty()) SharedRes.string.category_empty_title else SharedRes.string.category_search_empty),
                    color = muted,
                )
                return@ScrollPage
            }
            val header = MaterialTheme.typography.labelMedium
            // A year in its first months averages over the months it has had.
            val average = { cents: Long -> if (cents > 0L) formatEuroCents(cents / today.monthValue) else "—" }
            FinanceCard(Modifier.fillMaxWidth()) {
                TreeRow(
                    columns = columns,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    name = { Text(stringResource(Res.string.column_category), style = header, color = muted) },
                    share = { Text(stringResource(Res.string.categories_column_share), style = header, color = muted) },
                    budget = { Text(stringResource(Res.string.column_budget), style = header, color = muted) },
                    month = { Text(stringResource(SharedRes.string.category_spend_month), style = header, color = muted) },
                    average = { Text(stringResource(Res.string.recurring_month_average), style = header, color = muted, maxLines = 1) },
                    year = { Text(stringResource(SharedRes.string.category_spend_year), style = header, color = muted) },
                )
                state.totals?.takeIf { needle.isEmpty() }?.let { totals ->
                    val strong = MaterialTheme.typography.titleSmall
                    HorizontalDivider(color = colors.cardBorder)
                    TreeRow(
                        columns = columns,
                        name = { Text(stringResource(SharedRes.string.budget_field_total), style = strong) },
                        month = { Text(formatEuroCents(if (section == Section.EXPENSE) totals.monthExpenseCents else totals.monthIncomeCents), style = strong) },
                        average = { Text(average(yearTotal), style = strong) },
                        year = { Text(formatEuroCents(yearTotal), style = strong) },
                    )
                }
                visible.forEach { (parent, children) ->
                    HorizontalDivider(color = colors.cardBorder)
                    val folded = parent.id in collapsed && needle.isEmpty()
                    CategoryRow(
                        category = parent,
                        columns = columns,
                        monthCents = rolled(month, parent),
                        yearCents = rolled(year, parent),
                        yearTotal = yearTotal,
                        average = average,
                        partida = partides[parent.id],
                        fold = if (children.isEmpty()) null else folded,
                        onFold = { collapsed = if (parent.id in collapsed) collapsed - parent.id else collapsed + parent.id },
                        onClick = { onSelect(parent.id) },
                    )
                    if (!folded) {
                        children.forEach { child ->
                            HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(start = 76.dp))
                            CategoryRow(
                                category = child,
                                columns = columns,
                                monthCents = month[child.id] ?: 0L,
                                yearCents = year[child.id] ?: 0L,
                                yearTotal = yearTotal,
                                average = average,
                                partida = partides[child.id],
                                indent = true,
                                onClick = { onSelect(child.id) },
                            )
                        }
                    }
                }
                if (section == Section.EXPENSE && needle.isEmpty()) {
                    HorizontalDivider(color = colors.cardBorder)
                    val figures = uncategorized
                    TreeRow(
                        columns = columns,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onOpenUncategorized),
                        name = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Spacer(Modifier.width(FOLD_WIDTH))
                                IdentityIconTile(icon = categoryIcon(null), color = categoryColor(null), size = 32.dp)
                                Column {
                                    Text(stringResource(SharedRes.string.common_no_category), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        stringResource(SharedRes.string.category_uncategorized_body),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        share = { figures?.let { ShareBar(it.second, yearTotal, muted) } },
                        month = { Text(figures?.first?.takeIf { it > 0L }?.let(::formatEuroCents) ?: "—", color = if ((figures?.first ?: 0L) > 0L) colors.alert else muted) },
                        average = { Text(average(figures?.second ?: 0L), color = muted) },
                        year = { Text(figures?.second?.takeIf { it > 0L }?.let(::formatEuroCents) ?: "—") },
                    )
                }
            }
        }
    }
}

/** The room the fold arrow takes before a category's mark, kept empty where there is none to fold. */
private val FOLD_WIDTH = 24.dp

/** A row's part of the year's total, as a bar in its own colour and the figure beside it. */
@Composable
private fun ShareBar(cents: Long, total: Long, tone: Color) {
    if (cents <= 0L || total <= 0L) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BudgetProgressBar(fraction = (cents.toFloat() / total).coerceIn(0f, 1f), color = tone, modifier = Modifier.weight(1f))
        Text(
            formatPercentLabel(cents.toFloat() / total),
            Modifier.width(40.dp),
            style = MaterialTheme.typography.bodySmall,
            color = FinanceTheme.colors.mutedText,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun CategoryRow(
    category: CategoryRecord,
    columns: TreeColumns,
    monthCents: Long,
    yearCents: Long,
    yearTotal: Long,
    average: (Long) -> String,
    partida: PlanPart?,
    onClick: () -> Unit,
    indent: Boolean = false,
    /** Whether its subcategories are folded away; null when it has none. */
    fold: Boolean? = null,
    onFold: () -> Unit = {},
) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val figure = if (indent) muted else MaterialTheme.colorScheme.onSurface
    val style = if (indent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    TreeRow(
        columns = columns,
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick),
        name = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (indent) Spacer(Modifier.width(FOLD_WIDTH + 20.dp))
                if (!indent) {
                    Box(Modifier.size(FOLD_WIDTH), contentAlignment = Alignment.Center) {
                        if (fold != null) {
                            Icon(
                                if (fold) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = stringResource(if (fold) SharedRes.string.common_collapsed else SharedRes.string.common_expanded),
                                tint = muted,
                                modifier = Modifier.size(FOLD_WIDTH).clip(RoundedCornerShape(6.dp)).clickable(onClick = onFold).padding(2.dp),
                            )
                        }
                    }
                }
                IdentityIconTile(icon = categoryIcon(category.icon), color = categoryColor(category.color), size = if (indent) 26.dp else 32.dp)
                Text(category.name, Modifier.weight(1f, fill = false), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        share = { ShareBar(yearCents, yearTotal, if (indent) muted.copy(alpha = 0.6f) else themedIdentityColor(categoryColor(category.color))) },
        budget = {
            val planned = partida?.plannedCents
            if (partida != null && planned != null && planned > 0L) {
                val tone = when (partida.status) {
                    PlanStatus.OVER -> colors.debt
                    PlanStatus.MAY_EXCEED, PlanStatus.AHEAD_OF_PACE -> colors.alert
                    else -> colors.income
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BudgetProgressBar(fraction = (partida.actualCents.toFloat() / planned).coerceIn(0f, 1f), color = tone)
                    Text(
                        if (partida.actualCents <= planned) {
                            stringResource(SharedRes.string.budget_remaining, formatEuroCents(planned - partida.actualCents))
                        } else {
                            stringResource(SharedRes.string.budget_over, formatEuroCents(partida.actualCents - planned))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = tone,
                        maxLines = 1,
                    )
                }
            }
        },
        month = { Text(if (monthCents > 0L) formatEuroCents(monthCents) else "—", style = style, color = figure, fontWeight = if (indent) null else FontWeight.Medium) },
        average = { Text(average(yearCents), style = style, color = muted) },
        year = { Text(if (yearCents > 0L) formatEuroCents(yearCents) else "—", style = style, color = figure) },
    )
}

@Composable
private fun TreeRow(
    columns: TreeColumns,
    name: @Composable () -> Unit,
    month: @Composable () -> Unit,
    average: @Composable () -> Unit,
    year: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    share: @Composable () -> Unit = {},
    budget: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1.3f)) { name() }
        if (columns.share) Box(Modifier.weight(1f)) { share() }
        if (columns.budget) Box(Modifier.width(140.dp)) { budget() }
        Box(Modifier.width(104.dp), contentAlignment = Alignment.CenterEnd) { month() }
        if (columns.average) Box(Modifier.width(104.dp), contentAlignment = Alignment.CenterEnd) { average() }
        Box(Modifier.width(112.dp), contentAlignment = Alignment.CenterEnd) { year() }
    }
}

/**
 * A category over the whole page: this month, this year and its monthly average straight on the
 * page with its budget, its subcategories, its last twelve months as bars (a click opens that
 * month in Analysis), and all its movements, scrolling under the rest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPane(
    detail: CategoryFlowDetailState,
    state: CategoriesUiState,
    analysis: AnalysisRepository,
    dataVersion: Long,
    today: LocalDate,
    viewModel: CategoriesViewModel,
    onBack: () -> Unit,
    onSelect: (String?) -> Unit,
    onViewAnalysis: (YearMonth?) -> Unit,
    onBudget: () -> Unit,
    onAddMovement: () -> Unit,
    onOpenMovement: (MovementSummary) -> Unit,
) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val category = detail.category
    val children = state.categories.filter { it.parentId == category.id }.sortedForDisplay()
    val parent = category.parentId?.let { id -> state.categories.firstOrNull { it.id == id } }
    // A category that only takes income is read by what came in; any other, by what was spent.
    val income = category.kind == CategoryKind.INCOME
    val monthFigures = if (income) state.monthIncome else state.monthSpend
    val yearFigures = if (income) state.yearIncome else state.yearSpend
    val monthCents = (monthFigures[category.id] ?: 0L) + children.sumOf { monthFigures[it.id] ?: 0L }
    val yearCents = (yearFigures[category.id] ?: 0L) + children.sumOf { yearFigures[it.id] ?: 0L }
    val ids = remember(category.id, children) { children.map { it.id }.toSet() + category.id }
    // The last twelve months, a canonical figure for each.
    val months = remember(today) { (11 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) } }
    val series by produceState<List<Long>?>(null, ids, income, months, dataVersion) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                months.map { month ->
                    analysis.actualByCategory(month.atDay(1).toString(), month.plusMonths(1).atDay(1).toString())
                        .filter { it.categoryId in ids }
                        .sumOf { if (income) it.incomeCents else it.expenseCents }
                }
            }.getOrNull()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 900.dp
            val actions = @Composable {
                SecondaryButton(text = stringResource(SharedRes.string.category_flow_view_analysis), onClick = { onViewAnalysis(null) })
                SecondaryButton(text = stringResource(SharedRes.string.common_edit), onClick = { viewModel.onEditClicked(category) })
                DestructiveButton(text = stringResource(SharedRes.string.common_archive), onClick = { viewModel.onArchiveClicked(category) })
                PrimaryButton(text = stringResource(SharedRes.string.entity_add_movement), onClick = onAddMovement, leadingIcon = Icons.Outlined.Add)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(SharedRes.string.category_list_title))
                    }
                    IdentityIconTile(icon = categoryIcon(category.icon), color = categoryColor(category.color), size = 48.dp)
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        Text(category.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            parent?.name ?: category.kind.label(),
                            color = muted,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    }
                    if (wide) actions()
                }
                if (!wide) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
        }
        if (detail.errorMessage != null) {
            InlineBanner(
                kind = BannerKind.Error,
                text = stringResource(SharedRes.string.failure_load_categories),
                actionLabel = stringResource(SharedRes.string.common_retry),
                onAction = { viewModel.onCategoryDetailOpened(category.id) },
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val standing = @Composable {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Where it stands, straight on the page: no card around the figures.
                    Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Bottom) {
                        Figure(stringResource(SharedRes.string.category_spend_month)) {
                            MoneyText(cents = monthCents, style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 42.sp))
                        }
                        Figure(stringResource(SharedRes.string.category_spend_year)) { MoneyText(cents = yearCents, style = MaterialTheme.typography.headlineSmall) }
                        Figure(stringResource(Res.string.recurring_month_average)) {
                            MoneyText(cents = yearCents / today.monthValue, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    if (!income) {
                        val evaluation = detail.budgetEvaluation
                        if (evaluation == null) {
                            Box { SecondaryButton(text = stringResource(Res.string.trips_add_budget), onClick = onBudget) }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(SharedRes.string.trip_action_budget), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = muted)
                                    AppTextButton(onClick = onBudget) { Text(stringResource(SharedRes.string.common_edit)) }
                                }
                                EntityBudgetBar(evaluation)
                            }
                        }
                    }
                    if (children.isNotEmpty()) {
                        // Its subcategories with what each came to this year; each opens its own page.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            children.forEach { child ->
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { onSelect(child.id) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(Modifier.size(10.dp).background(categoryColor(child.color), CircleShape))
                                    Text(child.name, color = muted, maxLines = 1)
                                    MoneyText(cents = yearFigures[child.id] ?: 0L, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                                }
                            }
                        }
                    }
                }
            }
            val chart: (@Composable () -> Unit)? = series?.takeIf { values -> values.any { it != 0L } }?.let { values ->
                {
                    val nonZero = values.filter { it != 0L }
                    val average = nonZero.sum() / nonZero.size
                    HoverBars(months = months, values = values, line = average, onMonth = { onViewAnalysis(it) }, current = YearMonth.from(today)) { index ->
                        if (index == null) {
                            Text(stringResource(SharedRes.string.analysis_average, formatEuroCents(average)), style = MaterialTheme.typography.bodySmall, color = muted)
                        } else {
                            Text(formatMonthYear(months[index]).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = muted)
                            Text(formatEuroCents(values[index]), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            // While the months load their room is kept, so nothing shifts when they arrive.
            if ((chart != null || series == null) && maxWidth >= 900.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Box(Modifier.weight(1f)) { standing() }
                    Box(Modifier.width(440.dp)) { chart?.invoke() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    standing()
                    chart?.invoke()
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.people_history), style = MaterialTheme.typography.titleMedium)
            if (detail.entries.isNotEmpty()) Text(detail.entries.size.toString(), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        if (detail.entries.isEmpty()) {
            Text(stringResource(SharedRes.string.category_flow_empty), color = muted)
        } else {
            PartMovements(detail.entries, onOpenMovement, Modifier.weight(1f))
        }
    }
}
