package com.gestorfinances.app.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.SettlementDirection
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.domain.rules.SettlementScope
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.nextFieldKeyboardActions
import com.gestorfinances.app.ui.common.parseIsoDateOrNull
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.ui.common.formatEuroInput
import com.gestorfinances.app.ui.movements.AccountSelect
import com.gestorfinances.app.ui.movements.ExpenseKind
import com.gestorfinances.app.ui.movements.IncomeOwnershipSection
import com.gestorfinances.app.ui.movements.SplitEditorState
import com.gestorfinances.app.ui.movements.CategorySelect
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.FormToggleRow
import com.gestorfinances.app.ui.movements.FormTripTagSection
import com.gestorfinances.app.ui.movements.MovementTypeSelector
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.movements.recurringTemplateTypes
import com.gestorfinances.app.ui.theme.FinanceTheme

@Composable
internal fun RecurringFormScreen(
    form: TemplateFormState,
    accounts: List<AccountSummary>,
    categories: List<CategoryRecord>,
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    people: List<PersonSummary>,
    onFormChange: (TemplateFormState) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    incomeOwnership: IncomeOwnershipActions = IncomeOwnershipActions(),
) {
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        PageHeaderRow(
            onBack = onBack,
            title = stringResource(
                if (form.id == null) R.string.template_new_title else R.string.template_edit_title,
            ),
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            form.errorMessage?.let {
                InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_recurring)
            }

            FormCard(
                title = stringResource(R.string.recurring_form_movement_title),
                description = stringResource(R.string.recurring_form_movement_description),
            ) {
                MovementTypeSelector(
                    selected = form.type,
                    onSelect = { onFormChange(form.copy(type = it)) },
                    showLabel = false,
                    types = recurringTemplateTypes,
                )
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onFormChange(form.copy(name = it)) },
                    label = { Text(stringResource(R.string.template_field_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = nextFieldKeyboardActions(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!form.amountIsVariable) {
                    AmountField(form = form, onFormChange = onFormChange)
                } else {
                    Text(
                        text = stringResource(R.string.recurring_form_variable_amount_help),
                        color = FinanceTheme.colors.mutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FormToggleRow(
                    label = stringResource(R.string.template_field_amount_variable),
                    checked = form.amountIsVariable,
                    onCheckedChange = { onFormChange(form.copy(amountIsVariable = it)) },
                )
            }

            FormCard(
                title = stringResource(R.string.recurring_form_when_title),
                description = stringResource(R.string.recurring_form_when_description),
            ) {
                RecurringScheduleFields(form = form, onFormChange = onFormChange)
                val dateError = form.errorField == TemplateFormField.NEXT_DUE_DATE
                FormDatePicker(
                    label = stringResource(R.string.template_field_next_due),
                    date = form.nextDueDate,
                    onDateChange = { onFormChange(form.copy(nextDueDate = it)) },
                    isError = dateError,
                    supportingText = if (dateError && form.errorRes != null) {
                        stringResource(form.errorRes)
                    } else null,
                    modifier = Modifier.fillMaxWidth().scrollToWhen(dateError),
                )
            }

            FormCard(
                title = stringResource(R.string.recurring_form_recording_title),
                description = stringResource(R.string.recurring_form_recording_description),
            ) {
                RecordingFields(
                    form = form,
                    accounts = accounts,
                    categories = categories,
                    people = people,
                    onFormChange = onFormChange,
                    incomeOwnership = incomeOwnership,
                )
            }

            DisclosureCard(
                title = stringResource(R.string.recurring_form_optional_title),
                description = stringResource(R.string.recurring_form_optional_description),
                expanded = form.showOptional,
                onToggle = { onFormChange(form.copy(showOptional = !form.showOptional)) },
            ) {
                OptionalFields(
                    form = form,
                    trips = trips,
                    tags = tags,
                    onFormChange = onFormChange,
                )
            }

            DisclosureCard(
                title = stringResource(R.string.recurring_form_advanced_title),
                description = stringResource(R.string.recurring_form_advanced_description),
                expanded = form.showAdvanced,
                onToggle = { onFormChange(form.copy(showAdvanced = !form.showAdvanced)) },
            ) {
                AdvancedFields(form = form, onFormChange = onFormChange)
            }
        }

        HorizontalDivider(color = FinanceTheme.colors.cardBorder)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(stringResource(R.string.common_cancel))
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

@Composable
private fun AmountField(form: TemplateFormState, onFormChange: (TemplateFormState) -> Unit) {
    val error = form.errorField == TemplateFormField.AMOUNT
    OutlinedTextField(
        value = form.amount,
        onValueChange = { onFormChange(form.copy(amount = it)) },
        label = { Text(stringResource(R.string.template_field_amount)) },
        prefix = { Text("€") },
        singleLine = true,
        isError = error,
        supportingText = if (error && form.errorRes != null) {
            { Text(stringResource(form.errorRes)) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        keyboardActions = nextFieldKeyboardActions(),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().scrollToWhen(error),
    )
}

/** How the template form answers whose an income into a shared account is. */
internal class IncomeOwnershipActions(
    val onOwnerSelected: (ExpenseKind) -> Unit = {},
    val onMemberSelected: (String?) -> Unit = {},
    val onSplitEditorChange: (SplitEditorState) -> Unit = {},
    val onCreatePerson: (String) -> Unit = {},
)

@Composable
private fun RecordingFields(
    form: TemplateFormState,
    accounts: List<AccountSummary>,
    categories: List<CategoryRecord>,
    people: List<PersonSummary>,
    onFormChange: (TemplateFormState) -> Unit,
    incomeOwnership: IncomeOwnershipActions,
) {
    val accountError = form.errorField == TemplateFormField.ACCOUNT
    val destinationError = form.errorField == TemplateFormField.DESTINATION_ACCOUNT
    val personError = form.errorField == TemplateFormField.PERSON
    val errorText = form.errorRes?.let { stringResource(it) }
    AccountSelect(
        label = stringResource(
            if (form.type == MovementType.TRANSFER) R.string.recurring_form_origin_account
            else R.string.template_field_account,
        ),
        selectedId = form.accountId,
        accounts = accounts,
        onSelect = { onFormChange(form.copy(accountId = it)) },
        isError = accountError,
        supportingText = errorText.takeIf { accountError },
        modifier = Modifier.fillMaxWidth().scrollToWhen(accountError),
    )
    if (form.type == MovementType.TRANSFER) {
        AccountSelect(
            label = stringResource(R.string.template_field_dest_account),
            selectedId = form.destinationAccountId,
            accounts = accounts,
            onSelect = { onFormChange(form.copy(destinationAccountId = it)) },
            isError = destinationError,
            supportingText = errorText.takeIf { destinationError },
            modifier = Modifier.fillMaxWidth().scrollToWhen(destinationError),
        )
    } else if (form.type == MovementType.SETTLEMENT) {
        SettlementFields(
            form = form,
            people = people,
            isError = personError,
            errorText = errorText.takeIf { personError },
            onFormChange = onFormChange,
        )
    } else {
        CategorySelect(
            categories = categories,
            type = form.type,
            selectedId = form.categoryId,
            onSelect = { onFormChange(form.copy(categoryId = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
        val account = accounts.firstOrNull { it.id == form.accountId }
        if (form.type == MovementType.INCOME && account?.ownershipKind == AccountOwnershipKind.SHARED) {
            IncomeOwnershipSection(
                owner = form.incomeOwner,
                // A variable amount's allocation is entered as shares of 100 EUR.
                amount = if (form.amountIsVariable) formatEuroInput(VARIABLE_AMOUNT_WEIGHT_CENTS) else form.amount,
                account = account,
                people = people,
                otherPersonId = form.incomeMemberId,
                splitEditor = form.incomeSplitEditor,
                ownerErrorText = errorText.takeIf { form.errorField == TemplateFormField.INCOME_OWNER },
                personErrorText = errorText.takeIf { personError },
                splitError = form.errorField == TemplateFormField.SPLIT,
                onOwnerSelected = incomeOwnership.onOwnerSelected,
                onOtherPersonSelected = incomeOwnership.onMemberSelected,
                onSplitEditorChange = incomeOwnership.onSplitEditorChange,
                onCreatePersonInSplit = incomeOwnership.onCreatePerson,
            )
        }
    }
}

/** Who the recurring settlement is with, which way the money moves, and the debt it may consume. */
@Composable
private fun SettlementFields(
    form: TemplateFormState,
    people: List<PersonSummary>,
    isError: Boolean,
    errorText: String?,
    onFormChange: (TemplateFormState) -> Unit,
) {
    FormSelect(
        label = stringResource(R.string.template_field_person),
        options = people.map { SelectOption(id = it.id, label = it.name) },
        selectedId = form.personId,
        onSelect = { onFormChange(form.copy(personId = it)) },
        isError = isError,
        supportingText = errorText,
        modifier = Modifier.fillMaxWidth().scrollToWhen(isError),
    )
    LabeledSegmentedControl(
        label = stringResource(R.string.movement_field_settlement),
        options = SettlementDirection.entries,
        selected = form.settlementDirection,
        optionLabel = { it.formLabel() },
        onSelect = { onFormChange(form.copy(settlementDirection = it)) },
        modifier = Modifier.fillMaxWidth(),
    )
    LabeledSegmentedControl(
        label = stringResource(R.string.settlement_field_scope),
        options = SettlementScope.entries,
        selected = form.settlementScope,
        optionLabel = { it.formLabel() },
        onSelect = { onFormChange(form.copy(settlementScope = it)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.settlement_scope_help),
        style = MaterialTheme.typography.bodySmall,
        color = FinanceTheme.colors.mutedText,
    )
}

@Composable
private fun SettlementDirection.formLabel(): String = when (this) {
    SettlementDirection.PERSON_TO_USER -> stringResource(R.string.settlement_direction_person_to_user)
    SettlementDirection.USER_TO_PERSON -> stringResource(R.string.settlement_direction_user_to_person)
}

@Composable
private fun SettlementScope.formLabel(): String = when (this) {
    SettlementScope.ALL -> stringResource(R.string.settlement_scope_all)
    SettlementScope.RECURRING -> stringResource(R.string.settlement_scope_recurring)
}

@Composable
private fun OptionalFields(
    form: TemplateFormState,
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    onFormChange: (TemplateFormState) -> Unit,
) {
    if (form.type != MovementType.TRANSFER && form.type != MovementType.SETTLEMENT) {
        FormTripTagSection(
            trips = trips,
            tags = tags,
            tripId = form.tripId,
            tagId = form.tagId,
            onTripSelected = { onFormChange(form.copy(tripId = it)) },
            onTagSelected = { onFormChange(form.copy(tagId = it)) },
        )
        OutlinedTextField(
            value = form.payee,
            onValueChange = { onFormChange(form.copy(payee = it)) },
            label = { Text(stringResource(R.string.template_field_payee)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = nextFieldKeyboardActions(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = form.notes,
        onValueChange = { onFormChange(form.copy(notes = it)) },
        label = { Text(stringResource(R.string.template_field_notes)) },
        minLines = 2,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = doneKeyboardActions(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AdvancedFields(form: TemplateFormState, onFormChange: (TemplateFormState) -> Unit) {
    AdvancedNumberField(
        value = form.amountFlex,
        onValueChange = { onFormChange(form.copy(amountFlex = it)) },
        label = stringResource(R.string.template_field_amount_flex),
        field = TemplateFormField.AMOUNT_FLEX,
        form = form,
        decimal = true,
        prefix = "€",
    )
    AdvancedNumberField(
        value = form.dateFlex,
        onValueChange = { onFormChange(form.copy(dateFlex = it)) },
        label = stringResource(R.string.template_field_date_flex),
        field = TemplateFormField.DATE_FLEX,
        form = form,
    )
    AdvancedNumberField(
        value = form.leadDays,
        onValueChange = { onFormChange(form.copy(leadDays = it)) },
        label = stringResource(R.string.template_field_lead_days),
        field = TemplateFormField.LEAD_DAYS,
        form = form,
        isLast = true,
    )
    LabeledSegmentedControl(
        label = stringResource(R.string.template_field_status),
        options = TemplateStatus.entries,
        selected = form.status,
        optionLabel = { it.formLabel() },
        onSelect = { onFormChange(form.copy(status = it)) },
    )
}

@Composable
private fun AdvancedNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    field: TemplateFormField,
    form: TemplateFormState,
    decimal: Boolean = false,
    prefix: String? = null,
    isLast: Boolean = false,
) {
    val error = form.errorField == field
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        singleLine = true,
        isError = error,
        supportingText = if (error && form.errorRes != null) {
            { Text(stringResource(form.errorRes)) }
        } else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = if (isLast) doneKeyboardActions() else nextFieldKeyboardActions(),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().scrollToWhen(error),
    )
}

@Composable
private fun RecurringScheduleFields(
    form: TemplateFormState,
    onFormChange: (TemplateFormState) -> Unit,
) {
    FormSelect(
        label = stringResource(R.string.template_field_frequency),
        options = RecurrenceFrequency.entries.map { SelectOption(it.name, it.formLabel()) },
        selectedId = form.frequency.name,
        onSelect = { id ->
            RecurrenceFrequency.entries.firstOrNull { it.name == id }?.let {
                onFormChange(form.withFrequencyDefaults(it))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    val error = form.errorField == TemplateFormField.SCHEDULE
    val errorText = if (error && form.errorRes != null) stringResource(form.errorRes) else null
    when {
        form.frequency.usesDayOfMonth() -> OutlinedTextField(
            value = form.dayOfMonth,
            onValueChange = { onFormChange(form.copy(dayOfMonth = it)) },
            label = { Text(stringResource(R.string.template_field_anchor_day)) },
            singleLine = true,
            isError = error,
            supportingText = errorText?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions = nextFieldKeyboardActions(),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().scrollToWhen(error),
        )
        form.frequency.usesWeekday() -> {
            val labels = stringArrayResource(R.array.template_weekday_short)
            FormSelect(
                label = stringResource(R.string.template_field_weekday),
                options = labels.mapIndexed { index, label -> SelectOption(index.toString(), label) },
                selectedId = form.weekday?.toString(),
                onSelect = { onFormChange(form.copy(weekday = it?.toIntOrNull())) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        form.frequency == RecurrenceFrequency.CUSTOM -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = form.intervalCount,
                    onValueChange = { onFormChange(form.copy(intervalCount = it)) },
                    label = { Text(stringResource(R.string.template_field_interval)) },
                    singleLine = true,
                    isError = error,
                    supportingText = errorText?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = nextFieldKeyboardActions(),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f).scrollToWhen(error),
                )
                FormSelect(
                    label = stringResource(R.string.template_field_custom_unit),
                    options = CustomRecurrenceUnit.entries.map { SelectOption(it.name, it.formLabel()) },
                    selectedId = form.customUnit.name,
                    onSelect = { id ->
                        CustomRecurrenceUnit.entries.firstOrNull { it.name == id }?.let {
                            onFormChange(form.copy(customUnit = it))
                        }
                    },
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

private fun TemplateFormState.withFrequencyDefaults(frequency: RecurrenceFrequency): TemplateFormState {
    val date = parseIsoDateOrNull(nextDueDate)
    return copy(
        frequency = frequency,
        dayOfMonth = if (frequency.usesDayOfMonth() && dayOfMonth.isBlank()) {
            date?.dayOfMonth?.toString().orEmpty()
        } else {
            dayOfMonth
        },
        weekday = if (frequency.usesWeekday() && weekday == null) {
            date?.dayOfWeek?.value?.minus(1)
        } else {
            weekday
        },
        intervalCount = if (frequency == RecurrenceFrequency.CUSTOM && intervalCount.isBlank()) {
            "1"
        } else {
            intervalCount
        },
    )
}

@Composable
private fun FormCard(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CardHeading(title, description)
            content()
        }
    }
}

@Composable
private fun DisclosureCard(
    title: String,
    description: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) { CardHeading(title, description) }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = FinanceTheme.colors.mutedText,
                )
            }
            if (expanded) {
                HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun CardHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            color = FinanceTheme.colors.mutedText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RecurrenceFrequency.formLabel(): String = when (this) {
    RecurrenceFrequency.WEEKLY -> stringResource(R.string.recurring_cadence_weekly)
    RecurrenceFrequency.FORTNIGHTLY -> stringResource(R.string.recurring_cadence_fortnightly)
    RecurrenceFrequency.MONTHLY -> stringResource(R.string.recurring_cadence_monthly)
    RecurrenceFrequency.YEARLY -> stringResource(R.string.recurring_cadence_yearly)
    RecurrenceFrequency.CUSTOM -> stringResource(R.string.recurring_cadence_custom)
}

@Composable
private fun CustomRecurrenceUnit.formLabel(): String = when (this) {
    CustomRecurrenceUnit.DAYS -> stringResource(R.string.template_unit_days)
    CustomRecurrenceUnit.WEEKS -> stringResource(R.string.template_unit_weeks)
    CustomRecurrenceUnit.MONTHS -> stringResource(R.string.template_unit_months)
    CustomRecurrenceUnit.YEARS -> stringResource(R.string.template_unit_years)
}

@Composable
private fun TemplateStatus.formLabel(): String = when (this) {
    TemplateStatus.ACTIVE -> stringResource(R.string.template_status_active)
    TemplateStatus.PAUSED -> stringResource(R.string.template_status_paused)
    TemplateStatus.ENDED -> stringResource(R.string.template_status_ended)
}
