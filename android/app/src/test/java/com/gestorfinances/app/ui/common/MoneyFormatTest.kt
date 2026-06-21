package com.gestorfinances.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {
    @Test
    fun formatsThousandsDotAndDecimalComma() {
        assertEquals("18.420,15 €", formatEuroCents(1_842_015))
        assertEquals("1.234.567,89 €", formatEuroCents(123_456_789))
        assertEquals("9,99 €", formatEuroCents(999))
        assertEquals("0,00 €", formatEuroCents(0))
        assertEquals("-1.000,00 €", formatEuroCents(-100_000))
    }

    @Test
    fun parsesBackToCentsWithGrouping() {
        assertEquals(1_842_015L, parseEuroCents("18.420,15", allowNegative = false))
        assertEquals(-100_000L, parseEuroCents("-1.000,00", allowNegative = true))
    }
}
