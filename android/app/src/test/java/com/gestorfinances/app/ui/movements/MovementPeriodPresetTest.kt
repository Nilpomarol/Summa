package com.gestorfinances.app.ui.movements

import com.gestorfinances.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MovementPeriodPresetTest {
    @Test
    fun commonPeriodsHandleYearRolloverAndLeapYears() {
        val today = LocalDate.of(2024, 1, 15)
        val filters = MovementFilters(query = "cafe", accountId = "a", tripId = "t")
        val month = filters.withPeriod(MovementPeriodPreset.THIS_MONTH, today)
        assertEquals("2024-01-01", month.dateFrom)
        assertEquals("2024-01-31", month.dateTo)
        val previous = filters.withPeriod(MovementPeriodPreset.PREVIOUS_MONTH, today)
        assertEquals("2023-12-01", previous.dateFrom)
        assertEquals("2023-12-31", previous.dateTo)
        val year = filters.withPeriod(MovementPeriodPreset.THIS_YEAR, today)
        assertEquals("2024-01-01", year.dateFrom)
        assertEquals("2024-12-31", year.dateTo)
        val february = filters.withPeriod(MovementPeriodPreset.THIS_MONTH, LocalDate.of(2024, 2, 10))
        assertEquals("2024-02-29", february.dateTo)
        MovementPeriodPreset.entries.filter { it != MovementPeriodPreset.CUSTOM }.forEach { preset ->
            val result = filters.withPeriod(preset, today)
            assertEquals(preset, result.periodPreset(today))
            assertEquals(filters.query, result.query)
            assertEquals(filters.accountId, result.accountId)
            assertEquals(filters.tripId, result.tripId)
        }
        assertEquals(filters, month.withPeriod(MovementPeriodPreset.ALL, today))
    }

    @Test
    fun customAndOpenEndedRangesArePreservedAndValidated() {
        val today = LocalDate.of(2026, 1, 15)
        val custom = MovementFilters(dateFrom = "2025-11-03", dateTo = "2026-01-17")
        assertEquals(MovementPeriodPreset.CUSTOM, custom.periodPreset(today))
        assertEquals(custom, custom.withPeriod(MovementPeriodPreset.CUSTOM, today))
        assertNull(custom.withDateValidation().errorRes)
        assertNull(custom.copy(dateFrom = "").withDateValidation().errorRes)
        assertNull(custom.copy(dateTo = "").withDateValidation().errorRes)
        assertEquals(R.string.movement_filter_date_order_invalid, custom.copy(dateFrom = "2026-01-18").withDateValidation().errorRes)
        assertEquals(R.string.movement_filter_date_invalid, custom.copy(dateFrom = "2026-02-30").withDateValidation().errorRes)
        assertEquals(R.string.movement_filter_date_invalid, custom.copy(dateTo = "bad date").withDateValidation().errorRes)
        assertNull(custom.copy(dateFrom = custom.dateTo).withDateValidation().errorRes)
    }
}
