package com.gestorfinances.app.ui.trips

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import com.gestorfinances.app.ui.common.AppDropdownMenu
import com.gestorfinances.app.ui.common.AppDropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.TripDailyActual
import com.gestorfinances.app.data.repository.TripDayCategoryActual
import com.gestorfinances.app.data.repository.TripStatus
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.TripType
import com.gestorfinances.app.data.repository.dayCount
import com.gestorfinances.app.data.repository.effectiveColor
import com.gestorfinances.app.data.repository.effectiveIcon
import com.gestorfinances.app.data.repository.icon
import com.gestorfinances.app.data.repository.label
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.ChartDataRow
import com.gestorfinances.app.ui.common.CategoryIconPalette
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.ColorPickerRow
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceSwitch
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IconPickerRow
import com.gestorfinances.app.ui.common.IncomeExpenseChart
import com.gestorfinances.app.ui.common.IncomeExpenseChartPoint
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.RootPageHeader
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.color
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.formatExpandedDate
import com.gestorfinances.app.ui.common.formatPercentLabel
import com.gestorfinances.app.ui.common.formatWeekdayDate
import com.gestorfinances.app.ui.common.chartBalanceLabel
import com.gestorfinances.app.ui.common.chartTrendLabel
import com.gestorfinances.app.ui.common.parseIsoDateOrNull
import com.gestorfinances.app.ui.common.progressFraction
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.common.categoryIcon
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Composable
fun TripsScreen(
    viewModel: TripsViewModel,
    onOpenDetail: (TripSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    val form = state.form
    if (form != null) {
        BackHandler(onBack = viewModel::onFormDismissed)
        TripFormScreen(
            form = form,
            accounts = state.accounts,
            onFormChange = viewModel::onFormChanged,
            onBack = viewModel::onFormDismissed,
            onSave = viewModel::onSaveClicked,
            modifier = modifier,
        )
    } else {
        TripsContent(
            state = state,
            modifier = modifier,
            onStatusFilter = viewModel::onStatusFilterChanged,
            onAdd = viewModel::onAddClicked,
            onEdit = viewModel::onEditClicked,
            onArchive = viewModel::onArchiveClicked,
            onDetail = onOpenDetail,
        )
    }

    state.archiveCandidate?.let {
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(R.string.trip_archive_confirm_title)) },
            text = { Text(text = stringResource(R.string.trip_archive_warning)) },
            confirmButton = {
                DestructiveTextButton(onClick = viewModel::onArchiveConfirmed) {
                    Text(text = stringResource(R.string.common_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onArchiveDismissed) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun TripsContent(
    state: TripsUiState,
    modifier: Modifier,
    onStatusFilter: (TripStatus?) -> Unit,
    onAdd: () -> Unit,
    onEdit: (TripSummary) -> Unit,
    onArchive: (TripSummary) -> Unit,
    onDetail: (TripSummary) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RootPageHeader(title = stringResource(R.string.trip_list_title))
        }

        item {
            ChipFlowSection(label = stringResource(R.string.trip_field_status)) {
                FinanceFilterChip(
                    selected = state.statusFilter == null,
                    label = stringResource(R.string.trip_filter_all),
                    onClick = { onStatusFilter(null) },
                )
                TripStatus.entries.forEach { status ->
                    FinanceFilterChip(
                        selected = state.statusFilter == status,
                        label = status.filterLabel(),
                        onClick = { onStatusFilter(status) },
                    )
                }
            }
        }

        state.errorMessage?.let { message ->
            item {
                InlineBanner(kind = BannerKind.Error, text = message)
            }
        }

        if (state.isLoading) {
            item {
                Text(
                    text = stringResource(R.string.trip_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (state.visibleTrips.isEmpty()) {
            item { EmptyTripsCard(onAdd = onAdd) }
        } else {
            item {
                SectionHeader(
                    title = stringResource(R.string.trip_list_results),
                    trailing = {
                        NeutralPill(
                            text = pluralStringResource(
                                R.plurals.trip_list_count,
                                state.visibleTrips.size,
                                state.visibleTrips.size,
                            ),
                        )
                    },
                )
            }
            items(items = state.visibleTrips, key = { it.id }) { trip ->
                TripRow(
                    trip = trip,
                    onDetail = { onDetail(trip) },
                    onEdit = { onEdit(trip) },
                    onArchive = { onArchive(trip) },
                )
            }
            item {
                PrimaryButton(
                    text = stringResource(R.string.trip_list_add),
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EmptyTripsCard(onAdd: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.trip_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.trip_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton(
                text = stringResource(R.string.trip_list_add),
                onClick = onAdd,
            )
        }
    }
}

@Composable
private fun TripRow(
    trip: TripSummary,
    onDetail: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    val identityColor = categoryColor(trip.color)
    FinanceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetail),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconChip(
                    icon = trip.type.icon(),
                    contentDescription = trip.type.label(),
                    color = identityColor,
                    size = 42.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = trip.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = trip.dateRange() ?: trip.type.label(),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    NeutralPill(text = trip.status.label())
                }
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(
                        cents = trip.totalActualCents,
                        color = FinanceTheme.colors.expense,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(
                            R.string.trip_row_avg_day,
                            formatEuroCents(averageCents(trip.totalActualCents, trip.dayCount())),
                        ),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                TripRowMenu(onEdit = onEdit, onArchive = onArchive)
            }
        }
    }
}

@Composable
private fun TripRowMenu(
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.common_more_options),
                tint = FinanceTheme.colors.mutedText,
            )
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppDropdownMenuItem(
                text = { Text(text = stringResource(R.string.common_edit)) },
                onClick = { expanded = false; onEdit() },
            )
            AppDropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.common_archive),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { expanded = false; onArchive() },
            )
        }
    }
}

