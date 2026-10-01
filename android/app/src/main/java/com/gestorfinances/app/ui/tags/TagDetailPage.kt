package com.gestorfinances.app.ui.tags

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.TagTripActual
import com.gestorfinances.app.data.repository.effectiveColor
import com.gestorfinances.app.data.repository.effectiveIcon
import com.gestorfinances.app.data.repository.icon
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.DistributionSegment
import com.gestorfinances.app.ui.common.EntityActionPill
import com.gestorfinances.app.ui.common.EntityDetailHeader
import com.gestorfinances.app.ui.common.EntityDetailTopBar
import com.gestorfinances.app.ui.common.EntityFigure
import com.gestorfinances.app.ui.common.EntityHeaderMarkSize
import com.gestorfinances.app.ui.common.EntityMenuAction
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.MovementListItem
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.SegmentedDistributionBar
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.dayGroupedRows
import com.gestorfinances.app.ui.common.formatPercentLabel
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.app.ui.theme.themedIdentityColor
import java.time.LocalDate
import kotlin.math.abs

/** A tag's own page: what was spent with it, where (per trip), and its movements. */
@Composable
fun TagDetailPage(
    tagId: String,
    onBack: () -> Unit,
    viewModel: TagsViewModel,
    /** Changes after every movement write, so the page reloads while it stays visible. */
    dataVersion: Long,
    onOpenTrip: (tripId: String) -> Unit,
    onAddMovement: (tagId: String, tripId: String) -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    onDeleteCommitted: DeleteUndoHandler,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, tagId, dataVersion) {
        viewModel.onTagDetailOpened(tagId)
    }

    val detail = state.detail
    if (detail == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageHeaderRow(onBack = onBack)
            val errorMessage = state.errorMessage
            if (errorMessage != null) {
                InlineFailureBanner(
                    diagnostic = errorMessage,
                    messageRes = R.string.failure_load_tags,
                    onRetry = { viewModel.onTagDetailOpened(tagId) },
                )
            }
        }
    } else {
        TagDetailContent(
            // An action that fails here (archiving, say) reports on the list's state: show it too.
            detail = detail.copy(errorMessage = detail.errorMessage ?: state.errorMessage),
            onBack = onBack,
            onRetry = { viewModel.onTagDetailOpened(tagId) },
            onOpenTrip = onOpenTrip,
            onAddMovement = detail.addTripId?.let { tripId -> { onAddMovement(detail.tag.id, tripId) } },
            onEdit = { viewModel.onEditClicked(detail.tag) },
            onArchive = { viewModel.onArchiveClicked(detail.tag) },
            onMovementDetail = onMovementDetail,
            modifier = modifier,
        )
    }

    TagFormSheet(state = state, viewModel = viewModel)

    state.archiveCandidate?.let {
        TagArchiveDialog(
            viewModel = viewModel,
            onArchived = { undo ->
                onDeleteCommitted(undo)
                onBack()
            },
        )
    }
}

