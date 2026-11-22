package com.gestorfinances.app.ui.common

import kotlin.math.roundToInt

fun parseEuroCents(
    raw: String,
    allowNegative: Boolean,
): Long? {
    var value = raw.trim()
        .replace("€", "")
        .replace(" ", "")
        .replace("\u00A0", "")
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

fun formatEuroCents(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val absolute = kotlin.math.abs(cents)
    val whole = groupThousands(absolute / 100)
    val fraction = (absolute % 100).toString().padStart(2, '0')
    // A non-breaking space, so the symbol never wraps onto a line of its own.
    return "$sign$whole,$fraction\u00A0€"
}

/** Compact euro label for chart axis ticks: "2k €", "1,5k €", "500 €", "0". */
fun formatEuroCompact(cents: Long): String {
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

// Locale formatting: thousands dot, decimal comma — e.g. 18.420,15 €.
fun formatBasisPoints(value: Long): String {
    val sign = if (value < 0) "-" else ""
    val absolute = kotlin.math.abs(value)
    val whole = absolute / 100
    val fraction = (absolute % 100).toString().padStart(2, '0')
    return "$sign$whole,$fraction %"
}

/** Basis points as a compact percentage: "50%" for whole percents, "33,33%" otherwise. */
fun formatBasisPointsCompact(value: Long): String =
    if (value % 100L == 0L) "${value / 100}%" else formatBasisPoints(value).replace(" %", "%")

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

fun formatEuroInput(cents: Long): String =
    formatEuroCents(cents).removeSuffix(" €")

/** Whole-number percent label with a "<1%" floor so small-but-present shares aren't shown as 0%. */
fun formatPercentLabel(fraction: Float): String =
    if (fraction < 0.005f) "<1%" else "${(fraction * 100f).roundToInt()}%"
