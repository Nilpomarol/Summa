package com.gestorfinances.app.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.data.repository.TripCategoryActual
import com.gestorfinances.app.data.repository.TripDailyActual
import com.gestorfinances.app.data.repository.TripStatus
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.TripTagActual
import com.gestorfinances.app.data.repository.TripType
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Composable
fun TripsScreen(
    viewModel: TripsViewModel,
    onNewMovement: (TripSummary) -> Unit,
    onManageTags: (String?) -> Unit,
    onManageBudget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    TripsContent(
        state = state,
        modifier = modifier,
        onStatusFilter = viewModel::onStatusFilterChanged,
        onAdd = viewModel::onAddClicked,
        onEdit = viewModel::onEditClicked,
        onArchive = viewModel::onArchiveClicked,
        onDetail = viewModel::onDetailClicked,
        onManageTags = onManageTags,
    )

    state.detail?.let { detail ->
        TripDetailDialog(
            detail = detail,
            onDismiss = viewModel::onDetailDismissed,
            onEdit = { viewModel.onEditClicked(detail.trip) },
            onArchive = { viewModel.onArchiveClicked(detail.trip) },
            onNewMovement = { onNewMovement(detail.trip) },
            onManageTags = { onManageTags(detail.trip.id) },
            onManageBudget = { onManageBudget(detail.trip.id) },
        )
    }

    state.form?.let { form ->
        TripFormDialog(
            form = form,
            accounts = state.accounts,
            onFormChange = viewModel::onFormChanged,
            onDismiss = viewModel::onFormDismissed,
            onSave = viewModel::onSaveClicked,
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

@Composable
private fun TripDetailDialog(
    detail: TripDetailState,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onNewMovement: () -> Unit,
    onManageTags: () -> Unit,
    onManageBudget: () -> Unit,
) {
    val trip = detail.trip
    val days = detail.tripDays()
    var dailyMode by remember(trip.id) { mutableStateOf(TripDailyMode.DAILY) }
    var categoryMode by remember(trip.id) { mutableStateOf(TripBreakdownMode.TOTAL) }
    var tagMode by remember(trip.id) { mutableStateOf(TripBreakdownMode.TOTAL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.trip_detail_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TripDetailHeader(trip = trip)
                DetailLine(
                    label = stringResource(R.string.trip_field_default_account),
                    value = trip.defaultAccountName ?: stringResource(R.string.trip_detail_no_default_account),
                )
                trip.notes?.takeIf { it.isNotBlank() }?.let {
                    DetailLine(label = stringResource(R.string.trip_field_notes), value = it)
                }
                detail.errorMessage?.let {
                    InlineBanner(kind = BannerKind.Error, text = it)
                }
                if (detail.isLoading) {
                    Text(
                        text = stringResource(R.string.trip_loading),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TripKpiGrid(
                    actualCents = detail.summary.actualCents,
                    flowCents = detail.summary.accountOutflowCents,
                    days = days,
                )
                TripDailySection(
                    items = detail.dailyActual,
                    mode = dailyMode,
                    onModeChange = { dailyMode = it },
                )
                TripCategorySection(
                    items = detail.categoryActual,
                    days = days,
                    mode = categoryMode,
                    onModeChange = { categoryMode = it },
                )
                TripTagSection(
                    items = detail.tagActual,
                    days = days,
                    mode = tagMode,
                    onModeChange = { tagMode = it },
                )
                TripMovementSection(items = detail.movements)
                PrimaryButton(
                    text = stringResource(R.string.trip_action_new_movement),
                    onClick = onNewMovement,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onManageTags) {
                    Text(text = stringResource(R.string.trip_action_manage_tags))
                }
                TextButton(onClick = onManageBudget) {
                    Text(text = stringResource(R.string.trip_action_budget))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) {
                Text(text = stringResource(R.string.common_edit))
            }
        },
        dismissButton = {
            DestructiveTextButton(onClick = onArchive) {
                Text(text = stringResource(R.string.common_archive))
            }
        },
    )
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

private enum class TripDailyMode {
    DAILY,
    CUMULATIVE,
}

private enum class TripBreakdownMode {
    TOTAL,
    AVG_DAY,
}

@Composable
private fun TripKpiGrid(
    actualCents: Long,
    flowCents: Long,
    days: Long,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TripKpiCard(
                label = stringResource(R.string.trip_detail_total_actual),
                cents = actualCents,
                modifier = Modifier.weight(1f),
            )
            TripKpiCard(
                label = stringResource(R.string.trip_detail_total_flow),
                cents = flowCents,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TripTextKpiCard(
                label = stringResource(R.string.trip_detail_days),
                value = days.toString(),
                modifier = Modifier.weight(1f),
            )
            TripKpiCard(
                label = stringResource(R.string.trip_detail_avg_day),
                cents = if (days > 0L) actualCents / days else 0L,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TripKpiCard(
    label: String,
    cents: Long,
    modifier: Modifier = Modifier,
) {
    FinanceCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
            )
            MoneyText(
                cents = cents,
                color = FinanceTheme.colors.expense,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun TripTextKpiCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    FinanceCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun TripDailySection(
    items: List<TripDailyActual>,
    mode: TripDailyMode,
    onModeChange: (TripDailyMode) -> Unit,
) {
    AnalysisSection(title = stringResource(R.string.trip_detail_daily_chart), isEmpty = items.isEmpty()) {
        SegmentedControl(
            options = TripDailyMode.entries,
            selected = mode,
            label = { it.label() },
            onSelect = onModeChange,
        )
        val chartItems = items.displayItems(mode)
        val maxCents = chartItems.maxOf { abs(it.actualCents) }.coerceAtLeast(1L)
        chartItems.forEach { item ->
            AmountBarRow(
                label = item.date,
                cents = item.actualCents,
                maxCents = maxCents,
            )
        }
    }
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
        items.forEach { item ->
            AmountBarRow(
                label = item.categoryName ?: stringResource(R.string.common_no_category),
                cents = item.displayCents(mode, days),
                maxCents = maxCents,
                supporting = if (mode == TripBreakdownMode.TOTAL) averagePerDay(item.actualCents, days) else null,
            )
        }
    }
}

@Composable
private fun TripTagSection(
    items: List<TripTagActual>,
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
        items.forEach { item ->
            AmountBarRow(
                label = item.tagName ?: stringResource(R.string.trip_analysis_untagged),
                cents = item.displayCents(mode, days),
                maxCents = maxCents,
                supporting = if (mode == TripBreakdownMode.TOTAL) averagePerDay(item.actualCents, days) else null,
            )
        }
    }
}

@Composable
private fun TripMovementSection(items: List<MovementSummary>) {
    AnalysisSection(title = stringResource(R.string.trip_detail_movements), isEmpty = items.isEmpty()) {
        items.forEach { movement ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movement.displayTitle(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = listOfNotNull(movement.date, movement.categoryName, movement.tagName)
                            .joinToString(" / "),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                MoneyText(
                    cents = movement.signedAmountCents(),
                    color = FinanceTheme.colors.amountColor(movement.type),
                    style = MaterialTheme.typography.bodyMedium,
                    signed = movement.type != MovementType.EXPENSE && movement.type != MovementType.TRANSFER,
                )
            }
        }
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
private fun AmountBarRow(
    label: String,
    cents: Long,
    maxCents: Long,
    supporting: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                supporting?.let {
                    Text(
                        text = it,
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            MoneyText(
                cents = cents,
                color = FinanceTheme.colors.expense,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((abs(cents).toFloat() / maxCents.toFloat()).coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(FinanceTheme.colors.expense, RoundedCornerShape(50)),
            )
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
private fun TripFormDialog(
    form: TripFormState,
    accounts: List<AccountSummary>,
    onFormChange: (TripFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (form.id == null) R.string.trip_form_new_title else R.string.trip_form_edit_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                form.errorRes?.let {
                    InlineBanner(kind = BannerKind.Error, text = stringResource(it))
                }
                form.errorMessage?.let {
                    InlineBanner(kind = BannerKind.Error, text = it)
                }
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onFormChange(form.copy(name = it)) },
                    label = { Text(text = stringResource(R.string.trip_field_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
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
                OutlinedTextField(
                    value = form.startDate,
                    onValueChange = { onFormChange(form.copy(startDate = it)) },
                    label = { Text(text = stringResource(R.string.trip_field_start_date)) },
                    supportingText = { Text(text = stringResource(R.string.movement_date_format_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.endDate,
                    onValueChange = { onFormChange(form.copy(endDate = it)) },
                    label = { Text(text = stringResource(R.string.trip_field_end_date)) },
                    supportingText = { Text(text = stringResource(R.string.movement_date_format_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipFlowSection(label = stringResource(R.string.trip_field_default_account)) {
                    FinanceFilterChip(
                        selected = form.defaultAccountId == null,
                        label = stringResource(R.string.trip_detail_no_default_account),
                        onClick = { onFormChange(form.copy(defaultAccountId = null)) },
                    )
                    accounts.forEach { account ->
                        FinanceFilterChip(
                            selected = form.defaultAccountId == account.id,
                            label = account.name,
                            onClick = { onFormChange(form.copy(defaultAccountId = account.id)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = form.icon,
                    onValueChange = { onFormChange(form.copy(icon = it)) },
                    label = { Text(text = stringResource(R.string.trip_field_icon)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.color,
                    onValueChange = { onFormChange(form.copy(color = it)) },
                    label = { Text(text = stringResource(R.string.trip_field_color)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { onFormChange(form.copy(notes = it)) },
                    label = { Text(text = stringResource(R.string.trip_field_notes)) },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(
                    if (form.id == null) R.string.trip_save_new else R.string.trip_save_changes,
                ),
                onClick = onSave,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
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
private fun TripType.label(): String =
    stringResource(
        when (this) {
            TripType.TRIP -> R.string.trip_type_trip
            TripType.CELEBRATION -> R.string.trip_type_celebration
            TripType.OTHER -> R.string.trip_type_other
        },
    )

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

private fun TripType.icon() =
    when (this) {
        TripType.TRIP -> Icons.Outlined.Flight
        TripType.CELEBRATION -> Icons.Outlined.Celebration
        TripType.OTHER -> Icons.Outlined.Event
    }

private fun TripDetailState.tripDays(): Long {
    val start = trip.startDate?.let(::parseDateOrNull)
        ?: dailyActual.firstOrNull()?.date?.let(::parseDateOrNull)
    val end = trip.endDate?.let(::parseDateOrNull)
        ?: dailyActual.lastOrNull()?.date?.let(::parseDateOrNull)
        ?: start
    if (start == null || end == null || end < start) return 1L
    return ChronoUnit.DAYS.between(start, end).coerceAtLeast(0L) + 1L
}

private fun List<TripDailyActual>.displayItems(mode: TripDailyMode): List<TripDailyActual> {
    if (mode == TripDailyMode.DAILY) return this
    var runningTotal = 0L
    return map { item ->
        runningTotal += item.actualCents
        item.copy(actualCents = runningTotal)
    }
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
private fun averagePerDay(cents: Long, days: Long): String =
    stringResource(
        R.string.trip_avg_day_value,
        formatEuroCents(averageCents(cents, days)),
    )

@Composable
private fun TripDailyMode.label(): String =
    stringResource(
        when (this) {
            TripDailyMode.DAILY -> R.string.trip_analysis_mode_daily
            TripDailyMode.CUMULATIVE -> R.string.trip_analysis_mode_cumulative
        },
    )

@Composable
private fun TripBreakdownMode.label(): String =
    stringResource(
        when (this) {
            TripBreakdownMode.TOTAL -> R.string.trip_analysis_mode_total
            TripBreakdownMode.AVG_DAY -> R.string.trip_analysis_mode_avg_day
        },
    )

private fun MovementSummary.displayTitle(): String =
    name ?: payee ?: categoryName ?: type.name.lowercase()

private fun MovementSummary.signedAmountCents(): Long =
    when (type) {
        MovementType.EXPENSE -> -amountCents
        MovementType.SETTLEMENT ->
            if (settlementDirection == SettlementDirection.USER_TO_PERSON) -amountCents else amountCents
        else -> amountCents
    }

private fun parseDateOrNull(raw: String): LocalDate? =
    try {
        LocalDate.parse(raw)
    } catch (_: RuntimeException) {
        null
    }
