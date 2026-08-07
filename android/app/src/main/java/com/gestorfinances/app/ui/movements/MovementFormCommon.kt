@file:OptIn(ExperimentalMaterial3Api::class)

package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.supports
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TemplateStatus
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.ui.common.FinanceCard
import com.gestorfinances.app.ui.common.FinanceSwitch
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.doneKeyboardActions
import com.gestorfinances.app.ui.common.inPickerHierarchyOrder
import com.gestorfinances.app.ui.common.nextFieldKeyboardActions
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import com.gestorfinances.app.ui.theme.asFigures
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Colored type selector: each segment carries its semantic money color.
 */
@Composable
internal fun MovementTypeSelector(
    selected: MovementType,
    onSelect: (MovementType) -> Unit,
    showLabel: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showLabel) {
            Text(
                text = stringResource(R.string.movement_field_type),
                style = MaterialTheme.typography.labelMedium,
                color = FinanceTheme.colors.mutedText,
            )
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
        ) {
            Row(
                modifier = Modifier.padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                formMovementTypes.forEach { type ->
                    val isSelected = type == selected
                    val color = FinanceTheme.colors.amountColor(type)
                    Surface(
                        onClick = { onSelect(type) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (isSelected) color.copy(alpha = 0.12f) else Color.Transparent,
                        contentColor = if (isSelected) color else FinanceTheme.colors.mutedText,
                    ) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = type.formLabel(),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FormToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = androidx.compose.ui.semantics.Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        FinanceSwitch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun AccountSelect(
    label: String,
    selectedId: String?,
    accounts: List<AccountSummary>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    FormSelect(
        label = label,
        options = accounts.map { account ->
            SelectOption(
                id = account.id,
                label = account.name,
                leading = { ColorDot(colorHex = account.color) },
            )
        },
        selectedId = selectedId,
        onSelect = { id -> id?.let(onSelect) },
        modifier = modifier,
        isError = isError,
        supportingText = supportingText,
    )
}

@Composable
internal fun CategorySelect(
    categories: List<CategoryRecord>,
    type: MovementType,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    val noCategory = stringResource(R.string.common_no_category)
    val options = remember(categories, type, noCategory) {
        val compatible = categories.filter { it.supports(type) }
        buildCategorySelectOptions(compatible, noCategory)
    }
    FormSelect(
        label = stringResource(R.string.movement_field_category),
        options = options,
        selectedId = selectedId,
        onSelect = onSelect,
        placeholder = noCategory,
        modifier = modifier,
        isError = isError,
        supportingText = supportingText,
    )
}

/**
 * Builds the movement/template category picker options as a two-level tree: a parent that has
 * children (a container) becomes a **non-selectable** header, and its children are the pickable,
 * indented leaves beneath it. Leaves and childless top-level categories are selectable directly.
 * A movement is always posted to a leaf, never to a container.
 */
private fun buildCategorySelectOptions(
    compatible: List<CategoryRecord>,
    noCategoryLabel: String,
): List<SelectOption> {
    val presentIds = compatible.mapTo(HashSet()) { it.id }
    // A top-level category that has ≥1 child in this set is a container: it becomes a
    // non-selectable header so movements are only ever posted to a leaf.
    val containerIds = compatible.mapNotNull { it.parentId }.filterTo(HashSet()) { it in presentIds }
    return buildList {
        add(SelectOption(id = null, label = noCategoryLabel))
        compatible.inPickerHierarchyOrder().forEach { (cat, indented) ->
            add(
                SelectOption(
                    id = cat.id,
                    label = cat.name,
                    leading = categorySelectLeading(cat),
                    enabled = cat.id !in containerIds,
                    indented = indented,
                ),
            )
        }
    }
}

private fun categorySelectLeading(cat: CategoryRecord): @Composable () -> Unit = {
    IconChip(
        icon = categoryIcon(cat.icon),
        contentDescription = null,
        color = categoryColor(cat.color),
        size = 24.dp,
    )
}

@Composable
internal fun FormDatePicker(
    label: String,
    date: String,
    onDateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    /** Non-null only for optional date fields, such as a budget start date: shows a
     * trailing clear icon while [date] is non-blank. Required date fields never pass this. */
    onClear: (() -> Unit)? = null,
) {
    var showPicker by remember { mutableStateOf(false) }

    val displayText = remember(date) {
        if (date.isBlank()) {
            null
        } else {
            formatCompactDate(date)
        }
    }

    FieldFrame(
        label = label,
        focused = false,
        modifier = modifier,
        surfaceModifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = { showPicker = true },
        ),
        isError = isError,
        supportingText = supportingText,
    ) {
        Text(
            text = displayText ?: stringResource(R.string.common_no_date),
            modifier = Modifier.weight(1f),
            color = if (displayText == null) FinanceTheme.colors.mutedText else Color.Unspecified,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
        )
        if (onClear != null && displayText != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.common_clear),
                    tint = FinanceTheme.colors.mutedText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.CalendarMonth,
            contentDescription = null,
            tint = FinanceTheme.colors.mutedText,
            modifier = Modifier.size(18.dp),
        )
    }

    if (showPicker) {
        val initialMillis = remember(date) {
            runCatching {
                LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrDefault(System.currentTimeMillis())
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val ld = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateChange(ld.toString())
                    }
                    showPicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
internal fun FormRecurringSection(
    isRecurring: Boolean,
    frequency: RecurrenceFrequency,
    onToggle: (Boolean) -> Unit,
    onFrequencyChange: (RecurrenceFrequency) -> Unit,
    /** True when this movement is linked to an existing template (`form.templateId != null`,
     * recurrence consistency): the frequency is the template's truth, not this form's, so it's
     * shown as a muted read-only line instead of an editable select -- editing it here would
     * either silently do nothing or misrepresent what "changing" it actually means. No navigation
     * to the template editor from this form; the label alone is
     * honest and sufficient. */
    linked: Boolean,
    /** The linked template's real status (only meaningful when [linked]) -- ending a template
     * doesn't unlink prior movements (`RecurringViewModel.onEndConfirmed`), so a linked movement
     * can point at an ENDED template. Showing the same "gestionat a Recurrents" line in that case
     * would misrepresent a dead series as an ongoing one -- exactly what F12 exists to prevent. */
    templateStatus: TemplateStatus? = null,
) {
    FormToggleRow(
        label = stringResource(R.string.movement_field_recurring),
        checked = isRecurring,
        onCheckedChange = onToggle,
    )
    if (isRecurring) {
        if (linked && templateStatus == TemplateStatus.ENDED) {
            Text(
                text = stringResource(R.string.movement_recurring_managed_ended),
                style = MaterialTheme.typography.bodyMedium,
                color = FinanceTheme.colors.mutedText,
            )
        } else if (linked) {
            Text(
                text = stringResource(R.string.movement_recurring_managed, frequency.cadenceLabel()),
                style = MaterialTheme.typography.bodyMedium,
                color = FinanceTheme.colors.mutedText,
            )
        } else {
            val options = listOf(
                RecurrenceFrequency.WEEKLY,
                RecurrenceFrequency.FORTNIGHTLY,
                RecurrenceFrequency.MONTHLY,
                RecurrenceFrequency.YEARLY,
            )
            FormSelect(
                label = stringResource(R.string.movement_recurring_frequency),
                options = options.map { SelectOption(id = it.name, label = it.cadenceLabel()) },
                selectedId = frequency.name,
                onSelect = { id -> id?.let { onFrequencyChange(RecurrenceFrequency.valueOf(it)) } },
            )
        }
    }
}

@Composable
internal fun FormTripTagSection(
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    tripId: String?,
    tagId: String?,
    onTripSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
    isTagError: Boolean = false,
    tagErrorText: String? = null,
) {
    val noTrip = stringResource(R.string.movement_no_trip)
    FormSelect(
        label = stringResource(R.string.movement_field_trip),
        options = buildList {
            add(SelectOption(id = null, label = noTrip))
            trips.forEach { add(SelectOption(id = it.id, label = it.name)) }
        },
        selectedId = tripId,
        onSelect = onTripSelected,
        placeholder = noTrip,
    )
    if (tripId != null) {
        val trip = trips.firstOrNull { it.id == tripId }
        val tagOptions = tags.filter { it.supportsTrip(trip) }
        if (tagOptions.isNotEmpty()) {
            val noTag = stringResource(R.string.tag_picker_none)
            FormSelect(
                label = stringResource(R.string.movement_field_tag),
                options = buildList {
                    add(SelectOption(id = null, label = noTag))
                    tagOptions.forEach { add(SelectOption(id = it.id, label = it.name)) }
                },
                selectedId = tagId,
                onSelect = onTagSelected,
                placeholder = noTag,
                modifier = Modifier.scrollToWhen(isTagError),
                isError = isTagError,
                supportingText = tagErrorText,
            )
        }
    }
}

/**
 * Optional movement metadata is collapsed in the primary flow. A selected trip, recurrence, or
 * advanced value auto-expands this section when an existing movement is edited, so editing never
 * hides data that is already present. Required variant fields remain in their type section above.
 */
@Composable
internal fun FormOptionalSection(
    form: MovementFormState,
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    onFormChange: (MovementFormState) -> Unit,
    onTripSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
    onRecurringToggled: (Boolean) -> Unit,
    onRecurringFrequencyChanged: (RecurrenceFrequency) -> Unit,
    onOptionalToggled: () -> Unit,
    onAdvancedToggled: () -> Unit,
    expenseDetails: (@Composable () -> Unit)? = null,
) {
    FormDisclosureRow(
        label = stringResource(R.string.movement_form_optional),
        expanded = form.showOptional,
        onToggle = onOptionalToggled,
    )
    if (!form.showOptional) return

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Sharing and claiming money are expense-specific, but not part of the everyday
        // expense path. Keeping them here lets every movement type retain the same compact
        // primary body height without hiding the capability.
        expenseDetails?.invoke()

        val recurringAllowed = when (form.type) {
            MovementType.EXPENSE -> form.expenseKind != ExpenseKind.DEBT
            MovementType.INCOME -> !form.isSettlement
            MovementType.TRANSFER -> true
            else -> false
        }
        if (recurringAllowed) {
            FormRecurringSection(
                isRecurring = form.isRecurring,
                frequency = form.recurringFrequency,
                linked = form.templateId != null,
                templateStatus = form.templateStatus,
                onToggle = onRecurringToggled,
                onFrequencyChange = onRecurringFrequencyChanged,
            )
        }

        if (form.type == MovementType.EXPENSE || (form.type == MovementType.INCOME && !form.isSettlement)) {
            FormTripTagSection(
                trips = trips,
                tags = tags,
                tripId = form.tripId,
                tagId = form.tagId,
                onTripSelected = onTripSelected,
                onTagSelected = onTagSelected,
                isTagError = form.errorField == MovementFormField.TAG,
                tagErrorText = if (form.errorField == MovementFormField.TAG && form.errorRes != null) {
                    stringResource(form.errorRes)
                } else null,
            )
        }

        if (form.expenseKind != ExpenseKind.DEBT) {
            FormAdvancedSection(
                form = form,
                onFormChange = onFormChange,
                onToggle = onAdvancedToggled,
            )
        }
    }
}

@Composable
private fun FormDisclosureRow(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggle,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = FinanceTheme.colors.cardBorder)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FinanceTheme.colors.mutedText,
        )
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = FinanceTheme.colors.mutedText,
            modifier = Modifier.size(14.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = FinanceTheme.colors.cardBorder)
    }
}

@Composable
internal fun FormAdvancedSection(
    form: MovementFormState,
    onFormChange: (MovementFormState) -> Unit,
    onToggle: () -> Unit,
) {
    FormDisclosureRow(
        label = stringResource(R.string.movement_form_advanced),
        expanded = form.showAdvanced,
        onToggle = onToggle,
    )

    if (form.showAdvanced) {
        Column(
            modifier = Modifier.scrollToWhen(form.showAdvanced),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = doneKeyboardActions(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal val formMovementTypes = listOf(
    MovementType.EXPENSE,
    MovementType.INCOME,
    MovementType.TRANSFER,
)

@Composable
internal fun MovementType.formLabel(): String = when (this) {
    MovementType.EXPENSE -> stringResource(R.string.movement_type_expense)
    MovementType.INCOME -> stringResource(R.string.movement_type_income)
    MovementType.TRANSFER -> stringResource(R.string.movement_type_transfer)
    else -> ""
}

@Composable
internal fun RecurrenceFrequency.cadenceLabel(): String = when (this) {
    RecurrenceFrequency.WEEKLY -> stringResource(R.string.recurring_cadence_weekly)
    RecurrenceFrequency.FORTNIGHTLY -> stringResource(R.string.recurring_cadence_fortnightly)
    RecurrenceFrequency.MONTHLY -> stringResource(R.string.recurring_cadence_monthly)
    RecurrenceFrequency.YEARLY -> stringResource(R.string.recurring_cadence_yearly)
    RecurrenceFrequency.CUSTOM -> stringResource(R.string.recurring_cadence_custom)
}

internal fun TagSummary.supportsTrip(trip: TripSummary?): Boolean =
    trip != null && (this.tripId == trip.id || (this.tripId == null && (this.tripType == null || this.tripType == trip.type)))

/**
 * Editable hero amount for the movement form: icon chip + title + a large in-place euro input.
 * This replaces the former read-only preview plus a separate full-width "Import" field so the
 * amount is entered once, as the visual focal point. [isError]/[supportingText] mirror the
 * `OutlinedTextField` contract the old field used (amount validation). The read-only
 * [MovementSheetHeader] stays for the movement detail screen.
 */
@Composable
internal fun MovementAmountHeader(
    title: String,
    amount: String,
    onAmountChange: (String) -> Unit,
    type: MovementType,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    amountColor: Color = FinanceTheme.colors.amountColor(type),
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon = icon, contentDescription = null, color = iconColor, size = 48.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val figureStyle = MaterialTheme.typography.headlineMedium.asFigures()
                BasicTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    singleLine = true,
                    textStyle = figureStyle.copy(color = amountColor),
                    cursorBrush = SolidColor(amountColor),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = doneKeyboardActions(),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(text = "€", style = figureStyle, color = amountColor)
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (amount.isEmpty()) {
                                    Text(
                                        text = "0,00",
                                        style = figureStyle,
                                        color = FinanceTheme.colors.mutedText,
                                    )
                                }
                                innerTextField()
                            }
                        }
                    },
                )
                HorizontalDivider(
                    thickness = 1.5.dp,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
        if (isError && !supportingText.isNullOrEmpty()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 64.dp),
            )
        }
    }
}

