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
 * Fixed 8-color palette drawn from the design-token functional colors.
 * Stored in the DB as hex strings; `categoryColor()` / `accountIcon()` parse them back.
 */
val EntityColorPalette: List<EntityColorOption> = listOf(
    // Original 8 — design-token functional colors
    EntityColorOption("#765077", TokenColor.Plum),
    EntityColorOption("#5E7448", TokenColor.IncomeLight),
    EntityColorOption("#277F7D", TokenColor.RefundLight),
    EntityColorOption("#5F667A", TokenColor.TransferLight),
    EntityColorOption("#A66F1C", TokenColor.SettlementLight),
    EntityColorOption("#A56B00", TokenColor.AlertLight),
    EntityColorOption("#B94E46", TokenColor.DebtLight),
    EntityColorOption("#9E9187", TokenColor.CategoryUncategorized),
    // New additions
    EntityColorOption("#8B5CF6", Color(0xFF8B5CF6)),
    EntityColorOption("#3B82F6", Color(0xFF3B82F6)),
    EntityColorOption("#0D9488", Color(0xFF0D9488)),
    EntityColorOption("#22C55E", Color(0xFF22C55E)),
    EntityColorOption("#84CC16", Color(0xFF84CC16)),
    EntityColorOption("#F97316", Color(0xFFF97316)),
    EntityColorOption("#EC4899", Color(0xFFEC4899)),
    EntityColorOption("#EF4444", Color(0xFFEF4444)),
    EntityColorOption("#A16207", Color(0xFFA16207)),
    EntityColorOption("#374151", Color(0xFF374151)),
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
    // Housing & home
    EntityIconOption("home", categoryIcon("home")),
    EntityIconOption("apartment", categoryIcon("apartment")),
    EntityIconOption("weekend", categoryIcon("weekend")),
    EntityIconOption("kitchen", categoryIcon("kitchen")),
    EntityIconOption("build", categoryIcon("build")),
    EntityIconOption("cleaning_services", categoryIcon("cleaning_services")),
    EntityIconOption("local_laundry_service", categoryIcon("local_laundry_service")),
    // Food & drink
    EntityIconOption("restaurant", categoryIcon("restaurant")),
    EntityIconOption("fastfood", categoryIcon("fastfood")),
    EntityIconOption("local_cafe", categoryIcon("local_cafe")),
    EntityIconOption("local_bar", categoryIcon("local_bar")),
    EntityIconOption("liquor", categoryIcon("liquor")),
    EntityIconOption("cake", categoryIcon("cake")),
    EntityIconOption("icecream", categoryIcon("icecream")),
    // Transport
    EntityIconOption("directions_car", categoryIcon("directions_car")),
    EntityIconOption("train", categoryIcon("train")),
    EntityIconOption("directions_bus", categoryIcon("directions_bus")),
    EntityIconOption("local_taxi", categoryIcon("local_taxi")),
    EntityIconOption("two_wheeler", categoryIcon("two_wheeler")),
    EntityIconOption("directions_bike", categoryIcon("directions_bike")),
    EntityIconOption("directions_boat", categoryIcon("directions_boat")),
    EntityIconOption("flight", categoryIcon("flight")),
    EntityIconOption("local_gas_station", categoryIcon("local_gas_station")),
    EntityIconOption("ev_station", categoryIcon("ev_station")),
    EntityIconOption("local_parking", categoryIcon("local_parking")),
    // Shopping
    EntityIconOption("shopping_cart", categoryIcon("shopping_cart")),
    EntityIconOption("shopping_bag", categoryIcon("shopping_bag")),
    EntityIconOption("local_mall", categoryIcon("local_mall")),
    EntityIconOption("storefront", categoryIcon("storefront")),
    EntityIconOption("checkroom", categoryIcon("checkroom")),
    EntityIconOption("diamond", categoryIcon("diamond")),
    // Health & wellness
    EntityIconOption("local_hospital", categoryIcon("local_hospital")),
    EntityIconOption("medical_services", categoryIcon("medical_services")),
    EntityIconOption("local_pharmacy", categoryIcon("local_pharmacy")),
    EntityIconOption("fitness_center", categoryIcon("fitness_center")),
    EntityIconOption("spa", categoryIcon("spa")),
    EntityIconOption("self_improvement", categoryIcon("self_improvement")),
    // Entertainment & leisure
    EntityIconOption("sports_esports", categoryIcon("sports_esports")),
    EntityIconOption("movie", categoryIcon("movie")),
    EntityIconOption("music_note", categoryIcon("music_note")),
    EntityIconOption("theater_comedy", categoryIcon("theater_comedy")),
    EntityIconOption("nightlife", categoryIcon("nightlife")),
    EntityIconOption("casino", categoryIcon("casino")),
    EntityIconOption("sports_soccer", categoryIcon("sports_soccer")),
    EntityIconOption("sports_basketball", categoryIcon("sports_basketball")),
    EntityIconOption("hiking", categoryIcon("hiking")),
    EntityIconOption("pool", categoryIcon("pool")),
    EntityIconOption("park", categoryIcon("park")),
    EntityIconOption("beach_access", categoryIcon("beach_access")),
    EntityIconOption("hotel", categoryIcon("hotel")),
    EntityIconOption("luggage", categoryIcon("luggage")),
    // Technology
    EntityIconOption("phone_android", categoryIcon("phone_android")),
    EntityIconOption("computer", categoryIcon("computer")),
    EntityIconOption("headphones", categoryIcon("headphones")),
    EntityIconOption("camera_alt", categoryIcon("camera_alt")),
    EntityIconOption("wifi", categoryIcon("wifi")),
    // Education & work
    EntityIconOption("school", categoryIcon("school")),
    EntityIconOption("menu_book", categoryIcon("menu_book")),
    EntityIconOption("science", categoryIcon("science")),
    EntityIconOption("calculate", categoryIcon("calculate")),
    EntityIconOption("work", categoryIcon("work")),
    EntityIconOption("business_center", categoryIcon("business_center")),
    // Finance
    EntityIconOption("payments", categoryIcon("payments")),
    EntityIconOption("savings", categoryIcon("savings")),
    EntityIconOption("euro", categoryIcon("euro")),
    EntityIconOption("receipt", categoryIcon("receipt")),
    // Personal & social
    EntityIconOption("face", categoryIcon("face")),
    EntityIconOption("content_cut", categoryIcon("content_cut")),
    EntityIconOption("child_care", categoryIcon("child_care")),
    EntityIconOption("pets", categoryIcon("pets")),
    EntityIconOption("group", categoryIcon("group")),
    EntityIconOption("volunteer_activism", categoryIcon("volunteer_activism")),
    EntityIconOption("card_giftcard", categoryIcon("card_giftcard")),
    EntityIconOption("celebration", categoryIcon("celebration")),
    EntityIconOption("local_florist", categoryIcon("local_florist")),
    // Utilities
    EntityIconOption("bolt", categoryIcon("bolt")),
    EntityIconOption("water_drop", categoryIcon("water_drop")),
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
            .size(44.dp)
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
            .size(44.dp)
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
