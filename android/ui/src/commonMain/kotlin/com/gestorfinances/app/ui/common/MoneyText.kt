package com.gestorfinances.app.ui.common

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.gestorfinances.app.ui.theme.asFigures

/**
 * Renders a money amount as a ledger figure (IBM Plex Mono + tabular numerals).
 *
 * @param cents already-signed amount; negatives format with a leading minus.
 * @param signed when true, positive values are prefixed with `+` (income, positive deltas).
 */
@Composable
fun MoneyText(
    cents: Long,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    signed: Boolean = false,
) {
    val formatted = formatEuroCents(cents)
    val text = if (signed && cents > 0) "+$formatted" else formatted
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.asFigures(),
    )
}
