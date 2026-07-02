package com.gestorfinances.app.ui.common

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val catalanLocale: Locale = Locale.forLanguageTag("ca")
private val longDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", catalanLocale)

/** Parse a stored `'YYYY-MM-DD'` calendar date, or null when malformed. */
fun parseIsoDateOrNull(iso: String): LocalDate? =
    runCatching { LocalDate.parse(iso) }.getOrNull()

/** Catalan long date, e.g. `30 juny 2025`; falls back to the raw value if unparseable. */
fun formatLongDate(iso: String): String =
    parseIsoDateOrNull(iso)?.format(longDateFormatter) ?: iso

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

/**
 * Compact custom-range label, e.g. `12 jun - 18 jun 2026` (same year, shown once) or
 * `28 des 2025 - 3 gen 2026` (crosses a year, shown on both ends). Used where space is tight
 * (KPI cards, comparative chart captions) instead of the long `formatLongDate` pair.
 *
 * [includeYear] = false omits the year entirely, for when it's already common to both sides of a
 * comparison and shown elsewhere (see `periodComparisonLabels`).
 */
fun formatCompactDateRange(fromDate: LocalDate, toDateInclusive: LocalDate, includeYear: Boolean = true): String {
    val sameYear = fromDate.year == toDateInclusive.year
    val fromText = if (!includeYear || sameYear) {
        "${fromDate.dayOfMonth} ${shortMonth(fromDate)}"
    } else {
        "${fromDate.dayOfMonth} ${shortMonth(fromDate)} ${fromDate.year}"
    }
    val toText = if (includeYear) {
        "${toDateInclusive.dayOfMonth} ${shortMonth(toDateInclusive)} ${toDateInclusive.year}"
    } else {
        "${toDateInclusive.dayOfMonth} ${shortMonth(toDateInclusive)}"
    }
    return "$fromText - $toText"
}
