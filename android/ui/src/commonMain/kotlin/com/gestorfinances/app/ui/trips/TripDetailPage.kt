package com.gestorfinances.app.ui.trips

import com.gestorfinances.app.ui.common.OpenDialogs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripTagActual
import com.gestorfinances.app.data.repository.effectiveColor
import com.gestorfinances.app.data.repository.effectiveIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.gestorfinances.app.ui.common.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_cancel
import com.gestorfinances.ui.resources.common_close
import com.gestorfinances.ui.resources.common_no_category
import com.gestorfinances.ui.resources.entity_add_movement
import com.gestorfinances.ui.resources.failure_load_trips
import com.gestorfinances.ui.resources.trip_action_archive
import com.gestorfinances.ui.resources.trip_action_budget
import com.gestorfinances.ui.resources.trip_action_edit
import com.gestorfinances.ui.resources.trip_analysis_empty_body
import com.gestorfinances.ui.resources.trip_analysis_mode_avg_day
import com.gestorfinances.ui.resources.trip_archive_confirm_title
import com.gestorfinances.ui.resources.trip_archive_warning
import com.gestorfinances.ui.resources.trip_chart_after
import com.gestorfinances.ui.resources.trip_chart_average_per_day
import com.gestorfinances.ui.resources.trip_chart_before
import com.gestorfinances.ui.resources.trip_chart_budget_per_day
import com.gestorfinances.ui.resources.trip_detail_by_tag
import com.gestorfinances.ui.resources.trip_detail_daily_chart
import com.gestorfinances.ui.resources.trip_detail_exclude_one_time_short
import com.gestorfinances.ui.resources.trip_detail_include_one_time_short
import com.gestorfinances.ui.resources.trip_detail_no_tag
import com.gestorfinances.ui.resources.trip_detail_top_categories
import com.gestorfinances.ui.resources.trip_detail_total_actual
import com.gestorfinances.ui.resources.trip_detail_view_breakdown
import com.gestorfinances.ui.resources.trip_loading
import com.gestorfinances.ui.resources.trip_row_avg_day
import com.gestorfinances.ui.resources.trip_split_after
import com.gestorfinances.ui.resources.trip_split_before
import com.gestorfinances.ui.resources.trip_split_during
import com.gestorfinances.ui.resources.trip_tab_movements
import com.gestorfinances.ui.resources.trip_tab_resum
import com.gestorfinances.ui.resources.trip_timeline_day
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.TripCategoryActual
import com.gestorfinances.app.data.repository.TripDailyActual
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.dayCount
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.DistributionSegment
import com.gestorfinances.app.ui.common.EntityActionPill
import com.gestorfinances.app.ui.common.EntityBudgetBar
import com.gestorfinances.app.ui.common.EntityDetailHeader
import com.gestorfinances.app.ui.common.EntityDetailTopBar
import com.gestorfinances.app.ui.common.EntityFigure
import com.gestorfinances.app.ui.common.EntityHeaderMarkSize
import com.gestorfinances.app.ui.common.EntityMenuAction
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.SegmentedDistributionBar
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatEuroCompact
import com.gestorfinances.app.ui.common.formatPercentLabel
import com.gestorfinances.app.ui.common.formatWeekdayDate
import com.gestorfinances.app.ui.common.movementRowPosition
import com.gestorfinances.app.ui.common.parseIsoDateOrNull
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil

/**
 * A trip's own page. Reads [TripsViewModel]'s `detail` state directly — the caller is responsible
 * for having triggered a load before navigating here. Also hosts the edit form and archive
 * confirmation reachable from the overflow; archiving navigates back since the trip leaves the
 * active list.
 *
 * The header carries the cost, the daily average and the budget; below it two tabs: Resum (spend
 * per day and by category) and Moviments (the ledger by trip day, with each day's canonical total).
 * The whole page scrolls as one, the tab row sticking to the top once the header has gone.
 */
