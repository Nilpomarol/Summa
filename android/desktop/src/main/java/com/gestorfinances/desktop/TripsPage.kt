package com.gestorfinances.desktop

import com.gestorfinances.app.ui.common.OpenDialogs
import com.gestorfinances.ui.resources.trip_detail_no_tag
import com.gestorfinances.ui.resources.common_no_category
import com.gestorfinances.ui.resources.budget_field_total
import com.gestorfinances.desktop.resources.trips_day_table_hint
import com.gestorfinances.desktop.resources.trips_day_table
import com.gestorfinances.desktop.resources.trips_by_tag
import com.gestorfinances.desktop.resources.analysis_months_by_category
import com.gestorfinances.app.data.repository.effectiveColor
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.data.repository.TripDayTagActual
import com.gestorfinances.app.data.repository.TripDayCategoryActual
import com.gestorfinances.app.data.repository.TripAnalysisRepository
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import com.gestorfinances.ui.resources.movement_field_tag
import kotlin.math.abs
import java.time.temporal.ChronoUnit
import com.gestorfinances.ui.resources.trip_split_during
import com.gestorfinances.ui.resources.trip_split_before
import com.gestorfinances.ui.resources.trip_split_after
import com.gestorfinances.ui.resources.trip_row_avg_day
import com.gestorfinances.ui.resources.trip_detail_daily_chart
import com.gestorfinances.ui.resources.trip_chart_budget_per_day
import com.gestorfinances.ui.resources.trip_chart_before
import com.gestorfinances.ui.resources.trip_chart_average_per_day
import com.gestorfinances.ui.resources.trip_chart_after
import com.gestorfinances.ui.resources.budget_remaining
import com.gestorfinances.ui.resources.budget_over
import com.gestorfinances.ui.resources.analysis_row_share
import com.gestorfinances.desktop.resources.trips_starts_tomorrow
import com.gestorfinances.desktop.resources.trips_starts_in
import com.gestorfinances.desktop.resources.trips_hero_year
import com.gestorfinances.desktop.resources.trips_hero_per_trip
import com.gestorfinances.desktop.resources.trips_hero_per_day
import com.gestorfinances.desktop.resources.trips_hero_all
import com.gestorfinances.desktop.resources.trips_group_planned
import com.gestorfinances.desktop.resources.trips_group_finished
import com.gestorfinances.desktop.resources.trips_group_active
import com.gestorfinances.desktop.resources.trips_day_of
import com.gestorfinances.desktop.resources.trips_day_empty
import com.gestorfinances.desktop.resources.trips_day
import com.gestorfinances.desktop.resources.trips_add_budget
import com.gestorfinances.desktop.resources.column_name
import com.gestorfinances.desktop.resources.column_date
import com.gestorfinances.desktop.resources.column_category
import com.gestorfinances.desktop.resources.column_amount
import com.gestorfinances.desktop.resources.column_account
import com.gestorfinances.app.ui.trips.TripDayGroup
import com.gestorfinances.app.ui.trips.BreakdownEntry
import com.gestorfinances.app.ui.common.parseIsoDateOrNull
import com.gestorfinances.app.ui.common.heroTint
import com.gestorfinances.app.ui.common.formatPercentLabel
import com.gestorfinances.app.ui.common.ListHero
import com.gestorfinances.app.ui.common.HeroStatBox
import com.gestorfinances.app.ui.common.HeroCaption
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.gestorfinances.app.ui.common.AppIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.gestorfinances.app.ui.common.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.TripStatus
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.TripType
import com.gestorfinances.app.data.repository.dayCount
import com.gestorfinances.app.ui.budgets.BudgetSheetHost
import com.gestorfinances.app.ui.budgets.BudgetsViewModel
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.DestructiveButton
import com.gestorfinances.app.ui.common.EntityBudgetBar
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SearchField
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.color
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.movementTitle
import com.gestorfinances.app.ui.common.movementTypeIcon
import com.gestorfinances.app.ui.common.progressFraction
import com.gestorfinances.app.ui.common.signedAmountCents
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.movementAmountColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import com.gestorfinances.app.ui.trips.TripBreakdownSection
import com.gestorfinances.app.ui.trips.TripDailyChart
import com.gestorfinances.app.ui.trips.TripDetailState
import com.gestorfinances.app.ui.trips.TripFormSheet
import com.gestorfinances.app.ui.trips.TripStatusTag
import com.gestorfinances.app.ui.trips.TripsUiState
import com.gestorfinances.app.ui.trips.TripsViewModel
import com.gestorfinances.app.ui.trips.averageCents
import com.gestorfinances.app.ui.trips.buildDayGroups
import com.gestorfinances.app.ui.trips.dateRange
import com.gestorfinances.app.ui.trips.dayLabel
import com.gestorfinances.app.ui.trips.detailMetaLine
import com.gestorfinances.app.ui.trips.icon
import com.gestorfinances.app.ui.trips.label
import com.gestorfinances.app.ui.trips.percentOf
import com.gestorfinances.app.ui.trips.toBreakdownEntry
import com.gestorfinances.app.ui.trips.tripDailyBars
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.desktop.resources.column_budget
import com.gestorfinances.desktop.resources.column_cost
import com.gestorfinances.desktop.resources.column_dates
import com.gestorfinances.desktop.resources.column_days
import com.gestorfinances.desktop.resources.column_per_day
import com.gestorfinances.desktop.resources.column_status
import com.gestorfinances.desktop.resources.column_trip
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_back
import com.gestorfinances.ui.resources.common_cancel
import com.gestorfinances.ui.resources.common_edit
import com.gestorfinances.ui.resources.common_retry
import com.gestorfinances.ui.resources.entity_add_movement
import com.gestorfinances.ui.resources.failure_load_trips
import com.gestorfinances.ui.resources.trip_action_budget
import com.gestorfinances.ui.resources.trip_analysis_empty_body
import com.gestorfinances.ui.resources.trip_analysis_mode_avg_day
import com.gestorfinances.ui.resources.trip_archive_confirm_title
import com.gestorfinances.ui.resources.trip_archive_warning
import com.gestorfinances.ui.resources.trip_card_budget_used
import com.gestorfinances.ui.resources.trip_detail_by_tag
import com.gestorfinances.ui.resources.trip_detail_exclude_one_time_short
import com.gestorfinances.ui.resources.trip_detail_include_one_time_short
import com.gestorfinances.ui.resources.trip_detail_top_categories
import com.gestorfinances.ui.resources.trip_detail_total_actual
import com.gestorfinances.ui.resources.trip_empty_title
import com.gestorfinances.ui.resources.trip_filter_all_types
import com.gestorfinances.ui.resources.trip_list_add
import com.gestorfinances.ui.resources.trip_list_title
import com.gestorfinances.ui.resources.trip_search_empty
import com.gestorfinances.ui.resources.trip_search_placeholder
import com.gestorfinances.ui.resources.trip_tab_movements
import java.time.LocalDate
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

