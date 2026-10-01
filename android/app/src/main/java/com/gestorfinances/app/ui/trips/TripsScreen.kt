package com.gestorfinances.app.ui.trips

import com.gestorfinances.app.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.gestorfinances.app.ui.common.ListPage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import com.gestorfinances.app.ui.common.progressFraction
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.IdentityIconTile
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.ListFilterBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.EntityAppearancePickers
import com.gestorfinances.app.ui.common.EntityFormHeader
import com.gestorfinances.app.ui.common.EntityFormSheet
import com.gestorfinances.app.ui.common.FormDisclosure
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.data.repository.TripStatus
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.TripType
import com.gestorfinances.app.data.repository.dayCount
import com.gestorfinances.app.data.repository.icon
import com.gestorfinances.app.data.repository.label
import com.gestorfinances.app.ui.common.CategoryIconPalette
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.color
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.formatExpandedDate
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.common.InlineFailureBanner

/** This page visit's ViewModel, scoped to its navigation entry. */
@Composable
fun tripsViewModel(appContainer: AppContainer): TripsViewModel = viewModel {
    TripsViewModel(
        tripRepository = appContainer.tripRepository,
        tripAnalysisRepository = appContainer.tripAnalysisRepository,
        movementRepository = appContainer.movementRepository,
        accountRepository = appContainer.accountRepository,
        budgetRepository = appContainer.budgetRepository,
        tagRepository = appContainer.tagRepository,
    )
}

@Composable
fun TripsScreen(
    onBack: () -> Unit,
    viewModel: TripsViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onOpenDetail: (TripSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, dataVersion) {
        viewModel.onScreenShown()
    }

    TripsContent(
        onBack = onBack,
        state = state,
        modifier = modifier,
        onAdd = viewModel::onAddClicked,
        onDetail = onOpenDetail,
        onRetry = viewModel::onScreenShown,
    )
    TripFormSheet(state = state, viewModel = viewModel)
}

@Composable
internal fun TripsContent(
    onBack: () -> Unit,
    state: TripsUiState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onDetail: (TripSummary) -> Unit,
    onRetry: () -> Unit,
) {
    var type by rememberSaveable { mutableStateOf<TripType?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val visible = state.trips.filter {
        (type == null || it.type == type) && it.name.contains(query.trim(), ignoreCase = true)
    }
    ListPage(
        title = stringResource(R.string.trip_list_title),
        onBack = onBack,
        addLabel = stringResource(R.string.trip_list_add),
        onAdd = onAdd,
        modifier = modifier,
    ) {
        state.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_trips,
                    onRetry = onRetry,
                )
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
            return@ListPage
        }
        if (state.trips.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.trip_empty_title),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@ListPage
        }

        // Only the kinds in use; with one kind and a handful of trips there is nothing to narrow.
        val types = state.trips.map { it.type }.distinct().sortedBy { it.ordinal }
        if (types.size > 1 || state.trips.size > TRIPS_BEFORE_SEARCH) item {
            ListFilterBar(
                query = query,
                onQueryChange = { query = it },
                searchPlaceholder = stringResource(R.string.trip_search_placeholder),
            ) {
                if (types.size > 1) {
                    FinanceFilterChip(selected = type == null, label = stringResource(R.string.trip_filter_all_types), onClick = { type = null })
                    types.forEach { option ->
                        FinanceFilterChip(selected = type == option, label = option.label(), onClick = { type = option })
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.trip_search_empty),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        // What is under way first, then what is coming, then what is done.
        listOf(TripStatus.ACTIVE, TripStatus.PLANNED, TripStatus.FINISHED).forEach { status ->
            val trips = visible.filter { it.status == status }
            if (trips.isNotEmpty()) {
                item(key = "section-${status.name}") { SectionHeader(title = status.sectionLabel()) }
                items(items = trips, key = { it.id }) { trip ->
                    TripCard(
                        trip = trip,
                        budget = state.budgetByTrip[trip.id],
                        onClick = { onDetail(trip) },
                    )
                }
            }
        }
    }
}

/**
 * A trip as a simple card: its mark, name and dates, what it cost and per day, and its budget as
 * a thin bar when it has one.
 */
@Composable
private fun TripCard(trip: TripSummary, budget: BudgetEvaluation?, onClick: () -> Unit) {
    val days = trip.dayCount()
    FinanceCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IdentityIconTile(icon = trip.type.icon(), color = categoryColor(trip.color))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = trip.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        TripStatusTag(status = trip.status)
                    }
                    Text(
                        text = listOfNotNull(
                            trip.dateRange() ?: trip.type.label(),
                            pluralStringResource(R.plurals.trip_card_days, days.toInt(), days.toInt()).takeIf { days > 0 },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FinanceTheme.colors.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(
                        cents = trip.totalActualCents,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (days > 0) {
                        Text(
                            text = stringResource(R.string.trip_row_avg_day, formatEuroCents(averageCents(trip.totalActualCents, days))),
                            style = MaterialTheme.typography.bodySmall,
                            color = FinanceTheme.colors.mutedText,
                        )
                    }
                }
            }
            budget?.let { evaluation ->
                Spacer(modifier = Modifier.height(14.dp))
                BudgetProgressBar(fraction = evaluation.progressFraction(), color = evaluation.status.color())
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.trip_card_budget_used,
                        percentOf(evaluation.actualCents, evaluation.budget.limitAmountCents),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceTheme.colors.mutedText,
                )
            }
        }
    }
}

