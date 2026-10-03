package com.gestorfinances.desktop

import com.gestorfinances.app.ui.common.OpenDialogs
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import com.gestorfinances.ui.resources.movement_refunded_badge
import androidx.compose.ui.text.style.TextDecoration
import com.gestorfinances.ui.resources.movement_recurring_badge
import com.gestorfinances.ui.resources.movement_one_time_badge
import com.gestorfinances.desktop.resources.movements_select
import com.gestorfinances.desktop.resources.movements_column_context
import com.gestorfinances.app.ui.theme.themedIdentityColor
import com.gestorfinances.app.ui.theme.categoryColor
import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.supports
import com.gestorfinances.app.ui.common.AppTextButton
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DestructiveButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.SearchField
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.chipVisual
import com.gestorfinances.app.ui.common.formatCompactDateRelative
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.parseIsoDateOrNull
import com.gestorfinances.app.ui.common.signedAmountCents
import com.gestorfinances.app.ui.movements.AppTextField
import com.gestorfinances.app.ui.movements.ArchiveCandidate
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.MovementFilters
import com.gestorfinances.app.ui.movements.MovementOneTimeMode
import com.gestorfinances.app.ui.movements.MovementsUiState
import com.gestorfinances.app.ui.movements.MovementsViewModel
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.movements.buildCategorySelectOptions
import com.gestorfinances.app.ui.movements.formLabel
import com.gestorfinances.app.ui.movements.primaryMovementFilterTypes
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.movementAmountColor
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.desktop.resources.column_account
import com.gestorfinances.desktop.resources.column_amount
import com.gestorfinances.desktop.resources.column_category
import com.gestorfinances.desktop.resources.column_date
import com.gestorfinances.desktop.resources.column_name
import com.gestorfinances.desktop.resources.column_type
import com.gestorfinances.desktop.resources.movements_amount_max
import com.gestorfinances.desktop.resources.movements_amount_min
import com.gestorfinances.desktop.resources.movements_bulk_category
import com.gestorfinances.desktop.resources.movements_bulk_category_mixed
import com.gestorfinances.desktop.resources.movements_bulk_delete_title
import com.gestorfinances.desktop.resources.movements_bulk_deselect
import com.gestorfinances.desktop.resources.movements_count
import com.gestorfinances.desktop.resources.movements_filter_one_time
import com.gestorfinances.desktop.resources.movements_filter_shared
import com.gestorfinances.desktop.resources.movements_selected
import com.gestorfinances.desktop.resources.movements_select_all
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_cancel
import com.gestorfinances.ui.resources.common_clear_filters
import com.gestorfinances.ui.resources.common_no_category
import com.gestorfinances.ui.resources.common_retry
import com.gestorfinances.ui.resources.common_save
import com.gestorfinances.ui.resources.dashboard_budget_of
import com.gestorfinances.ui.resources.failure_load_movements
import com.gestorfinances.ui.resources.movement_archive_confirm_title
import com.gestorfinances.ui.resources.movement_archive_refunds_warning
import com.gestorfinances.ui.resources.movement_archive_revert_due_checkbox
import com.gestorfinances.ui.resources.movement_archive_warning
import com.gestorfinances.ui.resources.movement_empty_title
import com.gestorfinances.ui.resources.movement_field_category
import com.gestorfinances.ui.resources.movement_field_tag
import com.gestorfinances.ui.resources.movement_field_trip
import com.gestorfinances.ui.resources.movement_filter_account
import com.gestorfinances.ui.resources.movement_filter_all_accounts
import com.gestorfinances.ui.resources.movement_filter_all_categories
import com.gestorfinances.ui.resources.movement_filter_all_tags
import com.gestorfinances.ui.resources.movement_filter_all_trips
import com.gestorfinances.ui.resources.movement_filter_all_types
import com.gestorfinances.ui.resources.movement_filter_date_from
import com.gestorfinances.ui.resources.movement_filter_date_to
import com.gestorfinances.ui.resources.movement_filter_empty_body
import com.gestorfinances.ui.resources.movement_filter_empty_title
import com.gestorfinances.ui.resources.movement_filter_title
import com.gestorfinances.ui.resources.movement_list_title
import com.gestorfinances.ui.resources.movement_search_hint
import com.gestorfinances.ui.resources.movement_shared_badge
import com.gestorfinances.ui.resources.recurring_list_title
import java.time.YearMonth
import kotlin.math.abs
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

