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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.ui.theme.FinanceTheme

// ---------------------------------------------------------------------------
// Color palette
// ---------------------------------------------------------------------------

/** A selectable color option: the hex stored in the DB + the resolved Color for rendering. */
data class EntityColorOption(val hex: String, val color: Color)

/**
 * The curated identity palette: 24 muted, earthy hues in hue order, filling a 3 x 8 grid. Each is
 * dark enough for a white icon and lifted automatically on dark surfaces. Stored in the DB as hex strings;
 * `categoryColor()` parses them back. Colours saved before the palette existed are kept as they
 * are and shown as the selected swatch until the user picks a palette colour.
 */
val EntityColorPalette: List<EntityColorOption> = listOf(
    EntityColorOption("#B5614A", Color(0xFFB5614A)), // terracotta
    EntityColorOption("#A5483F", Color(0xFFA5483F)), // brick
    EntityColorOption("#C98553", Color(0xFFC98553)), // clay
    EntityColorOption("#B38535", Color(0xFFB38535)), // amber
    EntityColorOption("#A67C2E", Color(0xFFA67C2E)), // ochre
    EntityColorOption("#958630", Color(0xFF958630)), // mustard
    EntityColorOption("#858A3A", Color(0xFF858A3A)), // olive
    EntityColorOption("#66854B", Color(0xFF66854B)), // moss
    EntityColorOption("#6E9A80", Color(0xFF6E9A80)), // sage
    EntityColorOption("#2F6B4F", Color(0xFF2F6B4F)), // forest
    EntityColorOption("#2E6A67", Color(0xFF2E6A67)), // pine
    EntityColorOption("#3E8588", Color(0xFF3E8588)), // teal
    EntityColorOption("#2F6F85", Color(0xFF2F6F85)), // petrol
    EntityColorOption("#4F86A8", Color(0xFF4F86A8)), // sky
    EntityColorOption("#4E6FA3", Color(0xFF4E6FA3)), // denim
    EntityColorOption("#6E7FB3", Color(0xFF6E7FB3)), // periwinkle
    EntityColorOption("#34496E", Color(0xFF34496E)), // navy
    EntityColorOption("#5B5A9E", Color(0xFF5B5A9E)), // indigo
    EntityColorOption("#8574B3", Color(0xFF8574B3)), // lavender
    EntityColorOption("#7A4F7A", Color(0xFF7A4F7A)), // plum
    EntityColorOption("#B0647C", Color(0xFFB0647C)), // rose
    EntityColorOption("#8C7B6D", Color(0xFF8C7B6D)), // taupe
    EntityColorOption("#7A5A48", Color(0xFF7A5A48)), // coffee
    EntityColorOption("#5A5F63", Color(0xFF5A5F63)), // graphite
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
 * Labeled grid of palette swatches. Selecting one calls [onSelect] with its hex. A [selectedHex]
 * outside the palette (saved before it existed) leads the grid as the selected swatch, so the
 * current colour stays visible and is only replaced when the user picks another.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerRow(
    label: String,
    selectedHex: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val savedOutsidePalette = selectedHex
        ?.takeIf { hex -> EntityColorPalette.none { it.hex.equals(hex, ignoreCase = true) } }
        ?.let { hex -> parseHexOrNull(hex)?.let { EntityColorOption(hex, it) } }
    val options = listOfNotNull(savedOutsidePalette) + EntityColorPalette

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
            options.forEach { option ->
                ColorSwatch(
                    option = option,
                    selected = option.hex.equals(selectedHex, ignoreCase = true),
                    onSelect = { onSelect(option.hex) },
                )
            }
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