@Composable
internal fun MovementSheetHeader(
    title: String,
    amountCents: Long,
    type: MovementType,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    amountColor: Color = FinanceTheme.colors.amountColor(type),
    /** Total cost caption shown under the amount -- shared/external expenses only. The header
     * amount is the user's own share, so the total needs calling
     * out separately or it reads as the full cost. */
    totalCaption: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconChip(
            icon = icon,
            contentDescription = null,
            color = iconColor,
            size = 48.dp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            MoneyText(
                cents = amountCents,
                color = amountColor,
                style = MaterialTheme.typography.headlineMedium,
                signed = type == MovementType.INCOME || type == MovementType.SETTLEMENT
            )
            totalCaption?.let {
                Text(
                    text = it,
                    color = FinanceTheme.colors.mutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

internal data class GridItemData(
    val icon: ImageVector,
    val iconColor: Color,
    val label: String,
    val value: String,
)

/**
 * One row inside a [DetailGroupCard]: icon chip + label/value pair. Replaces the former
 * per-field bordered tiles (form-group consolidation) -- rows now share one card per group instead of each
 * field getting its own border, so related fields read as a set.
 */
@Composable
internal fun DetailRow(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = FinanceTheme.colors.mutedText
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** A titled (or untitled) card grouping several [DetailRow]s, hairline-divided. */
@Composable
internal fun DetailGroupCard(
    rows: List<GridItemData>,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    if (rows.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            Text(
                text = it,
                color = FinanceTheme.colors.mutedText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        FinanceCard {
            rows.forEachIndexed { index, item ->
                DetailRow(
                    icon = item.icon,
                    iconColor = item.iconColor,
                    label = item.label,
                    value = item.value,
                )
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = FinanceTheme.colors.cardBorder)
                }
            }
        }
    }
}

