package com.gestorfinances.app.ui.movements

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountInputTest {
    @Test
    fun aTypedPointIsTheDecimalCommaAndBadInputIsIgnored() {
        assertEquals("12,5", normalizedAmountInput("12.5"))
        assertEquals("1234,56", normalizedAmountInput("1234,56"))
        assertNull(normalizedAmountInput("1,234"))
        assertNull(normalizedAmountInput("1,2,3"))
        assertNull(normalizedAmountInput("12a"))
    }

    @Test
    fun theAmountShowsGroupedWithItsEuroSign() {
        val shown = GroupedEuroAmount.filter(AnnotatedString("1234567,8"))
        assertEquals("1.234.567,8 €", shown.text.text)
        // The cursor after "1234" sits after "1.234".
        assertEquals(5, shown.offsetMapping.originalToTransformed(4))
        assertEquals(4, shown.offsetMapping.transformedToOriginal(5))
        assertEquals(9, shown.offsetMapping.transformedToOriginal(shown.text.length))
        assertEquals("999 €", GroupedEuroAmount.filter(AnnotatedString("999")).text.text)
    }
}
