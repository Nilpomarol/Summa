package com.gestorfinances.app.ui.movements

import com.gestorfinances.app.ui.theme.formAction
import org.jetbrains.compose.resources.stringResource
import com.gestorfinances.app.ui.common.SegmentedControl
import com.gestorfinances.app.ui.common.formatEuroCents
import com.gestorfinances.app.ui.common.parseEuroCents
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.Role
import com.gestorfinances.app.ui.common.formatCompactDateRelative
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.gestorfinances.app.ui.common.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.category_list_add
import com.gestorfinances.ui.resources.common_no
import com.gestorfinances.ui.resources.common_no_category
import com.gestorfinances.ui.resources.common_review
import com.gestorfinances.ui.resources.failure_save_movement
import com.gestorfinances.ui.resources.movement_duplicate_override
import com.gestorfinances.ui.resources.movement_duplicate_warning
import com.gestorfinances.ui.resources.movement_field_account
import com.gestorfinances.ui.resources.movement_field_category
import com.gestorfinances.ui.resources.movement_field_date
import com.gestorfinances.ui.resources.movement_field_notes
import com.gestorfinances.ui.resources.movement_field_one_time
import com.gestorfinances.ui.resources.movement_field_payee
import com.gestorfinances.ui.resources.movement_field_tag
import com.gestorfinances.ui.resources.movement_field_trip
import com.gestorfinances.ui.resources.movement_form_edit
import com.gestorfinances.ui.resources.movement_form_new_expense
import com.gestorfinances.ui.resources.movement_form_new_income
import com.gestorfinances.ui.resources.movement_form_new_transfer
import com.gestorfinances.ui.resources.movement_forwhom_person
import com.gestorfinances.ui.resources.movement_forwhom_shared
import com.gestorfinances.ui.resources.movement_no_trip
import com.gestorfinances.ui.resources.movement_recurring_match
import com.gestorfinances.ui.resources.movement_recurring_stop_end
import com.gestorfinances.ui.resources.movement_recurring_stop_title
import com.gestorfinances.ui.resources.movement_recurring_stop_unlink
import com.gestorfinances.ui.resources.movement_repeat_ended
import com.gestorfinances.ui.resources.movement_repeat_label
import com.gestorfinances.ui.resources.movement_repeat_linked
import com.gestorfinances.ui.resources.movement_save_changes
import com.gestorfinances.ui.resources.movement_save_new
import com.gestorfinances.ui.resources.movement_warning_split_removed
import com.gestorfinances.ui.resources.recurring_update_amount
import com.gestorfinances.ui.resources.tag_picker_none
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.data.repository.supports
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.ui.common.FormDisclosure
import com.gestorfinances.app.ui.common.FormReveal
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.movementTypeIcon
import com.gestorfinances.app.ui.common.nextFieldKeyboardActions
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import com.gestorfinances.app.ui.theme.categoryColor

