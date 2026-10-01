@file:OptIn(ExperimentalMaterial3Api::class)

package com.gestorfinances.app.ui.movements

import com.gestorfinances.app.data.repository.AccountOwnershipKind
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.gestorfinances.app.data.repository.CategoryRecord
import com.gestorfinances.app.data.repository.MovementType
import com.gestorfinances.app.data.repository.supports
import com.gestorfinances.app.data.repository.TagSummary
import com.gestorfinances.app.data.repository.TripSummary
import com.gestorfinances.app.domain.rules.RecurrenceFrequency
import com.gestorfinances.app.ui.common.FinanceSwitch
import com.gestorfinances.app.ui.common.IconChip
import com.gestorfinances.app.ui.common.IdentityIconTile
import com.gestorfinances.app.ui.common.MoneyText
import com.gestorfinances.app.ui.common.categoryIcon
import com.gestorfinances.app.ui.common.inPickerHierarchyOrder
import com.gestorfinances.app.ui.common.formatCompactDate
import com.gestorfinances.app.ui.common.scrollToWhen
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.amountColor
import com.gestorfinances.app.ui.theme.categoryColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A labelled switch on one row. */
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
        options = accountOptions(accounts),
        selectedId = selectedId,
        onSelect = { id -> id?.let(onSelect) },
        modifier = modifier,
        isError = isError,
        supportingText = supportingText,
    )
}

@Composable
internal fun accountOptions(accounts: List<AccountSummary>): List<SelectOption> {
    val sharedBadge = stringResource(R.string.account_shared_badge)
    return accounts.map { account ->
        SelectOption(
            id = account.id,
            // A shared account changes what the form asks, so it says so before it is chosen.
            label = if (account.ownershipKind == AccountOwnershipKind.SHARED) {
                "${account.name} · $sharedBadge"
            } else {
                account.name
            },
            leading = { ColorDot(colorHex = account.color) },
        )
    }
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
internal fun buildCategorySelectOptions(
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
        LocalDateDialog(date = date, onDateChange = onDateChange, onDismiss = { showPicker = false })
    }
}

/** Material date picker for a local `YYYY-MM-DD` [date]; today when it is blank or invalid. */
@Composable
internal fun LocalDateDialog(
    date: String,
    onDateChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = remember(date) {
        runCatching {
            LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    val ld = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onDateChange(ld.toString())
                }
                onDismiss()
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    ) {
        DatePicker(state = pickerState)
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

internal val formMovementTypes = listOf(
    MovementType.EXPENSE,
    MovementType.INCOME,
    MovementType.TRANSFER,
)

/** Recurring templates additionally schedule settlements, which the movement form never creates. */
internal val recurringTemplateTypes = formMovementTypes + MovementType.SETTLEMENT

@Composable
internal fun MovementType.formLabel(): String = when (this) {
    MovementType.EXPENSE -> stringResource(R.string.movement_type_expense)
    MovementType.INCOME -> stringResource(R.string.movement_type_income)
    MovementType.TRANSFER -> stringResource(R.string.movement_type_transfer)
    MovementType.SETTLEMENT -> stringResource(R.string.movement_type_settlement)
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
        IdentityIconTile(icon = icon, color = iconColor, size = 48.dp)
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
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // One neutral treatment for every field: the header tile already carries the identity.
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(FinanceTheme.colors.progressTrack, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FinanceTheme.colors.mutedText,
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

/** A titled (or untitled) group of [DetailRow]s, hairline-divided, straight on the sheet. */
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
        Column {
            rows.forEachIndexed { index, item ->
                DetailRow(
                    icon = item.icon,
                    label = item.label,
                    value = item.value,
                )
                if (index != rows.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 48.dp), color = FinanceTheme.colors.cardBorder)
                }
            }
        }
    }
}

