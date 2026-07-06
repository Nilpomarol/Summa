package com.gestorfinances.app.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.DetectedTemplateAction
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SectionHeader
import com.gestorfinances.app.ui.common.TopBarIconButton
import com.gestorfinances.app.ui.movements.AccountSelect
import com.gestorfinances.app.ui.movements.CategorySelect
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.FormToggleRow
import com.gestorfinances.app.ui.movements.MovementTypeSelector
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor

@Composable
fun RecurringScreen(
    viewModel: RecurringViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onScreenShown()
    }

    RecurringContent(
        state = state,
        modifier = modifier,
        onAdd = viewModel::onAddClicked,
        onEdit = viewModel::onEditClicked,
        onPause = viewModel::onPauseClicked,
        onResume = viewModel::onResumeClicked,
        onEnd = viewModel::onEndClicked,
        onDelete = viewModel::onDeleteClicked,
        onConfirm = viewModel::onConfirmClicked,
        onSkip = viewModel::onSkipClicked,
        onSkipAll = viewModel::onSkipAllClicked,
        onDetectRecurring = viewModel::onDetectRecurringClicked,
    )
}

/** Every modal/dialog driven by [RecurringViewModel]'s state, independent of which screen is
 * currently showing -- [viewModel] is an app-level singleton (instantiated once in
 * `MainActivity`), so these need to render regardless of navigation, not just when the user is
 * on [RecurringScreen] itself. Rendered once, unconditionally, from `MainActivity` -- NOT called
 * from [RecurringScreen] itself, to avoid rendering every dialog twice when the user is actually
 * on that screen. */
