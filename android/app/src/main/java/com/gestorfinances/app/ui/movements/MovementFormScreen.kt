package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.PersonSummary
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.ui.common.BannerKind
import com.gestorfinances.app.ui.common.FinanceFilterChip
import com.gestorfinances.app.ui.common.InlineBanner
import com.gestorfinances.app.ui.common.PageHeaderRow
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.movementTypeIcon
import com.gestorfinances.app.ui.common.nextFieldKeyboardActions
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
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
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOverride: () -> Unit,
    onDataLossOverride: () -> Unit,
    onRecurrenceStopEnd: () -> Unit,
    onRecurrenceStopUnlink: () -> Unit,
    onWarningDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeColor = FinanceTheme.colors.amountColor(form.type)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            MovementSaveBar(
                form = form,
                onSave = onSave,
                onOverride = onOverride,
                onDataLossOverride = onDataLossOverride,
                onRecurrenceStopEnd = onRecurrenceStopEnd,
                onRecurrenceStopUnlink = onRecurrenceStopUnlink,
                onWarningDismissed = onWarningDismissed,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        PageHeaderRow(onBack = onBack)

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
                MovementType.EXTERNAL_EXPENSE -> {
                    if (selectedCategory != null) {
                        categoryIcon(selectedCategory.icon) to finance.debt
                    } else {
                        movementTypeIcon(form.type) to finance.debt
                    }
                }
            }
        }

        val amountCents = remember(form.amount) {
            parseEuroCents(form.amount, allowNegative = false) ?: 0L
        }

        val titleText = when {
            form.isNew -> when (form.type) {
                MovementType.EXPENSE -> "Nova despesa"
                MovementType.INCOME -> "Nou ingrés"
                MovementType.TRANSFER -> "Nova transferència"
                else -> "Nou moviment"
            }
            else -> form.name.ifBlank { "Edita moviment" }
        }

        MovementSheetHeader(
            title = titleText,
            amountCents = amountCents,
            type = form.type,
            icon = icon,
            iconColor = iconColor,
        )

        // Top-of-form text is reserved for save/repository failures (errorMessage) -- field-level
        // validation errors (errorRes) render next to the offending control instead (audit U8,
        // `docs/17` WP2), and the duplicate/data-loss warnings render next to the Save button below.
        form.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        MovementTypeSelector(
            selected = form.type,
            onSelect = { onFormChange(form.copy(type = it)) },
        )

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

        // Import
        val amountError = form.errorField == MovementFormField.AMOUNT
        OutlinedTextField(
            value = form.amount,
            onValueChange = { onFormChange(form.copy(amount = it, errorRes = null, errorField = null)) },
            label = { Text(stringResource(R.string.movement_field_amount)) },
            prefix = { Text(text = "€", color = typeColor) },
            textStyle = MaterialTheme.typography.titleLarge.copy(color = typeColor),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = doneKeyboardActions(),
            singleLine = true,
            isError = amountError,
            supportingText = if (amountError && form.errorRes != null) {
                { Text(stringResource(form.errorRes)) }
            } else null,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .scrollToWhen(amountError),
        )
        // Type 4 (Debt): the amount is what the user owes — surface that affordance.
        if (form.type == MovementType.EXPENSE && form.expenseKind == ExpenseKind.DEBT) {
            Text(
                text = stringResource(R.string.movement_debt_amount_help),
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }

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

        // Read-only auto-categorization hint (audit F1): tap to apply, never auto-applied.
        if (form.suggestedCategoryId != null && form.suggestedCategoryId != form.categoryId) {
            val suggestedCategory = remember(form.suggestedCategoryId, categories) {
                categories.firstOrNull { it.id == form.suggestedCategoryId }
            }
            suggestedCategory?.let { category ->
                FinanceFilterChip(
                    selected = false,
                    label = stringResource(R.string.movement_category_suggestion, category.name),
                    onClick = { onFormChange(form.copy(categoryId = category.id)) },
                )
            }
        }

        // Type-specific body. Optional metadata is disclosed below so the required variant fields
        // stay in the primary flow.
        when (form.type) {
            MovementType.EXPENSE -> ExpenseFormSection(
                form = form,
                accounts = accounts,
                people = people,
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
        )
        }
    }
}

/**
 * The save action is part of the focused page chrome, not the scrolling form. Keeping this as a
 * Scaffold bottom bar reserves its measured height and applies IME/navigation insets, so the
 * action remains reachable while the keyboard is open without adding a second save path.
 */
@Composable
private fun MovementSaveBar(
    form: MovementFormState,
    onSave: () -> Unit,
    onOverride: () -> Unit,
    onDataLossOverride: () -> Unit,
    onRecurrenceStopEnd: () -> Unit,
    onRecurrenceStopUnlink: () -> Unit,
    onWarningDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasWarning = form.duplicateWarning || form.pendingDataLossWarning != null
    val isRecurrenceStop = form.pendingDataLossWarning == DataLossWarning.RECURRING_STOP
    val warningText = when (form.pendingDataLossWarning) {
        DataLossWarning.SPLIT_REMOVED -> stringResource(R.string.movement_warning_split_removed)
        DataLossWarning.PAYER_SWITCH -> stringResource(R.string.movement_warning_payer_switch_drops_fields)
        DataLossWarning.RECURRING_STOP -> stringResource(R.string.movement_recurring_stop_title)
        null -> stringResource(R.string.movement_duplicate_warning)
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
                    form.pendingDataLossWarning != null -> onDataLossOverride
                    form.duplicateWarning -> onOverride
                    else -> onSave
                },
                modifier = Modifier.fillMaxWidth(),
            )
            // Third choice for the recurring-stop warning (audit F12/`docs/17` WP3): the old
            // "just detach" behavior remains alongside the default end-template action.
            if (isRecurrenceStop) {
                TextButton(onClick = onRecurrenceStopUnlink, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.movement_recurring_stop_unlink))
                }
            }
        }
    }
}