private enum class SortBy { DATE, NAME, AMOUNT }

/** A choice in a filter that means "movements with no category", beside the categories themselves. */
private const val NO_CATEGORY = "__none__"

/**
 * The ledger as a table: search, a type switch and a panel of filters over it, columns that sort,
 * months as headings, and rows that can be ticked to delete or file several at once. A row opens
 * its detail; see [MovementDialogs].
 */
@Composable
fun MovementsPage(viewModel: MovementsViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.onScreenShown() }
    val filters = state.filters
    var filtersOpen by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(SortBy.DATE) }
    var ascending by remember { mutableStateOf(false) }
    // Ticking rows is a mode of its own: the table stays plain until it is asked for.
    var selecting by remember { mutableStateOf(false) }
    var ticked by remember { mutableStateOf(emptySet<String>()) }
    val visible = state.visibleMovements
    val sorted = remember(visible, sortBy, ascending) {
        val ordered = when (sortBy) {
            // The ledger arrives newest first.
            SortBy.DATE -> if (ascending) visible.asReversed() else visible
            SortBy.NAME -> visible.sortedBy { (it.name ?: it.payee).orEmpty().lowercase() }.let { if (ascending) it else it.asReversed() }
            SortBy.AMOUNT -> visible.sortedBy { it.amountCents }.let { if (ascending) it else it.asReversed() }
        }
        ordered
    }
    val selected = remember(visible, ticked) { visible.filter { it.id in ticked } }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(SharedRes.string.movement_list_title), style = MaterialTheme.typography.headlineSmall)
                if (!state.isLoading) {
                    Text(
                        stringResource(Res.string.movements_count, visible.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FinanceTheme.colors.mutedText,
                    )
                }
            }
            SearchField(
                query = filters.query,
                onQueryChange = { viewModel.onFiltersChanged(filters.copy(query = it)) },
                placeholder = stringResource(SharedRes.string.movement_search_hint),
                modifier = Modifier.width(280.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val allTypes = stringResource(SharedRes.string.movement_filter_all_types)
            SegmentedControl(
                options = listOf<MovementType?>(null) + primaryMovementFilterTypes,
                selected = filters.type,
                label = { it?.formLabel() ?: allTypes },
                onSelect = { viewModel.onFiltersChanged(filters.copy(type = it)) },
                compact = true,
            )
            Spacer(Modifier.weight(1f))
            if (filters.activeFilterCount > 0) {
                AppTextButton(onClick = viewModel::onClearFiltersClicked) { Text(stringResource(SharedRes.string.common_clear_filters)) }
            }
            FinanceFilterChip(
                selected = selecting,
                label = stringResource(Res.string.movements_select),
                onClick = {
                    selecting = !selecting
                    ticked = emptySet()
                },
                trailingIcon = Icons.Outlined.Checklist,
            )
            FinanceFilterChip(
                selected = filtersOpen || filters.activeFilterCount > 0,
                label = stringResource(SharedRes.string.movement_filter_title) +
                    if (filters.activeFilterCount > 0) " · ${filters.activeFilterCount}" else "",
                onClick = { filtersOpen = !filtersOpen },
                trailingIcon = Icons.Outlined.FilterList,
            )
        }
        AnimatedVisibility(
            visible = filtersOpen,
            enter = expandVertically(tween(FILTER_PANEL_MILLIS)) + fadeIn(tween(FILTER_PANEL_MILLIS)),
            exit = shrinkVertically(tween(FILTER_PANEL_MILLIS)) + fadeOut(tween(FILTER_PANEL_MILLIS)),
        ) {
            FilterPanel(state, viewModel::onFiltersChanged)
        }
        if (selected.isNotEmpty()) {
            BulkBar(
                state = state,
                selected = selected,
                onDeselect = { ticked = emptySet() },
                onDelete = {
                    viewModel.onArchiveMany(selected)
                    ticked = emptySet()
                },
                onCategory = { type, categoryId ->
                    viewModel.onCategoryChangedForMany(selected, type, categoryId)
                    ticked = emptySet()
                },
            )
        }
        when {
            state.errorMessage != null -> InlineBanner(
                kind = BannerKind.Error,
                text = stringResource(SharedRes.string.failure_load_movements),
                actionLabel = stringResource(SharedRes.string.common_retry),
                onAction = viewModel::onScreenShown,
            )
            state.isLoading -> Unit
            visible.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val narrowed = state.movements.isNotEmpty()
                Text(
                    stringResource(if (narrowed) SharedRes.string.movement_filter_empty_title else SharedRes.string.movement_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                if (narrowed) Text(stringResource(SharedRes.string.movement_filter_empty_body), color = FinanceTheme.colors.mutedText)
            }
            else -> MovementTable(
                movements = sorted,
                monthHeadings = sortBy == SortBy.DATE,
                sortBy = sortBy,
                ascending = ascending,
                onSort = { column ->
                    if (sortBy == column) ascending = !ascending else {
                        sortBy = column
                        ascending = column == SortBy.NAME
                    }
                },
                selecting = selecting,
                ticked = ticked,
                onTick = { id -> ticked = if (id in ticked) ticked - id else ticked + id },
                onTickAll = { ticked = if (selected.size == visible.size) emptySet() else visible.mapTo(HashSet()) { it.id } },
                onOpen = viewModel::onDetailClicked,
                onDelete = viewModel::onArchiveClicked,
            )
        }
    }
}

