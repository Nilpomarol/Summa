package com.gestorfinances.app.ui.budgets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.BudgetSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.color
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.progressFraction
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.inPickerHierarchyOrder
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

@Composable
fun BudgetsScreen(
    viewModel: BudgetsViewModel,
    onBack: () -> Unit,
    contextTripId: String? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, contextTripId) {
        viewModel.onScreenShown(contextTripId)
    }

    val form = state.form
    if (form != null) {
        BackHandler(onBack = viewModel::onFormDismissed)
        BudgetFormScreen(
            form = form,
            categories = state.categories,
            trips = state.trips,
            onFormChange = viewModel::onFormChanged,
            onBack = viewModel::onFormDismissed,
            onSave = viewModel::onSaveClicked,
            modifier = modifier,
        )
    } else {
        BudgetsContent(
            state = state,
            modifier = modifier,
            onBack = onBack,
            onAdd = { viewModel.onAddClicked() },
            onEdit = viewModel::onEditClicked,
            onDelete = viewModel::onDeleteClicked,
        )
    }

    state.archiveCandidate?.let {
        AlertDialog(
            onDismissRequest = viewModel::onArchiveDismissed,
            title = { Text(text = stringResource(R.string.budget_archive_confirm_title)) },
            text = { Text(text = stringResource(R.string.budget_archive_warning)) },
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
private fun BudgetsContent(
    state: BudgetsUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (BudgetSummary) -> Unit,
    onDelete: (BudgetSummary) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.budget_list_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }

        state.errorMessage?.let { message ->
            item {
                InlineBanner(kind = BannerKind.Error, text = message)
            }
        }

        if (!state.isLoading && state.evaluations.isEmpty()) {
            item { EmptyBudgetsCard(onAdd = onAdd) }
        } else {
            items(items = state.evaluations, key = { it.budget.id }) { evaluation ->
                BudgetRow(
                    evaluation = evaluation,
                    onEdit = { onEdit(evaluation.budget) },
                    onDelete = { onDelete(evaluation.budget) },
                )
            }
            item {
                PrimaryButton(
                    text = stringResource(R.string.budget_list_add),
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BudgetRow(
    evaluation: BudgetEvaluation,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val color = evaluation.status.color()
    FinanceCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = evaluation.budget.displayName
                        ?: stringResource(R.string.common_no_category),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                NeutralPill(text = evaluation.status.label())
                BudgetRowMenu(onEdit = onEdit, onDelete = onDelete)
            }
            BudgetProgressBar(
                fraction = evaluation.progressFraction(),
                color = color,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.budget_progress,
                        formatEuroCents(evaluation.actualCents),
                        formatEuroCents(evaluation.budget.limitAmountCents),
                    ),
                    modifier = Modifier.weight(1f),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (evaluation.remainingCents >= 0L) {
                        stringResource(R.string.budget_remaining, formatEuroCents(evaluation.remainingCents))
                    } else {
                        stringResource(R.string.budget_over, formatEuroCents(-evaluation.remainingCents))
                    },
                    color = color,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun BudgetRowMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

@Composable
private fun EmptyBudgetsCard(onAdd: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.budget_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.budget_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton(
                text = stringResource(R.string.budget_list_add),
                onClick = onAdd,
            )
        }
    }
}

@Composable
private fun BudgetFormScreen(
    form: BudgetFormState,
    categories: List<CategoryRecord>,
    trips: List<TripSummary>,
    onFormChange: (BudgetFormState) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeaderRow(
            onBack = onBack,
            title = stringResource(
                if (form.id == null) R.string.budget_new_title else R.string.budget_edit_title,
            ),
        )
        form.errorMessage?.let {
            InlineBanner(kind = BannerKind.Error, text = it)
        }
        SegmentedControl(
            options = BudgetScope.entries,
            selected = form.scope,
            label = { it.label() },
            onSelect = { scope ->
                onFormChange(
                    form.copy(
                        scope = scope,
                        categoryId = if (scope == BudgetScope.CATEGORY) form.categoryId else null,
                        tripId = if (scope == BudgetScope.TRIP) form.tripId else null,
                    ),
                )
            },
        )
        val categoryError = form.errorField == BudgetFormField.CATEGORY
        val tripError = form.errorField == BudgetFormField.TRIP
        if (form.scope == BudgetScope.CATEGORY) {
            FormSelect(
                label = stringResource(R.string.budget_field_category),
                // A container (parent with children) stays selectable here — budgeting it rolls up
                // all its children — but children are shown indented beneath it for clarity.
                options = categories.inPickerHierarchyOrder().map { (category, indented) ->
                    SelectOption(
                        id = category.id,
                        label = category.name,
                        leading = {
                            IconChip(
                                icon = categoryIcon(category.icon),
                                contentDescription = null,
                                color = categoryColor(category.color),
                                size = 24.dp,
                            )
                        },
                        indented = indented,
                    )
                },
                selectedId = form.categoryId,
                onSelect = { onFormChange(form.copy(categoryId = it)) },
                modifier = Modifier.scrollToWhen(categoryError),
                isError = categoryError,
                supportingText = if (categoryError && form.errorRes != null) {
                    stringResource(form.errorRes)
                } else null,
            )
        } else {
            FormSelect(
                label = stringResource(R.string.budget_field_trip),
                options = trips.map { trip ->
                    SelectOption(
                        id = trip.id,
                        label = trip.name,
                        leading = {
                            IconChip(
                                icon = categoryIcon(trip.icon),
                                contentDescription = null,
                                color = categoryColor(trip.color),
                                size = 24.dp,
                            )
                        },
                    )
                },
                selectedId = form.tripId,
                onSelect = { tripId ->
                    val trip = trips.firstOrNull { it.id == tripId }
                    onFormChange(
                        form.copy(
                            tripId = tripId,
                            startDate = form.startDate.ifBlank { trip?.startDate.orEmpty() },
                        ),
                    )
                },
                modifier = Modifier.scrollToWhen(tripError),
                isError = tripError,
                supportingText = if (tripError && form.errorRes != null) {
                    stringResource(form.errorRes)
                } else null,
            )
        }
        val limitError = form.errorField == BudgetFormField.LIMIT
        OutlinedTextField(
            value = form.limit,
            onValueChange = { onFormChange(form.copy(limit = it)) },
            label = { Text(text = stringResource(R.string.budget_field_limit)) },
            prefix = { Text(text = "€") },
            singleLine = true,
            isError = limitError,
            supportingText = if (limitError && form.errorRes != null) {
                { Text(text = stringResource(form.errorRes)) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(limitError),
        )
        val thresholdError = form.errorField == BudgetFormField.THRESHOLD
        OutlinedTextField(
            value = form.threshold,
            onValueChange = { onFormChange(form.copy(threshold = it)) },
            label = { Text(text = stringResource(R.string.budget_field_threshold)) },
            suffix = { Text(text = "%") },
            singleLine = true,
            isError = thresholdError,
            supportingText = if (thresholdError && form.errorRes != null) {
                { Text(text = stringResource(form.errorRes)) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(thresholdError),
        )
        val startDateError = form.errorField == BudgetFormField.START_DATE
        FormDatePicker(
            label = stringResource(R.string.budget_field_start),
            date = form.startDate,
            onDateChange = { onFormChange(form.copy(startDate = it)) },
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(startDateError),
            isError = startDateError,
            supportingText = if (startDateError && form.errorRes != null) {
                stringResource(form.errorRes)
            } else null,
            onClear = { onFormChange(form.copy(startDate = "")) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(text = stringResource(R.string.common_cancel))
            }
            PrimaryButton(
                text = stringResource(
                    if (form.id == null) R.string.budget_save_new else R.string.budget_save_changes,
                ),
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BudgetScope.label(): String =
    stringResource(
        when (this) {
            BudgetScope.CATEGORY -> R.string.budget_scope_category
            BudgetScope.TRIP -> R.string.budget_scope_trip
        },
    )