/**
 * Trip detail as a full page, promoted from the former `AlertDialog`. Reads
 * [TripsViewModel]'s `detail` state directly — the caller is responsible for having triggered a
 * load (`onDetailClicked`/`onDetailOpened`) before navigating here. Also hosts the edit form and
 * archive confirmation reachable from the header's overflow menu (there is no dialog button row
 * to host them on a full page, unlike the former `AlertDialog`); the page stays visible
 * underneath both, and archiving navigates back since the trip stops existing in the active list.
 *
 * Content is four tabs mirroring Anàlisi's own `TabRow`: Resum (hero + budget +
 * cumulative chart) · Desglossament (category/tag percent-bar rows) · Dia a dia (display-only
 * per-day category rollup cards) · Moviments (the full day-grouped scoped ledger). Adding a
 * movement is a local action — it pre-fills this trip while the page is open (MainActivity).
 */
@Composable
fun TripDetailScreen(
    viewModel: TripsViewModel,
    onBack: () -> Unit,
    onManageTags: (String) -> Unit,
    onManageBudget: (String) -> Unit,
    onAddMovement: () -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val detail = state.detail
    val form = state.form

    Box(modifier = modifier.fillMaxSize()) {
        when {
            form != null -> {
                BackHandler(onBack = viewModel::onFormDismissed)
                TripFormScreen(
                    form = form,
                    accounts = state.accounts,
                    onFormChange = viewModel::onFormChanged,
                    onBack = viewModel::onFormDismissed,
                    onSave = viewModel::onSaveClicked,
                )
            }
            detail == null -> {
                Text(
                    text = stringResource(R.string.trip_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp),
                )
            }
            detail.isLoading -> {
                Text(
                    text = stringResource(R.string.trip_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp),
                )
            }
            else -> {
                TripDetailContent(
                    detail = detail,
                    onBack = onBack,
                    onEdit = { viewModel.onEditClicked(detail.trip) },
                    onArchive = { viewModel.onArchiveClicked(detail.trip) },
                    onManageTags = { onManageTags(detail.trip.id) },
                    onManageBudget = { onManageBudget(detail.trip.id) },
                    onAddMovement = onAddMovement,
                    onExcludeOneTimeToggled = viewModel::onExcludeOneTimeToggled,
                    onMovementDetail = onMovementDetail,
                )
            }
        }
    }

    state.archiveCandidate?.let {
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(R.string.trip_archive_confirm_title)) },
            text = { Text(text = stringResource(R.string.trip_archive_warning)) },
            confirmButton = {
                DestructiveTextButton(
                    // Only navigate back once the archive actually succeeds — awaiting
                    // `onSuccess` (rather than calling `onBack()` unconditionally right after
                    // firing the coroutine) keeps the user on this page with the failure shown
                    // inline (`detail.errorMessage`) instead of silently landing back on
                    // whatever screen this page was opened from.
                    onClick = { viewModel.onArchiveConfirmed(onSuccess = onBack) },
                ) {
                    Text(text = stringResource(R.string.common_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onArchiveDismissed) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun TripDetailContent(
    detail: TripDetailState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onManageTags: () -> Unit,
    onManageBudget: () -> Unit,
    onAddMovement: () -> Unit,
    onExcludeOneTimeToggled: (Boolean) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
) {
    val trip = detail.trip
    val days = trip.dayCount(
        fallbackStart = detail.dailyActual.firstOrNull()?.date,
        fallbackEnd = detail.dailyActual.lastOrNull()?.date,
    )
    var selectedTab by remember(trip.id) { mutableStateOf(TripDetailTab.RESUM) }
    // Hoisted above the tab switch so leaving and returning to Desglossament keeps the selection.
    var breakdownDimension by remember(trip.id) { mutableStateOf(TripBreakdownDimension.CATEGORY) }
    var breakdownMode by remember(trip.id) { mutableStateOf(TripBreakdownMode.TOTAL) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
            IconChip(
                icon = trip.type.icon(),
                contentDescription = trip.type.label(),
                color = categoryColor(trip.color),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = trip.detailMetaLine(days),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TripDetailMenu(onEdit = onEdit, onArchive = onArchive)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onAddMovement) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.trip_action_add_movement))
            }
        }

        detail.errorMessage?.let { message ->
            InlineBanner(
                kind = BannerKind.Error,
                text = message,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        if (detail.isLoading) {
            Text(
                text = stringResource(R.string.trip_loading),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        TabRow(
            selectedTabIndex = TripDetailTab.entries.indexOf(selectedTab),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            TripDetailTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface,
                    unselectedContentColor = FinanceTheme.colors.mutedText,
                    text = {
                        Text(
                            text = tab.label(),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    },
                )
            }
        }

        when (selectedTab) {
            TripDetailTab.RESUM -> TripResumTab(
                detail = detail,
                days = days,
                onExcludeOneTimeToggled = onExcludeOneTimeToggled,
                onManageBudget = onManageBudget,
            )
            TripDetailTab.BREAKDOWN -> TripBreakdownTab(
                detail = detail,
                days = days,
                dimension = breakdownDimension,
                mode = breakdownMode,
                onDimensionChange = { breakdownDimension = it },
                onModeChange = { breakdownMode = it },
                onManageTags = onManageTags,
            )
            TripDetailTab.DAYS -> TripDaysTab(detail = detail)
            TripDetailTab.MOVEMENTS -> TripMovementsTab(
                detail = detail,
                onMovementDetail = onMovementDetail,
            )
        }
    }
}

private val detailTabPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)

private enum class TripDetailTab {
    RESUM,
    BREAKDOWN,
    DAYS,
    MOVEMENTS,
}

@Composable
private fun TripDetailTab.label(): String =
    stringResource(
        when (this) {
            TripDetailTab.RESUM -> R.string.trip_tab_resum
            TripDetailTab.BREAKDOWN -> R.string.trip_detail_breakdown
            TripDetailTab.DAYS -> R.string.trip_tab_day_by_day
            TripDetailTab.MOVEMENTS -> R.string.trip_tab_movements
        },
    )

/** Resum tab: the headline answer — hero spend card, budget state, cumulative daily chart. */
@Composable
private fun TripResumTab(
    detail: TripDetailState,
    days: Long,
    onExcludeOneTimeToggled: (Boolean) -> Unit,
    onManageBudget: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = detailTabPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TripHeroSection(
                actualCents = detail.summary.actualCents,
                flowCents = detail.summary.accountOutflowCents,
                days = days,
                excludeOneTime = detail.excludeOneTime,
                onExcludeOneTimeToggled = onExcludeOneTimeToggled,
            )
        }
        item {
            TripBudgetSection(
                evaluation = detail.budgetEvaluation,
                onManageBudget = onManageBudget,
            )
        }
        item {
            TripDailySection(
                actualCents = detail.summary.actualCents,
                items = detail.dailyActual,
            )
        }
    }
}

