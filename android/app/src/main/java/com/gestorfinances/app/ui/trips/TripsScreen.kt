package com.gestorfinances.app.ui.trips

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripCategoryActual
import com.gestorfinances.app.data.repository.TripDailyActual
import com.gestorfinances.app.data.repository.TripStatus
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.TripTagActual
import com.gestorfinances.app.data.repository.TripType
import com.gestorfinances.app.data.repository.dayCount
import com.gestorfinances.app.data.repository.effectiveColor
import com.gestorfinances.app.data.repository.effectiveIcon
import com.gestorfinances.app.data.repository.icon
import com.gestorfinances.app.data.repository.label
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.CategoryIconPalette
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.ColorPickerRow
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
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
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.color
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatPercentLabel
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
import kotlin.math.abs

@Composable
fun TripsScreen(
    viewModel: TripsViewModel,
    onOpenDetail: (TripSummary) -> Unit,
    onManageTags: (String?) -> Unit,
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
            onManageTags = onManageTags,
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
    onManageTags: (String?) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.trip_list_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onManageTags(null) }) {
                    Text(text = stringResource(R.string.trip_action_manage_tags))
                }
            }
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
                    contentDescription = null,
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
                        text = trip.summaryLine(),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NeutralPill(text = trip.type.label())
                        NeutralPill(text = trip.status.label())
                    }
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.common_edit)) },
                onClick = { expanded = false; onEdit() },
            )
            DropdownMenuItem(
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
 * Trip detail as a full page (docs/15 §Phase 5R, promoted from the former `AlertDialog`). Reads
 * [TripsViewModel]'s `detail` state directly — the caller is responsible for having triggered a
 * load (`onDetailClicked`/`onDetailOpened`) before navigating here. Also hosts the edit form and
 * archive confirmation reachable from the header's overflow menu (there is no dialog button row
 * to host them on a full page, unlike the former `AlertDialog`); the page stays visible
 * underneath both, and archiving navigates back since the trip stops existing in the active list.
 */
@Composable
fun TripDetailScreen(
    viewModel: TripsViewModel,
    onBack: () -> Unit,
    onNewMovement: (TripSummary) -> Unit,
    onManageTags: (String) -> Unit,
    onManageBudget: (String) -> Unit,
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
            else -> {
                TripDetailContent(
                    detail = detail,
                    onBack = onBack,
                    onEdit = { viewModel.onEditClicked(detail.trip) },
                    onArchive = { viewModel.onArchiveClicked(detail.trip) },
                    onNewMovement = { onNewMovement(detail.trip) },
                    onManageTags = { onManageTags(detail.trip.id) },
                    onManageBudget = { onManageBudget(detail.trip.id) },
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
    onNewMovement: () -> Unit,
    onManageTags: () -> Unit,
    onManageBudget: () -> Unit,
    onExcludeOneTimeToggled: (Boolean) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
) {
    val trip = detail.trip
    val days = trip.dayCount(
        fallbackStart = detail.dailyActual.firstOrNull()?.date,
        fallbackEnd = detail.dailyActual.lastOrNull()?.date,
    )
    var categoryMode by remember(trip.id) { mutableStateOf(TripBreakdownMode.TOTAL) }
    var tagMode by remember(trip.id) { mutableStateOf(TripBreakdownMode.TOTAL) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.trip_detail_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                TripDetailMenu(onEdit = onEdit, onArchive = onArchive)
            }
        }

        item { TripDetailHeader(trip = trip) }

        item {
            DetailLine(
                label = stringResource(R.string.trip_field_default_account),
                value = trip.defaultAccountName ?: stringResource(R.string.trip_detail_no_default_account),
            )
        }

        trip.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            item { DetailLine(label = stringResource(R.string.trip_field_notes), value = notes) }
        }

        detail.errorMessage?.let { message ->
            item { InlineBanner(kind = BannerKind.Error, text = message) }
        }

        if (detail.isLoading) {
            item {
                Text(
                    text = stringResource(R.string.trip_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            TripKpiSection(
                actualCents = detail.summary.actualCents,
                flowCents = detail.summary.accountOutflowCents,
                days = days,
                excludeOneTime = detail.excludeOneTime,
                onExcludeOneTimeToggled = onExcludeOneTimeToggled,
            )
        }

        detail.budgetEvaluation?.let { evaluation ->
            item { TripBudgetSection(evaluation = evaluation) }
        }

        item { TripDailySection(items = detail.dailyActual) }

        item {
            TripCategorySection(
                items = detail.categoryActual,
                days = days,
                mode = categoryMode,
                onModeChange = { categoryMode = it },
            )
        }

        item {
            TripTagSection(
                items = detail.tagActual,
                tagsById = detail.tagsById,
                days = days,
                mode = tagMode,
                onModeChange = { tagMode = it },
            )
        }

        item {
            AnalysisSection(
                title = stringResource(R.string.trip_detail_movements),
                isEmpty = detail.movements.isEmpty(),
            ) {
                detail.movements.forEach { movement ->
                    MovementListItem(
                        movement = movement,
                        onClick = { onMovementDetail(movement) },
                    )
                }
            }
        }

        item {
            PrimaryButton(
                text = stringResource(R.string.trip_action_new_movement),
                onClick = onNewMovement,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onManageTags) {
                    Text(text = stringResource(R.string.trip_action_manage_tags))
                }
                TextButton(onClick = onManageBudget) {
                    Text(text = stringResource(R.string.trip_action_budget))
                }
            }
        }
    }
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.trip_action_edit)) },
                onClick = { expanded = false; onEdit() },
            )
            DropdownMenuItem(
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

@Composable
private fun TripDetailHeader(trip: TripSummary) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconChip(
                icon = trip.type.icon(),
                contentDescription = null,
                color = categoryColor(trip.color),
                size = 44.dp,
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
                trip.dateRange()?.let {
                    Text(
                        text = it,
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NeutralPill(text = trip.type.label())
                    NeutralPill(text = trip.status.label())
                }
            }
        }
    }
}

