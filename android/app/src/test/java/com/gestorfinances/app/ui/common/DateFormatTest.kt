package com.gestorfinances.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateFormatTest {
    @Test
    fun formatsCompactDateInCatalanWithYear() {
        assertEquals("15 des. 2025", formatCompactDate("2025-12-15"))
    }

    @Test
    fun formatsExpandedDateForDetails() {
        assertEquals("15 de desembre de 2025", formatExpandedDate("2025-12-15"))
    }

    /** In Catalan the article elides before a vowel, which only abril, agost and octubre hit. */
    @Test
    fun expandedDateElidesTheArticleBeforeAVowelMonth() {
        assertEquals("1 d'abril de 2025", formatExpandedDate("2025-04-01"))
        assertEquals("3 d'agost de 2025", formatExpandedDate("2025-08-03"))
        assertEquals("10 d'octubre de 2025", formatExpandedDate("2025-10-10"))
        assertEquals("15 de novembre de 2025", formatExpandedDate("2025-11-15"))
    }

    @Test
    fun weekdayLongDateElidesTheSameWayAndOmitsTheYear() {
        assertEquals("Diumenge, 3 d'agost", formatWeekdayLongDate(LocalDate.of(2025, 8, 3)))
        assertEquals("Dimecres, 3 de setembre", formatWeekdayLongDate(LocalDate.of(2025, 9, 3)))
    }

    @Test
    fun compactDateKeepsTheYearOnlyWhenItIsNotTheCurrentOne() {
        val today = LocalDate.of(2026, 1, 1)
        assertEquals("3 set.", formatCompactDateRelative("2026-09-03", today = today))
        assertEquals("3 ag. 2025", formatCompactDateRelative("2025-08-03", today = today))
    }

    @Test
    fun formatsWeekdayAndRangesWithoutIsoValues() {
        assertEquals("Dilluns 15 des. 2025", formatWeekdayDate("2025-12-15"))
        assertEquals(
            "28 des. 2025 - 3 gen. 2026",
            formatCompactDateRange(
                fromDate = LocalDate.of(2025, 12, 28),
                toDateInclusive = LocalDate.of(2026, 1, 3),
            ),
        )
    }

    @Test
    fun malformedDatesNeverRenderTheStoredIsoValue() {
        assertEquals("—", formatCompactDate("2026-02-30"))
        assertEquals("—", formatExpandedDate("not-a-date"))
        assertTrue(!formatCompactDate("not-a-date").contains("not-a-date"))
    }
}
