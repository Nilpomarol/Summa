package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.gestorfinances.app.ui.common.AppDropdownMenu
import com.gestorfinances.app.ui.common.AppDropdownMenuItem
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.HERO_MUTED_ALPHA
import com.gestorfinances.app.ui.common.HeroPanel
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.movementRowPosition
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.RootPageHeader
import com.gestorfinances.app.ui.common.SearchField
import com.gestorfinances.app.ui.common.TopBarIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.accountTypeIcon
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.formatMonth
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.asEyebrow
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
        onRetry = viewModel::onScreenShown,
    )
}

@Composable
private fun MovementsContent(
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
    val activeFilterCount = state.filters.activeFilterCount

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
    ) {
        item {
            RootPageHeader(
                title = stringResource(R.string.movement_list_title),
                modifier = Modifier.padding(bottom = LIST_BLOCK_GAP),
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
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_movements,
                    onRetry = onRetry,
                    modifier = Modifier.padding(bottom = LIST_BLOCK_GAP),
                )
            }
        }

        if (!state.isLoading && state.accounts.isNotEmpty()) {
            item {
                SearchField(
                    query = state.filters.query,
                    onQueryChange = { onFiltersChange(state.filters.copy(query = it)) },
                    placeholder = stringResource(R.string.movement_search_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = LIST_BLOCK_GAP),
                )
            }
            item {
                MovementFilterPills(
                    state = state,
                    onFiltersChange = onFiltersChange,
                    modifier = Modifier.padding(bottom = LIST_BLOCK_GAP),
                )
            }
        }

        if (!state.isLoading && state.accounts.isEmpty()) {
            item {
                Box(modifier = Modifier.padding(bottom = LIST_BLOCK_GAP)) {
                    InlineBanner(
                        kind = BannerKind.Info,
                        text = stringResource(R.string.movement_no_accounts_body),
                    )
                }
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
            else -> itemsIndexed(
                items = visibleMovements,
                key = { _, movement -> movement.id },
            ) { index, movement ->
                MovementListItem(
                    movement = movement,
                    showDate = true,
                    onClick = { onDetail(movement) },
                    position = movementRowPosition(index, visibleMovements.size),
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
            visibleCount = visibleMovements.size,
            totalCount = state.movements.size,
            onFiltersChange = onFiltersChange,
            onClearFilters = onClearFilters,
            onDismiss = { filtersExpanded = false },
        )
    }
}

/** The grey a category-less movement borrows when it needs an identity colour. */
private const val UNCATEGORIZED_COLOR = "#9097A3"

/** Snug padding so the five type pills fit one screen width without scrolling or wrapping. */
private val TYPE_PILL_PADDING = PaddingValues(horizontal = 10.dp, vertical = 9.dp)

/**
 * The one pill row under the search bar: a summary pill for the advanced filters, when any are on,
 * followed by the type selector. The type pills alone fit a phone width; only the summary pill can
 * push the row past it, so the row scrolls exactly then and never wraps to a second line.
 */
@Composable
private fun MovementFilterPills(
    state: MovementsUiState,
    onFiltersChange: (MovementFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FinanceTheme.colors
    val filters = state.filters
    val selected = filters.type
    val onSelected: (MovementType?) -> Unit = { onFiltersChange(filters.copy(type = it)) }
    var moreTypesExpanded by remember { mutableStateOf(false) }
    val rareTypeSelected = selected in rareMovementTypes
    val activeFilterCount = filters.activeFilterCount
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (activeFilterCount > 0) {
                    Modifier.horizontalScroll(rememberScrollState())
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (activeFilterCount > 0) {
            val clearDescription = stringResource(R.string.movement_filter_clear_active)
            FinanceFilterChip(
                selected = true,
                label = pluralStringResource(
                    R.plurals.movement_filter_active_count,
                    activeFilterCount,
                    activeFilterCount,
                ),
                // Keeps the query and the type pill; the count only ever covers the sheet filters.
                onClick = { onFiltersChange(MovementFilters(query = filters.query, type = selected)) },
                selectedColor = MaterialTheme.colorScheme.primary,
                contentPadding = TYPE_PILL_PADDING,
                trailingIcon = Icons.Outlined.Close,
                modifier = Modifier.semantics { contentDescription = clearDescription },
            )
        }
        FinanceFilterChip(
            selected = selected == null,
            label = stringResource(R.string.movement_filter_all_types),
            onClick = {
                moreTypesExpanded = false
                onSelected(null)
            },
            contentPadding = TYPE_PILL_PADDING,
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
                },
                contentPadding = TYPE_PILL_PADDING,
            )
        }
        Box {
            FinanceFilterChip(
                selected = rareTypeSelected,
                label = stringResource(R.string.movement_filter_more_types),
                onClick = { moreTypesExpanded = true },
                selectedColor = MaterialTheme.colorScheme.primary,
                contentPadding = TYPE_PILL_PADDING,
            )
            AppDropdownMenu(
                expanded = moreTypesExpanded,
                onDismissRequest = { moreTypesExpanded = false },
            ) {
                rareMovementTypes.forEach { type ->
                    AppDropdownMenuItem(
                        text = { Text(type.filterLabel()) },
                        selected = selected == type,
                        onClick = {
                            onSelected(type)
                            moreTypesExpanded = false
                        },
                    )
                }
            }
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
    visibleCount: Int,
    totalCount: Int,
    onFiltersChange: (MovementFilters) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    var activeSheet by remember { mutableStateOf<FilterSheetType?>(null) }

    val selectedAccount = accounts.firstOrNull { it.id == filters.accountId }
    val selectedCategory = categories.firstOrNull { it.id == filters.categoryId }
    val selectedTrip = trips.firstOrNull { it.id == filters.tripId }
    val unknownValue = stringResource(R.string.movement_filter_unknown_value)

    val accountValue = when {
        filters.accountId == null -> stringResource(R.string.movement_filter_all_accounts)
        else -> selectedAccount?.name ?: unknownValue
    }
    val categoryValue = when {
        filters.uncategorizedOnly -> stringResource(R.string.common_no_category)
        filters.categoryId == null -> stringResource(R.string.movement_filter_all_categories)
        else -> selectedCategory?.name ?: unknownValue
    }
    val tripValue = when {
        filters.tripId == null -> stringResource(R.string.trip_filter_all)
        else -> {
            val tripName = selectedTrip?.name ?: unknownValue
            val tagName = filters.tagId?.let { tagId ->
                tags.firstOrNull { it.id == tagId }?.name ?: unknownValue
            }
            if (tagName == null) tripName else "$tripName · $tagName"
        }
    }
    val periodActive = filters.dateFrom.isNotBlank() || filters.dateTo.isNotBlank()
    val categoryActive = filters.categoryId != null || filters.uncategorizedOnly

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
            FilterResultPanel(
                visibleCount = visibleCount,
                totalCount = totalCount,
                activeCount = filters.activeFilterCount,
            )

            filters.errorRes?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                FilterLedgerRow(
                    icon = selectedAccount?.let { accountTypeIcon(it.type) }
                        ?: Icons.Outlined.AccountBalanceWallet,
                    color = selectedAccount?.let { categoryColor(it.color) },
                    label = stringResource(R.string.movement_filter_account),
                    value = accountValue,
                    active = filters.accountId != null,
                    onClick = { activeSheet = FilterSheetType.ACCOUNT },
                    onClear = { onFiltersChange(filters.copy(accountId = null)) },
                )
                FilterLedgerRow(
                    icon = selectedCategory?.let { categoryIcon(it.icon) } ?: Icons.Outlined.Category,
                    color = selectedCategory?.let { categoryColor(it.color) },
                    label = stringResource(R.string.movement_filter_category),
                    value = categoryValue,
                    active = categoryActive,
                    onClick = { activeSheet = FilterSheetType.CATEGORY },
                    onClear = {
                        onFiltersChange(filters.copy(categoryId = null, uncategorizedOnly = false))
                    },
                )
                FilterLedgerRow(
                    icon = selectedTrip?.let { categoryIcon(it.icon) } ?: Icons.Outlined.Luggage,
                    color = selectedTrip?.let { categoryColor(it.color) },
                    label = stringResource(R.string.movement_field_trip),
                    value = tripValue,
                    active = filters.tripId != null,
                    onClick = { activeSheet = FilterSheetType.TRIP },
                    onClear = { onFiltersChange(filters.copy(tripId = null, tagId = null)) },
                )
                FilterLedgerRow(
                    icon = Icons.Outlined.CalendarMonth,
                    color = null,
                    label = stringResource(R.string.movement_filter_period),
                    value = filters.formattedPeriod(),
                    active = periodActive,
                    onClick = { activeSheet = FilterSheetType.PERIOD },
                    onClear = { onFiltersChange(filters.copy(dateFrom = "", dateTo = "")) },
                    showDivider = false,
                )
            }

            if (filters.activeFilterCount > 0) {
                OutlinedButton(
                    onClick = onClearFilters,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.common_clear_filters))
                }
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

/**
 * What the filters currently yield, on the same ink panel the dashboard opens with: the ledger is
 * filtered live, so the sheet leads with the count it produces rather than with a title.
 */
@Composable
private fun FilterResultPanel(
    visibleCount: Int,
    totalCount: Int,
    activeCount: Int,
) {
    val colors = FinanceTheme.colors
    HeroPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.movement_filter_title).uppercase(),
                style = MaterialTheme.typography.labelSmall.asEyebrow(),
                color = colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA),
            )
            Text(
                text = pluralStringResource(
                    R.plurals.movement_filter_result_count,
                    visibleCount,
                    visibleCount,
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.heroOnSurface,
            )
            Text(
                text = if (activeCount == 0) {
                    stringResource(R.string.movement_filter_none_active)
                } else {
                    pluralStringResource(
                        R.plurals.movement_filter_active_count,
                        activeCount,
                        activeCount,
                    ) + " · " + stringResource(R.string.movement_filter_of_total, totalCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.heroOnSurface.copy(alpha = HERO_MUTED_ALPHA),
            )
        }
    }
}

