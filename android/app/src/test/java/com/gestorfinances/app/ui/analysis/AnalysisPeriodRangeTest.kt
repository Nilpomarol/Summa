package com.gestorfinances.app.ui.analysis

import com.gestorfinances.app.data.repository.AnalysisBucket
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisPeriodRangeTest {
    @Test
    fun currentMonthComparisonStopsAtEquivalentDay() {
        val current = resolveAnalysisRange(
            scope = AnalysisScope.MONTH,
            month = YearMonth.of(2026, 8),
            year = 2026,
        )
        val previous = previousAnalysisRange(current, AnalysisScope.MONTH)!!

        val comparable = comparablePreviousRange(
            currentRange = current,
            previousRange = previous,
            scope = AnalysisScope.MONTH,
            today = LocalDate.of(2026, 8, 9),
        )

        assertEquals("2026-07-01", comparable.fromDate.toString())
        assertEquals("2026-07-10", comparable.toDateExclusive.toString())
    }

    @Test
    fun currentYearComparisonStopsAtEquivalentCalendarDay() {
        val current = resolveAnalysisRange(
            scope = AnalysisScope.YEAR,
            month = YearMonth.of(2026, 8),
            year = 2026,
        )
        val previous = previousAnalysisRange(current, AnalysisScope.YEAR)!!

        val comparable = comparablePreviousRange(
            currentRange = current,
            previousRange = previous,
            scope = AnalysisScope.YEAR,
            today = LocalDate.of(2026, 8, 9),
        )

        assertEquals("2025-01-01", comparable.fromDate.toString())
        assertEquals("2025-08-10", comparable.toDateExclusive.toString())
    }

    @Test
    fun completedPeriodComparisonRemainsComplete() {
        val current = resolveAnalysisRange(
            scope = AnalysisScope.MONTH,
            month = YearMonth.of(2026, 6),
            year = 2026,
        )
        val previous = previousAnalysisRange(current, AnalysisScope.MONTH)!!

        val comparable = comparablePreviousRange(
            currentRange = current,
            previousRange = previous,
            scope = AnalysisScope.MONTH,
            today = LocalDate.of(2026, 8, 9),
        )

        assertEquals(previous, comparable)
    }

    @Test
    fun monthScopeUsesWholeMonthAndDailyBuckets() {
        val range = resolveAnalysisRange(
            scope = AnalysisScope.MONTH,
            month = YearMonth.of(2026, 6),
            year = 2026,
        )

        assertEquals("2026-06-01", range.fromDate.toString())
        assertEquals("2026-07-01", range.toDateExclusive.toString())
        assertEquals(AnalysisBucket.DAY, range.bucket)
    }

    @Test
    fun yearScopeUsesWholeYearAndMonthlyBuckets() {
        val range = resolveAnalysisRange(
            scope = AnalysisScope.YEAR,
            month = YearMonth.of(2026, 6),
            year = 2026,
        )

        assertEquals("2026-01-01", range.fromDate.toString())
        assertEquals("2027-01-01", range.toDateExclusive.toString())
        assertEquals(AnalysisBucket.MONTH, range.bucket)
    }
}