/** One trip day's ledger group (Moviments tab) or rollup card (Dia a dia tab): the day's
 * canonical actual total (`tripActualByDay` — 0 when the day nets to zero and the query drops
 * it), its 1-based ordinal within the trip range (null for out-of-range spend such as advance
 * bookings), and the day's rows. */
private data class TripDayGroup<T>(
    val date: String,
    val ordinal: Long?,
    val totalCents: Long,
    val rows: List<T>,
)

private fun <T> buildDayGroups(
    trip: TripSummary,
    dailyActual: List<TripDailyActual>,
    rows: List<T>,
    dateOf: (T) -> String,
): List<TripDayGroup<T>> {
    val totals = dailyActual.associate { it.date to it.actualCents }
    return rows
        .groupBy(dateOf)
        .toSortedMap()
        .map { (date, dayRows) ->
            TripDayGroup(
                date = date,
                ordinal = trip.dayOrdinal(date),
                totalCents = totals[date] ?: 0L,
                rows = dayRows,
            )
        }
}

/** 1-based day number of [date] within the trip's own range, or null outside it. */
private fun TripSummary.dayOrdinal(date: String): Long? {
    val start = startDate?.let(::parseIsoDateOrNull) ?: return null
    val day = parseIsoDateOrNull(date) ?: return null
    if (day.isBefore(start)) return null
    val end = endDate?.let(::parseIsoDateOrNull)
    if (end != null && day.isAfter(end)) return null
    return ChronoUnit.DAYS.between(start, day) + 1
}

