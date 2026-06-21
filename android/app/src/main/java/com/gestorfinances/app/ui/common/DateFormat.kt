package com.gestorfinances.app.ui.common

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val catalanLocale: Locale = Locale.forLanguageTag("ca")
private val longDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", catalanLocale)
private val monthYearFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", catalanLocale)

/** Parse a stored `'YYYY-MM-DD'` calendar date, or null when malformed. */
fun parseIsoDateOrNull(iso: String): LocalDate? =
    runCatching { LocalDate.parse(iso) }.getOrNull()

/** Catalan long date, e.g. `30 juny 2025`; falls back to the raw value if unparseable. */
fun formatLongDate(iso: String): String =
    parseIsoDateOrNull(iso)?.format(longDateFormatter) ?: iso

/** Catalan month + year, capitalized, e.g. `Juny 2025`. */
fun formatMonthYear(month: YearMonth): String =
    month.atDay(1).format(monthYearFormatter).replaceFirstChar { it.uppercase() }
