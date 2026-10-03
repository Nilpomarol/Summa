package com.gestorfinances.app.ui.movements

import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.gestorfinances.app.ui.trips.icon
import com.gestorfinances.app.data.repository.supports
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.LinkPill
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.accountIcon
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.dayGroupedRows
import com.gestorfinances.app.ui.common.inPickerHierarchyOrder
import com.gestorfinances.app.ui.theme.categoryColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.di.AppContainer
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.FilterSelectorField
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.RootPageHeader
import com.gestorfinances.app.ui.common.SearchField
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.theme.FinanceTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The app-wide movements ViewModel: the ledger page and the movement sheets that any page can open
 * share it, so it lives as long as the Activity rather than one page visit.
 */
@Composable
fun movementsViewModel(appContainer: AppContainer): MovementsViewModel = viewModel {
    MovementsViewModel(
        movementRepository = appContainer.movementRepository,
        accountRepository = appContainer.accountRepository,
        categoryRepository = appContainer.categoryRepository,
        personRepository = appContainer.personRepository,
        tripRepository = appContainer.tripRepository,
        tagRepository = appContainer.tagRepository,
        splitRepository = appContainer.splitRepository,
        notificationRefresher = appContainer.notificationCoordinator,
        templateRepository = appContainer.templateRepository,
        financialDataRevision = appContainer.financialDataRevision,
    )
}