private enum class TripBreakdownMode {
    TOTAL,
    AVG_DAY,
}

/** KPI row (total spend, account outflow, days, avg/day) with the exclude-one-time toggle (point 13). */
@Composable
private fun TripKpiSection(
    actualCents: Long,
    flowCents: Long,
    days: Long,
    excludeOneTime: Boolean,
    onExcludeOneTimeToggled: (Boolean) -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TripKpiCell(
                    label = stringResource(R.string.trip_detail_total_actual),
                    modifier = Modifier.weight(1f),
                ) {
                    MoneyText(
                        cents = actualCents,
                        color = FinanceTheme.colors.expense,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                TripKpiCell(
                    label = stringResource(R.string.trip_detail_total_flow),
                    modifier = Modifier.weight(1f),
                ) {
                    MoneyText(
                        cents = flowCents,
                        color = FinanceTheme.colors.expense,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TripKpiCell(
                    label = stringResource(R.string.trip_detail_days),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = days.toString(), style = MaterialTheme.typography.titleMedium)
                }
                TripKpiCell(
                    label = if (excludeOneTime) {
                        stringResource(R.string.trip_detail_avg_day_excluding_one_time)
                    } else {
                        stringResource(R.string.trip_detail_avg_day)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    MoneyText(
                        cents = averageCents(actualCents, days),
                        color = FinanceTheme.colors.expense,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
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
                Switch(checked = excludeOneTime, onCheckedChange = onExcludeOneTimeToggled)
            }
        }
    }
}

@Composable
private fun TripKpiCell(
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        value()
    }
}

@Composable
private fun TripBudgetSection(evaluation: BudgetEvaluation) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.trip_action_budget),
            style = MaterialTheme.typography.titleSmall,
        )
        FinanceCard(modifier = Modifier.fillMaxWidth()) {
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

/**
 * Cumulative daily spend (design §6 charts are always cumulative, e.g. Analysis's own
 * `IncomeExpenseChart` use — no separate "daily bars" mode, matching that established pattern).
 */
@Composable
private fun TripDailySection(items: List<TripDailyActual>) {
    IncomeExpenseChart(
        title = stringResource(R.string.trip_detail_daily_chart),
        points = items.toChartPoints(),
        incomeLabel = stringResource(R.string.analysis_summary_income),
        expenseLabel = stringResource(R.string.analysis_summary_expense),
        emptyText = stringResource(R.string.trip_analysis_empty_body),
    )
}

@Composable
private fun TripCategorySection(
    items: List<TripCategoryActual>,
    days: Long,
    mode: TripBreakdownMode,
    onModeChange: (TripBreakdownMode) -> Unit,
) {
    AnalysisSection(title = stringResource(R.string.trip_detail_breakdown_category), isEmpty = items.isEmpty()) {
        SegmentedControl(
            options = TripBreakdownMode.entries,
            selected = mode,
            label = { it.label() },
            onSelect = onModeChange,
        )
        val maxCents = items.maxOf { abs(it.displayCents(mode, days)) }.coerceAtLeast(1L)
        val totalCents = items.sumOf { abs(it.actualCents) }.coerceAtLeast(1L)
        items.forEach { item ->
            TripBreakdownRow(
                icon = categoryIcon(item.categoryIcon),
                color = categoryColor(item.categoryColor),
                label = item.categoryName ?: stringResource(R.string.common_no_category),
                cents = item.displayCents(mode, days),
                maxCents = maxCents,
                percentOfCents = abs(item.actualCents),
                totalCents = totalCents,
            )
        }
    }
}

@Composable
private fun TripTagSection(
    items: List<TripTagActual>,
    tagsById: Map<String, TagSummary>,
    days: Long,
    mode: TripBreakdownMode,
    onModeChange: (TripBreakdownMode) -> Unit,
) {
    AnalysisSection(title = stringResource(R.string.trip_detail_breakdown_tag), isEmpty = items.isEmpty()) {
        SegmentedControl(
            options = TripBreakdownMode.entries,
            selected = mode,
            label = { it.label() },
            onSelect = onModeChange,
        )
        val maxCents = items.maxOf { abs(it.displayCents(mode, days)) }.coerceAtLeast(1L)
        val totalCents = items.sumOf { abs(it.actualCents) }.coerceAtLeast(1L)
        items.forEach { item ->
            val tag = item.tagId?.let { tagsById[it] }
            TripBreakdownRow(
                icon = categoryIcon(tag?.effectiveIcon()),
                color = categoryColor(tag?.effectiveColor() ?: item.tagColor),
                label = item.tagName ?: stringResource(R.string.trip_analysis_untagged),
                cents = item.displayCents(mode, days),
                maxCents = maxCents,
                percentOfCents = abs(item.actualCents),
                totalCents = totalCents,
            )
        }
    }
}

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
private fun AnalysisSection(
    title: String,
    isEmpty: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
        )
        if (isEmpty) {
            Text(
                text = stringResource(R.string.trip_analysis_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            content()
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
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

@Composable
private fun TripSummary.summaryLine(): String =
    listOfNotNull(
        type.label(),
        dateRange(),
        defaultAccountName?.let { stringResource(R.string.trip_detail_default_account, it) },
    ).joinToString(" / ")

@Composable
private fun TripSummary.dateRange(): String? =
    when {
        startDate != null && endDate != null -> stringResource(R.string.trip_date_range, startDate, endDate)
        startDate != null -> stringResource(R.string.trip_date_ongoing, startDate)
        endDate != null -> endDate
        else -> null
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
private fun List<TripDailyActual>.toChartPoints(): List<IncomeExpenseChartPoint> =
    map { item ->
        IncomeExpenseChartPoint(
            label = formatDayLabel(item.date),
            bucket = item.date,
            incomeCents = 0L,
            expenseCents = item.actualCents,
        )
    }

private fun formatDayLabel(date: String): String =
    try {
        LocalDate.parse(date).dayOfMonth.toString()
    } catch (_: DateTimeParseException) {
        date
    }

private fun TripCategoryActual.displayCents(
    mode: TripBreakdownMode,
    days: Long,
): Long =
    when (mode) {
        TripBreakdownMode.TOTAL -> actualCents
        TripBreakdownMode.AVG_DAY -> averageCents(actualCents, days)
    }

private fun TripTagActual.displayCents(
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
