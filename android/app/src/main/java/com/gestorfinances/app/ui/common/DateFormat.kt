package com.gestorfinances.app.ui.common

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val catalanLocale: Locale = Locale.forLanguageTag("ca")
private const val INVALID_DATE_LABEL = "—"

/** Parse a stored `'YYYY-MM-DD'` calendar date, or null when malformed. */
fun parseIsoDateOrNull(iso: String): LocalDate? =
    runCatching { LocalDate.parse(iso) }.getOrNull()

/** Compact Catalan date used in lists and controls, e.g. `30 jun. 2025`. */
fun formatCompactDate(iso: String): String =
    parseIsoDateOrNull(iso)?.let(::formatCompactDate) ?: INVALID_DATE_LABEL

fun formatCompactDate(date: LocalDate): String =
    "${date.dayOfMonth} ${shortMonth(date)} ${date.year}"

/** Expanded Catalan date used in detail surfaces, e.g. `30 de juny de 2025`. */
fun formatExpandedDate(iso: String): String =
    parseIsoDateOrNull(iso)?.let(::formatExpandedDate) ?: INVALID_DATE_LABEL

fun formatExpandedDate(date: LocalDate): String =
    "${date.dayOfMonth} de ${fullMonth(date)} de ${date.year}"

/**
 * Catalan month name only, capitalized, e.g. `Juny`. Uses the standalone form so it reads "Juny"
 * rather than the contextual "de juny" the locale produces in a sentence.
 */
fun formatMonth(month: YearMonth): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, catalanLocale)
        .replaceFirstChar { it.uppercase() }

/** Catalan month + year, capitalized, e.g. `Juny 2025`. */
fun formatMonthYear(month: YearMonth): String = "${formatMonth(month)} ${month.year}"

private fun shortMonth(date: LocalDate): String =
    date.month.getDisplayName(TextStyle.SHORT_STANDALONE, catalanLocale)

private fun fullMonth(date: LocalDate): String =
    date.month.getDisplayName(TextStyle.FULL_STANDALONE, catalanLocale)

/** Catalan weekday + compact date, capitalized, e.g. `Dissabte 13 jul. 2025`. */
fun formatWeekdayDate(iso: String): String {
    val date = parseIsoDateOrNull(iso) ?: return INVALID_DATE_LABEL
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, catalanLocale)
        .replaceFirstChar { it.uppercase() }
    return "$weekday ${formatCompactDate(date)}"
}

private fun formatCompactDate(date: LocalDate, includeYear: Boolean): String {
    val month = shortMonth(date)
    return if (includeYear) {
        "${date.dayOfMonth} $month ${date.year}"
    } else {
        "${date.dayOfMonth} $month"
    }
}

/**
 * Compact custom-range label, e.g. `12 jun. - 18 jun. 2026` (same year, shown once) or
 * `28 des. 2025 - 3 gen. 2026` (crosses a year, shown on both ends). Used where space is tight
 * (KPI cards, comparative chart captions) instead of the expanded date pair.
 *
 * [includeYear] = false omits the year entirely, for when it's already common to both sides of a
 * comparison and shown elsewhere (see `periodComparisonLabels`).
 */
fun formatCompactDateRange(fromDate: LocalDate, toDateInclusive: LocalDate, includeYear: Boolean = true): String {
    val sameYear = fromDate.year == toDateInclusive.year
    val fromText = formatCompactDate(fromDate, includeYear = includeYear && !sameYear)
    val toText = formatCompactDate(toDateInclusive, includeYear = includeYear)
    return "$fromText - $toText"
}