/** `Dia 2 · Dissabte 13 jul` within the trip range, plain `Dissabte 13 jul` outside it. */
@Composable
private fun dayLabel(ordinal: Long?, date: String): String {
    val formatted = formatWeekdayDate(date)
    return if (ordinal != null) {
        stringResource(R.string.trip_timeline_day, ordinal, formatted)
    } else {
        formatted
    }
}

/** Dia a dia tab: display-only per-day cards with category rollup lines; aggregates never navigate. */
@Composable
private fun TripDaysTab(detail: TripDetailState) {
    val groups = buildDayGroups(detail.trip, detail.dailyActual, detail.dayCategoryActual) { it.date }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = detailTabPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (groups.isEmpty()) {
            item { TripDetailEmptyText() }
        } else {
            items(items = groups, key = { it.date }) { group ->
                TripDayCard(group = group)
            }
        }
    }
}

@Composable
private fun TripDayCard(group: TripDayGroup<TripDayCategoryActual>) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dayLabel(group.ordinal, group.date),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                MoneyText(
                    cents = group.totalCents,
                    color = FinanceTheme.colors.expense,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            group.rows.forEach { line ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(categoryColor(line.categoryColor)),
                    )
                    Text(
                        text = line.categoryName ?: stringResource(R.string.common_no_category),
                        color = if (line.categoryName == null) {
                            FinanceTheme.colors.mutedText
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MoneyText(
                        cents = line.actualCents,
                        color = FinanceTheme.colors.expense,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** Moviments tab: the full scoped ledger as a day-grouped timeline — no cap, no drill-away. */
@Composable
private fun TripMovementsTab(
    detail: TripDetailState,
    onMovementDetail: (MovementSummary) -> Unit,
) {
    val groups = buildDayGroups(detail.trip, detail.dailyActual, detail.movements) { it.date }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = detailTabPadding,
    ) {
        if (groups.isEmpty()) {
            item { TripDetailEmptyText() }
        } else groups.forEachIndexed { index, group ->
            item(key = "day-${group.date}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) 0.dp else 18.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dayLabel(group.ordinal, group.date),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    MoneyText(
                        cents = group.totalCents,
                        color = FinanceTheme.colors.expense,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            items(items = group.rows, key = { it.id }) { movement ->
                MovementListItem(
                    movement = movement,
                    onClick = { onMovementDetail(movement) },
                    showDate = false,
                )
            }
        }
    }
}

@Composable
private fun TripDetailEmptyText() {
    Text(
        text = stringResource(R.string.trip_analysis_empty_body),
        color = FinanceTheme.colors.mutedText,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun TripDetailMenu(
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.common_more_options),
                tint = FinanceTheme.colors.mutedText,
            )
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppDropdownMenuItem(
                text = { Text(text = stringResource(R.string.trip_action_edit)) },
                onClick = { expanded = false; onEdit() },
            )
            AppDropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.trip_action_archive),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { expanded = false; onArchive() },
            )
        }
    }
}

