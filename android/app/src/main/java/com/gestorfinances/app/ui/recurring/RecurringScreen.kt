package com.gestorfinances.app.ui.recurring

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TemplateSummary
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.ui.common.ChipFlowSection
import com.gestorfinances.app.ui.common.DestructiveTextButton
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.NeutralPill
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.SegmentedControl
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
    )

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
            Text(
                text = stringResource(R.string.recurring_list_title),
                style = MaterialTheme.typography.headlineMedium,
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
                )
            }
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
) {
    val template = prompt.template
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
        }
    }
}

@Composable
private fun ConfirmPromptDialog(
    prompt: ConfirmPromptState,
    onFormChange: (ConfirmPromptState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.recurring_action_add_payment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                prompt.templateName.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = listOf(it, prompt.accountName).joinToString(separator = " · "),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                prompt.errorRes?.let {
                    Text(
                        text = stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                prompt.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(text = stringResource(R.string.recurring_action_add_payment))
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

@Composable
private fun TemplateFormDialog(
    form: TemplateFormState,
    accounts: List<com.gestorfinances.app.data.repository.AccountSummary>,
    categories: List<CategoryRecord>,
    onFormChange: (TemplateFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (form.id == null) R.string.template_new_title else R.string.template_edit_title,
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
                SegmentedControl(
                    options = templateTypes,
                    selected = form.type,
                    label = { it.label() },
                    onSelect = { onFormChange(form.copy(type = it)) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = form.amountIsVariable,
                        onCheckedChange = { onFormChange(form.copy(amountIsVariable = it)) },
                    )
                    Text(text = stringResource(R.string.template_field_amount_variable))
                }
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
                }
                ChipFlowSection(label = stringResource(R.string.template_field_account)) {
                    if (accounts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.movement_no_accounts_title),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    accounts.forEach { account ->
                        FinanceFilterChip(
                            selected = form.accountId == account.id,
                            label = account.name,
                            onClick = { onFormChange(form.copy(accountId = account.id)) },
                        )
                    }
                }
                if (form.type == MovementType.TRANSFER) {
                    ChipFlowSection(label = stringResource(R.string.template_field_dest_account)) {
                        accounts.forEach { account ->
                            FinanceFilterChip(
                                selected = form.destinationAccountId == account.id,
                                label = account.name,
                                onClick = { onFormChange(form.copy(destinationAccountId = account.id)) },
                            )
                        }
                    }
                } else {
                    ChipFlowSection(label = stringResource(R.string.template_field_category)) {
                        FinanceFilterChip(
                            selected = form.categoryId == null,
                            label = stringResource(R.string.template_no_category),
                            onClick = { onFormChange(form.copy(categoryId = null)) },
                        )
                        categories.filter { it.supportsType(form.type) }.forEach { category ->
                            FinanceFilterChip(
                                selected = form.categoryId == category.id,
                                label = category.name,
                                onClick = { onFormChange(form.copy(categoryId = category.id)) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onFormChange(form.copy(name = it)) },
                    label = { Text(text = stringResource(R.string.template_field_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                ScheduleFields(form = form, onFormChange = onFormChange)
                OutlinedTextField(
                    value = form.nextDueDate,
                    onValueChange = { onFormChange(form.copy(nextDueDate = it)) },
                    label = { Text(text = stringResource(R.string.template_field_next_due)) },
                    supportingText = { Text(text = stringResource(R.string.movement_date_format_hint)) },
                    singleLine = true,
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
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { onFormChange(form.copy(notes = it)) },
                    label = { Text(text = stringResource(R.string.template_field_notes)) },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(
                    text = stringResource(
                        if (form.id == null) R.string.template_save_new else R.string.template_save_changes,
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
private fun ScheduleFields(
    form: TemplateFormState,
    onFormChange: (TemplateFormState) -> Unit,
) {
    ChipFlowSection(label = stringResource(R.string.template_field_frequency)) {
        RecurrenceFrequency.entries.forEach { frequency ->
            FinanceFilterChip(
                selected = form.frequency == frequency,
                label = frequency.label(),
                onClick = { onFormChange(form.copy(frequency = frequency)) },
            )
        }
    }
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
            ChipFlowSection(label = stringResource(R.string.template_field_weekday)) {
                labels.forEachIndexed { index, label ->
                    FinanceFilterChip(
                        selected = form.weekday == index,
                        label = label,
                        onClick = { onFormChange(form.copy(weekday = index)) },
                    )
                }
            }
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
            ChipFlowSection(label = stringResource(R.string.template_field_custom_unit)) {
                CustomRecurrenceUnit.entries.forEach { unit ->
                    FinanceFilterChip(
                        selected = form.customUnit == unit,
                        label = unit.label(),
                        onClick = { onFormChange(form.copy(customUnit = unit)) },
                    )
                }
            }
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
    return if (type == MovementType.EXPENSE) -amount else amount
}

private fun CategoryRecord.supportsType(type: MovementType): Boolean =
    when (type) {
        MovementType.INCOME -> supportsIncome
        else -> supportsExpense
    }
