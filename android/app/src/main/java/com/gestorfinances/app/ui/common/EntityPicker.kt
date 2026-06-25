package com.gestorfinances.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.R
import com.gestorfinances.app.ui.theme.FinanceTheme
import com.gestorfinances.app.ui.theme.TokenColor

// ---------------------------------------------------------------------------
// Color palette
// ---------------------------------------------------------------------------

/** A selectable color option: the hex stored in the DB + the resolved Color for rendering. */
data class EntityColorOption(val hex: String, val color: Color)

/**
 * Fixed 8-color palette drawn from the design-token functional colors (docs/08 §2.4).
 * Stored in the DB as hex strings; `categoryColor()` / `accountIcon()` parse them back.
 */
val EntityColorPalette: List<EntityColorOption> = listOf(
    EntityColorOption("#3344E0", TokenColor.Indigo),
    EntityColorOption("#1F8F5F", TokenColor.IncomeLight),
    EntityColorOption("#128A93", TokenColor.RefundLight),
    EntityColorOption("#5B6B86", TokenColor.TransferLight),
    EntityColorOption("#B9772A", TokenColor.SettlementLight),
    EntityColorOption("#C98A14", TokenColor.AlertLight),
    EntityColorOption("#CC4B4B", TokenColor.DebtLight),
    EntityColorOption("#9097A3", TokenColor.CategoryUncategorized),
)

// ---------------------------------------------------------------------------
// Icon palettes
// ---------------------------------------------------------------------------

/** A selectable icon option: the key stored in the DB + the ImageVector for rendering. */
data class EntityIconOption(val key: String, val icon: ImageVector)

val AccountIconPalette: List<EntityIconOption> = listOf(
    EntityIconOption("account_balance", accountIcon("account_balance")),
    EntityIconOption("payments", accountIcon("payments")),
    EntityIconOption("savings", accountIcon("savings")),
    EntityIconOption("trending_up", accountIcon("trending_up")),
    EntityIconOption("credit_card", accountIcon("credit_card")),
    EntityIconOption("wallet", accountIcon("wallet")),
    EntityIconOption("business", accountIcon("business")),
    EntityIconOption("receipt_long", accountIcon("receipt_long")),
)

val CategoryIconPalette: List<EntityIconOption> = listOf(
    EntityIconOption("home", categoryIcon("home")),
    EntityIconOption("shopping_cart", categoryIcon("shopping_cart")),
    EntityIconOption("restaurant", categoryIcon("restaurant")),
    EntityIconOption("directions_car", categoryIcon("directions_car")),
    EntityIconOption("sports_esports", categoryIcon("sports_esports")),
    EntityIconOption("payments", categoryIcon("payments")),
    EntityIconOption("savings", categoryIcon("savings")),
    EntityIconOption("school", categoryIcon("school")),
    EntityIconOption("local_hospital", categoryIcon("local_hospital")),
    EntityIconOption("flight", categoryIcon("flight")),
    EntityIconOption("phone_android", categoryIcon("phone_android")),
    EntityIconOption("fitness_center", categoryIcon("fitness_center")),
)

// ---------------------------------------------------------------------------
// Color picker
// ---------------------------------------------------------------------------

/**
 * Labeled row of color swatches + a custom-color slot that opens a visual HSV picker.
 * Selecting a palette swatch calls [onSelect] immediately with the palette hex. The custom
 * slot opens an HSV color picker that calls [onSelect] live as the user drags. Passing a
 * [selectedHex] not in the palette automatically opens the picker seeded from that color.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerRow(
    label: String,
    selectedHex: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCustom = selectedHex != null &&
        EntityColorPalette.none { it.hex.equals(selectedHex, ignoreCase = true) }

    // Open picker when the current selection is custom; toggle on custom-swatch tap.
    var showColorPicker by remember(isCustom) { mutableStateOf(isCustom) }

    // Snapshot of selectedHex at the moment the picker opens; used as the seed for the
    // library controller's initial color. Stable while dragging (no-key remember inside picker).
    val pickerSeedHex = remember(showColorPicker) { if (showColorPicker) selectedHex else null }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FinanceTheme.colors.mutedText,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EntityColorPalette.forEach { option ->
                ColorSwatch(
                    option = option,
                    selected = option.hex.equals(selectedHex, ignoreCase = true),
                    onSelect = { onSelect(option.hex) },
                )
            }
            // Custom slot — palette icon when no custom color is active
            CustomColorSwatch(
                customColor = if (isCustom) parseHexOrNull(selectedHex) else null,
                active = showColorPicker || isCustom,
                onClick = { showColorPicker = !showColorPicker },
            )
        }

        if (showColorPicker) {
            HsvColorPicker(
                initialHex = pickerSeedHex,
                onSelect = onSelect,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    option: EntityColorOption,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(modifier = Modifier.size(30.dp).border(2.dp, option.color, CircleShape))
        }
        Box(
            modifier = Modifier
                .size(if (selected) 18.dp else 24.dp)
                .background(option.color, CircleShape),
        )
    }
}

@Composable
private fun CustomColorSwatch(
    customColor: Color?,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .then(
                if (customColor == null) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (customColor != null) {
            if (active) {
                Box(modifier = Modifier.size(30.dp).border(2.dp, customColor, CircleShape))
            }
            Box(
                modifier = Modifier
                    .size(if (active) 18.dp else 24.dp)
                    .background(customColor, CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Palette,
                contentDescription = stringResource(R.string.entity_color_custom),
                tint = if (active) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Icon picker
// ---------------------------------------------------------------------------

/**
 * Labeled grid of icon options. The selected icon chip uses a primary-tinted background
 * and a primary-colored border. Passes the key string back on selection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPickerRow(
    label: String,
    options: List<EntityIconOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FinanceTheme.colors.mutedText,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                IconPickerChip(
                    option = option,
                    selected = option.key == selectedKey,
                    onSelect = { onSelect(option.key) },
                )
            }
        }
    }
}

@Composable
private fun IconPickerChip(
    option: EntityIconOption,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.size(44.dp),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.key,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun parseHexOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val s = hex.trim().let { if (it.startsWith("#")) it else "#$it" }
    return runCatching { Color(android.graphics.Color.parseColor(s)) }.getOrNull()
}