private const val FILTER_PANEL_MILLIS = 140

/** Every way the ledger can be narrowed, all at once: they add up. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(state: MovementsUiState, onChange: (MovementFilters) -> Unit) {
    val filters = state.filters
    val field = Modifier.width(190.dp)
    FinanceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val allTypes = stringResource(SharedRes.string.movement_filter_all_types)
                val types = MovementType.entries.map { SelectOption(id = it.name, label = it.label()) }
                FormSelect(
                    label = stringResource(Res.string.column_type),
                    options = listOf(SelectOption(id = null, label = allTypes)) + types,
                    selectedId = filters.type?.name,
                    onSelect = { id -> onChange(filters.copy(type = id?.let(MovementType::valueOf))) },
                    modifier = field,
                    placeholder = allTypes,
                )
                val allAccounts = stringResource(SharedRes.string.movement_filter_all_accounts)
                FormSelect(
                    label = stringResource(SharedRes.string.movement_filter_account),
                    options = listOf(SelectOption(id = null, label = allAccounts)) + state.accounts.map { SelectOption(id = it.id, label = it.name) },
                    selectedId = filters.accountId,
                    onSelect = { onChange(filters.copy(accountId = it)) },
                    modifier = field,
                    placeholder = allAccounts,
                )
                val allCategories = stringResource(SharedRes.string.movement_filter_all_categories)
                val noCategory = stringResource(SharedRes.string.common_no_category)
                val categories = remember(state.categories, allCategories, noCategory) {
                    // The picker's own list, whose first entry stands for "none": here it is "all", and "none" is a choice of its own.
                    buildCategorySelectOptions(state.categories, allCategories).let {
                        it.take(1) + SelectOption(id = NO_CATEGORY, label = noCategory) + it.drop(1)
                    }
                }
                FormSelect(
                    label = stringResource(SharedRes.string.movement_field_category),
                    options = categories,
                    selectedId = if (filters.uncategorizedOnly) NO_CATEGORY else filters.categoryId,
                    onSelect = { id ->
                        onChange(filters.copy(categoryId = id.takeIf { it != NO_CATEGORY }, categoryIds = null, uncategorizedOnly = id == NO_CATEGORY))
                    },
                    modifier = field,
                    placeholder = allCategories,
                )
                val allTrips = stringResource(SharedRes.string.movement_filter_all_trips)
                FormSelect(
                    label = stringResource(SharedRes.string.movement_field_trip),
                    options = listOf(SelectOption(id = null, label = allTrips)) + state.trips.map { SelectOption(id = it.id, label = it.name) },
                    selectedId = filters.tripId,
                    onSelect = { onChange(filters.copy(tripId = it)) },
                    modifier = field,
                    placeholder = allTrips,
                )
                val allTags = stringResource(SharedRes.string.movement_filter_all_tags)
                FormSelect(
                    label = stringResource(SharedRes.string.movement_field_tag),
                    options = listOf(SelectOption(id = null, label = allTags)) + state.tags.map { SelectOption(id = it.id, label = it.name) },
                    selectedId = filters.tagId,
                    onSelect = { onChange(filters.copy(tagId = it)) },
                    modifier = field,
                    placeholder = allTags,
                )
                FormDatePicker(
                    label = stringResource(SharedRes.string.movement_filter_date_from),
                    date = filters.dateFrom,
                    onDateChange = { onChange(filters.copy(dateFrom = it)) },
                    modifier = field,
                    onClear = { onChange(filters.copy(dateFrom = "")) },
                )
                FormDatePicker(
                    label = stringResource(SharedRes.string.movement_filter_date_to),
                    date = filters.dateTo,
                    onDateChange = { onChange(filters.copy(dateTo = it)) },
                    modifier = field,
                    onClear = { onChange(filters.copy(dateTo = "")) },
                )
                AppTextField(
                    value = filters.amountMin,
                    onValueChange = { onChange(filters.copy(amountMin = it)) },
                    label = { Text(stringResource(Res.string.movements_amount_min)) },
                    prefix = { Text("€") },
                    singleLine = true,
                    modifier = field,
                )
                AppTextField(
                    value = filters.amountMax,
                    onValueChange = { onChange(filters.copy(amountMax = it)) },
                    label = { Text(stringResource(Res.string.movements_amount_max)) },
                    prefix = { Text("€") },
                    singleLine = true,
                    modifier = field,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FinanceFilterChip(
                    selected = filters.sharedOnly,
                    label = stringResource(Res.string.movements_filter_shared),
                    onClick = { onChange(filters.copy(sharedOnly = !filters.sharedOnly)) },
                )
                FinanceFilterChip(
                    selected = filters.recurringOnly,
                    label = stringResource(SharedRes.string.recurring_list_title),
                    onClick = { onChange(filters.copy(recurringOnly = !filters.recurringOnly)) },
                )
                val oneTimeOnly = filters.oneTimeMode == MovementOneTimeMode.ONLY
                FinanceFilterChip(
                    selected = oneTimeOnly,
                    label = stringResource(Res.string.movements_filter_one_time),
                    onClick = { onChange(filters.copy(oneTimeMode = if (oneTimeOnly) MovementOneTimeMode.INCLUDE else MovementOneTimeMode.ONLY)) },
                )
            }
            filters.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** What can be done to the ticked rows together. */
