package com.gestorfinances.app.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryNature
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    CategoriesContent(
        state = state,
        modifier = modifier,
        onAdd = viewModel::onAddClicked,
        onEdit = viewModel::onEditClicked,
        onArchive = viewModel::onArchiveClicked,
    )

    state.form?.let { form ->
        CategoryFormDialog(
            form = form,
            categories = state.categories,
            onFormChange = viewModel::onFormChanged,
            onDismiss = viewModel::onFormDismissed,
            onSave = viewModel::onSaveClicked,
        )
    }

    state.archiveCandidate?.let {
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(R.string.category_archive_confirm_title)) },
            text = { Text(text = stringResource(R.string.category_archive_warning)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::onArchiveConfirmed,
                ) {
                    Text(
                        text = stringResource(R.string.common_archive),
                        color = MaterialTheme.colorScheme.error,
                    )
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
private fun CategoriesContent(
    state: CategoriesUiState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onEdit: (CategoryRecord) -> Unit,
    onArchive: (CategoryRecord) -> Unit,
) {
    val rows = categoryRows(state.categories)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.category_list_title),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        state.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            UncategorizedCard()
        }

        if (state.isLoading) {
            item {
                Text(
                    text = stringResource(R.string.category_loading),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else if (state.categories.isEmpty()) {
            item {
                EmptyCategoriesCard(onAdd = onAdd)
            }
        } else {
            items(items = rows, key = { it.category.id }) { row ->
                CategoryRow(
                    row = row,
                    categories = state.categories,
                    onEdit = { onEdit(row.category) },
                    onArchive = { onArchive(row.category) },
                )
            }
            item {
                PrimaryButton(
                    text = stringResource(R.string.category_list_add),
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun UncategorizedCard() {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconChip(
                icon = categoryIcon(null),
                contentDescription = null,
                color = categoryColor(null),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.common_no_category),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.category_uncategorized_body),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun EmptyCategoriesCard(onAdd: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.category_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.category_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton(
                text = stringResource(R.string.category_list_add),
                onClick = onAdd,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    row: CategoryRow,
    categories: List<CategoryRecord>,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    val parentName = row.category.parentId?.let { parentId ->
        categories.firstOrNull { it.id == parentId }?.name
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (row.isChild) 20.dp else 0.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(
            icon = categoryIcon(row.category.icon),
            contentDescription = null,
            color = categoryColor(row.category.color),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.category.name,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "${row.category.kind.label()} · ${row.category.nature.label()}",
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            parentName?.let {
                Text(
                    text = stringResource(R.string.category_parent_value, it),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        CategoryRowMenu(onEdit = onEdit, onArchive = onArchive)
    }
}

@Composable
private fun CategoryRowMenu(
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
                text = { Text(stringResource(R.string.common_edit)) },
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
private fun CategoryFormDialog(
    form: CategoryFormState,
    categories: List<CategoryRecord>,
    onFormChange: (CategoryFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (form.id == null) R.string.category_form_new_title else R.string.category_form_edit_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                form.errorRes?.let {
                    Text(
                        text = stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                form.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onFormChange(form.copy(name = it, errorRes = null, errorMessage = null)) },
                    label = { Text(text = stringResource(R.string.category_field_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipFlowSection(label = stringResource(R.string.category_field_kind)) {
                    CategoryKind.entries.forEach { kind ->
                        FinanceFilterChip(
                            selected = form.kind == kind,
                            label = kind.label(),
                            onClick = { onFormChange(form.copy(kind = kind, errorRes = null, errorMessage = null)) },
                        )
                    }
                }
                ChipFlowSection(label = stringResource(R.string.category_field_nature)) {
                    CategoryNature.entries.forEach { nature ->
                        FinanceFilterChip(
                            selected = form.nature == nature,
                            label = nature.label(),
                            onClick = {
                                onFormChange(form.copy(nature = nature, errorRes = null, errorMessage = null))
                            },
                        )
                    }
                }
                CategoryParentSelector(
                    form = form,
                    categories = categories,
                    onParentSelected = {
                        onFormChange(form.copy(parentId = it, errorRes = null, errorMessage = null))
                    },
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(
                    text = stringResource(
                        if (form.id == null) R.string.category_save_new else R.string.category_save_changes,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun CategoryParentSelector(
    form: CategoryFormState,
    categories: List<CategoryRecord>,
    onParentSelected: (String?) -> Unit,
) {
    val hasActiveChildren = form.id != null && categories.any { it.parentId == form.id }
    val parentOptions = if (hasActiveChildren) {
        emptyList()
    } else {
        categories.filter { it.parentId == null && it.id != form.id }
    }

    if (hasActiveChildren) {
        Text(
            text = stringResource(R.string.category_parent_disabled_has_children),
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    ChipFlowSection(label = stringResource(R.string.category_field_parent)) {
        FinanceFilterChip(
            selected = form.parentId == null,
            label = stringResource(R.string.category_parent_none),
            onClick = { onParentSelected(null) },
        )
        parentOptions.forEach { category ->
            FinanceFilterChip(
                selected = form.parentId == category.id,
                label = category.name,
                onClick = { onParentSelected(category.id) },
            )
        }
    }
}

@Composable
private fun CategoryKind.label(): String =
    when (this) {
        CategoryKind.EXPENSE -> stringResource(R.string.category_kind_expense)
        CategoryKind.INCOME -> stringResource(R.string.category_kind_income)
        CategoryKind.BOTH -> stringResource(R.string.category_kind_both)
    }

@Composable
private fun CategoryNature.label(): String =
    when (this) {
        CategoryNature.FIXED -> stringResource(R.string.category_nature_fixed)
        CategoryNature.VARIABLE -> stringResource(R.string.category_nature_variable)
    }

private data class CategoryRow(
    val category: CategoryRecord,
    val isChild: Boolean,
)

private fun categoryRows(categories: List<CategoryRecord>): List<CategoryRow> {
    val activeIds = categories.map { it.id }.toSet()
    val childrenByParent = categories.groupBy { it.parentId }
    val roots = categories.filter { it.parentId == null || it.parentId !in activeIds }
        .sortedForDisplay()

    return roots.flatMap { root ->
        listOf(CategoryRow(root, isChild = false)) +
            childrenByParent[root.id].orEmpty()
                .sortedForDisplay()
                .map { CategoryRow(it, isChild = true) }
    }
}

private fun List<CategoryRecord>.sortedForDisplay(): List<CategoryRecord> =
    sortedWith(compareBy<CategoryRecord> { it.displayOrder }.thenBy { it.name.lowercase() })
