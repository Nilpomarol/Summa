package com.gestorfinances.app.ui.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
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
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

@Composable
fun TagsScreen(
    viewModel: TagsViewModel,
    contextTripId: String?,
    onBack: () -> Unit,
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
        onEdit = viewModel::onEditClicked,
        onArchive = viewModel::onArchiveClicked,
    )

    state.form?.let { form ->
        TagFormDialog(
            form = form,
            trips = state.trips,
            onFormChange = viewModel::onFormChanged,
            onDismiss = viewModel::onFormDismissed,
            onSave = viewModel::onSaveClicked,
        )
    }

    state.archiveCandidate?.let {
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(R.string.tag_archive_confirm_title)) },
            text = { Text(text = stringResource(R.string.tag_archive_warning)) },
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
private fun TagsContent(
    state: TagsUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (TagSummary) -> Unit,
    onArchive: (TagSummary) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.tag_list_title),
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onBack) {
                            Text(text = stringResource(R.string.common_back))
                        }
                        TextButton(onClick = onAdd) {
                            Text(text = stringResource(R.string.tag_list_add))
                        }
                    }
                },
            )
        }

        state.errorMessage?.let { message ->
            item {
                InlineBanner(kind = BannerKind.Error, text = message)
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
            item { EmptyTagsCard(onAdd = onAdd) }
        } else {
            items(items = state.visibleTags, key = { it.id }) { tag ->
                TagRow(
                    tag = tag,
                    onEdit = { onEdit(tag) },
                    onArchive = { onArchive(tag) },
                )
            }
            item {
                PrimaryButton(
                    text = stringResource(R.string.tag_list_add),
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EmptyTagsCard(onAdd: () -> Unit) {
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
            PrimaryButton(
                text = stringResource(R.string.tag_list_add),
                onClick = onAdd,
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
    val identityColor = categoryColor(tag.color)
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
                    icon = Icons.AutoMirrored.Outlined.Label,
                    contentDescription = null,
                    color = identityColor,
                    size = 42.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = tag.scopeLabel(),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NeutralPill(
                            text = if (tag.tripId == null) {
                                stringResource(R.string.tag_scope_global)
                            } else {
                                stringResource(R.string.tag_scope_trip_local)
                            },
                        )
                        tag.icon?.takeIf { it.isNotBlank() }?.let {
                            NeutralPill(text = it)
                        }
                    }
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
private fun TagFormDialog(
    form: TagFormState,
    trips: List<TripSummary>,
    onFormChange: (TagFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (form.id == null) R.string.tag_form_new_title else R.string.tag_form_edit_title,
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
                    label = { Text(text = stringResource(R.string.tag_field_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipFlowSection(label = stringResource(R.string.tag_field_scope)) {
                    FinanceFilterChip(
                        selected = form.tripId == null,
                        label = stringResource(R.string.tag_scope_global),
                        onClick = { onFormChange(form.copy(tripId = null)) },
                    )
                    FinanceFilterChip(
                        selected = form.tripId != null,
                        label = stringResource(R.string.tag_scope_trip_local),
                        onClick = { onFormChange(form.copy(tripId = trips.firstOrNull()?.id)) },
                    )
                }
                if (form.tripId != null) {
                    ChipFlowSection(label = stringResource(R.string.tag_field_trip)) {
                        trips.forEach { trip ->
                            FinanceFilterChip(
                                selected = form.tripId == trip.id,
                                label = trip.name,
                                onClick = { onFormChange(form.copy(tripId = trip.id)) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = form.icon,
                    onValueChange = { onFormChange(form.copy(icon = it)) },
                    label = { Text(text = stringResource(R.string.tag_field_icon)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.color,
                    onValueChange = { onFormChange(form.copy(color = it)) },
                    label = { Text(text = stringResource(R.string.tag_field_color)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(
                    if (form.id == null) R.string.tag_save_new else R.string.tag_save_changes,
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
private fun TagSummary.scopeLabel(): String =
    tripName?.let { stringResource(R.string.tag_scope_trip_local_value, it) }
        ?: stringResource(R.string.tag_scope_global)