@Composable
fun MovementFormBody(
    form: MovementFormState,
    accounts: List<AccountSummary>,
    categories: List<CategoryRecord>,
    people: List<PersonSummary>,
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    onFormChange: (MovementFormState) -> Unit,
    onTripSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
    onSharedToggled: (Boolean) -> Unit,
    onPayerSplitToggled: (Boolean) -> Unit,
    onSplitEditorChange: (SplitEditorState) -> Unit,
    onOtherPersonSelected: (String?) -> Unit,
    onRecurringToggled: (Boolean) -> Unit,
    onRecurringFrequencyChanged: (RecurrenceFrequency) -> Unit,
    onOptionalToggled: () -> Unit,
    onCreatePersonInSplit: (String) -> Unit,
    onCreateCategory: (name: String, iconKey: String?, colorHex: String) -> Unit,
    createCategory: @Composable (onConfirm: (name: String, iconKey: String?, colorHex: String) -> Unit, onDismiss: () -> Unit) -> Unit,
    /** Enter in the concept, on the desktop: the form is saved from the keyboard. */
    onSubmit: () -> Unit = {},
) {
    val errorText = form.errorRes?.let { stringResource(it) }
    fun errorFor(field: MovementFormField): String? = errorText.takeIf { form.errorField == field }

    // Top-of-form text is reserved for save/repository failures (errorMessage); field-level
    // validation errors render at their control, and save warnings next to the Save button.
    form.errorMessage?.let {
        InlineFailureBanner(diagnostic = it, messageRes = Res.string.failure_save_movement)
    }

    // Each type takes its own money colour once chosen.
    SegmentedControl(
        options = formMovementTypes,
        selected = form.type,
        label = { it.formLabel() },
        onSelect = { onFormChange(form.copy(type = it)) },
        optionColor = { FinanceTheme.colors.amountColor(it) },
    )
    errorFor(MovementFormField.TYPE)?.let {
        InlineBanner(kind = BannerKind.Alert, text = it, modifier = Modifier.scrollToWhen(true))
    }

    val selectedCategory = categories.firstOrNull { it.id == form.categoryId }
    val finance = FinanceTheme.colors
    val (icon, iconColor) = when {
        form.type == MovementType.TRANSFER -> movementTypeIcon(form.type) to finance.transfer
        selectedCategory != null -> categoryIcon(selectedCategory.icon) to categoryColor(selectedCategory.color)
        form.type == MovementType.INCOME -> movementTypeIcon(form.type) to finance.income
        else -> categoryIcon(null) to categoryColor(null)
    }
    val placeholderTitle = stringResource(
        when {
            !form.isNew -> Res.string.movement_form_edit
            form.type == MovementType.INCOME -> Res.string.movement_form_new_income
            form.type == MovementType.TRANSFER -> Res.string.movement_form_new_transfer
            else -> Res.string.movement_form_new_expense
        },
    )
    MovementFormHeader(
        icon = icon,
        iconColor = iconColor,
        title = form.name.ifBlank { placeholderTitle },
        titleIsPlaceholder = form.name.isBlank(),
        amount = form.amount,
        onAmountChange = { onFormChange(form.copy(amount = it, errorRes = null, errorField = null)) },
        amountColor = finance.amountColor(form.type),
        amountError = errorFor(MovementFormField.AMOUNT),
        modifier = Modifier.scrollToWhen(form.errorField == MovementFormField.AMOUNT),
    )
    ConceptField(
        name = form.name,
        onNameChange = { onFormChange(form.copy(name = it, errorRes = null, errorField = null)) },
        onDone = onSubmit,
    )

    val fundedBySharedAccount = form.type == MovementType.EXPENSE &&
        form.expenseKind != ExpenseKind.DEBT &&
        accounts.firstOrNull { it.id == form.accountId }?.ownershipKind == AccountOwnershipKind.SHARED
    val dateField: @Composable (Modifier) -> Unit = { modifier ->
        val dateError = form.errorField == MovementFormField.DATE
        FormDatePicker(
            label = stringResource(Res.string.movement_field_date),
            date = form.date,
            onDateChange = { onFormChange(form.copy(date = it, errorRes = null, errorField = null)) },
            modifier = modifier.scrollToWhen(dateError),
            isError = dateError,
            supportingText = errorFor(MovementFormField.DATE),
        )
    }

    // Two lines of fields for every type, so switching type keeps the sheet's height.
    if (form.type == MovementType.TRANSFER) {
        TransferAccountFields(form = form, accounts = accounts, onFormChange = onFormChange)
        dateField(Modifier.fillMaxWidth())
    } else {
        CategoryField(
            form = form,
            categories = categories,
            onFormChange = onFormChange,
            onCreateCategory = onCreateCategory,
            supportingText = errorFor(MovementFormField.CATEGORY),
            createCategory = createCategory,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val accountModifier = Modifier.weight(1f)
            if (form.type == MovementType.EXPENSE) {
                ExpenseAccountField(
                    form = form,
                    accounts = accounts,
                    people = people,
                    fundedBySharedAccount = fundedBySharedAccount,
                    onFormChange = onFormChange,
                    onOpenDetails = { if (!form.showOptional) onOptionalToggled() },
                    modifier = accountModifier,
                )
            } else {
                AccountSelect(
                    label = stringResource(Res.string.movement_field_account),
                    selectedId = form.accountId,
                    accounts = accounts,
                    onSelect = { onFormChange(form.copy(accountId = it)) },
                    modifier = accountModifier.scrollToWhen(form.errorField == MovementFormField.ACCOUNT),
                    isError = form.errorField == MovementFormField.ACCOUNT,
                    supportingText = errorFor(MovementFormField.ACCOUNT),
                )
            }
            dateField(Modifier.weight(1f))
        }
    }

    DetailsToggle(form = form, people = people, trips = trips, onToggle = onOptionalToggled)
    FormReveal(visible = form.showOptional) {
        MovementDetails(
            form = form,
            accounts = accounts,
            people = people,
            trips = trips,
            tags = tags,
            fundedBySharedAccount = fundedBySharedAccount,
            onFormChange = onFormChange,
            onTripSelected = onTripSelected,
            onTagSelected = onTagSelected,
            onSharedToggled = onSharedToggled,
            onPayerSplitToggled = onPayerSplitToggled,
            onSplitEditorChange = onSplitEditorChange,
            onOtherPersonSelected = onOtherPersonSelected,
            onRecurringToggled = onRecurringToggled,
            onRecurringFrequencyChanged = onRecurringFrequencyChanged,
            onCreatePersonInSplit = onCreatePersonInSplit,
        )
    }
}