@Composable
private fun BulkBar(
    state: MovementsUiState,
    selected: List<MovementSummary>,
    onDeselect: () -> Unit,
    onDelete: () -> Unit,
    onCategory: (MovementType, String?) -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    var choosingCategory by remember { mutableStateOf(false) }
    // A category belongs to expenses or to income: the ticked rows must all be one of the two.
    val type = selected.map { it.type }.distinct().singleOrNull()?.takeIf { it == MovementType.EXPENSE || it == MovementType.INCOME }
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.movements_selected, selected.size),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        if (type == null) {
            Text(
                stringResource(Res.string.movements_bulk_category_mixed),
                style = MaterialTheme.typography.bodySmall,
                color = FinanceTheme.colors.mutedText,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        AppTextButton(onClick = onDeselect) { Text(stringResource(Res.string.movements_bulk_deselect)) }
        SecondaryButton(text = stringResource(Res.string.movements_bulk_category), onClick = { choosingCategory = true }, enabled = type != null)
        DestructiveButton(text = stringResource(SharedRes.string.common_archive), onClick = { confirmingDelete = true })
    }
    if (confirmingDelete) {
        OpenDialogs.Track()
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(Res.string.movements_bulk_delete_title, selected.size)) },
            text = { Text(stringResource(SharedRes.string.movement_archive_warning)) },
            confirmButton = {
                AppTextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) { Text(stringResource(SharedRes.string.common_archive), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { AppTextButton(onClick = { confirmingDelete = false }) { Text(stringResource(SharedRes.string.common_cancel)) } },
        )
    }
    if (choosingCategory && type != null) {
        var categoryId by remember { mutableStateOf<String?>(null) }
        val noCategory = stringResource(SharedRes.string.common_no_category)
        OpenDialogs.Track()
        AlertDialog(
            onDismissRequest = { choosingCategory = false },
            title = { Text(stringResource(Res.string.movements_bulk_category)) },
            text = {
                FormSelect(
                    label = stringResource(SharedRes.string.movement_field_category),
                    options = buildCategorySelectOptions(state.categories.filter { it.supports(type) }, noCategory),
                    selectedId = categoryId,
                    onSelect = { categoryId = it },
                    placeholder = noCategory,
                )
            },
            confirmButton = {
                AppTextButton(onClick = {
                    choosingCategory = false
                    onCategory(type, categoryId)
                }) { Text(stringResource(SharedRes.string.common_save)) }
            },
            dismissButton = { AppTextButton(onClick = { choosingCategory = false }) { Text(stringResource(SharedRes.string.common_cancel)) } },
        )
    }
}