/**
 * One option in a filter picker: the same row anatomy the ledger uses, so choosing a filter and
 * reading one look like the same thing. The chosen option wears its own colour filled in.
 */
@Composable
private fun FilterOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color? = null,
    showDivider: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                val tileColor = color ?: MaterialTheme.colorScheme.primary
                if (selected) {
                    IdentityIconTile(icon = icon, color = tileColor, size = 36.dp)
                } else {
                    IconChip(
                        icon = icon,
                        contentDescription = null,
                        color = tileColor,
                        size = 36.dp,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 24.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (icon == null) 0.dp else 48.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/**
 * One filter dimension, built like a movement row: identity tile, what it filters, and the value
 * it currently holds. An untouched dimension keeps a quiet tinted tile; a filter that is on wears
 * the colour of the thing it selected and carries a clear button of its own.
 */
@Composable
private fun FilterLedgerRow(
    icon: ImageVector,
    color: Color?,
    label: String,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
    showDivider: Boolean = true,
) {
    val colors = FinanceTheme.colors
    val tileColor = color ?: MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active) {
                IdentityIconTile(icon = icon, color = tileColor)
            } else {
                IconChip(icon = icon, contentDescription = null, color = tileColor)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.primary else colors.subtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (active) {
                val clearDescription = stringResource(R.string.movement_filter_clear_named, label)
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = clearDescription,
                        tint = colors.mutedText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.subtleText,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 52.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
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

/** Gap under each block above the movement ledger; the ledger's own rows meet without one. */
private val LIST_BLOCK_GAP = 12.dp

private val phaseOneTypes = listOf(
    MovementType.EXPENSE,
    MovementType.INCOME,
    MovementType.TRANSFER,
)

private val rareMovementTypes = listOf(
    MovementType.SETTLEMENT,
    MovementType.REFUND,
    MovementType.EXTERNAL_EXPENSE,
)

private enum class FilterSheetType {
    ACCOUNT, CATEGORY, TRIP, PERIOD
}

@Composable
private fun MovementFilters.formattedPeriod(): String {
    if (dateFrom.isBlank() && dateTo.isBlank()) {
        return stringResource(R.string.movement_filter_period_all)
    }

    val start = runCatching { LocalDate.parse(dateFrom) }.getOrNull()
    val end = runCatching { LocalDate.parse(dateTo) }.getOrNull()
    if (start != null && end != null) {
        if (isWholeYear(start, end)) {
            return start.year.toString()
        }
        if (start.dayOfMonth == 1 && end == start.plusMonths(1).minusDays(1)) {
            return formatMonthYear(YearMonth.of(start.year, start.monthValue))
        }
        return "${formatCompactDate(start)} - ${formatCompactDate(end)}"
    }

    if (start != null) {
        return stringResource(R.string.movement_filter_period_from, formatCompactDate(start))
    }
    if (end != null) {
        return stringResource(R.string.movement_filter_period_to, formatCompactDate(end))
    }

    return stringResource(R.string.movement_filter_period_all)
}

/** One period the sheet offers whole: a month of the shown year, or the year itself. */
@Composable
private fun PeriodOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (selected) null else BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A range that runs the calendar year end to end, however it was picked. */
private fun isWholeYear(start: LocalDate, endInclusive: LocalDate): Boolean =
    start.dayOfYear == 1 &&
        start.year == endInclusive.year &&
        endInclusive == start.withDayOfYear(start.lengthOfYear())

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.movement_filter_account),
                style = MaterialTheme.typography.titleLarge,
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                FilterOptionRow(
                    label = stringResource(R.string.movement_filter_all_accounts),
                    selected = selectedAccountId == null,
                    onClick = {
                        onSelect(null)
                        onDismiss()
                    },
                )
                accounts.forEachIndexed { index, account ->
                    FilterOptionRow(
                        label = account.name,
                        selected = selectedAccountId == account.id,
                        onClick = {
                            onSelect(account.id)
                            onDismiss()
                        },
                        icon = accountTypeIcon(account.type),
                        color = categoryColor(account.color),
                        showDivider = index < accounts.lastIndex,
                    )
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
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                FilterOptionRow(
                    label = stringResource(R.string.movement_filter_all_categories),
                    selected = selectedCategoryId == null && !uncategorizedOnly,
                    onClick = {
                        onSelect(null, false)
                        onDismiss()
                    },
                )
                FilterOptionRow(
                    label = stringResource(R.string.common_no_category),
                    selected = uncategorizedOnly,
                    onClick = {
                        onSelect(null, true)
                        onDismiss()
                    },
                    icon = categoryIcon(null),
                    color = categoryColor(UNCATEGORIZED_COLOR),
                )
                categories.forEachIndexed { index, category ->
                    FilterOptionRow(
                        label = category.name,
                        selected = selectedCategoryId == category.id,
                        onClick = {
                            onSelect(category.id, false)
                            onDismiss()
                        },
                        icon = categoryIcon(category.icon),
                        color = categoryColor(category.color),
                        showDivider = index < categories.lastIndex,
                    )
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.movement_field_trip),
                style = MaterialTheme.typography.titleLarge,
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                FilterOptionRow(
                    label = stringResource(R.string.trip_filter_all),
                    selected = currentTripId == null,
                    onClick = {
                        currentTripId = null
                        currentTagId = null
                        onSelect(null, null)
                        onDismiss()
                    },
                )
                trips.forEachIndexed { index, trip ->
                    FilterOptionRow(
                        label = trip.name,
                        selected = currentTripId == trip.id,
                        onClick = {
                            currentTripId = trip.id
                            if (currentTripId != selectedTripId) {
                                currentTagId = null
                            }
                            if (tags.none { it.supportsTrip(trip) }) {
                                onSelect(trip.id, null)
                                onDismiss()
                            }
                        },
                        icon = categoryIcon(trip.icon),
                        color = categoryColor(trip.color),
                        showDivider = index < trips.lastIndex,
                    )
                }
            }

            val currentTrip = currentTripId?.let { id -> trips.firstOrNull { it.id == id } }
            currentTripId?.let { tripId ->
                val tripTags = tags.filter { it.supportsTrip(currentTrip) }
                if (tripTags.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.movement_field_tag),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        FilterOptionRow(
                            label = stringResource(R.string.tag_picker_none),
                            selected = currentTagId == null,
                            onClick = {
                                currentTagId = null
                                onSelect(tripId, null)
                                onDismiss()
                            },
                        )
                        tripTags.forEachIndexed { index, tag ->
                            FilterOptionRow(
                                label = tag.name,
                                selected = currentTagId == tag.id,
                                onClick = {
                                    currentTagId = tag.id
                                    onSelect(tripId, tag.id)
                                    onDismiss()
                                },
                                showDivider = index < tripTags.lastIndex,
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
                    Text(text = stringResource(R.string.common_clear))
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
            
            val isYearSelected = remember(dateFrom, dateTo, currentYear) {
                val start = runCatching { LocalDate.parse(dateFrom) }.getOrNull()
                val end = runCatching { LocalDate.parse(dateTo) }.getOrNull()
                start != null && end != null && start.year == currentYear && isWholeYear(start, end)
            }
            PeriodOption(
                label = stringResource(R.string.movement_filter_whole_year),
                selected = isYearSelected,
                onClick = {
                    val start = LocalDate.of(currentYear, 1, 1)
                    onSelect(start.toString(), start.withDayOfYear(start.lengthOfYear()).toString())
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0 until 3) {
                            val monthNum = row * 3 + col + 1
                            val monthName = formatMonth(YearMonth.of(currentYear, monthNum))
                            
                            val isMonthSelected = remember(dateFrom, dateTo, currentYear) {
                                runCatching {
                                    val start = LocalDate.parse(dateFrom)
                                    val end = LocalDate.parse(dateTo)
                                    start.year == currentYear && start.monthValue == monthNum &&
                                    start.dayOfMonth == 1 && end == start.plusMonths(1).minusDays(1)
                                }.getOrDefault(false)
                            }
                            
                            PeriodOption(
                                label = monthName,
                                selected = isMonthSelected,
                                onClick = {
                                    val start = LocalDate.of(currentYear, monthNum, 1)
                                    val end = start.plusMonths(1).minusDays(1)
                                    onSelect(start.toString(), end.toString())
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                            )
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

