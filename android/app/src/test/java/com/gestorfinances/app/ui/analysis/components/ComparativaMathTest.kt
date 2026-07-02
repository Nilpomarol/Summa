package com.gestorfinances.app.ui.analysis.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ComparativaMathTest {
    @Test
    fun percentChangeIsNewWhenPreviousIsZero() {
        assertEquals(PercentChange.New, percentChange(current = 500L, previous = 0L))
    }

    @Test
    fun percentChangeIsEliminatedWhenCurrentIsZero() {
        assertEquals(PercentChange.Eliminated, percentChange(current = 0L, previous = 500L))
    }

    @Test
    fun percentChangeComputesIntegerDivisionRatio() {
        assertEquals(PercentChange.Value(20L), percentChange(current = 1200L, previous = 1000L))
        assertEquals(PercentChange.Value(-25L), percentChange(current = 750L, previous = 1000L))
    }

    @Test
    fun percentChangeIsZeroWhenUnchanged() {
        assertEquals(PercentChange.Value(0L), percentChange(current = 1000L, previous = 1000L))
    }

    @Test
    fun formatBasisPointsDeltaPpRoundsHalfUpAndSignsCorrectly() {
        assertEquals("+2,4 pp", formatBasisPointsDeltaPp(235L, " pp"))
        assertEquals("-1,3 pp", formatBasisPointsDeltaPp(-125L, " pp"))
        assertEquals("0,0 pp", formatBasisPointsDeltaPp(0L, " pp"))
    }
}
