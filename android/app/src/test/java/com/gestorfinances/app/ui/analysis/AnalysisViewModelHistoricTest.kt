package com.gestorfinances.app.ui.analysis

import com.gestorfinances.app.data.repository.AnalysisBucket
import com.gestorfinances.app.data.repository.AnalysisPeriodTotals
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalysisViewModelHistoricTest {
    private fun totals(netWorthCents: Long, actualExpenseCents: Long) = AnalysisPeriodTotals(
        netWorthCents = netWorthCents,
        actualIncomeCents = 0L,
        actualExpenseCents = actualExpenseCents,
        netActualCents = 0L,
        accountFlowCents = 0L,
        savingsRateBasisPoints = 0L,
    )

    @Test
    fun zeroExpenseReturnsNullInsteadOfMisleadingZero() {
        val range = AnalysisPeriodRange(
            fromDate = LocalDate.of(2026, 6, 1),
            toDateExclusive = LocalDate.of(2026, 7, 1),
            bucket = AnalysisBucket.DAY,
        )
        val result = calculateDaysOfBuffer(
            scope = AnalysisScope.MONTH,
            range = range,
            totals = totals(netWorthCents = 100_000L, actualExpenseCents = 0L),
            bucketCount = 30,
        )
        assertNull(result)
    }

    @Test
    fun negativeNetWorthClampsToZeroDays() {
        val range = AnalysisPeriodRange(
            fromDate = LocalDate.of(2026, 6, 1),
            toDateExclusive = LocalDate.of(2026, 7, 1),
            bucket = AnalysisBucket.DAY,
        )
        val result = calculateDaysOfBuffer(
            scope = AnalysisScope.MONTH,
            range = range,
            totals = totals(netWorthCents = -50_000L, actualExpenseCents = 30_000L),
            bucketCount = 30,
        )
        assertEquals(0L, result)
    }

    @Test
    fun monthScopeUsesCalendarDaysBetween() {
        // June 2026 spans 30 days: avgDaily = 3000/30 = 100; days = 30000/100 = 300.
        val range = AnalysisPeriodRange(
            fromDate = LocalDate.of(2026, 6, 1),
            toDateExclusive = LocalDate.of(2026, 7, 1),
            bucket = AnalysisBucket.DAY,
        )
        val result = calculateDaysOfBuffer(
            scope = AnalysisScope.MONTH,
            range = range,
            totals = totals(netWorthCents = 30_000L, actualExpenseCents = 3_000L),
            bucketCount = 30,
        )
        assertEquals(300L, result)
    }

    @Test
    fun allTimeApproximatesDaysFromBucketCountNotTheFakeDateRange() {
        // ALL_TIME's range is the wide [0001-01-01, 9999-12-31) placeholder — must not be used
        // directly for a calendar-day diff. 12 monthly buckets -> daysInRange = 360;
        // avgDaily = 3600/360 = 10; days = 1000/10 = 100.
        val range = AnalysisPeriodRange(
            fromDate = LocalDate.of(1, 1, 1),
            toDateExclusive = LocalDate.of(9999, 12, 31),
            bucket = AnalysisBucket.MONTH,
        )
        val result = calculateDaysOfBuffer(
            scope = AnalysisScope.ALL_TIME,
            range = range,
            totals = totals(netWorthCents = 1_000L, actualExpenseCents = 3_600L),
            bucketCount = 12,
        )
        assertEquals(100L, result)
    }
}
