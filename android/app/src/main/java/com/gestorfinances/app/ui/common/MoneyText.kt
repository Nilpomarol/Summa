package com.gestorfinances.app.ui.common

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.gestorfinances.app.ui.theme.asFigures
import kotlin.math.roundToInt

/**
 * Renders a money amount as a ledger figure (Geist Mono + tabular numerals, design §3).
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

internal fun parseEuroCents(
    raw: String,
    allowNegative: Boolean,
): Long? {
    var value = raw.trim()
        .replace("€", "")
        .replace(" ", "")
    if (value.isEmpty()) return null

    var sign = 1L
    if (value.startsWith("-") || value.startsWith("−")) {
        if (!allowNegative) return null
        sign = -1L
        value = value.drop(1)
    }
    if (value.isEmpty()) return null

    if (value.any { !it.isDigit() && it != ',' && it != '.' }) return null

    val separatorIndex = maxOf(value.lastIndexOf(','), value.lastIndexOf('.'))
    val wholeText: String
    val centsPart: String
    if (separatorIndex >= 0) {
        val decimalSeparator = value[separatorIndex]
        val thousandsSeparator = if (decimalSeparator == ',') '.' else ','
        wholeText = value.substring(0, separatorIndex)
        if (wholeText.any { !it.isDigit() && it != thousandsSeparator }) return null
        val decimalText = value.substring(separatorIndex + 1)
        if (decimalText.length > 2 || decimalText.any { !it.isDigit() }) return null
        centsPart = decimalText.padEnd(2, '0')
    } else {
        wholeText = value
        centsPart = "00"
    }

    val wholePart = wholeText.filter { it.isDigit() }
    if (wholePart.isEmpty()) return null

    return (wholePart.toLongOrNull() ?: return null) * 100L * sign +
        (centsPart.toLongOrNull() ?: return null) * sign
}

internal fun formatEuroCents(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val absolute = kotlin.math.abs(cents)
    val whole = groupThousands(absolute / 100)
    val fraction = (absolute % 100).toString().padStart(2, '0')
    return "$sign$whole,$fraction €"
}

/** Compact euro label for chart axis ticks: "2k €", "1,5k €", "500 €", "0". */
internal fun formatEuroCompact(cents: Long): String {
    if (cents == 0L) return "0"
    val sign = if (cents < 0) "-" else ""
    val absEuros = kotlin.math.abs(cents) / 100.0
    return when {
        absEuros >= 1_000.0 -> {
            val thousands = absEuros / 1_000.0
            val text = if (thousands >= 10.0) {
                thousands.roundToInt().toString()
            } else {
                "%.1f".format(thousands).replace('.', ',').removeSuffix(",0")
            }
            "$sign${text}k €"
        }
        else -> "$sign${absEuros.roundToInt()} €"
    }
}

// Locale formatting (design §3): thousands dot, decimal comma — e.g. 18.420,15 €.
internal fun formatBasisPoints(value: Long): String {
    val sign = if (value < 0) "-" else ""
    val absolute = kotlin.math.abs(value)
    val whole = absolute / 100
    val fraction = (absolute % 100).toString().padStart(2, '0')
    return "$sign$whole,$fraction %"
}

private fun groupThousands(value: Long): String {
    val digits = value.toString()
    val firstGroup = digits.length % 3
    return buildString {
        digits.forEachIndexed { index, char ->
            if (index != 0 && (index - firstGroup) % 3 == 0) append('.')
            append(char)
        }
    }
}

internal fun formatEuroInput(cents: Long): String =
    formatEuroCents(cents).removeSuffix(" €")
