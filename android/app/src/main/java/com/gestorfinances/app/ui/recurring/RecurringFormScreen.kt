package com.gestorfinances.app.ui.recurring

import androidx.compose.ui.text.input.KeyboardCapitalization
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.EntityFormSheet
import com.gestorfinances.app.ui.common.FormDisclosure
import com.gestorfinances.app.ui.movements.cadenceLabel
import com.gestorfinances.app.ui.movements.SplitEditorCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.domain.rules.CustomRecurrenceUnit
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.domain.rules.SettlementScope
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.LabeledSegmentedControl
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
import com.gestorfinances.app.ui.movements.ForWhomFields
import com.gestorfinances.app.ui.movements.FormDatePicker
import com.gestorfinances.app.ui.movements.FormSelect
import com.gestorfinances.app.ui.movements.FormTripTagSection
import com.gestorfinances.app.ui.movements.formLabel
import com.gestorfinances.app.ui.movements.SelectOption
import com.gestorfinances.app.ui.movements.recurringTemplateTypes
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor

/**
 * A recurring item's create/edit sheet on the shared form layout: what it is and how much, when it
 * falls, where it is recorded, and under Més detalls who it is for, its trip and its notes.
 */
@Composable
internal fun RecurringFormSheet(state: RecurringUiState, viewModel: RecurringViewModel) {
    EntityFormSheet(
        form = state.form,
        key = { it.id ?: "new-template" },
        changed = { initial, current -> initial.withoutTransientUi() != current.withoutTransientUi() },
        onDiscard = viewModel::onFormDismissed,
        onSave = viewModel::onSaveClicked,
        title = { stringResource(if (it.id == null) R.string.template_new_title else R.string.template_edit_title) },
        saveLabel = { stringResource(if (it.id == null) R.string.template_save_new else R.string.template_save_changes) },
        saving = { it.isSaving },
    ) { form ->
        val edit = viewModel::onFormChanged
        form.errorMessage?.let { InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_recurring) }
        SegmentedControl(
            options = recurringTemplateTypes,
            selected = form.type,
            label = { it.formLabel() },
            onSelect = { edit(form.copy(type = it)) },
            optionColor = { FinanceTheme.colors.amountColor(it) },
        )
        OutlinedTextField(
            value = form.name,
            onValueChange = { edit(form.copy(name = it)) },
            label = { Text(stringResource(R.string.template_field_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = nextFieldKeyboardActions(),
            modifier = Modifier.fillMaxWidth(),
        )
        LabeledSegmentedControl(
            label = stringResource(R.string.template_field_amount_kind),
            options = listOf(false, true),
            selected = form.amountIsVariable,
            optionLabel = { stringResource(if (it) R.string.template_amount_approximate else R.string.template_amount_fixed) },
            onSelect = { edit(form.copy(amountIsVariable = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
        AmountField(form = form, onFormChange = edit)
        if (form.amountIsVariable) {
            Text(
                text = stringResource(R.string.recurring_form_variable_amount_help),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        RecurringScheduleFields(form = form, onFormChange = edit)
        RecordingFields(
            form = form,
            accounts = state.accounts,
            categories = state.categories,
            people = state.people,
            onFormChange = edit,
            sharingActions = SharingActions(
                onOwnerSelected = viewModel::onSharingSelected,
                onMemberSelected = viewModel::onSharingPersonSelected,
                onSplitEditorChange = viewModel::onSplitEditorChanged,
                onCreatePerson = viewModel::onCreatePersonInSplit,
            ),
        )
        val account = state.accounts.firstOrNull { it.id == form.accountId }
        FormDisclosure(
            open = form.showOptional,
            onToggle = { edit(form.copy(showOptional = !form.showOptional)) },
            summary = detailsSummary(form, state),
        ) {
            if (form.type == MovementType.EXPENSE) {
                ExpenseSharingFields(form = form, account = account, people = state.people, viewModel = viewModel)
            }
            OptionalFields(form = form, trips = state.trips, tags = state.tags, onFormChange = edit)
        }
    }
}

private fun TemplateFormState.withoutTransientUi(): TemplateFormState =
    copy(showOptional = false, isSaving = false, errorRes = null, errorField = null, errorMessage = null)

/** What Més detalls holds while closed: who an expense is for, and its trip. */
@Composable
private fun detailsSummary(form: TemplateFormState, state: RecurringUiState): String = listOfNotNull(
    when {
        form.type != MovementType.EXPENSE -> null
        form.sharing == ExpenseKind.SHARED -> stringResource(R.string.movement_forwhom_shared)
        form.sharing == ExpenseKind.FOR_OTHER -> state.people.firstOrNull { it.id == form.sharingPersonId }?.name
        else -> null
    },
    state.trips.firstOrNull { it.id == form.tripId }?.name,
).joinToString(" · ")

/**
 * Who a recurring expense is for, as the movement form asks it: the owner's own, shared (the split
 * editor), or wholly another person's. On a shared account it is always split, so only the editor shows.
 */
@Composable
private fun ExpenseSharingFields(
    form: TemplateFormState,
    account: AccountSummary?,
    people: List<PersonSummary>,
    viewModel: RecurringViewModel,
) {
    val sharedAccount = account?.ownershipKind == AccountOwnershipKind.SHARED
    val personError = form.errorField == TemplateFormField.PERSON
    // A variable amount's split is entered as shares of 100 EUR.
    val amountInput = form.amount.ifBlank { formatEuroInput(VARIABLE_AMOUNT_WEIGHT_CENTS) }
    if (!sharedAccount) {
        val kind = form.sharing ?: ExpenseKind.PERSONAL
        ForWhomFields(
            kind = kind,
            otherPersonId = form.sharingPersonId,
            people = people,
            amountInput = amountInput,
            onKindSelected = viewModel::onSharingSelected,
            onOtherPersonSelected = viewModel::onSharingPersonSelected,
            modifier = Modifier.scrollToWhen(personError),
            errorText = form.errorRes?.takeIf { personError }?.let { stringResource(it) },
        )
    }
    if (sharedAccount || form.sharing == ExpenseKind.SHARED) {
        form.splitEditor?.let { editor ->
            SplitEditorCard(
                splitEditor = editor,
                people = people,
                amountInput = amountInput,
                onChange = viewModel::onSplitEditorChanged,
                onCreatePerson = viewModel::onCreatePersonInSplit,
                fundedByAccount = sharedAccount,
                modifier = Modifier.scrollToWhen(form.errorField == TemplateFormField.SPLIT),
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
        label = {
            Text(stringResource(if (form.amountIsVariable) R.string.template_field_amount_estimate else R.string.template_field_amount))
        },
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

/** How the template form answers who an item is for. */
internal class SharingActions(
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
    sharingActions: SharingActions,
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
            // Between accounts of the same kind, as in the movement form.
            accounts = accounts.filter { other ->
                other.id != form.accountId &&
                    other.ownershipKind == accounts.firstOrNull { it.id == form.accountId }?.ownershipKind
            },
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
                owner = form.sharing,
                // A variable amount's allocation is entered as shares of 100 EUR.
                amount = form.amount.ifBlank { formatEuroInput(VARIABLE_AMOUNT_WEIGHT_CENTS) },
                account = account,
                people = people,
                otherPersonId = form.sharingPersonId,
                splitEditor = form.splitEditor,
                ownerErrorText = errorText.takeIf { form.errorField == TemplateFormField.INCOME_OWNER },
                personErrorText = errorText.takeIf { personError },
                splitError = form.errorField == TemplateFormField.SPLIT,
                onOwnerSelected = sharingActions.onOwnerSelected,
                onOtherPersonSelected = sharingActions.onMemberSelected,
                onSplitEditorChange = sharingActions.onSplitEditorChange,
                onCreatePersonInSplit = sharingActions.onCreatePerson,
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
private fun RecurringScheduleFields(
    form: TemplateFormState,
    onFormChange: (TemplateFormState) -> Unit,
) {
    val error = form.errorField == TemplateFormField.SCHEDULE
    val errorText = if (error && form.errorRes != null) stringResource(form.errorRes) else null
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
    FormSelect(
        label = stringResource(R.string.template_field_frequency),
        options = RecurrenceFrequency.entries.map { SelectOption(it.name, it.cadenceLabel()) },
        selectedId = form.frequency.name,
        onSelect = { id ->
            RecurrenceFrequency.entries.firstOrNull { it.name == id }?.let {
                onFormChange(form.withFrequencyDefaults(it))
            }
        },
        modifier = Modifier.weight(1f),
    )
    when {
        form.frequency.usesDayOfMonth() -> FormSelect(
            label = stringResource(R.string.template_field_anchor_day),
            options = (1..31).map { SelectOption(it.toString(), it.toString()) },
            selectedId = form.dayOfMonth.trim().takeIf { it.isNotEmpty() },
            onSelect = { onFormChange(form.copy(dayOfMonth = it.orEmpty())) },
            isError = error,
            supportingText = errorText,
            modifier = Modifier.weight(1f).scrollToWhen(error),
        )
        form.frequency.usesWeekday() -> {
            val labels = stringArrayResource(R.array.template_weekday_short)
            FormSelect(
                label = stringResource(R.string.template_field_weekday),
                options = labels.mapIndexed { index, label -> SelectOption(index.toString(), label) },
                selectedId = form.weekday?.toString(),
                onSelect = { onFormChange(form.copy(weekday = it?.toIntOrNull())) },
                modifier = Modifier.weight(1f),
            )
        }
        else -> Unit
    }
    }
    when {
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
    val dateError = form.errorField == TemplateFormField.NEXT_DUE_DATE
    val marginError = form.errorField == TemplateFormField.DATE_FLEX
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        FormDatePicker(
            label = stringResource(R.string.template_field_next_due),
            date = form.nextDueDate,
            onDateChange = { onFormChange(form.copy(nextDueDate = it)) },
            isError = dateError,
            supportingText = if (dateError && form.errorRes != null) stringResource(form.errorRes) else null,
            modifier = Modifier.weight(1.3f).scrollToWhen(dateError),
        )
        // A few margins cover real payments; one saved outside them stays selectable.
        val current = form.dateFlex.trim().toIntOrNull()?.takeIf { it > 0 }
        val margins = (MARGIN_CHOICES + listOfNotNull(current)).distinct().sorted()
        FormSelect(
            label = stringResource(R.string.template_field_date_flex),
            options = listOf(SelectOption("0", stringResource(R.string.template_margin_exact))) +
                margins.map { SelectOption(it.toString(), stringResource(R.string.template_margin_days, it)) },
            selectedId = (current ?: 0).toString(),
            onSelect = { onFormChange(form.copy(dateFlex = if (it == null || it == "0") "" else it)) },
            isError = marginError,
            supportingText = if (marginError && form.errorRes != null) stringResource(form.errorRes) else null,
            modifier = Modifier.weight(1f).scrollToWhen(marginError),
        )
    }
    Text(
        text = stringResource(R.string.template_date_flex_help),
        color = FinanceTheme.colors.mutedText,
        style = MaterialTheme.typography.bodySmall,
    )
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
private fun CustomRecurrenceUnit.formLabel(): String = when (this) {
    CustomRecurrenceUnit.DAYS -> stringResource(R.string.template_unit_days)
    CustomRecurrenceUnit.WEEKS -> stringResource(R.string.template_unit_weeks)
    CustomRecurrenceUnit.MONTHS -> stringResource(R.string.template_unit_months)
    CustomRecurrenceUnit.YEARS -> stringResource(R.string.template_unit_years)
}

private val MARGIN_CHOICES = listOf(1, 2, 3, 5, 7)
