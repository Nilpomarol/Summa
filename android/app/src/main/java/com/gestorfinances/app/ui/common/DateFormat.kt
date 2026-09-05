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

/**
 * Compact date for dense lists, dropping the year while it is the current one — `30 jun.` this
 * year, `30 jun. 2025` in any other. In a list where nearly every row is recent, a repeated
 * current year is noise that crowds out the rest of the line.
 */
fun formatCompactDateRelative(iso: String, today: LocalDate = LocalDate.now()): String {
    val date = parseIsoDateOrNull(iso) ?: return INVALID_DATE_LABEL
    return formatCompactDate(date, includeYear = date.year != today.year)
}

/**
 * Expanded Catalan date used in detail surfaces, e.g. `30 de juny de 2025` — and
 * `3 d'agost de 2025` before a vowel, where the article elides.
 */
fun formatExpandedDate(iso: String): String =
    parseIsoDateOrNull(iso)?.let(::formatExpandedDate) ?: INVALID_DATE_LABEL

fun formatExpandedDate(date: LocalDate): String =
    "${date.dayOfMonth} ${monthWithArticle(date)} de ${date.year}"

/**
 * Catalan month name only, capitalized, e.g. `Juny`. Uses the standalone form so it reads "Juny"
 * rather than the contextual "de juny" the locale produces in a sentence.
 */
fun formatMonth(month: YearMonth): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, catalanLocale)
        .replaceFirstChar { it.uppercase() }

/** Catalan month + year, capitalized, e.g. `Juny 2025`. */
fun formatMonthYear(month: YearMonth): String = "${formatMonth(month)} ${month.year}"

/**
 * Catalan weekday, day and month written out, e.g. `Dijous, 3 de setembre` — and `Dijous, 3
 * d'agost` before a vowel, where the article elides. The year is left out: this is a "today"
 * label, so the year is noise.
 */
fun formatWeekdayLongDate(date: LocalDate): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, catalanLocale)
        .replaceFirstChar { it.uppercase() }
    return "$weekday, ${date.dayOfMonth} ${monthWithArticle(date)}"
}

/**
 * The month written out behind its article, which elides before a vowel: `de setembre`, but
 * `d'abril`, `d'agost`, `d'octubre`. Kept in one place so both written-out date formats agree.
 */
private fun monthWithArticle(date: LocalDate): String {
    val month = fullMonth(date)
    return if (month.first().lowercaseChar() in "aeiou") "d'$month" else "de $month"
}

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
