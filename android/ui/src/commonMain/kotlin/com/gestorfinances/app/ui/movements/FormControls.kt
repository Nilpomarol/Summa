@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gestorfinances.app.ui.movements

import com.gestorfinances.app.ui.common.menuShape
import com.gestorfinances.app.ui.theme.LocalControlStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.gestorfinances.app.ui.theme.parseHexColorOrNull
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
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
import com.gestorfinances.app.ui.common.AppDropdownMenuItem
import com.gestorfinances.app.ui.common.AppDropdownSectionHeader
import com.gestorfinances.app.ui.theme.FinanceTheme

/**
 * A single choice inside a [FormSelect]; [leading] paints an icon/dot/avatar before the label.
 * [enabled] = false renders a non-selectable header (used for category containers — a parent that
 * groups children but cannot itself be assigned). [indented] shifts the row right to nest a child
 * under such a header.
 */
class SelectOption(
    val id: String?,
    val label: String,
    val leading: (@Composable () -> Unit)? = null,
    val enabled: Boolean = true,
    val indented: Boolean = false,
)

/**
 * Label-above bordered field shell: hairline `cardBorder`, r2 corners, 44dp
 * min height. Turns to a 1.5dp indigo border when [focused]. The label slot is omitted when
 * [label] is blank so the frame can double as a bare action control. When [isError] is set the
 * border turns to the theme's error color (taking priority over [focused]) and, if
 * [supportingText] is non-null, a caption line renders below the frame — mirroring
 * `OutlinedTextField`'s `isError`/`supportingText` for the non-text-field form controls
 * (`FormSelect`, `FormDatePicker`) that wrap this frame.
 */
@Composable
fun FieldFrame(
    label: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
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
                width = if (isError || focused) 1.5.dp else 1.dp,
                color = when {
                    isError -> MaterialTheme.colorScheme.error
                    focused -> MaterialTheme.colorScheme.primary
                    else -> FinanceTheme.colors.cardBorder
                },
            ),
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = if (LocalControlStyle.current.pointer) 34.dp else 44.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
        if (isError && !supportingText.isNullOrEmpty()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * The app's text field: Material's outlined one on the phone; with a pointer, the label above a
 * compact box like the selects beside it, as a Windows form's are.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
    shape: Shape = MaterialTheme.shapes.small,
) {
    if (!LocalControlStyle.current.pointer) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            label = label,
            placeholder = placeholder,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            isError = isError,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            shape = shape,
        )
        return
    }
    val muted = FinanceTheme.colors.mutedText
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    // As wide as it is told to be; left alone, as wide as Material's.
    Column(modifier.width(IntrinsicSize.Max).alpha(if (enabled) 1f else 0.5f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            CompositionLocalProvider(LocalContentColor provides muted) {
                ProvideTextStyle(MaterialTheme.typography.labelMedium, label)
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minWidth = 160.dp),
            enabled = enabled,
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            interactionSource = interaction,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { field ->
                Surface(
                    shape = shape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = if (isError || focused) 1.5.dp else 1.dp,
                        color = when {
                            isError -> MaterialTheme.colorScheme.error
                            focused -> MaterialTheme.colorScheme.primary
                            else -> FinanceTheme.colors.cardBorder
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.heightIn(min = 34.dp).padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CompositionLocalProvider(LocalContentColor provides muted) {
                            ProvideTextStyle(textStyle.copy(color = muted)) {
                                prefix?.invoke()
                                Box(Modifier.weight(1f)) {
                                    if (value.isEmpty()) placeholder?.invoke()
                                    field()
                                }
                                suffix?.invoke()
                            }
                        }
                    }
                }
            },
        )
        if (supportingText != null) {
            CompositionLocalProvider(LocalContentColor provides if (isError) MaterialTheme.colorScheme.error else muted) {
                ProvideTextStyle(MaterialTheme.typography.labelSmall, supportingText)
            }
        }
    }
}

/**
 * Design-system select: a [FieldFrame] anchor (not a stock text field) with a popup of
 * [options]. The selected option is check-marked; the chevron rotates while open. A `null`
 * selection shows [placeholder] in muted text, so the control also works as an action picker.
 */
@Composable
fun FormSelect(
    label: String,
    options: List<SelectOption>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "—",
    isError: Boolean = false,
    supportingText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
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
            isError = isError,
            supportingText = supportingText,
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
        SelectMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            options = options,
            selectedId = selectedId,
            onSelect = onSelect,
            actionLabel = actionLabel,
            onAction = onAction,
        )
    }
}

/** The popup of a select anchored in this box: [options], with [selectedId] check-marked. */
@Composable
fun ExposedDropdownMenuBoxScope.SelectMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<SelectOption>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        // About six choices show at once; longer lists scroll instead of covering the screen.
        modifier = Modifier.heightIn(max = 320.dp),
        shape = menuShape(),
        containerColor = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
        shadowElevation = 8.dp,
    ) {
        if (actionLabel != null && onAction != null) {
            AppDropdownMenuItem(
                text = { Text(actionLabel) },
                onClick = {
                    onDismiss()
                    onAction()
                },
            )
        }
        options.forEach { option ->
            if (!option.enabled) {
                AppDropdownSectionHeader(text = option.label)
            } else {
                AppDropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onSelect(option.id)
                        onDismiss()
                    },
                    leadingIcon = option.leading,
                    selected = option.id == selectedId,
                    indented = option.indented,
                )
            }
        }
    }
}

/** Round monogram avatar: tinted disc with a colored initial. */
@Composable
fun PersonMonogram(
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
fun ColorDot(
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

fun personInitial(name: String): String =
    name.trim().firstOrNull()?.uppercase() ?: "?"

private fun parseAvatarColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return parseHexColorOrNull(hex.trim())
}
