package com.gestorfinances.app.ui.tags

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import com.gestorfinances.app.ui.common.AppDropdownMenu
import com.gestorfinances.app.ui.common.AppDropdownMenuItem
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.TripType
import com.gestorfinances.app.data.repository.effectiveColor
import com.gestorfinances.app.data.repository.effectiveIcon
import com.gestorfinances.app.data.repository.label
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.CollapsibleSectionHeader
import com.gestorfinances.app.ui.common.ColorPickerRow
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IconPickerRow
import com.gestorfinances.app.ui.common.CategoryIconPalette
import com.gestorfinances.app.ui.common.DeleteUndoHandler
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.common.SearchField
import com.gestorfinances.app.ui.common.rememberFormDismissGuard
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

@Composable
fun TagsScreen(
    viewModel: TagsViewModel,
    contextTripId: String?,
    onBack: () -> Unit,
    onDeleteCommitted: DeleteUndoHandler = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, contextTripId) {
        viewModel.onScreenShown(contextTripId)
    }

    TagsContent(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onAdd = viewModel::onAddClicked,
        onSearchChanged = viewModel::onSearchChanged,
        onEdit = viewModel::onEditClicked,
        onArchive = viewModel::onArchiveClicked,
        onRetry = { viewModel.onScreenShown(contextTripId) },
    )

    state.form?.let { form ->
        val requestFormDismissal = rememberFormDismissGuard(
            formKey = form.id ?: "new-tag",
            currentValue = form,
            hasMeaningfulChanges = { initial, current ->
                initial.copy(errorRes = null, errorField = null, errorMessage = null) !=
                    current.copy(errorRes = null, errorField = null, errorMessage = null)
            },
            onDiscard = viewModel::onFormDismissed,
        )
        BackHandler(onBack = requestFormDismissal)
        TagFormSheet(
            form = form,
            trips = state.trips,
            categories = state.categories,
            onFormChange = viewModel::onFormChanged,
            onDismiss = requestFormDismissal,
            onSave = viewModel::onSaveClicked,
        )
    }

    state.archiveCandidate?.let {
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(R.string.tag_archive_confirm_title)) },
            text = { Text(text = stringResource(R.string.tag_archive_warning)) },
            confirmButton = {
                DestructiveTextButton(
                    onClick = { viewModel.onArchiveConfirmed(onSuccess = onDeleteCommitted) },
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
    onEdit: (TagSummary) -> Unit,
    onArchive: (TagSummary) -> Unit,
    onRetry: () -> Unit,
) {
    val sections = buildTagSections(state)
    // Every section starts expanded; a key only ends up here once toggled shut.
    var collapsedSections by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeaderRow(
                onBack = onBack,
                title = stringResource(R.string.tag_list_title),
            )
        }

        state.errorMessage?.let { message ->
            item {
                InlineFailureBanner(
                    diagnostic = message,
                    messageRes = R.string.failure_load_tags,
                    onRetry = onRetry,
                )
            }
        }

        item {
            PrimaryButton(
                text = stringResource(R.string.tag_list_add),
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
            )
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
                    EmptyTagsCard()
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
                    items(items = section.tags, key = { "${section.key}-${it.id}" }) { tag ->
                        TagRow(
                            tag = tag,
                            onEdit = { onEdit(tag) },
                            onArchive = { onArchive(tag) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTagsCard() {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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

@Composable
private fun TagRow(
    tag: TagSummary,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    val identityColor = categoryColor(tag.effectiveColor())
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconChip(
                    icon = categoryIcon(tag.effectiveIcon()),
                    contentDescription = null,
                    color = identityColor,
                    size = 42.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = tag.scopeLabel(),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    tag.categoryName?.let { NeutralPill(text = it) }
                }
                TagRowMenu(onEdit = onEdit, onArchive = onArchive)
            }
        }
    }
}

@Composable
private fun TagRowMenu(
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

@Composable
private fun TagFormSheet(
    form: TagFormState,
    trips: List<TripSummary>,
    categories: List<CategoryRecord>,
    onFormChange: (TagFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        maxHeightFraction = 0.88f,
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(
                        if (form.id == null) R.string.tag_form_new_title else R.string.tag_form_edit_title,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )

                form.errorMessage?.let {
                    InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_tag)
                }

                val nameError = form.errorField == TagFormField.NAME
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onFormChange(form.copy(name = it)) },
                    label = { Text(text = stringResource(R.string.tag_field_name)) },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError && form.errorRes != null) {
                        { Text(text = stringResource(form.errorRes)) }
                    } else null,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = doneKeyboardActions(onSave),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scrollToWhen(nameError),
                )

                LabeledSegmentedControl(
                    label = stringResource(R.string.tag_field_scope),
                    options = TagScopeOption.entries,
                    selected = form.scopeOption(),
                    optionLabel = { it.label() },
                    onSelect = { option ->
                        onFormChange(
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
                        onSelect = { onFormChange(form.copy(tripType = it)) },
                    )
                }

                if (form.scopeOption() == TagScopeOption.SPECIFIC_TRIP) {
                    val tripError = form.errorField == TagFormField.TRIP
                    FormSelect(
                        label = stringResource(R.string.tag_field_trip),
                        options = trips.map { SelectOption(id = it.id, label = it.name) },
                        selectedId = form.tripId,
                        onSelect = { onFormChange(form.copy(tripId = it)) },
                        modifier = Modifier.scrollToWhen(tripError),
                        isError = tripError,
                        supportingText = if (tripError && form.errorRes != null) stringResource(form.errorRes) else null,
                    )
                }

                FormSelect(
                    label = stringResource(R.string.tag_field_category),
                    options = listOf(SelectOption(id = null, label = stringResource(R.string.tag_category_picker_none))) +
                        categories.map { SelectOption(id = it.id, label = it.name) },
                    selectedId = form.categoryId,
                    onSelect = { onFormChange(form.copy(categoryId = it)) },
                    placeholder = stringResource(R.string.tag_category_picker_none),
                )

                ColorPickerRow(
                    label = stringResource(R.string.tag_field_color),
                    selectedHex = form.color.ifBlank { null },
                    onSelect = { onFormChange(form.copy(color = it)) },
                )

                IconPickerRow(
                    label = stringResource(R.string.tag_field_icon),
                    options = CategoryIconPalette,
                    selectedKey = form.icon.ifBlank { null },
                    onSelect = { onFormChange(form.copy(icon = it)) },
                )
            }

            HorizontalDivider(color = FinanceTheme.colors.cardBorder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
                PrimaryButton(
                    text = stringResource(
                        if (form.id == null) R.string.tag_save_new else R.string.tag_save_changes,
                    ),
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

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
private fun TagSummary.scopeLabel(): String =
    when {
        tripName != null -> stringResource(R.string.tag_scope_trip_local_value, tripName)
        tripType != null -> stringResource(R.string.tag_scope_event_type_value, tripType.label())
        else -> stringResource(R.string.tag_scope_global)
    }