@Composable
private fun MovementTable(
    movements: List<MovementSummary>,
    monthHeadings: Boolean,
    sortBy: SortBy,
    ascending: Boolean,
    onSort: (SortBy) -> Unit,
    selecting: Boolean,
    ticked: Set<String>,
    onTick: (String) -> Unit,
    onTickAll: () -> Unit,
    onOpen: (MovementSummary) -> Unit,
    onDelete: (MovementSummary) -> Unit,
) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    // With the months as headings, each movement knows whether it opens one.
    val rows = remember(movements, monthHeadings) {
        var month: YearMonth? = null
        movements.map { movement ->
            val own = parseIsoDateOrNull(movement.date)?.let(YearMonth::from)
            val heading = own.takeIf { monthHeadings && it != month }
            month = own
            heading to movement
        }
    }
    BoxWithConstraints {
        val columns = LedgerColumns(tick = selecting, category = maxWidth >= 860.dp, account = maxWidth >= 700.dp, context = maxWidth >= 980.dp)
        FinanceCard {
            TableRow(
                columns = columns,
                height = 36.dp,
                tick = { Tick(checked = ticked.isNotEmpty() && ticked.size >= movements.size, onChange = onTickAll, label = stringResource(Res.string.movements_select_all)) },
                leading = {},
                name = { SortHeading(stringResource(Res.string.column_name), SortBy.NAME, sortBy, ascending, onSort) },
                category = { Text(stringResource(Res.string.column_category), style = MaterialTheme.typography.labelMedium, color = muted) },
                account = { Text(stringResource(Res.string.column_account), style = MaterialTheme.typography.labelMedium, color = muted) },
                context = { Text(stringResource(Res.string.movements_column_context), style = MaterialTheme.typography.labelMedium, color = muted) },
                date = { SortHeading(stringResource(Res.string.column_date), SortBy.DATE, sortBy, ascending, onSort) },
                amount = { SortHeading(stringResource(Res.string.column_amount), SortBy.AMOUNT, sortBy, ascending, onSort) },
                trailing = {},
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            )
            HorizontalDivider(color = colors.cardBorder)
            ScrollList {
                items(rows, key = { (_, movement) -> movement.id }) { (heading, movement) ->
                    if (heading != null) {
                        MonthHeading(heading)
                    } else {
                        HorizontalDivider(color = colors.cardBorder)
                    }
                    MovementRow(
                        movement = movement,
                        columns = columns,
                        ticked = movement.id in ticked,
                        onTick = { onTick(movement.id) },
                        onOpen = { onOpen(movement) },
                        onDelete = { onDelete(movement) },
                    )
                }
            }
        }
    }
}