/** The trip's status as a small tag in its own colours. */
@Composable
private fun TripStatusTag(status: TripStatus) {
    val colors = FinanceTheme.colors
    val (container, content) = when (status) {
        TripStatus.PLANNED -> colors.tripPlannedContainer to colors.tripPlannedContent
        TripStatus.ACTIVE -> colors.tripActiveContainer to colors.tripActiveContent
        TripStatus.FINISHED -> colors.tripFinishedContainer to colors.tripFinishedContent
    }
    Text(
        text = status.label(),
        color = content,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = Modifier
            .background(container, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private const val TRIPS_BEFORE_SEARCH = 5

private fun percentOf(part: Long, whole: Long): Long = if (whole > 0L) part * 100 / whole else 0L

/** The trip create/edit sheet over whichever trip page opened it. */
@Composable
internal fun TripFormSheet(state: TripsUiState, viewModel: TripsViewModel) {
    var appearanceOpen by rememberSaveable(state.form?.id) { mutableStateOf(false) }
    var detailsOpen by rememberSaveable(state.form?.id) { mutableStateOf(false) }
    EntityFormSheet(
        form = state.form,
        key = { it.id ?: "new-trip" },
        changed = { initial, current -> initial.withoutErrors() != current.withoutErrors() },
        onDiscard = viewModel::onFormDismissed,
        onSave = viewModel::onSaveClicked,
        title = { stringResource(if (it.id == null) R.string.trip_form_new_title else R.string.trip_form_edit_title) },
        saveLabel = { stringResource(if (it.id == null) R.string.trip_save_new else R.string.trip_save_changes) },
    ) { form ->
        val edit: (TripFormState) -> Unit = { viewModel.onFormChanged(it.withoutErrors()) }
        val errorText = form.errorRes?.let { stringResource(it) }
        form.errorMessage?.let {
            InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_trip)
        }
        EntityFormHeader(
            icon = form.icon.ifBlank { null }?.let(::categoryIcon) ?: form.type.icon(),
            color = categoryColor(form.color.ifBlank { null }),
            name = form.name,
            onNameChange = { edit(form.copy(name = it)) },
            nameLabel = stringResource(R.string.trip_field_name),
            onTileClick = { appearanceOpen = !appearanceOpen },
            nameError = errorText?.takeIf { form.errorField == TripFormField.NAME },
            imeAction = ImeAction.Done,
            keyboardActions = doneKeyboardActions(viewModel::onSaveClicked),
        )
        EntityAppearancePickers(
            open = appearanceOpen,
            colorHex = form.color.ifBlank { null },
            onColor = { edit(form.copy(color = it)) },
            iconOptions = CategoryIconPalette,
            iconKey = form.icon.ifBlank { null },
            onIcon = { edit(form.copy(icon = it)) },
        )
        LabeledSegmentedControl(
            label = stringResource(R.string.trip_field_type),
            options = TripType.entries,
            selected = form.type,
            optionLabel = { it.label() },
            onSelect = { edit(form.copy(type = it)) },
        )
        LabeledSegmentedControl(
            label = stringResource(R.string.trip_field_status),
            options = TripStatus.entries,
            selected = form.status,
            optionLabel = { it.label() },
            onSelect = { edit(form.copy(status = it)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val startDateError = form.errorField == TripFormField.START_DATE
            val endDateError = form.errorField == TripFormField.END_DATE
            FormDatePicker(
                label = stringResource(R.string.trip_field_start_date),
                date = form.startDate,
                onDateChange = { edit(form.copy(startDate = it)) },
                modifier = Modifier.weight(1f).scrollToWhen(startDateError),
                isError = startDateError,
                supportingText = errorText?.takeIf { startDateError },
            )
            FormDatePicker(
                label = stringResource(R.string.trip_field_end_date),
                date = form.endDate,
                onDateChange = { edit(form.copy(endDate = it)) },
                modifier = Modifier.weight(1f).scrollToWhen(endDateError),
                isError = endDateError,
                supportingText = errorText?.takeIf { endDateError },
            )
        }
        val accountError = form.errorField == TripFormField.ACCOUNT
        FormSelect(
            label = stringResource(R.string.trip_field_default_account),
            options = listOf(SelectOption(id = null, label = stringResource(R.string.trip_detail_no_default_account))) +
                state.accounts.map { SelectOption(id = it.id, label = it.name) },
            selectedId = form.defaultAccountId,
            onSelect = { edit(form.copy(defaultAccountId = it)) },
            placeholder = stringResource(R.string.trip_detail_no_default_account),
            modifier = Modifier.scrollToWhen(accountError),
            isError = accountError,
            supportingText = errorText?.takeIf { accountError },
        )
        // Notes already written stay in view.
        val notesShown = detailsOpen || form.notes.isNotBlank()
        FormDisclosure(open = notesShown, onToggle = { detailsOpen = !detailsOpen }) {
            OutlinedTextField(
                value = form.notes,
                onValueChange = { edit(form.copy(notes = it)) },
                label = { Text(text = stringResource(R.string.trip_field_notes)) },
                minLines = 2,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun TripFormState.withoutErrors(): TripFormState = copy(errorRes = null, errorField = null, errorMessage = null)

/** Muted meta line under the detail page title: date range · day count · status. */
@Composable
internal fun TripSummary.detailMetaLine(days: Long): String =
    listOfNotNull(
        dateRange(),
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
private fun TripStatus.sectionLabel(): String =
    stringResource(
        when (this) {
            TripStatus.PLANNED -> R.string.trip_filter_planned
            TripStatus.ACTIVE -> R.string.trip_filter_active
            TripStatus.FINISHED -> R.string.trip_filter_finished
        },
    )

internal fun averageCents(cents: Long, days: Long): Long =
    if (days > 0L) cents / days else 0L
