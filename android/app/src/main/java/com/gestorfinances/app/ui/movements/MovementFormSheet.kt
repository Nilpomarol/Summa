@file:OptIn(ExperimentalMaterial3Api::class)

package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.gestorfinances.app.ui.common.PrimaryButton
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.movementTypeIcon
import com.gestorfinances.app.ui.common.parseEuroCents
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import com.gestorfinances.app.ui.theme.categoryColor

@Composable
fun MovementFormSheet(
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
    onAdvancedToggled: () -> Unit,
    onCreatePersonInSplit: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onOverride: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val typeColor = FinanceTheme.colors.amountColor(form.type)

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
                form.id == null -> when (form.type) {
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
            if (form.duplicateWarning) {
                InlineBanner(kind = BannerKind.Alert, text = stringResource(R.string.movement_duplicate_warning))
            }

            MovementTypeSelector(
                selected = form.type,
                onSelect = { onFormChange(form.copy(type = it)) },
            )

            // Concepte
            OutlinedTextField(
                value = form.name,
                onValueChange = { onFormChange(form.copy(name = it, errorRes = null)) },
                label = { Text(stringResource(R.string.movement_field_name)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            // Import
            OutlinedTextField(
                value = form.amount,
                onValueChange = { onFormChange(form.copy(amount = it, errorRes = null)) },
                label = { Text(stringResource(R.string.movement_field_amount)) },
                prefix = { Text(text = "€", color = typeColor) },
                textStyle = MaterialTheme.typography.titleLarge.copy(color = typeColor),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
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
            if (form.type == MovementType.TRANSFER) {
                FormDatePicker(
                    label = stringResource(R.string.movement_field_date),
                    date = form.date,
                    onDateChange = { onFormChange(form.copy(date = it, errorRes = null)) },
                    modifier = Modifier.fillMaxWidth(),
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
                        onDateChange = { onFormChange(form.copy(date = it, errorRes = null)) },
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

            // Type-specific body (each section owns its full run: body + recurring/trip-tag tail).
            when (form.type) {
                MovementType.EXPENSE -> ExpenseFormSection(
                    form = form,
                    accounts = accounts,
                    people = people,
                    trips = trips,
                    tags = tags,
                    onFormChange = onFormChange,
                    onSharedToggled = onSharedToggled,
                    onSplitEditorChange = onSplitEditorChange,
                    onOtherPersonSelected = onOtherPersonSelected,
                    onCreatePersonInSplit = onCreatePersonInSplit,
                    onRecurringToggled = onRecurringToggled,
                    onRecurringFrequencyChanged = onRecurringFrequencyChanged,
                    onTripSelected = onTripSelected,
                    onTagSelected = onTagSelected,
                )
                MovementType.INCOME -> IncomeFormSection(
                    form = form,
                    accounts = accounts,
                    people = people,
                    trips = trips,
                    tags = tags,
                    onFormChange = onFormChange,
                    onSettlementToggled = onSettlementToggled,
                    onSettlementPersonSelected = onSettlementPersonSelected,
                    onRecurringToggled = onRecurringToggled,
                    onRecurringFrequencyChanged = onRecurringFrequencyChanged,
                    onTripSelected = onTripSelected,
                    onTagSelected = onTagSelected,
                )
                MovementType.TRANSFER -> TransferFormSection(
                    form = form,
                    accounts = accounts,
                    onFormChange = onFormChange,
                    onRecurringToggled = onRecurringToggled,
                    onRecurringFrequencyChanged = onRecurringFrequencyChanged,
                )
                else -> Unit
            }

            if (form.expenseKind != ExpenseKind.DEBT) {
                FormAdvancedSection(
                    form = form,
                    onFormChange = onFormChange,
                    onToggle = onAdvancedToggled,
                )
            }

            PrimaryButton(
                text = when {
                    form.duplicateWarning -> stringResource(R.string.movement_duplicate_override)
                    form.id == null -> stringResource(R.string.movement_save_new)
                    else -> stringResource(R.string.movement_save_changes)
                },
                onClick = if (form.duplicateWarning) onOverride else onSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