@Composable
fun TripDetailScreen(
    viewModel: TripsViewModel,
    onBack: () -> Unit,
    onManageBudget: (String) -> Unit,
    onAddMovement: () -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    onOpenTag: (tagId: String) -> Unit,
    onDeleteCommitted: DeleteUndoHandler,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val detail = state.detail

    Box(modifier = modifier.fillMaxSize()) {
        when {
            detail == null || detail.isLoading -> {
                Text(
                    text = stringResource(Res.string.trip_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp),
                )
            }
            else -> TripDetailContent(
                detail = detail,
                onBack = onBack,
                onRetry = { viewModel.onDetailOpened(detail.trip.id) },
                onEdit = { viewModel.onEditClicked(detail.trip) },
                onArchive = { viewModel.onArchiveClicked(detail.trip) },
                onManageBudget = { onManageBudget(detail.trip.id) },
                onAddMovement = onAddMovement,
                onExcludeOneTimeToggled = viewModel::onExcludeOneTimeToggled,
                onMovementDetail = onMovementDetail,
                onOpenTag = onOpenTag,
            )
        }
    }

    TripFormSheet(state = state, viewModel = viewModel)

    state.archiveCandidate?.let {
        OpenDialogs.Track()
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(Res.string.trip_archive_confirm_title)) },
            text = { Text(text = stringResource(Res.string.trip_archive_warning)) },
            confirmButton = {
                DestructiveTextButton(
                    // Only navigate back once the archive succeeds, so a failure stays on this page.
                    onClick = {
                        viewModel.onArchiveConfirmed { undo ->
                            onDeleteCommitted(undo)
                            onBack()
                        }
                    },
                ) {
                    Text(text = stringResource(Res.string.common_archive))
                }
            },
            dismissButton = {
                AppTextButton(onClick = viewModel::onArchiveDismissed) {
                    Text(text = stringResource(Res.string.common_cancel))
                }
            },
        )
    }
}

