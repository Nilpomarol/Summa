package com.gestorfinances.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentWidthsTest {
    @Test
    fun smallSharesKeepTheMinimumAndTheBarStillFits() {
        val widths = segmentWidths(listOf(0.9f, 0.08f, 0.01f, 0.01f), available = 300f, minWidth = 14f)

        assertEquals(300f, widths.sum(), 0.01f)
        assertTrue(widths.all { it >= 14f - 0.01f })
        assertEquals(14f, widths[2], 0.01f)
        assertTrue(widths[0] > widths[1])
    }

    @Test
    fun fractionsThatDoNotSumToOneStillFillTheBar() {
        val widths = segmentWidths(listOf(0.25f, 0.25f), available = 200f, minWidth = 14f)

        assertEquals(listOf(100f, 100f), widths)
    }

    @Test
    fun tooManySegmentsShareTheWidthEqually() {
        val widths = segmentWidths(List(10) { 0.1f }, available = 100f, minWidth = 14f)

        assertTrue(widths.all { it == 10f })
    }
}
