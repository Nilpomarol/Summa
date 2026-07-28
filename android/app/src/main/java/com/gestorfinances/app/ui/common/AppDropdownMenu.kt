@file:OptIn(ExperimentalMaterial3Api::class)

package com.gestorfinances.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gestorfinances.app.ui.theme.FinanceTheme

/**
 * The app's single dropdown/menu surface (design baseline): a warm `surface` popup with a
 * hairline `cardBorder`, r3 (16 dp) corners, and a soft shadow — the same visual contract as the
 * form select popup ([com.gestorfinances.app.ui.movements.FormSelect]) so every menu in the app,
 * from three-dot action menus to selection pickers, reads identically. Compose its rows with
 * [AppDropdownMenuItem] and [AppDropdownSectionHeader].
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, FinanceTheme.colors.cardBorder),
        shadowElevation = 8.dp,
        content = content,
    )
}

/**
 * One tappable row inside an [AppDropdownMenu] (or the form select popup). The label and optional
 * leading/trailing content are slots so callers can supply icons, chips, avatars, or a color for a
 * destructive action. [selected] carries the chosen option's state with an indigo-tint pill, an
 * accent label, and — unless the caller supplies its own [trailingIcon] — a trailing check.
 * [indented] nests a child under an [AppDropdownSectionHeader].
 */
@Composable
fun AppDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    indented: Boolean = false,
) {
    val contentColor = when {
        !enabled -> FinanceTheme.colors.disabledText
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            Color.Transparent
        },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (indented) Spacer(modifier = Modifier.size(14.dp))
            leadingIcon?.invoke()
            Box(modifier = Modifier.weight(1f)) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                        text()
                    }
                }
            }
            when {
                trailingIcon != null -> trailingIcon()
                selected -> Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Non-selectable muted header inside an [AppDropdownMenu] — e.g. a category container that groups
 * indented children. */
@Composable
fun AppDropdownSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = FinanceTheme.colors.mutedText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
    )
}