/** Which columns the window has room for; the rest are dropped rather than squeezed. */
internal data class LedgerColumns(
    val tick: Boolean,
    val category: Boolean,
    val account: Boolean,
    val context: Boolean,
    /** A further column after the amount, for an account's statement: the balance each movement left. */
    val balance: Boolean = false,
    val delete: Boolean = true,
)

/**
 * A movement as a row of the ledger table, here and wherever else a ledger is shown. Seen from one
 * account ([seenFrom]), a transfer names the account at its other end where the trip and tag go.
 */
@Composable
internal fun MovementRow(
    movement: MovementSummary,
    columns: LedgerColumns,
    ticked: Boolean,
    onTick: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    seenFrom: String? = null,
    /** On a trip's own page its name on every row says nothing: only the tag is shown. */
    withinTrip: Boolean = false,
    /** Likewise on a tag's own page: only the trip is shown. */
    withinTag: Boolean = false,
    /** And on a recurring item's own page, where every payment is recurring, the mark for it. */
    withinRecurring: Boolean = false,
    amount: @Composable () -> Unit = { Amount(movement) },
    balance: @Composable () -> Unit = {},
) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val typeLabel = movement.type.label()
    val (icon, tone) = movement.chipVisual()
    // Expenses and income are told by their category and their amount; anything else says what it is.
    val plain = movement.type == MovementType.EXPENSE || movement.type == MovementType.INCOME
    val typeColor = when (movement.type) {
        MovementType.TRANSFER -> colors.transfer
        MovementType.SETTLEMENT -> colors.settlement
        MovementType.REFUND -> colors.refund
        else -> colors.shared
    }
    TableRow(
        columns = columns,
        height = 48.dp,
        modifier = Modifier
            .background(if (ticked) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onOpen),
        tick = { Tick(checked = ticked, onChange = onTick, label = null) },
        leading = { IdentityIconTile(icon = icon, color = tone, size = 28.dp) },
        name = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    movement.name ?: movement.payee ?: typeLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 2.dp),
                )
                MovementMarkers(movement, recurring = !withinRecurring)
            }
        },
        // A transfer has no trip or tag: its two accounts take that room too.
        wideAccount = movement.destinationAccountName != null,
        category = {
            if (plain) {
                Text(
                    movement.categoryName ?: stringResource(SharedRes.string.common_no_category),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (movement.categoryName == null) colors.subtleText else muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                // Where a category would be, what kind of movement this is, in that kind's colour.
                Text(typeLabel, style = MaterialTheme.typography.bodyMedium, color = typeColor, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        },
        account = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AccountName(movement.accountName ?: movement.paidByPersonName, movement.accountColor.takeIf { movement.accountName != null }, Modifier.weight(1f, fill = false))
                movement.destinationAccountName?.let {
                    Text("→", style = MaterialTheme.typography.bodyMedium, color = muted)
                    AccountName(it, movement.destinationAccountColor, Modifier.weight(1f, fill = false))
                }
            }
        },
        context = {
            val other = if (seenFrom != null && movement.destinationAccountId != null) {
                if (movement.accountId == seenFrom) "→" to (movement.destinationAccountName to movement.destinationAccountColor)
                else "←" to (movement.accountName to movement.accountColor)
            } else {
                null
            }
            if (other != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(other.first, style = MaterialTheme.typography.bodyMedium, color = muted)
                    AccountName(other.second.first, other.second.second)
                }
            } else Text(
                listOfNotNull(movement.tripName.takeIf { !withinTrip }, movement.tagName.takeIf { !withinTag }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        date = { Text(formatCompactDateRelative(movement.date), style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 1) },
        amount = amount,
        balance = balance,
        trailing = {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(SharedRes.string.common_archive), tint = muted, modifier = Modifier.size(16.dp))
            }
        },
    )
}