private enum class TripBreakdownMode {
    TOTAL,
    AVG_DAY,
}

private enum class TripBreakdownDimension {
    CATEGORY,
    TAG,
}

/**
 * Hero card: total actual spend as the single headline number, with avg/day and account outflow
 * folded into one muted secondary line, plus the exclude-one-time toggle.
 */
@Composable
private fun TripHeroSection(
    actualCents: Long,
    flowCents: Long,
    days: Long,
    excludeOneTime: Boolean,
    onExcludeOneTimeToggled: (Boolean) -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.trip_detail_total_actual),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
            )
            MoneyText(
                cents = actualCents,
                color = FinanceTheme.colors.expense,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = listOfNotNull(
                    // Avg/day is undefined without a day count (no dates, no daily actuals).
                    days.takeIf { it > 0L }?.let {
                        stringResource(
                            if (excludeOneTime) {
                                R.string.trip_detail_avg_day_excluding
                            } else {
                                R.string.trip_row_avg_day
                            },
                            formatEuroCents(averageCents(actualCents, days)),
                        )
                    },
                    stringResource(R.string.trip_detail_outflow, formatEuroCents(flowCents)),
                ).joinToString(" · "),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.trip_detail_exclude_one_time),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                FinanceSwitch(checked = excludeOneTime, onCheckedChange = onExcludeOneTimeToggled)
            }
        }
    }
}

/**
 * Budget block on the Resum tab. With an active TRIP-scope budget: a progress card, tappable to
 * edit it on the Budgets surface. Without one: a "Defineix pressupost" secondary button opening
 * that same surface. The bar always reflects total actual spend — it deliberately ignores the
 * exclude-one-time toggle.
 */
