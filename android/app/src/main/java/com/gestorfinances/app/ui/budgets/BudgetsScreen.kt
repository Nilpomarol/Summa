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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.BudgetEvaluation
import com.gestorfinances.app.data.repository.BudgetPeriod
import com.gestorfinances.app.data.repository.BudgetProjection
import com.gestorfinances.app.data.repository.BudgetScope
import com.gestorfinances.app.data.repository.BudgetSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.BudgetProgressBar
import com.gestorfinances.app.ui.common.BudgetForecastCard
import com.gestorfinances.app.ui.common.CollapsibleSectionHeader
import com.gestorfinances.app.ui.common.CompactEditIconButton
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.color
import com.gestorfinances.app.ui.common.label
import com.gestorfinances.app.ui.common.progressFraction
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.formatMonthYear
import com.gestorfinances.app.ui.common.inPickerHierarchyOrder
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.YearMonth

@Composable
fun BudgetsScreen(
    viewModel: BudgetsViewModel,
    onBack: () -> Unit,
    onOpenCategoryMovements: (CategoryRecord) -> Unit = {},
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
        onAdd = { viewModel.onAddClicked() },
        onAddOverall = viewModel::onAddOverallClicked,
        onPreviousMonth = viewModel::onPreviousMonthClicked,
        onNextMonth = viewModel::onNextMonthClicked,
        onEdit = viewModel::onEditClicked,
        onDelete = viewModel::onDeleteClicked,
        onOpenCategoryMovements = onOpenCategoryMovements,
        onTogglePastTrips = viewModel::onPastTripsExpandedToggled,
    )

    state.form?.let { form ->
        BudgetFormSheet(
            form = form,
            categories = state.categories,
            trips = state.trips,
            onFormChange = viewModel::onFormChanged,
            onDismiss = viewModel::onFormDismissed,
            onSave = viewModel::onSaveClicked,
            onDelete = viewModel::onDeleteEditingBudgetClicked,
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
    onAddOverall: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEdit: (BudgetSummary) -> Unit,
    onDelete: (BudgetSummary) -> Unit,
    onOpenCategoryMovements: (CategoryRecord) -> Unit,
    onTogglePastTrips: () -> Unit,
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

        val projectionsById = state.projections.associateBy { it.evaluation.budget.id }
        val overall = state.projections.firstOrNull {
            it.evaluation.budget.scope == BudgetScope.OVERALL_MONTH
        }
        val overallEvaluation = state.evaluations.firstOrNull {
            it.budget.scope == BudgetScope.OVERALL_MONTH
        }
        val monthlyCategories = state.evaluations.filter {
            it.budget.scope == BudgetScope.CATEGORY && it.budget.period == BudgetPeriod.MONTHLY
        }
        val yearlyCategories = state.evaluations.filter {
            it.budget.scope == BudgetScope.CATEGORY && it.budget.period == BudgetPeriod.YEARLY
        }
        val tripBudgets = state.evaluations.filter { it.budget.scope == BudgetScope.TRIP }
        val tripsById = state.trips.associateBy { it.id }
        val monthStart = state.selectedMonth.atDay(1).toString()
        val monthEnd = state.selectedMonth.atEndOfMonth().toString()
        val currentTripBudgets = tripBudgets.filter { evaluation ->
            tripsById[evaluation.budget.tripId]?.isDuring(monthStart, monthEnd) == true
        }
        val futureTripBudgets = tripBudgets.filter { evaluation ->
            tripsById[evaluation.budget.tripId]?.startsAfter(monthEnd) == true
        }
        val pastTripBudgets = tripBudgets - currentTripBudgets.toSet() - futureTripBudgets.toSet()

        if (overall != null) {
            item {
                BudgetForecastCard(
                    title = stringResource(R.string.budget_current_month),
                    projection = overall,
                    titleContent = { modifier ->
                        BudgetMonthSelector(
                            month = state.selectedMonth,
                            canMoveForward = state.selectedMonth < YearMonth.now(),
                            onPrevious = onPreviousMonth,
                            onNext = onNextMonth,
                            modifier = modifier,
                        )
                    },
                    showBreakdown = state.selectedMonth == YearMonth.now(),
                    onEdit = { onEdit(overall.evaluation.budget) },
                )
            }
        } else if (overallEvaluation != null) {
            item {
                BudgetRow(
                    evaluation = overallEvaluation,
                    headerContent = { modifier ->
                        BudgetMonthSelector(
                            month = state.selectedMonth,
                            canMoveForward = state.selectedMonth < YearMonth.now(),
                            onPrevious = onPreviousMonth,
                            onNext = onNextMonth,
                            modifier = modifier,
                        )
                    },
                    onEdit = { onEdit(overallEvaluation.budget) },
                )
            }
        } else if (!state.isLoading && state.selectedMonth == YearMonth.now()) {
            item {
                OverallBudgetEmptyCard(
                    onAddOverall = onAddOverall,
                    month = state.selectedMonth,
                    canMoveForward = state.selectedMonth < YearMonth.now(),
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                )
            }
        }

        if (currentTripBudgets.isNotEmpty()) {
            items(items = currentTripBudgets, key = { it.budget.id }) { evaluation ->
                BudgetRow(evaluation = evaluation, onEdit = { onEdit(evaluation.budget) })
            }
        }

        if (monthlyCategories.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.budget_monthly_categories)) }
            items(items = monthlyCategories, key = { it.budget.id }) { evaluation ->
                BudgetRow(
                    evaluation = evaluation,
                    projection = projectionsById[evaluation.budget.id],
                    onEdit = { onEdit(evaluation.budget) },
                    onCategoryClick = evaluation.budget.categoryId?.let { id ->
                        { state.categories.firstOrNull { it.id == id }?.let(onOpenCategoryMovements) }
                    },
                )
            }
        }

        if (yearlyCategories.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.budget_yearly_categories)) }
            items(items = yearlyCategories, key = { it.budget.id }) { evaluation ->
                BudgetRow(
                    evaluation = evaluation,
                    onEdit = { onEdit(evaluation.budget) },
                    onCategoryClick = evaluation.budget.categoryId?.let { id ->
                        { state.categories.firstOrNull { it.id == id }?.let(onOpenCategoryMovements) }
                    },
                )
            }
        }

        if (futureTripBudgets.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.budget_trip_future)) }
            items(items = futureTripBudgets, key = { it.budget.id }) { evaluation ->
                BudgetRow(
                    evaluation = evaluation,
                    onEdit = { onEdit(evaluation.budget) },
                )
            }
        }

        if (pastTripBudgets.isNotEmpty()) {
            item {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.budget_trip_past),
                    count = pastTripBudgets.size,
                    expanded = state.pastTripsExpanded,
                    onToggle = onTogglePastTrips,
                )
            }
            if (state.pastTripsExpanded) {
                items(items = pastTripBudgets, key = { it.budget.id }) { evaluation ->
                    BudgetRow(evaluation = evaluation, onEdit = { onEdit(evaluation.budget) })
                }
            }
        }

        if (!state.isLoading && overallEvaluation == null && monthlyCategories.isEmpty() && yearlyCategories.isEmpty() && tripBudgets.isEmpty()) {
            item { EmptyBudgetsCard(onAdd = onAdd) }
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

private fun TripSummary.isDuring(monthStart: String, monthEnd: String): Boolean =
    startDate != null && startDate <= monthEnd && (endDate == null || endDate >= monthStart)

private fun TripSummary.startsAfter(monthEnd: String): Boolean =
    startDate != null && startDate > monthEnd

@Composable
private fun BudgetMonthSelector(
    month: YearMonth,
    canMoveForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.budget_previous_month))
        }
        Text(text = formatMonthYear(month), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onNext, enabled = canMoveForward) {
            Icon(Icons.Outlined.ChevronRight, stringResource(R.string.budget_next_month))
        }
    }
}