@Composable
fun MovementsScreen(
    viewModel: MovementsViewModel,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit = viewModel::onAddClicked,
    onDetail: (MovementSummary) -> Unit = viewModel::onDetailClicked,
    onViewRecurring: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    MovementsContent(
        onViewRecurring = onViewRecurring,
        state = state,
        modifier = modifier,
        onFiltersChange = viewModel::onFiltersChanged,
        onClearFilters = viewModel::onClearFiltersClicked,
        onDetail = onDetail,
        onAdd = onAdd,
        onRetry = viewModel::onScreenShown,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun MovementsContent(
    onViewRecurring: () -> Unit,
    state: MovementsUiState,
    modifier: Modifier,
    onFiltersChange: (MovementFilters) -> Unit,
    onClearFilters: () -> Unit,
    onDetail: (MovementSummary) -> Unit,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
) {
    var filtersExpanded by remember { mutableStateOf(false) }
    val visibleMovements = state.visibleMovements
    val filters = state.filters
    val today = remember { LocalDate.now() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
    ) {
        item {
            RootPageHeader(
                title = stringResource(R.string.movement_list_title),
                modifier = Modifier.padding(bottom = 12.dp),
                trailing = { LinkPill(text = stringResource(R.string.recurring_list_title), onClick = onViewRecurring) },
            )
        }
        item {
            val searchDescription = stringResource(R.string.movement_search_hint)
            SearchField(
                query = filters.query,
                onQueryChange = { onFiltersChange(filters.copy(query = it)) },
                placeholder = searchDescription,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    .semantics { contentDescription = searchDescription },
            )
        }
        item {
            // One row, always: the quick type filters on the left, the filter sheet's pill pinned
            // right. The pill is measured first, so under a large font scale the last chip's label
            // ellipsizes instead of the row wrapping or scrolling.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (listOf<MovementType?>(null) + primaryMovementFilterTypes).forEach { type ->
                        val isSelected = filters.type == type && !filters.paidByPersonOnly
                        FinanceFilterChip(
                            selected = isSelected,
                            label = type?.filterLabel() ?: stringResource(R.string.movement_filter_all_types),
                            onClick = { onFiltersChange(filters.copy(type = type, paidByPersonOnly = false)) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.semantics { selected = isSelected },
                        )
                    }
                }
                FilterSheetButton(
                    activeCount = filters.activeFilterCount,
                    onClick = { filtersExpanded = true },
                    onClear = onClearFilters,
                )
            }
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
        state.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_movements,
                    onRetry = onRetry,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        if (!state.isLoading && state.accounts.isEmpty() && state.movements.isEmpty()) {
            item {
                InlineBanner(
                    kind = BannerKind.Info,
                    text = stringResource(R.string.movement_no_accounts_body),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        when {
            state.isLoading -> item {
                Text(stringResource(R.string.movement_loading), color = FinanceTheme.colors.mutedText)
            }
            state.movements.isEmpty() -> item {
                EmptyMovementsCard(onAdd)
            }
            visibleMovements.isEmpty() -> item {
                NoFilteredMovementsCard()
            }
            else -> dayGroupedRows(
                rows = visibleMovements,
                dateOf = { it.date },
                key = { it.id },
                today = today,
            ) { movement, position ->
                MovementListItem(
                    movement = movement,
                    showDate = false,
                    onClick = { onDetail(movement) },
                    position = position,
                )
            }
        }
    }
    if (filtersExpanded) {
        MovementFiltersSheet(
            filters = filters,
            accounts = state.accounts,
            categories = state.categories,
            trips = state.trips,
            tags = state.tags,
            onApply = onFiltersChange,
            onDismiss = { filtersExpanded = false },
        )
    }
}

/**
 * Opens the filter sheet. While its filters narrow the list it fills and widens into a pill with
 * the count and an × that clears them, so the row itself says the list is filtered.
 */
@Composable
private fun FilterSheetButton(
    activeCount: Int,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = activeCount > 0
    val description = if (active) {
        stringResource(R.string.movement_filter_action_accessibility, activeCount)
    } else {
        stringResource(R.string.movement_filter_title)
    }
    Surface(
        modifier = modifier.height(40.dp),
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(onClick = onClick)
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .padding(start = 10.dp, end = if (active) 2.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(if (active) 18.dp else 20.dp))
                if (active) Text(text = activeCount.toString(), style = MaterialTheme.typography.labelLarge)
            }
            if (active) {
                val clearDescription = stringResource(R.string.common_clear_filters)
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClear)
                        .semantics { contentDescription = clearDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovementFiltersSheet(
    filters: MovementFilters,
    accounts: List<AccountSummary>,
    categories: List<CategoryRecord>,
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    onApply: (MovementFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    // Changes belong to this visit to the sheet; dismissing it leaves the ledger untouched.
    var draft by remember { mutableStateOf(filters.withDateValidation()) }
    val today = remember { LocalDate.now() }
    var period by remember { mutableStateOf(filters.periodPreset(today)) }
    var dateField by remember { mutableStateOf<Boolean?>(null) }

    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.movement_filter_title), style = MaterialTheme.typography.titleLarge)
            // Supplied drill-down criteria stay visible, without offering another filter taxonomy.
            val context = draft.contextLabels(tags)
            if (context.isNotEmpty()) {
                Text(context.joinToString(" · "), color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodySmall)
            }
            FormSelect(
                label = stringResource(R.string.movement_filter_account),
                options = listOf(SelectOption(null, stringResource(R.string.movement_filter_all_accounts))) +
                    accounts.map { account ->
                        SelectOption(
                            id = account.id,
                            label = account.name,
                            leading = { SelectIcon(accountFilterIcon(account), account.color) },
                        )
                    },
                selectedId = draft.accountId,
                onSelect = { draft = draft.copy(accountId = it) },
                modifier = Modifier.fillMaxWidth(),
            )
            FormSelect(
                label = stringResource(R.string.movement_filter_category),
                options = categoryFilterOptions(categories, draft.type),
                selectedId = when {
                    draft.uncategorizedOnly -> UNCATEGORIZED_OPTION
                    else -> draft.categoryId
                },
                onSelect = { id ->
                    draft = when (id) {
                        UNCATEGORIZED_OPTION -> draft.copy(categoryId = null, uncategorizedOnly = true)
                        else -> draft.copy(categoryId = id, uncategorizedOnly = false)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (trips.isNotEmpty()) FormSelect(
                label = stringResource(R.string.movement_field_trip),
                options = listOf(SelectOption(null, stringResource(R.string.movement_filter_all_trips))) +
                    trips.map { trip ->
                        SelectOption(trip.id, trip.name, leading = { SelectIcon(trip.type.icon(), trip.color) })
                    },
                selectedId = draft.tripId,
                // A tag can belong to one trip, so changing the trip drops a tag from another.
                onSelect = { id -> draft = draft.copy(tripId = id, tagId = draft.tagId.takeIf { id == null || id == draft.tripId }) },
                modifier = Modifier.fillMaxWidth(),
            )
            val tagOptions = tags.filter { draft.tripId == null || it.tripId == draft.tripId }
            if (tagOptions.isNotEmpty()) FormSelect(
                label = stringResource(R.string.movement_field_tag),
                options = listOf(SelectOption(null, stringResource(R.string.movement_filter_all_tags))) +
                    tagOptions.map { tag ->
                        SelectOption(tag.id, tag.name, leading = { SelectIcon(Icons.Outlined.Sell, tag.color) })
                    },
                selectedId = draft.tagId,
                onSelect = { draft = draft.copy(tagId = it) },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.movement_filter_period),
                    style = MaterialTheme.typography.labelMedium,
                    color = FinanceTheme.colors.mutedText,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MovementPeriodPreset.entries.forEach { option ->
                        FinanceFilterChip(
                            selected = period == option,
                            label = stringResource(option.labelRes),
                            onClick = {
                                period = option
                                draft = draft.withPeriod(option, today).withDateValidation()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.semantics { selected = period == option },
                        )
                    }
                }
            }
            if (period == MovementPeriodPreset.CUSTOM) {
                // Dates are deliberately independent, so open-ended contextual ranges remain valid.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterSelectorField(
                        label = stringResource(R.string.movement_filter_date_from),
                        value = draft.dateFrom.takeIf { it.isNotBlank() }?.let(::formatCompactDate)
                            ?: stringResource(R.string.movement_filter_date_unset),
                        onClick = { dateField = true },
                        active = draft.dateFrom.isNotBlank(),
                        onClear = { draft = draft.copy(dateFrom = "").withDateValidation() },
                        modifier = Modifier.weight(1f),
                    )
                    FilterSelectorField(
                        label = stringResource(R.string.movement_filter_date_to),
                        value = draft.dateTo.takeIf { it.isNotBlank() }?.let(::formatCompactDate)
                            ?: stringResource(R.string.movement_filter_date_unset),
                        onClick = { dateField = false },
                        active = draft.dateTo.isNotBlank(),
                        onClear = { draft = draft.copy(dateTo = "").withDateValidation() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            draft.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton(
                    text = stringResource(R.string.common_clear_filters),
                    onClick = { draft = draft.cleared(); period = MovementPeriodPreset.ALL },
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = stringResource(R.string.common_apply),
                    enabled = draft.errorRes == null,
                    onClick = { onApply(draft); onDismiss() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    dateField?.let { from ->
        FilterDateDialog(
            date = if (from) draft.dateFrom else draft.dateTo,
            onSelect = { date ->
                draft = (if (from) draft.copy(dateFrom = date) else draft.copy(dateTo = date)).withDateValidation()
                dateField = null
            },
            onDismiss = { dateField = null },
        )
    }
}

/** Sentinel option id for "no category", which is a filter of its own rather than a category. */
private const val UNCATEGORIZED_OPTION = "__uncategorized__"

/**
 * Category choices for the filter: grouped under expense and income headers (only the one the
 * quick type filter allows, when it is set), each as the two-level tree the movement form uses.
 * A parent with children is a header, since movements are only ever posted to a leaf.
 */
@Composable
private fun categoryFilterOptions(categories: List<CategoryRecord>, type: MovementType?): List<SelectOption> {
    val all = stringResource(R.string.movement_filter_all_categories)
    val none = stringResource(R.string.common_no_category)
    val sections = listOf(
        MovementType.EXPENSE to stringResource(R.string.movement_filter_expenses),
        MovementType.INCOME to stringResource(R.string.movement_filter_income),
    ).filter { (sectionType, _) -> type == null || type == sectionType }
    return buildList {
        add(SelectOption(null, all))
        add(SelectOption(UNCATEGORIZED_OPTION, none))
        sections.forEach { (sectionType, title) ->
            val compatible = categories.filter { it.supports(sectionType) }
            if (compatible.isEmpty()) return@forEach
            // Upper case keeps the kind apart from a parent category's header beneath it.
            if (sections.size > 1) add(SelectOption(id = "section-$title", label = title.uppercase(), enabled = false))
            val containerIds = compatible.mapNotNull { it.parentId }.toSet()
            compatible.inPickerHierarchyOrder().forEach { (category, indented) ->
                add(
                    SelectOption(
                        id = category.id,
                        label = category.name,
                        leading = { SelectIcon(categoryIcon(category.icon), category.color) },
                        enabled = category.id !in containerIds,
                        indented = indented,
                    ),
                )
            }
        }
    }
}

/** The small tinted identity chip in front of a filter option. */
@Composable
private fun SelectIcon(icon: ImageVector, colorHex: String?) {
    IconChip(icon = icon, contentDescription = null, color = categoryColor(colorHex), size = 26.dp)
}

private fun accountFilterIcon(account: AccountSummary): ImageVector =
    if (account.icon != null) accountIcon(account.icon) else accountTypeIcon(account.type)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDateDialog(date: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = parseDate(date)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = dateState.selectedDateMillis != null,
                onClick = {
                    dateState.selectedDateMillis?.let {
                        onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                },
            ) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    ) { DatePicker(state = dateState) }
}

@Composable
private fun EmptyMovementsCard(onAdd: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.movement_empty_title), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onAdd) { Text(stringResource(R.string.movement_list_add)) }
        }
    }
}

@Composable
private fun NoFilteredMovementsCard() {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.movement_filter_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.movement_filter_empty_body), color = FinanceTheme.colors.mutedText)
        }
    }
}

@Composable
private fun MovementFilters.contextLabels(tags: List<TagSummary>): List<String> = buildList {
    type?.takeIf { it !in primaryMovementFilterTypes }?.let { add(it.label()) }
    if (paidByPersonOnly) add(stringResource(R.string.movement_type_external))
    tagId?.let { id ->
        add(tags.firstOrNull { it.id == id }?.name ?: stringResource(R.string.movement_filter_unknown_value))
    }
    sourceMode?.let {
        add(stringResource(if (it == MovementSourceMode.ACTUAL) R.string.movement_filter_actual_context
            else R.string.movement_filter_flow_context))
    }
    if (oneTimeMode != MovementOneTimeMode.INCLUDE) {
        add(stringResource(if (oneTimeMode == MovementOneTimeMode.EXCLUDE) R.string.analysis_one_time_exclude
            else R.string.analysis_one_time_only))
    }
}

@Composable
private fun MovementType.filterLabel(): String = when (this) {
    MovementType.EXPENSE -> stringResource(R.string.movement_filter_expenses)
    MovementType.INCOME -> stringResource(R.string.movement_filter_income)
    MovementType.TRANSFER -> stringResource(R.string.movement_filter_transfers)
    else -> label()
}
