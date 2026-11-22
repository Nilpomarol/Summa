package com.gestorfinances.desktop

import com.gestorfinances.ui.resources.tag_detail_deleted_trip
import com.gestorfinances.ui.resources.tag_detail_by_trip
import com.gestorfinances.ui.resources.movement_field_tag
import com.gestorfinances.desktop.resources.tags_per_movement
import com.gestorfinances.desktop.resources.column_share
import com.gestorfinances.app.ui.trips.icon
import com.gestorfinances.app.ui.trips.BreakdownEntry
import com.gestorfinances.app.ui.theme.themedIdentityColor
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.AppIconButton
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.data.repository.MovementSummary
import com.gestorfinances.app.data.repository.effectiveColor
import com.gestorfinances.app.data.repository.effectiveIcon
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DestructiveButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SearchField
import com.gestorfinances.app.ui.common.SecondaryButton
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatPercentLabel
import com.gestorfinances.app.ui.tags.TagArchiveDialog
import com.gestorfinances.app.ui.tags.TagDetailState
import com.gestorfinances.app.ui.tags.TagFormSheet
import com.gestorfinances.app.ui.tags.TagsUiState
import com.gestorfinances.app.ui.tags.TagsViewModel
import com.gestorfinances.app.ui.tags.buildTagSections
import com.gestorfinances.app.ui.tags.scopeLabel
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import com.gestorfinances.desktop.resources.Res
import com.gestorfinances.desktop.resources.column_category
import com.gestorfinances.ui.resources.budget_field_total
import com.gestorfinances.ui.resources.common_archive
import com.gestorfinances.ui.resources.common_edit
import com.gestorfinances.ui.resources.common_retry
import com.gestorfinances.ui.resources.entity_add_movement
import com.gestorfinances.ui.resources.failure_load_tags
import com.gestorfinances.ui.resources.tag_detail_empty
import com.gestorfinances.ui.resources.tag_detail_movements
import com.gestorfinances.ui.resources.tag_detail_total
import com.gestorfinances.ui.resources.tag_detail_trip
import com.gestorfinances.ui.resources.tag_detail_trip_share
import com.gestorfinances.ui.resources.tag_empty_body
import com.gestorfinances.ui.resources.tag_empty_title
import com.gestorfinances.ui.resources.tag_list_add
import com.gestorfinances.ui.resources.tag_list_title
import com.gestorfinances.ui.resources.tag_search_empty
import com.gestorfinances.ui.resources.tag_search_label
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.ui.resources.Res as SharedRes

/**
 * Tags as tables over the whole page, one per place they apply (everywhere, a kind of trip, one
 * trip). A tag opens over the whole page: what was spent with it, in which trips, and its movements.
 */
@Composable
fun TagsPage(
    viewModel: TagsViewModel,
    dataVersion: Long,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onOpenTrip: (tripId: String) -> Unit,
    onAddMovement: (tagId: String, tripId: String) -> Unit,
    onOpenMovement: (MovementSummary) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel, dataVersion) { viewModel.onScreenShown(null) }
    LaunchedEffect(viewModel, selectedId, dataVersion) { selectedId?.let(viewModel::onTagDetailOpened) }
    val detail = state.detail?.takeIf { it.tag.id == selectedId }

    if (selectedId == null || detail == null) {
        // Until the opened tag has loaded, the tables stay.
        TagTables(state, viewModel, onSelect)
    } else {
        TagPane(
            detail = detail,
            viewModel = viewModel,
            onBack = { onSelect(null) },
            onOpenTrip = onOpenTrip,
            onAddMovement = detail.addTripId?.let { tripId -> { onAddMovement(detail.tag.id, tripId) } },
            onOpenMovement = onOpenMovement,
        )
    }

    TagFormSheet(state = state, viewModel = viewModel)
    state.archiveCandidate?.let { tag ->
        TagArchiveDialog(viewModel = viewModel, onArchived = { if (selectedId == tag.id) onSelect(null) })
    }
}