@Composable
private fun TripBudgetSection(
    evaluation: BudgetEvaluation?,
    onManageBudget: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.trip_action_budget),
            style = MaterialTheme.typography.titleSmall,
        )
        if (evaluation == null) {
            OutlinedButton(
                onClick = onManageBudget,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(text = stringResource(R.string.trip_budget_define))
            }
        } else {
            FinanceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageBudget),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BudgetProgressBar(
                        fraction = evaluation.progressFraction(),
                        color = evaluation.status.color(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                R.string.budget_progress,
                                formatEuroCents(evaluation.actualCents),
                                formatEuroCents(evaluation.budget.limitAmountCents),
                            ),
                            modifier = Modifier.weight(1f),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = if (evaluation.remainingCents >= 0L) {
                                stringResource(R.string.budget_remaining, formatEuroCents(evaluation.remainingCents))
                            } else {
                                stringResource(R.string.budget_over, formatEuroCents(-evaluation.remainingCents))
                            },
                            color = evaluation.status.color(),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

/** States used to keep the Trip Detail KPI and chart honest at the presentation boundary. */
internal enum class TripDailyChartState {
    DATA,
    TRUE_EMPTY,
    KPI_WITHOUT_SERIES,
    KPI_SERIES_MISMATCH,
}

internal fun tripDailyChartState(
    actualCents: Long,
    items: List<TripDailyActual>,
): TripDailyChartState = when {
    items.isEmpty() && actualCents == 0L -> TripDailyChartState.TRUE_EMPTY
    items.isEmpty() -> TripDailyChartState.KPI_WITHOUT_SERIES
    items.sumOf { it.actualCents } == actualCents -> TripDailyChartState.DATA
    else -> TripDailyChartState.KPI_SERIES_MISMATCH
}

/**
 * Cumulative daily spend (design baseline charts are always cumulative, e.g. Analysis's own
 * `IncomeExpenseChart` use — no separate "daily bars" mode, matching that established pattern).
 */
@Composable
private fun TripDailySection(actualCents: Long, items: List<TripDailyActual>) {
    val chartState = tripDailyChartState(actualCents = actualCents, items = items)
    val points = if (chartState == TripDailyChartState.DATA) tripDailyChartPoints(items) else emptyList()
    val period = items.firstOrNull()?.date?.let { first ->
        items.lastOrNull()?.date?.let { last ->
            if (first == last) formatWeekdayDate(first) else "${formatWeekdayDate(first)} - ${formatWeekdayDate(last)}"
        }
    } ?: stringResource(R.string.trip_analysis_empty_title)
    IncomeExpenseChart(
        title = stringResource(R.string.trip_detail_daily_chart),
        points = points,
        incomeLabel = stringResource(R.string.analysis_summary_income),
        expenseLabel = stringResource(R.string.analysis_summary_expense),
        emptyText = stringResource(
            if (chartState == TripDailyChartState.TRUE_EMPTY) {
                R.string.trip_analysis_empty_body
            } else {
                R.string.trip_analysis_chart_unavailable
            },
        ),
        accessibilitySummary = stringResource(
            R.string.accessibility_chart_income_expense_summary,
            stringResource(R.string.trip_detail_daily_chart),
            period,
            formatEuroCents(0L),
            formatEuroCents(actualCents),
            chartBalanceLabel(0L, actualCents),
            chartTrendLabel(
                items.firstOrNull()?.actualCents,
                items.lastOrNull()?.actualCents,
            ),
        ),
        accessibilityRows = points.map { point ->
            ChartDataRow(
                point.label,
                "${stringResource(R.string.analysis_summary_expense)} ${formatEuroCents(point.expenseCents)}",
            )
        },
    )
}

/**
 * Desglossament tab: one composition list with a category/tag dimension switch plus the
 * Totals/Mitjana-per-dia mode toggle, so only one row list is on screen at a time.
 * Tag management lives here — where the tags are — as a trailing action on the tag dimension.
 */
@Composable
private fun TripBreakdownTab(
    detail: TripDetailState,
    days: Long,
    dimension: TripBreakdownDimension,
    mode: TripBreakdownMode,
    onDimensionChange: (TripBreakdownDimension) -> Unit,
    onModeChange: (TripBreakdownMode) -> Unit,
    onManageTags: () -> Unit,
) {
    val entries = when (dimension) {
        TripBreakdownDimension.CATEGORY -> detail.categoryActual.map { item ->
            BreakdownEntry(
                icon = categoryIcon(item.categoryIcon),
                color = categoryColor(item.categoryColor),
                label = item.categoryName ?: stringResource(R.string.common_no_category),
                actualCents = item.actualCents,
                displayCents = breakdownDisplayCents(item.actualCents, mode, days),
            )
        }
        TripBreakdownDimension.TAG -> detail.tagActual.map { item ->
            val tag = item.tagId?.let { detail.tagsById[it] }
            BreakdownEntry(
                icon = categoryIcon(tag?.effectiveIcon()),
                color = categoryColor(tag?.effectiveColor() ?: item.tagColor),
                label = item.tagName ?: stringResource(R.string.trip_analysis_untagged),
                actualCents = item.actualCents,
                displayCents = breakdownDisplayCents(item.actualCents, mode, days),
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = detailTabPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SegmentedControl(
                options = TripBreakdownDimension.entries,
                selected = dimension,
                label = { it.label() },
                onSelect = onDimensionChange,
            )
        }
        item {
            SegmentedControl(
                options = TripBreakdownMode.entries,
                selected = mode,
                label = { it.label() },
                onSelect = onModeChange,
            )
        }
        if (entries.isEmpty()) {
            item { TripDetailEmptyText() }
        } else {
            val maxCents = entries.maxOf { abs(it.displayCents) }.coerceAtLeast(1L)
            val totalCents = entries.sumOf { abs(it.actualCents) }.coerceAtLeast(1L)
            items(items = entries) { entry ->
                TripBreakdownRow(
                    icon = entry.icon,
                    color = entry.color,
                    label = entry.label,
                    cents = entry.displayCents,
                    maxCents = maxCents,
                    percentOfCents = abs(entry.actualCents),
                    totalCents = totalCents,
                )
            }
        }
        if (dimension == TripBreakdownDimension.TAG) {
            item {
                TextButton(onClick = onManageTags) {
                    Text(text = stringResource(R.string.trip_action_manage_tags))
                }
            }
        }
    }
}

/** One category/tag breakdown row's identity and amounts, normalized across the two dimensions. */
private data class BreakdownEntry(
    val icon: ImageVector,
    val color: Color,
    val label: String,
    val actualCents: Long,
    val displayCents: Long,
)

/** A category/tag breakdown row: icon + name + amount + percent bar (mirrors Analysis's own rows). */
@Composable
private fun TripBreakdownRow(
    icon: ImageVector,
    color: Color,
    label: String,
    cents: Long,
    maxCents: Long,
    percentOfCents: Long,
    totalCents: Long,
) {
    val fraction = (abs(cents).toFloat() / maxCents.toFloat()).coerceIn(0f, 1f)
    val pctFraction = (percentOfCents.toFloat() / totalCents.toFloat()).coerceIn(0f, 1f)
    val pctText = formatPercentLabel(pctFraction)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon = icon, contentDescription = null, color = color, size = 36.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    cents = cents,
                    color = FinanceTheme.colors.expense,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = pctText,
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun TripFormScreen(
    form: TripFormState,
    accounts: List<AccountSummary>,
    onFormChange: (TripFormState) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeaderRow(
            onBack = onBack,
            title = stringResource(
                if (form.id == null) R.string.trip_form_new_title else R.string.trip_form_edit_title,
            ),
        )
        form.errorMessage?.let {
            InlineBanner(kind = BannerKind.Error, text = it)
        }
        val nameError = form.errorField == TripFormField.NAME
        OutlinedTextField(
            value = form.name,
            onValueChange = { onFormChange(form.copy(name = it)) },
            label = { Text(text = stringResource(R.string.trip_field_name)) },
            singleLine = true,
            isError = nameError,
            supportingText = if (nameError && form.errorRes != null) {
                { Text(text = stringResource(form.errorRes)) }
            } else null,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = doneKeyboardActions(),
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(nameError),
        )
        SegmentedControl(
            options = TripType.entries,
            selected = form.type,
            label = { it.label() },
            onSelect = { onFormChange(form.copy(type = it)) },
        )
        SegmentedControl(
            options = TripStatus.entries,
            selected = form.status,
            label = { it.label() },
            onSelect = { onFormChange(form.copy(status = it)) },
        )
        val startDateError = form.errorField == TripFormField.START_DATE
        val endDateError = form.errorField == TripFormField.END_DATE
        val accountError = form.errorField == TripFormField.ACCOUNT
        val errorText = if (form.errorRes != null) stringResource(form.errorRes) else null
        FormDatePicker(
            label = stringResource(R.string.trip_field_start_date),
            date = form.startDate,
            onDateChange = { onFormChange(form.copy(startDate = it)) },
            modifier = Modifier.scrollToWhen(startDateError),
            isError = startDateError,
            supportingText = if (startDateError) errorText else null,
        )
        FormDatePicker(
            label = stringResource(R.string.trip_field_end_date),
            date = form.endDate,
            onDateChange = { onFormChange(form.copy(endDate = it)) },
            modifier = Modifier.scrollToWhen(endDateError),
            isError = endDateError,
            supportingText = if (endDateError) errorText else null,
        )
        FormSelect(
            label = stringResource(R.string.trip_field_default_account),
            options = listOf(SelectOption(id = null, label = stringResource(R.string.trip_detail_no_default_account))) +
                accounts.map { SelectOption(id = it.id, label = it.name) },
            selectedId = form.defaultAccountId,
            onSelect = { onFormChange(form.copy(defaultAccountId = it)) },
            placeholder = stringResource(R.string.trip_detail_no_default_account),
            modifier = Modifier.scrollToWhen(accountError),
            isError = accountError,
            supportingText = if (accountError) errorText else null,
        )
        ColorPickerRow(
            label = stringResource(R.string.trip_field_color),
            selectedHex = form.color.ifBlank { null },
            onSelect = { onFormChange(form.copy(color = it)) },
        )
        IconPickerRow(
            label = stringResource(R.string.trip_field_icon),
            options = CategoryIconPalette,
            selectedKey = form.icon.ifBlank { null },
            onSelect = { onFormChange(form.copy(icon = it)) },
        )
        OutlinedTextField(
            value = form.notes,
            onValueChange = { onFormChange(form.copy(notes = it)) },
            label = { Text(text = stringResource(R.string.trip_field_notes)) },
            minLines = 2,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
            PrimaryButton(
                text = stringResource(
                    if (form.id == null) R.string.trip_save_new else R.string.trip_save_changes,
                ),
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Muted meta line under the detail page title: expanded date range · day count · status. */
@Composable
private fun TripSummary.detailMetaLine(days: Long): String =
    listOfNotNull(
        dateRange(expanded = true),
        days.takeIf { it > 0L }?.let { stringResource(R.string.trip_detail_days_count, it) },
        status.label(),
    ).joinToString(" · ")

@Composable
private fun TripSummary.dateRange(expanded: Boolean = false): String? {
    val formatDate: (String) -> String = if (expanded) ::formatExpandedDate else ::formatCompactDate
    return when {
        startDate != null && endDate != null ->
            stringResource(R.string.trip_date_range, formatDate(startDate), formatDate(endDate))
        startDate != null -> stringResource(R.string.trip_date_ongoing, formatDate(startDate))
        endDate != null -> formatDate(endDate)
        else -> null
    }
}

@Composable
private fun TripStatus.label(): String =
    stringResource(
        when (this) {
            TripStatus.PLANNED -> R.string.trip_status_planned
            TripStatus.ACTIVE -> R.string.trip_status_active
            TripStatus.FINISHED -> R.string.trip_status_finished
        },
    )

@Composable
private fun TripStatus.filterLabel(): String =
    stringResource(
        when (this) {
            TripStatus.PLANNED -> R.string.trip_filter_planned
            TripStatus.ACTIVE -> R.string.trip_filter_active
            TripStatus.FINISHED -> R.string.trip_filter_finished
        },
    )

/** [IncomeExpenseChart] cumulates its `expenseCents` series itself, so the per-day deltas feed straight through. */
/**
 * The cumulative line needs two points to draw a visible segment. A one-day trip therefore gets
 * a zero baseline immediately before its real bucket; this is a chart origin, not a fabricated
 * movement or an extra day total.
 */
internal fun tripDailyChartPoints(items: List<TripDailyActual>): List<IncomeExpenseChartPoint> =
    buildList {
        if (items.size == 1) {
            add(
                IncomeExpenseChartPoint(
                    label = "",
                    bucket = "${items.first().date}:baseline",
                    incomeCents = 0L,
                    expenseCents = 0L,
                ),
            )
        }
        addAll(
            items.map { item ->
                IncomeExpenseChartPoint(
                    label = formatDayLabel(item.date),
                    bucket = item.date,
                    incomeCents = 0L,
                    expenseCents = item.actualCents,
                )
            },
        )
    }

private fun formatDayLabel(date: String): String =
    try {
        LocalDate.parse(date).dayOfMonth.toString()
    } catch (_: DateTimeParseException) {
        "—"
    }

private fun breakdownDisplayCents(
    actualCents: Long,
    mode: TripBreakdownMode,
    days: Long,
): Long =
    when (mode) {
        TripBreakdownMode.TOTAL -> actualCents
        TripBreakdownMode.AVG_DAY -> averageCents(actualCents, days)
    }

private fun averageCents(cents: Long, days: Long): Long =
    if (days > 0L) cents / days else 0L

@Composable
private fun TripBreakdownMode.label(): String =
    stringResource(
        when (this) {
            TripBreakdownMode.TOTAL -> R.string.trip_analysis_mode_total
            TripBreakdownMode.AVG_DAY -> R.string.trip_analysis_mode_avg_day
        },
    )

@Composable
private fun TripBreakdownDimension.label(): String =
    stringResource(
        when (this) {
            TripBreakdownDimension.CATEGORY -> R.string.trip_detail_breakdown_category
            TripBreakdownDimension.TAG -> R.string.trip_detail_breakdown_tag
        },
    )
