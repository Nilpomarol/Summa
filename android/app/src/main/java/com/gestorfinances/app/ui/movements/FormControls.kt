@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gestorfinances.app.ui.movements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.ui.theme.FinanceTheme

/** A single choice inside a [FormSelect]; [leading] paints an icon/dot/avatar before the label. */
internal class SelectOption(
    val id: String?,
    val label: String,
    val leading: (@Composable () -> Unit)? = null,
)

/**
 * Label-above bordered field shell (design §Fields): hairline `cardBorder`, r2 corners, 44dp
 * min height. Turns to a 1.5dp indigo border when [focused]. The label slot is omitted when
 * [label] is blank so the frame can double as a bare action control.
 */
@Composable
internal fun FieldFrame(
    label: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = FinanceTheme.colors.mutedText,
            )
        }
        Surface(
            modifier = surfaceModifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else FinanceTheme.colors.cardBorder,
            ),
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/**
 * Design-system select: a [FieldFrame] anchor (not a stock text field) with a popup of
 * [options]. The selected option is check-marked; the chevron rotates while open. A `null`
 * selection shows [placeholder] in muted text, so the control also works as an action picker.
 */
@Composable
internal fun FormSelect(
    label: String,
    options: List<SelectOption>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "—",
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        FieldFrame(
            label = label,
            focused = expanded,
            modifier = Modifier.fillMaxWidth(),
            surfaceModifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
        ) {
            selected?.leading?.invoke()
            Text(
                text = selected?.label ?: placeholder,
                modifier = Modifier.weight(1f),
                color = if (selected == null) {
                    FinanceTheme.colors.mutedText
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = FinanceTheme.colors.mutedText,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            option.leading?.invoke()
                            Text(
                                text = option.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    trailingIcon = if (option.id == selectedId) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(option.id)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** Round monogram avatar (design §Person row): tinted disc + colored initial. */
@Composable
internal fun PersonMonogram(
    label: String,
    colorHex: String?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val base = parseAvatarColor(colorHex) ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(base.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = base,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Small filled dot used as a [FormSelect] leading marker for account identity color. */
@Composable
internal fun ColorDot(
    colorHex: String?,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val base = parseAvatarColor(colorHex) ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(base),
    )
}

internal fun personInitial(name: String): String =
    name.trim().firstOrNull()?.uppercase() ?: "?"

private fun parseAvatarColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(hex.trim())) }.getOrNull()
}
