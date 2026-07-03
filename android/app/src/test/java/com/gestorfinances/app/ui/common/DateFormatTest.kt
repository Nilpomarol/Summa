package com.gestorfinances.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateFormatTest {
    @Test
    fun formatsMovementDateForCurrentYearAndOtherYears() {
        val currentYear = LocalDate.now().year
        val sameYearIso = "$currentYear-06-15"
        val pastYearIso = "2020-03-09"

        assertEquals("15/06", formatMovementDate(sameYearIso))
        assertEquals("09/03/2020", formatMovementDate(pastYearIso))
        assertEquals("invalid-date", formatMovementDate("invalid-date"))
    }
}