@Composable
private fun TagDetailContent(
    detail: TagDetailState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenTrip: (tripId: String) -> Unit,
    onAddMovement: (() -> Unit)?,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onMovementDetail: (MovementSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tag = detail.tag
    val today = remember { LocalDate.now() }
    val onlyTrip: Pair<String, String?>? = tag.tripId?.let { it to tag.tripName }
        ?: detail.byTrip.singleOrNull()?.let { entry -> entry.tripId?.let { it to entry.tripName } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
    ) {
        item {
            EntityDetailTopBar(
                onBack = onBack,
                menu = listOf(
                    EntityMenuAction(stringResource(R.string.common_edit), onEdit),
                    EntityMenuAction(stringResource(R.string.common_archive), onArchive, destructive = true),
                ),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            EntityDetailHeader(
                leading = {
                    IdentityIconTile(
                        icon = categoryIcon(tag.effectiveIcon()),
                        color = categoryColor(tag.effectiveColor()),
                        size = EntityHeaderMarkSize,
                    )
                },
                name = tag.name,
                subtitle = listOfNotNull(tag.scopeLabel(), tag.categoryName).joinToString(" · "),
                figures = {
                    EntityFigure(label = stringResource(R.string.tag_detail_total)) {
                        MoneyText(cents = detail.totalCents, style = MaterialTheme.typography.headlineLarge)
                    }
                    Box(modifier = Modifier.weight(1f))
                    EntityFigure(
                        label = stringResource(R.string.tag_detail_movements),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = detail.movements.size.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                details = detail.tripTotalCents?.takeIf { it > 0L && tag.tripName != null }?.let { tripTotal ->
                    {
                        Text(
                            text = stringResource(
                                R.string.tag_detail_trip_share,
                                formatPercentLabel(detail.totalCents.toFloat() / tripTotal),
                                tag.tripName.orEmpty(),
                            ),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                actions = onAddMovement?.let { add ->
                    {
                        EntityActionPill(
                            text = stringResource(R.string.entity_add_movement),
                            onClick = add,
                            icon = Icons.Outlined.Add,
                        )
                    }
                },
                // The one trip the tag belongs to, or the only one it was used in, is a pill away.
                link = onlyTrip?.let { (tripId, tripName) ->
                    {
                        EntityActionPill(
                            text = tripName ?: stringResource(R.string.tag_detail_trip),
                            onClick = { onOpenTrip(tripId) },
                            chevron = true,
                        )
                    }
                },
            )
        }
        // A tag used across trips says how much went where.
        if (detail.byTrip.size > 1) {
            item {
                TagTripBreakdown(
                    entries = detail.byTrip,
                    onOpenTrip = onOpenTrip,
                    modifier = Modifier.padding(top = 28.dp),
                )
            }
        }
        detail.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_tags,
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        if (detail.movements.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.tag_detail_empty),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            dayGroupedRows(
                rows = detail.movements,
                dateOf = { it.date },
                key = { it.id },
                today = today,
            ) { movement, position ->
                MovementListItem(
                    movement = movement,
                    onClick = { onMovementDetail(movement) },
                    showDate = false,
                    position = position,
                    showTag = false,
                )
            }
        }
    }
}

/** What the tag cost in each trip: one split bar, then a row per trip opening its page. */
@Composable
private fun TagTripBreakdown(
    entries: List<TagTripActual>,
    onOpenTrip: (tripId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positive = entries.filter { it.actualCents > 0L }
    val positiveTotal = positive.sumOf { it.actualCents }.coerceAtLeast(1L)
    val percentTotal = entries.sumOf { abs(it.actualCents) }.coerceAtLeast(1L)
    val outsideColor = FinanceTheme.colors.mutedText
    val deletedTripName = stringResource(R.string.tag_detail_deleted_trip)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.tag_detail_by_trip))
        if (positive.isNotEmpty()) {
            SegmentedDistributionBar(
                segments = positive.map {
                    DistributionSegment(
                        color = if (it.tripId == null) outsideColor.copy(alpha = 0.35f) else themedIdentityColor(categoryColor(it.tripColor)),
                        fraction = it.actualCents.toFloat() / positiveTotal,
                    )
                },
                contentDescription = positive.joinToString(", ") {
                    "${it.tripName ?: deletedTripName} ${formatPercentLabel(it.actualCents.toFloat() / positiveTotal)}"
                },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        entries.forEach { entry ->
            val tripId = entry.tripId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (tripId != null) Modifier.clickable { onOpenTrip(tripId) } else Modifier)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IdentityIconTile(
                    icon = entry.tripType?.icon() ?: Icons.Outlined.Place,
                    color = if (tripId == null) outsideColor else categoryColor(entry.tripColor),
                    size = 32.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = entry.tripName ?: deletedTripName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (tripId == null) FinanceTheme.colors.mutedText else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(cents = entry.actualCents, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = formatPercentLabel(abs(entry.actualCents).toFloat() / percentTotal),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