/**
 * As on the phone: what a shared movement, or one somebody else paid, came to for the owner leads,
 * with the whole amount beneath it when the two differ.
 */
@Composable
private fun Amount(movement: MovementSummary) {
    val colors = FinanceTheme.colors
    val style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
    val sharedExpense = movement.isShared && movement.type == MovementType.EXPENSE
    val sharedIncome = movement.isShared && movement.type == MovementType.INCOME
    if (!sharedExpense && !sharedIncome && !movement.paidByPerson) {
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(
                cents = movement.signedAmountCents(),
                color = colors.movementAmountColor(movement),
                style = style,
                signed = movement.type == MovementType.INCOME,
            )
            // What it cost before part of it came back.
            if (movement.refundedCents > 0L) {
                Text(
                    formatEuroCents(-movement.amountCents),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
        }
        return
    }
    val share = when {
        movement.paidByPerson -> -movement.netUserShareCents.coerceAtLeast(0L)
        sharedIncome -> movement.userShareCents
        else -> -movement.netUserShareCents
    }
    Column(horizontalAlignment = Alignment.End) {
        MoneyText(
            cents = share,
            color = when {
                movement.paidByPerson -> colors.debt
                sharedIncome -> colors.income
                else -> colors.shared
            },
            style = style,
            signed = sharedIncome,
        )
        if (abs(share) != movement.netAmountCents) {
            Text(
                stringResource(SharedRes.string.dashboard_budget_of, formatEuroCents(movement.netAmountCents)),
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText,
            )
        }
    }
}

/** A month as a section's title, not a row. */
@Composable
internal fun MonthHeading(month: YearMonth) = SectionHeading(formatMonthYear(month))

/** A table section's title, not a row: small capitals in the brand's colour over open space. */
@Composable
internal fun SectionHeading(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 8.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), thickness = 2.dp)
}

/** What sets a movement apart, beside its name: shared, recurring, extraordinary. */
@Composable
internal fun MovementMarkers(movement: MovementSummary, recurring: Boolean = true) {
    val colors = FinanceTheme.colors
    if (movement.isShared) Marker(Icons.Outlined.Group, stringResource(SharedRes.string.movement_shared_badge), colors.shared)
    if (movement.isRecurring && recurring) Marker(Icons.Outlined.Autorenew, stringResource(SharedRes.string.movement_recurring_badge), MaterialTheme.colorScheme.primary)
    if (movement.isOneTime) Marker(Icons.Outlined.NewReleases, stringResource(SharedRes.string.movement_one_time_badge), colors.alert)
    if (movement.refundedCents > 0L) {
        Marker(Icons.AutoMirrored.Outlined.AssignmentReturn, stringResource(SharedRes.string.movement_refunded_badge, formatEuroCents(movement.refundedCents)), colors.refund)
    }
}

