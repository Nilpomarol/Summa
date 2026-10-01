package com.gestorfinances.app.ui.movements

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
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
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.FormDisclosure
import com.gestorfinances.app.ui.common.FormReveal
import com.gestorfinances.app.ui.common.AppSheetHandleTouchHeight
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

/**
 * The create/edit movement sheet. The category tile, title and amount lead, the concept field
 * follows; then two lines of fields that every type shares until "Més detalls" opens, so switching type never resizes it.
 */
@Composable
fun MovementFormScreen(
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
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onOverride: () -> Unit,
    onSplitRemovalAccepted: () -> Unit,
    onRecurrenceStopEnd: () -> Unit,
    onRecurrenceStopUnlink: () -> Unit,
    onWarningDismissed: () -> Unit,
    dismissRequested: Boolean = false,
    confirmDismiss: (() -> Boolean)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // The collapsed sheet is its body plus the fixed chrome around it (save button, paddings),
    // tracked while collapsed so late font loading or an error line resize it; every type has the
    // same body, so switching type keeps the height. It is also where "Més detalls" animates from.
    var chromeHeight by remember { mutableStateOf<Dp?>(null) }
    var collapsedBodyHeight by remember { mutableStateOf<Dp?>(null) }
    val compactContentHeight = chromeHeight?.let { chrome -> collapsedBodyHeight?.plus(chrome) }
    val expanded = form.showOptional
    val pinned = expanded || compactContentHeight != null
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        dismissRequested = dismissRequested,
        fixedHeightFraction = MovementSheetExpandedHeightFraction.takeIf { expanded },
        fixedHeight = if (expanded) null else compactContentHeight?.plus(AppSheetHandleTouchHeight),
        keepDragHandleInside = true,
        confirmDismiss = confirmDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (pinned) Modifier.weight(1f) else Modifier)
                .onSizeChanged { size ->
                    val body = collapsedBodyHeight
                    if (!pinned && body != null) {
                        chromeHeight = with(density) { size.height.toDp() } - body
                    }
                }
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (pinned) Modifier.weight(1f) else Modifier)
                    .verticalScroll(rememberScrollState())
                    // The content's own height: a pinned viewport would otherwise be its minimum.
                    .wrapContentHeight(Alignment.Top)
                    .onSizeChanged { size ->
                        if (!expanded) collapsedBodyHeight = with(density) { size.height.toDp() }
                    }
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MovementFormBody(
                    form = form,
                    accounts = accounts,
                    categories = categories,
                    people = people,
                    trips = trips,
                    tags = tags,
                    onFormChange = onFormChange,
                    onTripSelected = onTripSelected,
                    onTagSelected = onTagSelected,
                    onSharedToggled = onSharedToggled,
                    onPayerSplitToggled = onPayerSplitToggled,
                    onSplitEditorChange = onSplitEditorChange,
                    onOtherPersonSelected = onOtherPersonSelected,
                    onRecurringToggled = onRecurringToggled,
                    onRecurringFrequencyChanged = onRecurringFrequencyChanged,
                    onOptionalToggled = onOptionalToggled,
                    onCreatePersonInSplit = onCreatePersonInSplit,
                    onCreateCategory = onCreateCategory,
                )
            }
            MovementSaveActions(
                form = form,
                onFormChange = onFormChange,
                onSave = onSave,
                onOverride = onOverride,
                onSplitRemovalAccepted = onSplitRemovalAccepted,
                onRecurrenceStopEnd = onRecurrenceStopEnd,
                onRecurrenceStopUnlink = onRecurrenceStopUnlink,
                onWarningDismissed = onWarningDismissed,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun MovementFormBody(
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
) {
    val errorText = form.errorRes?.let { stringResource(it) }
    fun errorFor(field: MovementFormField): String? = errorText.takeIf { form.errorField == field }

    // Top-of-form text is reserved for save/repository failures (errorMessage); field-level
    // validation errors render at their control, and save warnings next to the Save button.
    form.errorMessage?.let {
        InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_movement)
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
            !form.isNew -> R.string.movement_form_edit
            form.type == MovementType.INCOME -> R.string.movement_form_new_income
            form.type == MovementType.TRANSFER -> R.string.movement_form_new_transfer
            else -> R.string.movement_form_new_expense
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
    )

    val fundedBySharedAccount = form.type == MovementType.EXPENSE &&
        form.expenseKind != ExpenseKind.DEBT &&
        accounts.firstOrNull { it.id == form.accountId }?.ownershipKind == AccountOwnershipKind.SHARED
    val dateField: @Composable (Modifier) -> Unit = { modifier ->
        val dateError = form.errorField == MovementFormField.DATE
        FormDatePicker(
            label = stringResource(R.string.movement_field_date),
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
                    label = stringResource(R.string.movement_field_account),
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
) {
    var creating by remember { mutableStateOf(false) }
    val noCategory = stringResource(R.string.common_no_category)
    val createLabel = stringResource(R.string.category_list_add)
    val options = remember(categories, form.type, noCategory, createLabel) {
        buildCategorySelectOptions(categories.filter { it.supports(form.type) }, noCategory) +
            SelectOption(id = CREATE_CATEGORY_OPTION, label = createLabel, leading = { AddLeading() })
    }
    val isError = form.errorField == MovementFormField.CATEGORY
    FormSelect(
        label = stringResource(R.string.movement_field_category),
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
        CreateCategorySheet(
            // The sheet animates away after creating, then reports it is gone.
            onConfirm = onCreateCategory,
            onDismiss = { creating = false },
        )
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
                parts += stringResource(R.string.movement_forwhom_shared)
            form.expenseKind == ExpenseKind.FOR_OTHER ->
                people.firstOrNull { it.id == form.forOtherPersonId }?.let {
                    parts += stringResource(R.string.movement_forwhom_person, it.name)
                }
        }
    }
    if (form.type != MovementType.TRANSFER) {
        trips.firstOrNull { it.id == form.tripId }?.let { parts += it.name }
    }
    if (form.isRecurring) parts += form.recurringFrequency.cadenceLabel()
    if (form.type == MovementType.EXPENSE && form.isOneTime) parts += stringResource(R.string.movement_field_one_time)
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
            val noTrip = stringResource(R.string.movement_no_trip)
            FormSelect(
                label = stringResource(R.string.movement_field_trip),
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
        val noTag = stringResource(R.string.tag_picker_none)
        val tagError = form.errorField == MovementFormField.TAG
        FormSelect(
            label = stringResource(R.string.movement_field_tag),
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
            label = stringResource(R.string.movement_field_one_time),
            checked = form.isOneTime,
            onCheckedChange = { onFormChange(form.copy(isOneTime = it)) },
        )
    }
    if (form.type != MovementType.TRANSFER) {
        OutlinedTextField(
            value = form.payee,
            onValueChange = { onFormChange(form.copy(payee = it)) },
            label = { Text(stringResource(R.string.movement_field_payee)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = nextFieldKeyboardActions(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = form.notes,
        onValueChange = { onFormChange(form.copy(notes = it)) },
        label = { Text(stringResource(R.string.movement_field_notes)) },
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
    val no = stringResource(R.string.common_no)
    val options = if (form.templateId != null) {
        val linked = if (form.templateStatus == TemplateStatus.ENDED) {
            stringResource(R.string.movement_repeat_ended)
        } else {
            stringResource(R.string.movement_repeat_linked, form.recurringFrequency.cadenceLabel())
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
        label = stringResource(R.string.movement_repeat_label),
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
            text = stringResource(R.string.movement_recurring_match, match.name, formatCompactDateRelative(match.dueDate)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private const val MovementSheetExpandedHeightFraction = 0.84f

/** Save warnings and actions stay visible below the independently scrolling form body. */
@Composable
private fun MovementSaveActions(
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
        DataLossWarning.SPLIT_REMOVED -> stringResource(R.string.movement_warning_split_removed)
        DataLossWarning.RECURRING_STOP -> stringResource(R.string.movement_recurring_stop_title)
        null -> stringResource(R.string.movement_duplicate_warning)
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
                        R.string.recurring_update_amount,
                        formatEuroCents(amount),
                        formatEuroCents(match.fixedAmountCents),
                    ),
                    checked = form.updateRecurringAmount,
                    onCheckedChange = { onFormChange(form.copy(updateRecurringAmount = it)) },
                )
            }
        }
        if (hasWarning) {
            InlineBanner(kind = BannerKind.Alert, text = warningText)
            TextButton(onClick = onWarningDismissed, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.common_review))
            }
        }

        PrimaryButton(
            text = when {
                isRecurrenceStop -> stringResource(R.string.movement_recurring_stop_end)
                hasWarning -> stringResource(R.string.movement_duplicate_override)
                form.isNew -> stringResource(R.string.movement_save_new)
                else -> stringResource(R.string.movement_save_changes)
            },
            onClick = when {
                isRecurrenceStop -> onRecurrenceStopEnd
                form.pendingDataLossWarning != null -> onSplitRemovalAccepted
                form.duplicateWarning -> onOverride
                else -> onSave
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !form.isSaving,
        )
        // Third choice for the recurring-stop warning: the old
        // "just detach" behavior remains alongside the default end-template action.
        if (isRecurrenceStop) {
            TextButton(onClick = onRecurrenceStopUnlink, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.movement_recurring_stop_unlink))
            }
        }
    }
}
