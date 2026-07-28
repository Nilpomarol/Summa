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
