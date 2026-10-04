package com.gestorfinances.desktop

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class RunningDebtTest {
    @Test
    fun balanceAfterEachEntryCountsBackFromTheCurrentOne() {
        assertEquals(listOf(3750L, 0L, 3200L), runningDebt(3750L, listOf(3750L, -3200L, 3200L)))
    }

    @Test
    fun noColumnWhenTheEffectsDoNotAddUpToTheBalance() {
        assertNull(runningDebt(3750L, listOf(3750L, -3200L)))
    }
}
