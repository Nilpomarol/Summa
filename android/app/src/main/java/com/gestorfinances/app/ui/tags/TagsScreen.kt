package com.gestorfinances.app.ui.tags

import com.gestorfinances.app.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import com.gestorfinances.app.ui.common.ListPage
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.EntityListRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.common.EntityAppearancePickers
import com.gestorfinances.app.ui.common.EntityFormHeader
import com.gestorfinances.app.ui.common.EntityFormSheet
import androidx.compose.runtime.saveable.rememberSaveable
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripType
import com.gestorfinances.app.data.repository.effectiveColor
import com.gestorfinances.app.data.repository.effectiveIcon
import com.gestorfinances.app.data.repository.label
import com.gestorfinances.app.ui.common.CollapsibleSectionHeader
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.CategoryIconPalette
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.SearchField
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

/** This page visit's ViewModel, scoped to its navigation entry. */
@Composable
fun tagsViewModel(appContainer: AppContainer): TagsViewModel = viewModel {
    TagsViewModel(
        tagRepository = appContainer.tagRepository,
        tripRepository = appContainer.tripRepository,
        categoryRepository = appContainer.categoryRepository,
        movementRepository = appContainer.movementRepository,
        tripAnalysisRepository = appContainer.tripAnalysisRepository,
    )
}