/** An account by name behind a dot of its own colour. */
@Composable
private fun AccountName(name: String?, colorHex: String?, modifier: Modifier = Modifier) {
    if (name == null) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (colorHex != null) Box(Modifier.size(8.dp).background(themedIdentityColor(categoryColor(colorHex)), CircleShape))
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** What sets a movement apart (shared, recurring, extraordinary) as a small tinted mark that names itself under the pointer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Marker(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    TooltipArea(
        tooltip = {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder), shadowElevation = 4.dp) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        },
    ) {
        Box(Modifier.size(22.dp).background(color.copy(alpha = 0.16f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun Tick(checked: Boolean, onChange: () -> Unit, label: String?) {
    // The row behind opens the movement; the tick only ticks.
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = label, onClick = onChange), contentAlignment = Alignment.Center) {
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SortHeading(label: String, column: SortBy, sortBy: SortBy, ascending: Boolean, onSort: (SortBy) -> Unit) {
    val active = sortBy == column
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).clickable { onSort(column) }.padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onSurface else FinanceTheme.colors.mutedText,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
        )
        if (active) {
            Icon(
                if (ascending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

private const val ACCOUNT_WEIGHT = 1.3f
private const val CONTEXT_WEIGHT = 1.2f

@Composable
internal fun TableRow(
    columns: LedgerColumns,
    height: androidx.compose.ui.unit.Dp,
    tick: @Composable () -> Unit,
    leading: @Composable () -> Unit,
    name: @Composable () -> Unit,
    category: @Composable () -> Unit,
    account: @Composable () -> Unit,
    context: @Composable () -> Unit,
    date: @Composable () -> Unit,
    amount: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    wideAccount: Boolean = false,
    balance: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().height(height).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (columns.tick) Box(Modifier.width(28.dp)) { tick() }
        Box(Modifier.width(28.dp)) { leading() }
        Box(Modifier.weight(2f)) { name() }
        if (columns.category) Box(Modifier.weight(1.1f)) { category() }
        if (columns.account && columns.context && wideAccount) {
            // The two columns as one; the empty spacer stands for the gap they had between them,
            // so the columns after stay in line with the other rows'.
            Box(Modifier.weight(ACCOUNT_WEIGHT + CONTEXT_WEIGHT)) { account() }
            Spacer(Modifier.width(0.dp))
        } else {
            if (columns.account) Box(Modifier.weight(ACCOUNT_WEIGHT)) { account() }
            if (columns.context) Box(Modifier.weight(CONTEXT_WEIGHT)) { context() }
        }
        Box(Modifier.width(88.dp)) { date() }
        Box(Modifier.width(128.dp), contentAlignment = Alignment.CenterEnd) { amount() }
        if (columns.balance) Box(Modifier.width(104.dp), contentAlignment = Alignment.CenterEnd) { balance() }
        // Without the last column the figures would run under the scrollbar: they keep clear of it.
        if (columns.delete) Box(Modifier.width(28.dp)) { trailing() } else Spacer(Modifier.width(0.dp))
    }
}

@Composable
internal fun ArchiveConfirmation(
    candidate: ArchiveCandidate,
    onDismiss: () -> Unit,
    onConfirm: (revertDueDate: Boolean) -> Unit,
) {
    var revertDueDate by remember(candidate) { mutableStateOf(false) }
    OpenDialogs.Track()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(SharedRes.string.movement_archive_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(SharedRes.string.movement_archive_warning))
                if (candidate.activeRefundCount > 0) {
                    Text(
                        pluralStringResource(
                            SharedRes.plurals.movement_archive_refunds_warning,
                            candidate.activeRefundCount,
                            candidate.activeRefundCount,
                        ),
                    )
                }
                if (candidate.revertibleTemplateId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = revertDueDate, onCheckedChange = { revertDueDate = it })
                        Text(
                            stringResource(
                                SharedRes.string.movement_archive_revert_due_checkbox,
                                candidate.movement.name ?: candidate.movement.payee ?: candidate.movement.type.label(),
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = { onConfirm(revertDueDate) }) {
                Text(stringResource(SharedRes.string.common_archive), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { AppTextButton(onClick = onDismiss) { Text(stringResource(SharedRes.string.common_cancel)) } },
    )
}

/** A short ledger for a detail pane: date, what it was, its account, and its amount. A row opens the movement. */
@Composable
internal fun MovementMiniTable(movements: List<MovementSummary>, onOpen: (MovementSummary) -> Unit) {
    val muted = FinanceTheme.colors.mutedText
    FinanceCard(Modifier.fillMaxWidth()) {
        movements.forEachIndexed { index, movement ->
            if (index > 0) HorizontalDivider(color = FinanceTheme.colors.cardBorder)
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(movement) }.padding(horizontal = 16.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatCompactDateRelative(movement.date), Modifier.width(84.dp), color = muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    movement.name ?: movement.payee ?: movement.type.label(),
                    Modifier.weight(2f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    movement.accountName ?: movement.paidByPersonName.orEmpty(),
                    Modifier.weight(1f),
                    color = muted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MoneyText(
                    cents = movement.signedAmountCents(),
                    color = FinanceTheme.colors.movementAmountColor(movement),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