private const val CREATE_CATEGORY_OPTION = "__create_category__"

/** The category, with a new one creatable in place. */
@Composable
private fun CategoryField(
    form: MovementFormState,
    categories: List<CategoryRecord>,
    onFormChange: (MovementFormState) -> Unit,
    onCreateCategory: (name: String, iconKey: String?, colorHex: String) -> Unit,
    supportingText: String?,
    createCategory: @Composable (onConfirm: (name: String, iconKey: String?, colorHex: String) -> Unit, onDismiss: () -> Unit) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    val noCategory = stringResource(Res.string.common_no_category)
    val createLabel = stringResource(Res.string.category_list_add)
    val options = remember(categories, form.type, noCategory, createLabel) {
        buildCategorySelectOptions(categories.filter { it.supports(form.type) }, noCategory) +
            SelectOption(id = CREATE_CATEGORY_OPTION, label = createLabel, leading = { AddLeading() })
    }
    val isError = form.errorField == MovementFormField.CATEGORY
    FormSelect(
        label = stringResource(Res.string.movement_field_category),
        options = options,
        selectedId = form.categoryId,
        onSelect = { id ->
            if (id == CREATE_CATEGORY_OPTION) {
                creating = true
            } else {
                onFormChange(form.copy(categoryId = id, errorRes = null, errorField = null))
            }
        },
        placeholder = noCategory,
        modifier = Modifier.fillMaxWidth().scrollToWhen(isError),
        isError = isError,
        supportingText = supportingText,
    )
    if (creating) {
        createCategory(onCreateCategory) { creating = false }
    }
}

@Composable
private fun AddLeading() {
    Icon(
        imageVector = Icons.Outlined.Add,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
    )
}

/** Opens and closes the optional fields; closed, it names what they already hold. */
@Composable
private fun DetailsToggle(
    form: MovementFormState,
    people: List<PersonSummary>,
    trips: List<TripSummary>,
    onToggle: () -> Unit,
) {
    val parts = mutableListOf<String>()
    if (form.type == MovementType.EXPENSE) {
        when {
            form.expenseKind == ExpenseKind.SHARED || (form.expenseKind == ExpenseKind.DEBT && form.payerSplit) ->
                parts += stringResource(Res.string.movement_forwhom_shared)
            form.expenseKind == ExpenseKind.FOR_OTHER ->
                people.firstOrNull { it.id == form.forOtherPersonId }?.let {
                    parts += stringResource(Res.string.movement_forwhom_person, it.name)
                }
        }
    }
    if (form.type != MovementType.TRANSFER) {
        trips.firstOrNull { it.id == form.tripId }?.let { parts += it.name }
    }
    if (form.isRecurring) parts += form.recurringFrequency.cadenceLabel()
    if (form.type == MovementType.EXPENSE && form.isOneTime) parts += stringResource(Res.string.movement_field_one_time)
    FormDisclosure(open = form.showOptional, onToggle = onToggle, summary = parts.joinToString(" · "))
}

