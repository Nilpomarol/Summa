package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountOwnershipKind
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.AppModalBottomSheet
import com.gestorfinances.app.ui.common.AppSheetHandleTouchHeight
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.InlineFailureBanner
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.movementTypeIcon
import com.gestorfinances.app.ui.common.nextFieldKeyboardActions
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.categoryColor

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
    onSplitEditorChange: (SplitEditorState) -> Unit,
    onSettlementToggled: (Boolean) -> Unit,
    onSettlementPersonSelected: (String?) -> Unit,
    onOtherPersonSelected: (String?) -> Unit,
    onRecurringToggled: (Boolean) -> Unit,
    onRecurringFrequencyChanged: (RecurrenceFrequency) -> Unit,
    onOptionalToggled: () -> Unit,
    onAdvancedToggled: () -> Unit,
    onCreatePersonInSplit: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onOverride: () -> Unit,
    onSplitRemovalAccepted: () -> Unit,
    onRecurrenceStopEnd: () -> Unit,
    onRecurrenceStopUnlink: () -> Unit,
    onWarningDismissed: () -> Unit,
    dismissRequested: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val useExpandedSheetHeight = form.showOptional || form.isSettlement
    val density = LocalDensity.current
    // The three compact type layouts share one baseline. Retain its measured height while the
    // user changes type so the pinned Save action never briefly falls back into normal flow.
    var compactContentHeight by remember { mutableStateOf<androidx.compose.ui.unit.Dp?>(null) }
    // Expanded forms must pin the action bar from their first composition: edit flows can open
    // with optional values already disclosed, before a compact height has ever been measured.
    // Once a compact height exists, retain the pinned layout through a collapse animation too.
    val usePinnedActionLayout = useExpandedSheetHeight || compactContentHeight != null
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        dismissRequested = dismissRequested,
        fixedHeightFraction = MovementSheetExpandedHeightFraction.takeIf { useExpandedSheetHeight },
        fixedHeight = if (useExpandedSheetHeight) null else compactContentHeight?.plus(AppSheetHandleTouchHeight),
        keepDragHandleInside = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (usePinnedActionLayout) Modifier.weight(1f) else Modifier)
                .onSizeChanged { size ->
                    if (!useExpandedSheetHeight && compactContentHeight == null) {
                        compactContentHeight = with(density) { size.height.toDp() }
                    }
                }
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (usePinnedActionLayout) Modifier.weight(1f) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        val selectedCategory = remember(form.categoryId, categories) {
            categories.firstOrNull { it.id == form.categoryId }
        }
        val finance = FinanceTheme.colors
        val (icon, iconColor) = remember(form.type, selectedCategory, finance) {
            when (form.type) {
                MovementType.EXPENSE, MovementType.INCOME -> {
                    if (selectedCategory != null) {
                        categoryIcon(selectedCategory.icon) to categoryColor(selectedCategory.color)
                    } else if (form.type == MovementType.INCOME) {
                        movementTypeIcon(form.type) to finance.income
                    } else {
                        categoryIcon(null) to categoryColor(null)
                    }
                }
                MovementType.TRANSFER -> movementTypeIcon(form.type) to finance.transfer
                MovementType.SETTLEMENT -> movementTypeIcon(form.type) to finance.settlement
                MovementType.REFUND -> movementTypeIcon(form.type) to finance.refund
                MovementType.CONTRIBUTION -> movementTypeIcon(form.type) to finance.transfer
            }
        }

        val titleText = when {
            form.isNew -> when (form.type) {
                MovementType.EXPENSE -> stringResource(R.string.movement_form_new_expense)
                MovementType.INCOME -> stringResource(R.string.movement_form_new_income)
                MovementType.TRANSFER -> stringResource(R.string.movement_form_new_transfer)
                else -> stringResource(R.string.movement_list_add)
            }
            else -> form.name.ifBlank { stringResource(R.string.movement_form_edit) }
        }
        val amountError = form.errorField == MovementFormField.AMOUNT

        // Top-of-form text is reserved for save/repository failures (errorMessage) -- field-level
        // validation errors (errorRes) render next to the offending control, while duplicate and
        // data-loss warnings render next to the Save button below.
        form.errorMessage?.let {
            InlineFailureBanner(diagnostic = it, messageRes = R.string.failure_save_movement)
        }

        // Type row leads (no redundant "Tipus" label -- the hero title already names the type),
        // then the amount is entered in place as the hero figure, replacing the former read-only
        // preview plus a separate "Import" field.
        MovementTypeSelector(
            selected = form.type,
            onSelect = { onFormChange(form.copy(type = it)) },
            showLabel = false,
        )
        if (form.errorField == MovementFormField.TYPE && form.errorRes != null) {
            InlineBanner(
                kind = BannerKind.Alert,
                text = stringResource(form.errorRes),
                modifier = Modifier.scrollToWhen(true),
            )
        }

        MovementAmountHeader(
            title = titleText,
            amount = form.amount,
            onAmountChange = { onFormChange(form.copy(amount = it, errorRes = null, errorField = null)) },
            type = form.type,
            icon = icon,
            iconColor = iconColor,
            isError = amountError,
            supportingText = if (amountError && form.errorRes != null) stringResource(form.errorRes) else null,
            modifier = Modifier.scrollToWhen(amountError),
        )
        // Type 4 (Debt): the amount is what the user owes — surface that affordance.
        // Concepte
        OutlinedTextField(
            value = form.name,
            onValueChange = { onFormChange(form.copy(name = it, errorRes = null, errorField = null)) },
            label = { Text(stringResource(R.string.movement_field_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = nextFieldKeyboardActions(),
            modifier = Modifier.fillMaxWidth(),
        )

        // Row: Date & Category (transfers have no category — date spans full width)
        val dateError = form.errorField == MovementFormField.DATE
        val categoryError = form.errorField == MovementFormField.CATEGORY
        if (form.type == MovementType.TRANSFER) {
            FormDatePicker(
                label = stringResource(R.string.movement_field_date),
                date = form.date,
                onDateChange = { onFormChange(form.copy(date = it, errorRes = null, errorField = null)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollToWhen(dateError),
                isError = dateError,
                supportingText = if (dateError && form.errorRes != null) stringResource(form.errorRes) else null,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                FormDatePicker(
                    label = stringResource(R.string.movement_field_date),
                    date = form.date,
                    onDateChange = { onFormChange(form.copy(date = it, errorRes = null, errorField = null)) },
                    modifier = Modifier
                        .weight(1f)
                        .scrollToWhen(dateError),
                    isError = dateError,
                    supportingText = if (dateError && form.errorRes != null) stringResource(form.errorRes) else null,
                )
                CategorySelect(
                    categories = categories,
                    type = form.type,
                    selectedId = form.categoryId,
                    onSelect = { onFormChange(form.copy(categoryId = it, errorRes = null, errorField = null)) },
                    modifier = Modifier
                        .weight(1.5f)
                        .scrollToWhen(categoryError),
                    isError = categoryError,
                    supportingText = if (categoryError && form.errorRes != null) stringResource(form.errorRes) else null,
                )
            }
        }

        // A transfer crossing personal and shared ownership is saved as a contribution or withdrawal.
        val transferCrossesOwnership = form.type == MovementType.TRANSFER &&
            form.destinationAccountId != null &&
            (accounts.firstOrNull { it.id == form.accountId }?.ownershipKind == AccountOwnershipKind.SHARED) !=
            (accounts.firstOrNull { it.id == form.destinationAccountId }?.ownershipKind == AccountOwnershipKind.SHARED)

        // A shared account finances its own expense, unless someone else paid for it outright.
        val fundedBySharedAccount = form.type == MovementType.EXPENSE &&
            form.expenseKind != ExpenseKind.DEBT &&
            accounts.firstOrNull { it.id == form.accountId }?.ownershipKind == AccountOwnershipKind.SHARED

        // Type-specific body. Optional metadata is disclosed below so the required variant fields
        // stay in the primary flow.
        when (form.type) {
            MovementType.EXPENSE -> ExpenseFormSection(
                form = form,
                accounts = accounts,
                people = people,
                fundedBySharedAccount = fundedBySharedAccount,
                onFormChange = onFormChange,
                onSharedToggled = onSharedToggled,
                onSplitEditorChange = onSplitEditorChange,
                onOtherPersonSelected = onOtherPersonSelected,
                onCreatePersonInSplit = onCreatePersonInSplit,
            )
            MovementType.INCOME -> IncomeFormSection(
                form = form,
                accounts = accounts,
                people = people,
                onFormChange = onFormChange,
                onSettlementToggled = onSettlementToggled,
                onSettlementPersonSelected = onSettlementPersonSelected,
                onSharedToggled = onSharedToggled,
                onSplitEditorChange = onSplitEditorChange,
                onOtherPersonSelected = onOtherPersonSelected,
                onCreatePersonInSplit = onCreatePersonInSplit,
            )
            MovementType.TRANSFER -> TransferFormSection(
                form = form,
                accounts = accounts,
                onFormChange = onFormChange,
            )
            else -> Unit
        }

        FormOptionalSection(
            form = form,
            trips = trips,
            tags = tags,
            onFormChange = onFormChange,
            onTripSelected = onTripSelected,
            onTagSelected = onTagSelected,
            onRecurringToggled = onRecurringToggled,
            onRecurringFrequencyChanged = onRecurringFrequencyChanged,
            onOptionalToggled = onOptionalToggled,
            onAdvancedToggled = onAdvancedToggled,
            transferCrossesOwnership = transferCrossesOwnership,
            expenseDetails = if (form.type == MovementType.EXPENSE) {
                {
                    ExpenseDetailsSection(
                        form = form,
                        people = people,
                        fundedBySharedAccount = fundedBySharedAccount,
                        onFormChange = onFormChange,
                        onSharedToggled = onSharedToggled,
                        onSplitEditorChange = onSplitEditorChange,
                        onOtherPersonSelected = onOtherPersonSelected,
                        onCreatePersonInSplit = onCreatePersonInSplit,
                    )
                }
            } else null,
        )
        }
        MovementSaveActions(
            form = form,
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

private const val MovementSheetExpandedHeightFraction = 0.84f

/** Save warnings and actions stay visible below the independently scrolling form body. */
@Composable
private fun MovementSaveActions(
    form: MovementFormState,
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
        // Third choice for the recurring-stop warning (recurrence consistency): the old
        // "just detach" behavior remains alongside the default end-template action.
        if (isRecurrenceStop) {
            TextButton(onClick = onRecurrenceStopUnlink, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.movement_recurring_stop_unlink))
            }
        }
    }
}
