package com.gestorfinances.app.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.color
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.progressFraction
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.theme.FinanceTheme

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

    BudgetsContent(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onAdd = viewModel::onAddClicked,
        onEdit = viewModel::onEditClicked,
        onDelete = viewModel::onDeleteClicked,
    )

    state.form?.let { form ->
        BudgetFormDialog(
            form = form,
            categories = state.categories,
            trips = state.trips,
            onFormChange = viewModel::onFormChanged,
            onDismiss = viewModel::onFormDismissed,
            onSave = viewModel::onSaveClicked,
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
                        contentDescription = stringResource(R.string.common_done),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetFormDialog(
    form: BudgetFormState,
    categories: List<CategoryRecord>,
    trips: List<TripSummary>,
    onFormChange: (BudgetFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(
                    if (form.id == null) R.string.budget_new_title else R.string.budget_edit_title,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            form.errorRes?.let {
                InlineBanner(kind = BannerKind.Error, text = stringResource(it))
            }
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
            if (form.scope == BudgetScope.CATEGORY) {
                ChipFlowSection(label = stringResource(R.string.budget_field_category)) {
                    categories.forEach { category ->
                        FinanceFilterChip(
                            selected = form.categoryId == category.id,
                            label = category.name,
                            onClick = { onFormChange(form.copy(categoryId = category.id)) },
                        )
                    }
                }
            } else {
                ChipFlowSection(label = stringResource(R.string.budget_field_trip)) {
                    trips.forEach { trip ->
                        FinanceFilterChip(
                            selected = form.tripId == trip.id,
                            label = trip.name,
                            onClick = {
                                onFormChange(
                                    form.copy(
                                        tripId = trip.id,
                                        startDate = form.startDate.ifBlank { trip.startDate.orEmpty() },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = form.limit,
                onValueChange = { onFormChange(form.copy(limit = it)) },
                label = { Text(text = stringResource(R.string.budget_field_limit)) },
                prefix = { Text(text = "€") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.threshold,
                onValueChange = { onFormChange(form.copy(threshold = it)) },
                label = { Text(text = stringResource(R.string.budget_field_threshold)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.startDate,
                onValueChange = { onFormChange(form.copy(startDate = it)) },
                label = { Text(text = stringResource(R.string.budget_field_start)) },
                supportingText = { Text(text = stringResource(R.string.movement_date_format_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        if (form.id == null) R.string.budget_save_new else R.string.budget_save_changes,
                    ),
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                )
            }
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
