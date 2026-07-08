@file:OptIn(ExperimentalMaterial3Api::class)

package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.data.repository.AccountSummary
import com.gestorfinances.app.data.repository.CategoryKind
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Colored type selector: each segment carries its semantic money color.
 */
@Composable
internal fun MovementTypeSelector(
    selected: MovementType,
    onSelect: (MovementType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.movement_field_type),
            style = MaterialTheme.typography.labelMedium,
            color = FinanceTheme.colors.mutedText,
        )
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
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun AccountSelect(
    label: String,
    selectedId: String?,
    accounts: List<AccountSummary>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
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
    )
}

@Composable
internal fun CategorySelect(
    categories: List<CategoryRecord>,
    type: MovementType,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember(categories, type) {
        categories.filter { cat ->
            when (type) {
                MovementType.EXPENSE, MovementType.EXTERNAL_EXPENSE -> cat.kind == CategoryKind.EXPENSE || cat.kind == CategoryKind.BOTH
                MovementType.INCOME -> cat.kind == CategoryKind.INCOME || cat.kind == CategoryKind.BOTH
                else -> false
            }
        }
    }
    val noCategory = stringResource(R.string.common_no_category)
    FormSelect(
        label = stringResource(R.string.movement_field_category),
        options = buildList {
            add(SelectOption(id = null, label = noCategory))
            options.forEach { cat ->
                add(
                    SelectOption(
                        id = cat.id,
                        label = cat.name,
                        leading = {
                            IconChip(
                                icon = categoryIcon(cat.icon),
                                contentDescription = null,
                                color = categoryColor(cat.color),
                                size = 24.dp,
                            )
                        },
                    ),
                )
            }
        },
        selectedId = selectedId,
        onSelect = onSelect,
        placeholder = noCategory,
        modifier = modifier,
    )
}

@Composable
internal fun FormDatePicker(
    label: String,
    date: String,
    onDateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    val displayText = remember(date) {
        runCatching {
            LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }.getOrDefault(date)
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
    ) {
        Text(
            text = displayText,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
        )
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
) {
    FormToggleRow(
        label = stringResource(R.string.movement_field_recurring),
        checked = isRecurring,
        onCheckedChange = onToggle,
    )
    if (isRecurring) {
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

@Composable
internal fun FormTripTagSection(
    trips: List<TripSummary>,
    tags: List<TagSummary>,
    tripId: String?,
    tagId: String?,
    onTripSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
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
            )
        }
    }
}

@Composable
internal fun FormAdvancedSection(
    form: MovementFormState,
    onFormChange: (MovementFormState) -> Unit,
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
            text = stringResource(R.string.movement_form_advanced),
            style = MaterialTheme.typography.labelMedium,
            color = FinanceTheme.colors.mutedText,
        )
        Icon(
            imageVector = if (form.showAdvanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = FinanceTheme.colors.mutedText,
            modifier = Modifier.size(14.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = FinanceTheme.colors.cardBorder)
    }

    if (form.showAdvanced) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = form.notes,
                onValueChange = { onFormChange(form.copy(notes = it)) },
                label = { Text(stringResource(R.string.movement_field_notes)) },
                minLines = 2,
                shape = MaterialTheme.shapes.small,
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

@Composable
internal fun MovementSheetHeader(
    title: String,
    amountCents: Long,
    type: MovementType,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
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
                color = FinanceTheme.colors.amountColor(type),
                style = MaterialTheme.typography.headlineMedium,
                signed = type == MovementType.INCOME || type == MovementType.SETTLEMENT
            )
        }
    }
}

@Composable
internal fun DetailGridItem(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
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
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = FinanceTheme.colors.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun FullWidthDetailItem(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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
}