@Composable
internal fun RecurringOverlays(viewModel: RecurringViewModel) {
    val state by viewModel.state.collectAsState()

    state.confirmPrompt?.let { prompt ->
        ConfirmPromptDialog(
            prompt = prompt,
            onFormChange = viewModel::onConfirmFormChanged,
            onDismiss = viewModel::onConfirmDismissed,
            onSave = viewModel::onConfirmSaveClicked,
        )
    }

    state.form?.let { form ->
        TemplateFormDialog(
            form = form,
            accounts = state.accounts,
            categories = state.categories,
            onFormChange = viewModel::onFormChanged,
            onDismiss = viewModel::onFormDismissed,
            onSave = viewModel::onSaveClicked,
        )
    }

    state.detectionReview?.let { review ->
        DetectionReviewSheet(
            review = review,
            onToggle = viewModel::onDetectionItemToggled,
            onDismiss = viewModel::onDetectionReviewDismissed,
            onConfirmAll = viewModel::onDetectionConfirmAllClicked,
        )
    }

    state.endCandidate?.let { template ->
        AlertDialog(
            onDismissRequest = viewModel::onEndDismissed,
            title = { Text(text = stringResource(R.string.template_end_confirm_title)) },
            text = { Text(text = stringResource(R.string.template_end_confirm_body)) },
            confirmButton = {
                DestructiveTextButton(onClick = viewModel::onEndConfirmed) {
                    Text(text = stringResource(R.string.recurring_action_end))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onEndDismissed) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }

    state.deleteCandidate?.let { template ->
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismissed,
            title = { Text(text = stringResource(R.string.template_delete_confirm_title)) },
            text = { Text(text = stringResource(R.string.template_delete_confirm_body)) },
            confirmButton = {
                DestructiveTextButton(onClick = viewModel::onDeleteConfirmed) {
                    Text(text = stringResource(R.string.common_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteDismissed) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun RecurringContent(
    state: RecurringUiState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onEdit: (TemplateSummary) -> Unit,
    onPause: (TemplateSummary) -> Unit,
    onResume: (TemplateSummary) -> Unit,
    onEnd: (TemplateSummary) -> Unit,
    onDelete: (TemplateSummary) -> Unit,
    onConfirm: (DuePrompt) -> Unit,
    onSkip: (DuePrompt) -> Unit,
    onSkipAll: (DuePrompt) -> Unit,
    onDetectRecurring: () -> Unit,
) {
    val active = state.templates
        .filter { it.status == TemplateStatus.ACTIVE }
        .sortedWith(compareBy({ it.effectiveDayOfMonth() }, { it.name?.lowercase() ?: "" }))
    val paused = state.templates.filter { it.status == TemplateStatus.PAUSED }
    val ended = state.templates.filter { it.status == TemplateStatus.ENDED }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.recurring_list_title),
                trailing = {
                    TopBarIconButton(
                        icon = Icons.Outlined.AutoAwesome,
                        contentDescription = stringResource(
                            if (state.isDetecting) {
                                R.string.recurring_detect_action_running
                            } else {
                                R.string.recurring_detect_action
                            },
                        ),
                        onClick = onDetectRecurring,
                        enabled = !state.isDetecting,
                    )
                },
            )
        }

        if (state.hasMonthlySummary) {
            item(key = "monthly-summary") {
                MonthlySummaryCard(state = state)
            }
        }

        if (state.duePrompts.isNotEmpty()) {
            item(key = "due-header") {
                Text(
                    text = stringResource(R.string.recurring_due_section_title),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            items(items = state.duePrompts, key = { "due-${it.template.id}" }) { prompt ->
                DuePromptCard(
                    prompt = prompt,
                    onConfirm = { onConfirm(prompt) },
                    onSkip = { onSkip(prompt) },
                    onSkipAll = { onSkipAll(prompt) },
                    onEnd = { onEnd(prompt.template) },
                )
            }
        }

        state.errorMessage?.let { message ->
            item {
                InlineBanner(kind = BannerKind.Error, text = message)
            }
        }

        if (!state.isLoading && state.templates.isEmpty()) {
            item { EmptyRecurringCard(onAdd = onAdd) }
        } else {
            templateSection(
                titleRes = R.string.recurring_scheduled_section_title,
                templates = active,
                onEdit = onEdit,
                onPause = onPause,
                onResume = onResume,
                onEnd = onEnd,
                onDelete = onDelete,
            )
            templateSection(
                titleRes = R.string.recurring_paused_section_title,
                templates = paused,
                onEdit = onEdit,
                onPause = onPause,
                onResume = onResume,
                onEnd = onEnd,
                onDelete = onDelete,
            )
            templateSection(
                titleRes = R.string.recurring_ended_section_title,
                templates = ended,
                onEdit = onEdit,
                onPause = onPause,
                onResume = onResume,
                onEnd = onEnd,
                onDelete = onDelete,
            )
            item {
                PrimaryButton(
                    text = stringResource(R.string.recurring_list_add),
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.templateSection(
    titleRes: Int,
    templates: List<TemplateSummary>,
    onEdit: (TemplateSummary) -> Unit,
    onPause: (TemplateSummary) -> Unit,
    onResume: (TemplateSummary) -> Unit,
    onEnd: (TemplateSummary) -> Unit,
    onDelete: (TemplateSummary) -> Unit,
) {
    if (templates.isEmpty()) return
    item(key = "header-$titleRes") {
        Text(
            text = stringResource(titleRes),
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.labelMedium,
        )
    }
    items(items = templates, key = { it.id }) { template ->
        TemplateRow(
            template = template,
            onEdit = { onEdit(template) },
            onPause = { onPause(template) },
            onResume = { onResume(template) },
            onEnd = { onEnd(template) },
            onDelete = { onDelete(template) },
        )
    }
}

@Composable
private fun TemplateRow(
    template: TemplateSummary,
    onEdit: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(
            icon = Icons.Outlined.Autorenew,
            contentDescription = null,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = template.name ?: template.type.label(),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = listOfNotNull(template.cadenceLabel(), template.accountName)
                    .joinToString(separator = " · "),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.recurring_next_due, template.nextDueDate),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (template.amountIsVariable || template.amountCents == null) {
            NeutralPill(text = stringResource(R.string.recurring_amount_variable))
        } else {
            MoneyText(
                cents = template.signedAmountCents(),
                color = FinanceTheme.colors.amountColor(template.type),
                style = MaterialTheme.typography.titleSmall,
                signed = template.type != MovementType.EXPENSE,
            )
        }
        TemplateRowMenu(
            status = template.status,
            onEdit = onEdit,
            onPause = onPause,
            onResume = onResume,
            onEnd = onEnd,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun TemplateRowMenu(
    status: TemplateStatus,
    onEdit: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
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
            when (status) {
                TemplateStatus.ACTIVE -> DropdownMenuItem(
                    text = { Text(stringResource(R.string.recurring_action_pause)) },
                    onClick = { expanded = false; onPause() },
                )
                TemplateStatus.PAUSED, TemplateStatus.ENDED -> DropdownMenuItem(
                    text = { Text(stringResource(R.string.recurring_action_resume)) },
                    onClick = { expanded = false; onResume() },
                )
            }
            if (status != TemplateStatus.ENDED) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.recurring_action_end)) },
                    onClick = { expanded = false; onEnd() },
                )
            }
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
private fun MonthlySummaryCard(state: RecurringUiState) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.recurring_monthly_total),
                style = MaterialTheme.typography.titleSmall,
            )
            SummaryMetric(
                label = stringResource(R.string.recurring_summary_expense),
                cents = -state.monthlyExpenseCents,
                color = FinanceTheme.colors.expense,
            )
            SummaryMetric(
                label = stringResource(R.string.recurring_summary_income),
                cents = state.monthlyIncomeCents,
                color = FinanceTheme.colors.income,
                signed = true,
            )
            SummaryMetric(
                label = stringResource(R.string.recurring_summary_net),
                cents = state.monthlyNetCents,
                color = if (state.monthlyNetCents >= 0L) FinanceTheme.colors.income else FinanceTheme.colors.expense,
                signed = true,
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    cents: Long,
    color: androidx.compose.ui.graphics.Color,
    signed: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.labelMedium,
        )
        MoneyText(
            cents = cents,
            color = color,
            style = MaterialTheme.typography.titleSmall,
            signed = signed,
        )
    }
}

@Composable
private fun DuePromptCard(
    prompt: DuePrompt,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onSkipAll: () -> Unit,
    onEnd: () -> Unit,
) {
    val template = prompt.template
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NeutralPill(
                text = stringResource(R.string.recurring_due_badge),
                leadingIcon = Icons.Outlined.Warning,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name ?: template.type.label(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.recurring_next_due, prompt.dueDate),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (template.amountIsVariable || template.amountCents == null) {
                    NeutralPill(text = stringResource(R.string.recurring_amount_variable))
                } else {
                    MoneyText(
                        cents = template.signedAmountCents(),
                        color = FinanceTheme.colors.amountColor(template.type),
                        style = MaterialTheme.typography.titleSmall,
                        signed = template.type != MovementType.EXPENSE,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.recurring_action_add_payment),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSkip) {
                    Text(text = stringResource(R.string.recurring_action_skip))
                }
            }
            if (prompt.pendingCount > 1) {
                TextButton(onClick = onSkipAll) {
                    Text(text = stringResource(R.string.recurring_skip_all))
                }
            }
            DestructiveTextButton(onClick = onEnd) {
                Text(text = stringResource(R.string.recurring_action_end))
            }
        }
    }
}

/** Auto-triggered on app cold start (from `MainActivity`, not from [RecurringScreen] itself) when
 * any template is due -- see `docs/13-recurring-refunds-budgets-ui.md` §13 for the "once per cold
 * start, persists until acted on" rationale. Reuses [DuePromptCard] verbatim; every action here
 * (confirm/skip/skip-all/end) opens the same existing dialogs [RecurringOverlays] renders. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueRemindersSheet(
    duePrompts: List<DuePrompt>,
    onConfirm: (DuePrompt) -> Unit,
    onSkip: (DuePrompt) -> Unit,
    onSkipAll: (DuePrompt) -> Unit,
    onEnd: (TemplateSummary) -> Unit,
    onDismiss: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.recurring_due_section_title),
                style = MaterialTheme.typography.titleLarge,
            )
            duePrompts.forEach { prompt ->
                DuePromptCard(
                    prompt = prompt,
                    onConfirm = { onConfirm(prompt) },
                    onSkip = { onSkip(prompt) },
                    onSkipAll = { onSkipAll(prompt) },
                    onEnd = { onEnd(prompt.template) },
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.common_close))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmPromptDialog(
    prompt: ConfirmPromptState,
    onFormChange: (ConfirmPromptState) -> Unit,
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
                .imePadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.recurring_action_add_payment),
                style = MaterialTheme.typography.titleLarge,
            )
            prompt.templateName.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = listOf(it, prompt.accountName).joinToString(separator = " · "),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            prompt.errorRes?.let {
                InlineBanner(kind = BannerKind.Error, text = stringResource(it))
            }
            prompt.errorMessage?.let {
                InlineBanner(kind = BannerKind.Error, text = it)
            }
            OutlinedTextField(
                value = prompt.amount,
                onValueChange = { onFormChange(prompt.copy(amount = it)) },
                label = { Text(text = stringResource(R.string.template_field_amount)) },
                prefix = { Text(text = "€") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = prompt.date,
                onValueChange = { onFormChange(prompt.copy(date = it)) },
                label = { Text(text = stringResource(R.string.template_field_next_due)) },
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
                    text = stringResource(R.string.recurring_action_add_payment),
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EmptyRecurringCard(onAdd: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.recurring_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.recurring_empty_body),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton(
                text = stringResource(R.string.recurring_list_add),
                onClick = onAdd,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateFormDialog(
    form: TemplateFormState,
    accounts: List<AccountSummary>,
    categories: List<CategoryRecord>,
    onFormChange: (TemplateFormState) -> Unit,
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
                .imePadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(
                    if (form.id == null) R.string.template_new_title else R.string.template_edit_title,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            form.errorRes?.let {
                InlineBanner(kind = BannerKind.Error, text = stringResource(it))
            }
            form.errorMessage?.let {
                InlineBanner(kind = BannerKind.Error, text = it)
            }

            MovementTypeSelector(
                selected = form.type,
                onSelect = { onFormChange(form.copy(type = it)) },
            )

            // Concepte
            OutlinedTextField(
                value = form.name,
                onValueChange = { onFormChange(form.copy(name = it)) },
                label = { Text(text = stringResource(R.string.template_field_name)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            FormToggleRow(
                label = stringResource(R.string.template_field_amount_variable),
                checked = form.amountIsVariable,
                onCheckedChange = { onFormChange(form.copy(amountIsVariable = it)) },
            )
            if (!form.amountIsVariable) {
                OutlinedTextField(
                    value = form.amount,
                    onValueChange = { onFormChange(form.copy(amount = it)) },
                    label = { Text(text = stringResource(R.string.template_field_amount)) },
                    prefix = { Text(text = "€") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.amountFlex,
                    onValueChange = { onFormChange(form.copy(amountFlex = it)) },
                    label = { Text(text = stringResource(R.string.template_field_amount_flex)) },
                    prefix = { Text(text = "€") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Row: next due date & category (transfers have no category — date spans full width)
            if (form.type == MovementType.TRANSFER) {
                FormDatePicker(
                    label = stringResource(R.string.template_field_next_due),
                    date = form.nextDueDate,
                    onDateChange = { onFormChange(form.copy(nextDueDate = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    FormDatePicker(
                        label = stringResource(R.string.template_field_next_due),
                        date = form.nextDueDate,
                        onDateChange = { onFormChange(form.copy(nextDueDate = it)) },
                        modifier = Modifier.weight(1f),
                    )
                    CategorySelect(
                        categories = categories,
                        type = form.type,
                        selectedId = form.categoryId,
                        onSelect = { onFormChange(form.copy(categoryId = it)) },
                        modifier = Modifier.weight(1.5f),
                    )
                }
            }

            // Account(s)
            if (form.type == MovementType.TRANSFER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AccountSelect(
                        label = stringResource(R.string.template_field_account),
                        selectedId = form.accountId,
                        accounts = accounts,
                        onSelect = { onFormChange(form.copy(accountId = it)) },
                        modifier = Modifier.weight(1f),
                    )
                    AccountSelect(
                        label = stringResource(R.string.template_field_dest_account),
                        selectedId = form.destinationAccountId,
                        accounts = accounts,
                        onSelect = { onFormChange(form.copy(destinationAccountId = it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                AccountSelect(
                    label = stringResource(R.string.template_field_account),
                    selectedId = form.accountId,
                    accounts = accounts,
                    onSelect = { onFormChange(form.copy(accountId = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ScheduleFields(form = form, onFormChange = onFormChange)

            OutlinedTextField(
                value = form.dateFlex,
                onValueChange = { onFormChange(form.copy(dateFlex = it)) },
                label = { Text(text = stringResource(R.string.template_field_date_flex)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.leadDays,
                onValueChange = { onFormChange(form.copy(leadDays = it)) },
                label = { Text(text = stringResource(R.string.template_field_lead_days)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            LabeledSegmentedControl(
                label = stringResource(R.string.template_field_status),
                options = TemplateStatus.entries,
                selected = form.status,
                optionLabel = { it.label() },
                onSelect = { onFormChange(form.copy(status = it)) },
            )

            OutlinedTextField(
                value = form.payee,
                onValueChange = { onFormChange(form.copy(payee = it)) },
                label = { Text(text = stringResource(R.string.template_field_payee)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.notes,
                onValueChange = { onFormChange(form.copy(notes = it)) },
                label = { Text(text = stringResource(R.string.template_field_notes)) },
                minLines = 2,
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
                        if (form.id == null) R.string.template_save_new else R.string.template_save_changes,
                    ),
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetectionReviewSheet(
    review: DetectionReviewState,
    onToggle: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirmAll: () -> Unit,
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
                .fillMaxHeight(0.85f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.recurring_detect_review_title),
                style = MaterialTheme.typography.titleLarge,
            )
            review.errorMessage?.let {
                InlineBanner(kind = BannerKind.Error, text = it)
            }
            if (review.items.isEmpty()) {
                Text(
                    text = stringResource(R.string.recurring_detect_review_empty),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                review.items.forEachIndexed { index, item ->
                    DetectionCandidateRow(
                        item = item,
                        onToggle = { checked -> onToggle(index, checked) },
                    )
                }
            }
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
                if (review.items.isNotEmpty()) {
                    PrimaryButton(
                        text = stringResource(R.string.recurring_detect_confirm_selected),
                        onClick = onConfirmAll,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectionCandidateRow(
    item: DetectionReviewItem,
    onToggle: (Boolean) -> Unit,
) {
    val candidate = item.candidate
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = item.accepted, onCheckedChange = onToggle)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = candidate.name?.takeIf { it.isNotBlank() } ?: candidate.payee.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    NeutralPill(
                        text = stringResource(
                            if (candidate.action == DetectedTemplateAction.NEW) {
                                R.string.recurring_detect_badge_new
                            } else {
                                R.string.recurring_detect_badge_update
                            },
                        ),
                    )
                }
                Text(
                    text = listOf(
                        candidate.frequency.label(),
                        candidate.suggestedStatus.label(),
                        stringResource(R.string.recurring_detect_occurrences, candidate.occurrenceCount),
                    ).joinToString(separator = " · "),
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (candidate.amountIsVariable) {
                    NeutralPill(text = stringResource(R.string.template_field_amount_variable))
                } else {
                    MoneyText(
                        cents = candidate.amountCents ?: 0L,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleFields(
    form: TemplateFormState,
    onFormChange: (TemplateFormState) -> Unit,
) {
    FormSelect(
        label = stringResource(R.string.template_field_frequency),
        options = RecurrenceFrequency.entries.map { SelectOption(id = it.name, label = it.label()) },
        selectedId = form.frequency.name,
        onSelect = { id ->
            RecurrenceFrequency.entries.firstOrNull { it.name == id }?.let { frequency ->
                onFormChange(form.copy(frequency = frequency))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    when {
        form.frequency.usesDayOfMonth() -> OutlinedTextField(
            value = form.dayOfMonth,
            onValueChange = { onFormChange(form.copy(dayOfMonth = it)) },
            label = { Text(text = stringResource(R.string.template_field_anchor_day)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        form.frequency.usesWeekday() -> {
            val labels = stringArrayResource(R.array.template_weekday_short)
            FormSelect(
                label = stringResource(R.string.template_field_weekday),
                options = labels.mapIndexed { index, label -> SelectOption(id = index.toString(), label = label) },
                selectedId = form.weekday?.toString(),
                onSelect = { id -> onFormChange(form.copy(weekday = id?.toIntOrNull())) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        form.frequency == RecurrenceFrequency.CUSTOM -> {
            OutlinedTextField(
                value = form.intervalCount,
                onValueChange = { onFormChange(form.copy(intervalCount = it)) },
                label = { Text(text = stringResource(R.string.template_field_interval)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            FormSelect(
                label = stringResource(R.string.template_field_custom_unit),
                options = CustomRecurrenceUnit.entries.map { SelectOption(id = it.name, label = it.label()) },
                selectedId = form.customUnit.name,
                onSelect = { id ->
                    CustomRecurrenceUnit.entries.firstOrNull { it.name == id }?.let { unit ->
                        onFormChange(form.copy(customUnit = unit))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MovementType.label(): String =
    when (this) {
        MovementType.EXPENSE -> stringResource(R.string.movement_type_expense)
        MovementType.INCOME -> stringResource(R.string.movement_type_income)
        MovementType.TRANSFER -> stringResource(R.string.movement_type_transfer)
        MovementType.SETTLEMENT -> stringResource(R.string.movement_type_settlement)
        MovementType.REFUND -> stringResource(R.string.movement_type_refund)
        MovementType.EXTERNAL_EXPENSE -> stringResource(R.string.movement_type_external)
    }

@Composable
private fun RecurrenceFrequency.label(): String =
    when (this) {
        RecurrenceFrequency.WEEKLY -> stringResource(R.string.recurring_cadence_weekly)
        RecurrenceFrequency.FORTNIGHTLY -> stringResource(R.string.recurring_cadence_fortnightly)
        RecurrenceFrequency.MONTHLY -> stringResource(R.string.recurring_cadence_monthly)
        RecurrenceFrequency.YEARLY -> stringResource(R.string.recurring_cadence_yearly)
        RecurrenceFrequency.CUSTOM -> stringResource(R.string.recurring_cadence_custom)
    }

@Composable
private fun CustomRecurrenceUnit.label(): String =
    when (this) {
        CustomRecurrenceUnit.DAYS -> stringResource(R.string.template_unit_days)
        CustomRecurrenceUnit.WEEKS -> stringResource(R.string.template_unit_weeks)
        CustomRecurrenceUnit.MONTHS -> stringResource(R.string.template_unit_months)
        CustomRecurrenceUnit.YEARS -> stringResource(R.string.template_unit_years)
    }

@Composable
private fun TemplateStatus.label(): String =
    when (this) {
        TemplateStatus.ACTIVE -> stringResource(R.string.template_status_active)
        TemplateStatus.PAUSED -> stringResource(R.string.template_status_paused)
        TemplateStatus.ENDED -> stringResource(R.string.template_status_ended)
    }

@Composable
private fun TemplateSummary.cadenceLabel(): String =
    when (frequency) {
        RecurrenceFrequency.WEEKLY -> stringResource(R.string.recurring_cadence_weekly)
        RecurrenceFrequency.FORTNIGHTLY -> stringResource(R.string.recurring_cadence_fortnightly)
        RecurrenceFrequency.MONTHLY -> dayOfMonth?.let {
            stringResource(R.string.recurring_cadence_monthly_day, it.toInt())
        } ?: stringResource(R.string.recurring_cadence_monthly)
        RecurrenceFrequency.YEARLY -> stringResource(R.string.recurring_cadence_yearly)
        RecurrenceFrequency.CUSTOM -> stringResource(R.string.recurring_cadence_custom)
    }

private fun TemplateSummary.signedAmountCents(): Long {
    val amount = amountCents ?: 0L
    return if (type == MovementType.EXPENSE || type == MovementType.EXTERNAL_EXPENSE) -amount else amount
}