@Composable
fun TagsScreen(
    viewModel: TagsViewModel,
    contextTripId: String?,
    dataVersion: Long,
    onBack: () -> Unit,
    onOpenDetail: (TagSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    // Reloads when data changes elsewhere, as the other list pages do (an undone delete, say).
    LaunchedEffect(viewModel, contextTripId, dataVersion) {
        viewModel.onScreenShown(contextTripId)
    }

    TagsContent(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onAdd = viewModel::onAddClicked,
        onSearchChanged = viewModel::onSearchChanged,
        onOpenDetail = onOpenDetail,
        onRetry = { viewModel.onScreenShown(contextTripId) },
    )

    TagFormSheet(state = state, viewModel = viewModel)
}

@Composable
internal fun TagArchiveDialog(viewModel: TagsViewModel, onArchived: DeleteUndoHandler) {
    AlertDialog(
        onDismissRequest = viewModel::onArchiveDismissed,
        title = { Text(text = stringResource(R.string.tag_archive_confirm_title)) },
        text = { Text(text = stringResource(R.string.tag_archive_warning)) },
        confirmButton = {
            DestructiveTextButton(onClick = { viewModel.onArchiveConfirmed(onSuccess = onArchived) }) {
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

/** A single group of tags to render under a collapsible section header. */
private data class TagSection(
    val key: String,
    val title: String,
    val tags: List<TagSummary>,
)

/** Splits the visible tags into Globals / per-event-type / per-specific-trip sections. */
@Composable
private fun buildTagSections(state: TagsUiState): List<TagSection> {
    val tags = state.visibleTags

    val globals = tags.filter { it.tripId == null && it.tripType == null }
    val sections = mutableListOf<TagSection>()
    if (globals.isNotEmpty()) {
        sections += TagSection(
            key = "global",
            title = stringResource(R.string.tag_section_global),
            tags = globals,
        )
    }

    TripType.entries.forEach { type ->
        val typeTags = tags.filter { it.tripId == null && it.tripType == type }
        if (typeTags.isNotEmpty()) {
            sections += TagSection(
                key = "type-${type.dbValue}",
                title = stringResource(R.string.tag_section_event_type, type.label()),
                tags = typeTags,
            )
        }
    }

    // activeTags only returns trip-scoped tags whose trip is still active (Tags.sq), so
    // tripName is always non-null here.
    val tripTags = tags.filter { it.tripId != null }.groupBy { it.tripId }
    tripTags.forEach { (tripId, tagsForTrip) ->
        val tripName = tagsForTrip.first().tripName ?: return@forEach
        sections += TagSection(
            key = "trip-$tripId",
            title = stringResource(R.string.tag_section_trip, tripName),
            tags = tagsForTrip,
        )
    }

    return sections
}

@Composable
private fun TagsContent(
    state: TagsUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onOpenDetail: (TagSummary) -> Unit,
    onRetry: () -> Unit,
) {
    val sections = buildTagSections(state)
    // Every section starts expanded; a key only ends up here once toggled shut.
    var collapsedSections by remember { mutableStateOf(setOf<String>()) }

    ListPage(
        title = stringResource(R.string.tag_list_title),
        onBack = onBack,
        addLabel = stringResource(R.string.tag_list_add),
        onAdd = onAdd,
        modifier = modifier,
    ) {
        state.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_tags,
                    onRetry = onRetry,
                )
            }
        }


        if (!state.isLoading && (state.tags.isNotEmpty() || state.searchQuery.isNotBlank())) {
            item {
                SearchField(
                    query = state.searchQuery,
                    onQueryChange = onSearchChanged,
                    placeholder = stringResource(R.string.tag_search_label),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.isLoading) {
            item {
                Text(
                    text = stringResource(R.string.tag_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (state.visibleTags.isEmpty()) {
            item {
                if (state.searchQuery.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.tag_search_empty),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.tag_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.tag_empty_body),
                            color = FinanceTheme.colors.mutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        } else {
            sections.forEach { section ->
                val expanded = section.key !in collapsedSections
                item(key = "header-${section.key}") {
                    CollapsibleSectionHeader(
                        title = section.title,
                        count = section.tags.size,
                        expanded = expanded,
                        onToggle = {
                            collapsedSections = if (expanded) {
                                collapsedSections + section.key
                            } else {
                                collapsedSections - section.key
                            }
                        },
                    )
                }
                if (expanded) {
                    item(key = "rows-${section.key}") {
                        // One item, so the rows sit flush and read as one list between their dividers.
                        Column {
                            section.tags.forEachIndexed { index, tag ->
                                TagRow(
                                    tag = tag,
                                    totalCents = state.tagTotals[tag.id] ?: 0L,
                                    isLast = index == section.tags.lastIndex,
                                    onOpen = { onOpenDetail(tag) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A tag: its mark, its default category if it has one, and what has been spent with it. */
@Composable
private fun TagRow(tag: TagSummary, totalCents: Long, isLast: Boolean, onOpen: () -> Unit) {
    EntityListRow(
        leading = { IdentityIconTile(icon = categoryIcon(tag.effectiveIcon()), color = categoryColor(tag.effectiveColor())) },
        title = tag.name,
        subtitle = tag.categoryName,
        isLast = isLast,
        onClick = onOpen,
        trailing = {
            if (totalCents > 0L) {
                MoneyText(
                    cents = totalCents,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
    )
}

private enum class TagScopeOption {
    GLOBAL,
    EVENT_TYPE,
    SPECIFIC_TRIP,
}

private fun TagFormState.scopeOption(): TagScopeOption =
    when {
        tripId != null -> TagScopeOption.SPECIFIC_TRIP
        tripType != null -> TagScopeOption.EVENT_TYPE
        else -> TagScopeOption.GLOBAL
    }

/** The tag create/edit sheet over whichever tag page opened it. */
@Composable
internal fun TagFormSheet(state: TagsUiState, viewModel: TagsViewModel) {
    var appearanceOpen by rememberSaveable(state.form?.id) { mutableStateOf(false) }
    EntityFormSheet(
        form = state.form,
        key = { it.id ?: "new-tag" },
        changed = { initial, current -> initial.withoutErrors() != current.withoutErrors() },
        onDiscard = viewModel::onFormDismissed,
        onSave = viewModel::onSaveClicked,
        title = { stringResource(if (it.id == null) R.string.tag_form_new_title else R.string.tag_form_edit_title) },
        saveLabel = { stringResource(if (it.id == null) R.string.tag_save_new else R.string.tag_save_changes) },
    ) { form ->
        val edit: (TagFormState) -> Unit = { viewModel.onFormChanged(it.withoutErrors()) }
        val trips = state.trips
        form.errorMessage?.let {
            InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_tag)
        }
        // Without its own look, a tag wears its category's, as it does in lists.
        val category = state.categories.firstOrNull { it.id == form.categoryId }
        EntityFormHeader(
            icon = categoryIcon(form.icon.ifBlank { null } ?: category?.icon),
            color = categoryColor(form.color.ifBlank { null } ?: category?.color),
            name = form.name,
            onNameChange = { edit(form.copy(name = it)) },
            nameLabel = stringResource(R.string.tag_field_name),
            onTileClick = { appearanceOpen = !appearanceOpen },
            nameError = form.errorRes?.takeIf { form.errorField == TagFormField.NAME }?.let { stringResource(it) },
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
            label = stringResource(R.string.tag_field_scope),
            options = TagScopeOption.entries,
            selected = form.scopeOption(),
            optionLabel = { it.label() },
            onSelect = { option ->
                edit(
                    when (option) {
                        TagScopeOption.GLOBAL -> form.copy(tripId = null, tripType = null)
                        TagScopeOption.EVENT_TYPE -> form.copy(
                            tripId = null,
                            tripType = form.tripType ?: TripType.entries.first(),
                        )
                        TagScopeOption.SPECIFIC_TRIP -> form.copy(
                            tripType = null,
                            tripId = form.tripId ?: trips.firstOrNull()?.id,
                        )
                    },
                )
            },
        )
        if (form.scopeOption() == TagScopeOption.EVENT_TYPE) {
            LabeledSegmentedControl(
                label = stringResource(R.string.trip_field_type),
                options = TripType.entries,
                selected = form.tripType ?: TripType.entries.first(),
                optionLabel = { it.label() },
                onSelect = { edit(form.copy(tripType = it)) },
            )
        }
        if (form.scopeOption() == TagScopeOption.SPECIFIC_TRIP) {
            val tripError = form.errorField == TagFormField.TRIP
            FormSelect(
                label = stringResource(R.string.tag_field_trip),
                options = trips.map { SelectOption(id = it.id, label = it.name) },
                selectedId = form.tripId,
                onSelect = { edit(form.copy(tripId = it)) },
                modifier = Modifier.scrollToWhen(tripError),
                isError = tripError,
                supportingText = form.errorRes?.takeIf { tripError }?.let { stringResource(it) },
            )
        }
        FormSelect(
            label = stringResource(R.string.tag_field_category),
            options = listOf(SelectOption(id = null, label = stringResource(R.string.tag_category_picker_none))) +
                state.categories.map { SelectOption(id = it.id, label = it.name) },
            selectedId = form.categoryId,
            onSelect = { edit(form.copy(categoryId = it)) },
            placeholder = stringResource(R.string.tag_category_picker_none),
        )
    }
}

private fun TagFormState.withoutErrors(): TagFormState = copy(errorRes = null, errorField = null, errorMessage = null)

@Composable
private fun TagScopeOption.label(): String =
    stringResource(
        when (this) {
            TagScopeOption.GLOBAL -> R.string.tag_scope_global
            TagScopeOption.EVENT_TYPE -> R.string.tag_scope_event_type
            TagScopeOption.SPECIFIC_TRIP -> R.string.tag_scope_specific_trip
        },
    )

/** Descriptive line under the tag name: which trip/event-type it's scoped to, if any. */
@Composable
internal fun TagSummary.scopeLabel(): String =
    when {
        tripName != null -> stringResource(R.string.tag_scope_trip_local_value, tripName)
        tripType != null -> stringResource(R.string.tag_scope_event_type_value, tripType.label())
        else -> stringResource(R.string.tag_scope_global)
    }