private enum class TripDetailTab { RESUM, MOVEMENTS }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripDetailContent(
    detail: TripDetailState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onManageBudget: () -> Unit,
    onAddMovement: () -> Unit,
    onExcludeOneTimeToggled: (Boolean) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    onOpenTag: (tagId: String) -> Unit,
) {
    val trip = detail.trip
    val days = trip.dayCount(
        fallbackStart = detail.dailyActual.firstOrNull()?.date,
        fallbackEnd = detail.dailyActual.lastOrNull()?.date,
    )
    val today = remember { LocalDate.now() }
    var selectedTab by rememberSaveable(trip.id) { mutableStateOf(TripDetailTab.RESUM) }
    val groups = remember(detail) { buildDayGroups(trip, detail.dailyActual, detail.movements) { it.date } }
    val bars = remember(detail) { tripDailyBars(trip, detail.dailyActual) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
    ) {
        item {
            EntityDetailTopBar(
                onBack = onBack,
                menu = listOf(
                    EntityMenuAction(stringResource(Res.string.trip_action_edit), onEdit),
                    EntityMenuAction(
                        stringResource(
                            if (detail.excludeOneTime) {
                                Res.string.trip_detail_include_one_time_short
                            } else {
                                Res.string.trip_detail_exclude_one_time_short
                            },
                        ),
                        { onExcludeOneTimeToggled(!detail.excludeOneTime) },
                    ),
                    EntityMenuAction(stringResource(Res.string.trip_action_archive), onArchive, destructive = true),
                ),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            EntityDetailHeader(
                leading = {
                    IdentityIconTile(
                        icon = trip.type.icon(),
                        color = categoryColor(trip.color),
                        size = EntityHeaderMarkSize,
                    )
                },
                name = trip.name,
                subtitle = trip.detailMetaLine(days),
                figures = {
                    EntityFigure(label = stringResource(Res.string.trip_detail_total_actual)) {
                        MoneyText(cents = detail.summary.actualCents, style = MaterialTheme.typography.headlineLarge)
                    }
                    Box(modifier = Modifier.weight(1f))
                    EntityFigure(
                        label = stringResource(Res.string.trip_analysis_mode_avg_day),
                        horizontalAlignment = Alignment.End,
                    ) {
                        MoneyText(
                            cents = averageCents(detail.summary.actualCents, days),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                details = detail.budgetEvaluation?.let { evaluation -> { EntityBudgetBar(evaluation) } },
                actions = {
                    EntityActionPill(
                        text = stringResource(Res.string.entity_add_movement),
                        onClick = onAddMovement,
                        icon = Icons.Outlined.Add,
                    )
                    EntityActionPill(
                        text = stringResource(Res.string.trip_action_budget),
                        onClick = onManageBudget,
                    )
                },
            )
        }
        detail.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = Res.string.failure_load_trips,
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        stickyHeader(key = "tabs") {
            SegmentedControl(
                options = TripDetailTab.entries,
                selected = selectedTab,
                label = { tab ->
                    stringResource(
                        when (tab) {
                            TripDetailTab.RESUM -> Res.string.trip_tab_resum
                            TripDetailTab.MOVEMENTS -> Res.string.trip_tab_movements
                        },
                    )
                },
                onSelect = { selectedTab = it },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = 20.dp, bottom = 8.dp),
            )
        }
        when (selectedTab) {
            TripDetailTab.RESUM -> {
                item {
                    // The line is the budget spread over the trip's days, or without one, the average so far.
                    val budgetPerDay = detail.budgetEvaluation?.let {
                        it.budget.limitAmountCents / bars.days.size.coerceAtLeast(1)
                    }
                    TripDailyChart(
                        bars = bars,
                        lineCents = budgetPerDay ?: bars.averagePerElapsedDay(today),
                        lineIsBudget = budgetPerDay != null,
                        // A trip without its own colour charts in the app's, not in the grey of "no colour".
                        barColor = trip.color?.let { themedIdentityColor(categoryColor(it)) } ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                item {
                    TripBreakdownSection(
                        title = stringResource(Res.string.trip_detail_top_categories),
                        entries = detail.categoryActual.map { it.toBreakdownEntry() },
                        days = days,
                        modifier = Modifier.padding(top = 28.dp),
                    )
                }
                // Tags detail the spend within the trip; each opens its own page.
                if (detail.tagActual.any { it.tagId != null }) {
                    item {
                        TripBreakdownSection(
                            title = stringResource(Res.string.trip_detail_by_tag),
                            entries = detail.tagActual.map { it.toBreakdownEntry(detail.tagsById[it.tagId], onOpenTag) },
                            days = days,
                            modifier = Modifier.padding(top = 28.dp),
                        )
                    }
                }
            }
            TripDetailTab.MOVEMENTS -> if (groups.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.trip_analysis_empty_body),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                groups.forEach { group ->
                    item(key = "day-${group.date}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = dayLabel(group.ordinal, group.date),
                                color = FinanceTheme.colors.mutedText,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            MoneyText(
                                cents = group.totalCents,
                                color = FinanceTheme.colors.mutedText,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    itemsIndexed(items = group.rows, key = { _, movement -> movement.id }) { index, movement ->
                        MovementListItem(
                            movement = movement,
                            onClick = { onMovementDetail(movement) },
                            showDate = false,
                            position = movementRowPosition(index, group.rows.size),
                            showTrip = false,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Spend per day
// ---------------------------------------------------------------------------

/** One trip day's bar: its date, 1-based day number, and canonical actual spend. */
data class TripDayBar(val date: String, val ordinal: Long?, val cents: Long)

/**
 * A trip's canonical per-day spend split by the trip's own dates: what was paid before it started
 * (bookings), each day of it (zero-filled, so quiet days still show), and anything after it ended.
 */
data class TripDailyBars(
    val before: List<TripDailyActual>,
    val days: List<TripDayBar>,
    val after: List<TripDailyActual>,
) {
    val beforeCents: Long get() = before.sumOf { it.actualCents }
    val duringCents: Long get() = days.sumOf { it.cents }
    val afterCents: Long get() = after.sumOf { it.actualCents }

    /** Spend during the trip per day so far: every day once it is over, the days up to [today] while it runs. */
    fun averagePerElapsedDay(today: LocalDate): Long {
        val todayIso = today.toString()
        val elapsed = days.count { it.date <= todayIso }.coerceAtLeast(1)
        return duringCents / elapsed
    }
}

fun tripDailyBars(trip: TripSummary, daily: List<TripDailyActual>): TripDailyBars {
    val sorted = daily.sortedBy { it.date }
    val start = trip.startDate?.let(::parseIsoDateOrNull)
        ?: return TripDailyBars(emptyList(), sorted.map { TripDayBar(it.date, null, it.actualCents) }, emptyList())
    val end = trip.endDate?.let(::parseIsoDateOrNull)
    val startIso = start.toString()
    val endIso = end?.toString()
    // An open trip runs to its last spend, so the chart never stops short of the data.
    val lastDay = maxOf(end ?: start, sorted.lastOrNull()?.date?.takeIf { endIso == null }?.let(::parseIsoDateOrNull) ?: start)
    val totals = sorted.associate { it.date to it.actualCents }
    val days = generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(lastDay) }
        .map { day -> TripDayBar(day.toString(), ChronoUnit.DAYS.between(start, day) + 1, totals[day.toString()] ?: 0L) }
        .toList()
    return TripDailyBars(
        before = sorted.filter { it.date < startIso },
        days = days,
        after = if (endIso == null) emptyList() else sorted.filter { it.date > endIso },
    )
}

/** How far past the tallest day a booking bar may reach before it is cut. */
private const val OUTSIDE_SCALE_CAP = 2L

private data class ChartSlot(val label: String, val cents: Long, val date: String?, val outside: Boolean)

/**
 * Spend per trip day as bars, with bookings before the trip and anything after it as muted bars
 * at either end, and a dashed line at the daily budget (or the average so far). Tapping a bar
 * shows its amount above it; tapping it again, or anywhere else, hides it.
 */
@Composable
fun TripDailyChart(
    bars: TripDailyBars,
    lineCents: Long,
    lineIsBudget: Boolean,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    val beforeLabel = stringResource(Res.string.trip_chart_before)
    val afterLabel = stringResource(Res.string.trip_chart_after)
    val slots = remember(bars, beforeLabel, afterLabel) {
        buildList {
            if (bars.before.isNotEmpty()) {
                add(ChartSlot(beforeLabel, bars.beforeCents, bars.before.first().date, outside = true))
            }
            bars.days.forEach { day ->
                val label = day.ordinal?.toString() ?: parseIsoDateOrNull(day.date)?.dayOfMonth?.toString().orEmpty()
                add(ChartSlot(label, day.cents, day.date, outside = false))
            }
            if (bars.after.isNotEmpty()) {
                add(ChartSlot(afterLabel, bars.afterCents, bars.after.first().date, outside = true))
            }
        }
    }
    val description = stringResource(Res.string.trip_detail_daily_chart) + ". " +
        slots.filter { it.cents != 0L }.joinToString(", ") { "${it.label} ${formatEuroCents(it.cents)}" }
    var selected by remember(slots) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = FinanceTheme.colors.mutedText)
    val mutedBar = FinanceTheme.colors.mutedText.copy(alpha = 0.35f)
    val baseline = FinanceTheme.colors.cardBorder
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val amountColor = MaterialTheme.colorScheme.onSurface
    val tooltipFill = MaterialTheme.colorScheme.inverseSurface
    val tooltipStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = stringResource(Res.string.trip_detail_daily_chart),
            trailing = if (lineCents > 0L) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Canvas(modifier = Modifier.width(16.dp).height(2.dp)) {
                            drawLine(
                                color = lineColor,
                                start = Offset(0f, size.height / 2),
                                end = Offset(size.width, size.height / 2),
                                strokeWidth = size.height,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                            )
                        }
                        Text(
                            text = stringResource(
                                if (lineIsBudget) Res.string.trip_chart_budget_per_day else Res.string.trip_chart_average_per_day,
                                formatEuroCents(lineCents),
                            ),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            } else {
                null
            },
        )
        if (slots.isEmpty()) {
            Text(
                text = stringResource(Res.string.trip_analysis_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .semantics { contentDescription = description }
                    .pointerInput(slots) {
                        detectTapGestures { offset ->
                            val index = (offset.x / (size.width.toFloat() / slots.size)).toInt().coerceIn(0, slots.lastIndex)
                            selected = index.takeIf { it != selected }
                        }
                    },
            ) {
                val labelArea = 20.dp.toPx()
                val chartHeight = size.height - labelArea
                val slotWidth = size.width / slots.size
                val barWidth = (slotWidth * 0.62f).coerceAtMost(28.dp.toPx())
                val radius = CornerRadius(minOf(4.dp.toPx(), barWidth / 2))
                // Everything shares one scale, unless the bookings would flatten the days: then
                // they are cut at the top and carry their amount instead.
                val dayMax = maxOf(slots.filter { !it.outside }.maxOfOrNull { it.cents } ?: 0L, lineCents, 1L)
                val outsideMax = slots.filter { it.outside }.maxOfOrNull { it.cents } ?: 0L
                val scale = maxOf(dayMax, minOf(outsideMax, dayMax * OUTSIDE_SCALE_CAP)).toFloat()
                // Label every day on a short trip, every few on a long one.
                val daySlots = slots.count { !it.outside }
                val labelEvery = ceil(daySlots / 10f).toInt().coerceAtLeast(1)

                drawLine(baseline, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 1.dp.toPx())
                var dayIndex = 0
                slots.forEachIndexed { index, slot ->
                    val centerX = slotWidth * index + slotWidth / 2
                    val cents = slot.cents.coerceAtLeast(0L)
                    val clipped = cents > scale
                    val height = chartHeight * (minOf(cents.toFloat(), scale) / scale)
                    if (height > 0f) {
                        val fill = if (slot.outside) mutedBar else barColor
                        drawRoundRect(
                            // While a bar is picked, the others step back.
                            color = if (selected == null || selected == index) fill else fill.copy(alpha = fill.alpha * 0.4f),
                            topLeft = Offset(centerX - barWidth / 2, chartHeight - height),
                            size = Size(barWidth, height),
                            cornerRadius = radius,
                        )
                        if (clipped) {
                            val amount = textMeasurer.measure(formatEuroCompact(cents), labelStyle.copy(color = amountColor))
                            drawText(
                                textLayoutResult = amount,
                                topLeft = Offset(
                                    (centerX - amount.size.width / 2f).coerceIn(0f, size.width - amount.size.width),
                                    chartHeight - height + 4.dp.toPx(),
                                ),
                            )
                        }
                    }
                    val showLabel = slot.outside || dayIndex % labelEvery == 0
                    if (!slot.outside) dayIndex++
                    if (showLabel) {
                        val measured = textMeasurer.measure(slot.label, labelStyle)
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                (centerX - measured.size.width / 2f).coerceIn(0f, size.width - measured.size.width),
                                chartHeight + 4.dp.toPx(),
                            ),
                        )
                    }
                }
                selected?.let { index ->
                    // The picked bar's amount, in a small ink label just above it.
                    val slot = slots[index]
                    val text = textMeasurer.measure(formatEuroCents(slot.cents), tooltipStyle)
                    val padH = 6.dp.toPx()
                    val padV = 3.dp.toPx()
                    val boxW = text.size.width + padH * 2
                    val boxH = text.size.height + padV * 2
                    val centerX = slotWidth * index + slotWidth / 2
                    val barTop = chartHeight - chartHeight * (minOf(slot.cents.coerceAtLeast(0L).toFloat(), scale) / scale)
                    val left = (centerX - boxW / 2).coerceIn(0f, size.width - boxW)
                    val top = (barTop - boxH - 4.dp.toPx()).coerceAtLeast(0f)
                    drawRoundRect(tooltipFill, Offset(left, top), Size(boxW, boxH), CornerRadius(6.dp.toPx()))
                    drawText(text, topLeft = Offset(left + padH, top + padV))
                }
                if (lineCents > 0L) {
                    val y = chartHeight * (1f - lineCents / scale)
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    )
                }
            }
        }
        if (bars.beforeCents != 0L || bars.afterCents != 0L) {
            Text(
                text = listOfNotNull(
                    stringResource(Res.string.trip_split_before, formatEuroCents(bars.beforeCents)).takeIf { bars.beforeCents != 0L },
                    stringResource(Res.string.trip_split_during, formatEuroCents(bars.duringCents)),
                    stringResource(Res.string.trip_split_after, formatEuroCents(bars.afterCents)).takeIf { bars.afterCents != 0L },
                ).joinToString(" · "),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Spend by category and by tag
// ---------------------------------------------------------------------------

private const val BREAKDOWN_PREVIEW_LIMIT = 5

/** Below this share of the trip an entry is too thin for its own segment in the split bar. */
private const val MINOR_SHARE = 0.04f

/** One entry of a trip breakdown: a category or a tag, with its canonical spend. */
data class BreakdownEntry(
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val cents: Long,
    /** Muted and not tappable: the "no category" / "no tag" remainder. */
    val isRest: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

/** Where the trip's money went along one dimension: one split bar, then a compact row per entry. */
@Composable
fun TripBreakdownSection(
    title: String,
    entries: List<BreakdownEntry>,
    days: Long,
    modifier: Modifier = Modifier,
) {
    // Largest first; the "no category" / "no tag" remainder always comes last.
    val sorted = entries.filter { it.cents != 0L }
        .sortedWith(compareBy<BreakdownEntry> { it.isRest }.thenByDescending { abs(it.cents) })
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) sorted else sorted.take(BREAKDOWN_PREVIEW_LIMIT)
    val positive = sorted.filter { it.cents > 0L }
    val positiveTotal = positive.sumOf { it.cents }.coerceAtLeast(1L)
    val percentTotal = sorted.sumOf { abs(it.cents) }.coerceAtLeast(1L)
    val restColor = FinanceTheme.colors.mutedText.copy(alpha = 0.35f)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = title,
            trailing = if (sorted.size > BREAKDOWN_PREVIEW_LIMIT) {
                {
                    AppTextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) Res.string.common_close else Res.string.trip_detail_view_breakdown))
                    }
                }
            } else {
                null
            },
        )
        if (sorted.isEmpty()) {
            Text(
                text = stringResource(Res.string.trip_analysis_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }
        if (positive.isNotEmpty()) {
            // Slivers under a few percent, and the remainder, join one muted segment at the end.
            val (major, minor) = positive.partition { !it.isRest && it.cents.toFloat() / positiveTotal >= MINOR_SHARE }
            SegmentedDistributionBar(
                segments = major.map {
                    DistributionSegment(color = themedIdentityColor(it.color), fraction = it.cents.toFloat() / positiveTotal)
                } + listOfNotNull(
                    minor.takeIf { it.isNotEmpty() }?.let { rest ->
                        DistributionSegment(color = restColor, fraction = rest.sumOf { it.cents }.toFloat() / positiveTotal)
                    },
                ),
                contentDescription = positive.joinToString(", ") {
                    "${it.name} ${formatPercentLabel(it.cents.toFloat() / positiveTotal)}"
                },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        visible.forEach { entry ->
            TripBreakdownRow(
                entry = entry,
                averagePerDayCents = days.takeIf { it > 0L }?.let { averageCents(entry.cents, it) },
                share = abs(entry.cents).toFloat() / percentTotal,
            )
        }
    }
}

@Composable
private fun TripBreakdownRow(entry: BreakdownEntry, averagePerDayCents: Long?, share: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (entry.onClick != null) Modifier.clickable(onClick = entry.onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IdentityIconTile(
            icon = entry.icon,
            color = if (entry.isRest) FinanceTheme.colors.mutedText else entry.color,
            size = 32.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = entry.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = if (entry.isRest) FinanceTheme.colors.mutedText else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(cents = entry.cents, style = MaterialTheme.typography.titleSmall)
            Text(
                text = listOfNotNull(
                    formatPercentLabel(share),
                    averagePerDayCents?.let { stringResource(Res.string.trip_row_avg_day, formatEuroCents(it)) },
                ).joinToString(" · "),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun TripCategoryActual.toBreakdownEntry(): BreakdownEntry = BreakdownEntry(
    name = categoryName ?: stringResource(Res.string.common_no_category),
    icon = categoryIcon(categoryIcon),
    color = categoryColor(categoryColor),
    cents = actualCents,
    isRest = categoryId == null,
)

@Composable
fun TripTagActual.toBreakdownEntry(tag: TagSummary?, onOpenTag: ((String) -> Unit)?): BreakdownEntry = BreakdownEntry(
    name = tag?.name ?: tagName ?: stringResource(Res.string.trip_detail_no_tag),
    icon = categoryIcon(tag?.effectiveIcon()),
    color = categoryColor(tag?.effectiveColor() ?: tagColor),
    cents = actualCents,
    isRest = tagId == null,
    onClick = tagId?.let { id -> onOpenTag?.let { open -> { open(id) } } },
)

// ---------------------------------------------------------------------------
// Moviments by trip day
// ---------------------------------------------------------------------------

/**
 * One trip day's ledger group: the day's canonical actual total (`tripActualByDay` — 0 when the
 * day nets to zero and the query drops it), its 1-based day number within the trip (null for
 * spend outside it, such as bookings), and the day's rows.
 */
data class TripDayGroup<T>(
    val date: String,
    val ordinal: Long?,
    val totalCents: Long,
    val rows: List<T>,
)

fun <T> buildDayGroups(
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
            TripDayGroup(date = date, ordinal = trip.dayOrdinal(date), totalCents = totals[date] ?: 0L, rows = dayRows)
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
fun dayLabel(ordinal: Long?, date: String): String {
    val formatted = formatWeekdayDate(date)
    return if (ordinal != null) stringResource(Res.string.trip_timeline_day, ordinal, formatted) else formatted
}
