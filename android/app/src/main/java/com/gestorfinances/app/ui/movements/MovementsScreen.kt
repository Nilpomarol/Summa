package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.TopBarIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import com.gestorfinances.app.ui.common.FilterSelectorField
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@Composable
fun MovementsScreen(
    viewModel: MovementsViewModel,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit = viewModel::onAddClicked,
    onDetail: (MovementSummary) -> Unit = viewModel::onDetailClicked,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    MovementsContent(
        state = state,
        modifier = modifier,
        onFiltersChange = viewModel::onFiltersChanged,
        onClearFilters = viewModel::onClearFiltersClicked,
        onDetail = onDetail,
        onAdd = onAdd,
    )
}

private data class ActiveMovementFilterChip(
    val label: String,
    val onRemove: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveMovementFilterRow(
    filters: MovementFilters,
    accounts: List<AccountSummary>,
    categories: List<CategoryRecord>,
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    onFiltersChange: (MovementFilters) -> Unit,
) {
    val chips = buildList {
        filters.accountId?.let { accountId ->
            add(
                ActiveMovementFilterChip(
                    label = stringResource(
                        R.string.movement_filter_chip_account,
                        accounts.firstOrNull { it.id == accountId }?.name
                            ?: stringResource(R.string.movement_filter_unknown_value),
                    ),
                    onRemove = { onFiltersChange(filters.copy(accountId = null)) },
                ),
            )
        }
        if (filters.categoryId != null || filters.uncategorizedOnly) {
            val categoryName = if (filters.uncategorizedOnly) {
                stringResource(R.string.common_no_category)
            } else {
                categories.firstOrNull { it.id == filters.categoryId }?.name
                    ?: stringResource(R.string.movement_filter_unknown_value)
            }
            add(
                ActiveMovementFilterChip(
                    label = stringResource(R.string.movement_filter_chip_category, categoryName),
                    onRemove = {
                        onFiltersChange(filters.copy(categoryId = null, uncategorizedOnly = false))
                    },
                ),
            )
        }
        filters.tripId?.let { tripId ->
            add(
                ActiveMovementFilterChip(
                    label = stringResource(
                        R.string.movement_filter_chip_trip,
                        trips.firstOrNull { it.id == tripId }?.name
                            ?: stringResource(R.string.movement_filter_unknown_value),
                    ),
                    onRemove = { onFiltersChange(filters.copy(tripId = null, tagId = null)) },
                ),
            )
        }
        filters.tagId?.let { tagId ->
            add(
                ActiveMovementFilterChip(
                    label = stringResource(
                        R.string.movement_filter_chip_tag,
                        tags.firstOrNull { it.id == tagId }?.name
                            ?: stringResource(R.string.movement_filter_unknown_value),
                    ),
                    onRemove = { onFiltersChange(filters.copy(tagId = null)) },
                ),
            )
        }
        if (filters.dateFrom.isNotBlank() || filters.dateTo.isNotBlank()) {
            add(
                ActiveMovementFilterChip(
                    label = stringResource(R.string.movement_filter_chip_period, filters.formattedPeriod()),
                    onRemove = { onFiltersChange(filters.copy(dateFrom = "", dateTo = "")) },
                ),
            )
        }
        filters.sourceMode?.let { sourceMode ->
            val sourceLabel = when (sourceMode) {
                MovementSourceMode.ACTUAL -> stringResource(R.string.analysis_mode_actual)
                MovementSourceMode.FLOW -> stringResource(R.string.analysis_mode_flow)
            }
            add(
                ActiveMovementFilterChip(
                    label = stringResource(R.string.movement_filter_chip_source, sourceLabel),
                    onRemove = { onFiltersChange(filters.copy(sourceMode = null)) },
                ),
            )
        }
        filters.categoryNature?.let { nature ->
            val natureLabel = when (nature) {
                CategoryNature.FIXED -> stringResource(R.string.analysis_filter_fixed)
                CategoryNature.VARIABLE -> stringResource(R.string.analysis_filter_variable)
            }
            add(
                ActiveMovementFilterChip(
                    label = stringResource(R.string.movement_filter_chip_nature, natureLabel),
                    onRemove = { onFiltersChange(filters.copy(categoryNature = null)) },
                ),
            )
        }
        when (filters.oneTimeMode) {
            MovementOneTimeMode.INCLUDE -> Unit
            MovementOneTimeMode.EXCLUDE,
            MovementOneTimeMode.ONLY,
            -> add(
                ActiveMovementFilterChip(
                    label = stringResource(
                        R.string.movement_filter_chip_one_time,
                        when (filters.oneTimeMode) {
                            MovementOneTimeMode.EXCLUDE -> stringResource(R.string.analysis_one_time_exclude)
                            MovementOneTimeMode.ONLY -> stringResource(R.string.analysis_one_time_only)
                            MovementOneTimeMode.INCLUDE -> error("unreachable")
                        },
                    ),
                    onRemove = {
                        onFiltersChange(filters.copy(oneTimeMode = MovementOneTimeMode.INCLUDE))
                    },
                ),
            )
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            val removeDescription = stringResource(
                R.string.movement_filter_remove_named,
                chip.label,
            )
            AssistChip(
                onClick = chip.onRemove,
                label = { Text(chip.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier.semantics {
                    contentDescription = removeDescription
                },
            )
        }
    }
}

@Composable
private fun MovementsContent(
    state: MovementsUiState,
    modifier: Modifier,
    onFiltersChange: (MovementFilters) -> Unit,
    onClearFilters: () -> Unit,
    onDetail: (MovementSummary) -> Unit,
    onAdd: () -> Unit,
) {
    var filtersExpanded by remember { mutableStateOf(false) }
    val visibleMovements = state.visibleMovements
    val activeFilterCount = state.filters.activeFilterCount

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.movement_list_title),
                trailing = {
                    if (state.accounts.isNotEmpty()) {
                        Box {
                            TopBarIconButton(
                                icon = Icons.Outlined.Tune,
                                contentDescription = if (activeFilterCount == 0) {
                                    stringResource(R.string.movement_filter_title)
                                } else {
                                    stringResource(
                                        R.string.movement_filter_action_accessibility,
                                        activeFilterCount,
                                    )
                                },
                                onClick = { filtersExpanded = !filtersExpanded },
                            )
                            if (activeFilterCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-4).dp, y = 4.dp)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                ) {
                                    Text(
                                        text = activeFilterCount.toString(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }

        state.errorMessage?.let { message ->
            item {
                InlineBanner(kind = BannerKind.Error, text = message)
            }
        }

        if (!state.isLoading && state.accounts.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = state.filters.query,
                    onValueChange = { onFiltersChange(state.filters.copy(query = it)) },
                    placeholder = { Text(text = stringResource(R.string.movement_search_hint)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (activeFilterCount > 0) {
                item {
                    ActiveMovementFilterRow(
                        filters = state.filters,
                        accounts = state.accounts,
                        categories = state.categories,
                        trips = state.trips,
                        tags = state.tags,
                        onFiltersChange = onFiltersChange,
                    )
                }
            }
            item {
                MovementTypeFilterRow(
                    selected = state.filters.type,
                    onSelected = { onFiltersChange(state.filters.copy(type = it)) },
                )
            }
        }

        if (!state.isLoading && state.accounts.isEmpty()) {
            item {
                InlineBanner(
                    kind = BannerKind.Info,
                    text = stringResource(R.string.movement_no_accounts_body),
                )
            }
        }

        when {
            state.isLoading -> item {
                Text(
                    text = stringResource(R.string.movement_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.movements.isEmpty() && state.accounts.isNotEmpty() -> item {
                EmptyMovementsCard(onAdd = onAdd)
            }
            visibleMovements.isEmpty() && state.accounts.isNotEmpty() -> item {
                NoFilteredMovementsCard(onClearFilters = onClearFilters)
            }
            else -> items(items = visibleMovements, key = { it.id }) { movement ->
                MovementListItem(
                    movement = movement,
                    showDate = true,
                    onClick = { onDetail(movement) },
                )
            }
        }
    }

    if (filtersExpanded) {
        MovementFiltersSheet(
            filters = state.filters,
            accounts = state.accounts,
            categories = state.categories,
            trips = state.trips,
            tags = state.tags,
            onFiltersChange = onFiltersChange,
            onClearFilters = onClearFilters,
            onDismiss = { filtersExpanded = false },
        )
    }
}

@Composable
private fun MovementTypeFilterRow(
    selected: MovementType?,
    onSelected: (MovementType?) -> Unit,
) {
    val colors = FinanceTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FinanceFilterChip(
            selected = selected == null,
            label = stringResource(R.string.movement_filter_all_types),
            onClick = { onSelected(null) },
        )
        phaseOneTypes.forEach { type ->
            FinanceFilterChip(
                selected = selected == type,
                label = type.filterLabel(),
                onClick = { onSelected(type) },
                selectedColor = when (type) {
                    MovementType.EXPENSE -> colors.expense
                    MovementType.INCOME -> colors.income
                    MovementType.TRANSFER -> colors.transfer
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovementFiltersSheet(
    filters: MovementFilters,
    accounts: List<AccountSummary>,
    categories: List<CategoryRecord>,
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    onFiltersChange: (MovementFilters) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    var activeSheet by remember { mutableStateOf<FilterSheetType?>(null) }

    val allAccountsLabel = stringResource(R.string.movement_filter_all_accounts)
    val selectedAccountName = remember(filters.accountId, accounts, allAccountsLabel) {
        if (filters.accountId == null) {
            null
        } else {
            accounts.firstOrNull { it.id == filters.accountId }?.name
        }
    } ?: allAccountsLabel

    val noCategoryLabel = stringResource(R.string.common_no_category)
    val allCategoriesLabel = stringResource(R.string.movement_filter_all_categories)
    val selectedCategoryName = remember(filters.categoryId, filters.uncategorizedOnly, categories, noCategoryLabel, allCategoriesLabel) {
        when {
            filters.uncategorizedOnly -> noCategoryLabel
            filters.categoryId != null -> categories.firstOrNull { it.id == filters.categoryId }?.name
            else -> null
        }
    } ?: allCategoriesLabel

    val allTripsLabel = stringResource(R.string.trip_filter_all)
    val selectedTripTagValue = remember(filters.tripId, filters.tagId, trips, tags, allTripsLabel) {
        if (filters.tripId == null) {
            null
        } else {
            val tripName = trips.firstOrNull { it.id == filters.tripId }?.name ?: "Viatge desconegut"
            if (filters.tagId != null) {
                val tagName = tags.firstOrNull { it.id == filters.tagId }?.name ?: "Etiqueta"
                "$tripName · $tagName"
            } else {
                tripName
            }
        }
    } ?: allTripsLabel

    val selectedPeriodValue = filters.formattedPeriod()

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconChip(
                    icon = Icons.Outlined.Tune,
                    contentDescription = null,
                    color = MaterialTheme.colorScheme.primary,
                    size = 48.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.movement_filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.movement_filter_auto_apply),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceTheme.colors.mutedText,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.common_close))
                }
            }
            filters.errorRes?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterSelectorField(
                    label = stringResource(R.string.movement_filter_account),
                    value = selectedAccountName,
                    active = filters.accountId != null,
                    onClick = { activeSheet = FilterSheetType.ACCOUNT },
                    onClear = { onFiltersChange(filters.copy(accountId = null)) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                FilterSelectorField(
                    label = stringResource(R.string.movement_filter_category),
                    value = selectedCategoryName,
                    active = filters.categoryId != null || filters.uncategorizedOnly,
                    onClick = { activeSheet = FilterSheetType.CATEGORY },
                    onClear = { onFiltersChange(filters.copy(categoryId = null, uncategorizedOnly = false)) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterSelectorField(
                    label = stringResource(R.string.movement_field_trip),
                    value = selectedTripTagValue,
                    active = filters.tripId != null,
                    onClick = { activeSheet = FilterSheetType.TRIP },
                    onClear = { onFiltersChange(filters.copy(tripId = null, tagId = null)) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                FilterSelectorField(
                    label = stringResource(R.string.movement_filter_period),
                    value = selectedPeriodValue,
                    active = filters.dateFrom.isNotBlank() || filters.dateTo.isNotBlank(),
                    onClick = { activeSheet = FilterSheetType.PERIOD },
                    onClear = { onFiltersChange(filters.copy(dateFrom = "", dateTo = "")) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            OutlinedButton(
                onClick = onClearFilters,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.common_clear_filters))
            }
        }
    }

    when (activeSheet) {
        FilterSheetType.ACCOUNT -> {
            AccountFilterSheet(
                accounts = accounts,
                selectedAccountId = filters.accountId,
                onSelect = { accountId ->
                    onFiltersChange(filters.copy(accountId = accountId))
                },
                onDismiss = { activeSheet = null }
            )
        }
        FilterSheetType.CATEGORY -> {
            CategoryFilterSheet(
                categories = categories,
                selectedCategoryId = filters.categoryId,
                uncategorizedOnly = filters.uncategorizedOnly,
                onSelect = { categoryId, uncategorizedOnly ->
                    onFiltersChange(
                        filters.copy(
                            categoryId = categoryId,
                            uncategorizedOnly = uncategorizedOnly
                        )
                    )
                },
                onDismiss = { activeSheet = null }
            )
        }
        FilterSheetType.TRIP -> {
            TripFilterSheet(
                trips = trips,
                tags = tags,
                selectedTripId = filters.tripId,
                selectedTagId = filters.tagId,
                onSelect = { tripId, tagId ->
                    onFiltersChange(filters.copy(tripId = tripId, tagId = tagId))
                },
                onDismiss = { activeSheet = null }
            )
        }
        FilterSheetType.PERIOD -> {
            PeriodFilterSheet(
                dateFrom = filters.dateFrom,
                dateTo = filters.dateTo,
                onSelect = { dateFrom, dateTo ->
                    onFiltersChange(filters.copy(dateFrom = dateFrom, dateTo = dateTo))
                },
                onDismiss = { activeSheet = null }
            )
        }
        null -> Unit
    }
}

@Composable
private fun EmptyMovementsCard(onAdd: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.movement_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.movement_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onAdd) {
                Text(text = stringResource(R.string.movement_list_add))
            }
        }
    }
}

@Composable
private fun NoFilteredMovementsCard(onClearFilters: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.movement_filter_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.movement_filter_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onClearFilters) {
                Text(text = stringResource(R.string.common_clear_filters))
            }
        }
    }
}


@Composable
private fun MovementType.filterLabel(): String =
    when (this) {
        MovementType.EXPENSE -> stringResource(R.string.movement_filter_expenses)
        MovementType.INCOME -> stringResource(R.string.movement_filter_income)
        MovementType.TRANSFER -> stringResource(R.string.movement_filter_transfers)
        MovementType.SETTLEMENT -> stringResource(R.string.movement_type_settlement)
        MovementType.REFUND -> stringResource(R.string.movement_type_refund)
        MovementType.EXTERNAL_EXPENSE -> stringResource(R.string.movement_type_external)
    }

private val phaseOneTypes = listOf(
    MovementType.EXPENSE,
    MovementType.INCOME,
    MovementType.TRANSFER,
)

private enum class FilterSheetType {
    ACCOUNT, CATEGORY, TRIP, PERIOD
}

@Composable
private fun MovementFilters.formattedPeriod(): String {
    if (dateFrom.isBlank() && dateTo.isBlank()) return "Tots"
    
    val start = runCatching { LocalDate.parse(dateFrom) }.getOrNull()
    val end = runCatching { LocalDate.parse(dateTo) }.getOrNull()
    if (start != null && end != null) {
        if (start.dayOfMonth == 1 && end == start.plusMonths(1).minusDays(1)) {
            return formatMonthYear(YearMonth.of(start.year, start.monthValue))
        }
        return "${formatCompactDate(start)} - ${formatCompactDate(end)}"
    }
    
    if (start != null) {
        return "Des de ${formatCompactDate(start)}"
    }
    if (end != null) {
        return "Fins a ${formatCompactDate(end)}"
    }
    
    return "Tots"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountFilterSheet(
    accounts: List<AccountSummary>,
    selectedAccountId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.movement_filter_account),
                style = MaterialTheme.typography.titleLarge
            )

            val isAllSelected = selectedAccountId == null
            Surface(
                onClick = {
                    onSelect(null)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = if (isAllSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (isAllSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.movement_filter_all_accounts),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (isAllSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            accounts.forEach { account ->
                val isSelected = selectedAccountId == account.id
                Surface(
                    onClick = {
                        onSelect(account.id)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ColorDot(colorHex = account.color, size = 12.dp)
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterSheet(
    categories: List<CategoryRecord>,
    selectedCategoryId: String?,
    uncategorizedOnly: Boolean,
    onSelect: (String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.movement_filter_category),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            val isAllSelected = selectedCategoryId == null && !uncategorizedOnly
            Surface(
                onClick = {
                    onSelect(null, false)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = if (isAllSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (isAllSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.movement_filter_all_categories),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (isAllSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            val isUncategorizedSelected = uncategorizedOnly
            Surface(
                onClick = {
                    onSelect(null, true)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = if (isUncategorizedSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (isUncategorizedSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconChip(
                        icon = categoryIcon(null),
                        contentDescription = null,
                        color = categoryColor("#9097A3"),
                        size = 32.dp
                    )
                    Text(
                        text = stringResource(R.string.common_no_category),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (isUncategorizedSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            categories.forEach { category ->
                val isSelected = selectedCategoryId == category.id
                Surface(
                    onClick = {
                        onSelect(category.id, false)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconChip(
                            icon = categoryIcon(category.icon),
                            contentDescription = null,
                            color = categoryColor(category.color),
                            size = 32.dp
                        )
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripFilterSheet(
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    selectedTripId: String?,
    selectedTagId: String?,
    onSelect: (String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentTripId by remember { mutableStateOf(selectedTripId) }
    var currentTagId by remember { mutableStateOf(selectedTagId) }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.movement_field_trip),
                style = MaterialTheme.typography.titleLarge
            )

            val isAllTripsSelected = currentTripId == null
            Surface(
                onClick = {
                    currentTripId = null
                    currentTagId = null
                    onSelect(null, null)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = if (isAllTripsSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (isAllTripsSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.trip_filter_all),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (isAllTripsSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            trips.forEach { trip ->
                val isSelected = currentTripId == trip.id
                Surface(
                    onClick = {
                        currentTripId = trip.id
                        if (currentTripId != selectedTripId) {
                            currentTagId = null
                        }
                        val tripTags = tags.filter { it.supportsTrip(trip) }
                        if (tripTags.isEmpty()) {
                            onSelect(trip.id, null)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = trip.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            val currentTrip = currentTripId?.let { id -> trips.firstOrNull { it.id == id } }
            currentTripId?.let { tripId ->
                val tripTags = tags.filter { it.supportsTrip(currentTrip) }
                if (tripTags.isNotEmpty()) {
                    HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                    Text(
                        text = stringResource(R.string.movement_field_tag),
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    val isNoTagSelected = currentTagId == null
                    Surface(
                        onClick = {
                            currentTagId = null
                            onSelect(tripId, null)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = if (isNoTagSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (isNoTagSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.tag_picker_none),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (isNoTagSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    tripTags.forEach { tag ->
                        val isTagSelected = currentTagId == tag.id
                        Surface(
                            onClick = {
                                currentTagId = tag.id
                                onSelect(tripId, tag.id)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = if (isTagSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (isTagSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (isTagSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            if (currentTripId != null && tags.any { it.supportsTrip(currentTrip) }) {
                PrimaryButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        onSelect(currentTripId, currentTagId)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodFilterSheet(
    dateFrom: String,
    dateTo: String,
    onSelect: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentYear by remember {
        mutableStateOf(
            runCatching { LocalDate.parse(dateFrom).year }.getOrElse { LocalDate.now().year }
        )
    }
    
    val months = listOf(
        "Gener", "Febrer", "Març", "Abril", "Maig", "Juny",
        "Juliol", "Agost", "Setembre", "Octubre", "Novembre", "Desembre"
    )
    
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.movement_filter_period),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    onSelect("", "")
                    onDismiss()
                }) {
                    Text(text = stringResource(R.string.common_clear_filters))
                }
            }
            
            Text(
                text = stringResource(R.string.movement_filter_month_sheet_title),
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopBarIconButton(
                    icon = Icons.Outlined.ChevronLeft,
                    contentDescription = stringResource(R.string.common_back),
                    onClick = { currentYear-- }
                )
                Text(
                    text = currentYear.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                TopBarIconButton(
                    icon = Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(R.string.common_next),
                    onClick = { currentYear++ }
                )
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0 until 3) {
                            val monthIdx = row * 3 + col
                            val monthName = months[monthIdx]
                            val monthNum = monthIdx + 1
                            
                            val isMonthSelected = remember(dateFrom, dateTo, currentYear) {
                                runCatching {
                                    val start = LocalDate.parse(dateFrom)
                                    val end = LocalDate.parse(dateTo)
                                    start.year == currentYear && start.monthValue == monthNum &&
                                    start.dayOfMonth == 1 && end == start.plusMonths(1).minusDays(1)
                                }.getOrDefault(false)
                            }
                            
                            Surface(
                                onClick = {
                                    val start = LocalDate.of(currentYear, monthNum, 1)
                                    val end = start.plusMonths(1).minusDays(1)
                                    onSelect(start.toString(), end.toString())
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small,
                                color = if (isMonthSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isMonthSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = monthName,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            HorizontalDivider(color = FinanceTheme.colors.cardBorder)
            
            Text(
                text = stringResource(R.string.movement_filter_custom_range_title),
                style = MaterialTheme.typography.titleMedium
            )

            val dateFromPlaceholder = stringResource(R.string.movement_filter_date_from)
            val dateToPlaceholder = stringResource(R.string.movement_filter_date_to)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val displayFrom = remember(dateFrom, dateFromPlaceholder) {
                    if (dateFrom.isBlank()) dateFromPlaceholder else formatCompactDate(dateFrom)
                }
                Surface(
                    onClick = { showFromDatePicker = true },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = displayFrom,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (dateFrom.isBlank()) FinanceTheme.colors.mutedText else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = FinanceTheme.colors.mutedText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                val displayTo = remember(dateTo, dateToPlaceholder) {
                    if (dateTo.isBlank()) dateToPlaceholder else formatCompactDate(dateTo)
                }
                Surface(
                    onClick = { showToDatePicker = true },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = displayTo,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (dateTo.isBlank()) FinanceTheme.colors.mutedText else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = FinanceTheme.colors.mutedText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            if (dateFrom.isNotBlank() || dateTo.isNotBlank()) {
                PrimaryButton(
                    text = stringResource(R.string.common_apply),
                    onClick = { onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    
    if (showFromDatePicker) {
        val initialMillis = remember(dateFrom) {
            runCatching {
                LocalDate.parse(dateFrom).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrDefault(System.currentTimeMillis())
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showFromDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val ld = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onSelect(ld.toString(), dateTo)
                    }
                    showFromDatePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showFromDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
    
    if (showToDatePicker) {
        val initialMillis = remember(dateTo) {
            runCatching {
                LocalDate.parse(dateTo).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrDefault(System.currentTimeMillis())
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showToDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val ld = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onSelect(dateFrom, ld.toString())
                    }
                    showToDatePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showToDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