/**
 * Trips as a page of tables, one per state (what is under way, what is coming, what is done), under
 * what they came to on the dark panel; a row opens the trip's own page, which shows on one screen
 * what the phone splits into two tabs.
 */
@Composable
fun TripsPage(
    viewModel: TripsViewModel,
    tripAnalysis: TripAnalysisRepository,
    budgets: BudgetsViewModel,
    dataVersion: Long,
    /** The trip whose page is showing, or null for the tables; the shell holds it so other pages can open a trip. */
    openTripId: String?,
    onOpenTrip: (String?) -> Unit,
    onOpenTag: (tagId: String) -> Unit,
    onAddMovement: (tripId: String) -> Unit,
    onOpenMovement: (MovementSummary) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel, dataVersion) { viewModel.onScreenShown() }
    LaunchedEffect(viewModel, openTripId, dataVersion) { openTripId?.let(viewModel::onDetailOpened) }

    val detail = state.detail?.takeIf { it.trip.id == openTripId && !it.isLoading }
    if (openTripId == null) {
        TripList(state, viewModel, onOpen = { onOpenTrip(it.id) })
    } else if (detail != null) {
        // What each day spent in each category and under each tag: canonical figures, a day at a time.
        val byDay by produceState<TripDayBreakdown?>(null, detail.trip.id, detail.excludeOneTime, dataVersion) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    TripDayBreakdown(
                        categories = tripAnalysis.actualByDayByCategory(detail.trip.id, detail.excludeOneTime),
                        tags = tripAnalysis.actualByDayByTag(detail.trip.id, detail.excludeOneTime),
                    )
                }.getOrNull()
            }
        }
        TripPage(
            detail = detail,
            byDay = byDay,
            viewModel = viewModel,
            onBack = { onOpenTrip(null) },
            onOpenTag = onOpenTag,
            onManageBudget = { budgets.editBudgetFor(tripId = detail.trip.id) },
            onAddMovement = { onAddMovement(detail.trip.id) },
            onOpenMovement = onOpenMovement,
        )
    }

    TripFormSheet(state = state, viewModel = viewModel)
    BudgetSheetHost(viewModel = budgets, onChanged = { openTripId?.let(viewModel::onDetailOpened) }, onDeleteCommitted = {})
    state.archiveCandidate?.let {
        OpenDialogs.Track()
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(stringResource(SharedRes.string.trip_archive_confirm_title)) },
            text = { Text(stringResource(SharedRes.string.trip_archive_warning)) },
            confirmButton = {
                AppTextButton(onClick = { viewModel.onArchiveConfirmed { onOpenTrip(null) } }) {
                    Text(stringResource(SharedRes.string.common_archive), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { AppTextButton(onClick = viewModel::onArchiveDismissed) { Text(stringResource(SharedRes.string.common_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripList(state: TripsUiState, viewModel: TripsViewModel, onOpen: (TripSummary) -> Unit) {
    val muted = FinanceTheme.colors.mutedText
    var type by remember { mutableStateOf<TripType?>(null) }
    var query by remember { mutableStateOf("") }
    val today = remember { LocalDate.now() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = TripColumns(dates = maxWidth >= 600.dp, days = maxWidth >= 820.dp, perDay = maxWidth >= 940.dp)
        ScrollPage {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(SharedRes.string.trip_list_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                PrimaryButton(text = stringResource(SharedRes.string.trip_list_add), onClick = viewModel::onAddClicked, leadingIcon = Icons.Outlined.Add)
            }
            if (state.errorMessage != null) {
                InlineBanner(
                    kind = BannerKind.Error,
                    text = stringResource(SharedRes.string.failure_load_trips),
                    actionLabel = stringResource(SharedRes.string.common_retry),
                    onAction = viewModel::onScreenShown,
                )
            }
            if (state.isLoading) return@ScrollPage
            if (state.trips.isEmpty()) {
                Text(stringResource(SharedRes.string.trip_empty_title), color = muted)
                return@ScrollPage
            }
            TripsHero(state, today)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(SharedRes.string.trip_search_placeholder),
                    modifier = Modifier.width(300.dp).padding(end = 8.dp),
                )
                val types = state.trips.map { it.type }.distinct().sortedBy { it.ordinal }
                if (types.size > 1) {
                    FinanceFilterChip(selected = type == null, label = stringResource(SharedRes.string.trip_filter_all_types), onClick = { type = null })
                    types.forEach { option -> FinanceFilterChip(selected = type == option, label = option.label(), onClick = { type = option }) }
                }
            }
            val trips = state.trips.filter { (type == null || it.type == type) && it.name.contains(query.trim(), ignoreCase = true) }
            if (trips.isEmpty()) {
                Text(stringResource(SharedRes.string.trip_search_empty), color = muted)
                return@ScrollPage
            }
            // Each state is a table of its own, under its name and how many it holds.
            listOf(
                TripStatus.ACTIVE to Res.string.trips_group_active,
                TripStatus.PLANNED to Res.string.trips_group_planned,
                TripStatus.FINISHED to Res.string.trips_group_finished,
            ).forEach { (status, title) ->
                val rows = trips.filter { it.status == status }
                if (rows.isEmpty()) return@forEach
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                        Text(rows.size.toString(), style = MaterialTheme.typography.bodyMedium, color = muted)
                    }
                    TripTable(rows, state, columns, today, onOpen)
                }
            }
        }
    }
}

/**
 * What the year's trips came to on the dark panel (every trip, when none falls in this year), with
 * how many they were and what one costs, and under it the trip under way or the next one.
 */
@Composable
private fun TripsHero(state: TripsUiState, today: LocalDate) {
    val colors = FinanceTheme.colors
    val thisYear = state.trips.filter { (it.startDate ?: it.endDate)?.startsWith(today.year.toString()) == true }
    val counted = thisYear.ifEmpty { state.trips }
    val total = counted.sumOf { it.totalActualCents }
    val days = counted.sumOf { it.dayCount() }
    val active = state.trips.firstOrNull { it.status == TripStatus.ACTIVE }
    val next = state.trips.filter { it.status == TripStatus.PLANNED && it.startDate != null }.minByOrNull { it.startDate!! }
    ListHero(
        eyebrow = if (thisYear.isEmpty()) stringResource(Res.string.trips_hero_all) else stringResource(Res.string.trips_hero_year, today.year),
        cents = total,
        watermark = Icons.Outlined.Flight,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroTextBox(stringResource(SharedRes.string.trip_list_title), counted.size.toString())
            HeroStatBox(stringResource(Res.string.trips_hero_per_trip), total / counted.size.coerceAtLeast(1), colors.heroOnSurface)
            if (days > 0L) HeroStatBox(stringResource(Res.string.trips_hero_per_day), total / days, colors.heroOnSurface)
        }
        val lead = active ?: next
        if (lead != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IdentityIconTile(icon = lead.icon?.let(::categoryIcon) ?: lead.type.icon(), color = categoryColor(lead.color), size = 24.dp)
                HeroCaption(listOfNotNull(lead.name, lead.whenLine(today)?.first, formatEuroCents(lead.totalActualCents)).joinToString(" · "))
            }
        }
    }
}

/** A count or other plain text in a tinted box on the hero, beside the boxes of amounts. */
@Composable
private fun RowScope.HeroTextBox(label: String, text: String) {
    val colors = FinanceTheme.colors
    Column(
        Modifier
            .weight(1f)
            .background(heroTint(colors.heroOnSurface, 0.14f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = colors.heroOnSurface.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        Text(text, color = colors.heroOnSurface, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Where a trip stands in time, and the colour for it: how long until it starts, or which of its
 * days today is. Null once it is over, or with no dates to tell by.
 */
@Composable
private fun TripSummary.whenLine(today: LocalDate): Pair<String, Color>? {
    val colors = FinanceTheme.colors
    val start = startDate?.let(::parseIsoDateOrNull) ?: return null
    val end = endDate?.let(::parseIsoDateOrNull)
    val until = ChronoUnit.DAYS.between(today, start)
    return when {
        until > 1L -> stringResource(Res.string.trips_starts_in, until) to if (until <= 7L) colors.alert else colors.mutedText
        until == 1L -> stringResource(Res.string.trips_starts_tomorrow) to colors.alert
        end != null && today.isAfter(end) -> null
        end != null -> stringResource(Res.string.trips_day_of, -until + 1, ChronoUnit.DAYS.between(start, end) + 1) to colors.income
        else -> stringResource(Res.string.trips_day, -until + 1) to colors.income
    }
}

/** Which columns the window has room for; the rest are dropped rather than squeezed. */
private data class TripColumns(val dates: Boolean, val days: Boolean, val perDay: Boolean)

@Composable
private fun TripTable(trips: List<TripSummary>, state: TripsUiState, columns: TripColumns, today: LocalDate, onOpen: (TripSummary) -> Unit) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val header = MaterialTheme.typography.labelMedium
    FinanceCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.column_trip), Modifier.weight(2f), style = header, color = muted)
            if (columns.dates) Text(stringResource(Res.string.column_dates), Modifier.weight(2f), style = header, color = muted)
            if (columns.days) Text(stringResource(Res.string.column_days), Modifier.width(48.dp), style = header, color = muted, textAlign = TextAlign.End)
            Text(stringResource(Res.string.column_cost), Modifier.width(104.dp), style = header, color = muted, textAlign = TextAlign.End)
            if (columns.perDay) Text(stringResource(Res.string.column_per_day), Modifier.width(96.dp), style = header, color = muted, textAlign = TextAlign.End)
            Text(stringResource(Res.string.column_budget), Modifier.width(168.dp), style = header, color = muted)
        }
        trips.forEach { trip ->
            HorizontalDivider(color = colors.cardBorder)
            val days = trip.dayCount()
            val whenLine = trip.whenLine(today)
            Row(
                Modifier
                    .fillMaxWidth()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onOpen(trip) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(2f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IdentityIconTile(icon = trip.icon?.let(::categoryIcon) ?: trip.type.icon(), color = categoryColor(trip.color), size = 32.dp)
                    Column {
                        Text(trip.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // Without the dates' column, how soon it is goes under the name.
                        if (!columns.dates) whenLine?.let { (text, color) -> Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium) }
                    }
                }
                if (columns.dates) {
                    Column(Modifier.weight(2f)) {
                        Text(trip.dateRange() ?: trip.type.label(), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        whenLine?.let { (text, color) -> Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium) }
                    }
                }
                if (columns.days) Text(if (days > 0) days.toString() else "—", Modifier.width(48.dp), color = muted, textAlign = TextAlign.End)
                Text(
                    formatEuroCents(trip.totalActualCents),
                    Modifier.width(104.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                if (columns.perDay) {
                    Text(
                        if (days > 0) formatEuroCents(averageCents(trip.totalActualCents, days)) else "—",
                        Modifier.width(96.dp),
                        color = muted,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
                Column(Modifier.width(168.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.budgetByTrip[trip.id]?.let { evaluation ->
                        BudgetProgressBar(fraction = evaluation.progressFraction(), color = evaluation.status.color())
                        Text(
                            if (evaluation.remainingCents >= 0L) {
                                stringResource(SharedRes.string.budget_remaining, formatEuroCents(evaluation.remainingCents))
                            } else {
                                stringResource(SharedRes.string.budget_over, formatEuroCents(-evaluation.remainingCents))
                            } + " · " + stringResource(SharedRes.string.analysis_row_share, percentOf(evaluation.actualCents, evaluation.budget.limitAmountCents).toInt()),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = evaluation.status.color(),
                            maxLines = 1,
                        )
                    } ?: Text("—", color = muted)
                }
            }
        }
    }
}

/** A trip's spend by day, split by category and by tag. */
private class TripDayBreakdown(val categories: List<TripDayCategoryActual>, val tags: List<TripDayTagActual>)

/** A bar of the daily chart: what it is called under it and in full, its spend, and the dates it stands for. */
private class DaySlot(val label: String, val title: String, val cents: Long, val dates: Set<String>, val outside: Boolean)

/**
 * A trip on one screen: its figures straight on the page, spend per day over its movements by day,
 * and where the money went beside them. A day picked on the chart narrows the movements to it.
 */
@Composable
private fun TripPage(
    detail: TripDetailState,
    byDay: TripDayBreakdown?,
    viewModel: TripsViewModel,
    onBack: () -> Unit,
    onOpenTag: (String) -> Unit,
    onManageBudget: () -> Unit,
    onAddMovement: () -> Unit,
    onOpenMovement: (MovementSummary) -> Unit,
) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val trip = detail.trip
    val days = trip.dayCount(
        fallbackStart = detail.dailyActual.firstOrNull()?.date,
        fallbackEnd = detail.dailyActual.lastOrNull()?.date,
    )
    val today = remember { LocalDate.now() }
    val groups = remember(detail) { buildDayGroups(trip, detail.dailyActual, detail.movements) { it.date } }
    val bars = remember(detail) { tripDailyBars(trip, detail.dailyActual) }
    val beforeLabel = stringResource(SharedRes.string.trip_chart_before)
    val afterLabel = stringResource(SharedRes.string.trip_chart_after)
    val slots = buildList {
        if (bars.before.isNotEmpty()) add(DaySlot(beforeLabel, beforeLabel, bars.beforeCents, bars.before.map { it.date }.toSet(), outside = true))
        bars.days.forEach { day ->
            val label = day.ordinal?.toString() ?: parseIsoDateOrNull(day.date)?.dayOfMonth?.toString().orEmpty()
            add(DaySlot(label, dayLabel(day.ordinal, day.date), day.cents, setOf(day.date), outside = false))
        }
        if (bars.after.isNotEmpty()) add(DaySlot(afterLabel, afterLabel, bars.afterCents, bars.after.map { it.date }.toSet(), outside = true))
    }
    var picked by remember(trip.id) { mutableStateOf<Int?>(null) }
    val pickedSlot = picked?.let(slots::getOrNull)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        ScrollPage {
            val actions = @Composable {
                SecondaryButton(text = stringResource(SharedRes.string.common_edit), onClick = { viewModel.onEditClicked(trip) })
                DestructiveButton(text = stringResource(SharedRes.string.common_archive), onClick = { viewModel.onArchiveClicked(trip) })
                PrimaryButton(text = stringResource(SharedRes.string.entity_add_movement), onClick = onAddMovement, leadingIcon = Icons.Outlined.Add)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(SharedRes.string.common_back))
                }
                IdentityIconTile(icon = trip.icon?.let(::categoryIcon) ?: trip.type.icon(), color = categoryColor(trip.color), size = 48.dp)
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(trip.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(trip.detailMetaLine(days), color = muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (wide) actions()
            }
            if (!wide) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            if (detail.errorMessage != null) {
                InlineBanner(
                    kind = BannerKind.Error,
                    text = stringResource(SharedRes.string.failure_load_trips),
                    actionLabel = stringResource(SharedRes.string.common_retry),
                    onAction = { viewModel.onDetailOpened(trip.id) },
                )
            }

            // Where it stands, straight on the page: no cards around the figures.
            val figures = @Composable {
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Bottom) {
                    Figure(stringResource(SharedRes.string.trip_detail_total_actual)) {
                        MoneyText(cents = detail.summary.actualCents, style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 42.sp))
                    }
                    Figure(stringResource(SharedRes.string.trip_analysis_mode_avg_day)) {
                        MoneyText(cents = averageCents(detail.summary.actualCents, days), style = MaterialTheme.typography.headlineSmall)
                    }
                    // It changes every figure on the page, so it sits with them.
                    FinanceFilterChip(
                        selected = detail.excludeOneTime,
                        label = stringResource(SharedRes.string.trip_detail_exclude_one_time_short),
                        onClick = { viewModel.onExcludeOneTimeToggled(!detail.excludeOneTime) },
                    )
                }
            }
            val budget = @Composable { modifier: Modifier ->
                val evaluation = detail.budgetEvaluation
                if (evaluation == null) {
                    Box(modifier, contentAlignment = Alignment.BottomEnd) {
                        SecondaryButton(text = stringResource(Res.string.trips_add_budget), onClick = onManageBudget)
                    }
                } else {
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(SharedRes.string.trip_action_budget), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = muted)
                            AppTextButton(onClick = onManageBudget) { Text(stringResource(SharedRes.string.common_edit)) }
                        }
                        EntityBudgetBar(evaluation)
                    }
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.Bottom) {
                    figures()
                    budget(Modifier.weight(1f))
                }
            } else {
                figures()
                budget(Modifier.fillMaxWidth())
            }

            val chart = @Composable { modifier: Modifier ->
                // The line is the budget spread over the trip's days, or without one, the average so far.
                val budgetPerDay = detail.budgetEvaluation?.let { it.budget.limitAmountCents / bars.days.size.coerceAtLeast(1) }
                val line = (budgetPerDay ?: bars.averagePerElapsedDay(today)).takeIf { it > 0L }
                val tone = trip.color?.let { themedIdentityColor(categoryColor(it)) } ?: MaterialTheme.colorScheme.primary
                // A booking can dwarf every day of the trip: its bar is cut short, its figure is not.
                val tallestDay = bars.days.maxOfOrNull { it.cents }?.takeIf { it > 0L }
                HomeCard(
                    stringResource(SharedRes.string.trip_detail_daily_chart),
                    modifier,
                    trailing = line?.let {
                        {
                            Text(
                                stringResource(
                                    if (budgetPerDay != null) SharedRes.string.trip_chart_budget_per_day else SharedRes.string.trip_chart_average_per_day,
                                    formatEuroCents(it),
                                ),
                                color = muted,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    },
                ) {
                    HoverBars(
                        labels = slots.map { it.label },
                        values = slots.map { slot -> if (slot.outside && tallestDay != null) slot.cents.coerceAtMost(tallestDay * 2) else slot.cents },
                        line = line,
                        onPick = { index -> picked = index.takeIf { it != picked } },
                        current = picked,
                        tones = slots.map { slot -> if (slot.outside) muted.copy(alpha = 0.45f) else tone },
                        sparseLabels = slots.size > 16,
                    ) { index ->
                        val slot = (index ?: picked)?.let(slots::getOrNull)
                        if (slot == null) {
                            Text(
                                listOfNotNull(
                                    bars.beforeCents.takeIf { it != 0L }?.let { stringResource(SharedRes.string.trip_split_before, formatEuroCents(it)) },
                                    stringResource(SharedRes.string.trip_split_during, formatEuroCents(bars.duringCents)),
                                    bars.afterCents.takeIf { it != 0L }?.let { stringResource(SharedRes.string.trip_split_after, formatEuroCents(it)) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(slot.title, style = MaterialTheme.typography.labelLarge, color = muted, maxLines = 1)
                            Text(formatEuroCents(slot.cents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1)
                            if (!slot.outside && line != null) {
                                val over = slot.cents - line
                                Text(
                                    (if (over > 0L) "+" else "") + formatEuroCents(over),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (over > 0L) colors.debt else colors.income,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            val ledger = @Composable {
                val shown = pickedSlot?.let { slot -> groups.filter { it.date in slot.dates } } ?: groups
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(SharedRes.string.trip_tab_movements), style = MaterialTheme.typography.titleMedium)
                        Text(shown.sumOf { it.rows.size }.toString(), style = MaterialTheme.typography.bodyMedium, color = muted)
                        Spacer(Modifier.weight(1f))
                        // The day picked on the chart, and the way back to every day.
                        pickedSlot?.let { slot -> FinanceFilterChip(selected = true, label = slot.title, onClick = { picked = null }) }
                    }
                    if (shown.isEmpty()) {
                        Text(stringResource(if (pickedSlot == null) SharedRes.string.trip_analysis_empty_body else Res.string.trips_day_empty), color = muted)
                    } else {
                        TripLedger(shown, onOpenMovement)
                    }
                }
            }
            val tagged = detail.tagActual.any { it.tagId != null }
            val byCategory = @Composable { modifier: Modifier ->
                HomeCard(stringResource(SharedRes.string.trip_detail_top_categories), modifier) {
                    Breakdown(detail.categoryActual.map { it.toBreakdownEntry() }, days)
                }
            }
            val byTag = @Composable { modifier: Modifier ->
                HomeCard(stringResource(SharedRes.string.trip_detail_by_tag), modifier) {
                    Breakdown(detail.tagActual.map { it.toBreakdownEntry(detail.tagsById[it.tagId], onOpenTag) }, days)
                }
            }
            val dayTable = @Composable {
                byDay?.let { DayTable(it, detail, slots, days, picked, onPick = { index -> picked = index.takeIf { it != picked } }) }
            }
            if (wide) {
                // The chart and where the money went, side by side at one height.
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    chart(Modifier.weight(1f).fillMaxHeight())
                    byCategory(Modifier.weight(1f).fillMaxHeight())
                    if (tagged) byTag(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                chart(Modifier)
                byCategory(Modifier)
                if (tagged) byTag(Modifier)
            }
            dayTable()
            ledger()
        }
    }
}

/** What the day table's rows are: categories or tags. */
private enum class DayView { CATEGORIES, TAGS }

/**
 * The trip day by day as a table: a row per category or per tag (as chosen above it), a column per
 * day with what was paid before and after the trip at either end. A day's column picks that day,
 * as its bar on the chart does.
 */
@Composable
private fun DayTable(byDay: TripDayBreakdown, detail: TripDetailState, slots: List<DaySlot>, days: Long, picked: Int?, onPick: (Int) -> Unit) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val tagged = byDay.tags.any { it.tagId != null }
    var view by remember { mutableStateOf(DayView.CATEGORIES) }
    val shownView = if (tagged) view else DayView.CATEGORIES
    val perDay = { cents: Long -> if (days > 0L) wholeEuros(cents / days) else "" }

    // name, colour and cents by date, for each row; the unnamed remainder (null key) goes last.
    class Line(val name: String, val color: Color, val rest: Boolean, val byDate: Map<String, Long>)
    val lines: List<Line> = when (shownView) {
        DayView.CATEGORIES -> byDay.categories.groupBy { it.categoryId }.map { (id, cells) ->
            Line(
                name = cells.first().categoryName ?: stringResource(SharedRes.string.common_no_category),
                color = if (id == null) muted else categoryColor(cells.first().categoryColor),
                rest = id == null,
                byDate = cells.associate { it.date to it.actualCents },
            )
        }
        DayView.TAGS -> byDay.tags.groupBy { it.tagId }.map { (id, cells) ->
            val tag = detail.tagsById[id]
            Line(
                name = tag?.name ?: cells.first().tagName ?: stringResource(SharedRes.string.trip_detail_no_tag),
                color = if (id == null) muted else categoryColor(tag?.effectiveColor() ?: cells.first().tagColor),
                rest = id == null,
                byDate = cells.associate { it.date to it.actualCents },
            )
        }
    }.sortedWith(compareBy<Line> { it.rest }.thenByDescending { line -> line.byDate.values.sumOf { abs(it) } })

    fun row(name: String, cents: List<Long>, mark: Color? = null, strong: Boolean = false): MonthTableRow {
        val top = cents.maxOrNull()?.takeIf { it > 0L }?.toFloat()
        return MonthTableRow(
            name = name,
            cells = cents.map { if (it == 0L && !strong) "" else wholeEuros(it) },
            total = wholeEuros(cents.sum()),
            average = perDay(cents.sum()),
            mark = mark,
            strong = strong,
            heat = top?.takeIf { !strong }?.let { max -> cents.map { (it / max).coerceIn(0f, 1f) } },
            tones = cents.map { if (it < 0L) colors.income else null },
            onCell = onPick,
        )
    }
    HomeCard(
        title = stringResource(Res.string.trips_day_table),
        trailing = if (tagged) {
            {
                SegmentedControl(
                    options = DayView.entries,
                    selected = view,
                    label = { stringResource(if (it == DayView.CATEGORIES) Res.string.analysis_months_by_category else Res.string.trips_by_tag) },
                    onSelect = { view = it },
                    compact = true,
                )
            }
        } else {
            null
        },
    ) {
        Text(stringResource(Res.string.trips_day_table_hint), style = MaterialTheme.typography.bodySmall, color = muted)
        MonthTable(
            labels = slots.map { it.label },
            rows = lines.map { line -> row(line.name, slots.map { slot -> slot.dates.sumOf { line.byDate[it] ?: 0L } }, mark = line.color) },
            // The days' own totals are the chart's: the canonical spend per day.
            summary = listOf(row(stringResource(SharedRes.string.budget_field_total), slots.map { it.cents }, strong = true)),
            shown = slots.size,
            selected = picked,
            onColumn = onPick,
            averageLabel = stringResource(Res.string.column_per_day),
        )
    }
}

/** The trip's movements in the Movements table's own rows, under a heading for each day with what it came to. */
@Composable
private fun TripLedger(groups: List<TripDayGroup<MovementSummary>>, onOpen: (MovementSummary) -> Unit) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val header = MaterialTheme.typography.labelMedium
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // In a trip the tag says more than the account, so it is the account that goes first.
        val columns = LedgerColumns(tick = false, category = maxWidth >= 440.dp, account = maxWidth >= 720.dp, context = maxWidth >= 560.dp, delete = false)
        FinanceCard(Modifier.fillMaxWidth()) {
            TableRow(
                columns = columns,
                height = 36.dp,
                tick = {},
                leading = {},
                name = { Text(stringResource(Res.string.column_name), style = header, color = muted) },
                category = { Text(stringResource(Res.string.column_category), style = header, color = muted) },
                account = { Text(stringResource(Res.string.column_account), style = header, color = muted) },
                context = { Text(stringResource(SharedRes.string.movement_field_tag), style = header, color = muted, maxLines = 1) },
                date = { Text(stringResource(Res.string.column_date), style = header, color = muted) },
                amount = { Text(stringResource(Res.string.column_amount), style = header, color = muted, textAlign = TextAlign.End) },
                trailing = {},
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            )
            groups.forEach { group ->
                // A day's heading is a section's title, with what the day came to at its end.
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dayLabel(group.ordinal, group.date).uppercase(),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                    Text(formatEuroCents(group.totalCents), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), thickness = 2.dp)
                group.rows.forEachIndexed { index, movement ->
                    if (index > 0) HorizontalDivider(color = colors.cardBorder)
                    MovementRow(movement = movement, columns = columns, ticked = false, onTick = {}, onOpen = { onOpen(movement) }, onDelete = {}, withinTrip = true)
                }
            }
        }
    }
}

/** Below this share of the trip an entry is too thin for its own segment in the split bar. */
private const val MINOR_SHARE = 0.04f

/**
 * Where the trip's money went along one dimension: one split bar over a row per entry. The entry
 * under the pointer, on the bar or on its row, stands out in both; a tag's row opens the tag.
 */
@Composable
internal fun Breakdown(entries: List<BreakdownEntry>, days: Long) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    // Largest first; the "no category" / "no tag" remainder always comes last.
    val sorted = entries.filter { it.cents != 0L }.sortedWith(compareBy<BreakdownEntry> { it.isRest }.thenByDescending { abs(it.cents) })
    if (sorted.isEmpty()) {
        Text(stringResource(SharedRes.string.trip_analysis_empty_body), color = muted)
        return
    }
    val positiveTotal = sorted.filter { it.cents > 0L }.sumOf { it.cents }.coerceAtLeast(1L)
    val percentTotal = sorted.sumOf { abs(it.cents) }.coerceAtLeast(1L)
    val pointed = remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            // Slivers under a few percent, and the remainder, join one muted segment at the end.
            var minor = 0L
            sorted.forEachIndexed { index, entry ->
                if (entry.cents <= 0L) return@forEachIndexed
                if (entry.isRest || entry.cents.toFloat() / positiveTotal < MINOR_SHARE) {
                    minor += entry.cents
                } else {
                    Box(
                        Modifier
                            .weight(entry.cents.toFloat())
                            .fillMaxHeight()
                            .pointing(index, pointed)
                            .background(
                                themedIdentityColor(entry.color).copy(alpha = if (pointed.value == null || pointed.value == index) 1f else 0.3f),
                                RoundedCornerShape(50),
                            ),
                    )
                }
            }
            if (minor > 0L) Box(Modifier.weight(minor.toFloat()).fillMaxHeight().background(muted.copy(alpha = 0.35f), RoundedCornerShape(50)))
        }
        sorted.forEachIndexed { index, entry ->
            val dim = pointed.value != null && pointed.value != index
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .pointing(index, pointed)
                    .then(entry.onClick?.let { Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = it) } ?: Modifier)
                    .padding(vertical = 6.dp)
                    .alpha(if (dim) 0.45f else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IdentityIconTile(icon = entry.icon, color = if (entry.isRest) muted else entry.color, size = 32.dp)
                Text(
                    entry.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (entry.isRest) muted else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(cents = entry.cents, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(
                            formatPercentLabel(abs(entry.cents).toFloat() / percentTotal),
                            days.takeIf { it > 0L }?.let { stringResource(SharedRes.string.trip_row_avg_day, formatEuroCents(averageCents(entry.cents, it))) },
                        ).joinToString(" · "),
                        color = muted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