@Composable
private fun BudgetRow(
    evaluation: BudgetEvaluation,
    projection: BudgetProjection? = null,
    title: String? = null,
    headerContent: (@Composable (Modifier) -> Unit)? = null,
    onEdit: () -> Unit,
    onCategoryClick: (() -> Unit)? = null,
) {
    val color = evaluation.status.color()
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (evaluation.budget.scope == BudgetScope.CATEGORY) {
                    Box(
                        modifier = if (onCategoryClick != null) {
                            Modifier.clickable(onClick = onCategoryClick)
                        } else {
                            Modifier
                        },
                    ) {
                        IconChip(
                            icon = categoryIcon(evaluation.budget.categoryIcon),
                            contentDescription = null,
                            color = categoryColor(evaluation.budget.categoryColor),
                            size = 28.dp,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (headerContent != null) {
                    headerContent(Modifier.weight(1f))
                } else {
                    Text(
                        text = title ?: evaluation.budget.displayName
                            ?: stringResource(R.string.common_no_category),
                        modifier = Modifier
                            .weight(1f)
                            .then(if (onCategoryClick != null) Modifier.clickable(onClick = onCategoryClick) else Modifier),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                NeutralPill(text = evaluation.status.label())
                CompactEditIconButton(onClick = onEdit)
            }
            projection?.let {
                Text(
                    text = stringResource(R.string.budget_forecast_amount, formatEuroCents(it.forecastCents)),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
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
private fun OverallBudgetEmptyCard(
    onAddOverall: () -> Unit,
    month: YearMonth,
    canMoveForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BudgetMonthSelector(
                month = month,
                canMoveForward = canMoveForward,
                onPrevious = onPrevious,
                onNext = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.budget_overall_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.budget_overall_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton(
                text = stringResource(R.string.budget_add_overall),
                onClick = onAddOverall,
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
private fun BudgetFormSheet(
    form: BudgetFormState,
    categories: List<CategoryRecord>,
    trips: List<TripSummary>,
    onFormChange: (BudgetFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        maxHeightFraction = 0.84f,
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
            form.errorMessage?.let {
            InlineBanner(kind = BannerKind.Error, text = it)
        }
        if (form.errorField == null && form.errorRes != null) {
            InlineBanner(kind = BannerKind.Error, text = stringResource(form.errorRes))
        }
        if (
            form.id != null &&
            form.scope == BudgetScope.CATEGORY &&
            form.period == BudgetPeriod.MONTHLY
        ) {
            InlineBanner(
                kind = BannerKind.Info,
                text = stringResource(R.string.budget_existing_monthly_hint),
            )
        }
        if (form.id == null && form.scope != BudgetScope.OVERALL_MONTH) {
            SegmentedControl(
            options = listOf(BudgetScope.CATEGORY, BudgetScope.TRIP),
            selected = form.scope,
            label = { it.label() },
            onSelect = { scope ->
                onFormChange(
                    when (scope) {
                        BudgetScope.OVERALL_MONTH -> form.copy(
                            scope = scope,
                            period = BudgetPeriod.MONTHLY,
                            categoryId = null,
                            tripId = null,
                        )
                        BudgetScope.CATEGORY -> form.copy(
                            scope = scope,
                            period = form.period.takeIf { it == BudgetPeriod.YEARLY } ?: BudgetPeriod.MONTHLY,
                            tripId = null,
                        )
                        BudgetScope.TRIP -> form.copy(
                            scope = scope,
                            period = BudgetPeriod.ONE_OFF,
                            categoryId = null,
                        )
                    },
                )
            },
            )
        }
        val categoryError = form.errorField == BudgetFormField.CATEGORY
        val tripError = form.errorField == BudgetFormField.TRIP
        when (form.scope) {
            BudgetScope.OVERALL_MONTH -> {
                Text(
                    text = stringResource(R.string.budget_overall_form_hint),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            BudgetScope.CATEGORY -> {
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
            SegmentedControl(
                options = listOf(BudgetPeriod.MONTHLY, BudgetPeriod.YEARLY),
                selected = form.period,
                label = { it.label() },
                onSelect = { period -> onFormChange(form.copy(period = period)) },
            )
        }
            BudgetScope.TRIP -> {
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
                    onFormChange(form.copy(tripId = tripId))
                },
                modifier = Modifier.scrollToWhen(tripError),
                isError = tripError,
                supportingText = if (tripError && form.errorRes != null) {
                    stringResource(form.errorRes)
                } else null,
            )
            }
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
        if (form.id != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                DestructiveTextButton(onClick = onDelete) {
                    Text(text = stringResource(R.string.common_archive))
                }
            }
        }
        }
    }
}

@Composable
private fun BudgetScope.label(): String =
    stringResource(
        when (this) {
            BudgetScope.OVERALL_MONTH -> R.string.budget_scope_overall_month
            BudgetScope.CATEGORY -> R.string.budget_scope_category
            BudgetScope.TRIP -> R.string.budget_scope_trip
        },
    )

@Composable
private fun BudgetPeriod.label(): String =
    stringResource(
        when (this) {
            BudgetPeriod.MONTHLY -> R.string.budget_period_monthly
            BudgetPeriod.YEARLY -> R.string.budget_period_yearly
            BudgetPeriod.ONE_OFF -> R.string.budget_period_one_off
        },
    )