@Composable
private fun TagTables(state: TagsUiState, viewModel: TagsViewModel, onSelect: (String?) -> Unit) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val sections = buildTagSections(state)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val barred = maxWidth >= 640.dp
        ScrollPage {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(SharedRes.string.tag_list_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                PrimaryButton(text = stringResource(SharedRes.string.tag_list_add), onClick = viewModel::onAddClicked, leadingIcon = Icons.Outlined.Add)
            }
            // Under the title, as on Categories, People and Trips.
            SearchField(
                query = state.searchQuery,
                onQueryChange = viewModel::onSearchChanged,
                placeholder = stringResource(SharedRes.string.tag_search_label),
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
            )
            if (state.errorMessage != null) {
                InlineBanner(
                    kind = BannerKind.Error,
                    text = stringResource(SharedRes.string.failure_load_tags),
                    actionLabel = stringResource(SharedRes.string.common_retry),
                    onAction = { viewModel.onScreenShown(null) },
                )
            }
            if (state.isLoading) return@ScrollPage
            if (state.visibleTags.isEmpty()) {
                if (state.searchQuery.isNotBlank()) {
                    Text(stringResource(SharedRes.string.tag_search_empty), color = muted)
                } else {
                    Text(stringResource(SharedRes.string.tag_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(SharedRes.string.tag_empty_body), color = muted)
                }
                return@ScrollPage
            }
            val header = MaterialTheme.typography.labelMedium
            // Each place tags apply is a table of its own, under its name and how many it holds.
            sections.forEach { section ->
                // Biggest first: the bar beside each is its part of the section's spend.
                val tags = section.tags.sortedByDescending { state.tagTotals[it.id] ?: 0L }
                val sectionTotal = tags.sumOf { state.tagTotals[it.id] ?: 0L }
                val categorized = tags.any { it.categoryName != null }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(section.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tags.size.toString(), style = MaterialTheme.typography.bodyMedium, color = muted)
                    }
                    FinanceCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(stringResource(SharedRes.string.movement_field_tag), Modifier.weight(1.2f), style = header, color = muted)
                            if (barred) Text(stringResource(Res.string.column_share), Modifier.weight(1f), style = header, color = muted)
                            if (categorized) Text(stringResource(Res.string.column_category), Modifier.weight(0.8f), style = header, color = muted)
                            Text(stringResource(SharedRes.string.budget_field_total), Modifier.width(120.dp), style = header, color = muted, textAlign = TextAlign.End)
                        }
                        tags.forEach { tag ->
                            val total = state.tagTotals[tag.id] ?: 0L
                            HorizontalDivider(color = colors.cardBorder)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { onSelect(tag.id) }
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    IdentityIconTile(icon = categoryIcon(tag.effectiveIcon()), color = categoryColor(tag.effectiveColor()), size = 32.dp)
                                    Text(tag.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (barred) {
                                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (total > 0L && sectionTotal > 0L) {
                                            BudgetProgressBar(
                                                fraction = (total.toFloat() / sectionTotal).coerceIn(0f, 1f),
                                                color = themedIdentityColor(categoryColor(tag.effectiveColor())),
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                formatPercentLabel(total.toFloat() / sectionTotal),
                                                Modifier.width(40.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = muted,
                                                textAlign = TextAlign.End,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                                if (categorized) Text(tag.categoryName ?: "—", Modifier.weight(0.8f), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (total > 0L) formatEuroCents(total) else "—",
                                    Modifier.width(120.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (total > 0L) MaterialTheme.colorScheme.onSurface else muted,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A tag over the whole page: what was spent with it straight on the page, how that splits between
 * trips beside it (a trip's line opens the trip), and its movements, scrolling under the rest.
 */
@Composable
private fun TagPane(
    detail: TagDetailState,
    viewModel: TagsViewModel,
    onBack: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onAddMovement: (() -> Unit)?,
    onOpenMovement: (MovementSummary) -> Unit,
) {
    val colors = FinanceTheme.colors
    val muted = colors.mutedText
    val tag = detail.tag
    // The one trip the tag belongs to, or the only one it was used in, is a button away.
    val onlyTrip: Pair<String, String?>? = tag.tripId?.let { it to tag.tripName }
        ?: detail.byTrip.singleOrNull()?.let { entry -> entry.tripId?.let { it to entry.tripName } }
    val deletedTrip = stringResource(SharedRes.string.tag_detail_deleted_trip)
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 760.dp
            val actions = @Composable {
                onlyTrip?.let { (tripId, tripName) ->
                    SecondaryButton(text = tripName ?: stringResource(SharedRes.string.tag_detail_trip), onClick = { onOpenTrip(tripId) })
                }
                SecondaryButton(text = stringResource(SharedRes.string.common_edit), onClick = { viewModel.onEditClicked(tag) })
                DestructiveButton(text = stringResource(SharedRes.string.common_archive), onClick = { viewModel.onArchiveClicked(tag) })
                if (onAddMovement != null) {
                    PrimaryButton(text = stringResource(SharedRes.string.entity_add_movement), onClick = onAddMovement, leadingIcon = Icons.Outlined.Add)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(SharedRes.string.tag_list_title))
                    }
                    IdentityIconTile(icon = categoryIcon(tag.effectiveIcon()), color = categoryColor(tag.effectiveColor()), size = 48.dp)
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        Text(tag.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(tag.scopeLabel(), tag.categoryName).joinToString(" · "),
                            color = muted,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (wide) actions()
                }
                if (!wide) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
        }
        if (detail.errorMessage != null) {
            InlineBanner(
                kind = BannerKind.Error,
                text = stringResource(SharedRes.string.failure_load_tags),
                actionLabel = stringResource(SharedRes.string.common_retry),
                onAction = { viewModel.onTagDetailOpened(tag.id) },
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val standing = @Composable {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Where it stands, straight on the page: no card around the figures.
                    Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Bottom) {
                        Figure(stringResource(SharedRes.string.tag_detail_total)) {
                            MoneyText(cents = detail.totalCents, style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 42.sp))
                        }
                        Figure(stringResource(SharedRes.string.tag_detail_movements)) {
                            Text(detail.movements.size.toString(), style = MaterialTheme.typography.headlineSmall)
                        }
                        if (detail.movements.isNotEmpty()) {
                            Figure(stringResource(Res.string.tags_per_movement)) {
                                MoneyText(cents = detail.totalCents / detail.movements.size, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                    detail.tripTotalCents?.takeIf { it > 0L && tag.tripName != null }?.let { tripTotal ->
                        Text(
                            stringResource(
                                SharedRes.string.tag_detail_trip_share,
                                formatPercentLabel(detail.totalCents.toFloat() / tripTotal),
                                tag.tripName.orEmpty(),
                            ),
                            color = muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            // A tag used across trips says how much went where.
            val trips: (@Composable () -> Unit)? = detail.byTrip.takeIf { it.size > 1 }?.let { byTrip ->
                {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(SharedRes.string.tag_detail_by_trip), style = MaterialTheme.typography.labelLarge, color = muted)
                        Breakdown(
                            entries = byTrip.map { entry ->
                                BreakdownEntry(
                                    name = entry.tripName ?: deletedTrip,
                                    icon = entry.tripType?.icon() ?: Icons.Outlined.Flight,
                                    color = if (entry.tripId == null) muted else categoryColor(entry.tripColor),
                                    cents = entry.actualCents,
                                    isRest = entry.tripId == null,
                                    onClick = entry.tripId?.let { id -> { onOpenTrip(id) } },
                                )
                            },
                            days = 0L,
                        )
                    }
                }
            }
            if (trips != null && maxWidth >= 900.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Box(Modifier.weight(1f)) { standing() }
                    Box(Modifier.width(440.dp)) { trips() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    standing()
                    trips?.invoke()
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(SharedRes.string.tag_detail_movements), style = MaterialTheme.typography.titleMedium)
            if (detail.movements.isNotEmpty()) Text(detail.movements.size.toString(), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        if (detail.movements.isEmpty()) {
            Text(stringResource(SharedRes.string.tag_detail_empty), color = muted)
        } else {
            PartMovements(detail.movements, onOpenMovement, Modifier.weight(1f), withinTag = true)
        }
    }
}