/** The optional fields on one level: sharing, trip, repetition, then the rarely used ones. */
@Composable
private fun MovementDetails(
    form: MovementFormState,
    accounts: List<AccountSummary>,
    people: List<PersonSummary>,
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    fundedBySharedAccount: Boolean,
    onFormChange: (MovementFormState) -> Unit,
    onTripSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
    onSharedToggled: (Boolean) -> Unit,
    onPayerSplitToggled: (Boolean) -> Unit,
    onSplitEditorChange: (SplitEditorState) -> Unit,
    onOtherPersonSelected: (String?) -> Unit,
    onRecurringToggled: (Boolean) -> Unit,
    onRecurringFrequencyChanged: (RecurrenceFrequency) -> Unit,
    onCreatePersonInSplit: (String) -> Unit,
) {
    when (form.type) {
        MovementType.EXPENSE -> ExpenseShareRows(
            form = form,
            people = people,
            fundedBySharedAccount = fundedBySharedAccount,
            onFormChange = onFormChange,
            onSharedToggled = onSharedToggled,
            onPayerSplitToggled = onPayerSplitToggled,
            onSplitEditorChange = onSplitEditorChange,
            onOtherPersonSelected = onOtherPersonSelected,
            onCreatePersonInSplit = onCreatePersonInSplit,
        )
        MovementType.INCOME -> IncomeOwnershipDetails(
            form = form,
            accounts = accounts,
            people = people,
            onFormChange = onFormChange,
            onSharedToggled = onSharedToggled,
            onSplitEditorChange = onSplitEditorChange,
            onOtherPersonSelected = onOtherPersonSelected,
            onCreatePersonInSplit = onCreatePersonInSplit,
        )
        else -> Unit
    }
    // An expense someone else paid never recurs.
    val canRepeat = !(form.type == MovementType.EXPENSE && form.expenseKind == ExpenseKind.DEBT)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (form.type != MovementType.TRANSFER) {
            val noTrip = stringResource(Res.string.movement_no_trip)
            FormSelect(
                label = stringResource(Res.string.movement_field_trip),
                options = listOf(SelectOption(id = null, label = noTrip)) +
                    trips.map { SelectOption(id = it.id, label = it.name) },
                selectedId = form.tripId,
                onSelect = onTripSelected,
                placeholder = noTrip,
                modifier = Modifier.weight(1f),
            )
        }
        if (canRepeat) RepeatField(form, onRecurringToggled, onRecurringFrequencyChanged, Modifier.weight(1f))
    }
    val trip = trips.firstOrNull { it.id == form.tripId }
    val tagOptions = tags.filter { it.supportsTrip(trip) }
    if (form.type != MovementType.TRANSFER && trip != null && tagOptions.isNotEmpty()) {
        val noTag = stringResource(Res.string.tag_picker_none)
        val tagError = form.errorField == MovementFormField.TAG
        FormSelect(
            label = stringResource(Res.string.movement_field_tag),
            options = listOf(SelectOption(id = null, label = noTag)) +
                tagOptions.map { SelectOption(id = it.id, label = it.name) },
            selectedId = form.tagId,
            onSelect = onTagSelected,
            placeholder = noTag,
            modifier = Modifier.fillMaxWidth().scrollToWhen(tagError),
            isError = tagError,
            supportingText = form.errorRes?.takeIf { tagError }?.let { stringResource(it) },
        )
    }
    if (form.type == MovementType.EXPENSE) {
        FormToggleRow(
            label = stringResource(Res.string.movement_field_one_time),
            checked = form.isOneTime,
            onCheckedChange = { onFormChange(form.copy(isOneTime = it)) },
        )
    }
    if (form.type != MovementType.TRANSFER) {
        AppTextField(
            value = form.payee,
            onValueChange = { onFormChange(form.copy(payee = it)) },
            label = { Text(stringResource(Res.string.movement_field_payee)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = nextFieldKeyboardActions(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    AppTextField(
        value = form.notes,
        onValueChange = { onFormChange(form.copy(notes = it)) },
        label = { Text(stringResource(Res.string.movement_field_notes)) },
        minLines = 2,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val NOT_RECURRING = "none"

/**
 * Whether the movement repeats. A movement linked to a template shows the template's cadence
 * read-only: it is the template's truth, edited in Recurrents. Turning it off stays possible;
 * the save then asks whether the series ends.
 */
@Composable
private fun RepeatField(
    form: MovementFormState,
    onRecurringToggled: (Boolean) -> Unit,
    onRecurringFrequencyChanged: (RecurrenceFrequency) -> Unit,
    modifier: Modifier,
) {
    val no = stringResource(Res.string.common_no)
    val options = if (form.templateId != null) {
        val linked = if (form.templateStatus == TemplateStatus.ENDED) {
            stringResource(Res.string.movement_repeat_ended)
        } else {
            stringResource(Res.string.movement_repeat_linked, form.recurringFrequency.cadenceLabel())
        }
        listOf(SelectOption(id = form.recurringFrequency.name, label = linked))
    } else {
        listOf(
            RecurrenceFrequency.WEEKLY,
            RecurrenceFrequency.FORTNIGHTLY,
            RecurrenceFrequency.MONTHLY,
            RecurrenceFrequency.YEARLY,
        ).map { SelectOption(id = it.name, label = it.cadenceLabel()) }
    }
    FormSelect(
        label = stringResource(Res.string.movement_repeat_label),
        options = listOf(SelectOption(id = NOT_RECURRING, label = no)) + options,
        selectedId = if (form.isRecurring) form.recurringFrequency.name else NOT_RECURRING,
        onSelect = { id ->
            if (id == null || id == NOT_RECURRING) {
                onRecurringToggled(false)
            } else {
                if (form.templateId == null) onRecurringFrequencyChanged(RecurrenceFrequency.valueOf(id))
                if (!form.isRecurring) onRecurringToggled(true)
            }
        },
        placeholder = no,
        modifier = modifier,
    )
}

/** The recurring item a new movement was recognised as, ticked to save it as that occurrence. */
@Composable
private fun RecurringMatchRow(match: RecurringMatchSuggestion, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
        Icon(
            imageVector = Icons.Outlined.Repeat,
            contentDescription = null,
            tint = FinanceTheme.colors.mutedText,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(Res.string.movement_recurring_match, match.name, formatCompactDateRelative(match.dueDate)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Save warnings and actions stay visible below the independently scrolling form body. */
@Composable
fun MovementSaveActions(
    form: MovementFormState,
    onFormChange: (MovementFormState) -> Unit,
    onSave: () -> Unit,
    onOverride: () -> Unit,
    onSplitRemovalAccepted: () -> Unit,
    onRecurrenceStopEnd: () -> Unit,
    onRecurrenceStopUnlink: () -> Unit,
    onWarningDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasWarning = form.duplicateWarning || form.pendingDataLossWarning != null
    val isRecurrenceStop = form.pendingDataLossWarning == DataLossWarning.RECURRING_STOP
    val warningText = when (form.pendingDataLossWarning) {
        DataLossWarning.SPLIT_REMOVED -> stringResource(Res.string.movement_warning_split_removed)
        DataLossWarning.RECURRING_STOP -> stringResource(Res.string.movement_recurring_stop_title)
        null -> stringResource(Res.string.movement_duplicate_warning)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        form.recurringMatch?.let { match ->
            RecurringMatchRow(match, form.linkToRecurring) { onFormChange(form.copy(linkToRecurring = it)) }
            val amount = parseEuroCents(form.amount, allowNegative = false)
            if (form.linkToRecurring && match.fixedAmountCents != null && amount != null && amount != match.fixedAmountCents) {
                FormToggleRow(
                    label = stringResource(
                        Res.string.recurring_update_amount,
                        formatEuroCents(amount),
                        formatEuroCents(match.fixedAmountCents!!),
                    ),
                    checked = form.updateRecurringAmount,
                    onCheckedChange = { onFormChange(form.copy(updateRecurringAmount = it)) },
                )
            }
        }
        if (hasWarning) {
            InlineBanner(kind = BannerKind.Alert, text = warningText)
            AppTextButton(onClick = onWarningDismissed, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(Res.string.common_review))
            }
        }

        PrimaryButton(
            text = when {
                isRecurrenceStop -> stringResource(Res.string.movement_recurring_stop_end)
                hasWarning -> stringResource(Res.string.movement_duplicate_override)
                form.isNew -> stringResource(Res.string.movement_save_new)
                else -> stringResource(Res.string.movement_save_changes)
            },
            onClick = when {
                isRecurrenceStop -> onRecurrenceStopEnd
                form.pendingDataLossWarning != null -> onSplitRemovalAccepted
                form.duplicateWarning -> onOverride
                else -> onSave
            },
            modifier = Modifier.formAction(),
            enabled = !form.isSaving,
        )
        // Third choice for the recurring-stop warning: the old
        // "just detach" behavior remains alongside the default end-template action.
        if (isRecurrenceStop) {
            AppTextButton(onClick = onRecurrenceStopUnlink, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(Res.string.movement_recurring_stop_unlink))
            }
        }
    }
}
